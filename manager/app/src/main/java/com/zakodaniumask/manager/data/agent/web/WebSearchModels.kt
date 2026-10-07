// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.data.agent.web

/** A configurable web-search backend. */
enum class WebSearchBackend(val id: String, val label: String) {
    DUCKDUCKGO("duckduckgo", "DuckDuckGo"),
    SEARXNG("searxng", "SearXNG"),
    TAVILY("tavily", "Tavily"),
    BRAVE("brave", "Brave"),
    SERPER("serper", "Serper");

    companion object {
        fun fromId(id: String): WebSearchBackend =
            entries.firstOrNull { it.id == id } ?: DUCKDUCKGO
    }
}

/** How `web.fetch` converts a page to text. */
enum class WebFetchReader(val id: String, val label: String) {
    AUTO("auto", "Auto"),
    JINA("jina", "Jina Reader"),
    LOCAL("local", "Local parser");

    companion object {
        fun fromId(id: String): WebFetchReader =
            entries.firstOrNull { it.id == id } ?: AUTO
    }
}

data class SearchResult(
    val title: String,
    val url: String,
    val snippet: String,
)

/** Web-search settings resolved from [com.zakodaniumask.manager.data.agent.AgentSettings]. */
data class WebConfig(
    val enabled: Boolean,
    val backend: WebSearchBackend,
    val endpoint: String,
    val apiKey: String,
    val maxResults: Int,
    val fetchReader: WebFetchReader,
    val fetchApiKey: String,
    val allowLocal: Boolean,
)
