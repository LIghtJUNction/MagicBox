package com.github.lightjunction.magicbox

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@Composable
fun UpdateCheckCard() {
    val t = LocalUiText.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember {
        mutableStateOf(UpdateState(t.version(BuildConfig.VERSION_NAME), t.checkGithubReleases, false))
    }
    ActionCard(
        title = state.title,
        summary = state.summary,
        action = if (state.checking) t.checking else t.check,
        enabled = !state.checking,
        onClick = {
            state = UpdateState(t.checkingUpdates, t.contactingGithub, true)
            scope.launch { state = checkForUpdates(t) }
        },
    )
    ActionCard(
        title = t.githubReleases,
        summary = t.openApkPage,
        action = t.open,
        onClick = { openUri(context, MAGICBOX_RELEASES_URL) },
    )
}

