package com.example.edudel

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.edudel.ui.theme.EdudelTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private var isSimulationRunning by mutableStateOf(false)
    private var volumeUpPressCount by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EdudelTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isSimulationRunning) {
                        GpuFailureSimulationScreen(
                            pressCount = volumeUpPressCount,
                            targetPresses = 7
                        )
                    } else {
                        MainControlScreen(
                            onStartSimulation = {
                                volumeUpPressCount = 0
                                isSimulationRunning = true
                                Toast.makeText(
                                    this,
                                    "In-App Simulation Started. Press Volume Up 7 times to stop.",
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            onStartOverlayService = { stage ->
                                if (!Settings.canDrawOverlays(this)) {
                                    Toast.makeText(this, "Please grant 'Display over other apps' permission first.", Toast.LENGTH_LONG).show()
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:$packageName")
                                    )
                                    startActivity(intent)
                                } else {
                                    val serviceIntent = Intent(this, GpuMonitorOverlayService::class.java).apply {
                                        putExtra(GpuMonitorOverlayService.EXTRA_STAGE, stage)
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        startForegroundService(serviceIntent)
                                    } else {
                                        startService(serviceIntent)
                                    }
                                    Toast.makeText(this, "Background Stage $stage Overlay Started!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onStopOverlayService = {
                                val serviceIntent = Intent(this, GpuMonitorOverlayService::class.java).apply {
                                    action = GpuMonitorOverlayService.ACTION_STOP
                                }
                                startService(serviceIntent)
                                Toast.makeText(this, "Background GPU Overlay Stopped", Toast.LENGTH_SHORT).show()
                            },
                            onGrantPermission = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                                startActivity(intent)
                            },
                            onOpenAccessibilitySettings = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                startActivity(intent)
                                Toast.makeText(this, "Enable 'edudel' under Accessibility to activate top-layer overlay.", Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isSimulationRunning && keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            volumeUpPressCount++
            if (volumeUpPressCount >= 7) {
                isSimulationRunning = false
                volumeUpPressCount = 0
                Toast.makeText(this, "GPU Simulation Stopped via Volume Key shortcut", Toast.LENGTH_SHORT).show()
            } else {
                val remaining = 7 - volumeUpPressCount
                Toast.makeText(this, "Volume Up pressed ($volumeUpPressCount/7). $remaining left.", Toast.LENGTH_SHORT).show()
            }
            return true // Consume key event
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
fun MainControlScreen(
    onStartSimulation: () -> Unit,
    onStartOverlayService: (stage: Int) -> Unit,
    onStopOverlayService: () -> Unit,
    onGrantPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "GPU Failure Monitor (College Project)",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Simulates GPU artifacts & failure stages system-wide over other apps.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Background Overlay Controls & Stages",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Stage 1: Thin green vertical line overlay.\n" +
                                "• Stage 2: Full GPU failure & corruption simulation.\n" +
                                "• TYPE_ACCESSIBILITY_OVERLAY sits above status bar & lock screen.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Grant System Overlay Permission Button
            OutlinedButton(
                onClick = onGrantPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "1. Grant 'Display over other apps'")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Enable Accessibility Service Overlay Button
            OutlinedButton(
                onClick = onOpenAccessibilitySettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "2. Enable Accessibility Overlay (Highest Priority)")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stage selection buttons
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onStartOverlayService(1) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text(text = "START STAGE 1\n(Green Line)", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = { onStartOverlayService(2) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(text = "START STAGE 2\n(GPU Failure)", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Stop Background Overlay
            Button(
                onClick = onStopOverlayService,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(text = "STOP BACKGROUND OVERLAY")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // In-app simulation fallback
            OutlinedButton(
                onClick = onStartSimulation,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Test In-App Simulation (Volume Up x7 to Exit)")
            }
        }
    }
}

@Composable
fun GpuFailureSimulationScreen(pressCount: Int, targetPresses: Int) {
    var frameTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(33L)
            frameTick++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        SubtleGpuArtifactOverlay(stage = 2)

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
                .background(Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "GPU FAILURE SIMULATION",
                color = Color.Red,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Press VOLUME UP 7 times to exit ($pressCount/$targetPresses)",
                color = Color.Yellow,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}