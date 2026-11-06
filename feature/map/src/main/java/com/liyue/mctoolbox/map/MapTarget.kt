package com.liyue.mctoolbox.map

import com.liyue.mctoolbox.seedgen.Dimension

/**
 * A jump request from another screen (for example the structure finder) into
 * the seed map. The map applies it once and then forgets it.
 */
data class MapTarget(
    val seed: Long,
    val seedText: String,
    val versionLabel: String,
    val dimension: Dimension,
    val centerX: Double,
    val centerZ: Double,
)
