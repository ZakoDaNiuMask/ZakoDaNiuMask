package com.zakodaniumask.manager.ksuui.ui.screen.module

import android.content.Intent
import android.net.Uri
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
import com.zakodaniumask.manager.domain.model.InstalledModule
import com.zakodaniumask.manager.ksuui.KsuServiceLocator
import com.zakodaniumask.manager.ksuui.data.model.Module
import com.zakodaniumask.manager.ksuui.data.model.ModuleUpdateInfo
import com.zakodaniumask.manager.ksuui.ui.component.SearchStatus
import com.zakodaniumask.manager.ksuui.ui.util.module.Shortcut
import com.zakodaniumask.manager.ui.navigation.Navigator
import com.zakodaniumask.manager.ui.navigation.Route
import com.zakodaniumask.manager.ui.viewmodel.ModuleUiAction
import com.zakodaniumask.manager.ui.viewmodel.ModuleUiState as CurrentModuleUiState
import com.zakodaniumask.manager.ui.viewmodel.ModuleViewModel
import com.zakodaniumask.manager.ui.webui.WebUIActivity

@Immutable
data class KsuModuleController(
    val state: ModuleUiState,
    val actions: ModuleActions,
)

@Composable
fun rememberKsuModuleController(
    viewModel: ModuleViewModel,
    currentState: CurrentModuleUiState,
    navigator: Navigator,
): KsuModuleController {
    val context = LocalContext.current
    val searchLabel = stringResource(R.string.search_modules)
    var searchStatus by remember { mutableStateOf(SearchStatus(label = searchLabel)) }
    var sortEnabledFirst by remember { mutableStateOf(false) }
    var sortActionFirst by remember { mutableStateOf(false) }

    LaunchedEffect(currentState.search) {
        searchStatus = searchStatus.copy(searchText = currentState.search)
    }

    val modules = remember(currentState.moduleList) { currentState.moduleList.map { it.toKsuModule() } }
    val updateInfo = remember(currentState.moduleList) {
        currentState.moduleList.mapNotNull { current ->
            val update = current.moduleUpdate ?: return@mapNotNull null
            current.id to ModuleUpdateInfo(
                downloadUrl = update.zipUrl,
                version = update.version,
                changelog = update.changelog,
            )
        }.toMap()
    }
    val visibleModules = remember(modules, sortEnabledFirst, sortActionFirst) {
        when {
            sortEnabledFirst -> modules.sortedWith(
                compareByDescending<Module> { it.enabled }.thenBy { it.name.lowercase() },
            )

            sortActionFirst -> modules.sortedWith(
                compareByDescending<Module> { it.hasActionScript }.thenBy { it.name.lowercase() },
            )

            else -> modules
        }
    }
    val searchResults = if (currentState.search.isNotBlank()) visibleModules else emptyList()

    val actions = remember(
        viewModel,
        navigator,
        context,
        sortEnabledFirst,
        sortActionFirst,
        currentState.search,
        searchStatus,
    ) {
        ModuleActions(
            onRefresh = { viewModel.dispatch(ModuleUiAction.Refresh(manual = true)) },
            onSearchStatusChange = { searchStatus = it },
            onSearchTextChange = { text ->
                viewModel.dispatch(ModuleUiAction.Search(text))
                searchStatus = searchStatus.copy(
                    searchText = text,
                    current = if (text.isBlank()) {
                        SearchStatus.Status.COLLAPSED
                    } else {
                        SearchStatus.Status.EXPANDED
                    },
                )
            },
            onClearSearch = {
                viewModel.dispatch(ModuleUiAction.Search(""))
                searchStatus = searchStatus.copy(
                    searchText = "",
                    current = SearchStatus.Status.COLLAPSED,
                )
            },
            onRequestUpdateConfirmation = { _, info ->
                navigator.push(Route.Flash.moduleUpdate(info.downloadUrl))
                viewModel.dispatch(ModuleUiAction.MarkNeedRefresh)
            },
            onRequestUninstallConfirmation = { module ->
                viewModel.dispatch(ModuleUiAction.SetRemoved(module.id, true))
            },
            onDismissConfirmRequest = {},
            onConfirmUpdate = {},
            onOpenRepo = { navigator.push(Route.ModuleRepo) },
            onToggleSortActionFirst = {
                sortActionFirst = !sortActionFirst
                sortEnabledFirst = false
            },
            onToggleSortEnabledFirst = {
                sortEnabledFirst = !sortEnabledFirst
                sortActionFirst = false
            },
            onOpenWebUi = { module ->
                runCatching {
                    context.startActivity(
                        Intent(context, WebUIActivity::class.java).setData(
                            Shortcut.buildShortcutUri(module.id, ShortcutType.WebUI),
                        ),
                    )
                }
            },
            onToggleModule = { module ->
                viewModel.dispatch(ModuleUiAction.SetEnabled(module.id, !module.enabled))
            },
            onUninstallModule = { module ->
                viewModel.dispatch(ModuleUiAction.SetRemoved(module.id, true))
            },
            onUndoUninstallModule = { module ->
                viewModel.dispatch(ModuleUiAction.SetRemoved(module.id, false))
            },
            onOpenFlash = { uris: List<Uri> ->
                if (uris.isNotEmpty()) {
                    navigator.push(Route.Flash.modules(uris.map(Uri::toString)))
                    viewModel.dispatch(ModuleUiAction.MarkNeedRefresh)
                }
            },
            onExecuteModuleAction = { module ->
                navigator.push(Route.ExecuteModuleAction(module.id))
                viewModel.dispatch(ModuleUiAction.MarkNeedRefresh)
            },
        )
    }

    val state = ModuleUiState(
        isRefreshing = currentState.isRefreshing,
        hasLoaded = true,
        modules = modules,
        moduleList = visibleModules,
        updateInfo = updateInfo,
        searchStatus = searchStatus,
        searchResults = searchResults,
        sortEnabledFirst = sortEnabledFirst,
        sortActionFirst = sortActionFirst,
        checkModuleUpdate = runCatching {
            KsuServiceLocator.settingsRepository.checkModuleUpdate
        }.getOrDefault(true),
        isSafeMode = runCatching { Natives.isSafeMode }.getOrDefault(false),
        magiskInstalled = currentState.hasMagisk,
        confirmDialogState = null,
    )

    return remember(state, actions) { KsuModuleController(state, actions) }
}

private fun InstalledModule.toKsuModule(): Module = Module(
    id = id,
    name = name,
    author = author,
    version = version,
    versionCode = versionCode,
    description = description,
    enabled = enabled,
    update = update,
    remove = remove,
    updateJson = updateJson,
    hasWebUi = hasWebUi,
    hasActionScript = hasActionScript,
    metamodule = metamodule,
    actionIconPath = actionIconPath,
    webUiIconPath = webUiIconPath,
    preinit = "",
)
