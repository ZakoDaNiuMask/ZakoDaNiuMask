// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from Duck ToolBox (MIT), crates/duck-tricky-store/src/keybox/generate.rs; modified for ZakoDaNiuMask.
package com.zakodaniumask.manager.data.trickystore

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.pkcs.RSAPrivateKey
import org.bouncycastle.asn1.sec.ECPrivateKey
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.util.io.pem.PemObject
import java.io.StringWriter
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date

/**
 * Generates the self-signed "unknown" keybox Tricky Addon ships: a P-256 EC and an RSA-2048
 * key, each with a self-signed root CA plus a batch CA signed by it.
 */
object KeyboxGenerator {
    private const val VALIDITY_DAYS = 3650L
    private const val DAY_MS = 24L * 60 * 60 * 1000

    private data class GeneratedKey(
        val algorithm: String,
        val privatePem: String,
        val chainPems: List<String>,
    )

    fun unknownKeybox(): String {
        val ec = generate(algorithm = "EC", parameter = "secp256r1", signature = "SHA256withECDSA")
        val rsa = generate(algorithm = "RSA", parameter = "2048", signature = "SHA256withRSA")
        return buildXml(listOf(ec, rsa))
    }

    private fun generate(algorithm: String, parameter: String, signature: String): GeneratedKey {
        val keyPair = keyPair(algorithm, parameter)
        val root = selfSignedRoot(keyPair, signature)
        val batch = signedBy(root, keyPair, keyPair, signature)
        val privatePem = privateKeyPem(keyPair.private, algorithm)
        return GeneratedKey(
            algorithm = if (algorithm == "EC") "ecdsa" else "rsa",
            privatePem = privatePem,
            chainPems = listOf(pem(batch), pem(root)),
        )
    }

    private fun keyPair(algorithm: String, parameter: String): KeyPair {
        val generator = KeyPairGenerator.getInstance(algorithm)
        when (algorithm) {
            "EC" -> generator.initialize(ECGenParameterSpec(parameter))
            else -> generator.initialize(parameter.toInt())
        }
        return generator.generateKeyPair()
    }

    private fun selfSignedRoot(keyPair: KeyPair, signature: String): X509Certificate {
        val name = X500Name("CN=unknown")
        val (notBefore, notAfter) = validity()
        val builder = JcaX509v3CertificateBuilder(
            name,
            BigInteger.valueOf(System.currentTimeMillis()),
            notBefore,
            notAfter,
            name,
            keyPair.public,
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        builder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.keyCertSign or KeyUsage.digitalSignature),
        )
        val signer = JcaContentSignerBuilder(signature).build(keyPair.private)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }

    private fun signedBy(
        issuer: X509Certificate,
        issuerKey: KeyPair,
        subjectKey: KeyPair,
        signature: String,
    ): X509Certificate {
        val issuerName = X500Name(issuer.subjectX500Principal.name)
        val (notBefore, notAfter) = validity()
        val builder = JcaX509v3CertificateBuilder(
            issuerName,
            BigInteger.valueOf(System.nanoTime()),
            notBefore,
            notAfter,
            issuerName,
            subjectKey.public,
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(0))
        builder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.keyCertSign or KeyUsage.digitalSignature),
        )
        val signer = JcaContentSignerBuilder(signature).build(issuerKey.private)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }

    private fun validity(): Pair<Date, Date> {
        val now = System.currentTimeMillis()
        return Date(now - DAY_MS) to Date(now + VALIDITY_DAYS * DAY_MS)
    }

    private fun privateKeyPem(privateKey: PrivateKey, algorithm: String): String {
        val content = when (algorithm) {
            "EC" -> ECPrivateKey.getInstance(PrivateKeyInfo.getInstance(privateKey.encoded).parsePrivateKey()).encoded
            else -> RSAPrivateKey.getInstance(PrivateKeyInfo.getInstance(privateKey.encoded).parsePrivateKey()).encoded
        }
        val type = if (algorithm == "EC") "EC PRIVATE KEY" else "RSA PRIVATE KEY"
        val writer = StringWriter()
        JcaPEMWriter(writer).use { it.writeObject(PemObject(type, content)) }
        return writer.toString().trim()
    }

    private fun pem(certificate: X509Certificate): String {
        val writer = StringWriter()
        JcaPEMWriter(writer).use { it.writeObject(certificate) }
        return writer.toString().trim()
    }

    private fun buildXml(keys: List<GeneratedKey>): String = buildString {
        appendLine("<?xml version=\"1.0\"?>")
        appendLine("<AndroidAttestation>")
        appendLine("<NumberOfKeyboxes>1</NumberOfKeyboxes>")
        appendLine("<Keybox DeviceID=\"unknown\">")
        keys.forEach { key ->
            appendLine("<Key algorithm=\"${key.algorithm}\">")
            appendLine("<PrivateKey format=\"pem\">${key.privatePem}</PrivateKey>")
            appendLine("<CertificateChain>")
            appendLine("<NumberOfCertificates>${key.chainPems.size}</NumberOfCertificates>")
            key.chainPems.forEach { appendLine("<Certificate format=\"pem\">$it</Certificate>") }
            appendLine("</CertificateChain>")
            appendLine("</Key>")
        }
        appendLine("</Keybox>")
        appendLine("</AndroidAttestation>")
    }
}
