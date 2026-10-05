/*
 * Copyright 2026 Duck Apps Contributor
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
 *
 * Ported from Duck-Detector-Refactoring (https://github.com/eltavine/Duck-Detector-Refactoring).
 */

package com.zakodaniumask.manager.data.attestation

import java.security.cert.X509Certificate

enum class TeeTier { UNKNOWN, NONE, SOFTWARE, TEE, STRONGBOX }

enum class TeeTrustRoot { UNKNOWN, FACTORY, GOOGLE, GOOGLE_RKP, AOSP }

data class TeeCertificateItem(
    val slotLabel: String,
    val subject: String,
    val issuer: String,
    val serialNumber: String,
    val validFrom: String,
    val validUntil: String,
    val signatureAlgorithm: String,
    val publicKeySummary: String,
)

data class RootOfTrustSnapshot(
    val verifiedBootKeyHex: String?,
    val deviceLocked: Boolean?,
    val verifiedBootState: String?,
    val verifiedBootHashHex: String?,
)

data class AttestedKeyProperties(
    val algorithm: String? = null,
    val keySize: Int? = null,
    val ecCurve: String? = null,
    val purposes: List<String> = emptyList(),
    val digests: List<String> = emptyList(),
    val paddings: List<String> = emptyList(),
    val origin: String? = null,
    val rollbackResistant: Boolean = false,
)

data class AttestedAuthState(
    val noAuthRequired: Boolean? = null,
    val userAuthTypes: List<String> = emptyList(),
    val authTimeoutSeconds: Int? = null,
    val trustedConfirmationRequired: Boolean = false,
    val trustedPresenceRequired: Boolean = false,
    val unlockedDeviceRequired: Boolean = false,
)

data class AttestedApplicationInfo(
    val packageNames: List<String> = emptyList(),
    val signatureDigestsSha256: List<String> = emptyList(),
    val rawBytesHex: String? = null,
)

data class AttestedDeviceInfo(
    val brand: String? = null,
    val device: String? = null,
    val product: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val serial: String? = null,
    val imei: String? = null,
    val secondImei: String? = null,
    val meid: String? = null,
)

data class AttestationSnapshot(
    val tier: TeeTier,
    val attestationVersion: Int?,
    val keymasterVersion: Int?,
    val attestationTier: TeeTier?,
    val keymasterTier: TeeTier?,
    val challengeVerified: Boolean,
    val challengeSummary: String?,
    val rootOfTrust: RootOfTrustSnapshot?,
    val osVersion: String?,
    val osPatchLevel: String?,
    val vendorPatchLevel: String?,
    val bootPatchLevel: String?,
    val keyProperties: AttestedKeyProperties = AttestedKeyProperties(),
    val authState: AttestedAuthState = AttestedAuthState(),
    val applicationInfo: AttestedApplicationInfo = AttestedApplicationInfo(),
    val deviceInfo: AttestedDeviceInfo = AttestedDeviceInfo(),
    val deviceUniqueAttestation: Boolean = false,
    val moduleHashHex: String? = null,
    val trustedAttestationIndex: Int? = null,
    val rawCertificates: List<X509Certificate>,
    val displayCertificates: List<TeeCertificateItem>,
    val errorMessage: String? = null,
)

data class BootConsistencyResult(
    val vbmetaDigestMismatch: Boolean = false,
    val vbmetaDigestMissingWhileAttestedHashPresent: Boolean = false,
    val verifiedBootHashAllZeros: Boolean = false,
    val verifiedBootKeyAllZeros: Boolean = false,
    val runtimeComparisonPerformed: Boolean = false,
    val runtimePropsAvailable: Boolean = false,
    val runtimeVbmetaDigest: String? = null,
    val detail: String = "Boot consistency check unavailable.",
) {
    val hasHardAnomaly: Boolean
        get() = vbmetaDigestMismatch ||
            vbmetaDigestMissingWhileAttestedHashPresent ||
            verifiedBootHashAllZeros ||
            verifiedBootKeyAllZeros
}

data class CertificateTrustResult(
    val trustRoot: TeeTrustRoot = TeeTrustRoot.UNKNOWN,
    val chainLength: Int = 0,
    val chainSignatureValid: Boolean = false,
    val rootFingerprint: String? = null,
    val googleRootMatched: Boolean = false,
    val issuerMismatches: List<String> = emptyList(),
    val expiredCertificates: List<String> = emptyList(),
)

data class PropertyReadResult(
    val available: Boolean,
    val value: String? = null,
)
