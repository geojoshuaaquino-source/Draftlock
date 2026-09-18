package com.draftlock.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.draftlock.app.data.AppRequirement
import com.draftlock.app.data.LocalDocument
import com.draftlock.app.data.LockedApp
import com.draftlock.app.ui.theme.DraftLockTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class MockPage { HOME, LIBRARY, FOCUS, EDITOR, SETTINGS }
private data class InstalledApp(val packageName: String, val label: String, val icon: Bitmap?)

class MockupActivity : ComponentActivity() {
    private fun handleOAuthActivityResult(data: Intent?) {
        if (data == null) return
        GoogleOAuthManager(this).handleResult(data) { ok, msg ->
            runOnUiThread {
                val vm: DraftLockViewModel = androidx.lifecycle.ViewModelProvider(this)[DraftLockViewModel::class.java]
                if (ok) {
                    vm.checkGoogleConnection()
                    vm.saveGoogleStatus("Google account connected")
                    vm.fetchDriveFiles()
                } else {
                    vm.saveGoogleStatus("Google sign-in failed: $msg")
                }
            }
        }
    }

    @Deprecated("Use Activity Result APIs when refactoring the legacy flow")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != GoogleOAuthManager.AUTH_REQUEST_CODE) return

        if (resultCode != RESULT_OK || data == null) {
            val vm: DraftLockViewModel = androidx.lifecycle.ViewModelProvider(this)[DraftLockViewModel::class.java]
            vm.saveGoogleStatus("Google sign-in cancelled")
            return
        }

        handleOAuthActivityResult(data)
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { val vm: DraftLockViewModel = viewModel(); DraftLockMockupApp(vm) }
    }
}

@Composable
fun DraftLockMockupApp(vm: DraftLockViewModel) {
    val text by vm.text.collectAsStateWithLifecycle()
    val quota by vm.quota.collectAsStateWithLifecycle()
    val words by vm.todayWords.collectAsStateWithLifecycle()
    val name by vm.documentName.collectAsStateWithLifecycle()
    val requirements by vm.requirements.collectAsStateWithLifecycle()
    val lockedApps by vm.lockedApps.collectAsStateWithLifecycle()
    val localDocs by vm.localDocs.collectAsStateWithLifecycle()
    var page by remember { mutableStateOf(MockPage.HOME) }
    var onboarding by remember { mutableStateOf(true) }
    var overrideDialog by remember { mutableStateOf(false) }

    LaunchedEffect(requirements) { vm.refreshUsage() }
    LaunchedEffect(Unit) { vm.checkGoogleConnection(); if (vm.isGoogleConnected) vm.fetchDriveFiles() }

    DraftLockTheme {
        Box(Modifier.fillMaxSize().background(Color(0xFF020817))) {
            MockAtmosphere()
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = { if (page != MockPage.EDITOR) MockHeader(vm, words, quota, page) },
                bottomBar = { if (page != MockPage.EDITOR) MockBottomBar(page) { page = it } }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (page) {
                        MockPage.HOME -> MockHome(vm, words, quota, text, name, lockedApps.size, onOpen = { page = MockPage.EDITOR }, onLibrary = { page = MockPage.LIBRARY }, onFocus = { page = MockPage.FOCUS }) { overrideDialog = true }
                        MockPage.LIBRARY -> MockLibrary(vm, localDocs) { page = MockPage.EDITOR }
                        MockPage.FOCUS -> MockFocus(vm, lockedApps, requirements) { page = MockPage.SETTINGS }
                        MockPage.EDITOR -> MockEditor(vm, text, name) { page = MockPage.LIBRARY }
                        MockPage.SETTINGS -> MockSettings(vm) { page = MockPage.FOCUS }
                    }
                }
            }
            if (onboarding) MockOnboarding { onboarding = false }
            if (overrideDialog) OverrideDialog(vm) { overrideDialog = false }
        }
    }
}

