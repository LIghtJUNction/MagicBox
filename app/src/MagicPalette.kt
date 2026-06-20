package com.github.lightjunction.magicbox

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

object MagicPalette {
    val background = Color(0xFF0C0A0D)
    val surface = Color(0x1F1A1419)
    val navSurface = Color(0x9C120D12)
    val control = Color(0x6630242C)
    val controlSelected = Color(0xB8623B50)
    val text = Color(0xFFF6EEF1)
    val muted = Color(0xFFC6B7BE)
    val line = Color(0x526F5663)
    val ink = Color(0xB00A080B)
    val rose = Color(0xFFE08AA8)
    val buttonSurface = Color(0xA45F3850)
    val cyan = Color(0xFFD884A0)
    val green = Color(0xFF82C6A8)
    val orange = Color(0xFFE1AE82)
    val red = Color(0xFFFF7B82)
    val buttonText = Color(0xFFF3ECEF)
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
                surface = palette.primary.copy(alpha = 0.13f),
                navSurface = palette.base.copy(alpha = 0.76f),
                control = palette.secondary.copy(alpha = 0.22f),
                controlSelected = palette.primary.copy(alpha = 0.58f),
                buttonSurface = palette.primary.copy(alpha = 0.62f),
                line = palette.secondary.copy(alpha = 0.3f),
            )
    }
}

val LocalMagicTheme = staticCompositionLocalOf {
    MagicThemeColors.from(BackgroundPalette.forStyle(BackgroundStyle.Monet))
}
