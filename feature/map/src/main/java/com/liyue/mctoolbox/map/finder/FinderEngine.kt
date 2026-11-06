package com.liyue.mctoolbox.map.finder

import com.liyue.mctoolbox.map.Edition
import com.liyue.mctoolbox.map.StructureType
import com.liyue.mctoolbox.seedgen.Dimension
import com.liyue.mctoolbox.seedgen.McVersion
import com.liyue.mctoolbox.seedgen.SeedEngine
import com.liyue.mctoolbox.seedgen.StructurePos
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pure search logic behind the finder screen. Kept free of Android types so it
 * can be unit tested on the host JVM against the same native library the app
 * ships (see FinderEngineTest).
 */
class FinderEngine(private val engine: SeedEngine) {

    fun biomeOptions(version: McVersion): List<BiomeOption> = BiomeCatalog.options(engine, version)

    /** Nearest structures of one type, nearest first. */
    suspend fun searchStructures(
        seed: Long,
        version: McVersion,
        edition: Edition = Edition.JAVA,
        type: StructureType,
        centerX: Int,
        centerZ: Int,
        radius: Int,
        limit: Int,
    ): List<FinderHit> {
        if (!StructureSupport.isSupported(type, version)) {
            throw IllegalStateException("${type.label}在 ${version.label} 中不存在")
        }
        val dimension = type.dimension
        val maxRadius = minOf(radius, regionCapBlocks(type))

        val found = withContext(Dispatchers.Default) {
            var current = 512
            var hits = emptyList<SeedEngine.FeatureHit>()
            while (true) {
                val raw = if (edition.isBedrock) {
                    engine.bedrockFeaturesBlocking(
                        seed = seed,
                        version = version,
                        dimension = dimension,
                        minBlockX = centerX - current,
                        minBlockZ = centerZ - current,
                        maxBlockX = centerX + current,
                        maxBlockZ = centerZ + current,
                        featureMask = type.featureBit,
                        maxCount = 4096,
                    )
                } else {
                    engine.findFeaturesBlocking(
                        seed = seed,
                        version = version,
                        dimension = dimension,
                        minBlockX = centerX - current,
                        minBlockZ = centerZ - current,
                        maxBlockX = centerX + current,
                        maxBlockZ = centerZ + current,
                        featureMask = type.featureBit,
                        maxCount = 4096,
                    )
                }
                hits = raw.filter { it.kind == type.featureBit }
                if (hits.size >= limit || current >= maxRadius) break
                current = minOf(current * 2, maxRadius)
            }
            hits.sortedBy { squaredDistance(it.x, it.z, centerX, centerZ) }.take(limit)
        }

        return found.map { hit ->
            val sample = engine.biomeAt(seed, version, dimension, hit.x, hit.z)
            FinderHit(
                title = type.label,
                x = hit.x,
                z = hit.z,
                distance = distance(hit.x, hit.z, centerX, centerZ),
                detail = buildString {
                    append(sample.name)
                    if (hit.endShip) append(" · 带末地船")
                },
                dimension = dimension,
            )
        }
    }

