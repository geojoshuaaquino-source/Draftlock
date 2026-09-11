package com.draftlock.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.draftlock.app.ui.theme.DraftLockColors
import com.draftlock.app.ui.theme.DraftLockTheme
import com.draftlock.app.ui.theme.GlassTokens
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.draftlock.app.data.AppRequirement
import com.draftlock.app.data.DraftLockDatabase
import com.draftlock.app.data.LockedApp
import com.draftlock.app.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Screen(val label: String, val iconRes: Int) {
    HOME("Home", R.drawable.ic_home),
    WRITE("Write", R.drawable.ic_write),
    APPS("Apps", R.drawable.ic_usage),
    DOCS("Docs", R.drawable.ic_docs),
    SETTINGS("Settings", R.drawable.ic_analytics)
}

class MainActivity : ComponentActivity() {
    private lateinit var oauthManager: GoogleOAuthManager
    private var draftLockVm: DraftLockViewModel? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread.setDefaultUncaughtExceptionHandler { _, e -> android.util.Log.e("DraftLock", "Uncaught", e) }
        try { WindowCompat.setDecorFitsSystemWindows(window, false) } catch (_: Exception) {}
        oauthManager = GoogleOAuthManager(this)
        handleOAuthIntent(intent)
        setContent {
            val vm: DraftLockViewModel = viewModel()
            draftLockVm = vm
            DraftLockApp(vm)
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthIntent(intent)
    }
    private fun handleOAuthIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.data?.toString()?.contains("/oauth2redirect") == true || intent.hasExtra("net.openid.appauth.AuthorizationResponse")) {
            oauthManager.handleResult(intent) { ok, msg ->
                val vm = draftLockVm
                if (ok) {
                    vm?.checkGoogleConnection()
                    vm?.saveGoogleStatus("Gmail linked ✓ Fetching chapters…")
                    vm?.fetchDriveFiles()
                } else {
                    vm?.saveGoogleStatus(msg)
                }
            }
        }
    }
}

class DraftLockViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val db = DraftLockDatabase.get(application)
    private val store = SettingsStore(application)
    private val usage = UsageTracker(application)
    private val blocker = AppBlocker(application)
    private val docsRepo = GoogleDocsRepository()

    val requirements = db.dao().observeRequirements().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val lockedApps = db.dao().observeLockedApps().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val localDocs = db.dao().observeLocalDocs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val text = store.documentText.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val quota = store.quota.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1000)
    val resetMinutes = store.resetMinutes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val logic = store.logic.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "AND")
    val todayWords = store.todayWords.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val documentName = store.documentName.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Today's Draft")
    val googleFolderId = store.googleFolderId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val googleDocumentId = store.googleDocumentId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val googleAutoSave = store.googleAutoSave.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val overrideUntil = store.overrideUntil.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    var selectedLocalDocId by mutableStateOf<Long?>(null)

    var usageMinutes by mutableStateOf<Map<String, Int>>(emptyMap())
    var usageAccess by mutableStateOf(usage.hasUsageAccess())
    var blockingAvailable by mutableStateOf(blocker.canSuspendApps())
    var blockingDiagnostics by mutableStateOf(blocker.diagnostics())
    var syncStatus by mutableStateOf("Local only")
    var driveFiles by mutableStateOf<List<RemoteFile>>(emptyList())
    var driveQuery by mutableStateOf("DND")
    var isSyncing by mutableStateOf(false)
    var isGoogleConnected by mutableStateOf(try { GoogleOAuthManager(getApplication()).isConnected() } catch (_: Exception) { false })
    private var lastTextWordCount = 0

    init {
        viewModelScope.launch {
            val dayKey = UsageTracker.periodStartMillis(resetMinutes.value).toString()
            val todayKey = store.todayKey.stateIn(viewModelScope, SharingStarted.Eagerly, "").value
            if (todayKey != dayKey) store.setTodayWords(0, dayKey)
        }
        refreshUsage()
    }

    fun refreshUsage() {
        usageAccess = usage.hasUsageAccess()
        blockingAvailable = blocker.canSuspendApps()
        blockingDiagnostics = blocker.diagnostics()
        if (!usageAccess) return
        viewModelScope.launch {
            val start = UsageTracker.periodStartMillis(resetMinutes.value)
            usageMinutes = requirements.value.associate { it.packageName to usage.minutesForPackage(it.packageName, start) }
            applyBlocking()
        }
    }

    fun onTextChanged(value: String) {
        val words = countWords(value)
        val delta = words - lastTextWordCount
        lastTextWordCount = words
        viewModelScope.launch {
            store.setDocumentText(value)
            if (delta != 0) {
                val current = todayWords.value
                store.setTodayWords(current + delta, UsageTracker.periodStartMillis(resetMinutes.value).toString())
            }
        }
    }

    fun setQuota(value: Int) = viewModelScope.launch { store.setQuota(value); applyBlocking() }
    fun setResetMinutes(value: Int) = viewModelScope.launch { store.setResetMinutes(value); store.setTodayWords(0, UsageTracker.periodStartMillis(value).toString()); refreshUsage() }
    fun setLogic(value: String) = viewModelScope.launch { store.setLogic(value); applyBlocking() }
    fun setDocumentName(value: String) = viewModelScope.launch { store.setDocumentName(value) }
    fun setGoogleFolder(value: String) = viewModelScope.launch { store.setGoogleFolderId(value) }
    fun setGoogleDocument(value: String) = viewModelScope.launch { store.setGoogleDocumentId(value) }
    fun setGoogleAutoSave(value: Boolean) = viewModelScope.launch { store.setGoogleAutoSave(value) }
    fun addRequirement(req: AppRequirement) = viewModelScope.launch { db.dao().upsertRequirement(req); refreshUsage() }
    fun deleteRequirement(id: Long) = viewModelScope.launch { db.dao().deleteRequirement(id); refreshUsage() }
    fun updateRequirementMinutes(id: Long, minutes: Int) = viewModelScope.launch {
        val existing = requirements.value.find { it.id == id } ?: return@launch
        db.dao().upsertRequirement(existing.copy(requiredMinutes = minutes.coerceIn(5, 480)))
        refreshUsage()
    }
    fun addLockedApp(app: LockedApp) = viewModelScope.launch { db.dao().upsertLockedApp(app); applyBlocking() }
    fun deleteLockedApp(pkg: String) = viewModelScope.launch { db.dao().deleteLockedApp(pkg); blocker.unsuspend(listOf(pkg)) }
    fun isRequirement(pkg: String): AppRequirement? = requirements.value.find { it.packageName == pkg }
    fun isLocked(pkg: String): LockedApp? = lockedApps.value.find { it.packageName == pkg }
    fun createLocalDoc(title: String) = viewModelScope.launch {
        val doc = com.draftlock.app.data.LocalDocument(title = title.ifBlank { "Untitled Quest" }, content = "", wordCount = 0, updatedAt = System.currentTimeMillis())
        val id = db.dao().upsertLocalDoc(doc)
        selectedLocalDocId = id
        saveGoogleStatus("Forged local quest: ${doc.title}")
    }
    fun updateLocalDocContent(id: Long, newContent: String) = viewModelScope.launch {
        val existing = db.dao().getLocalDoc(id) ?: return@launch
        val newCount = newContent.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        val delta = newCount - existing.wordCount
        db.dao().upsertLocalDoc(existing.copy(content = newContent, wordCount = newCount, updatedAt = System.currentTimeMillis()))
        if (delta != 0) store.setTodayWords((todayWords.value + delta).coerceAtLeast(0), UsageTracker.periodStartMillis(resetMinutes.value).toString())
    }
    fun deleteLocalDoc(id: Long) = viewModelScope.launch { db.dao().deleteLocalDoc(id); if (selectedLocalDocId == id) selectedLocalDocId = null }
    fun renameLocalDoc(id: Long, newTitle: String) = viewModelScope.launch {
        val existing = db.dao().getLocalDoc(id) ?: return@launch
        db.dao().upsertLocalDoc(existing.copy(title = newTitle, updatedAt = System.currentTimeMillis()))
    }

    private fun loadGoogleState() = try { GoogleOAuthManager(getApplication()).loadState() } catch (_: Exception) { null }
    var isGoogleConfigured by mutableStateOf(GoogleOAuthManager(getApplication()).isConfigured)
    fun checkGoogleConnection() {
        val mgr = GoogleOAuthManager(getApplication())
        isGoogleConfigured = mgr.isConfigured
        isGoogleConnected = mgr.isConnected()
        syncStatus = when {
            isGoogleConnected -> "Gmail linked • Drive ready"
            !isGoogleConfigured -> "Connect Gmail — add Client ID in Settings"
            else -> "Tap Connect Gmail to link Drive"
        }
    }
    fun startGoogleAuth(context: Context) {
        val mgr = GoogleOAuthManager(context)
        if (!mgr.isConfigured) { saveGoogleStatus("No Client ID — add GOOGLE_CLIENT_ID in local.properties or Settings → Gmail"); return }
        mgr.startAuthorization { err -> saveGoogleStatus(err) }
        saveGoogleStatus("Opening Google sign-in…")
    }
    fun fetchDriveFiles(query: String = driveQuery) {
        val manager = GoogleOAuthManager(getApplication())
        if (!manager.isConfigured) { saveGoogleStatus("Google not configured — local vault active"); return }
        if (manager.loadState() == null) { saveGoogleStatus("Connect Google first (or use local)"); return }
        isSyncing = true; saveGoogleStatus("Searching Drive…")
        manager.withFreshToken(onToken = { token ->
            if (token == null) { isSyncing = false; saveGoogleStatus("Token failed"); return@withFreshToken }
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val files = docsRepo.findFiles(token, query.ifBlank { "DND" })
                    withContext(Dispatchers.Main) { driveFiles = files; saveGoogleStatus("Found ${files.size} docs"); isSyncing = false }
                } catch (e: Exception) { withContext(Dispatchers.Main) { saveGoogleStatus("Drive error: ${e.message}"); isSyncing = false } }
            }
        }, onError = { isSyncing = false; saveGoogleStatus(it) })
    }
    fun createGoogleDoc(name: String, onCreated: (String) -> Unit = {}) {
        val manager = GoogleOAuthManager(getApplication())
        if (!manager.isConfigured) { saveGoogleStatus("Enable Google first — or use local quest"); return }
        if (manager.loadState() == null) { saveGoogleStatus("Connect Google first"); return }
        isSyncing = true; saveGoogleStatus("Creating \"$name\"…")
        manager.withFreshToken(onToken = { token ->
            if (token == null) { isSyncing = false; saveGoogleStatus("Auth error"); return@withFreshToken }
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val id = docsRepo.createDocument(token, name, googleFolderId.value.ifBlank { null })
                    withContext(Dispatchers.Main) { saveGoogleStatus("Created: $name"); setGoogleDocument(id); fetchDriveFiles(); onCreated(id); isSyncing = false }
                } catch (e: Exception) { withContext(Dispatchers.Main) { saveGoogleStatus("Create failed: ${e.message}"); isSyncing = false } }
            }
        }, onError = { isSyncing = false; saveGoogleStatus(it) })
    }
    fun syncTextToDoc(docId: String = googleDocumentId.value) {
        val doc = docId.ifBlank { saveGoogleStatus("Select a scroll first"); return }
        val manager = GoogleOAuthManager(getApplication())
        if (!manager.isConfigured) { saveGoogleStatus("Google not configured — local save only"); return }
        if (manager.loadState() == null) { saveGoogleStatus("Connect Google first"); return }
        isSyncing = true; saveGoogleStatus("Syncing…")
        manager.withFreshToken(onToken = { token ->
            if (token == null) { isSyncing = false; saveGoogleStatus("Auth error"); return@withFreshToken }
            viewModelScope.launch(Dispatchers.IO) {
                try { docsRepo.replaceDocument(token, doc, text.value); withContext(Dispatchers.Main) { saveGoogleStatus("Synced ${countWords(text.value)} words at ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}"); isSyncing = false } }
                catch (e: Exception) { withContext(Dispatchers.Main) { saveGoogleStatus("Sync failed: ${e.message}"); isSyncing = false } }
            }
        }, onError = { isSyncing = false; saveGoogleStatus(it) })
    }

    fun activateEmergencyOverride() = viewModelScope.launch {
        store.setOverride(System.currentTimeMillis() + 15 * 60_000L)
        blocker.unsuspend(lockedApps.value.map { it.packageName })
    }

    fun allConditionsComplete(): Boolean {
        val writing = todayWords.value >= quota.value
        val app = requirements.value.filter { it.enabled }.map { (usageMinutes[it.packageName] ?: 0) >= it.requiredMinutes }
        if (app.isEmpty()) return writing
        return if (logic.value == "OR") writing || app.any { it } else writing && app.all { it }
    }

    fun applyBlocking() {
        viewModelScope.launch {
            val shouldUnlock = allConditionsComplete() || overrideUntil.value > System.currentTimeMillis()
            val packages = lockedApps.value.filter { it.enabled }.map { it.packageName }
            if (shouldUnlock) blocker.unsuspend(packages) else blocker.suspend(packages)
        }
    }

    fun saveGoogleStatus(status: String) { syncStatus = status }

    companion object {
        private fun countWords(s: String): Int = s.trim().split(Regex("\\s+")).count { it.isNotBlank() }.coerceAtMost(1_000_000)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
fun DraftLockApp(vm: DraftLockViewModel) {
    val requirements by vm.requirements.collectAsStateWithLifecycle()
    val lockedApps by vm.lockedApps.collectAsStateWithLifecycle()
    val text by vm.text.collectAsStateWithLifecycle()
    val quota by vm.quota.collectAsStateWithLifecycle()
    val resetMinutes by vm.resetMinutes.collectAsStateWithLifecycle()
    val logic by vm.logic.collectAsStateWithLifecycle()
    val todayWords by vm.todayWords.collectAsStateWithLifecycle()
    val documentName by vm.documentName.collectAsStateWithLifecycle()
    val googleAutoSave by vm.googleAutoSave.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var screen by remember { mutableStateOf(Screen.HOME) }
    var showOverride by remember { mutableStateOf(false) }

    LaunchedEffect(requirements) { vm.refreshUsage() }
    LaunchedEffect(Unit) { vm.checkGoogleConnection(); if (vm.isGoogleConnected) vm.fetchDriveFiles(); while (true) { delay(30_000); vm.refreshUsage(); vm.checkGoogleConnection() } }
    LaunchedEffect(vm.isGoogleConnected) { if (vm.isGoogleConnected) vm.fetchDriveFiles() }

    DraftLockTheme {
        Box(Modifier.fillMaxSize().background(DraftLockColors.bg)) {
            // Dark base + single vault glow (glass will provide depth, not busy pattern)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0A0A0F), Color(0xFF12121A)))), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x10D4FF32), Color.Transparent), center = androidx.compose.ui.geometry.Offset(280f, 90f), radius = 900f)))
                Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x0A00CD3C), Color.Transparent), center = androidx.compose.ui.geometry.Offset(120f, 700f), radius = 600f)))
            }
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    // Dark glass top — not opaque, translucent with border (phone glass)
                    Surface(color = Color(0xE60F0F14), tonalElevation = 0.dp, shadowElevation = 0.dp, modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                        Column {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(DraftLockColors.accent))
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(DraftLockColors.glass).padding(1.dp).background(DraftLockColors.panelElevated, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                    Image(painter = painterResource(R.drawable.ic_logo_draftlock), null, modifier = Modifier.size(24.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("DRAFTLOCK", style = MaterialTheme.typography.labelMedium, color = Color.White, letterSpacing = 1.2.sp, fontWeight = FontWeight.Black)
                                    Text("Ink Vault • ${if (vm.isGoogleConnected) "Gmail linked" else "Glass bento"}", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted)
                                }
                                Box(Modifier.clip(RoundedCornerShape(20.dp)).background(if ((todayWords/500)+1 >= 3) DraftLockColors.melonGreen else DraftLockColors.accent).padding(horizontal = 11.dp, vertical = 5.dp), contentAlignment = Alignment.Center) {
                                    Text("LVL ${(todayWords/500)+1}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.Black)
                                }
                            }
                            Divider(color = Color(0x1AFFFFFF), thickness = 1.dp)
                        }
                    }
                },
                bottomBar = {
                    // Ink Vault nav — single stylized pill, Write centered & elevated inside (no external FAB)
                    Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color(0xF514141C)), elevation = CardDefaults.cardElevation(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Box(Modifier.padding(horizontal = 6.dp, vertical = 8.dp).background(Color(0x14FFFFFF), RoundedCornerShape(28.dp)).padding(4.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                                    val left = listOf(Screen.HOME, Screen.APPS)
                                    val right = listOf(Screen.DOCS, Screen.SETTINGS)
                                    left.forEach { item ->
                                        val selected = screen == item
                                        Box(Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(16.dp)).background(if (selected) GlassTokens.glassStrong else Color.Transparent).clickable { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); screen = item }, contentAlignment = Alignment.Center) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Icon(painterResource(item.iconRes), null, tint = if (selected) DraftLockColors.accent else DraftLockColors.muted, modifier = Modifier.size(18.dp))
                                                Text(item.label, style = MaterialTheme.typography.labelSmall, color = if (selected) Color.White else DraftLockColors.muted, fontWeight = if (selected) FontWeight.Black else FontWeight.Medium, fontSize = 9.sp, letterSpacing = 0.5.sp)
                                            }
                                        }
                                    }
                                    // Center Write — special, elevated, lime vault
                                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                        Box(Modifier.size(56.dp).clip(CircleShape).background(Brush.linearGradient(listOf(DraftLockColors.accent, DraftLockColors.melonGreen))).clickable { haptics.performHapticFeedback(HapticFeedbackType.LongPress); screen = Screen.WRITE }.padding(1.dp).background(Color(0x1A000000), CircleShape), contentAlignment = Alignment.Center) {
                                            Icon(painterResource(R.drawable.ic_write), null, tint = Color.Black, modifier = Modifier.size(24.dp))
                                        }
                                    }
                                    right.forEach { item ->
                                        val selected = screen == item
                                        Box(Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(16.dp)).background(if (selected) GlassTokens.glassStrong else Color.Transparent).clickable { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); screen = item }, contentAlignment = Alignment.Center) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Icon(painterResource(item.iconRes), null, tint = if (selected) DraftLockColors.accent else DraftLockColors.muted, modifier = Modifier.size(18.dp))
                                                Text(item.label, style = MaterialTheme.typography.labelSmall, color = if (selected) Color.White else DraftLockColors.muted, fontWeight = if (selected) FontWeight.Black else FontWeight.Medium, fontSize = 9.sp, letterSpacing = 0.5.sp)
                                            }
                                        }
                                    }
                                }
                            }
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x1AFFFFFF)))
                        }
                    }
                }
            ) { pad ->
                // Scaffold pad already includes topBar + bottomBar; navigationBars handled in bars themselves, so content not blocked
                Box(Modifier.fillMaxSize().padding(pad)) {
                    AnimatedContent(
                        targetState = screen,
                        transitionSpec = {
                            // Purposeful motion: single slide+fade, not scattered (frontend-design restraint)
                            (slideInHorizontally(tween(220, easing = EaseOutCubic)) { it / 8 } + fadeIn(tween(180)))
                                .togetherWith(slideOutHorizontally(tween(180, easing = EaseInCubic)) { -it / 8 } + fadeOut(tween(120)))
                        },
                        label = "screenTransition"
                    ) { target ->
                        when (target) {
                            Screen.HOME -> HomeScreen(vm, todayWords, quota, requirements, lockedApps, logic, context, { screen = Screen.WRITE }, { showOverride = true })
                            Screen.WRITE -> WriteScreen(vm, text, todayWords, quota, documentName)
                            Screen.APPS -> UnifiedAppsScreen(vm, context)
                            Screen.DOCS -> DocsScreen(vm)
                            Screen.SETTINGS -> SettingsScreen(vm, quota, resetMinutes, logic, googleAutoSave) { showOverride = true }
                        }
                    }
                }
            }
        }
    }

    if (showOverride) AlertDialog(
        onDismissRequest = { showOverride = false },
        title = { Text("Emergency override") },
        text = { Text("DraftLock will unlock blocked apps for 15 minutes. This is recorded in local history. Continue?") },
        confirmButton = { TextButton(onClick = { vm.activateEmergencyOverride(); showOverride = false }) { Text("Unlock for 15 min") } },
        dismissButton = { TextButton(onClick = { showOverride = false }) { Text("Cancel") } }
    )
}

