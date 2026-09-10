package com.draftlock.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.draftlock.app.data.AppRequirement
import com.draftlock.app.data.DraftLockDatabase
import com.draftlock.app.data.LockedApp
import kotlin.math.min

private val Ink = Color(0xFFF4F4F0)
private val Muted = Color(0xFF8B8B86)
private val Panel = Color(0xFF151515)
private val Line = Color(0xFF30302D)
private val Accent = Color(0xFFB7FF4A)
private val Bg = Color(0xFF080808)

class PrototypeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DraftLockPrototype() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

private enum class Page { HOME, WRITE, RULES, DOCS, FILTER, EDIT, BUILDER, APP_REQUIREMENT, CONTROL, ACCESS, USAGE, SYNC, HISTORY, ANALYTICS, PERMISSIONS, ACCOUNT, SETUP, QUICK_ACCESS, SETTINGS }

@Composable
private fun DraftLockPrototype(vm: DraftLockViewModel = viewModel()) {
    val text by vm.text.collectAsStateWithLifecycle()
    val words by vm.todayWords.collectAsStateWithLifecycle()
    val quota by vm.quota.collectAsStateWithLifecycle()
    val name by vm.documentName.collectAsStateWithLifecycle()
    val autoSave by vm.googleAutoSave.collectAsStateWithLifecycle()
    val requirements by vm.requirements.collectAsStateWithLifecycle()
    val lockedApps by vm.lockedApps.collectAsStateWithLifecycle()
    val history by DraftLockDatabase.get(LocalContext.current).dao().observeRecentDays().collectAsStateWithLifecycle(initialValue = emptyList())
    var page by remember { mutableStateOf(Page.HOME) }
    val context = LocalContext.current

    BoxWithConstraints(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.TopCenter) {
        val scale = min(maxWidth.value / 390f, maxHeight.value / 844f)
        Box(Modifier.width(390.dp).height(844.dp).graphicsLayer(scaleX = scale, scaleY = scale)) {
            when (page) {
                Page.HOME -> Home(vm, words, quota, name, requirements, lockedApps, { page = Page.WRITE }, { page = Page.HISTORY }, { page = Page.ANALYTICS }, { page = Page.PERMISSIONS }, { page = Page.QUICK_ACCESS }, { page = Page.SETTINGS }, { page = Page.RULES }, { page = Page.DOCS })
                Page.WRITE -> Write(vm, text, words, name, { page = Page.DOCS }, { page = Page.HOME })
                Page.RULES -> Rules(vm, requirements, lockedApps, { page = Page.APP_REQUIREMENT }, { page = Page.BUILDER }, { page = Page.CONTROL }, { page = Page.HOME })
                Page.DOCS -> Docs(vm, name, autoSave, context, { page = Page.FILTER }, { page = Page.EDIT }, { page = Page.SYNC }, { page = Page.HOME })
                Page.FILTER -> Filter(vm, { page = Page.DOCS })
                Page.EDIT -> Edit(vm, text, words, { page = Page.DOCS })
                Page.BUILDER -> Builder(vm, { page = Page.RULES })
                Page.APP_REQUIREMENT -> AppRequirementScreen(vm, requirements, { page = Page.RULES })
                Page.CONTROL -> Control(requirements, lockedApps, words, quota, { page = Page.ACCESS }, { page = Page.RULES })
                Page.ACCESS -> Access(words, quota, { page = Page.WRITE }, { page = Page.RULES })
                Page.USAGE -> Usage(vm, { page = Page.HOME })
                Page.SYNC -> Sync(name, autoSave, { page = Page.DOCS })
                Page.HISTORY -> History(history, { page = Page.HOME }, { page = Page.ANALYTICS })
                Page.ANALYTICS -> Analytics(history, { page = Page.HISTORY })
                Page.PERMISSIONS -> Permissions(vm, context, { page = Page.ACCOUNT }, { page = Page.HOME })
                Page.ACCOUNT -> Account(context, { page = Page.SETUP }, { page = Page.PERMISSIONS })
                Page.SETUP -> Setup(vm, name, { page = Page.ACCOUNT })
                Page.QUICK_ACCESS -> QuickAccess(words, quota, requirements, { page = Page.WRITE }, { page = Page.HOME })
                Page.SETTINGS -> Settings(vm, quota, { page = Page.HOME })
            }
        }
    }
}

