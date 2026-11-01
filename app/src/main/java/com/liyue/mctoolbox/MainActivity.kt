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
import com.liyue.mctoolbox.home.HomeScreen
import com.liyue.mctoolbox.home.ToolCatalog
import com.liyue.mctoolbox.home.ToolEntry
import com.liyue.mctoolbox.map.SeedMapScreen
import com.liyue.mctoolbox.ui.AboutScreen
import com.liyue.mctoolbox.ui.AuthScreen
import com.liyue.mctoolbox.ui.ToolsScreen
import com.liyue.mctoolbox.ui.theme.LiYueTheme
import dagger.hilt.android.AndroidEntryPoint

private enum class Screen { TABS, MAP, DEBUG, AUTH }
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

                fun openTool(tool: ToolEntry) {
                    if (!tool.available) return
                    recentToolId = tool.id
                    prefs.edit().putString("recent_tool", tool.id).apply()
                    if (tool.id == ToolCatalog.SEED_MAP) screen = Screen.MAP
                }

                when (screen) {
                    Screen.MAP -> SeedMapScreen(
                        onBack = { screen = Screen.TABS },
                        onOpenDebug = { screen = Screen.DEBUG },
                    )
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
