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
import com.draftlock.app.data.DailyRecord
import com.draftlock.app.data.LockedApp
import kotlin.math.min

private val Ink = Color(0xFFF4F4F0)
private val Muted = Color(0xFF8B8B86)
private val Panel = Color(0xFF151515)
private val Line = Color(0xFF30302D)
private val Accent = Color(0xFFB7FF4A)
private val Bg = Color(0xFF080808)

private val LocalPageNavigation = staticCompositionLocalOf<(Page) -> Unit> { {} }

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

private enum class Page { SPLASH, HOME, WRITE, RULES, DOCS, FILTER, EDIT, BUILDER, APP_REQUIREMENT, CONTROL, ACCESS, USAGE, SYNC, HISTORY, ANALYTICS, PERMISSIONS, ACCOUNT, SETUP, QUICK_ACCESS, SETTINGS }

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
    var page by remember { mutableStateOf(Page.SPLASH) }
    val context = LocalContext.current

    CompositionLocalProvider(LocalPageNavigation provides { page = it }) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.TopCenter) {
            val scale = min(maxWidth.value / 390f, maxHeight.value / 844f)
            Box(Modifier.width(390.dp).height(844.dp).graphicsLayer(scaleX = scale, scaleY = scale)) {
                when (page) {
                    Page.SPLASH -> Splash { page = Page.HOME }
                    Page.HOME -> Home(words, quota, { page = Page.WRITE })
                    Page.WRITE -> Write(vm, text, words)
                    Page.RULES -> Rules(vm, requirements, { page = Page.APP_REQUIREMENT }, { page = Page.BUILDER }, { page = Page.CONTROL })
                    Page.DOCS -> Docs(context, { page = Page.FILTER }, { page = Page.EDIT }, { page = Page.SYNC })
                    Page.FILTER -> Filter { page = Page.DOCS }
                    Page.EDIT -> Edit(vm, text, words)
                    Page.BUILDER -> Builder { page = Page.RULES }
                    Page.APP_REQUIREMENT -> AppRequirementScreen { page = Page.RULES }
                    Page.CONTROL -> Control { page = Page.ACCESS }
                    Page.ACCESS -> Access(words, quota, { page = Page.WRITE }, { page = Page.RULES })
                    Page.USAGE -> Usage()
                    Page.SYNC -> Sync()
                    Page.HISTORY -> History(history, { page = Page.ANALYTICS })
                    Page.ANALYTICS -> Analytics(history)
                    Page.PERMISSIONS -> Permissions(context, { page = Page.ACCOUNT })
                    Page.ACCOUNT -> Account(context, { page = Page.SETUP })
                    Page.SETUP -> Setup { page = Page.ACCOUNT }
                    Page.QUICK_ACCESS -> QuickAccess(words, quota, { page = Page.WRITE })
                    Page.SETTINGS -> Settings(quota, { page = Page.ACCOUNT })
                }
            }
        }
    }
}

