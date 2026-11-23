package com.zakodaniumask.manager.ui.screen.themeSettings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.FileDownload
import androidx.compose.material.icons.twotone.Palette
import androidx.compose.material.icons.twotone.Refresh
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.data.theme.StoredTheme
import com.zakodaniumask.manager.data.theme.ThemePackageRepository
import com.zakodaniumask.manager.ui.component.settings.SegmentedColumn
import com.zakodaniumask.manager.ui.component.settings.SettingsBaseWidget
import com.zakodaniumask.manager.ui.screen.themeSettings.component.themePackageMessageRes
import com.zakodaniumask.manager.ui.theme.ThemeConfig
import com.zakodaniumask.manager.ui.theme.ThemePackageApplier
import org.koin.compose.koinInject

private val IMPORT_MIME_TYPES = arrayOf(
    "application/zip",
    "application/x-zip-compressed",
    "application/x-sukisu-theme",
    "application/octet-stream",
    "*/*",
)
private const val EXPORT_FILE_NAME = "ZakoDaNiuMask-theme.zip"

/**
 * 本地主题包：导入（SAF）、应用、导出当前主题、列表与删除、恢复默认外观。
 */
@Composable
fun ThemePackagesSection() {
    val repository: ThemePackageRepository = koinInject()
    val applier: ThemePackageApplier = koinInject()
    val themeConfig: ThemeConfig = koinInject()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val darkMode = themeConfig.forceDarkMode ?: isSystemInDarkTheme()
    var packages by remember { mutableStateOf<List<StoredTheme>>(emptyList()) }
    var pendingDelete by remember { mutableStateOf<StoredTheme?>(null) }
    var pendingReset by remember { mutableStateOf(false) }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        applier.restoreApplied()
        packages = repository.list()
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { repository.importPackage(uri) }
                    .onSuccess { stored ->
                        packages = repository.list()
                        if (!applier.apply(stored.id, darkMode)) {
                            errorRes = R.string.theme_package_apply_failed
                        }
                    }
                    .onFailure { errorRes = it.themePackageMessageRes() }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                val ok = applier.exportCurrent(uri, dark = darkMode, name = EXPORT_FILE_NAME)
                busy = false
                Toast.makeText(
                    context,
                    context.getString(if (ok) R.string.theme_package_export_success else R.string.theme_package_export_failed),
                    Toast.LENGTH_SHORT,
                ).show()
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
        item {
            SettingsBaseWidget(
                icon = Icons.TwoTone.FileDownload,
                title = stringResource(R.string.theme_package_export),
                description = stringResource(R.string.theme_package_export_summary),
                enabled = !busy,
                onClick = { exportLauncher.launch(EXPORT_FILE_NAME) },
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
                    val selected = theme.id == themeConfig.appliedThemeId
                    SettingsBaseWidget(
                        icon = Icons.TwoTone.Palette,
                        title = theme.manifest.name ?: theme.id,
                        description = if (selected) {
                            stringResource(R.string.theme_package_current)
                        } else {
                            stringResource(
                                if (theme.manifest.isAdaptive) {
                                    R.string.theme_package_mode_adaptive
                                } else {
                                    R.string.theme_package_mode_single
                                },
                            )
                        },
                        selected = selected,
                        onClick = {
                            scope.launch {
                                val ok = applier.apply(theme.id, dark = darkMode)
                                if (!ok) errorRes = R.string.theme_package_apply_failed
                            }
                        },
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

        item {
            SettingsBaseWidget(
                icon = Icons.TwoTone.Refresh,
                title = stringResource(R.string.theme_package_reset),
                description = stringResource(R.string.theme_package_reset_summary),
                onClick = { pendingReset = true },
            )
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
                            if (themeConfig.appliedThemeId == target.id) {
                                applier.detach()
                            }
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

    if (pendingReset) {
        AlertDialog(
            onDismissRequest = { pendingReset = false },
            title = { Text(stringResource(R.string.theme_package_reset_confirm_title)) },
            text = { Text(stringResource(R.string.theme_package_reset_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingReset = false
                        applier.resetAppearance()
                    },
                ) {
                    Text(stringResource(R.string.theme_package_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingReset = false }) {
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
