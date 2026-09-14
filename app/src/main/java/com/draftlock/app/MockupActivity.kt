package com.draftlock.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState

private val BgTop = Color(0xFF08162C)
private val Bg = Color(0xFF020815)
private val BgBottom = Color(0xFF01040C)
private val Ink = Color(0xFFF1F6FF)
private val Muted = Color(0xFF94A8C7)
private val Blue = Color(0xFF2F8CFF)
private val Cyan = Color(0xFF16B8FF)
private val Purple = Color(0xFF6658FF)
private val Green = Color(0xFF44E2B2)
private val Glass = Color(0x7A10233D)
private val GlassSoft = Color(0x42162D4D)
private val GlassLine = Color(0x6B5287CE)

private enum class AppScreen(val label: String, val icon: Int) {
    HOME("Home", R.drawable.ic_home),
    LIBRARY("Library", R.drawable.ic_docs),
    FOCUS("Focus", R.drawable.ic_usage),
    SETTINGS("Settings", R.drawable.ic_analytics)
}

class MockupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DraftLockMockup() }
    }
}

@Composable
private fun DraftLockMockup() {
    val vm: DraftLockViewModel = viewModel()
    var onboarding by remember { mutableStateOf(true) }
    var screen by remember { mutableStateOf(AppScreen.HOME) }
    var editor by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        vm.checkGoogleConnection()
        if (vm.isGoogleConnected) onboarding = false
        status = vm.syncStatus
    }

    DraftLockTheme {
        MockBackground {
            when {
                onboarding -> Onboarding(
                    onEmail = { onboarding = false },
                    onGoogle = { vm.startGoogleAuth(this@MockupActivity); status = "Opening Google sign-in…" },
                    onGithub = { onboarding = false }
                )
                editor -> EditorMock(vm, onBack = { editor = false }, onSaved = { status = "Saved" })
                else -> AppShell(
                    vm = vm,
                    screen = screen,
                    status = status,
                    onScreen = { screen = it },
                    onOpenEditor = { editor = true },
                    onGoogle = { vm.startGoogleAuth(this@MockupActivity); status = "Opening Google sign-in…" },
                    onStatus = { status = it }
                )
            }
        }
    }
}

@Composable
private fun MockBackground(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(BgTop, Bg, BgBottom)))) {
        Box(
            Modifier.size(540.dp).offset((-220).dp, (-190).dp)
                .background(Brush.radialGradient(listOf(Blue.copy(alpha = .22f), Color.Transparent)), CircleShape)
        )
        Box(
            Modifier.size(610.dp).align(Alignment.TopEnd).offset(250.dp, (-270).dp)
                .background(Brush.radialGradient(listOf(Purple.copy(alpha = .14f), Color.Transparent)), CircleShape)
        )
        Box(
            Modifier.fillMaxWidth().height(260.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Blue.copy(alpha = .09f))))
        )
        content()
    }
}

@Composable
private fun Onboarding(onEmail: () -> Unit, onGoogle: () -> Unit, onGithub: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Logo(82.dp)
        Spacer(Modifier.height(20.dp))
        Brand(34.sp)
        Spacer(Modifier.height(20.dp))
        Text("Write freely.", color = Muted, fontSize = 18.sp)
        Text("Keep it yours.", color = Muted, fontSize = 18.sp)
        Spacer(Modifier.height(50.dp))
        Dots()
        Spacer(Modifier.height(18.dp))
        PillButton("Continue with Email", Blue, Color.White, R.drawable.ic_write, onEmail)
        Spacer(Modifier.height(10.dp))
        PillButton("Continue with Google", GlassSoft, Ink, R.drawable.ic_google, onGoogle, outlined = true)
        Spacer(Modifier.height(14.dp))
        DividerText("or")
        Spacer(Modifier.height(14.dp))
        PillButton("Continue with GitHub", GlassSoft, Ink, R.drawable.ic_key, onGithub, outlined = true)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Already have an account?", color = Muted, fontSize = 11.sp)
            Text("Log in", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.weight(.9f))
    }
}

@Composable
private fun AppShell(
    vm: DraftLockViewModel,
    screen: AppScreen,
    status: String,
    onScreen: (AppScreen) -> Unit,
    onOpenEditor: () -> Unit,
    onGoogle: () -> Unit,
    onStatus: (String) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (screen) {
                AppScreen.HOME -> HomeMock(vm, onOpenEditor)
                AppScreen.LIBRARY -> LibraryMock(onOpenEditor)
                AppScreen.FOCUS -> FocusMock(onStatus)
                AppScreen.SETTINGS -> SettingsMock(vm, status, onGoogle, onStatus)
            }
        }
        BottomNav(screen, onScreen)
    }
}

