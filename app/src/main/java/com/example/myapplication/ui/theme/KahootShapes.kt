package com.example.myapplication.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

// Kahoot's 4 signature answer shapes. Each is fixed to one KahootPalette color
// (see Color.kt) so the pairing stays consistent wherever this appears.
enum class KahootAnswerShape { Triangle, Diamond, Circle, Square }

object TriangleShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            moveTo(size.width / 2f, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

object DiamondShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            moveTo(size.width / 2f, 0f)
            lineTo(size.width, size.height / 2f)
            lineTo(size.width / 2f, size.height)
            lineTo(0f, size.height / 2f)
            close()
        }
        return Outline.Generic(path)
    }
}

private fun shapeFor(shape: KahootAnswerShape): Shape = when (shape) {
    KahootAnswerShape.Triangle -> TriangleShape
    KahootAnswerShape.Diamond -> DiamondShape
    KahootAnswerShape.Circle -> CircleShape
    KahootAnswerShape.Square -> RoundedCornerShape(6.dp)
}

/** One of Kahoot's 4 signature answer tiles — a solid color block clipped to its paired shape. */
@Composable
fun KahootShapeIcon(
    shape: KahootAnswerShape,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .background(color, shapeFor(shape))
    )
}

/** The KahootPalette color permanently paired with this answer shape. */
fun KahootAnswerShape.paletteColor(): Color = when (this) {
    KahootAnswerShape.Triangle -> KahootPalette.Red
    KahootAnswerShape.Diamond -> KahootPalette.Blue
    KahootAnswerShape.Circle -> KahootPalette.Yellow
    KahootAnswerShape.Square -> KahootPalette.Green
}

/** Text glyph fallback for contexts that want a symbol rather than a drawn shape. */
fun KahootAnswerShape.glyph(): String = when (this) {
    KahootAnswerShape.Triangle -> "▲"
    KahootAnswerShape.Diamond -> "◆"
    KahootAnswerShape.Circle -> "●"
    KahootAnswerShape.Square -> "■"
}
