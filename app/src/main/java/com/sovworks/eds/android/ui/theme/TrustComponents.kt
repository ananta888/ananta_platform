package com.sovworks.eds.android.ui.theme

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

@Composable
fun TrustStars(level: Int) {
    Row {
        for (i in 1..5) {
            val color = if (i <= level) Color(0xFFFFBC00) else Color.LightGray
            Text(
                text = "★",
                color = color,
                fontSize = 18.sp
            )
        }
    }
}
