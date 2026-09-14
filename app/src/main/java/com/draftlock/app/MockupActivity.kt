package com.draftlock.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.draftlock.app.ui.theme.DraftLockTheme

private enum class MockScreen { HOME, WRITE, APPS, DOCS, SETTINGS }
private val BgTop = Color(0xFF08162C)
private val Bg = Color(0xFF020815)
private val Ink = Color(0xFFF1F6FF)
private val Muted = Color(0xFF94A8C7)
private val Blue = Color(0xFF2F8CFF)
private val Cyan = Color(0xFF16B8FF)
private val Purple = Color(0xFF6658FF)

class MockupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DraftLockMockup() }
    }
}

@Composable
private fun DraftLockMockup() {
    val vm: DraftLockViewModel = viewModel()
    val context = LocalContext.current
    val words by vm.todayWords.collectAsStateWithLifecycle()
    val quota by vm.quota.collectAsStateWithLifecycle()
    val requirements by vm.requirements.collectAsStateWithLifecycle()
    val lockedApps by vm.lockedApps.collectAsStateWithLifecycle()
    val logic by vm.logic.collectAsStateWithLifecycle()
    val text by vm.text.collectAsStateWithLifecycle()
    val documentName by vm.documentName.collectAsStateWithLifecycle()
    val resetMinutes by vm.resetMinutes.collectAsStateWithLifecycle()
    val googleAutoSave by vm.googleAutoSave.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(MockScreen.HOME) }
    var showOverride by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.checkGoogleConnection()
        if (vm.isGoogleConnected) vm.fetchDriveFiles()
        while (true) {
            kotlinx.coroutines.delay(30_000)
            vm.refreshUsage()
            vm.checkGoogleConnection()
        }
    }
    LaunchedEffect(requirements) { vm.refreshUsage() }
    LaunchedEffect(vm.isGoogleConnected) { if (vm.isGoogleConnected) vm.fetchDriveFiles() }

    DraftLockTheme {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(BgTop, Bg)))) {
            AmbientBackground()
            Column(Modifier.fillMaxSize()) {
                GlassTopBar(vm, words)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (screen) {
                        MockScreen.HOME -> HomeScreen(vm, words, quota, requirements, lockedApps, logic, context, { screen = MockScreen.WRITE }, { showOverride = true })
                        MockScreen.WRITE -> WriteScreen(vm, text, words, quota, documentName)
                        MockScreen.APPS -> UnifiedAppsScreen(vm, context)
                        MockScreen.DOCS -> DocsScreen(vm)
                        MockScreen.SETTINGS -> SettingsScreen(vm, quota, resetMinutes, logic, googleAutoSave) { showOverride = true }
                    }
                }
                GlassBottomNav(screen) { screen = it }
            }
        }
    }

    if (showOverride) AlertDialog(
        onDismissRequest = { showOverride = false },
        title = { Text("Emergency override") },
        text = { Text("DraftLock will unlock blocked apps for 15 minutes. Continue?") },
        confirmButton = { TextButton(onClick = { vm.activateEmergencyOverride(); showOverride = false }) { Text("Unlock for 15 min") } },
        dismissButton = { TextButton(onClick = { showOverride = false }) { Text("Cancel") } }
    )
}

@Composable
private fun AmbientBackground() {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.size(500.dp).offset((-220).dp, (-180).dp).background(Brush.radialGradient(listOf(Blue.copy(alpha=.20f), Color.Transparent)), CircleShape))
        Box(Modifier.size(560.dp).align(Alignment.TopEnd).offset(260.dp, (-220).dp).background(Brush.radialGradient(listOf(Purple.copy(alpha=.16f), Color.Transparent)), CircleShape))
        Box(Modifier.fillMaxWidth().height(260.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Cyan.copy(alpha=.07f)))))
    }
}

@Composable
private fun GlassTopBar(vm: DraftLockViewModel, words: Int) {
    Surface(color=Color(0xAA0B182C), tonalElevation=0.dp, shadowElevation=0.dp) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=16.dp, vertical=10.dp), verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x66152A46)), contentAlignment=Alignment.Center) {
                Icon(painterResource(R.drawable.ic_logo_draftlock), null, tint=Color.Unspecified, modifier=Modifier.size(27.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("draftlock", color=Ink, fontSize=19.sp, fontWeight=FontWeight.Bold)
                Text(if(vm.isGoogleConnected) "Google Drive connected" else "Your private writing vault", color=Muted, fontSize=10.sp)
            }
            Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Blue.copy(alpha=.22f)).padding(horizontal=10.dp, vertical=6.dp)) {
                Text("${words}w", color=Cyan, fontSize=10.sp, fontWeight=FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun GlassBottomNav(screen: MockScreen, onScreen:(MockScreen)->Unit) {
    Surface(color=Color(0xCC071326), tonalElevation=0.dp, shadowElevation=0.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(7.dp), horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            NavItem("Home", R.drawable.ic_home, screen==MockScreen.HOME) { onScreen(MockScreen.HOME) }
            NavItem("Apps", R.drawable.ic_usage, screen==MockScreen.APPS) { onScreen(MockScreen.APPS) }
            Box(Modifier.weight(1.35f).height(50.dp).clip(RoundedCornerShape(17.dp)).background(Brush.horizontalGradient(listOf(Cyan,Purple))).clickable { onScreen(MockScreen.WRITE) }, contentAlignment=Alignment.Center) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_write), null, tint=Color.White, modifier=Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Write", color=Color.White, fontWeight=FontWeight.Bold, fontSize=12.sp)
                }
            }
            NavItem("Docs", R.drawable.ic_docs, screen==MockScreen.DOCS) { onScreen(MockScreen.DOCS) }
            NavItem("Settings", R.drawable.ic_analytics, screen==MockScreen.SETTINGS) { onScreen(MockScreen.SETTINGS) }
        }
    }
}

@Composable
private fun RowScope.NavItem(label:String, icon:Int, selected:Boolean, onClick:()->Unit) {
    Box(Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(15.dp)).background(if(selected) Blue.copy(alpha=.18f) else Color.Transparent).clickable(onClick=onClick), contentAlignment=Alignment.Center) {
        Column(horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Icon(painterResource(icon), null, tint=if(selected) Cyan else Muted, modifier=Modifier.size(19.dp))
            Text(label, color=if(selected) Ink else Muted, fontSize=9.sp, fontWeight=if(selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}