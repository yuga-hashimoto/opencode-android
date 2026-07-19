package com.opencode.android.feature.connection

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.opencode.android.core.security.OpenCodeUrl
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class DiscoveredOpenCodeService(
    val name: String,
    val baseUrl: String,
    val host: String,
    val port: Int,
    val version: String? = null
)

internal data class ResolvedOpenCodeService(
    val name: String,
    val host: String,
    val port: Int,
    val attributes: Map<String, String> = emptyMap()
)

internal fun mapResolvedOpenCodeService(
    service: ResolvedOpenCodeService
): DiscoveredOpenCodeService? {
    if (service.port !in 1..65535) return null
    val api = service.attributes["api"]?.trim()
    if (!api.isNullOrEmpty() && api != "1") return null
    val scheme = if (service.attributes["tls"].isTruthy()) "https" else "http"
    val normalizedHost = service.host.trim().substringBefore('%').removeSurrounding("[", "]")
    if (normalizedHost.isEmpty()) return null
    val hostForUrl = if (':' in normalizedHost) "[$normalizedHost]" else normalizedHost
    val endpoint = OpenCodeUrl.normalize("$scheme://$hostForUrl:${service.port}").getOrNull()
        ?: return null
    return DiscoveredOpenCodeService(
        name = service.name.trim().ifBlank { normalizedHost },
        baseUrl = endpoint.toString(),
        host = normalizedHost,
        port = service.port,
        version = service.attributes["version"]?.trim()?.takeIf(String::isNotEmpty)
    )
}

internal fun mergeDiscoveredOpenCodeServices(
    services: Collection<DiscoveredOpenCodeService>
): List<DiscoveredOpenCodeService> = services
    .associateBy(DiscoveredOpenCodeService::baseUrl)
    .values
    .sortedWith(
        Comparator { left, right ->
            val byName = String.CASE_INSENSITIVE_ORDER.compare(left.name, right.name)
            if (byName != 0) byName else left.baseUrl.compareTo(right.baseUrl)
        }
    )

private fun String?.isTruthy(): Boolean =
    this?.trim()?.lowercase() in setOf("1", "true", "yes", "on")

/**
 * Discovers `_opencode._tcp` services on the local network.
 * TXT attributes understood: api=1, version=x.y.z, tls=1.
 */
class OpenCodeNsdDiscovery(
    context: Context,
    private val nsdManager: NsdManager = context.getSystemService(NsdManager::class.java)
) {
    private val appContext = context.applicationContext

    @Suppress("DEPRECATION")
    fun discover(): Flow<List<DiscoveredOpenCodeService>> = callbackFlow {
        val services = ConcurrentHashMap<String, DiscoveredOpenCodeService>()
        var discoveryStarted = false
        val multicastLock = acquireLegacyMulticastLock()

        fun publish() {
            trySend(mergeDiscoveredOpenCodeServices(services.values))
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                discoveryStarted = true
                publish()
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceType.equals(SERVICE_TYPE, ignoreCase = true)) return
                nsdManager.resolveService(
                    serviceInfo,
                    object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            val host = resolved.host?.hostAddress ?: return
                            val attributes = resolved.attributes.mapValues { (_, value) ->
                                value.toString(Charsets.UTF_8)
                            }
                            val discovered = mapResolvedOpenCodeService(
                                ResolvedOpenCodeService(
                                    name = resolved.serviceName,
                                    host = host,
                                    port = resolved.port,
                                    attributes = attributes
                                )
                            ) ?: return
                            services[resolved.serviceName] = discovered
                            publish()
                        }
                    }
                )
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                services.remove(serviceInfo.serviceName)
                publish()
            }

            override fun onDiscoveryStopped(serviceType: String) {
                discoveryStarted = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                discoveryStarted = false
                close(IllegalStateException("OpenCode discovery failed: $errorCode"))
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

        runCatching {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            multicastLock?.releaseSafely()
            close(it)
        }

        awaitClose {
            if (discoveryStarted) {
                runCatching { nsdManager.stopServiceDiscovery(listener) }
            }
            multicastLock?.releaseSafely()
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireLegacyMulticastLock(): WifiManager.MulticastLock? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return null
        val wifiManager = appContext.getSystemService(WifiManager::class.java) ?: return null
        return runCatching {
            wifiManager.createMulticastLock(MULTICAST_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()
    }

    private fun WifiManager.MulticastLock.releaseSafely() {
        runCatching { if (isHeld) release() }
    }

    companion object {
        const val SERVICE_TYPE = "_opencode._tcp."
        private const val MULTICAST_LOCK_TAG = "opencode-android-nsd"
    }
}
