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
}
