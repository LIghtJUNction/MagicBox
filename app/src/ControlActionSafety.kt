package com.github.lightjunction.magicbox

private val CONTROL_ACTIONS_REQUIRING_CONFIRMATION =
    setOf(
        "service start",
        "service ensure",
        "service restart sing-box",
        "service stop",
        "transparent set proxy",
        "transparent set external-tun",
        "transparent set hybrid",
        "transparent set tun",
        "config apply",
        "api close-all",
    )

fun controlActionRequiresConfirmation(command: String): Boolean = command in CONTROL_ACTIONS_REQUIRING_CONFIRMATION

