package com.example

import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.service.MyVpnService
import com.example.ui.VpnScreen
import com.example.ui.VpnViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val vpnViewModel: VpnViewModel by viewModels()

    // Handle native VPN service preparation prompt callback
    private val vpnPrepareLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            vpnViewModel.startVpn(this)
            MyVpnService.log("VPN permission approved. Initiating handshake...", MyVpnService.LogLevel.SUCCESS)
        } else {
            MyVpnService.log("VPN permission denied by user.", MyVpnService.LogLevel.ERROR)
            Toast.makeText(this, "VPN permission required to secure tunnel.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    VpnScreen(
                        viewModel = vpnViewModel,
                        onRequestConnect = { requestVpnConnection() }
                    )
                }
            }
        }
    }

    private fun requestVpnConnection() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPrepareLauncher.launch(intent)
        } else {
            // Already authorized
            vpnViewModel.startVpn(this)
        }
    }
}
