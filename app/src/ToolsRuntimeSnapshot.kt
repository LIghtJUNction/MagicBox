package com.github.lightjunction.magicbox

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class ToolsRuntimeSnapshot(
    val module: CliResult,
    val subscription: CliResult,
    val nodes: CliResult,
    val currentNode: CliResult,
    val dns: CliResult,
    val warp: CliResult,
    val health: CliResult,
    val routes: CliResult,
)

suspend fun loadToolsRuntimeSnapshot(): ToolsRuntimeSnapshot =
    coroutineScope {
        val moduleJob = async { loadModuleBridgeStatus() }
        val subJob = async { runMagicNet("sub list") }
        val nodeJob = async { runMagicNet("node list") }
        val currentNodeJob = async { runMagicNet("node current") }
        val dnsJob = async { runMagicNet("dns status") }
        val warpJob = async { runMagicNet("warp status") }
        val healthJob = async { runMagicNet("health") }
        val routeJob = async { runMagicNet("route list") }
        ToolsRuntimeSnapshot(
            module = moduleJob.await(),
            subscription = subJob.await(),
            nodes = nodeJob.await(),
            currentNode = currentNodeJob.await(),
            dns = dnsJob.await(),
            warp = warpJob.await(),
            health = healthJob.await(),
            routes = routeJob.await(),
        )
    }

