package com.zakodaniumask.manager.ksuui

import android.app.Application

/**
 * Compatibility bridge for KernelSU UI code that expects the process-wide Application.
 * Initialized from [com.zakodaniumask.manager.KernelSUApplication].
 */
lateinit var ksuApp: Application
