// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from Duck ToolBox (MIT), crates/duck-rkp/src/cose + crypto_kdf.rs; modified for ZakoDaNiuMask.
package com.zakodaniumask.manager.data.rkp

import org.bouncycastle.asn1.nist.NISTNamedCurves
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.macs.CMac
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.crypto.signers.HMacDSAKCalculator
import java.math.BigInteger
import java.security.SecureRandom

/** Ed25519 / P-256 / X25519 / AES-GCM / HMAC-SHA256 / HKDF-SHA256 / AES-CMAC primitives. */
object RkpCrypto {
    private val random = SecureRandom()
    private val p256: ECDomainParameters by lazy {
        val params = NISTNamedCurves.getByName("P-256")
        ECDomainParameters(params.curve, params.g, params.n, params.h)
    }

    val p256Order: BigInteger get() = p256.n

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

    fun sha256(data: ByteArray): ByteArray =
        SHA256Digest().apply { update(data, 0, data.size) }
            .let { digest -> ByteArray(32).also { digest.doFinal(it, 0) } }

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = HMac(SHA256Digest())
        mac.init(KeyParameter(key))
        mac.update(data, 0, data.size)
        return ByteArray(mac.macSize).also { mac.doFinal(it, 0) }
    }

    fun hkdfSha256(ikm: ByteArray, info: ByteArray, length: Int = 32): ByteArray {
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(ikm, ByteArray(32), info))
        return ByteArray(length).also { hkdf.generateBytes(it, 0, length) }
    }

    fun aesGcmEncrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = GCMBlockCipher(AESEngine())
        cipher.init(true, AEADParameters(KeyParameter(key), 128, nonce, aad))
        val output = ByteArray(cipher.getOutputSize(plaintext.size))
        val length = cipher.processBytes(plaintext, 0, plaintext.size, output, 0)
        cipher.doFinal(output, length)
        return output
    }

    /** AES-CMAC in NIST SP 800-108 counter mode: `counter_be32 || label`. */
    fun kdfFromHwKey(hwKey: ByteArray, label: ByteArray, length: Int): ByteArray {
        val output = ByteArray(length)
        var position = 0
        var counter = 1
        while (position < length) {
            val mac = CMac(AESEngine())
            mac.init(KeyParameter(hwKey))
            val message = ByteArray(4 + label.size)
            message[0] = ((counter shr 24) and 0xFF).toByte()
            message[1] = ((counter shr 16) and 0xFF).toByte()
            message[2] = ((counter shr 8) and 0xFF).toByte()
            message[3] = (counter and 0xFF).toByte()
            System.arraycopy(label, 0, message, 4, label.size)
            mac.update(message, 0, message.size)
            val block = ByteArray(mac.macSize).also { mac.doFinal(it, 0) }
            val take = minOf(block.size, output.size - position)
            System.arraycopy(block, 0, output, position, take)
            position += take
            counter++
        }
        return output
    }

    // ---- DICE device key: raw DICE_CDI leaf key material from a 32-byte seed ----

    sealed interface DeviceKey {
        val algorithm: Long
        fun sign(payload: ByteArray): ByteArray
        fun coseKey(): Map<Any?, Any?>
    }

    fun ed25519FromSeed(seed: ByteArray): DeviceKey {
        val key = Ed25519PrivateKeyParameters(seed, 0)
        val public = key.generatePublicKey().encoded
        return object : DeviceKey {
            override val algorithm = -8L
            override fun sign(payload: ByteArray) =
                Ed25519Signer().apply { init(true, key) }.generateSignature(payload)
            override fun coseKey() = linkedMapOf<Any?, Any?>(1L to 1L, 3L to -8L, -1L to 6L, -2L to public)
        }
    }

    fun p256FromSeed(seed: ByteArray): DeviceKey {
        val scalar = BigInteger(1, seed).mod(p256.n.subtract(BigInteger.ONE)).add(BigInteger.ONE)
        val (x, y) = p256Public(scalar)
        return object : DeviceKey {
            override val algorithm = -7L
            override fun sign(payload: ByteArray): ByteArray {
                val signer = ECDSASigner(HMacDSAKCalculator(SHA256Digest()))
                signer.init(true, ECPrivateKeyParameters(scalar, p256))
                val signature = signer.generateSignature(sha256(payload))
                return pad32(signature[0].toByteArray()) + pad32(signature[1].toByteArray())
            }
            override fun coseKey() = linkedMapOf<Any?, Any?>(1L to 2L, 3L to -7L, -1L to 1L, -2L to x, -3L to y)
        }
    }

    fun p256Public(scalar: BigInteger): Pair<ByteArray, ByteArray> {
        val point = p256.g.multiply(scalar).normalize()
        return pad32(point.affineXCoord?.encoded ?: ByteArray(32)) to
            pad32(point.affineYCoord?.encoded ?: ByteArray(32))
    }

    fun p256Generate(): BigInteger = BigInteger(1, randomBytes(32)).mod(p256.n.subtract(BigInteger.ONE)).add(BigInteger.ONE)

    fun p256Agreement(scalar: BigInteger, serverX: ByteArray, serverY: ByteArray): ByteArray {
        val q = p256.curve.createPoint(BigInteger(1, serverX), BigInteger(1, serverY))
        val agreement = ECDHBasicAgreement()
        agreement.init(ECPrivateKeyParameters(scalar, p256))
        return pad32(agreement.calculateAgreement(ECPublicKeyParameters(q, p256)).toByteArray())
    }

    // ---- X25519 ----

    class X25519(val privateKey: X25519PrivateKeyParameters) {
        val publicBytes: ByteArray = privateKey.generatePublicKey().encoded
        fun agreement(serverPublic: ByteArray): ByteArray {
            val agreement = X25519Agreement()
            agreement.init(privateKey)
            val output = ByteArray(agreement.agreementSize)
            agreement.calculateAgreement(X25519PublicKeyParameters(serverPublic, 0), output, 0)
            return output
        }
    }

    fun x25519Generate(): X25519 = X25519(X25519PrivateKeyParameters(random))

    fun pad32(bytes: ByteArray): ByteArray {
        val trimmed = bytes.dropWhile { it == 0.toByte() }.toByteArray()
        if (trimmed.size == 32) return trimmed
        if (trimmed.size > 32) return trimmed.copyOfRange(trimmed.size - 32, trimmed.size)
        val padded = ByteArray(32)
        System.arraycopy(trimmed, 0, padded, 32 - trimmed.size, trimmed.size)
        return padded
    }
}
