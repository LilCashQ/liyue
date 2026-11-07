package com.liyue.mctoolbox.map

import androidx.compose.ui.graphics.Color
import com.liyue.mctoolbox.seedgen.Dimension

/**
 * Map layers in Chunkbase's display order. [featureBit] matches the mask used
 * by the bundled Axolotl seed-map bridge. [maxBlocksPerPixel] bounds how far
 * the map can zoom out before a dense layer stops being drawn.
 */
enum class StructureType(
    val label: String,
    val dimension: Dimension,
    val color: Color,
    val featureBit: Int,
    val maxBlocksPerPixel: Float,
) {
    SLIME_CHUNK("史莱姆区块", Dimension.OVERWORLD, Color(0xFF7ED957), 1 shl 10, 4f),
    VILLAGE("村庄", Dimension.OVERWORLD, Color(0xFF7CB342), 1 shl 0, 16f),
    OUTPOST("掠夺者前哨", Dimension.OVERWORLD, Color(0xFFEF6C00), 1 shl 1, 64f),
    SHIPWRECK("沉船", Dimension.OVERWORLD, Color(0xFF00838F), 1 shl 2, 16f),
    MONUMENT("海底神殿", Dimension.OVERWORLD, Color(0xFF26A69A), 1 shl 3, 16f),
    MANSION("林地府邸", Dimension.OVERWORLD, Color(0xFF8D6E63), 1 shl 4, 64f),
    ANCIENT_CITY("远古城市", Dimension.OVERWORLD, Color(0xFF455A64), 1 shl 5, 16f),
    TRAIL_RUINS("古迹废墟", Dimension.OVERWORLD, Color(0xFFA1887F), 1 shl 6, 16f),
    TRIAL_CHAMBERS("试炼密室", Dimension.OVERWORLD, Color(0xFF5C6BC0), 1 shl 7, 4f),
    RUINED_PORTAL("废弃传送门", Dimension.OVERWORLD, Color(0xFF7E57C2), 1 shl 8, 4f),
    STRONGHOLD("要塞", Dimension.OVERWORLD, Color(0xFF90A4AE), 1 shl 9, 64f),
    DESERT_PYRAMID("沙漠神殿", Dimension.OVERWORLD, Color(0xFFF9A825), 1 shl 11, 16f),
    JUNGLE_TEMPLE("丛林神庙", Dimension.OVERWORLD, Color(0xFF2E7D32), 1 shl 12, 16f),
    SWAMP_HUT("沼泽小屋", Dimension.OVERWORLD, Color(0xFF6D4C41), 1 shl 13, 16f),
    IGLOO("雪屋", Dimension.OVERWORLD, Color(0xFF90CAF9), 1 shl 14, 16f),
    OCEAN_RUIN("海底废墟", Dimension.OVERWORLD, Color(0xFF29B6F6), 1 shl 15, 4f),
    TREASURE("埋藏的宝藏", Dimension.OVERWORLD, Color(0xFFFDD835), 1 shl 16, 16f),
    MINESHAFT("废弃矿井", Dimension.OVERWORLD, Color(0xFF8D6E63), 1 shl 17, 4f),
    DESERT_WELL("沙漠水井", Dimension.OVERWORLD, Color(0xFFD7CCC8), 1 shl 18, 16f),
    GEODE("紫水晶洞", Dimension.OVERWORLD, Color(0xFFAB47BC), 1 shl 19, 4f),
    ABANDONED_CAMP("废弃营地", Dimension.OVERWORLD, Color(0xFFA5D6A7), 1 shl 24, 16f),
    FORTRESS("下界要塞", Dimension.NETHER, Color(0xFFD84315), 1 shl 20, 64f),
    BASTION("堡垒遗迹", Dimension.NETHER, Color(0xFFAD1457), 1 shl 21, 16f),
    NETHER_FOSSIL("下界化石", Dimension.NETHER, Color(0xFFBCAAA4), 1 shl 25, 4f),
    END_CITY("末地城", Dimension.END, Color(0xFF9575CD), 1 shl 22, 16f),
    END_GATEWAY("末地折跃门", Dimension.END, Color(0xFFB39DDB), 1 shl 23, 4f),
    ;

    companion object {
        /**
         * Types that can be placed for at least one version this app exposes.
         * Abandoned camps arrived in 26.3, so they stay listed and the version
         * gate in `StructureSupport` hides them on older releases.
         */
        private val PLACEABLE = entries

        fun forDimension(dimension: Dimension): List<StructureType> =
            PLACEABLE.filter { it.dimension == dimension }

        /**
         * Layers the Bedrock generator can place with our ported algorithm.
         * Slime chunks, geodes, mineshafts, desert wells, ancient cities,
         * trial chambers, trail ruins and abandoned camps are Java-only here.
         */
        val BEDROCK_SUPPORTED: Set<StructureType> = setOf(
            VILLAGE, OUTPOST, SHIPWRECK, MONUMENT, MANSION, RUINED_PORTAL,
            STRONGHOLD, DESERT_PYRAMID, JUNGLE_TEMPLE, SWAMP_HUT, IGLOO,
            OCEAN_RUIN, TREASURE, FORTRESS, BASTION, END_CITY,
        )

        val defaultEnabled: Set<StructureType> = setOf(
            VILLAGE, ANCIENT_CITY, STRONGHOLD, MANSION,
            MONUMENT, OUTPOST, MINESHAFT, RUINED_PORTAL,
        )

        fun fromFeatureBit(bit: Int): StructureType? = entries.firstOrNull { it.featureBit == bit }

        fun maskOf(types: Collection<StructureType>): Int =
            types.fold(0) { acc, type -> acc or type.featureBit }
    }
}
