package com.usbstream.sender.viewmodel

import android.app.Application
import android.content.Intent
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.usbstream.sender.data.repository.SenderRepository
import com.usbstream.sender.data.usb.UsbAccessoryTransport
import com.usbstream.sender.domain.model.SenderState
import com.usbstream.sender.domain.model.StreamConfig
import com.usbstream.sender.domain.model.StreamStats
import com.usbstream.sender.data.usb.UsbStreamingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

class SenderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SenderRepository(application)

    private val _senderState = MutableStateFlow<SenderState>(SenderState.Idle)
    val senderState: StateFlow<SenderState> = _senderState

    val stats: StateFlow<StreamStats> = repository.stats

    private val _config = MutableStateFlow(StreamConfig())
    val config: StateFlow<StreamConfig> = _config

    init {
        Timber.plant(Timber.DebugTree())
        observeTransportState()
        // Auto-detect existing accessory on startup
        tryAutoConnect()
    }

    private fun observeTransportState() {
        repository.transport.state.onEach { transportState ->
            when (transportState) {
                is UsbAccessoryTransport.State.Connected -> {
                    _senderState.value = SenderState.Connected
                }
                is UsbAccessoryTransport.State.Disconnected -> {
                    if (_senderState.value is SenderState.Streaming) {
                        repository.stopStreaming()
                    }
                    _senderState.value = SenderState.WaitingForUsb
                    stopForegroundService()
                }
                is UsbAccessoryTransport.State.Error -> {
                    _senderState.value = SenderState.Error(transportState.message)
                }
                is UsbAccessoryTransport.State.PermissionPending -> {
                    _senderState.value = SenderState.WaitingForUsb
                }
            }
        }.launchIn(viewModelScope)
    }

    private fun tryAutoConnect() {
        val accessory = repository.getAvailableAccessory()
        if (accessory != null) {
            Timber.i("Auto-detected USB accessory: ${accessory.model}")
            onUsbAccessoryAttached(accessory)
        } else {
            _senderState.value = SenderState.WaitingForUsb
        }
    }

    fun onUsbAccessoryAttached(accessory: UsbAccessory) {
        repository.connect(accessory)
    }

    fun onUsbIntentReceived(intent: Intent) {
        val accessory = intent.getParcelableExtra<UsbAccessory>(UsbManager.EXTRA_ACCESSORY)
        if (accessory != null) {
            onUsbAccessoryAttached(accessory)
        }
    }

    fun startStreaming(lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
        if (_senderState.value !is SenderState.Connected) return
        viewModelScope.launch {
            runCatching {
                _senderState.value = SenderState.Streaming
                startForegroundService()
                repository.startStreaming(lifecycleOwner, _config.value)
                Timber.i("Streaming started")
            }.onFailure {
                Timber.e(it, "Failed to start streaming")
                _senderState.value = SenderState.Error(it.message ?: "Start failed")
                stopForegroundService()
            }
        }
    }

    fun stopStreaming() {
        repository.stopStreaming()
        _senderState.value = SenderState.Connected
        stopForegroundService()
    }

    fun setResolution(width: Int, height: Int) {
        _config.value = _config.value.copy(videoWidth = width, videoHeight = height)
    }

    fun setBitrateRange(min: Int, max: Int) {
        _config.value = _config.value.copy(videoBitrateMin = min, videoBitrateMax = max)
    }

    private fun startForegroundService() {
        val intent = Intent(getApplication(), UsbStreamingService::class.java)
        getApplication<Application>().startForegroundService(intent)
    }

    private fun stopForegroundService() {
        val intent = Intent(getApplication(), UsbStreamingService::class.java)
        getApplication<Application>().stopService(intent)
    }

    override fun onCleared() {
        super.onCleared()
        repository.release()
    }
}