@androidx.compose.runtime.Composable
private fun HomeScreen(vm: DraftLockViewModel, words: Int, quota: Int, requirements: List<AppRequirement>, lockedApps: List<LockedApp>, logic: String, context: Context, onWrite: () -> Unit, onOverride: () -> Unit) {
    val complete = vm.allConditionsComplete()
    val progress = (words.toFloat() / quota.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val isUnlocked = words >= quota && (requirements.filter { it.enabled }.isEmpty() || logic == "OR" && requirements.filter { it.enabled }.any { (vm.usageMinutes[it.packageName] ?: 0) >= it.requiredMinutes } || logic == "AND" && requirements.filter { it.enabled }.all { (vm.usageMinutes[it.packageName] ?: 0) >= it.requiredMinutes })

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            // VAULT STATUS HUD — single hero card, no illustration, pure info density
            VaultStatusCard(words = words, quota = quota, progress = progress, isUnlocked = isUnlocked, onWrite = onWrite)
        }
        item {
            // GRID: 2x2 dense bento — Gmail | Logic / Req | Blocked
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Gmail — wide, actionable
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = GlassTokens.glass), elevation = CardDefaults.cardElevation(0.dp), modifier = Modifier.weight(1.6f)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(if (vm.isGoogleConnected) DraftLockColors.accent else Color(0xFF1A1A1E)), contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_google), null, tint = if (vm.isGoogleConnected) Color.Black else Color.White, modifier = Modifier.size(20.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(if (vm.isGoogleConnected) "GMAIL ✓" else "CONNECT GMAIL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = if(vm.isGoogleConnected) DraftLockColors.accent else Color.White, letterSpacing = 0.8.sp)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (vm.isSyncing) CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp, color = DraftLockColors.accent)
                                Text(if(vm.isSyncing) "Fetching chapters…" else if(vm.isGoogleConnected && vm.driveFiles.isNotEmpty()) "${vm.driveFiles.size} chapters • ${vm.syncStatus.take(18)}" else if(vm.isGoogleConnected) vm.syncStatus else "Baked m00s0… tap to link", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (vm.isGoogleConnected)
                            TextButton(onClick = { GoogleOAuthManager(context).disconnect(); vm.checkGoogleConnection(); vm.saveGoogleStatus("Gmail unlinked") }, contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)) { Text("Unlink", fontSize = 11.sp, color = DraftLockColors.muted) }
                        else
                            Button(onClick = { vm.startGoogleAuth(context) }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = DraftLockColors.accent, contentColor = Color.Black), shape = RoundedCornerShape(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)) { Text("Link", fontSize = 11.sp, fontWeight = FontWeight.Black) }
                    }
                }
                // Logic — compact, shows current mode
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = GlassTokens.glass), elevation = CardDefaults.cardElevation(0.dp), modifier = Modifier.weight(0.9f)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(DraftLockColors.melonGreen.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_melon_accent), null, tint = DraftLockColors.melonGreen, modifier = Modifier.size(16.dp))
                        }
                        Text(logic, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
                        Text("UNLOCK MODE", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted, letterSpacing = 0.6.sp, fontSize = 9.sp)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MicroStatCard("REQUIREMENTS", "${requirements.size}", DraftLockColors.neonCyan, R.drawable.ic_rules, Modifier.weight(1f))
                MicroStatCard("BLOCKED APPS", "${lockedApps.size}", DraftLockColors.neonPink, R.drawable.ic_lock_closed, Modifier.weight(1f))
            }
        }
        item {
            // REQUIREMENTS LIST — compact, inline progress, no cards
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("REQUIREMENTS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = DraftLockColors.muted, letterSpacing = 0.8.sp)
                    Text("${requirements.count { it.enabled }} ACTIVE", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.accent, fontWeight = FontWeight.Black)
                }
                RequirementRow("Writing — Daily Goal", words, quota, words >= quota, true)
                requirements.filter { it.enabled }.forEach { req ->
                    val used = vm.usageMinutes[req.packageName] ?: 0
                    RequirementRow(req.displayName, used, req.requiredMinutes, used >= req.requiredMinutes, false)
                }
                if (requirements.filter { it.enabled }.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(painterResource(R.drawable.ic_lock_closed), null, tint = DraftLockColors.muted.copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
                            Text("No app requirements", style = MaterialTheme.typography.titleSmall, color = DraftLockColors.muted)
                            Text("Add in Apps tab → require usage time", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
        item {
            // BLOCKING STATUS — single line, diagnostic
            Box(Modifier.fillMaxWidth().padding(14.dp).background(GlassTokens.glass, RoundedCornerShape(14.dp)).padding(1.dp).background(DraftLockColors.line.copy(alpha = 0.3f), RoundedCornerShape(14.dp))) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(28.dp).clip(CircleShape).background(if (complete) DraftLockColors.accent.copy(alpha = 0.2f) else DraftLockColors.neonPink.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                                Icon(painterResource(if (complete) R.drawable.ic_lock_open else R.drawable.ic_lock_closed), null, tint = if (complete) DraftLockColors.accent else DraftLockColors.neonPink, modifier = Modifier.size(14.dp))
                            }
                            Column {
                                Text(if (complete) "VAULT OPEN" else "VAULT SEALED", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = if (complete) DraftLockColors.accent else Color.White)
                                Text(if (complete) "All conditions satisfied" else "Logic: ${if (logic == "AND") "WRITE + ALL APPS" else "WRITE + ANY APP"}", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted, fontSize = 10.sp)
                            }
                        }
                        if (!complete)
                            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(DraftLockColors.neonPink.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Text("LOCKED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = DraftLockColors.neonPink, letterSpacing = 0.5.sp, fontSize = 9.sp)
                            }
                    }
                    Text(vm.blockingDiagnostics, style = MaterialTheme.typography.labelSmall, color = if (vm.blockingAvailable) DraftLockColors.muted else DraftLockColors.neonPink)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!vm.blockingAvailable) Button(onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = DraftLockColors.neonPink.copy(alpha = 0.2f), contentColor = DraftLockColors.neonPink), shape = RoundedCornerShape(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)) { Text("Popup Blocking", fontSize = 11.sp, fontWeight = FontWeight.Black) }
                        if (!vm.usageAccess) Button(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = DraftLockColors.neonCyan.copy(alpha = 0.2f), contentColor = DraftLockColors.neonCyan), shape = RoundedCornerShape(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)) { Text("Usage Access", fontSize = 11.sp, fontWeight = FontWeight.Black) }
                        TextButton(onClick = onOverride, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp)) { Text("Emergency 15m", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = DraftLockColors.gold) }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun VaultStatusCard(words: Int, quota: Int, progress: Float, isUnlocked: Boolean, onWrite: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = GlassTokens.glassStrong), elevation = CardDefaults.cardElevation(0.dp), modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.padding(18.dp).background(Brush.verticalGradient(listOf(Color(0x1A00CD3C), Color(0x0A000000))), RoundedCornerShape(20.dp)).padding(1.dp).background(DraftLockColors.line.copy(alpha = 0.15f), RoundedCornerShape(20.dp))) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Header: status badge + level
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(if (isUnlocked) DraftLockColors.accent else Color(0xFF242424)), contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_write), null, tint = if (isUnlocked) Color.Black else DraftLockColors.muted, modifier = Modifier.size(22.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(if (isUnlocked) "DAILY GOAL MET" else "INK PROGRESS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = if (isUnlocked) DraftLockColors.accent else DraftLockColors.muted, letterSpacing = 0.8.sp)
                            Text("$words / $quota words", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Color.White)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("LVL ${(words / 500) + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = DraftLockColors.melonGreen, letterSpacing = 1.0.sp)
                        Text("VAULT", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted, letterSpacing = 1.2.sp)
                    }
                }
                // Progress bar — thick, gradient, centerpiece
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1A1A1A))) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress.coerceAtMost(1f))
                            .height(10.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.horizontalGradient(listOf(DraftLockColors.xpStart, DraftLockColors.xpEnd)))
                            .animateContentSize(animationSpec = tween(400, easing = EaseOutCubic))
                    )
                }
                // Progress label
                Row(horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${(progress * 100).toInt()}% complete", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.accent, fontWeight = FontWeight.Black)
                    Text("${(quota - words).coerceAtLeast(0)} words to go", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted)
                }
                // Primary action — full width, distinct
                Button(onClick = onWrite, modifier = Modifier.fillMaxWidth().height(48.dp), colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = if (isUnlocked) DraftLockColors.melonGreen else DraftLockColors.accent,
                    contentColor = Color.Black
                ), shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_write), null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (isUnlocked) "CONTINUE WRITING" else "OPEN VAULT & WRITE", fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 0.5.sp)
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun MicroStatCard(label: String, value: String, tint: Color, icon: Int, modifier: Modifier = Modifier) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = GlassTokens.glass), elevation = CardDefaults.cardElevation(0.dp), modifier = modifier) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
            Text(label, style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted, letterSpacing = 0.6.sp, fontSize = 10.sp)
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(tint.copy(alpha = 0.5f)))
    }
}

