package com.github.lightjunction.magicbox

private val CONTROL_ACTIONS_REQUIRING_CONFIRMATION =
    setOf(
        "service start",
        "service ensure",
        "service restart sing-box",
        "service stop",
        "transparent apply",
        "config apply",
        "api close-all",
    )

fun controlActionRequiresConfirmation(command: String): Boolean = command in CONTROL_ACTIONS_REQUIRING_CONFIRMATION
