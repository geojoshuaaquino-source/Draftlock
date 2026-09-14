package com.draftlock.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.draftlock.app.data.AppRequirement
import com.draftlock.app.data.LockedApp
import com.draftlock.app.ui.theme.DraftLockTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val MxBg = Color(0xFF050B18)
private val MxSurface = Color(0xCC0C1730)
private val MxSurface2 = Color(0x99142543)
private val MxLine = Color(0x332D6B9F)
private val MxText = Color(0xFFF2F7FF)
private val MxMuted = Color(0xFF91A8C8)
private val MxBlue = Color(0xFF3D8BFF)
private val MxCyan = Color(0xFF20D7FF)
private val MxPurple = Color(0xFF765CFF)

private enum class MxScreen { HOME, LIBRARY, FOCUS, EDITOR, SETTINGS }

@Composable
fun DraftLockMockupApp(vm: DraftLockViewModel) {
    val context = LocalContext.current
    val words by vm.todayWords.collectAsStateWithLifecycle()
    val quota by vm.quota.collectAsStateWithLifecycle()
    val requirements by vm.requirements.collectAsStateWithLifecycle()
    val lockedApps by vm.lockedApps.collectAsStateWithLifecycle()
    val text by vm.text.collectAsStateWithLifecycle()
    val documentName by vm.documentName.collectAsStateWithLifecycle()
    val localDocs by vm.localDocs.collectAsStateWithLifecycle()
    val resetMinutes by vm.resetMinutes.collectAsStateWithLifecycle()
    val logic by vm.logic.collectAsStateWithLifecycle()
    val autoSave by vm.googleAutoSave.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(MxScreen.HOME) }
    var showOnboarding by remember { mutableStateOf(!context.getSharedPreferences("draftlock_ui", Context.MODE_PRIVATE).getBoolean("onboarded", false)) }
    var showOverride by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.checkGoogleConnection()
        if (vm.isGoogleConnected) vm.fetchDriveFiles()
        vm.refreshUsage()
    }
    LaunchedEffect(requirements) { vm.refreshUsage() }
    LaunchedEffect(vm.isGoogleConnected) { if (vm.isGoogleConnected) vm.fetchDriveFiles() }

    DraftLockTheme {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF091A38), MxBg)))) {
            MockupAtmosphere()
            Column(Modifier.fillMaxSize()) {
                MockupHeader(vm, words, screen)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (screen) {
                        MxScreen.HOME -> MockupHome(vm, words, quota, requirements, lockedApps, logic, onWrite = { screen = MxScreen.EDITOR }, onFocus = { screen = MxScreen.FOCUS }, onLibrary = { screen = MxScreen.LIBRARY }, onOverride = { showOverride = true })
                        MxScreen.LIBRARY -> MockupLibrary(vm, localDocs, onOpen = { doc -> vm.selectedLocalDocId = doc.id; vm.setDocumentName(doc.title); vm.onTextChanged(doc.content); screen = MxScreen.EDITOR })
                        MxScreen.FOCUS -> MockupFocus(vm, requirements, lockedApps, context, onOverride = { showOverride = true })
                        MxScreen.EDITOR -> MockupEditor(vm, text, words, quota, documentName)
                        MxScreen.SETTINGS -> MockupSettings(vm, quota, resetMinutes, logic, autoSave, context, onOverride = { showOverride = true })
                    }
                }
                MockupNav(screen) { screen = it }
            }
        }
    }

    if (showOnboarding) {
        MockupOnboarding {
            context.getSharedPreferences("draftlock_ui", Context.MODE_PRIVATE).edit().putBoolean("onboarded", true).apply()
            showOnboarding = false
        }
    }
    if (showOverride) {
        AlertDialog(
            onDismissRequest = { showOverride = false },
            title = { Text("Emergency unlock", color = MxText) },
            text = { Text("Unlock blocked apps for 15 minutes. Your normal requirements remain unchanged.", color = MxMuted) },
            confirmButton = { TextButton(onClick = { vm.activateEmergencyOverride(); showOverride = false }) { Text("Unlock 15 min", color = MxCyan) } },
            dismissButton = { TextButton(onClick = { showOverride = false }) { Text("Cancel", color = MxMuted) } }
        )
    }
}

