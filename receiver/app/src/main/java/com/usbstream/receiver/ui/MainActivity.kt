package com.usbstream.receiver.ui

import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.usbstream.receiver.viewmodel.ReceiverViewModel
import timber.log.Timber

class MainActivity : ComponentActivity() {

    private val viewModel: ReceiverViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()

        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            viewModel.onUsbDeviceAttached(intent)
        }

        setContent {
            val state by viewModel.state.collectAsState()
            val stats by viewModel.stats.collectAsState()

            ReceiverScreen(
                state = state,
                stats = stats,
                onSurfaceReady = { surface -> viewModel.onSurfaceReady(surface) },
                onSurfaceDestroyed = { viewModel.onSurfaceDestroyed() }
            )
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            viewModel.onUsbDeviceAttached(intent)
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
