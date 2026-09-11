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
import com.draftlock.app.ui.theme.DraftLockColors
import com.draftlock.app.ui.theme.DraftLockTheme
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
    WRITE("Quest", R.drawable.ic_write),
    APPS("Arena", R.drawable.ic_gamepad),
    DOCS("Vault", R.drawable.ic_docs),
    SETTINGS("Config", R.drawable.ic_analytics)
}

class MainActivity : ComponentActivity() {
    private lateinit var oauthManager: GoogleOAuthManager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        oauthManager = GoogleOAuthManager(this)
        handleOAuthIntent(intent)
        setContent { DraftLockApp() }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthIntent(intent)
    }
    private fun handleOAuthIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.data?.toString()?.contains("/oauth2redirect") == true || intent.hasExtra("net.openid.appauth.AuthorizationResponse")) {
            oauthManager.handleResult(intent) { _, _ -> }
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
    var isGoogleConnected by mutableStateOf(loadGoogleState() != null)
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
        isGoogleConfigured = GoogleOAuthManager(getApplication()).isConfigured
        isGoogleConnected = loadGoogleState() != null
        if (!isGoogleConfigured) syncStatus = "Local mode — Google optional (add GOOGLE_CLIENT_ID)"
    }
    fun startGoogleAuth(context: Context) {
        val mgr = GoogleOAuthManager(context)
        if (!mgr.isConfigured) { saveGoogleStatus("Local mode: Google not configured — writing still counts!"); return }
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
fun DraftLockApp(vm: DraftLockViewModel = viewModel()) {
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
    LaunchedEffect(Unit) { while (true) { delay(30_000); vm.refreshUsage() } }

    DraftLockTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_trophy), null, tint = DraftLockColors.gold, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("DraftLock", fontWeight = FontWeight.Black)
                            Spacer(Modifier.width(8.dp))
                            Text("LVL ${(todayWords/500)+1}", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.accent, modifier = Modifier.background(DraftLockColors.panel, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            context.startActivity(Intent(context, PrototypeActivity::class.java))
                        }) { Text("Preview") }
                    }
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Screen.values().forEach { item ->
                        NavigationBarItem(
                            selected = screen == item,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                screen = item
                            },
                            icon = { Icon(painter = painterResource(id = item.iconRes), contentDescription = item.label, modifier = Modifier.size(22.dp)) },
                            label = { Text(item.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        ) { pad ->
            Surface(Modifier.fillMaxSize().padding(pad)) {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        (slideInHorizontally { it / 5 } + fadeIn(tween(250))) togetherWith
                                (slideOutHorizontally { -it / 5 } + fadeOut(tween(200)))
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
    val animatedWords by animateIntAsState(words.coerceAtLeast(0), spring(dampingRatio = 0.8f, stiffness = 300f), label = "words")
    val progress = (animatedWords.toFloat() / quota.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(progress, tween(600, easing = EaseOutCubic), label = "progress")
    val lvl = (animatedWords / 500) + 1
    val xpInLevel = animatedWords % 500
    val shimmer = rememberInfiniteTransition(label = "shimmer").animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing), repeatMode = RepeatMode.Restart), label = "shim")
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = DraftLockColors.panel), shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(8.dp), modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = 1f + animatedProgress * 0.012f; scaleY = 1f + animatedProgress * 0.012f }) {
                Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF1A2E1A), Color(0xFF151515))), shape = RoundedCornerShape(20.dp)).padding(18.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(painterResource(R.drawable.ic_flame), null, tint = Color(0xFFFF6B35), modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("STREAK", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted)
                                Spacer(Modifier.width(8.dp))
                                Text("$lvl LVL", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = DraftLockColors.gold, modifier = Modifier.background(Color(0xFF2A2410), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                            Text(if (complete) "UNLOCKED" else "LOCKED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = if (complete) DraftLockColors.accent else DraftLockColors.neonPink, modifier = Modifier.background(if(complete) Color(0xFF1A2E1A) else Color(0xFF2A1020), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                        Text("$animatedWords / $quota XP", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Color.White)
                        Text("Keep writing to level up — every word is XP", style = MaterialTheme.typography.bodySmall, color = DraftLockColors.muted)
                        Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2A2A))) {
                            Box(Modifier.fillMaxWidth(animatedProgress).height(14.dp).clip(RoundedCornerShape(8.dp)).background(Brush.horizontalGradient(listOf(DraftLockColors.xpStart, DraftLockColors.xpEnd))))
                            Box(Modifier.fillMaxWidth().height(14.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha=0.18f), Color.Transparent), startX = shimmer.value*400f - 200f, endX = shimmer.value*400f + 200f)))
                        }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("$xpInLevel / 500 to next", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted)
                            Text("${(progress*100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.accent)
                        }
                        Button(onClick = onWrite, modifier = Modifier.fillMaxWidth(), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = DraftLockColors.accent, contentColor = Color.Black)) { Icon(painterResource(R.drawable.ic_star), null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Continue Quest", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MiniStatCard("Quests", "${requirements.size}", R.drawable.ic_gamepad, DraftLockColors.neonCyan, Modifier.weight(1f))
                MiniStatCard("Bosses", "${lockedApps.size}", R.drawable.ic_lock_closed, DraftLockColors.neonPink, Modifier.weight(1f))
                MiniStatCard("Logic", logic, R.drawable.ic_star, DraftLockColors.gold, Modifier.weight(1f))
            }
        }
        item { Text("Active Quests", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black) }
        item { RequirementCard("Main Quest — Writing", "$animatedWords / $quota XP", words >= quota, isMain = true) }
        itemsIndexed(requirements.filter { it.enabled }) { idx, req ->
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { delay((idx*45).toLong()); visible = true }
            AnimatedVisibility(visible = visible, enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it/3 }) {
                RequirementCard(req.displayName, "${vm.usageMinutes[req.packageName] ?: 0} / ${req.requiredMinutes} min", (vm.usageMinutes[req.packageName] ?: 0) >= req.requiredMinutes)
            }
        }
        if (requirements.isEmpty()) item { Text("No quests yet — head to Arena to add apps.", style = MaterialTheme.typography.bodySmall, color = DraftLockColors.muted, modifier = Modifier.padding(8.dp)) }
        item {
            val statusScale by animateFloatAsState(if (complete) 1.02f else 1f, spring(dampingRatio = 0.6f, stiffness = 400f), label = "statusScale")
            Card(modifier = Modifier.scale(statusScale), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (complete) Color(0xFF142010) else DraftLockColors.panel)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(if(complete) R.drawable.ic_trophy else R.drawable.ic_lock_closed), null, tint = if(complete) DraftLockColors.gold else DraftLockColors.muted, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (complete) "All bosses defeated!" else "Bosses are guarding", fontWeight = FontWeight.Bold)
                    }
                    AnimatedContent(targetState = complete, label = "statusText") { done ->
                        Text(if (done) "All conditions clear — your apps are free." else "$logic gate: ${if(logic=="AND") "write AND all quests" else "write OR any quest"} to unlock.", style = MaterialTheme.typography.bodySmall, color = DraftLockColors.muted)
                    }
                    Text(vm.blockingDiagnostics, style = MaterialTheme.typography.bodySmall, color = if(vm.blockingAvailable) DraftLockColors.accent else DraftLockColors.neonPink)
                    if (!vm.usageAccess) androidx.compose.material3.Button(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = DraftLockColors.neonCyan)) { Text("Enable Usage Access") }
                    TextButton(onClick = onOverride) { Text("Emergency warp 15m") }
                }
            }
        }
    }
}
@androidx.compose.runtime.Composable private fun MiniStatCard(label:String, value:String, icon:Int, tint:Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = DraftLockColors.panel)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(6.dp))
            Text(value, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted)
        }
    }
}
@androidx.compose.runtime.Composable
private fun RequirementCard(name: String, value: String, complete: Boolean, isMain:Boolean=false) {
    val scale by animateFloatAsState(if (complete) 1f else 0.99f, spring(dampingRatio = 0.7f, stiffness = 300f), label = "cardScale")
    Card(modifier = Modifier.scale(scale).fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = if (complete) Color(0xFF1A2E1A) else DraftLockColors.panel), elevation = CardDefaults.cardElevation(if(complete) 6.dp else 2.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(if(complete) DraftLockColors.accent else Color(0xFF2A2A2A)), contentAlignment = Alignment.Center) {
                Icon(painterResource(if(complete) R.drawable.ic_trophy else if(isMain) R.drawable.ic_star else R.drawable.ic_gamepad), null, tint = if(complete) Color.Black else DraftLockColors.muted, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.Bold, maxLines = 1); Text(value, style = MaterialTheme.typography.bodySmall, color = DraftLockColors.muted) }
            AnimatedContent(targetState = complete, label = "check") { done ->
                Box(Modifier.size(28.dp).clip(CircleShape).background(if(done) DraftLockColors.accent else Color(0xFF2A2A2A)), contentAlignment = Alignment.Center) {
                    Text(if (done) "✓" else "•", fontWeight = FontWeight.Black, color = if(done) Color.Black else DraftLockColors.muted)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun WriteScreen(vm: DraftLockViewModel, text: String, words: Int, quota: Int, documentName: String) {
    var draft by remember(text) { mutableStateOf(text) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(documentName, fontWeight = FontWeight.Bold); Text("${words.coerceAtLeast(0)} / $quota words") }; Text("Saved locally") }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = draft, onValueChange = { draft = it; vm.onTextChanged(it) }, modifier = Modifier.fillMaxSize(), placeholder = { Text("Start writing…") }, singleLine = false)
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
                "REQUIRED" -> requirements.any { r -> r.packageName == it.packageName }
                "BLOCKED" -> lockedApps.any { l -> l.packageName == it.packageName }
                "AVAILABLE" -> requirements.none { r -> r.packageName == it.packageName } && lockedApps.none { l -> l.packageName == it.packageName }
                else -> true
            }
        }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp, shadowElevation = 4.dp) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_gamepad), null, tint = DraftLockColors.neonCyan, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Arena", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    }
                    FilterChip(selected = logic == "AND", onClick = { vm.setLogic(if (logic == "AND") "OR" else "AND") }, label = { Text(logic, fontWeight = FontWeight.Black) }, colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor = DraftLockColors.accent, selectedLabelColor = Color.Black))
                }
                Text("${requirements.size} quests • ${lockedApps.size} bosses • ${allApps.size} apps", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted)
                Text("Unlock: ${if (logic=="AND") "write AND all quests" else "write OR any quest"}", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.accent)
                OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text("Search arena…") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ALL","REQUIRED","BLOCKED","AVAILABLE").forEach { f ->
                        FilterChip(selected = filter==f, onClick = { filter = f }, label = { Text(f, style = MaterialTheme.typography.labelSmall) }, colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor = DraftLockColors.panel))
                    }
                }
                if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = DraftLockColors.accent)
            }
        }
        if (isLoading) {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)) {
                items(6) {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = DraftLockColors.panel), modifier = Modifier.fillMaxWidth().height(90.dp)) {
                        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1A1A1A), Color(0xFF232323)))).padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF2A2A2A)))
                                Spacer(Modifier.width(12.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                                    Box(Modifier.fillMaxWidth(0.5f).height(14.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF2A2A2A)))
                                    Box(Modifier.fillMaxWidth(0.7f).height(10.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF252525)))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)) {
                itemsIndexed(filtered, key = { _, it -> it.packageName }) { idx, app ->
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(app.packageName) { delay((idx*30).toLong().coerceAtMost(180)); visible = true }
                    AnimatedVisibility(visible = visible, enter = fadeIn(tween(260)) + slideInVertically(tween(260)) { it/4 } + scaleIn(tween(260))) {
                        val req = requirements.find { it.packageName == app.packageName }
                        val locked = lockedApps.find { it.packageName == app.packageName }
                        val minutes = vm.usageMinutes[app.packageName] ?: 0
                        UnifiedAppRow(app, req, locked, minutes, vm)
                    }
                }
                if (filtered.isEmpty()) { item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text("No apps match your filter.", color = DraftLockColors.muted) } } }
                item { Text(vm.blockingDiagnostics, style = MaterialTheme.typography.labelSmall, color = if(vm.blockingAvailable) DraftLockColors.accent else DraftLockColors.neonPink, modifier = Modifier.padding(12.dp)) }
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
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if(isBoss) Color(0xFF1E1218) else DraftLockColors.panel), elevation = CardDefaults.cardElevation(if(isBoss) 6.dp else 2.dp), modifier = Modifier.fillMaxWidth()) {
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
                    Button(onClick = { vm.addRequirement(AppRequirement(packageName = app.packageName, displayName = app.label, requiredMinutes = 30)) }, modifier = Modifier.weight(1f), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = DraftLockColors.accent, contentColor = Color.Black), shape = RoundedCornerShape(10.dp)) { Icon(painterResource(R.drawable.ic_star), null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Quest", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) }
                } else {
                    Button(onClick = { showMinutes = !showMinutes }, modifier = Modifier.weight(1f), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = DraftLockColors.accent, contentColor = Color.Black), shape = RoundedCornerShape(10.dp)) { Text("${req.requiredMinutes}m", fontWeight = FontWeight.Bold) }
                    TextButton(onClick = { vm.deleteRequirement(req.id) }) { Text("Remove", style = MaterialTheme.typography.labelSmall) }
                }
                if (locked == null) {
                    Button(onClick = { vm.addLockedApp(LockedApp(app.packageName, app.label)) }, modifier = Modifier.weight(1f), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1020), contentColor = DraftLockColors.neonPink), shape = RoundedCornerShape(10.dp)) { Icon(painterResource(R.drawable.ic_lock_closed), null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Boss", style = MaterialTheme.typography.labelSmall) }
                } else {
                    Button(onClick = { vm.deleteLockedApp(locked.packageName) }, modifier = Modifier.weight(1f), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = DraftLockColors.neonPink, contentColor = Color.White), shape = RoundedCornerShape(10.dp)) { Text("Free", style = MaterialTheme.typography.labelSmall) }
                }
            }
            if (showMinutes && req != null) {
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Quest duration: ${minutesVal.toInt()}m", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
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
    var newDocName by remember { mutableStateOf("Scroll ${java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault()).format(java.util.Date())}") }
    var newLocalTitle by remember { mutableStateOf("") }
    var filterTab by remember { mutableStateOf("LOCAL") }
    LaunchedEffect(Unit) { vm.checkGoogleConnection(); if (vm.isGoogleConnected) vm.fetchDriveFiles() }
    LazyColumn(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (vm.isGoogleConnected) Color(0xFF142010) else if(vm.isGoogleConfigured) Color(0xFF2A1A10) else DraftLockColors.panel), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(if(vm.isGoogleConnected) DraftLockColors.accent else if(vm.isGoogleConfigured) DraftLockColors.gold else Color(0xFF2A2A2A)), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_google), null, tint = if(vm.isGoogleConnected) Color.Black else Color.White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (vm.isGoogleConnected) "Cloud Linked" else if(!vm.isGoogleConfigured) "Local Vault" else "Cloud Offline", fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodySmall)
                        Text(vm.syncStatus, style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted)
                        if (!vm.isGoogleConfigured) Text("Add GOOGLE_CLIENT_ID to enable Drive", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.gold)
                    }
                    if (!vm.isGoogleConnected && vm.isGoogleConfigured) Button(onClick = { vm.startGoogleAuth(context) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)) { Text("Link", style = MaterialTheme.typography.labelSmall) }
                    else if (vm.isGoogleConnected) TextButton(onClick = { GoogleOAuthManager(context).disconnect(); vm.checkGoogleConnection(); vm.saveGoogleStatus("Unlinked") }) { Text("Unlink", style = MaterialTheme.typography.labelSmall) }
                    else Text("Local ✓", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.accent)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.FilterChip(selected = filterTab=="LOCAL", onClick = { filterTab="LOCAL" }, label = { Text("Local Quests (${localDocs.size})") }, colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor = DraftLockColors.accent, selectedLabelColor = Color.Black))
                androidx.compose.material3.FilterChip(selected = filterTab=="CLOUD", onClick = { filterTab="CLOUD" }, label = { Text("Cloud Scrolls") }, colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor = DraftLockColors.neonCyan, selectedLabelColor = Color.Black))
            }
        }
        if (filterTab == "LOCAL") {
            item { Text("Local Quests — every word adds to daily XP", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = newLocalTitle, onValueChange = { newLocalTitle = it }, label = { Text("New quest title") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp))
                    Button(onClick = { if(newLocalTitle.isNotBlank()) { vm.createLocalDoc(newLocalTitle); newLocalTitle="" } }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = DraftLockColors.accent, contentColor = Color.Black)) { Text("Forge") }
                }
            }
            if (localDocs.isEmpty()) item { Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = DraftLockColors.panel)) { Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { Text("No local quests — forge one above. Every keystroke is XP.", color = DraftLockColors.muted, style = MaterialTheme.typography.bodySmall) } } }
            items(localDocs) { doc ->
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = DraftLockColors.panel), modifier = Modifier.fillMaxWidth()) {
                    var editing by remember { mutableStateOf(false) }
                    var editText by remember(doc.content) { mutableStateOf(doc.content) }
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_docs), null, tint = DraftLockColors.accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) { Text(doc.title, fontWeight = FontWeight.Bold, maxLines = 1); Text("${doc.wordCount} XP • ${java.text.SimpleDateFormat("MMM dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(doc.updatedAt))}", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted) }
                            TextButton(onClick = { vm.deleteLocalDoc(doc.id) }) { Text("Delete", color = DraftLockColors.neonPink, style = MaterialTheme.typography.labelSmall) }
                        }
                        if (!editing) {
                            Text(doc.content.ifBlank { "Empty scroll — tap Edit to write. Words here count to your daily goal." }, style = MaterialTheme.typography.bodySmall, color = if(doc.content.isBlank()) DraftLockColors.muted else Color.White, maxLines = 3)
                            Button(onClick = { editing = true }, modifier = Modifier.fillMaxWidth(), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF1A2E1A))) { Text("Edit Quest") }
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
            item { Text("Active scroll: $documentName • Local total ${localDocs.sumOf{it.wordCount}} XP + main ${text.split(Regex("\\s+")).count{it.isNotBlank()}} = ${localDocs.sumOf{it.wordCount} + text.split(Regex("\\s+")).count{it.isNotBlank()}}", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted) }
        } else {
            item { Text("Active Document", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black) }
            item { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Document name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.setDocumentName(name); vm.saveGoogleStatus("Saved locally") }, modifier = Modifier.weight(1f)) { Text("Save Name") }
                Button(onClick = { vm.syncTextToDoc() }, enabled = vm.isGoogleConnected && !vm.isSyncing && docId.isNotBlank()) { Text(if (vm.isSyncing) "Syncing…" else "Sync Now") }
            } }
            item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Auto-save to Docs", Modifier.weight(1f)); Switch(checked = autoSave, onCheckedChange = vm::setGoogleAutoSave) } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = vm.driveQuery, onValueChange = { vm.driveQuery = it }, label = { Text("Filter prefix") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp))
                Button(onClick = { vm.fetchDriveFiles() }, enabled = vm.isGoogleConnected && !vm.isSyncing && vm.isGoogleConfigured, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = DraftLockColors.neonCyan, contentColor = Color.Black)) { Text("Scan") }
            } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = newDocName, onValueChange = { newDocName = it }, label = { Text("New scroll name") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp))
                Button(onClick = { if (newDocName.isNotBlank()) vm.createGoogleDoc(newDocName) { newDocName = "" } }, enabled = vm.isGoogleConnected && !vm.isSyncing, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = DraftLockColors.accent, contentColor = Color.Black)) { Text("Forge") }
            } }
            if (vm.isSyncing) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = DraftLockColors.accent) }
            if (!vm.isGoogleConfigured) item { Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2410))) { Text("Google not configured — add GOOGLE_CLIENT_ID to local.properties to enable cloud. Local quests work without it.", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = DraftLockColors.gold) } }
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
            if (vm.driveFiles.isEmpty() && vm.isGoogleConnected) item { Text("No scrolls — forge one above.", color = DraftLockColors.muted, style = MaterialTheme.typography.bodySmall) }
            item { Text("Local words: ${text.split(Regex("\\s+")).count { it.isNotBlank() }} • ${if (autoSave) "Auto-sync ON" else "Manual"} • Folder: ${folderId.ifBlank { "(root)" }}", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted) }
        }
    }
}

