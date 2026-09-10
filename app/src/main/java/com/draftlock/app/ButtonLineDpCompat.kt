package com.draftlock.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

@Composable
private fun ButtonLine(text: String, y: Dp, onClick: () -> Unit) {
    ButtonLine(text, y.value.toInt(), onClick)
}
