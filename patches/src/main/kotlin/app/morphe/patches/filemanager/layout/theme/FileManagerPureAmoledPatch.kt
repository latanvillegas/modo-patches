package app.morphe.patches.filemanager.layout.theme

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch
import java.nio.file.Files

private data class ColorTarget(
    val id: Long,
    val night: Boolean,
    val original: Long,
    val replacement: Long,
)

private val colorTargets = listOf(
    ColorTarget(0x7f06034f, false, 0xfffafafa, 0xffffffff),
    ColorTarget(0x7f0603a8, false, 0xff212121, 0xff000000),
    ColorTarget(0x7f060454, true, 0xff1f1f1f, 0xff000000),
    ColorTarget(0x7f060455, false, 0xff181818, 0xff000000),
    ColorTarget(0x7f060455, true, 0xff080808, 0xff000000),
    ColorTarget(0x7f060087, false, 0xff0e1513, 0xff000000),
    ColorTarget(0x7f060088, false, 0xff222222, 0xff000000),
    ColorTarget(0x7f060089, false, 0xff2c2c2c, 0xff000000),
    ColorTarget(0x7f06008a, false, 0xff373737, 0xff000000),
    ColorTarget(0x7f06008b, false, 0xff1d1d1d, 0xff000000),
    ColorTarget(0x7f06008c, false, 0xff101010, 0xff000000),
    ColorTarget(0x7f06008d, false, 0xe8ffffff, 0xffffffff),
    ColorTarget(0x7f06034e, false, 0xff121212, 0xff000000),
    ColorTarget(0x7f060056, true, 0xff1d1d1d, 0xff000000),
    ColorTarget(0x7f060068, true, 0xff222222, 0xff000000),
    ColorTarget(0x7f060069, true, 0xff2c2c2c, 0xff000000),
    ColorTarget(0x7f06006a, true, 0xff373737, 0xff000000),
    ColorTarget(0x7f06006b, true, 0xff1d1d1d, 0xff000000),
    ColorTarget(0x7f06006c, true, 0xff101010, 0xff000000),
    ColorTarget(0x7f060350, true, 0xd8ffffff, 0xffffffff),
    ColorTarget(0x7f060433, true, 0xff222222, 0xff000000),
    ColorTarget(0x7f060434, true, 0xff303030, 0xff000000),
    ColorTarget(0x7f06043b, true, 0xff282828, 0xff000000),
    ColorTarget(0x7f060470, true, 0xff181818, 0xff000000),
    ColorTarget(0x7f0604a4, true, 0xff2f2f2f, 0xff000000),
)