@Composable
private fun MockAtmosphere() {
    Box(Modifier.fillMaxSize().background(Color(0xFF020817))) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x553B4DFF), Color.Transparent), center = androidx.compose.ui.geometry.Offset(140f, 300f), radius = 900f)))
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x403D7BFF), Color.Transparent), center = androidx.compose.ui.geometry.Offset(1000f, 900f), radius = 850f)))
        Box(Modifier.fillMaxWidth().height(300.dp).align(Alignment.TopCenter).background(Brush.verticalGradient(listOf(Color(0x253C6FFF), Color.Transparent))))
        // Soft ribbon-like arcs echo the mockup's flowing background without introducing another screen-sized asset.
        Box(Modifier.fillMaxWidth().height(190.dp).offset(x = (-70).dp, y = 70.dp).clip(RoundedCornerShape(90.dp)).background(Brush.linearGradient(listOf(Color(0x182B5FFF), Color.Transparent))))
        Box(Modifier.fillMaxWidth().height(170.dp).offset(x = 90.dp, y = 470.dp).clip(RoundedCornerShape(90.dp)).background(Brush.linearGradient(listOf(Color(0x142C4DFF), Color.Transparent))))
    }
}

@Composable
private fun GlassSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(Color(0xB50A1730)).border(1.dp, Color(0x263E6EA8), RoundedCornerShape(22.dp)).padding(16.dp), content = content)
}

@Composable
private fun MockHeader(vm: DraftLockViewModel, words: Int, quota: Int, page: MockPage) {
    Row(Modifier.fillMaxWidth().background(Color(0xC9081022)).padding(horizontal = 18.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(Brush.linearGradient(listOf(Color(0xFF4E9DFF), Color(0xFF663CFF)))), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_logo_draftlock), null, tint = Color.White, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text("draft", color = Color(0xFFEAF3FF), fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Text("lock", color = Color(0xFF7562FF), fontSize = 20.sp, fontWeight = FontWeight.Medium)
            }
            Text(if (vm.isGoogleConnected) "Cloud connected" else "Your drafts are safe. Keep going.", color = Color(0xFF7890B7), fontSize = 9.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("$words / $quota", color = Color(0xFF78DFFF), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text(if (page == MockPage.FOCUS) "PROTECTION" else "TODAY", color = Color(0xFF667C9F), fontSize = 8.sp, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun MockBottomBar(page: MockPage, onPage: (MockPage) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xE8061122)).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        MockNav("Home", R.drawable.ic_home, page == MockPage.HOME) { onPage(MockPage.HOME) }
        MockNav("Library", R.drawable.ic_docs, page == MockPage.LIBRARY) { onPage(MockPage.LIBRARY) }
        Box(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(17.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF4D8DFF), Color(0xFF633BFF)))).clickable { onPage(MockPage.EDITOR) }, contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_write), null, tint = Color.White, modifier = Modifier.size(22.dp)) }
        MockNav("Focus", R.drawable.ic_lock_closed, page == MockPage.FOCUS) { onPage(MockPage.FOCUS) }
        MockNav("Settings", R.drawable.ic_analytics, page == MockPage.SETTINGS) { onPage(MockPage.SETTINGS) }
    }
}

@Composable
private fun RowScope.MockNav(label: String, icon: Int, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(15.dp)).clickable { onClick() }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(painterResource(icon), null, tint = if (selected) Color(0xFF6C9FFF) else Color(0xFF637795), modifier = Modifier.size(19.dp))
        Text(label, color = if (selected) Color.White else Color(0xFF637795), fontSize = 8.sp)
    }
}

