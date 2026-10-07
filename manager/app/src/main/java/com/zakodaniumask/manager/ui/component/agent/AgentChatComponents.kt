// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.ui.component.agent

import android.content.ClipData as AndroidClipData
import android.content.ClipboardManager as AndroidClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.ui.markdown.MarkdownText
import com.zakodaniumask.manager.ui.viewmodel.AgentChatItem
import com.zakodaniumask.manager.ui.viewmodel.AgentToolStatus

/** Renders one agent chat transcript item. Extracted from the agent screen. */
@Composable
fun AgentChatItemRow(item: AgentChatItem) {
    when (item) {
        is AgentChatItem.User -> AgentMessageBubble(
            text = item.text,
            container = MaterialTheme.colorScheme.primaryContainer,
            alignEnd = true,
        )

        is AgentChatItem.Assistant -> AgentMessageBubble(
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

        is AgentChatItem.Thinking -> AgentThinkingBubble(item.text)

        is AgentChatItem.ToolCall -> AgentToolCallBubble(item)
    }
}

@Composable
fun AgentMessageBubble(
    text: String,
    container: Color,
    alignEnd: Boolean,
    markdown: Boolean = false,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Surface(
            color = container,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(0.9f),
        ) {
            SelectionContainer {
                if (markdown) {
                    MarkdownText(
                        markdown = text,
                        modifier = Modifier.padding(12.dp),
                    )
                } else {
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

@Composable
fun AgentThinkingBubble(text: String) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentToolCallBubble(item: AgentChatItem.ToolCall) {
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
                text = "${item.tool} · ${toolStatusLabel(item.status)}",
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
                    text = toolStatusLabel(item.status),
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
private fun toolStatusLabel(status: AgentToolStatus): String = stringResource(
    when (status) {
        AgentToolStatus.PENDING -> R.string.agent_status_pending
        AgentToolStatus.RUNNING -> R.string.agent_status_running
        AgentToolStatus.DONE -> R.string.agent_status_done
        AgentToolStatus.ERROR -> R.string.agent_status_error
        AgentToolStatus.DENIED -> R.string.agent_status_denied
    },
)
