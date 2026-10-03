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
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@GpuMonitorOverlayService)
            setViewTreeSavedStateRegistryOwner(this@GpuMonitorOverlayService)
            setContent {
                SubtleGpuArtifactOverlay()
            }
        }

        overlayView = composeView
        try {
            windowManager.addView(composeView, params)
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
            .setContentText("Subtle GPU corruption overlay running...")
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

@Composable
fun SubtleGpuArtifactOverlay() {
    var frameTick by remember { mutableIntStateOf(0) }

    // Generate a fixed, stable set of permanent blackout spots when entering composition
    val staticBlackSpots = remember {
        val random = Random(42) // Fixed seed for persistent static placement
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

            val random = Random((frameTick * 17).toLong())

            // 1. Polka dots (circles)
            repeat(25) {
                val cx = random.nextFloat() * width
                val cy = random.nextFloat() * height
                val radius = random.nextFloat() * 15f + 5f
                val color = when (random.nextInt(5)) {
                    0 -> Color.Red
                    1 -> Color.Yellow
                    2 -> Color.Cyan
                    3 -> Color.Magenta
                    else -> Color.Green
                }
                drawCircle(
                    color = color.copy(alpha = 0.12f),
                    radius = radius,
                    center = Offset(cx, cy)
                )
            }

            // 2. Random colored blocks / squares
            repeat(8) {
                val bx = random.nextFloat() * width
                val by = random.nextFloat() * height
                val bw = random.nextFloat() * 60f + 10f
                val bh = random.nextFloat() * 20f + 5f
                val color = when (random.nextInt(4)) {
                    0 -> Color.Red
                    1 -> Color.Green
                    2 -> Color.Blue
                    else -> Color.Magenta
                }
                drawRect(
                    color = color.copy(alpha = 0.08f),
                    topLeft = Offset(bx, by),
                    size = Size(bw, bh)
                )
            }

            // 3. Random colored pixels ("sparkles")
            repeat(40) {
                val px = random.nextFloat() * width
                val py = random.nextFloat() * height
                drawRect(
                    color = if (random.nextBoolean()) Color.White.copy(alpha = 0.15f) else Color.Cyan.copy(alpha = 0.15f),
                    topLeft = Offset(px, py),
                    size = Size(3f, 3f)
                )
            }

            // 4. Black or white triangles / polygons
            repeat(3) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    val x1 = random.nextFloat() * width
                    val y1 = random.nextFloat() * height
                    val x2 = x1 + random.nextFloat() * 50f - 25f
                    val y2 = y1 + random.nextFloat() * 50f - 25f
                    val x3 = x1 + random.nextFloat() * 50f - 25f
                    val y3 = y1 + random.nextFloat() * 50f - 25f
                    moveTo(x1, y1)
                    lineTo(x2, y2)
                    lineTo(x3, y3)
                    close()
                }
                drawPath(
                    path = path,
                    color = if (random.nextBoolean()) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.08f)
                )
            }

            // 5. Horizontal and vertical artifact lines
            repeat(5) {
                val lineY = random.nextFloat() * height
                drawRect(
                    color = Color.Yellow.copy(alpha = 0.06f),
                    topLeft = Offset(0f, lineY),
                    size = Size(width, 1.5f)
                )
            }
            repeat(3) {
                val lineX = random.nextFloat() * width
                drawRect(
                    color = Color.Green.copy(alpha = 0.05f),
                    topLeft = Offset(lineX, 0f),
                    size = Size(1.5f, height)
                )
            }

            // 6. Checkerboard / missing texture pattern patch
            if (random.nextFloat() < 0.4f) {
                val cx = random.nextFloat() * (width - 100f)
                val cy = random.nextFloat() * (height - 100f)
                val gridSize = 12f
                for (r in 0 until 4) {
                    for (c in 0 until 4) {
                        if ((r + c) % 2 == 0) {
                            drawRect(
                                color = Color.Magenta.copy(alpha = 0.07f),
                                topLeft = Offset(cx + c * gridSize, cy + r * gridSize),
                                size = Size(gridSize, gridSize)
                            )
                        }
                    }
                }
            }

            // 7. Stable, non-sparkly persistent blackouts that stay fixed at the exact same spot until turned off
            val elapsedSeconds = frameTick / 20 // 20 FPS -> 20 ticks = 1 second
            val visibleSpotsCount = (elapsedSeconds * 4).coerceIn(0, staticBlackSpots.size)

            for (i in 0 until visibleSpotsCount) {
                val spot = staticBlackSpots[i]
                val actualX = (spot.first.x / 1080f) * width
                val actualY = (spot.first.y / 2400f) * height
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(actualX, actualY),
                    size = spot.second
                )
            }

            // When all static spots are visible, gradually fade to full black screen (turn off simulation)
            if (visibleSpotsCount >= staticBlackSpots.size) {
                val fullBlackAlpha = ((elapsedSeconds - (staticBlackSpots.size / 4)) / 10f).coerceIn(0f, 1f)
                drawRect(
                    color = Color.Black.copy(alpha = fullBlackAlpha),
                    topLeft = Offset(0f, 0f),
                    size = Size(width, height)
                )
            }
        }
    }
}