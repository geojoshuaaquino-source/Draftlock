package com.draftlock.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.draftlock.app.ui.theme.DraftLockColors
import com.draftlock.app.ui.theme.DraftLockTheme

private enum class MockPage { HOME, LIBRARY, FOCUS, EDITOR, SETTINGS }

class MockupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: DraftLockViewModel = viewModel()
            DraftLockMockupApp(vm)
        }
    }
}

@Composable
fun DraftLockMockupApp(vm: DraftLockViewModel) {
    val text by vm.text.collectAsStateWithLifecycle()
    val quota by vm.quota.collectAsStateWithLifecycle()
    val todayWords by vm.todayWords.collectAsStateWithLifecycle()
    val documentName by vm.documentName.collectAsStateWithLifecycle()
    val requirements by vm.requirements.collectAsStateWithLifecycle()
    val lockedApps by vm.lockedApps.collectAsStateWithLifecycle()
    val localDocs by vm.localDocs.collectAsStateWithLifecycle()
    var page by remember { mutableStateOf(MockPage.HOME) }
    var onboarding by remember { mutableStateOf(true) }
    var overrideDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.checkGoogleConnection()
        vm.refreshUsage()
    }

    DraftLockTheme {
        Box(Modifier.fillMaxSize().background(Color(0xFF050B19))) {
            MockBackground()
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = { MockTopBar(vm, todayWords, quota) },
                bottomBar = {
                    MockBottomBar(page) { page = it }
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (page) {
                        MockPage.HOME -> MockHome(
                            vm, todayWords, quota, text, documentName, requirements.size,
                            lockedApps.size,
                            onWrite = { page = MockPage.EDITOR },
                            onLibrary = { page = MockPage.LIBRARY },
                            onFocus = { page = MockPage.FOCUS },
                            onOverride = { overrideDialog = true }
                        )
                        MockPage.LIBRARY -> MockLibrary(vm, localDocs, onEdit = { page = MockPage.EDITOR })
                        MockPage.FOCUS -> MockFocus(vm, lockedApps, requirements, onManage = { page = MockPage.SETTINGS })
                        MockPage.EDITOR -> MockEditor(vm, text, documentName, onLibrary = { page = MockPage.LIBRARY })
                        MockPage.SETTINGS -> MockSettings(vm, onApps = { page = MockPage.FOCUS })
                    }
                }
            }

            if (onboarding) MockOnboarding { onboarding = false }
            if (overrideDialog) {
                AlertDialog(
                    onDismissRequest = { overrideDialog = false },
                    containerColor = Color(0xFF101B32),
                    title = { Text("Emergency override", color = Color.White, fontWeight = FontWeight.Bold) },
                    text = { Text("Temporarily unlock your blocked apps for 15 minutes?", color = Color(0xFFB8C5DF)) },
                    confirmButton = {
                        TextButton(onClick = { vm.activateEmergencyOverride(); overrideDialog = false }) { Text("Unlock", color = Color(0xFF65D8FF)) }
                    },
                    dismissButton = { TextButton(onClick = { overrideDialog = false }) { Text("Cancel", color = Color(0xFF8C9AB7)) } }
                )
            }
        }
    }
}

@Composable
private fun MockBackground() {
    Box(Modifier.fillMaxSize().background(Color(0xFF050B19))) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x443B82F6), Color.Transparent), radius = 850f)))
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x2220D8FF), Color.Transparent), radius = 600f)))
        Box(Modifier.fillMaxWidth().height(260.dp).align(Alignment.TopCenter).background(Brush.verticalGradient(listOf(Color(0x303D6BFF), Color.Transparent))))
        Box(Modifier.fillMaxSize().padding(20.dp).border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(32.dp)))
    }
}

@Composable
private fun GlassSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xB5142340))
            .border(1.dp, Color(0x20B9D9FF), RoundedCornerShape(24.dp))
            .padding(18.dp),
        content = content
    )
}

