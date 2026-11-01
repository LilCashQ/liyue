package com.liyue.mctoolbox.seedgen

/**
 * Java Edition versions supported by the bundled cubiomes snapshot.
 *
 * The [id] values are a stable LiYue-side index; the native layer maps them to
 * cubiomes' own `MCVersion` enum. Do not reorder entries without updating
 * `LIYUE_MC_VERSIONS` in `liyue_seedgen.c`.
 */
enum class McVersion(val id: Int, val label: String) {
    MC_1_7_10(0, "1.7.10"),
    MC_1_8_9(1, "1.8.9"),
    MC_1_9_4(2, "1.9.4"),
    MC_1_10_2(3, "1.10.2"),
    MC_1_11_2(4, "1.11.2"),
    MC_1_12_2(5, "1.12.2"),
    MC_1_13_2(6, "1.13.2"),
    MC_1_14_4(7, "1.14.4"),
    MC_1_15_2(8, "1.15.2"),
    MC_1_16_1(9, "1.16.1"),
    MC_1_16_5(10, "1.16.5"),
    MC_1_17_1(11, "1.17.1"),
    MC_1_18_2(12, "1.18.2"),
    MC_1_19_2(13, "1.19.2"),
    MC_1_19_4(14, "1.19.4"),
    MC_1_20_6(15, "1.20.6"),
    MC_1_21_1(16, "1.21.1"),
    MC_1_21_3(17, "1.21.3"),
    MC_1_21_4(18, "1.21.4"),
    MC_1_21_5(19, "1.21.5"),
    MC_1_21_6(20, "1.21.6"),
    MC_1_21_9(21, "1.21.9"),
    MC_1_21_11(22, "1.21.11"),
    ;

    /**
     * Older versions use cubiomes' layered generator, which has no scale above
     * 1:256; zooming further out would require generating a huge intermediate
     * grid. Modern noise versions can sample any step directly.
     */
    val maxBlocksPerPixel: Float
        get() = if (this >= MC_1_18_2) 2048f else 256f

    companion object {
        fun fromId(id: Int): McVersion? = entries.firstOrNull { it.id == id }
    }
}

enum class Dimension(val id: Int, val label: String) {
    OVERWORLD(0, "主世界"),
    NETHER(-1, "下界"),
    END(1, "末地"),
}

/** Result of a biome grid query, row-major with z as the outer index. */
class BiomeGrid(
    val width: Int,
    val height: Int,
    val cellBlocks: Int,
    val ids: IntArray,
) {
    init {
        require(ids.size == width * height) { "ids size ${ids.size} != ${width * height}" }
    }

    operator fun get(x: Int, z: Int): Int {
        require(x in 0 until width && z in 0 until height) { "($x, $z) outside $width x $height" }
        return ids[z * width + x]
    }
}
