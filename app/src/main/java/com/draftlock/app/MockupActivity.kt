package com.draftlock.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MockupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: DraftLockViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            DraftLockMockupApp(vm)
        }
    }
}