@Composable
private fun MockupAtmosphere() {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.size(430.dp).offset((-210).dp, (-150).dp).background(Brush.radialGradient(listOf(MxBlue.copy(alpha = .22f), Color.Transparent)), CircleShape))
        Box(Modifier.size(480.dp).align(Alignment.TopEnd).offset(220.dp, (-190).dp).background(Brush.radialGradient(listOf(MxPurple.copy(alpha = .19f), Color.Transparent)), CircleShape))
        Box(Modifier.size(360.dp).align(Alignment.BottomCenter).offset(0.dp, 160.dp).background(Brush.radialGradient(listOf(MxCyan.copy(alpha = .11f), Color.Transparent)), CircleShape))
    }
}

@Composable
private fun MockupHeader(vm: DraftLockViewModel, words: Int, screen: MxScreen) {
    Surface(color = Color(0xCC07152C), tonalElevation = 0.dp) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(MxCyan.copy(.25f), MxPurple.copy(.25f)))), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_logo_draftlock), null, tint = Color.Unspecified, modifier = Modifier.size(29.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("draftlock", color = MxText, fontSize = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.2).sp)
                Text(if (vm.isGoogleConnected) "Drive connected" else "Private writing vault", color = MxMuted, fontSize = 10.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${words} words", color = MxText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(screen.name.lowercase(), color = MxCyan, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun MockupHome(vm: DraftLockViewModel, words: Int, quota: Int, requirements: List<AppRequirement>, lockedApps: List<LockedApp>, logic: String, onWrite: () -> Unit, onFocus: () -> Unit, onLibrary: () -> Unit, onOverride: () -> Unit) {
    val progress = (words.toFloat() / quota.coerceAtLeast(1)).coerceIn(0f, 1f)
    val unlocked = vm.allConditionsComplete()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("GOOD TO SEE YOU", color = MxCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text("Build your draft.", color = MxText, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Your writing unlocks the rest of your phone.", color = MxMuted, fontSize = 12.sp)
        }
        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("TODAY'S DRAFT", color = MxMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(words.toString(), color = MxText, fontSize = 42.sp, fontWeight = FontWeight.Bold)
                            Text(" / $quota", color = MxMuted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 7.dp))
                        }
                    }
                    Box(Modifier.size(72.dp).clip(CircleShape).background(Brush.linearGradient(listOf(MxCyan, MxPurple))), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(62.dp).clip(CircleShape).background(MxBg), contentAlignment = Alignment.Center) { Text("${(progress * 100).toInt()}%", color = MxText, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF172642))) { Box(Modifier.fillMaxWidth(progress).height(7.dp).clip(RoundedCornerShape(7.dp)).background(Brush.horizontalGradient(listOf(MxCyan, MxBlue, MxPurple)))) }
                Spacer(Modifier.height(14.dp))
                GradientButton(if (unlocked) "Continue writing" else "Write to unlock", onWrite)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("REQUIREMENTS", requirements.count { it.enabled }.toString(), "${logic} logic", Modifier.weight(1f))
                StatTile("LOCKED APPS", lockedApps.count { it.enabled }.toString(), "protected", Modifier.weight(1f))
                StatTile("GOOGLE", if (vm.isGoogleConnected) "ON" else "OFF", if (vm.isGoogleConnected) "Drive ready" else "local only", Modifier.weight(1f))
            }
        }
        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("FOCUS MODE", color = MxText, fontWeight = FontWeight.Bold, fontSize = 15.sp); Text("Keep distracting apps behind the lock.", color = MxMuted, fontSize = 11.sp) }
                    SmallButton("Open", onFocus)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("${requirements.size} requirements")
                    Chip("${lockedApps.size} blocked")
                    if (unlocked) Chip("Unlocked")
                }
            }
        }
        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("YOUR LIBRARY", color = MxText, fontWeight = FontWeight.Bold, fontSize = 15.sp); Text("Local drafts stay on this device.", color = MxMuted, fontSize = 11.sp) }
                    SmallButton("Browse", onLibrary)
                }
            }
        }
        item { SmallTextButton("Emergency unlock", onOverride) }
    }
}

