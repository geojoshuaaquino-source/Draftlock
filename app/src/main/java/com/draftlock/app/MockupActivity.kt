package com.draftlock.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.draftlock.app.data.AppRequirement
import com.draftlock.app.data.LocalDocument
import com.draftlock.app.data.LockedApp
import com.draftlock.app.ui.theme.DraftLockTheme

private enum class Screen { HOME, LIBRARY, EDITOR, FOCUS, SETTINGS }
private val BG = Color(0xFF030817)
private val GLASS = Color(0xD20B162C)
private val GLASS2 = Color(0xA8142038)
private val INK = Color(0xFFF1F5FF)
private val MUTED = Color(0xFF8F9FBC)
private val BLUE = Color(0xFF4B63FF)
private val CYAN = Color(0xFF25B8FF)
private val PURPLE = Color(0xFF7655FF)
private val GREEN = Color(0xFF35D3A0)

class MockupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DraftLockTheme { DraftLockMockup() } }
    }
}

@Composable
private fun DraftLockMockup() {
    val vm: DraftLockViewModel = viewModel()
    val context = LocalContext.current
    val words by vm.todayWords.collectAsStateWithLifecycle()
    val quota by vm.quota.collectAsStateWithLifecycle()
    val docs by vm.localDocs.collectAsStateWithLifecycle()
    val lockedApps by vm.lockedApps.collectAsStateWithLifecycle()
    val requirements by vm.requirements.collectAsStateWithLifecycle()
    val text by vm.text.collectAsStateWithLifecycle()
    val docName by vm.documentName.collectAsStateWithLifecycle()
    val reset by vm.resetMinutes.collectAsStateWithLifecycle()
    val logic by vm.logic.collectAsStateWithLifecycle()
    val autoSave by vm.googleAutoSave.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(Screen.HOME) }
    var selected by remember { mutableStateOf<LocalDocument?>(null) }
    var showOverride by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var showApp by remember { mutableStateOf(false) }
    var showReq by remember { mutableStateOf(false) }
    var onboarding by remember { mutableStateOf(!context.getSharedPreferences("draftlock_ui",0).getBoolean("onboarded",false)) }

    LaunchedEffect(Unit) { vm.checkGoogleConnection(); vm.refreshUsage(); if (vm.isGoogleConnected) vm.fetchDriveFiles() }

    Box(Modifier.fillMaxSize().background(BG)) {
        Background()
        if (onboarding) {
            Onboarding(
                google = { onboarding=false; context.getSharedPreferences("draftlock_ui",0).edit().putBoolean("onboarded",true).apply(); vm.startGoogleAuth(context) },
                local = { onboarding=false; context.getSharedPreferences("draftlock_ui",0).edit().putBoolean("onboarded",true).apply() }
            )
        } else Column(Modifier.fillMaxSize()) {
            if (screen != Screen.EDITOR) TopBar(vm, words)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when(screen) {
                    Screen.HOME -> Home(vm, docs, words, quota, { selected=null; screen=Screen.EDITOR }, { screen=Screen.LIBRARY })
                    Screen.LIBRARY -> Library(docs, {screen=Screen.HOME}, {showNew=true}, { selected=it; vm.selectedLocalDocId=it.id; screen=Screen.EDITOR }, vm)
                    Screen.EDITOR -> Editor(vm, selected, text, docName, {selected=null;screen=Screen.HOME})
                    Screen.FOCUS -> Focus(vm, lockedApps, requirements, {showApp=true}, {showReq=true}, {showOverride=true})
                    Screen.SETTINGS -> Settings(vm, quota, reset, logic, autoSave, {showOverride=true})
                }
            }
            if(screen != Screen.EDITOR) BottomBar(screen) { screen=it }
        }
    }

    if(showNew) InputDialog("New draft","Title","Untitled Draft", {vm.createLocalDoc(it);showNew=false},{showNew=false})
    if(showApp) InputDialog("Lock app","Package name","", { if(it.isNotBlank()) vm.addLockedApp(LockedApp(it.trim(),it.substringAfterLast('.').replaceFirstChar { c->c.uppercase() }));showApp=false },{showApp=false})
    if(showReq) RequirementDialog({p,n,m->if(p.isNotBlank()&&n.isNotBlank())vm.addRequirement(AppRequirement(packageName=p.trim(),displayName=n.trim(),requiredMinutes=m));showReq=false},{showReq=false})
    if(showOverride) AlertDialog(onDismissRequest={showOverride=false},title={Text("Emergency Override")},text={Text("Unlock protected apps for 15 minutes.")},confirmButton={TextButton({vm.activateEmergencyOverride();showOverride=false}){Text("Unlock")}},dismissButton={TextButton({showOverride=false}){Text("Cancel")}})
}

