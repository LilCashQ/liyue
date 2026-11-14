package com.liyue.mctoolbox.map

import android.content.Context
import org.json.JSONObject

/**
 * Chinese names for the things the seed engine hands back in English: loot item
 * ids, biome names and structure piece codes. The table is a trimmed copy of the
 * official `zh_cn` language file, shipped as an asset so lookups stay offline.
 */
internal object GameNames {
    private var loaded = false
    private val table = HashMap<String, String>(2048)

    fun ensure(context: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            val text = context.assets.open("game_zh.json").bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            json.keys().forEach { key ->
                val value = json.optString(key)
                if (value.isNotEmpty()) table[key] = value
            }
        }
    }

    /** `minecraft:golden_apple` → 金苹果 */
    fun item(id: String): String {
        val key = id.substringAfter(':')
        table[key]?.let { return it }
        return humanize(key)
    }

    /** cubiomes biome name (`ocean`, `flower_forest`) → 海洋 */
    fun biome(rawName: String): String {
        val key = "biome:" + rawName.lowercase().replace(' ', '_')
        table[key]?.let { return it }
        return humanize(rawName)
    }

    /** structure piece code (`SHPR`, `base_floor`) → 末地传送门房 */
    fun piece(code: String): String = PIECES[code] ?: humanize(code)

    private fun humanize(raw: String): String =
        raw.replace(':', ' ').split('_', ' ').filter { it.isNotEmpty() }
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercaseChar() } }

    private val PIECES = mapOf(
        // 要塞
        "SHS" to "直走廊",
        "SHPH" to "牢房大厅",
        "SHLT" to "左转路口",
        "SHRT" to "右转路口",
        "SHRC" to "十字大厅",
        "SHSD" to "下楼梯",
        "SHSSD" to "直下楼梯",
        "SH5C" to "五岔大厅",
        "SHCC" to "带箱走廊",
        "SHLi" to "图书馆",
        "SHPR" to "末地传送门房",
        "SHFC" to "填充走廊",
        // 下界要塞
        "NeStart" to "要塞起点",
        "NeBS" to "直桥",
        "NeBCr" to "桥十字路口",
        "NeRC" to "带顶十字桥",
        "NeSR" to "桥楼梯",
        "NeMT" to "桥刷怪笼",
        "NeCE" to "桥入口",
        "NeSC" to "直走廊",
        "NeSCSC" to "走廊十字",
        "NeSCRT" to "走廊右转",
        "NeSCLT" to "走廊左转",
        "NeCCS" to "走廊楼梯",
        "NeCTB" to "T 形走廊",
        "NeCSR" to "地狱疣走廊",
        "NeBEF" to "要塞尽头",
        // 末地城
        "base_floor" to "底层",
        "base_roof" to "底层屋顶",
        "second_roof" to "二层屋顶",
        "third_roof" to "三层屋顶",
        "tower_base" to "塔基",
        "tower_floor" to "塔层",
        "tower_piece" to "塔身",
        "tower_top" to "塔顶",
        "fat_tower_base" to "胖塔基座",
        "fat_tower_middle" to "胖塔中段",
        "fat_tower_top" to "胖塔顶",
        "bridge_piece" to "桥梁",
        "bridge_end" to "桥尾",
        "bridge_gentle_stairs" to "缓坡台阶",
        "bridge_steep_stairs" to "陡坡台阶",
        "ship" to "末地船",
    )
}
