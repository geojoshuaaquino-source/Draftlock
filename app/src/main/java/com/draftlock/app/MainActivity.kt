package com.draftlock.app

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.draftlock.app.data.AppRequirement
import com.draftlock.app.data.DailyRecord
import com.draftlock.app.data.DraftLockDatabase
import com.draftlock.app.data.LockedApp
import com.draftlock.app.data.SettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

private enum class Screen(val label: String) { HOME("Home"), WRITE("Write"), RULES("Rules"), DOCS("Docs"), SETTINGS("Settings") }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DraftLockApp() }
    }
}

class DraftLockViewModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
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
    private var lastTextWordCount = 0

    init {
        viewModelScope.launch {
            val reset = UsageTracker.periodStartMillis(resetMinutes.value)
            val dayKey = reset.toString()
            if (store.todayKey.stateIn(viewModelScope, SharingStarted.Eagerly, "").value != dayKey) store.setTodayWords(0, dayKey)
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
            val current = todayWords.value
            if (delta != 0) store.setTodayWords(current + delta, UsageTracker.periodStartMillis(resetMinutes.value).toString())
        }
    }

    fun setQuota(value: Int) = viewModelScope.launch { store.setQuota(value) ; applyBlocking() }
    fun setResetMinutes(value: Int) = viewModelScope.launch { store.setResetMinutes(value); store.setTodayWords(0, UsageTracker.periodStartMillis(value).toString()); refreshUsage() }
    fun setLogic(value: String) = viewModelScope.launch { store.setLogic(value); applyBlocking() }
    fun setDocumentName(value: String) = viewModelScope.launch { store.setDocumentName(value) }
    fun setGoogleFolder(value: String) = viewModelScope.launch { store.setGoogleFolderId(value) }
    fun setGoogleDocument(value: String) = viewModelScope.launch { store.setGoogleDocumentId(value) }
    fun setGoogleAutoSave(value: Boolean) = viewModelScope.launch { store.setGoogleAutoSave(value) }
    fun addRequirement(req: AppRequirement) = viewModelScope.launch { db.dao().upsertRequirement(req); refreshUsage() }
    fun deleteRequirement(id: Long) = viewModelScope.launch { db.dao().deleteRequirement(id); refreshUsage() }
    fun addLockedApp(app: LockedApp) = viewModelScope.launch { db.dao().upsertLockedApp(app); applyBlocking() }
    fun deleteLockedApp(pkg: String) = viewModelScope.launch { db.dao().deleteLockedApp(pkg); blocker.unsuspend(listOf(pkg)) }

    fun activateEmergencyOverride() = viewModelScope.launch {
        store.setOverride(System.currentTimeMillis() + 15 * 60_000L)
        blocker.unsuspend(lockedApps.value.map { it.packageName })
    }

    fun allConditionsComplete(): Boolean {
        val writing = todayWords.value >= quota.value
        val app = requirements.value.filter { it.enabled }.map { usageMinutes[it.packageName] ?: 0 >= it.requiredMinutes }
        if (app.isEmpty()) return writing
        return if (logic.value == "OR") writing || app.any { it } else writing && app.all { it }
    }

    fun applyBlocking() {
        viewModelScope.launch {
            val overrideActive = overrideUntil.value > System.currentTimeMillis()
            val shouldUnlock = allConditionsComplete() || overrideActive
            val packages = lockedApps.value.filter { it.enabled }.map { it.packageName }
            if (shouldUnlock) blocker.unsuspend(packages) else blocker.suspend(packages)
        }
    }

    fun saveGoogleStatus(status: String) { syncStatus = status }

    companion object { private fun countWords(s: String): Int = s.trim().split(Regex("\\s+")).count { it.isNotBlank() }.coerceAtMost(1_000_000) }
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
    var screen by remember { mutableStateOf(Screen.HOME) }
    var showOverride by remember { mutableStateOf(false) }

    LaunchedEffect(requirements) { vm.refreshUsage() }
    LaunchedEffect(text) { if (text.isNotEmpty()) vm.onTextChanged(text) }
    LaunchedEffect(Unit) {
        while (true) { delay(30_000); vm.refreshUsage() }
    }

    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("DraftLock", fontWeight = FontWeight.Bold) }) },
            bottomBar = {
                NavigationBar {
                    Screen.values().forEach { item -> NavigationBarItem(selected = screen == item, onClick = { screen = item }, icon = { Text(item.label.take(1)) }, label = { Text(item.label) }) }
                }
            }
        ) { pad ->
            Surface(Modifier.fillMaxSize().padding(pad)) {
                when (screen) {
                    Screen.HOME -> HomeScreen(vm, todayWords, quota, requirements, lockedApps, logic, context, onWrite = { screen = Screen.WRITE }, onOverride = { showOverride = true })
                    Screen.WRITE -> WriteScreen(vm, text, todayWords, quota, documentName)
                    Screen.RULES -> RulesScreen(vm, requirements, lockedApps, logic, context)
                    Screen.DOCS -> DocsScreen(vm, documentName, googleAutoSave)
                    Screen.SETTINGS -> SettingsScreen(vm, quota, resetMinutes, logic, googleAutoSave, onOverride = { showOverride = true })
                }
            }
        }
    }

    if (showOverride) {
        AlertDialog(
            onDismissRequest = { showOverride = false },
            title = { Text("Emergency override") },
            text = { Text("DraftLock will unlock blocked apps for 15 minutes. This is recorded in local history. Continue?") },
            confirmButton = { TextButton(onClick = { vm.activateEmergencyOverride(); showOverride = false }) { Text("Unlock for 15 min") } },
            dismissButton = { TextButton(onClick = { showOverride = false }) { Text("Cancel") } }
        )
    }
}

