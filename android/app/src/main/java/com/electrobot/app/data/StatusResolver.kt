package com.electrobot.app.data

import com.electrobot.app.model.*
import java.text.SimpleDateFormat
import java.util.*

object StatusResolver {

    fun formatSecondsToHms(totalSec: Int): String {
        if (totalSec <= 0) return "00:00:00"
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }

    fun calculateChargeWindow(queueName: String, schedule: DailySchedule?, nowCal: Calendar): String {
        if (schedule == null) return "⚡ Графік завантажується..."
        val curMinutes = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)
        val intervals = schedule.queues[queueName] ?: emptyList()
        if (intervals.isEmpty()) return "⚡ Без обмежень"

        val sortedInvs = intervals.sortedBy { it.startMinute }
        for (inv in sortedInvs) {
            if (curMinutes in inv.startMinute until inv.endMinute) {
                return "❌ Світла немає — зарядка неможлива"
            }
        }

        val nextOutage = sortedInvs.firstOrNull { it.startMinute > curMinutes }
        return if (nextOutage != null) {
            val avail = nextOutage.startMinute - curMinutes
            val h = avail / 60
            val m = avail % 60
            if (h > 0) "⚡ Вікно зарядки: ~${h} год ${m} хв (до ${nextOutage.startStr})"
            else "⚠️ Залишилось: ~${m} хв (до ${nextOutage.startStr})"
        } else {
            val toMidnight = 1440 - curMinutes
            "⚡ Вікно зарядки: до кінця доби (~${toMidnight / 60} год)"
        }
    }

    fun resolve(
        addressQuery: String,
        queueName: String,
        schedule: DailySchedule?,
        outages1562: List<EmergencyOutage1562>,
        currentSecondsEpoch: Long = System.currentTimeMillis() / 1000
    ): UnifiedStatus {
        val now = Calendar.getInstance().apply { timeInMillis = currentSecondsEpoch * 1000 }
        val curMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val curSeconds = curMinutes * 60 + now.get(Calendar.SECOND)

        val (street, building) = KharkivData.extractStreetAndBuilding(addressQuery)

        // 1. Check 1562
        val match1562 = if (street.isNotEmpty()) {
            outages1562.firstOrNull { it.matchesAddress(street, building) }
        } else null

        if (match1562 != null) {
            var targetSec = 0
            var targetDisplay = match1562.plannedEndTime
            try {
                val parts = match1562.plannedEndTime.split(" ")
                if (parts.size >= 2) {
                    val tParts = parts[1].split(":")
                    val targetHour = tParts[0].toInt()
                    val targetMin = tParts[1].toInt()
                    val targetTotalSec = (targetHour * 60 + targetMin) * 60
                    if (targetTotalSec > curSeconds) {
                        targetSec = targetTotalSec - curSeconds
                    }
                    targetDisplay = String.format(Locale.US, "%02d:%02d", targetHour, targetMin)
                }
            } catch (e: Exception) {
                targetSec = 0
            }

            return UnifiedStatus(
                isOutage = true,
                stateType = "EMERGENCY_1562",
                badgeText = "🚨 АВАРІЯ 1562",
                badgeTheme = "danger",
                headline = "Аварійне знеструмлення (1562)",
                details = "Заявка АСДС 1562: ${match1562.workType}",
                subtext = "Вулиця: $street ${if (building.isNotEmpty()) "буд. $building" else ""} | ${match1562.service}",
                targetTimeStr = targetDisplay,
                targetSecondsLeft = targetSec,
                countdownHms = formatSecondsToHms(targetSec),
                chargeWindowStr = "❌ Тривають аварійні ремонтні роботи",
                isEmergency = true,
                outage1562 = match1562
            )
        }

        // 2. Check Telegram GAV
        if (schedule != null && schedule.isEmergency) {
            return UnifiedStatus(
                isOutage = true,
                stateType = "EMERGENCY_GAV",
                badgeText = "🚨 ЕКСТРЕНІ ВІДКЛЮЧЕННЯ (ГАВ)",
                badgeTheme = "danger",
                headline = "Діють графіки аварійних відключень",
                details = schedule.emergencyAlertText.ifEmpty { "Введено спеціальні графіки аварійних відключень НЕК «Укренерго»" },
                subtext = "Плановий погодинний розклад тимчасово не діє.",
                targetTimeStr = "До розпорядження",
                targetSecondsLeft = 0,
                countdownHms = "--:--:--",
                chargeWindowStr = "⚠️ Режим ГАВ: подача живлення нестабільна",
                isEmergency = true
            )
        }

        // 3. Check Scheduled Outage
        if (schedule != null && queueName.isNotEmpty()) {
            val intervals = schedule.queues[queueName] ?: emptyList()
            val activeOutage = intervals.firstOrNull { curMinutes in it.startMinute until it.endMinute }
            val chargeWin = calculateChargeWindow(queueName, schedule, now)

            return if (activeOutage != null) {
                val targetTotalSec = activeOutage.endMinute * 60
                val diffSec = (targetTotalSec - curSeconds).coerceAtLeast(0)
                UnifiedStatus(
                    isOutage = true,
                    stateType = "SCHEDULED_OFF",
                    badgeText = "🔴 ВІДКЛЮЧЕННЯ ЗА ГРАФІКОМ",
                    badgeTheme = "danger",
                    headline = "Знеструмлено (Черга $queueName)",
                    details = "Діє інтервал відключення: ${activeOutage.startStr} - ${activeOutage.endStr}",
                    subtext = "Очікуване увімкнення о ${activeOutage.endStr}",
                    targetTimeStr = activeOutage.endStr,
                    targetSecondsLeft = diffSec,
                    countdownHms = formatSecondsToHms(diffSec),
                    chargeWindowStr = chargeWin,
                    isEmergency = false
                )
            } else {
                val nextOutage = intervals.sortedBy { it.startMinute }.firstOrNull { it.startMinute > curMinutes }
                val targetTotalSec = if (nextOutage != null) nextOutage.startMinute * 60 else 1440 * 60
                val diffSec = (targetTotalSec - curSeconds).coerceAtLeast(0)
                val targetDisp = nextOutage?.startStr ?: "24:00"

                UnifiedStatus(
                    isOutage = false,
                    stateType = "SCHEDULED_ON",
                    badgeText = "🟢 СВІТЛО Є ЗА ГРАФІКОМ",
                    badgeTheme = "success",
                    headline = "Світло є (Черга $queueName)",
                    details = if (nextOutage != null) "Наступне відключення: ${nextOutage.startStr} - ${nextOutage.endStr}" else "До кінця доби без планових відключень",
                    subtext = if (nextOutage != null) "Вимкнення о $targetDisp" else "До кінця доби",
                    targetTimeStr = targetDisp,
                    targetSecondsLeft = diffSec,
                    countdownHms = formatSecondsToHms(diffSec),
                    chargeWindowStr = chargeWin,
                    isEmergency = false
                )
            }
        }

        return UnifiedStatus(
            isOutage = false,
            stateType = "NO_DATA",
            badgeText = "ℹ️ ОЧІКУВАННЯ ДАНИХ",
            badgeTheme = "neutral",
            headline = "Завантаження розкладу...",
            details = "Опитування серверів Харківобленерго та 1562...",
            subtext = "",
            targetTimeStr = "--:--",
            targetSecondsLeft = 0,
            countdownHms = "--:--:--",
            chargeWindowStr = "",
            isEmergency = false
        )
    }
}