@Composable
private fun MockHome(vm: DraftLockViewModel, words: Int, quota: Int, text: String, name: String, locked: Int, onOpen: () -> Unit, onLibrary: () -> Unit, onFocus: () -> Unit, onOverride: () -> Unit) {
    val progress = (words.toFloat() / quota.coerceAtLeast(1)).coerceIn(0f, 1f)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item { Text("Good evening,", color = Color(0xFFDCE8FA), fontSize = 18.sp); Text("Your drafts are safe. Keep going.", color = Color(0xFF8398BA), fontSize = 11.sp) }
        item {
            GlassSurface {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HomeStat("${vm.localDocs.collectAsStateWithLifecycle().value.size}", "Total Drafts", Modifier.weight(1f))
                    Divider(Modifier.height(42.dp).width(1.dp), color = Color(0x263C5C87))
                    HomeStat("$locked", "Locked", Modifier.weight(1f))
                    Divider(Modifier.height(42.dp).width(1.dp), color = Color(0x263C5C87))
                    HomeStat("${(vm.localDocs.collectAsStateWithLifecycle().value.size - locked).coerceAtLeast(0)}", "Unlocked", Modifier.weight(1f))
                }
            }
        }
        item {
            GlassSurface {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("TODAY'S PROGRESS", color = Color(0xFF6E86AA), fontSize = 9.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold); Text("$words", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold); Text("of $quota words", color = Color(0xFF7F93B3), fontSize = 11.sp) }
                    Box(Modifier.size(70.dp).clip(CircleShape).background(Color(0x183B75FF)).border(1.dp, Color(0x3B5E8EFF), CircleShape), contentAlignment = Alignment.Center) { Text("${(progress * 100).toInt()}%", color = Color(0xFF71DDFF), fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(12.dp)); LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(8.dp)), color = Color(0xFF5C8FFF), trackColor = Color(0x19365A8A))
            }
        }
        item {
            GlassSurface {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_docs), null, tint = Color(0xFF8A9FFF), modifier = Modifier.size(24.dp)); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1); Text(if (text.isBlank()) "Start a new draft…" else text.take(90), color = Color(0xFF8396B6), fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    SmallGlassButton("Open", onOpen)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                MetricCard("DRAFTS", vm.localDocs.collectAsStateWithLifecycle().value.size.toString(), Modifier.weight(1f))
                MetricCard("LOCKED", locked.toString(), Modifier.weight(1f))
                MetricCard("GOAL", "${(progress * 100).toInt()}%", Modifier.weight(1f))
            }
        }
        item { GlassSurface { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0x1C456FFF)), contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_lock_closed), null, tint = Color(0xFF73AFFF), modifier = Modifier.size(19.dp)) }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("Focus & Locked Apps", color = Color.White, fontWeight = FontWeight.Bold); Text("$locked apps protected", color = Color(0xFF7F92B2), fontSize = 10.sp) }; SmallGlassButton("Open", onFocus) } } }
        item { TextButton(onClick = onOverride, modifier = Modifier.fillMaxWidth()) { Text("Emergency Override • 15 minutes", color = Color(0xFF78DFFF), fontSize = 11.sp) } }
    }
}

@Composable
private fun HomeStat(value: String, label: String, modifier: Modifier) { Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) { Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(label, color = Color(0xFF748BAF), fontSize = 8.sp) } }

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier) { Column(modifier.clip(RoundedCornerShape(17.dp)).background(Color(0xA80B1931)).border(1.dp, Color(0x203B6494), RoundedCornerShape(17.dp)).padding(12.dp)) { Text(title, color = Color(0xFF6F86AA), fontSize = 8.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1) } }