    /** Nearest occurrences of a biome, refined to a 4-block grid sample. */
    suspend fun searchBiomes(
        seed: Long,
        version: McVersion,
        dimension: Dimension,
        option: BiomeOption,
        centerX: Int,
        centerZ: Int,
        radius: Int,
        limit: Int,
    ): List<FinderHit> {
        // Adaptive sampling: keep the coarse grid at roughly 128x128 samples no
        // matter how large the radius is, then refine each hit at 4-block
        // precision. Without this a 16k radius would mean millions of exact
        // biome queries and a frozen UI.
        val cell = sampleCellBlocks(radius)
        val side = max(2, (radius * 2) / cell)
        val cells = withContext(Dispatchers.Default) {
            engine.biomeGridBlocking(
                seed = seed,
                version = version,
                dimension = dimension,
                originBlockX = centerX - radius,
                originBlockZ = centerZ - radius,
                width = side,
                height = side,
                cellBlocks = cell,
            )
        }

        data class Candidate(val x: Int, val z: Int, val d: Int)

        val candidates = ArrayList<Candidate>()
        for (gz in 0 until side) {
            val baseZ = centerZ - radius + gz * cell + cell / 2
            for (gx in 0 until side) {
                if (cells[gz * side + gx] != option.id) continue
                val baseX = centerX - radius + gx * cell + cell / 2
                candidates.add(Candidate(baseX, baseZ, distance(baseX, baseZ, centerX, centerZ)))
            }
        }
        candidates.sortBy { it.d }

        val accepted = ArrayList<FinderHit>()
        val kept = ArrayList<IntArray>()
        val keepApart = cell * 4.0
        for (candidate in candidates) {
            if (accepted.size >= limit) break
            val tooClose = kept.any {
                hypot((it[0] - candidate.x).toDouble(), (it[1] - candidate.z).toDouble()) < keepApart
            }
            if (tooClose) continue
            kept.add(intArrayOf(candidate.x, candidate.z))
            val refined = withContext(Dispatchers.Default) {
                refineBiome(seed, version, dimension, option.id, candidate.x, candidate.z, cell)
            }
            val px = refined?.first ?: candidate.x
            val pz = refined?.second ?: candidate.z
            accepted.add(
                FinderHit(
                    title = option.label,
                    x = px,
                    z = pz,
                    distance = distance(px, pz, centerX, centerZ),
                    detail = option.name,
                    dimension = dimension,
                ),
            )
        }
        return accepted
    }

    /** Narrows a coarse biome hit down to the closest matching 4-block sample. */
    private fun refineBiome(
        seed: Long,
        version: McVersion,
        dimension: Dimension,
        biomeId: Int,
        centerX: Int,
        centerZ: Int,
        coarseCell: Int,
    ): Pair<Int, Int>? {
        // Probe the coarse cell at 4-block precision, but never more than a
        // 16x16 window so a 1024-block coarse cell stays cheap.
        val cell = 4
        val side = (coarseCell / cell).coerceIn(4, 16)
        val originX = centerX - (side * cell) / 2
        val originZ = centerZ - (side * cell) / 2
        val ids = engine.biomeGridBlocking(
            seed = seed,
            version = version,
            dimension = dimension,
            originBlockX = originX,
            originBlockZ = originZ,
            width = side,
            height = side,
            cellBlocks = cell,
        )
        var best: Pair<Int, Int>? = null
        var bestDistance = Int.MAX_VALUE
        for (gz in 0 until side) {
            for (gx in 0 until side) {
                if (ids[gz * side + gx] != biomeId) continue
                val x = originX + gx * cell + cell / 2
                val z = originZ + gz * cell + cell / 2
                val d = abs(x - centerX) + abs(z - centerZ)
                if (d < bestDistance) {
                    bestDistance = d
                    best = x to z
                }
            }
        }
        return best
    }

    suspend fun searchStrongholds(
        seed: Long,
        version: McVersion,
        edition: Edition = Edition.JAVA,
        centerX: Int,
        centerZ: Int,
        limit: Int,
    ): List<FinderHit> {
        val positions = withContext(Dispatchers.Default) {
            if (edition.isBedrock) {
                engine.bedrockStrongholdsBlocking(seed).map { StructurePos(it.first, it.second) }
            } else {
                engine.strongholdsBlocking(seed, version, 64)
            }
        }
        return positions
            .sortedBy { squaredDistance(it.x, it.z, centerX, centerZ) }
            .take(limit)
            .mapIndexed { index, pos ->
                FinderHit(
                    title = "要塞 #${index + 1}",
                    x = pos.x,
                    z = pos.z,
                    distance = distance(pos.x, pos.z, centerX, centerZ),
                    dimension = Dimension.OVERWORLD,
                )
            }
    }

