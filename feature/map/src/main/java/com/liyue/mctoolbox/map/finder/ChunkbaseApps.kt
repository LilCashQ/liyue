package com.liyue.mctoolbox.map.finder

import androidx.compose.ui.graphics.Color
import com.liyue.mctoolbox.map.StructureType
import com.liyue.mctoolbox.seedgen.McVersion

/** Section headers on the apps screen, mirroring chunkbase.com/apps. */
enum class AppGroup(val label: String) {
    MAP("地图"),
    OVERWORLD("主世界"),
    NETHER_END("下界与末地"),
    OTHER("其他玩法"),
}

/**
 * What a card actually searches for. Most entries are a single [StructureType],
 * but the finder also has tabs that are not structure placements at all
 * (biomes, strongholds, slime chunks) and map-only layers.
 */
enum class AppKind {
    SEED_MAP,
    STRUCTURE,
    STRONGHOLD,
    SLIME,
    BIOME,
    SPAWN,
    ORE_VEIN,
    CAVE,
    RAVINE,
    LAVA_POOL,
    DUNGEON,
    GOLDEN_APPLE,
}

/**
 * One entry of chunkbase.com's app index, ported one-to-one.
 *
 * [available] is false for the handful of apps that need world-generation
 * stages this app does not simulate yet; they stay visible (greyed out) so the
 * index matches Chunkbase and the gap is explicit rather than silent.
 */
data class ChunkbaseApp(
    val id: String,
    val title: String,
    val subtitle: String,
    val group: AppGroup,
    val kind: AppKind,
    val color: Color,
    val structure: StructureType? = null,
    val minVersion: McVersion? = null,
    val available: Boolean = true,
    val unavailableReason: String? = null,
) {
    val tab: FinderTab
        get() = when (kind) {
            AppKind.STRUCTURE, AppKind.ORE_VEIN, AppKind.CAVE, AppKind.RAVINE,
            AppKind.LAVA_POOL -> FinderTab.LAVA_POOL
            AppKind.DUNGEON -> FinderTab.DUNGEON
            AppKind.GOLDEN_APPLE -> FinderTab.GOLDEN_APPLE
            AppKind.STRONGHOLD -> FinderTab.STRONGHOLD
            AppKind.SLIME -> FinderTab.SLIME
            AppKind.BIOME -> FinderTab.BIOME
            AppKind.SPAWN -> FinderTab.SPAWN
            AppKind.SEED_MAP -> FinderTab.STRUCTURE
        }
}

/**
 * The complete Chunkbase app index. Order and grouping follow
 * https://www.chunkbase.com/apps/ so the two are easy to compare.
 */
object ChunkbaseApps {

    private fun structure(
        type: StructureType,
        title: String? = null,
        subtitle: String? = null,
        group: AppGroup = AppGroup.OVERWORLD,
        min: McVersion? = null,
    ) = ChunkbaseApp(
        id = type.name.lowercase(),
        title = title ?: type.label,
        subtitle = subtitle ?: "${type.label}位置",
        group = group,
        kind = AppKind.STRUCTURE,
        color = type.color,
        structure = type,
        minVersion = min ?: StructureSupport.minVersion(type),
    )