@androidx.compose.runtime.Composable
private fun HomeScreen(vm: DraftLockViewModel, words: Int, quota: Int, requirements: List<AppRequirement>, lockedApps: List<LockedApp>, logic: String, context: Context, onWrite: () -> Unit, onOverride: () -> Unit) {
    val complete = vm.allConditionsComplete()
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("WRITE FIRST. DISTRACTIONS WAIT.", style = MaterialTheme.typography.labelLarge)
            Text("Today", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Card {
                Column(Modifier.padding(18.dp)) {
                    Text("${words.coerceAtLeast(0)} / $quota", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text("words completed")
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onWrite) { Text("Continue writing") }
                }
            }
        }
        item { Text("Requirements", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { RequirementCard("Writing", "${words.coerceAtLeast(0)} / $quota words", words >= quota) }
        items(requirements.filter { it.enabled }) { req -> RequirementCard(req.displayName, "${vm.usageMinutes[req.packageName] ?: 0} / ${req.requiredMinutes} min", (vm.usageMinutes[req.packageName] ?: 0) >= req.requiredMinutes) }
        item {
            Card {
                Column(Modifier.padding(18.dp)) {
                    Text("Status", style = MaterialTheme.typography.labelLarge)
                    Text(if (complete) "UNLOCKED" else "LOCKED", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(if (complete) "All configured conditions are complete." else "$logic logic is active. Finish today's requirements to unlock the selected apps.")
                    Spacer(Modifier.height(10.dp))
                    Text("Locked apps: ${lockedApps.count { it.enabled }}")
                    if (!vm.blockingAvailable) Text("Blocking capability is not active. Device-owner/profile-owner setup is required for strong package suspension.")
                    if (!vm.usageAccess) TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text("Enable Usage Access") }
                    TextButton(onClick = onOverride) { Text("Emergency override") }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun RequirementCard(name: String, value: String, complete: Boolean) {
    Card {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.Bold); Text(value) }
            Text(if (complete) "✓" else "—", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@androidx.compose.runtime.Composable
private fun WriteScreen(vm: DraftLockViewModel, text: String, words: Int, quota: Int, documentName: String) {
    var draft by remember(text) { mutableStateOf(text) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(documentName, fontWeight = FontWeight.Bold); Text("${words.coerceAtLeast(0)} / $quota words") }
            Text("Saved locally")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it; vm.onTextChanged(it) },
            modifier = Modifier.fillMaxSize(),
            placeholder = { Text("Start writing…") },
            singleLine = false
        )
    }
}

@androidx.compose.runtime.Composable
private fun RulesScreen(vm: DraftLockViewModel, requirements: List<AppRequirement>, lockedApps: List<LockedApp>, logic: String, context: Context) {
    val packageManager = context.packageManager
    var showAddRequirement by remember { mutableStateOf(false) }
    var showAddLocked by remember { mutableStateOf(false) }
    val apps = remember(context) {
        packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { InstalledApp(it.packageName, packageManager.getApplicationLabel(it).toString(), packageManager.getApplicationIcon(it.packageName)) }
            .sortedBy { it.label.lowercase() }
    }
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Requirements", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Logic: "); TextButton(onClick = { vm.setLogic(if (logic == "AND") "OR" else "AND") }) { Text(logic) } }
        }
        items(requirements) { req ->
            Card { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(req.displayName, fontWeight = FontWeight.Bold); Text("${req.requiredMinutes} minutes") }; TextButton(onClick = { vm.deleteRequirement(req.id) }) { Text("Delete") } } }
        }
        item { Button(onClick = { showAddRequirement = true }) { Text("Add app requirement") } }
        item { Divider() }
        item { Text("Locked apps", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        items(lockedApps) { app -> Card { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Text(app.displayName, Modifier.weight(1f)); TextButton(onClick = { vm.deleteLockedApp(app.packageName) }) { Text("Remove") } } } }
        item { Button(onClick = { showAddLocked = true }) { Text("Add locked app") } }
        item { Text("Strong blocking uses DevicePolicyManager package suspension when this app is device owner or profile owner. Android does not grant that capability to ordinary apps.") }
    }

    if (showAddRequirement) AppPickerDialog("Add requirement", apps) { app ->
        vm.addRequirement(AppRequirement(packageName = app.packageName, displayName = app.label, requiredMinutes = 30)); showAddRequirement = false
    } onDismiss = { showAddRequirement = false }
    if (showAddLocked) AppPickerDialog("Lock an app", apps) { app ->
        vm.addLockedApp(LockedApp(app.packageName, app.label)); showAddLocked = false
    } onDismiss = { showAddLocked = false }
}

