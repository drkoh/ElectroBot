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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.electrobot.app.theme.ElectroBotTheme
import com.electrobot.app.theme.ElectroTheme
import com.electrobot.app.ui.MainScreen

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onResume() {
        super.onResume()
        com.electrobot.app.service.ElectroWidgetProvider.updateAllWidgets(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Doze mode exemption request for reliable background monitoring
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
            val initialModeStr = prefs.getString("app_theme_mode", "SYSTEM") ?: "SYSTEM"
            var currentThemeMode by remember {
                mutableStateOf(
                    try {
                        AppThemeMode.valueOf(initialModeStr)
                    } catch (_: Exception) {
                        AppThemeMode.SYSTEM
                    }
                )
            }

            val systemInDark = isSystemInDarkTheme()
            val effectiveDark = when (currentThemeMode) {
                AppThemeMode.SYSTEM -> systemInDark
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            ElectroBotTheme(darkTheme = effectiveDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ElectroTheme.colors.canvas
                ) {
                    MainScreen(
                        currentThemeMode = currentThemeMode,
                        isDarkTheme = effectiveDark,
                        onSelectThemeMode = { newMode ->
                            currentThemeMode = newMode
                            prefs.edit().putString("app_theme_mode", newMode.name).apply()
                        }
                    )
                }
            }
        }
    }
}
