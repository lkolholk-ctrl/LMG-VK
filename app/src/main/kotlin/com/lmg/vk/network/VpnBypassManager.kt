package com.lmg.vk.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import com.lmg.vk.engine.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

        // Периодический опрос (раз в 2 сек) как надёжная страховка для кастомных VPN (v2rayTun/Xray),
        // которые могут поднимать/гасить туннели без стандартного широковещательного события.
        scope.launch {
            while (isActive) {
                updateStateAndApply()
                delay(2000)
            }
        }

        updateStateAndApply()
    }

    fun applyMode(enabled: Boolean) {
        val cm = connectivityManager ?: return
        if (!enabled) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    cm.bindProcessToNetwork(null)
                }
            }
            _isBypassApplied.value = false
            Log.d(TAG, "VPN bypass disabled: restored default process network binding")
            return
        }
        updateStateAndApply()
    }

    fun updateStateAndApply() {
        val cm = connectivityManager ?: return
        scope.launch {
            val networks = cm.allNetworks
            var vpnFound = false
            var physicalNetwork: Network? = null

            for (network in networks) {
                val caps = runCatching { cm.getNetworkCapabilities(network) }.getOrNull() ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    vpnFound = true
                }
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                ) {
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                    ) {
                        physicalNetwork = network
                    }
                }
            }

            if (_isVpnActive.value != vpnFound) {
                _isVpnActive.value = vpnFound
                Log.d(TAG, "VPN state changed: isVpnActive = $vpnFound")
            }

            if (AppSettings.vpnBypassEnabled.value && physicalNetwork != null) {
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        cm.bindProcessToNetwork(physicalNetwork)
                    }
                }
                _isBypassApplied.value = true
            } else {
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        cm.bindProcessToNetwork(null)
                    }
                }
                _isBypassApplied.value = false
            }
        }
    }
}
