package com.lmg.vk.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import com.lmg.vk.engine.AppSettings
import com.lmg.vk.engine.NetworkVitality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

object VpnBypassManager {

    private const val TAG = "VpnBypassManager"

    private var connectivityManager: ConnectivityManager? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _isVpnActive = MutableStateFlow(false)
    val isVpnActive: StateFlow<Boolean> = _isVpnActive

    private val _isBypassApplied = MutableStateFlow(false)
    val isBypassApplied: StateFlow<Boolean> = _isBypassApplied

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var physicalNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var defaultNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private val callbackPhysicalNetworks = ConcurrentHashMap.newKeySet<Network>()
    private val blockedPhysicalNetworks = ConcurrentHashMap.newKeySet<Network>()
    private val bindingMutex = Mutex()
    private var physicalRequestRegistered = false
    private var lastEffectiveNetwork: Network? = null
    private var routeInitialized = false
    @Volatile
    private var boundNetwork: Network? = null

    internal fun currentNetwork(): Network? =
        boundNetwork.takeIf { AppSettings.vpnBypassEnabled.value && _isBypassApplied.value }

    fun init(context: Context) {
        if (connectivityManager != null) return
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        connectivityManager = cm

        val request = NetworkRequest.Builder()
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateStateAndApply()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                updateStateAndApply()
            }

            override fun onLost(network: Network) {
                updateStateAndApply()
            }
        }
        networkCallback = callback

        runCatching {
            cm.registerNetworkCallback(request, callback)
        }

        val physicalCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                callbackPhysicalNetworks.add(network)
                updateStateAndApply()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                if (isPhysical(networkCapabilities)) callbackPhysicalNetworks.add(network)
                else callbackPhysicalNetworks.remove(network)
                updateStateAndApply()
            }

            override fun onLost(network: Network) {
                callbackPhysicalNetworks.remove(network)
                blockedPhysicalNetworks.remove(network)
                updateStateAndApply()
            }

            override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                if (blocked) blockedPhysicalNetworks.add(network) else blockedPhysicalNetworks.remove(network)
                updateStateAndApply()
            }
        }
        physicalNetworkCallback = physicalCallback

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val defCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    updateStateAndApply()
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    updateStateAndApply()
                }

                override fun onLost(network: Network) {
                    updateStateAndApply()
                }
            }
            defaultNetworkCallback = defCallback
            runCatching {
                cm.registerDefaultNetworkCallback(defCallback)
            }
        }

        scope.launch {
            while (isActive) {
                updateStateAndApply()
                delay(5000)
            }
        }

        updateStateAndApply()
    }

    fun applyMode(enabled: Boolean) {
        // Re-read the persisted setting under the same mutex as callbacks: rapid toggles cannot
        // let an old queued disable operation undo a newer enable operation.
        updateStateAndApply()
    }

    fun updateStateAndApply() {
        val cm = connectivityManager ?: return
        scope.launch {
            bindingMutex.withLock {
                updatePhysicalRequest(cm, AppSettings.vpnBypassEnabled.value)
                val networks = cm.allNetworks.mapNotNull { network ->
                    runCatching { cm.getNetworkCapabilities(network) }.getOrNull()?.let { network to it }
                }
                val vpnFound = networks.any { (_, caps) ->
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                }
                if (_isVpnActive.value != vpnFound) {
                    _isVpnActive.value = vpnFound
                    Log.d(TAG, "VPN state changed: isVpnActive = $vpnFound")
                }

                val physicalNetworks = (networks.map { it.first } + callbackPhysicalNetworks)
                    .distinct()
                    .mapNotNull { network ->
                        runCatching { cm.getNetworkCapabilities(network) }.getOrNull()
                            ?.let { network to it }
                    }
                val physicalNetwork = selectPhysicalNetwork(physicalNetworks.map { (network, caps) ->
                    PhysicalNetworkRoute(network, isPhysical(caps), network in blockedPhysicalNetworks,
                        !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED),
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                        transportPriority(caps))
                }, boundNetwork)

                val target = physicalNetwork.takeIf {
                    vpnFound && AppSettings.vpnBypassEnabled.value
                }
                applyBinding(cm, target)
            }
        }
    }

    private fun updatePhysicalRequest(cm: ConnectivityManager, enabled: Boolean) {
        val callback = physicalNetworkCallback ?: return
        if (enabled == physicalRequestRegistered) return
        try {
            if (enabled) {
                // A passive callback only observes networks; it does not keep the underlying
                // connection available while a VPN owns the default route.
                cm.requestNetwork(NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build(), callback)
            } else {
                cm.unregisterNetworkCallback(callback)
                callbackPhysicalNetworks.clear()
                blockedPhysicalNetworks.clear()
            }
            physicalRequestRegistered = enabled
        } catch (error: Exception) {
            Log.w(TAG, "Physical network request failed: ${error.javaClass.simpleName}")
        }
    }

    private fun isPhysical(capabilities: NetworkCapabilities): Boolean =
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
            (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))

    private fun transportPriority(capabilities: NetworkCapabilities): Int {
        var score = 0
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) score += 30
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) score += 25
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) score += 10
        return score
    }

    private fun applyBinding(cm: ConnectivityManager, target: Network?) {
        val actual = cm.boundNetworkForProcess
        val applied = actual == target || runCatching { cm.bindProcessToNetwork(target) }.getOrDefault(false)
        if (!applied) {
            _isBypassApplied.value = false
            Log.w(TAG, "Failed to apply VPN bypass network")
            return
        }
        boundNetwork = target
        _isBypassApplied.value = target != null
        val effectiveNetwork = target ?: cm.activeNetwork
        if (routeInitialized && lastEffectiveNetwork == effectiveNetwork) return
        routeInitialized = true
        lastEffectiveNetwork = effectiveNetwork
        Log.d(TAG, if (target == null) "Using default network" else "Using physical network $target")
        com.lmg.vk.debug.DebugLog.add(
            if (target == null) "VPN BYPASS route=default" else "VPN BYPASS route=physical",
        )
        NetworkVitality.onDefaultNetworkChanged()
    }
}
