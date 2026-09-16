package com.github.lightjunction.magicbox.reboot

import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class ProxyMode(val title: String) { SYSTEM("系统代理"), TUN("TUN"), EBPF("eBPF") }
enum class Phase { IDLE, PERMISSION, STARTING, RUNNING, STOPPING, ERROR }
data class BoxState(
    val phase: Phase = Phase.IDLE,
    val detail: String = "添加订阅，开始连接。",
    val nodes: List<String> = emptyList(),
    val selected: String = "",
    val mode: ProxyMode = ProxyMode.SYSTEM,
    val authorized: Boolean = false,
) {
    val running: Boolean get() = phase == Phase.RUNNING
    val transitioning: Boolean get() = phase == Phase.STARTING || phase == Phase.STOPPING
}
interface RuntimePort {
    val state: StateFlow<BoxState>
    suspend fun refresh(requestPermission: Boolean = false)
    suspend fun start(mode: ProxyMode)
    suspend fun stop()
    suspend fun importText(text: String)
    suspend fun select(tag: String)
}
class BoxFailure(message: String) : Exception(message)

object InputPolicy {
    const val MAX_BYTES = 8 * 1024 * 1024
    fun validate(text: String): String {
        if (text.isBlank()) throw BoxFailure("请粘贴订阅链接或导入文件。")
        if ('\u0000' in text || text.toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
            throw BoxFailure("订阅必须是 UTF-8 文本，且不能超过 8 MiB。")
        }
        return text.trim().removePrefix("\uFEFF")
    }
    fun quote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"
    fun isReady(schema: Int, ok: Boolean, lifecycle: String, overall: Boolean?): Boolean =
        schema == 1 && ok && lifecycle == "ready" && overall == true
}

/** Pure geometry. No clocks, allocations or random generators in a draw pass. */
object TokenGeometry {
    const val COUNT = 112
    val glyphs = arrayOf("{", "}", "<", ">", "/", "01", "::", "net", "to", "·", "[]", "x")
    fun spread(progress: Float): Float = sin(PI * progress.coerceIn(0f, 1f)).toFloat().coerceAtLeast(0f)
    fun x(index: Int): Float = ((index * 73 + 19) % 997) / 997f
    fun y(index: Int): Float = ((index * 137 + 41) % 991) / 991f
    private val scatterX = FloatArray(COUNT) { cos(it * 2.399963f) * (0.12f + x(it) * 0.3f) }
    private val scatterY = FloatArray(COUNT) { sin(it * 2.399963f) * (0.12f + y(it) * 0.3f) }
    private val tapers = FloatArray(COUNT) { sin(x(it) * PI).toFloat() }
    fun dx(index: Int): Float = scatterX[index]
    fun dy(index: Int): Float = scatterY[index]
    fun taper(index: Int): Float = tapers[index]
}
