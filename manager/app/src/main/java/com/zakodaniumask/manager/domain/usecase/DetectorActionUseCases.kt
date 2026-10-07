package com.zakodaniumask.manager.domain.usecase

import com.zakodaniumask.manager.data.kernel.KernelRepository
import com.zakodaniumask.manager.data.susfs.SuSFSRepository
import com.zakodaniumask.manager.domain.model.DetectorActionKind
import com.zakodaniumask.manager.domain.model.DetectorActionStates

/** Reads the live state of every KernelSU/SuSFS switch the detector page can offer. */
class GetDetectorActionStatesUseCase(
    private val getKernelStatus: GetKernelStatusUseCase,
    private val kernelRepository: KernelRepository,
    private val suSFSRepository: SuSFSRepository,
) {
    suspend operator fun invoke(): DetectorActionStates {
        val kernelStatus = runCatching { getKernelStatus() }.getOrNull()
        val kernelAvailable = kernelStatus?.isRootAvailable == true
        val features = if (kernelAvailable) {
            runCatching { kernelRepository.getFeatureSettings() }.getOrNull()
        } else {
            null
        }

        val susfsStatus = runCatching { suSFSRepository.getStatus() }.getOrNull()
        val config = if (susfsStatus?.enabled == true) {
            runCatching { suSFSRepository.loadConfig() }.getOrNull()
        } else {
            null
        }

        return DetectorActionStates(
            kernelActionsAvailable = kernelAvailable && features != null,
            suEnabled = features?.suEnabled ?: false,
            kernelUmountEnabled = features?.kernelUmountEnabled ?: false,
            selinuxHideEnabled = features?.selinuxHideEnabled ?: false,
            defaultUmountModules = features?.defaultUmountModules ?: false,
            susfsAvailable = config != null,
            susfsEnabled = config?.enabled ?: false,
            susfsHideSusMnts = config?.hide_sus_mnts_for_non_su_procs ?: false,
        )
    }
}

/**
 * Applies a detector quick setting through the same repositories the settings screens use, so the
 * two surfaces never diverge. SuSFS actions only take effect when SuSFS is enabled, and callers must
 * gate them on [DetectorActionStates.susfsAvailable].
 */
class ApplyDetectorActionUseCase(
    private val setSuEnabled: SetSuEnabledUseCase,
    private val setKernelUmountEnabled: SetKernelUmountEnabledUseCase,
    private val setSelinuxHideEnabled: SetSelinuxHideEnabledUseCase,
    private val setDefaultUmountModules: SetDefaultUmountModulesUseCase,
    private val suSFSConfigUseCase: SuSFSConfigUseCase,
) {
    suspend operator fun invoke(kind: DetectorActionKind, enabled: Boolean): Boolean = runCatching {
        when (kind) {
            DetectorActionKind.SELINUX_HIDE -> setSelinuxHideEnabled(enabled) >= 0
            DetectorActionKind.KERNEL_UMOUNT -> setKernelUmountEnabled(enabled)
            DetectorActionKind.DEFAULT_UMOUNT_MODULES -> setDefaultUmountModules(enabled)
            DetectorActionKind.SU_ENABLED -> setSuEnabled(enabled)
            DetectorActionKind.SUSFS_ENABLED -> {
                suSFSConfigUseCase.setConfigEnabled(enabled)
                true
            }

            DetectorActionKind.SUSFS_HIDE_SUS_MNTS -> {
                suSFSConfigUseCase.hideSusMntsForNonSuProcs(enabled)
                true
            }

            DetectorActionKind.SUSFS_UNAME_SPOOF -> false
        }
    }.getOrDefault(false)
}
