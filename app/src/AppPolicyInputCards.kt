package com.github.lightjunction.magicbox

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppPolicyInputCard(
    target: AppTarget,
    packageName: String,
    loading: Boolean,
    onPackageNameChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val t = LocalUiText.current
    Card(padding = PaddingValues(10.dp)) {
        Label(t.addPackage(t.appTarget(target).lowercase()))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextInput(packageName, t.packagePlaceholder, onPackageNameChange, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            SmallButton(t.add, enabled = !loading, onClick = onAdd)
        }
    }
}

@Composable
fun PackageBatchInputCard(
    draft: String,
    loading: Boolean,
    onDraftChange: (String) -> Unit,
    onImport: () -> Unit,
) {
    val t = LocalUiText.current
    Card(padding = PaddingValues(10.dp)) {
        Label(t.bulkPackages())
        Spacer(Modifier.height(8.dp))
        TextInput(draft, t.bulkPackagePlaceholder(), onDraftChange, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        SmallButton(t.importPackages(), enabled = !loading, modifier = Modifier.fillMaxWidth(), onClick = onImport)
    }
}
