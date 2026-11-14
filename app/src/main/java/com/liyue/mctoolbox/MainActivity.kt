package com.liyue.mctoolbox

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.liyue.mctoolbox.auth.AuthViewModel
import com.liyue.mctoolbox.feature.seeddebug.SeedGenScreen
import com.liyue.mctoolbox.home.BedrockLibraryScreen
import com.liyue.mctoolbox.home.HomeScreen
import com.liyue.mctoolbox.home.ToolCatalog
import com.liyue.mctoolbox.home.ToolEntry
import com.liyue.mctoolbox.map.MapTarget
import com.liyue.mctoolbox.map.ScriptScreen
import com.liyue.mctoolbox.map.SeedMapScreen
import com.liyue.mctoolbox.map.GameVersions
import com.liyue.mctoolbox.map.MapOverlay
import com.liyue.mctoolbox.map.finder.AppKind
import com.liyue.mctoolbox.map.finder.ChunkbaseApp
import com.liyue.mctoolbox.map.finder.ChunkbaseAppsScreen
import com.liyue.mctoolbox.map.finder.FinderScreen
import com.liyue.mctoolbox.ui.AboutScreen
import com.liyue.mctoolbox.ui.AuthScreen
import com.liyue.mctoolbox.ui.ConvertScreen
import com.liyue.mctoolbox.ui.ToolsScreen
import com.liyue.mctoolbox.ui.theme.LiYueTheme
import dagger.hilt.android.AndroidEntryPoint