@Composable
private fun MockTopBar(vm: DraftLockViewModel, words: Int, quota: Int) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xD9071224)).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(Color(0xFF28D7FF), Color(0xFF675CFF)))), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_logo_draftlock), null, tint = Color.White, modifier = Modifier.size(27.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("draftlock", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(if (vm.isGoogleConnected) "Cloud connected" else "Your private writing vault", color = Color(0xFF8496B8), fontSize = 11.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("$words / $quota", color = Color(0xFF72D9FF), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("TODAY", color = Color(0xFF7183A4), fontSize = 9.sp, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun MockBottomBar(page: MockPage, onPage: (MockPage) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xE90A1426)).padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        MockNav("Home", R.drawable.ic_home, page == MockPage.HOME) { onPage(MockPage.HOME) }
        MockNav("Library", R.drawable.ic_docs, page == MockPage.LIBRARY) { onPage(MockPage.LIBRARY) }
        Box(Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(18.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF25D9FF), Color(0xFF665CFF)))).clickable { onPage(MockPage.EDITOR) }, contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_write), null, tint = Color.White, modifier = Modifier.size(23.dp))
        }
        MockNav("Focus", R.drawable.ic_lock_closed, page == MockPage.FOCUS) { onPage(MockPage.FOCUS) }
        MockNav("Settings", R.drawable.ic_analytics, page == MockPage.SETTINGS) { onPage(MockPage.SETTINGS) }
    }
}

@Composable
private fun RowScope.MockNav(label: String, icon: Int, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(16.dp)).clickable { onClick() }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(painterResource(icon), null, tint = if (selected) Color(0xFF63D9FF) else Color(0xFF72819B), modifier = Modifier.size(20.dp))
        Text(label, color = if (selected) Color.White else Color(0xFF72819B), fontSize = 9.sp)
    }
}

