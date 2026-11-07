package com.electrobot.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.electrobot.app.AppThemeMode
import com.electrobot.app.data.KharkivData
import com.electrobot.app.data.NetworkManager
import com.electrobot.app.data.StatusResolver
import com.electrobot.app.model.*
import com.electrobot.app.service.ElectroWidgetProvider
import com.electrobot.app.service.GitHubUpdater
import com.electrobot.app.service.OutageNotificationScheduler
import com.electrobot.app.service.UpdateInfo
import com.electrobot.app.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

// =========================================================================
// ElectroBot Mobile (Google Material Design 3 Edition)
// Native Android M3 architecture, full theme support (Light, Dark, System)
// =========================================================================

@Composable
fun MainScreen(
    currentThemeMode: AppThemeMode,
    isDarkTheme: Boolean,
    onSelectThemeMode: (AppThemeMode) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val theme = ElectroTheme.colors
    val prefs = remember { context.getSharedPreferences("electrobot_prefs", Context.MODE_PRIVATE) }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Графік, 1: Адреси, 2: Пошук, 3: 1562, 4: Опції

    val initialAddr = prefs.getString("active_address", "вул. Дудинської, 9") ?: "вул. Дудинської, 9"
    val autoQ = KharkivData.detectQueueForAddress(initialAddr)
    val savedQ = prefs.getString("active_queue", null)
    val effectiveQ = if (savedQ == null || (savedQ == "2.1" && initialAddr.contains("Дудинськ"))) autoQ else savedQ

    var activeQueue by remember { mutableStateOf(effectiveQ) }
    var activeAddress by remember { mutableStateOf(initialAddr) }

    LaunchedEffect(Unit) {
        if (savedQ != effectiveQ) {
            prefs.edit().putString("active_queue", effectiveQ).apply()
        }
    }

    var schedule by remember { mutableStateOf<DailySchedule?>(NetworkManager.loadScheduleCache(context) ?: NetworkManager.getFallbackSchedule()) }
    var outages1562 by remember { mutableStateOf<List<EmergencyOutage1562>>(emptyList()) }
    var lastUpdated1562 by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isRouterOnline by remember { mutableStateOf<Boolean?>(null) }
    var activeRouterIp by remember { mutableStateOf(prefs.getString("router_ip", "") ?: "") }
    var isWifiActive by remember { mutableStateOf(true) }

    var showAddressDialog by remember { mutableStateOf(false) }
    var targetFavoriteForEdit by remember { mutableStateOf<FavoriteAddress?>(null) }

    fun loadFavorites(): List<FavoriteAddress> {
        val jsonStr = prefs.getString("favorites_json", null)
        if (jsonStr == null) {
            return listOf(
                FavoriteAddress("fav_1", "Мій дім", "вул. Дудинської, 9", "1.1"),
                FavoriteAddress("fav_2", "Офіс", "вул. Полтавський Шлях, 10", "1.2")
            )
        }
        val list = mutableListOf<FavoriteAddress>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val addr = obj.optString("address", "")
                var q = obj.optString("queue", "1.1")
                if (q == "2.1" && addr.contains("Дудинськ")) {
                    q = "1.1"
                }
                list.add(
                    FavoriteAddress(
                        id = obj.optString("id", "fav_$i"),
                        title = obj.optString("title", "Адреса"),
                        address = addr,
                        queue = q
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveFavorites(favs: List<FavoriteAddress>) {
        val arr = JSONArray()
        for (f in favs) {
            val obj = JSONObject()
            obj.put("id", f.id)
            obj.put("title", f.title)
            obj.put("address", f.address)
            obj.put("queue", f.queue)
            arr.put(obj)
        }
        prefs.edit().putString("favorites_json", arr.toString()).apply()
    }

    var favoritesList by remember { mutableStateOf(loadFavorites()) }

    var currentSecondsTicker by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentSecondsTicker = System.currentTimeMillis() / 1000
        }
    }

    val unifiedStatus = remember(activeAddress, activeQueue, schedule, outages1562, currentSecondsTicker) {
        StatusResolver.resolve(activeAddress, activeQueue, schedule, outages1562, currentSecondsTicker)
    }

    fun refreshData() {
        coroutineScope.launch {
            isLoading = true
            isWifiActive = NetworkManager.isWifiConnected(context)
            val tgRes = NetworkManager.fetchTelegramSchedule()
            if (tgRes.isSuccess) {
                val s = tgRes.getOrNull()
                if (s != null) {
                    schedule = s
                    NetworkManager.saveScheduleCache(context, s)
                }
            }
            val s1562Res = NetworkManager.fetch1562Announcements()
            if (s1562Res.isSuccess) {
                outages1562 = s1562Res.getOrNull() ?: emptyList()
                lastUpdated1562 = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            }
            OutageNotificationScheduler.scheduleAlarmsForToday(context, activeQueue, schedule)
            ElectroWidgetProvider.updateAllWidgets(context)

            if (isWifiActive) {
                val savedRouterIp = prefs.getString("router_ip", "") ?: ""
                val (online, resIp) = NetworkManager.pingGateway(context, savedRouterIp, 800)
                isRouterOnline = online
                activeRouterIp = resIp
            } else {
                isRouterOnline = null
            }
            isLoading = false
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            refreshData()
            delay(120_000)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Material Design 3 Navigation Bar (Full WindowInsets and gesture/3-button bar protection)
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 2.dp,
                windowInsets = NavigationBarDefaults.windowInsets
            ) {
                val navItems = listOf(
                    Triple("⚡", "Розклад", 0),
                    Triple("📍", "Адреси", 1),
                    Triple("🔍", "Пошук", 2),
                    Triple("🚨", "1562", 3),
                    Triple("⚙️", "Опції", 4)
                )

                navItems.forEach { (icon, label, idx) ->
                    val isSelected = selectedTab == idx
                    val itemActiveColor = if (idx == 3 && isSelected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = idx },
                        icon = {
                            Text(
                                text = icon,
                                fontSize = if (isSelected) 22.sp else 20.sp
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = itemActiveColor,
                            selectedTextColor = itemActiveColor,
                            indicatorColor = if (idx == 3) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> ScheduleTab(
                    theme = theme,
                    currentThemeMode = currentThemeMode,
                    isDarkTheme = isDarkTheme,
                    onSelectThemeMode = onSelectThemeMode,
                    activeQueue = activeQueue,
                    activeAddress = activeAddress,
                    unifiedStatus = unifiedStatus,
                    schedule = schedule,
                    isRouterOnline = isRouterOnline,
                    activeRouterIp = activeRouterIp,
                    isWifiActive = isWifiActive,
                    isLoading = isLoading,
                    onRefresh = { refreshData() },
                    onEditAddress = {
                        targetFavoriteForEdit = favoritesList.firstOrNull { it.address == activeAddress }
                            ?: FavoriteAddress("fav_active", "Мій дім", activeAddress, activeQueue)
                        showAddressDialog = true
                    }
                )
                1 -> MyAddressesTab(
                    theme = theme,
                    context = context,
                    activeAddress = activeAddress,
                    activeQueue = activeQueue,
                    favorites = favoritesList,
                    schedule = schedule,
                    outages1562 = outages1562,
                    currentSecondsTicker = currentSecondsTicker,
                    onSelectActive = { fav ->
                        activeAddress = fav.address
                        activeQueue = fav.queue
                        prefs.edit().putString("active_address", fav.address).putString("active_queue", fav.queue).apply()
                        Toast.makeText(context, "🏠 Активна адреса: ${fav.address} (Черга ${fav.queue})", Toast.LENGTH_SHORT).show()
                        selectedTab = 0
                    },
                    onAddAddress = {
                        targetFavoriteForEdit = null
                        showAddressDialog = true
                    },
                    onEditAddress = { fav ->
                        targetFavoriteForEdit = fav
                        showAddressDialog = true
                    },
                    onDeleteAddress = { id ->
                        val updated = favoritesList.filter { it.id != id }
                        favoritesList = updated
                        saveFavorites(updated)
                        Toast.makeText(context, "Адресу видалено", Toast.LENGTH_SHORT).show()
                    },
                    onSelectQueue = { q ->
                        activeQueue = q
                        val newAddr = "Харків (Черга $q)"
                        activeAddress = newAddr
                        prefs.edit().putString("active_queue", q).putString("active_address", newAddr).apply()
                        Toast.makeText(context, "🎯 Обрано Чергу $q", Toast.LENGTH_SHORT).show()
                    }
                )
                2 -> SearchTab(
                    theme = theme,
                    context = context,
                    activeAddress = activeAddress,
                    favorites = favoritesList,
                    outages1562 = outages1562,
                    onSelectAddress = { addr, q ->
                        activeAddress = addr
                        activeQueue = q
                        prefs.edit().putString("active_address", addr).putString("active_queue", q).apply()
                        selectedTab = 0
                        Toast.makeText(context, "🏠 Встановлено: $addr (Черга $q)", Toast.LENGTH_SHORT).show()
                    },
                    onAddFavorite = { title, addr, q ->
                        val updated = favoritesList.toMutableList()
                        updated.add(FavoriteAddress("fav_${System.currentTimeMillis()}", title, addr, q))
                        favoritesList = updated
                        saveFavorites(updated)
                        Toast.makeText(context, "⭐ '$title' збережено в обрані", Toast.LENGTH_SHORT).show()
                    },
                    onDeleteFavorite = { id ->
                        val updated = favoritesList.filter { it.id != id }
                        favoritesList = updated
                        saveFavorites(updated)
                        Toast.makeText(context, "Видалено з обраних", Toast.LENGTH_SHORT).show()
                    }
                )
                3 -> Emergencies1562Tab(
                    theme = theme,
                    context = context,
                    activeAddress = activeAddress,
                    outages = outages1562,
                    lastUpdated = lastUpdated1562,
                    isLoading = isLoading,
                    onRefresh = { refreshData() }
                )
                4 -> SettingsTab(
                    theme = theme,
                    context = context,
                    currentThemeMode = currentThemeMode,
                    isDarkTheme = isDarkTheme,
                    onSelectThemeMode = onSelectThemeMode,
                    activeQueue = activeQueue,
                    activeAddress = activeAddress,
                    onQueueChange = { q ->
                        activeQueue = q
                        prefs.edit().putString("active_queue", q).apply()
                        OutageNotificationScheduler.scheduleAlarmsForToday(context, q, schedule)
                        ElectroWidgetProvider.updateAllWidgets(context)
                    },
                    onEditAddress = {
                        targetFavoriteForEdit = favoritesList.firstOrNull { it.address == activeAddress }
                            ?: FavoriteAddress("fav_active", "Мій дім", activeAddress, activeQueue)
                        showAddressDialog = true
                    },
                    activeRouterIp = activeRouterIp,
                    isRouterOnline = isRouterOnline,
                    onSaveRouterIp = { ip ->
                        activeRouterIp = ip
                        prefs.edit().putString("router_ip", ip).apply()
                        refreshData()
                    },
                    onCheckRouter = { refreshData() },
                    onRescheduleAlarms = {
                        OutageNotificationScheduler.scheduleAlarmsForToday(context, activeQueue, schedule)
                        ElectroWidgetProvider.updateAllWidgets(context)
                    }
                )
            }

            if (showAddressDialog) {
                AddEditAddressDialog(
                    theme = theme,
                    existingFavorite = targetFavoriteForEdit,
                    defaultQueue = activeQueue,
                    onDismiss = { showAddressDialog = false },
                    onConfirm = { id, title, address, queue, makeActive ->
                        val updated = favoritesList.toMutableList()
                        if (id != null) {
                            val idx = updated.indexOfFirst { it.id == id }
                            if (idx >= 0) {
                                updated[idx] = FavoriteAddress(id, title, address, queue)
                            } else {
                                updated.add(FavoriteAddress(id, title, address, queue))
                            }
                        } else {
                            val newId = "fav_${System.currentTimeMillis()}"
                            updated.add(FavoriteAddress(newId, title, address, queue))
                        }
                        favoritesList = updated
                        saveFavorites(updated)

                        if (makeActive || updated.size == 1) {
                            activeAddress = address
                            activeQueue = queue
                            prefs.edit().putString("active_address", address).putString("active_queue", queue).apply()
                        }
                        showAddressDialog = false
                        Toast.makeText(context, "✅ Збережено: $title (Черга $queue)", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// TAB 0: SCHEDULE & HERO (M3 Expressive)
// -----------------------------------------------------------------------------
@Composable
fun ScheduleTab(
    theme: ElectroCustomColors,
    currentThemeMode: AppThemeMode,
    isDarkTheme: Boolean,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    activeQueue: String,
    activeAddress: String,
    unifiedStatus: UnifiedStatus,
    schedule: DailySchedule?,
    isRouterOnline: Boolean?,
    activeRouterIp: String = "",
    isWifiActive: Boolean = true,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onEditAddress: () -> Unit
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val intervals = schedule?.queues?.get(activeQueue) ?: emptyList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // M3 Top Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ElectroBot",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "м. Харків • Харківобленерго",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // M3 3-Mode Theme Selector Button (System -> Light -> Dark)
                val nextMode = when (currentThemeMode) {
                    AppThemeMode.SYSTEM -> AppThemeMode.LIGHT
                    AppThemeMode.LIGHT -> AppThemeMode.DARK
                    AppThemeMode.DARK -> AppThemeMode.SYSTEM
                }
                val modeLabel = when (currentThemeMode) {
                    AppThemeMode.SYSTEM -> "📱 Авто"
                    AppThemeMode.LIGHT -> "☀️ Світла"
                    AppThemeMode.DARK -> "🌙 Темна"
                }

                FilledTonalButton(
                    onClick = {
                        onSelectThemeMode(nextMode)
                        val msg = when (nextMode) {
                            AppThemeMode.SYSTEM -> "📱 Встановлено системну тему"
                            AppThemeMode.LIGHT -> "☀️ Встановлено світлу тему"
                            AppThemeMode.DARK -> "🌙 Встановлено темну тему"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(modeLabel, style = MaterialTheme.typography.labelSmall)
                }

                // Wi-Fi / Router Status Pill
                if (!isWifiActive) {
                    AssistChip(
                        onClick = {
                            Toast.makeText(context, "📱 Мобільний зв'язок. Роутер пінгується через Wi-Fi.", Toast.LENGTH_LONG).show()
                        },
                        label = { Text("📱 LTE", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(36.dp)
                    )
                } else if (isRouterOnline != null) {
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                if (isRouterOnline) "🟢 Wi-Fi" else "🔴 Офлайн",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isRouterOnline) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                            )
                        },
                        modifier = Modifier.height(36.dp)
                    )
                }

                // M3 Refresh Icon Button
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Text(if (isLoading) "⏳" else "🔄", fontSize = 16.sp)
                }
            }
        }

        // Active Address M3 Elevated Card
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEditAddress() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📍", fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "АКТИВНА АДРЕСА",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = activeAddress,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Черга $activeQueue",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 22.sp, fontWeight = FontWeight.Light)
                }
            }
        }

        // =====================================================================
        // M3 HERO OUTAGE CARD
        // =====================================================================
        val now = Calendar.getInstance()
        val curHour = now.get(Calendar.HOUR_OF_DAY)
        val curMin = now.get(Calendar.MINUTE)
        val timeLabel = String.format(Locale.US, "%02d:%02d", curHour, curMin)

        val isOutage = unifiedStatus.isOutage
        val bulbIcon = if (isOutage) "💡̸" else "💡"
        val statusMainText = if (isOutage) "Вимкнено" else "Увімкнено"
        val statusTargetSubtext = if (isOutage) {
            "Відновиться через ~${unifiedStatus.targetTimeStr}"
        } else {
            "Вимкнеться через ~${unifiedStatus.targetTimeStr}"
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (isOutage) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top Row: Queue + Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "#$activeQueue",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(bulbIcon, fontSize = 20.sp)
                        }
                        Text(
                            text = "Станом на $timeLabel",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = statusMainText,
                            style = MaterialTheme.typography.titleLarge,
                            color = if (isOutage) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = statusTargetSubtext,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                // Big Monospaced Countdown Timer
                Column {
                    Text(
                        text = unifiedStatus.countdownHms,
                        color = if (isOutage) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = unifiedStatus.headline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // M3 Live 6.5h Timeline Ruler Canvas
                M3LiveTimelineRuler(intervals = intervals)

                // 24-Hour Overview Track
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "ДОБОВИЙ ОГЛЯД 24 ГОДИНИ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "🟩 Світло є  🟥 Відключено",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TimelineGraphicBar(intervals = intervals, compact = true)
                }

                // Smart Charge Window Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = unifiedStatus.chargeWindowStr,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (unifiedStatus.chargeWindowAdvice.isNotEmpty()) {
                            Text(
                                text = unifiedStatus.chargeWindowAdvice,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        // Share Button
        OutlinedButton(
            onClick = {
                val sorted = intervals.distinctBy { it.startMinute to it.endMinute }.sortedBy { it.startMinute }
                val invsStr = if (sorted.isNotEmpty()) {
                    sorted.joinToString("; ") { "${it.startStr} - ${it.endStr}" }
                } else "Відключень не заплановано"
                val statusText = if (isOutage) "🔴 Світло відключено (${unifiedStatus.countdownHms})" else "🟢 Світло є"
                val shareText = "⚡ Графік відключення світла (ElectroBot):\n📍 $activeAddress (Черга $activeQueue)\n📅 Сьогодні: $invsStr\n💡 Зараз: $statusText\n\nОфіційні дані: АТ «Харківобленерго»"
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, "Поділитися розкладом"))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📤", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Поділитися графіком (Telegram / Viber)",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// M3 LIVE 6.5-HOUR TIMELINE RULER
// Built on M3 Semantic Color Scheme (Automatic high-contrast in Light/Dark/System)
// -----------------------------------------------------------------------------
@Composable
fun M3LiveTimelineRuler(
    intervals: List<ScheduleInterval>
) {
    val density = LocalDensity.current
    val colorScheme = MaterialTheme.colorScheme

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
    ) {
        val cal = Calendar.getInstance()
        val curMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        val windowDurationMin = 390f
        val windowStartMin = (curMinute - 60f).coerceAtLeast(0f)
        val windowEndMin = windowStartMin + windowDurationMin

        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            val baselineY = 24.dp.toPx()
            val boxTop = 2.dp.toPx()
            val boxHeight = 22.dp.toPx()
            val boxRadius = 6.dp.toPx()

            fun minToX(m: Float): Float {
                return ((m - windowStartMin) / windowDurationMin) * canvasWidth
            }

            // 1. Baseline Ruler Line
            drawLine(
                color = colorScheme.outlineVariant,
                start = Offset(0f, baselineY),
                end = Offset(canvasWidth, baselineY),
                strokeWidth = 1.dp.toPx()
            )

            // 2. Outage Amber Boxes
            for (inv in intervals) {
                val invStart = inv.startMinute.toFloat()
                val invEnd = inv.endMinute.toFloat()

                if (invEnd > windowStartMin && invStart < windowEndMin) {
                    val clampedStart = maxOf(invStart, windowStartMin)
                    val clampedEnd = minOf(invEnd, windowEndMin)

                    val x1 = minToX(clampedStart)
                    val x2 = minToX(clampedEnd)
                    val boxW = maxOf(x2 - x1, 4f)

                    drawRoundRect(
                        color = colorScheme.tertiaryContainer,
                        topLeft = Offset(x1, boxTop),
                        size = Size(boxW, boxHeight),
                        cornerRadius = CornerRadius(boxRadius, boxRadius)
                    )

                    drawRoundRect(
                        color = colorScheme.tertiary,
                        topLeft = Offset(x1, boxTop),
                        size = Size(boxW, boxHeight),
                        cornerRadius = CornerRadius(boxRadius, boxRadius),
                        style = Stroke(width = 1.5.dp.toPx())
                    )

                    // Text inside box using onTertiaryContainer (Always optimal contrast!)
                    if (boxW > 45.dp.toPx()) {
                        val durationHours = (inv.endMinute - inv.startMinute) / 60.0
                        val text = "💡 %.1f год".format(Locale.US, durationHours).replace(".0", "")

                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb(
                                (colorScheme.onTertiaryContainer.alpha * 255).toInt(),
                                (colorScheme.onTertiaryContainer.red * 255).toInt(),
                                (colorScheme.onTertiaryContainer.green * 255).toInt(),
                                (colorScheme.onTertiaryContainer.blue * 255).toInt()
                            )
                            textSize = with(density) { 10.sp.toPx() }
                            isFakeBoldText = true
                            isAntiAlias = true
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        val textY = boxTop + boxHeight / 2f + with(density) { 3.5.dp.toPx() }
                        drawContext.canvas.nativeCanvas.drawText(
                            text,
                            x1 + boxW / 2f,
                            textY,
                            paint
                        )
                    }
                }
            }

            // 3. Hour and 30-min Ticks & Labels
            val startHour = (windowStartMin / 60).toInt()
            val endHour = (windowEndMin / 60).toInt() + 1

            for (h in startHour..endHour) {
                val hourMin = (h * 60).toFloat()
                if (hourMin in windowStartMin..windowEndMin) {
                    val tx = minToX(hourMin)
                    drawLine(
                        color = colorScheme.onSurfaceVariant,
                        start = Offset(tx, baselineY),
                        end = Offset(tx, baselineY + 6.dp.toPx()),
                        strokeWidth = 1.2.dp.toPx()
                    )

                    val label = String.format(Locale.US, "%02d:00", h % 24)
                    val textPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(
                            (colorScheme.onSurface.alpha * 255).toInt(),
                            (colorScheme.onSurface.red * 255).toInt(),
                            (colorScheme.onSurface.green * 255).toInt(),
                            (colorScheme.onSurface.blue * 255).toInt()
                        )
                        textSize = with(density) { 9.5.sp.toPx() }
                        isFakeBoldText = true
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        tx,
                        baselineY + 20.dp.toPx(),
                        textPaint
                    )
                }

                val halfMin = hourMin + 30f
                if (halfMin in windowStartMin..windowEndMin) {
                    val hx = minToX(halfMin)
                    drawLine(
                        color = colorScheme.outlineVariant,
                        start = Offset(hx, baselineY),
                        end = Offset(hx, baselineY + 3.5.dp.toPx()),
                        strokeWidth = 0.8.dp.toPx()
                    )
                }
            }

            // 4. Current Time Needle Cursor
            val needleX = minToX(curMinute.toFloat())
            if (needleX in 0f..canvasWidth) {
                drawLine(
                    color = colorScheme.error,
                    start = Offset(needleX, 0f),
                    end = Offset(needleX, canvasHeight - 12.dp.toPx()),
                    strokeWidth = 2.dp.toPx()
                )

                drawCircle(
                    color = colorScheme.error,
                    radius = 3.5.dp.toPx(),
                    center = Offset(needleX, baselineY)
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 24-HOUR SEGMENTED TIMELINE BAR (M3 Semantic Tonal Colors)
// -----------------------------------------------------------------------------
@Composable
fun TimelineGraphicBar(
    intervals: List<ScheduleInterval>,
    compact: Boolean = false
) {
    val now = Calendar.getInstance()
    val curHour = now.get(Calendar.HOUR_OF_DAY)
    val curMin = now.get(Calendar.MINUTE)
    val curMinute = curHour * 60 + curMin
    val curFraction = curMinute.toFloat() / 1440f
    val barHeight = if (compact) 12.dp else 24.dp
    val colorScheme = MaterialTheme.colorScheme

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth()
        ) {
            val totalWidth = maxWidth
            val needleX = totalWidth * curFraction

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .clip(RoundedCornerShape(999.dp))
                    .background(colorScheme.secondaryContainer)
                    .border(1.dp, colorScheme.secondary.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
            ) {
                for (inv in intervals) {
                    val startRatio = inv.startMinute.toFloat() / 1440f
                    val endRatio = inv.endMinute.toFloat() / 1440f
                    val segWidth = (endRatio - startRatio) * totalWidth.value

                    Box(
                        modifier = Modifier
                            .offset(x = totalWidth * startRatio)
                            .width(segWidth.dp)
                            .fillMaxHeight()
                            .background(colorScheme.error)
                    )
                }
            }

            // Needle indicator
            Box(
                modifier = Modifier
                    .offset(x = needleX - 1.dp)
                    .width(2.dp)
                    .height(barHeight)
                    .background(colorScheme.primary)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("00:00", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
            Text("06:00", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
            Text("12:00", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
            Text("18:00", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
            Text("24:00", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
        }
    }
}

fun calculatePowerOnIntervals(outageIntervals: List<ScheduleInterval>): List<ScheduleInterval> {
    if (outageIntervals.isEmpty()) {
        return listOf(ScheduleInterval(0, 1440, "00:00", "24:00"))
    }
    val sorted = outageIntervals.sortedBy { it.startMinute }
    val onList = mutableListOf<ScheduleInterval>()
    var cur = 0
    for (inv in sorted) {
        if (inv.startMinute > cur) {
            val sStr = String.format(Locale.US, "%02d:%02d", cur / 60, cur % 60)
            onList.add(ScheduleInterval(cur, inv.startMinute, sStr, inv.startStr))
        }
        cur = maxOf(cur, inv.endMinute)
    }
    if (cur < 1440) {
        val sStr = String.format(Locale.US, "%02d:%02d", cur / 60, cur % 60)
        onList.add(ScheduleInterval(cur, 1440, sStr, "24:00"))
    }
    return onList
}

// -----------------------------------------------------------------------------
// TAB 1: MY ADDRESSES (M3 Inset Grouped)
// -----------------------------------------------------------------------------
@Composable
fun MyAddressesTab(
    theme: ElectroCustomColors,
    context: Context,
    activeAddress: String,
    activeQueue: String,
    favorites: List<FavoriteAddress>,
    schedule: DailySchedule?,
    outages1562: List<EmergencyOutage1562>,
    currentSecondsTicker: Long = 0L,
    onSelectActive: (FavoriteAddress) -> Unit,
    onAddAddress: () -> Unit,
    onEditAddress: (FavoriteAddress) -> Unit,
    onDeleteAddress: (String) -> Unit,
    onSelectQueue: (String) -> Unit
) {
    val allQueues = listOf("1.1", "1.2", "2.1", "2.2", "3.1", "3.2", "4.1", "4.2", "5.1", "5.2", "6.1", "6.2")
    val now = Calendar.getInstance()
    val curMinute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    var showAllQueuesAccordion by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "МОЇ ЗБЕРЕЖЕНІ АДРЕСИ (${favorites.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Оберіть адресу для активації на головному екрані або скорегуйте номер черги.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (schedule?.isEmergency == true) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = "🚨 УВАГА! ДІЮТЬ АВАРІЙНІ ВІДКЛЮЧЕННЯ (ГАВ). Відключення відбуваються поза розкладом.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }

        items(favorites) { fav ->
            val isCurrent = (fav.address == activeAddress || (fav.queue == activeQueue && fav.address.contains("Дудинськ")))
            val status = StatusResolver.resolve(fav.address, fav.queue, schedule, outages1562, currentSecondsTicker)
            val outageInvs = (schedule?.queues?.get(fav.queue) ?: emptyList()).distinctBy { it.startMinute to it.endMinute }.sortedBy { it.startMinute }
            val powerOnInvs = calculatePowerOnIntervals(outageInvs)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = androidx.compose.foundation.BorderStroke(
                    width = if (isCurrent) 1.5.dp else 1.dp,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text(
                                text = fav.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Черга ${fav.queue}",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (isCurrent) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "✓ Активна",
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Button(
                                onClick = { onSelectActive(fav) },
                                shape = RoundedCornerShape(999.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Обрати", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Text(
                        text = "📍 ${fav.address}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Live Status Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (status.isOutage) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = if (status.isOutage) "🔴 Зараз світла НЕМАЄ" else "🟢 Зараз світло Є",
                                    color = if (status.isOutage) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = if (status.isOutage) "Очікуване увімкнення о ${status.targetTimeStr}"
                                           else "Наступне відключення о ${status.targetTimeStr}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (status.countdownHms.isNotBlank() && status.countdownHms != "00:00:00") {
                                Text(
                                    text = status.countdownHms,
                                    color = if (status.isOutage) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    TimelineGraphicBar(intervals = outageInvs, compact = true)

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (outageInvs.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (inv in outageInvs) {
                                    AssistChip(
                                        onClick = {},
                                        label = { Text("🔴 ${inv.startStr} - ${inv.endStr}", style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }

                        if (powerOnInvs.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (inv in powerOnInvs) {
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text("🟢 ${inv.startStr} - ${inv.endStr}", style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { onEditAddress(fav) },
                            shape = RoundedCornerShape(999.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Редагувати", style = MaterialTheme.typography.labelSmall)
                        }
                        if (favorites.size > 1) {
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { onDeleteAddress(fav.id) },
                                shape = RoundedCornerShape(999.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("🗑️", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = onAddAddress,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Text("➕ Додати нову адресу", style = MaterialTheme.typography.labelLarge)
            }
        }

        item {
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAllQueuesAccordion = !showAllQueuesAccordion },
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📋 Довідник усіх 12 черг міста",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = if (showAllQueuesAccordion) "▲" else "▼",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (showAllQueuesAccordion) {
            items(allQueues) { q ->
                val intervalsQ = schedule?.queues?.get(q) ?: emptyList()
                val isOffNow = intervalsQ.any { curMinute in it.startMinute until it.endMinute }
                val isCurrent = (q == activeQueue)
                val hoursStr = if (intervalsQ.isNotEmpty()) {
                    intervalsQ.joinToString("; ") { "${it.startStr} - ${it.endStr}" }
                } else {
                    "Відключень не заплановано"
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isCurrent) 1.5.dp else 1.dp,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Черга $q",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(if (isOffNow) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (isOffNow) "🔴 Вимкнено" else "🟢 Світло є",
                                        color = if (isOffNow) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            Text(
                                text = "Години: $hoursStr",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = { onSelectQueue(q) },
                            shape = RoundedCornerShape(999.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Обрати", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// TAB 2: STREET SEARCH & FAVORITES (M3)
// -----------------------------------------------------------------------------
@Composable
fun SearchTab(
    theme: ElectroCustomColors,
    context: Context,
    activeAddress: String,
    favorites: List<FavoriteAddress>,
    outages1562: List<EmergencyOutage1562>,
    onSelectAddress: (String, String) -> Unit,
    onAddFavorite: (String, String, String) -> Unit,
    onDeleteFavorite: (String) -> Unit
) {
    var query by remember { mutableStateOf("Дудинської, 9") }
    var results by remember { mutableStateOf<List<StreetRecord>>(emptyList()) }
    var selectedQueue by remember { mutableStateOf("1.1") }
    var showSaveDialogForAddr by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        val (st, _) = KharkivData.extractStreetAndBuilding(query)
        results = KharkivData.searchStreets(context, st.ifEmpty { query })
        val detected = KharkivData.detectQueueForAddress(query)
        if (detected.isNotBlank()) {
            selectedQueue = detected
        }
    }

    val (stTyped, bldTyped) = KharkivData.extractStreetAndBuilding(query)
    val active1562Alert = outages1562.firstOrNull { it.matchesAddress(stTyped, bldTyped) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "ШВИДКИЙ ВИБІР З ОБРАНИХ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(favorites) { fav ->
                AssistChip(
                    onClick = { onSelectAddress(fav.address, fav.queue) },
                    label = { Text("⚡ ${fav.title} (Ч.${fav.queue})", style = MaterialTheme.typography.labelSmall) },
                    trailingIcon = {
                        Text(
                            text = "✕",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .clickable { onDeleteFavorite(fav.id) }
                                .padding(2.dp)
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "ПОШУК ВУЛИЦІ (ХАРКІВ)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Наприклад: Дудинської, 9...") },
            leadingIcon = { Text("🔍", fontSize = 16.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )

        if (active1562Alert != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "🚨 Аварійне знеструмлення АСДС 1562!",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Відновлення планується: ${active1562Alert.plannedEndTime}",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Text(
            text = "Знайдені адреси (${results.size}):",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(results) { street ->
                val fullAddr = if (bldTyped.isNotEmpty()) "${street.type} ${street.name}, буд. $bldTyped" else "${street.type} ${street.name}"
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = fullAddr,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "м. Харків",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = {
                                    val q = KharkivData.detectQueueForAddress(fullAddr).ifEmpty { selectedQueue }
                                    onSelectAddress(fullAddr, q)
                                },
                                shape = RoundedCornerShape(999.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Обрати", style = MaterialTheme.typography.labelSmall)
                            }

                            OutlinedButton(
                                onClick = { showSaveDialogForAddr = fullAddr },
                                shape = RoundedCornerShape(999.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("⭐", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialogForAddr != null) {
        val targetAddr = showSaveDialogForAddr!!
        var favTitle by remember { mutableStateOf("Мій дім") }
        var favQ by remember(targetAddr) { mutableStateOf(KharkivData.detectQueueForAddress(targetAddr).ifEmpty { "1.1" }) }

        AlertDialog(
            onDismissRequest = { showSaveDialogForAddr = null },
            title = { Text("Зберегти адресу", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Назва адреси:", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = favTitle,
                        onValueChange = { favTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text("Адреса: $targetAddr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Черга:", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = favQ,
                        onValueChange = { favQ = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddFavorite(favTitle, targetAddr, favQ)
                        showSaveDialogForAddr = null
                    }
                ) {
                    Text("Зберегти")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialogForAddr = null }) {
                    Text("Скасувати")
                }
            }
        )
    }
}

// -----------------------------------------------------------------------------
// TAB 3: EMERGENCIES 1562 (M3)
// -----------------------------------------------------------------------------
@Composable
fun Emergencies1562Tab(
    theme: ElectroCustomColors,
    context: Context,
    activeAddress: String,
    outages: List<EmergencyOutage1562>,
    lastUpdated: String = "",
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    val initialAddr = KharkivData.extractStreetAndBuilding(activeAddress).let { (st, bld) ->
        if (st.isNotEmpty()) "$st${if (bld.isNotEmpty()) ", $bld" else ""}"
        else activeAddress.replace("Харків, ", "").replace("Харків (", "").replace(")", "").trim()
    }

    var filterText by remember(activeAddress) { mutableStateOf(initialAddr) }
    var elecOnly by remember { mutableStateOf(true) }
    var isCityWideMode by remember { mutableStateOf(false) }

    val effectiveFilter = if (isCityWideMode) "" else filterText.trim()
    val hasFilter = effectiveFilter.isNotBlank()
    val (streetTyped, bldTyped) = if (hasFilter) KharkivData.extractStreetAndBuilding(effectiveFilter) else ("" to "")
    val searchStem = (streetTyped.ifEmpty { effectiveFilter }).lowercase()
        .replace("вул.", "").replace("вулиця", "").replace("просп.", "").replace("пр.", "").trim()
    val stem = if (searchStem.length >= 4) searchStem.substring(0, 4) else searchStem

    val basePool = outages.filter {
        if (elecOnly) {
            val raw = it.rawAnnouncement.lowercase()
            raw.contains("електро") || raw.contains("струм") || raw.contains("обленерго") || raw.contains("джерел") || raw.contains("знеструм")
        } else true
    }

    val exactMatches = mutableListOf<EmergencyOutage1562>()
    val streetMatches = mutableListOf<EmergencyOutage1562>()
    val allAffectedBlds = mutableListOf<String>()

    if (hasFilter) {
        for (ann in basePool) {
            var stMatched = false
            var bldMatched = false

            for ((stName, bList) in ann.affectedStreets) {
                val sNorm = stName.lowercase().replace("вул.", "").replace("вулиця", "").replace("просп.", "").replace("пр.", "").trim()
                if (sNorm.contains(stem) || stem.contains(sNorm)) {
                    stMatched = true
                    allAffectedBlds.addAll(bList)
                    if (bldTyped.isNotEmpty() && ann.hasBuilding(bldTyped, bList)) {
                        bldMatched = true
                    }
                }
            }
            if (!stMatched && ann.rawAnnouncement.lowercase().contains(stem)) {
                stMatched = true
            }

            if (bldMatched) exactMatches.add(ann)
            else if (stMatched) streetMatches.add(ann)
        }
    }

    val uniqueBlds = allAffectedBlds.distinct()
    val otherCityList = if (hasFilter) {
        basePool.filter { it !in exactMatches && it !in streetMatches }
    } else emptyList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Аварії АСДС «1562»",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (lastUpdated.isNotEmpty()) "Оновлено о $lastUpdated • Робіт: ${outages.size}" else "Оперативна диспетчерська",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/Kharkiv1562Bot"))
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("🤖 Бот", style = MaterialTheme.typography.labelSmall)
                }

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Text(if (isLoading) "⏳" else "🔄", fontSize = 16.sp)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = !isCityWideMode && filterText.isNotBlank(),
                onClick = {
                    filterText = initialAddr
                    isCityWideMode = false
                },
                label = { Text("📍 Моя адреса", style = MaterialTheme.typography.labelSmall) }
            )

            FilterChip(
                selected = isCityWideMode,
                onClick = { isCityWideMode = true },
                label = { Text("🏙️ Все місто (${basePool.size})", style = MaterialTheme.typography.labelSmall) }
            )

            FilterChip(
                selected = elecOnly,
                onClick = { elecOnly = !elecOnly },
                label = { Text(if (elecOnly) "⚡ Лише струм" else "🔧 Усі", style = MaterialTheme.typography.labelSmall) }
            )
        }

        OutlinedTextField(
            value = filterText,
            onValueChange = {
                filterText = it
                isCityWideMode = false
            },
            placeholder = { Text("Введіть вулицю та будинок...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        if (hasFilter) {
            if (exactMatches.isNotEmpty()) {
                val first = exactMatches.first()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "🚨 Будинок № $bldTyped ($streetTyped) — АВАРІЙНЕ ЗНЕСТРУМЛЕННЯ!",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Орієнтовне відновлення: ${first.plannedEndTime}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else if (streetMatches.isNotEmpty()) {
                val bldsStr = uniqueBlds.take(8).joinToString(", ")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "🟢 Будинок № $bldTyped ($streetTyped): АВАРІЙ НЕМАЄ",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Роботи на вулиці стосуються тільки інших будинків: $bldsStr. Ваш будинок живиться за графіком.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (exactMatches.isNotEmpty()) {
                item {
                    Text(
                        text = "🚨 АВАРІЇ ЗА ВАШОЮ АДРЕСОЮ (${exactMatches.size}):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        letterSpacing = 0.5.sp
                    )
                }
                items(exactMatches) { out ->
                    Outage1562M3Card(out = out, bldHighlight = bldTyped, isUnaffected = false)
                }
            }

            if (streetMatches.isNotEmpty()) {
                item {
                    Text(
                        text = "⚠️ РОБОТИ НА ВУЛИЦІ (${streetMatches.size}):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        letterSpacing = 0.5.sp
                    )
                }
                items(streetMatches) { out ->
                    Outage1562M3Card(out = out, bldHighlight = bldTyped, isUnaffected = true)
                }
            }

            if (otherCityList.isNotEmpty()) {
                item {
                    Text(
                        text = "🏙️ ІНШІ РОБОТИ ПО МІСТУ (${otherCityList.size}):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                }
                items(otherCityList) { out ->
                    Outage1562M3Card(out = out, bldHighlight = "", isUnaffected = false)
                }
            }

            if (basePool.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✅ Відкритих аварій 1562 не зафіксовано",
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                }
            } else if (isCityWideMode || !hasFilter) {
                items(basePool) { out ->
                    Outage1562M3Card(out = out, bldHighlight = "", isUnaffected = false)
                }
            }
        }
    }
}

@Composable
fun Outage1562M3Card(
    out: EmergencyOutage1562,
    bldHighlight: String,
    isUnaffected: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Початок: ${out.startTime}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Відновлення: ${out.plannedEndTime}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }

            val streetsStr = out.affectedStreets.entries.joinToString("; ") { (s, b) ->
                "$s (буд. ${b.joinToString(", ")})"
            }
            val prefix = if (isUnaffected && bldHighlight.isNotEmpty()) "ℹ️ [Буд. $bldHighlight не знеструмлено] " else ""
            Text(
                text = prefix + (streetsStr.ifEmpty { out.rawAnnouncement.take(120) }),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = out.workType,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Служба: ${out.service}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// -----------------------------------------------------------------------------
// TAB 4: SETTINGS (M3 Settings with Light, Dark & System Theme Selection)
// -----------------------------------------------------------------------------
@Composable
fun SettingsTab(
    theme: ElectroCustomColors,
    context: Context,
    currentThemeMode: AppThemeMode,
    isDarkTheme: Boolean,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    activeQueue: String,
    activeAddress: String,
    onQueueChange: (String) -> Unit,
    onEditAddress: () -> Unit,
    activeRouterIp: String,
    isRouterOnline: Boolean?,
    onSaveRouterIp: (String) -> Unit,
    onCheckRouter: () -> Unit,
    onRescheduleAlarms: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateStatusMessage by remember { mutableStateOf<String?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }

    var isNotifEnabled by remember { mutableStateOf(OutageNotificationScheduler.isNotificationsEnabled(context)) }
    var minutesBefore by remember { mutableIntStateOf(OutageNotificationScheduler.getMinutesBefore(context)) }
    var isRestoreEnabled by remember { mutableStateOf(OutageNotificationScheduler.isNotifyRestoreEnabled(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Налаштування",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        // 1. THEME SELECTION SECTION (M3 Three-Way Segmented Row)
        Text(
            text = "ТЕМА ОФОРМЛЕННЯ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Оберіть режим теми:",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val modes = listOf(
                        Triple(AppThemeMode.SYSTEM, "📱 Системна", "За системними налаштуваннями"),
                        Triple(AppThemeMode.LIGHT, "☀️ Світла", "Завжди світла тема"),
                        Triple(AppThemeMode.DARK, "🌙 Темна", "Завжди темна тема")
                    )

                    modes.forEach { (mode, label, _) ->
                        val isSelected = currentThemeMode == mode
                        if (isSelected) {
                            Button(
                                onClick = { onSelectThemeMode(mode) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onSelectThemeMode(mode) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Text(label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                Text(
                    text = when (currentThemeMode) {
                        AppThemeMode.SYSTEM -> "📱 Застосовується тема вашої системи Android (${if (isDarkTheme) "зараз темна" else "зараз світла"})."
                        AppThemeMode.LIGHT -> "☀️ Активна комфортна світла тема (Office / High-Contrast)."
                        AppThemeMode.DARK -> "🌙 Активна темна тема для економії заряду та захисту очей."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 2. GENERAL SETTINGS
        Text(
            text = "МОЯ АДРЕСА ТА ЧЕРГА",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(activeAddress, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("Підчерга: Черга $activeQueue", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text("Черга $activeQueue", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = onEditAddress,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("✏️ Змінити адресу або чергу", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // 3. NOTIFICATIONS
        Text(
            text = "СПОДІВАННЯ ТА БУДИЛЬНИКИ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Завчасні сповіщення про вимкнення", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text("Попереджати до початку відключення", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = isNotifEnabled,
                        onCheckedChange = { checked ->
                            isNotifEnabled = checked
                            OutageNotificationScheduler.setNotificationsEnabled(context, checked)
                            onRescheduleAlarms()
                        }
                    )
                }

                if (isNotifEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(15, 30, 45, 60).forEach { mins ->
                            val isSelected = minutesBefore == mins
                            if (isSelected) {
                                Button(
                                    onClick = {
                                        minutesBefore = mins
                                        OutageNotificationScheduler.setMinutesBefore(context, mins)
                                        onRescheduleAlarms()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    Text("$mins хв", style = MaterialTheme.typography.labelSmall)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        minutesBefore = mins
                                        OutageNotificationScheduler.setMinutesBefore(context, mins)
                                        onRescheduleAlarms()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    Text("$mins хв", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💡 Сповіщати за 10 хв до увімкнення", style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = isRestoreEnabled,
                            onCheckedChange = { checked ->
                                isRestoreEnabled = checked
                                OutageNotificationScheduler.setNotifyRestoreEnabled(context, checked)
                                onRescheduleAlarms()
                            }
                        )
                    }
                }

                FilledTonalButton(
                    onClick = {
                        OutageNotificationScheduler.sendTestNotification(context)
                        Toast.makeText(context, "🔔 Тестове сповіщення надіслано", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("🔔 Надіслати тестове сповіщення", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // 4. GITHUB UPDATES
        Text(
            text = "ОНОВЛЕННЯ ТА ВЕРСІЯ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("GitHub Releases", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text("Поточна версія: ${GitHubUpdater.CURRENT_VERSION}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(GitHubUpdater.CURRENT_VERSION, color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }

                if (updateStatusMessage != null) {
                    Text(
                        text = updateStatusMessage ?: "",
                        color = if (updateInfo?.hasUpdate == true) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (isDownloading) {
                    LinearProgressIndicator(
                        progress = { downloadProgress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                val curUpdate = updateInfo
                if (curUpdate != null && curUpdate.hasUpdate && !isDownloading) {
                    Button(
                        onClick = {
                            isDownloading = true
                            downloadProgress = 0
                            scope.launch {
                                val res = GitHubUpdater.downloadAndInstallApk(
                                    context = context,
                                    downloadUrl = curUpdate.downloadUrl,
                                    fileName = curUpdate.apkFileName
                                ) { progress -> downloadProgress = progress }
                                isDownloading = false
                                if (res.isFailure) {
                                    updateStatusMessage = "❌ Помилка: ${res.exceptionOrNull()?.localizedMessage}"
                                } else {
                                    updateStatusMessage = "✅ Встановлюємо ${curUpdate.latestVersion}..."
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("⬇️ Встановити ${curUpdate.latestVersion}", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            isCheckingUpdate = true
                            updateStatusMessage = "Перевірка GitHub..."
                            scope.launch {
                                val res = GitHubUpdater.checkForUpdates()
                                isCheckingUpdate = false
                                if (res.isSuccess) {
                                    val info = res.getOrThrow()
                                    updateInfo = info
                                    updateStatusMessage = if (info.hasUpdate) "Знайдено оновлення ${info.latestVersion}!" else "Встановлено найновішу версію"
                                } else {
                                    updateStatusMessage = "Помилка зв'язку"
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (isCheckingUpdate) "⏳ Перевірка..." else "🔄 Перевірити оновлення", style = MaterialTheme.typography.labelSmall)
                    }

                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/drkoh/ElectroBot/releases"))
                            context.startActivity(intent)
                        }
                    ) {
                        Text("GitHub ↗", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// ADD / EDIT ADDRESS DIALOG (M3 Dialog)
// -----------------------------------------------------------------------------
@Composable
fun AddEditAddressDialog(
    theme: ElectroCustomColors,
    existingFavorite: FavoriteAddress?,
    defaultQueue: String,
    onDismiss: () -> Unit,
    onConfirm: (id: String?, title: String, address: String, queue: String, makeActive: Boolean) -> Unit
) {
    val isNew = (existingFavorite == null)
    var titleText by remember { mutableStateOf(existingFavorite?.title ?: if (isNew) "Нова адреса" else "Мій дім") }
    var addrText by remember { mutableStateOf(existingFavorite?.address ?: "") }
    var queueText by remember { mutableStateOf(existingFavorite?.queue ?: defaultQueue) }
    var makeActive by remember { mutableStateOf(isNew) }
    val allQueues = listOf("1.1", "1.2", "2.1", "2.2", "3.1", "3.2", "4.1", "4.2", "5.1", "5.2", "6.1", "6.2")

    val autoDetected = remember(addrText) {
        if (addrText.isNotBlank()) KharkivData.detectQueueForAddress(addrText) else ""
    }

    LaunchedEffect(autoDetected) {
        if (autoDetected.isNotBlank() && (isNew || autoDetected != queueText)) {
            queueText = autoDetected
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isNew) "Додати адресу" else "Редагувати адресу",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Назва:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("Вулиця та номер будинку:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = addrText,
                    onValueChange = { addrText = it },
                    placeholder = { Text("напр. вул. Дудинської, 9") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (autoDetected.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "✨ Знайдено Чергу $autoDetected",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text("Підчерга відключень:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(allQueues) { q ->
                        val isSel = (q == queueText)
                        FilterChip(
                            selected = isSel,
                            onClick = { queueText = q },
                            label = { Text(q, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { makeActive = !makeActive }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = makeActive,
                        onCheckedChange = { makeActive = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Зробити активною на головному",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (addrText.isNotBlank()) {
                        val finalQ = if (queueText.isNotBlank()) queueText else if (autoDetected.isNotBlank()) autoDetected else "1.1"
                        val finalTitle = titleText.trim().ifEmpty { "Адреса" }
                        onConfirm(existingFavorite?.id, finalTitle, addrText.trim(), finalQ, makeActive)
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Зберегти")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Скасувати")
            }
        }
    )
}
