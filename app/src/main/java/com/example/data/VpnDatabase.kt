package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [VpnProfile::class], version = 1, exportSchema = false)
abstract class VpnDatabase : RoomDatabase() {
    abstract fun vpnProfileDao(): VpnProfileDao

    companion object {
        @Volatile
        private var INSTANCE: VpnDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): VpnDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VpnDatabase::class.java,
                    "vpn_database"
                )
                .addCallback(VpnDatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class VpnDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    val dao = database.vpnProfileDao()
                    // Populate default profiles
                    dao.insertProfile(
                        VpnProfile(
                            name = "Zambia - Lusaka Gateway 🇿🇲",
                            serverAddress = "41.223.120.1",
                            serverPort = 1194,
                            dnsServer = "8.8.8.8",
                            mtu = 1500,
                            protocol = "UDP",
                            isSelected = true
                        )
                    )
                    dao.insertProfile(
                        VpnProfile(
                            name = "Australia - Sydney Hub 🇦🇺",
                            serverAddress = "101.0.0.1",
                            serverPort = 1194,
                            dnsServer = "1.1.1.1",
                            mtu = 1450,
                            protocol = "UDP",
                            isSelected = false
                        )
                    )
                    dao.insertProfile(
                        VpnProfile(
                            name = "United Kingdom - London Node 🇬🇧",
                            serverAddress = "109.169.30.1",
                            serverPort = 1194,
                            dnsServer = "8.8.8.8",
                            mtu = 1500,
                            protocol = "UDP",
                            isSelected = false
                        )
                    )
                    dao.insertProfile(
                        VpnProfile(
                            name = "South Africa - Johannesburg Hub 🇿🇦",
                            serverAddress = "196.25.1.1",
                            serverPort = 1194,
                            dnsServer = "8.8.8.8",
                            mtu = 1500,
                            protocol = "UDP",
                            isSelected = false
                        )
                    )
                    dao.insertProfile(
                        VpnProfile(
                            name = "United States - New York Relay 🇺🇸",
                            serverAddress = "198.101.242.72",
                            serverPort = 1194,
                            dnsServer = "8.8.8.8",
                            mtu = 1500,
                            protocol = "UDP",
                            isSelected = false
                        )
                    )
                    dao.insertProfile(
                        VpnProfile(
                            name = "Uganda - Kampala Tunnel 🇺🇬",
                            serverAddress = "197.221.1.1",
                            serverPort = 1194,
                            dnsServer = "8.8.8.8",
                            mtu = 1500,
                            protocol = "UDP",
                            isSelected = false
                        )
                    )
                }
            }
        }
    }
}