@Composable
private fun MockupLibrary(vm: DraftLockViewModel, docs: List<com.draftlock.app.data.LocalDocument>, onOpen: (com.draftlock.app.data.LocalDocument) -> Unit) {
    var query by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    val filtered = docs.filter { query.isBlank() || it.title.contains(query, true) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("LIBRARY", color = MxCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp); Text("Your drafts", color = MxText, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
        item { OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search drafts", color = MxMuted) }, leadingIcon = { Icon(painterResource(R.drawable.ic_search), null, tint = MxMuted) }, colors = fieldColors()) }
        item {
            GlassCard {
                Text("NEW DRAFT", color = MxMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Chapter or project name", color = MxMuted) }, colors = fieldColors())
                    Spacer(Modifier.width(8.dp))
                    SmallButton("Create") { if (newName.isNotBlank()) { vm.createLocalDoc(newName.trim()); newName = "" } }
                }
            }
        }
        items(filtered, key = { it.id }) { doc ->
            GlassCard(modifier = Modifier.clickable { onOpen(doc) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(MxBlue.copy(.3f), MxPurple.copy(.25f)))), contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_write), null, tint = MxCyan, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) { Text(doc.title, color = MxText, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${doc.wordCount} words  •  ${java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(doc.updatedAt))}", color = MxMuted, fontSize = 10.sp) }
                    Text("›", color = MxCyan, fontSize = 26.sp)
                }
            }
        }
        if (filtered.isEmpty()) item { Text("No drafts yet. Create one above.", color = MxMuted, fontSize = 12.sp) }
    }
}

@Composable
private fun MockupFocus(vm: DraftLockViewModel, requirements: List<AppRequirement>, lockedApps: List<LockedApp>, context: Context, onOverride: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("FOCUS", color = MxCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp); Text("Protected apps", color = MxText, fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("Finish the requirement, then the lock opens.", color = MxMuted, fontSize = 12.sp) }
        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("LOCK STATUS", color = MxMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text(if (vm.allConditionsComplete()) "UNLOCKED" else "LOCKED", color = if (vm.allConditionsComplete()) MxCyan else MxText, fontSize = 22.sp, fontWeight = FontWeight.Bold) }; SmallButton("Emergency", onOverride) }
            }
        }
        item { Text("REQUIREMENTS", color = MxMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
        items(requirements, key = { it.id }) { req ->
            val used = vm.usageMinutes[req.packageName] ?: 0
            GlassCard { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(req.displayName, color = MxText, fontWeight = FontWeight.Bold); Text("${used} / ${req.requiredMinutes} min", color = MxMuted, fontSize = 10.sp) }; Text(if (used >= req.requiredMinutes) "✓" else "${(used * 100 / req.requiredMinutes.coerceAtLeast(1)).coerceAtMost(100)}%", color = if (used >= req.requiredMinutes) MxCyan else MxMuted, fontWeight = FontWeight.Bold) } }
        }
        item { Text("BLOCKED APPS", color = MxMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
        items(lockedApps, key = { it.packageName }) { app -> GlassCard { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(app.displayName, color = MxText, fontWeight = FontWeight.Bold); Text(app.packageName, color = MxMuted, fontSize = 9.sp) }; Text(if (app.enabled) "LOCKED" else "OFF", color = if (app.enabled) MxCyan else MxMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold) } } }
        item { SmallTextButton("Refresh usage") { vm.refreshUsage() } }
    }
}

