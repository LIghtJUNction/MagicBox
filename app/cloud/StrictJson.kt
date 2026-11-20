package com.github.lightjunction.magicbox

import org.json.JSONTokener

/** Android's JSONTokener accepts JavaScript-like input. Validate JSON grammar first. */
internal class StrictJson(private val source: String) {
    private var index = 0
    private fun invalid(): Nothing = throw CloudFailure("需要有效且无重复字段的 JSON 对象。")
    private fun space() { while (index < source.length && source[index] in " \t\r\n") index++ }
    private fun take(value: Char): Boolean {
        if (index < source.length && source[index] == value) { index++; return true }
        return false
    }
    fun objectDocument() {
        space()
        if (index >= source.length || source[index] != '{') invalid()
        value(0); space()
        if (index != source.length) invalid()
    }
    private fun value(depth: Int) {
        if (depth > 32) throw CloudFailure("配置嵌套过深。")
        space()
        if (index >= source.length) invalid()
        when (source[index]) {
            '{' -> {
                index++; space()
                val keys = HashSet<String>()
                if (take('}')) return
                while (true) {
                    space(); val key = string()
                    if (!keys.add(key)) invalid()
                    space(); if (!take(':')) invalid()
                    value(depth + 1); space()
                    if (take('}')) break
                    if (!take(',')) invalid()
                }
            }
            '[' -> {
                index++; space()
                if (take(']')) return
                while (true) {
                    value(depth + 1); space()
                    if (take(']')) break
                    if (!take(',')) invalid()
                }
            }
            '"' -> string()
            't' -> literal("true")
            'f' -> literal("false")
            'n' -> literal("null")
            '-', in '0'..'9' -> number()
            else -> invalid()
        }
    }
    private fun literal(text: String) {
        if (!source.startsWith(text, index)) invalid()
        index += text.length
    }
    private fun number() {
        take('-')
        if (!take('0')) {
            if (index >= source.length || source[index] !in '1'..'9') invalid()
            while (index < source.length && source[index] in '0'..'9') index++
        }
        fun digits() {
            val start = index
            while (index < source.length && source[index] in '0'..'9') index++
            if (index == start) invalid()
        }
        if (take('.')) digits()
        if (take('e') || take('E')) { if (!take('+')) take('-'); digits() }
    }
    private fun string(): String {
        val start = index
        if (!take('"')) invalid()
        while (index < source.length) {
            val character = source[index++]
            if (character == '"') return JSONTokener(source.substring(start, index)).nextValue() as String
            if (character < ' ') invalid()
            if (character == '\\') {
                if (index >= source.length) invalid()
                when (source[index++]) {
                    '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> Unit
                    'u' -> repeat(4) {
                        if (index >= source.length || source[index++] !in "0123456789abcdefABCDEF") invalid()
                    }
                    else -> invalid()
                }
            }
        }
        invalid()
    }
}
