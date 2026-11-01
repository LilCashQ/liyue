package com.liyue.mctoolbox.map

import com.liyue.mctoolbox.seedgen.McVersion

/**
 * User-facing Minecraft versions. Several minor releases share one world
 * generation family, so they map to the same cubiomes algorithm; the UI shows
 * the backing algorithm when the label differs from it.
 */
data class GameVersion(
    val label: String,
    val engine: McVersion,
) {
    val engineLabel: String get() = engine.label
    val isAlias: Boolean get() = label != engine.label
}

object GameVersions {

    val all: List<GameVersion> = buildList {
        fun family(vararg labels: String, engine: McVersion) {
            labels.forEach { add(GameVersion(it, engine)) }
        }
        family("1.21.11", "1.21.10", "1.21.9", engine = McVersion.MC_1_21_11)
        family("1.21.8", "1.21.7", "1.21.6", engine = McVersion.MC_1_21_6)
        family("1.21.5", engine = McVersion.MC_1_21_5)
        family("1.21.4", engine = McVersion.MC_1_21_4)
        family("1.21.3", "1.21.2", engine = McVersion.MC_1_21_3)
        family("1.21.1", "1.21", engine = McVersion.MC_1_21_1)
        family("1.20.6", "1.20.5", "1.20.4", "1.20.3", "1.20.2", "1.20.1", "1.20", engine = McVersion.MC_1_20_6)
        family("1.19.4", "1.19.3", engine = McVersion.MC_1_19_4)
        family("1.19.2", "1.19.1", "1.19", engine = McVersion.MC_1_19_2)
        family("1.18.2", "1.18.1", "1.18", engine = McVersion.MC_1_18_2)
        family("1.17.1", "1.17", engine = McVersion.MC_1_17_1)
        family("1.16.5", "1.16.4", "1.16.3", "1.16.2", engine = McVersion.MC_1_16_5)
        family("1.16.1", "1.16", engine = McVersion.MC_1_16_1)
        family("1.15.2", "1.15.1", "1.15", engine = McVersion.MC_1_15_2)
        family("1.14.4", "1.14.3", "1.14.2", "1.14.1", "1.14", engine = McVersion.MC_1_14_4)
        family("1.13.2", "1.13.1", "1.13", engine = McVersion.MC_1_13_2)
        family("1.12.2", "1.12.1", "1.12", engine = McVersion.MC_1_12_2)
        family("1.11.2", "1.11.1", "1.11", engine = McVersion.MC_1_11_2)
        family("1.10.2", "1.10.1", "1.10", engine = McVersion.MC_1_10_2)
        family("1.9.4", "1.9.3", "1.9.2", "1.9", engine = McVersion.MC_1_9_4)
        family("1.8.9", "1.8.8", "1.8.7", "1.8.6", "1.8.5", "1.8.4", "1.8.3", "1.8.2", "1.8.1", "1.8", engine = McVersion.MC_1_8_9)
        family("1.7.10", "1.7.9", "1.7.8", "1.7.7", "1.7.6", "1.7.5", "1.7.4", "1.7.3", "1.7.2", engine = McVersion.MC_1_7_10)
    }

    val default: GameVersion = all.first()
}