@Composable
private fun MockupEditor(vm: DraftLockViewModel, text: String, words: Int, quota: Int, documentName: String) {
    var draft by remember(text) { mutableStateOf(text) }
    var name by remember(documentName) { mutableStateOf(documentName) }
    val progress = (words.toFloat() / quota.coerceAtLeast(1)).coerceIn(0f, 1f)
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("EDITOR", color = MxCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp); Text(name.ifBlank { "Untitled draft" }, color = MxText, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                Text("$words / $quota", color = MxMuted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF172642))) { Box(Modifier.fillMaxWidth(progress).height(5.dp).background(Brush.horizontalGradient(listOf(MxCyan, MxPurple)))) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.weight(1f), singleLine = true, colors = fieldColors())
            SmallButton("Rename") { vm.setDocumentName(name.trim().ifBlank { "Untitled draft" }) }
        }
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(22.dp)).background(Color(0xB30A1428)).padding(2.dp)) {
            OutlinedTextField(value = draft, onValueChange = { draft = it; vm.onTextChanged(it) }, modifier = Modifier.fillMaxSize(), placeholder = { Text("Start writing…", color = MxMuted) }, colors = fieldColors().copy(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent), shape = RoundedCornerShape(20.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            GradientButton("Save to Drive", { vm.syncTextToDoc() }, Modifier.weight(1f))
            SmallButton("Save new") { vm.saveAsNewDoc(name) }
        }
        Text(vm.syncStatus, color = MxMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MockupSettings(vm: DraftLockViewModel, quota: Int, resetMinutes: Int, logic: String, autoSave: Boolean, context: Context, onOverride: () -> Unit) {
    var quotaText by remember(quota) { mutableStateOf(quota.toString()) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("SETTINGS", color = MxCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp); Text("Make DraftLock yours", color = MxText, fontSize = 27.sp, fontWeight = FontWeight.Bold) }
        item { SettingCard("Daily word goal", "How much writing unlocks your phone") { Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(value = quotaText, onValueChange = { quotaText = it.filter(Char::isDigit) }, modifier = Modifier.weight(1f), singleLine = true, colors = fieldColors()); Spacer(Modifier.width(8.dp)); SmallButton("Apply") { quotaText.toIntOrNull()?.let(vm::setQuota) } } } }
        item { SettingCard("Requirement logic", "Choose how app requirements combine") { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ChoiceButton("AND", logic == "AND") { vm.setLogic("AND") }; ChoiceButton("OR", logic == "OR") { vm.setLogic("OR") } } } }
        item { SettingCard("Reset period", "Usage resets at this interval") { Slider(value = resetMinutes.toFloat(), onValueChange = { vm.setResetMinutes(it.toInt()) }, valueRange = 0f..1440f, steps = 23); Text(if (resetMinutes == 0) "Daily" else "${resetMinutes / 60}h ${resetMinutes % 60}m", color = MxMuted, fontSize = 11.sp) } }
        item { SettingCard("Google Drive", if (vm.isGoogleConnected) "Connected and ready" else "Connect to edit Drive documents") { Row(verticalAlignment = Alignment.CenterVertically) { Text(if (vm.isGoogleConnected) "Connected" else "Not connected", color = if (vm.isGoogleConnected) MxCyan else MxMuted, modifier = Modifier.weight(1f)); SmallButton(if (vm.isGoogleConnected) "Disconnect" else "Connect") { if (vm.isGoogleConnected) { GoogleOAuthManager(context).disconnect(); vm.checkGoogleConnection() } else vm.startGoogleAuth(context) } } } }
        item { SettingCard("Google auto-save", "Keep the existing Drive auto-save preference") { Row(verticalAlignment = Alignment.CenterVertically) { Text(if (autoSave) "Enabled" else "Disabled", color = MxText, modifier = Modifier.weight(1f)); Switch(checked = autoSave, onCheckedChange = vm::setGoogleAutoSave) } } }
        item { SettingCard("Emergency override", "Temporarily unlock protected apps") { SmallButton("Unlock for 15 min", onOverride) } }
        item { Text(vm.syncStatus, color = MxMuted, fontSize = 10.sp) }
    }
}

