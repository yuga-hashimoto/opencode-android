package com.opencode.android.feature.workspace

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.merge

data class DiscoveredServer(
    val name: String,
    val host: String,
    val port: Int
) {
    val baseUrl: String get() = "http://$host:$port"
}

private const val OPENCODE_LEGACY_SERVICE_TYPE = "_opencode._tcp."
private const val HTTP_SERVICE_TYPE = "_http._tcp."
private const val OPENCODE_NAME_PREFIX = "opencode-"

class LanDiscovery(private val nsdManager: NsdManager) {
    fun discover(): Flow<DiscoveredServer> = merge(
        browseService(OPENCODE_LEGACY_SERVICE_TYPE) { true },
        browseService(HTTP_SERVICE_TYPE) { serviceName ->
            serviceName.startsWith(OPENCODE_NAME_PREFIX, ignoreCase = true)
        }
    )

    private fun browseService(
        serviceType: String,
        nameFilter: (String) -> Boolean
    ): Flow<DiscoveredServer> = callbackFlow {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

            @Suppress("DEPRECATION")
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host?.hostAddress ?: return
                val rawName = serviceInfo.serviceName.orEmpty()
                val displayName = rawName
                    .removePrefix(OPENCODE_NAME_PREFIX)
                    .ifBlank { host }
                trySend(
                    DiscoveredServer(
                        name = displayName,
                        host = host,
                        port = serviceInfo.port
                    )
                )
            }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!nameFilter(serviceInfo.serviceName.orEmpty())) return
                runCatching {
                    @Suppress("DEPRECATION")
                    nsdManager.resolveService(serviceInfo, resolveListener)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
        }

        runCatching {
            nsdManager.discoverServices(
                serviceType,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener
            )
        }.onFailure { close() }

        awaitClose {
            runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
        }
    }
}
