package com.liyue.mctoolbox.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.liyue.mctoolbox.seedgen.McVersion
import com.liyue.mctoolbox.seedgen.SeedEngine
import java.util.concurrent.ConcurrentHashMap

/**
 * Biome id -> ARGB lookup per version. The native layer reports version-aware
 * names (biomes were renamed over time), so the table is keyed by name and
 * rebuilt only when a new version is first used.
 */
object BiomePalette {

    private val cache = ConcurrentHashMap<McVersion, IntArray>()

    fun lut(version: McVersion, engine: SeedEngine): IntArray = cache.getOrPut(version) {
        IntArray(257) { index ->
            if (index == 256) UNKNOWN.toArgb()
            else colorForName(engine.biomeName(version, index)).toArgb()
        }
    }

    fun colorForName(rawName: String): Color {
        val name = ALIASES[rawName] ?: rawName
        BY_NAME[name]?.let { return it }
        return when {
            name.startsWith("unknown") -> UNKNOWN
            name.contains("ocean") -> OCEAN
            name.contains("river") -> RIVER
            name.contains("snow") || name.contains("frozen") || name.contains("ice") -> SNOW
            name.contains("desert") || name.contains("badlands") -> SAND
            name.contains("swamp") -> SWAMP
            name.contains("jungle") -> JUNGLE
            name.contains("taiga") || name.contains("forest") || name.contains("grove") -> FOREST
            name.contains("peak") || name.contains("slope") || name.contains("stony") -> STONE
            else -> UNKNOWN
        }
    }

    private val OCEAN = Color(0xFF3A5F8A)
    private val RIVER = Color(0xFF4F86C6)
    private val SNOW = Color(0xFFE4EEF3)
    private val SAND = Color(0xFFE2CE94)
    private val SWAMP = Color(0xFF4C6B3C)
    private val JUNGLE = Color(0xFF3C8E2F)
    private val FOREST = Color(0xFF4E8F3A)
    private val STONE = Color(0xFF9AA09A)
    private val UNKNOWN = Color(0xFF8A8A8A)

    private val ALIASES = mapOf(
        "extreme_hills" to "windswept_hills",
        "extreme_hills_edge" to "mountain_edge",
        "extreme_hills_plus" to "windswept_gravelly_hills",
        "stone_beach" to "stony_shore",
        "cold_taiga" to "snowy_taiga",
        "cold_taiga_hills" to "snowy_taiga_hills",
        "cold_taiga_mountains" to "snowy_taiga_mountains",
        "ice_plains" to "snowy_plains",
        "ice_mountains" to "snowy_mountains",
        "mushroom_island" to "mushroom_fields",
        "mushroom_island_shore" to "mushroom_field_shore",
        "jungle_edge" to "sparse_jungle",
        "modified_jungle" to "jungle",
        "modified_jungle_edge" to "sparse_jungle",
        "mega_taiga" to "old_growth_pine_taiga",
        "mega_spruce_taiga" to "old_growth_spruce_taiga",
        "mega_taiga_hills" to "giant_tree_taiga_hills",
        "mega_spruce_taiga_hills" to "giant_spruce_taiga_hills",
        "roofed_forest" to "dark_forest",
        "roofed_forest_hills" to "dark_forest_hills",
        "swampland" to "swamp",
        "swampland_hills" to "swamp_hills",
        "shattered_savanna" to "windswept_savanna",
        "shattered_savanna_plateau" to "wooded_badlands",
        "modified_gravelly_mountains" to "windswept_gravelly_hills",
        "modified_wooded_badlands_plateau" to "wooded_badlands",
        "modified_badlands_plateau" to "badlands",
        "giant_tree_taiga" to "old_growth_pine_taiga",
        "giant_spruce_taiga" to "old_growth_spruce_taiga",
        "tall_birch_forest" to "old_growth_birch_forest",
        "tall_birch_hills" to "old_growth_birch_forest",
        "desert_lakes" to "desert",
    )

