package app.morphe.patches.filemanager.music

import java.io.File
import java.io.RandomAccessFile

/**
 * Host-side reference implementation for RIFF/WAVE ID3v2.3 APIC extraction.
 *
 * IMPORTANT: This is NOT injected into the target APK. The bytecode patch
 * must add equivalent target-side code before this can be used at runtime.
 */
internal object WavApicExtractor {
    private const val MAX_TAG_BYTES = 16 * 1024 * 1024
    private const val MAX_IMAGE_BYTES = 10 * 1024 * 1024

    fun extract(file: File): ByteArray? = runCatching {
        RandomAccessFile(file, "r").use { input ->
            val length = input.length()
            if (length < 12 || readAscii(input, 4) != "RIFF") return@use null
            input.skipBytes(4)
            if (readAscii(input, 4) != "WAVE") return@use null
            var offset = 12L
            while (offset <= length - 8) {
                input.seek(offset)
                val type = readAscii(input, 4)
                val size = readU32LE(input)
                val dataStart = offset + 8
                if (size > length - dataStart) return@use null
                if (type.equals("id3 ", ignoreCase = true) || type == "ID3\u0000") {
                    if (size < 10 || size > MAX_TAG_BYTES) return@use null
                    val bytes = ByteArray(size.toInt())
                    input.readFully(bytes)
                    val image = extractFromId3(bytes)
                    if (image != null) return@use image
                }
                val next = dataStart + size + (size and 1L)
                if (next <= offset || next > length) return@use null
                offset = next
            }
            null
        }
    }.getOrNull()

    private fun extractFromId3(tag: ByteArray): ByteArray? {
        if (tag.size < 10 || String(tag, 0, 3, Charsets.ISO_8859_1) != "ID3") return null
        if (tag[3].toInt() != 3) return null // ID3v2.3 only
        val flags = tag[5].toInt() and 0xff
        if ((flags and 0x80) != 0) return null // Unsynchronisation needs dedicated decoding
        val declared = syncsafe(tag, 6) ?: return null
        // Reject truncated ID3 tags instead of parsing incomplete frames.
        if (declared > tag.size - 10L) return null
        val end = (10L + declared).toInt()
        var cursor = 10
        if ((flags and 0x40) != 0) {
            if (cursor + 4 > end) return null
            val ext = u32be(tag, cursor)
            if (ext > end - cursor - 4) return null
            cursor += 4 + ext.toInt()
        }
        while (cursor + 10 <= end) {
            if (tag[cursor] == 0.toByte()) break
            val frameId = String(tag, cursor, 4, Charsets.ISO_8859_1)
            val size = u32be(tag, cursor + 4)
            if (size <= 0 || size > end - cursor - 10) break
            val flagsHi = tag[cursor + 8].toInt() and 0xff
            val flagsLo = tag[cursor + 9].toInt() and 0xff
            if (frameId == "APIC" && flagsHi == 0 && flagsLo == 0) {
                val image = parseApic(tag, cursor + 10, size.toInt())
                if (image != null) return image
            }
            cursor += 10 + size.toInt()
        }
        return null
    }

    private fun parseApic(data: ByteArray, start: Int, size: Int): ByteArray? {
        val end = start + size
        if (start >= end) return null
        val encoding = data[start].toInt() and 0xff
        var p = start + 1
        while (p < end && data[p] != 0.toByte()) p++ // MIME
        if (++p >= end) return null
        p++ // picture type
        if (encoding == 0 || encoding == 3) {
            while (p < end && data[p] != 0.toByte()) p++
            p++
        } else if (encoding == 1 || encoding == 2) {
            while (p + 1 < end && !(data[p] == 0.toByte() && data[p + 1] == 0.toByte())) p += 2
            p += 2
        } else return null
        val bytes = end - p
        if (bytes <= 0 || bytes > MAX_IMAGE_BYTES) return null
        val jpeg = bytes >= 3 && (data[p].toInt() and 0xff) == 0xff &&
            (data[p + 1].toInt() and 0xff) == 0xd8 &&
            (data[p + 2].toInt() and 0xff) == 0xff
        val png = bytes >= 8 && data.copyOfRange(p, p + 8).contentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        )
        return if (jpeg || png) data.copyOfRange(p, end) else null
    }

    private fun readAscii(file: RandomAccessFile, count: Int): String {
        val data = ByteArray(count)
        file.readFully(data)
        return String(data, Charsets.ISO_8859_1)
    }

    private fun readU32LE(file: RandomAccessFile): Long =
        (file.readUnsignedByte().toLong()) or
        (file.readUnsignedByte().toLong() shl 8) or
        (file.readUnsignedByte().toLong() shl 16) or
        (file.readUnsignedByte().toLong() shl 24)

    private fun u32be(data: ByteArray, offset: Int): Long =
        ((data[offset].toLong() and 255) shl 24) or
        ((data[offset + 1].toLong() and 255) shl 16) or
        ((data[offset + 2].toLong() and 255) shl 8) or
        (data[offset + 3].toLong() and 255)

    private fun syncsafe(data: ByteArray, offset: Int): Long? {
        if ((offset..offset + 3).any { (data[it].toInt() and 0x80) != 0 }) return null
        return ((data[offset].toLong() and 127) shl 21) or
            ((data[offset + 1].toLong() and 127) shl 14) or
            ((data[offset + 2].toLong() and 127) shl 7) or
            (data[offset + 3].toLong() and 127)
    }
}
