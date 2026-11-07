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
// ElectroBot Mobile (Apple Human Interface / Cupertino Edition)
// =========================================================================

@Composable
fun MainScreen(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
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

    // Dialog state for adding/editing address
    var showAddressDialog by remember { mutableStateOf(false) }
    var targetFavoriteForEdit by remember { mutableStateOf<FavoriteAddress?>(null) }

    // Favorites in State
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

    // Live second ticker for countdown
    var currentSecondsTicker by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentSecondsTicker = System.currentTimeMillis() / 1000
        }
    }

    // Unified Status
    val unifiedStatus = remember(activeAddress, activeQueue, schedule, outages1562, currentSecondsTicker) {
        StatusResolver.resolve(activeAddress, activeQueue, schedule, outages1562, currentSecondsTicker)
    }

    // Data refresher
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
        containerColor = theme.canvas,
        bottomBar = {
            CupertinoTabBar(
                theme = theme,
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it }
            )
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
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
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
// APPLE CUPERTINO TAB BAR
// -----------------------------------------------------------------------------
@Composable
fun CupertinoTabBar(
    theme: ElectroCustomColors,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit
) {
    val tabs = listOf(
        Triple("⚡", "Розклад", 0),
        Triple("📍", "Адреси", 1),
        Triple("🔍", "Пошук", 2),
        Triple("🚨", "1562", 3),
        Triple("⚙️", "Опції", 4)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.headerBg)
    ) {
        // Cupertino hairline top border
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(theme.headerBorder)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { (icon, title, index) ->
                val isSelected = selectedTab == index
                val activeColor = if (index == 3 && isSelected) theme.accentDanger else theme.accentPrimary

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectTab(index) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = icon,
                        fontSize = 19.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = title,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) activeColor else theme.textMuted
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// TAB 0: SCHEDULE & HERO (Apple Human Interface)
// -----------------------------------------------------------------------------
@Composable
fun ScheduleTab(
    theme: ElectroCustomColors,
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
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Cupertino Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ElectroBot",
                    color = theme.textPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "м. Харків • Харківобленерго",
                    color = theme.textMuted,
                    fontSize = 11.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Wi-Fi / Router Status Pill
                if (!isWifiActive) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(theme.badgeNeutralBg)
                            .border(0.5.dp, theme.badgeNeutralBorder, RoundedCornerShape(999.dp))
                            .clickable {
                                Toast.makeText(context, "📱 Мобільна мережа. Роутер пінгується через домашній Wi-Fi.", Toast.LENGTH_LONG).show()
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "📱 LTE",
                            color = theme.badgeNeutralText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else if (isRouterOnline != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (isRouterOnline) theme.badgeSuccessBg else theme.badgeDangerBg)
                            .border(0.5.dp, if (isRouterOnline) theme.badgeSuccessBorder else theme.badgeDangerBorder, RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (isRouterOnline) "🟢 Wi-Fi" else "🔴 Офлайн",
                            color = if (isRouterOnline) theme.badgeSuccessText else theme.badgeDangerText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Apple-style Frosted Refresh Button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(theme.inputBg)
                        .border(0.5.dp, theme.cardBorder, CircleShape)
                        .clickable { onRefresh() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (isLoading) "⏳" else "🔄", fontSize = 14.sp)
                }
            }
        }

        // Active Address Cupertino Inset Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(theme.cardBg)
                .border(0.5.dp, theme.cardBorder, RoundedCornerShape(16.dp))
                .clickable { onEditAddress() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(theme.badgeNeutralBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📍", fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "АКТИВНА АДРЕСА",
                        color = theme.textMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = activeAddress,
                        color = theme.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(theme.badgeNeutralBg)
                        .border(0.5.dp, theme.badgeNeutralBorder, RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Черга $activeQueue",
                        color = theme.badgeNeutralText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text("›", color = theme.textMuted, fontSize = 20.sp, fontWeight = FontWeight.Light)
            }
        }

        // =====================================================================
        // APPLE HERO OUTAGE CARD (Matching Reference Widget)
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(theme.cardBg)
                .border(
                    0.5.dp,
                    if (isOutage) theme.accentDanger.copy(alpha = 0.6f) else theme.cardBorder,
                    RoundedCornerShape(22.dp)
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Top Row: Queue + Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // Left Column: #Queue Bulb + Time
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "#$activeQueue",
                                color = theme.textPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(bulbIcon, fontSize = 18.sp)
                        }
                        Text(
                            text = "Станом на $timeLabel",
                            color = theme.accentWarning,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Right Column: State + Target
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = statusMainText,
                            color = if (isOutage) theme.accentDanger else theme.accentSuccess,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = statusTargetSubtext,
                            color = theme.accentWarning,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Big Monospaced Countdown Timer
                Column {
                    Text(
                        text = unifiedStatus.countdownHms,
                        color = if (isOutage) theme.accentDanger else theme.accentSuccess,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = unifiedStatus.headline,
                        color = theme.textMuted,
                        fontSize = 12.sp
                    )
                }

                // Apple Live 6.5h Timeline Ruler Canvas
                AppleLiveTimelineRuler(
                    theme = theme,
                    intervals = intervals
                )

                // 24-Hour Overview Track
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "ДОБОВИЙ ОГЛЯД 24 ГОДИНИ",
                            color = theme.textMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "🟩 Світло є  🟥 Відключено",
                            color = theme.textMuted,
                            fontSize = 9.sp
                        )
                    }
                    TimelineGraphicBar(theme = theme, intervals = intervals, compact = true)
                }

                // Smart Charge Window Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.inputBg)
                        .border(0.5.dp, theme.accentPrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = unifiedStatus.chargeWindowStr,
                            color = theme.accentPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (unifiedStatus.chargeWindowAdvice.isNotEmpty()) {
                            Text(
                                text = unifiedStatus.chargeWindowAdvice,
                                color = theme.textPrimary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }

        // Cupertino Share Button
        Button(
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
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = theme.cardBg),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, theme.cardBorder),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📤", fontSize = 15.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Поділитися графіком (Telegram / Viber)",
                    color = theme.accentPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// APPLE LIVE 6.5-HOUR TIMELINE RULER (Compose Native Canvas)
// -----------------------------------------------------------------------------
@Composable
fun AppleLiveTimelineRuler(
    theme: ElectroCustomColors,
    intervals: List<ScheduleInterval>
) {
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
    ) {
        val totalWidthDp = maxWidth
        val totalWidthPx = with(density) { totalWidthDp.toPx() }

        val cal = Calendar.getInstance()
        val curMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        // 6.5 hours rolling window
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

            // 1. Draw Baseline Ruler Line
            drawLine(
                color = theme.cardBorder,
                start = Offset(0f, baselineY),
                end = Offset(canvasWidth, baselineY),
                strokeWidth = 1.dp.toPx()
            )

            // 2. Draw Outage Interval Amber Boxes
            for (inv in intervals) {
                val invStart = inv.startMinute.toFloat()
                val invEnd = inv.endMinute.toFloat()

                if (invEnd > windowStartMin && invStart < windowEndMin) {
                    val clampedStart = maxOf(invStart, windowStartMin)
                    val clampedEnd = minOf(invEnd, windowEndMin)

                    val x1 = minToX(clampedStart)
                    val x2 = minToX(clampedEnd)
                    val boxW = maxOf(x2 - x1, 4f)

                    // Draw amber fill
                    drawRoundRect(
                        color = Color(0x33FF9F0A),
                        topLeft = Offset(x1, boxTop),
                        size = Size(boxW, boxHeight),
                        cornerRadius = CornerRadius(boxRadius, boxRadius)
                    )

                    // Draw amber stroke
                    drawRoundRect(
                        color = Color(0xFFFF9F0A),
                        topLeft = Offset(x1, boxTop),
                        size = Size(boxW, boxHeight),
                        cornerRadius = CornerRadius(boxRadius, boxRadius),
                        style = Stroke(width = 1.2.dp.toPx())
                    )

                    // Draw text inside if wide enough
                    if (boxW > 45.dp.toPx()) {
                        val durationHours = (inv.endMinute - inv.startMinute) / 60.0
                        val text = "💡 %.1f год".format(Locale.US, durationHours).replace(".0", "")
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = with(density) { 9.sp.toPx() }
                            isFakeBoldText = true
                            isAntiAlias = true
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        val textY = boxTop + boxHeight / 2f + with(density) { 3.dp.toPx() }
                        drawContext.canvas.nativeCanvas.drawText(
                            text,
                            x1 + boxW / 2f,
                            textY,
                            paint
                        )
                    }
                }
            }

            // 3. Draw Hour and 30-min Ticks & Labels
            val startHour = (windowStartMin / 60).toInt()
            val endHour = (windowEndMin / 60).toInt() + 1

            for (h in startHour..endHour) {
                // Hour tick
                val hourMin = (h * 60).toFloat()
                if (hourMin in windowStartMin..windowEndMin) {
                    val tx = minToX(hourMin)
                    drawLine(
                        color = theme.textMuted,
                        start = Offset(tx, baselineY),
                        end = Offset(tx, baselineY + 6.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )

                    // Hour label text (09:00, 10:00...)
                    val label = String.format(Locale.US, "%02d:00", h % 24)
                    val textPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor(if (theme.isDark) "#8E8E93" else "#636366")
                        textSize = with(density) { 9.sp.toPx() }
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

                // 30-min half tick
                val halfMin = hourMin + 30f
                if (halfMin in windowStartMin..windowEndMin) {
                    val hx = minToX(halfMin)
                    drawLine(
                        color = theme.cardBorder,
                        start = Offset(hx, baselineY),
                        end = Offset(hx, baselineY + 3.5.dp.toPx()),
                        strokeWidth = 0.8.dp.toPx()
                    )
                }
            }

            // 4. Current Time Needle Indicator
            val needleX = minToX(curMinute.toFloat())
            if (needleX in 0f..canvasWidth) {
                // Red vertical cursor line
                drawLine(
                    color = Color(0xFFFF453A),
                    start = Offset(needleX, 0f),
                    end = Offset(needleX, canvasHeight - 12.dp.toPx()),
                    strokeWidth = 2.dp.toPx()
                )

                // Top Pin Circle
                drawCircle(
                    color = Color(0xFFFF453A),
                    radius = 3.5.dp.toPx(),
                    center = Offset(needleX, baselineY)
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 24-HOUR SEGMENTED TIMELINE BAR
// -----------------------------------------------------------------------------
@Composable
fun TimelineGraphicBar(
    theme: ElectroCustomColors,
    intervals: List<ScheduleInterval>,
    compact: Boolean = false
) {
    val now = Calendar.getInstance()
    val curHour = now.get(Calendar.HOUR_OF_DAY)
    val curMin = now.get(Calendar.MINUTE)
    val curMinute = curHour * 60 + curMin
    val curFraction = curMinute.toFloat() / 1440f
    val barHeight = if (compact) 12.dp else 24.dp

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            val totalWidth = maxWidth
            val needleX = totalWidth * curFraction

            // Green power-on track container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .clip(RoundedCornerShape(999.dp))
                    .background(theme.badgeSuccessBg)
                    .border(0.5.dp, theme.badgeSuccessBorder, RoundedCornerShape(999.dp))
            ) {
                // Outage red segments
                for (inv in intervals) {
                    val startRatio = inv.startMinute.toFloat() / 1440f
                    val endRatio = inv.endMinute.toFloat() / 1440f
                    val segWidth = (endRatio - startRatio) * totalWidth.value

                    Box(
                        modifier = Modifier
                            .offset(x = totalWidth * startRatio)
                            .width(segWidth.dp)
                            .fillMaxHeight()
                            .background(theme.badgeDangerBg)
                    )
                }
            }

            // Subtle vertical needle
            Box(
                modifier = Modifier
                    .offset(x = needleX - 1.dp)
                    .width(2.dp)
                    .height(barHeight)
                    .background(theme.accentPrimary)
            )
        }

        // Timeline hours (00, 06, 12, 18, 24)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("00:00", color = theme.textMuted, fontSize = 8.sp)
            Text("06:00", color = theme.textMuted, fontSize = 8.sp)
            Text("12:00", color = theme.textMuted, fontSize = 8.sp)
            Text("18:00", color = theme.textMuted, fontSize = 8.sp)
            Text("24:00", color = theme.textMuted, fontSize = 8.sp)
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
// TAB 1: MY ADDRESSES (Apple Inset Grouped)
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
        // Section Title
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "МОЇ ЗБЕРЕЖЕНІ АДРЕСИ (${favorites.size})",
                    color = theme.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Оберіть адресу для активації на головному екрані або скорегуйте номер черги.",
                    color = theme.textMuted,
                    fontSize = 12.sp
                )
            }
        }

        // Emergency GAV Banner if applicable
        if (schedule?.isEmergency == true) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(theme.badgeDangerBg)
                        .border(0.5.dp, theme.badgeDangerBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "🚨 УВАГА! ДІЮТЬ АВАРІЙНІ ВІДКЛЮЧЕННЯ (ГАВ). Відключення відбуваються поза розкладом.",
                        color = theme.badgeDangerText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // User's Saved Address Cards
        items(favorites) { fav ->
            val isCurrent = (fav.address == activeAddress || (fav.queue == activeQueue && fav.address.contains("Дудинськ")))
            val status = StatusResolver.resolve(fav.address, fav.queue, schedule, outages1562, currentSecondsTicker)
            val outageInvs = (schedule?.queues?.get(fav.queue) ?: emptyList()).distinctBy { it.startMinute to it.endMinute }.sortedBy { it.startMinute }
            val powerOnInvs = calculatePowerOnIntervals(outageInvs)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(theme.cardBg)
                    .border(
                        0.5.dp,
                        if (isCurrent) theme.accentPrimary else theme.cardBorder,
                        RoundedCornerShape(18.dp)
                    )
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Header row: Title + Queue Badge + Active Pill / Select
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text(
                                text = fav.title,
                                color = theme.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(theme.badgeNeutralBg)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Черга ${fav.queue}",
                                    color = theme.badgeNeutralText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (isCurrent) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(theme.badgeSuccessBg)
                                    .border(0.5.dp, theme.badgeSuccessBorder, RoundedCornerShape(999.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "✓ Активна",
                                    color = theme.badgeSuccessText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Button(
                                onClick = { onSelectActive(fav) },
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = theme.accentPrimary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Обрати", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Address line
                    Text(
                        text = "📍 ${fav.address}",
                        color = theme.textMuted,
                        fontSize = 13.sp
                    )

                    // Current Live Status Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (status.isOutage) theme.badgeDangerBg else theme.badgeSuccessBg)
                            .border(0.5.dp, if (status.isOutage) theme.badgeDangerBorder else theme.badgeSuccessBorder, RoundedCornerShape(12.dp))
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
                                    color = if (status.isOutage) theme.badgeDangerText else theme.badgeSuccessText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (status.isOutage) "Очікуване увімкнення о ${status.targetTimeStr}"
                                           else "Наступне відключення о ${status.targetTimeStr}",
                                    color = theme.textPrimary,
                                    fontSize = 11.sp
                                )
                            }

                            if (status.countdownHms.isNotBlank() && status.countdownHms != "00:00:00") {
                                Text(
                                    text = status.countdownHms,
                                    color = if (status.isOutage) theme.badgeDangerText else theme.badgeSuccessText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // 24h Mini Timeline
                    TimelineGraphicBar(theme = theme, intervals = outageInvs, compact = true)

                    // OFF vs ON pills
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // OFF
                        if (outageInvs.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (inv in outageInvs) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(theme.badgeDangerBg)
                                            .border(0.5.dp, theme.badgeDangerBorder, RoundedCornerShape(999.dp))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "🔴 ${inv.startStr} - ${inv.endStr}",
                                            color = theme.badgeDangerText,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        // ON
                        if (powerOnInvs.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (inv in powerOnInvs) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(theme.badgeSuccessBg)
                                            .border(0.5.dp, theme.badgeSuccessBorder, RoundedCornerShape(999.dp))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "🟢 ${inv.startStr} - ${inv.endStr}",
                                            color = theme.badgeSuccessText,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Actions
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
                            Text("Редагувати", fontSize = 11.sp)
                        }
                        if (favorites.size > 1) {
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { onDeleteAddress(fav.id) },
                                shape = RoundedCornerShape(999.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("🗑️", color = theme.accentDanger, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Add New Address Button
        item {
            Button(
                onClick = onAddAddress,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = theme.accentPrimary),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Text("➕ Додати нову адресу", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        // All 12 Queues Accordion
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(theme.cardBg)
                    .border(0.5.dp, theme.cardBorder, RoundedCornerShape(16.dp))
                    .clickable { showAllQueuesAccordion = !showAllQueuesAccordion }
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📋 Довідник усіх 12 черг міста",
                        color = theme.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (showAllQueuesAccordion) "▲" else "▼",
                        color = theme.accentPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
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

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.cardBg)
                        .border(
                            0.5.dp,
                            if (isCurrent) theme.accentPrimary else theme.cardBorder,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Черга $q",
                                    color = theme.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(if (isOffNow) theme.badgeDangerBg else theme.badgeSuccessBg)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (isOffNow) "🔴 Вимкнено" else "🟢 Світло є",
                                        color = if (isOffNow) theme.badgeDangerText else theme.badgeSuccessText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = "Години: $hoursStr",
                                color = theme.textMuted,
                                fontSize = 11.sp
                            )
                        }

                        Button(
                            onClick = { onSelectQueue(q) },
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.accentPrimary),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Обрати", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// TAB 2: STREET SEARCH & FAVORITES (Apple Cupertino)
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
        // Section Header
        Text(
            text = "ШВИДКИЙ ВИБІР З ОБРАНИХ",
            color = theme.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(favorites) { fav ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(theme.cardBg)
                        .border(0.5.dp, theme.cardBorder, RoundedCornerShape(999.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "⚡ ${fav.title} (Ч.${fav.queue})",
                            color = theme.accentPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                onSelectAddress(fav.address, fav.queue)
                            }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "✕",
                            color = theme.textMuted,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clickable { onDeleteFavorite(fav.id) }
                                .padding(2.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "ПОШУК ВУЛИЦІ (ХАРКІВ)",
            color = theme.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )

        // Apple Search Bar
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Наприклад: Дудинської, 9...", fontSize = 14.sp) },
            leadingIcon = { Text("🔍", fontSize = 16.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = theme.inputBg,
                unfocusedContainerColor = theme.inputBg,
                focusedBorderColor = theme.accentPrimary,
                unfocusedBorderColor = theme.inputBorder,
                focusedTextColor = theme.textPrimary,
                unfocusedTextColor = theme.textPrimary
            )
        )

        if (active1562Alert != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(theme.badgeDangerBg)
                    .border(0.5.dp, theme.badgeDangerBorder, RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "🚨 Аварійне знеструмлення АСДС 1562!",
                        color = theme.badgeDangerText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Відновлення планується: ${active1562Alert.plannedEndTime}",
                        color = theme.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Text(
            text = "Знайдені адреси (${results.size}):",
            color = theme.textMuted,
            fontSize = 11.sp
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(results) { street ->
                val fullAddr = if (bldTyped.isNotEmpty()) "${street.type} ${street.name}, буд. $bldTyped" else "${street.type} ${street.name}"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(theme.cardBg)
                        .border(0.5.dp, theme.cardBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = fullAddr,
                                color = theme.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "м. Харків",
                                color = theme.textMuted,
                                fontSize = 11.sp
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = {
                                    val q = KharkivData.detectQueueForAddress(fullAddr).ifEmpty { selectedQueue }
                                    onSelectAddress(fullAddr, q)
                                },
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = theme.accentPrimary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Обрати", fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = { showSaveDialogForAddr = fullAddr },
                                shape = RoundedCornerShape(999.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("⭐", fontSize = 11.sp)
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
            title = { Text("Зберегти адресу", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Назва адреси:", fontSize = 12.sp)
                    OutlinedTextField(
                        value = favTitle,
                        onValueChange = { favTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text("Адреса: $targetAddr", fontSize = 11.sp, color = theme.textMuted)
                    Text("Черга:", fontSize = 12.sp)
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
// TAB 3: EMERGENCIES 1562 (Apple Cupertino)
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
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Аварії АСДС «1562»",
                    color = theme.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (lastUpdated.isNotEmpty()) "Оновлено о $lastUpdated • Робіт: ${outages.size}" else "Оперативна диспетчерська",
                    color = theme.textMuted,
                    fontSize = 11.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/Kharkiv1562Bot"))
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.cardBg),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, theme.accentPrimary),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("🤖 Бот", color = theme.accentPrimary, fontSize = 11.sp)
                }

                Button(
                    onClick = onRefresh,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.cardBg),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, theme.cardBorder),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(if (isLoading) "⏳" else "🔄", color = theme.textPrimary, fontSize = 11.sp)
                }
            }
        }

        // Apple Segmented Control Pills
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
                label = { Text("📍 Моя адреса", fontSize = 11.sp) },
                shape = RoundedCornerShape(999.dp)
            )

            FilterChip(
                selected = isCityWideMode,
                onClick = { isCityWideMode = true },
                label = { Text("🏙️ Все місто (${basePool.size})", fontSize = 11.sp) },
                shape = RoundedCornerShape(999.dp)
            )

            FilterChip(
                selected = elecOnly,
                onClick = { elecOnly = !elecOnly },
                label = { Text(if (elecOnly) "⚡ Лише струм" else "🔧 Усі", fontSize = 11.sp) },
                shape = RoundedCornerShape(999.dp)
            )
        }

        // Search Input
        OutlinedTextField(
            value = filterText,
            onValueChange = {
                filterText = it
                isCityWideMode = false
            },
            placeholder = { Text("Введіть вулицю та будинок...", fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = theme.inputBg,
                unfocusedContainerColor = theme.inputBg,
                focusedBorderColor = theme.accentPrimary,
                unfocusedBorderColor = theme.inputBorder,
                focusedTextColor = theme.textPrimary,
                unfocusedTextColor = theme.textPrimary
            )
        )

        // Diagnostic Feedback Card
        if (hasFilter) {
            if (exactMatches.isNotEmpty()) {
                val first = exactMatches.first()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(theme.badgeDangerBg)
                        .border(0.5.dp, theme.badgeDangerBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "🚨 Будинок № $bldTyped ($streetTyped) — АВАРІЙНЕ ЗНЕСТРУМЛЕННЯ!",
                            color = theme.badgeDangerText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Орієнтовне відновлення: ${first.plannedEndTime}",
                            color = theme.textPrimary,
                            fontSize = 11.sp
                        )
                    }
                }
            } else if (streetMatches.isNotEmpty()) {
                val bldsStr = uniqueBlds.take(8).joinToString(", ")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(theme.badgeSuccessBg)
                        .border(0.5.dp, theme.badgeSuccessBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "🟢 Будинок № $bldTyped ($streetTyped): АВАРІЙ НЕМАЄ",
                            color = theme.badgeSuccessText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Роботи на вулиці стосуються тільки інших будинків: $bldsStr. Ваш будинок живиться за графіком.",
                            color = theme.textPrimary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (exactMatches.isNotEmpty()) {
                item {
                    Text(
                        text = "🚨 АВАРІЇ ЗА ВАШОЮ АДРЕСОЮ (${exactMatches.size}):",
                        color = theme.accentDanger,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
                items(exactMatches) { out ->
                    Outage1562AppleCard(out = out, theme = theme, bldHighlight = bldTyped, isUnaffected = false)
                }
            }

            if (streetMatches.isNotEmpty()) {
                item {
                    Text(
                        text = "⚠️ РОБОТИ НА ВУЛИЦІ (${streetMatches.size}):",
                        color = theme.accentWarning,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
                items(streetMatches) { out ->
                    Outage1562AppleCard(out = out, theme = theme, bldHighlight = bldTyped, isUnaffected = true)
                }
            }

            if (otherCityList.isNotEmpty()) {
                item {
                    Text(
                        text = "🏙️ ІНШІ РОБОТИ ПО МІСТУ (${otherCityList.size}):",
                        color = theme.textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                }
                items(otherCityList) { out ->
                    Outage1562AppleCard(out = out, theme = theme, bldHighlight = "", isUnaffected = false)
                }
            }

            if (basePool.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(theme.cardBg)
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✅ Відкритих аварій 1562 не зафіксовано",
                            color = theme.accentSuccess,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else if (isCityWideMode || !hasFilter) {
                items(basePool) { out ->
                    Outage1562AppleCard(out = out, theme = theme, bldHighlight = "", isUnaffected = false)
                }
            }
        }
    }
}

@Composable
fun Outage1562AppleCard(
    out: EmergencyOutage1562,
    theme: ElectroCustomColors,
    bldHighlight: String,
    isUnaffected: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(theme.cardBg)
            .border(0.5.dp, theme.cardBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Початок: ${out.startTime}",
                    color = theme.textMuted,
                    fontSize = 10.sp
                )
                Text(
                    text = "Відновлення: ${out.plannedEndTime}",
                    color = theme.accentDanger,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            val streetsStr = out.affectedStreets.entries.joinToString("; ") { (s, b) ->
                "$s (буд. ${b.joinToString(", ")})"
            }
            val prefix = if (isUnaffected && bldHighlight.isNotEmpty()) "ℹ️ [Буд. $bldHighlight не знеструмлено] " else ""
            Text(
                text = prefix + (streetsStr.ifEmpty { out.rawAnnouncement.take(120) }),
                color = theme.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = out.workType,
                color = theme.textMuted,
                fontSize = 11.sp
            )

            Text(
                text = "Служба: ${out.service}",
                color = theme.accentPrimary,
                fontSize = 10.sp
            )
        }
    }
}

// -----------------------------------------------------------------------------
// TAB 4: SETTINGS (Apple Inset Grouped Settings)
// -----------------------------------------------------------------------------
@Composable
fun SettingsTab(
    theme: ElectroCustomColors,
    context: Context,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
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
            color = theme.textPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        )

        // Group 1: General
        Text(
            text = "ЗАГАЛЬНІ",
            color = theme.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(theme.cardBg)
                .border(0.5.dp, theme.cardBorder, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(activeAddress, color = theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Підчерга: Черга $activeQueue", color = theme.textMuted, fontSize = 11.sp)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(theme.accentPrimary)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text("Черга $activeQueue", color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = onEditAddress,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.inputBg),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, theme.cardBorder)
                ) {
                    Text("✏️ Змінити адресу або чергу", color = theme.textPrimary, fontSize = 12.sp)
                }

                HorizontalDivider(color = theme.cardBorder, thickness = 0.5.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Темна тема (Cupertino Dark)", color = theme.textPrimary, fontSize = 13.sp)
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = { onToggleTheme() }
                    )
                }
            }
        }

        // Group 2: Notifications
        Text(
            text = "СПОДІВАННЯ ТА БУДИЛЬНИКИ",
            color = theme.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(theme.cardBg)
                .border(0.5.dp, theme.cardBorder, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Завчасні сповіщення про вимкнення", color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Попереджати до початку відключення", color = theme.textMuted, fontSize = 11.sp)
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
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (isSelected) theme.accentPrimary else theme.inputBg)
                                    .border(0.5.dp, if (isSelected) theme.accentPrimary else theme.cardBorder, RoundedCornerShape(999.dp))
                                    .clickable {
                                        minutesBefore = mins
                                        OutageNotificationScheduler.setMinutesBefore(context, mins)
                                        onRescheduleAlarms()
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$mins хв",
                                    color = if (isSelected) TextWhite else theme.textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💡 Сповіщати за 10 хв до увімкнення", color = theme.textPrimary, fontSize = 11.sp)
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

                Button(
                    onClick = {
                        OutageNotificationScheduler.sendTestNotification(context)
                        Toast.makeText(context, "🔔 Тестове сповіщення надіслано", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.inputBg),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, theme.cardBorder)
                ) {
                    Text("🔔 Надіслати тест", color = theme.textPrimary, fontSize = 11.sp)
                }
            }
        }

        // Group 3: GitHub Updates
        Text(
            text = "ОНОВЛЕННЯ ТА ВЕРСІЯ",
            color = theme.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(theme.cardBg)
                .border(0.5.dp, theme.cardBorder, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("GitHub Releases", color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Поточна: ${GitHubUpdater.CURRENT_VERSION}", color = theme.textMuted, fontSize = 11.sp)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(theme.badgeNeutralBg)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(GitHubUpdater.CURRENT_VERSION, color = theme.badgeNeutralText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (updateStatusMessage != null) {
                    Text(
                        text = updateStatusMessage ?: "",
                        color = if (updateInfo?.hasUpdate == true) theme.accentSuccess else theme.textMuted,
                        fontSize = 11.sp
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
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.accentPrimary)
                    ) {
                        Text("⬇️ Встановити ${curUpdate.latestVersion}", fontSize = 12.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
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
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.inputBg),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, theme.cardBorder)
                    ) {
                        Text(if (isCheckingUpdate) "⏳ Перевірка..." else "🔄 Перевірити оновлення", color = theme.textPrimary, fontSize = 11.sp)
                    }

                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/drkoh/ElectroBot/releases"))
                            context.startActivity(intent)
                        }
                    ) {
                        Text("GitHub ↗", color = theme.accentPrimary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// ADD / EDIT ADDRESS DIALOG (Apple Cupertino Action Sheet / Modal)
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
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Назва:", fontSize = 12.sp, color = theme.textMuted)
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("Вулиця та номер будинку:", fontSize = 12.sp, color = theme.textMuted)
                OutlinedTextField(
                    value = addrText,
                    onValueChange = { addrText = it },
                    placeholder = { Text("напр. вул. Дудинської, 9") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (autoDetected.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(theme.badgeSuccessBg)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "✨ Знайдено Чергу $autoDetected",
                            color = theme.badgeSuccessText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text("Підчерга відключень:", fontSize = 12.sp, color = theme.textMuted)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(allQueues) { q ->
                        val isSel = (q == queueText)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (isSel) theme.accentPrimary else theme.inputBg)
                                .border(0.5.dp, if (isSel) theme.accentPrimary else theme.cardBorder, RoundedCornerShape(999.dp))
                            .clickable { queueText = q }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = q,
                                color = if (isSel) TextWhite else theme.textPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
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
                        color = theme.textPrimary,
                        fontSize = 12.sp
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
                shape = RoundedCornerShape(999.dp)
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
