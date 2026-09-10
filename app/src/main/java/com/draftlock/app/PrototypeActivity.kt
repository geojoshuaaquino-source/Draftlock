package com.draftlock.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.draftlock.app.data.AppRequirement
import com.draftlock.app.data.DailyRecord
import com.draftlock.app.data.DraftLockDatabase
import com.draftlock.app.data.LockedApp

class PrototypeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PrototypeApp() }
    }
}

private enum class Tab(val label: String) { HOME("HOME"), WRITE("WRITE"), RULES("RULES"), DOCS("DOCS") }
private enum class Page { HOME, WRITE, RULES, DOCS, FILTER, EDIT, REQUIREMENTS, BUILDER, APP_REQUIREMENT, CONTROL, ACCESS, USAGE, SYNC, HISTORY, ANALYTICS, PERMISSIONS, ACCOUNT, SETUP, QUICK_ACCESS, SETTINGS }

@Composable
private fun PrototypeApp(vm: DraftLockViewModel = viewModel()) {
    val text by vm.text.collectAsStateWithLifecycle()
    val words by vm.todayWords.collectAsStateWithLifecycle()
    val quota by vm.quota.collectAsStateWithLifecycle()
    val name by vm.documentName.collectAsStateWithLifecycle()
    val autoSave by vm.googleAutoSave.collectAsStateWithLifecycle()
    val requirements by vm.requirements.collectAsStateWithLifecycle()
    val lockedApps by vm.lockedApps.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val history by DraftLockDatabase.get(context).dao().observeRecentDays().collectAsStateWithLifecycle(initialValue = emptyList())
    var tab by remember { mutableStateOf(Tab.HOME) }
    var page by remember { mutableStateOf(Page.HOME) }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
            Surface(Modifier.fillMaxSize().padding(pad), color = MaterialTheme.colorScheme.background) {
                when (page) {
                    Page.HOME -> Home(vm, words, quota, name, requirements, lockedApps, { tab = Tab.WRITE; page = Page.WRITE }, { page = Page.HISTORY }, { page = Page.ANALYTICS }, { page = Page.PERMISSIONS }, { page = Page.SETTINGS }, { page = Page.QUICK_ACCESS })
                    Page.WRITE -> Write(vm, text, words, quota, name, { tab = Tab.DOCS; page = Page.DOCS })
                    Page.RULES -> Rules(vm, requirements, lockedApps, { page = Page.REQUIREMENTS }, { page = Page.BUILDER }, { page = Page.CONTROL })
                    Page.DOCS -> Docs(vm, name, autoSave, { page = Page.FILTER }, { page = Page.EDIT }, context, { page = Page.SYNC })
                    Page.FILTER -> Filter(vm, { page = Page.DOCS })
                    Page.EDIT -> EditDoc(vm, text, words, name, { page = Page.DOCS })
                    Page.REQUIREMENTS -> AppRequirements(vm, requirements, context, { page = Page.RULES })
                    Page.BUILDER -> RuleBuilder(vm, requirements, { page = Page.RULES })
                    Page.APP_REQUIREMENT -> AppRequirementEditor(vm, { page = Page.REQUIREMENTS })
                    Page.CONTROL -> Control(requirements, lockedApps, words, quota, vm, { page = Page.ACCESS })
                    Page.ACCESS -> AccessControl(words, quota, requirements, vm, { page = Page.RULES })
                    Page.USAGE -> Usage(vm, { page = Page.HOME })
                    Page.SYNC -> Sync(name, autoSave, { page = Page.DOCS })
                    Page.HISTORY -> History(history, { page = Page.HOME }, { page = Page.ANALYTICS })
                    Page.ANALYTICS -> Analytics(history, { page = Page.HISTORY })
                    Page.PERMISSIONS -> Permissions(vm, context, { page = Page.ACCOUNT }, { page = Page.HOME })
                    Page.ACCOUNT -> Account({ page = Page.SETUP }, { page = Page.PERMISSIONS })
                    Page.SETUP -> Setup(vm, name, { page = Page.ACCOUNT })
                    Page.QUICK_ACCESS -> QuickAccess(words, quota, requirements, { page = Page.HOME })
                    Page.SETTINGS -> Settings(vm, quota, { page = Page.HOME })
                }
            }
        }
    }
}

