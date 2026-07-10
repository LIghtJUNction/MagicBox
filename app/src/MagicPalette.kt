package com.github.lightjunction.magicbox

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

object MagicPalette {
    val background = Color(0xFF050506)
    val surface = Color(0xE8141116)
    val navSurface = Color(0xF00B090C)
    val control = Color(0xF21B151A)
    val controlSelected = Color(0xF04B2938)
    val text = Color(0xFFFFF9FC)
    val muted = Color(0xFFC5BAC0)
    val line = Color(0xC15C3E4D)
    val ink = Color(0xF2080608)
    val rose = Color(0xFFF29AB9)
    val buttonSurface = Color(0xF0633448)
    val cyan = Color(0xFFE68EAC)
    val green = Color(0xFF78D7A8)
    val orange = Color(0xFFF0B77E)
    val red = Color(0xFFFF858D)
    val buttonText = Color(0xFFFFF7FA)
}

data class MagicThemeColors(
    val surface: Color,
    val navSurface: Color,
    val control: Color,
    val controlSelected: Color,
    val buttonSurface: Color,
    val line: Color,
) {
    companion object {
        fun from(palette: BackgroundPalette): MagicThemeColors =
            MagicThemeColors(
                surface = palette.primary.copy(alpha = 0.16f),
                navSurface = palette.base.copy(alpha = 0.94f),
                control = palette.secondary.copy(alpha = 0.2f),
                controlSelected = palette.primary.copy(alpha = 0.68f),
                buttonSurface = palette.primary.copy(alpha = 0.78f),
                line = palette.secondary.copy(alpha = 0.42f),
            )
    }
}

val LocalMagicTheme = staticCompositionLocalOf {
    MagicThemeColors.from(BackgroundPalette.forStyle(BackgroundStyle.Monet))
}
