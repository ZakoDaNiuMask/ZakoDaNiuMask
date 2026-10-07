// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.ui.screen.themeSettings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.ArrowDownward
import androidx.compose.material.icons.twotone.ArrowUpward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.ui.component.settings.AppBackButton
import com.zakodaniumask.manager.ui.navigation.LocalNavigator
import com.zakodaniumask.manager.ui.screen.BottomBarDestination
import com.zakodaniumask.manager.ui.theme.CardConfig
import com.zakodaniumask.manager.ui.theme.ThemeConfig
import com.zakodaniumask.manager.ui.theme.blurEffect
import com.zakodaniumask.manager.ui.theme.blurSource
import com.zakodaniumask.manager.ui.util.adaptiveScaffoldWindowInsets
import com.zakodaniumask.manager.ui.viewmodel.SettingsUiAction
import com.zakodaniumask.manager.ui.viewmodel.SettingsViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NavigationLayoutScreen() {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val navigator = LocalNavigator.current
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val all = BottomBarDestination.entries.toList()
    val names = all.map { it.name }
    val order = buildList {
        addAll(state.navOrder.filter { it in names })
        addAll(names.filter { it !in state.navOrder })
    }
    val ordered = order.mapNotNull { name -> all.firstOrNull { it.name == name } }

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
                title = { Text(stringResource(R.string.navigation_layout)) },
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
            modifier = Modifier
                .fillMaxSize()
                .blurSource()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            item { Spacer(Modifier.height(paddingValues.calculateTopPadding())) }
            item {
                Text(
                    text = stringResource(R.string.navigation_layout_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(ordered) { destination ->
                val isHome = destination == BottomBarDestination.Home
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(cardConfig.cardAlpha),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            imageVector = destination.iconSelected,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(
                            text = stringResource(destination.label),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                val newOrder = order.toMutableList()
                                val index = newOrder.indexOf(destination.name)
                                if (index > 0) {
                                    newOrder[index] = newOrder[index - 1]
                                    newOrder[index - 1] = destination.name
                                    viewModel.dispatch(SettingsUiAction.SetNavOrder(newOrder))
                                }
                            },
                            enabled = order.indexOf(destination.name) > 0,
                        ) {
                            Icon(Icons.TwoTone.ArrowUpward, contentDescription = null)
                        }
                        IconButton(
                            onClick = {
                                val newOrder = order.toMutableList()
                                val index = newOrder.indexOf(destination.name)
                                if (index in 0 until newOrder.lastIndex) {
                                    newOrder[index] = newOrder[index + 1]
                                    newOrder[index + 1] = destination.name
                                    viewModel.dispatch(SettingsUiAction.SetNavOrder(newOrder))
                                }
                            },
                            enabled = order.indexOf(destination.name) < order.lastIndex,
                        ) {
                            Icon(Icons.TwoTone.ArrowDownward, contentDescription = null)
                        }
                        Switch(
                            checked = isHome || destination.name !in state.navHidden,
                            enabled = !isHome,
                            onCheckedChange = { visible ->
                                val hidden = state.navHidden.toMutableSet()
                                if (visible) hidden.remove(destination.name)
                                else hidden.add(destination.name)
                                viewModel.dispatch(SettingsUiAction.SetNavHidden(hidden))
                            },
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(paddingValues.calculateBottomPadding() + 24.dp)) }
        }
    }
}
