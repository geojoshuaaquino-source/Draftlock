package com.draftlock.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.draftlock.app.ui.theme.DraftLockTheme
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.core.graphics.drawable.toBitmap
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
    APPS("Apps", R.drawable.ic_rules),
    DOCS("Docs", R.drawable.ic_docs),
    SETTINGS("Settings", R.drawable.ic_analytics)
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
            oauthManager.handleResult(intent) { success, msg ->
                // status handled via ViewModel syncStatus
            }
        }
    }
}

class DraftLockViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val db = DraftLockDatabase.get(application)
    private val store = SettingsStore(application)
    private val usage = UsageTracker(application)
    private val blocker = AppBlocker(application)

    val requirements = db.dao().observeRequirements().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val lockedApps = db.dao().observeLockedApps().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
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

    var usageMinutes by mutableStateOf<Map<String, Int>>(emptyMap())
    var usageAccess by mutableStateOf(usage.hasUsageAccess())
    var blockingAvailable by mutableStateOf(blocker.canSuspendApps())
    var syncStatus by mutableStateOf("Local only")
    var driveFiles by mutableStateOf<List<RemoteFile>>(emptyList())
    var driveQuery by mutableStateOf("DND")
    var isSyncing by mutableStateOf(false)
    var isGoogleConnected by mutableStateOf(loadGoogleState() != null)
    private var lastTextWordCount = 0
    private val docsRepo = GoogleDocsRepository()

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

    private fun loadGoogleState() = try { GoogleOAuthManager(getApplication()).loadState() } catch (_: Exception) { null }

    fun checkGoogleConnection() { isGoogleConnected = loadGoogleState() != null }

    fun startGoogleAuth(context: Context) {
        try { GoogleOAuthManager(context).startAuthorization(); saveGoogleStatus("Opening Google sign-in…") }
        catch (e: Exception) { saveGoogleStatus(e.message ?: "Google sign-in failed") }
    }

    fun fetchDriveFiles(query: String = driveQuery) {
        val manager = GoogleOAuthManager(getApplication())
        if (manager.loadState() == null) { saveGoogleStatus("Connect Google first"); return }
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
        val doc = docId.ifBlank { return }
        val manager = GoogleOAuthManager(getApplication())
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
                    title = { Text("DraftLock", fontWeight = FontWeight.Bold) },
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
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("WRITE FIRST. DISTRACTIONS WAIT.", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("Today", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.graphicsLayer { scaleX = 1f + animatedProgress * 0.02f; scaleY = 1f + animatedProgress * 0.02f }
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("$animatedWords / $quota", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("words completed", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onPrimaryContainer, trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onWrite) { Text("Continue writing") }
                }
            }
        }
        item { Text("Requirements", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { RequirementCard("Writing", "$animatedWords / $quota words", words >= quota) }
        items(requirements.filter { it.enabled }) { req -> RequirementCard(req.displayName, "${vm.usageMinutes[req.packageName] ?: 0} / ${req.requiredMinutes} min", (vm.usageMinutes[req.packageName] ?: 0) >= req.requiredMinutes) }
        item {
            val statusScale by animateFloatAsState(if (complete) 1.02f else 1f, spring(dampingRatio = 0.6f, stiffness = 400f), label = "statusScale")
            Card(modifier = Modifier.scale(statusScale)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Status", style = MaterialTheme.typography.labelLarge)
                    Text(if (complete) "UNLOCKED" else "LOCKED", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = if (complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    AnimatedContent(targetState = complete, label = "statusText") { done ->
                        Text(if (done) "All configured conditions are complete." else "$logic logic is active. Finish today's requirements to unlock the selected apps.")
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Locked apps: ${lockedApps.count { it.enabled }}")
                    if (!vm.blockingAvailable) Text("Blocking capability is not active. Device-owner/profile-owner setup is required for strong package suspension.", style = MaterialTheme.typography.bodySmall)
                    if (!vm.usageAccess) TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text("Enable Usage Access") }
                    TextButton(onClick = onOverride) { Text("Emergency override") }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun RequirementCard(name: String, value: String, complete: Boolean) {
    val scale by animateFloatAsState(if (complete) 1f else 0.98f, spring(dampingRatio = 0.7f, stiffness = 300f), label = "cardScale")
    Card(modifier = Modifier.scale(scale), colors = CardDefaults.cardColors(containerColor = if (complete) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.Bold); Text(value, style = MaterialTheme.typography.bodyMedium) }
            AnimatedContent(targetState = complete, label = "check") { done ->
                Text(if (done) "✓" else "—", style = MaterialTheme.typography.titleLarge, color = if (done) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
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
    var filter by remember { mutableStateOf("ALL") } // ALL, REQUIRED, BLOCKED, AVAILABLE
    val pm = context.packageManager
    val allApps = remember(context) {
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
            .map { it.activityInfo.applicationInfo }.distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { InstalledApp(it.packageName, pm.getApplicationLabel(it).toString(), pm.getApplicationIcon(it.packageName)) }
            .sortedBy { it.label.lowercase() }
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
        // Header with logic
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("All Apps", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    FilterChip(selected = logic == "AND", onClick = { vm.setLogic(if (logic == "AND") "OR" else "AND") }, label = { Text(logic, fontWeight = FontWeight.Bold) })
                }
                Text("${requirements.size} required • ${lockedApps.size} blocked • ${allApps.size} installed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Unlock needs: ${if (logic=="AND") "writing AND all required apps" else "writing OR any required app"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text("Search apps…") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ALL","REQUIRED","BLOCKED","AVAILABLE").forEach { f ->
                        FilterChip(selected = filter==f, onClick = { filter = f }, label = { Text(f) })
                    }
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)) {
            items(filtered, key = { it.packageName }) { app ->
                val req = requirements.find { it.packageName == app.packageName }
                val locked = lockedApps.find { it.packageName == app.packageName }
                val minutes = vm.usageMinutes[app.packageName] ?: 0
                UnifiedAppRow(app, req, locked, minutes, vm)
            }
            if (filtered.isEmpty()) { item { Text("No apps match.", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            item { Text("Tip: Tap Require to track usage, Block to suspend until writing is done. Blocking needs device-owner.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp)) }
        }
    }
}

@Composable
private fun UnifiedAppRow(app: InstalledApp, req: AppRequirement?, locked: LockedApp?, minutes: Int, vm: DraftLockViewModel) {
    var showMinutes by remember { mutableStateOf(false) }
    var minutesVal by remember(req?.requiredMinutes ?: 30) { mutableStateOf(req?.requiredMinutes?.toFloat() ?: 30f) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Image(bitmap = app.icon.toBitmap(96,96).asImageBitmap(), contentDescription = app.label, modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    if (req != null) Text("$minutes / ${req.requiredMinutes} min today", style = MaterialTheme.typography.bodySmall, color = if (minutes >= req.requiredMinutes) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (req != null) Icon(painter = painterResource(R.drawable.ic_analytics), contentDescription = null, tint = if (minutes >= req.requiredMinutes) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                if (locked != null) Icon(painter = painterResource(R.drawable.ic_lock_closed), contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (req == null) {
                    Button(onClick = { vm.addRequirement(AppRequirement(packageName = app.packageName, displayName = app.label, requiredMinutes = 30)) }, modifier = Modifier.weight(1f)) { Text("Require") }
                } else {
                    Button(onClick = { showMinutes = !showMinutes }, modifier = Modifier.weight(1f), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("${req.requiredMinutes}m") }
                    TextButton(onClick = { vm.deleteRequirement(req.id) }) { Text("Remove") }
                }
                if (locked == null) {
                    Button(onClick = { vm.addLockedApp(LockedApp(app.packageName, app.label)) }, modifier = Modifier.weight(1f), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Text("Block", color = MaterialTheme.colorScheme.onErrorContainer) }
                } else {
                    Button(onClick = { vm.deleteLockedApp(locked.packageName) }, modifier = Modifier.weight(1f), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Unblock") }
                }
            }
            if (showMinutes && req != null) {
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(12.dp)) {
                    Text("Required minutes: ${minutesVal.toInt()}m", style = MaterialTheme.typography.bodyMedium)
                    Slider(value = minutesVal, onValueChange = { minutesVal = it }, valueRange = 5f..120f, steps = 22)
                    Button(onClick = { vm.updateRequirementMinutes(req.id, minutesVal.toInt()); showMinutes = false }, modifier = Modifier.align(Alignment.End)) { Text("Save") }
                }
            }
        }
    }
}

private data class InstalledApp(val packageName: String, val label: String, val icon: Drawable)

@androidx.compose.runtime.Composable
private fun DocsScreen(vm: DraftLockViewModel) {
    val context = LocalContext.current
    val documentName by vm.documentName.collectAsStateWithLifecycle()
    val autoSave by vm.googleAutoSave.collectAsStateWithLifecycle()
    val text by vm.text.collectAsStateWithLifecycle()
    var name by remember(documentName) { mutableStateOf(documentName) }
    var newDocName by remember { mutableStateOf("DND Chapter ${java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault()).format(java.util.Date())}") }
    LaunchedEffect(Unit) { vm.checkGoogleConnection(); if (vm.isGoogleConnected) vm.fetchDriveFiles() }
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = if (vm.isGoogleConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painter = painterResource(R.drawable.ic_google), contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (vm.isGoogleConnected) "Google Connected" else "Not Connected", fontWeight = FontWeight.Bold)
                        Text(vm.syncStatus, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!vm.isGoogleConnected) Button(onClick = { vm.startGoogleAuth(context); vm.checkGoogleConnection() }) { Text("Connect") }
                    else TextButton(onClick = { GoogleOAuthManager(context).disconnect(); vm.checkGoogleConnection(); vm.saveGoogleStatus("Disconnected") }) { Text("Disconnect") }
                }
            }
        }
        item { Text("Active Document", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        item { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Document name") }, modifier = Modifier.fillMaxWidth()) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.setDocumentName(name); vm.saveGoogleStatus("Saved locally") }, modifier = Modifier.weight(1f)) { Text("Save Name") }
            Button(onClick = { vm.syncTextToDoc() }, enabled = vm.isGoogleConnected && !vm.isSyncing && vm.googleDocumentId.collectAsStateWithLifecycle().value.isNotBlank()) { Text(if (vm.isSyncing) "Syncing…" else "Sync Now") }
        } }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Auto-save to Docs", Modifier.weight(1f)); Switch(checked = autoSave, onCheckedChange = vm::setGoogleAutoSave) } }
        item { Divider() }
        item { Text("Drive Library", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = vm.driveQuery, onValueChange = { vm.driveQuery = it }, label = { Text("Filter (prefix)") }, modifier = Modifier.weight(1f), singleLine = true)
            Button(onClick = { vm.fetchDriveFiles() }, enabled = vm.isGoogleConnected && !vm.isSyncing) { Text("Search") }
        } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = newDocName, onValueChange = { newDocName = it }, label = { Text("New doc name") }, modifier = Modifier.weight(1f), singleLine = true)
            Button(onClick = { if (newDocName.isNotBlank()) vm.createGoogleDoc(newDocName) { newDocName = "" } }, enabled = vm.isGoogleConnected && !vm.isSyncing) { Text("Create") }
        } }
        if (vm.isSyncing) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        items(vm.driveFiles) { file ->
            Card(modifier = Modifier.fillMaxWidth().clickable { vm.setGoogleDocument(file.id); vm.setDocumentName(file.name); vm.saveGoogleStatus("Selected ${file.name}") }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painter = painterResource(R.drawable.ic_docs), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(file.name, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("Edited ${file.modifiedTime.take(10)} • ${file.id.take(8)}…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (file.id == vm.googleDocumentId.collectAsStateWithLifecycle().value) Icon(painter = painterResource(R.drawable.ic_lock_open), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
        if (vm.driveFiles.isEmpty() && vm.isGoogleConnected) item { Text("No docs found. Try search or create one.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        item { Text("Local words: ${text.split(Regex("\\s+")).count { it.isNotBlank() }} • ${if (autoSave) "Auto-sync ON" else "Manual sync"} • Folder ID: ${vm.googleFolderId.collectAsStateWithLifecycle().value.ifBlank { "(root)" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Text("Google Docs integration uses official Drive file + Docs batchUpdate APIs. Token stored encrypted via AndroidKeyStore.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
