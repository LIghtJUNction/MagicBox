package com.github.lightjunction.magicbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MagicBoxApp()
        }
    }
}

@Composable
private fun MagicBoxApp() {
    var selectedPage by remember { mutableStateOf(MagicPage.Stats) }
    var boxOpen by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MagicPalette.background)
                .statusBarsPadding()
                .navigationBarsPadding()
    ) {
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
        ) {
            when (selectedPage) {
                MagicPage.Stats -> StatsPage(boxOpen = boxOpen, onToggle = { boxOpen = !boxOpen })
                MagicPage.Rules -> RulesPage()
                MagicPage.Issues -> IssuesPage()
                MagicPage.Module -> ModulePage()
            }
        }

        BottomNavigation(
            selectedPage = selectedPage,
            onSelect = { selectedPage = it },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun StatsPage(boxOpen: Boolean, onToggle: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(12.dp))
        HeaderBlock()
        MagicStatusCard(boxOpen = boxOpen, onToggle = onToggle)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(title = "Mode", value = "auto", modifier = Modifier.weight(1f))
            StatCard(title = "Core", value = "sing", modifier = Modifier.weight(1f))
        }
        SectionTitle("Statistics")
        ToolPreviewCard(title = "Traffic", summary = "Frontend target: MagicNet cli api stats")
        ToolPreviewCard(title = "Health", summary = "Frontend target: cli health, ebpf status, transparent status")
        ToolPreviewCard(title = "Support Snapshot", summary = "Frontend target: cli support bundle with redaction")
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun RulesPage() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(18.dp))
        PageTitle("Rules")
        ToolPreviewCard(title = "Proxy Domains", summary = "Maps to cli route add-domain proxy <domain>")
        ToolPreviewCard(title = "Direct Domains", summary = "Maps to cli route add-domain direct <domain>")
        ToolPreviewCard(title = "Block Domains", summary = "Maps to cli route add-domain block <domain>")
        ToolPreviewCard(title = "Apply", summary = "Maps to cli route apply after reviewing changes")
    }
}

@Composable
private fun IssuesPage() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(18.dp))
        PageTitle("Diff Issues")
        Card {
            BasicText(
                text = "Create change proposals from local edits.",
                style = TextStyle(color = MagicPalette.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(8.dp))
            BasicText(
                text = "This screen is for config/rule/blocklist diffs, GitHub issue body generation, and support context collection.",
                style = TextStyle(color = MagicPalette.muted, fontSize = 14.sp, lineHeight = 20.sp),
            )
        }
        ToolPreviewCard(title = "Config Diff Issue", summary = "Reference: MagicNet WebUI ConfigPage issue flow")
        ToolPreviewCard(title = "Blocklist Issue", summary = "Reference: MagicNet block diff and issue body")
        ToolPreviewCard(title = "Diagnostics Issue", summary = "Collect module prop, status, health, MCP, network, support bundle")
    }
}

@Composable
private fun ModulePage() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(18.dp))
        PageTitle("Module")
        Card {
            BasicText(
                text = "MagicBox is the app frontend.",
                style = TextStyle(color = MagicPalette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(8.dp))
            BasicText(
                text = "Backend behavior belongs to the MagicNet Magisk module under /data/adb/modules/MagicNet.",
                style = TextStyle(color = MagicPalette.muted, fontSize = 14.sp, lineHeight = 20.sp),
            )
        }
        ToolPreviewCard(title = "Service", summary = "Targets: cli service status/start/stop/restart")
        ToolPreviewCard(title = "MCP", summary = "Targets: cli mcp status/enable/disable/secret")
        Card {
            BasicText(
                text = "Attribution",
                style = TextStyle(color = MagicPalette.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(8.dp))
            BasicText(
                text = "Original project: YumeBox by YumeYucca. MagicBox keeps project attribution and AGPL context in the repository documentation.",
                style = TextStyle(color = MagicPalette.muted, fontSize = 14.sp, lineHeight = 20.sp),
            )
        }
    }
}