@Composable
private fun MockLibrary(vm: DraftLockViewModel, localDocs: List<LocalDocument>, onEdit: () -> Unit) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    var search by remember { mutableStateOf("") }
    var newTitle by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    var renameId by remember { mutableStateOf<Long?>(null) }
    var renameText by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Library", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("Your drafts, safe locally or in Google Docs.", color = Color(0xFF8095B7), fontSize = 11.sp) }
        item { Row(Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xA50A1A32)).padding(4.dp)) { Seg("All", tab == 0) { tab = 0 }; Seg("Google Docs", tab == 1) { tab = 1 } } }
        item { SearchField(search) { search = it } }
        if (tab == 0) {
            item { Row(verticalAlignment = Alignment.CenterVertically) { Text("DRAFTS", color = Color(0xFF6F86AA), fontSize = 9.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); SmallGlassButton("+ New") { showCreate = true } } }
            val filtered = localDocs.filter { search.isBlank() || it.title.contains(search, true) }
            items(filtered, key = { it.id }) { doc ->
                GlassSurface {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x182E6EFF)), contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_docs), null, tint = Color(0xFF6F9DFF), modifier = Modifier.size(21.dp)) }
                        Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(doc.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1); Text("${doc.wordCount} words • local", color = Color(0xFF7187A9), fontSize = 9.sp) }
                        SmallGlassButton("Open") { vm.selectedLocalDocId = doc.id; vm.setDocumentName(doc.title); vm.onTextChanged(doc.content); onEdit() }
                    }
                    Spacer(Modifier.height(9.dp)); Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { SmallGlassButton("Rename") { renameId = doc.id; renameText = doc.title }; SmallGlassButton("Delete") { vm.deleteLocalDoc(doc.id) } }
                }
            }
            if (filtered.isEmpty()) item { EmptyCard("No drafts found", "Create a local draft or connect Google Docs.") }
        } else {
            item { GlassSurface { Row(verticalAlignment = Alignment.CenterVertically) { Icon(painterResource(R.drawable.ic_google), null, tint = if (vm.isGoogleConnected) Color(0xFF6EDCFF) else Color.White, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(if (vm.isGoogleConnected) "Google connected" else "Google Docs", color = Color.White, fontWeight = FontWeight.Bold); Text(vm.syncStatus, color = Color(0xFF7489AA), fontSize = 9.sp) }; if (vm.isGoogleConnected) SmallGlassButton("Refresh") { vm.fetchDriveFiles(search) } else GradientButton("Connect", Modifier.width(92.dp)) { vm.startGoogleAuth(context) } } } }
            if (vm.isGoogleConnected) {
                items(vm.driveFiles.filter { search.isBlank() || it.name.contains(search, true) }, key = { it.id }) { file -> GlassSurface { Row(verticalAlignment = Alignment.CenterVertically) { Icon(painterResource(R.drawable.ic_docs), null, tint = Color(0xFF718FFF), modifier = Modifier.size(23.dp)); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(file.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1); Text("Google document", color = Color(0xFF7187A9), fontSize = 9.sp) }; SmallGlassButton("Open") { vm.loadDocContent(file.id, file.name); onEdit() } } } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { var createName by remember { mutableStateOf("") }; OutlinedTextField(createName, { createName = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("New Google Doc") }, colors = editorFieldColors()); GradientButton("Create", Modifier.width(90.dp)) { if (createName.isNotBlank()) { vm.createGoogleDoc(createName); createName = "" } } } }
            } else item { EmptyCard("Google not connected", "Connect Google to browse, create, load and sync Docs.") }
        }
    }
    if (showCreate) Dialog(onDismissRequest = { showCreate = false }) { Surface(color = Color(0xFF0A1730), shape = RoundedCornerShape(24.dp)) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("New local draft", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold); OutlinedTextField(newTitle, { newTitle = it }, label = { Text("Title") }, singleLine = true, colors = editorFieldColors()); GradientButton("Create", Modifier.fillMaxWidth()) { if (newTitle.isNotBlank()) { vm.createLocalDoc(newTitle); newTitle = ""; showCreate = false } } } } }
    if (renameId != null) Dialog(onDismissRequest = { renameId = null }) { Surface(color = Color(0xFF0A1730), shape = RoundedCornerShape(24.dp)) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("Rename draft", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold); OutlinedTextField(renameText, { renameText = it }, label = { Text("Title") }, singleLine = true, colors = editorFieldColors()); GradientButton("Save", Modifier.fillMaxWidth()) { if (renameText.isNotBlank()) { vm.renameLocalDoc(renameId!!, renameText); renameId = null } } } } }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) { OutlinedTextField(value, onChange, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search drafts…", color = Color(0xFF647A9D)) }, leadingIcon = { Icon(painterResource(R.drawable.ic_docs), null, tint = Color(0xFF739BFF)) }, colors = editorFieldColors(), shape = RoundedCornerShape(15.dp)) }

@Composable
private fun RowScope.Seg(text: String, selected: Boolean, onClick: () -> Unit) { Box(Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (selected) Color(0xFF443DD0) else Color.Transparent).clickable { onClick() }.padding(horizontal = 22.dp, vertical = 9.dp), contentAlignment = Alignment.Center) { Text(text, color = if (selected) Color.White else Color(0xFF7186A8), fontSize = 10.sp, fontWeight = FontWeight.Bold) } }

@Composable
private fun MockFocus(vm: DraftLockViewModel, lockedApps: List<LockedApp>, requirements: List<AppRequirement>, onManage: () -> Unit) {
    val context = LocalContext.current
    var manage by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("ALL") }
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    LaunchedEffect(manage) {
        if (manage) apps = withContext(Dispatchers.IO) { context.packageManager.getInstalledApplications(0).mapNotNull { a -> if (a.packageName == context.packageName) null else { val label = context.packageManager.getApplicationLabel(a).toString(); val icon = try { val d = context.packageManager.getApplicationIcon(a.packageName); Bitmap.createBitmap(72,72,Bitmap.Config.ARGB_8888).also { b -> d.setBounds(0,0,72,72); d.draw(Canvas(b)) } } catch (_: Exception) { null }; InstalledApp(a.packageName,label,icon) } }.sortedBy { it.label.lowercase() } }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Focus & Locked Apps", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold); Text("Block distractions. Stay in the zone.", color = Color(0xFF8095B7), fontSize = 11.sp) }
        item { GlassSurface { Row(verticalAlignment = Alignment.CenterVertically) { Icon(painterResource(R.drawable.ic_lock_closed), null, tint = Color(0xFF7A9CFF), modifier = Modifier.size(25.dp)); Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text("Focus Mode", color = Color.White, fontWeight = FontWeight.Bold); Text("Unlock conditions remain unchanged.", color = Color(0xFF7086A9), fontSize = 9.sp) }; Switch(checked = true, onCheckedChange = null) } ; Spacer(Modifier.height(12.dp)); Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color(0xA3142542)).padding(4.dp)) { listOf("Pomodoro","Custom","None").forEach { SmallMode(it, it == "Pomodoro") } }; Spacer(Modifier.height(10.dp)); SmallGlassButton("Refresh usage") { vm.refreshUsage() } } }
        item { GlassSurface { Text("PROTECTION STATUS", color = Color(0xFF7187AA), fontSize = 9.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(9.dp).clip(CircleShape).background(if (vm.allConditionsComplete()) Color(0xFF5CE2AE) else Color(0xFFFFC76B))); Spacer(Modifier.width(8.dp)); Text(if (vm.allConditionsComplete()) "Requirements complete" else "Apps remain locked", color = Color.White, fontWeight = FontWeight.Bold) } } }
        item { Row(verticalAlignment = Alignment.CenterVertically) { Text("LOCKED APPS", color = Color(0xFF7187AA), fontSize = 9.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); GradientButton("Manage", Modifier.width(100.dp)) { manage = true } } }
        items(lockedApps) { app -> FocusAppRow(vm, app, requirements.find { it.packageName == app.packageName }) }
        if (lockedApps.isEmpty()) item { EmptyCard("No apps locked", "Use Manage to choose which apps DraftLock protects.") }
        item { GlassSurface { Text("APP PROTECTION", color = Color(0xFF7187AA), fontSize = 9.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text("${requirements.size} requirements • ${lockedApps.size} blocked", color = Color.White, fontWeight = FontWeight.Bold); Text(vm.blockingDiagnostics, color = if (vm.blockingAvailable) Color(0xFF71DDB1) else Color(0xFFFF789A), fontSize = 9.sp); Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { if (!vm.blockingAvailable) SmallGlassButton("Enable blocking") { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }; if (!vm.usageAccess) SmallGlassButton("Usage access") { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } } } }
        item { GradientButton("Manage apps & requirements", Modifier.fillMaxWidth(), onManage) }
        item { TextButton(onClick = { vm.activateEmergencyOverride() }, modifier = Modifier.fillMaxWidth()) { Text("Emergency Override • 15 minutes", color = Color(0xFF78DFFF), fontSize = 11.sp) } }
    }
    if (manage) Dialog(onDismissRequest = { manage = false }) { Surface(color = Color(0xFF07142A), shape = RoundedCornerShape(24.dp)) { Column(Modifier.fillMaxWidth().padding(18.dp)) { Text("Manage apps", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp)); OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search apps") }, colors = editorFieldColors()); Row(Modifier.padding(vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("ALL","BLOCKED","REQUIRED","AVAILABLE").forEach { f -> SmallMode(f, filter == f) } }; LazyColumn(Modifier.heightIn(max = 470.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { items(apps.filter { it.label.contains(query,true) || it.packageName.contains(query,true) }.filter { when(filter) { "BLOCKED" -> vm.isLocked(it.packageName)!=null; "REQUIRED" -> vm.isRequirement(it.packageName)!=null; "AVAILABLE" -> vm.isLocked(it.packageName)==null && vm.isRequirement(it.packageName)==null; else -> true } }) { app -> ManageAppRow(vm, app) } } } } }
}

@Composable
private fun FocusAppRow(vm: DraftLockViewModel, app: LockedApp, req: AppRequirement?) { GlassSurface { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0x202C66FF)), contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_lock_closed), null, tint = Color(0xFF78A8FF), modifier = Modifier.size(20.dp)) }; Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(app.displayName, color = Color.White, fontWeight = FontWeight.Bold); Text(if (req == null) "Blocked" else "${req.requiredMinutes} min requirement", color = Color(0xFF7187A9), fontSize = 9.sp) }; SmallGlassButton("Unblock") { vm.deleteLockedApp(app.packageName) } } } }