@Composable private fun Header(kicker: String, title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(kicker, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable private fun Item(title: String, value: String, detail: String, status: String? = null, onClick: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(value, fontWeight = FontWeight.Bold); Text(detail) }
            status?.let { Text(it, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable private fun NavLabels() { Text("01    HOME       02    WRITE       03    RULES       04    DOCS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }

@Composable private fun Home(vm: DraftLockViewModel, words: Int, quota: Int, name: String, requirements: List<AppRequirement>, lockedApps: List<LockedApp>, onWrite: () -> Unit, onHistory: () -> Unit, onAnalytics: () -> Unit, onPermissions: () -> Unit, onSettings: () -> Unit, onQuickAccess: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Header("WRITING ACCOUNTABILITY SYSTEM", "DRAFTLOCK", "Write your draft. Clear your requirements. Keep distractions locked until the work is done.") }
        item { Text("SYNCGOOGLE DOCS      CONTROLACTIVE", fontWeight = FontWeight.Bold) }
        item { Text("SESSION 07", style = MaterialTheme.typography.labelSmall) }
        item { Text("HOME", fontWeight = FontWeight.Bold) }
        item { Text("ON TRACK", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { Text("TODAY’S WRITING", fontWeight = FontWeight.Bold) }
        item { Text("$words / $quota WORDS", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item { Text("ACTIVE DRAFT\n$name") }
        item { Item("LICHESS", "18 / 30 min", "", "LOCKED") }
        item { Item("ACODE", "30 / 30 min", "", "CLEAR") }
        item { Button(onClick = onWrite, Modifier.fillMaxWidth()) { Text("CONTINUE WRITING") } }
        item { Text("REQUIREMENTS", fontWeight = FontWeight.Bold) }
        item { Text("${requirements.count { it.enabled }} / 3 ACTIVE") }
        item { Text("WRITE $quota WORDS\n$words / $quota") }
        item { Text("APP REQUIREMENTS", fontWeight = FontWeight.Bold) }
        item { lockedApps.take(2).forEach { Text("${it.displayName} • ${if (it.enabled) "LOCKED" else "CLEAR"}") } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { TextButton(onClick = onHistory) { Text("HISTORY") }; TextButton(onClick = onAnalytics) { Text("ANALYTICS") }; TextButton(onClick = onPermissions) { Text("PERMISSIONS") } } }
        item { TextButton(onClick = onQuickAccess) { Text("ANDROID / QUICK ACCESS") } }
        item { TextButton(onClick = onSettings) { Text("SYSTEM / CONFIG") } }
        item { NavLabels() }
    }
}

@Composable private fun Write(vm: DraftLockViewModel, text: String, words: Int, quota: Int, name: String, onDocs: () -> Unit) {
    var draft by remember(text) { mutableStateOf(text) }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Header("WRITE / ACTIVE DOCUMENT", name.uppercase(), "SAVED\nTOTAL 3,842 WORDS\nTODAY +$words")
        OutlinedTextField(value = draft, onValueChange = { draft = it; vm.onTextChanged(it) }, Modifier.fillMaxWidth().weight(1f), placeholder = { Text("The corridor narrowed as the lights began to flicker. Ego stopped, listening for the sound beneath the ventilation hum. Something had followed him down three floors, but the footsteps had vanished. The door ahead had no handle. He raised his hand anyway.") })
        Spacer(Modifier.height(8.dp))
        Text("CURSOR ACTIVE • EDITS COUNT TOWARD TODAY", style = MaterialTheme.typography.labelSmall)
        Text("AUTOSAVED 8 SEC AGO", style = MaterialTheme.typography.labelSmall)
        TextButton(onClick = onDocs) { Text("DOCS SYNC") }
        NavLabels()
    }
}

@Composable private fun Rules(vm: DraftLockViewModel, requirements: List<AppRequirement>, lockedApps: List<LockedApp>, onRequirements: () -> Unit, onBuilder: () -> Unit, onControl: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Header("ACCOUNTABILITY / REQUIREMENTS", "REQUIREMENTS", "${requirements.count { it.enabled }} / 3 ACTIVE") }
        item { Text("WRITE 1,000 WORDS\n${vm.todayWords.value} / 1000") }
        item { Text("APP REQUIREMENTS", fontWeight = FontWeight.Bold) }
        items(requirements) { r -> Item(r.displayName, "${r.requiredMinutes} MIN", if (r.enabled) "ACTIVE" else "DISABLED") }
        item { Button(onClick = onRequirements, Modifier.fillMaxWidth()) { Text("APP REQUIREMENT") } }
        item { Button(onClick = onBuilder, Modifier.fillMaxWidth()) { Text("RULE BUILDER") } }
        item { Divider() }
        item { Header("ACCOUNTABILITY / CONTROL", "BLOCKED APPS", "${lockedApps.count { it.enabled }} ACTIVE LOCKS") }
        items(lockedApps) { a -> Item(a.displayName, "Unlock: 30 min today", "", if (a.enabled) "LOCKED" else "CLEAR") }
        item { Text("ENFORCEMENT\nUsageStats measures time. Device-management capability enforces package suspension when configured.") }
        item { Button(onClick = onControl, Modifier.fillMaxWidth()) { Text("ACCESS CONTROL") } }
        item { NavLabels() }
    }
}

@Composable private fun Docs(vm: DraftLockViewModel, name: String, autoSave: Boolean, onFilter: () -> Unit, onEdit: () -> Unit, context: android.content.Context, onSync: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Header("GOOGLE DOCS", "LIBRARY", "SYNCED") }
        item { OutlinedTextField(value = name, onValueChange = vm::setDocumentName, Modifier.fillMaxWidth(), label = { Text("SEARCH DOCUMENTS") }) }
        item { Text("FILTER") }
        item { Text("NAME: DND*") }
        item { Item("DND CHAPTER 12", "3,842 words", "edited 2m ago", "EDIT", onEdit) }
        item { Item("DND CHAPTER 11", "4,106 words", "yesterday", "EDIT") }
        item { Item("NOVEL NOTES", "Not tracked", "", "OPEN") }
        item { Divider() }
        item { Text("CLOUD SYNC", fontWeight = FontWeight.Bold) }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("BACKGROUND SYNC", Modifier.weight(1f)); Switch(autoSave, vm::setGoogleAutoSave) } }
        item { Text("Writing never pauses while Docs syncs.") }
        item { Text("ACTIVE FILE\nDND CHAPTER 12\nSaved locally • syncing in background") }
        item { Text("DESTINATION\nWriting / DND") }
        item { Button(onClick = onSync, Modifier.fillMaxWidth()) { Text("CLOUD SYNC") } }
        item { Button(onClick = { GoogleOAuthManager(context).startAuthorization() }, Modifier.fillMaxWidth()) { Text("CONNECT GOOGLE ACCOUNT") } }
        item { TextButton(onClick = onFilter) { Text("FILTER") } }
        item { NavLabels() }
    }
}

@Composable private fun Filter(vm: DraftLockViewModel, onBack: () -> Unit) { var prefix by remember { mutableStateOf("DND") }; var folder by remember { mutableStateOf("Writing / DND") }; Column(Modifier.fillMaxSize().padding(18.dp)) { Header("LIBRARY / FILTER", "FILTER DOCS", "DND*"); OutlinedTextField(prefix, { prefix = it }, Modifier.fillMaxWidth(), label = { Text("NAME STARTS WITH") }); Text("Only Google Docs whose filename begins with DND appear in the writing library."); Text("INCLUDE EDITABLE DOCS\nEdits count toward today’s goal.\nON"); OutlinedTextField(folder, { folder = it }, Modifier.fillMaxWidth(), label = { Text("FOLDER") }); Button(onClick = { vm.setGoogleFolder(folder); onBack() }, Modifier.fillMaxWidth()) { Text("APPLY FILTER") } } }

@Composable private fun EditDoc(vm: DraftLockViewModel, text: String, words: Int, name: String, onBack: () -> Unit) { var draft by remember(text) { mutableStateOf(text) }; Column(Modifier.fillMaxSize().padding(18.dp)) { Header("GOOGLE DOC / EDIT MODE", "CHAPTER 12", "SYNCED"); Text("DOC TOTAL 3,842\nCOUNTED TODAY $words"); OutlinedTextField(draft, { draft = it; vm.onTextChanged(it) }, Modifier.fillMaxWidth().weight(1f), placeholder = { Text("THE LOWER HALL\nThe door had no handle. Ego placed his palm against the metal and waited. The lock clicked. The text cursor remains active inside the Google Doc. Additions, edits, and deletions are tracked locally and synchronized in the background.") }); Text("EVERY EDIT UPDATES TODAY’S TOTAL\n+$words\nACCOUNTABILITY"); Button(onClick = onBack, Modifier.fillMaxWidth()) { Text("DONE") } } }

@Composable private fun AppRequirements(vm: DraftLockViewModel, requirements: List<AppRequirement>, context: android.content.Context, onBack: () -> Unit) { LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { Header("ACCOUNTABILITY / APP", "APP REQUIREMENT", "APPS ON THIS PHONE") }; item { Text("CHOOSE WHICH APPS TO REQUIRE\nEach app can have its own time target. Choose none, one, or several apps.") }; items(requirements) { r -> Item(r.displayName, "30 MIN", "SELECT TO ADD • 30 MIN TARGET") }; item { Text("Chrome\nYouTube   •   Discord   •   More installed apps") }; item { TextButton(onClick = onBack) { Text("NONE / ADD SELECTED APPS") } } } }

