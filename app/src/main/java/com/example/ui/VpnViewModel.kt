package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.VpnDatabase
import com.example.data.VpnProfile
import com.example.data.VpnRepository
import com.example.service.MyVpnService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VpnViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: VpnRepository
    val allProfiles: StateFlow<List<VpnProfile>>
    val selectedProfile: StateFlow<VpnProfile?>

    // Connect directly to MyVpnService companion state flows
    val vpnStats: StateFlow<MyVpnService.VpnStats> = MyVpnService.vpnStats
    val vpnLogs: StateFlow<List<MyVpnService.LogEntry>> = MyVpnService.logs

    init {
        val database = VpnDatabase.getDatabase(application, viewModelScope)
        repository = VpnRepository(database.vpnProfileDao())
        
        allProfiles = repository.allProfiles
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        selectedProfile = repository.selectedProfile
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )
    }

    fun selectProfile(profileId: Int) {
        viewModelScope.launch {
            repository.selectProfile(profileId)
            
            // If currently connected and we changed profile, log it
            if (vpnStats.value.status == MyVpnService.VpnStatus.CONNECTED) {
                MyVpnService.log("Selected profile changed. Reconnect to apply changes.", MyVpnService.LogLevel.WARNING)
            }
        }
    }

    fun saveProfile(
        id: Int = 0,
        name: String,
        address: String,
        port: Int,
        dns: String,
        mtu: Int,
        username: String = "",
        password: String = ""
    ) {
        viewModelScope.launch {
            val isSelected = id == 0 || (selectedProfile.value?.id == id)
            val profile = VpnProfile(
                id = if (id == 0) 0 else id,
                name = name,
                serverAddress = address,
                serverPort = port,
                dnsServer = dns,
                mtu = mtu,
                username = username,
                password = password,
                isSelected = isSelected
            )
            repository.insertProfile(profile)
            if (id == 0) {
                MyVpnService.log("Saved new profile: $name", MyVpnService.LogLevel.INFO)
            } else {
                MyVpnService.log("Updated profile: $name", MyVpnService.LogLevel.INFO)
            }
        }
    }

    fun deleteProfile(profile: VpnProfile) {
        viewModelScope.launch {
            repository.deleteProfile(profile)
            MyVpnService.log("Deleted profile: ${profile.name}", MyVpnService.LogLevel.WARNING)
        }
    }

    fun startVpn(context: Context) {
        val profile = selectedProfile.value ?: return
        val intent = Intent(context, MyVpnService::class.java).apply {
            putExtra("name", profile.name)
            putExtra("address", profile.serverAddress)
            putExtra("port", profile.serverPort)
            putExtra("dns", profile.dnsServer)
            putExtra("mtu", profile.mtu)
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopVpn(context: Context) {
        val intent = Intent(context, MyVpnService::class.java).apply {
            action = "STOP"
        }
        context.startService(intent)
    }

    fun addTime(seconds: Long) {
        MyVpnService.addTime(seconds)
    }

    fun claimOneGigabyte() {
        MyVpnService.claimOneGigabyte()
    }

    fun setNetworkMode(mode: String) {
        MyVpnService.setNetworkMode(mode)
    }

    fun getSelectedNetworkMode(): String {
        return MyVpnService.selectedNetworkMode
    }

    fun clearLogs() {
        MyVpnService.clearLogs()
    }
}
