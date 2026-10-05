// SPDX-License-Identifier: GPL-3.0-or-later
// Portions Copyright (C) Anatdx (YukiSU)
// Source: https://github.com/Rouyashiki/YukiSU
// Ported into ZakoDaNiuMask; see LICENSE.

package com.zakodaniumask.manager.ui.screen.partition

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.data.partition.PartitionInfo
import com.zakodaniumask.manager.data.partition.PartitionManagerRepository
import com.zakodaniumask.manager.data.partition.SlotInfo
import com.zakodaniumask.manager.ui.component.SwipeableSnackbarHost
import com.zakodaniumask.manager.ui.component.WarningCard
import com.zakodaniumask.manager.ui.component.rememberConfirmDialog
import com.zakodaniumask.manager.ui.component.settings.AppBackButton
import com.zakodaniumask.manager.ui.component.settings.SettingsBaseWidget
import com.zakodaniumask.manager.ui.component.settings.lazySegmentColumn
import com.zakodaniumask.manager.ui.navigation.LocalNavigator
import com.zakodaniumask.manager.ui.screen.LabelText
import com.zakodaniumask.manager.ui.theme.CardConfig
import com.zakodaniumask.manager.ui.theme.ThemeConfig
import com.zakodaniumask.manager.ui.theme.blurEffect
import com.zakodaniumask.manager.ui.theme.blurSource
import com.zakodaniumask.manager.ui.util.LocalSnackbarHost
import com.zakodaniumask.manager.ui.util.adaptiveScaffoldWindowInsets
import com.zakodaniumask.manager.ui.util.showReplacingSnackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.io.File
import java.io.IOException
import java.util.Locale

