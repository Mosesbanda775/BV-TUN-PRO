package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VpnProfileDao {
    @Query("SELECT * FROM vpn_profiles ORDER BY name ASC")
    fun getAllProfiles(): Flow<List<VpnProfile>>

    @Query("SELECT * FROM vpn_profiles WHERE isSelected = 1 LIMIT 1")
    fun getSelectedProfile(): Flow<VpnProfile?>

    @Query("SELECT * FROM vpn_profiles WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedProfileSync(): VpnProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: VpnProfile)

    @Update
    suspend fun updateProfile(profile: VpnProfile)

    @Delete
    suspend fun deleteProfile(profile: VpnProfile)

    @Query("UPDATE vpn_profiles SET isSelected = 0")
    suspend fun clearSelection()

    @Transaction
    suspend fun selectProfile(profileId: Int) {
        clearSelection()
        setSelected(profileId)
    }

    @Query("UPDATE vpn_profiles SET isSelected = 1 WHERE id = :profileId")
    suspend fun setSelected(profileId: Int)
}