    suspend fun searchSlimeChunks(
        seed: Long,
        centerX: Int,
        centerZ: Int,
        radius: Int,
        limit: Int,
    ): List<FinderHit> {
        val centerChunkX = Math.floorDiv(centerX, 16)
        val centerChunkZ = Math.floorDiv(centerZ, 16)
        val chunkRadius = (radius / 16).coerceIn(4, 512)
        val positions = withContext(Dispatchers.Default) {
            engine.slimeChunksBlocking(
                seed = seed,
                chunkX0 = centerChunkX - chunkRadius,
                chunkZ0 = centerChunkZ - chunkRadius,
                chunkX1 = centerChunkX + chunkRadius,
                chunkZ1 = centerChunkZ + chunkRadius,
                maxCount = 4096,
            )
        }
        return positions
            .sortedBy { squaredDistance(it.x, it.z, centerX, centerZ) }
            .take(limit)
            .map { pos ->
                FinderHit(
                    title = "史莱姆区块",
                    x = pos.x,
                    z = pos.z,
                    distance = distance(pos.x, pos.z, centerX, centerZ),
                    detail = "区块 ${Math.floorDiv(pos.x, 16)}, ${Math.floorDiv(pos.z, 16)} · y<40",
                    dimension = Dimension.OVERWORLD,
                )
            }
    }

    suspend fun searchSpawn(seed: Long, version: McVersion): List<FinderHit> {
        val spawn = withContext(Dispatchers.Default) { engine.spawnPointBlocking(seed, version) }
            ?: throw IllegalStateException("该版本无法计算出生点")
        return listOf(
            FinderHit(
                title = "出生点",
                x = spawn.x,
                z = spawn.z,
                distance = distance(spawn.x, spawn.z, 0, 0),
                detail = "距原点",
                dimension = Dimension.OVERWORLD,
            ),
        )
    }

    companion object {
        /**
         * Vanilla structure spacing in chunks. Used to bound the search area:
         * one-chunk structures such as geodes would otherwise scan millions of
         * regions.
         */
        fun regionSizeChunks(type: StructureType): Int = when (type) {
            StructureType.MANSION -> 80
            StructureType.RUINED_PORTAL -> 40
            StructureType.VILLAGE, StructureType.TRAIL_RUINS,
            StructureType.TRIAL_CHAMBERS, StructureType.ABANDONED_CAMP -> 34
            StructureType.OUTPOST, StructureType.MONUMENT, StructureType.DESERT_PYRAMID,
            StructureType.JUNGLE_TEMPLE, StructureType.SWAMP_HUT, StructureType.IGLOO,
            StructureType.DESERT_WELL -> 32
            StructureType.FORTRESS, StructureType.BASTION -> 27
            StructureType.SHIPWRECK, StructureType.ANCIENT_CITY -> 24
            StructureType.OCEAN_RUIN, StructureType.END_CITY -> 20
            StructureType.END_GATEWAY -> 16
            else -> 1
        }

        /**
         * Upper bound for a structure search radius in blocks. Types that can
         * spawn in nearly every chunk (region size 1) get a much smaller cap so
         * the scan stays interactive.
         */
        fun regionCapBlocks(type: StructureType): Int {
            val chunks = regionSizeChunks(type)
            if (chunks <= 1) return 512
            return minOf(16384, chunks * 16 * 128)
        }

        /** Coarse sampling cell for a biome search radius (2^k blocks). */
        fun sampleCellBlocks(radius: Int): Int {
            var cell = 16
            while (cell < 256 && (radius * 2) / cell > 128) cell *= 2
            return cell
        }

        fun distance(x: Int, z: Int, cx: Int, cz: Int): Int =
            hypot((x - cx).toDouble(), (z - cz).toDouble()).toInt()

        fun squaredDistance(x: Int, z: Int, cx: Int, cz: Int): Long {
            val dx = (x - cx).toLong()
            val dz = (z - cz).toLong()
            return dx * dx + dz * dz
        }
    }
}
