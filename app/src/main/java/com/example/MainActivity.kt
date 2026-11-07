package com.example

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.service.AudioService
import com.example.ui.LanTalkieScreen
import com.example.ui.MainViewModel
import com.example.ui.theme.LanTalkieTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var audioService: AudioService? = null
    private var isBound = false

    private var hasRecordAudioPermission by mutableStateOf(false)
    private var hasNotificationPermission by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasRecordAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission = permissions[Manifest.permission.POST_NOTIFICATIONS] == true
        } else {
            hasNotificationPermission = true
        }

        if (!hasRecordAudioPermission) {
            viewModel.updateServiceError("Microphone permission was denied. LAN Talkie requires microphone access to transmit voice.")
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? AudioService.LocalBinder
            val service = localBinder?.getService()
            if (service != null) {
                audioService = service
                isBound = true
                viewModel.onServiceConnected(service)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audioService = null
            isBound = false
            viewModel.onServiceDisconnected()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Volume keys control the voice-call stream, which is the one the received audio plays on
        volumeControlStream = AudioManager.STREAM_VOICE_CALL

        checkPermissions()
        bindPttService()

        setContent {
            LanTalkieTheme {
                val uiState by viewModel.uiState.collectAsState()

                // Observe AudioService StateFlows when bound
                LaunchedEffect(audioService) {
                    val service = audioService ?: return@LaunchedEffect
                    launch {
                        service.status.collect { status ->
                            viewModel.updateStatus(status)
                        }
                    }
                    launch {
                        service.amplitude.collect { amp ->
                            viewModel.updateAmplitude(amp)
                        }
                    }
                    launch {
                        service.errorMessage.collect { err ->
                            viewModel.updateServiceError(err)
                        }
                    }
                    launch {
                        service.diagnostics.collect { text ->
                            viewModel.updateDiagnostics(text)
                        }
                    }
                }

                LanTalkieScreen(
                    uiState = uiState,
                    onRemoteIpChange = { viewModel.setRemoteIp(it) },
                    onPortChange = { viewModel.setPort(it) },
                    onBindWifiChange = { viewModel.setBindWifi(it) },
                    onRefreshIp = { viewModel.refreshLocalIp() },
                    onToggleService = {
                        viewModel.toggleService { port, remoteIp, remotePort ->
                            startAndBindService(port, remoteIp, remotePort)
                        }
                    },
                    onPttPress = {
                        if (!hasRecordAudioPermission) {
                            requestAppPermissions()
                            false
                        } else {
                            viewModel.onPttPressed()
                        }
                    },
                    onPttRelease = { viewModel.onPttReleased() },
                    onDismissError = { viewModel.dismissError() },
                    hasRecordPermission = hasRecordAudioPermission,
                    onRequestPermissions = { requestAppPermissions() }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        bindPttService()
        viewModel.refreshLocalIp()
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun checkPermissions() {
        hasRecordAudioPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestAppPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun bindPttService() {
        if (!isBound) {
            val intent = Intent(this, AudioService::class.java)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun startAndBindService(port: Int, remoteIp: String, remotePort: Int) {
        if (!hasRecordAudioPermission) {
            requestAppPermissions()
            return
        }

        val startIntent = Intent(this, AudioService::class.java).apply {
            action = AudioService.ACTION_START
            putExtra(AudioService.EXTRA_PORT, port)
            putExtra(AudioService.EXTRA_REMOTE_IP, remoteIp)
            putExtra(AudioService.EXTRA_REMOTE_PORT, remotePort)
            putExtra(AudioService.EXTRA_BIND_WIFI, viewModel.uiState.value.bindWifi)
        }
        ContextCompat.startForegroundService(this, startIntent)
        bindPttService()
    }
}
