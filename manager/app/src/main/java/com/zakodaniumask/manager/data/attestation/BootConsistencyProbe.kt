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

class BootConsistencyProbe {

    fun inspect(snapshot: AttestationSnapshot): BootConsistencyResult {
        val property = SystemPropertiesReader.read(VBMETA_DIGEST_PROP)
        val root = snapshot.rootOfTrust
        if (root == null) {
            return BootConsistencyResult(
                runtimePropsAvailable = property.available,
                runtimeVbmetaDigest = normalizeHex(property.value),
                detail = "Boot consistency check unavailable because attested root of trust was missing.",
            )
        }

        val attestedBootHash = normalizeHex(root.verifiedBootHashHex)
        val runtimeVbmetaDigest = normalizeHex(property.value)
        val bootState = parseBootState(root.verifiedBootState)
        val compareRuntimeDigest =
            bootState == ParsedBootState.VERIFIED || bootState == ParsedBootState.SELF_SIGNED
        val verifiedBootHashAllZeros = compareRuntimeDigest && isAllZeroHex(root.verifiedBootHashHex)
        val verifiedBootKeyAllZeros = compareRuntimeDigest && isAllZeroHex(root.verifiedBootKeyHex)
        val verifiedStateUnlockedObserved =
            bootState == ParsedBootState.VERIFIED && root.deviceLocked == false
        val vbmetaDigestMissingWhileAttestedHashPresent =
            compareRuntimeDigest && attestedBootHash != null && property.available &&
                runtimeVbmetaDigest == null
        val vbmetaDigestMismatch =
            compareRuntimeDigest && attestedBootHash != null && runtimeVbmetaDigest != null &&
                attestedBootHash != runtimeVbmetaDigest
        val runtimeComparisonPerformed =
            compareRuntimeDigest && attestedBootHash != null && runtimeVbmetaDigest != null

        return BootConsistencyResult(
            vbmetaDigestMismatch = vbmetaDigestMismatch,
            vbmetaDigestMissingWhileAttestedHashPresent = vbmetaDigestMissingWhileAttestedHashPresent,
            verifiedBootHashAllZeros = verifiedBootHashAllZeros,
            verifiedBootKeyAllZeros = verifiedBootKeyAllZeros,
            runtimeComparisonPerformed = runtimeComparisonPerformed,
            runtimePropsAvailable = property.available,
            runtimeVbmetaDigest = runtimeVbmetaDigest,
            detail = buildString {
                val issues = buildList {
                    if (vbmetaDigestMismatch) {
                        add("Attested verifiedBootHash did not match ro.boot.vbmeta.digest.")
                    }
                    if (vbmetaDigestMissingWhileAttestedHashPresent) {
                        add("Attested verifiedBootHash was present, but ro.boot.vbmeta.digest was empty.")
                    }
                    if (verifiedBootHashAllZeros) add("Attested verifiedBootHash was all zeros.")
                    if (verifiedBootKeyAllZeros) add("Attested verifiedBootKey was all zeros.")
                }
                when {
                    issues.isNotEmpty() -> append(issues.joinToString(separator = " "))
                    bootState == ParsedBootState.FAILED ->
                        append("Attestation reported Failed; AOSP does not guarantee other RootOfTrust fields in this state.")
                    bootState == ParsedBootState.UNVERIFIED ->
                        append("Attestation reported Unverified; AOSP allows an all-zero verifiedBootKey and runtime vbmeta comparison was skipped.")
                    verifiedStateUnlockedObserved ->
                        append("Attestation reported Verified while deviceLocked=false; AOSP allows this on approved test devices, so no anomaly was raised.")
                    !property.available -> append("Boot consistency check could not read ro.boot.vbmeta.digest.")
                    !compareRuntimeDigest ->
                        append("Boot state ${root.verifiedBootState ?: UNKNOWN_BOOT_STATE} was recorded without runtime vbmeta comparison.")
                    attestedBootHash == null ->
                        append("Attestation did not expose verifiedBootHash for runtime comparison.")
                    runtimeVbmetaDigest == null ->
                        append("Runtime vbmeta digest was unavailable for comparison.")
                    else -> append("Attested verifiedBootHash matched ro.boot.vbmeta.digest.")
                }
            },
        )
    }

    companion object {
        const val VBMETA_DIGEST_PROP = "ro.boot.vbmeta.digest"
        private const val UNKNOWN_BOOT_STATE = "Unknown"

        fun normalizeHex(raw: String?): String? {
            val cleaned = raw
                ?.filterNot { it.isWhitespace() || it == ':' }
                ?.lowercase()
                ?.takeIf { it.isNotBlank() }
            return cleaned?.takeIf { value -> value.all { it in '0'..'9' || it in 'a'..'f' } }
        }

        fun isAllZeroHex(raw: String?): Boolean {
            val normalized = normalizeHex(raw) ?: return false
            return normalized.isNotEmpty() && normalized.all { it == '0' }
        }

        private fun parseBootState(raw: String?): ParsedBootState =
            when (raw?.trim()?.lowercase()) {
                "verified" -> ParsedBootState.VERIFIED
                "self-signed" -> ParsedBootState.SELF_SIGNED
                "unverified" -> ParsedBootState.UNVERIFIED
                "failed" -> ParsedBootState.FAILED
                else -> ParsedBootState.UNKNOWN
            }
    }
}

private enum class ParsedBootState { VERIFIED, SELF_SIGNED, UNVERIFIED, FAILED, UNKNOWN }