@Composable private fun Frame(kicker: String, title: String, badge: String? = null, bottom: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Bg)) {
        Text(kicker, Modifier.offset(19.dp, 23.dp), color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(title, Modifier.offset(19.dp, 36.dp), color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (badge != null) Pill(badge, 371.dp - badgeWidth(badge), 27.dp)
        Box(Modifier.offset(0.dp, 68.dp).fillMaxWidth().height(700.dp), content = content)
        if (bottom) BottomNav()
    }
}

private fun badgeWidth(s: String) = (s.length * 7 + 22).dp

@Composable private fun Pill(text: String, x: Dp, y: Dp, accent: Boolean = false) {
    Box(Modifier.offset(x, y).height(31.dp).width(badgeWidth(text)).background(if (accent) Accent else Panel), contentAlignment = Alignment.Center) {
        Text(text, color = if (accent) Color.Black else Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun ButtonLine(text: String, y: Int, onClick: () -> Unit) {
    Box(Modifier.offset(19.dp, y.dp).width(352.dp).height(48.dp).background(Panel).clickable { onClick() }, contentAlignment = Alignment.CenterStart) {
        Text(text, Modifier.padding(horizontal = 17.dp), color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun BottomNav() {
    val items = listOf("01" to "HOME", "02" to "WRITE", "03" to "RULES", "04" to "DOCS")
    Row(Modifier.offset(19.dp, 778.dp).width(352.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        items.forEach { (n, t) -> Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(n, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold); Text(t, color = Muted, fontSize = 12.sp) } }
    }
}

@Composable private fun Home(vm: DraftLockViewModel, words: Int, quota: Int, name: String, reqs: List<AppRequirement>, apps: List<LockedApp>, onWrite: () -> Unit, onHistory: () -> Unit, onAnalytics: () -> Unit, onPermissions: () -> Unit, onQuick: () -> Unit, onSettings: () -> Unit, onRules: () -> Unit, onDocs: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.offset(19.dp, 178.dp).width(352.dp)) {
            Text("WRITING ACCOUNTABILITY SYSTEM", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("DRAFTLOCK", color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            Text("Write your draft. Clear your requirements. Keep distractions locked until the work is done.", color = Ink, fontSize = 15.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 10.dp))
        }
        StatusPair("SYNC", "GOOGLE DOCS", 19.dp, 345.67.dp)
        StatusPair("CONTROL", "ACTIVE", 200.dp, 345.67.dp)
        Text("SESSION 07", Modifier.offset(19.dp, 23.dp), color = Muted, fontSize = 13.sp)
        BottomNav()
    }
}

@Composable private fun StatusPair(a: String, b: String, x: Dp, y: Dp) {
    Column(Modifier.offset(x, y).width(171.dp).height(78.dp).background(Panel).padding(14.dp)) { Text(a, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold); Text(b, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
}

@Composable private fun Write(vm: DraftLockViewModel, text: String, words: Int, name: String, onDocs: () -> Unit, onHome: () -> Unit) {
    var draft by remember(text) { mutableStateOf(text) }
    Frame("WRITE / ACTIVE DOCUMENT", "DND CHAPTER 12", "SAVED", true) {
        Text("TOTAL 3,842 WORDS", Modifier.offset(19.dp, 5.dp), color = Muted, fontSize = 13.sp)
        Text("TODAY +$words", Modifier.offset(315.dp, 5.dp), color = Ink, fontSize = 13.sp)
        OutlinedTextField(draft, { draft = it; vm.onTextChanged(it) }, Modifier.offset(36.dp, 46.dp).width(318.dp).height(216.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Panel, focusedContainerColor = Panel, unfocusedBorderColor = Line, focusedBorderColor = Accent, unfocusedTextColor = Ink, focusedTextColor = Ink), placeholder = { Text("The corridor narrowed as the lights began to flicker.\n\nEgo stopped, listening for the sound beneath the ventilation hum. Something had followed him down three floors, but the footsteps had vanished.\n\nThe door ahead had no handle. He raised his hand anyway.", color = Ink, fontSize = 15.sp) })
        Text("CURSOR ACTIVE • EDITS COUNT TOWARD TODAY", Modifier.offset(36.dp, 287.dp), color = Muted, fontSize = 13.sp)
        Text("AUTOSAVED 8 SEC AGO", Modifier.offset(19.dp, 480.dp), color = Muted, fontSize = 13.sp)
        Pill("DOCS SYNC", 299.dp, 471.dp, true)
    }
}

@Composable private fun Rules(vm: DraftLockViewModel, reqs: List<AppRequirement>, apps: List<LockedApp>, onApp: () -> Unit, onBuilder: () -> Unit, onControl: () -> Unit, onHome: () -> Unit) {
    Frame("ACCOUNTABILITY", "REQUIREMENTS", "${reqs.count { it.enabled }} / 3 ACTIVE", true) {
        Text("WRITE 1,000 WORDS", Modifier.offset(36.dp, 32.dp), color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Pill("${vm.todayWords.value} / 1000", 276.dp, 28.dp)
        Text("Lichess", Modifier.offset(36.dp, 131.dp), color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Pill("18 / 30 MIN", 271.dp, 127.dp)
        Text("Acode", Modifier.offset(36.dp, 230.dp), color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Pill("30 / 30 MIN", 271.dp, 226.dp, true)
        Text("APP REQUIREMENTS", Modifier.offset(36.dp, 327.dp), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("CHOOSE WHICH APPS TO REQUIRE", Modifier.offset(36.dp, 348.dp), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Each app can have its own time target. Choose none, one, or several apps.", Modifier.offset(36.dp, 372.dp), color = Muted, fontSize = 13.sp)
        ButtonLine("APP REQUIREMENT", 432, onApp)
        ButtonLine("RULE BUILDER", 490, onBuilder)
        ButtonLine("ACCESS CONTROL", 548, onControl)
    }
}

@Composable private fun Docs(vm: DraftLockViewModel, name: String, auto: Boolean, context: Context, onFilter: () -> Unit, onEdit: () -> Unit, onSync: () -> Unit, onHome: () -> Unit) {
    Frame("GOOGLE DOCS", "LIBRARY", "SYNCED", true) {
        Box(Modifier.offset(19.dp, 12.dp).width(352.dp).height(48.dp).background(Panel)) { Text("SEARCH DOCUMENTS", Modifier.padding(17.dp), color = Muted, fontSize = 13.sp) }
        Text("FILTER", Modifier.offset(19.dp, 81.dp), color = Muted, fontSize = 13.sp)
        Pill("NAME: DND*", 294.dp, 72.dp)
        DocRow("DND CHAPTER 12", "3,842 words • edited 2m ago", "EDIT", 130, onEdit)
        DocRow("DND CHAPTER 11", "4,106 words • yesterday", "EDIT", 212) {}
        DocRow("NOVEL NOTES", "Not tracked", "OPEN", 294) {}
        ButtonLine("CLOUD SYNC", 365, onSync)
        ButtonLine("CONNECT GOOGLE ACCOUNT", 423) { GoogleOAuthManager(context).startAuthorization() }
        TextButton(onClick = onFilter, Modifier.offset(19.dp, 481.dp)) { Text("FILTER", color = Accent) }
    }
}

@Composable private fun DocRow(title: String, detail: String, status: String, y: Int, onClick: () -> Unit) {
    Row(Modifier.offset(32.dp, y.dp).width(338.dp).height(64.dp).clickable { onClick() }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(detail, color = Muted, fontSize = 13.sp) }
        Pill(status, 314.dp, (y + 12).dp, status == "OPEN")
    }
}

@Composable private fun Filter(vm: DraftLockViewModel, back: () -> Unit) { var prefix by remember { mutableStateOf("DND") }; var folder by remember { mutableStateOf("Writing / DND") }; Frame("LIBRARY / FILTER", "FILTER DOCS", "DND*") { Text("NAME STARTS WITH", Modifier.offset(19.dp, 6.dp), color = Muted, fontSize = 13.sp); OutlinedTextField(prefix, { prefix = it }, Modifier.offset(19.dp, 30.dp).width(352.dp), colors = fieldColors()); Text("DND", Modifier.offset(36.dp, 86.dp), color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold); Text("Only Google Docs whose filename begins with DND appear in the writing library.", Modifier.offset(19.dp, 139.dp).width(352.dp), color = Muted, fontSize = 13.sp); Text("INCLUDE EDITABLE DOCS", Modifier.offset(19.dp, 186.dp), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Edits count toward today’s goal.", Modifier.offset(19.dp, 210.dp), color = Muted, fontSize = 13.sp); Pill("ON", 338.dp, 216.dp, true); Text("FOLDER", Modifier.offset(19.dp, 252.dp), color = Muted, fontSize = 13.sp); Text(folder, Modifier.offset(244.dp, 246.dp), color = Ink, fontSize = 16.sp); ButtonLine("APPLY FILTER", 301, back) } }

@Composable private fun Edit(vm: DraftLockViewModel, text: String, words: Int, back: () -> Unit) { var draft by remember(text) { mutableStateOf(text) }; Frame("GOOGLE DOC / EDIT MODE", "CHAPTER 12", "SYNCED") { Text("DOC TOTAL", Modifier.offset(19.dp, 6.dp), color = Muted, fontSize = 13.sp); Text("3,842", Modifier.offset(19.dp, 30.dp), color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("COUNTED TODAY", Modifier.offset(200.dp, 6.dp), color = Muted, fontSize = 13.sp); Text(words.toString(), Modifier.offset(200.dp, 30.dp), color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("THE LOWER HALL", Modifier.offset(36.dp, 113.dp), color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold); OutlinedTextField(draft, { draft = it; vm.onTextChanged(it) }, Modifier.offset(36.dp, 141.dp).width(318.dp).height(194.dp), colors = fieldColors(), placeholder = { Text("The door had no handle. Ego placed his palm against the metal and waited.\n\nThe lock clicked.\n\nThe text cursor remains active inside the Google Doc. Additions, edits, and deletions are tracked locally and synchronized in the background.", color = Ink, fontSize = 15.sp) }); Text("EVERY EDIT UPDATES TODAY’S TOTAL", Modifier.offset(19.dp, 584.dp), color = Muted, fontSize = 13.sp); Text("+$words", Modifier.offset(332.dp, 578.dp), color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold) } }

@Composable private fun Builder(vm: DraftLockViewModel, back: () -> Unit) { Frame("ACCOUNTABILITY / LOGIC", "RULE BUILDER", "AND") { Text("WRITE 1,000 WORDS", Modifier.offset(36.dp, 26.dp), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold); Pill("GOAL", 310.dp, 23.dp); Text("AND", Modifier.offset(19.dp, 70.dp), color = Muted, fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("LICHESS • 30 MIN", Modifier.offset(36.dp, 162.dp), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold); Pill("PENDING", 293.dp, 158.dp); Text("OR", Modifier.offset(19.dp, 219.dp), color = Muted, fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("ACODE • 30 MIN", Modifier.offset(36.dp, 269.dp), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold); Pill("COMPLETE", 288.dp, 265.dp, true); Text("Nested AND / OR branches evaluate each app requirement independently.", Modifier.offset(19.dp, 326.dp).width(352.dp), color = Muted, fontSize = 13.sp); ButtonLine("SAVE RULE", 412, back) } }

@Composable private fun AppRequirementScreen(vm: DraftLockViewModel, reqs: List<AppRequirement>, back: () -> Unit) { Frame("ACCOUNTABILITY / APP", "APP REQUIREMENT") { Text("APPS ON THIS PHONE", Modifier.offset(36.dp, 30.dp), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold); Text("Lichess", Modifier.offset(36.dp, 51.dp), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("SELECT TO ADD • 30 MIN TARGET", Modifier.offset(36.dp, 83.dp), color = Muted, fontSize = 13.sp); Text("Acode", Modifier.offset(36.dp, 118.dp), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("SELECT TO ADD • 30 MIN TARGET", Modifier.offset(36.dp, 150.dp), color = Muted, fontSize = 13.sp); Pill("30 MIN", 316.dp, 160.dp); Text("Chrome", Modifier.offset(36.dp, 244.dp), color = Ink, fontSize = 13.sp); Text("YouTube   •   Discord   •   More installed apps", Modifier.offset(36.dp, 265.dp), color = Muted, fontSize = 13.sp); ButtonLine("NONE / ADD SELECTED APPS", 329, back) } }

@Composable private fun Control(reqs: List<AppRequirement>, apps: List<LockedApp>, words: Int, quota: Int, onAccess: () -> Unit, back: () -> Unit) { Frame("ACCOUNTABILITY / CONTROL", "BLOCKED APPS", "2 ACTIVE LOCKS") { DocRow("Lichess", "Unlock: 30 min today", "LOCKED", 21) {}; DocRow("Acode", "Unlock: 30 min today", "CLEAR", 103) {}; Text("ENFORCEMENT", Modifier.offset(36.dp, 189.dp), color = Muted, fontSize = 13.sp); Text("UsageStats measures time. Device-management capability enforces package suspension when configured.", Modifier.offset(36.dp, 210.dp).width(318.dp), color = Ink, fontSize = 13.sp); ButtonLine("ACCESS CONTROL", 286, onAccess) } }

@Composable private fun Access(words: Int, quota: Int, onWrite: () -> Unit, back: () -> Unit) { Box(Modifier.fillMaxSize().background(Bg)) { Text("ACCESS CONTROL", Modifier.offset(142.dp, 219.dp), color = Muted, fontSize = 13.sp); Text("LICHESS LOCKED", Modifier.offset(76.dp, 246.dp), color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Bold); Text("Finish today’s requirement to continue.", Modifier.offset(64.dp, 285.dp), color = Muted, fontSize = 14.sp); StatusPair("WRITING", "$words / $quota", 19.dp, 380.dp); StatusPair("LICHESS", "18 / 30", 200.dp, 380.dp); ButtonLine("WRITE ${maxOf(quota - words, 0)} WORDS", 470, onWrite); ButtonLine("VIEW REQUIREMENTS", 530, back) } }

@Composable private fun Usage(vm: DraftLockViewModel, back: () -> Unit) { Frame("ANDROID USAGE", "USAGE", "LIVE") { DocRow("Lichess", "30 min requirement", "18 MIN", 27) {}; DocRow("Acode", "Requirement: 30 min", "30 MIN", 109) {}; Text("APPS DETECTED ON THIS PHONE", Modifier.offset(36.dp, 194.dp), color = Muted, fontSize = 12.sp); Text("Placeholder app icons shown here; production UI uses each installed app’s real icon and name.", Modifier.offset(36.dp, 216.dp).width(318.dp), color = Muted, fontSize = 13.sp) } }

@Composable private fun Sync(name: String, auto: Boolean, back: () -> Unit) { Frame("CLOUD SYNC", "GOOGLE DOCS", "ON") { Text("BACKGROUND SYNC", Modifier.offset(96.dp, 25.dp), color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold); Text("Writing never pauses while Docs syncs.", Modifier.offset(96.dp, 49.dp), color = Muted, fontSize = 13.sp); Text("ACTIVE FILE", Modifier.offset(36.dp, 121.dp), color = Muted, fontSize = 13.sp); Text("DND CHAPTER 12", Modifier.offset(217.dp, 116.dp), color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold); Text("Saved locally • syncing in background", Modifier.offset(36.dp, 172.dp), color = Muted, fontSize = 13.sp); Text("LAST SYNC", Modifier.offset(19.dp, 219.dp), color = Muted, fontSize = 18.sp); Text("14:42", Modifier.offset(322.dp, 219.dp), color = Ink, fontSize = 18.sp); Text("DESTINATION", Modifier.offset(19.dp, 255.dp), color = Muted, fontSize = 18.sp); Text("Writing / DND", Modifier.offset(244.dp, 255.dp), color = Ink, fontSize = 18.sp) } }

@Composable private fun History(records: List<com.draftlock.app.data.DailyRecord>, back: () -> Unit, analytics: () -> Unit) { Frame("PROGRESS LOG", "HISTORY", "12 DAY STREAK") { Text("STREAK", Modifier.offset(34.dp, 27.dp), color = Muted, fontSize = 18.sp); Text("12", Modifier.offset(34.dp, 51.dp), color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold); Text("MONTH", Modifier.offset(215.dp, 27.dp), color = Muted, fontSize = 18.sp); Text("18.4K", Modifier.offset(215.dp, 51.dp), color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold); DocRow("SEP 10", "DND Chapter 12 • goal complete", "1204", 125) {}; DocRow("SEP 09", "DND Chapter 11 • goal complete", "1086", 207) {}; DocRow("SEP 08", "Goal missed", "642", 289) {}; ButtonLine("ANALYTICS", 380, analytics) } }

@Composable private fun Analytics(records: List<com.draftlock.app.data.DailyRecord>, back: () -> Unit) { Frame("PROGRESS / ANALYTICS", "STATISTICS", "30 DAYS") { StatusPair("TOTAL WORDS", "31,842", 19.dp, 79.dp); StatusPair("DAILY AVG", "1,061", 200.dp, 79.dp); Stat("GOALS COMPLETED", "24 / 30", 326); Stat("BEST DAY", "1,842 WORDS", 362); Stat("LONGEST STREAK", "19 DAYS", 398) } }

@Composable private fun Stat(label: String, value: String, y: Int) { Row(Modifier.offset(19.dp, y.dp).width(352.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold); Text(value, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold) } }

@Composable private fun Permissions(vm: DraftLockViewModel, context: Context, account: () -> Unit, back: () -> Unit) { Frame("ANDROID / PERMISSIONS", "SYSTEM ACCESS", "READY") { Perm("USAGE ACCESS", "Track selected app time", 27); Perm("APP BLOCKING", "Enforce selected app locks", 109); Perm("GOOGLE ACCOUNT", "Read and edit selected Docs", 191); ButtonLine("REVIEW PERMISSIONS", 254, { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }); ButtonLine("GOOGLE ACCOUNT", 312, account) } }

@Composable private fun Perm(a: String, b: String, y: Int) { Text(a, Modifier.offset(90.dp, y.dp), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(b, Modifier.offset(90.dp, (y + 28).dp), color = Muted, fontSize = 13.sp); Pill("OK", 325.dp, (y + 5).dp, true) }

@Composable private fun Account(context: Context, setup: () -> Unit, back: () -> Unit) { Frame("ACCOUNT / CLOUD", "GOOGLE ACCOUNT", "CONNECTED") { Text("WRITER ACCOUNT", Modifier.offset(96.dp, 25.dp), color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold); Text("Google Drive + Docs access", Modifier.offset(96.dp, 49.dp), color = Muted, fontSize = 13.sp); Stat("SYNC", "ENABLED", 118); Text("ACCESS SCOPE", Modifier.offset(36.dp, 192.dp), color = Muted, fontSize = 13.sp); Text("DraftLock works with documents you select or that match your configured filter.", Modifier.offset(36.dp, 213.dp).width(318.dp), color = Ink, fontSize = 13.sp); ButtonLine("RECONNECT ACCOUNT", 277) { GoogleOAuthManager(context).startAuthorization() }; ButtonLine("GOOGLE DOCS / SETUP", 335, setup) } }

@Composable private fun Setup(vm: DraftLockViewModel, name: String, back: () -> Unit) { Frame("GOOGLE DOCS / SETUP", "DESTINATION") { Text("USE EXISTING DOC", Modifier.offset(36.dp, 27.dp), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold); Pill("SELECTED", 288.dp, 23.dp); ButtonLine("DND CHAPTER 12", 66) {}; Text("CREATE NEW DOC", Modifier.offset(36.dp, 164.dp), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold); Pill("OPTIONAL", 288.dp, 160.dp); Text("Filename: DND Chapter 13\nFolder: Writing / DND", Modifier.offset(36.dp, 204.dp), color = Muted, fontSize = 15.sp); ButtonLine("SAVE DESTINATION", 294, back) } }

@Composable private fun QuickAccess(words: Int, quota: Int, reqs: List<AppRequirement>, onWrite: () -> Unit, back: () -> Unit) { Frame("ANDROID / QUICK ACCESS", "HOME WIDGETS", "CONCEPT") { Text("DRAFTLOCK", Modifier.offset(36.dp, 23.dp), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("TODAY", Modifier.offset(326.dp, 29.dp), color = Muted, fontSize = 13.sp); Text("$words / $quota", Modifier.offset(36.dp, 57.dp), color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold); Text("REQUIREMENTS", Modifier.offset(36.dp, 162.dp), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold); Pill("1 PENDING", 282.dp, 158.dp); Text("LICHESS", Modifier.offset(36.dp, 202.dp), color = Ink, fontSize = 18.sp); Text("18 / 30M", Modifier.offset(276.dp, 202.dp), color = Muted, fontSize = 18.sp); Text("ACODE", Modifier.offset(36.dp, 234.dp), color = Ink, fontSize = 18.sp); Text("30 / 30M", Modifier.offset(276.dp, 234.dp), color = Muted, fontSize = 18.sp); ButtonLine("WRITE", 286, onWrite); Text("Tap widget → opens the active tracked document.", Modifier.offset(19.dp, 344.dp), color = Muted, fontSize = 13.sp) } }

@Composable private fun Settings(vm: DraftLockViewModel, quota: Int, back: () -> Unit) { Frame("SYSTEM / CONFIG", "SETTINGS") { Setting("DAILY WORD GOAL", "Minimum required writing", quota.toString(), 24); Setting("RESET TIME", "Daily requirements reset", "12:00 AM", 101); Setting("EMERGENCY OVERRIDE", "Requires confirmation + history record", "AVAILABLE", 179); Setting("RESET TODAY", "Recalculate local usage + writing totals", "OPEN", 257); ButtonLine("MANAGE GOOGLE ACCOUNT", 369, back) } }

@Composable private fun Setting(a: String, b: String, v: String, y: Int) { Text(a, Modifier.offset(32.dp, y.dp), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(b, Modifier.offset(32.dp, (y + 28).dp), color = Muted, fontSize = 13.sp); Pill(v, 286.dp, (y + 5).dp, v == "AVAILABLE") }

@Composable private fun fieldColors() = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Panel, focusedContainerColor = Panel, unfocusedBorderColor = Line, focusedBorderColor = Accent, unfocusedTextColor = Ink, focusedTextColor = Ink, unfocusedLabelColor = Muted, focusedLabelColor = Accent)
