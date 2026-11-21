package com.liyue.mctoolbox.home

/** 工具页与底栏图标：豆包生成的 Minecraft 像素风图标集，切好去白底后放大到 96px。 */
object PixelIcons {
    private val TOOLS = mapOf(
        ToolCatalog.SEED_MAP to R.drawable.pxg_map,
        ToolCatalog.STRUCTURE_FINDER to R.drawable.pxg_spyglass,
        ToolCatalog.BEDROCK_LIBRARY to R.drawable.pxg_chest,
        ToolCatalog.CONVERT to R.drawable.pxg_minecart,
        ToolCatalog.ID_TABLE to R.drawable.pxg_book,
        ToolCatalog.JAVA_ID_TABLE to R.drawable.pxg_sign,
        ToolCatalog.COMMANDS to R.drawable.pxg_command,
        ToolCatalog.ART_TEXT to R.drawable.pxg_book,
        ToolCatalog.RAW_TEXT to R.drawable.pxg_paper,
        ToolCatalog.SELECTOR to R.drawable.pxg_spyglass,
        ToolCatalog.EXECUTE_FIX to R.drawable.pxg_repeater,
        ToolCatalog.TYPING to R.drawable.pxg_torch,
        ToolCatalog.SCRIPT_STUDIO to R.drawable.pxg_gear,
        ToolCatalog.FLOWER_FARM to R.drawable.pxg_poppy,
    )

    fun tool(id: String): Int? = TOOLS[id]

    val navLaunch = R.drawable.pxg_sword
    val navTools = R.drawable.pxg_crafting
    val navAbout = R.drawable.pxg_sign
}
