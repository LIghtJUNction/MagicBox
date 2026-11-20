package com.github.lightjunction.magicbox.reboot

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.github.lightjunction.magicbox.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

internal data class CloudColors(val paper: Color, val ink: Color, val muted: Color, val line: Color, val accent: Color)
internal val LocalCloud = staticCompositionLocalOf {
    CloudColors(Color.White, Color(0xFF202124), Color(0xFF5F6368), Color(0xFFE8EAED), Color(0xFF0B57D0))
}

@Composable
fun TokenCloudApp() {
    val context = LocalContext.current
    val runtime = remember { PlatformRuntime.get(context.applicationContext) }
    val state by runtime.state.collectAsState()
    val preferences = remember { context.getSharedPreferences("token-cloud", Context.MODE_PRIVATE) }
    var page by remember { mutableIntStateOf(0) }
    var mode by remember { mutableStateOf(if (BuildConfig.UI_ONLY) ProxyMode.TUN else ProxyMode.SYSTEM) }
    var motionOff by remember { mutableStateOf(preferences.getBoolean("reduced-motion", false)) }
    var message by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var foreground by remember { mutableStateOf(true) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> foreground = true
                Lifecycle.Event.ON_STOP -> foreground = false
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val systemMotionOff = !android.animation.ValueAnimator.areAnimatorsEnabled() ||
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode
    val reduced = motionOff || systemMotionOff || !foreground
    val dark = isSystemInDarkTheme()
    val colors = if (dark) CloudColors(Color(0xFF121316), Color(0xFFE8EAED), Color(0xFF9AA0A6), Color(0xFF303134), Color(0xFFA8C7FA))
        else LocalCloud.current
    fun perform(operation: suspend () -> Unit) {
        if (busy || state.transitioning) return
        busy = true
        message = ""
        scope.launch {
            try { operation() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: BoxFailure) { message = error.message ?: "操作没有完成。" }
            catch (_: Exception) { message = "操作没有完成。请检查网络、授权或订阅格式；原订阅不会被清空。" }
            finally { busy = false }
        }
    }
    LaunchedEffect(foreground) {
        if (foreground) {
            while (true) {
                if (!busy) runCatching { runtime.refresh(false) }
                delay(4000)
            }
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) perform {
            val content = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.let(EngineIO::readBounded)
                    ?: throw BoxFailure("无法读取这个文件。")
            }
            runtime.importText(content)
            message = "导入完成。"
        }
    }
    BackHandler(page != 0) { page = 0 }
    CompositionLocalProvider(LocalCloud provides colors, LocalReducedMotion provides reduced) {
        Column(Modifier.fillMaxSize().background(colors.paper).safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 24.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TokenSurface(null, Modifier.size(48.dp), reduced, true) {}
                CloudText("MagicBox", 20, weight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                CloudText(if (BuildConfig.UI_ONLY) "UI 版" else "通用版", 12, color = colors.muted)
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                when (page) {
                    0 -> {
                        item {
                            CloudText(when (state.phase) {
                                Phase.STARTING -> "正在连接"
                                Phase.STOPPING -> "正在断开"
                                Phase.RUNNING -> if (state.mode == ProxyMode.SYSTEM) "本地代理已就绪" else "已连接"
                                Phase.PERMISSION -> "等待授权"
                                Phase.ERROR -> "连接失败"
                                Phase.IDLE -> "未连接"
                            }, 26, weight = FontWeight.Medium)
                            Spacer(Modifier.height(12.dp))
                            CloudText(state.detail, 14, color = colors.muted, lineHeight = 22)
                        }
                        item {
                            DetailRow("当前节点", state.selected.ifBlank { "未选择" })
                            DetailRow("可用节点", "${state.nodes.size} 个")
                        }
                        item {
                            TokenSurface(
                                if (busy || state.transitioning) "处理中" else if (state.running) "断开连接" else if (BuildConfig.UI_ONLY && !state.authorized) "授权模块" else "连接",
                                Modifier.fillMaxWidth().height(52.dp), reduced, !busy && !state.transitioning,
                            ) {
                                perform {
                                    if (state.running) runtime.stop()
                                    else if (BuildConfig.UI_ONLY && !state.authorized) runtime.refresh(true)
                                    else runtime.start(mode)
                                }
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ProxyMode.entries.forEach { option ->
                                    val available = !BuildConfig.UI_ONLY || option != ProxyMode.SYSTEM
                                    Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                                        .background(if (mode == option) colors.line.copy(alpha = 0.6f) else Color.Transparent)
                                        .semantics { selected = mode == option }
                                        .clickable(enabled = available && !state.running && !busy, role = Role.RadioButton) { mode = option }, contentAlignment = Alignment.Center) {
                                        CloudText(option.title, 13, color = if (available) if (mode == option) colors.accent else colors.muted else colors.muted.copy(alpha = 0.45f))
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            CloudText(when {
                                BuildConfig.UI_ONLY -> "需要 root 和已启用的 MagicNet 模块。"
                                mode == ProxyMode.SYSTEM -> "127.0.0.1:2080 · 需在 Wi-Fi 或应用中手动设置代理。"
                                mode == ProxyMode.TUN -> "需要 root；请先停止其他透明代理。"
                                else -> "需要 root 和兼容的 eBPF 内核；检测失败时停止连接。"
                            }, 12, color = colors.muted, lineHeight = 20)
                        }
                    }
                    1 -> {
                        item {
                            CloudText("订阅", 24, weight = FontWeight.Medium)
                            Spacer(Modifier.height(8.dp))
                            CloudText("粘贴订阅链接或内容，也可以导入文件。", 14, color = colors.muted, lineHeight = 22)
                        }
                        item {
                            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.line.copy(alpha = 0.45f)).padding(16.dp)) {
                                if (input.isEmpty()) CloudText("https://…", 14, color = colors.muted, lineHeight = 23)
                                BasicTextField(input, { next ->
                                    if (next.length <= 262144) input = next else message = "较大的订阅请通过文件导入，最大 8 MiB。"
                                }, Modifier.fillMaxWidth().heightIn(min = 128.dp, max = 260.dp), textStyle = TextStyle(color = colors.ink, fontSize = 14.sp, lineHeight = 23.sp))
                            }
                        }
                        item {
                            TokenSurface(if (busy) "正在解析" else "导入", Modifier.fillMaxWidth().height(52.dp), reduced, !busy && !state.running) {
                                perform { runtime.importText(input); input = ""; message = "导入完成。" }
                            }
                            Spacer(Modifier.height(8.dp))
                            TextAction("从文件导入", !busy && !state.running) { filePicker.launch(arrayOf("*/*")) }
                            if (state.running) CloudText("断开连接后可更换订阅。", 12, color = colors.muted)
                        }
                        item {
                            Divider()
                            CloudText("节点 · ${state.nodes.size}", 13, color = colors.muted)
                        }
                        items(state.nodes, key = { it }) { tag ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).clickable(enabled = !busy && !state.running) { perform { runtime.select(tag) } }, verticalAlignment = Alignment.CenterVertically) {
                                CloudText(if (tag == state.selected) "●" else "○", 14, color = colors.accent)
                                Spacer(Modifier.width(16.dp))
                                BasicText(tag, Modifier.weight(1f), style = TextStyle(color = colors.ink, fontSize = 15.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (state.nodes.isEmpty()) item { CloudText("导入订阅后，节点会出现在这里。", 13, color = colors.muted, lineHeight = 22) }
                    }
                    else -> {
                        item { CloudText("设置", 24, weight = FontWeight.Medium) }
                        item {
                            DetailRow("版本", BuildConfig.VERSION_NAME)
                            DetailRow("运行方式", if (BuildConfig.UI_ONLY) "MagicNet 模块" else "内置内核")
                            Divider()
                            TextAction(if (motionOff) "动态效果：减少" else "动态效果：开启") {
                                motionOff = !motionOff
                                preferences.edit().putBoolean("reduced-motion", motionOff).apply()
                            }
                            Spacer(Modifier.height(16.dp))
                            TextAction("检查运行状态", !busy) { perform { runtime.refresh(true) } }
                            TextAction("停止代理并恢复网络", !busy && !state.transitioning) { perform { runtime.stop() } }
                            if (!BuildConfig.UI_ONLY) TextAction("打开 Wi-Fi 设置") { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
                            TextAction("查看开源许可") {
                                message = "MagicBox：AGPL-3.0；sing-box 与 Proxylink 的固定版本、源码和许可证随构建产物一起提供。"
                            }
                        }
                    }
                }
                if (message.isNotBlank()) item { CloudText(message, 14, color = colors.accent, lineHeight = 23); TextAction("关闭提示") { message = "" } }
            }
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("连接", "订阅", "设置").forEachIndexed { index, title ->
                    Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp))
                        .semantics { contentDescription = "${title}页面"; selected = page == index }
                        .clickable(role = Role.Tab) { page = index }, contentAlignment = Alignment.Center) {
                        CloudText(title, 14, weight = if (page == index) FontWeight.Medium else FontWeight.Normal, color = if (page == index) colors.accent else colors.muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudText(text: String, size: Int, color: Color = LocalCloud.current.ink, weight: FontWeight = FontWeight.Normal, lineHeight: Int = size + 8, mono: Boolean = false) {
    BasicText(text, style = TextStyle(color = color, fontSize = size.sp, fontWeight = weight, lineHeight = lineHeight.sp, fontFamily = if (mono) FontFamily.Monospace else FontFamily.SansSerif))
}
@Composable private fun Divider() { Spacer(Modifier.fillMaxWidth().height(1.dp).background(LocalCloud.current.line)); Spacer(Modifier.height(16.dp)) }
@Composable private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        CloudText(label, 13, color = LocalCloud.current.muted)
        Spacer(Modifier.width(20.dp))
        BasicText(value, Modifier.weight(1f), style = TextStyle(color = LocalCloud.current.ink, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
@Composable private fun TextAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.CenterStart) {
        CloudText(text, 14, color = if (enabled) LocalCloud.current.accent else LocalCloud.current.muted)
    }
}