@Composable private fun RuleBuilder(vm: DraftLockViewModel, requirements: List<AppRequirement>, onBack: () -> Unit) { Column(Modifier.fillMaxSize().padding(18.dp)) { Header("ACCOUNTABILITY / LOGIC", "RULE BUILDER"); Text("AND\nWRITE 1,000 WORDS\nGOAL\nAND\nLICHESS • 30 MIN\nPENDING\nOR\nACODE • 30 MIN\nCOMPLETE"); Text("Nested AND / OR branches evaluate each app requirement independently."); Button(onClick = { vm.setLogic("AND"); onBack() }, Modifier.fillMaxWidth()) { Text("SAVE RULE") } } }

@Composable private fun AppRequirementEditor(vm: DraftLockViewModel, onBack: () -> Unit) { Column(Modifier.fillMaxSize().padding(18.dp)) { Header("ACCOUNTABILITY / APP", "APP REQUIREMENT", "30 MIN"); Text("APPS ON THIS PHONE\nLichess\nSELECT TO ADD • 30 MIN TARGET\nAcode\nSELECT TO ADD • 30 MIN TARGET"); Button(onClick = onBack, Modifier.fillMaxWidth()) { Text("SAVE") } } }

@Composable private fun Control(requirements: List<AppRequirement>, lockedApps: List<LockedApp>, words: Int, quota: Int, vm: DraftLockViewModel, onAccess: () -> Unit) { Column(Modifier.fillMaxSize().padding(18.dp)) { Header("ACCOUNTABILITY / CONTROL", "BLOCKED APPS", "${lockedApps.count { it.enabled }} ACTIVE LOCKS"); lockedApps.forEach { Text("${it.displayName}\nUnlock: 30 min today\n${if (it.enabled) "LOCKED" else "CLEAR"}") }; Text("ENFORCEMENT\nUsageStats measures time. Device-management capability enforces package suspension when configured."); Button(onClick = onAccess, Modifier.fillMaxWidth()) { Text("ACCESS CONTROL") } } }