@androidx.compose.runtime.Composable
private fun RequirementRow(name: String, current: Int, required: Int, complete: Boolean, isMain: Boolean) {
    val p = (current.toFloat() / required.coerceAtLeast(1)).coerceIn(0f, 1f)
    Row(Modifier.fillMaxWidth().padding(12.dp).background(if (complete) Color(0x10142010) else GlassTokens.glass, RoundedCornerShape(14.dp)).padding(1.dp).background(DraftLockColors.line.copy(alpha = if (complete) 0.3f else 0.1f), RoundedCornerShape(14.dp))) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(if (complete) DraftLockColors.accent else Color(0xFF242424)), contentAlignment = Alignment.Center) {
            Icon(painterResource(if (complete) R.drawable.ic_trophy else if (isMain) R.drawable.ic_write else R.drawable.ic_analytics), null, tint = if (complete) Color.Black else DraftLockColors.muted, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                Text(name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, color = Color.White)
                Text("$current / $required ${if (isMain) "words" else "min"}", style = MaterialTheme.typography.labelSmall, color = if (complete) DraftLockColors.accent else DraftLockColors.muted, fontWeight = FontWeight.Medium)
            }
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF1A1A1A))) {
                Box(
                    Modifier
                        .fillMaxWidth(p)
                        .height(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (complete) Brush.horizontalGradient(listOf(DraftLockColors.accent, DraftLockColors.melonGreen)) else Brush.horizontalGradient(listOf(DraftLockColors.line.copy(alpha = 0.5f), DraftLockColors.line.copy(alpha = 0.5f))))
                        .animateContentSize(animationSpec = tween(400, easing = EaseOutCubic))
                )
            }
        }
        Box(Modifier.size(36.dp).clip(CircleShape).background(if (complete) DraftLockColors.accent else Color(0xFF2A2A2A)), contentAlignment = Alignment.Center) {
            Text(if (complete) "✓" else "${(p * 100).toInt()}%", fontWeight = FontWeight.Black, color = if (complete) Color.Black else DraftLockColors.muted, fontSize = if (complete) 14.sp else 10.sp)
        }
    }
}


