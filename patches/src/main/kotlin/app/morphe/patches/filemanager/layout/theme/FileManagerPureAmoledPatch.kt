package app.morphe.patches.filemanager.layout.theme

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch
import java.nio.file.Files

private data class ColorTarget(
    val id: Long,
    val night: Boolean,
    val original: Long,
    val replacement: Long,
    val valueType: Int = 0x1c,
)

private val colorTargets = listOf(
    // El modo claro usa blanco; la variante night de primary ya se fuerza a negro.
    ColorTarget(0x7f060454, false, 0x7f0603a8, 0x7f06034f, 0x01),
    // En Android 23+ s4()/E3() lee este recurso cuando no usa actionbar_color.
    ColorTarget(0x7f0604a7, false, 0xff000000, 0xffffffff),
    // Popup de la barra clara: eliminar el gris #f0f0f0.
    ColorTarget(0x7f060024, false, 0xfff0f0f0, 0xffffffff),
    // Redirigir solo los fondos de los menus oscuros a primary (#000000).
    // 0x01 es TYPE_REFERENCE; el resto de objetivos conserva TYPE_INT_COLOR_ARGB8.
    ColorTarget(0x7f060022, false, 0x7f060036, 0x7f060454, 0x01),
    ColorTarget(0x7f060026, false, 0x7f060036, 0x7f060454, 0x01),
    ColorTarget(0x7f0604bb, false, 0xffd92525, 0xffff0000),
    ColorTarget(0x7f060030, false, 0xff00b2ac, 0xff00ffff),
    ColorTarget(0x7f060487, false, 0xff4aaed5, 0xff00c0ff),
    ColorTarget(0x7f0600cb, false, 0xff2987d9, 0xff0080ff),
    ColorTarget(0x7f0600ec, false, 0xff943fe5, 0xffaa00ff),
    ColorTarget(0x7f06048a, false, 0xffebc849, 0xffffd500),
    ColorTarget(0x7f060489, false, 0xfff2d500, 0xffffff00),
    ColorTarget(0x7f06047f, false, 0xffa4ca39, 0xffbfff00),
    ColorTarget(0x7f060491, false, 0xff00897b, 0xff00ffdd),
    ColorTarget(0x7f060491, true, 0xff319799, 0xff00ffdd),
    ColorTarget(0x7f060486, false, 0xff1976d2, 0xff0080ff),
    ColorTarget(0x7f060486, true, 0xff296199, 0xff0080ff),
    ColorTarget(0x7f06048c, false, 0xff6a1b9a, 0xffaa00ff),
    ColorTarget(0x7f06048c, true, 0xff7a5999, 0xffaa00ff),
    ColorTarget(0x7f060497, false, 0xffda251c, 0xffff0000),
    ColorTarget(0x7f06049d, false, 0xff207245, 0xff00ff66),
    ColorTarget(0x7f060498, false, 0xffd24625, 0xffff5500),
    ColorTarget(0x7f06003c, false, 0xff00adef, 0xff00c0ff),
    ColorTarget(0x7f06004d, false, 0xff00b8d4, 0xff00ddff),
    ColorTarget(0x7f060061, false, 0xff008578, 0xff00ccbb),
    ColorTarget(0x7f060081, false, 0xff66b9ad, 0xff00ddcc),
    ColorTarget(0x7f060019, false, 0xff4db6ac, 0xff00ddcc),
    ColorTarget(0x7f06001a, false, 0xff409c93, 0xff00ccbb),
    ColorTarget(0x7f06001a, true, 0xff009483, 0xff00ccbb),
    ColorTarget(0x7f0600ea, true, 0xff1a1a1a, 0xff000000),
    ColorTarget(0x7f06044e, false, 0xff222222, 0xff000000),
    ColorTarget(0x7f06044f, false, 0xff222222, 0xff000000),
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

    if (targets.size != 57) {
        throw PatchException("AMOLED: objetivos duplicados")
    }

    val found = mutableMapOf<Pair<Long, Boolean>, Pair<Int, Long>>()
    var popupStyleParentOffset: Int? = null

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

                val effectiveType = typeId.toLong() + typeIdOffset
                if (effectiveType > 0xff) {
                    throw PatchException("AMOLED: ID de tipo inválido")
                }

                val id =
                    (packageId shl 24) or
                    (effectiveType shl 16) or
                    index.toLong()

                val entrySize = u16(entry)
                val entryFlags = u16(entry + 2)

                if (entrySize < 8 ||
                    entryLong + entrySize > type.end
                ) {
                    throw PatchException("AMOLED: tamaño de entrada inválido")
                }

                // El popup oscuro es el que usa ToolbarStyle.Default.
                // Su padre fijo Dark produce texto blanco invisible en modo claro.
                // AppBaseTheme hereda de MaterialComponents.DayNight y mantiene
                // los atributos locales de fondo y colorSurface del popup.
                if (id == 0x7f1403a1L && !night) {
                    if ((entryFlags and 1) == 0 || entrySize < 16 ||
                        entryLong + entrySize + 2L * 12L > type.end
                    ) {
                        throw PatchException("AMOLED: estilo popup incompatible")
                    }
                    val parentOffset = entry + 8
                    val mapCount = u32(entry + 12)
                    if (u32(parentOffset) != 0x7f140385L || mapCount != 2L) {
                        throw PatchException("AMOLED: padre o atributos popup inesperados")
                    }
                    val maps = entry + entrySize
                    if (u32(maps) != 0x01010031L ||
                        u32(maps + 8) != 0x7f060022L ||
                        u32(maps + 12) != 0x7f04014aL ||
                        u32(maps + 20) != 0x7f060022L ||
                        u8(maps + 7) != 0x01 ||
                        u8(maps + 19) != 0x01
                    ) {
                        throw PatchException("AMOLED: mapa popup inesperado")
                    }
                    popupStyleParentOffset = parentOffset
                }

                // Las entradas complejas se verifican arriba y no son colores simples.
                if ((entryFlags and 1) != 0) continue

                if (entryLong + entrySize + 8 > type.end) {
                    throw PatchException("AMOLED: Res_value fuera de límites")
                }

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
                if (valueType != target.valueType &&
                    !(target.valueType == 0x1c && valueType == 0x1d)
                ) {
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

    val popupOffset = popupStyleParentOffset
        ?: throw PatchException("AMOLED: estilo ToobarPopupTheme.Dark no encontrado")

    // Todas las comprobaciones se completan antes de escribir.
    write32(popupOffset, 0x7f140559L)
    for ((offset, replacement) in found.values) {
        write32(offset, replacement)
    }

    return bytes
}

/**
 * Morphe rawResourcePatch expone este selector como XML textual (cabecera "<?"),
 * aunque dentro del APK original sea AXML binario.
 * Conserva la sintaxis y usa texto primario opaco con variantes day/night.
 */
private fun patchToolbarForegroundText(original: ByteArray): ByteArray {
    val text = original.toString(Charsets.UTF_8)
    if (!text.trimStart().startsWith("<?xml") &&
        !text.trimStart().startsWith("<selector")
    ) {
        throw PatchException("AMOLED: selector toolbar no es XML textual")
    }
    if (!text.contains("<selector") || !text.contains("</selector>")) {
        throw PatchException("AMOLED: estructura selector inesperada")
    }
    // Morphe entrega XML textual y normaliza #ffffffff a #ffffff.
    // Evitar regex: el XML real de File Manager Plus 3.8.2 ya fue
    // identificado y sus dos atributos se verifican antes de escribir.
    val disabled = """android:color="#66ffffff" """
        .trimEnd()
    val enabled = """android:color="#ffffff" """
        .trimEnd()
    if (text.split(disabled).size != 2 ||
        text.split(enabled).size != 2 ||
        text.indexOf(disabled) >= text.indexOf(enabled)
    ) {
        throw PatchException(
            "AMOLED: selector toolbar distinto del original " +
                "(disabled=${text.split(disabled).size - 1}, " +
                "enabled=${text.split(enabled).size - 1}, " +
                "bytes=${original.size})"
        )
    }
    return text.replace(disabled, """android:color="@color/main_primary_text" """.trimEnd())
        .replace(enabled, """android:color="@color/main_primary_text" """.trimEnd())
        .toByteArray(Charsets.UTF_8)
}

@Suppress("unused")
val fileManagerPureAmoledPatch = rawResourcePatch(
    name = "File Manager Plus - Pure AMOLED",
    description = "Separa superficies blancas en modo claro y negras AMOLED en modo oscuro; conserva la selección de tema en File Manager Plus 3.8.2.",
    default = false,
) {
    compatibleWith("com.alphainventor.filemanager"("3.8.2"))

    execute {
        val file = get("resources.arsc")
        val original = Files.readAllBytes(file.toPath())
        val modified = patchFileManagerArsc(original)

        val toolbarFile = get("res/color/toolbar_dark_foreground.xml")
        val toolbarOriginal = Files.readAllBytes(toolbarFile.toPath())
        val toolbarModified = patchToolbarForegroundText(toolbarOriginal)

        Files.write(file.toPath(), modified)
        Files.write(toolbarFile.toPath(), toolbarModified)
    }
}