private fun patchFileManagerArsc(original: ByteArray): ByteArray {
    val bytes = original.copyOf()

    fun requireRange(offset: Int, length: Int) {
        if (offset < 0 || length < 0 ||
            offset.toLong() + length > bytes.size.toLong()
        ) {
            throw PatchException("AMOLED: ARSC fuera de límites")
        }
    }

    fun u8(offset: Int): Int {
        requireRange(offset, 1)
        return bytes[offset].toInt() and 0xff
    }

    fun u16(offset: Int): Int =
        u8(offset) or (u8(offset + 1) shl 8)

    fun u32(offset: Int): Long =
        (u16(offset).toLong() or
            (u16(offset + 2).toLong() shl 16))

    fun write32(offset: Int, value: Long) {
        requireRange(offset, 4)
        for (i in 0..3) {
            bytes[offset + i] =
                ((value ushr (i * 8)) and 0xff).toByte()
        }
    }

    fun checkedInt(value: Long): Int {
        if (value < 0 || value > Int.MAX_VALUE) {
            throw PatchException("AMOLED: tamaño ARSC inválido")
        }
        return value.toInt()
    }

    data class Chunk(
        val start: Int,
        val type: Int,
        val header: Int,
        val end: Int,
    )

    fun chunks(start: Int, end: Int): List<Chunk> {
        val result = mutableListOf<Chunk>()
        var cursor = start

        while (cursor < end) {
            requireRange(cursor, 8)
            val type = u16(cursor)
            val header = u16(cursor + 2)
            val size = checkedInt(u32(cursor + 4))

            if (header < 8 || size < header ||
                cursor.toLong() + size > end.toLong()
            ) {
                throw PatchException("AMOLED: chunk ARSC inválido")
            }

            result += Chunk(
                cursor,
                type,
                header,
                cursor + size,
            )
            cursor += size
        }

        if (cursor != end) {
            throw PatchException("AMOLED: tabla ARSC incompleta")
        }

        return result
    }

    if (u16(0) != 0x0002 ||
        u32(4) != bytes.size.toLong() ||
        u32(8) != 1L
    ) {
        throw PatchException("AMOLED: cabecera ARSC incompatible")
    }

    val targets = colorTargets.associateBy { it.id to it.night }

    if (targets.size != 25) {
        throw PatchException("AMOLED: objetivos duplicados")
    }

    val found = mutableMapOf<Pair<Long, Boolean>, Pair<Int, Long>>()

    val tableHeader = u16(2)
    if (tableHeader < 12) {
        throw PatchException("AMOLED: cabecera de tabla inválida")
    }

    for (pkg in chunks(tableHeader, bytes.size)) {
        if (pkg.type != 0x0200) continue

        if (pkg.header < 284) {
            throw PatchException("AMOLED: cabecera de paquete inválida")
        }

        val packageId = u32(pkg.start + 8)
        val typeIdOffset =
            if (pkg.header >= 288) u32(pkg.start + 284)
            else 0L

        for (type in chunks(pkg.start + pkg.header, pkg.end)) {
            if (type.type != 0x0201) continue

            val typeId = u8(type.start + 8)
            val flags = u8(type.start + 9)
            val count = checkedInt(u32(type.start + 12))
            val entriesStart = checkedInt(u32(type.start + 16))
            val configSize = checkedInt(u32(type.start + 20))

            if (entriesStart < type.header ||
                type.start.toLong() + entriesStart > type.end
            ) {
                throw PatchException("AMOLED: inicio de entradas inválido")
            }

            if (configSize < 4 ||
                20L + configSize > type.header
            ) {
                throw PatchException("AMOLED: configuración inválida")
            }

            // No interpretamos formatos sparse ni offset16.
            if ((flags and 0x03) != 0) continue

            if (type.header.toLong() + count.toLong() * 4 >
                type.end.toLong() - type.start
            ) {
                throw PatchException("AMOLED: índices inválidos")
            }

            val uiMode =
                if (configSize > 29) u8(type.start + 20 + 29)
                else 0

            // Excluir variantes de interfaz como television.
            // Solo se procesan recursos normales y nocturnos.
            if ((uiMode and 0x0f) != 0x00) continue

            val nightMode = uiMode and 0x30
            if (nightMode != 0x00 && nightMode != 0x20) continue

            val night = nightMode == 0x20

            for (index in 0 until count) {
                val relative = u32(
                    type.start + type.header + index * 4
                )

                if (relative == 0xffffffffL) continue

                val entryLong =
                    type.start.toLong() + entriesStart + relative

                if (entryLong > Int.MAX_VALUE) {
                    throw PatchException("AMOLED: entrada inválida")
                }

                val entry = entryLong.toInt()
                val dataStart = type.start.toLong() + entriesStart
                if (entryLong < dataStart || entryLong + 8 > type.end) {
                    throw PatchException("AMOLED: entrada fuera de límites")
                }

                val entrySize = u16(entry)
                val entryFlags = u16(entry + 2)

                if (entrySize < 8 ||
                    entryLong + entrySize > type.end
                ) {
                    throw PatchException("AMOLED: tamaño de entrada inválido")
                }

                // Las entradas complejas no contienen un Res_value simple.
                if ((entryFlags and 1) != 0) continue

                if (entryLong + entrySize + 8 > type.end) {
                    throw PatchException("AMOLED: Res_value fuera de límites")
                }

                val effectiveType = typeId.toLong() + typeIdOffset
                if (effectiveType > 0xff) {
                    throw PatchException("AMOLED: ID de tipo inválido")
                }

                val id =
                    (packageId shl 24) or
                    (effectiveType shl 16) or
                    index.toLong()

                val key = id to night
                val target = targets[key] ?: continue

                if (found.containsKey(key)) {
                    throw PatchException("AMOLED: recurso duplicado $id")
                }

                if (configSize <= 29 ||
                    (uiMode and 0x30) !=
                    (if (target.night) 0x20 else 0x00)
                ) {
                    throw PatchException("AMOLED: configuración inesperada")
                }

                val value = entry + entrySize

                if (entrySize < 8 ||
                    value.toLong() + 8 > type.end ||
                    u16(value) != 8
                ) {
                    throw PatchException("AMOLED: Res_value inválido")
                }

                val valueType = u8(value + 3)
                if (valueType != 0x1c && valueType != 0x1d) {
                    throw PatchException("AMOLED: tipo de color inválido")
                }

                val offset = value + 4
                if (u32(offset) != target.original) {
                    throw PatchException(
                        "AMOLED: valor original inesperado para $id"
                    )
                }

                found[key] = offset to target.replacement
            }
        }
    }

    if (found.size != targets.size) {
        throw PatchException(
            "AMOLED: encontrados ${found.size} de ${targets.size} recursos"
        )
    }

    // Todas las comprobaciones se completan antes de escribir.
    for ((offset, replacement) in found.values) {
        write32(offset, replacement)
    }

    return bytes
}

@Suppress("unused")
val fileManagerPureAmoledPatch = rawResourcePatch(
    name = "File Manager Plus - Pure AMOLED",
    description = "Aplica fondos AMOLED y colores de texto de alto contraste en File Manager Plus 3.8.2.",
    default = false,
) {
    compatibleWith("com.alphainventor.filemanager"("3.8.2"))

    execute {
        val file = get("resources.arsc")
        val original = Files.readAllBytes(file.toPath())
        val modified = patchFileManagerArsc(original)

        Files.write(file.toPath(), modified)
    }
}
