package com.liyue.mctoolbox.map.finder

import com.liyue.mctoolbox.map.GameVersion
import com.liyue.mctoolbox.map.GameVersions
import com.liyue.mctoolbox.map.StructureType
import com.liyue.mctoolbox.seedgen.Dimension
import com.liyue.mctoolbox.seedgen.OreKind
import com.liyue.mctoolbox.seedgen.McVersion

/** One tab of the finder, mirroring the Chunkbase "apps" split. */
enum class FinderTab(val label: String) {
    STRUCTURE("结构"),
    BIOME("群系"),
    STRONGHOLD("要塞"),
    SLIME("史莱姆区块"),
    SPAWN("出生点"),
    ORE_VEIN("矿脉"),
    GOLDEN_APPLE("宝箱战利品"),
    LAVA_POOL("岩浆湖"),
    DUNGEON("地牢"),
    QUAD_HUT("四女巫小屋"),
    SEED_QUEUE("种子队列"),
}

/** A single search result: always a block position plus context. */
data class FinderHit(
    val title: String,
    val x: Int,
    val z: Int,
    val distance: Int,
    val detail: String? = null,
    val dimension: Dimension = Dimension.OVERWORLD,
    /** Set by the seed queue, where every row is a different world. */
    val seed: Long? = null,
)

data class BiomeOption(val id: Int, val name: String, val label: String)

data class FinderUiState(
    val tab: FinderTab = FinderTab.STRUCTURE,
    val seedText: String = "0",
    val version: GameVersion = GameVersions.default,
    val structure: StructureType = StructureType.VILLAGE,
    val veinKind: OreKind = OreKind.IRON_VEIN,
    val biome: BiomeOption? = null,
    val biomeDimension: Dimension = Dimension.OVERWORLD,
    val biomeOptions: List<BiomeOption> = emptyList(),
    val centerXText: String = "0",
    val centerZText: String = "0",
    val radiusText: String = "2048",
    val limitText: String = "20",
    val seedQueueText: String = "",
    val minCountText: String = "2",
    val loading: Boolean = false,
    val error: String? = null,
    val hits: List<FinderHit> = emptyList(),
    val elapsedMs: Long = 0,
    val searched: Boolean = false,
) {
    val centerX: Int get() = centerXText.trim().toIntOrNull() ?: 0
    val centerZ: Int get() = centerZText.trim().toIntOrNull() ?: 0
    val radius: Int get() = (radiusText.trim().toIntOrNull() ?: 2048).coerceIn(128, 16384)
    val limit: Int get() = (limitText.trim().toIntOrNull() ?: 20).coerceIn(1, 100)
    val dimension: Dimension
        get() = when (tab) {
            FinderTab.BIOME -> biomeDimension
            FinderTab.ORE_VEIN, FinderTab.LAVA_POOL, FinderTab.DUNGEON,
            FinderTab.QUAD_HUT, FinderTab.SEED_QUEUE -> Dimension.OVERWORLD
            else -> structure.dimension
        }
}

/**
 * First Java Edition version that ships each structure, taken from the
 * capability matrix (docs/capability-matrix.md). Types without an entry exist
 * in every supported version.
 */
object StructureSupport {

    /**
     * Types the bundled cubiomes snapshot cannot place at all for the versions
     * this app exposes. Empty today: abandoned camps need 26.3, which is now
     * the newest entry in [GameVersions], so the version gate below covers it.
     */
    private val UNSUPPORTED = emptySet<StructureType>()

    fun minVersion(type: StructureType): McVersion? = when (type) {
        StructureType.MONUMENT -> McVersion.MC_1_8_9
        StructureType.IGLOO, StructureType.END_CITY -> McVersion.MC_1_9_4
        StructureType.MANSION -> McVersion.MC_1_11_2
        StructureType.SHIPWRECK, StructureType.OCEAN_RUIN,
        StructureType.TREASURE, StructureType.END_GATEWAY -> McVersion.MC_1_13_2
        StructureType.OUTPOST -> McVersion.MC_1_14_4
        StructureType.RUINED_PORTAL, StructureType.BASTION,
        StructureType.NETHER_FOSSIL -> McVersion.MC_1_16_1
        StructureType.GEODE -> McVersion.MC_1_17_1
        StructureType.ANCIENT_CITY -> McVersion.MC_1_19_2
        StructureType.TRAIL_RUINS -> McVersion.MC_1_20_6
        StructureType.TRIAL_CHAMBERS -> McVersion.MC_1_21_1
        StructureType.ABANDONED_CAMP -> McVersion.MC_26_3
        else -> null
    }

    fun isSupported(type: StructureType, version: McVersion): Boolean {
        if (type in UNSUPPORTED) return false
        val min = minVersion(type) ?: return true
        return version >= min
    }

    fun forVersion(version: McVersion): List<StructureType> =
        StructureType.entries.filter { isSupported(it, version) }
}
