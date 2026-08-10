package com.example.myapplication.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Kahoot-inspired palette: vivid purple backgrounds + the 4 signature
// answer colors, each paired with its own shape (triangle/diamond/circle/square).
object KahootPalette {
    val PurpleDark = Color(0xFF2A0944)
    val Purple = Color(0xFF46178F)
    val PurpleLight = Color(0xFF864CBD)

    // Answer colors — kept paired 1:1 with KahootAnswerShape in KahootShapes.kt
    val Red = Color(0xFFE21B3C)      // Triangle
    val Blue = Color(0xFF1368CE)     // Diamond
    val Yellow = Color(0xFFFFA602)   // Circle
    val Green = Color(0xFF26890C)    // Square

    val White = Color(0xFFFFFFFF)
    val Cream = Color(0xFFFFF8E7)
    val TextDark = Color(0xFF2A0944)
}