@Composable
private fun ManageAppRow(vm: DraftLockViewModel, app: InstalledApp) {
    val req = vm.isRequirement(app.packageName)
    val locked = vm.isLocked(app.packageName)
    var minutes by remember(app.packageName) { mutableStateOf((req?.requiredMinutes ?: 30).toFloat()) }
    GlassSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (app.icon != null) Image(app.icon!!.asImageBitmap(), app.label, Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))) else Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x1C396BFF)), contentAlignment = Alignment.Center) { Text(app.label.take(1), color = Color(0xFF7CA5FF), fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(9.dp)); Column(Modifier.weight(1f)) { Text(app.label, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(app.packageName, color = Color(0xFF61789B), fontSize = 8.sp, maxLines = 1) }
        }
        Spacer(Modifier.height(7.dp)); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (req == null) SmallGlassButton("Require") { vm.addRequirement(AppRequirement(packageName = app.packageName, displayName = app.label, requiredMinutes = minutes.toInt().coerceIn(5,480))) } else { SmallGlassButton("${req.requiredMinutes}m") { vm.updateRequirementMinutes(req.id, (req.requiredMinutes + 5).coerceAtMost(480)) }; SmallGlassButton("Remove") { vm.deleteRequirement(req.id) } }
            if (locked == null) SmallGlassButton("Block") { vm.addLockedApp(LockedApp(app.packageName, app.label)) } else SmallGlassButton("Unblock") { vm.deleteLockedApp(app.packageName) }
        }
        if (req != null) Slider(value = minutes, onValueChange = { minutes = it }, valueRange = 5f..480f, steps = 94, onValueChangeFinished = { vm.updateRequirementMinutes(req.id, minutes.toInt()) })
    }
}

