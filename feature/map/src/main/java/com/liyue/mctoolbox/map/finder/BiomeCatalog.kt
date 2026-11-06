package com.liyue.mctoolbox.map.finder

import com.liyue.mctoolbox.seedgen.Dimension
import com.liyue.mctoolbox.seedgen.SeedEngine

/**
 * Biome ids and display names. The identifiers come from cubiomes'
 * `biome2str`, which is version aware (biomes were renamed over time), so the
 * lookup here is keyed by the identifier rather than by the numeric id.
 */
object BiomeCatalog {

    private val NETHER = setOf(
        "nether_wastes", "soul_sand_valley", "crimson_forest", "warped_forest",
        "basalt_deltas",
    )

    private val END = setOf(
        "the_end", "small_end_islands", "end_midlands", "end_highlands",
        "end_barrens", "the_void",
    )

    private val ZH: Map<String, String> = mapOf(
        "ocean" to "海洋",
        "plains" to "平原",
        "desert" to "沙漠",
        "windswept_hills" to "风袭丘陵",
        "forest" to "森林",
        "taiga" to "针叶林",
        "swamp" to "沼泽",
        "river" to "河流",
        "nether_wastes" to "下界荒地",
        "the_end" to "末地",
        "frozen_ocean" to "冻洋",
        "frozen_river" to "冻河",
        "snowy_plains" to "雪原",
        "snowy_mountains" to "雪山",
        "mushroom_fields" to "蘑菇岛",
        "mushroom_field_shore" to "蘑菇岛岸",
        "beach" to "沙滩",
        "desert_hills" to "沙漠丘陵",
        "wooded_hills" to "森林丘陵",
        "taiga_hills" to "针叶林丘陵",
        "mountain_edge" to "山地边缘",
        "jungle" to "丛林",
        "jungle_hills" to "丛林丘陵",
        "sparse_jungle" to "稀疏丛林",
        "deep_ocean" to "深海",
        "stony_shore" to "石岸",
        "snowy_beach" to "雪滩",
        "birch_forest" to "桦木森林",
        "birch_forest_hills" to "桦木森林丘陵",
        "dark_forest" to "黑森林",
        "snowy_taiga" to "积雪针叶林",
        "snowy_taiga_hills" to "积雪针叶林丘陵",
        "old_growth_pine_taiga" to "原始松林",
        "giant_tree_taiga_hills" to "巨型针叶林丘陵",
        "windswept_forest" to "风袭森林",
        "savanna" to "热带草原",
        "savanna_plateau" to "热带高原",
        "badlands" to "恶地",
        "wooded_badlands" to "疏林恶地",
        "badlands_plateau" to "恶地高原",
        "small_end_islands" to "末地小岛",
        "end_midlands" to "末地内陆",
        "end_highlands" to "末地高地",
        "end_barrens" to "末地荒地",
        "warm_ocean" to "暖水海洋",
        "lukewarm_ocean" to "温水海洋",
        "cold_ocean" to "冷水海洋",
        "deep_warm_ocean" to "暖水深海",
        "deep_lukewarm_ocean" to "温水深海",
        "deep_cold_ocean" to "冷水深海",
        "deep_frozen_ocean" to "冻水深海",
        "seasonal_forest" to "季节性森林",
        "rainforest" to "雨林",
        "shrubland" to "灌木地",
        "the_void" to "虚空",
        "sunflower_plains" to "向日葵平原",
        "desert_lakes" to "沙漠湖泊",
        "windswept_gravelly_hills" to "风袭砂砾丘陵",
        "flower_forest" to "繁花森林",
        "taiga_mountains" to "针叶林山地",
        "swamp_hills" to "沼泽丘陵",
        "ice_spikes" to "冰刺之地",
        "modified_jungle" to "丛林变种",
        "modified_jungle_edge" to "丛林边缘变种",
        "old_growth_birch_forest" to "原始桦木森林",
        "tall_birch_hills" to "高大桦木丘陵",
        "dark_forest_hills" to "黑森林丘陵",
        "snowy_taiga_mountains" to "积雪针叶林山地",
        "old_growth_spruce_taiga" to "原始云杉针叶林",
        "giant_spruce_taiga_hills" to "巨型云杉针叶林丘陵",
        "modified_gravelly_mountains" to "砂砾山地变种",
        "windswept_savanna" to "风袭热带草原",
        "shattered_savanna_plateau" to "破碎热带高原",
        "eroded_badlands" to "被风蚀的恶地",
        "modified_wooded_badlands_plateau" to "疏林恶地高原变种",
        "modified_badlands_plateau" to "恶地高原变种",
        "bamboo_jungle" to "竹林",
        "bamboo_jungle_hills" to "竹林丘陵",
        "soul_sand_valley" to "灵魂沙峡谷",
        "crimson_forest" to "绯红森林",
        "warped_forest" to "诡异森林",
        "basalt_deltas" to "玄武岩三角洲",
        "dripstone_caves" to "溶洞",
        "lush_caves" to "繁茂洞穴",
        "meadow" to "草甸",
        "grove" to "雪林",
        "snowy_slopes" to "积雪山坡",
        "jagged_peaks" to "尖峭山峰",
        "frozen_peaks" to "冰封山峰",
        "stony_peaks" to "裸岩山峰",
        "deep_dark" to "深暗之域",
        "mangrove_swamp" to "红树林沼泽",
        "cherry_grove" to "樱花树林",
        "pale_garden" to "苍白之园",
        "sulfur_caves" to "硫磺洞穴",
        "dappled_forest" to "斑驳森林",
    )

    fun labelOf(name: String): String = ZH[name] ?: name

    fun dimensionOf(name: String): Dimension = when {
        NETHER.contains(name) -> Dimension.NETHER
        END.contains(name) -> Dimension.END
        else -> Dimension.OVERWORLD
    }

    /** Enumerates the biomes cubiomes knows about for a version. */
    fun options(engine: SeedEngine, version: com.liyue.mctoolbox.seedgen.McVersion): List<BiomeOption> {
        val seen = HashSet<String>()
        val result = ArrayList<BiomeOption>()
        for (id in 0..255) {
            val name = runCatching { engine.biomeName(version, id) }.getOrNull() ?: continue
            if (!seen.add(name)) continue
            result.add(BiomeOption(id, name, labelOf(name)))
        }
        return result.sortedWith(compareBy({ dimensionOf(it.name).id }, { it.label }))
    }

    fun forDimension(options: List<BiomeOption>, dimension: Dimension): List<BiomeOption> =
        options.filter { dimensionOf(it.name) == dimension }
}