@Composable
private fun MockHome(vm: DraftLockViewModel, words: Int, quota: Int, text: String, name: String, reqs: Int, locked: Int, onWrite: () -> Unit, onLibrary: () -> Unit, onFocus: () -> Unit, onOverride: () -> Unit) {
    val progress = (words.toFloat() / quota.coerceAtLeast(1)).coerceIn(0f, 1f)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 20.dp, 18.dp, 30.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item {
            Text("Good evening.", color = Color(0xFF8294B5), fontSize = 14.sp)
            Text("Make something worth locking.", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        }
        item {
            GlassSurface {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("TODAY'S PROGRESS", color = Color(0xFF7386A7), fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("$words", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                        Text("of $quota words", color = Color(0xFF8193B3), fontSize = 12.sp)
                    }
                    Box(Modifier.size(78.dp).clip(CircleShape).background(Color(0x1837D8FF)).border(1.dp, Color(0x335DDCFF), CircleShape), contentAlignment = Alignment.Center) {
                        Text("${(progress * 100).toInt()}%", color = Color(0xFF68DFFF), fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(15.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(8.dp)), color = Color(0xFF4EDBFF), trackColor = Color(0x182D405F))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("DRAFT", name, Modifier.weight(1f))
                MetricCard("RULES", reqs.toString(), Modifier.weight(1f))
                MetricCard("LOCKED", locked.toString(), Modifier.weight(1f))
            }
        }
        item {
            GlassSurface {
                Text("CURRENT DRAFT", color = Color(0x7386A7), fontSize = 10.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                Text(if (text.isBlank()) "Start writing your draft…" else text.take(110), color = Color.White, fontSize = 15.sp, maxLines = 3)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    GradientButton("Continue writing", Modifier.weight(1f), onWrite)
                    SmallGlassButton("Library", onLibrary)
                }
            }
        }
        item {
            GlassSurface {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0x203E83FF)), contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_lock_closed), null, tint = Color(0xFF7BBAFF), modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text("Focus protection", color = Color.White, fontWeight = FontWeight.Bold); Text("$locked apps protected", color = Color(0xFF8091AF), fontSize = 11.sp) }
                    SmallGlassButton("Open", onFocus)
                }
                Spacer(Modifier.height(11.dp))
                Text("Need access right now?", color = Color(0xFF7385A3), fontSize = 11.sp)
                TextButton(onClick = onOverride) { Text("Emergency 15 min override", color = Color(0xFF72D9FF), fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xA813223A)).border(1.dp, Color(0x18B9D9FF), RoundedCornerShape(18.dp)).padding(13.dp)) {
        Text(title, color = Color(0xFF7184A4), fontSize = 8.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp)); Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun MockLibrary(vm: DraftLockViewModel, localDocs: List<com.draftlock.app.data.LocalDocument>, onEdit: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    var newTitle by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 20.dp, 18.dp, 30.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item { Text("Library", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("Your drafts, stored locally or synced with Google.", color = Color(0xFF7E90B0), fontSize = 12.sp) }
        item {
            Row(Modifier.clip(RoundedCornerShape(15.dp)).background(Color(0xA8152944)).padding(4.dp)) {
                Seg("Local", tab == 0) { tab = 0 }; Seg("Google Docs", tab == 1) { tab = 1 }
            }
        }
        item {
            GlassSurface {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_google), null, tint = if (vm.isGoogleConnected) Color(0xFF6EDCFF) else Color.White, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(if (vm.isGoogleConnected) "Google connected" else "Google Docs", color = Color.White, fontWeight = FontWeight.Bold); Text(vm.syncStatus, color = Color(0xFF7E90B0), fontSize = 10.sp) }
                    if (!vm.isGoogleConnected) GradientButton("Connect", Modifier.width(92.dp)) { vm.startGoogleAuth(androidx.compose.ui.platform.LocalContext.current) } else SmallGlassButton("Refresh") { vm.fetchDriveFiles() }
                }
            }
        }
        if (tab == 0) {
            item { Row(verticalAlignment = Alignment.CenterVertically) { Text("LOCAL DRAFTS", color = Color(0xFF7386A7), fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); SmallGlassButton("+ New") { showCreate = true } } }
            items(localDocs) { doc ->
                GlassSurface {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Color(0x183CCFFF)), contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_docs), null, tint = Color(0xFF68DFFF), modifier = Modifier.size(21.dp)) }
                        Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(doc.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1); Text("${doc.wordCount} words", color = Color(0xFF7E90B0), fontSize = 10.sp) }
                        SmallGlassButton("Open") { vm.selectedLocalDocId = doc.id; vm.setDocumentName(doc.title); vm.onTextChanged(doc.content); onEdit() }
                    }
                }
            }
            if (localDocs.isEmpty()) item { EmptyCard("No local drafts yet", "Create a private draft and it will stay in the vault.") }
        } else {
            item { GlassSurface { Text("Google Docs", color = Color.White, fontWeight = FontWeight.Bold); Text("${vm.driveFiles.size} documents available", color = Color(0xFF7E90B0), fontSize = 11.sp); Spacer(Modifier.height(10.dp)); GradientButton("Search / refresh", Modifier.fillMaxWidth()) { vm.fetchDriveFiles() } } }
            items(vm.driveFiles) { file ->
                GlassSurface { Text(file.name, color = Color.White, fontWeight = FontWeight.Bold); Text("Google document", color = Color(0xFF7E90B0), fontSize = 10.sp); Spacer(Modifier.height(9.dp)); SmallGlassButton("Open in editor") { vm.loadDocContent(file.id, file.name); onEdit() } }
            }
        }
    }
    if (showCreate) Dialog(onDismissRequest = { showCreate = false }) { Surface(color = Color(0xFF101C31), shape = RoundedCornerShape(24.dp)) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("New local draft", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp); OutlinedTextField(newTitle, { newTitle = it }, label = { Text("Title") }, singleLine = true); GradientButton("Create", Modifier.fillMaxWidth()) { if (newTitle.isNotBlank()) { vm.createLocalDoc(newTitle); newTitle = ""; showCreate = false } } } } }
}

@Composable
private fun RowScope.Seg(text: String, selected: Boolean, onClick: () -> Unit) { Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (selected) Color(0xFF2A4F86) else Color.Transparent).clickable { onClick() }.padding(vertical = 9.dp), contentAlignment = Alignment.Center) { Text(text, color = if (selected) Color.White else Color(0xFF7E90B0), fontSize = 11.sp, fontWeight = FontWeight.Bold) } }

