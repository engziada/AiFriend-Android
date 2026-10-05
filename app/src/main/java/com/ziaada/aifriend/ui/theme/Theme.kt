package com.ziaada.aifriend.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme(
    primary = Color(0xFF24C8FF),
    secondary = Color(0xFF7C5CFF),
    background = Color(0xFF03152C),
    surface = Color(0xFF071F3E),
    onBackground = Color.White,
    onSurface = Color.White,
)

@Composable
fun AiFriendTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
