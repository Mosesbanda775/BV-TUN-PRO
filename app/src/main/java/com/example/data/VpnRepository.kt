package com.example.data

import kotlinx.coroutines.flow.Flow

class VpnRepository(private val vpnProfileDao: VpnProfileDao) {
    val allProfiles: Flow<List<VpnProfile>> = vpnProfileDao.getAllProfiles()
    val selectedProfile: Flow<VpnProfile?> = vpnProfileDao.getSelectedProfile()

    suspend fun getSelectedProfileSync(): VpnProfile? {
        return vpnProfileDao.getSelectedProfileSync()
    }

    suspend fun selectProfile(profileId: Int) {
        vpnProfileDao.selectProfile(profileId)
    }

    suspend fun insertProfile(profile: VpnProfile) {
        vpnProfileDao.insertProfile(profile)
    }

    suspend fun updateProfile(profile: VpnProfile) {
        vpnProfileDao.updateProfile(profile)
    }

    suspend fun deleteProfile(profile: VpnProfile) {
        vpnProfileDao.deleteProfile(profile)
    }
}
