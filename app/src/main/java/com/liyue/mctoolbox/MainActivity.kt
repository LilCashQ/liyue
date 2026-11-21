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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.liyue.mctoolbox.home.LaunchScreen
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
import com.liyue.mctoolbox.ui.ArtTextScreen
import com.liyue.mctoolbox.ui.BedrockCommandScreen
import com.liyue.mctoolbox.ui.ExecuteConvertScreen
import com.liyue.mctoolbox.ui.RawTextScreen
import com.liyue.mctoolbox.ui.SelectorScreen
import com.liyue.mctoolbox.ui.TypingAnimationScreen
import com.liyue.mctoolbox.ui.ConvertScreen
import com.liyue.mctoolbox.ui.IdTableScreen
import com.liyue.mctoolbox.ui.JavaIdTableScreen
import com.liyue.mctoolbox.ui.LicensesScreen
import com.liyue.mctoolbox.ui.ToolsScreen
import com.liyue.mctoolbox.ui.theme.LiYueTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth

private enum class Screen { TABS, MAP, APPS, FINDER, BEDROCK, SCRIPT, FLOWER, CONVERT, ID_TABLE, JAVA_ID_TABLE, COMMANDS, ART_TEXT, RAW_TEXT, SELECTOR, EXECUTE_FIX, TYPING, LICENSES, DEBUG, AUTH }
private enum class Tab { LAUNCH, TOOLS, ABOUT }

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
                var tab by rememberSaveable { mutableStateOf(Tab.LAUNCH) }
                var recentToolIds by rememberSaveable {
                    mutableStateOf(prefs.getString("recent_tools", null))
                }
                var frequentToolIds by rememberSaveable {
                    mutableStateOf(prefs.getString("frequent_tools", null))
                }
                var mapTarget by remember { mutableStateOf<MapTarget?>(null) }
                var finderPreset by remember { mutableStateOf<ChunkbaseApp?>(null) }
                var finderFromMap by remember { mutableStateOf(false) }

                fun openTool(tool: ToolEntry) {
                    if (!tool.available) return
                    recentToolIds = (listOf(tool.id) + (recentToolIds?.split(",") ?: emptyList()))
                        .distinct().take(8).joinToString(",")
                    // 常用功能按真实使用次数排，用够 3 次才算「常用」
                    val counts = mutableMapOf<String, Int>()
                    prefs.getString("tool_usage", null)?.split(",")?.forEach { part ->
                        val bits = part.split(":")
                        if (bits.size == 2) {
                            bits[0].takeIf { it.isNotBlank() }?.let { id ->
                                counts[id] = (counts[id] ?: 0) + (bits[1].toIntOrNull() ?: 0)
                            }
                        }
                    }
                    counts[tool.id] = (counts[tool.id] ?: 0) + 1
                    prefs.edit()
                        .putString("recent_tools", recentToolIds)
                        .putString("tool_usage", counts.entries.joinToString(",") { "${it.key}:${it.value}" })
                        .apply()
                    frequentToolIds = counts.entries
                        .filter { it.value >= 3 }
                        .sortedByDescending { it.value }
                        .take(8)
                        .joinToString(",") { it.key }
                        .ifBlank { null }
                    prefs.edit().putString("frequent_tools", frequentToolIds).apply()
                    when (tool.id) {
                        ToolCatalog.SEED_MAP -> screen = Screen.MAP
                        ToolCatalog.STRUCTURE_FINDER -> screen = Screen.APPS
                        ToolCatalog.BEDROCK_LIBRARY -> screen = Screen.BEDROCK
                        ToolCatalog.SCRIPT_STUDIO -> screen = Screen.SCRIPT
                        ToolCatalog.FLOWER_FARM -> screen = Screen.FLOWER
                        ToolCatalog.CONVERT -> screen = Screen.CONVERT
                        ToolCatalog.ID_TABLE -> screen = Screen.ID_TABLE
                        ToolCatalog.JAVA_ID_TABLE -> screen = Screen.JAVA_ID_TABLE
                        ToolCatalog.COMMANDS -> screen = Screen.COMMANDS
                        ToolCatalog.ART_TEXT -> screen = Screen.ART_TEXT
                        ToolCatalog.RAW_TEXT -> screen = Screen.RAW_TEXT
                        ToolCatalog.SELECTOR -> screen = Screen.SELECTOR
                        ToolCatalog.EXECUTE_FIX -> screen = Screen.EXECUTE_FIX
                        ToolCatalog.TYPING -> screen = Screen.TYPING
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
                    Screen.ID_TABLE -> IdTableScreen(onBack = { screen = Screen.TABS })
                    Screen.JAVA_ID_TABLE -> JavaIdTableScreen(onBack = { screen = Screen.TABS })
                    Screen.COMMANDS -> BedrockCommandScreen(onBack = { screen = Screen.TABS })
                    Screen.ART_TEXT -> ArtTextScreen(onBack = { screen = Screen.TABS })
                    Screen.RAW_TEXT -> RawTextScreen(onBack = { screen = Screen.TABS })
                    Screen.SELECTOR -> SelectorScreen(onBack = { screen = Screen.TABS })
                    Screen.EXECUTE_FIX -> ExecuteConvertScreen(onBack = { screen = Screen.TABS })
                    Screen.TYPING -> TypingAnimationScreen(onBack = { screen = Screen.TABS })
                    Screen.LICENSES -> LicensesScreen(onBack = { screen = Screen.TABS })
                    Screen.DEBUG -> SeedGenScreen(onBack = { screen = Screen.TABS })
                    Screen.AUTH -> AuthScreen(onBack = { screen = Screen.TABS })
                    Screen.TABS -> MainTabs(
                        tab = tab,
                        onTabChange = { tab = it },
                        recentToolIds = recentToolIds,
                        frequentToolIds = frequentToolIds,
                        user = user,
                        onOpenTool = ::openTool,
                        onLogin = { screen = Screen.AUTH },
                        onLogout = { authViewModel.logout() },
                        onOpenLicenses = { screen = Screen.LICENSES },
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
    recentToolIds: String?,
    frequentToolIds: String?,
    user: com.liyue.mctoolbox.auth.AuthUser?,
    onOpenTool: (ToolEntry) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    Scaffold(
        bottomBar = {
            Column {
                PixelHintBar()
                PixelNavBar(tab = tab, onTabChange = onTabChange)
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (tab) {
                Tab.LAUNCH -> LaunchScreen()
                Tab.TOOLS -> HomeScreen(
                    recentToolIds = recentToolIds,
                    frequentToolIds = frequentToolIds,
                    onOpenTool = onOpenTool,
                )
                Tab.ABOUT -> AboutScreen(
                    user = user,
                    onLogin = onLogin,
                    onLogout = onLogout,
                    onOpenLicenses = onOpenLicenses,
                )
            }
        }
    }
}

private val NavFill = androidx.compose.ui.graphics.Color(0xFF5E5449)
private val NavGreen = androidx.compose.ui.graphics.Color(0xFF7ED04A)
private val NavText = androidx.compose.ui.graphics.Color(0xFFE9E1D6)

/** 底部三格导航：选中一格铺草绿方块。 */
@Composable
private fun PixelNavBar(tab: Tab, onTabChange: (Tab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NavFill)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        listOf(
            Triple(Tab.LAUNCH, "启动游戏", com.liyue.mctoolbox.home.PixelIcons.navLaunch),
            Triple(Tab.TOOLS, "工具", com.liyue.mctoolbox.home.PixelIcons.navTools),
            Triple(Tab.ABOUT, "关于", com.liyue.mctoolbox.home.PixelIcons.navAbout),
        ).forEach { (target, label, iconRes) ->
            val selected = tab == target
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onTabChange(target) }
                    .padding(horizontal = 4.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (selected) NavGreen else androidx.compose.ui.graphics.Color.Transparent)
                        .padding(vertical = 6.dp),
                ) {
                    Image(
                        painter = painterResource(iconRes),
                        contentDescription = label,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) androidx.compose.ui.graphics.Color(0xFF1E1E1E) else NavText,
                    )
                }
            }
        }
    }
}

/** 底栏上面的像素提示条。 */
@Composable
private fun PixelHintBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(androidx.compose.ui.graphics.Color(0xFF4A4540))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(com.liyue.mctoolbox.home.R.drawable.pxg_torch),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "提示：工具仅用于游戏创作与学习。像素让想象更自由！",
            style = MaterialTheme.typography.labelSmall,
            color = androidx.compose.ui.graphics.Color(0xFFD9D0C3),
        )
    }
}
