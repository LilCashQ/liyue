package com.liyue.mctoolbox.home

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.RectangleShape
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import java.security.MessageDigest
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color

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

    private const val APK_NAME = "liyue-launcher-1.6.1.apk"
    const val VERSION_NAME = "1.6.1"
    const val VERSION_CODE = 59596200L
    private const val APK_SHA256 = "7cfebc583354030f09be1a2788cacf71bcbf4492d26dacc29e140c45ee3e5874"

    const val DOWNLOAD_URL =
        "https://github.com/LilCashQ/liyue/releases/download/launcher-1.6.1/liyue-launcher-1.6.1-arm64.apk"

    data class InstalledVersion(val name: String, val code: Long)

    fun installedVersion(context: Context): InstalledVersion? = runCatching {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(PACKAGE, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(PACKAGE, 0)
        }
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        InstalledVersion(info.versionName ?: "?", code)
    }.getOrNull()

    fun versionName(context: Context): String? = installedVersion(context)?.name

    fun isInstalled(context: Context): Boolean = installedVersion(context) != null

    fun needsUpdate(context: Context): Boolean =
        installedVersion(context)?.code?.let { it < VERSION_CODE } ?: false

    /** 返回 null 表示成功，否则返回给用户看的失败原因。 */
    fun launch(context: Context): String? {
        val explicit = Intent(ACTION_START).apply {
            setClassName(PACKAGE, ACTIVITY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val explicitError = runCatching { context.startActivity(explicit) }.exceptionOrNull()
        if (explicitError == null) return null

        // Older/modified launcher builds may not retain the custom action filter.
        val componentOnly = Intent().apply {
            setClassName(PACKAGE, ACTIVITY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val componentError = runCatching { context.startActivity(componentOnly) }.exceptionOrNull()
        if (componentError == null) return null

        val fallback = context.packageManager.getLaunchIntentForPackage(PACKAGE)
        if (fallback != null) {
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val fallbackError = runCatching { context.startActivity(fallback) }.exceptionOrNull()
            if (fallbackError == null) return null
            return describe(fallbackError)
        }
        return describe(componentError)
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
            val temp = File(target.parentFile, "${target.name}.download")
            temp.delete()
            val conn = (URL(DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 90_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "LiYue-Android")
            }
            try {
                conn.connect()
                if (conn.responseCode !in 200..299) error("下载失败：HTTP ${conn.responseCode}")
                val total = conn.contentLengthLong
                val sha256 = MessageDigest.getInstance("SHA-256")
                conn.inputStream.use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(1 shl 16)
                        var copied = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            sha256.update(buffer, 0, read)
                            copied += read
                            if (total > 0) onProgress((copied.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                if (total > 0 && temp.length() != total) {
                    error("下载不完整：${temp.length()} / $total 字节")
                }
                val digest = sha256.digest().joinToString("") { "%02x".format(it) }
                if (!digest.equals(APK_SHA256, ignoreCase = true)) {
                    error("安装包校验失败，请重新下载")
                }
                android.system.Os.rename(temp.absolutePath, target.absolutePath)
            } catch (t: Throwable) {
                temp.delete()
                throw t
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
    data class Idle(val installed: Boolean, val version: String?, val versionCode: Long?) : LaunchState
    data class Downloading(val progress: Float) : LaunchState
    data class Ready(val apk: File) : LaunchState
    data class Failed(val message: String) : LaunchState
}

@Composable
fun LaunchScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var installed by remember { mutableStateOf(LauncherBridge.installedVersion(context)) }
    var state by remember {
        mutableStateOf<LaunchState>(
            LaunchState.Idle(installed != null, installed?.name, installed?.code),
        )
    }

    fun refresh() {
        installed = LauncherBridge.installedVersion(context)
        if (state !is LaunchState.Downloading) {
            state = LaunchState.Idle(installed != null, installed?.name, installed?.code)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val installedVersion = installed?.name
    val installedCode = installed?.code
    val updateAvailable = installedCode != null && installedCode < LauncherBridge.VERSION_CODE

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF3E3B37))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            text = "启动游戏",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.3).sp,
            color = Color(0xFFF2F2F2),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "用里悦启动器跑基岩版：免安装导入官方包，多版本隔离，能挂载 SO 模块。",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFCFC6BA),
        )
        Spacer(Modifier.height(18.dp))

        HeroLaunchCard(
            installedVersion = installedVersion,
            updateAvailable = updateAvailable,
            cachedApk = LauncherBridge.apkFile(context).takeIf { it.isFile },
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
            title = "从里悦启动游戏",
            body = "启动器不会创建桌面图标；安装和卸载可在系统设置 → 应用 → 里悦启动器中管理。装好后可从这里点「开始游戏」打开。",
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
    updateAvailable: Boolean,
    cachedApk: File?,
    state: LaunchState,
    onLaunch: () -> Unit,
    onDownload: () -> Unit,
    onInstallCached: (File) -> Unit,
    onRefresh: () -> Unit,
) {
    val installed = installedVersion != null
    Surface(
        shape = RoundedCornerShape(0.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CubeMark(size = 52.dp, corner = 16.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "里悦启动器",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (installed) {
                            "已安装 · v$installedVersion${if (updateAvailable) " · 有更新" else ""}"
                        } else "未安装",
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
                            .clip(RectangleShape),
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
                            TextLink(
                                if (updateAvailable) "更新启动器" else "重新下载安装包",
                                onDownload,
                            )
                            if (cachedApk != null) TextLink("修复安装", { onInstallCached(cachedApk) })
                        }
                    } else {
                        LaunchActionButton(text = "下载并安装启动器", onClick = onDownload)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextLink("我已经装过了", onRefresh)
                            if (cachedApk != null) TextLink("继续安装", { onInstallCached(cachedApk) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LaunchActionButton(text: String, onClick: () -> Unit) {
    PixelButton(
        text = text,
        onClick = onClick,
        primary = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TextLink(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(0.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun InfoCard(title: String, body: String) {
    PixelPanel(modifier = Modifier.fillMaxWidth()) {
        Column {
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

/** 里悦的几何标记：蓝色圆角底 + 白色等距立方体，和桌面图标一致。 */
@Composable
internal fun CubeMark(size: Dp = 52.dp, corner: Dp = 16.dp) {
    Canvas(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(0.dp))
            .background(MaterialTheme.colorScheme.primary),
    ) {
        val w = this.size.width
        val h = this.size.height
        fun p(x: Float, y: Float) = Offset(w * x, h * y)
        val top = Path().apply {
            moveTo(p(0.5f, 0.23f).x, p(0.5f, 0.23f).y)
            lineTo(p(0.74f, 0.36f).x, p(0.74f, 0.36f).y)
            lineTo(p(0.5f, 0.49f).x, p(0.5f, 0.49f).y)
            lineTo(p(0.26f, 0.36f).x, p(0.26f, 0.36f).y)
            close()
        }
        val left = Path().apply {
            moveTo(p(0.26f, 0.36f).x, p(0.26f, 0.36f).y)
            lineTo(p(0.5f, 0.49f).x, p(0.5f, 0.49f).y)
            lineTo(p(0.5f, 0.81f).x, p(0.5f, 0.81f).y)
            lineTo(p(0.26f, 0.68f).x, p(0.26f, 0.68f).y)
            close()
        }
        val right = Path().apply {
            moveTo(p(0.74f, 0.36f).x, p(0.74f, 0.36f).y)
            lineTo(p(0.5f, 0.49f).x, p(0.5f, 0.49f).y)
            lineTo(p(0.5f, 0.81f).x, p(0.5f, 0.81f).y)
            lineTo(p(0.74f, 0.68f).x, p(0.74f, 0.68f).y)
            close()
        }
        drawPath(top, Color.White.copy(alpha = 1f))
        drawPath(left, Color.White.copy(alpha = 0.82f))
        drawPath(right, Color.White.copy(alpha = 0.64f))
    }
}
