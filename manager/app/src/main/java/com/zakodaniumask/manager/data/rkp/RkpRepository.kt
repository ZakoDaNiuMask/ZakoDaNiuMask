// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from Duck ToolBox (MIT), crates/duck-rkp; modified for ZakoDaNiuMask.
package com.zakodaniumask.manager.data.rkp

import android.content.Context
import com.zakodaniumask.manager.data.AppSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.sec.ECPrivateKey
import org.json.JSONObject
import java.io.File
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.concurrent.TimeUnit

enum class DiceCurve(val id: String) { ED25519("ed25519"), P256("p256") }

enum class KeySourceKind(val id: String) { UNSET("unset"), SEED("seed"), HW_KEY("hw-key") }

data class KeySource(
    val kind: KeySourceKind = KeySourceKind.UNSET,
    val seedHex: String = "",
    val hwKeyHex: String = "",
    val kdfLabel: String = "rkp_bcc_km",
)

data class DeviceInfo(
    val brand: String = "generic",
    val manufacturer: String = "generic",
    val product: String = "default",
    val model: String = "default",
    val device: String = "default",
    val vbState: String = "green",
    val bootloaderState: String = "locked",
    val systemPatchLevel: Int = 202501,
    val bootPatchLevel: Int = 20250101,
    val vendorPatchLevel: Int = 20250101,
    val securityLevel: String = "tee",
    val fused: Int = 1,
    val vbmetaDigest: String? = null,
    val osVersion: String = "13",
    val diceIssuer: String = "Android",
    val diceSubject: String = "KeyMint",
)

data class RkpProfile(
    val keySource: KeySource = KeySource(),
    val curve: DiceCurve = DiceCurve.ED25519,
    val device: DeviceInfo = DeviceInfo(),
    val fingerprint: String = "generic/default/default:13/TP1A.220624.014/0:user/release-keys",
    val serverUrl: String = "https://remoteprovisioning.googleapis.com/v1",
    val numKeys: Int = 1,
)

data class RkpInfoResult(
    val mode: String,
    val curve: String,
    val publicKeyHex: String,
    val serverUrl: String,
    val numKeys: Int,
)

data class RkpKeyboxResult(
    val keyboxPath: String,
    val keyboxXml: String,
    val deviceId: String,
    val certCount: Int,
)

