package com.zakodaniumask.manager.ghostlock.ui

import com.zakodaniumask.manager.ghostlock.R
import com.zakodaniumask.manager.ghostlock.domain.model.CpuPair
import com.zakodaniumask.manager.ghostlock.domain.model.ExecutionFieldValue
import com.zakodaniumask.manager.ghostlock.domain.model.ProfileFieldNode
import com.zakodaniumask.manager.ghostlock.domain.model.RootlessStatus
import com.zakodaniumask.manager.ghostlock.domain.model.UserProfileFile

data class GhostlockUiState(
    val deviceName: String = "",
    val kernelRelease: String = "",
    val socName: String = "",
    val kernelSupported: Boolean = false,
    val rootlessEnabled: Boolean = false,
    val rootlessStatus: RootlessStatus = RootlessStatus.NOT_REQUIRED,
    val running: Boolean = false,
    val cpuPairLabels: List<String> = emptyList(),
    val cpuPairIndex: Int = 0,
    /** CPU pair from the resolved profile when it differs from the device pick. */
    val customCpuPair: CpuPair? = null,
    val safeModeEnabled: Boolean = false,
    val forceAttackTestEnabled: Boolean = false,
    val tcpRouteEnabled: Boolean = true,
    val compact: Boolean = false,
    val executionSheetVisible: Boolean = false,
    val executionSheetDismissible: Boolean = false,
    val dialogVisible: Boolean = false,
    val dialogType: DialogType = DialogType.NONE,
    val dialogTitleRes: Int = 0,
    val dialogMessage: String = "",
    val dialogMessageRes: Int = 0,
    val dialogItems: List<String> = emptyList(),
    val dialogItemResIds: List<Int> = emptyList(),
    val dialogCurrentItemIndex: Int = -1,
    val dialogInput: String = "",
    val dialogConfirmLabelRes: Int = R.string.parse_start,
    /** Documentation URL shown as an extra button on a NOTICE dialog. */
    val dialogDocUrl: String? = null,
    val overwriteDialogVisible: Boolean = false,
    val overwriteMessage: String = "",
    val logLines: List<GhostlockLogLine> = emptyList(),
    val executionRelease: String = "",
    val executionHasProfile: Boolean = false,
    val executionFields: List<ExecutionFieldValue> = emptyList(),
    val executionEditing: Map<String, String> = emptyMap(),
    val advancedScreenVisible: Boolean = false,
    val debugExportEnabled: Boolean = true,
    val debugExportLocation: String = "",
    val debugKernelLogEnabled: Boolean = true,
    val aboutVisible: Boolean = false,
    val parametersVisible: Boolean = false,
    val profileOverrideVisible: Boolean = false,
    val advancedOverrideVisible: Boolean = false,
    /** Stored document edited by the open session; null for the builtin. */
    val editTargetName: String? = null,
    val profileOverrideRelease: String = "",
    val profileOverrideRoots: List<ProfileFieldNode> = emptyList(),
    val profileOverrideEditing: Map<String, String> = emptyMap(),
    /** Controller-reported geometry violations, dotted paths. */
    val profileInvalidPaths: Set<String> = emptySet(),
    /** Explicit route from the profile; null means geometry inference. */
    val profileRoute: String? = null,
    /** Declared fallback route; null/"none" means disabled. */
    val profileFallback: String? = null,
    /** Manually selected builtin source; null means automatic matching. */
    val activeBuiltinProfile: String? = null,
    val builtinScreenVisible: Boolean = false,
    /** Unfilled reference templates, listed separately on the builtin picker. */
    val builtinTemplates: List<String> = emptyList(),
    /** Builtin releases sorted by similarity to the device kernel. */
    val builtinProfiles: List<String> = emptyList(),
    val loadConfigVisible: Boolean = false,
    /** Verbatim documents in the user profile folder, newest first. */
    val userProfiles: List<UserProfileFile> = emptyList(),
    /** Loaded user document feeding the imported layer; null means none. */
    val activeUserProfile: String? = null,
    /** File name of the open user-profile detail screen, null when closed. */
    val userProfileDetail: String? = null,
    val userProfileRenameTarget: String? = null,
    val userProfileDeleteTarget: String? = null,
)

enum class DialogType { NONE, LIST, INPUT, CONFIRM, NOTICE }

data class GhostlockLogLine(val text: String, val color: Int)

interface GhostlockActions {
    fun onRun()
    fun onProfileInvalid()
    fun onStatusClick()
    fun onCloseExecutionSheet()
    fun onCopyLogs()
    fun onImportOffsetsHocon()
    fun onImportOffsetsJson()
    fun onDocumentsResult(request: DocumentRequest, uris: List<String>)
    fun onParseOta()
    fun onParseImage()
    fun onCpuPairSelected(index: Int)
    fun onSafeModeChanged(enabled: Boolean)
    fun onForceAttackTestChanged(enabled: Boolean)
    fun onRootlessChanged(enabled: Boolean)
    fun onDialogItemSelected(index: Int)
    fun onDialogInputChange(value: String)
    fun onDialogConfirm(value: String)
    fun onDialogDismiss()
    fun onDialogDismissFinished()
    fun onOverwriteConfirm()
    fun onOverwriteDismiss()
    fun onExecutionFieldChanged(path: String, value: String)
    fun onRouteChanged(index: Int)
    fun onFallbackChanged(index: Int)
    fun onExportProfile()
    fun onSaveProfileEdits()
    fun onSaveProfileAs()
    fun onExportProfileEdits()
    fun onRevertProfileEdits()
    fun onOpenAdvanced()
    fun onCloseAdvanced()
    fun onShowAbout()
    fun onCloseAbout()
    fun onDebugExportChanged(enabled: Boolean)
    fun onDebugExportLocationPick()
    fun onDebugKernelLogChanged(enabled: Boolean)
    fun onOpenParameters()
    fun onCloseParameters()
    fun onOpenLoadConfig()
    fun onCloseLoadConfig()
    fun onOpenUserProfileDetail(name: String)
    fun onCloseUserProfileDetail()
    fun onLoadUserProfile(name: String)
    fun onUnloadUserProfile()
    fun onEditUserProfile(name: String)
    fun onUserProfileRename(name: String)
    fun onUserProfileExport(name: String)
    fun onConvertUserProfile(name: String)
    fun onUserProfileDelete(name: String)
    fun onUserProfileDeleteConfirm()
    fun onUserProfileDeleteDismiss()
    fun onOpenBuiltinProfiles()
    fun onCloseBuiltinProfiles()
    fun onSelectBuiltinProfile(release: String?)
    fun onOpenProfileOverrides()
    fun onCloseProfileOverrides()
    fun onOpenAdvancedOverrides()
    fun onCloseAdvancedOverrides()
    fun onProfileOverrideChanged(path: String, value: String)
}