@Composable
private fun MockupOnboarding(onDone: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xEE050B18)).padding(24.dp), contentAlignment = Alignment.Center) {
        GlassCard {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Box(Modifier.size(78.dp).clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(MxCyan, MxPurple))), contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_logo_draftlock), null, tint = Color.Unspecified, modifier = Modifier.size(54.dp)) } }
            Spacer(Modifier.height(20.dp))
            Text("Write first. Unlock later.", color = MxText, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("DraftLock turns your writing goal into the key for the distracting parts of your phone. Your local drafts remain yours.", color = MxMuted, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(20.dp))
            GradientButton("Enter your vault", onDone)
        }
    }
}

@Composable
private fun MockupNav(screen: MxScreen, onScreen: (MxScreen) -> Unit) {
    Surface(color = Color(0xE6071429), tonalElevation = 0.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MxNavItem("Home", R.drawable.ic_home, screen == MxScreen.HOME) { onScreen(MxScreen.HOME) }
            MxNavItem("Library", R.drawable.ic_docs, screen == MxScreen.LIBRARY) { onScreen(MxScreen.LIBRARY) }
            Box(Modifier.weight(1.25f).height(52.dp).clip(RoundedCornerShape(18.dp)).background(Brush.horizontalGradient(listOf(MxCyan, MxPurple))).clickable { onScreen(MxScreen.EDITOR) }, contentAlignment = Alignment.Center) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(painterResource(R.drawable.ic_write), null, tint = Color.White, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Write", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) } }
            MxNavItem("Focus", R.drawable.ic_usage, screen == MxScreen.FOCUS) { onScreen(MxScreen.FOCUS) }
            MxNavItem("More", R.drawable.ic_analytics, screen == MxScreen.SETTINGS) { onScreen(MxScreen.SETTINGS) }
        }
    }
}

@Composable
private fun RowScope.MxNavItem(label: String, icon: Int, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(16.dp)).background(if (selected) MxBlue.copy(.18f) else Color.Transparent).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) { Icon(painterResource(icon), null, tint = if (selected) MxCyan else MxMuted, modifier = Modifier.size(19.dp)); Text(label, color = if (selected) MxText else MxMuted, fontSize = 9.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) } }
}

@Composable
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MxSurface).padding(16.dp), content = content)
}

@Composable
private fun StatTile(title: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(MxSurface2).padding(12.dp)) { Text(title, color = MxMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(5.dp)); Text(value, color = MxText, fontSize = 19.sp, fontWeight = FontWeight.Bold); Text(sub, color = MxCyan, fontSize = 8.sp) }
}

@Composable
private fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(15.dp)).background(Brush.horizontalGradient(listOf(MxBlue, MxPurple))).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    Box(Modifier.height(38.dp).clip(RoundedCornerShape(13.dp)).background(MxBlue.copy(.2f)).clickable(onClick = onClick).padding(horizontal = 13.dp), contentAlignment = Alignment.Center) { Text(text, color = MxCyan, fontWeight = FontWeight.Bold, fontSize = 10.sp) }
}

@Composable
private fun SmallTextButton(text: String, onClick: () -> Unit) { Text(text, color = MxMuted, fontSize = 10.sp, modifier = Modifier.clickable(onClick = onClick).padding(6.dp)) }

@Composable
private fun Chip(text: String) { Box(Modifier.clip(RoundedCornerShape(20.dp)).background(MxBlue.copy(.13f)).padding(horizontal = 10.dp, vertical = 6.dp)) { Text(text, color = MxMuted, fontSize = 9.sp) } }

@Composable
private fun SettingCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) { GlassCard { Text(title, color = MxText, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(subtitle, color = MxMuted, fontSize = 10.sp); Spacer(Modifier.height(10.dp)); content() } }

@Composable
private fun ChoiceButton(text: String, selected: Boolean, onClick: () -> Unit) { Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if (selected) MxBlue.copy(.3f) else MxSurface2).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp)) { Text(text, color = if (selected) MxCyan else MxMuted, fontWeight = FontWeight.Bold, fontSize = 11.sp) } }

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(focusedBorderColor = MxCyan, unfocusedBorderColor = MxLine, focusedTextColor = MxText, unfocusedTextColor = MxText, cursorColor = MxCyan, focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedPlaceholderColor = MxMuted, unfocusedPlaceholderColor = MxMuted)
