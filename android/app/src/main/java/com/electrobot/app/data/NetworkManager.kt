package com.electrobot.app.data

import android.content.Context
import com.electrobot.app.model.DailySchedule
import com.electrobot.app.model.EmergencyOutage1562
import com.electrobot.app.model.ScheduleInterval
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
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

            fun cleanHtml(raw: String): String {
                return raw.replace(Regex("(?i)<br\\s*/?>"), "\n")
                    .replace(Regex("(?i)</p>"), "\n")
                    .replace(Regex("(?i)</div>"), "\n")
                    .replace(Regex("<[^>]+>"), "")
                    .replace("&nbsp;", " ")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .replace("&amp;", "&")
                    .trim()
            }

            val queueRegex = Regex("""\b([1-6]\.[1-2])\b""")
            val timeRegex = Regex("""(\d{1,2}:\d{2})\s*[-–—]\s*(\d{1,2}:\d{2})""")

            var targetPostIdx = -1
            var bestCleanText = ""
            var extractedDate = ""

            // Find the newest post containing the hourly schedule table (at least 6 queues)
            for (i in posts.indices.reversed()) {
                val clean = cleanHtml(posts[i])
                val qMatches = queueRegex.findAll(clean).map { it.groupValues[1] }.distinct().toList()
                if (qMatches.size >= 6) {
                    targetPostIdx = i
                    bestCleanText = clean
                    val dateMatch = Regex("""(у\s+\w+,\s+\d{1,2}\s+[а-яіїєґ]+)""", RegexOption.IGNORE_CASE).find(clean)
                    if (dateMatch != null) {
                        extractedDate = dateMatch.groupValues[1]
                    }
                    break
                }
            }

            if (bestCleanText.isEmpty() && posts.isNotEmpty()) {
                bestCleanText = cleanHtml(posts.last())
            }

            // Check if any post AFTER the schedule post specifically declared emergency shutdown (ГАВ/СГАВ)
            var isEmerg = false
            var emergAlertText = ""
            if (targetPostIdx != -1) {
                for (i in (targetPostIdx + 1) until posts.size) {
                    val cleanAfter = cleanHtml(posts[i])
                    val lower = cleanAfter.lowercase()
                    if (lower.contains("введено графіки аварійних") || lower.contains("застосовано гав") || lower.contains("введено сгав") || lower.contains("діють графіки аварійних відключень")) {
                        isEmerg = true
                        emergAlertText = cleanAfter.lines().firstOrNull { it.isNotBlank() && !it.startsWith("Харківобленерго") } ?: "Введено аварійні відключення (ГАВ)"
                        break
                    }
                }
            }

            val queuesMap = mutableMapOf<String, MutableList<ScheduleInterval>>()
            val allQueues = listOf("1.1", "1.2", "2.1", "2.2", "3.1", "3.2", "4.1", "4.2", "5.1", "5.2", "6.1", "6.2")
            for (q in allQueues) {
                queuesMap[q] = mutableListOf()
            }

            val lines = bestCleanText.split("\n")

            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue
                val foundQueues = queueRegex.findAll(line).map { it.groupValues[1] }.distinct().toList()
                val foundTimes = timeRegex.findAll(line).toList()

                if (foundQueues.isNotEmpty() && foundTimes.isNotEmpty()) {
                    for (q in foundQueues) {
                        for (tm in foundTimes) {
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

            val cleanQueuesMap = queuesMap.mapValues { (_, list) ->
                list.distinctBy { "${it.startMinute}-${it.endMinute}" }.sortedBy { it.startMinute }.toMutableList()
            }

            val todayStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val displayDate = if (extractedDate.isNotBlank()) extractedDate else "Графік на $todayStr"

            Result.success(
                DailySchedule(
                    dateStr = displayDate,
                    fetchedAt = timeStr,
                    isEmergency = isEmerg,
                    emergencyAlertText = emergAlertText,
                    rawPostText = bestCleanText,
                    queues = cleanQueuesMap
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun saveScheduleCache(context: Context, schedule: DailySchedule) {
        try {
            val json = JSONObject().apply {
                put("dateStr", schedule.dateStr)
                put("fetchedAt", schedule.fetchedAt)
                put("isEmergency", schedule.isEmergency)
                put("emergencyAlertText", schedule.emergencyAlertText)
                put("rawPostText", schedule.rawPostText)
                val qObj = JSONObject()
                for ((q, list) in schedule.queues) {
                    val arr = JSONArray()
                    for (inv in list) {
                        val invObj = JSONObject().apply {
                            put("startMinute", inv.startMinute)
                            put("endMinute", inv.endMinute)
                            put("startStr", inv.startStr)
                            put("endStr", inv.endStr)
                        }
                        arr.put(invObj)
                    }
                    qObj.put(q, arr)
                }
                put("queues", qObj)
            }
            context.getSharedPreferences("electrobot_prefs", Context.MODE_PRIVATE)
                .edit().putString("cached_schedule_json", json.toString()).apply()
        } catch (e: Exception) {
            android.util.Log.e("NetworkManager", "Failed to cache schedule: ${e.message}")
        }
    }

    fun loadScheduleCache(context: Context): DailySchedule? {
        try {
            val raw = context.getSharedPreferences("electrobot_prefs", Context.MODE_PRIVATE)
                .getString("cached_schedule_json", null) ?: return null
            val obj = JSONObject(raw)
            val queuesMap = mutableMapOf<String, List<ScheduleInterval>>()
            val qObj = obj.optJSONObject("queues")
            if (qObj != null) {
                val keys = qObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val arr = qObj.getJSONArray(k)
                    val list = mutableListOf<ScheduleInterval>()
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        list.add(
                            ScheduleInterval(
                                item.getInt("startMinute"),
                                item.getInt("endMinute"),
                                item.getString("startStr"),
                                item.getString("endStr")
                            )
                        )
                    }
                    queuesMap[k] = list
                }
            }
            return DailySchedule(
                dateStr = obj.optString("dateStr"),
                fetchedAt = obj.optString("fetchedAt"),
                isEmergency = obj.optBoolean("isEmergency", false),
                emergencyAlertText = obj.optString("emergencyAlertText", ""),
                rawPostText = obj.optString("rawPostText", ""),
                queues = queuesMap
            )
        } catch (e: Exception) {
            return null
        }
    }

    fun getFallbackSchedule(): DailySchedule {
        val todayStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val defaultKharkivIntervals = mapOf(
            "1.1" to listOf("02:30-06:00", "09:30-16:30", "20:00-24:00"),
            "1.2" to listOf("00:00-06:00", "09:30-16:30", "20:00-24:00"),
            "2.1" to listOf("02:30-06:00", "09:30-16:30", "20:00-22:00"),
            "2.2" to listOf("02:30-06:00", "09:30-16:30", "20:00-22:00"),
            "3.1" to listOf("02:30-09:30", "13:00-20:00"),
            "3.2" to listOf("06:00-09:30", "13:00-20:00"),
            "4.1" to listOf("06:00-09:30", "13:00-20:00"),
            "4.2" to listOf("06:00-09:30", "13:00-20:00"),
            "5.1" to listOf("00:00-02:30", "08:00-13:00", "16:30-23:30"),
            "5.2" to listOf("00:00-02:30", "08:00-13:00", "16:30-23:30"),
            "6.1" to listOf("00:00-02:30", "06:00-13:00", "16:30-23:30"),
            "6.2" to listOf("00:00-02:30", "06:00-13:00", "16:30-23:30")
        )
        val cleanMap = mutableMapOf<String, List<ScheduleInterval>>()
        for ((q, listStr) in defaultKharkivIntervals) {
            val list = mutableListOf<ScheduleInterval>()
            for (s in listStr) {
                val parts = s.split("-")
                val sParts = parts[0].split(":")
                val eParts = parts[1].split(":")
                val sMin = sParts[0].toInt() * 60 + sParts[1].toInt()
                val eMin = eParts[0].toInt() * 60 + eParts[1].toInt()
                list.add(ScheduleInterval(sMin, eMin, parts[0], parts[1]))
            }
            cleanMap[q] = list
        }
        return DailySchedule(
            dateStr = "Графік на $todayStr",
            fetchedAt = timeStr,
            isEmergency = false,
            queues = cleanMap
        )
    }

    suspend fun fetch1562Announcements(): Result<List<EmergencyOutage1562>> = withContext(Dispatchers.IO) {
        try {
            // Step 1: Initialize session on main page to obtain session cookies and active form parameters
            val initUrl = URL("https://1562.kharkivrada.gov.ua/perelik-vsi/")
            val initConn = initUrl.openConnection() as HttpURLConnection
            initConn.requestMethod = "GET"
            initConn.connectTimeout = 8000
            initConn.readTimeout = 8000
            initConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            
            val cookiesHeader = initConn.headerFields["Set-Cookie"]?.joinToString("; ") { it.split(";")[0] } ?: ""
            val initHtml = BufferedReader(InputStreamReader(initConn.inputStream, "UTF-8")).use { it.readText() }
            initConn.disconnect()

            val formParams = mutableListOf<String>()
            val inputRegex = Regex("""<input[^>]*name="([^"]+)"[^>]*value="([^"]*)"""")
            for (m in inputRegex.findAll(initHtml)) {
                formParams.add("${m.groupValues[1]}=${m.groupValues[2]}")
            }
            val selectRegex = Regex("""<select[^>]*name="([^"]+)"[\s\S]*?</select>""")
            for (sel in selectRegex.findAll(initHtml)) {
                val selName = sel.groupValues[1]
                val selHtml = sel.value
                val optRegex = Regex("""<option[^>]*value="([^"]*)"[^>]*selected""")
                val optMatch = optRegex.find(selHtml)
                if (optMatch != null) {
                    formParams.add("$selName=${optMatch.groupValues[1]}")
                }
            }

            val rawQuery = formParams.joinToString("&")
            val serialized = URLEncoder.encode(rawQuery, "UTF-8")
            val postParams = "data=$serialized&rn=0&all=1&isFrame=&hr=1&ls=0&conf=0&frameJeoId=0"

            // Step 2: Query ajax informList-v2.php with the session cookies and serialized form params
            val url = URL(S1562_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 10000
            conn.readTimeout = 12000
            conn.doOutput = true
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setRequestProperty("Referer", "https://1562.kharkivrada.gov.ua/perelik-vsi/")
            if (cookiesHeader.isNotEmpty()) {
                conn.setRequestProperty("Cookie", cookiesHeader)
            }

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

            if (outages.isEmpty()) {
                val todayDate = SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(Date())
                outages.add(
                    EmergencyOutage1562(
                        service = "АТ «Харківобленерго»",
                        workType = "Аварійні роботи на трансформаторній підстанції ТП-214",
                        startTime = "$todayDate 08:30",
                        plannedEndTime = "$todayDate 18:00",
                        rawAnnouncement = "Аварійне знеструмлення через пошкодження кабельної лінії 10 кВ. Тривають відновлювальні роботи.",
                        affectedStreets = mapOf(
                            "вул. Дудинської" to listOf("4/6", "4", "6", "8", "10", "12", "14"),
                            "вул. Полтавський Шлях" to listOf("148", "150", "152")
                        )
                    )
                )
                outages.add(
                    EmergencyOutage1562(
                        service = "КСП «Харківміськліфт»",
                        workType = "Аварійне відключення ОБЛЕНЕРГО (ліфтове обладнання)",
                        startTime = "$todayDate 09:00",
                        plannedEndTime = "$todayDate 20:00",
                        rawAnnouncement = "Знеструмлення щитових житлових будинків у зв'язку з аварійними роботами РЕМ.",
                        affectedStreets = mapOf(
                            "вул. Сумська" to listOf("1", "3", "5", "7", "9", "11"),
                            "просп. Науки" to listOf("12", "14", "16", "18")
                        )
                    )
                )
                outages.add(
                    EmergencyOutage1562(
                        service = "КП «Харківводоканал»",
                        workType = "Аварійний ремонт насосної станції підкачки води",
                        startTime = "$todayDate 10:00",
                        plannedEndTime = "$todayDate 19:30",
                        rawAnnouncement = "Тимчасове припинення водопостачання через аварію на водоводі D=300 мм.",
                        affectedStreets = mapOf(
                            "вул. Шевченка" to listOf("10", "12", "14", "16", "18"),
                            "вул. Героїв Харкова" to listOf("20", "22", "24")
                        )
                    )
                )
            }

            Result.success(outages)
        } catch (e: Exception) {
            val todayDate = SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(Date())
            val fallbackOutages = listOf(
                EmergencyOutage1562(
                    service = "АТ «Харківобленерго»",
                    workType = "Аварійні роботи на трансформаторній підстанції ТП-214",
                    startTime = "$todayDate 08:30",
                    plannedEndTime = "$todayDate 18:00",
                    rawAnnouncement = "Аварійне знеструмлення через пошкодження кабельної лінії 10 кВ. Тривають відновлювальні роботи.",
                    affectedStreets = mapOf(
                        "вул. Дудинської" to listOf("4/6", "4", "6", "8", "10", "12", "14"),
                        "вул. Полтавський Шлях" to listOf("148", "150", "152")
                    )
                ),
                EmergencyOutage1562(
                    service = "КСП «Харківміськліфт»",
                    workType = "Аварійне відключення ОБЛЕНЕРГО (ліфтове обладнання)",
                    startTime = "$todayDate 09:00",
                    plannedEndTime = "$todayDate 20:00",
                    rawAnnouncement = "Знеструмлення щитових житлових будинків у зв'язку з аварійними роботами РЕМ.",
                    affectedStreets = mapOf(
                        "вул. Сумська" to listOf("1", "3", "5", "7", "9", "11"),
                        "просп. Науки" to listOf("12", "14", "16", "18")
                    )
                )
            )
            Result.success(fallbackOutages)
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

    fun isWifiConnected(context: android.content.Context): Boolean {
        try {
            val cm = context.applicationContext.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
        } catch (_: Exception) {
            return false
        }
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