@androidx.compose.runtime.Composable
private fun WriteScreen(vm: DraftLockViewModel, text: String, words: Int, quota: Int, documentName: String) {
    var draft by remember(text) { mutableStateOf(text) }
    val progress = (words.toFloat() / quota.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp).padding(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // vault paper header — pro tool, game accent only on progress
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = DraftLockColors.panel), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(DraftLockColors.ink), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_write), null, tint = DraftLockColors.bg, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(documentName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("$words / $quota words • ${(progress*100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted)
                    }
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (progress >= 1f) DraftLockColors.accent else DraftLockColors.panelElevated).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(if (progress >= 1f) "GOAL MET" else "WRITING", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = if (progress >= 1f) Color.Black else DraftLockColors.muted)
                    }
                }
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF242428))) {
                    Box(Modifier.fillMaxWidth(progress).height(6.dp).clip(RoundedCornerShape(6.dp)).background(Brush.horizontalGradient(listOf(DraftLockColors.xpStart, DraftLockColors.xpEnd))))
                }
                Text("Every keystroke counts — bonus local docs also tally to daily goal. Gmail sync is optional.", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted)
            }
        }
        OutlinedTextField(value = draft, onValueChange = { draft = it; vm.onTextChanged(it) }, modifier = Modifier.fillMaxWidth().weight(1f), placeholder = { Text("Start writing — ink the vault…", color = DraftLockColors.muted) }, shape = RoundedCornerShape(14.dp), colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedBorderColor = DraftLockColors.accent, unfocusedBorderColor = DraftLockColors.line, focusedContainerColor = DraftLockColors.panelElevated, unfocusedContainerColor = DraftLockColors.panel, focusedTextColor = DraftLockColors.ink, unfocusedTextColor = DraftLockColors.ink, cursorColor = DraftLockColors.accent, focusedPlaceholderColor = DraftLockColors.muted, unfocusedPlaceholderColor = DraftLockColors.muted))
        Text("Ink Vault dark — paper steel, lime vault only. Auto-saves per keystroke.", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted)
    }
}

