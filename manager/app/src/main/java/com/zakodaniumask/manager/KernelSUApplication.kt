package com.zakodaniumask.manager

import android.app.Application
import android.os.Build
import com.zakodaniumask.manager.di.appModules
import com.zakodaniumask.manager.domain.usecase.InitializeApplicationUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class KernelSUApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Isolated processes (MagicaService, RootProbeService) must not initialize the
            // Koin graph or run application initialization.
            if (android.os.Process.isIsolated()) {
                return
            }
            val processName = getProcessName()
            if (processName.endsWith("MagicaService") || processName.endsWith("RootProbeService")) {
                // avoid loading unnecessary thing when starting an isolated service
                return
            }
        }

        val koin = startKoin {
            androidLogger()
            androidContext(this@KernelSUApplication)
            modules(appModules)
        }.koin
        runBlocking(Dispatchers.IO) {
            koin.get<InitializeApplicationUseCase>()()
        }
    }
}
