package com.liyue.mctoolbox.seedgen

/**
 * Ore kinds the bundled cubiomes bridge can scan. [group] is the index into
 * the bridge's `ore_groups` table, which bundles the version-specific variants
 * of one ore (normal / deepslate / large vein ...).
 *
 * [bestY] and [range] are vanilla's own distribution numbers, used only for the
 * UI hint text.
 */
enum class OreKind(
    val group: Int,
    val label: String,
    val dimension: Dimension,
    val bestY: Int,
    val rangeLabel: String,
    val minVersion: McVersion?,
) {
    DIAMOND(0, "钻石", Dimension.OVERWORLD, -59, "Y -64 ~ 16", McVersion.MC_1_7_10),
    IRON(1, "铁", Dimension.OVERWORLD, 16, "Y -64 ~ 320", McVersion.MC_1_7_10),
    IRON_VEIN(2, "铁（矿脉）", Dimension.OVERWORLD, 0, "Y -60 ~ -8", McVersion.MC_1_18_2),
    COPPER(3, "铜", Dimension.OVERWORLD, 48, "Y -16 ~ 112", McVersion.MC_1_17_1),
    COPPER_VEIN(4, "铜（矿脉）", Dimension.OVERWORLD, 0, "Y 0 ~ 50", McVersion.MC_1_18_2),
    GOLD(5, "金", Dimension.OVERWORLD, -16, "Y -64 ~ 32", McVersion.MC_1_7_10),
    REDSTONE(6, "红石", Dimension.OVERWORLD, -59, "Y -64 ~ 15", McVersion.MC_1_7_10),
    LAPIS(7, "青金石", Dimension.OVERWORLD, 0, "Y -64 ~ 64", McVersion.MC_1_7_10),
    COAL(8, "煤炭", Dimension.OVERWORLD, 96, "Y 0 ~ 320", McVersion.MC_1_7_10),
    DEBRIS(9, "远古残骸", Dimension.NETHER, 15, "Y 8 ~ 119", McVersion.MC_1_16_1),
    EMERALD(10, "绿宝石", Dimension.OVERWORLD, 236, "Y -16 ~ 320", McVersion.MC_1_7_10),
    QUARTZ(11, "下界石英", Dimension.NETHER, 0, "Y 10 ~ 117", McVersion.MC_1_16_1),
    NETHER_GOLD(12, "下界金", Dimension.NETHER, 0, "Y 10 ~ 117", McVersion.MC_1_16_1),
    ;
}

/** One ore block found by a scan. */
data class OreBlock(val x: Int, val y: Int, val z: Int)

/** Result of scanning one chunk for one ore kind. */
data class OreChunkResult(
    val chunkX: Int,
    val chunkZ: Int,
    val blocks: List<OreBlock>,
)
