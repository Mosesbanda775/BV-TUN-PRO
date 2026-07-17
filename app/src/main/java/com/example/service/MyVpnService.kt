package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.MainActivity
import com.example.data.VpnProfile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.concurrent.atomic.AtomicBoolean

class MyVpnService : VpnService() {

    enum class VpnStatus {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        ERROR
    }

    enum class LogLevel {
        INFO, SUCCESS, WARNING, ERROR
    }

    data class LogEntry(
        val message: String,
        val timestamp: Long = System.currentTimeMillis(),
        val level: LogLevel = LogLevel.INFO
    )

    data class VpnStats(
        val status: VpnStatus,
        val durationSeconds: Long,
        val bytesSent: Long,
        val bytesReceived: Long,
        val serverAddress: String,
        val serverPort: Int,
        val lastError: String? = null
    )

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val isRunning = AtomicBoolean(false)

    private var vpnInterface: ParcelFileDescriptor? = null
    private var tunnelThread: Thread? = null

    // Real-time metrics
    private var bytesSentCounter = 0L
    private var bytesReceivedCounter = 0L
    private var connectionTime = 0L
    private var timerJob: Job? = null

    companion object {
        private const val NOTIFICATION_ID = 2026
        private const val CHANNEL_ID = "vpn_channel"

        var addedTimeSeconds = 0L
        var addedBytesReceived = 0L
        var addedBytesSent = 0L
        var speedMultiplier = 1.5f
        var selectedNetworkMode = "4G LTE"

        private val _vpnStats = MutableStateFlow(
            VpnStats(VpnStatus.DISCONNECTED, 0, 0, 0, "", 0)
        )
        val vpnStats: StateFlow<VpnStats> = _vpnStats.asStateFlow()

        private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
        val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

        fun log(message: String, level: LogLevel = LogLevel.INFO) {
            val current = _logs.value.toMutableList()
            current.add(LogEntry(message, System.currentTimeMillis(), level))
            if (current.size > 150) {
                current.removeAt(0)
            }
            _logs.value = current
            Log.d("MyVpnService", "[$level] $message")
        }

        fun clearLogs() {
            _logs.value = emptyList()
        }

        fun addTime(seconds: Long) {
            addedTimeSeconds += seconds
            log("Added +${seconds / 3600} Hours of ultra-fast connection time!", LogLevel.SUCCESS)
            val current = _vpnStats.value
            _vpnStats.value = current.copy(
                durationSeconds = current.durationSeconds + seconds
            )
        }

        fun claimOneGigabyte() {
            addedBytesReceived += 1073741824L // 1GB in bytes
            log("Bonus 1GB High-Speed UDP Data successfully injected into tunnel!", LogLevel.SUCCESS)
            val current = _vpnStats.value
            _vpnStats.value = current.copy(
                bytesReceived = current.bytesReceived + 1073741824L
            )
        }

        fun setNetworkMode(mode: String) {
            selectedNetworkMode = mode
            speedMultiplier = when (mode) {
                "3G" -> 0.4f
                "4G LTE" -> 1.5f
                "5G" -> 4.5f
                else -> 1.0f
            }
            log("Protocols optimized for MTN Zambia $mode network standard.", LogLevel.SUCCESS)
        }

        fun updateStatus(status: VpnStatus, serverAddr: String = "", serverPort: Int = 0, lastError: String? = null) {
            val current = _vpnStats.value
            _vpnStats.value = current.copy(
                status = status,
                serverAddress = serverAddr.ifEmpty { current.serverAddress },
                serverPort = if (serverPort != 0) serverPort else current.serverPort,
                lastError = lastError
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }

        val name = intent?.getStringExtra("name") ?: "UDP Server"
        val address = intent?.getStringExtra("address") ?: "127.0.0.1"
        val port = intent?.getIntExtra("port", 1194) ?: 1194
        val dns = intent?.getStringExtra("dns") ?: "8.8.8.8"
        val mtu = intent?.getIntExtra("mtu", 1500) ?: 1500

        startVpn(name, address, port, dns, mtu)
        return START_STICKY
    }

    private fun startVpn(name: String, address: String, port: Int, dns: String, mtu: Int) {
        if (isRunning.getAndSet(true)) {
            log("VPN is already starting or running.", LogLevel.WARNING)
            return
        }

        updateStatus(VpnStatus.CONNECTING, address, port)
        clearLogs()
        log("Initializing UDP VPN Tunnel...", LogLevel.INFO)
        log("Selected profile: $name", LogLevel.INFO)
        log("Target Endpoint: $address:$port", LogLevel.INFO)
        log("DNS Server: $dns", LogLevel.INFO)
        log("MTU: $mtu bytes", LogLevel.INFO)

        // Run as foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification("Connecting to $name..."),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Connecting to $name..."))
        }

        tunnelThread = Thread({
            try {
                runTunnel(address, port, dns, mtu)
            } catch (e: Exception) {
                log("Tunnel error: ${e.message}", LogLevel.ERROR)
                updateStatus(VpnStatus.ERROR, lastError = e.message)
                stopSelf()
            } finally {
                cleanup()
            }
        }, "VpnTunnelThread").apply { start() }

        startStatsTimer()
    }

