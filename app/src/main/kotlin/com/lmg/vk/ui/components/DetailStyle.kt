package com.lmg.vk.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Tokens from the approved web detail-page reference; independent of dynamic system colors. */
object DetailStyle {
    val accent = Color(0xFFFA354B)
    val padding = 22.dp
    val sectionGap = 22.dp
    fun background(dark: Boolean) = if (dark) Color(0xFF101012) else Color.White
    fun text(dark: Boolean) = if (dark) Color(0xFFF5F5F7) else Color(0xFF17171B)
    fun muted(dark: Boolean) = if (dark) Color(0xFFA7A7AE) else Color(0xFF73737C)
    fun surface(dark: Boolean) = if (dark) Color(0xFF222226) else Color(0xFFF2F2F5)
    fun line(dark: Boolean) = if (dark) Color(0xFF29292E) else Color(0xFFEDEDF1)
}

@Composable
fun DetailSecondaryAction(label: String, icon: ImageVector, isDark: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.alpha(if (enabled) 1f else .42f).detailClickable(enabled, onClick)
        .heightIn(min = 44.dp).padding(horizontal = 7.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = DetailStyle.muted(isDark), modifier = Modifier.size(17.dp))
        Text(label, color = DetailStyle.muted(isDark), fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
    }
}
