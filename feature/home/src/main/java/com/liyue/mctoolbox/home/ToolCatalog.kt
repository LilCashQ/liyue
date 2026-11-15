package com.liyue.mctoolbox.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

data class ToolEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val available: Boolean,
)

object ToolCatalog {
    const val SEED_MAP = "seed_map"
    const val STRUCTURE_FINDER = "structure_finder"
    const val BEDROCK_LIBRARY = "bedrock_library"
    const val CONVERT = "world_convert"
    const val ID_TABLE = "id_table"
    const val JAVA_ID_TABLE = "java_id_table"
    const val COMMANDS = "bedrock_commands"
    const val ART_TEXT = "art_text"
    const val RAW_TEXT = "raw_text"
    const val SELECTOR = "selector"
    const val EXECUTE_FIX = "execute_fix"
    const val TYPING = "typing_title"
    const val SCRIPT_STUDIO = "script_studio"
    const val FLOWER_FARM = "flower_farm"

    val all: List<ToolEntry> = listOf(
        ToolEntry(
            id = SEED_MAP,
            title = "种子地图",
            subtitle = "查看群系、结构与出生点",
            icon = Icons.Filled.LocationOn,
            available = true,
        ),
        ToolEntry(
            id = STRUCTURE_FINDER,
            title = "查找器",
            subtitle = "Chunkbase 全套：36 个查找器一次装齐",
            icon = Icons.Filled.Search,
            available = true,
        ),
        ToolEntry(
            id = BEDROCK_LIBRARY,
            title = "基岩版版本库",
            subtitle = "全部基岩版本与网盘下载，直通种子地图",
            icon = Icons.Filled.List,
            available = true,
        ),
        ToolEntry(
            id = CONVERT,
            title = "存档转换",
            subtitle = "Java 版 ⇄ 基岩版，整张地图搬过去",
            icon = Icons.Filled.Refresh,
            available = true,
        ),
        ToolEntry(
            id = ID_TABLE,
            title = "基岩版 ID 表",
            subtitle = "命令要用的方块/物品/实体 ID，点一行复制",
            icon = Icons.Filled.List,
            available = true,
        ),
        ToolEntry(
            id = JAVA_ID_TABLE,
            title = "Java 版 ID 表",
            subtitle = "方块与物品的字符串 ID、数字 ID，点一行复制",
            icon = Icons.Filled.Search,
            available = true,
        ),
        ToolEntry(
            id = COMMANDS,
            title = "基岩版命令速查",
            subtitle = "79 条命令的说明与语法，点开复制",
            icon = Icons.Filled.Edit,
            available = true,
        ),
        ToolEntry(
            id = ART_TEXT,
            title = "艺术字转换",
            subtitle = "字母数字换成游戏内字形，九种风格",
            icon = Icons.Filled.Edit,
            available = true,
        ),
        ToolEntry(
            id = RAW_TEXT,
            title = "rawtext 构建器",
            subtitle = "拼 tellraw 的 JSON，直接复制命令",
            icon = Icons.Filled.Edit,
            available = true,
        ),
        ToolEntry(
            id = SELECTOR,
            title = "选择器编辑器",
            subtitle = "拼 @a[name=,tag=,distance=…] 这类参数",
            icon = Icons.Filled.Search,
            available = true,
        ),
        ToolEntry(
            id = EXECUTE_FIX,
            title = "execute 语法转换",
            subtitle = "旧版 execute 一键升级成新格式",
            icon = Icons.Filled.Refresh,
            available = true,
        ),
        ToolEntry(
            id = TYPING,
            title = "T 显动画",
            subtitle = "打字机标题，计分板推进逐字显示",
            icon = Icons.Filled.Edit,
            available = true,
        ),
        ToolEntry(
            id = SCRIPT_STUDIO,
            title = "脚本工坊",
            subtitle = "写 Lua 搜种子，和 cubiomes-viewer 一样",
            icon = Icons.Filled.Edit,
            available = true,
        ),
        ToolEntry(
            id = FLOWER_FARM,
            title = "全花种农场",
            subtitle = "花林 + 沼泽 + 苍白花园挤在一个窗口里",
            icon = Icons.Filled.Star,
            available = true,
        ),
        ToolEntry(
            id = "mods",
            title = "模组中心",
            subtitle = "Modrinth / CurseForge / MCPEDL",
            icon = Icons.Filled.ShoppingCart,
            available = false,
        ),
        ToolEntry(
            id = "library",
            title = "本地库",
            subtitle = "已下载的模组与附加包",
            icon = Icons.Filled.List,
            available = false,
        ),
        ToolEntry(
            id = "favorites",
            title = "收藏夹",
            subtitle = "本地收藏，不绑定账号",
            icon = Icons.Filled.Star,
            available = false,
        ),
        ToolEntry(
            id = "settings",
            title = "设置",
            subtitle = "主题、下载与地图偏好",
            icon = Icons.Filled.Settings,
            available = false,
        ),
    )

    fun byId(id: String?): ToolEntry? = all.firstOrNull { it.id == id }
}
