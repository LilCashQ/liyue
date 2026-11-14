package com.liyue.mctoolbox.convert

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.ResultReceiver
import com.liyue.mctoolbox.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Runs one conversion inside a dedicated `:chunker` process. The Chunker engine
 * keeps a lot of chunk data live while it works; isolating it here gives the job
 * its own heap and keeps an OOM from taking the UI down with it.
 */
class ConvertService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // Chunker has no abort API, so cancelling means tearing the process down.
        if (running) {
            running = false
            android.os.Process.killProcess(android.os.Process.myPid())
        }
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val input = intent?.getStringExtra(EXTRA_INPUT)?.let(::File)
        val output = intent?.getStringExtra(EXTRA_OUTPUT)?.let(::File)
        val target = intent?.getStringExtra(EXTRA_TARGET)?.let(::File)
        val format = intent?.getStringExtra(EXTRA_FORMAT)
        val receiver = intent?.getParcelableExtra<ResultReceiver>(EXTRA_RECEIVER)
        if (input == null || output == null || target == null || format == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        running = true
        scope.launch {
            val outcome = runCatching {
                WorldConverter.convert(this@ConvertService, input, output, format) { fraction, text ->
                    receiver?.send(
                        Activity.RESULT_OK,
                        android.os.Bundle().apply {
                            putInt(KEY_KIND, KIND_PROGRESS)
                            putFloat(KEY_PROGRESS, fraction)
                            putString(KEY_STAGE, text)
                        },
                    )
                }
                WorldConverter.zip(output, target)
                output.deleteRecursively()
            }
            outcome.onSuccess {
                receiver?.send(Activity.RESULT_OK, android.os.Bundle().apply {
                    putInt(KEY_KIND, KIND_DONE)
                    putString(KEY_PATH, target.absolutePath)
                })
            }.onFailure { error ->
                val outOfMemory = error is OutOfMemoryError || error.cause is OutOfMemoryError
                receiver?.send(Activity.RESULT_OK, android.os.Bundle().apply {
                    putInt(KEY_KIND, KIND_ERROR)
                    putString(KEY_MESSAGE, error.message ?: error.toString())
                    putBoolean(KEY_OOM, outOfMemory)
                })
                output.deleteRecursively()
                target.delete()
            }
            running = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "存档转换",
                NotificationManager.IMPORTANCE_LOW,
            )
            manager.createNotificationChannel(channel)
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("正在转换存档")
            .setContentText("转换完成前请不要退出里悦")
            .setSmallIcon(R.drawable.ic_stat_convert)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val EXTRA_INPUT = "input"
        const val EXTRA_OUTPUT = "output"
        const val EXTRA_TARGET = "target"
        const val EXTRA_FORMAT = "format"
        const val EXTRA_RECEIVER = "receiver"

        const val KEY_KIND = "kind"
        const val KEY_PROGRESS = "progress"
        const val KEY_STAGE = "stage"
        const val KEY_PATH = "path"
        const val KEY_MESSAGE = "message"
        const val KEY_OOM = "oom"

        const val KIND_PROGRESS = 1
        const val KIND_DONE = 2
        const val KIND_ERROR = 3

        private const val CHANNEL_ID = "convert"
        private const val NOTIFICATION_ID = 8125
    }
}
