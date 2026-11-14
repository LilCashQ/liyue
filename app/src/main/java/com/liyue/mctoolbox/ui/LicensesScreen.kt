package com.liyue.mctoolbox.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 里悦用到的开源项目。每一条都写清用途、许可证和仓库地址，
 * 点一下直接用浏览器打开仓库。
 */
private data class Project(
    val name: String,
    val monogram: String,
    val tint: Color,
    val role: String,
    val license: String,
    val url: String,
    val iconRes: Int? = null,
)

private val CORE = listOf(
    Project(
        name = "cubiomes",
        monogram = "cu",
        tint = Color(0xFF2D6BFF),
        role = "群系、结构、要塞、史莱姆区块的生成算法；整张种子地图都由它算",
        license = "MIT",
        url = "https://github.com/Cubitect/cubiomes",
    ),
    Project(
        name = "Axolotl",
        monogram = "Ax",
        tint = Color(0xFF7A5AF8),
        role = "结构详情、地下剖面、矿石扫描、战利品滚算的 C 实现",
        license = "GPL-3.0",
        url = "https://github.com/Mystic-Stars/Axolotl",
    ),
    Project(
        name = "cubiomes-viewer",
        monogram = "cv",
        tint = Color(0xFF12A594),
        role = "图层与统计面板的交互参考（生物群系、高度、气候参数）",
        license = "MIT",
        url = "https://github.com/Cubitect/cubiomes-viewer",
        iconRes = com.liyue.mctoolbox.R.drawable.lic_cubiomesviewer,
    ),
    Project(
        name = "Chunker",
        monogram = "Ch",
        tint = Color(0xFF0E8F5E),
        role = "Java 版 ⇄ 基岩版存档转换引擎（经 LiteChunk 移植）",
        license = "MIT",
        url = "https://github.com/FreneticWind60/LiteChunk",
    ),
)

private val FRAMEWORK = listOf(
    Project(
        name = "Kotlin",
        monogram = "K",
        tint = Color(0xFF7F52FF),
        role = "应用与原生桥接层的开发语言",
        license = "Apache-2.0",
        url = "https://github.com/JetBrains/kotlin",
        iconRes = com.liyue.mctoolbox.R.drawable.lic_kotlin,
    ),
    Project(
        name = "Jetpack Compose",
        monogram = "JC",
        tint = Color(0xFF4285F4),
        role = "全部界面的声明式 UI 框架",
        license = "Apache-2.0",
        url = "https://github.com/androidx/androidx",
        iconRes = com.liyue.mctoolbox.R.drawable.lic_jetpackcompose,
    ),
    Project(
        name = "AndroidX",
        monogram = "AX",
        tint = Color(0xFF34A853),
        role = "生命周期、导航、SAF 文件选择等基础组件",
        license = "Apache-2.0",
        url = "https://github.com/androidx/androidx",
        iconRes = com.liyue.mctoolbox.R.drawable.lic_android,
    ),
    Project(
        name = "Hilt",
        monogram = "Hi",
        tint = Color(0xFF1A73E8),
        role = "依赖注入",
        license = "Apache-2.0",
        url = "https://github.com/google/dagger",
    ),
    Project(
        name = "Kotlin Coroutines",
        monogram = "co",
        tint = Color(0xFFEA580C),
        role = "瓦片渲染、查找、转换的并发调度",
        license = "Apache-2.0",
        url = "https://github.com/Kotlin/kotlinx.coroutines",
    ),
    Project(
        name = "Guava",
        monogram = "Gu",
        tint = Color(0xFF1E88E5),
        role = "转换引擎依赖的集合与工具库",
        license = "Apache-2.0",
        url = "https://github.com/google/guava",
    ),
    Project(
        name = "Gson",
        monogram = "Gs",
        tint = Color(0xFFE53935),
        role = "结构详情与存档元数据的 JSON 解析",
        license = "Apache-2.0",
        url = "https://github.com/google/gson",
    ),
    Project(
        name = "fastutil",
        monogram = "fu",
        tint = Color(0xFF8E24AA),
        role = "转换引擎使用的高性能基本类型集合",
        license = "Apache-2.0",
        url = "https://github.com/vigna/fastutil",
    ),
    Project(
        name = "LZ4",
        monogram = "L4",
        tint = Color(0xFF00897B),
        role = "区块数据的快速压缩与解压",
        license = "Apache-2.0",
        url = "https://github.com/lz4/lz4-java",
    ),
    Project(
        name = "LevelDB (HiveMC)",
        monogram = "DB",
        tint = Color(0xFF546E7A),
        role = "基岩版 db 存储的读写",
        license = "Apache-2.0",
        url = "https://github.com/hivemc/leveldb",
    ),
)

private val ASSETS = listOf(
    Project(
        name = "Minecraft Assets",
        monogram = "MA",
        tint = Color(0xFF6D9B3F),
        role = "矿石与物品图标用的原版贴图",
        license = "Mojang 资源，仅作展示",
        url = "https://github.com/InventivetalentDev/minecraft-assets",
        iconRes = com.liyue.mctoolbox.R.drawable.lic_grass,
    ),
    Project(
        name = "cJSON",
        monogram = "cJ",
        tint = Color(0xFF455A64),
        role = "原生层解析战利品表的 JSON",
        license = "MIT",
        url = "https://github.com/DaveGamble/cJSON",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("开源致谢") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        ) {
            item {
                Text(
                    text = "里悦站在这些项目的肩膀上。下面是每一块的用途、许可证与仓库地址，点一条可以直接打开。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            group("核心算法", CORE, ::open)
            group("运行时与框架", FRAMEWORK, ::open)
            group("资源与数据", ASSETS, ::open)
            item {
                Text(
                    text = "Minecraft 是 Mojang Studios 的商标，里悦与 Mojang、Microsoft 没有隶属关系。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.group(
    title: String,
    projects: List<Project>,
    open: (String) -> Unit,
) {
    item(key = "head-$title") {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        )
    }
    item(key = "card-$title") {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                projects.forEachIndexed { index, project ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(start = 66.dp),
                        )
                    }
                    ProjectRow(project) { open(project.url) }
                }
            }
        }
    }
}

@Composable
private fun ProjectRow(project: Project, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(project.tint.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (project.iconRes != null) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(project.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
            } else {
                Text(
                    text = project.monogram,
                    color = project.tint,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = project.license,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(6.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = project.role,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = project.url.removePrefix("https://"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Icon(
            Icons.Filled.Share,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(16.dp),
        )
    }
}