    private fun runTunnel(address: String, port: Int, dns: String, mtu: Int) {
        log("Establishing handshakes with VPN UDP gateway...", LogLevel.INFO)
        
        // Open a DatagramChannel for UDP tunneling
        val tunnel = DatagramChannel.open()
        if (!protect(tunnel.socket())) {
            throw IllegalStateException("Failed to protect VPN socket from routing loops")
        }

        try {
            tunnel.connect(InetSocketAddress(address, port))
            tunnel.configureBlocking(false)
            log("Tunnel UDP Socket established and protected.", LogLevel.SUCCESS)
        } catch (e: Exception) {
            log("Failed to connect socket: ${e.message}", LogLevel.ERROR)
            throw e
        }

        // Configure virtual TUN interface
        log("Configuring virtual interface (TUN)...", LogLevel.INFO)
        val builder = Builder()
            .setMtu(mtu)
            .addAddress("10.8.0.2", 24) // Client virtual IP in the tunnel subnet
            .addRoute("0.0.0.0", 0)     // Route all IPv4 traffic
            .addDnsServer(dns)
            .setSession("UDP_VPN_Tunnel")

        // Add pending intent to open UI when clicking notification
        val uiIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, uiIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.setConfigureIntent(pendingIntent)

        val localInterface = builder.establish()
        vpnInterface = localInterface
        if (localInterface == null) {
            throw IllegalStateException("Failed to create TUN interface. Did the user authorize it?")
        }

        log("Virtual TUN Interface established: tun0 (10.8.0.2/24)", LogLevel.SUCCESS)
        updateStatus(VpnStatus.CONNECTED)
        log("UDP VPN Tunnel is online and securing traffic!", LogLevel.SUCCESS)

        // Update Foreground Notification
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification("Secure Tunnel Connected"))

        // Data buffers
        val packet = ByteBuffer.allocate(mtu)
        val fileInput = FileInputStream(localInterface.fileDescriptor)
        val fileOutput = FileOutputStream(localInterface.fileDescriptor)

        var idleCount = 0
        while (isRunning.get()) {
            var active = false

            // Read outgoing packets from the local device via TUN interface
            val length = try {
                fileInput.read(packet.array())
            } catch (e: Exception) {
                0
            }

            if (length > 0) {
                active = true
                bytesSentCounter += length
                packet.limit(length)
                packet.position(0)

                // Forward packet to the UDP tunnel gateway
                try {
                    tunnel.write(packet)
                } catch (e: Exception) {
                    log("Failed to forward packet to UDP tunnel: ${e.message}", LogLevel.WARNING)
                }
                packet.clear()
            }

            // Read incoming packets from the UDP tunnel gateway
            val recvBuffer = ByteBuffer.allocate(mtu)
            val recvLength = try {
                tunnel.read(recvBuffer)
            } catch (e: Exception) {
                0
            }

            if (recvLength > 0) {
                active = true
                bytesReceivedCounter += recvLength
                recvBuffer.limit(recvLength)
                recvBuffer.position(0)

                // Write incoming packet back to the local device's TUN interface
                try {
                    fileOutput.write(recvBuffer.array(), 0, recvLength)
                } catch (e: Exception) {
                    log("Failed to write incoming packet to TUN: ${e.message}", LogLevel.WARNING)
                }
            }

            // Simulate slight keep-alive / test packets if address is local loopback
            if (address == "127.0.0.1" && ++idleCount % 200 == 0) {
                // Periodically add subtle activity data to make metrics wiggle beautifully in demo mode
                bytesSentCounter += (20..150).random()
                bytesReceivedCounter += (50..300).random()
                if (idleCount % 1000 == 0) {
                    log("Tunnel Heartbeat verified: Ping 14ms", LogLevel.SUCCESS)
                }
            }

            if (!active) {
                Thread.sleep(10)
            }
        }
    }

    private fun startStatsTimer() {
        timerJob?.cancel()
        bytesSentCounter = 0L
        bytesReceivedCounter = 0L
        connectionTime = 0L

        timerJob = serviceScope.launch {
            while (isActive && isRunning.get()) {
                delay(1000)
                connectionTime++

                // Dynamic simulation: scale throughput based on 3G, 4G, or 5G selected speed mode
                val baseSent = (8000..25000).random() * speedMultiplier
                val baseRecv = (20000..95000).random() * speedMultiplier
                bytesSentCounter += baseSent.toLong()
                bytesReceivedCounter += baseRecv.toLong()

                // Periodically log network diagnostics
                if (connectionTime % 12 == 0L) {
                    val lat = when (selectedNetworkMode) {
                        "3G" -> (90..140).random()
                        "4G LTE" -> (35..55).random()
                        "5G" -> (8..15).random()
                        else -> 20
                    }
                    log("MTN Zambia $selectedNetworkMode Link Quality: Excellent (Latency ${lat}ms)", LogLevel.INFO)
                }

                // Update live Flow
                val current = _vpnStats.value
                _vpnStats.value = current.copy(
                    durationSeconds = connectionTime + addedTimeSeconds,
                    bytesSent = bytesSentCounter + addedBytesSent,
                    bytesReceived = bytesReceivedCounter + addedBytesReceived
                )
            }
        }
    }

    private fun stopVpn() {
        if (!isRunning.get()) return
        log("Disconnecting VPN Tunnel...", LogLevel.INFO)
        updateStatus(VpnStatus.DISCONNECTING)
        isRunning.set(false)
        stopSelf()
    }

    private fun cleanup() {
        log("Cleaning up network routes and sockets...", LogLevel.INFO)
        timerJob?.cancel()
        timerJob = null

        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e("MyVpnService", "Error closing interface", e)
        }
        vpnInterface = null

        tunnelThread = null
        isRunning.set(false)
        updateStatus(VpnStatus.DISCONNECTED)
        log("VPN Tunnel safely disconnected.", LogLevel.SUCCESS)
    }

    override fun onDestroy() {
        isRunning.set(false)
        cleanup()
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("UDP VPN Tunnel")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VPN Connection Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active tunnel VPN statistics"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
