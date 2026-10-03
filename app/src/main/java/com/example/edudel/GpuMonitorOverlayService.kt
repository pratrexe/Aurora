package com.example.edudel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay
import kotlin.random.Random

class GpuMonitorOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null

    companion object {
        const val ACTION_STOP = "com.example.edudel.ACTION_STOP"
        const val EXTRA_STAGE = "com.example.edudel.EXTRA_STAGE"
        var currentStageState = mutableIntStateOf(1)
        private const val CHANNEL_ID = "GpuMonitorChannel"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val requestedStage = intent?.getIntExtra(EXTRA_STAGE, currentStageState.intValue) ?: currentStageState.intValue
        currentStageState.intValue = requestedStage

        startForeground(NOTIFICATION_ID, createNotification())
        showOverlay()

        return START_STICKY
    }

    private fun showOverlay() {
        if (overlayView != null) return

        @Suppress("DEPRECATION")
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@GpuMonitorOverlayService)
            setViewTreeSavedStateRegistryOwner(this@GpuMonitorOverlayService)
            setContent {
                SubtleGpuArtifactOverlay(stage = currentStageState.intValue)
            }
        }

        overlayView = composeView
        try {
            windowManager.addView(composeView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val lockIntent = Intent(this, LockScreenOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(lockIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GPU Failure Simulation Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Runs background screen GPU corruption simulation overlay"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, GpuMonitorOverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("GPU Failure Simulation Active")
            .setContentText("Stage ${currentStageState.intValue} simulation running...")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .addAction(
                Notification.Action.Builder(
                    null,
                    "Stop Simulation",
                    stopPendingIntent
                ).build()
            )
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

enum class GlitchState {
    NORMAL,
    SMALL_GLITCHES,
    SEVERE_CORRUPTION,
    FREEZE,
    PARTIAL_RECOVERY
}

@Composable
fun SubtleGpuArtifactOverlay(stage: Int = GpuMonitorOverlayService.currentStageState.intValue) {
    if (stage == 1) {
        // Stage 1: Just a thin green vertical line
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                if (width <= 0 || height <= 0) return@Canvas

                val lineX = width * 0.35f
                drawRect(
                    color = Color.Green,
                    topLeft = Offset(lineX, 0f),
                    size = Size(2.5f, height)
                )
            }
        }
        return
    }

    // Stage 2: Full GPU failure simulation
    var frameTick by remember { mutableIntStateOf(0) }

    val staticBlackSpots = remember {
        val random = Random(42)
        val list = mutableListOf<Pair<Offset, Size>>()
        repeat(90) {
            val bx = random.nextFloat() * 1080f
            val by = random.nextFloat() * 2400f
            val bSize = random.nextFloat() * 35f + 8f
            list.add(Pair(Offset(bx, by), Size(bSize, bSize)))
        }
        list
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(50L) // ~20 FPS
            frameTick++
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            if (width <= 0 || height <= 0) return@Canvas

            val state = when {
                frameTick < 100 -> GlitchState.NORMAL
                frameTick < 250 -> GlitchState.SMALL_GLITCHES
                frameTick < 450 -> GlitchState.SEVERE_CORRUPTION
                else -> GlitchState.FREEZE
            }

            val intensity = when (state) {
                GlitchState.NORMAL -> 0.05f
                GlitchState.SMALL_GLITCHES -> 0.3f
                GlitchState.SEVERE_CORRUPTION -> 1.0f
                GlitchState.FREEZE -> 0.8f
                else -> 0.5f
            }

            val frameSeed = if (state == GlitchState.FREEZE) {
                (frameTick / 25) * 17L
            } else {
                (frameTick * 17).toLong()
            }
            val random = Random(frameSeed)

            // 2. Horizontal Corruption
            val bandCount = 24
            val bandHeight = height / bandCount
            for (b in 0 until bandCount) {
                val bandY = b * bandHeight
                if (random.nextFloat() < (0.15f * intensity)) {
                    val xOffset = (random.nextFloat() * 30f - 15f) * intensity
                    val bandH = bandHeight * (0.8f + random.nextFloat() * 0.4f)
                    drawRect(
                        color = Color.Cyan.copy(alpha = 0.04f * intensity),
                        topLeft = Offset(xOffset.coerceAtLeast(0f), bandY),
                        size = Size(width, bandH)
                    )
                }
            }

            // Vertical Band Pixel Color Disorientation
            val vBandCount = 20
            val vBandWidth = width / vBandCount
            for (vb in 0 until vBandCount) {
                if (random.nextFloat() < (0.25f * intensity)) {
                    val vx = vb * vBandWidth
                    val yOffset = (random.nextFloat() * 40f - 20f) * intensity
                    val disorientColor = when (random.nextInt(6)) {
                        0 -> Color.Magenta.copy(alpha = 0.09f * intensity)
                        1 -> Color.Cyan.copy(alpha = 0.09f * intensity)
                        2 -> Color.Yellow.copy(alpha = 0.08f * intensity)
                        3 -> Color.Green.copy(alpha = 0.07f * intensity)
                        4 -> Color.Red.copy(alpha = 0.08f * intensity)
                        else -> Color.Blue.copy(alpha = 0.07f * intensity)
                    }
                    val bandH = height * (0.3f + random.nextFloat() * 0.7f)
                    val startY = random.nextFloat() * (height - bandH)
                    drawRect(
                        color = disorientColor,
                        topLeft = Offset(vx, (startY + yOffset).coerceIn(0f, height)),
                        size = Size(vBandWidth, bandH)
                    )
                }
            }

            // 3. RGB Channel Separation
            if (intensity > 0.1f) {
                val rgbShift = (random.nextFloat() * 12f - 6f) * intensity
                repeat(4) {
                    val ry = random.nextFloat() * height
                    val rh = random.nextFloat() * 10f + 2f
                    drawRect(
                        color = Color.Red.copy(alpha = 0.06f * intensity),
                        topLeft = Offset(rgbShift, ry),
                        size = Size(width, rh)
                    )
                    drawRect(
                        color = Color.Cyan.copy(alpha = 0.06f * intensity),
                        topLeft = Offset(-rgbShift, ry + 2f),
                        size = Size(width, rh)
                    )
                }
            }

            // 4. VRAM-style Block Corruption
            val blockCount = (12 * intensity).toInt()
            repeat(blockCount) {
                val bx = random.nextFloat() * width
                val by = random.nextFloat() * height
                val bw = random.nextFloat() * 80f + 10f
                val bh = random.nextFloat() * 40f + 8f
                val blockColor = when (random.nextInt(5)) {
                    0 -> Color.Red
                    1 -> Color.Green
                    2 -> Color.Blue
                    3 -> Color.Magenta
                    else -> Color.Yellow
                }
                drawRect(
                    color = blockColor.copy(alpha = 0.1f * intensity),
                    topLeft = Offset(bx, by),
                    size = Size(bw, bh)
                )
            }

            // 5. Texture & UI Corruption (Polka dots, checkerboard, polygon tearing)
            val polkaCount = (30 * intensity).toInt()
            repeat(polkaCount) {
                val cx = random.nextFloat() * width
                val cy = random.nextFloat() * height
                val radius = random.nextFloat() * 15f + 4f
                val color = when (random.nextInt(5)) {
                    0 -> Color.Red
                    1 -> Color.Yellow
                    2 -> Color.Cyan
                    3 -> Color.Magenta
                    else -> Color.Green
                }
                drawCircle(
                    color = color.copy(alpha = 0.1f * intensity),
                    radius = radius,
                    center = Offset(cx, cy)
                )
            }

            // Polygon geometry tearing
            if (intensity > 0.2f) {
                repeat((3 * intensity).toInt()) {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        val x1 = random.nextFloat() * width
                        val y1 = random.nextFloat() * height
                        val x2 = x1 + random.nextFloat() * 60f - 30f
                        val y2 = y1 + random.nextFloat() * 60f - 30f
                        val x3 = x1 + random.nextFloat() * 60f - 30f
                        val y3 = y1 + random.nextFloat() * 60f - 30f
                        moveTo(x1, y1)
                        lineTo(x2, y2)
                        lineTo(x3, y3)
                        close()
                    }
                    drawPath(
                        path = path,
                        color = if (random.nextBoolean()) Color.White.copy(alpha = 0.05f * intensity) else Color.Black.copy(alpha = 0.08f * intensity)
                    )
                }
            }

            // Checkerboard missing texture patch
            if (random.nextFloat() < (0.5f * intensity)) {
                val cx = random.nextFloat() * (width - 120f)
                val cy = random.nextFloat() * (height - 120f)
                val gridSize = 12f
                for (r in 0 until 4) {
                    for (c in 0 until 4) {
                        if ((r + c) % 2 == 0) {
                            drawRect(
                                color = Color.Magenta.copy(alpha = 0.08f * intensity),
                                topLeft = Offset(cx + c * gridSize, cy + r * gridSize),
                                size = Size(gridSize, gridSize)
                            )
                        }
                    }
                }
            }

            // 7. Persistent static blackouts
            val visibleSpotsCount = (staticBlackSpots.size * intensity).toInt().coerceIn(2, staticBlackSpots.size)
            for (i in 0 until visibleSpotsCount) {
                val spot = staticBlackSpots[i]
                val actualX = (spot.first.x / 1080f) * width
                val actualY = (spot.first.y / 2400f) * height
                drawRect(
                    color = Color.Black.copy(alpha = 0.85f),
                    topLeft = Offset(actualX, actualY),
                    size = spot.second
                )
            }
        }
    }
}