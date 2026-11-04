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

    fun calculateChargeWindow(queueName: String, schedule: DailySchedule?, nowCal: Calendar): Pair<String, String> {
        if (schedule == null) return Pair("⚡ Завантаження розкладу...", "Очікуємо дані Харківобленерго")
        val curMinutes = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)
        val intervals = schedule.queues[queueName] ?: emptyList()
        if (intervals.isEmpty()) return Pair("⚡ Без обмежень", "Сьогодні планові відключення для вашої черги відсутні")

        val sortedInvs = intervals.sortedBy { it.startMinute }
        val activeOutage = sortedInvs.firstOrNull { curMinutes in it.startMinute until it.endMinute }

        if (activeOutage != null) {
            // Power is OFF right now. When is the next ON window?
            val onStart = activeOutage.endMinute
            val nextOutageAfter = sortedInvs.firstOrNull { it.startMinute > onStart }
            val onEnd = nextOutageAfter?.startMinute ?: 1440
            val onDurationMins = (onEnd - onStart).coerceAtLeast(0)
            val onH = onDurationMins / 60
            val onM = onDurationMins % 60
            val durStr = if (onH > 0 && onM > 0) "${onH} год ${onM} хв" else if (onH > 0) "${onH} год" else "${onM} хв"
            val title = "⏳ Наступне вікно живлення: з ${activeOutage.endStr} до ${nextOutageAfter?.startStr ?: "24:00"}"
            val advice = if (onDurationMins >= 120) {
                "🟢 Тривалість $durStr — станція EcoFlow/Bluetti встигне повністю зарядитися (~1.5–2 год)."
            } else if (onDurationMins >= 60) {
                "🟡 Тривалість $durStr — станція встигне набрати ~60–80% заряду."
            } else {
                "⚠️ Коротке вікно ($durStr) — підключіть станцію в режимі турбо-зарядки."
            }
            return Pair(title, advice)
        }

        // Power is ON right now
        val nextOutage = sortedInvs.firstOrNull { it.startMinute > curMinutes }
        return if (nextOutage != null) {
            val avail = nextOutage.startMinute - curMinutes
            val h = avail / 60
            val m = avail % 60
            val durStr = if (h > 0 && m > 0) "${h} год ${m} хв" else if (h > 0) "${h} год" else "${m} хв"
            val title = "⚡ Вікно живлення: $durStr (до ${nextOutage.startStr})"
            val advice = if (avail >= 150) {
                "🟢 Достатньо часу для 100% заряду станцій EcoFlow / Bluetti (~1.5–2 год) та павербанків."
            } else if (avail >= 60) {
                "🟡 Помірний час: зарядна станція встигне набрати ~60–80% ємності."
            } else {
                "⚠️ Залишилось менше години ($durStr): перевірте рівень заряду станції та гаджетів."
            }
            Pair(title, advice)
        } else {
            val toMidnight = 1440 - curMinutes
            val h = toMidnight / 60
            val title = "⚡ Вікно живлення: до кінця доби (~${h} год)"
            val advice = "🟢 Стабільне живлення: планових відключень до півночі більше не передбачено."
            Pair(title, advice)
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
                chargeWindowAdvice = "Живлення відключено аварійно службою 1562. Економте заряд станції.",
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
                isEmergency = true,
                chargeWindowAdvice = "Погодинний розклад не діє через розпорядження Укренерго. Використовуйте енергію ощадливо."
            )
        }

        // 3. Check Scheduled Outage
        if (schedule != null && queueName.isNotEmpty()) {
            val intervals = schedule.queues[queueName] ?: emptyList()
            val activeOutage = intervals.firstOrNull { curMinutes in it.startMinute until it.endMinute }
            val (chargeWinStr, chargeWinAdvice) = calculateChargeWindow(queueName, schedule, now)

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
                    chargeWindowStr = chargeWinStr,
                    isEmergency = false,
                    chargeWindowAdvice = chargeWinAdvice
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
                    chargeWindowStr = chargeWinStr,
                    isEmergency = false,
                    chargeWindowAdvice = chargeWinAdvice
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
            isEmergency = false,
            chargeWindowAdvice = ""
        )
    }
}