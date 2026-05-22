package com.usbstream.receiver.viewmodel

import android.app.Application
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.usbstream.receiver.data.repository.ReceiverRepository
import com.usbstream.receiver.data.usb.UsbHostTransport
import com.usbstream.receiver.data.usb.UsbReceiverService
import com.usbstream.receiver.domain.model.ReceiverState
import com.usbstream.receiver.domain.model.ReceiverStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

class ReceiverViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ReceiverRepository(application)

    private val _state = MutableStateFlow<ReceiverState>(ReceiverState.Idle)
    val state: StateFlow<ReceiverState> = _state

    val stats: StateFlow<ReceiverStats> = repository.stats

    private var outputSurface: Surface? = null

    init {
        Timber.plant(Timber.DebugTree())
        observeTransportState()
        tryAutoConnect()
    }

    private fun observeTransportState() {
        repository.transport.state.onEach { transportState ->
            when (transportState) {
                is UsbHostTransport.State.Connected -> {
                    _state.value = ReceiverState.Connected
                    outputSurface?.let { startDecoding(it) }
                }
                is UsbHostTransport.State.Disconnected -> {
                    repository.stopDecoding()
                    _state.value = ReceiverState.WaitingForUsb
                    stopForegroundService()
                }
                is UsbHostTransport.State.Error -> {
                    _state.value = ReceiverState.Error(transportState.message)
                }
                is UsbHostTransport.State.PermissionPending -> {
                    _state.value = ReceiverState.WaitingForUsb
                }
            }
        }.launchIn(viewModelScope)
    }

    private fun tryAutoConnect() {
        val devices = repository.getConnectedDevices()
        if (devices.isNotEmpty()) {
            Timber.i("Auto-detected ${devices.size} USB device(s)")
            connect(devices.first())
        } else {
            _state.value = ReceiverState.WaitingForUsb
        }
    }

    fun onSurfaceReady(surface: Surface) {
        outputSurface = surface
        if (_state.value is ReceiverState.Connected) {
            startDecoding(surface)
        }
    }

    fun onSurfaceDestroyed() {
        outputSurface = null
        repository.stopDecoding()
        if (_state.value is ReceiverState.Receiving) {
            _state.value = ReceiverState.Connected
        }
    }

    fun connect(device: UsbDevice) {
        repository.connect(device)
    }

    fun onUsbDeviceAttached(intent: Intent) {
        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
        if (device != null) connect(device)
    }

    private fun startDecoding(surface: Surface) {
        runCatching {
            repository.startDecoding(surface)
            _state.value = ReceiverState.Receiving
            startForegroundService()
            Timber.i("Decoding started")
        }.onFailure {
            Timber.e(it, "Failed to start decoding")
            _state.value = ReceiverState.Error(it.message ?: "Decode start failed")
        }
    }

    private fun startForegroundService() {
        val intent = Intent(getApplication(), UsbReceiverService::class.java)
        getApplication<Application>().startForegroundService(intent)
    }

    private fun stopForegroundService() {
        val intent = Intent(getApplication(), UsbReceiverService::class.java)
        getApplication<Application>().stopService(intent)
    }

    override fun onCleared() {
        super.onCleared()
        repository.release()
    }
}