@Composable private fun Background(){Box(Modifier.fillMaxSize()){Box(Modifier.size(520.dp).offset((-240).dp,-180.dp).background(Brush.radialGradient(listOf(PURPLE.copy(.22f),Color.Transparent)),CircleShape));Box(Modifier.size(560.dp).align(Alignment.TopEnd).offset(250.dp,-190.dp).background(Brush.radialGradient(listOf(CYAN.copy(.14f),Color.Transparent)),CircleShape));Box(Modifier.fillMaxWidth().height(280.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent,BLUE.copy(.07f)))) )}}

@Composable private fun Onboarding(google:()->Unit,local:()->Unit){Column(Modifier.fillMaxSize().padding(28.dp).navigationBarsPadding(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Box(Modifier.size(98.dp).clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(CYAN,PURPLE))),contentAlignment=Alignment.Center){Icon(painterResource(R.drawable.ic_logo_draftlock),null,tint=Color.Unspecified,modifier=Modifier.size(66.dp))};Spacer(Modifier.height(22.dp));Text("draftlock",color=INK,fontSize=30.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(9.dp));Text("Write freely.\nKeep it yours.",color=MUTED,fontSize=16.sp,lineHeight=23.sp);Spacer(Modifier.height(32.dp));Action("Continue with Google",R.drawable.ic_google,true,google);Spacer(Modifier.height(10.dp));Action("Use local vault",R.drawable.ic_write,false,local)}}

@Composable private fun TopBar(vm:DraftLockViewModel,words:Int){Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp,10.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(GLASS2),contentAlignment=Alignment.Center){Icon(painterResource(R.drawable.ic_logo_draftlock),null,tint=Color.Unspecified,Modifier.size(29.dp))};Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text("draftlock",color=INK,fontSize=19.sp,fontWeight=FontWeight.Bold);Text(if(vm.isGoogleConnected)"Google Drive connected" else "Your private writing vault",color=MUTED,fontSize=10.sp)};Badge("${words}w",CYAN)}}

@Composable private fun BottomBar(screen:Screen,on:(Screen)->Unit){Surface(color=Color(0xDD061124),tonalElevation=0.dp){Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(7.dp),horizontalArrangement=Arrangement.spacedBy(5.dp)){Nav("Home",R.drawable.ic_home,screen==Screen.HOME){on(Screen.HOME)};Nav("Library",R.drawable.ic_docs,screen==Screen.LIBRARY){on(Screen.LIBRARY)};Box(Modifier.weight(1.2f).height(50.dp).clip(RoundedCornerShape(17.dp)).background(Brush.horizontalGradient(listOf(CYAN,PURPLE))).clickable{on(Screen.EDITOR)},contentAlignment=Alignment.Center){Icon(painterResource(R.drawable.ic_write),null,tint=Color.White,Modifier.size(20.dp))};Nav("Focus",R.drawable.ic_usage,screen==Screen.FOCUS){on(Screen.FOCUS)};Nav("Settings",R.drawable.ic_analytics,screen==Screen.SETTINGS){on(Screen.SETTINGS)}}}}
@Composable private fun RowScope.Nav(label:String,icon:Int,sel:Boolean,onClick:()->Unit){Box(Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(15.dp)).background(if(sel)BLUE.copy(.17f)else Color.Transparent).clickable(onClick=onClick),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(painterResource(icon),null,tint=if(sel)CYAN else MUTED,Modifier.size(18.dp));Text(label,color=if(sel)INK else MUTED,fontSize=8.sp)}}}

