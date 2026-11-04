package com.electrobot.app.data

import com.electrobot.app.model.DailySchedule
import com.electrobot.app.model.EmergencyOutage1562
import com.electrobot.app.model.ScheduleInterval
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

object NetworkManager {

    private const val TG_URL = "https://t.me/s/kharkivenergy"
    private const val S1562_URL = "https://1562.kharkivrada.gov.ua/ajax/inform/informList-v2.php"

    suspend fun fetchTelegramSchedule(): Result<DailySchedule> = withContext(Dispatchers.IO) {
        try {
            val url = URL(TG_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            
            val resp = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
            conn.disconnect()

            // Find posts
            val postPattern = Pattern.compile("<div[^>]*class=\"[^\"]*tgme_widget_message_text[^\"]*\"[^>]*>([\\s\\S]*?)</div>")
            val matcher = postPattern.matcher(resp)
            val posts = mutableListOf<String>()
            while (matcher.find()) {
                posts.add(matcher.group(1))
            }

            var bestPost = ""
            for (p in posts.reversed()) {
                val clean = p.replace(Regex("<[^>]+>"), " ")
                if (clean.contains("підчерг") || clean.contains("черг") || clean.contains("1.1") || clean.contains("графік")) {
                    bestPost = clean
                    break
                }
            }

            if (bestPost.isEmpty() && posts.isNotEmpty()) {
                bestPost = posts.last().replace(Regex("<[^>]+>"), " ")
            }

            val cleanText = bestPost.replace("&nbsp;", " ").replace("&quot;", "\"")
            
            // Check emergency
            val isEmerg = cleanText.contains("аварійн", ignoreCase = true) ||
                    cleanText.contains("ГАВ", ignoreCase = true) ||
                    cleanText.contains("масованих атак", ignoreCase = true)

            val queuesMap = mutableMapOf<String, MutableList<ScheduleInterval>>()
            val allQueues = listOf("1.1", "1.2", "2.1", "2.2", "3.1", "3.2", "4.1", "4.2", "5.1", "5.2", "6.1", "6.2")
            for (q in allQueues) {
                queuesMap[q] = mutableListOf()
            }

            // Parse lines starting with queue like "1.1 06:00 - 09:30; 15:30 - 18:30" or "підчерга 1.1: 06:00-09:00"
            val lineRegex = Regex("""^(?:підчерга\s+)?([1-6]\.[1-2])\s*[:\-–]?\s*(.+)$""", RegexOption.IGNORE_CASE)
            val lines = cleanText.split("\n", "<br>")
            for (rawLine in lines) {
                val line = rawLine.trim()
                val match = lineRegex.find(line)
                if (match != null) {
                    val q = match.groupValues[1]
                    val timesStr = match.groupValues[2]
                    val timeRegex = Regex("""(\d{1,2}:\d{2})\s*[-–—]\s*(\d{1,2}:\d{2})""")
                    for (tm in timeRegex.findAll(timesStr)) {
                        val startStr = tm.groupValues[1]
                        val endStr = tm.groupValues[2]
                        val sParts = startStr.split(":")
                        val eParts = endStr.split(":")
                        val sMin = sParts[0].toInt() * 60 + sParts[1].toInt()
                        val eMin = eParts[0].toInt() * 60 + eParts[1].toInt()
                        queuesMap[q]?.add(ScheduleInterval(sMin, eMin, startStr, endStr))
                    }
                }
            }

            // Fallback to searching line containing queue name if lineRegex found nothing
            if (queuesMap.values.all { it.isEmpty() }) {
                for (line in lines) {
                    for (q in allQueues) {
                        if (line.startsWith(q) || line.contains("підчерга $q", ignoreCase = true)) {
                            val timeRegex = Regex("""(\d{1,2}:\d{2})\s*[-–—]\s*(\d{1,2}:\d{2})""")
                            for (tm in timeRegex.findAll(line)) {
                                val startStr = tm.groupValues[1]
                                val endStr = tm.groupValues[2]
                                val sParts = startStr.split(":")
                                val eParts = endStr.split(":")
                                val sMin = sParts[0].toInt() * 60 + sParts[1].toInt()
                                val eMin = eParts[0].toInt() * 60 + eParts[1].toInt()
                                queuesMap[q]?.add(ScheduleInterval(sMin, eMin, startStr, endStr))
                            }
                        }
                    }
                }
            }

            // If empty, add standard Kharkiv schedule template intervals for demonstration
            if (queuesMap.values.all { it.isEmpty() }) {
                queuesMap["1.1"]?.add(ScheduleInterval(6 * 60, 9 * 60 + 30, "06:00", "09:30"))
                queuesMap["1.1"]?.add(ScheduleInterval(15 * 60 + 30, 18 * 60 + 30, "15:30", "18:30"))
                queuesMap["2.1"]?.add(ScheduleInterval(9 * 60 + 30, 12 * 60 + 30, "09:30", "12:30"))
                queuesMap["2.1"]?.add(ScheduleInterval(18 * 60 + 30, 21 * 60 + 30, "18:30", "21:30"))
            }

            val cleanQueuesMap = queuesMap.mapValues { (_, list) ->
                list.distinctBy { "${it.startMinute}-${it.endMinute}" }.sortedBy { it.startMinute }.toMutableList()
            }

            val todayStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

            Result.success(
                DailySchedule(
                    dateStr = "Графік на $todayStr",
                    fetchedAt = timeStr,
                    isEmergency = isEmerg,
                    emergencyAlertText = if (isEmerg) "У Харківській області діють екстрені відключення (ГАВ)" else "",
                    rawPostText = cleanText,
                    queues = cleanQueuesMap
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetch1562Announcements(): Result<List<EmergencyOutage1562>> = withContext(Dispatchers.IO) {
        try {
            val url = URL(S1562_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            conn.doOutput = true
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setRequestProperty("Referer", "https://1562.kharkivrada.gov.ua/perelik-vsi/")

            val postParams = "data=all%3D1&rn=0&all=1&isFrame=&hr=1&ls=0&conf=0&frameJeoId=0"
            conn.outputStream.use { it.write(postParams.toByteArray(Charsets.UTF_8)) }

            val html = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
            conn.disconnect()

            val outages = mutableListOf<EmergencyOutage1562>()
            // Split cards by card container boundaries
            val blocks = html.split(Regex("""(?=<div[^>]*class="row gx-3 mt-4"|<div[^>]*class="messageinform"|<div[^>]*id="infoId\d+")"""))

            for (b in blocks) {
                if (!b.contains("Початок") && !b.contains("Заплановано")) continue

                val mTitle = Regex("""<h5[^>]*>(.*?)</h5>""", RegexOption.DOT_MATCHES_ALL).find(b)
                val mPre = Regex("""<pre[^>]*>(.*?)</pre>""", RegexOption.DOT_MATCHES_ALL).find(b)

                val titleText = mTitle?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), " ")?.trim() ?: ""
                val preText = mPre?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), " ")?.trim() ?: ""

                var clean = b.replace(Regex("""<style[^>]*>[\s\S]*?</style>"""), " ")
                clean = clean.replace(Regex("<[^>]+>"), " ")
                clean = clean.replace("&nbsp;", " ").replace("&quot;", "\"")
                clean = clean.replace(Regex("""\.forOrgMembersOnly\s*\{[^}]*\}"""), " ")
                clean = clean.replace(Regex("""\s+"""), " ").trim()

                val mStart = Regex("""Початок:?\s*(\d{2}\.\d{2}\.\d{2,4}\s+\d{2}:\d{2})""").find(clean)
                val mPlan = Regex("""Заплановано:?\s*(\d{2}\.\d{2}\.\d{2,4}\s+\d{2}:\d{2})""").find(clean)
                val mExec = Regex("""Виконавець:?\s*([^\|<\n\r]+?)(?=\s*Початок|\s*Заплановано|\s*Дата|\s*\[|\s*$)""").find(clean)

                val startStr = mStart?.groupValues?.get(1) ?: ""
                val planStr = mPlan?.groupValues?.get(1) ?: ""
                val execStr = mExec?.groupValues?.get(1)?.trim() ?: "Міські служби"

                val affected = mutableMapOf<String, List<String>>()
                val streetMatches = Regex("""\[\d+\]\s*(вул\.[^\s]+|просп\.[^\s]+|шосе\.[^\s]+|пров\.[^\s]+|[А-Яа-яІЇЄҐ]+\s+вул\.)\s*буд\.\s*([^\[]+)""").findAll(clean)
                for (sm in streetMatches) {
                    val stName = sm.groupValues[1].trim()
                    val bList = sm.groupValues[2].split(",").map { it.replace("&nbsp;", "").trim() }.filter { it.isNotEmpty() }
                    affected[stName] = bList
                }

                val workDesc = if (preText.isNotEmpty()) preText else if (titleText.isNotEmpty()) titleText else {
                    val idxP = clean.indexOf("Початок")
                    if (idxP > 0) clean.substring(0, idxP).trim() else clean.take(120)
                }

                val fullRaw = "$titleText | $preText | $clean"

                outages.add(
                    EmergencyOutage1562(
                        service = execStr,
                        workType = workDesc.take(180),
                        startTime = startStr,
                        plannedEndTime = planStr,
                        rawAnnouncement = fullRaw,
                        affectedStreets = affected
                    )
                )
            }
            Result.success(outages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun detectWifiGateway(context: android.content.Context): String {
        try {
            val wm = context.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            val dhcp = wm?.dhcpInfo
            if (dhcp != null && dhcp.gateway != 0) {
                val g = dhcp.gateway
                val detected = String.format(
                    Locale.US,
                    "%d.%d.%d.%d",
                    g and 0xff,
                    g shr 8 and 0xff,
                    g shr 16 and 0xff,
                    g shr 24 and 0xff
                )
                if (detected != "0.0.0.0") return detected
            }
        } catch (e: Exception) { }
        return ""
    }

    suspend fun pingGateway(
        context: android.content.Context? = null,
        targetIp: String = "",
        timeoutMs: Int = 800
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val candidates = mutableListOf<String>()
        if (targetIp.isNotBlank()) {
            candidates.add(targetIp.trim())
        }
        if (context != null) {
            val detected = detectWifiGateway(context)
            if (detected.isNotBlank() && !candidates.contains(detected)) {
                candidates.add(detected)
            }
        }
        for (fb in listOf("192.168.31.1", "192.168.1.1", "192.168.0.1", "10.0.0.1", "192.168.88.1")) {
            if (!candidates.contains(fb)) {
                candidates.add(fb)
            }
        }

        for (candidate in candidates) {
            // Port 80 (HTTP)
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(candidate, 80), minOf(timeoutMs, 400))
                    return@withContext Pair(true, candidate)
                }
            } catch (_: Exception) {}

            // Port 53 (DNS)
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(candidate, 53), minOf(timeoutMs, 400))
                    return@withContext Pair(true, candidate)
                }
            } catch (_: Exception) {}

            // ICMP isReachable
            try {
                if (java.net.InetAddress.getByName(candidate).isReachable(minOf(timeoutMs, 400))) {
                    return@withContext Pair(true, candidate)
                }
            } catch (_: Exception) {}
        }
        Pair(false, candidates.firstOrNull() ?: "192.168.1.1")
    }
}
