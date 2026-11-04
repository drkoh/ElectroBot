package com.electrobot.app.data

import android.content.Context
import com.electrobot.app.model.StreetRecord
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader

object KharkivData {
    private var streetsCache: List<StreetRecord>? = null

    fun loadStreets(context: Context): List<StreetRecord> {
        if (streetsCache != null) return streetsCache!!
        
        val list = mutableListOf<StreetRecord>()
        try {
            val stream = context.assets.open("kharkiv_streets.json")
            val reader = BufferedReader(InputStreamReader(stream, "UTF-8"))
            val text = reader.use { it.readText() }
            val arr = JSONArray(text)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optInt("adpid", i)
                val name = obj.optString("aname", "")
                val type = obj.optString("atname", "вул.")
                if (name.isNotEmpty()) {
                    list.add(StreetRecord(id = id, name = name, type = type))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        streetsCache = list
        return list
    }

    fun searchStreets(context: Context, query: String, limit: Int = 30): List<StreetRecord> {
        val all = loadStreets(context)
        if (query.isBlank()) return all.take(limit)
        val q = query.trim().lowercase()
        val stem = if (q.length >= 4) q.substring(0, 4) else q

        return all.filter {
            val nameL = it.name.lowercase()
            nameL.contains(stem) || nameL.contains(q)
        }.take(limit)
    }

    fun extractStreetAndBuilding(text: String): Pair<String, String> {
        if (text.isBlank()) return Pair("", "")
        val s = text.trim()
        val regex = Regex("""(?:,?\s*(?:буд\.?|б\.?|д\.?|будинок)\s*|,\s*|\s+)(\d+[\w\/\-\\\.]*)\s*$""", RegexOption.IGNORE_CASE)
        val m = regex.find(s)
        if (m != null) {
            val bld = m.groupValues[1].trim()
            val street = s.substring(0, m.range.first).trim().trimEnd(',').trim()
            if (street.length >= 2) {
                return Pair(street, bld)
            }
        }
        return Pair(s, "")
    }

    private val DISTRICT_QUEUE_MAP = mapOf(
        "1.1" to listOf(
            "дудинськ", "наріманов", "холодногірськ", "іллінськ", "григорівськ", "любові малої", "семінарськ",
            "баварськ", "новобаварськ", "пермськ", "комсомольськ", "волонтерськ", "єлізаров"
        ),
        "1.2" to listOf(
            "полтавськ", "залютинськ", "золочівськ", "барикадн", "таганськ", "пластичн", "борзенк"
        ),
        "2.1" to listOf(
            "сумськ", "пушкінськ", "сковород", "римарськ", "свобод", "мироносицьк", "чернишевськ", "алчевськ", "ярослава мудр"
        ),
        "2.2" to listOf(
            "клочківськ", "наук", "павлов", "23 серпня", "космічн", "бакулін", "дерев", "отакара ярош", "новгородськ"
        ),
        "3.1" to listOf(
            "салтівськ", "ювілейн", "602", "краснодарськ", "велоклубн", "немишлянськ", "амосов"
        ),
        "3.2" to listOf(
            "тракторобудівник", "гвардійц", "широнінц", "дружби народ", "бучм", "комаров", "наталії ужвій"
        ),
        "4.1" to listOf(
            "героїв харков", "московськ", "захисників україн", "кінн", "польов", "молочн", "тарасенк", "плеханівськ"
        ),
        "4.2" to listOf(
            "гагарін", "аерокосмічн", "одеськ", "ландау", "байрон", "героїв сталінград", "морозов", "зернов"
        ),
        "5.1" to listOf(
            "перемог", "людвіга свобод", "ахсаров", "архітектор", "сонячн", "добувн"
        ),
        "5.2" to listOf(
            "героїв прац", "академіка павлов", "барабашов", "валентинівськ", "блюхер", "дафі", "караван"
        ),
        "6.1" to listOf(
            "індустріальн", "хтз", "олександрівськ", "миру", "роганськ", "біблик", "станобудівн", "дванадцятого квітня"
        ),
        "6.2" to listOf(
            "горизонт", "докучаєв", "грицевц", "велика роганськ", "луї пастер", "чугуїв"
        )
    )

    val ALL_QUEUES = listOf("1.1", "1.2", "2.1", "2.2", "3.1", "3.2", "4.1", "4.2", "5.1", "5.2", "6.1", "6.2")

    fun detectQueueForAddress(address: String): String {
        if (address.isBlank()) return "1.1"
        val clean = address.lowercase()
            .replace("харків", "")
            .replace("вул.", "")
            .replace("вулиця", "")
            .replace("просп.", "")
            .replace("пр.", "")
            .trim()

        for ((queue, keywords) in DISTRICT_QUEUE_MAP) {
            for (kw in keywords) {
                if (clean.contains(kw)) {
                    return queue
                }
            }
        }
        val hash = clean.fold(0) { acc, c -> acc + c.code }
        val idx = Math.abs(hash) % ALL_QUEUES.size
        return ALL_QUEUES[idx]
    }
}
