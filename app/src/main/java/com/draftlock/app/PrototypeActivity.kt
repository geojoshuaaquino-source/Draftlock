package com.draftlock.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.draftlock.app.data.DailyRecord
import com.draftlock.app.data.DraftLockDatabase
import com.draftlock.app.data.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class PrototypeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PrototypeApp() }
    }
}

private enum class PrototypeTab(val label: String) { HOME("HOME"), WRITE("WRITE"), RULES("RULES"), DOCS("DOCS") }
private enum class PrototypePage { HOME, WRITE, RULES, DOCS, FILTER, DOC_EDIT, CONTROL, USAGE, SYNC, HISTORY, ANALYTICS, PERMISSIONS, ACCOUNT, SETUP, SETTINGS }

@Composable
private fun PrototypeApp(vm: DraftLockViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val text by vm.text.collectAsStateWithLifecycle()
    val words by vm.todayWords.collectAsStateWithLifecycle()
    val quota by vm.quota.collectAsStateWithLifecycle()
    val name by vm.documentName.collectAsStateWithLifecycle()
    val autoSave by vm.googleAutoSave.collectAsStateWithLifecycle()
    val requirements by vm.requirements.collectAsStateWithLifecycle()
    val lockedApps by vm.lockedApps.collectAsStateWithLifecycle()
    val records = remember(context) {
        DraftLockDatabase.get(context).dao().observeRecentDays().stateIn(
            androidx.lifecycle.viewmodel.compose.viewModel<DraftLockViewModel>().viewModelScope,
            SharingStarted.WhileSubscribed(5_000), emptyList()
        )
    }
    val history by records.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(PrototypeTab.HOME) }
    var page by remember { mutableStateOf(PrototypePage.HOME) }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar {
                    PrototypeTab.values().forEach { item ->
                        NavigationBarItem(
                            selected = tab == item && page in setOf(PrototypePage.HOME, PrototypePage.WRITE, PrototypePage.RULES, PrototypePage.DOCS),
                            onClick = { tab = item; page = when (item) { PrototypeTab.HOME -> PrototypePage.HOME; PrototypeTab.WRITE -> PrototypePage.WRITE; PrototypeTab.RULES -> PrototypePage.RULES; PrototypeTab.DOCS -> PrototypePage.DOCS } },
                            icon = { Text(item.label.take(1)) }, label = { Text(item.label) }
                        )
                    }
                }
            }
        ) { pad ->
            Surface(Modifier.fillMaxSize().padding(pad), color = MaterialTheme.colorScheme.background) {
                when (page) {
                    PrototypePage.HOME -> HomePrototype(words, quota, name, vm, onWrite = { tab = PrototypeTab.WRITE; page = PrototypePage.WRITE }, onHistory = { page = PrototypePage.HISTORY }, onAnalytics = { page = PrototypePage.ANALYTICS }, onPermissions = { page = PrototypePage.PERMISSIONS }, onSettings = { page = PrototypePage.SETTINGS })
                    PrototypePage.WRITE -> WritePrototype(vm, text, words, quota, name, onDocs = { tab = PrototypeTab.DOCS; page = PrototypePage.DOCS })
                    PrototypePage.RULES -> RulesPrototype(vm, requirements, lockedApps, onControl = { page = PrototypePage.CONTROL })
                    PrototypePage.DOCS -> DocsPrototype(vm, name, autoSave, onFilter = { page = PrototypePage.FILTER }, onEdit = { page = PrototypePage.DOC_EDIT }, context = context)
                    PrototypePage.FILTER -> FilterPrototype(vm, onBack = { page = PrototypePage.DOCS })
                    PrototypePage.DOC_EDIT -> DocEditPrototype(vm, text, words, quota, name, onBack = { page = PrototypePage.DOCS })
                    PrototypePage.CONTROL -> ControlPrototype(vm, requirements, lockedApps, words, quota, onBack = { page = PrototypePage.RULES })
                    PrototypePage.USAGE -> UsagePrototype(vm, onBack = { page = PrototypePage.HOME })
                    PrototypePage.SYNC -> SyncPrototype(name, autoSave, onBack = { page = PrototypePage.HOME })
                    PrototypePage.HISTORY -> HistoryPrototype(history, onBack = { page = PrototypePage.HOME }, onAnalytics = { page = PrototypePage.ANALYTICS })
                    PrototypePage.ANALYTICS -> AnalyticsPrototype(history, onBack = { page = PrototypePage.HISTORY })
                    PrototypePage.PERMISSIONS -> PermissionsPrototype(vm, context, onAccount = { page = PrototypePage.ACCOUNT }, onBack = { page = PrototypePage.HOME })
                    PrototypePage.ACCOUNT -> AccountPrototype(onSetup = { page = PrototypePage.SETUP }, onBack = { page = PrototypePage.PERMISSIONS })
                    PrototypePage.SETUP -> SetupPrototype(vm, name, onBack = { page = PrototypePage.ACCOUNT })
                    PrototypePage.SETTINGS -> SettingsPrototype(vm, quota, onBack = { page = PrototypePage.HOME })
                }
            }
        }
    }
}

