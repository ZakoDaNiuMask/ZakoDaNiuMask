package com.zakodaniumask.manager.ui.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AutoFixHigh
import androidx.compose.material.icons.twotone.ExpandMore
import androidx.compose.material.icons.twotone.FileOpen
import androidx.compose.material.icons.twotone.FileUpload
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.data.flash.RemoteBootImageSource
import com.zakodaniumask.manager.data.partition.PartitionManagerRepository
import com.zakodaniumask.manager.domain.model.LkmSelection
import com.zakodaniumask.manager.ui.component.ConfirmResult
import com.zakodaniumask.manager.ui.component.DialogHandle
import com.zakodaniumask.manager.ui.component.WarningCard
import com.zakodaniumask.manager.ui.component.HorizontalPagerWithInteraction
import com.zakodaniumask.manager.ui.component.rememberConfirmDialog
import com.zakodaniumask.manager.ui.component.rememberCustomDialog
import com.zakodaniumask.manager.ui.component.rememberLoadingDialog
import com.zakodaniumask.manager.ui.component.settings.AppBackButton
import com.zakodaniumask.manager.ui.component.settings.SegmentedColumn
import com.zakodaniumask.manager.ui.component.settings.SettingsBaseWidget
import com.zakodaniumask.manager.ui.component.settings.SettingsChooseDialog
import com.zakodaniumask.manager.ui.component.settings.SettingsChooseWidget
import com.zakodaniumask.manager.ui.navigation.LocalNavigator
import com.zakodaniumask.manager.ui.navigation.Route
import com.zakodaniumask.manager.ui.screen.kernelFlash.component.SlotSelectionDialog
import com.zakodaniumask.manager.ui.theme.blurEffect
import com.zakodaniumask.manager.ui.theme.blurSource
import com.zakodaniumask.manager.ui.util.adaptiveScaffoldWindowInsets
import com.zakodaniumask.manager.ui.viewmodel.InstallUiEvent
import com.zakodaniumask.manager.ui.viewmodel.InstallViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

enum class KpmPatchOption {
    FOLLOW_KERNEL,
    PATCH_KPM,
    UNDO_PATCH_KPM,
}

@Composable
private fun KpmPatchOptionItem(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    SettingsBaseWidget(
        iconPlaceholder = false,
        selected = selected,
        title = title,
        description = description,
        onClick = { onSelect() },
        leadingContent = {
            RadioButton(selected = selected, onClick = null)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InstallScreen(
    preselectedKernelUri: String? = null
) {
    val viewModel = koinViewModel<InstallViewModel>()
    val installState by viewModel.state.collectAsStateWithLifecycle()
    val environment = installState.environment
    val context = LocalContext.current

    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val navigator = LocalNavigator.current
    val isGKI = environment.isGki

    LaunchedEffect(isGKI) {
        if (!isGKI) pagerState.scrollToPage(0)
    }

    val failedReboot = stringResource(R.string.failed_reboot)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is InstallUiEvent.Error -> {
                    val message = event.message.ifBlank { failedReboot }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
    }

    Scaffold(
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        topBar = {
            TopBar(
                onBack = { navigator.pop() },
                scrollBehavior = scrollBehavior,
                selectedTab = pagerState.currentPage,
                onTabSelected = { scope.launch { pagerState.animateScrollToPage(it) } }
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) { innerPadding ->
        HorizontalPagerWithInteraction(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .blurSource()
        ) { page ->
            if (installState.loading) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .blurSource()
                ) {
                    item {
                        Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding()))
                    }
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            LoadingIndicator()
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding()))
                    }
                }
            } else {
                when (page) {
                    0 -> LKMInstallPage(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                            .blurSource(),
                        topPadding = innerPadding.calculateTopPadding(),
                        bottomPadding = innerPadding.calculateBottomPadding(),
                        isGKI = environment.isGki,
                        rootAvailable = environment.rootAvailable,
                        isAbDevice = environment.isAbDevice,
                        currentKmi = environment.currentKmi,
                        supportedKmis = environment.supportedKmis,
                        activeSlotSuffix = environment.activeSlotSuffix,
                        inactiveSlotSuffix = environment.inactiveSlotSuffix,
                        availablePartitions = environment.availablePartitions,
                        defaultPartition = environment.defaultPartition,
                    )

                    1 -> Anykernel3InstallPage(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                            .blurSource(),
                        topPadding = innerPadding.calculateTopPadding(),
                        bottomPadding = innerPadding.calculateBottomPadding(),
                        rootAvailable = environment.rootAvailable,
                        isAbDevice = environment.isAbDevice,
                        activeSlotSuffix = environment.activeSlotSuffix,
                        preselectedKernelUri = preselectedKernelUri,
                    )
                }
            }
        }
    }
}

