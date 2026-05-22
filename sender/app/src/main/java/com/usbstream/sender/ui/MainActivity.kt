package com.usbstream.sender.ui

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.usbstream.sender.viewmodel.SenderViewModel
import timber.log.Timber

class MainActivity : ComponentActivity() {

    private val viewModel: SenderViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            Timber.i("All permissions granted")
        } else {
            Timber.w("Some permissions denied: ${results.filter { !it.value }.keys}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        requestRequiredPermissions()

        // Handle USB accessory intent on cold start
        if (intent?.action == UsbManager.ACTION_USB_ACCESSORY_ATTACHED) {
            viewModel.onUsbIntentReceived(intent)
        }

        setContent {
            val state by viewModel.senderState.collectAsState()
            val stats by viewModel.stats.collectAsState()

            SenderScreen(
                state = state,
                stats = stats,
                onStartStream = { viewModel.startStreaming(this) },
                onStopStream = { viewModel.stopStreaming() }
            )
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.action == UsbManager.ACTION_USB_ACCESSORY_ATTACHED) {
            viewModel.onUsbIntentReceived(intent)
        }
    }

    private fun requestRequiredPermissions() {
        val required = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
