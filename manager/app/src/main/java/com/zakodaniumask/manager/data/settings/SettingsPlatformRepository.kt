package com.zakodaniumask.manager.data.settings

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.zakodaniumask.manager.Natives
import com.zakodaniumask.manager.data.AppSettingsRepository
import com.zakodaniumask.manager.data.shell.KsuCliRepository
import com.zakodaniumask.manager.data.theme.ThemeRepository
import com.zakodaniumask.manager.domain.model.AppearanceSetting
import com.zakodaniumask.manager.domain.model.PlatformFeatureStatus
import com.zakodaniumask.manager.domain.model.PlatformSetting
import com.zakodaniumask.manager.domain.model.SettingsPlatformSnapshot
import com.zakodaniumask.manager.magica.BootCompletedReceiver
import com.zakodaniumask.manager.ui.theme.BackgroundManager
import com.zakodaniumask.manager.ui.theme.CardConfig
import com.zakodaniumask.manager.ui.theme.ThemeConfig
import com.topjohnwu.superuser.ShellUtils
import java.security.SecureRandom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SettingsPlatformRepository(
    private val application: Application,
    private val settings: AppSettingsRepository,
    private val themeConfig: ThemeConfig,
    private val themeRepository: ThemeRepository,
    private val backgroundManager: BackgroundManager,
    private val cardConfig: CardConfig,
    private val localeHelper: LocaleHelper,
    private val ksuCliRepository: KsuCliRepository,
) {
    private companion object {
        private val secureRandom = SecureRandom()
    }

    fun load(): SettingsPlatformSnapshot {
        themeConfig.forceDarkMode = themeRepository.loadThemeMode()
        themeConfig.seedColor = themeRepository.loadSeedColor()
        themeConfig.useDynamicColor = themeRepository.loadDynamicColorState()
        themeConfig.dynamicColorSpec = themeRepository.loadDynamicColorSpec()
        themeConfig.dynamicPaletteStyle = themeRepository.loadDynamicPaletteStyle(
            themeConfig.dynamicColorSpec,
        )
        themeConfig.useBuiltinMonoFont = settings.getBoolean("use_builtin_monospace_font", false)
        backgroundManager.loadCustomBackground()
        val systemDpi = application.resources.displayMetrics.densityDpi
        val currentDpi = settings.getInt("app_dpi", systemDpi)
        val customBackground = settings.getString("custom_background", null) != null
        val themeMode = when (themeConfig.forceDarkMode) {
            true -> 2
            false -> 1
            null -> 0
        }
        cardConfig.load()
        cardConfig.updateBackground(customBackground)
        when (themeMode) {
            2 -> cardConfig.updateThemePreference(darkMode = true, lightMode = false)
            1 -> cardConfig.updateThemePreference(darkMode = false, lightMode = true)
            else -> cardConfig.updateThemePreference(darkMode = null, lightMode = null)
        }
        if (themeMode == 0 && isSystemDark()) cardConfig.setThemeDefaults(true)
        cardConfig.save()
        return SettingsPlatformSnapshot(
            dpi = settings.getInt("app_dpi", 0),
            predictiveBackAnimation = settings.getString("predictive_back_animation", "").orEmpty(),
            predictiveBackExitDirection = settings.getString("predictive_back_exit_direction", "")
                .orEmpty(),
            themeMode = themeMode,
            useDynamicColor = themeConfig.useDynamicColor,
            dynamicColorSpec = themeConfig.dynamicColorSpec,
            dynamicPaletteStyle = themeConfig.dynamicPaletteStyle,
            currentLocaleTag = localeHelper.getCurrentAppLocale(application)?.toLanguageTag(),
            useAltIcon = settings.getBoolean("use_alt_icon", false),
            cardAlpha = cardConfig.cardAlpha,
            backgroundDim = themeConfig.backgroundDim,
            customBackgroundEnabled = customBackground,
            systemDpi = systemDpi,
            currentDpi = currentDpi,
            checkManagerUpdate = settings.getBoolean("check_update", true),
            checkBetaUpdate = settings.getBoolean("check_beta_update", true),
            checkModuleUpdate = loadModuleUpdatePreference(),
            autoJailbreakEnabled = settings.getBoolean("auto_jailbreak", false),
            useBuiltinMonoFont = themeConfig.useBuiltinMonoFont,
            useSoftReboot = settings.getBoolean("use_soft_reboot", false),
            enableSwipeDismiss = settings.getBoolean("enable_swipe_dismiss", true),
            pagerInterceptionMode = settings.getInt("pager_interception_mode", 1).coerceIn(0, 2),
            ignoreUapi = settings.getBoolean("ignore_uapi", false),
            moduleDescriptionMaxLines = settings.getInt("module_description_max_lines", 4)
                .coerceIn(1, 10),
            showFullStatus = settings.getBoolean("show_fingerprint", false),
            enableWebDebugging = settings.getBoolean("enable_web_debugging", false),
        )
    }

    suspend fun updateAppearance(
        setting: AppearanceSetting,
    ): Result<SettingsPlatformSnapshot> = try {
        when (setting) {
            is AppearanceSetting.ThemeMode -> setThemeMode(setting.index)
            is AppearanceSetting.SeedColor -> {
                themeRepository.saveSeedColor(setting.color)
                themeConfig.seedColor = setting.color
            }

            is AppearanceSetting.DynamicColor -> {
                themeRepository.saveDynamicColorState(setting.enabled)
                themeConfig.useDynamicColor = setting.enabled
            }

            is AppearanceSetting.DynamicColorSpec -> {
                themeConfig.dynamicPaletteStyle = themeRepository.saveDynamicColorSpec(
                    setting.spec,
                    themeConfig.dynamicPaletteStyle,
                )
                themeConfig.dynamicColorSpec = setting.spec
            }

            is AppearanceSetting.DynamicPaletteStyle -> {
                themeConfig.dynamicPaletteStyle = themeRepository.saveDynamicPaletteStyle(
                    setting.style,
                    themeConfig.dynamicColorSpec,
                )
            }

            is AppearanceSetting.CustomBackground -> {
                check(
                    backgroundManager.saveAndApplyCustomBackground(
                        application,
                        setting.uri.toUri()
                    )
                )
                backgroundManager.saveBackgroundDim(0.3f)
                backgroundManager.saveEnableBlur(true)
                backgroundManager.saveEnableBlurExp(false)
                backgroundManager.saveUseBackgroundSeedColor(true)
                backgroundManager.saveEnableHighContrastMode(false)
                cardConfig.cardElevation = 0.dp
                cardConfig.updateBackground(true)
                cardConfig.save()
            }

            AppearanceSetting.RemoveCustomBackground -> removeCustomBackground()
            is AppearanceSetting.CardAlpha -> {
                cardConfig.cardAlpha = setting.value
                cardConfig.isCustomAlphaSet = true
                settings.putBoolean("is_custom_alpha_set", true)
                settings.putFloat("card_alpha", setting.value)
            }

            is AppearanceSetting.BackgroundDim ->
                backgroundManager.saveBackgroundDim(setting.value)

            AppearanceSetting.SaveCardConfig -> cardConfig.save()
        }
        Result.success(load())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    fun updatePlatform(
        setting: PlatformSetting,
    ): Result<SettingsPlatformSnapshot> = try {
        when (setting) {
            PlatformSetting.InitializeFirstRun -> initializeFirstRun()
            is PlatformSetting.PredictiveBackAnimation ->
                settings.putString("predictive_back_animation", setting.value)

            is PlatformSetting.PredictiveBackExitDirection ->
                settings.putString("predictive_back_exit_direction", setting.value)

            is PlatformSetting.Dpi -> settings.putInt("app_dpi", setting.value)
            is PlatformSetting.AlternateIcon -> {
                settings.putBoolean("use_alt_icon", setting.enabled)
                toggleLauncherIcon(setting.enabled)
            }

            is PlatformSetting.ManagerUpdateCheck -> {
                settings.putBoolean("check_update", setting.enabled)
                if (!setting.enabled) settings.putBoolean("check_beta_update", false)
            }

            is PlatformSetting.BetaUpdateCheck ->
                settings.putBoolean("check_beta_update", setting.enabled)

            is PlatformSetting.ModuleUpdateCheck ->
                settings.putBoolean("check_module_update", setting.enabled)

            is PlatformSetting.Locale -> settings.putString("app_locale", setting.tag)
            is PlatformSetting.AutoJailbreak -> setAutoJailbreak(setting.enabled)
            is PlatformSetting.AdbRoot -> setAdbRoot(setting.enabled)
            is PlatformSetting.SuCompatMode -> settings.putInt("su_compat_mode", setting.value)
            is PlatformSetting.BuiltinMonospaceFont -> {
                settings.putBoolean("use_builtin_monospace_font", setting.enabled)
                themeConfig.useBuiltinMonoFont = setting.enabled
            }

            is PlatformSetting.UseSoftReboot ->
                settings.putBoolean("use_soft_reboot", setting.enabled)

            is PlatformSetting.SwipeDismiss ->
                settings.putBoolean("enable_swipe_dismiss", setting.enabled)

            is PlatformSetting.PagerInterceptionMode ->
                settings.putInt("pager_interception_mode", setting.value.coerceIn(0, 2))

            is PlatformSetting.IgnoreUapi -> setIgnoreUapi(setting.enabled)
            is PlatformSetting.ModuleDescriptionMaxLines ->
                settings.putInt("module_description_max_lines", setting.value.coerceIn(1, 10))

            is PlatformSetting.ShowFullStatus ->
                settings.putBoolean("show_fingerprint", setting.enabled)

            is PlatformSetting.WebDebugging ->
                settings.putBoolean("enable_web_debugging", setting.enabled)
        }
        Result.success(load())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    fun isSoftRebootPreferred(): Boolean =
        (Natives.isFullFeatured() || settings.getBoolean("ignore_uapi", false)) &&
            (Natives.isLateLoadMode || settings.getBoolean("use_soft_reboot", false))

    private fun setIgnoreUapi(enabled: Boolean) {
        settings.putBoolean("ignore_uapi", enabled)
        val marker = "/data/adb/ksu/.ignore_uapi"
        val command = if (enabled) {
            "mkdir -p /data/adb/ksu && touch $marker"
        } else {
            "rm -f $marker"
        }
        runCatching {
            ShellUtils.fastCmd(ksuCliRepository.getRootShell(), command)
        }
    }

    suspend fun getFeatureStatus(): PlatformFeatureStatus = withContext(Dispatchers.IO) {
        PlatformFeatureStatus(
            suCompatPersistValue = runCatching {
                ksuCliRepository.getFeaturePersistValue("su_compat")
            }.getOrNull(),
            suStatus = runCatching {
                ksuCliRepository.getFeatureStatus("su_compat")
            }.getOrDefault(""),
            kernelUmountStatus = runCatching {
                ksuCliRepository.getFeatureStatus("kernel_umount")
            }.getOrDefault(""),
            adbRootStatus = runCatching {
                ksuCliRepository.getFeatureStatus("adb_root")
            }.getOrDefault(""),
            adbRootEnabled = runCatching {
                ksuCliRepository.getFeaturePersistValue("adb_root") == 1L
            }.getOrDefault(false),
            sulogStatus = runCatching {
                ksuCliRepository.getFeatureStatus("sulog")
            }.getOrDefault(""),
            selinuxHideStatus = runCatching {
                ksuCliRepository.getFeatureStatus("selinux_hide")
            }.getOrDefault(""),
            mountHideStatus = runCatching {
                ksuCliRepository.getFeatureStatus("mount_hide")
            }.getOrDefault(""),
        )
    }

    private fun setThemeMode(index: Int) {
        val forceDark = when (index) {
            1 -> false
            2 -> true
            else -> null
        }
        themeRepository.saveThemeMode(forceDark)
        themeConfig.forceDarkMode = forceDark
        when (index) {
            2 -> {
                cardConfig.updateThemePreference(darkMode = true, lightMode = false)
                cardConfig.setThemeDefaults(true)
            }

            1 -> {
                cardConfig.updateThemePreference(darkMode = false, lightMode = true)
                cardConfig.setThemeDefaults(false)
            }

            else -> {
                cardConfig.updateThemePreference(darkMode = null, lightMode = null)
                cardConfig.setThemeDefaults(isSystemDark())
            }
        }
        cardConfig.save()
    }

    private fun removeCustomBackground() {
        backgroundManager.clearCustomBackground(application)
        cardConfig.cardAlpha = 1f
        cardConfig.isCustomAlphaSet = false
        cardConfig.isCustomBackgroundEnabled = false
        cardConfig.save()
        themeConfig.preventBackgroundRefresh = false
        backgroundManager.saveBackgroundDim(0f)
        backgroundManager.saveEnableBlurExp(false)
        backgroundManager.saveUseBackgroundSeedColor(false)
        backgroundManager.saveEnableHighContrastMode(false)
        settings.putBoolean("prevent_background_refresh", false)
    }

    private fun initializeFirstRun() {
        if (settings.getBoolean("is_first_run", true)) {
            themeConfig.preventBackgroundRefresh = false
            settings.putBoolean("prevent_background_refresh", false)
            settings.putBoolean("is_first_run", false)
        }
    }

    private fun setAutoJailbreak(enabled: Boolean) {
        application.packageManager.setComponentEnabledSetting(
            ComponentName(application, BootCompletedReceiver::class.java),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        settings.putBoolean("auto_jailbreak", enabled)
    }

    private fun setAdbRoot(enabled: Boolean) {
        if (ksuCliRepository.execKsud("feature set adb_root ${if (enabled) 1 else 0}", true)) {
            ShellUtils.fastCmd("setprop ctl.restart adbd")
            ksuCliRepository.execKsud("feature save", true)
        }
    }

    private fun toggleLauncherIcon(useAlt: Boolean) {
        val packageName = application.packageName
        val main = ComponentName(packageName, "com.zakodaniumask.manager.ui.MainActivity")
        val alias = ComponentName(packageName, "com.zakodaniumask.manager.ui.MainActivityAlias")
        application.packageManager.setComponentEnabledSetting(
            if (useAlt) alias else main,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        application.packageManager.setComponentEnabledSetting(
            if (useAlt) main else alias,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun loadModuleUpdatePreference(): Boolean {
        val enabled = settings.getBoolean(
            "check_module_update",
            settings.getBoolean("check_update", true),
        )
        if (!settings.contains("check_module_update")) {
            settings.putBoolean("check_module_update", enabled)
        }
        return enabled
    }

    private fun isSystemDark(): Boolean =
        application.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES

    /** Per-install token used to validate ksu:// deep links and internal download installs. */
    val intentToken: String
        get() {
            val existing = settings.getString("intent_token", null)
            if (!existing.isNullOrBlank()) return existing
            val token = ByteArray(32).also(secureRandom::nextBytes)
                .joinToString(separator = "") { "%02x".format(it) }
            settings.putString("intent_token", token)
            return token
        }
}
