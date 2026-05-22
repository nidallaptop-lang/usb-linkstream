package com.usbstream.receiver.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.usbstream.receiver.domain.model.ReceiverState
import com.usbstream.receiver.domain.model.ReceiverStats

@Composable
fun ReceiverScreen(
    state: ReceiverState,
    stats: ReceiverStats,
    onSurfaceReady: (android.view.Surface) -> Unit,
    onSurfaceDestroyed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Full-screen SurfaceView for decoded video
        VideoSurface(
            modifier = Modifier.fillMaxSize(),
            onSurfaceReady = onSurfaceReady,
            onSurfaceDestroyed = onSurfaceDestroyed
        )

        // Overlay HUD — always on top
        HudOverlay(
            state = state,
            stats = stats,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun VideoSurface(
    modifier: Modifier = Modifier,
    onSurfaceReady: (android.view.Surface) -> Unit,
    onSurfaceDestroyed: () -> Unit
) {
    val context = LocalContext.current
    val surfaceView = remember { SurfaceView(context) }

    DisposableEffect(surfaceView) {
        val callback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                onSurfaceReady(holder.surface)
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                onSurfaceDestroyed()
            }
        }
        surfaceView.holder.addCallback(callback)
        onDispose { surfaceView.holder.removeCallback(callback) }
    }

    AndroidView(factory = { surfaceView }, modifier = modifier)
}

@Composable
private fun HudOverlay(
    state: ReceiverState,
    stats: ReceiverStats,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "USB STREAM",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )
            ReceiverStatusBadge(state = state)
        }

        // Bottom stats
        AnimatedVisibility(visible = state is ReceiverState.Receiving, enter = fadeIn(), exit = fadeOut()) {
            StatsBar(stats = stats)
        }

        // Waiting overlay
        AnimatedVisibility(
            visible = state is ReceiverState.WaitingForUsb || state is ReceiverState.Idle,
            enter = fadeIn(), exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "WAITING FOR SENDER",
                        color = Color(0xFFAAAAAA),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Connect sender via USB OTG cable",
                        color = Color(0xFF666666),
                        fontSize = 13.sp
                    )
                }
            }
        }

        if (state is ReceiverState.Error) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "ERROR: ${state.message}",
                    color = Color(0xFFFF4444),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ReceiverStatusBadge(state: ReceiverState) {
    val (color, label) = when (state) {
        is ReceiverState.Idle -> Color(0xFF444444) to "IDLE"
        is ReceiverState.WaitingForUsb -> Color(0xFFFFAA00) to "WAITING"
        is ReceiverState.Connected -> Color(0xFF00AAFF) to "READY"
        is ReceiverState.Receiving -> Color(0xFF00FF88) to "LIVE"
        is ReceiverState.Error -> Color(0xFFFF4444) to "ERROR"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(text = label, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
    }
}

@Composable
private fun StatsBar(stats: ReceiverStats) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            MiniStat("FPS", "%.1f".format(stats.fps), Color(0xFF00FF88))
            MiniStat("BIT", "%.1f".format(stats.bitrateMbps), Color(0xFF00AAFF))
            MiniStat("E2E", "${stats.e2eLatencyMs}ms", Color(0xFFFFAA00))
            MiniStat("SYNC", "${stats.avSyncDriftMs}ms",
                if (kotlin.math.abs(stats.avSyncDriftMs) < 5L) Color(0xFF00FF88) else Color(0xFFFFAA00))
            MiniStat("DROP", "${stats.droppedFrames}", Color(0xFFFF6666))
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Color(0xFF888888), fontSize = 8.sp, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(2.dp))
        Text(text = value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}