@Composable private fun AccessControl(words: Int, quota: Int, requirements: List<AppRequirement>, vm: DraftLockViewModel, onBack: () -> Unit) { Column(Modifier.fillMaxSize().padding(18.dp)) { Header("ACCESS CONTROL", "LICHESS LOCKED", "Finish today’s requirement to continue."); Text("WRITING\n$words / $quota\nLICHESS\n18 / 30"); Text("WRITE ${maxOf(0, quota - words)} WORDS"); Button(onClick = onBack, Modifier.fillMaxWidth()) { Text("VIEW REQUIREMENTS") } } }

@Composable private fun Usage(vm: DraftLockViewModel, onBack: () -> Unit) { Column(Modifier.fillMaxSize().padding(18.dp)) { Header("ANDROID USAGE", "USAGE", "LIVE"); Text("Lichess\n30 min requirement\n18 MIN\n\nAcode\nRequirement: 30 min\n30 MIN\n\nAPPS DETECTED ON THIS PHONE\nPlaceholder app icons shown here; production UI uses each installed app’s real icon and name."); Button(onClick = onBack) { Text("BACK") } } }

@Composable private fun Sync(name: String, autoSave: Boolean, onBack: () -> Unit) { Column(Modifier.fillMaxSize().padding(18.dp)) { Header("CLOUD SYNC", "GOOGLE DOCS", "ON"); Text("BACKGROUND SYNC\nWriting never pauses while Docs syncs.\n\nACTIVE FILE\n$name\nSaved locally • syncing in background\n\nLAST SYNC\n14:42\n\nDESTINATION\nWriting / DND"); Button(onClick = onBack) { Text("BACK") } } }