@Composable
private fun HomeMock(vm: DraftLockViewModel, onOpenEditor: () -> Unit) {
    val words by vm.todayWords.collectAsState(initial = 0)
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Logo(32.dp)
                Spacer(Modifier.width(8.dp))
                Brand(19.sp)
            }
            Box(Modifier.size(35.dp).clip(CircleShape).background(GlassSoft).border(1.dp, GlassLine, CircleShape), contentAlignment = Alignment.Center) {
                Text("●", color = Ink, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(17.dp))
        Text("Good evening,", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text("Your drafts are safe. Keep going.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(15.dp))
        GlassCard {
            Row(Modifier.fillMaxWidth()) {
                Stat("12", "Total Drafts", Modifier.weight(1f), Cyan)
                Stat("4", "Locked", Modifier.weight(1f), Purple)
                Stat("8", "Unlocked", Modifier.weight(1f), Green)
            }
        }
        Spacer(Modifier.height(11.dp))
        SearchBar()
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FilterChip("All", true)
            FilterChip("Locked", false)
            FilterChip("Unlocked", false)
        }
        Spacer(Modifier.height(11.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
            item { DraftRow("The Next Chapter", "The city was quieter than usual…", "Updated 2h ago • 1,428 words", true, onOpenEditor) }
            item { DraftRow("Project Aurora", "It started with a single message…", "Updated 5h ago • 892 words", false, onOpenEditor) }
            item { DraftRow("Untitled Draft", "Ideas… maybe something here…", "Updated 1d ago • 204 words", false, onOpenEditor) }
            item { DraftRow("The Last Light", "Some doors are not meant to be…", "Updated 2d ago • 1,203 words", true, onOpenEditor) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(Modifier.size(52.dp).shadow(12.dp, CircleShape).clip(CircleShape).background(Brush.linearGradient(listOf(Cyan, Purple))), contentAlignment = Alignment.Center) {
                Text("+", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Light)
            }
        }
    }
}

@Composable
private fun LibraryMock(onOpenEditor: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp)) {
        TopTitle("Library")
        Spacer(Modifier.height(14.dp))
        SearchBar("Search drafts…")
        Spacer(Modifier.height(11.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            item { DraftRow("The Next Chapter", "The city was quieter than usual…", "1,428 words", true, onOpenEditor) }
            item { DraftRow("Project Aurora", "It started with a single message…", "892 words", false, onOpenEditor) }
            item { DraftRow("Untitled Draft", "Ideas… maybe something here…", "204 words", false, onOpenEditor) }
            item { DraftRow("The Last Light", "Some doors are not meant to be…", "1,203 words", true, onOpenEditor) }
        }
    }
}

@Composable
private fun FocusMock(onStatus: (String) -> Unit) {
    var focus by remember { mutableStateOf(true) }
    var yt by remember { mutableStateOf(true) }
    var instagram by remember { mutableStateOf(true) }
    var tiktok by remember { mutableStateOf(true) }
    var discord by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp)) {
        TopTitle("Focus & Locked Apps")
        Spacer(Modifier.height(14.dp))
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_usage), null, tint = Cyan, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Focus Mode", color = Ink, fontWeight = FontWeight.SemiBold)
                    Text("Block distractions. Stay in the zone.", color = Muted, fontSize = 10.sp)
                }
                BlueSwitch(focus) { focus = it }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x47153A70)).padding(4.dp)) {
                Segment("Pomodoro", true)
                Segment("Custom", false)
                Segment("None", false)
            }
            Spacer(Modifier.height(9.dp))
            Selector("25:00")
            Spacer(Modifier.height(9.dp))
            PillButton("Start Focus", Blue, Color.White, R.drawable.ic_write, { onStatus("Focus started") })
        }
        Spacer(Modifier.height(11.dp))
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_lock_closed), null, tint = Cyan, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Locked Apps", color = Ink, fontWeight = FontWeight.SemiBold)
                    Text("These apps will be locked while Focus is active.", color = Muted, fontSize = 9.sp)
                }
            }
            AppToggle("YouTube", R.drawable.ic_google, yt) { yt = it }
            AppToggle("Instagram", R.drawable.ic_write, instagram) { instagram = it }
            AppToggle("TikTok", R.drawable.ic_usage, tiktok) { tiktok = it }
            AppToggle("Discord", R.drawable.ic_docs, discord) { discord = it }
        }
        Spacer(Modifier.height(10.dp))
        ActionRow("App Protection", "Require authentication to open locked apps.", R.drawable.ic_shield)
        Spacer(Modifier.height(8.dp))
        ActionRow("Emergency Override", "Allow access for 15 minutes.", R.drawable.ic_rules)
    }
}

