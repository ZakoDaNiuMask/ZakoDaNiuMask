package com.zakodaniumask.manager.domain.model

/** Boot stage at which the user startup script is executed by ksud. */
enum class BootScriptStage(val id: String) {
    POST_FS_DATA("post-fs-data"),
    SERVICE("service"),
    BOOT_COMPLETED("boot-completed");

    companion object {
        fun fromId(id: String?): BootScriptStage =
            entries.firstOrNull { it.id == id } ?: POST_FS_DATA
    }
}

data class BootScriptSettings(
    val enabled: Boolean = false,
    val stage: BootScriptStage = BootScriptStage.POST_FS_DATA,
    val content: String = DEFAULT_BOOT_SCRIPT,
) {
    companion object {
        const val DEFAULT_BOOT_SCRIPT = "#!/system/bin/sh\n\n"
    }
}
