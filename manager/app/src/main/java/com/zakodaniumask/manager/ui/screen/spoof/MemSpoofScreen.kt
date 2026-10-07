// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.ui.screen.spoof

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.calculateBottomPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.ui.component.SwipeableSnackbarHost
import com.zakodaniumask.manager.ui.component.WarningCard
import com.zakodaniumask.manager.ui.component.settings.AppBackButton
import com.zakodaniumask.manager.ui.component.settings.SegmentedColumn
import com.zakodaniumask.manager.ui.navigation.LocalNavigator
import com.zakodaniumask.manager.ui.theme.blurEffect
import com.zakodaniumask.manager.ui.theme.blurSource
import com.zakodaniumask.manager.ui.theme.cardConfig
import com.zakodaniumask.manager.ui.theme.themeConfig
import com.zakodaniumask.manager.ui.util.LocalSnackbarHost
import com.zakodaniumask.manager.ui.util.adaptiveScaffoldWindowInsets
import com.zakodaniumask.manager.ui.util.showReplacingSnackbar
import com.zakodaniumask.manager.ui.viewmodel.MemSpoofEvent
import com.zakodaniumask.manager.ui.viewmodel.MemSpoofViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MemSpoofScreen(viewModel: MemSpoofViewModel = koinViewModel()) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val snackBarHost = LocalSnackbarHost.current

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val applySuccessMsg = stringResource(R.string.spoof_mem_apply_success)
    val applyFailedMsg = stringResource(R.string.spoof_mem_apply_failed)
    val disabledMsg = stringResource(R.string.spoof_mem_disabled)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is MemSpoofEvent.Result ->
                    snackBarHost.showReplacingSnackbar(
                        when {
                            !event.success -> applyFailedMsg
                            else -> applySuccessMsg
                        }
                    )
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
        snackbarHost = { SwipeableSnackbarHost(hostState = snackBarHost) },
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(),
                title = { Text(stringResource(R.string.mem_spoof_title)) },
                navigationIcon = { AppBackButton(onClick = { navigator.pop() }) },
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (themeConfig.isEnableBlur) Color.Transparent
                    else MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
                    scrolledContainerColor = if (themeConfig.isEnableBlur) Color.Transparent
                    else MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
                ),
            )
        },
    ) { paddingValues ->
        if (uiState.isLoading) {
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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .blurSource()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            item {
                Spacer(modifier = Modifier.height(paddingValues.calculateTopPadding()))
            }

            item {
                WarningCard(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp, bottom = 12.dp),
                    message = stringResource(R.string.spoof_mem_warning),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                )
            }

            item {
                MemSpoofForm(
                    currentTotalBytes = uiState.currentTotalBytes,
                    isApplying = uiState.isApplying,
                    onApply = { total, cma -> viewModel.apply(total, cma) },
                    onDisable = { viewModel.apply(0L, 0L) },
                )
            }

            item {
                Spacer(modifier = Modifier.height(paddingValues.calculateBottomPadding()))
            }
        }
    }
}

@Composable
private fun MemSpoofForm(
    currentTotalBytes: Long,
    isApplying: Boolean,
    onApply: (Long, Long) -> Unit,
    onDisable: () -> Unit,
) {
    var totalGbValue by remember(currentTotalBytes) {
        mutableStateOf(
            if (currentTotalBytes > 0) {
                (currentTotalBytes / (1024L * 1024L * 1024L)).toString()
            } else {
                ""
            }
        )
    }
    var cmaMbValue by remember { mutableStateOf("") }
    var invalidInput by remember { mutableStateOf(false) }

    val applyLabel = stringResource(R.string.spoof_cpu_apply)
    val disableLabel = stringResource(R.string.spoof_mem_disable)

    SegmentedColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Text(
                    text = stringResource(R.string.spoof_mem_total_gb),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = totalGbValue,
                    onValueChange = { totalGbValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isApplying,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }
        item {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Text(
                    text = stringResource(R.string.spoof_mem_cma_mb),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = cmaMbValue,
                    onValueChange = { cmaMbValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isApplying,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }
        item {
            Button(
                onClick = {
                    val totalGb = totalGbValue.toLongOrNull()
                    val cmaMb = cmaMbValue.toLongOrNull() ?: 0L
                    if (totalGb == null || totalGb <= 0 || cmaMb < 0) {
                        invalidInput = true
                        return@Button
                    }
                    invalidInput = false
                    onApply(
                        totalGb * 1024L * 1024L * 1024L,
                        cmaMb * 1024L * 1024L,
                    )
                },
                enabled = !isApplying,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(applyLabel)
            }
        }
        item {
            TextButton(
                onClick = onDisable,
                enabled = !isApplying,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text(disableLabel)
            }
        }
    }

    if (invalidInput) {
        Text(
            text = stringResource(R.string.spoof_mem_invalid_input),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
