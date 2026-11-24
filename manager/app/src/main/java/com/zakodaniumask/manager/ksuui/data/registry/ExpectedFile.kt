package com.zakodaniumask.manager.ksuui.data.registry

/** Minimal placeholder for the KernelSU registry download verifier. */
data class ExpectedFile(
    val size: Long = 0L,
    val sha256: String = "",
)