    private val BY_NAME = mapOf(
        "ocean" to Color(0xFF3A5F8A),
        "deep_ocean" to Color(0xFF2C4A70),
        "warm_ocean" to Color(0xFF3E7FA8),
        "lukewarm_ocean" to Color(0xFF4A86AE),
        "cold_ocean" to Color(0xFF4A6F8E),
        "deep_warm_ocean" to Color(0xFF33627F),
        "deep_lukewarm_ocean" to Color(0xFF3A6E90),
        "deep_cold_ocean" to Color(0xFF3B5B78),
        "frozen_ocean" to Color(0xFF7FA3BF),
        "deep_frozen_ocean" to Color(0xFF6E8EA6),
        "river" to Color(0xFF4F86C6),
        "frozen_river" to Color(0xFF8FB6D4),
        "plains" to Color(0xFF9CC26B),
        "sunflower_plains" to Color(0xFFB7D36A),
        "snowy_plains" to Color(0xFFE9F2F5),
        "ice_spikes" to Color(0xFFDDEBF2),
        "desert" to Color(0xFFE7D8A2),
        "desert_hills" to Color(0xFFDECC92),
        "forest" to Color(0xFF4E8F3A),
        "flower_forest" to Color(0xFF6FA84F),
        "birch_forest" to Color(0xFFA7C97F),
        "birch_forest_hills" to Color(0xFFAFD087),
        "old_growth_birch_forest" to Color(0xFF9DBF78),
        "dark_forest" to Color(0xFF2F5A2A),
        "dark_forest_hills" to Color(0xFF2A5226),
        "windswept_hills" to Color(0xFF8EA37A),
        "windswept_gravelly_hills" to Color(0xFF9AA08C),
        "windswept_forest" to Color(0xFF7E9C6C),
        "mountain_edge" to Color(0xFFA8B48C),
        "taiga" to Color(0xFF587C4C),
        "taiga_hills" to Color(0xFF5E7F52),
        "old_growth_pine_taiga" to Color(0xFF4F6F45),
        "old_growth_spruce_taiga" to Color(0xFF4A6842),
        "giant_tree_taiga_hills" to Color(0xFF7C8D55),
        "giant_spruce_taiga_hills" to Color(0xFF6E7F4A),
        "snowy_taiga" to Color(0xFFCFE0DA),
        "snowy_taiga_hills" to Color(0xFFC4D8D2),
        "snowy_mountains" to Color(0xFFD9E4EA),
        "snowy_slopes" to Color(0xFFE2EAF0),
        "snowy_taiga_mountains" to Color(0xFFBDD2CC),
        "swamp" to Color(0xFF4C6B3C),
        "swamp_hills" to Color(0xFF456236),
        "mangrove_swamp" to Color(0xFF4E7A54),
        "jungle" to Color(0xFF3C8E2F),
        "jungle_hills" to Color(0xFF45993A),
        "sparse_jungle" to Color(0xFF5B9C4B),
        "bamboo_jungle" to Color(0xFF4E9A3A),
        "bamboo_jungle_hills" to Color(0xFF45913A),
        "savanna" to Color(0xFFC6B458),
        "savanna_plateau" to Color(0xFFCBBE6A),
        "windswept_savanna" to Color(0xFFC2AE5E),
        "badlands" to Color(0xFFC97B3F),
        "eroded_badlands" to Color(0xFFD68B4C),
        "wooded_badlands" to Color(0xFFA96B3C),
        "beach" to Color(0xFFEEDDA8),
        "snowy_beach" to Color(0xFFEAF0F2),
        "stony_shore" to Color(0xFF9C9C94),
        "mushroom_fields" to Color(0xFFC9A6C9),
        "mushroom_field_shore" to Color(0xFFD8C2D8),
        "meadow" to Color(0xFF7FB55E),
        "grove" to Color(0xFF6F8F6A),
        "jagged_peaks" to Color(0xFFD5DEE6),
        "frozen_peaks" to Color(0xFFE8F0F5),
        "stony_peaks" to Color(0xFF9FA79B),
        "dripstone_caves" to Color(0xFF8C7A6B),
        "lush_caves" to Color(0xFF4F8F4A),
        "deep_dark" to Color(0xFF1E2B30),
        "cherry_grove" to Color(0xFFE7AFC0),
        "pale_garden" to Color(0xFF9BA394),
        "the_void" to Color(0xFF1A1A1A),
        "nether_wastes" to Color(0xFFB84A34),
        "soul_sand_valley" to Color(0xFF5A4A3A),
        "crimson_forest" to Color(0xFF8C2F2F),
        "warped_forest" to Color(0xFF2F6B62),
        "basalt_deltas" to Color(0xFF6B6773),
        "the_end" to Color(0xFFDDE3A8),
        "small_end_islands" to Color(0xFFC8D0A0),
        "end_midlands" to Color(0xFFC3CB9A),
        "end_highlands" to Color(0xFFBCC492),
        "end_barrens" to Color(0xFFC7CFA2),
    )
}
