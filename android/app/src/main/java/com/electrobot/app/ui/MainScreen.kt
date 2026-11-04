package com.electrobot.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.electrobot.app.data.KharkivData
import com.electrobot.app.data.NetworkManager
import com.electrobot.app.data.StatusResolver
import com.electrobot.app.model.*
import com.electrobot.app.service.GitHubUpdater
import com.electrobot.app.service.UpdateInfo
import com.electrobot.app.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

@Composable
fun MainScreen(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val theme = ElectroTheme.colors
    val prefs = remember { context.getSharedPreferences("electrobot_prefs", Context.MODE_PRIVATE) }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Графік, 1: Всі черги, 2: Пошук, 3: Аварії 1562, 4: Опції

    var activeQueue by remember { mutableStateOf(prefs.getString("active_queue", "2.1") ?: "2.1") }
    var activeAddress by remember { mutableStateOf(prefs.getString("active_address", "вул. Дудинської, 9") ?: "вул. Дудинської, 9") }

    var schedule by remember { mutableStateOf<DailySchedule?>(null) }
    var outages1562 by remember { mutableStateOf<List<EmergencyOutage1562>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isRouterOnline by remember { mutableStateOf<Boolean?>(null) }

    // Dialog state for editing address
    var showEditAddressDialog by remember { mutableStateOf(false) }

    // Favorites in State
    fun loadFavorites(): List<FavoriteAddress> {
        val jsonStr = prefs.getString("favorites_json", null)
        if (jsonStr == null) {
            // Default favorites
            return listOf(
                FavoriteAddress("fav_1", "Мій дім", "вул. Дудинської, 9", "2.1"),
                FavoriteAddress("fav_2", "Офіс", "вул. Полтавський Шлях, 10", "1.2")
            )
        }
        val list = mutableListOf<FavoriteAddress>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    FavoriteAddress(
                        id = obj.optString("id", "fav_$i"),
                        title = obj.optString("title", "Адреса"),
                        address = obj.optString("address", ""),
                        queue = obj.optString("queue", "2.1")
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

    // Unified Status
    val unifiedStatus by remember(activeAddress, activeQueue, schedule, outages1562) {
        derivedStateOf {
            StatusResolver.resolve(activeAddress, activeQueue, schedule, outages1562)
        }
    }

    // Live second ticker for countdown
    var currentSecondsTicker by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentSecondsTicker = System.currentTimeMillis() / 1000
        }
    }

    // Initial load and periodic refresh
    fun refreshData() {
        coroutineScope.launch {
            isLoading = true
            val tgRes = NetworkManager.fetchTelegramSchedule()
            if (tgRes.isSuccess) {
                schedule = tgRes.getOrNull()
            }
            val s1562Res = NetworkManager.fetch1562Announcements()
            if (s1562Res.isSuccess) {
                outages1562 = s1562Res.getOrNull() ?: emptyList()
            }
            // Router ping check
            isRouterOnline = NetworkManager.pingGateway("192.168.1.1", 600)
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    Scaffold(
        containerColor = theme.canvas,
        bottomBar = {
            NavigationBar(
                containerColor = theme.headerBg,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Text("⚡", fontSize = 18.sp) },
                    label = { Text("Графік", fontSize = 11.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = theme.accentPrimary,
                        selectedTextColor = theme.accentPrimary,
                        unselectedIconColor = theme.textMuted,
                        unselectedTextColor = theme.textMuted,
                        indicatorColor = theme.activeTabBg
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Text("📋", fontSize = 18.sp) },
                    label = { Text("Всі черги", fontSize = 11.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = theme.accentPrimary,
                        selectedTextColor = theme.accentPrimary,
                        unselectedIconColor = theme.textMuted,
                        unselectedTextColor = theme.textMuted,
                        indicatorColor = theme.activeTabBg
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Text("🔍", fontSize = 18.sp) },
                    label = { Text("Пошук", fontSize = 11.sp, fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = theme.accentPrimary,
                        selectedTextColor = theme.accentPrimary,
                        unselectedIconColor = theme.textMuted,
                        unselectedTextColor = theme.textMuted,
                        indicatorColor = theme.activeTabBg
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Text("🚨", fontSize = 18.sp) },
                    label = { Text("1562", fontSize = 11.sp, fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = theme.accentDanger,
                        selectedTextColor = theme.accentDanger,
                        unselectedIconColor = theme.textMuted,
                        unselectedTextColor = theme.textMuted,
                        indicatorColor = theme.activeTabBg
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Text("⚙️", fontSize = 18.sp) },
                    label = { Text("Опції", fontSize = 11.sp, fontWeight = if (selectedTab == 4) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = theme.accentPrimary,
                        selectedTextColor = theme.accentPrimary,
                        unselectedIconColor = theme.textMuted,
                        unselectedTextColor = theme.textMuted,
                        indicatorColor = theme.activeTabBg
                    )
                )
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
                    activeQueue = activeQueue,
                    activeAddress = activeAddress,
                    unifiedStatus = unifiedStatus,
                    schedule = schedule,
                    isRouterOnline = isRouterOnline,
                    isLoading = isLoading,
                    onRefresh = { refreshData() },
                    onEditAddress = { showEditAddressDialog = true }
                )
                1 -> AllQueuesTab(
                    theme = theme,
                    activeQueue = activeQueue,
                    schedule = schedule,
                    onSelectQueue = { q ->
                        activeQueue = q
                        val newAddr = "Харків (Черга $q)"
                        activeAddress = newAddr
                        prefs.edit().putString("active_queue", q).putString("active_address", newAddr).apply()
                        Toast.makeText(context, "🎯 Обрано Чергу $q для моніторингу!", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(context, "🏠 Встановлено адресу: $addr (Черга $q)", Toast.LENGTH_SHORT).show()
                    },
                    onAddFavorite = { title, addr, q ->
                        val updated = favoritesList.toMutableList()
                        updated.add(FavoriteAddress("fav_${System.currentTimeMillis()}", title, addr, q))
                        favoritesList = updated
                        saveFavorites(updated)
                        Toast.makeText(context, "⭐ Адресу '$title' збережено в обрані!", Toast.LENGTH_SHORT).show()
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
                    }
                )
            }

            // Edit Address Dialog
            if (showEditAddressDialog) {
                EditAddressDialog(
                    theme = theme,
                    currentAddress = activeAddress,
                    currentQueue = activeQueue,
                    onDismiss = { showEditAddressDialog = false },
                    onConfirm = { newAddr, newQ ->
                        activeAddress = newAddr
                        activeQueue = newQ
                        prefs.edit().putString("active_address", newAddr).putString("active_queue", newQ).apply()
                        showEditAddressDialog = false
                        Toast.makeText(context, "✅ Адресу оновлено: $newAddr (Черга $newQ)", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// TAB 0: SCHEDULE & HERO
// -----------------------------------------------------------------------------
@Composable
fun ScheduleTab(
    theme: ElectroCustomColors,
    activeQueue: String,
    activeAddress: String,
    unifiedStatus: UnifiedStatus,
    schedule: DailySchedule?,
    isRouterOnline: Boolean?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onEditAddress: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚡", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ElectroBot Mobile",
                    color = theme.accentPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isRouterOnline != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isRouterOnline) theme.badgeSuccessBg else theme.badgeDangerBg)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isRouterOnline) "🟢 Роутер онлайн" else "🔴 Роутер офлайн",
                            color = if (isRouterOnline) theme.badgeSuccessText else theme.badgeDangerText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(36.dp)
                ) {
                    Text(if (isLoading) "⏳" else "🔄", fontSize = 16.sp)
                }
            }
        }

        // Active Location Pill (Clickable to edit!)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(theme.cardBg)
                .border(1.dp, theme.cardBorder, RoundedCornerShape(8.dp))
                .clickable { onEditAddress() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text("📍", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Моя активна адреса (натисніть для зміни):", color = theme.textMuted, fontSize = 10.sp)
                    Text(
                        text = activeAddress,
                        color = theme.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(theme.badgeNeutralBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Черга $activeQueue",
                        color = theme.badgeNeutralText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text("✏️", fontSize = 14.sp)
            }
        }

        // Hero Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = theme.cardBg),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                if (unifiedStatus.isOutage) theme.accentDanger else theme.cardBorder
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Status Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (unifiedStatus.isOutage) theme.badgeDangerBg else theme.badgeSuccessBg)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = unifiedStatus.badgeText,
                            color = if (unifiedStatus.isOutage) theme.badgeDangerText else theme.badgeSuccessText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = unifiedStatus.headline,
                        color = theme.textMuted,
                        fontSize = 12.sp
                    )
                }

                // Countdown Timer
                Column {
                    Text(
                        text = if (unifiedStatus.isOutage) "Орієнтовне увімкнення (${unifiedStatus.targetTimeStr}):" else "До відключення за графіком (${unifiedStatus.targetTimeStr}):",
                        color = theme.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = unifiedStatus.countdownHms,
                        color = if (unifiedStatus.isOutage) theme.badgeDangerText else theme.badgeNeutralText,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Details & Subtext
                Text(
                    text = unifiedStatus.details,
                    color = theme.textPrimary,
                    fontSize = 12.sp
                )
                if (unifiedStatus.subtext.isNotEmpty()) {
                    Text(
                        text = unifiedStatus.subtext,
                        color = theme.textMuted,
                        fontSize = 11.sp
                    )
                }

                // Battery Charge Window Helper
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.inputBg)
                        .border(1.dp, theme.accentPrimaryHover, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = unifiedStatus.chargeWindowStr,
                        color = theme.badgeNeutralText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 24-hour Timeline Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = theme.cardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, theme.cardBorder)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📊 Добовий графік (24 години)",
                        color = theme.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "🟩 Світло є  🟥 Відключено",
                        color = theme.textMuted,
                        fontSize = 10.sp
                    )
                }

                // Graphic Bar
                val intervals = schedule?.queues?.get(activeQueue) ?: emptyList()
                TimelineGraphicBar(theme = theme, intervals = intervals)
            }
        }
    }
}

// -----------------------------------------------------------------------------
// TIMELINE GRAPHIC BAR
// -----------------------------------------------------------------------------
@Composable
fun TimelineGraphicBar(
    theme: ElectroCustomColors,
    intervals: List<ScheduleInterval>
) {
    val now = Calendar.getInstance()
    val curMinute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    val curFraction = curMinute.toFloat() / 1440f

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(theme.badgeSuccessBg)
                .border(1.dp, theme.badgeSuccessBorder, RoundedCornerShape(6.dp))
        ) {
            val totalWidth = maxWidth

            // Red outage segments
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

            // Current Time Needle
            Box(
                modifier = Modifier
                    .offset(x = totalWidth * curFraction - 1.dp)
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF388BFD))
            )
        }

        // Timeline hours labels (00, 06, 12, 18, 24)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("00:00", color = theme.textMuted, fontSize = 9.sp)
            Text("06:00", color = theme.textMuted, fontSize = 9.sp)
            Text("12:00", color = theme.textMuted, fontSize = 9.sp)
            Text("18:00", color = theme.textMuted, fontSize = 9.sp)
            Text("24:00", color = theme.textMuted, fontSize = 9.sp)
        }
    }
}