@Composable private fun Splash(onContinue: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Bg)) {
        Text("WRITING ACCOUNTABILITY SYSTEM", Modifier.offset(86.dp, 178.dp), color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text("DRAFTLOCK", Modifier.offset(96.dp, 205.dp), color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text("Write your draft. Clear your requirements. Keep distractions locked until the work is done.", Modifier.offset(49.dp, 251.dp).width(292.dp), color = Ink, fontSize = 15.sp, lineHeight = 18.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Box(Modifier.offset(19.dp, 465.dp).width(352.dp).height(48.dp).background(Panel).clickable { onContinue() }, contentAlignment = Alignment.Center) {
            Text("CONTINUE", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Text("SYNC", Modifier.offset(19.dp, 345.dp), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("GOOGLE DOCS", Modifier.offset(19.dp, 369.dp), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("CONTROL", Modifier.offset(200.dp, 345.dp), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("ACTIVE", Modifier.offset(200.dp, 369.dp), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("SESSION 07", Modifier.offset(19.dp, 23.dp), color = Muted, fontSize = 13.sp)
    }
}

@Composable private fun Home(words: Int, quota: Int, onWrite: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Bg)) {
        Text("SESSION 07", Modifier.offset(19.dp, 23.dp), color = Muted, fontSize = 13.sp)
        Text("HOME", Modifier.offset(19.dp, 36.dp), color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Pill("ON TRACK", 305.dp, 30.dp)
        Text("TODAY’S WRITING", Modifier.offset(36.dp, 97.dp), color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(words.toString(), Modifier.offset(36.dp, 110.dp), color = Ink, fontSize = 42.sp, fontWeight = FontWeight.Bold)
        Text("/ 1,000 WORDS", Modifier.offset(36.dp, 151.dp), color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.offset(36.dp, 232.dp).width(318.dp).height(65.dp).background(Panel).clickable { onWrite() }) {
            Text("ACTIVE DRAFT", Modifier.offset(54.dp, 233.dp), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("DND Chapter 12", Modifier.offset(54.dp, 261.dp), color = Muted, fontSize = 13.sp)
            Pill(words.toString(), 319.dp, 238.dp)
        }
        AppRow("LICHESS", "18 / 30 min", "LOCKED", 314.dp, false)
        AppRow("ACODE", "30 / 30 min", "CLEAR", 396.dp, true)
        ButtonLine("CONTINUE WRITING", 465) { onWrite() }
        BottomNav()
    }
}

@Composable private fun AppRow(title: String, detail: String, status: String, y: Dp, clear: Boolean) {
    Box(Modifier.offset(32.dp, y).width(322.dp).height(46.dp)) {
        Box(Modifier.offset(0.dp, 0.dp).width(46.dp).height(46.dp).background(Panel))
        Text(title, Modifier.offset(58.dp, 1.dp), color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(detail, Modifier.offset(58.dp, 29.dp), color = Muted, fontSize = 13.sp)
        Pill(status, 270.dp, 5.dp, clear)
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
    val navigate = LocalPageNavigation.current
    val items = listOf(Page.HOME to ("01" to "HOME"), Page.WRITE to ("02" to "WRITE"), Page.RULES to ("03" to "RULES"), Page.DOCS to ("04" to "DOCS"))
    Row(Modifier.offset(19.dp, 778.dp).width(352.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        items.forEach { (page, labels) ->
            Column(Modifier.width(44.dp).clickable { navigate(page) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(labels.first, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(labels.second, color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable private fun Write(vm: DraftLockViewModel, text: String, words: Int) {
    var draft by remember(text) { mutableStateOf(text) }
    Frame("WRITE / ACTIVE DOCUMENT", "DND CHAPTER 12", "SAVED", true) {
        Text("TOTAL 3,842 WORDS", Modifier.offset(19.dp, 5.dp), color = Muted, fontSize = 13.sp)
        Text("TODAY +$words", Modifier.offset(315.dp, 5.dp), color = Ink, fontSize = 13.sp)
        OutlinedTextField(draft, { draft = it; vm.onTextChanged(it) }, Modifier.offset(36.dp, 46.dp).width(318.dp).height(216.dp), colors = fieldColors(), placeholder = { Text("The corridor narrowed as the lights began to flicker.\n\nEgo stopped, listening for the sound beneath the ventilation hum. Something had followed him down three floors, but the footsteps had vanished.\n\nThe door ahead had no handle. He raised his hand anyway.", color = Ink, fontSize = 15.sp) })
        Text("CURSOR ACTIVE • EDITS COUNT TOWARD TODAY", Modifier.offset(36.dp, 287.dp), color = Muted, fontSize = 13.sp)
        Text("AUTOSAVED 8 SEC AGO", Modifier.offset(19.dp, 480.dp), color = Muted, fontSize = 13.sp)
        Pill("DOCS SYNC", 299.dp, 471.dp, true)
    }
}

@Composable private fun Rules(vm: DraftLockViewModel, reqs: List<AppRequirement>, onApp: () -> Unit, onBuilder: () -> Unit, onControl: () -> Unit) {
    Frame("ACCOUNTABILITY", "REQUIREMENTS", "2 / 3 ACTIVE", true) {
        Text("WRITE 1,000 WORDS", Modifier.offset(36.dp, 32.dp), color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Pill("${vm.todayWords.value} / 1000", 276.dp, 28.dp)
        Text("Lichess", Modifier.offset(36.dp, 131.dp), color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Pill("18 / 30 MIN", 271.dp, 127.dp)
        Text("Acode", Modifier.offset(36.dp, 230.dp), color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Pill("30 / 30 MIN", 271.dp, 226.dp, true)
        Text("APP REQUIREMENTS", Modifier.offset(36.dp, 327.dp), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("CHOOSE WHICH APPS TO REQUIRE", Modifier.offset(36.dp, 348.dp), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Each app can have its own time target. Choose none, one, or several apps.", Modifier.offset(36.dp, 372.dp).width(318.dp), color = Muted, fontSize = 13.sp)
        ButtonLine("APP REQUIREMENT", 432, onApp)
        ButtonLine("RULE BUILDER", 490, onBuilder)
        ButtonLine("ACCESS CONTROL", 548, onControl)
    }
}

@Composable private fun Docs(context: Context, onFilter: () -> Unit, onEdit: () -> Unit, onSync: () -> Unit) {
    Frame("GOOGLE DOCS", "LIBRARY", "SYNCED", true) {
        Box(Modifier.offset(19.dp, 12.dp).width(352.dp).height(48.dp).background(Panel)) { Text("SEARCH DOCUMENTS", Modifier.padding(17.dp), color = Muted, fontSize = 13.sp) }
        Text("FILTER", Modifier.offset(19.dp, 81.dp), color = Muted, fontSize = 13.sp)
        Pill("NAME: DND*", 294.dp, 72.dp)
        DocRow("DND CHAPTER 12", "3,842 words • edited 2m ago", "EDIT", 130, onEdit)
        DocRow("DND CHAPTER 11", "4,106 words • yesterday", "EDIT", 212) {}
        DocRow("NOVEL NOTES", "Not tracked", "OPEN", 294) {}
        ButtonLine("CLOUD SYNC", 365, onSync)
        ButtonLine("CONNECT GOOGLE ACCOUNT", 423) { GoogleOAuthManager(context).startAuthorization() }
        TextButton(onClick = onFilter, modifier = Modifier.offset(19.dp, 481.dp)) { Text("FILTER", color = Accent) }
    }
}

@Composable private fun DocRow(title: String, detail: String, status: String, y: Int, onClick: () -> Unit) {
    Row(Modifier.offset(32.dp, y.dp).width(338.dp).height(64.dp).clickable { onClick() }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(detail, color = Muted, fontSize = 13.sp) }
        Pill(status, 314.dp, (y + 12).dp, status == "OPEN")
    }
}

@Composable private fun Filter(back: () -> Unit) { var prefix by remember { mutableStateOf("DND") }; Frame("LIBRARY / FILTER", "FILTER DOCS", "DND*") { Text("NAME STARTS WITH", Modifier.offset(19.dp, 6.dp), color = Muted, fontSize = 13.sp); OutlinedTextField(prefix, { prefix = it }, Modifier.offset(19.dp, 30.dp).width(352.dp), colors = fieldColors()); Text("DND", Modifier.offset(36.dp, 86.dp), color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold); Text("Only Google Docs whose filename begins with DND appear in the writing library.", Modifier.offset(19.dp, 139.dp).width(352.dp), color = Muted, fontSize = 13.sp); Text("INCLUDE EDITABLE DOCS", Modifier.offset(19.dp, 186.dp), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Edits count toward today’s goal.", Modifier.offset(19.dp, 210.dp), color = Muted, fontSize = 13.sp); Pill("ON", 338.dp, 216.dp, true); Text("FOLDER", Modifier.offset(19.dp, 252.dp), color = Muted, fontSize = 13.sp); Text("Writing / DND", Modifier.offset(244.dp, 246.dp), color = Ink, fontSize = 16.sp); ButtonLine("APPLY FILTER", 301, back) } }

@Composable private fun Edit(vm: DraftLockViewModel, text: String, words: Int) { var draft by remember(text) { mutableStateOf(text) }; Frame("GOOGLE DOCS / EDIT", "DND CHAPTER 12", "SYNCED", true) { OutlinedTextField(draft, { draft = it; vm.onTextChanged(it) }, Modifier.offset(19.dp, 15.dp).width(352.dp).height(390.dp), colors = fieldColors()); Text("$words WORDS TODAY", Modifier.offset(19.dp, 425.dp), color = Muted, fontSize = 13.sp); ButtonLine("SAVE TO GOOGLE DOCS", 470) {}; ButtonLine("BACK TO LIBRARY", 528) {} } }

@Composable private fun Builder(back: () -> Unit) { Frame("RULES / BUILDER", "RULE BUILDER", "ACTIVE") { Text("WORD QUOTA", Modifier.offset(19.dp, 5.dp), color = Muted, fontSize = 13.sp); Text("1,000", Modifier.offset(19.dp, 30.dp), color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold); Text("APP REQUIREMENTS", Modifier.offset(19.dp, 88.dp), color = Muted, fontSize = 13.sp); Text("Lichess — 30 MIN", Modifier.offset(19.dp, 113.dp), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Acode — 30 MIN", Modifier.offset(19.dp, 145.dp), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold); ButtonLine("SAVE RULES", 205, back) } }

@Composable private fun AppRequirementScreen(back: () -> Unit) { Frame("RULES / APPS", "APP REQUIREMENT", "2 SELECTED") { Text("SELECT REQUIRED APPS", Modifier.offset(19.dp, 7.dp), color = Muted, fontSize = 13.sp); Text("Lichess", Modifier.offset(19.dp, 42.dp), color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold); Pill("30 MIN", 280.dp, 37.dp); Text("Acode", Modifier.offset(19.dp, 101.dp), color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold); Pill("30 MIN", 280.dp, 96.dp, true); ButtonLine("SAVE APP REQUIREMENTS", 165, back) } }

@Composable private fun Control(back: () -> Unit) { Frame("ACCOUNTABILITY / CONTROL", "ACCESS CONTROL", "ACTIVE") { Text("REQUIREMENTS MET", Modifier.offset(19.dp, 10.dp), color = Muted, fontSize = 13.sp); Text("YES", Modifier.offset(19.dp, 36.dp), color = Accent, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text("LOCKED APPS", Modifier.offset(19.dp, 91.dp), color = Muted, fontSize = 13.sp); Text("Lichess", Modifier.offset(19.dp, 117.dp), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("ACCESS", Modifier.offset(260.dp, 117.dp), color = Muted, fontSize = 13.sp); ButtonLine("OPEN ACCESS DETAILS", 180, back) } }

@Composable private fun Access(words: Int, quota: Int, onWrite: () -> Unit, onRules: () -> Unit) { Frame("ACCESS CONTROL", "CURRENT STATUS", "ACTIVE") { Text("TODAY", Modifier.offset(19.dp, 10.dp), color = Muted, fontSize = 13.sp); Text("$words / $quota WORDS", Modifier.offset(19.dp, 35.dp), color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Bold); Text("Lichess", Modifier.offset(19.dp, 93.dp), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold); Pill("LOCKED", 282.dp, 87.dp); Text("Acode", Modifier.offset(19.dp, 143.dp), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold); Pill("CLEAR", 292.dp, 137.dp, true); ButtonLine("CONTINUE WRITING", 205, onWrite); ButtonLine("VIEW REQUIREMENTS", 263, onRules) } }

@Composable private fun Usage() { Frame("USAGE", "APP TIME", "TODAY") { Text("LICHESS", Modifier.offset(19.dp, 15.dp), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("18 / 30 MIN", Modifier.offset(250.dp, 15.dp), color = Muted, fontSize = 13.sp); Text("ACODE", Modifier.offset(19.dp, 72.dp), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("30 / 30 MIN", Modifier.offset(250.dp, 72.dp), color = Ink, fontSize = 13.sp); Text("USAGE IS READ FROM ANDROID USAGE ACCESS WHEN AVAILABLE.", Modifier.offset(19.dp, 145.dp).width(352.dp), color = Muted, fontSize = 12.sp) } }

@Composable private fun Sync() { Frame("GOOGLE DOCS", "CLOUD SYNC", "READY") { Text("ACCOUNT", Modifier.offset(19.dp, 15.dp), color = Muted, fontSize = 13.sp); Text("Google account connected", Modifier.offset(19.dp, 39.dp), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("LAST SYNC", Modifier.offset(19.dp, 100.dp), color = Muted, fontSize = 13.sp); Text("2 MIN AGO", Modifier.offset(19.dp, 124.dp), color = Ink, fontSize = 18.sp); Text("WRITE CHANGES ARE PUSHED TO THE ACTIVE DOC.", Modifier.offset(19.dp, 180.dp).width(352.dp), color = Muted, fontSize = 13.sp) } }

@Composable private fun History(history: List<DailyRecord>, onAnalytics: () -> Unit) { Frame("PROGRESS", "HISTORY", "30 DAYS", true) { if (history.isEmpty()) Text("NO RECORDED DAYS YET", Modifier.offset(19.dp, 18.dp), color = Muted, fontSize = 13.sp) else history.take(8).forEachIndexed { i, day -> Text(day.dayKey, Modifier.offset(19.dp, (18 + i * 42).dp), color = Muted, fontSize = 12.sp); Text("${day.words} WORDS", Modifier.offset(180.dp, (15 + i * 42).dp), color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold) }; ButtonLine("VIEW ANALYTICS", 365, onAnalytics) } }

@Composable private fun Analytics(history: List<DailyRecord>) { Frame("PROGRESS / HISTORY", "ANALYTICS", "30 DAYS") { val total = history.sumOf { it.words }; val avg = if (history.isEmpty()) 0 else total / history.size; Text("TOTAL WORDS", Modifier.offset(19.dp, 18.dp), color = Muted, fontSize = 13.sp); Text(total.toString(), Modifier.offset(19.dp, 42.dp), color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold); Text("AVERAGE / DAY", Modifier.offset(19.dp, 100.dp), color = Muted, fontSize = 13.sp); Text(avg.toString(), Modifier.offset(19.dp, 124.dp), color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Bold) } }

@Composable private fun Permissions(context: Context, onAccount: () -> Unit) { Frame("SETUP", "PERMISSIONS", "CHECK") { Text("USAGE ACCESS", Modifier.offset(19.dp, 15.dp), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Needed to measure required app time.", Modifier.offset(19.dp, 41.dp), color = Muted, fontSize = 13.sp); ButtonLine("OPEN USAGE ACCESS", 90) { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }; Text("NOTIFICATIONS", Modifier.offset(19.dp, 165.dp), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Used for accountability reminders when enabled.", Modifier.offset(19.dp, 191.dp), color = Muted, fontSize = 13.sp); ButtonLine("CONTINUE", 240, onAccount) } }

@Composable private fun Account(context: Context, onSetup: () -> Unit) { Frame("GOOGLE", "ACCOUNT", "CONNECTED") { Text("Google account", Modifier.offset(19.dp, 15.dp), color = Muted, fontSize = 13.sp); Text("Connected for Docs sync", Modifier.offset(19.dp, 40.dp), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold); ButtonLine("CONNECT / CHANGE ACCOUNT", 100) { GoogleOAuthManager(context).startAuthorization() }; ButtonLine("CONTINUE SETUP", 158, onSetup) } }

@Composable private fun Setup(onAccount: () -> Unit) { Frame("DRAFTLOCK", "SETUP", "READY") { Text("CONNECT GOOGLE DOCS, SELECT REQUIREMENTS, THEN START WRITING.", Modifier.offset(19.dp, 15.dp).width(352.dp), color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold); ButtonLine("GO TO ACCOUNT", 100, onAccount) } }

@Composable private fun QuickAccess(words: Int, quota: Int, onWrite: () -> Unit) { Frame("QUICK ACCESS", "TODAY", "ACTIVE") { Text("$words / $quota WORDS", Modifier.offset(19.dp, 15.dp), color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold); ButtonLine("CONTINUE WRITING", 90, onWrite) } }

@Composable private fun Settings(quota: Int, onAccount: () -> Unit) { Frame("DRAFTLOCK", "SETTINGS", "") { Text("DAILY QUOTA", Modifier.offset(19.dp, 15.dp), color = Muted, fontSize = 13.sp); Text("$quota WORDS", Modifier.offset(19.dp, 40.dp), color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold); ButtonLine("GOOGLE ACCOUNT", 100, onAccount) } }

@Composable private fun fieldColors() = OutlinedTextFieldDefaults.colors(focusedTextColor = Ink, unfocusedTextColor = Ink, focusedBorderColor = Line, unfocusedBorderColor = Line, cursorColor = Accent)
