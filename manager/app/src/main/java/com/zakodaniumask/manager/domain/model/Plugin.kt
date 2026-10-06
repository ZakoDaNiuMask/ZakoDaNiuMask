package com.zakodaniumask.manager.domain.model

data class PluginConfigField(
    val key: String,
    val label: String,
    val labels: Map<String, String>,
    val type: String,
    val default: String,
    val options: List<String>,
)

data class PluginQuickAction(
    val function: String,
    val label: String,
    val labels: Map<String, String>,
)

data class PluginInfo(
    val id: String,
    val name: String,
    val author: String,
    val version: String,
    val description: String,
    val descriptions: Map<String, String>,
    val license: String,
    val enabled: Boolean,
    val hasManifest: Boolean,
    val hasAction: Boolean,
    val quickAction: PluginQuickAction?,
    val config: List<PluginConfigField>,
)

data class OnlinePlugin(
    val name: String,
    val version: String,
    val url: String,
    val description: String,
)
