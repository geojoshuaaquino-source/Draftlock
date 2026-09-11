package com.draftlock.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

private val Ink = Color(0xFFF4F4F0)
private val Muted = Color(0xFF8B8B86)
private val Panel = Color(0xFF151515)
private val Line = Color(0xFF30302D)
private val Accent = Color(0xFFB7FF4A)
private val Bg = Color(0xFF080808)

private enum class Page { SPLASH, HOME, WRITE, RULES, DOCS, FILTER, EDIT, BUILDER, APP, CONTROL, ACCESS, USAGE, SYNC, HISTORY, ANALYTICS, PERMISSIONS, ACCOUNT, SETUP, QUICK_ACCESS, SETTINGS }

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

@Composable
private fun DraftLockPrototype() {
    var page by remember { mutableStateOf(Page.SPLASH) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptics = LocalHapticFeedback.current
    fun navigate(to: Page) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        page = to
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.TopCenter) {
        val scale = min(maxWidth.value / 390f, maxHeight.value / 844f)
        Box(Modifier.width(390.dp).height(844.dp).graphicsLayer(scaleX = scale, scaleY = scale)) {
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    (slideInHorizontally { it / 4 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally { -it / 4 } + fadeOut(tween(200)))
                },
                label = "pageTransition"
            ) { target ->
                Box(Modifier.fillMaxSize()) {
                    when (target) {
                        Page.SPLASH -> Splash { navigate(Page.HOME) }
                        Page.HOME -> Home { navigate(Page.WRITE) }
                        Page.WRITE -> Write()
                        Page.RULES -> Rules({ navigate(Page.APP) }, { navigate(Page.BUILDER) }, { navigate(Page.CONTROL) })
                        Page.DOCS -> Docs(context, { navigate(Page.FILTER) }, { navigate(Page.EDIT) }, { navigate(Page.SYNC) })
                        Page.FILTER -> Filter { navigate(Page.DOCS) }
                        Page.EDIT -> Edit()
                        Page.BUILDER -> Builder { navigate(Page.RULES) }
                        Page.APP -> AppRequirement { navigate(Page.RULES) }
                        Page.CONTROL -> Control { navigate(Page.ACCESS) }
                        Page.ACCESS -> Access { navigate(Page.WRITE) }
                        Page.USAGE -> Usage()
                        Page.SYNC -> Sync()
                        Page.HISTORY -> History { navigate(Page.ANALYTICS) }
                        Page.ANALYTICS -> Analytics()
                        Page.PERMISSIONS -> Permissions(context) { navigate(Page.ACCOUNT) }
                        Page.ACCOUNT -> Account(context) { navigate(Page.SETUP) }
                        Page.SETUP -> Setup()
                        Page.QUICK_ACCESS -> QuickAccess()
                        Page.SETTINGS -> Settings()
                    }
                }
            }
            if (page == Page.HOME || page == Page.WRITE || page == Page.RULES || page == Page.DOCS) {
                BottomNav { navigate(it) }
            }
        }
    }
}

@Composable private fun T(text:String,x:Float,y:Float,w:Float?=null,size:Float=13f,color:Color=Ink,bold:Boolean=false,align:TextAlign=TextAlign.Start) {
    val m = Modifier.offset(x.dp,y.dp).then(if (w != null) Modifier.width(w.dp) else Modifier)
    Text(text,m,color=color,fontSize=size.sp,fontWeight=if(bold) FontWeight.Bold else FontWeight.Normal,textAlign=align,lineHeight=(size*1.15f).sp)
}

@Composable private fun R(x:Float,y:Float,w:Float,h:Float,accent:Boolean=false,onClick:(()->Unit)?=null) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if(pressed) 0.97f else 1f, spring(dampingRatio = 0.7f, stiffness = 400f), label="press")
    val base = Modifier.offset(x.dp,y.dp).width(w.dp).height(h.dp).background(if(accent) Accent else Panel).scale(scale)
    Box(
        if(onClick != null) base.clickable(
            onClick = onClick,
        ) else base
    )
}

@Composable private fun B(text:String,x:Float,y:Float,w:Float,h:Float=48f,onClick:(()->Unit)?=null,accent:Boolean=false,align:TextAlign=TextAlign.Start,size:Float=13f) {
    R(x,y,w,h,accent,onClick)
    T(text,x+17f,y+16f,w-34f,size,if(accent)Color.Black else Ink,true,align)
}

