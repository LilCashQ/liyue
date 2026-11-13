package com.liyue.mctoolbox.map

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One seed the user actually generated, kept in a small MRU list. */
internal data class SeedHistoryEntry(
    val seedText: String,
    val versionLabel: String,
    val usedAt: Long,
) {
    /**
     * Two entries are the same when the text resolves to the same world seed on
     * the same version; a bare number and its signed form share a slot.
     */
    fun identity(): String = "${com.liyue.mctoolbox.seedgen.SeedEngine.parseJavaSeed(seedText)}|$versionLabel"
}

/** Seed input history, persisted as a JSON array in SharedPreferences. */
internal object SeedHistoryStore {
    private const val PREFS = "liyue_seed_history"
    private const val KEY = "entries"
    private const val MAX_ENTRIES = 30

    fun load(context: Context): List<SeedHistoryEntry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        return runCatching { parse(raw) }.getOrDefault(emptyList())
    }

    fun add(context: Context, seedText: String, versionLabel: String): List<SeedHistoryEntry> {
        val trimmed = seedText.trim()
        if (trimmed.isEmpty()) return load(context)
        val entry = SeedHistoryEntry(trimmed, versionLabel, System.currentTimeMillis())
        val entries = (listOf(entry) + load(context))
            .distinctBy { it.identity() }
            .take(MAX_ENTRIES)
        save(context, entries)
        return entries
    }

    fun remove(context: Context, identity: String): List<SeedHistoryEntry> {
        val entries = load(context).filterNot { it.identity() == identity }
        save(context, entries)
        return entries
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    private fun parse(raw: String): List<SeedHistoryEntry> {
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val seed = item.optString("seed").takeIf { it.isNotEmpty() } ?: continue
                add(
                    SeedHistoryEntry(
                        seedText = seed,
                        versionLabel = item.optString("version"),
                        usedAt = item.optLong("at"),
                    ),
                )
            }
        }
    }

    private fun save(context: Context, entries: List<SeedHistoryEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("seed", entry.seedText)
                    put("version", entry.versionLabel)
                    put("at", entry.usedAt)
                },
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, array.toString())
            .apply()
    }
}