@Composable
private fun SettingsMock(vm: DraftLockViewModel, status: String, onGoogle: () -> Unit, onStatus: (String) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp)) {
        TopTitle("Settings")
        Spacer(Modifier.height(14.dp))
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Logo(38.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("DraftLock", color = Ink, fontWeight = FontWeight.SemiBold)
                    Text("Write freely. Keep it yours.", color = Muted, fontSize = 10.sp)
                }
                Text("v1.0", color = Muted, fontSize = 9.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        GlassCard {
            Text("Google account", color = Ink, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(status.ifBlank { if (vm.isGoogleConnected) "Connected" else "Not connected" }, color = Muted, fontSize = 10.sp)
            Spacer(Modifier.height(9.dp))
            PillButton(if (vm.isGoogleConnected) "Google connected" else "Connect Google", Blue, Color.White, R.drawable.ic_google, if (vm.isGoogleConnected) ({ onStatus("Already connected") }) else onGoogle)
        }
        Spacer(Modifier.height(10.dp))
        ActionRow("Daily goal", "${vm.todayWords.value} words written today", R.drawable.ic_write)
        Spacer(Modifier.height(8.dp))
        ActionRow("Privacy", "Your vault stays local unless you sync it.", R.drawable.ic_shield)
    }
}

@Composable
private fun EditorMock(vm: DraftLockViewModel, onBack: () -> Unit, onSaved: () -> Unit) {
    var draft by remember {
        mutableStateOf(
            "The city was quieter than usual, the kind of silence that felt deliberate rather than peaceful. He stood at the edge of the rooftop, watching the lights flicker on one by one, each window a reminder of lives he'd never really be a part of.\n\nIt wasn’t that he didn’t want to belong. It was just easier to keep moving. The past had a way of finding people, and he’d learned long ago that the best way to survive was to stay ahead of it."
        )
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = Ink, fontSize = 28.sp, modifier = Modifier.clickable { onBack() })
            Spacer(Modifier.width(8.dp))
            Text("The Next Chapter", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("•••", color = Muted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge("Locked")
            Spacer(Modifier.weight(1f))
            Text("1,428 words", color = Muted, fontSize = 10.sp)
        }
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.weight(1f)) {
            Text("Chapter 1", color = Ink, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(11.dp))
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                textStyle = TextStyle(color = Ink, fontSize = 14.sp, lineHeight = 22.sp),
                cursorBrush = SolidColor(Cyan)
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x441A3457)).padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(19.dp)) {
                Text("B", color = Ink, fontWeight = FontWeight.Bold)
                Text("I", color = Ink, fontStyle = FontStyle.Italic)
                Text("U", color = Ink)
                Text("☷", color = Ink)
                Text("⌕", color = Ink)
            }
        }
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("● Auto-save", color = Green, fontSize = 9.sp, modifier = Modifier.weight(1f))
            PillButton("Save", Blue, Color.White, R.drawable.ic_lock_closed, onSaved, modifier = Modifier.width(136.dp))
        }
    }
}

@Composable
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Glass).border(1.dp, GlassLine, RoundedCornerShape(18.dp)).padding(13.dp),
        content = content
    )
}

@Composable
private fun DraftRow(title: String, detail: String, meta: String, locked: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(GlassSoft).border(1.dp, GlassLine.copy(alpha = .55f), RoundedCornerShape(15.dp)).clickable { onClick() }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(35.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x47203F69)), contentAlignment = Alignment.Center) {
            Icon(painterResource(if (locked) R.drawable.ic_lock_closed else R.drawable.ic_docs), null, tint = Cyan, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Badge(if (locked) "Locked" else "Unlocked", locked)
            }
            Text(detail, color = Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, color = Muted, fontSize = 8.sp)
        }
        Text("›", color = Muted, fontSize = 21.sp)
    }
}

