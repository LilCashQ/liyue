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

    private val STRUCTURES = intArrayOf(
        R.drawable.st_00, // village
        R.drawable.st_01, // stronghold
        R.drawable.st_02, // mansion
        R.drawable.st_03, // desert pyramid
        R.drawable.st_04, // trail ruins
        R.drawable.st_05, // swamp hut
        R.drawable.st_06, // monument
        R.drawable.st_07, // shipwreck
        R.drawable.st_08, // outpost
        R.drawable.st_09, // ancient city
        R.drawable.st_10, // trial chambers
        R.drawable.st_11, // mineshaft
        R.drawable.st_12, // treasure
        R.drawable.st_13, // ruined portal
        R.drawable.st_14, // fortress
        R.drawable.st_15, // end city
    )

    private val STRUCTURES_B = intArrayOf(
        R.drawable.sx_00, // slime chunk
        R.drawable.sx_01, // jungle temple
        R.drawable.sx_02, // igloo
        R.drawable.sx_03, // ocean ruin
        R.drawable.sx_04, // desert well
        R.drawable.sx_05, // geode
        R.drawable.sx_06, // abandoned camp
        R.drawable.sx_07, // bastion
        R.drawable.sx_08, // nether fossil
        R.drawable.sx_09, // end gateway
        R.drawable.sx_10, // ravine
        R.drawable.sx_11, // cave
        R.drawable.sx_12, // ore vein
        R.drawable.sx_13, // spawn
        R.drawable.sx_14, // pin
        R.drawable.sx_15, // chests
    )

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

    fun structure(type: StructureType): Int? {
        val index = structureIndex(type)
        if (index < 0) {
            return STRUCTURES_B.getOrNull(
                when (type) {
                    StructureType.SLIME_CHUNK -> 0
                    StructureType.JUNGLE_TEMPLE -> 1
                    StructureType.IGLOO -> 2
                    StructureType.OCEAN_RUIN -> 3
                    StructureType.DESERT_WELL -> 4
                    StructureType.GEODE -> 5
                    StructureType.ABANDONED_CAMP -> 6
                    StructureType.BASTION -> 7
                    StructureType.NETHER_FOSSIL -> 8
                    StructureType.END_GATEWAY -> 9
                    else -> -1
                }
            )
        }
        return STRUCTURES.getOrNull(index)
    }

    private fun structureIndex(type: StructureType): Int = (
        when (type) {
            StructureType.VILLAGE -> 0
            StructureType.STRONGHOLD -> 1
            StructureType.MANSION -> 2
            StructureType.DESERT_PYRAMID -> 3
            StructureType.TRAIL_RUINS -> 4
            StructureType.SWAMP_HUT -> 5
            StructureType.MONUMENT -> 6
            StructureType.SHIPWRECK -> 7
            StructureType.OUTPOST -> 8
            StructureType.ANCIENT_CITY -> 9
            StructureType.TRIAL_CHAMBERS -> 10
            StructureType.MINESHAFT -> 11
            StructureType.TREASURE -> 12
            StructureType.RUINED_PORTAL -> 13
            StructureType.FORTRESS -> 14
            StructureType.END_CITY -> 15
            StructureType.SLIME_CHUNK,
            StructureType.JUNGLE_TEMPLE,
            StructureType.IGLOO,
            StructureType.OCEAN_RUIN,
            StructureType.DESERT_WELL,
            StructureType.GEODE,
            StructureType.ABANDONED_CAMP,
            StructureType.BASTION,
            StructureType.NETHER_FOSSIL,
            StructureType.END_GATEWAY,
            -> -1
        }
    )

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
