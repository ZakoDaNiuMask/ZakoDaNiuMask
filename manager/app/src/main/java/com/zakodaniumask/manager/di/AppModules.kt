package com.zakodaniumask.manager.di

import coil.ImageLoader
import com.zakodaniumask.manager.BuildConfig
import com.zakodaniumask.manager.data.AppSettingsRepository
import com.zakodaniumask.manager.data.application.ApplicationControlRepository
import com.zakodaniumask.manager.data.application.DynamicManagerRepository
import com.zakodaniumask.manager.data.bootscript.BootScriptRepository
import com.zakodaniumask.manager.data.count.CountRepository
import com.zakodaniumask.manager.data.download.DownloadRepository
import com.zakodaniumask.manager.data.file.ModuleFileRepository
import com.zakodaniumask.manager.data.flash.FlashRepository
import com.zakodaniumask.manager.data.flash.RemoteBootImageSource
import com.zakodaniumask.manager.data.kernel.KernelRepository
import com.zakodaniumask.manager.data.kernel.SpoofRepository
import com.zakodaniumask.manager.data.kernel.UmountRepository
import com.zakodaniumask.manager.data.kpm.KpmRepository
import com.zakodaniumask.manager.data.userko.UserKoRepository
import com.zakodaniumask.manager.data.plugin.OnlinePluginRepository
import com.zakodaniumask.manager.data.plugin.PluginRepository
import com.zakodaniumask.manager.data.logging.BugreportRepository
import com.zakodaniumask.manager.data.logging.SulogRepository
import com.zakodaniumask.manager.data.module.ModuleActionRepository
import com.zakodaniumask.manager.data.module.ModuleCatalogRepository
import com.zakodaniumask.manager.data.module.ModulePreferencesRepository
import com.zakodaniumask.manager.data.module.ModuleRepository
import com.zakodaniumask.manager.data.network.NetworkRequestRepository
import com.zakodaniumask.manager.data.network.NetworkStatusRepository
import com.zakodaniumask.manager.data.network.WebResourceRepository
import com.zakodaniumask.manager.data.packageinfo.AppIconDataSource
import com.zakodaniumask.manager.data.packageinfo.InstalledPackageCache
import com.zakodaniumask.manager.data.packageinfo.InstalledPackageRepository
import com.zakodaniumask.manager.data.packageinfo.RootServiceRepository
import com.zakodaniumask.manager.data.packageinfo.SuperUserRepository
import com.zakodaniumask.manager.data.profile.ProfileRepository
import com.zakodaniumask.manager.data.profile.ProfileTemplateRepository
import com.zakodaniumask.manager.data.settings.LocaleHelper
import com.zakodaniumask.manager.data.settings.LocaleRepository
import com.zakodaniumask.manager.data.settings.SettingsPlatformRepository
import com.zakodaniumask.manager.data.shell.KsuCliRepository
import com.zakodaniumask.manager.data.shell.ShortcutRepository
import com.zakodaniumask.manager.data.startup.ApplicationInitializationRepository
import com.zakodaniumask.manager.data.startup.StartupRepository
import com.zakodaniumask.manager.data.susfs.SuSFSConfigHelper
import com.zakodaniumask.manager.data.susfs.SuSFSRepository
import com.zakodaniumask.manager.ghostlock.data.AndroidGhostlockRepository
import com.zakodaniumask.manager.ghostlock.domain.repository.GhostlockRepository
import com.zakodaniumask.manager.ghostlock.ui.GhostlockViewModel
import com.zakodaniumask.manager.data.system.HomeRuntimeRepository
import com.zakodaniumask.manager.data.system.HomeStateRepository
import com.zakodaniumask.manager.data.text.HanziToPinyin
import com.zakodaniumask.manager.data.theme.MonetCompatColorSource
import com.zakodaniumask.manager.data.theme.ThemeRepository
import com.zakodaniumask.manager.data.update.ManagerUpdateRepository
import com.zakodaniumask.manager.data.privilege.PrivilegeManager
import com.zakodaniumask.manager.data.webui.WebUiRepository
import com.zakodaniumask.manager.domain.text.TextTransliterator
import com.zakodaniumask.manager.domain.usecase.AddUmountPathUseCase
import com.zakodaniumask.manager.domain.usecase.ApplyDetectorActionUseCase
import com.zakodaniumask.manager.domain.usecase.ApplyLanguageUseCase
import com.zakodaniumask.manager.domain.usecase.BackupAllowlistUseCase
import com.zakodaniumask.manager.domain.usecase.CalculateInstalledModuleSizeUseCase
import com.zakodaniumask.manager.domain.usecase.CheckFlashModuleMountUseCase
import com.zakodaniumask.manager.domain.usecase.CheckManagerUpdateUseCase
import com.zakodaniumask.manager.domain.usecase.CleanSulogUseCase
import com.zakodaniumask.manager.domain.usecase.ClearDynamicManagerUseCase
import com.zakodaniumask.manager.domain.usecase.ConfigureSuLogUseCase
import com.zakodaniumask.manager.domain.usecase.ControlAppUseCase
import com.zakodaniumask.manager.domain.usecase.ClearPluginLogUseCase
import com.zakodaniumask.manager.domain.usecase.ControlKpmModuleUseCase
import com.zakodaniumask.manager.domain.usecase.DownloadOnlinePluginUseCase
import com.zakodaniumask.manager.domain.usecase.GetOnlinePluginsUseCase
import com.zakodaniumask.manager.domain.usecase.GetPluginConfigUseCase
import com.zakodaniumask.manager.domain.usecase.GetPluginLogUseCase
import com.zakodaniumask.manager.domain.usecase.GetPluginsUseCase
import com.zakodaniumask.manager.domain.usecase.InstallPluginUseCase
import com.zakodaniumask.manager.domain.usecase.RunPluginActionUseCase
import com.zakodaniumask.manager.domain.usecase.RunPluginCallbackUseCase
import com.zakodaniumask.manager.domain.usecase.SetPluginConfigUseCase
import com.zakodaniumask.manager.domain.usecase.SetPluginEnabledUseCase
import com.zakodaniumask.manager.domain.usecase.UninstallPluginUseCase
import com.zakodaniumask.manager.domain.usecase.GetKpmModuleInfoUseCase
import com.zakodaniumask.manager.domain.usecase.GetKpmModulesUseCase
import com.zakodaniumask.manager.domain.usecase.DeleteUserKoUseCase
import com.zakodaniumask.manager.domain.usecase.GetUserKoStateUseCase
import com.zakodaniumask.manager.domain.usecase.ImportUserKoUseCase
import com.zakodaniumask.manager.domain.usecase.LoadUserKoUseCase
import com.zakodaniumask.manager.domain.usecase.SetUserKoAutoLoadUseCase
import com.zakodaniumask.manager.domain.usecase.SetUserKoStageUseCase
import com.zakodaniumask.manager.domain.usecase.UnloadUserKoUseCase
import com.zakodaniumask.manager.domain.usecase.GetKpmStatusUseCase
import com.zakodaniumask.manager.domain.usecase.LoadKpmModuleUseCase
import com.zakodaniumask.manager.domain.usecase.UnloadKpmModuleUseCase
import com.zakodaniumask.manager.domain.usecase.DeleteProfileTemplateUseCase
import com.zakodaniumask.manager.domain.usecase.EnableSulogUseCase
import com.zakodaniumask.manager.domain.usecase.EnqueueDownloadUseCase
import com.zakodaniumask.manager.domain.usecase.EnqueueManagerUpdateUseCase
import com.zakodaniumask.manager.domain.usecase.EnsureManagerInstalledUseCase
import com.zakodaniumask.manager.domain.usecase.ExecuteFlashOperationUseCase
import com.zakodaniumask.manager.domain.usecase.ExecuteModuleActionUseCase
import com.zakodaniumask.manager.domain.usecase.ExportProfileTemplatesUseCase
import com.zakodaniumask.manager.domain.usecase.ExtractModuleIdUseCase
import com.zakodaniumask.manager.domain.usecase.ExtractModuleNameUseCase
import com.zakodaniumask.manager.domain.usecase.FetchRemoteTextUseCase
import com.zakodaniumask.manager.domain.usecase.GenerateBugreportUseCase
import com.zakodaniumask.manager.domain.usecase.GetAppProfileUseCase
import com.zakodaniumask.manager.domain.usecase.GetAppSepolicyUseCase
import com.zakodaniumask.manager.domain.usecase.GetBooleanPreferenceUseCase
import com.zakodaniumask.manager.domain.usecase.GetBootScriptUseCase
import com.zakodaniumask.manager.domain.usecase.GetCatalogModuleUseCase
import com.zakodaniumask.manager.domain.usecase.GetDefaultUmountModulesUseCase
import com.zakodaniumask.manager.domain.usecase.GetDetectorActionStatesUseCase
import com.zakodaniumask.manager.domain.usecase.GetHomeBasicInfoUseCase
import com.zakodaniumask.manager.domain.usecase.GetInstallEnvironmentUseCase
import com.zakodaniumask.manager.domain.usecase.GetKernelFeatureSettingsUseCase
import com.zakodaniumask.manager.domain.usecase.GetKernelStatusUseCase
import com.zakodaniumask.manager.domain.usecase.GetManagerRuntimeInfoUseCase
import com.zakodaniumask.manager.domain.usecase.GetPlatformFeatureStatusUseCase
import com.zakodaniumask.manager.domain.usecase.GetProfileTemplateUseCase
import com.zakodaniumask.manager.domain.usecase.GetStringPreferenceUseCase
import com.zakodaniumask.manager.domain.usecase.GetStringSetPreferenceUseCase
import com.zakodaniumask.manager.domain.usecase.GetSuSFSStatusUseCase
import com.zakodaniumask.manager.domain.usecase.GetSuperUserAppGroupUseCase
import com.zakodaniumask.manager.domain.usecase.ImportAllowlistUseCase
import com.zakodaniumask.manager.domain.usecase.ImportProfileTemplatesUseCase
import com.zakodaniumask.manager.domain.usecase.InitializeApplicationUseCase
import com.zakodaniumask.manager.domain.usecase.IsLateLoadModeUseCase
import com.zakodaniumask.manager.domain.usecase.IsModuleUriAccessibleUseCase
import com.zakodaniumask.manager.domain.usecase.IsNetworkAvailableUseCase
import com.zakodaniumask.manager.domain.usecase.IsSoftRebootPreferredUseCase
import com.zakodaniumask.manager.domain.usecase.IsSystemLanguageSettingsUseCase
import com.zakodaniumask.manager.domain.usecase.LaunchSystemLanguageSettingsUseCase
import com.zakodaniumask.manager.domain.usecase.LoadSettingsPlatformUseCase
import com.zakodaniumask.manager.domain.usecase.ObserveCatalogModulesUseCase
import com.zakodaniumask.manager.domain.usecase.ObserveDownloadUseCase
import com.zakodaniumask.manager.domain.usecase.ObserveDynamicManagerStateUseCase
import com.zakodaniumask.manager.domain.usecase.ObserveInstalledModulesUseCase
import com.zakodaniumask.manager.domain.usecase.ObserveKernelFlashUseCase
import com.zakodaniumask.manager.domain.usecase.ObserveModuleCatalogOfflineUseCase
import com.zakodaniumask.manager.domain.usecase.ObserveModuleCatalogRefreshingUseCase
import com.zakodaniumask.manager.domain.usecase.ObserveProfileTemplateOfflineUseCase
import com.zakodaniumask.manager.domain.usecase.ObserveProfileTemplateRefreshingUseCase
import com.zakodaniumask.manager.domain.usecase.ObserveProfileTemplatesUseCase
import com.zakodaniumask.manager.domain.usecase.ObserveStartupStateUseCase
import com.zakodaniumask.manager.domain.usecase.ObserveSulogStateUseCase
import com.zakodaniumask.manager.domain.usecase.ObserveSuperUserStateUseCase
import com.zakodaniumask.manager.domain.usecase.ObserveUmountStateUseCase
import com.zakodaniumask.manager.domain.usecase.RebootUseCase
import com.zakodaniumask.manager.domain.usecase.RefreshDynamicManagerUseCase
import com.zakodaniumask.manager.domain.usecase.RefreshInstalledModulesUseCase
import com.zakodaniumask.manager.domain.usecase.RefreshModuleCatalogUseCase
import com.zakodaniumask.manager.domain.usecase.RefreshProfileTemplatesUseCase
import com.zakodaniumask.manager.domain.usecase.RefreshSulogUseCase
import com.zakodaniumask.manager.domain.usecase.RefreshSuperUsersUseCase
import com.zakodaniumask.manager.domain.usecase.RefreshUmountPathsUseCase
import com.zakodaniumask.manager.domain.usecase.RemovePreferenceUseCase
import com.zakodaniumask.manager.domain.usecase.RemoveUmountPathUseCase
import com.zakodaniumask.manager.domain.usecase.SaveModuleActionLogUseCase
import com.zakodaniumask.manager.domain.usecase.SaveProfileTemplateUseCase
import com.zakodaniumask.manager.domain.usecase.SelectDynamicManagerUseCase
import com.zakodaniumask.manager.domain.usecase.SetAppProfileUseCase
import com.zakodaniumask.manager.domain.usecase.SetAppSepolicyUseCase
import com.zakodaniumask.manager.domain.usecase.SetBootScriptUseCase
import com.zakodaniumask.manager.domain.usecase.SetBooleanPreferenceUseCase
import com.zakodaniumask.manager.domain.usecase.SetDefaultUmountModulesUseCase
import com.zakodaniumask.manager.domain.usecase.SetKernelUmountEnabledUseCase
import com.zakodaniumask.manager.domain.usecase.SetManualDynamicManagerUseCase
import com.zakodaniumask.manager.domain.usecase.SetModuleEnabledUseCase
import com.zakodaniumask.manager.domain.usecase.SetModuleRemovedUseCase
import com.zakodaniumask.manager.domain.usecase.SetSelinuxHideEnabledUseCase
import com.zakodaniumask.manager.domain.usecase.SetStringPreferenceUseCase
import com.zakodaniumask.manager.domain.usecase.SetStringSetPreferenceUseCase
import com.zakodaniumask.manager.domain.usecase.SetSuEnabledUseCase
import com.zakodaniumask.manager.domain.usecase.StartKernelFlashUseCase
import com.zakodaniumask.manager.domain.usecase.SuSFSConfigUseCase
import com.zakodaniumask.manager.domain.usecase.TakeModuleUriPermissionUseCase
import com.zakodaniumask.manager.domain.usecase.TransliterateTextUseCase
import com.zakodaniumask.manager.domain.usecase.UpdateAppearanceUseCase
import com.zakodaniumask.manager.domain.usecase.UpdateCachedModuleEnabledUseCase
import com.zakodaniumask.manager.domain.usecase.UpdatePlatformSettingUseCase
import com.zakodaniumask.manager.domain.usecase.ValidateSepolicyUseCase
import com.zakodaniumask.manager.ui.activity.util.ThemeUtils
import com.zakodaniumask.manager.ui.component.ZipFileDetector
import com.zakodaniumask.manager.ui.theme.BackgroundManager
import com.zakodaniumask.manager.ui.theme.CardConfig
import com.zakodaniumask.manager.ui.theme.ThemeConfig
import com.zakodaniumask.manager.ui.util.module.Shortcut
import com.zakodaniumask.manager.ui.viewmodel.AppProfileViewModel
import com.zakodaniumask.manager.ui.viewmodel.DynamicManagerViewModel
import com.zakodaniumask.manager.ui.viewmodel.ExecuteModuleActionViewModel
import com.zakodaniumask.manager.ui.viewmodel.FlashViewModel
import com.zakodaniumask.manager.ui.viewmodel.HomeViewModel
import com.zakodaniumask.manager.ui.viewmodel.InstallViewModel
import com.zakodaniumask.manager.ui.viewmodel.KernelFlashViewModel
import com.zakodaniumask.manager.ui.viewmodel.MainIntentViewModel
import com.zakodaniumask.manager.ui.viewmodel.ModuleDetailViewModel
import com.zakodaniumask.manager.ui.viewmodel.ModuleRepoViewModel
import com.zakodaniumask.manager.ui.viewmodel.ModuleViewModel
import com.zakodaniumask.manager.ui.viewmodel.BootScriptViewModel
import com.zakodaniumask.manager.ui.viewmodel.DetectorViewModel
import com.zakodaniumask.manager.ui.viewmodel.KpmViewModel
import com.zakodaniumask.manager.ui.viewmodel.UserKoViewModel
import com.zakodaniumask.manager.ui.viewmodel.CpuSpoofViewModel
import com.zakodaniumask.manager.ui.viewmodel.UtsSpoofViewModel
import com.zakodaniumask.manager.ui.viewmodel.MemSpoofViewModel
import com.zakodaniumask.manager.ui.viewmodel.OnlinePluginViewModel
import com.zakodaniumask.manager.ui.viewmodel.PluginViewModel
import com.zakodaniumask.manager.ui.viewmodel.SettingsViewModel
import com.zakodaniumask.manager.ui.viewmodel.SuSFSViewModel
import com.zakodaniumask.manager.ui.viewmodel.SulogViewModel
import com.zakodaniumask.manager.ui.viewmodel.SuperUserViewModel
import com.zakodaniumask.manager.ui.viewmodel.TemplateEditorViewModel
import com.zakodaniumask.manager.ui.viewmodel.TemplateViewModel
import com.zakodaniumask.manager.ui.viewmodel.UmountManagerScreenViewModel
import com.zakodaniumask.manager.ui.webui.MonetColorsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.zhanghai.android.appiconloader.coil.AppIconFetcher
import me.zhanghai.android.appiconloader.coil.AppIconKeyer
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

