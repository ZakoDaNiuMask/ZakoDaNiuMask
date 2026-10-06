package com.zakodaniumask.manager.domain.model

/** Which privileged backend is active. Root always takes precedence when it is available. */
enum class PrivBackend {
    ROOT,
    ROOTLESS,
    NONE,
}

data class ShellResult(
    val code: Int,
    val stdout: String,
    val stderr: String,
)