@androidx.compose.runtime.Composable
private fun SettingsScreen(vm: DraftLockViewModel, quota: Int, resetMinutes: Int, logic: String, autoSave: Boolean, onOverride: () -> Unit) {
    var quotaText by remember(quota) { mutableStateOf(quota.toString()) }
    var resetText by remember(resetMinutes) { mutableStateOf(resetMinutes.toString()) }
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { OutlinedTextField(value = quotaText, onValueChange = { quotaText = it.filter(Char::isDigit) }, label = { Text("Daily word quota") }, modifier = Modifier.fillMaxWidth()) }
        item { Button(onClick = { vm.setQuota(quotaText.toIntOrNull() ?: quota) }) { Text("Save quota") } }
        item { OutlinedTextField(value = resetText, onValueChange = { resetText = it.filter(Char::isDigit) }, label = { Text("Reset minutes after midnight (0–1439)") }, modifier = Modifier.fillMaxWidth()) }
        item { Button(onClick = { vm.setResetMinutes(resetText.toIntOrNull()?.coerceIn(0, 1439) ?: resetMinutes) }) { Text("Save reset time") } }
        item { Text("Requirement logic: $logic") }
        item { Button(onClick = onOverride) { Text("Emergency override") } }
        item { Text("Emergency override is intentionally inconvenient and unlocks selected apps for only 15 minutes.") }
        item { Text("For strong app suspension, provision DraftLock as the device owner during device setup or testing, for example with adb dpm set-device-owner. Do this only on a device you control; device-owner provisioning changes device management state.") }
    }
}
