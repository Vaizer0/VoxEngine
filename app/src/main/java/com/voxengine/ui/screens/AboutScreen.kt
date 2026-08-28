package com.voxengine.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.voxengine.ui.navigation.Screen
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(navController: NavController = rememberNavController()) {
    val context = LocalContext.current

    fun openUrl(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(context, "Unable to open link", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        TopAppBar(title = { Text("About") })

        // Project info
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("VoxEngine", style = MaterialTheme.typography.headlineMedium)
                Text("Version ${com.voxengine.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Android system-level TTS engine supporting multi-engine switching, voice cloning and design. " +
                    "Released as an open-source project, free of charge.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { openUrl("https://github.com/Autsunset/VoxEngine") }) {
                    Text("GitHub Project")
                }
                TextButton(onClick = { navController.navigate(Screen.Log.route) }) {
                    Text("View Logs")
                }
            }
        }

        // Terms of service & privacy
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Terms of Service & Privacy", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "This software uses the Xiaomi MiMo TTS API for speech synthesis. Please comply with the following terms:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { openUrl("https://platform.xiaomimimo.com/docs/terms/user-agreement") }) {
                    Text("MiMo User Agreement")
                }
                TextButton(onClick = { openUrl("https://privacy.mi.com/XiaomiMiMoPlatform/zh_CN/") }) {
                    Text("MiMo Privacy Policy")
                }
            }
        }

        // Disclaimer
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Disclaimer", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "1. This software is an open-source project. The developer charges no fees and provides no commercial services." +
                    "\n\n2. This software is for learning and personal use only. Any illegal use is strictly prohibited, including but not limited to:\n" +
                    "   - Generating false information, fraudulent content, or misleading speech\n" +
                    "   - Infringing on others' legitimate rights such as portrait rights or voice rights\n" +
                    "   - Large-scale automated calls or using it for commercial profit\n" +
                    "   - Any other behavior that violates laws and regulations" +
                    "\n\n3. This software calls the Xiaomi MiMo API. Users must comply with the Xiaomi MiMo platform's user agreement and terms of use. " +
                    "Any consequences of violating Xiaomi platform rules (including, but not limited to, account suspension) are borne by the user." +
                    "\n\n4. Token Plan may be limited to programming/development scenarios. Connecting it to third-party apps (such as readers) for speech synthesis " +
                    "may violate Xiaomi's terms of service and lead to account suspension. Usage-based billing (currently free for a limited time) is recommended." +
                    "\n\n5. The developer is not liable for any direct or indirect losses arising from the use of this software." +
                    "\n\n6. By using this software, you acknowledge that you have read and agreed to the above terms.",
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                )
            }
        }
    }
}