val applicationScopeQualifier = named("applicationScope")

val coreModule = module {
    single<CoroutineScope>(applicationScopeQualifier) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
    single {
        OkHttpClient.Builder()
            .cache(Cache(File(androidApplication().cacheDir, "okhttp"), 10L * 1024L * 1024L))
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "ZakoDaNiuMask/${BuildConfig.VERSION_CODE}")
                        .header("Accept-Language", Locale.getDefault().toLanguageTag())
                        .build()
                )
            }
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }
    single {
        val application = androidApplication()
        val iconSize = application.resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
        ImageLoader.Builder(application)
            .components {
                add(AppIconKeyer())
                add(AppIconFetcher.Factory(iconSize, false, application))
            }
            .build()
    }
}

val repositoryModule = module {
    single { KsuCliRepository(androidApplication()) }
    single { com.zakodaniumask.manager.data.partition.PartitionManagerRepository(get()) }
    single { com.zakodaniumask.manager.data.detection.RootProbeClientRepository(androidApplication()) }
    single { com.zakodaniumask.manager.data.bootloader.BootloaderDetector(androidApplication()) }
    single { com.zakodaniumask.manager.data.tee.TeeDetector(androidApplication()) }
    single { com.zakodaniumask.manager.data.properties.SystemPropertiesDetector() }
    single { com.zakodaniumask.manager.data.kernel.KernelCheckDetector() }
    single { com.zakodaniumask.manager.data.selinux.SelinuxDetector() }
    singleOf(::BootScriptRepository)
    singleOf(::KpmRepository)
    singleOf(::UserKoRepository)
    singleOf(::PluginRepository)
    singleOf(::OnlinePluginRepository)
    singleOf(::CountRepository)
    singleOf(::InstalledPackageCache)
    singleOf(::AppIconDataSource)
    singleOf(::RootServiceRepository)
    singleOf(::InstalledPackageRepository)
    single {
        SuperUserRepository(
            application = get(),
            cache = get(),
            installedPackageRepository = get(),
            profileRepository = get(),
            applicationScope = get(applicationScopeQualifier),
        )
    }
    single {
        AppSettingsRepository(
            context = androidApplication(),
            applicationScope = get(applicationScopeQualifier),
        )
    }
    singleOf(::StartupRepository)
    single {
        ApplicationInitializationRepository(
            application = get(),
            imageLoader = get(),
            applicationScope = get(applicationScopeQualifier),
            flashRepository = get(),
            ksuCliRepository = get(),
            monetCompatColorSource = get(),
        )
    }
    singleOf(::ManagerUpdateRepository)
    singleOf(::ApplicationControlRepository)
    singleOf(::DownloadRepository)
    single { FlashRepository(get(), get(applicationScopeQualifier), get(), get()) }
    single { RemoteBootImageSource(androidApplication()) }
    singleOf(::KernelRepository)
    singleOf(::HomeRuntimeRepository)
    singleOf(::HomeStateRepository)
    singleOf(::NetworkStatusRepository)
    singleOf(::NetworkRequestRepository)
    singleOf(::DynamicManagerRepository)
    singleOf(::SulogRepository)
    singleOf(::BugreportRepository)
    singleOf(::UmountRepository)
    singleOf(::SpoofRepository)
    singleOf(::ModuleCatalogRepository)
    singleOf(::ModuleRepository)
    singleOf(::ModulePreferencesRepository)
    singleOf(::ModuleActionRepository)
    singleOf(::WebResourceRepository)
    singleOf(::PrivilegeManager)
    singleOf(::WebUiRepository)
    singleOf(::ModuleFileRepository)
    singleOf(::ProfileRepository)
    singleOf(::ProfileTemplateRepository)
    singleOf(::SuSFSConfigHelper)
    singleOf(::SuSFSRepository)
    singleOf(::AndroidGhostlockRepository) bind GhostlockRepository::class
    // Shared across the GhostLock routes (see GhostlockScreen/SubScreens).
    singleOf(::GhostlockViewModel)
    singleOf(::MonetCompatColorSource)
    singleOf(::ThemeRepository)
    single {
        val themeRepository = get<ThemeRepository>()
        ThemeConfig(themeRepository::defaultSeedColor)
    }
    singleOf(::CardConfig)
    singleOf(::BackgroundManager)
    singleOf(::ThemeUtils)
    singleOf(::LocaleHelper)
    singleOf(::LocaleRepository)
    singleOf(::SettingsPlatformRepository)
    singleOf(::ShortcutRepository)
    singleOf(::Shortcut)
    singleOf(::MonetColorsProvider)
    singleOf(::ZipFileDetector)
    single { HanziToPinyin.create() } bind TextTransliterator::class
}