class RkpRepository(
    private val context: Context,
    private val settings: AppSettingsRepository,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val outputDir: File get() = File(context.filesDir, "rkp").apply { mkdirs() }

    fun profile(): RkpProfile {
        val raw = settings.getString(KEY_PROFILE) ?: return RkpProfile()
        return runCatching { parseProfile(JSONObject(raw)) }.getOrDefault(RkpProfile())
    }

    fun saveProfile(profile: RkpProfile) =
        settings.putString(KEY_PROFILE, encodeProfile(profile).toString())

    fun clearProfile() = settings.remove(KEY_PROFILE)

    fun detect(): DeviceInfo = DeviceInfo(
        brand = prop("ro.product.brand").ifBlank { "generic" },
        manufacturer = prop("ro.product.manufacturer").ifBlank { "generic" },
        product = prop("ro.product.name").ifBlank { "default" },
        model = prop("ro.product.model").ifBlank { "default" },
        device = prop("ro.product.device").ifBlank { "default" },
        vbState = prop("ro.boot.verifiedbootstate").ifBlank { "green" },
        bootloaderState = if (prop("ro.boot.flash.locked") == "1") "locked" else "unlocked",
        systemPatchLevel = prop("ro.build.version.security_patch").filter { it.isDigit() }.toIntOrNull() ?: 202501,
        bootPatchLevel = prop("ro.vendor.boot_security_patch").filter { it.isDigit() }.toIntOrNull() ?: 20250101,
        vendorPatchLevel = prop("ro.vendor.build.security_patch").filter { it.isDigit() }.toIntOrNull() ?: 20250101,
        osVersion = prop("ro.build.version.release").ifBlank { "13" },
        vbmetaDigest = prop("ro.boot.vbmeta.digest").takeIf { it.isNotBlank() },
    )

    fun info(profile: RkpProfile): Result<RkpInfoResult> = runCatching {
        val key = deviceKey(profile)
        val cose = key.coseKey()
        val x = cose[-2L] as? ByteArray ?: ByteArray(0)
        val y = cose[-3L] as? ByteArray
        RkpInfoResult(
            mode = profile.keySource.kind.id,
            curve = profile.curve.id,
            publicKeyHex = hex(x) + (y?.let(::hex) ?: ""),
            serverUrl = profile.serverUrl,
            numKeys = profile.numKeys,
        )
    }

    fun keybox(profile: RkpProfile): Result<RkpKeyboxResult> = runCatching {
        val key = secureEcKey()
        val eek = fetchEek(profile)
        val device = deviceKey(profile)
        val keysToSign = listOf(coseKey(key))
        val csr = buildCsr(device, eek.challenge, keysToSign, eek.publicBytes, eek.curve, profile.device)
        val chain = submitCsr(profile.serverUrl, eek.challenge, csr)
        val xml = keyboxXml(key, chain)
        val deviceId = "${profile.device.manufacturer}-${hex(RkpCrypto.randomBytes(6))}"
        val file = File(outputDir, "keybox_${System.currentTimeMillis()}.xml").apply { writeText(xml) }
        RkpKeyboxResult(file.absolutePath, xml, deviceId, chain.size)
    }

    // ---- keys ----

    private class EcKey(val privateKey: PrivateKey, val x: ByteArray, val y: ByteArray)

    private fun secureEcKey(): EcKey {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val pair = generator.generateKeyPair()
        val publicKey = pair.public as ECPublicKey
        return EcKey(
            privateKey = pair.private,
            x = RkpCrypto.pad32(publicKey.w.affineX.toByteArray()),
            y = RkpCrypto.pad32(publicKey.w.affineY.toByteArray()),
        )
    }

    private fun coseKey(key: EcKey): Map<Any?, Any?> =
        linkedMapOf(1L to 2L, 3L to -7L, -1L to 1L, -2L to key.x, -3L to key.y)

    private fun deviceKey(profile: RkpProfile): RkpCrypto.DeviceKey {
        val seed = resolveSeed(profile.keySource)
        return when (profile.curve) {
            DiceCurve.ED25519 -> RkpCrypto.ed25519FromSeed(seed)
            DiceCurve.P256 -> RkpCrypto.p256FromSeed(seed)
        }
    }

    private fun resolveSeed(source: KeySource): ByteArray = when (source.kind) {
        KeySourceKind.UNSET -> error("key source is not set")
        KeySourceKind.SEED -> hexToBytes(source.seedHex).also { require(it.size == 32) { "seed must be 32 bytes" } }
        KeySourceKind.HW_KEY -> RkpCrypto.kdfFromHwKey(
            hexToBytes(source.hwKeyHex).also { require(it.size == 16) { "hardware key must be 16 bytes" } },
            source.kdfLabel.toByteArray(Charsets.UTF_8),
            32,
        )
    }

    // ---- RKP protocol ----

    private class Eek(val curve: Int, val publicBytes: ByteArray, val challenge: ByteArray)

    private fun fetchEek(profile: RkpProfile): Eek {
        val body = RkpCbor.encode(linkedMapOf<Any?, Any?>("fingerprint" to profile.fingerprint, "id" to 42L))
        val response = post("${profile.serverUrl}:fetchEekChain", body)
        val root = RkpCbor.Reader(response).read() as? List<*> ?: error("invalid fetchEekChain response")
        val chains = root.getOrNull(0) as? List<*> ?: error("fetchEekChain returned no chains")
        val challenge = (root.getOrNull(1) as? ByteArray) ?: ByteArray(0)

        var p256: ByteArray? = null
        var x25519: ByteArray? = null
        chains.forEach { chain ->
            (chain as? List<*>)?.forEach { entry ->
                val key = entry as? Map<*, *> ?: return@forEach
                val alg = key[3L] as? Long
                if (alg != -25L) return@forEach
                val crv = key[-1L] as? Long
                val x = key[-2L] as? ByteArray ?: return@forEach
                when (crv) {
                    2L -> if (x25519 == null) x25519 = x
                    1L -> {
                        val y = key[-3L] as? ByteArray
                        if (y != null && p256 == null) p256 = x + y
                    }
                }
            }
        }
        return when {
            x25519 != null -> Eek(2, x25519!!, challenge)
            p256 != null -> Eek(1, p256!!, challenge)
            else -> error("fetchEekChain returned no usable EEK")
        }
    }

    private fun buildCsr(
        device: RkpCrypto.DeviceKey,
        challenge: ByteArray,
        keysToSign: List<Map<Any?, Any?>>,
        eekPublic: ByteArray,
        eekCurve: Int,
        deviceInfo: DeviceInfo,
    ): ByteArray {
        val deviceCbor = deviceInfoCbor(deviceInfo)
        val csrPayload = RkpCbor.encode(listOf(3L, "keymint", deviceCbor, keysToSign))
        val dice = buildDiceChain(device, deviceInfo)
        val protectedData = buildProtectedData(device, challenge, keysToSign, eekPublic, eekCurve, deviceCbor, dice)
        val signedData = coseSign1(device, RkpCbor.encode(listOf(challenge, csrPayload)))
        return RkpCbor.encode(listOf(1L, emptyMap<Any?, Any?>(), dice, signedData, protectedData))
    }

    private fun buildDiceChain(device: RkpCrypto.DeviceKey, deviceInfo: DeviceInfo): List<Any?> {
        val payload = RkpCbor.encode(
            linkedMapOf<Any?, Any?>(
                1L to deviceInfo.diceIssuer,
                2L to deviceInfo.diceSubject,
                -4_670_552L to RkpCbor.encode(device.coseKey()),
                -4_670_553L to byteArrayOf(0x20),
            ),
        )
        return listOf(device.coseKey(), coseSign1(device, payload))
    }

    private fun coseSign1(device: RkpCrypto.DeviceKey, payload: ByteArray): List<Any?> {
        val protected = RkpCbor.encode(linkedMapOf<Any?, Any?>(1L to device.algorithm))
        val signatureInput = RkpCbor.encode(listOf("Signature1", protected, ByteArray(0), payload))
        return listOf(protected, emptyMap<Any?, Any?>(), payload, device.sign(signatureInput))
    }

    private fun buildProtectedData(
        device: RkpCrypto.DeviceKey,
        challenge: ByteArray,
        keysToSign: List<Map<Any?, Any?>>,
        eekPublic: ByteArray,
        eekCurve: Int,
        deviceCbor: ByteArray,
        dice: List<Any?>,
    ): ByteArray {
        val keysToSignCbor = RkpCbor.encode(keysToSign)
        val macKey = RkpCrypto.randomBytes(32)
        val keysToSignMac = buildKeysToSignMac(macKey, keysToSignCbor)
        val signedMacProtected = RkpCbor.encode(linkedMapOf<Any?, Any?>(1L to device.algorithm))
        val signedMacAad = RkpCbor.encode(listOf(challenge, deviceCbor, keysToSignMac))
        val signedMacInput = RkpCbor.encode(listOf("Signature1", signedMacProtected, signedMacAad, macKey))
        val signedMac = listOf(signedMacProtected, emptyMap<Any?, Any?>(), macKey, device.sign(signedMacInput))
        val plaintext = RkpCbor.encode(listOf(signedMac, dice))

        val transport = deriveTransportKey(eekCurve, eekPublic)
        val nonce = RkpCrypto.randomBytes(12)
        val protected = RkpCbor.encode(linkedMapOf<Any?, Any?>(1L to 3L))
        val aad = RkpCbor.encode(listOf("Encrypt", protected, ByteArray(0)))
        val ciphertext = RkpCrypto.aesGcmEncrypt(transport.key, nonce, aad, plaintext)
        val recipientProtected = RkpCbor.encode(linkedMapOf<Any?, Any?>(1L to -25L))
        val recipient = listOf(recipientProtected, linkedMapOf<Any?, Any?>(-1L to transport.coseKey), null)
        return RkpCbor.encode(
            listOf(protected, linkedMapOf<Any?, Any?>(5L to nonce), ciphertext, listOf(recipient)),
        )
    }

    private class Transport(val key: ByteArray, val coseKey: Map<Any?, Any?>)

    private fun deriveTransportKey(eekCurve: Int, eekPublic: ByteArray): Transport {
        val clientPub: ByteArray
        val shared: ByteArray
        val coseKey: Map<Any?, Any?>
        if (eekCurve == 2) {
            val ephemeral = RkpCrypto.x25519Generate()
            clientPub = ephemeral.publicBytes
            shared = ephemeral.agreement(eekPublic)
            coseKey = linkedMapOf(1L to 1L, -1L to 4L, -2L to clientPub)
        } else {
            val scalar = RkpCrypto.p256Generate()
            val (x, y) = RkpCrypto.p256Public(scalar)
            clientPub = x + y
            shared = RkpCrypto.p256Agreement(scalar, eekPublic.copyOfRange(0, 32), eekPublic.copyOfRange(32, 64))
            coseKey = linkedMapOf(1L to 2L, -1L to 1L, -2L to x, -3L to y)
        }
        val context = RkpCbor.encode(
            listOf(
                3L,
                listOf("client", ByteArray(0), clientPub),
                listOf("server", ByteArray(0), eekPublic),
                listOf(256L, ByteArray(0)),
            ),
        )
        return Transport(RkpCrypto.hkdfSha256(shared, context), coseKey)
    }

    private fun buildKeysToSignMac(macKey: ByteArray, keysToSignCbor: ByteArray): ByteArray {
        val macProtected = RkpCbor.encode(linkedMapOf<Any?, Any?>(1L to 5L))
        val macStructure = RkpCbor.encode(listOf("MAC0", macProtected, ByteArray(0), keysToSignCbor))
        return RkpCrypto.hmacSha256(macKey, macStructure)
    }

    private fun submitCsr(serverUrl: String, challenge: ByteArray, csr: ByteArray): List<ByteArray> {
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(challenge)
        val response = post("$serverUrl:signCertificates?challenge=$encoded", csr)
        val root = RkpCbor.Reader(response).read() as? List<*> ?: error("invalid signCertificates response")
        val shared = root.getOrNull(0) as? ByteArray ?: error("signCertificates returned no shared chain")
        val unique = root.getOrNull(1) as? List<*> ?: error("signCertificates returned no unique chains")
        val first = unique.firstOrNull() as? ByteArray ?: error("signCertificates returned no chain")
        return splitDerCertificates(shared + first)
    }

    // ---- keybox ----

    private fun keyboxXml(key: EcKey, chain: List<ByteArray>): String = buildString {
        appendLine("<?xml version=\"1.0\"?>")
        appendLine("<AndroidAttestation>")
        appendLine("<NumberOfKeyboxes>1</NumberOfKeyboxes>")
        appendLine("<Keybox DeviceID=\"unknown\">")
        appendLine("<Key algorithm=\"ecdsa\">")
        appendLine("<PrivateKey format=\"pem\">${ecPrivateKeyPem(key.privateKey)}</PrivateKey>")
        appendLine("<CertificateChain>")
        appendLine("<NumberOfCertificates>${chain.size}</NumberOfCertificates>")
        chain.forEach { appendLine("<Certificate format=\"pem\">${pem("CERTIFICATE", it)}</Certificate>") }
        appendLine("</CertificateChain>")
        appendLine("</Key>")
        appendLine("</Keybox>")
        appendLine("</AndroidAttestation>")
    }

    private fun ecPrivateKeyPem(privateKey: PrivateKey): String {
        val sec1 = ECPrivateKey.getInstance(PrivateKeyInfo.getInstance(privateKey.encoded).parsePrivateKey()).encoded
        return pem("EC PRIVATE KEY", sec1)
    }

    private fun pem(type: String, content: ByteArray): String {
        val base64 = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(content)
        return "-----BEGIN $type-----\n$base64\n-----END $type-----"
    }

    private fun splitDerCertificates(data: ByteArray): List<ByteArray> {
        val certs = mutableListOf<ByteArray>()
        var offset = 0
        while (offset + 4 <= data.size) {
            if (data[offset] != 0x30.toByte()) break
            val length = derLength(data, offset + 1) ?: break
            val total = length.first + length.second
            if (offset + total > data.size) break
            certs += data.copyOfRange(offset, offset + total)
            offset += total
        }
        return certs
    }

    private fun derLength(data: ByteArray, offset: Int): Pair<Int, Int>? {
        if (offset >= data.size) return null
        val first = data[offset].toInt() and 0xFF
        if (first < 0x80) return first to 2
        val count = first and 0x7F
        if (count == 0 || count > 4 || offset + 1 + count > data.size) return null
        var value = 0
        for (i in 0 until count) value = (value shl 8) or (data[offset + 1 + i].toInt() and 0xFF)
        return value to (2 + count)
    }

    private fun post(url: String, body: ByteArray): ByteArray {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/cbor".toMediaType()))
            .header("Accept", "application/cbor")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return response.body?.bytes() ?: error("empty response")
        }
    }

    private fun deviceInfoCbor(device: DeviceInfo): ByteArray {
        val entries = linkedMapOf<Any?, Any?>(
            "brand" to device.brand,
            "manufacturer" to device.manufacturer,
            "product" to device.product,
            "model" to device.model,
            "device" to device.device,
            "vb_state" to device.vbState,
            "bootloader_state" to device.bootloaderState,
            "system_patch_level" to device.systemPatchLevel.toLong(),
            "boot_patch_level" to device.bootPatchLevel.toLong(),
            "vendor_patch_level" to device.vendorPatchLevel.toLong(),
            "security_level" to device.securityLevel,
            "fused" to device.fused.toLong(),
        )
        device.vbmetaDigest?.let { entries["vbmeta_digest"] = hexToBytes(it) }
        if (device.osVersion.isNotBlank()) entries["os_version"] = device.osVersion
        return RkpCbor.encode(entries)
    }

    // ---- persistence ----

    private fun encodeProfile(profile: RkpProfile): JSONObject = JSONObject().apply {
        put("key_source", JSONObject().apply {
            put("kind", profile.keySource.kind.id)
            put("seed_hex", profile.keySource.seedHex)
            put("hw_key_hex", profile.keySource.hwKeyHex)
            put("kdf_label", profile.keySource.kdfLabel)
        })
        put("curve", profile.curve.id)
        put("device", JSONObject().apply {
            put("brand", profile.device.brand)
            put("manufacturer", profile.device.manufacturer)
            put("product", profile.device.product)
            put("model", profile.device.model)
            put("device", profile.device.device)
            put("vb_state", profile.device.vbState)
            put("bootloader_state", profile.device.bootloaderState)
            put("system_patch_level", profile.device.systemPatchLevel)
            put("boot_patch_level", profile.device.bootPatchLevel)
            put("vendor_patch_level", profile.device.vendorPatchLevel)
            put("security_level", profile.device.securityLevel)
            put("fused", profile.device.fused)
            put("vbmeta_digest", profile.device.vbmetaDigest)
            put("os_version", profile.device.osVersion)
            put("dice_issuer", profile.device.diceIssuer)
            put("dice_subject", profile.device.diceSubject)
        })
        put("fingerprint", profile.fingerprint)
        put("server_url", profile.serverUrl)
        put("num_keys", profile.numKeys)
    }

    private fun parseProfile(json: JSONObject): RkpProfile {
        val source = json.optJSONObject("key_source")
        val device = json.optJSONObject("device")
        return RkpProfile(
            keySource = KeySource(
                kind = KeySourceKind.entries.firstOrNull { it.id == source?.optString("kind") } ?: KeySourceKind.UNSET,
                seedHex = source?.optString("seed_hex").orEmpty(),
                hwKeyHex = source?.optString("hw_key_hex").orEmpty(),
                kdfLabel = source?.optString("kdf_label").orEmpty().ifBlank { "rkp_bcc_km" },
            ),
            curve = DiceCurve.entries.firstOrNull { it.id == json.optString("curve") } ?: DiceCurve.ED25519,
            device = DeviceInfo(
                brand = device?.optString("brand").orEmpty().ifBlank { "generic" },
                manufacturer = device?.optString("manufacturer").orEmpty().ifBlank { "generic" },
                product = device?.optString("product").orEmpty().ifBlank { "default" },
                model = device?.optString("model").orEmpty().ifBlank { "default" },
                device = device?.optString("device").orEmpty().ifBlank { "default" },
                vbState = device?.optString("vb_state").orEmpty().ifBlank { "green" },
                bootloaderState = device?.optString("bootloader_state").orEmpty().ifBlank { "locked" },
                systemPatchLevel = device?.optInt("system_patch_level", 202501) ?: 202501,
                bootPatchLevel = device?.optInt("boot_patch_level", 20250101) ?: 20250101,
                vendorPatchLevel = device?.optInt("vendor_patch_level", 20250101) ?: 20250101,
                securityLevel = device?.optString("security_level").orEmpty().ifBlank { "tee" },
                fused = device?.optInt("fused", 1) ?: 1,
                vbmetaDigest = device?.optString("vbmeta_digest")?.takeIf { it.isNotBlank() },
                osVersion = device?.optString("os_version").orEmpty().ifBlank { "13" },
                diceIssuer = device?.optString("dice_issuer").orEmpty().ifBlank { "Android" },
                diceSubject = device?.optString("dice_subject").orEmpty().ifBlank { "KeyMint" },
            ),
            fingerprint = json.optString("fingerprint").ifBlank {
                "generic/default/default:13/TP1A.220624.014/0:user/release-keys"
            },
            serverUrl = json.optString("server_url").ifBlank { "https://remoteprovisioning.googleapis.com/v1" },
            numKeys = json.optInt("num_keys", 1).coerceIn(1, 4),
        )
    }

    private fun prop(name: String): String = runCatching {
        val systemProperties = Class.forName("android.os.SystemProperties")
        val get = systemProperties.getMethod("get", String::class.java, String::class.java)
        (get.invoke(null, name, "") as? String).orEmpty()
    }.getOrDefault("")

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun hexToBytes(value: String): ByteArray {
        val trimmed = value.trim()
        require(trimmed.length % 2 == 0) { "invalid hex" }
        return ByteArray(trimmed.length / 2) { index ->
            trimmed.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private companion object {
        const val KEY_PROFILE = "rkp_profile"
    }
}
