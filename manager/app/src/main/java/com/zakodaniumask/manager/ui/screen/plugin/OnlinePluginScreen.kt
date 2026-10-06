package com.zakodaniumask.manager.ui.screen.plugin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Download
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.domain.usecase.DownloadOnlinePluginUseCase
import com.zakodaniumask.manager.domain.usecase.InstallPluginUseCase
import com.zakodaniumask.manager.ui.component.SwipeableSnackbarHost
import com.zakodaniumask.manager.ui.component.WarningCard
import com.zakodaniumask.manager.ui.component.settings.AppBackButton
import com.zakodaniumask.manager.ui.component.settings.SegmentedColumn
import com.zakodaniumask.manager.ui.component.settings.SettingsBaseWidget
import com.zakodaniumask.manager.ui.navigation.LocalNavigator
import com.zakodaniumask.manager.ui.theme.CardConfig
import com.zakodaniumask.manager.ui.theme.ThemeConfig
import com.zakodaniumask.manager.ui.theme.blurEffect
import com.zakodaniumask.manager.ui.theme.blurSource
import com.zakodaniumask.manager.ui.util.LocalSnackbarHost
import com.zakodaniumask.manager.ui.util.adaptiveScaffoldWindowInsets
import com.zakodaniumask.manager.ui.util.showReplacingSnackbar
import com.zakodaniumask.manager.ui.viewmodel.OnlinePluginViewModel
import java.io.File
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnlinePluginScreen() {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val viewModel: OnlinePluginViewModel = koinViewModel()
    val downloadPlugin: DownloadOnlinePluginUseCase = koinInject()
    val installPlugin: InstallPluginUseCase = koinInject()
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val snackBarHost = LocalSnackbarHost.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val installedMsg = stringResource(R.string.plugin_install_success)
    val installFailedMsg = stringResource(R.string.plugin_install_failed)

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
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
                title = { Text(stringResource(R.string.online_plugin_title)) },
                navigationIcon = { AppBackButton(onClick = { navigator.pop() }) },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.TwoTone.Refresh, contentDescription = stringResource(R.string.plugin_refresh))
                    }
                },
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                scrollBehavior = scrollBehavior,
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

            val error = uiState.error
            if (error != null) {
                item {
                    WarningCard(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(top = 8.dp, bottom = 12.dp),
                        message = error,
                        color = MaterialTheme.colorScheme.errorContainer,
                    )
                }
            }

            if (uiState.plugins.isEmpty() && error == null) {
                item {
                    WarningCard(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(top = 8.dp, bottom = 12.dp),
                        message = stringResource(R.string.plugin_empty),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
            } else {
                item {
                    SegmentedColumn {
                        uiState.plugins.forEach { online ->
                            item {
                                SettingsBaseWidget(
                                    iconPlaceholder = false,
                                    title = online.name,
                                    description = listOfNotNull(
                                        online.version.takeIf { it.isNotBlank() }?.let { "v$it" },
                                        online.description.takeIf { it.isNotBlank() },
                                    ).joinToString("\n"),
                                    onClick = null,
                                    trailingContent = {
                                        IconButton(onClick = {
                                            scope.launch {
                                                val safeName = online.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                                                val dest = File(context.cacheDir, "plugin_$safeName.zip")
                                                val download = downloadPlugin(online.url, dest.absolutePath)
                                                val path = download.getOrNull()
                                                val ok = path != null && installPlugin(path)
                                                snackBarHost.showReplacingSnackbar(
                                                    if (ok) installedMsg else installFailedMsg
                                                )
                                                dest.delete()
                                            }
                                        }) {
                                            Icon(
                                                Icons.TwoTone.Download,
                                                contentDescription = stringResource(R.string.plugin_install),
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(paddingValues.calculateBottomPadding()))
            }
        }
    }
}