@Composable
private fun HeaderBlock() {
    Column {
        BasicText(
            text = "MagicBox",
            style = TextStyle(color = MagicPalette.text, fontSize = 34.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(6.dp))
        BasicText(
            text = "Frontend for MagicNet statistics, rules, and diff issues.",
            style = TextStyle(color = MagicPalette.muted, fontSize = 15.sp),
        )
    }
}

@Composable
private fun MagicStatusCard(boxOpen: Boolean, onToggle: () -> Unit) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
        padding = PaddingValues(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MagicMark(active = boxOpen)
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = if (boxOpen) "Box open" else "Box idle",
                    style = TextStyle(color = MagicPalette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.height(6.dp))
                BasicText(
                    text = if (boxOpen) "Local UI state only" else "Tap to preview interaction",
                    style = TextStyle(color = MagicPalette.muted, fontSize = 14.sp),
                )
            }
            StatusPill(if (boxOpen) "READY" else "IDLE")
        }
    }
}

@Composable
private fun MagicMark(active: Boolean) {
    val accent = if (active) MagicPalette.green else MagicPalette.blue
    Box(
        modifier =
            Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(accent, MagicPalette.gold)))
                .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(42.dp)) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.92f),
                size = Size(size.width, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
            )
            drawLine(
                color = Color.White.copy(alpha = 0.92f),
                start = Offset(size.width * 0.18f, size.height * 0.36f),
                end = Offset(size.width * 0.82f, size.height * 0.36f),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = Color.White,
                radius = 4.dp.toPx(),
                center = Offset(size.width * 0.68f, size.height * 0.68f),
            )
        }
    }
}

@Composable
private fun StatusPill(text: String) {
    Box(
        modifier =
            Modifier
                .clip(CircleShape)
                .background(MagicPalette.text.copy(alpha = 0.08f))
                .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        BasicText(
            text = text,
            style = TextStyle(color = MagicPalette.text, fontSize = 12.sp, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        BasicText(text = title, style = TextStyle(color = MagicPalette.muted, fontSize = 13.sp))
        Spacer(Modifier.height(10.dp))
        BasicText(
            text = value,
            style = TextStyle(color = MagicPalette.text, fontSize = 28.sp, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun ToolPreviewCard(title: String, summary: String) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(modifier = Modifier.size(44.dp)) {
                drawCircle(color = MagicPalette.blue.copy(alpha = 0.16f), radius = size.minDimension / 2)
                drawCircle(
                    color = MagicPalette.blue,
                    radius = 5.dp.toPx(),
                    center = Offset(size.width * 0.5f, size.height * 0.5f),
                )
                drawCircle(
                    color = MagicPalette.blue.copy(alpha = 0.55f),
                    radius = size.minDimension / 2.8f,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 7f)),
                    ),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                BasicText(
                    text = title,
                    style = TextStyle(color = MagicPalette.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                )
                Spacer(Modifier.height(4.dp))
                BasicText(text = summary, style = TextStyle(color = MagicPalette.muted, fontSize = 13.sp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    BasicText(
        text = text,
        style = TextStyle(color = MagicPalette.text, fontSize = 19.sp, fontWeight = FontWeight.Bold),
    )
}

@Composable
private fun PageTitle(text: String) {
    BasicText(
        text = text,
        style = TextStyle(color = MagicPalette.text, fontSize = 30.sp, fontWeight = FontWeight.Bold),
    )
}

@Composable
private fun Card(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(26.dp))
                .background(MagicPalette.surface)
                .border(1.dp, MagicPalette.line, RoundedCornerShape(26.dp))
                .padding(padding),
        content = content,
    )
}

@Composable
private fun BottomNavigation(
    selectedPage: MagicPage,
    onSelect: (MagicPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MagicPalette.surface)
                .border(1.dp, MagicPalette.line, RoundedCornerShape(28.dp))
                .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MagicPage.entries.forEach { page ->
            val selected = page == selectedPage
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (selected) MagicPalette.text else Color.Transparent)
                        .clickable { onSelect(page) },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = page.label,
                    style =
                        TextStyle(
                            color = if (selected) Color.White else MagicPalette.muted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        ),
                )
            }
        }
    }
}

private enum class MagicPage(val label: String) {
    Stats("Stats"),
    Rules("Rules"),
    Issues("Issues"),
    Module("Module"),
}

private object MagicPalette {
    val background = Color(0xFFF7F4EF)
    val surface = Color(0xFFFFFFFF)
    val text = Color(0xFF202329)
    val muted = Color(0xFF6E7480)
    val line = Color(0xFFE5DED4)
    val blue = Color(0xFF3A67E8)
    val green = Color(0xFF2EA872)
    val gold = Color(0xFFE2B451)
}
