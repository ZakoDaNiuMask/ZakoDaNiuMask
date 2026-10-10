package com.zakodaniumask.manager.domain.model

import com.zakodaniumask.manager.KernelVersion
import com.zakodaniumask.manager.Natives.KernelPatchImplementation

data class ManagerRecord(
    val uid: Int,
    val signatureIndex: Int,
)

data class ManagerRuntimeInfo(
    val managers: List<ManagerRecord> = emptyList(),
    val dynamicSignatureEnabled: Boolean = false,
)

data class KernelStatus(
    val isManager: Boolean = false,
    val ksuVersion: Int? = null,
    val managerUAPIVersion: Int = 1,
    val kernelUAPIVersion: Int? = 1,
    val ksuFullVersion: String? = null,
    val lkmMode: Boolean? = null,
    val kernelVersion: KernelVersion,
    val isRootAvailable: Boolean = false,
    val isFullFeatured: Boolean = false,
    val uapiCompatMode: UapiCompatMode = UapiCompatMode.SUPPORTED,
    val isSELinuxPermissive: Boolean = false,
    val isOfficialSignature: Boolean = true,
    val kernelPatchImplementation: KernelPatchImplementation = KernelPatchImplementation.NONE,
    val hookType: String = "",
    val isSafeMode: Boolean = false,
    val isLateLoadMode: Boolean = false,
    val isPrBuild: Boolean = false,
)

/**
 * How the running kernel's UAPI version relates to this manager.
 *
 * The manager adapts to the kernel, so only [TOO_OLD] / [LEGACY] block usage; a
 * [KERNEL_NEWER] kernel is driven with the subset of capabilities this manager knows.
 */
enum class UapiCompatMode {
    /** Kernel UAPI is within [Natives.KERNEL_UAPI_VERSION_MIN, managerUAPIVersion]. */
    SUPPORTED,

    /** Kernel UAPI is newer than the manager; usable but may expose unknown extras. */
    KERNEL_NEWER,

    /** Kernel UAPI is older than the minimum this manager supports. */
    TOO_OLD,

    /** Pre-UAPI-version kernel (reports 0); treated as unusable unless forced. */
    LEGACY,
}

data class KernelFeatureSettings(
    val suEnabled: Boolean,
    val kernelUmountEnabled: Boolean,
    val suLogEnabled: Boolean,
    val selinuxHideEnabled: Boolean,
    val mountHideEnabled: Boolean,
    val samsungCompatEnabled: Boolean,
    val ptctlEnabled: Boolean,
    val uhookEnabled: Boolean,
    val sgSpoofEnabled: Boolean,
    val defaultUmountModules: Boolean,
)