@Composable private fun P(text:String,x:Float,y:Float,w:Float,h:Float=31f,accent:Boolean=false) {
    val infinite = rememberInfiniteTransition(label="pulse")
    val pulse by infinite.animateFloat(
        initialValue = 1f, targetValue = if(accent) 1.03f else 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOutCubic), RepeatMode.Reverse),
        label="pulseScale"
    )
    Box(Modifier.offset(x.dp,y.dp).width(w.dp).height(h.dp).graphicsLayer(scaleX = pulse, scaleY = pulse).background(if(accent) Accent else Panel), contentAlignment = Alignment.Center) {
        Text(text, color = if(accent) Color.Black else Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable private fun IconBox(x:Float,y:Float,label:String) {
    R(x,y,46f,46f)
    T(label,x,y+14f,46f,size=10f,color=Muted,bold=true,align=TextAlign.Center)
}

@Composable private fun Splash(onContinue:()->Unit) {
    T("SESSION 07",19f,23f,size=13f,color=Muted)
    T("WRITING ACCOUNTABILITY SYSTEM",86.13f,178f,217.73f,size=13f,color=Muted,bold=true)
    T("DRAFTLOCK",95.98f,205f,198.02f,size=32f,bold=true)
    T("Write your draft. Clear your requirements. Keep distractions locked until the work is done.",49f,250.89f,292f,size=15f,align=TextAlign.Center)
    T("SYNC",19f,345.67f,171f,size=12f,color=Muted,bold=true)
    T("GOOGLE DOCS",19f,369.67f,171f,size=18f,bold=true)
    T("CONTROL",200f,345.67f,171f,size=12f,color=Muted,bold=true)
    T("ACTIVE",200f,369.67f,171f,size=18f,bold=true)
    B("CONTINUE",19f,465f,352f,onClick=onContinue,align=TextAlign.Center)
}

@Composable private fun Home(onWrite:()->Unit) {
    T("SESSION 07",19f,23f,88.02f,size=13f,color=Muted)
    T("HOME",19f,36f,88.02f,size=26f,bold=true)
    P("ON TRACK",304.92f,29.94f,66.08f)
    T("TODAY’S WRITING",36f,96.89f,318f,size=13f,color=Muted,bold=true)
    T("742",36f,109.89f,318f,size=42f,bold=true)
    T("/ 1,000 WORDS",36f,151.48f,318f,size=13f,color=Muted,bold=true)
    R(36f,232.73f,318f,65f,onClick=onWrite)
    T("ACTIVE DRAFT",90f,232.73f,217.47f,size=18f,bold=true)
    T("DND Chapter 12",90f,260.73f,217.47f,size=13.5f,color=Muted)
    P("742",319.47f,237.98f,38.53f)
    IconBox(32f,314.73f,"L")
    T("Lichess",90f,314.73f,200.94f,size=18f,bold=true)
    T("18 / 30 MINUTES",90f,342.73f,200.94f,size=13f,color=Muted)
    P("LOCKED",302.94f,319.98f,55.06f)
    IconBox(32f,396.73f,"A")
    T("Acode",90f,396.73f,206.45f,size=18f,bold=true)
    T("30 / 30 MINUTES",90f,424.73f,206.45f,size=13f,color=Muted)
    P("CLEAR",308.45f,401.98f,49.55f,accent=true)
    B("CONTINUE WRITING",19f,465.48f,352f,onClick=onWrite)
}

@Composable private fun Write() {
    T("WRITE / ACTIVE DOCUMENT",19f,23f,238f,size=13f,color=Muted,bold=true)
    T("DND CHAPTER 12",19f,36f,238f,size=24f,bold=true)
    P("SAVED",321.45f,26.31f,49.55f)
    T("TOTAL 3,842 WORDS",19f,72.64f,120f,size=13f,color=Muted)
    T("TODAY +742",315.91f,72.64f,55.09f,size=13f,bold=true)
    var value by remember { mutableStateOf("The corridor narrowed as the lights began to flicker.\n\nEgo stopped, listening for the sound beneath the ventilation hum. Something had followed him down three floors, but the footsteps had vanished.\n\nThe door ahead had no handle. He raised his hand anyway.") }
    BasicTextField(value, { value = it }, Modifier.offset(36.dp,114.64.dp).width(318.dp).height(215.94.dp), textStyle=TextStyle(color=Ink,fontSize=15.sp,lineHeight=18.sp),cursorBrush=SolidColor(Accent))
    T("CURSOR ACTIVE • EDITS COUNT TOWARD TODAY",36f,355.58f,318f,size=13f,color=Muted)
    T("AUTOSAVED 8 SEC AGO",19f,548.64f,104.66f,size=13f,color=Muted)
    P("DOCS SYNC",299.42f,539.64f,71.58f,accent=true)
}

@Composable private fun Rules(onApp:()->Unit,onBuilder:()->Unit,onControl:()->Unit) {
    T("ACCOUNTABILITY",19f,23f,size=13f,color=Muted,bold=true)
    T("REQUIREMENTS",19f,36f,264.02f,size=25f,bold=true)
    P("2 / 3 ACTIVE",321.45f,29.94f,49.55f)
    T("WRITE 1,000 WORDS",36f,100.39f,size=15f,bold=true); P("742 / 1000",276.91f,96.89f,77.09f)
    T("Lichess",36f,199.39f,size=19f,bold=true); P("18 / 30 MIN",271.41f,195.89f,82.59f)
    T("Acode",36f,298.39f,size=19f,bold=true); P("30 / 30 MIN",271.41f,294.89f,82.59f,accent=true)
    T("APP REQUIREMENTS",36f,395.89f,size=12f,color=Muted,bold=true)
    T("CHOOSE WHICH APPS TO REQUIRE",36f,416.89f,318f,size=18f,bold=true)
    T("Tap a requirement to configure it.",36f,440.89f,318f,size=13f,color=Muted)
    R(36f,470f,318f,48f,onClick=onApp); T("APP REQUIREMENT",53f,486f,284f,size=13f,bold=true)
    R(36f,528f,318f,48f,onClick=onBuilder); T("RULE BUILDER",53f,544f,284f,size=13f,bold=true)
    R(36f,586f,318f,48f,onClick=onControl); T("ACCESS CONTROL",53f,602f,284f,size=13f,bold=true)
}

@Composable private fun Docs(context:Context,onFilter:()->Unit,onEdit:()->Unit,onSync:()->Unit) {
    T("GOOGLE DOCS",19f,23f,size=13f,color=Muted,bold=true); T("LIBRARY",19f,36f,size=26f,bold=true); P("SYNCED",315.94f,29.94f,55.06f)
    R(19f,79.89f,352f,48f); T("SEARCH DOCUMENTS",36f,96f,318f,size=13f,color=Muted)
    T("FILTER",19f,148.89f,size=13f,color=Accent,bold=true); P("NAME: DND*",293.91f,139.89f,77.09f)
    DocRow("DND CHAPTER 12","3,842 words • edited 2m ago","EDIT",198.14f,onEdit)
    DocRow("DND CHAPTER 11","4,106 words • yesterday","EDIT",280.14f,null)
    DocRow("NOVEL NOTES","Not tracked","OPEN",362.14f,null)
    R(19f,431.5f,352f,48f,onClick=onSync); T("CLOUD SYNC",36f,447.5f,318f,size=13f,bold=true)
    R(19f,489.5f,352f,48f,onClick={context.startActivity(Intent(context, PrototypeActivity::class.java))}); T("CONNECT GOOGLE ACCOUNT",36f,505.5f,318f,size=13f,bold=true)
    R(19f,547.5f,352f,48f,onClick=onFilter); T("FILTER DOCUMENTS",36f,563.5f,318f,size=13f,bold=true)
}

@Composable private fun DocRow(title:String,detail:String,status:String,y:Float,onClick:(()->Unit)?) {
    IconBox(32f,y,"D")
    if(onClick != null) R(90f,y,268f,64f,onClick=onClick)
    T(title,90f,y,216f,size=18f,bold=true); T(detail,90f,y+28f,216f,size=13f,color=Muted); P(status, if(status=="EDIT")313.95f else 313.95f,y+5f,56f,accent=status=="OPEN")
}

@Composable private fun Filter(back:()->Unit) {
    T("LIBRARY / FILTER",19f,23f,size=13f,color=Muted,bold=true); T("FILTER DOCS",19f,36f,198f,size=26f,bold=true); P("DND*",326.95f,27.05f,44.05f)
    T("NAME STARTS WITH",19f,74.09f,size=13f,color=Muted,bold=true); R(36f,111.09f,318f,24f); T("DND",36f,111.09f,318f,size=16f,bold=true)
    T("Only Google Docs whose filename begins with DND appear in the writing library.",19f,164.09f,352f,size=13f,color=Muted)
    T("INCLUDE EDITABLE DOCS",19f,211.28f,215.44f,size=18f,bold=true); T("Edits count toward today’s goal.",19f,235.28f,size=13f,color=Muted); P("ON",337.97f,216.58f,33.03f,accent=true)
    T("FOLDER",19f,270.38f,size=13f,color=Muted); T("Writing / DND",243.7f,264.88f,127.3f,size=16f)
    B("APPLY FILTER",19f,300.88f,352f,onClick=back)
}

@Composable private fun Edit() {
    T("GOOGLE DOC / EDIT MODE",19f,23f,180f,size=13f,color=Muted,bold=true); T("CHAPTER 12",19f,36f,180f,size=26f,bold=true); P("SYNCED",315.94f,27.05f,55.06f)
    R(19f,74.09f,171f,78f); T("DOC TOTAL",36f,84f,137f,size=13f,color=Muted,bold=true); T("3,842",36f,112f,137f,size=28f,bold=true)
    R(200f,74.09f,171f,78f); T("COUNTED TODAY",217f,84f,137f,size=13f,color=Muted,bold=true); T("742",217f,112f,137f,size=28f,bold=true)
    T("THE LOWER HALL",36f,181.09f,size=18f,bold=true)
    T("The door had no handle. Ego placed his palm against the metal and waited.\n\nThe lock clicked.\n\nThe text cursor remains active inside the Google Doc. Additions, edits, and deletions are tracked locally and synchronized in the background.",36f,209.09f,318f,size=14f)
    T("EVERY EDIT UPDATES TODAY’S TOTAL",19f,654.59f,250f,size=13f,color=Muted,bold=true); T("+126",331.83f,649.09f,39f,size=18f,bold=true,align=TextAlign.End)
}

@Composable private fun Builder(back:()->Unit) {
    T("ACCOUNTABILITY / LOGIC",19f,23f,size=13f,color=Muted,bold=true); T("RULE BUILDER",19f,36f,size=26f,bold=true); P("AND",332.47f,27.05f,38f)
    T("WRITE 1,000 WORDS",36f,94.59f,size=15f,bold=true); P("GOAL",309.95f,91.09f,61.05f)
    R(19f,139.09f,352f,42f); T("AND",36f,151f,size=13f,bold=true)
    T("LICHESS • 30 MIN",36f,201.59f,size=15f,bold=true); P("PENDING",293.44f,198.09f,77.56f)
    R(19f,246.09f,352f,42f); T("OR",36f,258f,size=13f,bold=true)
    T("ACODE • 30 MIN",36f,308.59f,size=15f,bold=true); P("COMPLETE",287.92f,305.09f,82.08f,accent=true)
    T("Complete either required app path after the writing goal is met.",19f,365.09f,352f,size=13f,color=Muted)
    B("SAVE RULE",19f,412.28f,352f,onClick=back)
}

@Composable private fun AppRequirement(back:()->Unit) {
    T("ACCOUNTABILITY / APP",19f,26.45f,size=13f,color=Muted,bold=true); T("APP REQUIREMENT",19f,39.45f,size=25f,bold=true)
    T("APPS ON THIS PHONE",36f,98f,size=12f,color=Muted,bold=true); T("Lichess",36f,119f,size=18f,bold=true); T("SELECT TO ADD • 30 MIN TARGET",36f,151.5f,size=13f,color=Muted)
    T("Acode",36f,186.59f,size=18f,bold=true); T("SELECT TO ADD • 30 MIN TARGET",19f,232.09f,size=13f,color=Muted); P("30 MIN",315.94f,228.59f,54.06f)
    T("Chrome",36f,312.59f,size=18f,bold=true); T("YouTube • Discord • More installed apps",36f,333.59f,318f,size=13f,color=Muted)
    B("NONE / ADD SELECTED APPS",19f,397.78f,352f,50.09f,onClick=back)
}

@Composable private fun Control(next:()->Unit) {
    T("ACCOUNTABILITY / CONTROL",19f,23f,size=13f,color=Muted,bold=true); T("BLOCKED APPS",19f,36f,size=26f,bold=true); P("2 ACTIVE LOCKS",304.92f,27.05f,65.08f)
    IconBox(32f,89.34f,"L"); T("Lichess",90f,89.34f,size=18f,bold=true); T("Blocked until requirements complete",90f,117.34f,205f,size=13f,color=Muted); P("LOCKED",302.94f,94.59f,55.06f)
    IconBox(32f,171.34f,"A"); T("Acode",90f,171.34f,size=18f,bold=true); T("Currently available",90f,199.34f,205f,size=13f,color=Muted); P("CLEAR",308.45f,176.59f,49.55f,accent=true)
    T("ENFORCEMENT",36f,257.09f,size=12f,color=Muted,bold=true); T("Selected apps remain unavailable until the active requirements are satisfied.",36f,278.09f,318f,size=13f,color=Muted)
    R(36f,322f,318f,48f,onClick=next); T("VIEW ACCESS CONTROL",53f,338f,284f,size=13f,bold=true)
}

@Composable private fun Access(onWrite:()->Unit) {
    T("ACCESS CONTROL",142.44f,218.88f,110f,size=18f,bold=true,align=TextAlign.Center); T("Lichess Locked",76f,245.88f,238f,size=18f,bold=true,align=TextAlign.Center)
    T("Finish the required writing goal to regain access.",63.72f,284.52f,262.55f,size=13f,color=Muted,align=TextAlign.Center)
    R(19f,380f,171f,78f); T("WRITING",36f,390f,137f,size=13f,color=Muted,bold=true); T("742 / 1000",36f,418f,137f,size=24f,bold=true)
    R(200f,380f,171f,78f); T("LICHESS",217f,390f,137f,size=13f,color=Muted,bold=true); T("18 / 30",217f,418f,137f,size=24f,bold=true)
    B("WRITE 258 WORDS",19f,470f,352f,onClick=onWrite); B("VIEW REQUIREMENTS",19f,530f,352f)
}

@Composable private fun Usage() {
    T("ANDROID USAGE",19f,23f,size=13f,color=Muted,bold=true); T("USAGE",19f,36f,size=26f,bold=true); T("LIVE",326.95f,29.94f,44f,size=12f,color=Muted,bold=true,align=TextAlign.End)
    IconBox(32f,92.89f,"L"); T("Lichess",90f,95.14f,size=18f,bold=true); T("Tracked today",90f,123.14f,size=13f,color=Muted); P("18 MIN",302.94f,100.39f,55.06f)
    IconBox(32f,174.89f,"A"); T("Acode",90f,177.14f,size=18f,bold=true); T("Tracked today",90f,205.14f,size=13f,color=Muted); P("30 MIN",302.94f,182.39f,55.06f,accent=true)
    T("APPS DETECTED ON THIS PHONE",36f,262.89f,size=12f,color=Muted,bold=true); T("Install or open apps to populate usage data.",36f,283.89f,318f,size=13f,color=Muted)
}

@Composable private fun Sync() {
    T("CLOUD SYNC",19f,23f,size=13f,color=Muted,bold=true); T("GOOGLE DOCS",19f,36f,size=26f,bold=true); P("ON",337.97f,27.05f,33.03f,accent=true)
    IconBox(32f,91.09f,"G"); T("BACKGROUND SYNC",96f,93.3f,size=16f,bold=true); T("Writing never pauses while Docs syncs.",96f,117.3f,230f,size=13f,color=Muted)
    T("ACTIVE FILE",36f,188.59f,size=12f,color=Muted,bold=true); T("DND CHAPTER 12",216.91f,183.09f,137f,size=17f,bold=true); T("Saved locally • syncing in background",36f,239.09f,318f,size=13f,color=Muted)
    T("LAST SYNC",19f,285.69f,size=13f,color=Muted,bold=true); T("14:42",322.03f,285.69f,49f,size=13f,bold=true,align=TextAlign.End)
    T("DESTINATION",19f,321.69f,size=13f,color=Muted,bold=true); T("Writing / DND",243.7f,321.69f,127.3f,size=16f,align=TextAlign.End)
}

@Composable private fun History(next:()->Unit) {
    T("PROGRESS LOG",19f,23f,size=13f,color=Muted,bold=true); T("HISTORY",19f,36f,size=26f,bold=true); P("12 DAY STREAK",277.39f,29.94f,92.61f)
    T("STREAK",34f,94.89f,size=13f,color=Muted,bold=true); T("12",34f,118.89f,size=30f,bold=true); T("MONTH",215f,94.89f,size=13f,color=Muted,bold=true); T("18.4K",215f,118.89f,size=30f,bold=true)
    HistoryRow("SEP 10","1,204",193.64f); HistoryRow("SEP 09","1,086",275.64f); HistoryRow("SEP 08","642",357.64f)
    B("VIEW ANALYTICS",19f,440f,352f,onClick=next)
}
@Composable private fun HistoryRow(day:String,words:String,y:Float) { IconBox(32f,y-2.25f,"H"); T(day,90f,y,100f,size=18f,bold=true); T("Daily writing total",90f,y+28f,160f,size=13f,color=Muted); P(words,313.95f,y+5f,56f) }

@Composable private fun Analytics() {
    T("PROGRESS / ANALYTICS",19f,23f,size=13f,color=Muted,bold=true); T("STATISTICS",19f,36f,size=26f,bold=true); P("30 DAYS",310.44f,29.94f,59.56f)
    R(19f,79.89f,171f,78f); T("TOTAL WORDS",36f,90f,137f,size=13f,color=Muted,bold=true); T("31,842",36f,118f,137f,size=28f,bold=true)
    R(200f,79.89f,171f,78f); T("DAILY AVG",217f,90f,137f,size=13f,color=Muted,bold=true); T("1,061",217f,118f,137f,size=28f,bold=true)
    R(31f,181.89f,328f,121f); T("WORDS / DAY",48f,195f,290f,size=11f,color=Muted,bold=true); T("▁▂▃▆▅▇▄▆▅▇",48f,231f,290f,size=28f,color=Accent,bold=true,align=TextAlign.Center)
    T("GOALS COMPLETED",19f,326.89f,size=13f,color=Muted,bold=true); T("24 / 30",302.45f,326.89f,68f,size=13f,bold=true,align=TextAlign.End)
    T("BEST DAY",19f,362.89f,size=13f,color=Muted,bold=true); T("1,842 WORDS",263.28f,362.89f,107f,size=13f,bold=true,align=TextAlign.End)
    T("LONGEST STREAK",19f,398.89f,size=13f,color=Muted,bold=true); T("19 DAYS",302.45f,398.89f,68f,size=13f,bold=true,align=TextAlign.End)
}

@Composable private fun Permissions(context:Context,next:()->Unit) {
    T("ANDROID / PERMISSIONS",19f,23f,size=13f,color=Muted,bold=true); T("SYSTEM ACCESS",19f,36f,size=26f,bold=true); P("READY",321.45f,27.05f,49.55f,accent=true)
    IconBox(32f,87.09f,"U"); T("USAGE ACCESS",90f,89.34f,size=18f,bold=true); T("Usage time can be read.",90f,117.34f,size=13f,color=Muted); P("OK",324.97f,94.59f,45.03f,accent=true)
    IconBox(32f,169.09f,"B"); T("APP BLOCKING",90f,171.34f,size=18f,bold=true); T("Blocking capability available.",90f,199.34f,size=13f,color=Muted); P("OK",324.97f,176.59f,45.03f,accent=true)
    IconBox(32f,251.09f,"G"); T("GOOGLE ACCOUNT",90f,253.34f,size=18f,bold=true); T("Sign-in required for Docs sync.",90f,281.34f,size=13f,color=Muted); P("OK",324.97f,258.59f,45.03f,accent=true)
    B("REVIEW PERMISSIONS",19f,322.09f,352f,onClick={context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))})
    R(19f,380f,352f,48f,onClick=next); T("CONTINUE",36f,396f,318f,size=13f,bold=true)
}