@Composable
private fun SmallMode(text: String, selected: Boolean) { Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if (selected) Color(0xFF4B3DD1) else Color.Transparent).padding(horizontal = 15.dp, vertical = 9.dp), contentAlignment = Alignment.Center) { Text(text, color = if (selected) Color.White else Color(0xFF8195B7), fontSize = 9.sp, fontWeight = FontWeight.Bold) } }

@Composable
private fun MockEditor(vm: DraftLockViewModel, initialText: String, initialName: String, onLibrary: () -> Unit) {
    val googleAutoSave by vm.googleAutoSave.collectAsStateWithLifecycle()
    val docId by vm.googleDocumentId.collectAsStateWithLifecycle()
    var draft by remember(initialText) { mutableStateOf(initialText) }
    var title by remember(initialName) { mutableStateOf(initialName) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { SmallGlassButton("‹", onLibrary); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("The Next Chapter", color = Color(0xFF6F86A8), fontSize = 8.sp) }; Text("${wordCount(draft)} words", color = Color(0xFF789DFF), fontSize = 9.sp) }
        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x182C58B0)).border(1.dp, Color(0x284A70C0), RoundedCornerShape(12.dp)).padding(horizontal = 11.dp, vertical = 6.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(painterResource(R.drawable.ic_lock_closed), null, tint = Color(0xFF78A9FF), modifier = Modifier.size(13.dp)); Spacer(Modifier.width(5.dp)); Text(if (vm.allConditionsComplete()) "Unlocked" else "Locked", color = Color(0xFFB8CFFF), fontSize = 9.sp) } }; Spacer(Modifier.weight(1f)); Text("•••", color = Color(0xFF8398B8)) }
        GlassSurface(Modifier.weight(1f)) {
            OutlinedTextField(title, { title = it; vm.setDocumentName(it) }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Title") }, colors = editorFieldColors())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(draft, { draft = it; vm.onTextChanged(it); if (vm.selectedLocalDocId != null) vm.updateLocalDocContent(vm.selectedLocalDocId!!, it) }, modifier = Modifier.fillMaxWidth().weight(1f), placeholder = { Text("Start writing…", color = Color(0xFF556D91)) }, textStyle = LocalTextStyle.current.copy(color = Color(0xFFE7F0FF), fontSize = 15.sp, lineHeight = 24.sp), colors = editorFieldColors())
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { FormatKey("B"); FormatKey("I"); FormatKey("U"); FormatKey("☷"); FormatKey("↗") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${if (googleAutoSave) "Auto-save" else "Manual save"}", color = Color(0xFF67DDB5), fontSize = 9.sp, modifier = Modifier.weight(1f))
            SmallGlassButton("Save as new") { vm.saveAsNewDoc(title) }
            Spacer(Modifier.width(6.dp)); GradientButton(if (docId.isBlank()) "Save" else "Save", Modifier.width(105.dp)) { vm.syncTextToDoc() }
        }
    }
}