private val BOOT_CRITICAL_PARTITIONS = setOf(
    "boot",
    "init_boot",
    "vendor_boot",
    "vendor_kernel_boot",
    "dtbo",
    "vbmeta",
    "recovery",
)

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.2f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.2f MB", mb)
    val gb = mb / 1024.0
    if (gb < 1024) return String.format(Locale.US, "%.2f GB", gb)
    return String.format(Locale.US, "%.2f TB", gb / 1024.0)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PartitionManagerScreen() {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val repository: PartitionManagerRepository = koinInject()
    val navigator = LocalNavigator.current
    val snackBarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    var isLoading by remember { mutableStateOf(true) }
    var isBusy by remember { mutableStateOf(false) }
    var slotInfo by remember { mutableStateOf<SlotInfo?>(null) }
    var commonPartitions by remember { mutableStateOf<List<PartitionInfo>>(emptyList()) }
    var allPartitions by remember { mutableStateOf<List<PartitionInfo>>(emptyList()) }
    var showAll by remember { mutableStateOf(false) }
    var selectedPartition by remember { mutableStateOf<PartitionInfo?>(null) }
    var avbStatus by remember { mutableStateOf("") }
    var kernelVersion by remember { mutableStateOf("") }

    val confirmDialog = rememberConfirmDialog()
    val confirmDialogTitle = stringResource(android.R.string.dialog_alert_title)

    suspend fun refresh() {
        isLoading = true
        try {
            val info = repository.getSlotInfo()
            slotInfo = info
            val slot = info.currentSlot
            commonPartitions = repository.getPartitionList(slot, scanAll = false)
            allPartitions = repository.getPartitionList(slot, scanAll = true).map { partition ->
                if (partition.blockDevice.isBlank()) {
                    partition.copy(
                        blockDevice = repository.getPartitionBlockDevice(partition.name, slot)
                    )
                } else {
                    partition
                }
            }
            avbStatus = repository.getAvbStatus()
            kernelVersion = repository.getKernelVersion(slot)
        } catch (error: Throwable) {
            snackBarHost.showReplacingSnackbar(
                error.message ?: error.javaClass.simpleName
            )
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
        refresh()
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val partition = selectedPartition
        if (uri != null && partition != null) {
            scope.launch {
                isBusy = true
                try {
                    val staged = withContext(Dispatchers.IO) {
                        val target = File.createTempFile("partition_flash_", ".img", context.cacheDir)
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            target.outputStream().buffered().use { output ->
                                input.copyTo(output)
                            }
                        } ?: throw IOException("The selected document cannot be opened")
                        target
                    }
                    val success = repository.flashPartition(
                        imagePath = staged.absolutePath,
                        partition = partition.name,
                        slot = slotInfo?.currentSlot,
                    )
                    staged.delete()
                    snackBarHost.showReplacingSnackbar(
                        context.getString(
                            if (success) R.string.partition_flash_success
                            else R.string.partition_flash_failed,
                            partition.name,
                        )
                    )
                    if (success) selectedPartition = null
                } catch (error: Throwable) {
                    snackBarHost.showReplacingSnackbar(
                        error.message ?: error.javaClass.simpleName
                    )
                } finally {
                    isBusy = false
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(),
                title = { Text(stringResource(R.string.partition_manager)) },
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    AppBackButton(onClick = { navigator.pop() })
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor =
                        if (themeConfig.isEnableBlur)
                            Color.Transparent
                        else
                            MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
                    scrolledContainerColor =
                        if (themeConfig.isEnableBlur)
                            Color.Transparent
                        else
                            MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
                ),
            )
        },
        snackbarHost = { SwipeableSnackbarHost(hostState = snackBarHost) },
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator()
            }
            return@Scaffold
        }

        val partitions = if (showAll) allPartitions else commonPartitions

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .blurSource()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + 5.dp,
                bottom = paddingValues.calculateBottomPadding() + 12.dp,
            ),
        ) {
            item {
                WarningCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    message = stringResource(R.string.partition_manager_warning),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            slotInfo?.takeIf { it.isAbDevice }?.let { info ->
                item {
                    SegmentedInfo {
                        InfoRow(
                            label = stringResource(R.string.partition_slot_current),
                            value = info.currentSlot ?: "-",
                        )
                        InfoRow(
                            label = stringResource(R.string.partition_slot_other),
                            value = info.otherSlot ?: "-",
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            if (avbStatus.isNotBlank() || kernelVersion.isNotBlank()) {
                item {
                    SegmentedInfo {
                        if (avbStatus.isNotBlank()) {
                            InfoRow(
                                label = stringResource(R.string.partition_avb),
                                value = avbStatus,
                            )
                        }
                        if (kernelVersion.isNotBlank()) {
                            InfoRow(
                                label = stringResource(R.string.partition_kernel_version),
                                value = kernelVersion,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            item {
                SegmentedInfo {
                    SettingsBaseWidget(
                        icon = Icons.TwoTone.Storage,
                        title = stringResource(
                            if (showAll) R.string.partition_filter_all
                            else R.string.partition_filter_common
                        ),
                        description = stringResource(R.string.partition_filter_hint),
                        onClick = { showAll = !showAll },
                        trailingContent = {
                            if (showAll) {
                                Icon(Icons.TwoTone.Check, contentDescription = null)
                            }
                        },
                    )
                    SettingsBaseWidget(
                        iconPlaceholder = false,
                        title = stringResource(R.string.partition_refresh),
                        onClick = { scope.launch { refresh() } },
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (partitions.isEmpty()) {
                item {
                    WarningCard(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        message = stringResource(R.string.partition_no_partitions),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    )
                }
            } else {
                lazySegmentColumn(partitions, key = { _, it -> it.name }) { _, partition ->
                    SettingsBaseWidget(
                        title = partition.name,
                        description = buildString {
                            append(if (partition.isLogical) "logical" else "physical")
                            append(" · ")
                            append(formatSize(partition.size))
                        },
                        onClick = { selectedPartition = partition },
                        descriptionColumnContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 5.dp),
                            ) {
                                if (partition.isDangerous) {
                                    LabelText(
                                        label = stringResource(R.string.partition_dangerous),
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                    )
                                }
                                if (partition.name in BOOT_CRITICAL_PARTITIONS) {
                                    LabelText(label = stringResource(R.string.partition_boot_critical))
                                }
                            }
                        },
                    )
                }
            }
        }

        val partition = selectedPartition
        if (partition != null) {
            PartitionDetailDialog(
                partition = partition,
                slot = slotInfo?.currentSlot,
                onDismiss = { selectedPartition = null },
                onBackup = {
                    scope.launch {
                        isBusy = true
                        try {
                            val directory = context.getExternalFilesDir(null) ?: context.filesDir
                            val output = File(
                                directory,
                                "${partition.name}_${System.currentTimeMillis()}.img"
                            )
                            val success = repository.backupPartition(
                                partition = partition.name,
                                outputPath = output.absolutePath,
                                slot = slotInfo?.currentSlot,
                            )
                            snackBarHost.showReplacingSnackbar(
                                context.getString(
                                    if (success) R.string.partition_backup_success
                                    else R.string.partition_backup_failed,
                                    if (success) output.absolutePath else partition.name,
                                )
                            )
                        } catch (error: Throwable) {
                            snackBarHost.showReplacingSnackbar(
                                error.message ?: error.javaClass.simpleName
                            )
                        } finally {
                            isBusy = false
                        }
                    }
                },
                onFlash = { imagePicker.launch("application/octet-stream") },
                onMap = {
                    val slot = slotInfo?.otherSlot ?: slotInfo?.currentSlot
                    if (slot == null) {
                        scope.launch {
                            snackBarHost.showReplacingSnackbar(
                                context.getString(R.string.partition_no_ab)
                            )
                        }
                    } else {
                        scope.launch {
                            isBusy = true
                            try {
                                val success = repository.mapLogicalPartitions(slot)
                                snackBarHost.showReplacingSnackbar(
                                    context.getString(
                                        if (success) R.string.partition_map_success
                                        else R.string.partition_map_failed,
                                    )
                                )
                            } finally {
                                isBusy = false
                            }
                        }
                    }
                },
                onDisableAvb = {
                    scope.launch {
                        val result = confirmDialog.awaitConfirm(
                            title = confirmDialogTitle,
                            content = context.getString(R.string.partition_avb_disable_confirm),
                        )
                        if (result == com.zakodaniumask.manager.ui.component.ConfirmResult.Confirmed) {
                            isBusy = true
                            try {
                                val success = repository.disableAvb()
                                snackBarHost.showReplacingSnackbar(
                                    context.getString(
                                        if (success) R.string.partition_avb_disabled
                                        else R.string.partition_avb_disable_failed,
                                    )
                                )
                            } finally {
                                isBusy = false
                            }
                        }
                    }
                },
            )
        }

        if (isBusy) {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                text = {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingIndicator()
                    }
                },
            )
        }
    }
}

@Composable
private fun SegmentedInfo(content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        com.zakodaniumask.manager.ui.component.settings.SegmentedColumn {
            item { content() }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    SettingsBaseWidget(
        iconPlaceholder = false,
        title = label,
        description = value,
        onClick = null,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PartitionDetailDialog(
    partition: PartitionInfo,
    slot: String?,
    onDismiss: () -> Unit,
    onBackup: () -> Unit,
    onFlash: () -> Unit,
    onMap: () -> Unit,
    onDisableAvb: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(partition.name) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    DetailLine(stringResource(R.string.partition_block_device), partition.blockDevice)
                }
                item {
                    DetailLine(
                        stringResource(R.string.partition_type),
                        if (partition.isLogical) "logical" else "physical",
                    )
                }
                item {
                    DetailLine(stringResource(R.string.partition_size), formatSize(partition.size))
                }
                slot?.let { value ->
                    item { DetailLine(stringResource(R.string.partition_slot_current), value) }
                }
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsBaseWidget(
                        title = stringResource(R.string.partition_backup),
                        onClick = { onBackup() },
                    )
                }
                item {
                    SettingsBaseWidget(
                        title = stringResource(R.string.partition_flash_image),
                        onClick = { onFlash() },
                    )
                }
                item {
                    SettingsBaseWidget(
                        title = stringResource(R.string.partition_map),
                        onClick = { onMap() },
                    )
                }
                item {
                    SettingsBaseWidget(
                        title = stringResource(R.string.partition_avb_disable),
                        onClick = { onDisableAvb() },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
