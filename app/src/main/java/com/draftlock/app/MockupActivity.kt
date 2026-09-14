package com.draftlock.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BasicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext

private val MockBg = Color(0xFF020816)
private val MockBg2 = Color(0xFF07152B)
private val Glass = Color(0x66213A63)
private val GlassStrong = Color(0x8C0C1D38)
private val GlassSoft = Color(0x3D183152)
private val GlassLine = Color(0x664E83CA)
private val MockInk = Color(0xFFF2F6FF)
private val MockMuted = Color(0xFF94A8C7)
private val MockBlue = Color(0xFF2F8CFF)
private val MockBlueBright = Color(0xFF16B8FF)
private val MockPurple = Color(0xFF6758FF)
private val MockGreen = Color(0xFF44E2B2)

private enum class MockScreen(val label: String, val icon: Int) {
    HOME("Home", R.drawable.ic_home),
    LIBRARY("Library", R.drawable.ic_docs),
    FOCUS("Focus", R.drawable.ic_usage),
    SETTINGS("Settings", R.drawable.ic_analytics)
}

class MockupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DraftLockMockupApp()
        }
    }
}

@Composable
private fun DraftLockMockupApp() {
    val vm: DraftLockViewModel = viewModel()
    val context = LocalContext.current
    var showOnboarding by remember { mutableStateOf(true) }
    var screen by remember { mutableStateOf(MockScreen.HOME) }
    var editorOpen by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        vm.checkGoogleConnection()
        if (vm.isGoogleConnected) showOnboarding = false
        if (status.isBlank()) status = vm.syncStatus
    }

    DraftLockTheme {
        MockBackdrop {
            when {
                showOnboarding -> OnboardingScreen(
                    onEmail = { showOnboarding = false },
                    onGoogle = {
                        vm.startGoogleAuth(context)
                        status = "Opening Google sign-in…"
                    },
                    onGithub = { showOnboarding = false }
                )
                editorOpen -> EditorScreen(
                    vm = vm,
                    onBack = { editorOpen = false },
                    onSaved = { status = "Saved" }
                )
                else -> MainMockupScreen(
                    vm = vm,
                    screen = screen,
                    status = status,
                    onScreen = { screen = it },
                    onOpenEditor = { editorOpen = true },
                    onGoogle = {
                        vm.startGoogleAuth(context)
                        status = "Opening Google sign-in…"
                    },
                    onStatus = { status = it }
                )
            }
        }
    }
}

@Composable
private fun MockBackdrop(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MockBg2, MockBg, Color(0xFF01040C))
                )
            )
    ) {
        Box(
            Modifier
                .size(520.dp)
                .offset(x = (-170).dp, y = (-150).dp)
                .background(
                    Brush.radialGradient(
                        listOf(MockBlue.copy(alpha = 0.20f), Color.Transparent)
                    ),
                    CircleShape
                )
        )
        Box(
            Modifier
                .size(620.dp)
                .align(Alignment.TopEnd)
                .offset(x = 220.dp, y = (-250).dp)
                .background(
                    Brush.radialGradient(
                        listOf(MockPurple.copy(alpha = 0.14f), Color.Transparent)
                    ),
                    CircleShape
                )
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, MockBlue.copy(alpha = 0.10f))
                    )
                )
        )
        content()
    }
}

@Composable
private fun OnboardingScreen(
    onEmail: () -> Unit,
    onGoogle: () -> Unit,
    onGithub: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.weight(1f))
        LogoLock(78.dp)
        Spacer(Modifier.height(22.dp))
        Row {
            Text("draft", fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = MockInk, letterSpacing = (-1.2).sp)
            Text("lock", fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = MockBlueBright, letterSpacing = (-1.2).sp)
        }
        Spacer(Modifier.height(20.dp))
        Text("Write freely.", fontSize = 18.sp, color = MockMuted)
        Text("Keep it yours.", fontSize = 18.sp, color = MockMuted)
        Spacer(Modifier.height(54.dp))
        Dots(active = 0)
        Spacer(Modifier.height(18.dp))
        MockButton("Continue with Email", MockBlue, Color.White, R.drawable.ic_write, onEmail)
        Spacer(Modifier.height(10.dp))
        MockButton("Continue with Google", GlassSoft, MockInk, R.drawable.ic_google, onGoogle, outlined = true)
        Spacer(Modifier.height(14.dp))
        DividerLabel("or")
        Spacer(Modifier.height(14.dp))
        MockButton("Continue with GitHub", GlassSoft, MockInk, R.drawable.ic_key, onGithub, outlined = true)
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onEmail) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Already have an account?", color = MockMuted, fontSize = 11.sp)
                Text("Log in", color = MockBlueBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.weight(0.8f))
    }
}

