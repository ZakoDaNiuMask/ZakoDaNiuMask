// SPDX-License-Identifier: GPL-3.0-or-later
//
// Minimal ELF reader that extracts the module name from a `.ko`'s `.modinfo` section. Used so
// that rmmod can address an imported module by its real kernel module name.

package com.zakodaniumask.manager.data.userko

object ElfModinfo {
    private const val EI_CLASS = 4
    private const val EI_DATA = 5
    private const val SHT_PROGBITS = 1

    /** Returns the `name=` value from the `.modinfo` section, or null when unavailable. */
    fun moduleName(bytes: ByteArray): String? {
        if (bytes.size < 0x40) return null
        if (bytes[0] != 0x7F.toByte() || bytes[1] != 'E'.code.toByte() ||
            bytes[2] != 'L'.code.toByte() || bytes[3] != 'F'.code.toByte()
        ) {
            return null
        }
        val is64 = bytes[EI_CLASS].toInt() == 2
        val littleEndian = bytes[EI_DATA].toInt() == 1
        if (bytes[EI_DATA].toInt() != 1 && bytes[EI_DATA].toInt() != 2) return null

        val shoff = if (is64) u64(bytes, 0x28, littleEndian) else u32(bytes, 0x20, littleEndian)
        val shentsize = if (is64) u16(bytes, 0x3A, littleEndian) else u16(bytes, 0x2E, littleEndian)
        val shnum = if (is64) u16(bytes, 0x3C, littleEndian) else u16(bytes, 0x30, littleEndian)
        val shstrndx = if (is64) u16(bytes, 0x3E, littleEndian) else u16(bytes, 0x32, littleEndian)
        if (shoff <= 0 || shentsize <= 0 || shnum <= 0) return null

        val shstrHeader = sectionHeader(bytes, shoff, shentsize, shstrndx, is64, littleEndian)
            ?: return null
        val shstr = readRange(bytes, shstrHeader.offset, shstrHeader.size) ?: return null

        for (index in 0 until shnum) {
            val header = sectionHeader(bytes, shoff, shentsize, index, is64, littleEndian) ?: continue
            if (header.type != SHT_PROGBITS) continue
            val name = cStringAt(shstr, header.nameOffset) ?: continue
            if (name != ".modinfo") continue
            val data = readRange(bytes, header.offset, header.size) ?: return null
            return parseModinfoName(data)
        }
        return null
    }

    private fun parseModinfoName(data: ByteArray): String? {
        var start = 0
        while (start < data.size) {
            var end = start
            while (end < data.size && data[end] != 0.toByte()) end++
            val token = data.decodeToString(start, end)
            if (token.startsWith("name=")) {
                return token.substringAfter("name=").ifBlank { null }
            }
            start = end + 1
        }
        return null
    }

    private data class SectionHeader(
        val nameOffset: Int,
        val type: Int,
        val offset: Long,
        val size: Long,
    )

    private fun sectionHeader(
        bytes: ByteArray,
        shoff: Long,
        shentsize: Int,
        index: Int,
        is64: Boolean,
        littleEndian: Boolean,
    ): SectionHeader? {
        val base = shoff + index.toLong() * shentsize
        if (base < 0 || base + shentsize > bytes.size) return null
        val offset = base.toInt()
        val nameOffset = u32(bytes, offset, littleEndian).toInt()
        val type = u32(bytes, offset + 4, littleEndian).toInt()
        return if (is64) {
            SectionHeader(
                nameOffset = nameOffset,
                type = type,
                offset = u64(bytes, offset + 0x18, littleEndian),
                size = u64(bytes, offset + 0x20, littleEndian),
            )
        } else {
            SectionHeader(
                nameOffset = nameOffset,
                type = type,
                offset = u32(bytes, offset + 0x10, littleEndian),
                size = u32(bytes, offset + 0x14, littleEndian),
            )
        }
    }

    private fun readRange(bytes: ByteArray, offset: Long, size: Long): ByteArray? {
        if (offset < 0 || size < 0) return null
        val end = offset + size
        if (end > bytes.size) return null
        return bytes.copyOfRange(offset.toInt(), end.toInt())
    }

    private fun cStringAt(bytes: ByteArray, offset: Int): String? {
        if (offset < 0 || offset >= bytes.size) return null
        var end = offset
        while (end < bytes.size && bytes[end] != 0.toByte()) end++
        return bytes.decodeToString(offset, end)
    }

    private fun u16(bytes: ByteArray, offset: Int, littleEndian: Boolean): Int {
        if (offset + 2 > bytes.size) return 0
        val b0 = bytes[offset].toInt() and 0xFF
        val b1 = bytes[offset + 1].toInt() and 0xFF
        return if (littleEndian) b0 or (b1 shl 8) else (b0 shl 8) or b1
    }

    private fun u32(bytes: ByteArray, offset: Int, littleEndian: Boolean): Long {
        if (offset + 4 > bytes.size) return 0
        var value = 0L
        if (littleEndian) {
            for (i in 3 downTo 0) value = (value shl 8) or (bytes[offset + i].toLong() and 0xFF)
        } else {
            for (i in 0 until 4) value = (value shl 8) or (bytes[offset + i].toLong() and 0xFF)
        }
        return value
    }

    private fun u64(bytes: ByteArray, offset: Int, littleEndian: Boolean): Long {
        if (offset + 8 > bytes.size) return 0
        var value = 0L
        if (littleEndian) {
            for (i in 7 downTo 0) value = (value shl 8) or (bytes[offset + i].toLong() and 0xFF)
        } else {
            for (i in 0 until 8) value = (value shl 8) or (bytes[offset + i].toLong() and 0xFF)
        }
        return value
    }
}
