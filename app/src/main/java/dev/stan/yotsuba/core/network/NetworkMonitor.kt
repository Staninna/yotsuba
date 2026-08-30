package dev.stan.yotsuba.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class NetworkStatus {
    Offline, Metered, Unmetered;

    /** True on a connection the carrier bills by the byte; false offline or on wifi. */
    val isMetered: Boolean get() = this == Metered
}

/**
 * A boolean cannot answer "is this connection metered", which is what mediaAutoplay needs (§8).
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    fun current(): NetworkStatus = statusOf(manager.activeNetwork)

    val status: Flow<NetworkStatus> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(current()) }
            override fun onLost(network: Network) { trySend(current()) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(current())
            }
        }
        trySend(current())
        manager.registerNetworkCallback(NetworkRequest.Builder().build(), callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun statusOf(network: Network?): NetworkStatus {
        val caps = network?.let { manager.getNetworkCapabilities(it) }
            ?: return NetworkStatus.Offline
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return NetworkStatus.Offline
        return if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
            NetworkStatus.Unmetered
        } else {
            NetworkStatus.Metered
        }
    }
}
