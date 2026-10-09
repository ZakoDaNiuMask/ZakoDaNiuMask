// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from Duck ToolBox (MIT), ui/src/features/tricky-store; modified for ZakoDaNiuMask.
package com.zakodaniumask.manager.ui.screen.keybox

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.data.trickystore.FieldKind
import com.zakodaniumask.manager.data.trickystore.PolicyField
import com.zakodaniumask.manager.ui.component.SwipeableSnackbarHost
import com.zakodaniumask.manager.ui.component.settings.AppBackButton
import com.zakodaniumask.manager.ui.component.settings.SegmentedColumn
import com.zakodaniumask.manager.ui.navigation.LocalNavigator
import com.zakodaniumask.manager.ui.theme.CardConfig
import com.zakodaniumask.manager.ui.theme.ThemeConfig
import com.zakodaniumask.manager.ui.theme.blurEffect
import com.zakodaniumask.manager.ui.theme.blurSource
import com.zakodaniumask.manager.ui.util.LocalSnackbarHost
import com.zakodaniumask.manager.ui.util.adaptiveScaffoldWindowInsets
import com.zakodaniumask.manager.ui.util.showReplacingSnackbar
import com.zakodaniumask.manager.ui.viewmodel.TrickyStoreEvent
import com.zakodaniumask.manager.ui.viewmodel.TrickyStoreViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrickyPolicyScreen(viewModel: TrickyStoreViewModel = koinViewModel()) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val snackBarHost = LocalSnackbarHost.current
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()

    val savedMsg = stringResource(R.string.keybox_ts_saved)
    val restartMsg = stringResource(R.string.keybox_ts_restart_required)
    val failedMsg = stringResource(R.string.keybox_ts_save_failed)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TrickyStoreEvent.Saved ->
                    snackBarHost.showReplacingSnackbar(if (event.restartRequired) restartMsg else savedMsg)
                is TrickyStoreEvent.Failed ->
                    snackBarHost.showReplacingSnackbar("$failedMsg: ${event.detail}")
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val fields = uiState.status?.schema?.defaultPolicy.orEmpty()

    Scaffold(
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        snackbarHost = { SwipeableSnackbarHost(hostState = snackBarHost) },
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(),
                title = { Text(stringResource(R.string.keybox_ts_policy_title)) },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().blurSource().nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            item { Spacer(Modifier.height(paddingValues.calculateTopPadding())) }
            item {
                SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                    fields.forEach { field ->
                        item {
                            PolicyRow(
                                field = field,
                                value = uiState.defaultPolicy[field.key].orEmpty(),
                                onChange = { viewModel.setDefaultPolicy(field.key, it) },
                            )
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = { viewModel.save() },
                    enabled = !uiState.saving,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Text(stringResource(R.string.keybox_ts_save))
                }
            }
            item { Spacer(Modifier.height(paddingValues.calculateBottomPadding() + 12.dp)) }
        }
    }
}

@Composable
private fun PolicyRow(field: PolicyField, value: String, onChange: (String) -> Unit) {
    if (field.kind == FieldKind.BOOLEAN) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            Text(field.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = value == "true", onCheckedChange = { onChange(it.toString()) })
        }
        return
    }
    var text by remember(field.key, value) { mutableStateOf(value) }
    androidx.compose.foundation.layout.Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(field.label, style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onChange(it)
            },
            singleLine = !field.multiline,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            placeholder = { field.placeholder?.let { Text(it) } },
        )
        field.hint?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
