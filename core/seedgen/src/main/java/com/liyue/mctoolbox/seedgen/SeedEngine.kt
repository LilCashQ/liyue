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

    /**
     * Bedrock Edition structure search inside a block-space box. Bedrock rolls
     * its regions with a Mersenne Twister instead of the Java LCG, so the
     * feature mask is expanded one type at a time. Types the Bedrock generator
     * cannot place are skipped instead of approximated.
     */
    fun bedrockFeaturesBlocking(
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
        val bits = BEDROCK_FEATURES.filter { featureMask and it != 0 }
        if (bits.isEmpty()) return emptyList()
        val result = ArrayList<FeatureHit>(minOf(maxCount, 256))
        val out = IntArray(4096)
        for (bit in bits) {
            val want = maxCount - result.size
            if (want <= 0) break
            val count = NativeSeedgen.bedrockFeatures(
                seed = seed,
                version = version.id,
                dimension = dimension.id,
                feature = bit,
                minX = minBlockX,
                minZ = minBlockZ,
                maxX = maxBlockX,
                maxZ = maxBlockZ,
                out = out,
            )
            if (count <= 0) continue
            for (i in 0 until minOf(count, want)) {
                result.add(FeatureHit(x = out[i * 2], z = out[i * 2 + 1], kind = bit, endShip = false))
            }
        }
        return result
    }

    /** Bedrock strongholds: a ring around the origin instead of Java's concentric placing. */
    fun bedrockStrongholdsBlocking(seed: Long): List<Pair<Int, Int>> {
        val out = IntArray(6)
        val count = NativeSeedgen.bedrockStrongholds(seed, out)
        if (count <= 0) return emptyList()
        return (0 until count).map { out[it * 2] to out[it * 2 + 1] }
    }

    enum class DungeonKind(val label: String) {
        ZOMBIE("僵尸地牢"),
        SPIDER("蜘蛛地牢"),
        SKELETON("骷髅地牢"),
    }

    data class DungeonHit(val x: Int, val y: Int, val z: Int, val kind: DungeonKind)

    /**
     * Finds monster rooms (dungeons) in the given block rectangle. Requires
     * 1.18 or newer; older versions return an empty list.
     */
    fun dungeonsBlocking(
        seed: Long,
        version: McVersion,
        minX: Int,
        minZ: Int,
        maxX: Int,
        maxZ: Int,
        maxCount: Int = 512,
    ): List<DungeonHit> {
        if (version.ordinal < McVersion.MC_1_18_2.ordinal) return emptyList()
        val out = IntArray(maxCount.coerceAtLeast(1) * 4)
        val count = NativeSeedgen.dungeons(seed, version.id, minX, minZ, maxX, maxZ, out)
        if (count <= 0) return emptyList()
        val kinds = DungeonKind.entries
        return (0 until minOf(count, out.size / 4)).map {
            val kind = out[it * 4 + 3]
            DungeonHit(
                out[it * 4],
                out[it * 4 + 1],
                out[it * 4 + 2],
                kinds.getOrElse(kind) { DungeonKind.ZOMBIE },
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
        val status = if (terrain && dimension == Dimension.OVERWORLD) {
            NativeSeedgen.renderTileShaded(
                seed, version.id, dimension.id, scale, x, z, width, height,
                elevation, if (contours) 1 else 0, scale * 2,
                highlightMask, rgb,
            )
        } else {
            NativeSeedgen.renderTile(
                seed, version.id, dimension.id, scale, x, z, width, height,
                elevation, 0, if (contours) 1 else 0,
                highlightMask, rgb,
            )
        }
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

    /**
     * Scans one chunk for one ore kind. Returns the individual ore blocks, or
     * an empty list when the ore does not exist in this version/dimension.
     */
    fun oreScanBlocking(
        seed: Long,
        version: McVersion,
        dimension: Dimension,
        chunkX: Int,
        chunkZ: Int,
        kind: OreKind,
        maxBlocks: Int = 4096,
    ): List<OreBlock> {
        val out = IntArray(maxBlocks * 3)
        val count = NativeSeedgen.scanOres(
            seed = seed,
            version = version.id,
            dim = dimension.id,
            chunkX = chunkX,
            chunkZ = chunkZ,
            kind = kind.group,
            out = out,
        )
        if (count <= 0) return emptyList()
        return (0 until count).map { OreBlock(out[it * 3], out[it * 3 + 1], out[it * 3 + 2]) }
    }

    /** Raw-Y biome sample (block coordinates, no 1:4 scaling). */
    fun biomeAtRawBlocking(
        seed: Long,
        version: McVersion,
        dimension: Dimension,
        x: Int,
        y: Int,
        z: Int,
    ): Int = NativeSeedgen.biomeAtRaw(seed, version.id, dimension.id, x, y, z)

    fun spawnPointBlocking(seed: Long, version: McVersion): StructurePos? {
        val out = IntArray(2)
        return if (NativeSeedgen.spawnPoint(seed, version.id, out) == 0) {
            StructurePos(out[0], out[1])
        } else {
            null
        }
    }

    /**
     * Structure details as a JSON document, or null when the engine cannot
     * describe the position. See cubiomes_details.c for the shape.
     *
     * [featureBit] is the single-bit feature mask of the structure kind;
     * [container] selects one loot container to resolve instead of the full
     * layout (pass -1 for the piece list).
     */
    fun structureDetailsBlocking(
        seed: Long,
        version: McVersion,
        dimension: Dimension,
        x: Int,
        z: Int,
        featureBit: Int,
        container: Int = -1,
    ): String? = NativeSeedgen.structureDetails(
        seed = seed,
        version = version.id,
        dim = dimension.id,
        x = x,
        z = z,
        featureBit = featureBit,
        container = container,
    )

    /**
     * Renders one underground tile as RGBA bytes, or null when the version,
     * dimension or bounds are unsupported.
     */
    fun undergroundTileBlocking(
        seed: Long,
        version: McVersion,
        dimension: Dimension,
        scale: Int,
        x: Int,
        z: Int,
        width: Int,
        height: Int,
        y: Int,
        layers: Int,
    ): ByteArray? {
        val out = ByteArray(width * height * 4)
        val status = NativeSeedgen.undergroundTile(
            seed = seed,
            version = version.id,
            dim = dimension.id,
            scale = scale,
            x = x,
            z = z,
            width = width,
            height = height,
            y = y,
            layers = layers,
            outRgba = out,
        )
        return if (status == 0) out else null
    }

    /** One chest that holds the searched item: block position plus stack size. */
    data class LootChest(val x: Int, val z: Int, val count: Int)

    /**
     * Rolls every chest of the structure at (x, z) and keeps the ones that hold
     * [itemName]. This is the same loot engine the structure detail sheet uses,
     * so the answer matches what the game would generate.
     */
    fun lootSearchBlocking(
        seed: Long,
        version: McVersion,
        dimension: Dimension,
        x: Int,
        z: Int,
        featureBit: Int,
        itemName: String,
        maxChests: Int = 32,
    ): List<LootChest> {
        val out = IntArray(maxChests * 3)
        val count = NativeSeedgen.lootSearch(
            seed = seed,
            version = version.id,
            dim = dimension.id,
            x = x,
            z = z,
            featureBit = featureBit,
            itemName = itemName,
            out = out,
        )
        if (count <= 0) return emptyList()
        return (0 until count).map { i ->
            LootChest(out[i * 3], out[i * 3 + 1], out[i * 3 + 2])
        }
    }

    /**
     * Approximate terrain height at a block position, or null when the version
     * has no height model. The native side works on the 1:4 grid, so the value
     * is snapped to four blocks.
     */
    fun surfaceHeightBlocking(
        seed: Long,
        version: McVersion,
        blockX: Int,
        blockZ: Int,
    ): Int? {
        val out = FloatArray(1)
        val x4 = Math.floorDiv(blockX, 4)
        val z4 = Math.floorDiv(blockZ, 4)
        val status = NativeSeedgen.surfaceHeights(seed, version.id, x4, z4, 1, 1, out)
        if (status != 0) return null
        return out[0].toInt()
    }

    companion object {
        /**
         * Feature bits the Bedrock generator can actually place. Slime chunks,
         * geodes, mineshafts, desert wells, ancient cities, trial chambers,
         * trail ruins, abandoned camps, nether fossils and end gateways are
         * Java-driven noise features without a Bedrock equivalent in our port.
         */
        private val BEDROCK_FEATURES = intArrayOf(
            1 shl 0, 1 shl 1, 1 shl 2, 1 shl 3, 1 shl 4, 1 shl 8, 1 shl 11,
            1 shl 12, 1 shl 13, 1 shl 14, 1 shl 15, 1 shl 16, 1 shl 20,
            1 shl 21, 1 shl 22,
        )

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
