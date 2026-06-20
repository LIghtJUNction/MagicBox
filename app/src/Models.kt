package com.github.lightjunction.magicbox

import androidx.compose.ui.graphics.Color
import java.util.Locale

enum class MagicPage(val label: String) {
    Stats("MagicNet"),
    Rules("Rule"),
    Apps("App"),
    Tools("Tool"),
}

enum class AppLanguage(val code: String) {
    English("en"),
    Chinese("zh");

    companion object {
        fun fromCode(code: String): AppLanguage =
            entries.firstOrNull { it.code == code } ?: systemDefault()

        fun systemDefault(): AppLanguage =
            if (Locale.getDefault().language.equals("zh", ignoreCase = true)) Chinese else English
    }
}

enum class BackgroundStyle(val code: String) {
    Monet("monet"),
    Ember("ember"),
    Aurora("aurora"),
    Minimal("minimal");

    companion object {
        fun fromCode(code: String): BackgroundStyle =
            entries.firstOrNull { it.code == code } ?: Monet
    }
}

enum class RuleBucket(val label: String, val cli: String) {
    Proxy("Proxy", "proxy"),
    Direct("Direct", "direct"),
    Block("Block", "block"),
}

enum class AppTarget(val label: String, val cli: String) {
    Proxy("Proxy", "proxy"),
    Bypass("Bypass", "bypass"),
}

data class LiveStats(
    val up: Float,
    val down: Float,
) {
    val total: Float = up + down
}

data class CliResult(
    val success: Boolean,
    val command: String,
    val output: String,
    val summary: String =
        when {
            output.isBlank() && success -> "Command completed with empty output."
            output.isBlank() -> "Command failed with empty output."
            else -> output.lineSequence().firstOrNull().orEmpty()
        },
) {
}

data class RouteSummary(
    val proxy: List<String>,
    val direct: List<String>,
    val block: List<String>,
) {
    val total: Int = proxy.size + direct.size + block.size

    fun forBucket(bucket: RuleBucket): List<String> =
        when (bucket) {
            RuleBucket.Proxy -> proxy
            RuleBucket.Direct -> direct
            RuleBucket.Block -> block
        }
}

data class RuntimeRuleSummary(
    val ruleSets: List<String>,
) {
    val total: Int = ruleSets.size
}

data class AppSummary(
    val mode: String,
    val proxy: List<String>,
    val bypass: List<String>,
)

data class HealthEntry(
    val severity: HealthSeverity,
    val title: String,
    val details: String,
)

data class ModuleBridge(
    val version: String,
    val cli: String,
    val enabled: String,
    val bind: String,
    val port: String,
    val secretSet: String,
)

data class BackgroundPalette(
    val base: Color,
    val primary: Color,
    val secondary: Color,
) {
    companion object {
        fun forStyle(style: BackgroundStyle): BackgroundPalette =
            when (style) {
                BackgroundStyle.Monet -> BackgroundPalette(Color(0xFF08080E), Color(0xFF7B5FC8), Color(0xFFB86C86))
                BackgroundStyle.Ember -> BackgroundPalette(Color(0xFF080814), Color(0xFFE16A47), Color(0xFF7357D8))
                BackgroundStyle.Aurora -> BackgroundPalette(Color(0xFF071018), Color(0xFF3E7CFF), Color(0xFFB86C86))
                BackgroundStyle.Minimal -> BackgroundPalette(Color(0xFF0B0A0D), Color(0xFF3A3238), Color(0xFF5B5258))
            }
    }
}

enum class HealthSeverity(val color: Color) {
    Ok(MagicPalette.green),
    Warn(MagicPalette.orange),
    Error(MagicPalette.red),
}

data class UpdateState(
    val title: String,
    val summary: String,
    val checking: Boolean,
)
