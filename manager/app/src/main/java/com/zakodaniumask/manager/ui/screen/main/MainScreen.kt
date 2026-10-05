package com.zakodaniumask.manager.ui.screen.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zakodaniumask.manager.ui.activity.component.NavigationBar
import com.zakodaniumask.manager.ui.component.HorizontalPagerWithInteraction
import com.zakodaniumask.manager.ui.rememberMaterial3BlurBackdrop
import com.zakodaniumask.manager.ui.screen.BottomBarDestination
import com.zakodaniumask.manager.ui.theme.ThemeConfig
import com.zakodaniumask.manager.ui.theme.blurSource
import com.zakodaniumask.manager.ui.util.LocalBlurState
import com.zakodaniumask.manager.ui.util.LocalHandlePageChange
import com.zakodaniumask.manager.ui.util.LocalPagerPage
import com.zakodaniumask.manager.ui.util.LocalPagerState
import com.zakodaniumask.manager.ui.util.LocalPortraitState
import com.zakodaniumask.manager.ui.util.LocalSelectedPage
import com.zakodaniumask.manager.ui.util.LocalSnackbarHost
import com.zakodaniumask.manager.ui.viewmodel.HomeViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import top.yukonga.miuix.kmp.utils.PagerGestureNestedScrollConnection
import top.yukonga.miuix.kmp.utils.PagerInterceptionMode
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import top.yukonga.miuix.kmp.utils.pagerGestureOverride


@Composable
fun MainScreen(
    pagerInterceptionMode: Int = PagerInterceptionMode.CrossAxisInterceptor.ordinal,
) {
    val themeConfig: ThemeConfig = koinInject()
    val homeViewModel = koinViewModel<HomeViewModel>()
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val pages = remember(homeState.systemStatus.isFullFeatured) {
        BottomBarDestination.getPages(homeState.systemStatus.isFullFeatured)
    }

    var uiSelectedPage by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(
        initialPage = uiSelectedPage,
        pageCount = { pages.size }
    )

    val pagerMode = PagerInterceptionMode.entries.getOrElse(pagerInterceptionMode) {
        PagerInterceptionMode.Native
    }
    val interceptPagerGestures = pagerMode == PagerInterceptionMode.CrossAxisInterceptor

    val handlePageChange: (Int) -> Unit = { page -> uiSelectedPage = page }

    // Reflect user swipes back into the selection once the pager settles.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage to pagerState.isScrollInProgress }
            .collect { (page, scrolling) -> if (!scrolling) uiSelectedPage = page }
    }

    // Selection (bottom bar, home detector preview) drives the pager.
    LaunchedEffect(pagerState, uiSelectedPage) {
        if (pagerState.currentPage != uiSelectedPage || pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(uiSelectedPage)
        }
    }

    BackHandler(pagerState.currentPage != 0) {
        handlePageChange(0)
    }

    CompositionLocalProvider(
        LocalPagerState provides pagerState,
        LocalHandlePageChange provides handlePageChange,
        LocalSelectedPage provides uiSelectedPage
    ) {
        val content = @Composable { paddingBottom: Dp ->
            HorizontalPagerWithInteraction(
                enableGestureOverride = false,
                modifier = Modifier
                    .fillMaxSize()
                    .pagerGestureOverride(
                        pagerState = pagerState,
                        mode = pagerMode,
                        enabled = true,
                    ),
                state = pagerState,
                userScrollEnabled = !interceptPagerGestures,
                beyondViewportPageCount = 1,
                pageNestedScrollConnection = if (interceptPagerGestures) {
                    PagerGestureNestedScrollConnection
                } else {
                    PagerDefaults.pageNestedScrollConnection(
                        state = pagerState,
                        orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                    )
                },
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    snapAnimationSpec = PagerNavigationSpringSpec,
                ),
            ) { pageIndex ->
                if (pages.isEmpty()) return@HorizontalPagerWithInteraction

                val snackBarHostState = remember { SnackbarHostState() }
                CompositionLocalProvider(
                    LocalSnackbarHost provides snackBarHostState,
                    LocalPagerPage provides pageIndex,
                    LocalBlurState provides rememberMaterial3BlurBackdrop(
                        enableBlur = themeConfig.isEnableBlur,
                        pagerState = pagerState,
                        pagerPage = pageIndex,
                    ),
                ) {
                    val destination = pages[pageIndex]
                    destination.direction(paddingBottom)
                }
            }
        }

        if (LocalPortraitState.current) {
            Scaffold(
                // The child pages own their top-bar insets. The outer scaffold only reserves the
                // measured bottom navigation bar height for the pager content.
                contentWindowInsets = WindowInsets(),
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    NavigationBar(
                        destinations = pages,
                        isBottomBar = true,
                    )
                },
                containerColor = Color.Transparent,
            ) { innerPadding ->
                Box(
                    modifier = Modifier.blurSource()
                ) {
                    content(innerPadding.calculateBottomPadding())
                }
            }
        } else {
            var navWidth by remember { mutableIntStateOf(0) }
            val density = LocalDensity.current

            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .blurSource()
                ) {
                    Spacer(
                        modifier = Modifier.width(
                            with(density) { navWidth.toDp() }
                        )
                    )

                    Box(Modifier.weight(1f)) {
                        content(0.dp)
                    }
                }

                NavigationBar(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .onSizeChanged {
                            navWidth = it.width
                        },
                    destinations = pages,
                    isBottomBar = false,
                )
            }
        }
    }
}
