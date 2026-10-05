/*
 * Copyright 2026 Duck Apps Contributor
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
 *
 * Preliminary subset ported from Duck-Detector-Refactoring
 * (https://github.com/eltavine/Duck-Detector-Refactoring): KeyStore attestation chain basics.
 * CRL/network, Soter, StrongBox/KeyMint deep checks and native probes are not ported.
 */

package com.zakodaniumask.manager.data.tee

import android.content.Context
import com.zakodaniumask.manager.data.attestation.AndroidAttestationCollector
import com.zakodaniumask.manager.data.attestation.AttestationSnapshot
import com.zakodaniumask.manager.data.attestation.CertificateTrustAnalyzer
import com.zakodaniumask.manager.data.attestation.CertificateTrustResult
import com.zakodaniumask.manager.data.attestation.GoogleAttestationRootStore
import com.zakodaniumask.manager.data.attestation.TeeCertificateItem
import com.zakodaniumask.manager.data.attestation.TeeTier
import com.zakodaniumask.manager.data.attestation.TeeTrustRoot
import com.zakodaniumask.manager.data.detection.DetectorStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TeeReport(
    val status: DetectorStatus,
    val tier: TeeTier,
    val attestationVersion: Int?,
    val keymasterVersion: Int?,
    val attestationTier: TeeTier?,
    val keymasterTier: TeeTier?,
    val verifiedBootState: String?,
    val deviceLocked: Boolean?,
    val challengeVerified: Boolean,
    val trustRoot: TeeTrustRoot,
    val chainValid: Boolean,
    val certificates: List<TeeCertificateItem>,
    val error: String? = null,
)

class TeeDetector(context: Context) {

    private val appContext = context.applicationContext

    suspend fun scan(): TeeReport = withContext(Dispatchers.IO) {
        val snapshot: AttestationSnapshot = AndroidAttestationCollector().collect()
        val trust: CertificateTrustResult = runCatching {
            CertificateTrustAnalyzer(GoogleAttestationRootStore(appContext))
                .inspect(snapshot.rawCertificates)
        }.getOrDefault(CertificateTrustResult())
        val root = snapshot.rootOfTrust

        val status = when {
            snapshot.errorMessage != null && snapshot.tier == TeeTier.UNKNOWN ->
                DetectorStatus.ERROR
            snapshot.tier == TeeTier.SOFTWARE -> DetectorStatus.DANGER
            trust.trustRoot != TeeTrustRoot.GOOGLE && trust.trustRoot != TeeTrustRoot.GOOGLE_RKP ->
                DetectorStatus.DANGER
            !trust.chainSignatureValid -> DetectorStatus.SUPPORT
            snapshot.tier == TeeTier.UNKNOWN -> DetectorStatus.SUPPORT
            else -> DetectorStatus.CLEAR
        }

        TeeReport(
            status = status,
            tier = snapshot.tier,
            attestationVersion = snapshot.attestationVersion,
            keymasterVersion = snapshot.keymasterVersion,
            attestationTier = snapshot.attestationTier,
            keymasterTier = snapshot.keymasterTier,
            verifiedBootState = root?.verifiedBootState,
            deviceLocked = root?.deviceLocked,
            challengeVerified = snapshot.challengeVerified,
            trustRoot = trust.trustRoot,
            chainValid = trust.chainSignatureValid,
            certificates = snapshot.displayCertificates,
            error = snapshot.errorMessage,
        )
    }
}