val useCaseModule = module {
    factoryOf(::InitializeApplicationUseCase)
    factoryOf(::GetHomeBasicInfoUseCase)
    factoryOf(::IsNetworkAvailableUseCase)
    factoryOf(::LoadSettingsPlatformUseCase)
    factoryOf(::UpdateAppearanceUseCase)
    factoryOf(::UpdatePlatformSettingUseCase)
    factoryOf(::GetPlatformFeatureStatusUseCase)
    factoryOf(::IsSoftRebootPreferredUseCase)
    factoryOf(::CheckManagerUpdateUseCase)
    factoryOf(::EnsureManagerInstalledUseCase)
    factoryOf(::RebootUseCase)
    factoryOf(::EnqueueDownloadUseCase)
    factoryOf(::EnqueueManagerUpdateUseCase)
    factoryOf(::ObserveDownloadUseCase)
    factoryOf(::GetKernelStatusUseCase)
    factoryOf(::GetInstallEnvironmentUseCase)
    factoryOf(::ExecuteFlashOperationUseCase)
    factoryOf(::CheckFlashModuleMountUseCase)
    factoryOf(::GetManagerRuntimeInfoUseCase)
    factoryOf(::GetKernelFeatureSettingsUseCase)
    factoryOf(::SetSuEnabledUseCase)
    factoryOf(::SetKernelUmountEnabledUseCase)
    factoryOf(::ConfigureSuLogUseCase)
    factoryOf(::SetSelinuxHideEnabledUseCase)
    factoryOf(::SetDefaultUmountModulesUseCase)
    factoryOf(::IsLateLoadModeUseCase)
    factoryOf(::GetAppProfileUseCase)
    factoryOf(::SetAppProfileUseCase)
    factoryOf(::GetAppSepolicyUseCase)
    factoryOf(::SetAppSepolicyUseCase)
    factoryOf(::ControlAppUseCase)
    factoryOf(::ValidateSepolicyUseCase)
    factoryOf(::GetDefaultUmountModulesUseCase)
    factoryOf(::GetSuSFSStatusUseCase)
    factoryOf(::SuSFSConfigUseCase)
    factoryOf(::GetDetectorActionStatesUseCase)
    factoryOf(::ApplyDetectorActionUseCase)
    factoryOf(::GetBootScriptUseCase)
    factoryOf(::SetBootScriptUseCase)
    factoryOf(::GetKpmStatusUseCase)
    factoryOf(::GetKpmModulesUseCase)
    factoryOf(::GetKpmModuleInfoUseCase)
    factoryOf(::LoadKpmModuleUseCase)
    factoryOf(::UnloadKpmModuleUseCase)
    factoryOf(::ControlKpmModuleUseCase)
    factoryOf(::GetUserKoStateUseCase)
    factoryOf(::ImportUserKoUseCase)
    factoryOf(::LoadUserKoUseCase)
    factoryOf(::UnloadUserKoUseCase)
    factoryOf(::DeleteUserKoUseCase)
    factoryOf(::SetUserKoAutoLoadUseCase)
    factoryOf(::SetUserKoStageUseCase)
    factoryOf(::GetPluginsUseCase)
    factoryOf(::InstallPluginUseCase)
    factoryOf(::UninstallPluginUseCase)
    factoryOf(::SetPluginEnabledUseCase)
    factoryOf(::RunPluginCallbackUseCase)
    factoryOf(::RunPluginActionUseCase)
    factoryOf(::GetPluginConfigUseCase)
    factoryOf(::SetPluginConfigUseCase)
    factoryOf(::GetPluginLogUseCase)
    factoryOf(::ClearPluginLogUseCase)
    factoryOf(::GetOnlinePluginsUseCase)
    factoryOf(::DownloadOnlinePluginUseCase)
    factoryOf(::ApplyLanguageUseCase)
    factoryOf(::IsSystemLanguageSettingsUseCase)
    factoryOf(::LaunchSystemLanguageSettingsUseCase)
    factoryOf(::GenerateBugreportUseCase)
    factoryOf(::ObserveStartupStateUseCase)
    factoryOf(::GetSuperUserAppGroupUseCase)
    factoryOf(::ObserveCatalogModulesUseCase)
    factoryOf(::ObserveModuleCatalogRefreshingUseCase)
    factoryOf(::ObserveModuleCatalogOfflineUseCase)
    factoryOf(::RefreshModuleCatalogUseCase)
    factoryOf(::GetCatalogModuleUseCase)
    factoryOf(::ObserveProfileTemplatesUseCase)
    factoryOf(::ObserveProfileTemplateRefreshingUseCase)
    factoryOf(::ObserveProfileTemplateOfflineUseCase)
    factoryOf(::RefreshProfileTemplatesUseCase)
    factoryOf(::GetProfileTemplateUseCase)
    factoryOf(::SaveProfileTemplateUseCase)
    factoryOf(::DeleteProfileTemplateUseCase)
    factoryOf(::ImportProfileTemplatesUseCase)
    factoryOf(::ExportProfileTemplatesUseCase)
    factoryOf(::GetBooleanPreferenceUseCase)
    factoryOf(::SetBooleanPreferenceUseCase)
    factoryOf(::GetStringPreferenceUseCase)
    factoryOf(::SetStringPreferenceUseCase)
    factoryOf(::GetStringSetPreferenceUseCase)
    factoryOf(::SetStringSetPreferenceUseCase)
    factoryOf(::ObserveDynamicManagerStateUseCase)
    factoryOf(::RefreshDynamicManagerUseCase)
    factoryOf(::SelectDynamicManagerUseCase)
    factoryOf(::SetManualDynamicManagerUseCase)
    factoryOf(::ClearDynamicManagerUseCase)
    factoryOf(::ObserveSulogStateUseCase)
    factoryOf(::RefreshSulogUseCase)
    factoryOf(::EnableSulogUseCase)
    factoryOf(::CleanSulogUseCase)
    factoryOf(::ObserveUmountStateUseCase)
    factoryOf(::RefreshUmountPathsUseCase)
    factoryOf(::AddUmountPathUseCase)
    factoryOf(::RemoveUmountPathUseCase)
    factoryOf(::ObserveKernelFlashUseCase)
    factoryOf(::StartKernelFlashUseCase)
    factoryOf(::RemovePreferenceUseCase)
    factoryOf(::ObserveSuperUserStateUseCase)
    factoryOf(::RefreshSuperUsersUseCase)
    factoryOf(::BackupAllowlistUseCase)
    factoryOf(::ImportAllowlistUseCase)
    factoryOf(::FetchRemoteTextUseCase)
    factoryOf(::IsModuleUriAccessibleUseCase)
    factoryOf(::TakeModuleUriPermissionUseCase)
    factoryOf(::ExtractModuleNameUseCase)
    factoryOf(::ExtractModuleIdUseCase)
    factoryOf(::ObserveInstalledModulesUseCase)
    factoryOf(::RefreshInstalledModulesUseCase)
    factoryOf(::CalculateInstalledModuleSizeUseCase)
    factoryOf(::UpdateCachedModuleEnabledUseCase)
    factoryOf(::ExecuteModuleActionUseCase)
    factoryOf(::SaveModuleActionLogUseCase)
    factoryOf(::SetModuleEnabledUseCase)
    factoryOf(::SetModuleRemovedUseCase)
    factoryOf(::TransliterateTextUseCase)
}

