package com.liyue.mctoolbox.map

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One finished flower farm search, kept so the parameters can be recalled later. */
internal data class FlowerHistoryEntry(
    val id: Long,
    val seedText: String,
    val versionLabel: String,
    val minX: Int,
    val minZ: Int,
    val maxX: Int,
    val maxZ: Int,
    val longSide: Int,
    val shortSide: Int,
    val orientation: Int,
    val flowerMargin: Int,
    val fastMode: Boolean,
    val requirePale: Boolean,
    val requireBirch: Boolean,
    val requireCherry: Boolean,
    val requireBasalt: Boolean,
    val hits: Int,
    val elapsedMs: Long,
    val createdAt: Long,
) {
    val orientationLabel: String
        get() = when (orientation) {
            1 -> "横向"
            2 -> "竖向"
            else -> "任意"
        }

    val windowLabel: String get() = if (longSide == shortSide) "${longSide}×$longSide" else "$longSide×$shortSide"

    fun filterLabels(): List<String> = buildList {
        if (requirePale) add("苍白花园")
        if (requireBirch) add("白桦森林")
        if (requireCherry) add("樱花林")
        if (requireBasalt) add("玄武岩")
        if (fastMode) add("快速")
    }

    fun summary(): String = buildString {
        append("窗口 $windowLabel · $orientationLabel")
        val filters = filterLabels()
        if (filters.isNotEmpty()) {
            append(" · ")
            append(filters.joinToString("、"))
        }
        append(" · ")
        append(if (hits > 0) "$hits 个结果" else "无结果")
    }
}

internal object FlowerHistoryStore {
    private const val PREFS = "liyue_flower_history"
    private const val KEY = "entries"
    private const val MAX_ENTRIES = 40

    fun load(context: Context): List<FlowerHistoryEntry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        return runCatching { parse(raw) }.getOrDefault(emptyList())
    }

    fun add(context: Context, entry: FlowerHistoryEntry): List<FlowerHistoryEntry> {
        val entries = (listOf(entry) + load(context))
            .distinctBy { it.seedText to it.rectKey() }
            .take(MAX_ENTRIES)
        save(context, entries)
        return entries
    }

    fun remove(context: Context, id: Long): List<FlowerHistoryEntry> {
        val entries = load(context).filterNot { it.id == id }
        save(context, entries)
        return entries
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    private fun save(context: Context, entries: List<FlowerHistoryEntry>) {
        val array = JSONArray()
        entries.forEach { array.put(encode(it)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, array.toString())
            .apply()
    }

    private fun encode(entry: FlowerHistoryEntry): JSONObject = JSONObject().apply {
        put("id", entry.id)
        put("seed", entry.seedText)
        put("version", entry.versionLabel)
        put("minX", entry.minX)
        put("minZ", entry.minZ)
        put("maxX", entry.maxX)
        put("maxZ", entry.maxZ)
        put("long", entry.longSide)
        put("short", entry.shortSide)
        put("orientation", entry.orientation)
        put("margin", entry.flowerMargin)
        put("fast", entry.fastMode)
        put("pale", entry.requirePale)
        put("birch", entry.requireBirch)
        put("cherry", entry.requireCherry)
        put("basalt", entry.requireBasalt)
        put("hits", entry.hits)
        put("elapsed", entry.elapsedMs)
        put("at", entry.createdAt)
    }

    private fun parse(raw: String): List<FlowerHistoryEntry> {
        val array = JSONArray(raw)
        val out = ArrayList<FlowerHistoryEntry>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            out.add(
                FlowerHistoryEntry(
                    id = item.optLong("id"),
                    seedText = item.optString("seed", "0"),
                    versionLabel = item.optString("version", ""),
                    minX = item.optInt("minX"),
                    minZ = item.optInt("minZ"),
                    maxX = item.optInt("maxX"),
                    maxZ = item.optInt("maxZ"),
                    longSide = item.optInt("long", 640),
                    shortSide = item.optInt("short", 320),
                    orientation = item.optInt("orientation"),
                    flowerMargin = item.optInt("margin"),
                    fastMode = item.optBoolean("fast", true),
                    requirePale = item.optBoolean("pale", true),
                    requireBirch = item.optBoolean("birch", true),
                    requireCherry = item.optBoolean("cherry", true),
                    requireBasalt = item.optBoolean("basalt", false),
                    hits = item.optInt("hits"),
                    elapsedMs = item.optLong("elapsed"),
                    createdAt = item.optLong("at"),
                ),
            )
        }
        return out
    }
}

internal fun FlowerHistoryEntry.rectKey(): String = "$minX,$minZ,$maxX,$maxZ,$longSide,$shortSide,$orientation,$flowerMargin"
