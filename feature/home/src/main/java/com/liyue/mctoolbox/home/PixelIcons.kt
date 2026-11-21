package com.liyue.mctoolbox.home

/** 工具页与底栏用的原版像素贴图（16×16 放大到 96×96，最近邻，不糊）。 */
object PixelIcons {
    private val TOOLS = mapOf(
        ToolCatalog.SEED_MAP to R.drawable.px_seed_map,
        ToolCatalog.STRUCTURE_FINDER to R.drawable.px_structure_finder,
        ToolCatalog.BEDROCK_LIBRARY to R.drawable.px_world_convert,
        ToolCatalog.CONVERT to R.drawable.px_world_convert,
        ToolCatalog.ID_TABLE to R.drawable.px_id_table,
        ToolCatalog.JAVA_ID_TABLE to R.drawable.px_java_id_table,
        ToolCatalog.COMMANDS to R.drawable.px_bedrock_commands,
        ToolCatalog.ART_TEXT to R.drawable.px_art_text,
        ToolCatalog.RAW_TEXT to R.drawable.px_raw_text,
        ToolCatalog.SELECTOR to R.drawable.px_structure_finder,
        ToolCatalog.EXECUTE_FIX to R.drawable.px_execute_fix,
        ToolCatalog.TYPING to R.drawable.px_typing_title,
        ToolCatalog.SCRIPT_STUDIO to R.drawable.px_script_studio,
        ToolCatalog.FLOWER_FARM to R.drawable.px_flower_farm,
    )

    fun tool(id: String): Int? = TOOLS[id]

    val navLaunch = R.drawable.px_nav_launch
    val navTools = R.drawable.px_nav_tools
    val navAbout = R.drawable.px_nav_about
}