@Composable
private fun MainMockupScreen(
    vm: DraftLockViewModel,
    screen: MockScreen,
    status: String,
    onScreen: (MockScreen) -> Unit,
    onOpenEditor: () -> Unit,
    onGoogle: () -> Unit,
    onStatus: (String) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        when (screen) {
            MockScreen.HOME -> HomeMock(vm, onOpenEditor, onGoogle, onStatus)
            MockScreen.LIBRARY -> LibraryMock(vm, onOpenEditor)
            MockScreen.FOCUS -> FocusMock(vm, onStatus)
            MockScreen.SETTINGS -> SettingsMock(vm, status, onGoogle, onStatus)
        }
        GlassBottomNav(screen, onScreen)
    }
}

@Composable
private fun HomeMock(
    vm: DraftLockViewModel,
    onOpenEditor: () -> Unit,
    onGoogle: () -> Unit,
    onStatus: (String) -> Unit
) {
    val todayWords by vm.todayWords.collectAsState(initial = 0)
    val localDocs by vm.localDocs.collectAsState(initial = emptyList())
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                LogoLock(30.dp)
                Spacer(Modifier.width(8.dp))
                Row {
                    Text("draft", fontSize = 18.sp, color = MockInk, fontWeight = FontWeight.SemiBold)
                    Text("lock", fontSize = 18.sp, color = MockBlueBright, fontWeight = FontWeight.SemiBold)
                }
            }
            AvatarCircle()
        }
        Spacer(Modifier.height(18.dp))
        Text("Good evening,", fontSize = 22.sp, color = MockInk, fontWeight = FontWeight.SemiBold)
        Text("Your drafts are safe. Keep going.", fontSize = 12.sp, color = MockMuted)
        Spacer(Modifier.height(16.dp))
        GlassCard {
            Row(Modifier.fillMaxWidth()) {
                Stat("${maxOf(12, localDocs.size)}", "Total Drafts", Modifier.weight(1f))
                Stat("4", "Locked", Modifier.weight(1f), MockPurple)
                Stat("${maxOf(8, localDocs.size + 5)}", "Unlocked", Modifier.weight(1f), MockGreen)
            }
        }
        Spacer(Modifier.height(12.dp))
        SearchBar()
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("All", true)
            Chip("Locked", false)
            Chip("Unlocked", false)
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            item {
                DraftRow("The Next Chapter", "The city was quieter than usual…", "Updated 2h ago • 1,428 words", true, onOpenEditor)
            }
            item {
                DraftRow("Project Aurora", "It started with a single message…", "Updated 5h ago • 892 words", false, onOpenEditor)
            }
            item {
                DraftRow("Untitled Draft", "Ideas… maybe something here…", "Updated 1d ago • 204 words", false, onOpenEditor)
            }
            item {
                DraftRow("The Last Light", "Some doors are not meant to be…", "Updated 2d ago • 1,203 words", true, onOpenEditor)
            }
        }
        FloatingPlus()
    }
}

@Composable
private fun LibraryMock(vm: DraftLockViewModel, onOpenEditor: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        TopTitle("Library")
        Spacer(Modifier.height(14.dp))
        SearchBar("Search drafts…")
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
            item { DraftRow("The Next Chapter", "The city was quieter than usual…", "1,428 words", true, onOpenEditor) }
            item { DraftRow("Project Aurora", "It started with a single message…", "892 words", false, onOpenEditor) }
            item { DraftRow("Untitled Draft", "Ideas… maybe something here…", "204 words", false, onOpenEditor) }
            item { DraftRow("The Last Light", "Some doors are not meant to be…", "1,203 words", true, onOpenEditor) }
        }
    }
}

@Composable
private fun FocusMock(vm: DraftLockViewModel, onStatus: (String) -> Unit) {
    var focus by remember { mutableStateOf(true) }
    var yt by remember { mutableStateOf(true) }
    var insta by remember { mutableStateOf(true) }
    var tiktok by remember { mutableStateOf(true) }
    var discord by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        TopTitle("Focus & Locked Apps")
        Spacer(Modifier.height(14.dp))
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_usage), null, tint = MockBlueBright, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Focus Mode", color = MockInk, fontWeight = FontWeight.SemiBold)
                    Text("Block distractions. Stay in the zone.", color = MockMuted, fontSize = 11.sp)
                }
                BlueSwitch(focus) { focus = it }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0x48183B6F))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Seg("Pomodoro", true)
                Seg("Custom", false)
                Seg("None", false)
            }
            Spacer(Modifier.height(10.dp))
            Selector("25:00")
            Spacer(Modifier.height(9.dp))
            MockButton("Start Focus", MockBlue, Color.White, R.drawable.ic_play, { onStatus("Focus started") })
        }
        Spacer(Modifier.height(12.dp))
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_lock_closed), null, tint = MockBlueBright, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Locked Apps", color = MockInk, fontWeight = FontWeight.SemiBold)
                    Text("These apps will be locked while Focus is active.", color = MockMuted, fontSize = 10.sp)
                }
            }
            AppToggle("YouTube", R.drawable.ic_google, yt) { yt = it }
            AppToggle("Instagram", R.drawable.ic_write, insta) { insta = it }
            AppToggle("TikTok", R.drawable.ic_usage, tiktok) { tiktok = it }
            AppToggle("Discord", R.drawable.ic_docs, discord) { discord = it }
        }
        Spacer(Modifier.height(12.dp))
        SmallActionCard("App Protection", "Require authentication to open locked apps.", R.drawable.ic_shield)
        Spacer(Modifier.height(8.dp))
        SmallActionCard("Emergency Override", "Allow access for 15 minutes.", R.drawable.ic_rules)
    }
}