@Composable
private fun MockFocus(vm: DraftLockViewModel, lockedApps: List<com.draftlock.app.data.LockedApp>, requirements: List<com.draftlock.app.data.AppRequirement>, onManage: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 20.dp, 18.dp, 30.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Focus", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("The lock is the product. Writing unlocks the rest.", color = Color(0xFF7E90B0), fontSize = 12.sp) }
        item { GlassSurface { Text("PROTECTION STATUS", color = Color(0xFF7386A7), fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(7.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(11.dp).clip(CircleShape).background(if (vm.allConditionsComplete()) Color(0xFF61E6B2) else Color(0xFFFFC56A))); Spacer(Modifier.width(9.dp)); Text(if (vm.allConditionsComplete()) "Requirements complete" else "Apps remain locked", color = Color.White, fontWeight = FontWeight.Bold) } } }
        item { Text("LOCKED APPS", color = Color(0xFF7386A7), fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold) }
        items(lockedApps) { app -> GlassSurface { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0x202D8BFF)), contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_lock_closed), null, tint = Color(0xFF6FD9FF), modifier = Modifier.size(20.dp)) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(app.displayName, color = Color.White, fontWeight = FontWeight.Bold); Text(app.packageName, color = Color(0xFF7183A2), fontSize = 9.sp) }; SmallGlassButton("Unlock") { vm.deleteLockedApp(app.packageName) } } } }
        if (lockedApps.isEmpty()) item { EmptyCard("No apps locked", "Use the existing app manager to choose what DraftLock protects.") }
        item { GradientButton("Manage apps & requirements", Modifier.fillMaxWidth(), onManage) }
        item { if (requirements.isNotEmpty()) GlassSurface { Text("REQUIREMENTS", color = Color(0xFF7386A7), fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); requirements.forEach { req -> Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Text(req.displayName, color = Color.White, modifier = Modifier.weight(1f)); Text("${req.requiredMinutes} min", color = Color(0xFF73D9FF), fontSize = 11.sp) } } } }
    }
}

@Composable
private fun MockEditor(vm: DraftLockViewModel, text: String, name: String, onLibrary: () -> Unit) {
    var draft by remember(text) { mutableStateOf(text) }
    var title by remember(name) { mutableStateOf(name) }
    Column(Modifier.fillMaxSize().padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { SmallGlassButton("‹ Library", onLibrary); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("EDITOR", color = Color(0xFF7184A4), fontSize = 9.sp, letterSpacing = 1.4.sp); Text(title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1) }; Text("${draft.trim().split(Regex("\\s+")).count { it.isNotBlank() }}w", color = Color(0xFF69DFFF), fontSize = 11.sp) }
        GlassSurface(Modifier.fillMaxSize()) {
            OutlinedTextField(title, { title = it; vm.setDocumentName(it) }, singleLine = true, label = { Text("Draft title") }, modifier = Modifier.fillMaxWidth(), colors = editorFieldColors())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(draft, { draft = it; vm.onTextChanged(it) }, modifier = Modifier.fillMaxWidth().weight(1f), placeholder = { Text("Start writing…", color = Color(0xFF566986)) }, textStyle = LocalTextStyle.current.copy(color = Color(0xFFEAF2FF), fontSize = 16.sp, lineHeight = 25.sp), colors = editorFieldColors())
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradientButton("Save to Google Doc", Modifier.weight(1f)) { vm.syncTextToDoc() }
                SmallGlassButton("Save as new") { vm.saveAsNewDoc(title) }
            }
        }
    }
}

@Composable
private fun editorFieldColors() = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF55D9FF), unfocusedBorderColor = Color(0x204D719D), focusedLabelColor = Color(0xFF6BDEFF), unfocusedLabelColor = Color(0xFF7183A2), cursorColor = Color(0xFF62DDFF), focusedTextColor = Color.White, unfocusedTextColor = Color.White)

