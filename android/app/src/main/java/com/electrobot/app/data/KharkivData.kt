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
}
