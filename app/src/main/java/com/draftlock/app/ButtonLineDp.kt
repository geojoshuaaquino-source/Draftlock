package com.draftlock.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ButtonLine(text: String, y: Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .offset(19.dp, y)
            .width(352.dp)
            .height(48.dp)
            .background(Panel)
            .clickable { onClick() },
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 17.dp),
            color = Ink,
            fontSize = 13.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
    }
}
