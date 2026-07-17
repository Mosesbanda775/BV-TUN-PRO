package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vpn_profiles")
data class VpnProfile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val serverAddress: String,
    val serverPort: Int = 1194,
    val dnsServer: String = "8.8.8.8",
    val mtu: Int = 1500,
    val protocol: String = "UDP",
    val bypassApps: String = "",
    val username: String = "",
    val password: String = "",
    val isSelected: Boolean = false
)
