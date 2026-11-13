package com.liyue.mctoolbox.map

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.liyue.mctoolbox.seedgen.OreKind

/**
 * Hand-drawn thumbnail icons for structures, ores and biome families. The
 * bitmaps ship as 128px WebP sprites cut out of 4x4 sheets, so the lookup is a
 * plain index into a flat array.
 */
internal object MapIcons {

    private val ORES = intArrayOf(
        R.drawable.ore_00, // diamond
        R.drawable.ore_01, // iron
        R.drawable.ore_02, // gold
        R.drawable.ore_03, // redstone
        R.drawable.ore_04, // lapis
        R.drawable.ore_05, // coal
        R.drawable.ore_06, // copper
        R.drawable.ore_07, // emerald
        R.drawable.ore_08, // quartz
        R.drawable.ore_09, // debris
        R.drawable.ore_10, // geode
        R.drawable.ore_11, // lava pool
        R.drawable.ore_12, // dungeon
        R.drawable.ore_13, // cave
        R.drawable.ore_14, // magma
        R.drawable.ore_15, // mountain section
    )

    private val BIOMES = intArrayOf(
        R.drawable.bio_00, // taiga
        R.drawable.bio_01, // birch forest
        R.drawable.bio_02, // plains
        R.drawable.bio_03, // desert
        R.drawable.bio_04, // snowy plains
        R.drawable.bio_05, // snowy peaks
        R.drawable.bio_06, // jungle
        R.drawable.bio_07, // swamp
        R.drawable.bio_08, // ocean
        R.drawable.bio_09, // beach
        R.drawable.bio_10, // flower forest
        R.drawable.bio_11, // cherry grove
        R.drawable.bio_12, // pale garden
        R.drawable.bio_13, // badlands
        R.drawable.bio_14, // mushroom fields
        R.drawable.bio_15, // river
    )

    /** Decoded sprites for the map canvas, keyed by structure type. */
    fun structureBitmaps(context: Context): Map<StructureType, ImageBitmap> {
        val out = LinkedHashMap<StructureType, ImageBitmap>()
        StructureType.entries.forEach { type ->
            val res = structure(type) ?: return@forEach
            runCatching {
                BitmapFactory.decodeResource(context.resources, res)?.asImageBitmap()
            }.getOrNull()?.let { out[type] = it }
        }
        return out
    }

    fun structure(type: StructureType): Int? = when (type) {
        StructureType.VILLAGE -> R.drawable.cb_village
        StructureType.OUTPOST -> R.drawable.cb_outpost
        StructureType.SHIPWRECK -> R.drawable.cb_shipwreck
        StructureType.MONUMENT -> R.drawable.cb_monument
        StructureType.MANSION -> R.drawable.cb_mansion
        StructureType.ANCIENT_CITY -> R.drawable.cb_ancient_city
        StructureType.TRAIL_RUINS -> R.drawable.cb_trail_ruins
        StructureType.TRIAL_CHAMBERS -> R.drawable.cb_trial_chambers
        StructureType.RUINED_PORTAL -> R.drawable.cb_ruined_portal
        StructureType.STRONGHOLD -> R.drawable.cb_stronghold
        StructureType.DESERT_PYRAMID -> R.drawable.cb_desert_pyramid
        StructureType.JUNGLE_TEMPLE -> R.drawable.cb_jungle_temple
        StructureType.SWAMP_HUT -> R.drawable.cb_swamp_hut
        StructureType.IGLOO -> R.drawable.cb_igloo
        StructureType.OCEAN_RUIN -> R.drawable.cb_ocean_ruin
        StructureType.TREASURE -> R.drawable.cb_treasure
        StructureType.MINESHAFT -> R.drawable.cb_mineshaft
        StructureType.DESERT_WELL -> R.drawable.cb_desert_well
        StructureType.GEODE -> R.drawable.cb_geode
        StructureType.ABANDONED_CAMP -> R.drawable.cb_abandoned_camp
        StructureType.FORTRESS -> R.drawable.cb_fortress
        StructureType.BASTION -> R.drawable.cb_bastion
        StructureType.NETHER_FOSSIL -> R.drawable.cb_nether_fossil
        StructureType.END_CITY -> R.drawable.cb_end_city
        StructureType.END_GATEWAY -> R.drawable.cb_end_gateway
        StructureType.SLIME_CHUNK -> R.drawable.cb_slime
    }

    fun ore(kind: OreKind): Int? = ORES.getOrNull(
        when (kind) {
            OreKind.DIAMOND -> 0
            OreKind.IRON -> 1
            OreKind.IRON_VEIN -> 13
            OreKind.GOLD -> 2
            OreKind.REDSTONE -> 3
            OreKind.LAPIS -> 4
            OreKind.COAL -> 5
            OreKind.COPPER -> 6
            OreKind.COPPER_VEIN -> 15
            OreKind.EMERALD -> 7
            OreKind.QUARTZ, OreKind.NETHER_GOLD -> 8
            OreKind.DEBRIS -> 9
        }
    )

    /** Maps a version-aware biome name (cubiomes spelling) to a family icon. */
    fun biome(rawName: String): Int? {
        val name = BiomePalette.canonical(rawName)
        val index = when {
            name.contains("pale_garden") -> 12
            name.contains("cherry") -> 11
            name.contains("flower_forest") -> 10
            name == "mushroom_fields" || name.contains("mushroom") -> 14
            name.contains("badlands") -> 13
            name.contains("birch") -> 1
            name.contains("jungle") -> 6
            name.contains("swamp") -> 7
            name.contains("river") -> 15
            name.contains("beach") || name.contains("shore") -> 9
            name.contains("mushroom_field") -> 14
            name.contains("ocean") -> 8
            name.contains("desert") -> 3
            name.contains("snow") || name.contains("frozen") || name.contains("ice") -> 4
            name.contains("peak") || name.contains("slope") || name.contains("grove") ||
                name.contains("hills") || name.contains("mountain") -> 5
            name.contains("taiga") -> 0
            name.contains("forest") -> 0
            name.contains("plains") || name.contains("meadow") || name.contains("savanna") -> 2
            name.contains("nether") || name.contains("crimson") || name.contains("warped") ||
                name.contains("soul") || name.contains("basalt") -> 13
            name.contains("end") -> 12
            else -> null
        }
        return BIOMES.getOrNull(index ?: return null)
    }
}

/**
 * Square thumbnail with the same corner radius everywhere it is used, so lists
 * and chips line up on one optical grid.
 */
@Composable
internal fun MapGlyph(res: Int, size: Dp = 18.dp, corner: Dp = 5.dp) {
    Image(
        painter = painterResource(res),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(corner)),
    )
}

/** Fallback dot for anything without artwork yet. */
@Composable
internal fun MapDot(color: Color, size: Dp = 9.dp) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(50))
            .background(color),
    )
}
