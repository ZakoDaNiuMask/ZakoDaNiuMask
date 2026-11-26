package com.zakodaniumask.manager.sukiui.ui.util

import androidx.compose.runtime.Composable

/** Phase 1 compat: custom text storage is not ported yet; use sensible defaults. */
enum class CustomTextKey(val default: String) {
    HOME_TITLE("Baka MD3E"),
    HOME_STATUS_WORKING("Working"),
    HOME_STATUS_INSTALL_HINT("Tap to install"),
    HOME_BTN_INSTALL("Install"),
    HOME_TAG_LKM("LKM"),
    HOME_TAG_SAFEMODE("Safe mode"),
    HOME_STATS_SUPERUSER("Superuser"),
    HOME_STATS_MODULE("Modules"),
    HOME_INFO_KERNEL("Kernel"),
    HOME_INFO_KERNEL_FULL("Kernel full version"),
    HOME_INFO_MANAGER_VERSION("Manager"),
    HOME_INFO_SELINUX("SELinux"),
    HOME_INFO_SECCOMP("Seccomp"),
    HOME_INFO_ZYGISK("Zygisk"),
    HOME_INFO_HOOK_TYPE("Hook type"),
    HOME_INFO_DEVICE_MODEL("Device"),
    HOME_INFO_FINGERPRINT("Fingerprint"),
    HOME_WARN_VERSION_SPOOF("Version spoof is active"),
    HOME_UPDATE_NEW_VERSION("New version available: %d"),
    HOME_CI_UPDATE_AVAILABLE("CI build available: %d"),
    HOME_CI_UPDATE_BUSY("CI update in progress"),
    HOME_WARN_PR_BUILD("This is a PR build"),
    HOME_WARN_PR_KERNEL("This kernel is a PR build"),
    HOME_WARN_REQUIRE_KERNEL("Kernel update required"),
    HOME_WARN_REQUIRE_KERNEL_GKI("Kernel update required (GKI)"),
    HOME_WARN_REQUIRE_MANAGER("Manager update required"),
    HOME_WARN_LKM_UPDATE("LKM update available"),
    HOME_WARN_ROOT_FAILED("Root unavailable"),
    HOME_WARN_PREVIEW_MODE("Preview mode"),
    PAGE_THEME_STORE("Theme store"),
}

@Composable
fun customText(key: CustomTextKey, vararg args: Any): String =
    if (args.isEmpty()) key.default else runCatching { String.format(key.default, *args) }.getOrDefault(key.default)
