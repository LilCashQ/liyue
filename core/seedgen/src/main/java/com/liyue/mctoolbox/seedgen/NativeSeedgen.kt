package com.liyue.mctoolbox.seedgen

internal object NativeSeedgen {
    init {
        val explicit = System.getProperty("liyue.nativeLib")
        if (explicit != null) {
            System.load(explicit)
        } else {
            System.loadLibrary("liyue_seedgen")
        }
    }

    external fun versionCount(): Int
    external fun cubiomesVersion(version: Int): Int
    external fun biomeAt(
        seed: Long,
        version: Int,
        dim: Int,
        scale: Int,
        x: Int,
        y: Int,
        z: Int,
    ): Int

    external fun biomeGrid(
        seed: Long,
        version: Int,
        dim: Int,
        blockX: Int,
        blockZ: Int,
        w: Int,
        h: Int,
        cellBlocks: Int,
        y: Int,
        out: IntArray,
    ): Int

    external fun biomeName(version: Int, biomeId: Int): String?

    external fun findFeatures(
        seed: Long,
        version: Int,
        dim: Int,
        minX: Int,
        minZ: Int,
        maxX: Int,
        maxZ: Int,
        featureMask: Int,
        out: IntArray,
    ): Int

    /** Paints one map layer: 0..4 climate parameters, 5 height. */
    external fun renderTileLayer(
        seed: Long,
        version: Int,
        layer: Int,
        scale: Int,
        x: Int,
        z: Int,
        width: Int,
        height: Int,
        outRgb: ByteArray,
    ): Int

    external fun renderTile(
        seed: Long,
        version: Int,
        dim: Int,
        scale: Int,
        x: Int,
        z: Int,
        width: Int,
        height: Int,
        elevation: Int,
        terrain: Int,
        contours: Int,
        highlightMask: ByteArray?,
        outRgb: ByteArray,
    ): Int

    external fun renderTileShaded(
        seed: Long,
        version: Int,
        dim: Int,
        scale: Int,
        x: Int,
        z: Int,
        width: Int,
        height: Int,
        elevation: Int,
        contours: Int,
        heightCell: Int,
        highlightMask: ByteArray?,
        outRgb: ByteArray,
    ): Int

    external fun seedMapCapability(version: Int, capability: Int, value: Int): Int

    external fun strongholds(
        seed: Long,
        version: Int,
        maxCount: Int,
        out: IntArray,
    ): Int

    external fun slimeChunks(
        seed: Long,
        chunkX0: Int,
        chunkZ0: Int,
        chunkX1: Int,
        chunkZ1: Int,
        maxCount: Int,
        out: IntArray,
    ): Int

    external fun mineshafts(
        seed: Long,
        version: Int,
        chunkX0: Int,
        chunkZ0: Int,
        chunkX1: Int,
        chunkZ1: Int,
        maxCount: Int,
        out: IntArray,
    ): Int

    external fun spawnPoint(seed: Long, version: Int, out: IntArray): Int

    external fun scanOres(
        seed: Long,
        version: Int,
        dim: Int,
        chunkX: Int,
        chunkZ: Int,
        kind: Int,
        out: IntArray,
    ): Int

    external fun biomeAtRaw(
        seed: Long,
        version: Int,
        dim: Int,
        x: Int,
        y: Int,
        z: Int,
    ): Int

    external fun structurePositions(
        seed: Long,
        version: Int,
        dim: Int,
        type: Int,
        x0: Int,
        z0: Int,
        x1: Int,
        z1: Int,
        maxCount: Int,
        out: IntArray,
    ): Int

    /**
     * Structure details as a JSON document describing the variant (rotation,
     * biome, bounding box) and, where the engine can lay them out, the
     * individual pieces and their loot containers. Null when the position has
     * nothing to describe.
     */
    external fun structureDetails(
        seed: Long,
        version: Int,
        dim: Int,
        x: Int,
        z: Int,
        featureBit: Int,
        container: Int,
    ): String?

    /**
     * Renders one underground tile as RGBA. [layers] is a 3-bit mask:
     * 1 = solid blocks, 2 = carved caves, 4 = carved canyons. Width and height
     * must be multiples of 16 and at most 64.
     */
    external fun undergroundTile(
        seed: Long,
        version: Int,
        dim: Int,
        scale: Int,
        x: Int,
        z: Int,
        width: Int,
        height: Int,
        y: Int,
        layers: Int,
        outRgba: ByteArray,
    ): Int

    /**
     * Bedrock Edition structure search. [feature] is one structure flag
     * (1 shl n, matching the map layer bits); [version] selects the world
     * generation used for the biome checks, since Bedrock and Java share the
     * Overworld biome generator from 1.18 on. Results are written as x/z pairs
     * and the number of structures is returned.
     */
    external fun bedrockFeatures(
        seed: Long,
        version: Int,
        dimension: Int,
        feature: Int,
        minX: Int,
        minZ: Int,
        maxX: Int,
        maxZ: Int,
        out: IntArray,
    ): Int

    /** Bedrock stronghold ring: writes up to three x/z pairs, returns the count. */
    external fun bedrockStrongholds(seed: Long, out: IntArray): Int

    /**
     * Enumerates monster rooms (dungeons) in a block rectangle.
     * Writes quadruplets (x, y, z, type) and returns the number of dungeons;
     * type is 0 zombie, 1 spider, 2 skeleton.
     */
    external fun dungeons(
        seed: Long,
        version: Int,
        minX: Int,
        minZ: Int,
        maxX: Int,
        maxZ: Int,
        out: IntArray,
    ): Int

    /**
     * Approximate Overworld surface height on the 1:4 grid, in blocks.
     * [x4] and [z4] are 1:4 scale coordinates; writes width * height floats.
     */
    external fun surfaceHeights(
        seed: Long,
        version: Int,
        x4: Int,
        z4: Int,
        width: Int,
        height: Int,
        outHeights: FloatArray,
    ): Int

    /** Chests of one structure that contain [itemName]; triples of x, z, count. */
    external fun lootSearch(
        seed: Long,
        version: Int,
        dim: Int,
        x: Int,
        z: Int,
        featureBit: Int,
        itemName: String,
        out: IntArray,
    ): Int

    external fun quadHuts(
        seed: Long,
        version: Int,
        minRegionX: Int,
        minRegionZ: Int,
        maxRegionX: Int,
        maxRegionZ: Int,
        out: IntArray,
    ): Int
}
