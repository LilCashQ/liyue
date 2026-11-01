package com.liyue.mctoolbox.seedgen

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Thin Kotlin surface over the cubiomes JNI bridge. All calls are blocking and
 * must stay off the main thread; the public API hands work to [dispatcher].
 */
class SeedEngine(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    suspend fun biomeGrid(
        seed: Long,
        version: McVersion,
        dimension: Dimension,
        centerBlockX: Int,
        centerBlockZ: Int,
        width: Int,
        height: Int,
        cellBlocks: Int = CELL_BLOCKS,
        y: Int = DEFAULT_Y,
    ): BiomeGrid = withContext(dispatcher) {
        require(cellBlocks == CELL_BLOCKS) { "only 4-block cells are supported for now" }
        // biome cells are aligned to the world block grid; the native layer picks
        // the exact voronoi path (1.18+) or the 1:4 layer path (older versions).
        val originBlockX = Math.floorDiv(centerBlockX - width * CELL_BLOCKS / 2, CELL_BLOCKS) * CELL_BLOCKS
        val originBlockZ = Math.floorDiv(centerBlockZ - height * CELL_BLOCKS / 2, CELL_BLOCKS) * CELL_BLOCKS
        val ids = IntArray(width * height)
        val err = NativeSeedgen.biomeGrid(
            seed = seed,
            version = version.id,
            dim = dimension.id,
            blockX = originBlockX,
            blockZ = originBlockZ,
            w = width,
            h = height,
            cellBlocks = CELL_BLOCKS,
            y = y,
            out = ids,
        )
        check(err == 0) { "genBiomes failed with code $err" }
        BiomeGrid(width, height, CELL_BLOCKS, ids)
    }

    suspend fun biomeAt(
        seed: Long,
        version: McVersion,
        dimension: Dimension,
        blockX: Int,
        blockZ: Int,
        y: Int = DEFAULT_Y,
    ): BiomeSample = withContext(dispatcher) {
        val id = NativeSeedgen.biomeAt(seed, version.id, dimension.id, 1, blockX, y, blockZ)
        BiomeSample(id, biomeName(version, id))
    }

    fun biomeName(version: McVersion, biomeId: Int): String =
        NativeSeedgen.biomeName(version.id, biomeId) ?: "unknown($biomeId)"

    /** Blocking grid render used by the map renderer from background threads. */
    fun biomeGridBlocking(
        seed: Long,
        version: McVersion,
        dimension: Dimension,
        originBlockX: Int,
        originBlockZ: Int,
        width: Int,
        height: Int,
        cellBlocks: Int,
        y: Int = DEFAULT_Y,
    ): IntArray {
        val ids = IntArray(width * height)
        val err = NativeSeedgen.biomeGrid(
            seed = seed,
            version = version.id,
            dim = dimension.id,
            blockX = originBlockX,
            blockZ = originBlockZ,
            w = width,
            h = height,
            cellBlocks = cellBlocks,
            y = y,
            out = ids,
        )
        check(err == 0) { "genBiomes failed with code $err" }
        return ids
    }

    /**
     * Blocking structure enumeration used by the map layer. Returns an empty
     * list when the type is unsupported for the version/dimension.
     */
    fun structurePositionsBlocking(
        seed: Long,
        version: McVersion,
        dimension: Dimension,
        structureType: Int,
        blockX0: Int,
        blockZ0: Int,
        blockX1: Int,
        blockZ1: Int,
        maxCount: Int = 512,
    ): List<StructurePos> {
        val out = IntArray(maxCount * 2)
        val count = NativeSeedgen.structurePositions(
            seed = seed,
            version = version.id,
            dim = dimension.id,
            type = structureType,
            x0 = blockX0,
            z0 = blockZ0,
            x1 = blockX1,
            z1 = blockZ1,
            maxCount = maxCount,
            out = out,
        )
        if (count <= 0) return emptyList()
        return (0 until count).map { StructurePos(out[it * 2], out[it * 2 + 1]) }
    }

    fun strongholdsBlocking(seed: Long, version: McVersion, maxCount: Int = 64): List<StructurePos> {
        val out = IntArray(maxCount * 2)
        val count = NativeSeedgen.strongholds(seed, version.id, maxCount, out)
        if (count <= 0) return emptyList()
        return (0 until count).map { StructurePos(out[it * 2], out[it * 2 + 1]) }
    }

    fun slimeChunksBlocking(
        seed: Long,
        chunkX0: Int,
        chunkZ0: Int,
        chunkX1: Int,
        chunkZ1: Int,
        maxCount: Int = 2048,
    ): List<StructurePos> {
        val out = IntArray(maxCount * 2)
        val count = NativeSeedgen.slimeChunks(seed, chunkX0, chunkZ0, chunkX1, chunkZ1, maxCount, out)
        if (count <= 0) return emptyList()
        return (0 until count).map { StructurePos(out[it * 2], out[it * 2 + 1]) }
    }

    data class FeatureHit(val x: Int, val z: Int, val kind: Int, val endShip: Boolean)

    /**
     * Unified structure search backed by the bundled Axolotl bridge: covers all
     * structure types, strongholds, slime chunks, mineshafts, fossils and end
     * ships with the same biome/terrain validation as the reference app.
     */
    fun findFeaturesBlocking(
        seed: Long,
        version: McVersion,
        dimension: Dimension,
        minBlockX: Int,
        minBlockZ: Int,
        maxBlockX: Int,
        maxBlockZ: Int,
        featureMask: Int,
        maxCount: Int = 4096,
    ): List<FeatureHit> {
        val out = IntArray(maxCount * 4)
        val count = NativeSeedgen.findFeatures(
            seed = seed,
            version = version.id,
            dim = dimension.id,
            minX = minBlockX,
            minZ = minBlockZ,
            maxX = maxBlockX,
            maxZ = maxBlockZ,
            featureMask = featureMask,
            out = out,
        )
        if (count <= 0) return emptyList()
        return (0 until count).map {
            FeatureHit(
                x = out[it * 4],
                z = out[it * 4 + 1],
                kind = out[it * 4 + 2],
                endShip = out[it * 4 + 3] > 0,
            )
        }
    }

    fun renderTileBlocking(
        seed: Long,
        version: McVersion,
        dimension: Dimension,
        scale: Int,
        x: Int,
        z: Int,
        width: Int,
        height: Int,
        elevation: Int = 63,
        terrain: Boolean = true,
        contours: Boolean = true,
        highlightMask: ByteArray? = null,
    ): ByteArray {
        val rgb = ByteArray(width * height * 3)
        val status = NativeSeedgen.renderTile(
            seed, version.id, dimension.id, scale, x, z, width, height,
            elevation, if (terrain) 1 else 0, if (contours) 1 else 0,
            highlightMask, rgb,
        )
        check(status == 0) { "renderTile failed with $status" }
        return rgb
    }

    fun mineshaftsBlocking(
        seed: Long,
        version: McVersion,
        chunkX0: Int,
        chunkZ0: Int,
        chunkX1: Int,
        chunkZ1: Int,
        maxCount: Int = 256,
    ): List<StructurePos> {
        val out = IntArray(maxCount * 2)
        val count = NativeSeedgen.mineshafts(
            seed, version.id, chunkX0, chunkZ0, chunkX1, chunkZ1, maxCount, out,
        )
        if (count <= 0) return emptyList()
        return (0 until count).map { StructurePos(out[it * 2], out[it * 2 + 1]) }
    }

    fun spawnPointBlocking(seed: Long, version: McVersion): StructurePos? {
        val out = IntArray(2)
        return if (NativeSeedgen.spawnPoint(seed, version.id, out) == 0) {
            StructurePos(out[0], out[1])
        } else {
            null
        }
    }

    companion object {
        const val DEFAULT_Y = 63
        const val CELL_BLOCKS = 4

        /**
         * Java Edition seed parsing: a decimal long is used verbatim, any other
         * text is folded through `String.hashCode()` and sign-extended, which is
         * what the vanilla client does.
         */
        fun parseJavaSeed(text: String): Long {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return 0L
            trimmed.toLongOrNull()?.let { return it }
            return trimmed.hashCode().toLong()
        }
    }
}

data class BiomeSample(val id: Int, val name: String) {
    val isKnown: Boolean get() = id >= 0
}

data class StructurePos(val x: Int, val z: Int)
