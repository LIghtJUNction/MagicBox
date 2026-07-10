package com.github.lightjunction.magicbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CommandBlock(title: String, result: CliResult?, showOutput: Boolean = false) {
    val t = LocalUiText.current
    val summary =
        when {
            result == null -> t.notRunYet
            result.output.isBlank() && result.success -> t.commandCompletedEmpty
            result.output.isBlank() -> t.commandFailedEmpty
            else -> result.summary
        }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Label(title)
                Body(summary)
            }
            StatusPill(
                when (result?.success) {
                    true -> t.ok
                    false -> t.fail
                    null -> t.idle
                },
            )
        }
        val output = result?.output.orEmpty().trim()
        if (output.isNotBlank() && output != result?.summary && (showOutput || result?.success == false)) {
            Spacer(Modifier.height(8.dp))
            Mono(output.take(900))
        }
    }
}

@Composable
fun ActionCard(
    title: String,
    summary: String,
    action: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(enabled = enabled, onClick = onClick),
        padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = title,
                    style =
                        TextStyle(
                            color = MagicPalette.text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                )
                Body(summary)
            }
            Spacer(Modifier.width(8.dp))
            StatusPill(action)
        }
    }
}

@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val colors = LocalMagicTheme.current
    val pill = RoundedCornerShape(999.dp)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(pill)
                .background(
                    Brush.linearGradient(
                        listOf(colors.line.copy(alpha = 0.72f), colors.controlSelected.copy(alpha = 0.48f)),
                    ),
                )
                .padding(2.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(pill)
                    .background(colors.navSurface)
                    .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { option ->
                val active = option == selected
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(pill)
                            .then(
                                if (active) {
                                    Modifier.background(
                                        Brush.linearGradient(
                                            listOf(colors.controlSelected, colors.buttonSurface),
                                        ),
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .clickable { onSelect(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = label(option),
                        style =
                            TextStyle(
                                color = if (active) MagicPalette.text else MagicPalette.muted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun SmallButton(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalMagicTheme.current
    val fill =
        if (enabled) {
            Brush.linearGradient(
                listOf(colors.buttonSurface, colors.controlSelected),
            )
        } else {
            Brush.linearGradient(
                listOf(colors.control, colors.control.copy(alpha = 0.82f)),
            )
        }
    Box(
        modifier =
            modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(fill)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style =
                TextStyle(
                    color = if (enabled) MagicPalette.buttonText else MagicPalette.muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun TextInput(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMagicTheme.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = MagicPalette.text, fontSize = 15.sp),
        modifier =
            modifier
                .height(52.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(colors.line.copy(alpha = 0.74f), colors.controlSelected.copy(alpha = 0.4f)),
                    ),
                )
                .padding(2.dp),
        decorationBox = { inner ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.control)
                        .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isBlank()) {
                    BasicText(
                        placeholder,
                        style = TextStyle(color = MagicPalette.muted, fontSize = 15.sp),
                    )
                }
                inner()
            }
        },
    )
}

@Composable
fun MultiLineTextInput(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMagicTheme.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(color = MagicPalette.text, fontSize = 14.sp, lineHeight = 19.sp),
        modifier =
            modifier
                .height(112.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        listOf(colors.line.copy(alpha = 0.74f), colors.controlSelected.copy(alpha = 0.4f)),
                    ),
                )
                .padding(2.dp),
        decorationBox = { inner ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.control)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                if (value.isBlank()) {
                    BasicText(
                        placeholder,
                        style =
                            TextStyle(
                                color = MagicPalette.muted,
                                fontSize = 14.sp,
                                lineHeight = 19.sp,
                            ),
                    )
                }
                inner()
            }
        },
    )
}

@Composable
fun Value(text: String) {
    BasicText(
        text = text,
        style = TextStyle(color = MagicPalette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun Label(text: String) {
    BasicText(
        text = text,
        style =
            TextStyle(
                color = MagicPalette.muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            ),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun Body(text: String) {
    BasicText(
        text = text,
        style = TextStyle(color = MagicPalette.muted, fontSize = 13.sp, lineHeight = 18.sp),
    )
}

@Composable
fun Mono(text: String) {
    BasicText(
        text = text,
        style = TextStyle(color = MagicPalette.text, fontSize = 11.sp, lineHeight = 15.sp),
    )
}

@Composable
fun StatusPill(text: String, enabled: Boolean = true, onClick: (() -> Unit)? = null) {
    val colors = LocalMagicTheme.current
    val pill = RoundedCornerShape(999.dp)
    val fill =
        if (enabled) {
            Brush.linearGradient(
                listOf(colors.controlSelected, colors.buttonSurface.copy(alpha = 0.84f)),
            )
        } else {
            Brush.linearGradient(
                listOf(colors.control, colors.control.copy(alpha = 0.78f)),
            )
        }
    val interaction =
        if (onClick != null) {
            Modifier
                .heightIn(min = 48.dp)
                .clickable(enabled = enabled, onClick = onClick)
        } else {
            Modifier.heightIn(min = 30.dp)
        }
    Box(
        modifier =
            Modifier
                .clip(pill)
                .background(fill)
                .then(interaction)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style =
                TextStyle(
                    color = if (enabled) MagicPalette.text else MagicPalette.muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalMagicTheme.current
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            colors.line.copy(alpha = 0.76f),
                            colors.controlSelected.copy(alpha = 0.42f),
                            colors.navSurface.copy(alpha = 0.9f),
                        ),
                    ),
                )
                .padding(2.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(colors.surface, MagicPalette.ink.copy(alpha = 0.82f)),
                        ),
                    )
                    .padding(padding),
            content = content,
        )
    }
}

@Composable
fun BottomNavigation(
    selectedPage: MagicPage,
    onSelect: (MagicPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalUiText.current
    val colors = LocalMagicTheme.current
    val pill = RoundedCornerShape(999.dp)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(pill)
                .background(
                    Brush.linearGradient(
                        listOf(colors.line.copy(alpha = 0.86f), colors.controlSelected.copy(alpha = 0.5f)),
                    ),
                )
                .padding(2.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(pill)
                    .background(colors.navSurface)
                    .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MagicPage.entries.forEach { page ->
                val selected = page == selectedPage
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(pill)
                            .then(
                                if (selected) {
                                    Modifier.background(
                                        Brush.linearGradient(
                                            listOf(colors.controlSelected, colors.buttonSurface),
                                        ),
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .clickable { onSelect(page) },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = t.pageLabel(page),
                        style =
                            TextStyle(
                                color = if (selected) MagicPalette.text else MagicPalette.muted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