@Composable
private fun FormatKey(text: String) { Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x121F3C66)).clickable { }.border(1.dp, Color(0x203F6090), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Text(text, color = Color(0xFFB8C9E5), fontWeight = FontWeight.Bold, fontSize = 12.sp) } }

@Composable
private fun MockSettings(vm: DraftLockViewModel, onApps: () -> Unit) {
    val context = LocalContext.current
    val quota by vm.quota.collectAsStateWithLifecycle(); val reset by vm.resetMinutes.collectAsStateWithLifecycle(); val logic by vm.logic.collectAsStateWithLifecycle(); val autoSave by vm.googleAutoSave.collectAsStateWithLifecycle()
    var resetText by remember(reset) { mutableStateOf(reset.toString()) }
    var quotaText by remember(quota) { mutableStateOf(quota.toString()) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp,18.dp,18.dp,28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Settings", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("Everything that controls the lock, in one place.", color = Color(0xFF8095B7), fontSize = 11.sp) }
        item { GlassSurface { SettingTextField("Daily word quota", quotaText) { quotaText = it.filter(Char::isDigit) }; GradientButton("Save quota", Modifier.fillMaxWidth()) { vm.setQuota(quotaText.toIntOrNull() ?: quota) } } }
        item { GlassSurface { SettingTextField("Reset minutes after period start", resetText) { resetText = it.filter(Char::isDigit) }; GradientButton("Save reset", Modifier.fillMaxWidth()) { vm.setResetMinutes(resetText.toIntOrNull()?.coerceIn(0,1439) ?: reset) } } }
        item { GlassSurface { SettingRow("Unlock logic", logic) { Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { SmallGlassButton("AND") { vm.setLogic("AND") }; SmallGlassButton("OR") { vm.setLogic("OR") } } }; Spacer(Modifier.height(8.dp)); SettingRow("Google auto-save", if (autoSave) "Enabled" else "Disabled") { Switch(autoSave, vm::setGoogleAutoSave) } } }
        item { GlassSurface { Row(verticalAlignment = Alignment.CenterVertically) { Icon(painterResource(R.drawable.ic_google), null, tint = if (vm.isGoogleConnected) Color(0xFF6EDCFF) else Color.White, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("Google Drive", color = Color.White, fontWeight = FontWeight.Bold); Text(vm.syncStatus, color = Color(0xFF7489AA), fontSize = 9.sp) }; if (vm.isGoogleConnected) SmallGlassButton("Disconnect") { com.draftlock.app.GoogleOAuthManager(context).disconnect(); vm.checkGoogleConnection() } else GradientButton("Connect", Modifier.width(92.dp)) { vm.startGoogleAuth(context) } } } }
        item { GlassSurface { Text("Blocking & access", color = Color.White, fontWeight = FontWeight.Bold); Text(vm.blockingDiagnostics, color = if (vm.blockingAvailable) Color(0xFF6DE0B4) else Color(0xFFFF789A), fontSize = 9.sp); Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { SmallGlassButton("Accessibility") { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }; SmallGlassButton("Usage access") { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } } } }
        item { GradientButton("Focus & locked apps", Modifier.fillMaxWidth(), onApps) }
        item { TextButton(onClick = { vm.activateEmergencyOverride() }, modifier = Modifier.fillMaxWidth()) { Text("Emergency override • 15 minutes", color = Color(0xFF78DFFF), fontSize = 11.sp) } }
    }
}

