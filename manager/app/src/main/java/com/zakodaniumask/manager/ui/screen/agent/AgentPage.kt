// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.ui.screen.agent

import android.content.ClipData as AndroidClipData
import android.content.ClipboardManager as AndroidClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Send
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.History
import androidx.compose.material.icons.twotone.Forum
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.data.agent.AgentRunService
import com.zakodaniumask.manager.data.agent.AgentSessionMeta
import com.zakodaniumask.manager.data.agent.mcp.ToolTier
import com.zakodaniumask.manager.ui.markdown.MarkdownText
import com.zakodaniumask.manager.ui.navigation.LocalNavigator
import com.zakodaniumask.manager.ui.navigation.Route
import com.zakodaniumask.manager.ui.theme.CardConfig
import com.zakodaniumask.manager.ui.theme.ThemeConfig
import com.zakodaniumask.manager.ui.theme.blurEffect
import com.zakodaniumask.manager.ui.theme.blurSource
import com.zakodaniumask.manager.ui.util.adaptiveScaffoldWindowInsets
import com.zakodaniumask.manager.ui.viewmodel.AgentChatItem
import com.zakodaniumask.manager.ui.viewmodel.AgentToolStatus
import com.zakodaniumask.manager.ui.viewmodel.AgentViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AgentPage(bottomPadding: Dp = 0.dp) {
    val viewModel: AgentViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val confirm by viewModel.confirm.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val listState = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSessions by remember { mutableStateOf(false) }
    var sessions by remember { mutableStateOf<List<AgentSessionMeta>>(emptyList()) }

    LaunchedEffect(state.isRunning) {
        if (state.isRunning) AgentRunService.start(context) else AgentRunService.stop(context)
    }
    LaunchedEffect(showSessions) {
        if (showSessions) sessions = viewModel.listSessions()
    }

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(state.items.size) {
        if (state.items.isNotEmpty()) {
            listState.animateScrollToItem(state.items.lastIndex)
        }
    }

    confirm?.let { request ->
        AlertDialog(
            onDismissRequest = { viewModel.resolveConfirm(false) },
            title = { Text(stringResource(R.string.agent_confirm_title)) },
            text = {
                Column {
                    Text(
                        stringResource(
                            R.string.agent_confirm_message,
                            request.tool,
                            if (request.tier == ToolTier.DANGER) {
                                stringResource(R.string.agent_tier_danger)
                            } else {
                                stringResource(R.string.agent_tier_write)
                            },
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = request.arguments,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.resolveConfirm(true) }) {
                    Text(stringResource(R.string.agent_confirm_allow))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.resolveConfirm(false) }) {
                    Text(stringResource(R.string.agent_confirm_deny))
                }
            },
        )
    }

    if (showSessions) {
        AlertDialog(
            onDismissRequest = { showSessions = false },
            title = { Text(stringResource(R.string.agent_sessions)) },
            text = {
                Column {
                    TextButton(onClick = {
                        viewModel.clearConversation()
                        showSessions = false
                    }) { Text(stringResource(R.string.agent_new_chat)) }
                    if (sessions.isEmpty()) {
                        Text(stringResource(R.string.agent_sessions_empty))
                    } else {
                        sessions.forEach { meta ->
                            Text(
                                text = meta.title.ifBlank { meta.id },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            viewModel.openSession(meta.id)
                                            showSessions = false
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSessions = false }) {
                    Text(stringResource(R.string.agent_confirm_deny))
                }
            },
        )
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
                title = { Text(stringResource(R.string.agent)) },
                actions = {
                    IconButton(onClick = { showSessions = true }) {
                        Icon(Icons.TwoTone.Forum, contentDescription = null)
                    }
                    IconButton(onClick = { navigator.push(Route.AgentAudit) }) {
                        Icon(Icons.TwoTone.History, contentDescription = null)
                    }
                    IconButton(onClick = { viewModel.clearConversation() }) {
                        Icon(Icons.TwoTone.Delete, contentDescription = null)
                    }
                    IconButton(onClick = { navigator.push(Route.AgentSettings) }) {
                        Icon(Icons.TwoTone.Settings, contentDescription = null)
                    }
                },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .blurSource()
                .padding(top = paddingValues.calculateTopPadding())
                .imePadding(),
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 8.dp,
                ),
            ) {
                if (state.items.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.agent_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
                items(state.items, key = { it.id }) { item ->
                    AgentItemRow(item)
                }
                if (state.isRunning) {
                    item {
                        Text(
                            text = stringResource(R.string.agent_thinking),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            state.error?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    placeholder = { Text(stringResource(R.string.agent_input_hint)) },
                    enabled = !state.isRunning,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val text = input
                        input = ""
                        viewModel.send(text)
                    },
                    enabled = !state.isRunning && input.isNotBlank(),
                ) {
                    Icon(Icons.AutoMirrored.TwoTone.Send, contentDescription = null)
                }
            }
            Spacer(Modifier.height(8.dp + bottomPadding))
        }
    }
}

@Composable
private fun AgentItemRow(item: AgentChatItem) {
    when (item) {
        is AgentChatItem.User -> Bubble(
            text = item.text,
            container = MaterialTheme.colorScheme.primaryContainer,
            alignEnd = true,
        )

        is AgentChatItem.Assistant -> Bubble(
            text = item.text,
            container = MaterialTheme.colorScheme.surfaceContainerHigh,
            alignEnd = false,
            markdown = true,
        )

        is AgentChatItem.Info -> Text(
            text = item.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        is AgentChatItem.Thinking -> ThinkingBubble(item.text)

        is AgentChatItem.ToolCall -> ToolCallBubble(item)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolCallBubble(item: AgentChatItem.ToolCall) {
    val context = LocalContext.current
    var showDetail by remember { mutableStateOf(false) }
    val color = when (item.status) {
        AgentToolStatus.ERROR, AgentToolStatus.DENIED -> MaterialTheme.colorScheme.errorContainer
        AgentToolStatus.RUNNING -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    Surface(
        color = color,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDetail = true },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "${item.tool} · ${item.status.name.lowercase()}",
                style = MaterialTheme.typography.labelMedium,
            )
            if (item.arguments.isNotBlank() && item.arguments != "{}") {
                Text(
                    text = item.arguments,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (item.result.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.result.take(200),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    if (showDetail) {
        ModalBottomSheet(onDismissRequest = { showDetail = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
            ) {
                Text(
                    text = item.tool,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = item.status.name.lowercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.arguments.isNotBlank() && item.arguments != "{}") {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.agent_tool_arguments),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    SelectionContainer {
                        Text(item.arguments, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (item.result.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.agent_tool_result),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as AndroidClipboardManager
                            cm.setPrimaryClip(AndroidClipData.newPlainText("result", item.result))
                        }) {
                            Text(stringResource(R.string.agent_tool_copy_result))
                        }
                    }
                    SelectionContainer {
                        Text(item.result, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingBubble(text: String) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = (if (expanded) "\u25be " else "\u25b8 ") +
                    stringResource(R.string.agent_thinking_stream),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (expanded) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Bubble(text: String, container: Color, alignEnd: Boolean, markdown: Boolean = false) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Surface(
            color = container,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(0.9f),
        ) {
            if (markdown) {
                SelectionContainer {
                    MarkdownText(
                        markdown = text,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            } else {
                SelectionContainer {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}
