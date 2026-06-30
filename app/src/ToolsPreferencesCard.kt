package com.github.lightjunction.magicbox

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ToolsPreferencesCard(
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    backgroundStyle: BackgroundStyle,
    onBackgroundChange: (BackgroundStyle) -> Unit,
) {
    val t = LocalUiText.current
    Card(padding = PaddingValues(10.dp)) {
        Label(t.language)
        Spacer(Modifier.height(8.dp))
        SegmentedControl(AppLanguage.entries, language, { t.languageName(it) }) { onLanguageChange(it) }
    }
    Card(padding = PaddingValues(10.dp)) {
        Label(t.background)
        Spacer(Modifier.height(8.dp))
        SegmentedControl(BackgroundStyle.entries, backgroundStyle, { t.backgroundName(it) }) { onBackgroundChange(it) }
        Spacer(Modifier.height(8.dp))
        Body(t.backgroundHint)
    }
}

