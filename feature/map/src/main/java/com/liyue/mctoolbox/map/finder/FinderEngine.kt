package com.liyue.mctoolbox.map.finder

import com.liyue.mctoolbox.map.Edition
import com.liyue.mctoolbox.map.StructureType
import com.liyue.mctoolbox.seedgen.Dimension
import com.liyue.mctoolbox.seedgen.McVersion
import com.liyue.mctoolbox.seedgen.OreKind
import com.liyue.mctoolbox.seedgen.SeedEngine
import com.liyue.mctoolbox.seedgen.StructurePos
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

        val found = collectStructures(seed, version, edition, type, centerX, centerZ, maxRadius, limit)
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

    /**
     * Quad witch huts, as cubiomes-viewer reports them: the native side walks
     * the seed's quad-bases inside a window of 512-block structure regions and
     * rebuilds the four huts of each base, so the AFK position is exact.
     */
    suspend fun searchQuadHuts(
        seed: Long,
        version: McVersion,
        centerX: Int,
        centerZ: Int,
        radius: Int,
        limit: Int,
        edition: Edition = Edition.JAVA,
    ): List<FinderHit> = withContext(Dispatchers.Default) {
        if (edition.isBedrock) throw IllegalStateException("四女巫小屋只支持 Java 版算法")
        val quads = engine.quadHutsBlocking(
            seed = seed,
            version = version,
            centerX = centerX,
            centerZ = centerZ,
            radius = radius,
            maxQuads = 32,
        )
        quads.map { quad ->
            FinderHit(
                title = "四女巫小屋",
                x = quad.x,
                z = quad.z,
                distance = distance(quad.x, quad.z, centerX, centerZ),
                detail = "刷怪空间 ${quad.spawnSpaces} 格 · AFK 中心",
                dimension = Dimension.OVERWORLD,
            )
        }.sortedBy { it.distance }.take(limit)
    }

    /**
     * Viewer's "Locations" tab: run the same test against a whole list of
     * seeds, so a batch can be filtered down to the ones that qualify.
     */
    suspend fun runSeedQueue(
        seeds: List<Long>,
        version: McVersion,
        edition: Edition,
        type: StructureType,
        centerX: Int,
        centerZ: Int,
        radius: Int,
        minCount: Int,
        limit: Int,
    ): List<FinderHit> = withContext(Dispatchers.Default) {
        val hits = mutableListOf<FinderHit>()
        for (seed in seeds) {
            currentCoroutineContext().ensureActive()
            val found = collectStructures(
                seed = seed,
                version = version,
                edition = edition,
                type = type,
                centerX = centerX,
                centerZ = centerZ,
                maxRadius = minOf(radius, regionCapBlocks(type)),
                want = 64,
            )
            if (found.isEmpty()) continue
            if (found.size < minCount) continue
            val nearest = found.minByOrNull { distance(it.x, it.z, centerX, centerZ) } ?: continue
            hits += FinderHit(
                title = "$type.label × ${found.size}",
                x = nearest.x,
                z = nearest.z,
                distance = distance(nearest.x, nearest.z, centerX, centerZ),
                detail = "种子 $seed",
                dimension = type.dimension,
                seed = seed,
            )
            if (hits.size >= limit) break
        }
        hits.sortedBy { it.distance }
    }

    /** Grows the search box from 512 blocks until [want] hits or the cap. */
    private suspend fun collectStructures(
        seed: Long,
        version: McVersion,
        edition: Edition,
        type: StructureType,
        centerX: Int,
        centerZ: Int,
        maxRadius: Int,
        want: Int,
    ): List<SeedEngine.FeatureHit> = withContext(Dispatchers.Default) {
        val dimension = type.dimension
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
            if (hits.size >= want || current >= maxRadius) break
            current = minOf(current * 2, maxRadius)
        }
        hits.sortedBy { squaredDistance(it.x, it.z, centerX, centerZ) }.take(want)
    }

    /**
     * Chunkbase's "Golden Apple" app: roll the chests of every structure in
     * range and keep the ones that actually hold the item. Bedrock loot tables
     * are not modelled by the bundled engine, so this is Java only.
     */
    suspend fun searchChestLoot(
        seed: Long,
        version: McVersion,
        type: StructureType,
        centerX: Int,
        centerZ: Int,
        radius: Int,
        itemName: String,
        itemLabel: String,
        limit: Int,
        edition: Edition = Edition.JAVA,
    ): List<FinderHit> {
        if (!StructureSupport.isSupported(type, version)) {
            throw IllegalStateException("${type.label}在 ${version.label} 中不存在")
        }
        if (edition.isBedrock) throw IllegalStateException("宝箱战利品只支持 Java 版")
        val maxRadius = minOf(radius, regionCapBlocks(type))
        val structures = collectStructures(seed, version, edition, type, centerX, centerZ, maxRadius, 96)
        val hits = ArrayList<FinderHit>()
        withContext(Dispatchers.Default) {
            for (structure in structures) {
                val chests = engine.lootSearchBlocking(
                    seed = seed,
                    version = version,
                    dimension = type.dimension,
                    x = structure.x,
                    z = structure.z,
                    featureBit = type.featureBit,
                    itemName = itemName,
                )
                for (chest in chests) {
                    hits += FinderHit(
                        title = "$itemLabel ×${chest.count} · ${type.label}",
                        x = chest.x,
                        z = chest.z,
                        distance = distance(chest.x, chest.z, centerX, centerZ),
                        detail = "结构中心 ${structure.x}, ${structure.z}",
                        dimension = type.dimension,
                    )
                    if (hits.size >= limit) return@withContext
                }
            }
        }
        return hits.sortedBy { it.distance }.take(limit)
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

    /**
     * Underground lava pools. Below the lava line every carved cave cell is
     * filled with lava, so the finder carves a horizontal slice with the same
     * cave noise the game uses and clusters the open cells. This is how the
     * pools line up with what you actually walk into, and it costs roughly
     * 150 ms per 256x256 block tile (cave carving only, no canyon pass).
     */
    suspend fun searchLavaPools(
        seed: Long,
        version: McVersion,
        centerX: Int,
        centerZ: Int,
        radius: Int,
        limit: Int,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): List<FinderHit> = withContext(Dispatchers.Default) {
        val lavaY = if (version.ordinal >= McVersion.MC_1_18_2.ordinal) -54 else 10
        val span = radius.coerceIn(TILE_BLOCKS, LAVA_MAX_SPAN)
        val tilesPerSide = max(1, span / TILE_BLOCKS)
        val total = tilesPerSide * tilesPerSide
        val originX = Math.floorDiv(centerX - span / 2, TILE_BLOCKS) * TILE_BLOCKS
        val originZ = Math.floorDiv(centerZ - span / 2, TILE_BLOCKS) * TILE_BLOCKS
        val hits = mutableListOf<FinderHit>()
        var done = 0
        for (tileZ in 0 until tilesPerSide) {
            for (tileX in 0 until tilesPerSide) {
                currentCoroutineContext().ensureActive()
                val px = originX + tileX * TILE_BLOCKS
                val pz = originZ + tileZ * TILE_BLOCKS
                val rgba = engine.undergroundTileBlocking(
                    seed = seed,
                    version = version,
                    dimension = Dimension.OVERWORLD,
                    scale = TILE_SCALE,
                    x = px,
                    z = pz,
                    width = TILE_PIXELS,
                    height = TILE_PIXELS,
                    y = lavaY,
                    layers = LAYER_CAVE,
                )
                if (rgba != null) {
                    hits += poolClusters(rgba, px, pz, lavaY, centerX, centerZ)
                }
                done++
                onProgress(done, total)
            }
        }
        hits.sortedBy { it.distance }.take(limit)
    }

    /** Groups open cells of one tile into blobs and keeps their centres. */
    private fun poolClusters(
        rgba: ByteArray,
        originX: Int,
        originZ: Int,
        y: Int,
        centerX: Int,
        centerZ: Int,
    ): List<FinderHit> {
        val open = BooleanArray(TILE_PIXELS * TILE_PIXELS)
        for (index in 0 until TILE_PIXELS * TILE_PIXELS) {
            val r = rgba[index * 4].toInt() and 0xFF
            val g = rgba[index * 4 + 1].toInt() and 0xFF
            val b = rgba[index * 4 + 2].toInt() and 0xFF
            open[index] = r == CAVE_R && g == CAVE_G && b == CAVE_B
        }
        val seen = BooleanArray(open.size)
        val out = mutableListOf<FinderHit>()
        val queue = ArrayDeque<Int>()
        for (start in open.indices) {
            if (!open[start] || seen[start]) continue
            seen[start] = true
            queue.addLast(start)
            var sumX = 0L
            var sumZ = 0L
            var count = 0
            var minX = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE
            var minZ = Int.MAX_VALUE
            var maxZ = Int.MIN_VALUE
            while (queue.isNotEmpty()) {
                val cell = queue.removeFirst()
                val cx = cell % TILE_PIXELS
                val cz = cell / TILE_PIXELS
                val blockX = originX + cx * TILE_SCALE
                val blockZ = originZ + cz * TILE_SCALE
                sumX += blockX
                sumZ += blockZ
                count++
                if (blockX < minX) minX = blockX
                if (blockX > maxX) maxX = blockX
                if (blockZ < minZ) minZ = blockZ
                if (blockZ > maxZ) maxZ = blockZ
                for (neighbour in 0 until 4) {
                    val nx = cx + if (neighbour == 0) 1 else if (neighbour == 1) -1 else 0
                    val nz = cz + if (neighbour == 2) 1 else if (neighbour == 3) -1 else 0
                    if (nx < 0 || nz < 0 || nx >= TILE_PIXELS || nz >= TILE_PIXELS) continue
                    val next = nz * TILE_PIXELS + nx
                    if (!open[next] || seen[next]) continue
                    seen[next] = true
                    queue.addLast(next)
                }
            }
            // A single 4-block cell is a crack, not a pool worth walking to.
            if (count < MIN_POOL_CELLS) continue
            val cx = (sumX / count).toInt()
            val cz = (sumZ / count).toInt()
            out += FinderHit(
                title = "岩浆湖",
                x = cx,
                z = cz,
                distance = distance(cx, cz, centerX, centerZ),
                detail = "Y $y · 约 ${count * TILE_SCALE * TILE_SCALE} 格 · " +
                    "范围 ${maxX - minX + TILE_SCALE}×${maxZ - minZ + TILE_SCALE}",
                dimension = Dimension.OVERWORLD,
            )
        }
        return out
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

    /**
     * Chunkbase's "Ore Vein" app: find the large iron/copper veins that the
     * 1.18 world generator scatters. A vein is a noise-driven region that spans
     * several chunks, so chunks that contain vein blocks are joined into one
     * cluster and reported at its centroid.
     */
    fun searchOreVeins(
        seed: Long,
        version: McVersion,
        kind: OreKind,
        centerX: Int,
        centerZ: Int,
        radius: Int,
        limit: Int,
    ): List<FinderHit> {
        if (version < McVersion.MC_1_18_2) {
            throw IllegalStateException("矿脉需要 1.18 及以上")
        }
        val capped = radius.coerceIn(128, 768)
        val chunkRadius = capped / 16
        val cx = centerX / 16
        val cz = centerZ / 16
        val hits = ArrayList<Pair<Int, Int>>()
        for (dz in -chunkRadius..chunkRadius) {
            for (dx in -chunkRadius..chunkRadius) {
                if (dx * dx + dz * dz > chunkRadius * chunkRadius) continue
                val blocks = engine.oreScanBlocking(
                    seed = seed,
                    version = version,
                    dimension = Dimension.OVERWORLD,
                    chunkX = cx + dx,
                    chunkZ = cz + dz,
                    kind = kind,
                    maxBlocks = 64,
                )
                if (blocks.isNotEmpty()) hits += (cx + dx) to (cz + dz)
            }
        }
        return clusterChunks(hits)
            .map { cluster ->
                val bx = cluster.sumOf { it.first } * 16 / cluster.size + 8
                val bz = cluster.sumOf { it.second } * 16 / cluster.size + 8
                FinderHit(
                    title = "${kind.label} · ${cluster.size} 区块",
                    x = bx,
                    z = bz,
                    distance = distance(bx, bz, centerX, centerZ),
                    detail = "跨度约 ${cluster.size * 16} 方块",
                )
            }
            .sortedBy { it.distance }
            .take(limit)
    }

    /** Joins horizontal neighbours so one vein is reported once. */
    private fun clusterChunks(chunks: List<Pair<Int, Int>>): List<List<Pair<Int, Int>>> {
        val remaining = chunks.toMutableSet()
        val clusters = ArrayList<List<Pair<Int, Int>>>()
        while (remaining.isNotEmpty()) {
            val start = remaining.first()
            remaining.remove(start)
            val group = ArrayList<Pair<Int, Int>>()
            val queue = ArrayDeque<Pair<Int, Int>>()
            queue += start
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                group += current
                for (dx in -1..1) {
                    for (dz in -1..1) {
                        val neighbour = (current.first + dx) to (current.second + dz)
                        if (remaining.remove(neighbour)) queue += neighbour
                    }
                }
            }
            clusters += group
        }
        return clusters
    }

    /**
     * Finds dungeon rooms (see [SeedEngine.dungeonsBlocking]). Each one is an
     * exact decoration-feature placement, so the radius only bounds the area
     * that gets scanned; the cost grows with area, not with result count.
     */
    suspend fun searchDungeons(
        seed: Long,
        version: McVersion,
        centerX: Int,
        centerZ: Int,
        radius: Int,
        limit: Int,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): List<FinderHit> = withContext(Dispatchers.Default) {
        if (version.ordinal < McVersion.MC_1_18_2.ordinal) {
            throw IllegalStateException("地牢查找需要 1.18 及以上版本")
        }
        val span = radius.coerceIn(DUNGEON_MIN_SPAN, DUNGEON_MAX_SPAN)
        val steps = DUNGEON_STEPS
        val hits = mutableListOf<FinderHit>()
        val seen = HashSet<Long>()
        var done = 0
        for (step in 1..steps) {
            currentCoroutineContext().ensureActive()
            val r = span / steps * step
            val found = engine.dungeonsBlocking(
                seed = seed,
                version = version,
                minX = centerX - r,
                minZ = centerZ - r,
                maxX = centerX + r,
                maxZ = centerZ + r,
                maxCount = DUNGEON_CAP,
            )
            for (d in found) {
                val key = (d.x.toLong() shl 40) or (d.y.toLong() shl 20) or (d.z.toLong() and 0xFFFFF)
                if (!seen.add(key)) continue
                val dx = (d.x - centerX).toLong()
                val dz = (d.z - centerZ).toLong()
                hits += FinderHit(
                    title = d.kind.label,
                    x = d.x,
                    z = d.z,
                    distance = sqrt((dx * dx + dz * dz).toDouble()).toInt(),
                    detail = "Y ${d.y} · ${d.kind.label}刷怪笼",
                )
            }
            done++
            onProgress(done, steps)
            if (hits.size >= limit) break
        }
        hits.sortedBy { it.distance }.take(limit)
    }

    companion object {
    private const val DUNGEON_MIN_SPAN = 128
    private const val DUNGEON_MAX_SPAN = 1024
    private const val DUNGEON_STEPS = 4
    private const val DUNGEON_CAP = 512
        private const val TILE_BLOCKS = 256
        private const val TILE_PIXELS = 64
        private const val TILE_SCALE = 4
        private const val LAYER_CAVE = 2
        private const val LAVA_MAX_SPAN = 1024
        private const val MIN_POOL_CELLS = 6
        private const val CAVE_R = 68
        private const val CAVE_G = 170
        private const val CAVE_B = 214

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