@Composable private fun Header(kicker: String, title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Text(kicker, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable private fun StatCard(title: String, value: String, detail: String, complete: Boolean? = null, onClick: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(detail) }
            complete?.let { Text(if (it) "CLEAR" else "LOCKED", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable private fun HomePrototype(words: Int, quota: Int, name: String, vm: DraftLockViewModel, onWrite: () -> Unit, onHistory: () -> Unit, onAnalytics: () -> Unit, onPermissions: () -> Unit, onSettings: () -> Unit) {
    val progress = words.coerceAtLeast(0)
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Header("WRITING ACCOUNTABILITY SYSTEM", "DRAFTLOCK", "Write your draft. Clear your requirements. Keep distractions locked until the work is done.") }
        item { Text("SYNC  GOOGLE DOCS        CONTROL  ACTIVE", fontWeight = FontWeight.Bold) }
        item { Text("SESSION 07", style = MaterialTheme.typography.labelSmall) }
        item { StatCard("TODAY'S WRITING", "$progress / $quota WORDS", "ACTIVE DRAFT • $name") }
        item { Button(onClick = onWrite, Modifier.fillMaxWidth()) { Text("CONTINUE WRITING") } }
        item { Text("REQUIREMENTS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        item { StatCard("WRITING", "$progress / $quota", if (progress >= quota) "GOAL COMPLETE" else "WRITE ${quota - progress} WORDS", progress >= quota) }
        item { StatCard("ANDROID USAGE", "LIVE", "Selected app requirements", onClick = onPermissions) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = onHistory, Modifier.weight(1f)) { Text("HISTORY") }; Button(onClick = onAnalytics, Modifier.weight(1f)) { Text("ANALYTICS") } } }
        item { TextButton(onClick = onSettings) { Text("SYSTEM / CONFIG • SETTINGS") } }
    }
}

@Composable private fun WritePrototype(vm: DraftLockViewModel, text: String, words: Int, quota: Int, name: String, onDocs: () -> Unit) {
    var draft by remember(text) { mutableStateOf(text) }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Header("WRITE / ACTIVE DOCUMENT", name.uppercase(), "SAVED • TOTAL LOCAL WORDS • TODAY +$words")
        OutlinedTextField(value = draft, onValueChange = { draft = it; vm.onTextChanged(it) }, Modifier.fillMaxWidth().weight(1f), placeholder = { Text("The corridor narrowed as the lights began to flicker…") })
        Spacer(Modifier.height(8.dp))
        Text("CURSOR ACTIVE • EDITS COUNT TOWARD TODAY", style = MaterialTheme.typography.labelSmall)
        Text("AUTOSAVED • ${words.coerceAtLeast(0)} / $quota", style = MaterialTheme.typography.labelSmall)
        TextButton(onClick = onDocs) { Text("DOCS SYNC") }
    }
}

@Composable private fun RulesPrototype(vm: DraftLockViewModel, requirements: List<com.draftlock.app.data.AppRequirement>, lockedApps: List<com.draftlock.app.data.LockedApp>, onControl: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Header("ACCOUNTABILITY / REQUIREMENTS", "APP REQUIREMENTS", "Choose which apps to require before they can unlock.") }
        items(requirements) { req -> StatCard(req.displayName, "${req.requiredMinutes} MIN", if (req.enabled) "ACTIVE" else "DISABLED", req.enabled) }
        item { Divider() }
        item { Header("ACCOUNTABILITY / CONTROL", "BLOCKED APPS", "${lockedApps.count { it.enabled }} ACTIVE LOCKS") }
        items(lockedApps) { app -> StatCard(app.displayName, if (app.enabled) "LOCKED" else "CLEAR", "Unlock after today's requirement", app.enabled) }
        item { Button(onClick = onControl, Modifier.fillMaxWidth()) { Text("VIEW ACCESS CONTROL") } }
        item { Text("ENFORCEMENT\nUsageStats measures time. Device-management capability enforces package suspension when configured.") }
    }
}

