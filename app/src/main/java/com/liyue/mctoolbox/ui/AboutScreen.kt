package com.liyue.mctoolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liyue.mctoolbox.auth.AuthUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton

private data class AboutRow(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)

private val supportRows = listOf(
    AboutRow("捐赠支持", "用于后续开发与维护", Icons.Filled.Favorite),
    AboutRow("开源致谢", "cubiomes 等开源项目", Icons.Filled.Star),
)

private val policyRows = listOf(
    AboutRow("隐私政策", "了解我们如何处理数据", Icons.Filled.Lock),
    AboutRow("用户协议", "使用本应用前请阅读", Icons.Filled.List),
    AboutRow("权限说明", "应用申请的权限与用途", Icons.Filled.Info),
)

private val otherRows = listOf(
    AboutRow("检查更新", "当前版本 v0.12.0（构建 2026-11-01）", Icons.Filled.Info),
    AboutRow("反馈", "功能建议或问题反馈", Icons.Filled.Email),
    AboutRow("帮助", "常见问题与使用说明", Icons.Filled.Info),
    AboutRow("分享应用", "把里悦分享给朋友", Icons.Filled.Share),
    AboutRow("设置", "主题、下载与地图偏好", Icons.Filled.Settings),
    AboutRow("关于", "里悦 · Minecraft 工具箱", Icons.Filled.Person),
)

@Composable
fun AboutScreen(
    user: AuthUser?,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<Pair<String, String>?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = 32.dp),
    ) {
        item { AccountCard(user, onLogin = onLogin, onLogout = onLogout) }

        item { SectionLabel("支持") }
        items(supportRows) { row -> RowCard(row) { dialog = row.title to row.subtitle } }

        item { SectionLabel("政策") }
        items(policyRows) { row -> RowCard(row) { dialog = row.title to policyText(row.title) } }

        item { SectionLabel("其他") }
        items(otherRows) { row ->
            RowCard(row) {
                when (row.title) {
                    "分享应用" -> {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                android.content.Intent.EXTRA_TEXT,
                                "里悦 · Minecraft 工具箱：种子地图、模组中心与实用工具。",
                            )
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "分享里悦"))
                    }
                    "检查更新" -> dialog = "检查更新" to "当前已是最新版本 v0.12.0。"
                    "关于" -> dialog = "关于里悦" to
                        "里悦是一个 Minecraft 工具箱：种子地图使用 cubiomes 计算真实群系与结构，模组中心将在后续版本接入。"
                    else -> dialog = row.title to "该功能正在开发中。"
                }
            }
        }
    }

    dialog?.let { (title, body) ->
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = {
                TextButton(onClick = { dialog = null }) { Text("知道了") }
            },
        )
    }
}

@Composable
private fun AccountCard(user: AuthUser?, onLogin: () -> Unit, onLogout: () -> Unit) {
    Card(shape = RoundedCornerShape(28.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFFA8C8FF), Color(0xFF7FA6F0))))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color(0xFF34506E), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(34.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = user?.nickname ?: "未登录",
                        color = Color(0xFF1B2A3D),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (user != null) user.email else "登录后同步收藏与下载记录",
                        color = Color(0xFF3A4A5E),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (user == null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onLogin),
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF34506E),
                ) {
                    Text(
                        text = "登录 / 注册",
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onLogout),
                    shape = RoundedCornerShape(50),
                    color = Color(0x33345060),
                ) {
                    Text(
                        text = "退出登录",
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = Color(0xFF1B2A3D),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 16.dp, start = 4.dp, bottom = 2.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RowCard(row: AboutRow, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = row.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(row.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = row.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Filled.List,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private fun policyText(title: String): String = when (title) {
    "隐私政策" -> "里悦默认不收集个人信息。账号数据仅保存在本机；使用种子地图时，种子、版本与坐标不会上传。若后续接入账号服务，会在本页说明数据范围并征得同意。"
    "用户协议" -> "里悦是第三方 Minecraft 工具，与 Mojang、Microsoft 无隶属关系。请勿使用本应用从事侵权或违反游戏服务条款的行为。"
    "权限说明" -> "网络：后续模组中心需要访问公开 API；存储：导出地图截图与下载文件时必须由你选择目录。应用不申请全盘存储权限。"
    else -> "该功能正在开发中。"
}
