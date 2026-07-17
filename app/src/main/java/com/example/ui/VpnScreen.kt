package com.example.ui

import android.net.VpnService
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.VpnProfile
import com.example.service.MyVpnService
import com.example.service.MyVpnService.VpnStatus
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpnScreen(
    viewModel: VpnViewModel,
    onRequestConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stats by viewModel.vpnStats.collectAsState()
    val logs by viewModel.vpnLogs.collectAsState()
    val profiles by viewModel.allProfiles.collectAsState()
    val selectedProfile by viewModel.selectedProfile.collectAsState()

    var showAddProfileSheet by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<VpnProfile?>(null) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "VPN Lock Icon",
                            tint = if (stats.status == VpnStatus.CONNECTED) NeonGreen else CyberCyan,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "UDP VPN Tunnel",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            letterSpacing = 1.sp
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.clearLogs()
                            MyVpnService.log("Terminal logs cleared.", MyVpnService.LogLevel.INFO)
                        },
                        modifier = Modifier.testTag("clear_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear logs",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepBg,
                    titleContentColor = TextPrimary
                )
            )
        },
        containerColor = DeepBg,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Halo and Toggle Switch
            item {
                VpnConnectionCenter(
                    status = stats.status,
                    durationSeconds = stats.durationSeconds,
                    selectedProfile = selectedProfile,
                    onToggleClick = {
                        if (stats.status == VpnStatus.CONNECTED || stats.status == VpnStatus.CONNECTING) {
                            viewModel.stopVpn(context)
                        } else {
                            onRequestConnect()
                        }
                    }
                )
            }

            // Real-time Bandwidth Speed Counters
            item {
                VpnMetricsDashboard(
                    status = stats.status,
                    bytesSent = stats.bytesSent,
                    bytesReceived = stats.bytesReceived
                )
            }

            // Zambia MTN Network Booster & Rewards
            item {
                ZambiaMtnOptimizerSection(
                    viewModel = viewModel
                )
            }

            // Selected Server Settings Card
            item {
                SelectedServerCard(
                    selectedProfile = selectedProfile,
                    onEditClick = {
                        editingProfile = selectedProfile
                        showAddProfileSheet = true
                    }
                )
            }

            // Server Profile Storage and Configurations
            item {
                ProfileManagerSection(
                    profiles = profiles,
                    selectedProfile = selectedProfile,
                    onProfileSelect = { viewModel.selectProfile(it.id) },
                    onDeleteProfile = { viewModel.deleteProfile(it) },
                    onAddNewClick = {
                        editingProfile = null
                        showAddProfileSheet = true
                    }
                )
            }

            // Handshake Terminal Logs Terminal Window
            item {
                LogTerminalWindow(
                    logs = logs
                )
            }

            // Extra padding at the bottom
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showAddProfileSheet) {
        AddProfileDialog(
            profile = editingProfile,
            onDismiss = { showAddProfileSheet = false },
            onSave = { name, address, port, dns, mtu, user, pass ->
                viewModel.saveProfile(
                    id = editingProfile?.id ?: 0,
                    name = name,
                    address = address,
                    port = port,
                    dns = dns,
                    mtu = mtu,
                    username = user,
                    password = pass
                )
                showAddProfileSheet = false
            }
        )
    }
}