@Composable private fun DocsPrototype(vm: DraftLockViewModel, name: String, autoSave: Boolean, onFilter: () -> Unit, onEdit: () -> Unit, context: android.content.Context) {
    var folder by remember { mutableStateOf("Writing / DND") }
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Header("GOOGLE DOCS", "LIBRARY", "SYNCED") }
        item { OutlinedTextField(value = name, onValueChange = vm::setDocumentName, Modifier.fillMaxWidth(), label = { Text("SEARCH DOCUMENTS") }) }
        item { TextButton(onClick = onFilter) { Text("FILTER") } }
        item { StatCard(name, "3,842 words", "edited recently", onClick = onEdit) }
        item { StatCard("DND CHAPTER 11", "4,106 words", "yesterday") }
        item { StatCard("NOVEL NOTES", "Not tracked", "OPEN") }
        item { Divider() }
        item { Text("CLOUD SYNC", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("BACKGROUND SYNC", Modifier.weight(1f)); Switch(checked = autoSave, onCheckedChange = vm::setGoogleAutoSave) } }
        item { Text("Writing never pauses while Docs syncs.\nDestination: $folder") }
        item { Button(onClick = { vm.setGoogleFolder(folder) }, Modifier.fillMaxWidth()) { Text("SAVE DESTINATION") } }
        item { Button(onClick = { GoogleOAuthManager(context).startAuthorization() }, Modifier.fillMaxWidth()) { Text("CONNECT GOOGLE ACCOUNT") } }
    }
}

@Composable private fun FilterPrototype(vm: DraftLockViewModel, onBack: () -> Unit) {
    var prefix by remember { mutableStateOf("DND") }
    var folder by remember { mutableStateOf("Writing / DND") }
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Header("GOOGLE DOCS / LIBRARY / FILTER", "FILTER DOCS", "Only Google Docs whose filename begins with DND appear in the writing library.") }
        item { OutlinedTextField(value = prefix, onValueChange = { prefix = it }, Modifier.fillMaxWidth(), label = { Text("NAME STARTS WITH") }) }
        item { Text("INCLUDE EDITABLE DOCS • ON") }
        item { OutlinedTextField(value = folder, onValueChange = { folder = it }, Modifier.fillMaxWidth(), label = { Text("FOLDER") }) }
        item { Button(onClick = onBack, Modifier.fillMaxWidth()) { Text("APPLY FILTER") } }
    }
}

@Composable private fun DocEditPrototype(vm: DraftLockViewModel, text: String, words: Int, quota: Int, name: String, onBack: () -> Unit) {
    var draft by remember(text) { mutableStateOf(text) }
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Header("GOOGLE DOC / EDIT MODE", name.uppercase(), "SYNCED") }
        item { Text("DOC TOTAL 3,842     COUNTED TODAY $words", fontWeight = FontWeight.Bold) }
        item { OutlinedTextField(value = draft, onValueChange = { draft = it; vm.onTextChanged(it) }, Modifier.fillMaxWidth().height(360.dp)) }
        item { Text("EVERY EDIT UPDATES TODAY'S TOTAL", fontWeight = FontWeight.Bold) }
        item { Text("+$words   ACCOUNTABILITY") }
        item { Button(onClick = onBack, Modifier.fillMaxWidth()) { Text("DONE") } }
    }
}

@Composable private fun ControlPrototype(vm: DraftLockViewModel, requirements: List<com.draftlock.app.data.AppRequirement>, lockedApps: List<com.draftlock.app.data.LockedApp>, words: Int, quota: Int, onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Header("ACCOUNTABILITY / CONTROL", "ACCESS CONTROL", "Finish today's requirement to continue.") }
        item { StatCard("WRITING", "$words / $quota", "WRITE ${maxOf(0, quota - words)} WORDS") }
        items(requirements) { r -> StatCard(r.displayName, "${vm.usageMinutes[r.packageName] ?: 0} / ${r.requiredMinutes}", "MINUTES", (vm.usageMinutes[r.packageName] ?: 0) >= r.requiredMinutes) }
        items(lockedApps) { a -> StatCard(a.displayName, if (a.enabled) "LOCKED" else "CLEAR", "Selected lock") }
        item { Button(onClick = onBack, Modifier.fillMaxWidth()) { Text("VIEW REQUIREMENTS") } }
    }
}

@Composable private fun UsagePrototype(vm: DraftLockViewModel, onBack: () -> Unit) { Column(Modifier.fillMaxSize().padding(18.dp)) { Header("ANDROID USAGE", "USAGE", "LIVE"); Text("Selected applications are measured through Android UsageStats."); Button(onClick = onBack) { Text("BACK") } } }
@Composable private fun SyncPrototype(name: String, autoSave: Boolean, onBack: () -> Unit) { Column(Modifier.fillMaxSize().padding(18.dp)) { Header("CLOUD SYNC", "GOOGLE DOCS", if (autoSave) "ON" else "OFF"); Text("ACTIVE FILE\n$name\nSaved locally • syncing in background"); Text("DESTINATION\nWriting / DND"); Button(onClick = onBack) { Text("BACK") } } }

