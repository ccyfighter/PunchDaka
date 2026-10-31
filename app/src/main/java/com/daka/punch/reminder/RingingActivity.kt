// 目标路径: app/src/main/java/com/daka/punch/reminder/RingingActivity.kt
package com.daka.punch.reminder

import android.app.KeyguardManager
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daka.punch.data.Prefs
import com.daka.punch.data.Repository
import com.daka.punch.ui.theme.PunchTheme
import com.daka.punch.util.TimeUtils
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 闹钟式响铃界面：
 *  - 锁屏 / 息屏下直接全屏显示
 *  - 振动 + 本地生成的提示音（不读取任何媒体文件，无权限、无联网）
 *  - 只能"向右滑动"关闭，关闭后回到手机主页面
 */
class RingingActivity : ComponentActivity() {

    private var tone: ToneGenerator? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var ringRunnable: Runnable? = null
    private var stopRunnable: Runnable? = null
    private var reminderId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Prefs.init(this)
        Repository.init(this)

        reminderId = intent?.getLongExtra(AlarmScheduler.EXTRA_ID, -1L) ?: -1L
        val label = intent?.getStringExtra("label") ?: "打卡提醒"

        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        try {
            val km = getSystemService(KeyguardManager::class.java)
            km.requestDismissKeyguard(this, null)
        } catch (e: Exception) {
            // 忽略：无密码锁屏时可能失败
        }

        acquireWakeLock()
        startRinging()

        setContent {
            PunchTheme {
                RingingScreen(
                    label = label,
                    onDismiss = { dismissToHome() },
                    onSnooze = { snoozeAndHome() }
                )
            }
        }

        stopRunnable = Runnable { stopRinging() }
        handler.postDelayed(stopRunnable!!, (Prefs.ringSeconds.coerceAtLeast(10)) * 1000L)
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "punch:Ringing"
            )
            wakeLock?.acquire(3 * 60 * 1000L)
        } catch (e: Exception) {
            // 忽略：部分设备限制
        }
    }

    private fun startRinging() {
        try {
            tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        } catch (e: Exception) {
            tone = null
        }
        try {
            val vm = getSystemService(VibratorManager::class.java)
            vibrator = vm.defaultVibrator
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500, 700), 0))
        } catch (e: Exception) {
            // 忽略
        }
        val r = object : Runnable {
            override fun run() {
                try {
                    tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 900)
                } catch (e: Exception) {
                    // 忽略
                }
                handler.postDelayed(this, 1300L)
            }
        }
        ringRunnable = r
        handler.post(r)
    }

    private fun stopRinging() {
        ringRunnable?.let { handler.removeCallbacks(it) }
        ringRunnable = null
        try {
            tone?.stopTone()
            tone?.release()
        } catch (e: Exception) {
            // 忽略
        }
        tone = null
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            // 忽略
        }
        vibrator = null
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
            // 忽略
        }
        wakeLock = null
    }

    private fun dismissToHome() {
        stopRinging()
        if (reminderId > 0) Notifier.cancel(this, reminderId)
        goHome()
    }

    private fun snoozeAndHome() {
        if (reminderId > 0) {
            AlarmScheduler.scheduleSnooze(this, reminderId, 10 * 60 * 1000L)
            Notifier.cancel(this, reminderId)
        }
        stopRinging()
        goHome()
    }

    /** 关闭本界面并返回手机主页面 */
    private fun goHome() {
        finish()
        try {
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(home)
        } catch (e: Exception) {
            // 忽略：极端情况下没有桌面应用
        }
    }

    override fun onDestroy() {
        stopRunnable?.let { handler.removeCallbacks(it) }
        stopRinging()
        releaseWakeLock()
        super.onDestroy()
    }
}

@Composable
private fun RingingScreen(
    label: String,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    // 拦截返回键：必须右滑关闭
    BackHandler { /* 不响应，强制右滑 */ }

    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val thresholdPx = with(density) { (config.screenWidthDp.dp * 0.35f).toPx() }

    var dragX by remember { mutableFloatStateOf(0f) }
    val offsetX by animateFloatAsState(
        targetValue = dragX,
        animationSpec = tween(durationMillis = 130),
        label = "swipe"
    )

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val progress = (offsetX / thresholdPx).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF6D5EFF), Color(0xFF4F46E5), Color(0xFF0EA5E9))
                )
            )
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragX >= thresholdPx) onDismiss() else dragX = 0f
                    },
                    onDragCancel = { dragX = 0f },
                    onHorizontalDrag = { _, amount ->
                        dragX = (dragX + amount).coerceIn(0f, thresholdPx * 1.6f)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp)
                .graphicsLayer {
                    translationX = offsetX
                    alpha = 1f - (progress * 0.55f)
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label.ifBlank { "打卡提醒" },
                style = MaterialTheme.typography.titleLarge,
                color = Color.White.copy(alpha = 0.92f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = TimeUtils.prettyDay(TimeUtils.todayKey(Prefs.dayStartHour)),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f)
            )
            Spacer(modifier = Modifier.height(26.dp))
            Text(
                text = TimeUtils.formatClock(now),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 76.sp
                ),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(70.dp))

            Surface(
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.16f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "向右滑动关闭",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Button(
                onClick = onSnooze,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF4F46E5)
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier.height(48.dp)
            ) {
                Text(text = "稍后 10 分钟", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