@Composable private fun Home(vm:DraftLockViewModel,docs:List<LocalDocument>,words:Int,quota:Int,onWrite:()->Unit,onLibrary:()->Unit){var q by remember{mutableStateOf("")};var filter by remember{mutableStateOf("All")};val locked=quota>0&&words>=quota;val list=docs.filter{q.isBlank()||it.title.contains(q,true)||it.content.contains(q,true)};LazyColumn(Modifier.fillMaxSize().padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(11.dp),contentPadding=PaddingValues(bottom=20.dp)){item{Spacer(Modifier.height(6.dp));Text("Good evening,",color=INK,fontSize=25.sp,fontWeight=FontWeight.SemiBold);Text("Your drafts are safe. Keep going.",color=MUTED,fontSize=12.sp)};item{Glass{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){Stat("Total Drafts",docs.size.toString());Stat("Locked",if(locked)docs.size.toString()else"0");Stat("Unlocked",if(locked)"0"else docs.size.toString())}}};item{Search(q,{q=it},"Search drafts…")};item{Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){listOf("All","Locked","Unlocked").forEach{Filter(it,filter==it){filter=it}}}};items(list.filter{filter=="All"||filter=="Locked"&&locked||filter=="Unlocked"&&!locked},key={it.id}){d->Draft(d,locked){vm.selectedLocalDocId=d.id;onWrite()}};item{Glass{Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Today",color=INK,fontWeight=FontWeight.SemiBold);Text(if(quota==0)"Unlimited quota" else "$words / $quota words",color=MUTED,fontSize=10.sp)};Badge(if(quota==0)"∞" else "${(words*100/quota.coerceAtLeast(1)).coerceIn(0,100)}%",CYAN)}}};item{Action("New draft",R.drawable.ic_write,true,onWrite)}}}

@Composable private fun Library(docs:List<LocalDocument>,back:()->Unit,new:()->Unit,onOpen:(LocalDocument)->Unit,vm:DraftLockViewModel){var rename by remember{mutableStateOf<LocalDocument?>(null)};Column(Modifier.fillMaxSize().padding(horizontal=18.dp)){Title("Library","Your local drafts",back,new,"New");LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=20.dp)){items(docs,key={it.id}){d->Glass{Row(verticalAlignment=Alignment.CenterVertically){Icon(painterResource(R.drawable.ic_docs),null,tint=CYAN,Modifier.size(22.dp));Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f).clickable{onOpen(d)}){Text(d.title,color=INK,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis);Text("${d.wordCount} words",color=MUTED,fontSize=9.sp)};TextButton({rename=d}){Text("Rename")};TextButton({vm.deleteLocalDoc(d.id)}){Text("Delete")}}}}}}};rename?.let{d->InputDialog("Rename draft","Title",d.title,{vm.renameLocalDoc(d.id,it);rename=null},{rename=null})}}

@Composable private fun Editor(vm:DraftLockViewModel,selected:LocalDocument?,text:String,name:String,back:()->Unit){var title by remember(selected?.id,name){mutableStateOf(selected?.title?:name)};var body by remember(selected?.id,text){mutableStateOf(selected?.content?:text)};Column(Modifier.fillMaxSize().statusBarsPadding()){Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){Text("‹",color=INK,fontSize=32.sp,modifier=Modifier.clickable{back()});Spacer(Modifier.width(8.dp));Column(Modifier.weight(1f)){Text(title.ifBlank{"Untitled Draft"},color=INK,fontSize=16.sp,maxLines=1,overflow=TextOverflow.Ellipsis);Text("${wordCount(body)} words",color=MUTED,fontSize=9.sp)};Badge("Locked",PURPLE)};Glass(Modifier.fillMaxWidth().weight(1f).padding(horizontal=18.dp)){BasicTextField(body,{body=it;if(selected!=null)vm.updateLocalDocContent(selected.id,it)else vm.onTextChanged(it)},Modifier.fillMaxSize(),textStyle=LocalTextStyle.current.copy(color=INK,fontSize=15.sp,lineHeight=25.sp),decorationBox={inner->if(body.isBlank())Text("Start writing…",color=MUTED,fontSize=15.sp);inner()})};Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Text(if(vm.isGoogleConnected)"Auto-save • Drive" else"Auto-save • Local",color=GREEN,fontSize=10.sp);Spacer(Modifier.weight(1f));Action("Save",R.drawable.ic_write,true){if(vm.isGoogleConnected&&vm.googleDocumentId.value.isNotBlank())vm.syncTextToDoc()else vm.onTextChanged(body)}}}}

