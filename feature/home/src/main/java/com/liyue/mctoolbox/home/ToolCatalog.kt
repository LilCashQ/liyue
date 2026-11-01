package com.liyue.mctoolbox.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
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

    val all: List<ToolEntry> = listOf(
        ToolEntry(
            id = SEED_MAP,
            title = "种子地图",
            subtitle = "查看群系、结构与出生点",
            icon = Icons.Filled.LocationOn,
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