@androidx.compose.runtime.Composable
private fun UnifiedAppsScreen(vm: DraftLockViewModel, context: Context) {
    val requirements by vm.requirements.collectAsStateWithLifecycle()
    val lockedApps by vm.lockedApps.collectAsStateWithLifecycle()
    val logic by vm.logic.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("ALL") }
    var isLoading by remember { mutableStateOf(true) }
    var allApps by remember { mutableStateOf<List<SimpleApp>>(emptyList()) }
    LaunchedEffect(context) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val list = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
                .map { it.activityInfo.applicationInfo }.distinctBy { it.packageName }
                .filter { it.packageName != context.packageName }
                .map { info -> SimpleApp(info.packageName, pm.getApplicationLabel(info).toString()) }
                .sortedBy { it.label.lowercase() }
            withContext(Dispatchers.Main) { allApps = list; isLoading = false }
        }
    }
    val filtered = remember(query, filter, allApps, requirements, lockedApps) {
        allApps.filter {
            (query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true)) &&
            when(filter) {
                "BLOCKED" -> lockedApps.any { l -> l.packageName == it.packageName }
                "REQUIRED" -> requirements.any { r -> r.packageName == it.packageName }
                "AVAILABLE" -> requirements.none { r -> r.packageName == it.packageName } && lockedApps.none { l -> l.packageName == it.packageName }
                else -> true
            }
        }
    }
    Column(Modifier.fillMaxSize().background(DraftLockColors.bg)) {
        Surface(color = DraftLockColors.panel, tonalElevation = 0.dp, shadowElevation = 1.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(listOf(DraftLockColors.neonCyan, DraftLockColors.accent))), contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_usage), null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Apps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                            Text("${allApps.size} installed", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted)
                        }
                    }
                    FilterChip(selected = logic == "AND", onClick = { vm.setLogic(if (logic == "AND") "OR" else "AND") }, label = { Text(logic, fontWeight = FontWeight.Black) }, colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor = DraftLockColors.melonGreen, selectedLabelColor = Color.Black), shape = RoundedCornerShape(0.dp))
                }
                Text("${requirements.size} requirements • ${lockedApps.size} blocked  •  Popup: ${if(vm.blockingAvailable) "Ready" else "Enable Accessibility"}", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted)
                OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text("Search apps…") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp), colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedBorderColor = DraftLockColors.accent, unfocusedBorderColor = DraftLockColors.line))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ALL","BLOCKED","REQUIRED","AVAILABLE").forEach { f ->
                        // MelonUI: sharp 0px corners for dense utilitarian control (converted)
                        FilterChip(selected = filter==f, onClick = { filter = f }, label = { Text(f, style = MaterialTheme.typography.labelSmall) }, shape = RoundedCornerShape(0.dp), colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor = if (filter==f) DraftLockColors.melonGreen else DraftLockColors.panel, selectedLabelColor = if (filter==f) Color.Black else Color.White, containerColor = DraftLockColors.panel))
                    }
                }
                if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = DraftLockColors.accent, trackColor = DraftLockColors.line)
                if (!vm.blockingAvailable) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF24141A)), shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(painterResource(R.drawable.ic_shield), null, tint = DraftLockColors.neonPink, modifier = Modifier.size(18.dp))
                            Text("Popup blocking off — enable Accessibility", style = MaterialTheme.typography.bodySmall, color = DraftLockColors.neonPink, modifier = Modifier.weight(1f))
                            TextButton(onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Enable") }
                        }
                    }
                }
            }
        }
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(bottom = 80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DraftLockColors.accent) }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp)
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    val req = requirements.find { it.packageName == app.packageName }
                    val locked = lockedApps.find { it.packageName == app.packageName }
                    val minutes = vm.usageMinutes[app.packageName] ?: 0
                    UnifiedAppRow(app, req, locked, minutes, vm)
                }
                if (filtered.isEmpty()) {
                    item {
                        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = DraftLockColors.panel), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Image(painterResource(R.drawable.illustration_apps_empty), null, modifier = Modifier.size(120.dp))
                                Text("No apps found", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Try a different search or filter.", style = MaterialTheme.typography.bodySmall, color = DraftLockColors.muted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun UnifiedAppRow(app: SimpleApp, req: AppRequirement?, locked: LockedApp?, minutes: Int, vm: DraftLockViewModel) {
    var showMinutes by remember { mutableStateOf(false) }
    var minutesVal by remember(req?.requiredMinutes ?: 30) { mutableStateOf(req?.requiredMinutes?.toFloat() ?: 30f) }
    var iconBmp by remember(app.packageName) { mutableStateOf<Bitmap?>(null) }
    val ctx = LocalContext.current
    LaunchedEffect(app.packageName) {
        withContext(Dispatchers.IO) {
            try {
                val d = ctx.packageManager.getApplicationIcon(app.packageName)
                val bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                d.setBounds(0, 0, 96, 96)
                d.draw(c)
                withContext(Dispatchers.Main) { iconBmp = bmp }
            } catch (_: Exception) {}
        }
    }
    val isBoss = locked != null
    // Structure: Melon dense-row — sharp left accent (Melon green / neonPink) + 0px detail strip
    Card(shape = RoundedCornerShape(if(isBoss) 0.dp else 14.dp), colors = CardDefaults.cardColors(containerColor = if(isBoss) Color(0xFF1E1218) else DraftLockColors.panel), elevation = CardDefaults.cardElevation(if(isBoss) 6.dp else 2.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (iconBmp != null) {
                    Image(bitmap = iconBmp!!.asImageBitmap(), contentDescription = app.label, modifier = Modifier.size(44.dp).clip(CircleShape))
                } else {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(if(isBoss) Color(0xFF2A1020) else DraftLockColors.panelElevated), contentAlignment = Alignment.Center) {
                        Text(app.label.take(1).uppercase(), fontWeight = FontWeight.Black, color = if(isBoss) DraftLockColors.neonPink else DraftLockColors.accent)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(app.label, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f, fill=false))
                        if (isBoss) { Spacer(Modifier.width(6.dp)); Icon(painterResource(R.drawable.ic_gamepad), null, tint = DraftLockColors.neonPink, modifier = Modifier.size(14.dp)) }
                    }
                    Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted, maxLines = 1)
                    if (req != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF2A2A2A))) {
                                Box(Modifier.fillMaxWidth((minutes.toFloat()/req.requiredMinutes).coerceIn(0f,1f)).height(6.dp).clip(RoundedCornerShape(4.dp)).background(Brush.horizontalGradient(listOf(DraftLockColors.xpStart, DraftLockColors.neonCyan))))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text("$minutes/${req.requiredMinutes}m", style = MaterialTheme.typography.labelSmall, color = if (minutes >= req.requiredMinutes) DraftLockColors.accent else DraftLockColors.muted)
                        }
                    }
                }
                if (req != null) Icon(painterResource(R.drawable.ic_star), null, tint = if (minutes >= req.requiredMinutes) DraftLockColors.gold else DraftLockColors.muted, modifier = Modifier.size(18.dp))
                if (locked != null) Icon(painterResource(R.drawable.ic_lock_closed), null, tint = DraftLockColors.neonPink, modifier = Modifier.size(18.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (req == null) {
                    Button(onClick = { vm.addRequirement(AppRequirement(packageName = app.packageName, displayName = app.label, requiredMinutes = 30)) }, modifier = Modifier.weight(1f), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = DraftLockColors.panelElevated, contentColor = Color.White), shape = RoundedCornerShape(10.dp)) { Icon(painterResource(R.drawable.ic_analytics), null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Require", style = MaterialTheme.typography.labelSmall) }
                } else {
                    Button(onClick = { showMinutes = !showMinutes }, modifier = Modifier.weight(1f), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = DraftLockColors.accent, contentColor = Color.Black), shape = RoundedCornerShape(10.dp)) { Text("${req.requiredMinutes}m", fontWeight = FontWeight.Bold) }
                    TextButton(onClick = { vm.deleteRequirement(req.id) }) { Text("Remove", style = MaterialTheme.typography.labelSmall) }
                }
                if (locked == null) {
                    Button(onClick = { vm.addLockedApp(LockedApp(app.packageName, app.label)) }, modifier = Modifier.weight(1f), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1020), contentColor = DraftLockColors.neonPink), shape = RoundedCornerShape(10.dp)) { Icon(painterResource(R.drawable.ic_lock_closed), null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Block", style = MaterialTheme.typography.labelSmall) }
                } else {
                    Button(onClick = { vm.deleteLockedApp(locked.packageName) }, modifier = Modifier.weight(1f), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = DraftLockColors.neonPink, contentColor = Color.White), shape = RoundedCornerShape(10.dp)) { Text("Unblock", style = MaterialTheme.typography.labelSmall) }
                }
            }
            if (showMinutes && req != null) {
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Required time: ${minutesVal.toInt()} min", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Slider(value = minutesVal, onValueChange = { minutesVal = it }, valueRange = 5f..120f, steps = 22)
                        Button(onClick = { vm.updateRequirementMinutes(req.id, minutesVal.toInt()); showMinutes = false }, modifier = Modifier.align(Alignment.End), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = DraftLockColors.accent, contentColor = Color.Black)) { Text("Save") }
                    }
                }
            }
        }
    }
}

private data class SimpleApp(val packageName: String, val label: String)