val viewModelModule = module {
    viewModel { parameters ->
        AppProfileViewModel(
            uid = parameters[0],
            packageName = parameters[1],
            getAppGroup = get(),
            getProfile = get(),
            getDefaultUmountModules = get(),
            setProfile = get(),
            getSepolicy = get(),
            setSepolicy = get(),
            controlApp = get(),
            validateSepolicy = get(),
        )
    }
    viewModelOf(::HomeViewModel)
    viewModelOf(::DetectorViewModel)
    viewModelOf(::BootScriptViewModel)
    viewModelOf(::KpmViewModel)
    viewModelOf(::UserKoViewModel)
    viewModelOf(::CpuSpoofViewModel)
    viewModelOf(::UtsSpoofViewModel)
    viewModelOf(::MemSpoofViewModel)
    viewModelOf(::PluginViewModel)

    viewModelOf(::OnlinePluginViewModel)
    viewModelOf(::InstallViewModel)
    viewModelOf(::MainIntentViewModel)
    viewModelOf(::KernelFlashViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::ModuleViewModel)
    viewModelOf(::SuperUserViewModel)
    viewModelOf(::SuSFSViewModel)
    viewModelOf(::ModuleRepoViewModel)
    viewModel { parameters -> ModuleDetailViewModel(parameters[0], get()) }
    viewModelOf(::TemplateViewModel)
    viewModel { parameters ->
        TemplateEditorViewModel(
            templateId = parameters[0],
            readOnly = parameters[1],
            isCreation = parameters[2],
            getTemplate = get(),
            saveTemplate = get(),
            deleteTemplate = get(),
        )
    }
    viewModelOf(::SulogViewModel)
    viewModelOf(::DynamicManagerViewModel)
    viewModelOf(::FlashViewModel)
    viewModelOf(::UmountManagerScreenViewModel)
    viewModel { parameters ->
        ExecuteModuleActionViewModel(
            moduleId = parameters[0],
            executeModuleAction = get(),
            saveModuleActionLog = get(),
        )
    }
}

val appModules = listOf(coreModule, repositoryModule, useCaseModule, viewModelModule)