@Composable
private fun Badge(text: String, locked: Boolean = true) {
    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if (locked) Color(0x352A62A4) else Color(0x2E1AA27D)).padding(horizontal = 7.dp, vertical = 3.dp)) {
        Text(text, color = if (locked) Cyan else Green, fontSize = 7.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TopTitle(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("‹", color = Muted, fontSize = 25.sp)
        Spacer(Modifier.width(7.dp))
        Text(title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text("•••", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun SearchBar(text: String = "Search drafts…") {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color(0x4B132740)).border(1.dp, GlassLine, RoundedCornerShape(15.dp)).padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("⌕", color = Muted, fontSize = 17.sp)
        Spacer(Modifier.width(7.dp))
        Text(text, color = Muted, fontSize = 10.sp, modifier = Modifier.weight(1f))
        Text("☷", color = Muted, fontSize = 14.sp)
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean) {
    Box(Modifier.clip(RoundedCornerShape(14.dp)).background(if (selected) Brush.horizontalGradient(listOf(Blue, Purple)) else Color(0x38213D61)).padding(horizontal = 17.dp, vertical = 7.dp)) {
        Text(text, color = if (selected) Color.White else Muted, fontSize = 8.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier, color: Color) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = Muted, fontSize = 8.sp)
    }
}

@Composable
private fun PillButton(text: String, bg: Color, fg: Color, icon: Int?, onClick: () -> Unit, outlined: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth()) {
    Row(modifier.height(46.dp).clip(RoundedCornerShape(24.dp)).background(bg).border(if (outlined) 1.dp else 0.dp, GlassLine, RoundedCornerShape(24.dp)).clickable { onClick() }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (icon != null) {
            Icon(painterResource(icon), null, tint = fg, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, color = fg, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DividerText(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(GlassLine))
        Text("  $text  ", color = Muted, fontSize = 9.sp)
        Box(Modifier.weight(1f).height(1.dp).background(GlassLine))
    }
}

@Composable
private fun Dots() {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(4) { i -> Box(Modifier.size(if (i == 0) 7.dp else 5.dp).clip(CircleShape).background(if (i == 0) Cyan else Muted.copy(alpha = .35f))) }
    }
}

@Composable
private fun Brand(size: androidx.compose.ui.unit.TextUnit) {
    Row {
        Text("draft", color = Ink, fontSize = size, fontWeight = FontWeight.SemiBold, letterSpacing = (-.8).sp)
        Text("lock", color = Cyan, fontSize = size, fontWeight = FontWeight.SemiBold, letterSpacing = (-.8).sp)
    }
}

@Composable
private fun Logo(size: Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * .24f)).background(Brush.linearGradient(listOf(Cyan, Purple))), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_lock_closed), null, tint = Color.White, modifier = Modifier.size(size * .56f))
    }
}

@Composable
private fun BottomNav(selected: AppScreen, onSelected: (AppScreen) -> Unit) {
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 7.dp).shadow(10.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(Color(0xB3061326)).border(1.dp, GlassLine, RoundedCornerShape(24.dp)).padding(horizontal = 7.dp, vertical = 5.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf(AppScreen.HOME, AppScreen.LIBRARY, AppScreen.FOCUS, AppScreen.SETTINGS).forEach { item ->
                Column(Modifier.weight(1f).clickable { onSelected(item) }.padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Icon(painterResource(item.icon), null, tint = if (item == selected) Cyan else Muted, modifier = Modifier.size(17.dp))
                    Text(item.label, color = if (item == selected) Ink else Muted, fontSize = 7.sp)
                }
            }
        }
    }
}

@Composable
private fun BlueSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Blue, uncheckedThumbColor = Muted, uncheckedTrackColor = Color(0x57304768)))
}

@Composable
private fun Segment(text: String, selected: Boolean) {
    Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (selected) Blue else Color.Transparent).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (selected) Color.White else Muted, fontSize = 8.sp)
    }
}

@Composable
private fun Selector(text: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x3A193554)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = Ink, fontSize = 10.sp, modifier = Modifier.weight(1f))
        Text("⌄", color = Muted, fontSize = 14.sp)
    }
}

@Composable
private fun AppToggle(name: String, icon: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x3C1B3252)), contentAlignment = Alignment.Center) { Icon(painterResource(icon), null, tint = Ink, modifier = Modifier.size(16.dp)) }
        Spacer(Modifier.width(9.dp))
        Text(name, color = Ink, fontSize = 10.sp, modifier = Modifier.weight(1f))
        BlueSwitch(checked, onChange)
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, icon: Int) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GlassSoft).border(1.dp, GlassLine.copy(alpha = .55f), RoundedCornerShape(16.dp)).padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(31.dp).clip(RoundedCornerShape(9.dp)).background(Color(0x3D244A7A)), contentAlignment = Alignment.Center) { Icon(painterResource(icon), null, tint = Cyan, modifier = Modifier.size(17.dp)) }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("›", color = Muted, fontSize = 19.sp)
    }
}