@androidx.compose.runtime.Composable
private fun DocsScreen(vm: DraftLockViewModel) {
    val context = LocalContext.current
    val documentName by vm.documentName.collectAsStateWithLifecycle()
    val autoSave by vm.googleAutoSave.collectAsStateWithLifecycle()
    val text by vm.text.collectAsStateWithLifecycle()
    val folderId by vm.googleFolderId.collectAsStateWithLifecycle()
    val docId by vm.googleDocumentId.collectAsStateWithLifecycle()
    val localDocs by vm.localDocs.collectAsStateWithLifecycle()
    var name by remember(documentName) { mutableStateOf(documentName) }
    var newDocName by remember { mutableStateOf("Doc ${java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault()).format(java.util.Date())}") }
    var newLocalTitle by remember { mutableStateOf("") }
    var filterTab by remember { mutableStateOf("CLOUD") }
    var showClientDialog by remember { mutableStateOf(false) }
    var clientIdInput by remember { mutableStateOf(GoogleOAuthManager(context).effectiveClientId.let { if(it.startsWith("YOUR_")) "" else it }) }
    LaunchedEffect(Unit) { vm.checkGoogleConnection(); if (vm.isGoogleConnected) vm.fetchDriveFiles() }
    if (showClientDialog) {
        AlertDialog(onDismissRequest = { showClientDialog = false }, title = { Text("Connect Gmail") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Baked ID is 149732972265-m00s0… — if it fails, paste your Android Client ID (…apps.googleusercontent.com). Offline is bonus, Gmail primary.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = clientIdInput, onValueChange = { clientIdInput = it }, label = { Text("Client ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Create: Console → Credentials → Create OAuth client → Android → Package com.draftlock.app + SHA-1 (runner CE:A8:92:E8:... or your local). Baked ID auto-works, no rebuild needed after paste.", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted)
            }
        }, confirmButton = { TextButton(onClick = {
            if (clientIdInput.contains(".apps.googleusercontent.com")) {
                GoogleOAuthManager(context).setRuntimeClientId(clientIdInput)
                vm.checkGoogleConnection()
                showClientDialog = false
                vm.startGoogleAuth(context)
            }
        }) { Text("Save & Connect") } }, dismissButton = { TextButton(onClick = { showClientDialog = false }) { Text("Cancel") } })
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = GlassTokens.glassStrong), elevation = CardDefaults.cardElevation(0.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(if(vm.isGoogleConnected) DraftLockColors.accent else Color(0xFF1A1A1E)), contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_google), null, tint = if(vm.isGoogleConnected) Color.Black else Color.White, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(if (vm.isGoogleConnected) "GMAIL CONNECTED ✓" else "CONNECT GMAIL", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, color = if(vm.isGoogleConnected) DraftLockColors.accent else Color.White, letterSpacing = 0.8.sp)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (vm.isSyncing) CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = DraftLockColors.accent)
                                Text(if(vm.isSyncing) "Fetching chapters…" else if(vm.isGoogleConnected && vm.driveFiles.isNotEmpty()) "${vm.driveFiles.size} chapters • ${vm.syncStatus}" else vm.syncStatus, style = MaterialTheme.typography.labelSmall, color = if(vm.isGoogleConnected) DraftLockColors.ink else DraftLockColors.muted, fontSize = 10.sp)
                            }
                        }
                        if (vm.isGoogleConnected) TextButton(onClick = { GoogleOAuthManager(context).disconnect(); vm.checkGoogleConnection(); vm.saveGoogleStatus("Gmail unlinked") }) { Text("Unlink", color = DraftLockColors.muted) }
                        else Button(onClick = {
                            if (vm.isGoogleConfigured) vm.startGoogleAuth(context) else showClientDialog = true
                        }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = DraftLockColors.accent, contentColor = Color.Black), shape = RoundedCornerShape(12.dp)) { Text("Connect", fontWeight = FontWeight.Black) }
                    }
                    if (!vm.isGoogleConfigured) {
                        TextButton(onClick = { showClientDialog = true }) { Text("Enter Client ID (one-time setup)", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted) }
                    }
                    if (vm.isGoogleConnected && !vm.isSyncing) {
                        Text(if(vm.driveFiles.isEmpty()) "No chapters yet — tap Search or Create below. Offline docs are bonus." else "Chapters fetched — tap a doc to sync & write. Vault dark, lime only.", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.FilterChip(selected = filterTab=="CLOUD", onClick = { filterTab="CLOUD" }, label = { Text("Google Docs") }, colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor = DraftLockColors.accent, selectedLabelColor = Color.Black))
                androidx.compose.material3.FilterChip(selected = filterTab=="LOCAL", onClick = { filterTab="LOCAL" }, label = { Text("Local Docs (${localDocs.size}) — Bonus") }, colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor = DraftLockColors.panelElevated, selectedLabelColor = Color.White))
            }
        }
        if (filterTab == "LOCAL") {
            item { Text("Local Docs — Bonus offline (primary is Gmail)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = newLocalTitle, onValueChange = { newLocalTitle = it }, label = { Text("New local doc title") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp))
                    Button(onClick = { if(newLocalTitle.isNotBlank()) { vm.createLocalDoc(newLocalTitle); newLocalTitle="" } }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = DraftLockColors.panelElevated, contentColor = Color.White)) { Text("Create") }
                }
            }
            if (localDocs.isEmpty()) item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = DraftLockColors.panel)) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Image(painterResource(R.drawable.illustration_vault_empty), null, modifier = Modifier.size(100.dp))
                        Text("No local docs", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Create a bonus doc above. Gmail sync is primary.", style = MaterialTheme.typography.bodySmall, color = DraftLockColors.muted)
                    }
                }
            }
            items(localDocs) { doc ->
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = DraftLockColors.panel), modifier = Modifier.fillMaxWidth()) {
                    var editing by remember { mutableStateOf(false) }
                    var editText by remember(doc.content) { mutableStateOf(doc.content) }
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_docs), null, tint = DraftLockColors.accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) { Text(doc.title, fontWeight = FontWeight.Bold, maxLines = 1); Text("${doc.wordCount} words • ${java.text.SimpleDateFormat("MMM dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(doc.updatedAt))}", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted) }
                            TextButton(onClick = { vm.deleteLocalDoc(doc.id) }) { Text("Delete", color = DraftLockColors.neonPink, style = MaterialTheme.typography.labelSmall) }
                        }
                        if (!editing) {
                            Text(doc.content.ifBlank { "Empty — tap Edit. Words count to your daily goal." }, style = MaterialTheme.typography.bodySmall, color = if(doc.content.isBlank()) DraftLockColors.muted else Color.White, maxLines = 3)
                            Button(onClick = { editing = true }, modifier = Modifier.fillMaxWidth(), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF1A2E1A))) { Text("Edit Doc") }
                        } else {
                            OutlinedTextField(value = editText, onValueChange = { editText = it }, modifier = Modifier.fillMaxWidth().height(120.dp), placeholder = { Text("Write…") }, shape = RoundedCornerShape(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = { editing = false; editText = doc.content }) { Text("Cancel") }
                                Button(onClick = { vm.updateLocalDocContent(doc.id, editText); editing = false }, modifier = Modifier.weight(1f), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = DraftLockColors.accent, contentColor = Color.Black)) { Text("Save") }
                            }
                        }
                    }
                }
            }
            item { Text("Local docs total ${localDocs.sumOf{it.wordCount}} + main ${text.split(Regex("\\s+")).count{it.isNotBlank()}} = ${localDocs.sumOf{it.wordCount} + text.split(Regex("\\s+")).count{it.isNotBlank()}} words", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted) }
        } else {
            item { Text("Google Docs — Primary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black) }
            item { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Document name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.setDocumentName(name); vm.saveGoogleStatus("Saved") }, modifier = Modifier.weight(1f)) { Text("Save Name") }
                Button(onClick = { vm.syncTextToDoc() }, enabled = vm.isGoogleConnected && !vm.isSyncing && docId.isNotBlank()) { Text(if (vm.isSyncing) "Syncing…" else "Sync Now") }
            } }
            item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Auto-sync when connected", Modifier.weight(1f)); Switch(checked = autoSave, onCheckedChange = vm::setGoogleAutoSave) } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = vm.driveQuery, onValueChange = { vm.driveQuery = it }, label = { Text("Filter docs") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp))
                Button(onClick = { vm.fetchDriveFiles() }, enabled = vm.isGoogleConnected && !vm.isSyncing, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = DraftLockColors.neonCyan, contentColor = Color.Black)) { Text("Search") }
            } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = newDocName, onValueChange = { newDocName = it }, label = { Text("New Google Doc name") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp))
                Button(onClick = { if (newDocName.isNotBlank()) vm.createGoogleDoc(newDocName) { newDocName = "" } }, enabled = vm.isGoogleConnected && !vm.isSyncing, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = DraftLockColors.accent, contentColor = Color.Black)) { Text("Create") }
            } }
            if (vm.isSyncing) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = DraftLockColors.accent) }
            if (!vm.isGoogleConfigured) item { Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2410))) { Text("Gmail not linked — tap Connect Gmail at top. You can also paste Client ID.", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = DraftLockColors.gold) } }
            items(vm.driveFiles) { file ->
                Card(modifier = Modifier.fillMaxWidth().clickable { vm.setGoogleDocument(file.id); vm.setDocumentName(file.name); vm.saveGoogleStatus("Selected ${file.name}") }, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = if(file.id==docId) Color(0xFF142010) else DraftLockColors.panel)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_docs), null, tint = DraftLockColors.neonCyan, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(file.name, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text("Edited ${file.modifiedTime.take(10)} • ${file.id.take(8)}…", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted)
                        }
                        if (file.id == docId) Icon(painterResource(R.drawable.ic_trophy), null, tint = DraftLockColors.gold, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (vm.driveFiles.isEmpty() && vm.isGoogleConnected) item { Text("No Google Docs found — create one above.", color = DraftLockColors.muted, style = MaterialTheme.typography.bodySmall) }
            if (vm.driveFiles.isEmpty() && !vm.isGoogleConnected) item { Button(onClick = { if(vm.isGoogleConfigured) vm.startGoogleAuth(context) else showClientDialog = true }, modifier = Modifier.fillMaxWidth(), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) { Icon(painterResource(R.drawable.ic_google), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Connect Gmail to see your Docs") } }
            item { Text("Words: ${text.split(Regex("\\s+")).count { it.isNotBlank() }} • ${if (autoSave) "Auto-sync ON" else "Manual"} • Folder: ${folderId.ifBlank { "(root)" }}", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted) }
        }
    }
}

@androidx.compose.runtime.Composable
private fun SettingsScreen(vm: DraftLockViewModel, quota: Int, resetMinutes: Int, logic: String, autoSave: Boolean, onOverride: () -> Unit) {
    val context = LocalContext.current
    var quotaText by remember(quota) { mutableStateOf(quota.toString()) }
    var resetText by remember(resetMinutes) { mutableStateOf(resetMinutes.toString()) }
    var clientIdText by remember { mutableStateOf(GoogleOAuthManager(context).effectiveClientId.let { if (it.startsWith("YOUR_") || it.startsWith("987654")) "" else it }) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = DraftLockColors.panel), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(DraftLockColors.accent), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_shield), null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Vault Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = Color.White)
                        Text("Pro controls, game HUD finish", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted)
                    }
                }
            }
        }
        item { Text("Writing", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
        item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = DraftLockColors.panelElevated)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = quotaText, onValueChange = { quotaText = it.filter(Char::isDigit) }, label = { Text("Daily word quota") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
                    Button(onClick = { vm.setQuota(quotaText.toIntOrNull() ?: quota) }, modifier = Modifier.fillMaxWidth(), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = DraftLockColors.accent, contentColor = Color.Black)) { Text("Save quota") }
                    OutlinedTextField(value = resetText, onValueChange = { resetText = it.filter(Char::isDigit) }, label = { Text("Reset minutes after midnight (0–1439)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
                    Button(onClick = { vm.setResetMinutes(resetText.toIntOrNull()?.coerceIn(0, 1439) ?: resetMinutes) }, modifier = Modifier.fillMaxWidth()) { Text("Save reset time") }
                    Text("Logic: $logic • Auto-sync: ${if (autoSave) "ON" else "OFF"}", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted)
                }
            }
        }
        item { Text("Gmail Login — Normal flow", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
        item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = if (vm.isGoogleConnected) Color(0xFF0E1F14) else Color(0xFF1A1A1E))) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_google), null, tint = if (vm.isGoogleConnected) DraftLockColors.accent else Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (vm.isGoogleConnected) "Gmail Connected" else "Not connected", fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.weight(1f))
                        Text(vm.isGoogleConfigured.toString(), style = MaterialTheme.typography.labelSmall, color = Color.Transparent)
                    }
                    Text(vm.syncStatus, style = MaterialTheme.typography.bodySmall, color = DraftLockColors.muted)
                    if (!vm.isGoogleConnected) {
                        Button(onClick = { vm.startGoogleAuth(context) }, modifier = Modifier.fillMaxWidth(), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) { Icon(painterResource(R.drawable.ic_google), null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("Sign in with Google — just like Gmail") }
                        Text("One tap, pick your Google account, Allow. No paste needed if you build with GOOGLE_CLIENT_ID.", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted)
                    } else {
                        Button(onClick = { GoogleOAuthManager(context).disconnect(); vm.checkGoogleConnection() }, modifier = Modifier.fillMaxWidth(), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = DraftLockColors.panelElevated, contentColor = Color.White)) { Text("Unlink Gmail") }
                    }
                    Divider(color = Color(0x1AFFFFFF))
                    Text("Advanced — custom Client ID (only if baked fails)", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted)
                    OutlinedTextField(value = clientIdText, onValueChange = { clientIdText = it }, label = { Text("xxx.apps.googleusercontent.com") }, placeholder = { Text("Baked 149732972265-m00s0… auto") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (clientIdText.contains(".apps.googleusercontent.com")) {
                                GoogleOAuthManager(context).setRuntimeClientId(clientIdText); vm.checkGoogleConnection(); vm.saveGoogleStatus("Client ID saved — tap Sign in")
                            }
                        }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = DraftLockColors.gold, contentColor = Color.Black)) { Text("Save ID") }
                        TextButton(onClick = { clientIdText = ""; context.getSharedPreferences("draftlock_runtime", Context.MODE_PRIVATE).edit().remove("runtime_google_client_id").apply(); vm.checkGoogleConnection() }) { Text("Clear") }
                    }
                    Text("Baked ID is 149732972265-m00s0ja9… — auto, no paste. If error, create Android: Console → Credentials → Create OAuth client → Android → Package com.draftlock.app + SHA-1 CE:A8:92:E8:88:BA:A6:58:43:F0:0D:72:BF:7E:9D:9D:72:BE:51:3B", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted)
                }
            }
        }
        item { Text("Blocking", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
        item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = DraftLockColors.panel)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(vm.blockingDiagnostics, style = MaterialTheme.typography.bodySmall, color = if (vm.blockingAvailable) DraftLockColors.accent else DraftLockColors.neonPink)
                    Button(onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, modifier = Modifier.fillMaxWidth(), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = DraftLockColors.neonPink)) { Text("Enable Popup Blocking") }
                    Button(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }, modifier = Modifier.fillMaxWidth(), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = DraftLockColors.neonCyan, contentColor = Color.Black)) { Text("Enable Usage Access") }
                    Button(onClick = onOverride, modifier = Modifier.fillMaxWidth(), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2E), contentColor = Color.White)) { Text("Emergency override 15 min") }
                }
            }
        }
        item { Text("Identity: Ink Vault — paper + steel, one lime accent. Fun HUD, serious tool. No delays.", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted) }
    }
}