@Composable private fun Focus(vm:DraftLockViewModel,apps:List<LockedApp>,reqs:List<AppRequirement>,addApp:()->Unit,addReq:()->Unit,override:()->Unit){Column(Modifier.fillMaxSize().padding(horizontal=18.dp)){Title("Focus & Locked Apps","Stay in the zone",null,null,null);LazyColumn(verticalArrangement=Arrangement.spacedBy(11.dp),contentPadding=PaddingValues(bottom=20.dp)){item{Glass{Text("Focus Mode",color=INK,fontWeight=FontWeight.SemiBold);Text("Block distractions using your real protection rules.",color=MUTED,fontSize=10.sp);Spacer(Modifier.height(10.dp));Action("Refresh protection",R.drawable.ic_write,true){vm.refreshUsage()}}};item{Section("Locked Apps","Apps protected by DraftLock",addApp)};items(apps,key={it.packageName}){a->RowCard(a.displayName,a.packageName,R.drawable.ic_usage){vm.deleteLockedApp(a.packageName)}};item{Section("Writing Requirements","Required usage before apps unlock",addReq)};items(reqs,key={it.id}){r->RowCard(r.displayName,"${r.requiredMinutes} min • ${r.packageName}",R.drawable.ic_write){vm.deleteRequirement(r.id)}};item{RowCard("Emergency Override","Unlock protected apps for 15 minutes",R.drawable.ic_usage,override)}}}}

@Composable private fun Settings(vm:DraftLockViewModel,quota:Int,reset:Int,logic:String,auto:Boolean,override:()->Unit){var q by remember(quota){mutableFloatStateOf(quota.toFloat())};var r by remember(reset){mutableFloatStateOf(reset.toFloat())};Column(Modifier.fillMaxSize().padding(horizontal=18.dp)){Title("Settings","Control your writing lock",null,null,null);LazyColumn(verticalArrangement=Arrangement.spacedBy(11.dp),contentPadding=PaddingValues(bottom=24.dp)){item{Glass{Text("Writing quota",color=INK,fontWeight=FontWeight.SemiBold);Text(if(quota==0)"Unlimited" else "$quota words",color=MUTED,fontSize=10.sp);Slider(q.coerceIn(0f,5000f),{q=it},onValueChangeFinished={vm.setQuota(q.toInt())},valueRange=0f..5000f)}};item{Glass{Text("Reset period",color=INK,fontWeight=FontWeight.SemiBold);Text(if(reset==0)"Daily" else"Every $reset minutes",color=MUTED,fontSize=10.sp);Slider(r.coerceIn(0f,1440f),{r=it},onValueChangeFinished={vm.setResetMinutes(r.toInt())},valueRange=0f..1440f)}};item{RowCard("Unlock logic",logic,R.drawable.ic_usage){vm.setLogic(if(logic=="AND")"OR"else"AND")}};item{RowCard("Google Drive",vm.syncStatus,R.drawable.ic_docs){vm.startGoogleAuth(LocalContext.current)}};item{RowCard("Auto-save",if(auto)"Enabled"else"Disabled",R.drawable.ic_docs){vm.setGoogleAutoSave(!auto)}};item{RowCard("Emergency Override","Unlock for 15 minutes",R.drawable.ic_usage,override)};item{RowCard("Protection diagnostics",vm.blockingDiagnostics,R.drawable.ic_analytics){vm.refreshUsage()}}}}}

