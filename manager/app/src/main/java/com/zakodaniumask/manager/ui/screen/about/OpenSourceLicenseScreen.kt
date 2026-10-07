package com.zakodaniumask.manager.ui.screen.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.LocalPolice
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.util.author
import com.mikepenz.aboutlibraries.util.withJson
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.ui.component.WarningCard
import com.zakodaniumask.manager.ui.component.settings.AppBackButton
import com.zakodaniumask.manager.ui.component.settings.SettingsBaseWidget
import com.zakodaniumask.manager.ui.component.settings.lazySegmentColumn
import com.zakodaniumask.manager.ui.navigation.LocalNavigator
import com.zakodaniumask.manager.ui.screen.LabelText
import com.zakodaniumask.manager.ui.theme.CardConfig
import com.zakodaniumask.manager.ui.theme.ThemeConfig
import com.zakodaniumask.manager.ui.theme.blurEffect
import com.zakodaniumask.manager.ui.theme.blurSource
import com.zakodaniumask.manager.ui.util.adaptiveScaffoldWindowInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

private data class PortedComponent(
    val name: String,
    val licenseName: String,
    val website: String,
)

private val PORTED_COMPONENTS = listOf(
    PortedComponent(
        name = "Duck Detector",
        licenseName = "Apache-2.0",
        website = "https://github.com/eltavine/Duck-Detector-Refactoring",
    ),
    PortedComponent(
        name = "YukiSU",
        licenseName = "GPL-3.0",
        website = "https://github.com/Rouyashiki/YukiSU",
    ),
    PortedComponent(
        name = "BakaSU (ReSukiSU)",
        licenseName = "GPL-3.0",
        website = "https://github.com/Baka-SU/BakaSU",
    ),
    PortedComponent(
        name = "SukiSU-Ultra",
        licenseName = "GPL-3.0",
        website = "https://github.com/SukiSU-Ultra/SukiSU-Ultra",
    ),
    PortedComponent(
        name = "JingMatrix/KernelSU",
        licenseName = "GPL-3.0",
        website = "https://github.com/JingMatrix/KernelSU",
    ),
    PortedComponent(
        name = "FolkPatch",
        licenseName = "GPL-3.0",
        website = "https://github.com/LyraVoid/FolkPatch",
    ),
    PortedComponent(
        name = "AxManager (Axeron)",
        licenseName = "Apache-2.0",
        website = "https://github.com/fahrez182/AxManager",
    ),
    PortedComponent(
        name = "GhostLock",
        licenseName = "Apache-2.0",
        website = "https://github.com/YuKongA/ghostlock-app",
    ),
    PortedComponent(
        name = "NebuSec CyberMeowfia",
        licenseName = "Apache-2.0",
        website = "https://github.com/NebuSec/CyberMeowfia",
    ),
    PortedComponent(
        name = "JoinChang ghostlock-oneplus",
        licenseName = "Apache-2.0",
        website = "https://github.com/JoinChang/ghostlock-oneplus",
    ),
    PortedComponent(
        name = "x-spy CVE-2026-43499-popsicle",
        licenseName = "Apache-2.0",
        website = "https://github.com/x-spy/CVE-2026-43499-popsicle",
    ),
    PortedComponent(
        name = "Lua",
        licenseName = "MIT",
        website = "https://www.lua.org/",
    ),
)


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OpenSourceLicenseScreen() {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val navigator = LocalNavigator.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState(
            initialHeightOffset = -154f,
            initialHeightOffsetLimit = -154f // from debugger
        )
    )

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
    }

    // from https://github.com/mikepenz/AboutLibraries#setup
    val context = LocalContext.current
    val libraries by produceState(initialValue = Libs(emptyList(), emptySet()), context) {
        value = withContext(Dispatchers.IO) {
            Libs.Builder().withJson(context, R.raw.aboutlibraries).build()
        }
    }

    var selectedLibrary by remember { mutableStateOf<Library?>(null) }
    var selectedPorted by remember { mutableStateOf<PortedComponent?>(null) }

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
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                title = { Text(text = stringResource(id = R.string.open_source_license)) },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    AppBackButton(
                        onClick = { navigator.pop() },
                        icon = Icons.AutoMirrored.TwoTone.ArrowBack,
                        modifier = Modifier.size(36.dp),
                        containerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = 0.1f
                        )
                    )
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
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .blurSource(),
            contentPadding = paddingValues
        ) {
            item {
                Text(
                    text = stringResource(R.string.open_source_license_ported),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            lazySegmentColumn(PORTED_COMPONENTS, key = { _, it -> it.name }) { _, component ->
                SettingsBaseWidget(
                    iconPlaceholder = false,
                    title = component.name,
                    description = "${component.licenseName} · ${component.website}",
                    onClick = { selectedPorted = component }
                )
            }
            item {
                Text(
                    text = stringResource(R.string.open_source_license_libraries),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            lazySegmentColumn(libraries.libraries) { _, lib ->
                SettingsBaseWidget(
                    iconPlaceholder = false,
                    title = lib.name,
                    description = lib.author,
                    descriptionColumnContent = {
                        Row {
                            lib.licenses.forEach {
                                LabelText(it.name)
                            }
                        }
                    },
                    onClick = {
                        selectedLibrary = lib
                    }
                ) {
                    lib.artifactVersion?.let {
                        Text(it)
                    }
                }
            }
        }
        if (selectedPorted != null) {
            val component = selectedPorted!!
            val uriHandler = LocalUriHandler.current
            AlertDialog(
                onDismissRequest = { selectedPorted = null },
                confirmButton = {
                    Button(onClick = { selectedPorted = null }) {
                        Text(stringResource(R.string.close))
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = {
                        uriHandler.openUri(component.website)
                    }) {
                        Text(stringResource(R.string.visit_home_page))
                    }
                },
                title = {
                    Text(
                        text = component.name,
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                text = {
                    Column {
                        Text(
                            text = component.licenseName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = component.website,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
            )
        }
        if (selectedLibrary != null) {
            val library = selectedLibrary!!
            val uriHandler = LocalUriHandler.current
            AlertDialog(
                onDismissRequest = { selectedLibrary = null },
                confirmButton = {
                    Button(onClick = { selectedLibrary = null }) {
                        Text(stringResource(R.string.close))
                    }
                },
                dismissButton = {
                    library.website.let { url ->
                        OutlinedButton(onClick = {
                            uriHandler.openUri(url!!)
                        }) {
                            Text(stringResource(R.string.visit_home_page))
                        }
                    }
                },
                title = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = library.name,
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                },
                text = {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            WarningCard(
                                color = MaterialTheme.colorScheme.tertiary,
                                renderBackground = false,
                                icon = {
                                    Icon(
                                        imageVector = Icons.TwoTone.LocalPolice,
                                        contentDescription = null,
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                message = stringResource(
                                    R.string.license,
                                    library.licenses.joinToString(separator = ", ") { it.name }),
                            )
                        }

                        items(library.licenses.toList()) { license ->
                            OutlinedCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceBright
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Row {
                                        Text(
                                            text = license.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    license.url?.let { url ->
                                                        uriHandler.openUri(url)
                                                    }
                                                }
                                        )
                                    }

                                    Spacer(modifier = Modifier.size(8.dp))

                                    Text(
                                        text = license.licenseContent
                                            ?: stringResource(R.string.no_license_text),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                properties = DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}