@Composable private fun History(history: List<DailyRecord>, onBack: () -> Unit, onAnalytics: () -> Unit) { LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { Header("PROGRESS LOG", "HISTORY", "12 DAY STREAK"); Text("STREAK\n12\nMONTH\n18.4K") }; items(history) { r -> Item(r.dayKey, "${r.words}", if (r.overrideUsed) "override used" else if (r.words >= r.quota) "goal complete" else "Goal missed") }; item { Text("SEP 10\nDND Chapter 12 • goal complete\n1204\n\nSEP 09\nDND Chapter 11 • goal complete\n1086\n\nSEP 08\nGoal missed\n642") }; item { Row { Button(onClick = onAnalytics) { Text("ANALYTICS") }; Spacer(Modifier.width(8.dp)); Button(onClick = onBack) { Text("HOME") } } } } }

@Composable private fun Analytics(history: List<DailyRecord>, onBack: () -> Unit) { val total = history.sumOf { it.words }; val avg = if (history.isEmpty()) 0 else total / history.size; val best = history.maxOfOrNull { it.words } ?: 0; val completed = history.count { it.words >= it.quota }; Column(Modifier.fillMaxSize().padding(18.dp)) { Header("PROGRESS / ANALYTICS", "STATISTICS", "30 DAYS"); Text("TOTAL WORDS${if (history.isEmpty()) "31,842" else total}\nDAILY AVG${if (history.isEmpty()) "1,061" else avg}\nGOALS COMPLETED\n${if (history.isEmpty()) "24 / 30" else "$completed / ${history.size}"}\nBEST DAY\n${if (history.isEmpty()) "1,842 WORDS" else "$best WORDS"}\nLONGEST STREAK\n19 DAYS"); Button(onClick = onBack) { Text("HISTORY") } } }