@Composable private fun Account(context:Context,next:()->Unit) {
    T("ACCOUNT / CLOUD",19f,23f,size=13f,color=Muted,bold=true); T("GOOGLE ACCOUNT",19f,36f,size=26f,bold=true); P("CONNECTED",299.42f,27.05f,70.58f,accent=true)
    IconBox(32f,91.09f,"G"); T("WRITER ACCOUNT",96f,93.3f,size=16f,bold=true); T("Google Drive + Docs access",96f,117.3f,230f,size=13f,color=Muted)
    T("SYNC",36f,186.59f,size=13f,color=Muted,bold=true); P("ENABLED",293.44f,183.09f,76.56f,accent=true)
    T("ACCESS SCOPE",36f,260.09f,size=13f,color=Muted,bold=true); T("Read and write access is used only for the selected writing destination.",36f,281.09f,318f,size=13f,color=Muted)
    B("RECONNECT ACCOUNT",19f,345.28f,352f,onClick={GoogleOAuthManager(context).startAuthorization()})
    R(19f,403f,352f,48f,onClick=next); T("CONTINUE",36f,419f,318f,size=13f,bold=true)
}

@Composable private fun Setup() {
    T("GOOGLE DOCS / SETUP",19f,23f,size=13f,color=Muted,bold=true); T("DESTINATION",19f,36f,size=26f,bold=true)
    T("USE EXISTING DOC",36f,94.59f,size=18f,bold=true); P("SELECTED",287.92f,91.09f,82.08f,accent=true); R(36f,134.09f,318f,48f); T("DND CHAPTER 12",53f,150.09f,284f,size=14f,bold=true)
    T("CREATE NEW DOC",36f,231.59f,size=18f,bold=true); P("OPTIONAL",287.92f,228.09f,82.08f); T("Filename and folder can be configured here.",36f,271.09f,318f,size=13f,color=Muted)
    B("SAVE DESTINATION",19f,362.09f,352f)
}

