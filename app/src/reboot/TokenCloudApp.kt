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
    CloudColors(Color(0xFFF6F5F0), Color(0xFF202720), Color(0xFF697067), Color(0xFFE3E5DD), Color(0xFFAE482C))
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
    val colors = if (dark) CloudColors(Color(0xFF151A16), Color(0xFFF0F2E9), Color(0xFFA9B0A5), Color(0xFF30382F), Color(0xFFEDA180))
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
            Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                CloudText("{·}", 25, color = colors.accent, mono = true)
                Spacer(Modifier.width(12.dp))
                CloudText("MagicBox", 22, weight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                CloudText(if (BuildConfig.UI_ONLY) "UI / ROOT" else "UNIVERSAL", 10, mono = true, color = colors.muted)
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 28.dp, end = 28.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (page) {
                    0 -> {
                        item {
                            Spacer(Modifier.height(8.dp))
                            CloudText("TOKEN CLOUD / 01", 11, color = colors.muted, mono = true)
                            Spacer(Modifier.height(17.dp))
                            CloudText(if (state.running) "连接，在此\n自然发生。" else "自由连接。\n不必复杂。", 39, weight = FontWeight.Light, lineHeight = 49)
                        }
                        item { TokenSurface(null, Modifier.fillMaxWidth().height(128.dp), reduced, true) {} }
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(if (state.running) colors.accent else colors.muted))
                                Spacer(Modifier.width(9.dp))
                                CloudText(when (state.phase) {
                                    Phase.STARTING -> "正在建立连接"
                                    Phase.STOPPING -> "正在断开"
                                    Phase.RUNNING -> if (state.mode == ProxyMode.SYSTEM) "本地代理已就绪" else "数据面已就绪"
                                    Phase.PERMISSION -> "等待 root 与模块授权"
                                    Phase.ERROR -> "连接尚未就绪"
                                    Phase.IDLE -> "尚未连接"
                                }, 13, color = colors.muted)
                            }
                            Spacer(Modifier.height(12.dp))
                            CloudText(state.detail, 13, color = colors.muted, lineHeight = 21)
                        }
                        item {
                            TokenSurface(
                                if (busy || state.transitioning) "正在处理" else if (state.running) "断开连接  ↗" else if (BuildConfig.UI_ONLY && !state.authorized) "授权并连接模块  ↗" else "建立连接  ↗",
                                Modifier.fillMaxWidth().height(66.dp), reduced, !busy && !state.transitioning,
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
                                    Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp))
                                        .background(if (mode == option) colors.line else Color.Transparent)
                                        .clickable(enabled = available && !state.running && !busy, role = Role.RadioButton) { mode = option }, contentAlignment = Alignment.Center) {
                                        CloudText(option.title, 13, color = if (available) colors.ink else colors.muted.copy(alpha = 0.45f))
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            CloudText(when {
                                BuildConfig.UI_ONLY -> "仅控制已安装的 MagicNet；本包不含代理内核。"
                                mode == ProxyMode.SYSTEM -> "监听 127.0.0.1:2080。需在 Wi-Fi 或应用内手动设置代理，不自动接管全部流量。"
                                mode == ProxyMode.TUN -> "需要 root。与其他透明代理互斥；不会占用系统 VPN 通道。"
                                else -> "需要 root、兼容内核及 eBPF 能力；检测失败不会悄悄切成 TUN。"
                            }, 12, color = colors.muted, lineHeight = 20)
                        }
                        item { Divider(); DetailRow("当前节点", state.selected.ifBlank { "等待导入" }); DetailRow("可用节点", state.nodes.size.toString().padStart(2, '0')) }
                    }
                    1 -> {
                        item {
                            CloudText("你的连接集合", 32, weight = FontWeight.Light)
                            Spacer(Modifier.height(12.dp))
                            CloudText("粘贴 HTTPS 订阅、节点分享链接，或导入本地配置。内容只在设备与订阅源之间传输。", 14, color = colors.muted, lineHeight = 23)
                        }
                        item {
                            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.line.copy(alpha = 0.55f)).padding(20.dp)) {
                                if (input.isEmpty()) CloudText("https://…\n\n或粘贴订阅内容", 14, color = colors.muted, lineHeight = 25)
                                BasicTextField(input, { next ->
                                    if (next.length <= 262144) input = next else message = "较大的订阅请通过文件导入，最大 8 MiB。"
                                }, Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 260.dp), textStyle = TextStyle(color = colors.ink, fontSize = 14.sp, lineHeight = 23.sp))
                            }
                        }
                        item {
                            TokenSurface(if (busy) "正在解析" else "导入这个集合  ↗", Modifier.fillMaxWidth().height(62.dp), reduced, !busy && !state.running) {
                                perform { runtime.importText(input); input = ""; message = "导入完成。" }
                            }
                            Spacer(Modifier.height(8.dp))
                            TextAction("从文件导入", !busy && !state.running) { filePicker.launch(arrayOf("*/*")) }
                            if (state.running) CloudText("请先断开连接再替换订阅，避免正在使用的配置被覆盖。", 12, color = colors.muted)
                        }
                        item {
                            Divider()
                            CloudText("COLLECTION / ${state.nodes.size}", 11, color = colors.muted, mono = true)
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
                        item { CloudText("简单一点。", 36, weight = FontWeight.Light); Spacer(Modifier.height(12.dp)); CloudText("少一点干扰，多一点掌控。", 14, color = colors.muted) }
                        item {
                            DetailRow("版本", BuildConfig.VERSION_NAME)
                            DetailRow("运行方式", if (BuildConfig.UI_ONLY) "MagicNet 模块" else "内置共享内核")
                            Divider()
                            TextAction(if (motionOff) "动态效果 · 已减少" else "动态效果 · Token 云集合") {
                                motionOff = !motionOff
                                preferences.edit().putBoolean("reduced-motion", motionOff).apply()
                            }
                            CloudText("点击分裂为字符，再汇聚固化。横向拖动可打散粒子；松手后归位。静止时不持续刷新。", 13, color = colors.muted, lineHeight = 23)
                            Spacer(Modifier.height(20.dp))
                            TextAction("检查运行状态", !busy) { perform { runtime.refresh(true) } }
                            TextAction("停止代理并恢复网络", !busy && !state.transitioning) { perform { runtime.stop() } }
                            if (!BuildConfig.UI_ONLY) TextAction("打开 Wi-Fi 设置") { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
                            TextAction("查看开源许可") {
                                message = "MagicBox：AGPL-3.0；sing-box 与 Proxylink 的固定版本、源码和许可证随构建产物一起提供。"
                            }
                        }
                        item {
                            Divider()
                            CloudText("没有账户。没有遥测。\n你的订阅，留在你的设备里。", 18, weight = FontWeight.Light, lineHeight = 30)
                            Spacer(Modifier.height(20.dp))
                            CloudText("系统代理并不等于全局代理。root 模式会修改设备路由，首次运行请保留可用的恢复通道。", 12, color = colors.muted, lineHeight = 21)
                        }
                    }
                }
                if (message.isNotBlank()) item { CloudText(message, 14, color = colors.accent, lineHeight = 23); TextAction("关闭提示") { message = "" } }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("连接", "订阅", "设置").forEachIndexed { index, title ->
                    Box(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(18.dp))
                        .background(if (page == index) colors.line else Color.Transparent)
                        .clickable(role = Role.Tab) { page = index }, contentAlignment = Alignment.Center) {
                        CloudText(title, 14, weight = if (page == index) FontWeight.SemiBold else FontWeight.Normal, color = if (page == index) colors.ink else colors.muted)
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
    TokenSurface(text, Modifier.fillMaxWidth().heightIn(min = 48.dp), LocalReducedMotion.current, enabled, plain = true, onClick = onClick)
}