@Composable
private fun MockSettings(vm: DraftLockViewModel, onApps: () -> Unit) {
    val quota by vm.quota.collectAsStateWithLifecycle(); val reset by vm.resetMinutes.collectAsStateWithLifecycle(); val logic by vm.logic.collectAsStateWithLifecycle(); val autoSave by vm.googleAutoSave.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 20.dp, 18.dp, 30.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item { Text("Settings", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("Tune your writing lock without changing how it works.", color = Color(0xFF7E90B0), fontSize = 12.sp) }
        item { GlassSurface { SettingRow("Daily word quota", "$quota words") { Slider(value = quota.toFloat(), onValueChange = { vm.setQuota(it.toInt()) }, valueRange = 100f..10000f) } } }
        item { GlassSurface { SettingRow("Unlock logic", logic) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("AND", "OR").forEach { value -> FilterChip(selected = logic == value, onClick = { vm.setLogic(value) }, label = { Text(value) }) } } } } }
        item { GlassSurface { SettingRow("Google auto-save", if (autoSave) "Enabled" else "Disabled") { Switch(checked = autoSave, onCheckedChange = vm::setGoogleAutoSave) } } }
        item { GlassSurface { Text("Google Drive", color = Color.White, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp)); Text(vm.syncStatus, color = Color(0xFF7E90B0), fontSize = 11.sp); Spacer(Modifier.height(10.dp)); if (vm.isGoogleConnected) SmallGlassButton("Disconnect") { GoogleOAuthManager(androidx.compose.ui.platform.LocalContext.current).disconnect(); vm.checkGoogleConnection() } else GradientButton("Connect Google", Modifier.fillMaxWidth()) { vm.startGoogleAuth(androidx.compose.ui.platform.LocalContext.current) } } }
        item { GradientButton("Manage locked apps", Modifier.fillMaxWidth(), onApps) }
        item { Text("Reset period: ${if (reset == 0) "Daily" else "$reset minutes"}", color = Color(0xFF6F819F), fontSize = 10.sp) }
    }
}

@Composable
private fun SettingRow(title: String, value: String, control: @Composable () -> Unit) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontWeight = FontWeight.Bold); Text(value, color = Color(0xFF7183A2), fontSize = 10.sp) }; control() } }

@Composable
private fun MockOnboarding(onDone: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xF8050B19)), contentAlignment = Alignment.Center) {
        GlassSurface(Modifier.fillMaxWidth().padding(28.dp)) {
            Box(Modifier.size(76.dp).clip(RoundedCornerShape(23.dp)).background(Brush.linearGradient(listOf(Color(0xFF24D9FF), Color(0xFF675CFF)))).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_logo_draftlock), null, tint = Color.White, modifier = Modifier.size(46.dp)) }
            Spacer(Modifier.height(22.dp)); Text("Write first. Unlock later.", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text("DraftLock protects your distractions until you earn access through your writing goals.", color = Color(0xFF8B9BB8), fontSize = 13.sp, lineHeight = 20.sp); Spacer(Modifier.height(22.dp)); GradientButton("Enter your vault", Modifier.fillMaxWidth(), onDone)
        }
    }
}

@Composable
private fun GradientButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.height(48.dp).clip(RoundedCornerShape(15.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF27D8FF), Color(0xFF675CFF)))).clickable { onClick() }, contentAlignment = Alignment.Center) { Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
}

@Composable
private fun SmallGlassButton(text: String, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(13.dp)).background(Color(0x183C638E)).border(1.dp, Color(0x20A8D8FF), RoundedCornerShape(13.dp)).clickable { onClick() }.padding(horizontal = 13.dp, vertical = 10.dp), contentAlignment = Alignment.Center) { Text(text, color = Color(0xFFBBD7F7), fontWeight = FontWeight.Bold, fontSize = 10.sp) }
}

@Composable
private fun EmptyCard(title: String, body: String) { GlassSurface { Text(title, color = Color.White, fontWeight = FontWeight.Bold); Spacer(Modifier.height(5.dp)); Text(body, color = Color(0xFF7183A2), fontSize = 11.sp) } }