@Composable
private fun SettingsMock(vm: DraftLockViewModel, status: String, onGoogle: () -> Unit, onStatus: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        TopTitle("Settings")
        Spacer(Modifier.height(14.dp))
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogoLock(36.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("DraftLock", color = MockInk, fontWeight = FontWeight.SemiBold)
                    Text("Write freely. Keep it yours.", color = MockMuted, fontSize = 11.sp)
                }
                Text("v1.0", color = MockMuted, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        GlassCard {
            Text("Google account", color = MockInk, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(status.ifBlank { if (vm.isGoogleConnected) "Connected" else "Not connected" }, color = MockMuted, fontSize = 11.sp)
            Spacer(Modifier.height(10.dp))
            MockButton(if (vm.isGoogleConnected) "Google connected" else "Connect Google", MockBlue, Color.White, R.drawable.ic_google, if (vm.isGoogleConnected) ({ onStatus("Already connected") }) else onGoogle)
        }
        Spacer(Modifier.height(10.dp))
        SmallActionCard("Daily goal", "${vm.todayWords.value} words written today", R.drawable.ic_write)
        Spacer(Modifier.height(8.dp))
        SmallActionCard("Privacy", "Your vault stays local unless you sync it.", R.drawable.ic_shield)
    }
}

@Composable
private fun EditorScreen(vm: DraftLockViewModel, onBack: () -> Unit, onSaved: () -> Unit) {
    var draft by remember { mutableStateOf("The city was quieter than usual, the kind of silence that felt deliberate rather than peaceful. He stood at the edge of the rooftop, watching the lights flicker on one by one, each window a reminder of lives he'd never really be a part of.\n\nIt wasn’t that he didn’t want to belong. It was just easier to keep moving. The past had a way of finding people, and he’d learned long ago that the best way to survive was to stay ahead of it.") }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = MockInk, fontSize = 28.sp, modifier = Modifier.clickable { onBack() })
            Spacer(Modifier.width(8.dp))
            Text("The Next Chapter", color = MockInk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("•••", color = MockMuted, fontSize = 14.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge("🔒 Locked")
            Spacer(Modifier.weight(1f))
            Text("1,428 words", color = MockMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        GlassCard(modifier = Modifier.weight(1f)) {
            Text("Chapter 1", color = MockInk, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth().weight(1f),
                textStyle = TextStyle(color = MockInk, fontSize = 14.sp, lineHeight = 22.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MockBlueBright)
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x4A162C4A)).padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text("B", color = MockInk, fontWeight = FontWeight.Bold)
                Text("I", color = MockInk, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                Text("U", color = MockInk)
                Text("☷", color = MockInk)
                Text("⌕", color = MockInk)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("● Auto-save", color = MockGreen, fontSize = 10.sp, modifier = Modifier.weight(1f))
            MockButton("🔒 Save", MockBlue, Color.White, null, { onSaved() }, modifier = Modifier.width(140.dp))
        }
    }
}

@Composable
private fun TopTitle(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("‹", color = MockMuted, fontSize = 26.sp)
        Spacer(Modifier.width(8.dp))
        Text(title, color = MockInk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text("•••", color = MockMuted, fontSize = 13.sp)
    }
}

@Composable
private fun GlassBottomNav(screen: MockScreen, onScreen: (MockScreen) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .shadow(12.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xAF061323))
            .border(1.dp, GlassLine, RoundedCornerShape(24.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            listOf(MockScreen.HOME, MockScreen.LIBRARY, MockScreen.FOCUS, MockScreen.SETTINGS).forEach { item ->
                val selected = item == screen
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { onScreen(item) }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(painterResource(item.icon), null, tint = if (selected) MockBlueBright else MockMuted, modifier = Modifier.size(18.dp))
                    Text(item.label, fontSize = 8.sp, color = if (selected) MockInk else MockMuted)
                }
            }
        }
    }
}