@Composable private fun Glass(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){Surface(modifier,color=GLASS,shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Color(0x332E6CFF)),tonalElevation=0.dp){Column(Modifier.padding(15.dp),content=content)}}
@Composable private fun Action(text:String,icon:Int,primary:Boolean,onClick:()->Unit){Row(Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(15.dp)).background(if(primary)Brush.horizontalGradient(listOf(CYAN,PURPLE))else Brush.linearGradient(listOf(GLASS2,GLASS2))).clickable(onClick=onClick),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){Icon(painterResource(icon),null,tint=Color.White,Modifier.size(17.dp));Spacer(Modifier.width(7.dp));Text(text,color=Color.White,fontSize=11.sp,fontWeight=FontWeight.Bold)}}
@Composable private fun Badge(text:String,accent:Color){Box(Modifier.clip(RoundedCornerShape(12.dp)).background(accent.copy(.16f)).padding(horizontal=9.dp,vertical=5.dp)){Text(text,color=accent,fontSize=9.sp,fontWeight=FontWeight.Bold)}}
@Composable private fun Stat(label:String,value:String){Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.width(82.dp)){Text(value,color=INK,fontSize=17.sp,fontWeight=FontWeight.SemiBold);Text(label,color=MUTED,fontSize=8.sp)}}
@Composable private fun Search(value:String,onValue:(String)->Unit,hint:String){Surface(color=GLASS,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,Color(0x332E6CFF))){Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){Text("⌕",color=MUTED,fontSize=20.sp);Spacer(Modifier.width(8.dp));BasicTextField(value,onValue,Modifier.weight(1f),textStyle=LocalTextStyle.current.copy(color=INK,fontSize=12.sp),decorationBox={inner->if(value.isBlank())Text(hint,color=MUTED,fontSize=12.sp);inner()})}}}
@Composable private fun Filter(label:String,selected:Boolean,on:()->Unit){Box(Modifier.clip(RoundedCornerShape(15.dp)).background(if(selected)BLUE else GLASS2).clickable(onClick=on).padding(horizontal=15.dp,vertical=8.dp)){Text(label,color=if(selected)INK else MUTED,fontSize=10.sp)}}
@Composable private fun Draft(d:LocalDocument,locked:Boolean,on:()->Unit){Glass(Modifier.fillMaxWidth().clickable(onClick=on)){Row(verticalAlignment=Alignment.CenterVertically){Icon(painterResource(if(locked)R.drawable.ic_lock else R.drawable.ic_docs),null,tint=if(locked)PURPLE else CYAN,Modifier.size(22.dp));Spacer(Modifier.width(11.dp));Column(Modifier.weight(1f)){Text(d.title,color=INK,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(d.content.ifBlank{"Start writing…"}.replace("\n"," "),color=MUTED,fontSize=9.sp,maxLines=1,overflow=TextOverflow.Ellipsis);Text("${d.wordCount} words",color=MUTED,fontSize=8.sp)};Badge(if(locked)"Locked"else"Unlocked",if(locked)PURPLE else GREEN)}}}
@Composable private fun Title(title:String,sub:String,back:(()->Unit)?,action:(()->Unit)?,label:String?){Row(Modifier.fillMaxWidth().padding(top=7.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically){if(back!=null)Text("‹",color=INK,fontSize=30.sp,modifier=Modifier.clickable(onClick=back));Column(Modifier.weight(1f)){Text(title,color=INK,fontSize=22.sp,fontWeight=FontWeight.SemiBold);Text(sub,color=MUTED,fontSize=10.sp)};if(action!=null&&label!=null)TextButton(onClick=action){Text(label)}}}
@Composable private fun Section(title:String,sub:String,onAdd:()->Unit){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(title,color=INK,fontWeight=FontWeight.SemiBold);Text(sub,color=MUTED,fontSize=9.sp)};TextButton(onClick=onAdd){Text("+ Add")}}}
@Composable private fun RowCard(title:String,sub:String,icon:Int,onClick:(()->Unit)?=null){Glass(Modifier.fillMaxWidth().then(if(onClick!=null)Modifier.clickable(onClick=onClick)else Modifier)){Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(38.dp).clip(CircleShape).background(BLUE.copy(.16f)),contentAlignment=Alignment.Center){Icon(painterResource(icon),null,tint=CYAN,Modifier.size(19.dp))};Spacer(Modifier.width(11.dp));Column(Modifier.weight(1f)){Text(title,color=INK,fontWeight=FontWeight.Medium,fontSize=13.sp);Text(sub,color=MUTED,fontSize=9.sp,maxLines=2,overflow=TextOverflow.Ellipsis)};Text("›",color=MUTED,fontSize=20.sp)}}}
@Composable private fun InputDialog(title:String,label:String,initial:String,ok:(String)->Unit,cancel:()->Unit){var v by remember{mutableStateOf(initial)};AlertDialog(onDismissRequest=cancel,title={Text(title)},text={OutlinedTextField(v,{v=it},label={Text(label)},singleLine=true)},confirmButton={TextButton({ok(v)}){Text("Save")}},dismissButton={TextButton(cancel){Text("Cancel")}})}
@Composable private fun RequirementDialog(ok:(String,String,Int)->Unit,cancel:()->Unit){var p by remember{mutableStateOf("")};var n by remember{mutableStateOf("")};var m by remember{mutableFloatStateOf(30f)};AlertDialog(onDismissRequest=cancel,title={Text("Writing requirement")},text={Column{OutlinedTextField(p,{p=it},label={Text("Package name")},singleLine=true);OutlinedTextField(n,{n=it},label={Text("Display name")},singleLine=true);Text("${m.toInt()} minutes");Slider(m,{m=it},valueRange=5f..480f)}},confirmButton={TextButton({ok(p,n,m.toInt())}){Text("Add")}},dismissButton={TextButton(cancel){Text("Cancel")}})}
private fun wordCount(s:String)=s.trim().split(Regex("\\s+")).count{it.isNotBlank()}
