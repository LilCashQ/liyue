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

    /** Village icons keyed by drawable id, since the pick depends on the biome. */
    fun villageBitmaps(context: Context): Map<Int, ImageBitmap> {
        val ids = listOf(
            R.drawable.cb_village_plains,
            R.drawable.cb_village_desert,
            R.drawable.cb_village_savanna,
            R.drawable.cb_village_snowy,
            R.drawable.cb_village_taiga,
        )
        val out = LinkedHashMap<Int, ImageBitmap>()
        ids.forEach { id ->
            runCatching {
                BitmapFactory.decodeResource(context.resources, id)?.asImageBitmap()
            }.getOrNull()?.let { out[id] = it }
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

    fun ore(kind: OreKind): Int? = when (kind) {
        OreKind.DIAMOND -> R.drawable.ore_diamond
        OreKind.IRON -> R.drawable.ore_iron
        OreKind.IRON_VEIN -> R.drawable.ore_iron_vein
        OreKind.GOLD -> R.drawable.ore_gold
        OreKind.REDSTONE -> R.drawable.ore_redstone
        OreKind.LAPIS -> R.drawable.ore_lapis
        OreKind.COAL -> R.drawable.ore_coal
        OreKind.COPPER -> R.drawable.ore_copper
        OreKind.COPPER_VEIN -> R.drawable.ore_copper_vein
        OreKind.EMERALD -> R.drawable.ore_emerald
        OreKind.QUARTZ -> R.drawable.ore_quartz
        OreKind.NETHER_GOLD -> R.drawable.ore_nether_gold
        OreKind.DEBRIS -> R.drawable.ore_debris
    }

    /** Villages wear the biome they spawned in, like Chunkbase does. */
    fun village(biomeId: Int?): Int = when (biomeId) {
        2 -> R.drawable.cb_village_desert
        35, 36, 163 -> R.drawable.cb_village_savanna
        12, 30, 140 -> R.drawable.cb_village_snowy
        5, 19, 32, 33 -> R.drawable.cb_village_taiga
        else -> R.drawable.cb_village_plains
    }

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
