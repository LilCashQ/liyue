package com.liyue.mctoolbox.home

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 里悦启动器（Minecraft 基岩版）的安装与启动入口。
 *
 * 启动器是一个独立 APK，清单里没有 LAUNCHER 类别，所以不会出现在桌面；
 * 只有里悦通过显式组件名 + 私有 action 才能把它拉起来。
 */
object LauncherBridge {
    const val PACKAGE = "org.levimc.launcher"
    const val ACTIVITY = "org.levimc.launcher.ui.activities.SplashActivity"
    const val ACTION_START = "com.liyue.launcher.action.START"

    private const val APK_NAME = "liyue-launcher.apk"

    const val DOWNLOAD_URL =
        "https://github.com/LilCashQ/liyue/releases/download/launcher-1.6.0/liyue-launcher-1.6.0-arm64.apk"

    fun versionName(context: Context): String? = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(PACKAGE, 0).versionName
    }.getOrNull()

    fun isInstalled(context: Context): Boolean = versionName(context) != null

    /** 返回 null 表示成功，否则返回给用户看的失败原因。 */
    fun launch(context: Context): String? {
        val explicit = Intent(ACTION_START).apply {
            setClassName(PACKAGE, ACTIVITY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val explicitError = runCatching { context.startActivity(explicit) }.exceptionOrNull()
        if (explicitError == null) return null

        val fallback = context.packageManager.getLaunchIntentForPackage(PACKAGE)
        if (fallback != null) {
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val fallbackError = runCatching { context.startActivity(fallback) }.exceptionOrNull()
            if (fallbackError == null) return null
            return describe(fallbackError)
        }
        return describe(explicitError)
    }

    private fun describe(error: Throwable): String = when (error) {
        is android.content.ActivityNotFoundException -> "系统里没有这个入口，启动器可能没装好，重新安装一次"
        is SecurityException -> "系统拦住了这次跳转（${error.message ?: "SecurityException"}）"
        else -> error.message ?: error::class.java.simpleName
    }

    fun apkFile(context: Context): File = File(File(context.filesDir, "launcher"), APK_NAME)

    suspend fun download(context: Context, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val target = apkFile(context)
            target.parentFile?.mkdirs()
            if (target.exists()) target.delete()
            val conn = (URL(DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "LiYue-Android")
            }
            try {
                conn.connect()
                if (conn.responseCode !in 200..299) {
                    error("下载失败：HTTP ${conn.responseCode}")
                }
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(1 shl 16)
                        var copied = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (total > 0) onProgress((copied.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            onProgress(1f)
            target
        }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

private sealed interface LaunchState {
    data class Idle(val installed: Boolean, val version: String?) : LaunchState
    data class Downloading(val progress: Float) : LaunchState
    data class Ready(val apk: File) : LaunchState
    data class Failed(val message: String) : LaunchState
}

@Composable
fun LaunchScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var installedVersion by remember { mutableStateOf(LauncherBridge.versionName(context)) }
    var state by remember {
        mutableStateOf<LaunchState>(
            LaunchState.Idle(installedVersion != null, installedVersion),
        )
    }

    // 从系统安装器返回时重新读一次安装状态
    LaunchedEffect(Unit) {
        installedVersion = LauncherBridge.versionName(context)
        state = LaunchState.Idle(installedVersion != null, installedVersion)
    }

    fun refresh() {
        installedVersion = LauncherBridge.versionName(context)
        state = LaunchState.Idle(installedVersion != null, installedVersion)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = "启动游戏",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "用里悦启动器跑基岩版：免安装导入官方包，多版本隔离，能挂载 SO 模块。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))

        HeroLaunchCard(
            installedVersion = installedVersion,
            state = state,
            onLaunch = {
                val problem = LauncherBridge.launch(context)
                if (problem != null) state = LaunchState.Failed(problem)
            },
            onDownload = {
                state = LaunchState.Downloading(0f)
                scope.launch {
                    try {
                        val apk = LauncherBridge.download(context) { p ->
                            state = LaunchState.Downloading(p)
                        }
                        state = LaunchState.Ready(apk)
                        LauncherBridge.install(context, apk)
                    } catch (t: Throwable) {
                        state = LaunchState.Failed(t.message ?: "下载失败")
                    }
                }
            },
            onInstallCached = { apk ->
                runCatching { LauncherBridge.install(context, apk) }
                    .onFailure { state = LaunchState.Failed(it.message ?: "打不开安装器") }
            },
            onRefresh = { refresh() },
        )

        Spacer(Modifier.height(14.dp))
        InfoCard(
            title = "它不在桌面上",
            body = "启动器装好后不会出现在桌面和应用列表里，只有从这里点「开始游戏」才会打开。想彻底删掉，去系统设置 → 应用 → 里悦启动器 卸载。",
        )
        Spacer(Modifier.height(10.dp))
        InfoCard(
            title = "第一次用要先导入游戏",
            body = "启动器本身不含游戏。打开后点「导入 APK」，选你手上的基岩版安装包，它就能免安装直接运行。",
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HeroLaunchCard(
    installedVersion: String?,
    state: LaunchState,
    onLaunch: () -> Unit,
    onDownload: () -> Unit,
    onInstallCached: (File) -> Unit,
    onRefresh: () -> Unit,
) {
    val installed = installedVersion != null
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "里悦启动器",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (installed) "已安装 · v$installedVersion" else "未安装",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (installed) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            when (state) {
                is LaunchState.Downloading -> {
                    Text(
                        text = "正在下载 ${(state.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "约 31 MB，下完会自动拉起系统安装器",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is LaunchState.Ready -> {
                    Text(
                        text = "下载完成，正在打开安装器",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    LaunchActionButton(text = "再打开一次安装器", onClick = { onInstallCached(state.apk) })
                }

                is LaunchState.Failed -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(10.dp))
                    LaunchActionButton(
                        text = if (installed) "重试启动" else "重新下载",
                        onClick = if (installed) onLaunch else onDownload,
                    )
                }

                is LaunchState.Idle -> {
                    if (installed) {
                        LaunchActionButton(text = "开始游戏", onClick = onLaunch)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextLink("重新检测", onRefresh)
                            TextLink("重新下载安装包", onDownload)
                        }
                    } else {
                        LaunchActionButton(text = "下载并安装启动器", onClick = onDownload)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextLink("我已经装过了", onRefresh)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LaunchActionButton(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(50))
            .background(androidx.compose.ui.graphics.Color(0xFF111417))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = androidx.compose.ui.graphics.Color.White,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun TextLink(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun InfoCard(title: String, body: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Default,
            )
        }
    }
}

/** 里悦内部的「启动器是否可用」查询，给其他页面复用。 */
fun launcherInstalled(context: Context): Boolean = LauncherBridge.isInstalled(context)
