package com.liyue.mctoolbox.map

/**
 * Hands the seed map's current seed and version to the finder when it is opened
 * from inside the map, so the user does not retype them.
 */
object FinderLaunch {
    var seedText: String? = null
    var versionLabel: String? = null

    fun take(): Pair<String?, String?> {
        val seed = seedText
        val version = versionLabel
        seedText = null
        versionLabel = null
        return seed to version
    }
}
