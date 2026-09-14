package com.draftlock.app

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.draftlock.app.ui.theme.DraftLockTheme

private val BgTop = Color(0xFF081A35)
private val Bg = Color(0xFF030A18)
private val BgBottom = Color(0xFF01040C)
private val Ink = Color(0xFFF2F7FF)
private val Muted = Color(0xFF91A7C7)
private val Blue = Color(0xFF2F8CFF)
private val Cyan = Color(0xFF16C2FF)
private val Purple = Color(0xFF6A58FF)
private val Green = Color(0xFF43E1B0)
private val Glass = Color(0x66162D4D)
private val GlassSoft = Color(0x421B3B62)
private val GlassLine = Color(0x70568DD2)

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
    val context = LocalContext.current
    val activity = context as? Activity
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
                    onGoogle = {
                        activity?.let { vm.startGoogleAuth(it) }
                        status = "Opening Google sign-in…"
                    },
                    onGithub = { onboarding = false }
                )
                editor -> EditorMock(onBack = { editor = false }, onSaved = { status = "Saved" })
                else -> AppShell(
                    vm = vm,
                    screen = screen,
                    status = status,
                    onScreen = { screen = it },
                    onOpenEditor = { editor = true },
                    onGoogle = {
                        activity?.let { vm.startGoogleAuth(it) }
                        status = "Opening Google sign-in…"
                    },
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
            Modifier.size(560.dp).padding(0.dp).background(
                Brush.radialGradient(listOf(Blue.copy(alpha = .25f), Color.Transparent)), CircleShape
            ).align(Alignment.TopStart)
        )
        Box(
            Modifier.size(520.dp).background(
                Brush.radialGradient(listOf(Purple.copy(alpha = .20f), Color.Transparent)), CircleShape
            ).align(Alignment.TopEnd)
        )
        Box(
            Modifier.fillMaxWidth().height(300.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Blue.copy(alpha = .10f))))
        )
        content()
    }
}

@Composable
private fun Onboarding(onEmail: () -> Unit, onGoogle: () -> Unit, onGithub: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Logo(82.dp)
        Spacer(Modifier.height(18.dp))
        Brand(34.sp)
        Spacer(Modifier.height(18.dp))
        Text("Write freely.", color = Muted, fontSize = 18.sp)
        Text("Keep it yours.", color = Muted, fontSize = 18.sp)
        Spacer(Modifier.height(42.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(Cyan))
            Box(Modifier.size(7.dp).clip(CircleShape).background(GlassLine))
            Box(Modifier.size(7.dp).clip(CircleShape).background(GlassLine))
        }
        Spacer(Modifier.height(18.dp))
        PillButton("Continue with Email", Blue, Color.White, R.drawable.ic_write, onEmail)
        Spacer(Modifier.height(10.dp))
        PillButton("Continue with Google", GlassSoft, Ink, R.drawable.ic_google, onGoogle, true)
        Spacer(Modifier.height(10.dp))
        PillButton("Continue with GitHub", GlassSoft, Ink, R.drawable.ic_key, onGithub, true)
        Spacer(Modifier.height(14.dp))
        Text("Already have an account?  Log in", color = Muted, fontSize = 11.sp)
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
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Logo(34.dp)
                Spacer(Modifier.width(9.dp))
                Brand(20.sp)
            }
            GlassIcon("•")
        }
        Spacer(Modifier.height(20.dp))
        Text("Good evening,", color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("Your drafts are safe. Keep going.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        GlassCard {
            Row(Modifier.fillMaxWidth()) {
                Stat("12", "Total Drafts", Modifier.weight(1f), Cyan)
                Stat("4", "Locked", Modifier.weight(1f), Purple)
                Stat("8", "Unlocked", Modifier.weight(1f), Green)
            }
        }
        Spacer(Modifier.height(12.dp))
        SearchBar("Search your drafts…")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FilterChip("All", true)
            FilterChip("Locked", false)
            FilterChip("Unlocked", false)
        }
        Spacer(Modifier.height(11.dp))
        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            contentPadding = PaddingValues(bottom = 10.dp)
        ) {
            item { DraftRow("The Next Chapter", "The city was quieter than usual…", "Updated 2h ago • 1,428 words", true, onOpenEditor) }
            item { DraftRow("Project Aurora", "It started with a single message…", "Updated 5h ago • 892 words", false, onOpenEditor) }
            item { DraftRow("Untitled Draft", "Ideas… maybe something here…", "Updated 1d ago • $words words", false, onOpenEditor) }
            item { DraftRow("The Last Light", "Some doors are not meant to be…", "Updated 2d ago • 1,203 words", true, onOpenEditor) }
        }
        Box(
            Modifier.size(54.dp).align(Alignment.End).shadow(14.dp, CircleShape)
                .clip(CircleShape).background(Brush.linearGradient(listOf(Cyan, Purple))),
            contentAlignment = Alignment.Center
        ) { Text("+", color = Color.White, fontSize = 28.sp) }
    }
}