@Composable private fun Permissions(vm: DraftLockViewModel, context: android.content.Context, onAccount: () -> Unit, onBack: () -> Unit) { Column(Modifier.fillMaxSize().padding(18.dp)) { Header("ANDROID / PERMISSIONS", "SYSTEM ACCESS", "READY"); Text("USAGE ACCESS\nTrack selected app time\n${if (vm.usageAccess) "OK" else "REQUIRED"}\n\nAPP BLOCKING\nEnforce selected app locks\n${if (vm.blockingAvailable) "OK" else "REQUIRED"}\n\nGOOGLE ACCOUNT\nRead and edit selected Docs\nOK"); Button(onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)) }, Modifier.fillMaxWidth()) { Text("REVIEW PERMISSIONS") }; Button(onClick = onAccount, Modifier.fillMaxWidth()) { Text("GOOGLE ACCOUNT") }; TextButton(onClick = onBack) { Text("BACK") } } }

@Composable private fun Account(onSetup: () -> Unit, onBack: () -> Unit) { Column(Modifier.fillMaxSize().padding(18.dp)) { Header("ACCOUNT / CLOUD", "GOOGLE ACCOUNT", "CONNECTED"); Text("WRITER ACCOUNT\nGoogle Drive + Docs access\n\nSYNC\nENABLED\n\nACCESS SCOPE\nDraftLock works with documents you select or that match your configured filter."); Button(onClick = onSetup, Modifier.fillMaxWidth()) { Text("GOOGLE DOCS / SETUP") }; TextButton(onClick = onBack) { Text("BACK") } } }

@Composable private fun Setup(vm: DraftLockViewModel, name: String, onBack: () -> Unit) { var folder by remember { mutableStateOf("Writing / DND") }; Column(Modifier.fillMaxSize().padding(18.dp)) { Header("GOOGLE DOCS / SETUP", "DESTINATION", "USE EXISTING DOC"); Text("SELECTED\n$name\n\nCREATE NEW DOC\nOPTIONAL\nFilename: DND Chapter 13\nFolder: Writing / DND"); OutlinedTextField(folder, { folder = it }, Modifier.fillMaxWidth(), label = { Text("FOLDER") }); Button(onClick = { vm.setGoogleFolder(folder); onBack() }, Modifier.fillMaxWidth()) { Text("SAVE DESTINATION") } } }

@Composable private fun QuickAccess(words: Int, quota: Int, requirements: List<AppRequirement>, onBack: () -> Unit) { Column(Modifier.fillMaxSize().padding(18.dp)) { Header("ANDROID / QUICK ACCESS", "HOME WIDGETS", "CONCEPT"); Text("DRAFTLOCK\nTODAY\n$words / $quota\n\nREQUIREMENTS\n1 PENDING\nLICHESS\n18 / 30M\nACODE\n30 / 30M\n\nWRITE\nTap widget → opens the active tracked document."); Button(onClick = onBack) { Text("HOME") } } }

@Composable private fun Settings(vm: DraftLockViewModel, quota: Int, onBack: () -> Unit) { var q by remember(quota) { mutableStateOf(quota.toString()) }; Column(Modifier.fillMaxSize().padding(18.dp)) { Header("SYSTEM / CONFIG", "SETTINGS"); Text("DAILY WORD GOAL\nMinimum required writing"); OutlinedTextField(q, { q = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("1,000") }); Text("RESET TIME\nDaily requirements reset\n12:00 AM\n\nEMERGENCY OVERRIDE\nRequires confirmation + history record\nAVAILABLE\n\nRESET TODAY\nRecalculate local usage + writing totals\nOPEN"); Button(onClick = { vm.setQuota(q.toIntOrNull() ?: quota); onBack() }, Modifier.fillMaxWidth()) { Text("SAVE") }; TextButton(onClick = onBack) { Text("MANAGE GOOGLE ACCOUNT") } } }
