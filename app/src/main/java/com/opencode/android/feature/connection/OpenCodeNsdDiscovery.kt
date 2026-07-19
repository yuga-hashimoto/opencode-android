package com.opencode.android.feature.connection

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.ConcurrentHashMap

data class DiscoveredOpenCodeService(
    val name: String,
    val host: String,
    val port: Int,
    val baseUrl: String
)

/**
 * Discovers LAN OpenCode servers advertised as `_opencode._tcp` (preferred)
 * or generic `_http._tcp` services whose name contains "opencode".
 */
class OpenCodeNsdDiscovery(
    context: Context
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    fun discover(): Flow<List<DiscoveredOpenCodeService>> = callbackFlow {
        val found = ConcurrentHashMap<String, DiscoveredOpenCodeService>()
        fun publish() {
            trySend(found.values.sortedBy { it.name.lowercase() })
        }

        val listeners = mutableListOf<NsdManager.DiscoveryListener>()
        val resolveLocks = ConcurrentHashMap.newKeySet<String>()

        fun resolve(serviceInfo: NsdServiceInfo) {
            val key = "${serviceInfo.serviceName}|${serviceInfo.serviceType}"
            if (!resolveLocks.add(key)) return
            nsdManager.resolveService(
                serviceInfo,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        resolveLocks.remove(key)
                    }

                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        resolveLocks.remove(key)
                        val host = resolved.host?.hostAddress ?: return
                        val port = resolved.port.takeIf { it > 0 } ?: return
                        val name = resolved.serviceName.ifBlank { host }
                        val lowerName = name.lowercase()
                        val type = resolved.serviceType.orEmpty().lowercase()
                        val isOpenCode = type.contains("opencode") || lowerName.contains("opencode")
                        if (!isOpenCode) return
                        found[key] = DiscoveredOpenCodeService(
                            name = name,
                            host = host,
                            port = port,
                            baseUrl = "http://$host:$port/"
                        )
                        publish()
                    }
                }
            )
        }

        fun start(serviceType: String) {
            val listener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                override fun onDiscoveryStarted(serviceType: String) = Unit
                override fun onDiscoveryStopped(serviceType: String) = Unit
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    resolve(serviceInfo)
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    val key = "${serviceInfo.serviceName}|${serviceInfo.serviceType}"
                    found.remove(key)
                    publish()
                }
            }
            listeners += listener
            runCatching {
                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            }
        }

        start(SERVICE_TYPE_OPENCODE)
        start(SERVICE_TYPE_HTTP)
        publish()

        awaitClose {
            listeners.forEach { listener ->
                runCatching { nsdManager.stopServiceDiscovery(listener) }
            }
        }
    }

    companion object {
        const val SERVICE_TYPE_OPENCODE = "_opencode._tcp."
        const val SERVICE_TYPE_HTTP = "_http._tcp."
    }
}
