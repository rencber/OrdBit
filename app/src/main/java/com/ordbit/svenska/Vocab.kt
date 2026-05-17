package com.ordbit.svenska

import android.content.Context
import org.json.JSONObject

/** A single vocabulary entry. */
data class Word(
    val id: String,
    val section: String,
    val sv: String,
    val en: String,
    val pos: String,
    val exSv: String,
    val exEn: String
)

/** A group of words — either a CEFR level or a thematic category. */
data class Section(val id: String, val name: String, val category: Boolean)

/**
 * The bundled dictionary.
 *
 * The whole word list ships inside the APK as `assets/vocab.json`, so the app
 * never downloads anything and stays 100% offline. The file is loaded once at
 * startup (see MainActivity).
 *
 * The JSON can be regenerated at any size by `tools/build_vocab.py`, which
 * pulls the free Swedish Kelly-list and uses the Anthropic API to add an
 * English translation and an example sentence for every word. That generation
 * step runs once on a developer machine — the app itself never calls an API.
 */
object Vocab {

    var sections: List<Section> = emptyList()
        private set

    var all: List<Word> = emptyList()
        private set

    private var loaded = false

    /** Reads assets/vocab.json. Safe to call more than once. */
    fun load(context: Context) {
        if (loaded) return
        try {
            val text = context.assets.open("vocab.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val root = JSONObject(text)

            val secArr = root.getJSONArray("sections")
            sections = (0 until secArr.length()).map { i ->
                val o = secArr.getJSONObject(i)
                Section(o.getString("id"), o.getString("name"), o.optBoolean("category", false))
            }

            val wordArr = root.getJSONArray("words")
            val counters = HashMap<String, Int>()
            all = (0 until wordArr.length()).map { i ->
                val o = wordArr.getJSONObject(i)
                val sec = o.getString("section")
                val n = counters.getOrDefault(sec, 0)
                counters[sec] = n + 1
                Word(
                    id = "$sec-$n",
                    section = sec,
                    sv = o.getString("sv"),
                    en = o.getString("en"),
                    pos = o.optString("pos", ""),
                    exSv = o.optString("exSv", ""),
                    exEn = o.optString("exEn", "")
                )
            }
            loaded = true
        } catch (e: Exception) {
            // Tiny built-in fallback so the app never crashes on a bad file.
            sections = listOf(Section("A1", "Nyb\u00f6rjare", false))
            all = listOf(
                Word("A1-0", "A1", "hej", "hello", "interjektion", "Hej!", "Hi!")
            )
            loaded = true
        }
    }

    fun sectionName(id: String): String =
        if (id == "FAV") "Favoriter" else sections.firstOrNull { it.id == id }?.name ?: id

    fun isCategory(id: String): Boolean =
        sections.firstOrNull { it.id == id }?.category == true

    fun bySection(id: String): List<Word> = all.filter { it.section == id }
}
