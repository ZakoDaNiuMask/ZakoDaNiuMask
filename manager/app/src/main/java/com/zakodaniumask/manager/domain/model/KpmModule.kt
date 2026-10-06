package com.zakodaniumask.manager.domain.model

data class KpmModule(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val args: String,
)

data class KpmStatus(
    val supported: Boolean = false,
    val version: String = "",
)
