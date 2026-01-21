package com.smartwifi.logic

import android.content.Context
import android.telephony.TelephonyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

@Singleton
class MobileNetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as android.telephony.SubscriptionManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private var cachedSignalDbm: Int = -100
    private var cachedDisplayOverride: Int = 0

    init {
        registerListeners()
    }

    private fun registerListeners() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                telephonyManager.registerTelephonyCallback(
                    context.mainExecutor,
                    object : android.telephony.TelephonyCallback(), 
                             android.telephony.TelephonyCallback.SignalStrengthsListener,
                             android.telephony.TelephonyCallback.DisplayInfoListener {
                        
                        override fun onSignalStrengthsChanged(signalStrength: android.telephony.SignalStrength) {
                            cachedSignalDbm = getDbmFromSignalStrength(signalStrength)
                        }

                        override fun onDisplayInfoChanged(telephonyDisplayInfo: android.telephony.TelephonyDisplayInfo) {
                            cachedDisplayOverride = telephonyDisplayInfo.overrideNetworkType
                        }
                    }
                )
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.listen(object : android.telephony.PhoneStateListener() {
                    override fun onSignalStrengthsChanged(signalStrength: android.telephony.SignalStrength) {
                        super.onSignalStrengthsChanged(signalStrength)
                        cachedSignalDbm = getDbmFromSignalStrength(signalStrength)
                    }
                    
                    override fun onDisplayInfoChanged(telephonyDisplayInfo: android.telephony.TelephonyDisplayInfo) {
                        super.onDisplayInfoChanged(telephonyDisplayInfo)
                        cachedDisplayOverride = telephonyDisplayInfo.overrideNetworkType
                    }
                }, android.telephony.PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or android.telephony.PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED)
            }
        } catch (e: Exception) {
            Log.e("MobileNetworkMonitor", "Error registering listener", e)
        }
    }

    private fun getDbmFromSignalStrength(signalStrength: android.telephony.SignalStrength): Int {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
             signalStrength.cellSignalStrengths.forEach { 
                 val dbm = it.dbm
                 if (dbm < 0 && dbm > -140) return dbm
             }
        }
        val level = signalStrength.level 
        return when (level) {
            4 -> -65
            3 -> -85
            2 -> -100
            1 -> -115
            else -> -120
        }
    }

    fun getLinkSpeed(): Int {
        try {
            // Calculate a baseline estimation based on Signal Quality (-140 to -50)
            // Range shifted to be more optimistic as requested: 100% at -90dBm
            val signalFactor = ((cachedSignalDbm + 140).toFloat() / 50f).coerceIn(0f, 1f)
            
            val maxTheoreticalMbps = when (getNetworkType()) {
                "5G" -> 1000
                "4.5G" -> 300
                "4G" -> 150
                "3G" -> 42
                else -> 50
            }

            val estimatedCapacity = (maxTheoreticalMbps * signalFactor).toInt()

            // Try to get system reported bandwidth
            val network = connectivityManager.activeNetwork
            val caps = connectivityManager.getNetworkCapabilities(network)
            val systemBandwidth = if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                caps.linkDownstreamBandwidthKbps / 1000
            } else 0

            // Use the higher value: if the system under-reports but the technology/signal is good, show the capacity.
            // If the system reports a high value, use that.
            return maxOf(estimatedCapacity, systemBandwidth).coerceAtLeast(1)
            
        } catch (e: Exception) {
            return 10 // Safe fallback
        }
    }

    fun getCarrierName(): String {
        return try {
            val activeSubId = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                android.telephony.SubscriptionManager.getDefaultDataSubscriptionId()
            } else {
                -1
            }

            if (activeSubId != -1) {
                val activeInfo = subscriptionManager.activeSubscriptionInfoList?.find { it.subscriptionId == activeSubId }
                if (activeInfo != null) {
                    var carrier = activeInfo.carrierName?.toString()
                    if (carrier.isNullOrEmpty()) carrier = activeInfo.displayName?.toString()
                    if (!carrier.isNullOrEmpty()) return carrier
                }
            }

            val name = telephonyManager.simOperatorName
            if (name.isNullOrEmpty()) {
                telephonyManager.networkOperatorName ?: "Mobile Data"
            } else {
                name
            }
        } catch (e: Exception) {
            "Mobile Network"
        }
    }

    fun getNetworkType(): String {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                 val networkType = telephonyManager.dataNetworkType
                 
                 if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                     when (cachedDisplayOverride) {
                         android.telephony.TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO,
                         android.telephony.TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA -> return "4.5G"
                         android.telephony.TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                         android.telephony.TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE -> return "5G"
                     }
                 }

                 when (networkType) {
                     TelephonyManager.NETWORK_TYPE_NR -> "5G"
                     TelephonyManager.NETWORK_TYPE_LTE -> "4G" 
                     TelephonyManager.NETWORK_TYPE_HSPAP, 
                     TelephonyManager.NETWORK_TYPE_HSPA,
                     TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                     TelephonyManager.NETWORK_TYPE_EDGE,
                     TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
                     else -> "4G"
                 }
            } else {
                "4G"
            }
        } catch (e: Exception) {
            "Mobile"
        }
    }
    
    fun getSignalStrength(): Int {
        return cachedSignalDbm
    }
}
