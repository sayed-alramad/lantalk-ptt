package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ServiceStatus
import com.example.ui.theme.AlertRed
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.DarkSlateCard
import com.example.ui.theme.DarkSlateSurface
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.ui.theme.TransmitAmber
import kotlinx.coroutines.launch

@Composable
fun LanTalkieScreen(
    uiState: MainUiState,
    onRemoteIpChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onBindWifiChange: (Boolean) -> Unit,
    onRefreshIp: () -> Unit,
    onToggleService: () -> Unit,
    onPttPress: () -> Boolean,
    onPttRelease: () -> Unit,
    onDismissError: () -> Unit,
    hasRecordPermission: Boolean,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showInstructionsDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(DarkNavyBackground)
            .testTag("lan_talkie_screen"),
        containerColor = DarkNavyBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top App Bar / Title Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(CyanPrimary, TransmitAmber)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "App Icon",
                            tint = TextDark,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "LAN TALKIE",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp,
                            color = TextWhite
                        )
                        Text(
                            text = "OFFLINE P2P VOICE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = CyanPrimary
                        )
                    }
                }

                IconButton(
                    onClick = { showInstructionsDialog = true },
                    modifier = Modifier.testTag("instructions_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "How to connect",
                        tint = CyanPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Permission Warning Card if microphone is not granted
            if (!hasRecordPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp)
                        .padding(bottom = 12.dp)
                        .testTag("permission_card"),
                    colors = CardDefaults.cardColors(containerColor = AlertRed.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(AlertRed, AlertRed)))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = AlertRed)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Microphone Permission Required",
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "LAN Talkie needs microphone access to capture and transmit real-time voice packets over your local Wi-Fi or hotspot.",
                            fontSize = 13.sp,
                            color = TextWhite.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onRequestPermissions,
                            colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                            modifier = Modifier.align(Alignment.End).testTag("grant_permission_button")
                        ) {
                            Text("Grant Permission", color = TextWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Status Indicator Card
            StatusCard(
                status = uiState.status,
                amplitude = uiState.amplitude,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
            )

            // Live packet counters: TX = sent by this phone, RX = received from the other phone
            if (uiState.diagnostics.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.diagnostics,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp)
                        .testTag("diagnostics_text")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Network Configuration Card (Local IPv4, Remote IPv4, Port)
            NetworkConfigCard(
                localIp = uiState.localIp,
                remoteIp = uiState.remoteIpInput,
                port = uiState.portInput,
                ipError = uiState.ipError,
                portError = uiState.portError,
                isServiceActive = uiState.status.isActive,
                bindWifi = uiState.bindWifi,
                onBindWifiChange = onBindWifiChange,
                onRemoteIpChange = onRemoteIpChange,
                onPortChange = onPortChange,
                onRefreshIp = onRefreshIp,
                onCopyIp = { ip ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Local IP", ip))
                    Toast.makeText(context, "Copied IP: $ip", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Service Control Button (Start / Stop Service)
            Button(
                onClick = onToggleService,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.status.isActive) AlertRed else SignalGreen
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .height(52.dp)
                    .testTag("toggle_service_button")
            ) {
                Icon(
                    imageVector = if (uiState.status.isActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = if (uiState.status.isActive) TextWhite else TextDark
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.status.isActive) "STOP LISTENING SERVICE" else "START LISTENING SERVICE",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 1.sp,
                    color = if (uiState.status.isActive) TextWhite else TextDark
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Push-to-Talk Big Tactile Button
            PttButton(
                status = uiState.status,
                amplitude = uiState.amplitude,
                onPress = onPttPress,
                onRelease = onPttRelease,
                modifier = Modifier.testTag("hold_to_talk_button")
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = when (uiState.status) {
                    ServiceStatus.IDLE -> "Start the service to begin listening and talking"
                    ServiceStatus.LISTENING -> "PRESS & HOLD TO TRANSMIT VOICE"
                    ServiceStatus.TRANSMITTING -> "TRANSMITTING... (RELEASE TO LISTEN)"
                    ServiceStatus.RECEIVING -> "INCOMING AUDIO FROM PEER..."
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = when (uiState.status) {
                    ServiceStatus.TRANSMITTING -> TransmitAmber
                    ServiceStatus.RECEIVING -> SignalGreen
                    ServiceStatus.LISTENING -> CyanPrimary
                    ServiceStatus.IDLE -> TextMuted
                },
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Error Dialog
    if (uiState.generalError != null) {
        AlertDialog(
            onDismissRequest = onDismissError,
            icon = { Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = AlertRed) },
            title = { Text("Network / Audio Notice", fontWeight = FontWeight.Bold) },
            text = { Text(uiState.generalError) },
            confirmButton = {
                TextButton(onClick = onDismissError) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // How-to-use / Setup instructions modal
    if (showInstructionsDialog) {
        AlertDialog(
            onDismissRequest = { showInstructionsDialog = false },
            icon = { Icon(Icons.Default.Wifi, contentDescription = null, tint = CyanPrimary) },
            title = { Text("How to Connect Two Phones", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "1. Local Network Connection:",
                        fontWeight = FontWeight.Bold,
                        color = CyanPrimary
                    )
                    Text(
                        "Connect both phones to the same Wi-Fi router, OR enable Personal Hotspot on Phone A and connect Phone B to it. No internet or data is needed!\n"
                    )
                    Text(
                        "2. Setup IPs:",
                        fontWeight = FontWeight.Bold,
                        color = CyanPrimary
                    )
                    Text(
                        "Look at Phone A's 'My Local IPv4' and enter it into Phone B's 'Remote Peer IPv4'. Do the reverse on Phone A.\n"
                    )
                    Text(
                        "3. Start Service:",
                        fontWeight = FontWeight.Bold,
                        color = CyanPrimary
                    )
                    Text(
                        "Tap 'START LISTENING SERVICE' on both phones (Port 8888).\n"
                    )
                    Text(
                        "4. Talk:",
                        fontWeight = FontWeight.Bold,
                        color = CyanPrimary
                    )
                    Text(
                        "Press and hold the large Push-to-Talk button to speak. Both devices will stream high-fidelity 16kHz voice with low latency!"
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showInstructionsDialog = false }) {
                    Text("Got It", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun StatusCard(
    status: ServiceStatus,
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    val statusColor by animateColorAsState(
        targetValue = when (status) {
            ServiceStatus.IDLE -> TextMuted
            ServiceStatus.LISTENING -> CyanPrimary
            ServiceStatus.TRANSMITTING -> TransmitAmber
            ServiceStatus.RECEIVING -> SignalGreen
        },
        label = "statusColor"
    )

    Card(
        modifier = modifier.testTag("status_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSlateSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                listOf(statusColor.copy(alpha = 0.5f), SurfaceBorder)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ENGINE STATUS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = TextMuted
                )

                // Glowing status badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = status.label.uppercase(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // VU Meter / Level Meter
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (status == ServiceStatus.TRANSMITTING) Icons.Default.Mic else Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = if (status.isActive) statusColor else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                LinearProgressIndicator(
                    progress = { if (status.isActive) amplitude else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = statusColor,
                    trackColor = DarkSlateCard
                )
            }
        }
    }
}

@Composable
fun NetworkConfigCard(
    localIp: String?,
    remoteIp: String,
    port: String,
    ipError: String?,
    portError: String?,
    isServiceActive: Boolean,
    bindWifi: Boolean,
    onBindWifiChange: (Boolean) -> Unit,
    onRemoteIpChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onRefreshIp: () -> Unit,
    onCopyIp: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.testTag("network_config_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSlateSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(SurfaceBorder, DarkSlateCard))
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "NETWORK CONFIGURATION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Local IPv4 Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSlateCard)
                    .border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "MY LOCAL IPV4",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanPrimary
                        )
                        Text(
                            text = localIp ?: "Detecting / Not connected",
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (localIp != null) TextWhite else AlertRed
                        )
                    }

                    Row {
                        if (localIp != null) {
                            IconButton(
                                onClick = { onCopyIp(localIp) },
                                modifier = Modifier.size(36.dp).testTag("copy_ip_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Local IP",
                                    tint = CyanPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = onRefreshIp,
                            modifier = Modifier.size(36.dp).testTag("refresh_ip_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Local IP",
                                tint = CyanPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Remote Peer IP Input
            OutlinedTextField(
                value = remoteIp,
                onValueChange = onRemoteIpChange,
                label = { Text("Remote Peer IPv4") },
                placeholder = { Text("e.g. 192.168.43.15") },
                isError = ipError != null,
                supportingText = {
                    if (ipError != null) {
                        Text(ipError, color = AlertRed, fontSize = 11.sp)
                    } else {
                        Text("Enter the other phone's Local IPv4", color = TextMuted, fontSize = 11.sp)
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanPrimary,
                    unfocusedBorderColor = SurfaceBorder,
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    focusedContainerColor = DarkSlateCard,
                    unfocusedContainerColor = DarkSlateCard
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("remote_ip_input")
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Port Input (Both devices listen and transmit on same port)
            OutlinedTextField(
                value = port,
                onValueChange = onPortChange,
                enabled = !isServiceActive,
                label = { Text("UDP Port") },
                placeholder = { Text("8888") },
                isError = portError != null,
                supportingText = {
                    if (portError != null) {
                        Text(portError, color = AlertRed, fontSize = 11.sp)
                    } else {
                        Text(
                            if (isServiceActive) "Stop service to change port" else "Default 8888 (1024-65535)",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanPrimary,
                    unfocusedBorderColor = SurfaceBorder,
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    focusedContainerColor = DarkSlateCard,
                    unfocusedContainerColor = DarkSlateCard,
                    disabledContainerColor = DarkSlateCard.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("port_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Optional: force the UDP socket onto the Wi-Fi network (change only while stopped)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Bind to Wi-Fi network",
                    color = TextWhite,
                    fontSize = 13.sp
                )
                Switch(
                    checked = bindWifi,
                    onCheckedChange = onBindWifiChange,
                    enabled = !isServiceActive,
                    modifier = Modifier.testTag("bind_wifi_switch")
                )
            }
        }
    }
}

@Composable
fun PttButton(
    status: ServiceStatus,
    amplitude: Float,
    onPress: () -> Boolean,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val isTransmitting = status == ServiceStatus.TRANSMITTING
    val isReceiving = status == ServiceStatus.RECEIVING

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isTransmitting || isReceiving) 1.08f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isTransmitting) 400 else 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val buttonColor by animateColorAsState(
        targetValue = when {
            isTransmitting -> TransmitAmber
            isReceiving -> SignalGreen
            status == ServiceStatus.LISTENING -> CyanPrimary
            else -> DarkSlateCard
        },
        label = "buttonColor"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(200.dp)
            .scale(if (isTransmitting || isReceiving) pulseScale else 1f)
    ) {
        // Outer glow rings
        if (isTransmitting || isReceiving) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(buttonColor.copy(alpha = 0.2f))
            )
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .clip(CircleShape)
                    .background(buttonColor.copy(alpha = 0.35f))
            )
        }

        // Main Circular Push-to-Talk Button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(160.dp)
                .shadow(
                    elevation = if (isTransmitting) 16.dp else 8.dp,
                    shape = CircleShape,
                    spotColor = buttonColor
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            buttonColor,
                            buttonColor.copy(alpha = 0.85f),
                            DarkNavyBackground
                        )
                    )
                )
                .border(3.dp, buttonColor, CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            val accepted = onPress()
                            if (accepted) {
                                tryAwaitRelease()
                                onRelease()
                            }
                        }
                    )
                }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = when {
                        isReceiving -> Icons.Default.VolumeUp
                        else -> Icons.Default.Mic
                    },
                    contentDescription = "Push To Talk",
                    tint = if (status.isActive) TextDark else TextWhite,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when {
                        isTransmitting -> "TALKING"
                        isReceiving -> "HEARING"
                        status == ServiceStatus.LISTENING -> "HOLD TO TALK"
                        else -> "SERVICE IDLE"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = if (status.isActive) TextDark else TextWhite
                )
            }
        }
    }
}
