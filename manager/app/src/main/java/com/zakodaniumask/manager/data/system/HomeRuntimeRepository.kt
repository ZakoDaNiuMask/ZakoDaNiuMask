package com.zakodaniumask.manager.data.system

import android.annotation.SuppressLint
import android.app.Application
import android.os.Build
import android.system.Os
import com.zakodaniumask.manager.BuildConfig
import com.zakodaniumask.manager.domain.model.HomeBasicInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HomeRuntimeRepository(
    private val application: Application,
) {
    suspend fun getBasicInfo(
        managerUapiVersion: Int,
        includeSelinuxStatus: Boolean = true,
    ): HomeBasicInfo =
        withContext(Dispatchers.IO) {
            val uname = runCatching { Os.uname() }.getOrNull()
            HomeBasicInfo(
                kernelRelease = uname?.release ?: "Unknown",
                androidVersion = Build.VERSION.RELEASE ?: "Unknown",
                deviceModel = getDeviceModel(),
                managerVersion = Triple(
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE,
                    managerUapiVersion,
                ),
                selinuxStatus = if (includeSelinuxStatus) {
                    runCatching { getSELinuxStatus(application) }.getOrDefault("Unknown")
                } else {
                    ""
                },
                seccompStatus = runCatching { Os.prctl(21, 0, 0, 0, 0) }.getOrDefault(-1),
            )
        }

    @SuppressLint("PrivateApi")
    private fun getDeviceModel(): String = runCatching {
        val systemProperties = Class.forName("android.os.SystemProperties")
        val getMethod = systemProperties.getMethod("get", String::class.java, String::class.java)
        fun prop(key: String): String? =
            (getMethod.invoke(null, key, "") as? String)?.takeIf { it.isNotEmpty() }

        prop("ro.product.marketname")
            ?: prop("ro.product.odm.marketname")
            ?: prop("ro.product.vendor.marketname")
            ?: prop("ro.vendor.oplus.market.name")
            ?: prop("ro.vivo.market.name")
            ?: prop("ro.config.marketing_name")
            ?: prop("ro.vendor.product.ztename")
            ?: getSamsungProductName()
            ?: getDeviceInfo()
    }.getOrDefault(getDeviceInfo())

    private companion object {
        const val SAMSUNG_FLOATING_FEATURE_CLASS =
            "com.samsung.android.feature.SemFloatingFeature"
        const val KEY_SAMSUNG_PRODUCT_NAME =
            "SEC_FLOATING_FEATURE_SETTINGS_CONFIG_BRAND_NAME"
        val SAMSUNG_FLOATING_FEATURE_PATHS = listOf(
            "/vendor/etc/floating_feature.xml",
            "/system/etc/floating_feature.xml",
        )
    }

    /** Samsung: floating feature brand name with the "Samsung" prefix (ported from SukiSU). */
    @SuppressLint("PrivateApi")
    private fun getSamsungProductName(): String? {
        val name = getSamsungProductNameFromFloatingFeature()
            ?: getSamsungProductNameFromFloatingFeatureFile()
        val trimmed = name?.trim()?.takeIf {
            it.isNotEmpty() &&
                !it.equals("unknown", ignoreCase = true) &&
                !it.equals("null", ignoreCase = true)
        } ?: return null
        return if (trimmed.startsWith("Samsung", ignoreCase = true)) trimmed else "Samsung $trimmed"
    }

    @SuppressLint("PrivateApi")
    private fun getSamsungProductNameFromFloatingFeature(): String? = try {
        val clazz = Class.forName(SAMSUNG_FLOATING_FEATURE_CLASS)
        val instance = clazz.getMethod("getInstance").invoke(null)
        val value = clazz.getMethod("getString", String::class.java)
            .invoke(instance, KEY_SAMSUNG_PRODUCT_NAME) as? String
        value?.takeIf { it.isNotBlank() }
    } catch (_: Throwable) {
        null
    }

    private fun getSamsungProductNameFromFloatingFeatureFile(): String? {
        val pattern = Regex(
            "<$KEY_SAMSUNG_PRODUCT_NAME>\\s*(.*?)\\s*</$KEY_SAMSUNG_PRODUCT_NAME>",
            RegexOption.DOT_MATCHES_ALL,
        )
        return SAMSUNG_FLOATING_FEATURE_PATHS.firstNotNullOfOrNull { path ->
            try {
                pattern.find(java.io.File(path).readText())?.groupValues?.getOrNull(1)
                    ?.takeIf { it.isNotBlank() }
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun getDeviceInfo(): String = runCatching {
        val manufacturer = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        val brand = Build.BRAND.orEmpty().takeUnless {
            it.equals(Build.MANUFACTURER, ignoreCase = true)
        }.orEmpty()
        listOf(manufacturer, brand, Build.MODEL.orEmpty())
            .filter(String::isNotBlank)
            .joinToString(" ")
    }.getOrDefault("Unknown Device")
}
