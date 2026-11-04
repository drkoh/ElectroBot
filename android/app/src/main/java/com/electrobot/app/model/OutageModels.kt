package com.electrobot.app.model

enum class PowerStatus {
    ON,
    OFF,
    SWITCHING
}

data class ScheduleInterval(
    val startMinute: Int,
    val endMinute: Int,
    val startStr: String,
    val endStr: String
)

data class DailySchedule(
    val dateStr: String = "",
    val fetchedAt: String = "",
    val isEmergency: Boolean = false,
    val emergencyAlertText: String = "",
    val rawPostText: String = "",
    val queues: Map<String, List<ScheduleInterval>> = emptyMap()
)

data class EmergencyOutage1562(
    val service: String,
    val workType: String,
    val startTime: String,
    val plannedEndTime: String,
    val modifiedTime: String = "",
    val rawAnnouncement: String = "",
    val affectedStreets: Map<String, List<String>> = emptyMap()
) {
    fun matchesStreet(streetQuery: String): Boolean {
        if (streetQuery.isBlank()) return false
        val qNorm = streetQuery.lowercase().replace("вул.", "").replace("вулиця", "").replace("просп.", "").replace("пр.", "").trim()
        val stem = if (qNorm.length >= 4) qNorm.substring(0, 4) else qNorm
        for ((stName, _) in affectedStreets) {
            val sNorm = stName.lowercase().replace("вул.", "").replace("вулиця", "").trim()
            if (sNorm.contains(stem) || qNorm.contains(sNorm) || sNorm.contains(qNorm)) {
                return true
            }
        }
        return false
    }

    fun hasBuilding(building: String, bList: List<String>): Boolean {
        if (building.isBlank()) return false
        val bClean = building.trim().lowercase().replace("буд.", "").replace("-", "").trim()
        for (b in bList) {
            val candClean = b.lowercase().replace("-", "").trim()
            if (candClean == bClean || (bClean.contains("/") && candClean in bClean.split("/"))) {
                return true
            }
        }
        return false
    }

    fun matchesAddress(streetQuery: String, building: String = ""): Boolean {
        val qNorm = streetQuery.lowercase().replace("вул.", "").replace("вулиця", "").replace("просп.", "").replace("пр.", "").trim()
        val stem = if (qNorm.length >= 4) qNorm.substring(0, 4) else qNorm
        
        for ((stName, bList) in affectedStreets) {
            val sNorm = stName.lowercase().replace("вул.", "").replace("вулиця", "").trim()
            if (sNorm.contains(stem) || qNorm.contains(sNorm) || sNorm.contains(qNorm)) {
                if (building.isEmpty()) return true
                if (hasBuilding(building, bList)) return true
            }
        }
        return false
    }
}

data class UnifiedStatus(
    val isOutage: Boolean,
    val stateType: String, // "EMERGENCY_1562", "EMERGENCY_GAV", "SCHEDULED_OFF", "SCHEDULED_ON", "NO_DATA"
    val badgeText: String,
    val badgeTheme: String, // "danger", "warning", "success", "neutral"
    val headline: String,
    val details: String,
    val subtext: String,
    val targetTimeStr: String,
    val targetSecondsLeft: Int,
    val countdownHms: String,
    val chargeWindowStr: String,
    val isEmergency: Boolean,
    val outage1562: EmergencyOutage1562? = null
)

data class FavoriteAddress(
    val id: String,
    val title: String,
    val address: String,
    val queue: String
)

data class StreetRecord(
    val id: Int,
    val name: String,
    val type: String
)
