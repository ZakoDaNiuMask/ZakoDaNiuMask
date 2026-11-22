package org.bakasu.bakasu.ui.screen.themeSettings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import org.bakasu.bakasu.R
import org.bakasu.bakasu.data.theme.StoredTheme
import org.bakasu.bakasu.data.theme.ThemePackageRepository
import org.bakasu.bakasu.ui.component.settings.SegmentedColumn
import org.bakasu.bakasu.ui.component.settings.SettingsBaseWidget
import org.bakasu.bakasu.ui.screen.themeSettings.component.themePackageMessageRes
import org.koin.compose.koinInject

private val IMPORT_MIME_TYPES = arrayOf("application/zip", "application/x-zip-compressed")

/**
 * 本地主题包：导入（SAF）、已导入列表、删除。应用主题与导出当前主题留到 M4。
 */
@Composable
fun ThemePackagesSection() {
    val repository: ThemePackageRepository = koinInject()
    val scope = rememberCoroutineScope()
    var packages by remember { mutableStateOf<List<StoredTheme>>(emptyList()) }
    var pendingDelete by remember { mutableStateOf<StoredTheme?>(null) }
    var errorRes by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        packages = repository.list()
    }

    fun reload() {
        scope.launch { packages = repository.list() }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { repository.importPackage(uri) }
                    .onSuccess { reload() }
                    .onFailure { errorRes = it.themePackageMessageRes() }
            }
        }
    }

    SegmentedColumn(title = stringResource(R.string.theme_package_section_title)) {
        item {
            SettingsBaseWidget(
                icon = Icons.TwoTone.Add,
                title = stringResource(R.string.theme_package_import),
                description = stringResource(R.string.theme_package_import_summary),
                onClick = { importLauncher.launch(IMPORT_MIME_TYPES) },
            )
        }

        if (packages.isEmpty()) {
            item {
                SettingsBaseWidget(
                    title = stringResource(R.string.theme_package_empty),
                    enabled = false,
                )
            }
        } else {
            packages.forEach { theme ->
                item(key = theme.id) {
                    val modeRes = if (theme.manifest.isAdaptive) {
                        R.string.theme_package_mode_adaptive
                    } else {
                        R.string.theme_package_mode_single
                    }
                    SettingsBaseWidget(
                        icon = Icons.TwoTone.Palette,
                        title = theme.manifest.name ?: theme.id,
                        description = theme.manifest.author ?: stringResource(modeRes),
                        trailingContent = {
                            IconButton(onClick = { pendingDelete = theme }) {
                                Icon(
                                    imageVector = Icons.TwoTone.Delete,
                                    contentDescription = stringResource(R.string.theme_package_delete),
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.theme_package_delete_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.theme_package_delete_confirm_message,
                        target.manifest.name ?: target.id,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        scope.launch {
                            repository.delete(target.id)
                            packages = repository.list()
                        }
                    },
                ) {
                    Text(stringResource(R.string.theme_package_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    errorRes?.let { res ->
        AlertDialog(
            onDismissRequest = { errorRes = null },
            title = { Text(stringResource(R.string.theme_package_error_title)) },
            text = { Text(stringResource(res)) },
            confirmButton = {
                TextButton(onClick = { errorRes = null }) {
                    Text(stringResource(R.string.confirm))
                }
            },
        )
    }
}