@Composable private fun QuickAccess() {
    T("ANDROID / QUICK ACCESS",19f,23f,size=13f,color=Muted,bold=true); T("HOME WIDGETS",19f,36f,size=26f,bold=true); P("CONCEPT",310.44f,27.05f,59.56f)
    T("DRAFTLOCK",36f,91.09f,size=18f,bold=true); T("TODAY",326.45f,96.59f,43.55f,size=12f,color=Muted,bold=true,align=TextAlign.End); T("742 / 1000",36f,125.09f,size=26f,bold=true)
    T("REQUIREMENTS",36f,229.78f,size=13f,color=Muted,bold=true); P("1 PENDING",282.42f,226.28f,87.58f)
    T("LICHESS",36f,269.28f,size=16f,bold=true); T("18 / 30M",275.66f,269.28f,94.34f,size=13f,bold=true,align=TextAlign.End)
    T("ACODE",36f,301.28f,size=16f,bold=true); T("30 / 30M",275.66f,301.28f,94.34f,size=13f,bold=true,align=TextAlign.End)
    T("WRITE",19f,354.28f,size=13f,color=Muted,bold=true); T("Open the writing surface from your launcher widget.",19f,412.28f,352f,size=13f,color=Muted)
}

@Composable private fun Settings() {
    T("SYSTEM / CONFIG",19f,23f,size=13f,color=Muted,bold=true); T("SETTINGS",19f,36f,size=26f,bold=true)
    SettingRow("DAILY WORD GOAL","1,000",92.89f,308.45f,61.55f); SettingRow("RESET TIME","12:00 AM",170.39f,291.92f,78.08f); SettingRow("EMERGENCY OVERRIDE","AVAILABLE",247.89f,286.42f,83.58f); SettingRow("RESET TODAY","OPEN",325.39f,313.95f,56.05f); B("MANAGE GOOGLE ACCOUNT",19f,391.89f,352f)
}
@Composable private fun SettingRow(label:String,value:String,y:Float,x:Float,w:Float) { T(label,32f,y,250f,size=15f,bold=true); T("System preference",32f,y+28f,180f,size=13f,color=Muted); P(value,x,y+5f,w) }

@Composable private fun BottomNav(onPage:(Page)->Unit) {
    Row(Modifier.offset(19.dp,778.dp).width(352.dp),horizontalArrangement=Arrangement.SpaceBetween) {
        NavItem("01","HOME",Page.HOME,onPage,53.47f); NavItem("02","WRITE",Page.WRITE,onPage,138.03f); NavItem("03","RULES",Page.RULES,onPage,227.47f); NavItem("04","DOCS",Page.DOCS,onPage,316.92f)
    }
}
@Composable private fun NavItem(n:String,label:String,page:Page,onPage:(Page)->Unit,x:Float) {
    Column(Modifier.width(44.dp).clickable{onPage(page)},horizontalAlignment=Alignment.CenterHorizontally){ T(n,x,778f,44f,size=16f,bold=true,align=TextAlign.Center); T(label,x,798f,44f,size=12f,color=Muted,align=TextAlign.Center) }
}