@Composable
private fun LibraryMock(onOpenEditor: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
        TopTitle("Library")
        Spacer(Modifier.height(14.dp))
        SearchBar("Search drafts…")
        Spacer(Modifier.height(12.dp))
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
    var youtube by remember { mutableStateOf(true) }
    var instagram by remember { mutableStateOf(true) }
    var tiktok by remember { mutableStateOf(true) }
    var discord by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
        TopTitle("Focus & Locked Apps")
        Spacer(Modifier.height(14.dp))
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_usage), null, tint = Cyan, modifier = Modifier.size(29.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Focus Mode", color = Ink, fontWeight = FontWeight.Bold)
                    Text("Block distractions. Stay in the zone.", color = Muted, fontSize = 10.sp)
                }
                BlueSwitch(focus) { focus = it }
            }
            Spacer(Modifier.height(11.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x451B4E80)).padding(4.dp)) {
                Segment("Pomodoro", true)
                Segment("Custom", false)
                Segment("None", false)
            }
            Spacer(Modifier.height(9.dp))
            Selector("25:00")
            Spacer(Modifier.height(9.dp))
            PillButton("Start Focus", Blue, Color.White, R.drawable.ic_write) { onStatus("Focus started") }
        }
        Spacer(Modifier.height(11.dp))
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_lock_closed), null, tint = Cyan, modifier = Modifier.size(27.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Locked Apps", color = Ink, fontWeight = FontWeight.Bold)
                    Text("These apps will be locked while Focus is active.", color = Muted, fontSize = 9.sp)
                }
            }
            AppToggle("YouTube", youtube) { youtube = it }
            AppToggle("Instagram", instagram) { instagram = it }
            AppToggle("TikTok", tiktok) { tiktok = it }
            AppToggle("Discord", discord) { discord = it }
        }
        Spacer(Modifier.height(9.dp))
        ActionRow("App Protection", "Require authentication to open locked apps.", R.drawable.ic_shield)
        Spacer(Modifier.height(8.dp))
        ActionRow("Emergency Override", "Allow access for 15 minutes.", R.drawable.ic_rules)
    }
}

@Composable
private fun SettingsMock(vm: DraftLockViewModel, status: String, onGoogle: () -> Unit, onStatus: (String) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
        TopTitle("Settings")
        Spacer(Modifier.height(14.dp))
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Logo(40.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("DraftLock", color = Ink, fontWeight = FontWeight.Bold)
                    Text("Write freely. Keep it yours.", color = Muted, fontSize = 10.sp)
                }
                Text("v1.0", color = Muted, fontSize = 9.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        GlassCard {
            Text("Google account", color = Ink, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text(status.ifBlank { if (vm.isGoogleConnected) "Connected" else "Not connected" }, color = Muted, fontSize = 10.sp)
            Spacer(Modifier.height(9.dp))
            PillButton(
                if (vm.isGoogleConnected) "Google connected" else "Connect Google",
                Blue, Color.White, R.drawable.ic_google,
                if (vm.isGoogleConnected) ({ onStatus("Already connected") }) else onGoogle
            )
        }
        Spacer(Modifier.height(10.dp))
        ActionRow("Daily goal", "${vm.todayWords.value} words written today", R.drawable.ic_write)
        Spacer(Modifier.height(8.dp))
        ActionRow("Privacy", "Your vault stays local unless you sync it.", R.drawable.ic_shield)
    }
}

@Composable
private fun EditorMock(onBack: () -> Unit, onSaved: () -> Unit) {
    var title by remember { mutableStateOf(TextFieldValue("The Next Chapter")) }
    var body by remember { mutableStateOf(TextFieldValue("The city was quieter than usual, the kind of silence that felt deliberate rather than peaceful.\n\nHe stood at the edge of the rooftop, watching the lights flicker on one by one.\n\nEvery window was a story. Every shadow felt like a warning.")) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = Ink, fontSize = 34.sp, modifier = Modifier.clickable { onBack() })
            Spacer(Modifier.width(8.dp))
            Text("Editor", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text("Saved", color = Green, fontSize = 11.sp, modifier = Modifier.clickable { onSaved() })
        }
        Spacer(Modifier.height(14.dp))
        GlassCard {
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                textStyle = MaterialTheme.typography.headlineSmall.copy(color = Ink, fontWeight = FontWeight.Bold),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(13.dp))
            BasicTextField(
                value = body,
                onValueChange = { body = it },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink, lineHeight = 24.sp),
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                cursorBrush = SolidColor(Cyan)
            )
        }
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tag("1,428 words")
            Tag("Unlocked")
            Spacer(Modifier.weight(1f))
            Text("•••", color = Muted, fontSize = 18.sp)
        }
    }
}

