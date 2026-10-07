package com.zakodaniumask.manager.domain.model

/** Detector sections that can carry a quick-settings action. */
enum class DetectorSection {
    SU,
    BOOTLOADER,
    TEE,
    SYSTEM_PROPERTIES,
    KERNEL_CHECK,
    SELINUX,
}

/**
 * A KernelSU/SuSFS hiding switch that a detector finding can offer as a quick setting.
 *
 * [enabled] is null for jump-only actions (for example SuSFS `uname` spoofing, which needs values).
 */
enum class DetectorActionKind {
    SELINUX_HIDE,
    KERNEL_UMOUNT,
    DEFAULT_UMOUNT_MODULES,
    SU_ENABLED,
    SUSFS_ENABLED,
    SUSFS_HIDE_SUS_MNTS,
    SUSFS_UNAME_SPOOF,
}

data class DetectorAction(
    val kind: DetectorActionKind,
    val supported: Boolean,
    val enabled: Boolean? = null,
)

/** Live state of every quick-setting the detector page may offer. */
data class DetectorActionStates(
    val kernelActionsAvailable: Boolean = false,
    val suEnabled: Boolean = false,
    val kernelUmountEnabled: Boolean = false,
    val selinuxHideEnabled: Boolean = false,
    val defaultUmountModules: Boolean = false,
    val susfsAvailable: Boolean = false,
    val susfsEnabled: Boolean = false,
    val susfsHideSusMnts: Boolean = false,
)
