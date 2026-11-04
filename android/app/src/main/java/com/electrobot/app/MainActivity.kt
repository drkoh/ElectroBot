package com.electrobot.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.electrobot.app.theme.ElectroBotTheme
import com.electrobot.app.theme.ElectroTheme
import com.electrobot.app.ui.MainScreen

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Doze mode exemption request (borrowed from WaSender)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "Ignore battery optimizations request failed: ${e.message}")
                }
            }
        }

        val prefs = getSharedPreferences("electrobot_prefs", Context.MODE_PRIVATE)

        setContent {
            var isDarkTheme by remember {
                mutableStateOf(prefs.getBoolean("is_dark_theme", true))
            }

            ElectroBotTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ElectroTheme.colors.canvas
                ) {
                    MainScreen(
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = {
                            isDarkTheme = !isDarkTheme
                            prefs.edit().putBoolean("is_dark_theme", isDarkTheme).apply()
                        }
                    )
                }
            }
        }
    }
}
