package com.smartwifi.logic

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkDecisionBrain @Inject constructor() {

    private val probationList = mutableMapOf<String, Long>()
    private val PROBATION_DURATION_MS = 5 * 60 * 1000L
    
    // Configurable thresholds (defaults, repository overrides these)
    private var roamingTrigger = 5 // User setting: 5dB
    private val PENALTY_HOTSPOT_SWITCH = 15 
    private val ZOMBIE_ISOLATION_RSSI = -60

    fun normalizeSsid(ssid: String): String {
        return ssid.replace("\"", "")
            .replace(Regex("(?i)[-_ ]?(5g|5ghz|2\\.4g|2\\.4ghz|EXT|LTE)"), "")
            .trim()
    }

    fun calculateDesirabilityScore(rssi: Int, isMetered: Boolean, is5Ghz: Boolean): Int {
        var score = if (isMetered) 50 else 100
        val signalScore = (rssi + 140).coerceIn(0, 90) // Using same scale as Dashboard
        score += signalScore
        if (is5Ghz) score += 30 // Higher weight for 5GHz
        return score
    }

    /**
     * Strictly applies the Roaming Trigger and Priority Rules.
     */
    fun shouldSwitchNetwork(
        currentRssi: Int, 
        currentIsMetered: Boolean, 
        candidateRssi: Int, 
        candidateIsMetered: Boolean,
        userRoamingTrigger: Int = 5 // Passed from repository
    ): Boolean {
        // 1. Hotspot Penalty (Fixed -> Hotspot)
        if (!currentIsMetered && candidateIsMetered) {
            return candidateRssi > (currentRssi + PENALTY_HOTSPOT_SWITCH)
        }
        
        // 2. Fixed Preference (Hotspot -> Fixed)
        if (currentIsMetered && !candidateIsMetered) {
            return candidateRssi > -80
        }
        
        // 3. Same Type / Family (Roaming Trigger)
        // If candidate is better by the trigger threshold, we switch.
        return candidateRssi > (currentRssi + userRoamingTrigger)
    }

    fun isZombieConnection(hasInternet: Boolean): Boolean {
         return !hasInternet
    }

    fun addToProbation(bssid: String) {
        probationList[bssid] = System.currentTimeMillis() + PROBATION_DURATION_MS
    }

    fun isUnderProbation(bssid: String): Boolean {
        val endTime = probationList[bssid] ?: return false
        if (System.currentTimeMillis() > endTime) {
            probationList.remove(bssid) 
            return false
        }
        return true
    }

    fun getProbationList(): Map<String, Long> {
        return probationList.toMap()
    }
}
