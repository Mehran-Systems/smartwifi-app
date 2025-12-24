package com.smartwifi.logic

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkDecisionBrain @Inject constructor() {

    // Map of BSSID to Timestamp (when probation ends)
    private val probationList = mutableMapOf<String, Long>()
    private val PROBATION_DURATION_MS = 5 * 60 * 1000L // 5 minutes (Updated from 2)
    
    // Thresholds
    private val SCORE_FIXED_WIFI = 100
    private val SCORE_METERED_HOTSPOT = 50
    private val PENALTY_HOTSPOT_SWITCH = 15 // dBm
    private val ZOMBIE_ISOLATION_RSSI = -60

    /**
     * Normalizes SSID to handle 2.4G/5G suffixes.
     * Example: "HomeWifi-5G" -> "HomeWifi"
     */
    fun normalizeSsid(ssid: String): String {
        return ssid.replace("\"", "")
            .replace(Regex("(?i)[-_ ]?(5g|5ghz|2\\.4g|2\\.4ghz|EXT|LTE)"), "")
            .trim()
    }

    /**
     * Calculates the desirability score of a network candidate.
     * Higher is better.
     */
    fun calculateDesirabilityScore(rssi: Int, isMetered: Boolean, is5Ghz: Boolean): Int {
        var score = if (isMetered) SCORE_METERED_HOTSPOT else SCORE_FIXED_WIFI
        
        // Signal Quality Bonus/Penalty
        // Map RSSI (-100 to -50) to roughly (0 to 50) points
        val signalScore = (rssi + 100).coerceIn(0, 50)
        score += signalScore
        
        // 5GHz Bonus
        if (is5Ghz) score += 20
        
        return score
    }

    /**
     * Determines if we should switch from Current to Candidate.
     * Implements the "Penalty Rule" for Hotspots.
     */
    fun shouldSwitchNetwork(
        currentRssi: Int, 
        currentIsMetered: Boolean, 
        candidateRssi: Int, 
        candidateIsMetered: Boolean
    ): Boolean {
        // Base Rule: Candidate must be strictly better
        
        // Penalty Rule: If switching FROM Fixed TO Hotspot
        if (!currentIsMetered && candidateIsMetered) {
            // Network B (Hotspot) needs to be > 15 dBm stronger than Network A (Fixed)
            // AND we treat Fixed as inherently more valuable, so we check signal delta directly here 
            // as per requirements "Signal > 15 dBm stronger".
            val isSignificantlyStronger = candidateRssi > (currentRssi + PENALTY_HOTSPOT_SWITCH)
            return isSignificantlyStronger
        }
        
        // Default Scoring Comparison for other cases (Hotspot->Fixed, Fixed->Fixed, Hotspot->Hotspot)
        // We use a simple RSSI check with hysteresis if types are same, or favor Fixed if types differ.
        if (currentIsMetered && !candidateIsMetered) {
            // Always prefer switching to Fixed if signal is usable (e.g. > -85)
            // But let's use a safe threshold to avoid barely-usable fixed networks.
            return candidateRssi > -80
        }
        
        // Same Type Comparison: Use standard hysteresis (e.g. 10dB better)
        return candidateRssi > (currentRssi + 10)
    }

    fun shouldEnterProbation(rssi: Int, hasInternet: Boolean): Boolean {
        // Core Philosophy: Internet First.
        // If we have strong signal but no internet, it's a Zombie.
        return rssi > ZOMBIE_ISOLATION_RSSI && !hasInternet
    }
    
    // Zombie Check specifically for the "Connected but no Internet" passive check
    fun isZombieConnection(hasInternet: Boolean): Boolean {
         return !hasInternet
    }

    fun addToProbation(bssid: String) {
        probationList[bssid] = System.currentTimeMillis() + PROBATION_DURATION_MS
    }

    fun isUnderProbation(bssid: String): Boolean {
        val endTime = probationList[bssid] ?: return false
        if (System.currentTimeMillis() > endTime) {
            probationList.remove(bssid) // Expired
            return false
        }
        return true
    }

    fun shouldTriggerDataFallback(rssi: Int, hasInternet: Boolean): Boolean {
        // Scenario C: Weak signal AND no internet -> Fallback
        // Or just NO internet (since we prioritize Internet over WiFi)
        return !hasInternet
    }

    fun getProbationList(): Map<String, Long> {
        return probationList.toMap()
    }
}