@Composable
fun VpnConnectionCenter(
    status: VpnStatus,
    durationSeconds: Long,
    selectedProfile: VpnProfile?,
    onToggleClick: () -> Unit
) {
    val isConnecting = status == VpnStatus.CONNECTING
    val isConnected = status == VpnStatus.CONNECTED

    // Core Pulse Animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isConnected) 1.15f else if (isConnecting) 1.08f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )

    val activeColor = when (status) {
        VpnStatus.CONNECTED -> NeonGreen
        VpnStatus.CONNECTING -> WarmAmber
        VpnStatus.DISCONNECTING -> TextSecondary
        VpnStatus.ERROR -> Color.Red
        VpnStatus.DISCONNECTED -> CyberCyan
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(200.dp)
                .testTag("vpn_toggle_button")
        ) {
            // Pulse Rings
            Canvas(
                modifier = Modifier
                    .size(190.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                        alpha = if (isConnected || isConnecting) 0.15f else 0.05f
                    }
            ) {
                drawCircle(color = activeColor)
            }

            // Spinning Loader Ring when connecting
            if (isConnecting) {
                Canvas(
                    modifier = Modifier
                        .size(175.dp)
                        .graphicsLayer { rotationZ = spinAngle }
                ) {
                    drawArc(
                        color = activeColor,
                        startAngle = 0f,
                        sweepAngle = 120f,
                        useCenter = false,
                        style = Stroke(width = 6.dp.toPx())
                    )
                }
            } else {
                // Static decorative subtle status outer border
                Canvas(modifier = Modifier.size(175.dp)) {
                    drawCircle(
                        color = activeColor.copy(alpha = 0.4f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }

            // Central Touch Target Toggle Button
            Surface(
                onClick = onToggleClick,
                shape = CircleShape,
                color = DarkSurface,
                border = BorderStroke(3.dp, activeColor),
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape),
                tonalElevation = 6.dp
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.PowerSettingsNew else Icons.Default.Security,
                        contentDescription = "VPN Status Action",
                        tint = activeColor,
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isConnected) "STOP" else if (isConnecting) "STARTING" else "TAP TO VPN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = activeColor,
                        letterSpacing = 1.2.sp
                    )
                }
            }
        }

        // Live stats detail label
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = status.name.uppercase(Locale.ROOT),
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = activeColor,
                letterSpacing = 2.sp
            )
            
            if (isConnected) {
                Text(
                    text = formatDuration(durationSeconds),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            } else {
                Text(
                    text = selectedProfile?.name ?: "No Server Selected",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun VpnMetricsDashboard(
    status: VpnStatus,
    bytesSent: Long,
    bytesReceived: Long
) {
    // We compute dynamic speed based on incremental delta inside Composable
    var lastSent by remember { mutableLongStateOf(0L) }
    var lastReceived by remember { mutableLongStateOf(0L) }
    var uploadSpeed by remember { mutableStateOf("0 B/s") }
    var downloadSpeed by remember { mutableStateOf("0 B/s") }

    LaunchedEffect(bytesSent, bytesReceived) {
        if (status == VpnStatus.CONNECTED) {
            val sentDiff = if (lastSent > 0) bytesSent - lastSent else 0L
            val recvDiff = if (lastReceived > 0) bytesReceived - lastReceived else 0L

            uploadSpeed = formatSpeed(sentDiff)
            downloadSpeed = formatSpeed(recvDiff)

            lastSent = bytesSent
            lastReceived = bytesReceived
        } else {
            uploadSpeed = "0 B/s"
            downloadSpeed = "0 B/s"
            lastSent = 0L
            lastReceived = 0L
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Download Card
        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, GridBorder)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Download Arrow",
                        tint = NeonGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "DOWNLOAD",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = downloadSpeed,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )
                Text(
                    text = "Total: ${formatBytes(bytesReceived)}",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }

        // Upload Card
        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, GridBorder)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Upload Arrow",
                        tint = CyberCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "UPLOAD",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = uploadSpeed,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )
                Text(
                    text = "Total: ${formatBytes(bytesSent)}",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun SelectedServerCard(
    selectedProfile: VpnProfile?,
    onEditClick: () -> Unit
) {
    if (selectedProfile == null) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, GridBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(LightSurface, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = "Active Server Icon",
                        tint = CyberCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = selectedProfile.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${selectedProfile.serverAddress}:${selectedProfile.serverPort} (${selectedProfile.protocol})",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            IconButton(
                onClick = onEditClick,
                modifier = Modifier.testTag("edit_selected_profile_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Configure Profile",
                    tint = CyberCyan
                )
            }
        }
    }
}

@Composable
fun ProfileManagerSection(
    profiles: List<VpnProfile>,
    selectedProfile: VpnProfile?,
    onProfileSelect: (VpnProfile) -> Unit,
    onDeleteProfile: (VpnProfile) -> Unit,
    onAddNewClick: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SERVER PROFILES (${profiles.size})",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onAddNewClick,
                    modifier = Modifier
                        .size(32.dp)
                        .background(LightSurface, CircleShape)
                        .testTag("add_profile_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Profile",
                        tint = CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
                TextButton(onClick = { isExpanded = !isExpanded }) {
                    Text(
                        text = if (isExpanded) "COLLAPSE" else "MANAGE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, GridBorder, RoundedCornerShape(12.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (profiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No saved profiles. Click '+' to add one.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    profiles.forEach { profile ->
                        val isCurrent = profile.id == selectedProfile?.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isCurrent) LightSurface else Color.Transparent)
                                .clickable { onProfileSelect(profile) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                RadioButton(
                                    selected = isCurrent,
                                    onClick = { onProfileSelect(profile) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = CyberCyan,
                                        unselectedColor = TextSecondary
                                    )
                                )
                                Column {
                                    Text(
                                        text = profile.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "${profile.serverAddress}:${profile.serverPort}",
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                            
                            // Prevent deleting loopback / default profiles to keep data valid
                            if (profile.id > 2) {
                                IconButton(
                                    onClick = { onDeleteProfile(profile) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Profile",
                                        tint = Color.Red.copy(alpha = 0.8f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogTerminalWindow(
    logs: List<MyVpnService.LogEntry>
) {
    val listState = rememberLazyListState()

    // Auto scroll to bottom when new logs arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = "Terminal Icon",
                    tint = CyberCyan,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "CONNECTION LOGS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )
            }
            Text(
                text = "${logs.size} EVENTS",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = TextSecondary
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(LogBackground, RoundedCornerShape(10.dp))
                .border(1.dp, GridBorder, RoundedCornerShape(10.dp))
                .padding(10.dp)
        ) {
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Tunnel inactive. Connection logs will appear here.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(logs) { entry ->
                        val color = when (entry.level) {
                            MyVpnService.LogLevel.SUCCESS -> NeonGreen
                            MyVpnService.LogLevel.WARNING -> WarmAmber
                            MyVpnService.LogLevel.ERROR -> Color.Red
                            MyVpnService.LogLevel.INFO -> TextPrimary
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = formatTime(entry.timestamp),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                            Text(
                                text = entry.message,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = color,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProfileDialog(
    profile: VpnProfile?,
    onDismiss: () -> Unit,
    onSave: (name: String, address: String, port: Int, dns: String, mtu: Int, user: String, pass: String) -> Unit
) {
    var name by remember { mutableStateOf(profile?.name ?: "") }
    var address by remember { mutableStateOf(profile?.serverAddress ?: "") }
    var port by remember { mutableStateOf(profile?.serverPort?.toString() ?: "1194") }
    var dns by remember { mutableStateOf(profile?.dnsServer ?: "8.8.8.8") }
    var mtu by remember { mutableStateOf(profile?.mtu?.toString() ?: "1500") }
    var username by remember { mutableStateOf(profile?.username ?: "") }
    var password by remember { mutableStateOf(profile?.password ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (profile == null) "Add Server Profile" else "Edit Server Profile",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        containerColor = DarkSurface,
        textContentColor = TextPrimary,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        focusedLabelColor = CyberCyan
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("profile_name_input")
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Server Address (IP/Host)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        focusedLabelColor = CyberCyan
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("server_address_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            focusedLabelColor = CyberCyan
                        ),
                        modifier = Modifier.weight(1f).testTag("server_port_input")
                    )

                    OutlinedTextField(
                        value = mtu,
                        onValueChange = { mtu = it },
                        label = { Text("MTU") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            focusedLabelColor = CyberCyan
                        ),
                        modifier = Modifier.weight(1f).testTag("server_mtu_input")
                    )
                }

                OutlinedTextField(
                    value = dns,
                    onValueChange = { dns = it },
                    label = { Text("DNS Server") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        focusedLabelColor = CyberCyan
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("server_dns_input")
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username (Optional)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        focusedLabelColor = CyberCyan
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("server_username_input")
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password (Optional)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        focusedLabelColor = CyberCyan
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("server_password_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && address.isNotBlank()) {
                        onSave(
                            name,
                            address,
                            port.toIntOrNull() ?: 1194,
                            dns,
                            mtu.toIntOrNull() ?: 1500,
                            username,
                            password
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                modifier = Modifier.testTag("save_profile_button")
            ) {
                Text("Save", color = DeepBg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

// Helpers
private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1].toString()
    return String.format(Locale.getDefault(), "%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}

private fun formatSpeed(bytesPerSecond: Long): String {
    return "${formatBytes(bytesPerSecond)}/s"
}

private fun formatTime(timestamp: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    return String.format(
        Locale.getDefault(),
        "%02d:%02d:%02d",
        cal.get(java.util.Calendar.HOUR_OF_DAY),
        cal.get(java.util.Calendar.MINUTE),
        cal.get(java.util.Calendar.SECOND)
    )
}

@Composable
fun ZambiaMtnOptimizerSection(
    viewModel: VpnViewModel,
    modifier: Modifier = Modifier
) {
    var activeMode by remember { mutableStateOf(viewModel.getSelectedNetworkMode()) }

    val speedMultiplier = when (activeMode) {
        "3G" -> "0.4x"
        "4G LTE" -> "1.5x"
        "5G" -> "4.5x"
        else -> "1.0x"
    }

    val currentMtu = when (activeMode) {
        "3G" -> 1400
        "4G LTE" -> 1450
        "5G" -> 1500
        else -> 1500
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.5.dp, WarmAmber.copy(alpha = 0.6f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("zambia_mtn_optimizer_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(WarmAmber.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "MTN Network Optimization",
                        tint = WarmAmber,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "MTN Zambia Booster",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "🇿🇲",
                            fontSize = 18.sp
                        )
                    }
                    Text(
                        text = "Auto-tuned UDP Tunneling & Fast Protocol Packets",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            HorizontalDivider(color = GridBorder, thickness = 1.dp)

            // Selector Row
            Text(
                text = "SELECT ACTIVE MTN NETWORK BAND:",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = WarmAmber,
                letterSpacing = 1.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("3G", "4G LTE", "5G").forEach { mode ->
                    val isSelected = activeMode == mode
                    Button(
                        onClick = {
                            activeMode = mode
                            viewModel.setNetworkMode(mode)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) WarmAmber else LightSurface,
                            contentColor = if (isSelected) Color.Black else TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("mtn_network_mode_$mode"),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = mode,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Info Subtext
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DeepBg.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = "Bolt info",
                    tint = CyberCyan,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Speed Multiplier: $speedMultiplier  |  Target MTU: ${currentMtu}b (UDP)",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace
                )
            }

            HorizontalDivider(color = GridBorder, thickness = 1.dp)

            // Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Add Time Button
                OutlinedButton(
                    onClick = {
                        viewModel.addTime(7200) // Adds 2 Hours
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, CyberCyan.copy(alpha = 0.8f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = CyberCyan
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("add_time_button_2h")
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Add Time Icon",
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Add +2 Hours",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                // Claim 1GB Data Button
                Button(
                    onClick = {
                        viewModel.claimOneGigabyte()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("claim_1gb_data_button")
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Claim 1GB Icon",
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Claim 1GB Data",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