@Composable
private fun BottomNav(selected: AppScreen, onSelect: (AppScreen) -> Unit) {
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp)).background(Color(0x9A0B1B31))
            .border(1.dp, GlassLine, RoundedCornerShape(24.dp)).padding(6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        AppScreen.entries.forEach { item ->
            val active = item == selected
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(17.dp))
                    .background(if (active) Color(0x4D2474BC) else Color.Transparent)
                    .clickable { onSelect(item) }.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(painterResource(item.icon), item.label, tint = if (active) Cyan else Muted, modifier = Modifier.size(20.dp))
                Text(item.label, color = if (active) Ink else Muted, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun GlassCard(content: @Composable Column.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Glass)
            .border(1.dp, GlassLine, RoundedCornerShape(22.dp)).padding(14.dp),
        content = content
    )
}

@Composable
private fun Logo(size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * .28f))
            .background(Brush.linearGradient(listOf(Cyan, Blue, Purple)))
            .border(1.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(size * .28f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(R.drawable.ic_lock_closed), null, tint = Color.White, modifier = Modifier.size(size * .52f))
    }
}

@Composable
private fun Brand(size: androidx.compose.ui.unit.TextUnit) {
    Text("draftlock", color = Ink, fontSize = size, fontWeight = FontWeight.Bold, letterSpacing = (-.7).sp)
}

@Composable
private fun TopTitle(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Logo(31.dp)
        Spacer(Modifier.width(9.dp))
        Text(title, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GlassIcon(text: String) {
    Box(Modifier.size(36.dp).clip(CircleShape).background(GlassSoft).border(1.dp, GlassLine, CircleShape), contentAlignment = Alignment.Center) {
        Text(text, color = Ink, fontSize = 15.sp)
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier, tint: Color) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = tint, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 9.sp)
    }
}

@Composable
private fun SearchBar(placeholder: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(GlassSoft).border(1.dp, GlassLine, RoundedCornerShape(17.dp)).padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("⌕", color = Cyan, fontSize = 19.sp)
        Spacer(Modifier.width(8.dp))
        Text(placeholder, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean) {
    Box(Modifier.clip(RoundedCornerShape(14.dp)).background(if (selected) Color(0x552A7BC8) else GlassSoft).border(1.dp, if (selected) Cyan.copy(alpha = .45f) else GlassLine, RoundedCornerShape(14.dp)).padding(horizontal = 13.dp, vertical = 7.dp)) {
        Text(text, color = if (selected) Ink else Muted, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun DraftRow(title: String, preview: String, meta: String, locked: Boolean, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp)).background(GlassSoft).border(1.dp, GlassLine, RoundedCornerShape(19.dp)).clickable { onOpen() }.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(39.dp).clip(RoundedCornerShape(12.dp)).background(if (locked) Color(0x3D6558FF) else Color(0x3030A7E8)), contentAlignment = Alignment.Center) {
            Icon(painterResource(if (locked) R.drawable.ic_lock_closed else R.drawable.ic_docs), null, tint = if (locked) Purple else Cyan, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(preview, color = Muted, fontSize = 10.sp, maxLines = 1)
            Text(meta, color = Muted.copy(alpha = .72f), fontSize = 8.sp)
        }
        Text(if (locked) "▣" else "›", color = if (locked) Purple else Muted, fontSize = 15.sp)
    }
}

@Composable
private fun PillButton(text: String, bg: Color, fg: Color, icon: Int, onClick: () -> Unit, outlined: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().height(49.dp).clip(RoundedCornerShape(17.dp)).background(bg)
            .border(if (outlined) 1.dp else 0.dp, if (outlined) GlassLine else Color.Transparent, RoundedCornerShape(17.dp))
            .clickable { onClick() }.padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(painterResource(icon), null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(9.dp))
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BlueSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Blue, uncheckedThumbColor = Muted, uncheckedTrackColor = GlassSoft))
}

@Composable
private fun Segment(text: String, selected: Boolean) {
    Box(Modifier.weight(1f).clip(RoundedCornerShape(13.dp)).background(if (selected) Color(0x663B91E8) else Color.Transparent).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (selected) Ink else Muted, fontSize = 9.sp)
    }
}

@Composable
private fun Selector(text: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color(0x3A0E2948)).border(1.dp, GlassLine, RoundedCornerShape(15.dp)).padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Duration", color = Muted, fontSize = 10.sp)
        Spacer(Modifier.weight(1f))
        Text(text, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AppToggle(name: String, enabled: Boolean, onChanged: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(Color(0x303A91D5)), contentAlignment = Alignment.Center) { Text(name.take(1), color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(9.dp))
        Text(name, color = Ink, fontSize = 11.sp, modifier = Modifier.weight(1f))
        BlueSwitch(enabled, onChanged)
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, icon: Int) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(GlassSoft).border(1.dp, GlassLine, RoundedCornerShape(18.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, tint = Cyan, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 9.sp)
        }
        Text("›", color = Muted, fontSize = 18.sp)
    }
}

@Composable
private fun Tag(text: String) {
    Box(Modifier.clip(RoundedCornerShape(11.dp)).background(GlassSoft).padding(horizontal = 9.dp, vertical = 6.dp)) {
        Text(text, color = Muted, fontSize = 9.sp)
    }
}