@Composable private fun HistoryPrototype(history: List<DailyRecord>, onBack: () -> Unit, onAnalytics: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Header("PROGRESS LOG", "HISTORY", "12 DAY STREAK") }
        items(history) { r -> StatCard(r.dayKey, "${r.words} WORDS", if (r.overrideUsed) "OVERRIDE USED" else if (r.words >= r.quota) "GOAL COMPLETE" else "GOAL MISSED", r.words >= r.quota) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = onAnalytics, Modifier.weight(1f)) { Text("ANALYTICS") }; Button(onClick = onBack, Modifier.weight(1f)) { Text("HOME") } } }
    }
}

@Composable private fun AnalyticsPrototype(history: List<DailyRecord>, onBack: () -> Unit) {
    val total = history.sumOf { it.words }
    val avg = if (history.isEmpty()) 0 else total / history.size
    val best = history.maxOfOrNull { it.words } ?: 0
    val completed = history.count { it.words >= it.quota }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Header("PROGRESS / ANALYTICS", "STATISTICS", "30 DAYS")
        StatCard("TOTAL WORDS", total.toString(), "LOCAL HISTORY")
        StatCard("DAILY AVG", avg.toString(), "WORDS")
        StatCard("GOALS COMPLETED", "$completed / ${history.size}", "DAYS")
        StatCard("BEST DAY", "$best WORDS", "LOCAL RECORD")
        Spacer(Modifier.height(12.dp)); Button(onClick = onBack) { Text("HISTORY") }
    }
}

@Composable private fun PermissionsPrototype(vm: DraftLockViewModel, context: android.content.Context, onAccount: () -> Unit, onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Header("ANDROID / PERMISSIONS", "SYSTEM ACCESS", "READY") }
        item { StatCard("USAGE ACCESS", if (vm.usageAccess) "OK" else "REQUIRED", "Track selected app time", vm.usageAccess) }
        item { StatCard("APP BLOCKING", if (vm.blockingAvailable) "OK" else "REQUIRED", "Enforce selected app locks", vm.blockingAvailable) }
        item { StatCard("GOOGLE ACCOUNT", "SETUP", "Read and edit selected Docs") }
        item { Button(onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)) }, Modifier.fillMaxWidth()) { Text("REVIEW PERMISSIONS") } }
        item { Button(onClick = onAccount, Modifier.fillMaxWidth()) { Text("GOOGLE ACCOUNT") } }
        item { TextButton(onClick = onBack) { Text("BACK") } }
    }
}

@Composable private fun AccountPrototype(onSetup: () -> Unit, onBack: () -> Unit) { Column(Modifier.fillMaxSize().padding(18.dp)) { Header("ACCOUNT / CLOUD", "GOOGLE ACCOUNT", "CONNECTED"); Text("WRITER ACCOUNT\nGoogle Drive + Docs access\n\nSYNC\nENABLED\n\nACCESS SCOPE\nDraftLock works with documents you select or that match your configured filter."); Button(onClick = onSetup, Modifier.fillMaxWidth()) { Text("GOOGLE DOCS / SETUP") }; TextButton(onClick = onBack) { Text("BACK") } } }
@Composable private fun SetupPrototype(vm: DraftLockViewModel, name: String, onBack: () -> Unit) { var folder by remember { mutableStateOf("Writing / DND") }; Column(Modifier.fillMaxSize().padding(18.dp)) { Header("GOOGLE DOCS / SETUP", "DESTINATION", "USE EXISTING DOC") ; Text("SELECTED\n$name"); OutlinedTextField(value = folder, onValueChange = { folder = it }, Modifier.fillMaxWidth(), label = { Text("FOLDER") }); Button(onClick = { vm.setGoogleFolder(folder); onBack() }, Modifier.fillMaxWidth()) { Text("SAVE DESTINATION") }; TextButton(onClick = onBack) { Text("BACK") } } }
@Composable private fun SettingsPrototype(vm: DraftLockViewModel, quota: Int, onBack: () -> Unit) { var q by remember(quota) { mutableStateOf(quota.toString()) }; Column(Modifier.fillMaxSize().padding(18.dp)) { Header("SYSTEM / CONFIG", "SETTINGS"); OutlinedTextField(value = q, onValueChange = { q = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("DAILY WORD GOAL") }); Button(onClick = { vm.setQuota(q.toIntOrNull() ?: quota); onBack() }, Modifier.fillMaxWidth()) { Text("SAVE") }; Text("RESET TIME\nDaily requirements reset at the configured local time\n\nEMERGENCY OVERRIDE\nRequires confirmation + history record\n\nRESET TODAY\nRecalculate local usage + writing totals"); TextButton(onClick = onBack) { Text("BACK") } } }
