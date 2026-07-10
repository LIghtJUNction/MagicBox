package com.github.lightjunction.magicbox

import androidx.compose.runtime.staticCompositionLocalOf

const val MAGICNET_CLI = "/data/adb/modules/MagicNet/cli"
const val PREFS_NAME = "magicbox"
const val PREF_LANGUAGE = "language"
const val PREF_BACKGROUND = "background"
const val PREF_TRAFFIC_AUTO_SAMPLE = "traffic_auto_sample"
const val PREF_TRAFFIC_SAMPLE_INTERVAL = "traffic_sample_interval"
const val PREF_TRAFFIC_ALERT_THRESHOLD = "traffic_alert_threshold"
const val ASCII_GLYPHS = ".:;,+*~^/\\|_-<>[]{}()01"
const val MAGICBOX_RELEASES_URL = "https://github.com/LIghtJUNction/MagicBox/releases"
const val MAGICBOX_LATEST_RELEASE_API =
    "https://api.github.com/repos/LIghtJUNction/MagicBox/releases/latest"

val RECOMMENDED_BYPASS_PACKAGES =
    listOf(
        "com.eg.android.AlipayGphone",
        "com.tencent.mm",
        "com.unionpay",
        "com.taobao.taobao",
        "com.tmall.wireless",
        "com.jingdong.app.mall",
        "com.xunmeng.pinduoduo",
        "com.sankuai.meituan",
        "com.autonavi.minimap",
        "com.baidu.BaiduMap",
        "com.MobileTicket",
        "com.tencent.mobileqq",
        "tv.danmaku.bili",
        "com.ss.android.ugc.aweme",
        "com.netease.cloudmusic",
        "com.sina.weibo",
        "com.zhihu.android",
        "com.android.vending",
        "com.google.android.gms",
        "com.google.android.gsf",
    )

val LocalUiText = staticCompositionLocalOf { UiText.en }
val SU_CANDIDATES =
    listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/debug_ramdisk/su",
        "/sbin/su",
        "su",
    )