// -----------------------------------------------------------------------------
// TAB 1: ALL QUEUES OVERVIEW (Всі черги)
// -----------------------------------------------------------------------------
@Composable
fun AllQueuesTab(
    theme: ElectroCustomColors,
    activeQueue: String,
    schedule: DailySchedule?,
    onSelectQueue: (String) -> Unit
) {
    val allQueues = listOf("1.1", "1.2", "2.1", "2.2", "3.1", "3.2", "4.1", "4.2", "5.1", "5.2", "6.1", "6.2")
    val now = Calendar.getInstance()
    val curMinute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Purpose Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = theme.cardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, theme.cardBorder)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "📋 Зведена таблиця черг Харкова (ГПВ)",
                    color = theme.accentPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "💡 Навіщо обирати чергу: натисніть «🏠 Зробити моєю», щоб ElectroBot вів персональний зворотний відлік на головному екрані та надсилав сповіщення саме для вашого будинку.",
                    color = theme.textMuted,
                    fontSize = 11.sp
                )
            }
        }

        // Emergency GAV Banner if applicable
        if (schedule?.isEmergency == true) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = theme.badgeDangerBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.badgeDangerBorder)
            ) {
                Text(
                    text = "🚨 УВАГА! ДІЮТЬ ГРАФІКИ АВАРІЙНИХ ВІДКЛЮЧЕНЬ (ГАВ). Відключення можуть відбуватися поза розкладом.",
                    color = theme.badgeDangerText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(allQueues) { q ->
                val intervals = schedule?.queues?.get(q) ?: emptyList()
                val isOffNow = intervals.any { curMinute in it.startMinute until it.endMinute }
                val isCurrent = (q == activeQueue)
                val hoursStr = if (intervals.isNotEmpty()) {
                    intervals.joinToString("; ") { "${it.startStr} - ${it.endStr}" }
                } else {
                    "Відключень не заплановано"
                }
                val totalOffMin = intervals.sumOf { it.endMinute - it.startMinute }
                val durStr = if (totalOffMin > 0) "${totalOffMin / 60}г ${totalOffMin % 60}хв" else "0 год"

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.cardBg),
                    border = androidx.compose.foundation.BorderStroke(
                        if (isCurrent) 1.5.dp else 1.dp,
                        if (isCurrent) theme.accentPrimary else theme.cardBorder
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Черга $q",
                                    color = theme.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isOffNow) theme.badgeDangerBg else theme.badgeSuccessBg)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (isOffNow) "🔴 Відключено" else "🟢 Світло є",
                                        color = if (isOffNow) theme.badgeDangerText else theme.badgeSuccessText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = "Години: $hoursStr",
                                color = theme.textPrimary,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "Без світла за добу: $durStr",
                                color = theme.textMuted,
                                fontSize = 10.sp
                            )
                        }

                        if (isCurrent) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(theme.badgeSuccessBg)
                                    .border(1.dp, theme.badgeSuccessBorder, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "✅ Моя черга",
                                    color = theme.badgeSuccessText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Button(
                                onClick = { onSelectQueue(q) },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = theme.accentPrimary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("🏠 Зробити моєю", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// TAB 2: STREET SEARCH & FAVORITES
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
    var selectedQueue by remember { mutableStateOf("2.1") }
    var showSaveDialogForAddr by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        val (st, _) = KharkivData.extractStreetAndBuilding(query)
        results = KharkivData.searchStreets(context, st.ifEmpty { query })
    }

    val (stTyped, bldTyped) = KharkivData.extractStreetAndBuilding(query)
    val active1562Alert = outages1562.firstOrNull { it.matchesAddress(stTyped, bldTyped) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Saved Favorites Section
        Text(
            text = "⭐ Мої збережені адреси:",
            color = theme.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(favorites) { fav ->
                Card(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)),
                    colors = CardDefaults.cardColors(containerColor = theme.cardBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, theme.cardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚡ ${fav.title} (Ч.${fav.queue})",
                            color = theme.accentPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                onSelectAddress(fav.address, fav.queue)
                            }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "✕",
                            color = theme.accentDanger,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clickable { onDeleteFavorite(fav.id) }
                                .padding(4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "🔍 Пошук вулиці та будинку (м. Харків):",
            color = theme.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Наприклад: Дудинської, 9...", fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = theme.inputBg,
                unfocusedContainerColor = theme.inputBg,
                focusedBorderColor = theme.accentPrimary,
                unfocusedBorderColor = theme.inputBorder,
                focusedTextColor = theme.textPrimary,
                unfocusedTextColor = theme.textPrimary
            )
        )

        // If 1562 Emergency alert found for this address
        if (active1562Alert != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = theme.badgeDangerBg),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, theme.badgeDangerBorder)
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                    Text(
                        text = active1562Alert.workType,
                        color = theme.textMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Text(
            text = "Знайдені вулиці (${results.size}):",
            color = theme.textMuted,
            fontSize = 11.sp
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(results) { street ->
                val fullAddr = if (bldTyped.isNotEmpty()) "${street.type} ${street.name}, буд. $bldTyped" else "${street.type} ${street.name}"
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.cardBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, theme.cardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
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
                                fontSize = 10.sp
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { onSelectAddress(fullAddr, selectedQueue) },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = theme.accentPrimary),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("🏠 Обрати", fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = { showSaveDialogForAddr = fullAddr },
                                shape = RoundedCornerShape(6.dp),
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

    // Save Favorite Dialog
    if (showSaveDialogForAddr != null) {
        val targetAddr = showSaveDialogForAddr!!
        var favTitle by remember { mutableStateOf("Мій дім") }
        var favQ by remember { mutableStateOf("2.1") }

        AlertDialog(
            onDismissRequest = { showSaveDialogForAddr = null },
            title = { Text("Зберегти адресу в обрані", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Назва (напр. Дім, Робота, Батьки):", fontSize = 12.sp)
                    OutlinedTextField(
                        value = favTitle,
                        onValueChange = { favTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text("Адреса: $targetAddr", fontSize = 11.sp, color = theme.textMuted)
                    Text("Черга відключень:", fontSize = 12.sp)
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
// TAB 3: EMERGENCIES 1562 (Диспетчерська АСДС 1562)
// -----------------------------------------------------------------------------
@Composable
fun Emergencies1562Tab(
    theme: ElectroCustomColors,
    context: Context,
    activeAddress: String,
    outages: List<EmergencyOutage1562>,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    var filterText by remember { mutableStateOf("Дудинської, 9") }
    var elecOnly by remember { mutableStateOf(true) }

    val (streetTyped, bldTyped) = KharkivData.extractStreetAndBuilding(filterText)
    val searchStem = (streetTyped.ifEmpty { filterText }).lowercase().replace("вул.", "").replace("вулиця", "").replace("просп.", "").replace("пр.", "").trim()
    val stem = if (searchStem.length >= 4) searchStem.substring(0, 4) else searchStem

    // Filter base pool
    val basePool = outages.filter {
        if (elecOnly) {
            val raw = it.rawAnnouncement.lowercase()
            raw.contains("електро") || raw.contains("струм") || raw.contains("обленерго") || raw.contains("джерел") || raw.contains("знеструм")
        } else true
    }

    // Match exact vs street
    val exactMatches = mutableListOf<EmergencyOutage1562>()
    val streetMatches = mutableListOf<EmergencyOutage1562>()
    val allAffectedBlds = mutableListOf<String>()

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

    val uniqueBlds = allAffectedBlds.distinct()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "🚨 Аварії АСДС «1562»",
                    color = theme.accentDanger,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Оперативна диспетчерська м. Харкова",
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
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.cardBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, theme.accentPrimary),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("🤖 Бот", color = theme.accentPrimary, fontSize = 11.sp)
                }

                Button(
                    onClick = onRefresh,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.cardBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, theme.cardBorder),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(if (isLoading) "⏳" else "🔄", color = theme.textPrimary, fontSize = 11.sp)
                }
            }
        }

        // Search Filter + Quick 'My address'
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = filterText,
                onValueChange = { filterText = it },
                placeholder = { Text("Введіть вулицю і будинок...", fontSize = 12.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = theme.inputBg,
                    unfocusedContainerColor = theme.inputBg,
                    focusedBorderColor = theme.accentPrimary,
                    unfocusedBorderColor = theme.inputBorder,
                    focusedTextColor = theme.textPrimary,
                    unfocusedTextColor = theme.textPrimary
                )
            )

            Button(
                onClick = { filterText = activeAddress.replace("Харків, ", "").trim() },
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = theme.cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.accentPrimary),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Text("📍 Моя", color = theme.accentPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Diagnostic Feedback Card (Material 3 Smart Banner)
        if (filterText.isNotBlank()) {
            if (bldTyped.isNotEmpty()) {
                if (exactMatches.isNotEmpty()) {
                    // Exact house outage
                    val first = exactMatches.first()
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.badgeDangerBg),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, theme.badgeDangerBorder)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = "🚨 УВАГА! Будинок № $bldTyped ($streetTyped) — АВАРІЙНЕ ЗНЕСТРУМЛЕННЯ!",
                                color = theme.badgeDangerText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "⏰ Орієнтовне відновлення: ${first.plannedEndTime}",
                                color = theme.textPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "⚡ Роботи: ${first.workType} (${first.service})",
                                color = theme.textMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                } else if (streetMatches.isNotEmpty()) {
                    // Street affected, but user's house is unaffected
                    val bldsStr = uniqueBlds.take(10).joinToString(", ")
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.badgeSuccessBg),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, theme.badgeSuccessBorder)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = "🟢 Будинок № $bldTyped ($streetTyped): АВАРІЙ 1562 НЕМАЄ",
                                color = theme.badgeSuccessText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "💡 Аварійні роботи на вашій вулиці стосуються тільки сусідніх будинків: $bldsStr. Електроенергія у вашому будинку № $bldTyped подається за штатним графіком Обленерго.",
                                color = theme.textPrimary,
                                fontSize = 11.sp
                            )
                        }
                    }
                } else {
                    // No 1562 outages
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.cardBg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, theme.cardBorder)
                    ) {
                        Text(
                            text = "ℹ️ За адресою «$filterText» аварійних знеструмлень 1562 не зафіксовано. Якщо світла немає — діє графік Обленерго (ГПВ) або аварійні графіки (ГАВ).",
                            color = theme.textMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }

        // Announcements List
        val displayList = if (filterText.isNotBlank()) {
            if (exactMatches.isNotEmpty()) exactMatches
            else if (streetMatches.isNotEmpty()) streetMatches
            else emptyList()
        } else basePool

        Text(
            text = "Аварійні повідомлення (${displayList.size}):",
            color = theme.textMuted,
            fontSize = 11.sp
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(displayList) { out ->
                val isUnaffected = (bldTyped.isNotEmpty() && !out.hasBuilding(bldTyped, out.affectedStreets.values.flatten()))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.cardBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, theme.cardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
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
                        val prefix = if (isUnaffected) "ℹ️ [Буд. $bldTyped не торкнулося] " else ""
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
                            text = "Виконавець: ${out.service}",
                            color = theme.badgeNeutralText,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// TAB 4: SETTINGS (Опції)
// -----------------------------------------------------------------------------
@Composable
fun SettingsTab(
    theme: ElectroCustomColors,
    context: Context,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    activeQueue: String,
    activeAddress: String,
    onQueueChange: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateStatusMessage by remember { mutableStateOf<String?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "⚙️ Налаштування додатку",
            color = theme.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        // GitHub Updates Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = theme.cardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, theme.cardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("🚀 Оновлення через GitHub", color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Поточна версія: ${GitHubUpdater.CURRENT_VERSION} (репозиторій drkoh/ElectroBot)", color = theme.textMuted, fontSize = 11.sp)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(theme.badgeNeutralBg)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(GitHubUpdater.CURRENT_VERSION, color = theme.badgeNeutralText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (updateStatusMessage != null) {
                    Text(
                        text = updateStatusMessage ?: "",
                        color = if (updateInfo?.hasUpdate == true) theme.badgeSuccessText else theme.textMuted,
                        fontSize = 11.sp
                    )
                }

                if (isDownloading) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Завантаження APK: $downloadProgress%", color = theme.textMuted, fontSize = 11.sp)
                    }
                }

                val curUpdate = updateInfo
                if (curUpdate != null && curUpdate.hasUpdate && !isDownloading) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = theme.cardBg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, theme.badgeSuccessText)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Знайдено оновлення: ${curUpdate.latestVersion}!", color = theme.badgeSuccessText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(curUpdate.releaseTitle, color = theme.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            if (curUpdate.releaseNotes.isNotBlank()) {
                                Text(curUpdate.releaseNotes, color = theme.textMuted, fontSize = 10.sp, maxLines = 4)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = {
                                    isDownloading = true
                                    downloadProgress = 0
                                    scope.launch {
                                        val res = GitHubUpdater.downloadAndInstallApk(
                                            context = context,
                                            downloadUrl = curUpdate.downloadUrl,
                                            fileName = curUpdate.apkFileName
                                        ) { progress ->
                                            downloadProgress = progress
                                        }
                                        isDownloading = false
                                        if (res.isFailure) {
                                            updateStatusMessage = "❌ Помилка завантаження: ${res.exceptionOrNull()?.localizedMessage}"
                                        } else {
                                            updateStatusMessage = "✅ Завантажено! Запуск інсталятора..."
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = theme.accentPrimary)
                            ) {
                                Text("⬇️ Завантажити та встановити ${curUpdate.latestVersion}", fontSize = 11.sp)
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            isCheckingUpdate = true
                            updateStatusMessage = "Перевірка GitHub Releases..."
                            scope.launch {
                                val res = GitHubUpdater.checkForUpdates()
                                isCheckingUpdate = false
                                if (res.isSuccess) {
                                    val info = res.getOrThrow()
                                    updateInfo = info
                                    if (info.hasUpdate) {
                                        updateStatusMessage = "Знайдено оновлення ${info.latestVersion}!"
                                    } else {
                                        updateStatusMessage = "Встановлено найновішу версію (${info.currentVersion})"
                                    }
                                } else {
                                    val err = res.exceptionOrNull()?.localizedMessage ?: "Помилка зв'язку"
                                    updateStatusMessage = "Помилка: $err"
                                }
                            }
                        },
                        enabled = !isCheckingUpdate && !isDownloading,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.cardBg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, theme.cardBorder)
                    ) {
                        Text(
                            if (isCheckingUpdate) "⏳ Перевірка..." else "🔄 Перевірити оновлення",
                            color = theme.textPrimary,
                            fontSize = 11.sp
                        )
                    }

                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/drkoh/ElectroBot/releases"))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                    ) {
                        Text("Відкрити GitHub ↗", color = theme.accentPrimary, fontSize = 11.sp)
                    }
                }
            }
        }

        // Theme Switch Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = theme.cardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, theme.cardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Тема оформлення:", color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(if (isDarkTheme) "🌙 Темна тема (Pro Slate)" else "☀️ Світла тема (Office Light)", color = theme.textMuted, fontSize = 11.sp)
                }

                Switch(
                    checked = isDarkTheme,
                    onCheckedChange = { onToggleTheme() }
                )
            }
        }

        // Notification Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = theme.cardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, theme.cardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🔔 Сповіщення про відключення:", color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "ElectroBot надішле пуш-сповіщення за 15 та 30 хвилин до планового відключення живлення.",
                    color = theme.textMuted,
                    fontSize = 11.sp
                )
                Button(
                    onClick = {
                        Toast.makeText(context, "🔔 Тестове сповіщення: через 15 хв відключення Черги $activeQueue!", Toast.LENGTH_LONG).show()
                    },
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accentPrimary)
                ) {
                    Text("Перевірити сповіщення", fontSize = 11.sp)
                }
            }
        }

        // Version & Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = theme.cardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, theme.cardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ℹ️ Про додаток ElectroBot Mobile v1.1", color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("• Офіційні графіки АТ «Харківобленерго»", color = theme.textMuted, fontSize = 11.sp)
                Text("• Оперативні аварійні роботи АСДС «1562»", color = theme.textMuted, fontSize = 11.sp)
                Text("• Перевірка роутера 192.168.1.1 онлайн/офлайн", color = theme.textMuted, fontSize = 11.sp)
                Text("• Працює безперебійно навіть під час знеструмлень", color = theme.textMuted, fontSize = 11.sp)
            }
        }
    }
}

// -----------------------------------------------------------------------------
// EDIT ADDRESS DIALOG
// -----------------------------------------------------------------------------
@Composable
fun EditAddressDialog(
    theme: ElectroCustomColors,
    currentAddress: String,
    currentQueue: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var addrText by remember { mutableStateOf(currentAddress) }
    var queueText by remember { mutableStateOf(currentQueue) }
    val allQueues = listOf("1.1", "1.2", "2.1", "2.2", "3.1", "3.2", "4.1", "4.2", "5.1", "5.2", "6.1", "6.2")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Редагувати адресу та чергу", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Адреса (вулиця та номер будинку):", fontSize = 12.sp)
                OutlinedTextField(
                    value = addrText,
                    onValueChange = { addrText = it },
                    placeholder = { Text("напр. вул. Дудинської, 9") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("Підчерга відключень:", fontSize = 12.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(allQueues) { q ->
                        val isSel = (q == queueText)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) theme.accentPrimary else theme.cardBg)
                                .border(1.dp, if (isSel) theme.accentPrimary else theme.cardBorder, RoundedCornerShape(6.dp))
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (addrText.isNotBlank()) {
                        onConfirm(addrText.trim(), queueText)
                    }
                }
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
