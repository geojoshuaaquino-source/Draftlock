package com.draftlock.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.draftlock.app.data.DraftLockDatabase
import com.draftlock.app.ui.theme.DraftLockColors
import com.draftlock.app.ui.theme.DraftLockTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BlockingOverlayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val blockedPkg = intent.getStringExtra("blocked_pkg") ?: "Blocked app"
        val blockedLabel = intent.getStringExtra("blocked_label") ?: blockedPkg
        setContent {
            DraftLockTheme {
                BlockingScreen(blockedLabel, blockedPkg) {
                    startActivity(Intent(this, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) })
                    finish()
                }
            }
        }
        // also log block event
        lifecycleScope.launch {
            val db = DraftLockDatabase.get(this@BlockingOverlayActivity)
            // keep simple: not persisting history, just ensure UI
        }
    }
}

@Composable
private fun BlockingScreen(label: String, pkg: String, onWrite: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B0F0B), Color(0xFF1A2E1A)))), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)), elevation = CardDefaults.cardElevation(12.dp), modifier = Modifier.padding(20.dp).fillMaxWidth()) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(64.dp).background(Color(0xFF2A1020), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_lock_closed), null, tint = DraftLockColors.neonPink, modifier = Modifier.size(32.dp))
                }
                Text("App Blocked", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Color.White)
                Text("\"$label\" is locked until you hit your writing goal.", style = MaterialTheme.typography.bodyMedium, color = DraftLockColors.muted)
                Divider(color = DraftLockColors.line)
                Text(pkg, style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted)
                Text("Finish your daily quota in DraftLock to unlock. Offline progress counts — Gmail sync is a bonus, not required.", style = MaterialTheme.typography.bodySmall, color = DraftLockColors.muted)
                Button(onClick = onWrite, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = DraftLockColors.accent, contentColor = Color.Black)) {
                    Icon(painterResource(R.drawable.ic_write), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Go Write", fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onWrite) { Text("Emergency 15-min override in DraftLock → Settings") }
            }
        }
    }
}