private data class InstalledApp(val packageName: String, val label: String, val icon: Drawable)

@androidx.compose.runtime.Composable
private fun AppPickerDialog(title: String, apps: List<InstalledApp>, onPick: (InstalledApp) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { LazyColumn(Modifier.height(420.dp)) { items(apps) { app -> Row(Modifier.fillMaxWidth().clickable { onPick(app) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Text(app.label); Spacer(Modifier.width(8.dp)); Text(app.packageName, style = MaterialTheme.typography.bodySmall) } } } }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@androidx.compose.runtime.Composable
private fun DocsScreen(vm: DraftLockViewModel, documentName: String, autoSave: Boolean) {
    var name by remember(documentName) { mutableStateOf(documentName) }
    var folder by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Google Docs", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { Text("Google OAuth uses a browser-based authorization flow. DraftLock never asks for or stores your Google password.") }
        item { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Document name") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(value = folder, onValueChange = { folder = it }, label = { Text("Google Drive folder ID (optional)") }, modifier = Modifier.fillMaxWidth()) }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Automatic save", Modifier.weight(1f)); Switch(autoSave, onCheckedChange = vm::setGoogleAutoSave) } }
        item { Button(onClick = { vm.setDocumentName(name); vm.setGoogleFolder(folder); vm.saveGoogleStatus("Destination saved locally") }) { Text("Save destination") } }
        item { Text("Sync: ${vm.syncStatus}") }
        item { Text("The actual Drive/Docs calls use the official APIs. A Google OAuth client ID must be supplied in local.properties as GOOGLE_CLIENT_ID before account authorization can be completed.") }
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
