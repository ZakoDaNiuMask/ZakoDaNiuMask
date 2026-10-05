/*
 * Copyright 2026 Duck Apps Contributor
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
 *
 * Preliminary subset ported from Duck-Detector-Refactoring
 * (https://github.com/eltavine/Duck-Detector-Refactoring): boot-state system properties plus the
 * KeyStore-attested root of trust. The Widevine and native property-area probes are not ported.
 */

package com.zakodaniumask.manager.data.bootloader

import android.content.Context
import com.zakodaniumask.manager.data.attestation.AttestationSnapshot
import com.zakodaniumask.manager.data.attestation.BootConsistencyProbe
import com.zakodaniumask.manager.data.attestation.CertificateTrustAnalyzer
import com.zakodaniumask.manager.data.attestation.GoogleAttestationRootStore
import com.zakodaniumask.manager.data.attestation.AndroidAttestationCollector
import com.zakodaniumask.manager.data.attestation.SystemPropertiesReader
import com.zakodaniumask.manager.data.attestation.TeeTrustRoot
import com.zakodaniumask.manager.data.detection.DetectorStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BootloaderProperty(val name: String, val value: String?)

data class BootloaderReport(
    val status: DetectorStatus,
    val locked: Boolean?,
    val verifiedBootState: String?,
    val trustRoot: TeeTrustRoot,
    val chainValid: Boolean,
    val osVersion: String?,
    val osPatchLevel: String?,
    val properties: List<BootloaderProperty>,
    val consistencyDetail: String?,
    val error: String? = null,
)

class BootloaderDetector(context: Context) {

    private val appContext = context.applicationContext

    suspend fun scan(): BootloaderReport = withContext(Dispatchers.IO) {
        val snapshot: AttestationSnapshot = AndroidAttestationCollector().collect()
        val trust = runCatching {
            CertificateTrustAnalyzer(GoogleAttestationRootStore(appContext))
                .inspect(snapshot.rawCertificates)
        }.getOrDefault(
            com.zakodaniumask.manager.data.attestation.CertificateTrustResult()
        )
        val consistency = BootConsistencyProbe().inspect(snapshot)
        val properties = TRACKED_PROPERTIES.map { name ->
            BootloaderProperty(name, SystemPropertiesReader.read(name).value)
        }
        val root = snapshot.rootOfTrust
        val locked = root?.deviceLocked
        val bootState = root?.verifiedBootState

        val status = when {
            snapshot.errorMessage != null && root == null -> DetectorStatus.ERROR
            root == null -> DetectorStatus.SUPPORT
            locked == false -> DetectorStatus.DANGER
            bootState != null && !bootState.equals("Verified", ignoreCase = true) ->
                DetectorStatus.DANGER
            consistency.hasHardAnomaly -> DetectorStatus.DANGER
            !trust.chainSignatureValid -> DetectorStatus.SUPPORT
            else -> DetectorStatus.CLEAR
        }

        BootloaderReport(
            status = status,
            locked = locked,
            verifiedBootState = bootState,
            trustRoot = trust.trustRoot,
            chainValid = trust.chainSignatureValid,
            osVersion = snapshot.osVersion,
            osPatchLevel = snapshot.osPatchLevel,
            properties = properties,
            consistencyDetail = consistency.detail,
            error = snapshot.errorMessage,
        )
    }

    companion object {
        private val TRACKED_PROPERTIES = listOf(
            "ro.boot.flash.locked",
            "ro.boot.verifiedbootstate",
            "ro.boot.vbmeta.device_state",
            "ro.boot.veritymode",
            "ro.boot.vbmeta.digest",
            "ro.boot.vbmeta.hash_alg",
            "ro.boot.vbmeta.size",
            "ro.boot.avb_version",
            "ro.boot.warranty_bit",
            "ro.warranty_bit",
            "ro.boot.knox.state",
            "ro.oem_unlock_supported",
            "ro.debuggable",
            "ro.secure",
        )
    }
}
