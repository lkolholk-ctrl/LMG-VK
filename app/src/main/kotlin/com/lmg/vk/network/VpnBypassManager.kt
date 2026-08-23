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

/**
 * VpnBypassManager — направляет сетевой трафик приложения напрямую через физический
 * сетевой интерфейс (Wi-Fi или мобильную сеть оператора), минуя активный VPN-туннель.
 *
 * Мгновенно отслеживает появление/исчезновение VPN-соединения и переключает
 * привязку сокетов приложения без необходимости ручного переключения тумблера.
 */
object VpnBypassManager {

    private const val TAG = "VpnBypassManager"

    private var connectivityManager: ConnectivityManager? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _isVpnActive = MutableStateFlow(false)
    val isVpnActive: StateFlow<Boolean> = _isVpnActive

    private val _isBypassApplied = MutableStateFlow(false)
    val isBypassApplied: StateFlow<Boolean> = _isBypassApplied

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var defaultNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private val bindingMutex = Mutex()
    private var boundNetwork: Network? = null

    fun init(context: Context) {
        if (connectivityManager != null) return
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        connectivityManager = cm

        // ВАЖНО: NetworkRequest по умолчанию содержит NET_CAPABILITY_NOT_VPN.
        // Чтобы получать события появления/отключения VPN, явно снимаем это ограничение.
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
        val cm = connectivityManager ?: return
        if (enabled) {
            updateStateAndApply()
        } else {
            scope.launch {
                bindingMutex.withLock {
                    applyBinding(cm, null)
                }
            }
        }
    }

    fun updateStateAndApply() {
        val cm = connectivityManager ?: return
        scope.launch {
            bindingMutex.withLock {
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

                val physicalNetwork = networks
                    .asSequence()
                    .filter { (_, caps) ->
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                            !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                            (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
                    }
                    .maxByOrNull { (_, caps) ->
                        var score = 0
                        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) score += 100
                        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)) score += 20
                        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) score += 30
                        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) score += 25
                        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) score += 10
                        score
                    }
                    ?.first

                val target = physicalNetwork.takeIf {
                    vpnFound && AppSettings.vpnBypassEnabled.value
                }
                applyBinding(cm, target)
            }
        }
    }

    private fun applyBinding(cm: ConnectivityManager, target: Network?) {
        if (boundNetwork == target && _isBypassApplied.value == (target != null)) return
        val applied = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching { cm.bindProcessToNetwork(target) }.getOrDefault(false)
        } else {
            target == null
        }
        if (!applied) {
            Log.w(TAG, "Failed to apply VPN bypass network")
            return
        }
        boundNetwork = target
        _isBypassApplied.value = target != null
        Log.d(TAG, if (target == null) "Using default network" else "Using physical network $target")
        com.lmg.vk.debug.DebugLog.add(
            if (target == null) "VPN BYPASS route=default" else "VPN BYPASS route=physical",
        )
        NetworkVitality.onDefaultNetworkChanged()
    }
}
