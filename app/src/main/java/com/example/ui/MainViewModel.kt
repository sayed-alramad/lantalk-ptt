package com.example.ui

import androidx.lifecycle.ViewModel
import com.example.model.ServiceStatus
import com.example.service.AudioService
import com.example.util.NetworkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MainUiState(
    val localIp: String? = null,
    val remoteIpInput: String = "",
    val portInput: String = "8888",
    val status: ServiceStatus = ServiceStatus.IDLE,
    val amplitude: Float = 0f,
    val ipError: String? = null,
    val portError: String? = null,
    val generalError: String? = null,
    val diagnostics: String = "",
    val bindWifi: Boolean = false,
    val isServiceBound: Boolean = false
)

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var audioService: AudioService? = null

    init {
        refreshLocalIp()
    }

    fun onServiceConnected(service: AudioService) {
        audioService = service
        _uiState.value = _uiState.value.copy(
            isServiceBound = true,
            status = service.status.value,
            localIp = service.localIp.value ?: NetworkUtils.getPreferredLocalIpv4(),
            remoteIpInput = if (_uiState.value.remoteIpInput.isEmpty()) service.remoteIp.value else _uiState.value.remoteIpInput,
            portInput = service.port.value.toString()
        )
    }

    fun onServiceDisconnected() {
        audioService = null
        _uiState.value = _uiState.value.copy(isServiceBound = false, status = ServiceStatus.IDLE)
    }

    fun updateStatus(status: ServiceStatus) {
        _uiState.value = _uiState.value.copy(status = status)
    }

    fun updateAmplitude(amp: Float) {
        _uiState.value = _uiState.value.copy(amplitude = amp)
    }

    fun updateServiceError(err: String?) {
        _uiState.value = _uiState.value.copy(generalError = err)
    }

    fun setBindWifi(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(bindWifi = enabled)
    }

    fun updateDiagnostics(text: String) {
        _uiState.value = _uiState.value.copy(diagnostics = text)
    }

    fun setRemoteIp(ip: String) {
        // Accepts Arabic-Indic digits and Arabic/regular commas and turns them into a normal IPv4 string
        val trimmed = NetworkUtils.normalizeIpInput(ip).trim()
        val error = if (trimmed.isNotEmpty() && !NetworkUtils.isValidIpv4(trimmed)) {
            "Invalid IPv4 (e.g. 192.168.1.15)"
        } else {
            null
        }
        _uiState.value = _uiState.value.copy(
            remoteIpInput = trimmed,
            ipError = error
        )
        if (error == null && trimmed.isNotEmpty()) {
            val portInt = _uiState.value.portInput.toIntOrNull() ?: 8888
            audioService?.updateRemoteEndpoint(trimmed, portInt)
        }
    }

    fun setPort(portStr: String) {
        val filtered = portStr.filter { it.isDigit() }.take(5)
        val portInt = filtered.toIntOrNull()
        val error = when {
            filtered.isEmpty() -> "Port is required"
            portInt == null || !NetworkUtils.isValidPort(portInt) -> "Port must be 1024-65535"
            else -> null
        }
        _uiState.value = _uiState.value.copy(
            portInput = filtered,
            portError = error
        )
        if (error == null && portInt != null) {
            audioService?.updateRemoteEndpoint(_uiState.value.remoteIpInput, portInt)
        }
    }

    fun refreshLocalIp() {
        val ip = NetworkUtils.getPreferredLocalIpv4()
        _uiState.value = _uiState.value.copy(localIp = ip)
        audioService?.refreshLocalIp()
    }

    fun toggleService(onStartCommand: (port: Int, remoteIp: String, remotePort: Int) -> Unit) {
        val state = _uiState.value
        if (state.status.isActive) {
            audioService?.stopPttService()
        } else {
            // Validate inputs
            val portInt = state.portInput.toIntOrNull()
            if (portInt == null || !NetworkUtils.isValidPort(portInt)) {
                _uiState.value = _uiState.value.copy(portError = "Valid port 1024-65535 required")
                return
            }

            if (state.remoteIpInput.isNotEmpty() && !NetworkUtils.isValidIpv4(state.remoteIpInput)) {
                _uiState.value = _uiState.value.copy(ipError = "Invalid target IPv4 address")
                return
            }

            onStartCommand(portInt, state.remoteIpInput, portInt)
        }
    }

    fun onPttPressed(): Boolean {
        val state = _uiState.value
        if (!state.status.isActive) {
            _uiState.value = _uiState.value.copy(generalError = "Start the service first before pressing Hold-to-Talk.")
            return false
        }
        if (!NetworkUtils.isValidIpv4(state.remoteIpInput)) {
            _uiState.value = _uiState.value.copy(
                ipError = "Specify a valid remote peer IPv4 address",
                generalError = "Cannot transmit: peer IP address is invalid or empty."
            )
            return false
        }
        audioService?.setTransmitting(true)
        return true
    }

    fun onPttReleased() {
        audioService?.setTransmitting(false)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(generalError = null)
    }
}