@Composable
private fun LKMInstallPage(
    modifier: Modifier,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    isGKI: Boolean,
    rootAvailable: Boolean,
    isAbDevice: Boolean,
    currentKmi: String,
    supportedKmis: List<String>,
    activeSlotSuffix: String,
    inactiveSlotSuffix: String,
    availablePartitions: List<String>,
    defaultPartition: String,
) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val selectFileTip = stringResource(
        id = R.string.select_file_tip,
        defaultPartition
    )
    val installOnlySupportKoFile = stringResource(R.string.install_only_support_ko_file)
    val dialogTitle = stringResource(id = android.R.string.dialog_alert_title)
    val dialogContent = stringResource(id = R.string.install_inactive_slot_warning)
    val downloadFromUrlSummary = stringResource(R.string.install_from_url_summary)
    val downloadDialogMessage = stringResource(R.string.download_dialog_msg)
    val downloadNoBootPartition = stringResource(R.string.download_no_boot_partition)
    val downloadProbeFailedTemplate = stringResource(R.string.download_probe_failed)
    val remoteBootImageSource: RemoteBootImageSource = koinInject()
    val loadingDialog = rememberLoadingDialog()

    var showDownloadDialog by remember { mutableStateOf(false) }
    var downloadUrl by remember { mutableStateOf("") }
    var remotePartitions by remember { mutableStateOf<List<String>>(emptyList()) }
    var remotePartitionSelectionIndex by remember { mutableIntStateOf(0) }

    var lkmInstallMethod by remember { mutableStateOf<InstallMethod?>(null) }
    var lkmSelection by remember { mutableStateOf<LkmSelection>(LkmSelection.KmiNone) }
    var lkmFileName by remember { mutableStateOf<String?>(null) }
    var allowShell by remember { mutableStateOf(false) }
    var enableAdb by remember { mutableStateOf(false) }
    var forceBackup by remember { mutableStateOf(false) }
    var partitionSelectionIndex by remember { mutableIntStateOf(0) }
    var hasCustomSelected by remember { mutableStateOf(false) }
    var advancedOptionsShown by remember { mutableStateOf(false) }

    val advRotation by animateFloatAsState(
        targetValue = if (advancedOptionsShown) 180f else 0f,
        label = "AdvRotation"
    )

    val lkmMethods = remember(rootAvailable, isAbDevice, isGKI, selectFileTip, downloadFromUrlSummary) {
        buildList {
            add(InstallMethod.SelectFile(summary = selectFileTip))
            add(InstallMethod.DownloadFile(summary = downloadFromUrlSummary))
            if (isGKI && rootAvailable) {
                add(InstallMethod.DirectInstall)
                if (isAbDevice) {
                    add(InstallMethod.DirectInstallToInactiveSlot)
                }
            }
        }
    }

    val confirmDialog = rememberConfirmDialog(
        onConfirm = {
            lkmInstallMethod = InstallMethod.DirectInstallToInactiveSlot
        },
        onDismiss = null
    )

    val selectImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri ->
                lkmInstallMethod = InstallMethod.SelectFile(
                    uri,
                    summary = selectFileTip
                )
            }
        }
    }

    val selectLkmLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri ->
                val isKo = isKoFile(context, uri)
                if (isKo) {
                    lkmSelection = LkmSelection.LkmUri(uri.toString())
                    lkmFileName = uri.toString()
                } else {
                    lkmSelection = LkmSelection.KmiNone
                    lkmFileName = null
                    Toast.makeText(
                        context,
                        installOnlySupportKoFile,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    val onMethodClick = { option: InstallMethod ->
        when (option) {
            is InstallMethod.SelectFile -> {
                selectImageLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "application/*"
                    putExtra(
                        Intent.EXTRA_MIME_TYPES,
                        arrayOf("application/octet-stream", "application/zip")
                    )
                })
            }

            is InstallMethod.DirectInstall -> {
                lkmInstallMethod = option
            }

            is InstallMethod.DirectInstallToInactiveSlot -> {
                confirmDialog.showConfirm(dialogTitle, dialogContent)
            }

            is InstallMethod.DownloadFile -> {
                downloadUrl = option.url.orEmpty()
                showDownloadDialog = true
            }

            is InstallMethod.HorizonKernel -> Unit
        }
    }

    val onLkmInstall = {
        lkmInstallMethod?.let { method ->
            when (method) {
                is InstallMethod.SelectFile,
                is InstallMethod.DirectInstall,
                is InstallMethod.DirectInstallToInactiveSlot -> {
                    navigator.push(
                        Route.Flash.boot(
                            bootUri = if (method is InstallMethod.SelectFile) {
                                method.uri?.toString()
                            } else {
                                null
                            },
                            lkmUri = (lkmSelection as? LkmSelection.LkmUri)?.uri,
                            kmi = (lkmSelection as? LkmSelection.KmiString)?.value,
                            ota = method is InstallMethod.DirectInstallToInactiveSlot,
                            partition = availablePartitions.getOrNull(partitionSelectionIndex),
                            allowShell = allowShell,
                            enableAdb = enableAdb,
                            forceBackup = if (method is InstallMethod.SelectFile) forceBackup else false,
                        )
                    )
                }

                is InstallMethod.DownloadFile -> Unit

                is InstallMethod.HorizonKernel -> Unit
            }
        }
        Unit
    }

    val startDownloadAndFlash: (InstallMethod.DownloadFile) -> Unit = { method ->
        val url = method.url
        val partition = method.partition
        if (url.isNullOrBlank() || partition.isNullOrBlank()) {
            Unit
        } else {
            val kmi = (lkmSelection as? LkmSelection.KmiString)?.value ?: method.remoteKmi
            scope.launch {
                try {
                    val bootUri = loadingDialog.withLoading {
                        remoteBootImageSource.downloadPartition(url, partition)
                    }
                    navigator.push(
                        Route.Flash.boot(
                            bootUri = bootUri.toString(),
                            lkmUri = (lkmSelection as? LkmSelection.LkmUri)?.uri,
                            kmi = kmi,
                            ota = false,
                            partition = partition,
                            allowShell = allowShell,
                            enableAdb = enableAdb,
                            forceBackup = forceBackup,
                        )
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    Toast.makeText(
                        context,
                        error.message ?: error.javaClass.simpleName,
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
        Unit
    }

    val selectKmiDialog = rememberSelectKmiDialog(supportedKmis) { kmi ->
        kmi?.let {
            lkmSelection = LkmSelection.KmiString(it)
            when (val method = lkmInstallMethod) {
                is InstallMethod.DownloadFile -> startDownloadAndFlash(method)
                else -> onLkmInstall()
            }
        }
    }

    val onClickNext = {
        val method = lkmInstallMethod
        when {
            method is InstallMethod.DownloadFile -> {
                if (isGKI && lkmSelection == LkmSelection.KmiNone &&
                    method.remoteKmi.isNullOrBlank() && currentKmi.isBlank()
                ) {
                    selectKmiDialog.show()
                } else {
                    startDownloadAndFlash(method)
                }
            }

            isGKI && lkmSelection == LkmSelection.KmiNone && currentKmi.isBlank() -> {
                selectKmiDialog.show()
            }

            else -> onLkmInstall()
        }
    }

    val isOta = lkmInstallMethod is InstallMethod.DirectInstallToInactiveSlot
    val suffix = if (isOta) inactiveSlotSuffix else activeSlotSuffix
    val displayPartitions = availablePartitions.map { name ->
        if (defaultPartition == name) "$name (default)" else name
    }
    val defaultIndex =
        availablePartitions.indexOf(defaultPartition).takeIf { it >= 0 } ?: 0

    if (!hasCustomSelected && partitionSelectionIndex != defaultIndex) {
        partitionSelectionIndex = defaultIndex
    }

    val canSelectPartition =
        lkmInstallMethod is InstallMethod.DirectInstall ||
                lkmInstallMethod is InstallMethod.DirectInstallToInactiveSlot

    LazyColumn(
        modifier = modifier
    ) {
        item {
            Spacer(modifier = Modifier.height(topPadding))
        }

        item {
            SegmentedColumn {
                lkmMethods.forEach { method ->
                    val selected = method.javaClass == lkmInstallMethod?.javaClass
                    item(key = method.javaClass) {
                        SettingsBaseWidget(
                            title = stringResource(id = method.label),
                            description = method.summary,
                            selected = selected,
                            onClick = { onMethodClick(method) },
                            leadingContent = {
                                RadioButton(
                                    selected = selected,
                                    onClick = null,
                                )
                            },
                        )
                    }
                }
            }
        }

        item {
            SegmentedColumn {
                expandableItem(
                    expanded = advancedOptionsShown,
                    topContent = {
                        SettingsBaseWidget(
                            icon = Icons.TwoTone.Settings,
                            title = stringResource(R.string.advanced_options),
                            onClick = {
                                advancedOptionsShown = !advancedOptionsShown
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.TwoTone.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.graphicsLayer {
                                        rotationZ = advRotation
                                    }
                                )
                            },
                        )
                    },
                    bottomContent = {
                        item(visible = canSelectPartition && displayPartitions.isNotEmpty()) {
                            SettingsChooseWidget(
                                icon = Icons.TwoTone.AutoFixHigh,
                                items = displayPartitions,
                                selectedIndex = partitionSelectionIndex,
                                title = "${stringResource(R.string.install_select_partition)} ($suffix)",
                                onSelectedIndexChange = {
                                    hasCustomSelected = true
                                    partitionSelectionIndex = it
                                },
                            )
                        }

                        item(
                            visible = lkmInstallMethod is InstallMethod.DownloadFile &&
                                    remotePartitions.isNotEmpty()
                        ) {
                            SettingsChooseWidget(
                                icon = Icons.TwoTone.AutoFixHigh,
                                items = remotePartitions,
                                selectedIndex = remotePartitionSelectionIndex,
                                title = stringResource(R.string.install_select_partition),
                                onSelectedIndexChange = { index ->
                                    remotePartitionSelectionIndex = index
                                    val current = lkmInstallMethod as? InstallMethod.DownloadFile
                                    if (current != null) {
                                        lkmInstallMethod = current.copy(
                                            partition = remotePartitions.getOrNull(index)
                                        )
                                    }
                                },
                            )
                        }

                        item {
                            val hasLkmUri = lkmSelection is LkmSelection.LkmUri

                            SettingsBaseWidget(
                                icon = Icons.TwoTone.FileOpen,
                                title = stringResource(id = R.string.install_upload_lkm_file),
                                description = stringResource(id = R.string.install_upload_lkm_file_summary),
                                selected = hasLkmUri,
                                onClick = {
                                    if (hasLkmUri) {
                                        lkmSelection = LkmSelection.KmiNone
                                        lkmFileName = null
                                    } else {
                                        selectLkmLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                                            type = "application/octet-stream"
                                        })
                                    }
                                },
                                descriptionColumnContent = if (hasLkmUri) {
                                    {
                                        Text(
                                            text = lkmFileName ?: "",
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                } else {
                                    null
                                },
                            )
                        }

                        item {
                            SettingsBaseWidget(
                                iconPlaceholder = false,
                                selected = allowShell,
                                title = stringResource(id = R.string.allow_shell),
                                description = stringResource(id = R.string.allow_shell_summary),
                                onClick = { allowShell = !allowShell },
                                leadingContent = {
                                    Checkbox(
                                        checked = allowShell,
                                        onCheckedChange = null,
                                    )
                                },
                            )
                        }

                        item {
                            SettingsBaseWidget(
                                iconPlaceholder = false,
                                selected = enableAdb,
                                title = stringResource(id = R.string.enable_adb),
                                description = stringResource(id = R.string.enable_adb_summary),
                                onClick = { enableAdb = !enableAdb },
                                leadingContent = {
                                    Checkbox(
                                        checked = enableAdb,
                                        onCheckedChange = null,
                                    )
                                },
                            )
                        }

                        item(visible = lkmInstallMethod is InstallMethod.SelectFile) {
                            SettingsBaseWidget(
                                iconPlaceholder = false,
                                selected = forceBackup,
                                title = stringResource(id = R.string.install_force_backup),
                                description = stringResource(id = R.string.install_force_backup_summary),
                                onClick = { forceBackup = !forceBackup },
                                leadingContent = {
                                    Checkbox(
                                        checked = forceBackup,
                                        onCheckedChange = null,
                                    )
                                },
                            )
                        }
                    }
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = lkmInstallMethod != null,
                    onClick = onClickNext,
                ) {
                    Text(
                        stringResource(id = R.string.install_next),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(bottomPadding))
        }
    }

    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = { Text(stringResource(R.string.install_from_url)) },
            text = {
                OutlinedTextField(
                    value = downloadUrl,
                    onValueChange = { downloadUrl = it },
                    label = { Text(stringResource(R.string.download_dialog_msg)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val url = downloadUrl.trim()
                    val parsed = runCatching { url.toUri() }.getOrNull()
                    if (parsed?.scheme?.equals("https", ignoreCase = true) != true ||
                        parsed?.host.isNullOrBlank()
                    ) {
                        Toast.makeText(
                            context,
                            downloadDialogMessage,
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        showDownloadDialog = false
                        scope.launch {
                            try {
                                val probe = loadingDialog.withLoading {
                                    remoteBootImageSource.probe(url)
                                }
                                if (probe.partitions.isEmpty()) {
                                    Toast.makeText(
                                        context,
                                        downloadNoBootPartition,
                                        Toast.LENGTH_LONG,
                                    ).show()
                                } else {
                                    remotePartitions = probe.partitions
                                    remotePartitionSelectionIndex = 0
                                    lkmInstallMethod = InstallMethod.DownloadFile(
                                        url = url,
                                        partition = probe.partitions.first(),
                                        remoteKmi = probe.kmi,
                                        summary = downloadFromUrlSummary,
                                    )
                                    if (!probe.kmi.isNullOrBlank() &&
                                        lkmSelection == LkmSelection.KmiNone
                                    ) {
                                        lkmSelection = LkmSelection.KmiString(probe.kmi)
                                    }
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Throwable) {
                                Toast.makeText(
                                    context,
                                    downloadProbeFailedTemplate.format(
                                        error.message ?: error.javaClass.simpleName
                                    ),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    }
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun Anykernel3InstallPage(
    modifier: Modifier,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    rootAvailable: Boolean,
    isAbDevice: Boolean,
    activeSlotSuffix: String,
    preselectedKernelUri: String?,
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val summary = stringResource(R.string.horizon_kernel_summary)
    val remoteBootImageSource: RemoteBootImageSource = koinInject()
    val partitionManagerRepository: PartitionManagerRepository = koinInject()
    val loadingDialog = rememberLoadingDialog()
    val urlDialogLabel = stringResource(R.string.download_dialog_msg)
    val rootRequiredText = stringResource(R.string.root_required)
    val ak3DownloadFailedTemplate = stringResource(R.string.ak3_download_failed)
    val ak3FromUrlSummary = stringResource(R.string.ak3_from_url_summary)
    val ak3PreflightConfirmText = stringResource(R.string.ak3_preflight_confirm)
    var ak3InstallMethod by remember { mutableStateOf<InstallMethod?>(null) }
    var skipKsud by remember { mutableStateOf(false) }
    var kpmPatchOption by remember { mutableStateOf(KpmPatchOption.FOLLOW_KERNEL) }
    var showSlotSelectionDialog by remember { mutableStateOf(false) }
    var tempKernelUri by remember { mutableStateOf<Uri?>(null) }
    var advancedOptionsShown by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var urlText by remember { mutableStateOf("") }

    val ak3AdvRotation by animateFloatAsState(
        targetValue = if (advancedOptionsShown) 180f else 0f,
        label = "Ak3AdvRotation"
    )

    val selectImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri ->
                if (isAbDevice) {
                    tempKernelUri = uri
                    showSlotSelectionDialog = true
                } else {
                    ak3InstallMethod = InstallMethod.HorizonKernel(
                        uri = uri,
                        summary = summary
                    )
                }
            }
        }
    }

    LaunchedEffect(preselectedKernelUri, isAbDevice) {
        preselectedKernelUri?.let { uriString ->
            try {
                val preselectedUri = uriString.toUri()
                tempKernelUri = preselectedUri

                if (isAbDevice) {
                    showSlotSelectionDialog = true
                } else {
                    ak3InstallMethod = InstallMethod.HorizonKernel(
                        uri = preselectedUri,
                        summary = summary
                    )
                }
            } catch (_: Exception) {
            }
        }
    }

    val confirmDialog = rememberConfirmDialog()

    val onClickNext = {
        if (!rootAvailable) {
            Toast.makeText(context, rootRequiredText, Toast.LENGTH_SHORT).show()
        } else {
            (ak3InstallMethod as? InstallMethod.HorizonKernel)?.let { method ->
                method.uri?.let { uri ->
                    navigator.push(
                        Route.KernelFlash(
                            kernelUri = uri.toString(),
                            selectedSlot = method.slot,
                            skipKsud = skipKsud,
                            kpmPatchEnabled = kpmPatchOption == KpmPatchOption.PATCH_KPM,
                            kpmUndoPatch = kpmPatchOption == KpmPatchOption.UNDO_PATCH_KPM,
                        )
                    )
                }
            }
        }
    }

    val startAk3FromUrl: (String) -> Unit = { rawUrl ->
        val url = rawUrl.trim()
        val parsed = runCatching { url.toUri() }.getOrNull()
        if (parsed?.scheme?.equals("https", ignoreCase = true) != true ||
            parsed?.host.isNullOrBlank()
        ) {
            Toast.makeText(context, urlDialogLabel, Toast.LENGTH_SHORT).show()
        } else if (!rootAvailable) {
            Toast.makeText(context, rootRequiredText, Toast.LENGTH_SHORT).show()
        } else {
            showUrlDialog = false
            scope.launch {
                try {
                    val file = loadingDialog.withLoading {
                        remoteBootImageSource.downloadFile(url, "anykernel3.zip")
                    }
                    val preflight = runCatching {
                        partitionManagerRepository.inspectAk3Package(file.absolutePath)
                    }.getOrNull()
                    val confirmContent = if (preflight != null) {
                        buildString {
                            append("kernel: ")
                            append(preflight.kernelName.ifBlank { "-" })
                            append('\n')
                            append("devices: ")
                            append(preflight.devices.joinToString(", ").ifBlank { "-" })
                            append('\n')
                            append("slot_policy: ")
                            append(preflight.packageSlotPolicy ?: "-")
                            append("\n\n")
                            append(ak3PreflightConfirmText)
                        }
                    } else {
                        ak3PreflightConfirmText
                    }
                    val confirmed = confirmDialog.awaitConfirm(
                        title = summary,
                        content = confirmContent,
                    )
                    if (confirmed != ConfirmResult.Confirmed) return@launch
                    val slotSuffix = activeSlotSuffix.removePrefix("_")
                        .takeIf { it == "a" || it == "b" }
                    navigator.push(
                        Route.KernelFlash(
                            kernelUri = remoteBootImageSource.contentUri(file).toString(),
                            selectedSlot = if (isAbDevice) slotSuffix else null,
                            skipKsud = skipKsud,
                            kpmPatchEnabled = kpmPatchOption == KpmPatchOption.PATCH_KPM,
                            kpmUndoPatch = kpmPatchOption == KpmPatchOption.UNDO_PATCH_KPM,
                        )
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    Toast.makeText(
                        context,
                        ak3DownloadFailedTemplate.format(
                            error.message ?: error.javaClass.simpleName
                        ),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    SlotSelectionDialog(
        show = showSlotSelectionDialog && isAbDevice,
        currentSlot = activeSlotSuffix.removePrefix("_")
            .takeIf { it == "a" || it == "b" },
        onDismiss = { showSlotSelectionDialog = false },
        onSlotSelected = { slot ->
            showSlotSelectionDialog = false
            ak3InstallMethod = InstallMethod.HorizonKernel(
                uri = tempKernelUri,
                slot = slot,
                summary = summary
            )
        }
    )

    LazyColumn(
        modifier = modifier
    ) {
        item {
            Spacer(modifier = Modifier.height(topPadding))
        }

        if (!rootAvailable) {
            item {
                WarningCard(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    message = rootRequiredText,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                )
            }
        }

        val horizonSelected = ak3InstallMethod is InstallMethod.HorizonKernel

        item {
            SegmentedColumn {
                item {
                        SettingsBaseWidget(
                            title = stringResource(R.string.GKI_install_methods),
                            description = stringResource(R.string.ak3_select_zip),
                            icon = Icons.TwoTone.FileUpload,
                            selected = horizonSelected,
                            onClick = {
                                selectImageLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                                    type = "application/*"
                                    putExtra(
                                        Intent.EXTRA_MIME_TYPES,
                                        arrayOf("application/octet-stream", "application/zip")
                                    )
                                })
                            },
                        )
                    }

                    item {
                        SettingsBaseWidget(
                            title = stringResource(R.string.install_from_url),
                            description = ak3FromUrlSummary,
                            icon = Icons.TwoTone.FileUpload,
                            onClick = { showUrlDialog = true },
                        )
                    }

                    (ak3InstallMethod as? InstallMethod.HorizonKernel)?.slot?.let { slot ->
                        item {
                            SettingsBaseWidget(
                                title = stringResource(
                                    id = R.string.selected_slot,
                                    if (slot == "a") {
                                        stringResource(id = R.string.slot_a)
                                    } else {
                                        stringResource(id = R.string.slot_b)
                                    }
                                ),
                                onClick = null,
                            )
                        }
                    }
                }
            }

            item {
                SegmentedColumn {
                    expandableItem(
                        expanded = advancedOptionsShown,
                        topContent = {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Settings,
                                title = stringResource(R.string.advanced_options),
                                onClick = {
                                    advancedOptionsShown = !advancedOptionsShown
                                },
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.TwoTone.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier.graphicsLayer {
                                            rotationZ = ak3AdvRotation
                                        }
                                    )
                                },
                            )
                        },
                        bottomContent = {
                            item {
                                SettingsBaseWidget(
                                    iconPlaceholder = false,
                                    selected = skipKsud,
                                    title = stringResource(id = R.string.skip_ksud),
                                    description = stringResource(id = R.string.skip_ksud_summary),
                                    onClick = { skipKsud = !skipKsud },
                                    leadingContent = {
                                        Checkbox(
                                            checked = skipKsud,
                                            onCheckedChange = null,
                                        )
                                    },
                                )
                            }
                            item {
                                KpmPatchOptionItem(
                                    title = stringResource(R.string.kpm_patch_follow),
                                    description = stringResource(R.string.kpm_patch_follow_desc),
                                    selected = kpmPatchOption == KpmPatchOption.FOLLOW_KERNEL,
                                    onSelect = { kpmPatchOption = KpmPatchOption.FOLLOW_KERNEL },
                                )
                            }
                            item {
                                KpmPatchOptionItem(
                                    title = stringResource(R.string.kpm_patch_apply),
                                    description = stringResource(R.string.kpm_patch_apply_desc),
                                    selected = kpmPatchOption == KpmPatchOption.PATCH_KPM,
                                    onSelect = { kpmPatchOption = KpmPatchOption.PATCH_KPM },
                                )
                            }
                            item {
                                KpmPatchOptionItem(
                                    title = stringResource(R.string.kpm_patch_undo),
                                    description = stringResource(R.string.kpm_patch_undo_desc),
                                    selected = kpmPatchOption == KpmPatchOption.UNDO_PATCH_KPM,
                                    onSelect = { kpmPatchOption = KpmPatchOption.UNDO_PATCH_KPM },
                                )
                            }
                        }
                    )
                }
            }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = ak3InstallMethod != null,
                    onClick = {
                        onClickNext()
                    },
                ) {
                    Text(
                        stringResource(id = R.string.install_next),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(bottomPadding))
        }
    }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text(stringResource(R.string.install_from_url)) },
            text = {
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text(urlDialogLabel) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { startAk3FromUrl(urlText) }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberSelectKmiDialog(
    supportedKmi: List<String>,
    onSelected: (String?) -> Unit,
): DialogHandle {
    return rememberCustomDialog { dismiss ->
        SettingsChooseDialog(
            show = true,
            title = stringResource(R.string.select_kmi),
            items = supportedKmi,
            selectedIndex = -1,
            onDismiss = dismiss,
            onSelectedIndexChange = { index ->
                onSelected(supportedKmi.getOrNull(index))
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    onBack: () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
) {
    Column(modifier = Modifier.blurEffect()) {
        LargeFlexibleTopAppBar(
            title = {
                Text(stringResource(R.string.install))
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
            navigationIcon = {
                AppBackButton(
                    onClick = onBack
                )
            },
            windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
            scrollBehavior = scrollBehavior
        )

        PrimaryTabRow(
            selectedTabIndex = selectedTab.coerceAtMost(1),
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { onTabSelected(0) },
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = { Text(stringResource(R.string.Lkm_install_methods)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { onTabSelected(1) },
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = { Text(stringResource(R.string.GKI_install_methods)) }
            )
        }
    }
}

private fun isKoFile(context: Context, uri: Uri): Boolean {
    val seg = uri.lastPathSegment ?: ""
    if (seg.endsWith(".ko", ignoreCase = true)) return true

    return try {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx != -1 && cursor.moveToFirst()) {
                val name = cursor.getString(idx)
                name?.endsWith(".ko", ignoreCase = true) == true
            } else {
                false
            }
        } ?: false
    } catch (_: Throwable) {
        false
    }
}

@Preview
@Composable
fun SelectInstallPreview() {
    InstallScreen()
}

sealed class InstallMethod {
    data class SelectFile(
        val uri: Uri? = null,
        @param:StringRes override val label: Int = R.string.select_file,
        override val summary: String?
    ) : InstallMethod()

    data object DirectInstall : InstallMethod() {
        override val label: Int
            get() = R.string.direct_install
    }

    data object DirectInstallToInactiveSlot : InstallMethod() {
        override val label: Int
            get() = R.string.install_inactive_slot
    }

    data class HorizonKernel(
        val uri: Uri? = null,
        val slot: String? = null,
        @param:StringRes override val label: Int = R.string.horizon_kernel,
        override val summary: String? = null
    ) : InstallMethod()

    data class DownloadFile(
        val url: String? = null,
        val partition: String? = null,
        val remoteKmi: String? = null,
        @param:StringRes override val label: Int = R.string.install_from_url,
        override val summary: String? = null
    ) : InstallMethod()

    abstract val label: Int
    open val summary: String? = null
}