    val all: List<ChunkbaseApp> = listOf(
        ChunkbaseApp(
            id = "seed_map",
            title = "种子地图",
            subtitle = "群系、结构与坐标总览",
            group = AppGroup.MAP,
            kind = AppKind.SEED_MAP,
            color = Color(0xFF1976D2),
        ),

        // ---- 主世界 ----
        structure(StructureType.VILLAGE, subtitle = "最近的村庄与所属群系"),
        structure(StructureType.MONUMENT, subtitle = "海底神殿"),
        structure(StructureType.MANSION, subtitle = "林地府邸"),
        structure(StructureType.OUTPOST, subtitle = "掠夺者前哨"),
        structure(StructureType.ANCIENT_CITY, subtitle = "远古城市"),
        ChunkbaseApp(
            id = "stronghold",
            title = "要塞",
            subtitle = "末地传送门所在的要塞",
            group = AppGroup.OVERWORLD,
            kind = AppKind.STRONGHOLD,
            color = StructureType.STRONGHOLD.color,
        ),
        structure(StructureType.TRIAL_CHAMBERS, subtitle = "试炼密室"),
        structure(StructureType.TRAIL_RUINS, subtitle = "古迹废墟"),
        structure(StructureType.DESERT_PYRAMID, subtitle = "沙漠神殿"),
        structure(StructureType.JUNGLE_TEMPLE, subtitle = "丛林神庙"),
        structure(StructureType.SWAMP_HUT, subtitle = "沼泽小屋"),
        structure(StructureType.IGLOO, subtitle = "雪屋"),
        structure(StructureType.SHIPWRECK, subtitle = "沉船"),
        structure(StructureType.OCEAN_RUIN, subtitle = "海底废墟"),
        structure(StructureType.TREASURE, subtitle = "埋藏的宝藏"),
        structure(StructureType.RUINED_PORTAL, subtitle = "废弃传送门"),
        structure(StructureType.MINESHAFT, subtitle = "废弃矿井"),
        structure(StructureType.DESERT_WELL, subtitle = "沙漠水井"),
        structure(StructureType.GEODE, subtitle = "紫水晶洞"),
        structure(StructureType.ABANDONED_CAMP, subtitle = "废弃营地（26.3+）"),
        ChunkbaseApp(
            id = "slime_chunk",
            title = "史莱姆区块",
            subtitle = "1.48% 概率生成史莱姆",
            group = AppGroup.OVERWORLD,
            kind = AppKind.SLIME,
            color = StructureType.SLIME_CHUNK.color,
        ),
        ChunkbaseApp(
            id = "cave",
            title = "洞穴",
            subtitle = "按 Y 层看洞穴与峡谷",
            group = AppGroup.OVERWORLD,
            kind = AppKind.CAVE,
            color = Color(0xFF42A5F5),
            minVersion = McVersion.MC_1_13_2,
        ),
        ChunkbaseApp(
            id = "ravine",
            title = "峡谷",
            subtitle = "只看峡谷雕刻出的空腔",
            group = AppGroup.OVERWORLD,
            kind = AppKind.RAVINE,
            color = Color(0xFFE8A33D),
            minVersion = McVersion.MC_1_13_2,
        ),
        ChunkbaseApp(
            id = "lava_pool",
            title = "熔岩池",
            subtitle = "地下的熔岩湖与熔岩池",
            group = AppGroup.OVERWORLD,
            kind = AppKind.LAVA_POOL,
            color = Color(0xFFE64A19),
        ),
        ChunkbaseApp(
            id = "ore_vein",
            title = "矿脉",
            subtitle = "铁与铜的大型矿脉",
            group = AppGroup.OVERWORLD,
            kind = AppKind.ORE_VEIN,
            color = Color(0xFFD4A373),
            minVersion = McVersion.MC_1_18_2,
        ),
        ChunkbaseApp(
            id = "dungeon",
            title = "地牢",
            subtitle = "僵尸 / 骷髅 / 蜘蛛刷怪笼",
            minVersion = McVersion.MC_1_18_2,
            group = AppGroup.OVERWORLD,
            kind = AppKind.DUNGEON,
            color = Color(0xFF6D6D6D),
        ),
        ChunkbaseApp(
            id = "biome",
            title = "群系",
            subtitle = "按种子找指定群系",
            group = AppGroup.OVERWORLD,
            kind = AppKind.BIOME,
            color = Color(0xFF66BB6A),
        ),

        // ---- 下界与末地 ----
        structure(StructureType.FORTRESS, subtitle = "下界要塞", group = AppGroup.NETHER_END),
        structure(StructureType.BASTION, subtitle = "堡垒遗迹", group = AppGroup.NETHER_END),
        structure(
            StructureType.NETHER_FOSSIL,
            title = "下界化石",
            subtitle = "下界化石",
            group = AppGroup.NETHER_END,
        ),
        structure(StructureType.END_CITY, subtitle = "末地城", group = AppGroup.NETHER_END),
        structure(StructureType.END_GATEWAY, subtitle = "末地折跃门", group = AppGroup.NETHER_END),

        // ---- 其他 ----
        ChunkbaseApp(
            id = "spawn",
            title = "出生点",
            subtitle = "世界出生位置",
            group = AppGroup.OTHER,
            kind = AppKind.SPAWN,
            color = Color(0xFF8E24AA),
        ),
        ChunkbaseApp(
            id = "golden_apple",
            title = "金苹果",
            subtitle = "滚算宝箱，找出真的带金苹果的那几个",
            group = AppGroup.OTHER,
            kind = AppKind.GOLDEN_APPLE,
            color = Color(0xFFFBC02D),
        ),
    )

    fun byId(id: String?): ChunkbaseApp? = all.firstOrNull { it.id == id }

    fun byGroup(group: AppGroup): List<ChunkbaseApp> = all.filter { it.group == group }
}