private enum class Screen { TABS, MAP, APPS, FINDER, BEDROCK, SCRIPT, FLOWER, CONVERT, DEBUG, AUTH }
private enum class Tab { HOME, TOOLS, ABOUT }

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiYueTheme {
                val authViewModel: AuthViewModel = viewModel()
                val user by authViewModel.user.collectAsStateWithLifecycle()
                val prefs = remember {
                    getSharedPreferences("liyue_ui", Context.MODE_PRIVATE)
                }
                var screen by rememberSaveable { mutableStateOf(Screen.TABS) }
                var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
                var recentToolId by rememberSaveable {
                    mutableStateOf(prefs.getString("recent_tool", null))
                }
                var mapTarget by remember { mutableStateOf<MapTarget?>(null) }
                var finderPreset by remember { mutableStateOf<ChunkbaseApp?>(null) }
                var finderFromMap by remember { mutableStateOf(false) }

                fun openTool(tool: ToolEntry) {
                    if (!tool.available) return
                    recentToolId = tool.id
                    prefs.edit().putString("recent_tool", tool.id).apply()
                    when (tool.id) {
                        ToolCatalog.SEED_MAP -> screen = Screen.MAP
                        ToolCatalog.STRUCTURE_FINDER -> screen = Screen.APPS
                        ToolCatalog.BEDROCK_LIBRARY -> screen = Screen.BEDROCK
                        ToolCatalog.SCRIPT_STUDIO -> screen = Screen.SCRIPT
                        ToolCatalog.FLOWER_FARM -> screen = Screen.FLOWER
                        ToolCatalog.CONVERT -> screen = Screen.CONVERT
                    }
                }

                when (screen) {
                    Screen.MAP -> SeedMapScreen(
                        onBack = { screen = Screen.TABS },
                        onOpenDebug = { screen = Screen.DEBUG },
                        onOpenFinder = { seedText, versionLabel ->
                            com.liyue.mctoolbox.map.FinderLaunch.seedText = seedText
                            com.liyue.mctoolbox.map.FinderLaunch.versionLabel = versionLabel
                            finderFromMap = true
                            finderPreset = null
                            screen = Screen.FINDER
                        },
                        initialTarget = mapTarget,
                        onTargetConsumed = { mapTarget = null },
                    )
                    Screen.APPS -> ChunkbaseAppsScreen(
                        onBack = { screen = Screen.TABS },
                        onOpenApp = { app ->
                            when (app.kind) {
                                AppKind.SEED_MAP -> screen = Screen.MAP
                                AppKind.CAVE, AppKind.RAVINE -> {
                                    mapTarget = MapTarget(
                                        seed = 0L,
                                        seedText = "0",
                                        versionLabel = GameVersions.default.label,
                                        dimension = com.liyue.mctoolbox.seedgen.Dimension.OVERWORLD,
                                        centerX = 0.0,
                                        centerZ = 0.0,
                                        overlay = if (app.kind == AppKind.CAVE) {
                                            MapOverlay.CAVE
                                        } else {
                                            MapOverlay.RAVINE
                                        },
                                    )
                                    screen = Screen.MAP
                                }
                                else -> {
                                    finderPreset = app
                                    screen = Screen.FINDER
                                }
                            }
                        },
                    )
                    Screen.BEDROCK -> BedrockLibraryScreen(
                        onBack = { screen = Screen.TABS },
                        onOpenSeedMap = { label ->
                            val target = GameVersions.find(label) ?: GameVersions.default
                            mapTarget = MapTarget(
                                seed = 0L,
                                seedText = "0",
                                versionLabel = target.label,
                                dimension = com.liyue.mctoolbox.seedgen.Dimension.OVERWORLD,
                                centerX = 0.0,
                                centerZ = 0.0,
                            )
                            screen = Screen.MAP
                        },
                    )
                    Screen.FINDER -> FinderScreen(
                        onBack = {
                            screen = if (finderFromMap) {
                                finderFromMap = false
                                Screen.MAP
                            } else {
                                Screen.APPS
                            }
                        },
                        onOpenMap = { target ->
                            mapTarget = target
                            screen = Screen.MAP
                        },
                        preset = finderPreset,
                    )
                    Screen.SCRIPT -> ScriptScreen(
                        onBack = { screen = Screen.TABS },
                        onOpenMap = { target ->
                            mapTarget = target
                            screen = Screen.MAP
                        },
                    )
                    Screen.FLOWER -> com.liyue.mctoolbox.map.FlowerFarmScreen(
                        onBack = { screen = Screen.TABS },
                        onOpenMap = { target ->
                            mapTarget = target
                            screen = Screen.MAP
                        },
                    )
                    Screen.CONVERT -> ConvertScreen(onBack = { screen = Screen.TABS })
                    Screen.DEBUG -> SeedGenScreen(onBack = { screen = Screen.TABS })
                    Screen.AUTH -> AuthScreen(onBack = { screen = Screen.TABS })
                    Screen.TABS -> MainTabs(
                        tab = tab,
                        onTabChange = { tab = it },
                        recentToolId = recentToolId,
                        user = user,
                        onOpenTool = ::openTool,
                        onLogin = { screen = Screen.AUTH },
                        onLogout = { authViewModel.logout() },
                    )
                }
            }
        }
    }
}

@Composable
private fun MainTabs(
    tab: Tab,
    onTabChange: (Tab) -> Unit,
    recentToolId: String?,
    user: com.liyue.mctoolbox.auth.AuthUser?,
    onOpenTool: (ToolEntry) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.HOME,
                    onClick = { onTabChange(Tab.HOME) },
                    icon = { Icon(Icons.Filled.List, contentDescription = null) },
                    label = { Text("功能") },
                )
                NavigationBarItem(
                    selected = tab == Tab.TOOLS,
                    onClick = { onTabChange(Tab.TOOLS) },
                    icon = { Icon(Icons.Filled.Star, contentDescription = null) },
                    label = { Text("工具") },
                )
                NavigationBarItem(
                    selected = tab == Tab.ABOUT,
                    onClick = { onTabChange(Tab.ABOUT) },
                    icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                    label = { Text("关于") },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (tab) {
                Tab.HOME -> HomeScreen(
                    recentToolId = recentToolId,
                    onOpenTool = onOpenTool,
                )
                Tab.TOOLS -> ToolsScreen(
                    onBack = { onTabChange(Tab.HOME) },
                    onOpenTool = { id -> ToolCatalog.byId(id)?.let(onOpenTool) },
                )
                Tab.ABOUT -> AboutScreen(
                    user = user,
                    onLogin = onLogin,
                    onLogout = onLogout,
                )
            }
        }
    }
}
