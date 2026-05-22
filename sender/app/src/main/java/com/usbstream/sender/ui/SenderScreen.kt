package com.usbstream.sender.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usbstream.sender.domain.model.SenderState
import com.usbstream.sender.domain.model.StreamStats

@Composable
fun SenderScreen(
    state: SenderState,
    stats: StreamStats,
    onStartStream: () -> Unit,
    onStopStream: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            HeaderBar(state = state)
            StatsDashboard(stats = stats, visible = state is SenderState.Streaming)
            ControlPanel(
                state = state,
                onStart = onStartStream,
                onStop = onStopStream
            )
        }
    }
}

@Composable
private fun HeaderBar(state: SenderState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "USB STREAM",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp
        )
        StatusIndicator(state = state)
    }
}

@Composable
private fun StatusIndicator(state: SenderState) {
    val (color, label) = when (state) {
        is SenderState.Idle -> Color(0xFF444444) to "IDLE"
        is SenderState.WaitingForUsb -> Color(0xFFFFAA00) to "WAITING FOR USB"
        is SenderState.Connected -> Color(0xFF00AAFF) to "CONNECTED"
        is SenderState.Streaming -> Color(0xFF00FF88) to "LIVE"
        is SenderState.Error -> Color(0xFFFF4444) to "ERROR"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (state is SenderState.Streaming) {
            PulsingDot(color = color)
            Spacer(Modifier.width(8.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(text = label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
    }
}

@Composable
private fun PulsingDot(color: Color) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.2f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(600),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

@Composable
private fun StatsDashboard(stats: StreamStats, visible: Boolean) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "PERFORMANCE",
                    color = Color(0xFF888888),
                    fontSize = 10.sp,
                    letterSpacing = 3.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    StatItem(label = "FPS", value = "%.1f".format(stats.fps), unit = "", color = Color(0xFF00FF88))
                    StatItem(label = "BITRATE", value = "%.1f".format(stats.bitrateMbps), unit = "Mbps", color = Color(0xFF00AAFF))
                    StatItem(label = "ENC LAT", value = "${stats.encoderLatencyMs}", unit = "ms", color = Color(0xFFFFAA00))
                    StatItem(label = "DROPPED", value = "${stats.droppedFrames}", unit = "", color = Color(0xFFFF4444))
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, unit: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = Color(0xFF666666),
            fontSize = 9.sp,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = color,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(2.dp))
                Text(
                    text = unit,
                    color = color.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ControlPanel(
    state: SenderState,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (state is SenderState.Error) {
            Text(
                text = state.message,
                color = Color(0xFFFF6666),
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        if (state is SenderState.WaitingForUsb) {
            Text(
                text = "Connect via USB cable. This device must be in USB Accessory mode.",
                color = Color(0xFF888888),
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            when (state) {
                is SenderState.Connected -> {
                    Button(
                        onClick = onStart,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00CC66)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(56.dp).fillMaxWidth(0.5f)
                    ) {
                        Text("START STREAM", fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    }
                }
                is SenderState.Streaming -> {
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCC2222)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(56.dp).fillMaxWidth(0.5f)
                    ) {
                        Text("STOP STREAM", fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    }
                }
                else -> {}
            }
        }
    }
}
