package com.zakodaniumask.manager.ksuui.ui.screen.superuser

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.zakodaniumask.manager.Natives
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.domain.model.InstalledApp
import com.zakodaniumask.manager.domain.model.InstalledAppGroup
import com.zakodaniumask.manager.ksuui.data.model.AppInfo
import com.zakodaniumask.manager.ksuui.ui.component.SearchStatus
import com.zakodaniumask.manager.ksuui.ui.util.ownerNameForUid
import com.zakodaniumask.manager.ksuui.ui.viewmodel.AppSortConfig
import com.zakodaniumask.manager.ksuui.ui.viewmodel.AppSortType
import com.zakodaniumask.manager.ui.navigation.Navigator
import com.zakodaniumask.manager.ui.navigation.Route
import com.zakodaniumask.manager.ui.viewmodel.SortType
import com.zakodaniumask.manager.ui.viewmodel.SuperUserUiAction
import com.zakodaniumask.manager.ui.viewmodel.SuperUserUiState as CurrentSuperUserUiState
import com.zakodaniumask.manager.ui.viewmodel.SuperUserViewModel

@Immutable
data class KsuSuperUserController(
    val state: SuperUserUiState,
    val actions: SuperUserActions,
)

/**
 * Maps the current manager's SuperUser state onto the KernelSU screen model, so the ported
 * Material/Miuix screens can be used without a second root-service implementation.
 */
@Composable
fun rememberKsuSuperUserController(
    viewModel: SuperUserViewModel,
    currentState: CurrentSuperUserUiState,
    navigator: Navigator,
): KsuSuperUserController {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val searchLabel = stringResource(R.string.search_apps)

    var searchStatus by remember { mutableStateOf(SearchStatus(label = searchLabel)) }
    LaunchedEffect(currentState.search) {
        searchStatus = searchStatus.copy(searchText = currentState.search)
    }

    val groups = remember(currentState.appGroupList) {
        currentState.appGroupList.map { it.toGroupedApps(packageManager) }
    }
    val groupsByUid = remember(groups) { groups.associateBy { it.uid } }
    val recentlyInstalled = remember(currentState.appGroupList, groupsByUid) {
        currentState.appGroupList
            .filter { it.isRecentlyInstalled }
            .mapNotNull { groupsByUid[it.uid] }
    }
    val searchResults = if (currentState.search.isNotBlank()) groups else emptyList()

    val sortConfig = remember(currentState.currentSortType, currentState.reverseOrder) {
        AppSortConfig(
            sortType = when (currentState.currentSortType) {
                SortType.INSTALL_TIME -> AppSortType.INSTALL_TIME
                SortType.UPDATE_TIME -> AppSortType.UPDATE_TIME
                else -> AppSortType.NAME
            },
            reversed = currentState.reverseOrder,
        )
    }

    val actions = remember(viewModel, navigator, currentState.showSystemApps, currentState.reverseOrder, searchStatus) {
        SuperUserActions(
            onRefresh = { viewModel.dispatch(SuperUserUiAction.Refresh) },
            onOpenSulog = { navigator.push(Route.Sulog) },
            onSearchTextChange = { text ->
                viewModel.dispatch(SuperUserUiAction.Search(text))
                searchStatus = searchStatus.copy(
                    searchText = text,
                    current = if (text.isBlank()) {
                        SearchStatus.Status.COLLAPSED
                    } else {
                        SearchStatus.Status.EXPANDED
                    },
                )
            },
            onSearchStatusChange = { searchStatus = it },
            onClearSearch = {
                viewModel.dispatch(SuperUserUiAction.Search(""))
                searchStatus = searchStatus.copy(
                    searchText = "",
                    current = SearchStatus.Status.COLLAPSED,
                )
            },
            onToggleShowSystemApps = {
                viewModel.dispatch(SuperUserUiAction.SetShowSystemApps(!currentState.showSystemApps))
            },
            onToggleShowOnlyPrimaryUserApps = {},
            onUpdateSortConfig = { config ->
                val type = when (config.sortType) {
                    AppSortType.INSTALL_TIME -> SortType.INSTALL_TIME
                    AppSortType.UPDATE_TIME -> SortType.UPDATE_TIME
                    else -> SortType.NAME
                }
                viewModel.dispatch(SuperUserUiAction.SetSort(type))
                if (config.reversed != currentState.reverseOrder) {
                    viewModel.dispatch(SuperUserUiAction.SetReverseOrder(config.reversed))
                }
            },
            onOpenProfile = { group ->
                navigator.push(Route.AppProfile(group.uid, group.primary.packageName))
            },
        )
    }

    val state = SuperUserUiState(
        isRefreshing = currentState.isRefreshing,
        hasLoaded = true,
        groupedApps = groups,
        recentlyInstalledResults = if (currentState.search.isBlank()) recentlyInstalled else emptyList(),
        userIds = listOf(0),
        searchStatus = searchStatus,
        searchResults = searchResults,
        showSystemApps = currentState.showSystemApps,
        showOnlyPrimaryUserApps = false,
        sortConfig = sortConfig,
        error = null,
    )

    return remember(state, actions) { KsuSuperUserController(state, actions) }
}

private fun InstalledAppGroup.toGroupedApps(packageManager: PackageManager): GroupedApps {
    val mappedApps = apps.map { it.toAppInfo(packageManager) }
    val primary = mappedApps.firstOrNull { it.packageName == primaryPackageName } ?: mappedApps.first()
    return GroupedApps(
        uid = uid,
        apps = mappedApps,
        primary = primary,
        anyAllowSu = allowSu,
        anyCustom = hasCustomProfile,
        shouldUmount = shouldUmount,
        ownerName = userName ?: runCatching { ownerNameForUid(uid, mappedApps) }.getOrNull(),
        matchedIdentifiers = setOf(primary.displayIdentifier),
    )
}

private fun InstalledApp.toAppInfo(packageManager: PackageManager): AppInfo {
    val packageInfo = runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()
        ?: PackageInfo().apply {
            this.packageName = this@toAppInfo.packageName
            applicationInfo = ApplicationInfo().apply {
                this.packageName = this@toAppInfo.packageName
                uid = this@toAppInfo.uid
            }
        }
    val profile = runCatching { Natives.getAppProfile(profileKey, uid) }.getOrNull()
    return AppInfo(
        label = label,
        packageInfo = packageInfo,
        profile = profile,
        profileKey = profileKey,
        special = special,
    )
}
