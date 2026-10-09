// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from Duck ToolBox (MIT), crates/duck-rkp/src/cbor.rs; modified for ZakoDaNiuMask.
package com.zakodaniumask.manager.data.rkp

import java.math.BigInteger

/** Minimal CBOR (RFC 8949) codec; values are Long, ByteArray, String, List<Any?>, Map<Any?, Any?> or null. */
object RkpCbor {
    fun encode(value: Any?): ByteArray {
        val out = ArrayList<Byte>()
        write(out, value)
        return out.toByteArray()
    }

    private fun write(out: MutableList<Byte>, value: Any?) {
        when (value) {
            null -> out.add(0xF6.toByte())
            is Long -> writeInteger(out, value)
            is Int -> writeInteger(out, value.toLong())
            is BigInteger -> if (value.signum() >= 0) {
                writeHead(out, 0, value)
            } else {
                writeHead(out, 1, value.negate().subtract(BigInteger.ONE))
            }
            is ByteArray -> {
                writeHead(out, 2, value.size.toLong())
                value.forEach { out.add(it) }
            }
            is String -> {
                val encoded = value.toByteArray(Charsets.UTF_8)
                writeHead(out, 3, encoded.size.toLong())
                encoded.forEach { out.add(it) }
            }
            is List<*> -> {
                writeHead(out, 4, value.size.toLong())
                value.forEach { write(out, it) }
            }
            is Map<*, *> -> {
                writeHead(out, 5, value.size.toLong())
                value.forEach { (key, item) ->
                    write(out, key)
                    write(out, item)
                }
            }
            else -> error("unsupported CBOR value: ${value::class}")
        }
    }

    private fun writeInteger(out: MutableList<Byte>, value: Long) {
        if (value >= 0) writeHead(out, 0, value) else writeHead(out, 1, -1 - value)
    }

    private fun writeHead(out: MutableList<Byte>, major: Int, length: Long) {
        val head = major shl 5
        when {
            length < 24 -> out.add((head or length.toInt()).toByte())
            length < 0x100 -> {
                out.add((head or 24).toByte()); out.add(length.toByte())
            }
            length < 0x10000 -> {
                out.add((head or 25).toByte())
                out.add((length shr 8).toByte()); out.add(length.toByte())
            }
            length < 0x100000000L -> {
                out.add((head or 26).toByte())
                out.add((length shr 24).toByte()); out.add((length shr 16).toByte())
                out.add((length shr 8).toByte()); out.add(length.toByte())
            }
            else -> {
                out.add((head or 27).toByte())
                for (shift in intArrayOf(56, 48, 40, 32, 24, 16, 8, 0)) out.add((length shr shift).toByte())
            }
        }
    }

    private fun writeHead(out: MutableList<Byte>, major: Int, value: BigInteger) {
        if (value.bitLength() < 64) {
            writeHead(out, major, value.toLong())
            return
        }
        out.add(((major shl 5) or 27).toByte())
        val bytes = value.toByteArray().takeLast(8)
        val padded = ByteArray(8)
        System.arraycopy(bytes.toByteArray(), 0, padded, 8 - bytes.size, bytes.size)
        padded.forEach { out.add(it) }
    }

    class Reader(private val data: ByteArray) {
        private var offset = 0

        fun read(): Any? {
            val initial = byte()
            val major = (initial.toInt() and 0xFF) shr 5
            return when (major) {
                0 -> readLength(initial)
                1 -> -1L - readLength(initial)
                2 -> readBytes(readLength(initial).toInt())
                3 -> String(readBytes(readLength(initial).toInt()), Charsets.UTF_8)
                4 -> List(readLength(initial).toInt()) { read() }
                5 -> {
                    val count = readLength(initial).toInt()
                    val map = LinkedHashMap<Any?, Any?>()
                    repeat(count) { map[read()] = read() }
                    map
                }
                6 -> read()
                else -> null
            }
        }

        private fun readLength(initial: Byte): Long {
            val info = initial.toInt() and 0x1F
            return when {
                info < 24 -> info.toLong()
                info == 24 -> readBytes(1)[0].toLong() and 0xFF
                info == 25 -> readBytes(2).let { ((it[0].toLong() and 0xFF) shl 8) or (it[1].toLong() and 0xFF) }
                info == 26 -> readBytes(4).let {
                    ((it[0].toLong() and 0xFF) shl 24) or
                        ((it[1].toLong() and 0xFF) shl 16) or
                        ((it[2].toLong() and 0xFF) shl 8) or
                        (it[3].toLong() and 0xFF)
                }
                info == 27 -> readBytes(8).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
                else -> error("indefinite lengths are not supported")
            }
        }

        private fun byte(): Byte {
            check(offset < data.size) { "unexpected end of CBOR" }
            return data[offset++]
        }

        private fun readBytes(count: Int): ByteArray {
            val slice = data.copyOfRange(offset, offset + count)
            offset += count
            return slice
        }
    }
}
