package com.zakodaniumask.manager.sukiui.ui.screen.home

import com.zakodaniumask.manager.KernelVersion

fun KernelVersion.isSupported(): Boolean =
    !(major < 0 || patchLevel < 0) && (major > 3 || (major == 3 && patchLevel >= 4))