@Composable
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(GlassStrong)
            .border(1.dp, GlassLine, RoundedCornerShape(18.dp))
            .padding(14.dp),
        content = content
    )
}

@Composable
private fun DraftRow(title: String, detail: String, meta: String, locked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(Glass)
            .border(1.dp, GlassLine.copy(alpha = 0.55f), RoundedCornerShape(15.dp))
            .clickable { onClick() }
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(35.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x46223F69)), contentAlignment = Alignment.Center) {
            Icon(painterResource(if (locked) R.drawable.ic_lock_closed else R.drawable.ic_docs), null, tint = MockBlueBright, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = MockInk, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Badge(if (locked) "Locked" else "Unlocked", locked)
            }
            Text(detail, color = MockMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, color = MockMuted, fontSize = 9.sp)
        }
        Text("›", color = MockMuted, fontSize = 22.sp)
    }
}

@Composable
private fun Badge(text: String, locked: Boolean = false) {
    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if (locked) Color(0x382E5B99) else Color(0x2F1DAA85)).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(text, color = if (locked) MockBlueBright else MockGreen, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier, tint: Color = MockBlueBright) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = tint, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = MockMuted, fontSize = 8.sp)
    }
}

@Composable
private fun SearchBar(text: String = "Search drafts…") {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(Color(0x53132640))
            .border(1.dp, GlassLine, RoundedCornerShape(15.dp))
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("⌕", color = MockMuted, fontSize = 18.sp)
        Spacer(Modifier.width(8.dp))
        Text(text, color = MockMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text("☷", color = MockMuted, fontSize = 15.sp)
    }
}

@Composable
private fun Chip(text: String, selected: Boolean) {
    Box(Modifier.clip(RoundedCornerShape(14.dp)).background(if (selected) Brush.horizontalGradient(listOf(MockBlue, MockPurple)) else Color(0x38203A5D)).padding(horizontal = 18.dp, vertical = 7.dp)) {
        Text(text, color = if (selected) Color.White else MockMuted, fontSize = 9.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun Dots(active: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        repeat(4) { i ->
            Box(Modifier.size(if (i == active) 7.dp else 5.dp).clip(CircleShape).background(if (i == active) MockBlueBright else MockMuted.copy(alpha = 0.35f)))
        }
    }
}

@Composable
private fun DividerLabel(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(GlassLine))
        Text("  $text  ", color = MockMuted, fontSize = 10.sp)
        Box(Modifier.weight(1f).height(1.dp).background(GlassLine))
    }
}

@Composable
private fun MockButton(
    text: String,
    background: Color,
    foreground: Color,
    icon: Int?,
    onClick: () -> Unit,
    outlined: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    Row(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(background)
            .border(if (outlined) 1.dp else 0.dp, GlassLine, RoundedCornerShape(24.dp))
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(painterResource(icon), null, tint = foreground, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = foreground, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AvatarCircle() {
    Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0x4E234470)).border(1.dp, GlassLine, CircleShape), contentAlignment = Alignment.Center) {
        Text("●", color = MockInk, fontSize = 15.sp)
    }
}

@Composable
private fun FloatingPlus() {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Box(Modifier.size(50.dp).clip(CircleShape).background(Brush.linearGradient(listOf(MockBlueBright, MockPurple))).shadow(12.dp, CircleShape), contentAlignment = Alignment.Center) {
            Text("+", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Light)
        }
    }
}

@Composable
private fun BlueSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = MockBlue,
            uncheckedThumbColor = MockMuted,
            uncheckedTrackColor = Color(0x55344C70)
        )
    )
}

@Composable
private fun Seg(text: String, selected: Boolean) {
    Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (selected) MockBlue else Color.Transparent).padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (selected) Color.White else MockMuted, fontSize = 9.sp)
    }
}

@Composable
private fun Selector(text: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x38193657)).padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = MockInk, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text("⌄", color = MockMuted)
    }
}

@Composable
private fun AppToggle(name: String, icon: Int, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x3C1A2E4A)), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, tint = MockInk, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(name, color = MockInk, fontSize = 11.sp, modifier = Modifier.weight(1f))
        BlueSwitch(checked, onCheckedChange)
    }
}

@Composable
private fun SmallActionCard(title: String, subtitle: String, icon: Int) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Glass).border(1.dp, GlassLine.copy(alpha = 0.55f), RoundedCornerShape(16.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x3B244975)), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, tint = MockBlueBright, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = MockInk, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MockMuted, fontSize = 9.sp)
        }
        Text("›", color = MockMuted, fontSize = 20.sp)
    }
}

@Composable
private fun LogoLock(size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size.value * 0.24f))).background(Brush.linearGradient(listOf(MockBlueBright, MockPurple))), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_lock_closed), null, tint = Color.White, modifier = Modifier.size(size * 0.58f))
    }
}