@Composable
private fun SettingTextField(label: String, value: String, onChange: (String) -> Unit) { OutlinedTextField(value, onChange, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true, colors = editorFieldColors()); Spacer(Modifier.height(9.dp)) }

@Composable
private fun SettingRow(title: String, value: String, control: @Composable () -> Unit) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontWeight = FontWeight.Bold); Text(value, color = Color(0xFF7187A9), fontSize = 9.sp) }; control() } }

@Composable
private fun MockOnboarding(onDone: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xF5020818)), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(86.dp).clip(RoundedCornerShape(27.dp)).background(Brush.linearGradient(listOf(Color(0xFF4D8DFF), Color(0xFF633BFF)))), contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_logo_draftlock), null, tint = Color.White, modifier = Modifier.size(54.dp)) }
            Spacer(Modifier.height(20.dp)); Row { Text("draft", color = Color(0xFFEAF3FF), fontSize = 31.sp, fontWeight = FontWeight.Medium); Text("lock", color = Color(0xFF7562FF), fontSize = 31.sp, fontWeight = FontWeight.Medium) }
            Spacer(Modifier.height(6.dp)); Text("Write freely.\nKeep it yours.", color = Color(0xFF9AB8FF), fontSize = 16.sp, lineHeight = 22.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(35.dp)); GradientButton("Continue with Email", Modifier.fillMaxWidth(), onDone); Spacer(Modifier.height(10.dp)); SecondaryAuthButton("Continue with Google", R.drawable.ic_google) { onDone() }; Spacer(Modifier.height(18.dp)); Text("Already have an account?  Log in", color = Color(0xFF7189B2), fontSize = 10.sp)
        }
    }
}

@Composable
private fun SecondaryAuthButton(text: String, icon: Int, onClick: () -> Unit) { Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(Color(0x12172A45)).border(1.dp, Color(0x29456B9E), RoundedCornerShape(24.dp)).clickable { onClick() }, contentAlignment = Alignment.Center) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(painterResource(icon), null, tint = Color.White, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(9.dp)); Text(text, color = Color(0xFFD7E5FA), fontSize = 11.sp) } } }

@Composable
private fun OverrideDialog(vm: DraftLockViewModel, close: () -> Unit) { AlertDialog(onDismissRequest = close, containerColor = Color(0xFF0B1730), title = { Text("Emergency Override", color = Color.White) }, text = { Text("Unlock blocked apps for 15 minutes?", color = Color(0xFFB2C3DF)) }, confirmButton = { TextButton(onClick = { vm.activateEmergencyOverride(); close() }) { Text("Unlock", color = Color(0xFF73DFFF)) } }, dismissButton = { TextButton(onClick = close) { Text("Cancel", color = Color(0xFF8396B5)) } }) }

@Composable
private fun GradientButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) { Box(modifier.height(46.dp).clip(RoundedCornerShape(15.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF4E8FFF), Color(0xFF613CFF)))).clickable { onClick() }, contentAlignment = Alignment.Center) { Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp) } }

@Composable
private fun SmallGlassButton(text: String, onClick: () -> Unit) { Box(Modifier.clip(RoundedCornerShape(13.dp)).background(Color(0x152D4D78)).border(1.dp, Color(0x29466C9B), RoundedCornerShape(13.dp)).clickable { onClick() }.padding(horizontal = 12.dp, vertical = 9.dp), contentAlignment = Alignment.Center) { Text(text, color = Color(0xFFBFD4F2), fontWeight = FontWeight.Bold, fontSize = 9.sp) } }

@Composable
private fun EmptyCard(title: String, body: String) { GlassSurface { Text(title, color = Color.White, fontWeight = FontWeight.Bold); Spacer(Modifier.height(5.dp)); Text(body, color = Color(0xFF7187A9), fontSize = 10.sp) } }

@Composable
private fun editorFieldColors() = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF5B9CFF), unfocusedBorderColor = Color(0x25476A99), focusedLabelColor = Color(0xFF76DFFF), unfocusedLabelColor = Color(0xFF7187A9), cursorColor = Color(0xFF6EDFFF), focusedTextColor = Color.White, unfocusedTextColor = Color.White)

private fun wordCount(value: String): Int = value.trim().split(Regex("\\s+")).count { it.isNotBlank() }
