package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val NTP_CHROME_COLORS_PROPERTIES = "Lm19;"
private const val NTP_VERSION = "153.0.8010.37"
private const val NTP_BLACK = "#FF000000"
private const val NTP_WHITE = "#FFFFFFFF"

/**
 * NTP resources are deliberately whitelisted instead of sharing the broad
 * native-browser resource scan. This keeps Settings, toolbar and WebContents
 * outside the NTP patch boundary.
 */
private val pureAmoledNtpResourcesPatch = resourcePatch(
    description = "Scoped native New Tab Page resources for the selectable Cromite AMOLED palette.",
) {
    compatibleWith("org.cromite.cromite"(NTP_VERSION))
    val browserTheme by browserThemeOption()

    execute {
        val colorDirectories = get("res").listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("color") }
            .orEmpty()
        val valueDirectories = get("res").listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("values") }
            .orEmpty()

        fun isNight(directory: java.io.File) = directory.name.contains("night")
        fun surface(directory: java.io.File) = when (browserTheme) {
            "black" -> NTP_BLACK
            "white" -> NTP_WHITE
            else -> if (isNight(directory)) NTP_BLACK else NTP_WHITE
        }

        val exactColorFiles = setOf(
            "fake_search_box_base_color.xml",
            "home_surface_background_color.xml",
            "home_surface_ui_background_color.xml",
            "tile_view_icon_background_color.xml",
        )
        val exactValueNames = setOf(
            "fake_search_box_base_color",
            "home_surface_background_color",
            "home_surface_ui_background_color",
            "tile_view_icon_background_color",
        )

        var hits = 0

        colorDirectories.forEach { directory ->
            exactColorFiles.forEach { fileName ->
                val file = java.io.File(directory, fileName)
                if (!file.isFile) return@forEach
                document(file.absolutePath).use { document ->
                    val nodes = document.getElementsByTagName("item")
                    for (index in 0 until nodes.length) {
                        val item = nodes.item(index) as? Element ?: continue
                        item.setAttribute("android:color", surface(directory))
                        hits++
                    }
                }
            }
        }

        valueDirectories.forEach { directory ->
            java.io.File(directory.absolutePath).listFiles()
                ?.filter { it.isFile && it.extension == "xml" }
                .orEmpty()
                .forEach { file ->
                    runCatching {
                        document(file.absolutePath).use { document ->
                            val nodes = document.getElementsByTagName("*")
                            for (index in 0 until nodes.length) {
                                val element = nodes.item(index) as? Element ?: continue
                                val name = element.getAttribute("name")
                                val type = element.getAttribute("type")
                                val isColorNode = element.tagName == "color" ||
                                    (element.tagName == "item" && type == "color")
                                if (isColorNode && name in exactValueNames) {
                                    element.textContent = surface(directory)
                                    hits++
                                }
                            }
                        }
                    }
                }
        }

        if (hits == 0) {
            throw IllegalStateException(
                "AMOLED NTP resource verification failed for Cromite $NTP_VERSION: no whitelisted NTP resources were found."
            )
        }
    }
}

/**
 * New Tab Page is intentionally isolated from the main browser UI patch.
 *
 * Cromite 153 / R8 maps NtpChromeColorsProperties to m19. Its method c is
 * getBackgroundColorFromNtpBackgroundData(...), where the NTP surface roles
 * are resolved after SemanticColorUtils has been inlined.
 */
private val ntpBackgroundColorFingerprint = Fingerprint(
    definingClass = NTP_CHROME_COLORS_PROPERTIES,
    name = "c",
    returnType = "I",
)

@Suppress("unused")
val pureAmoledNtpPatch = bytecodePatch(
    name = "AMOLED - New Tab Page",
    description = "Applies Device default / Pure Black / Pure White to native Cromite New Tab Page surfaces independently from toolbar, Settings, system bars, and WebContents.",
) {
    compatibleWith("org.cromite.cromite"(NTP_VERSION))
    val browserTheme by browserThemeOption()
    dependsOn(pureAmoledNtpResourcesPatch)

    execute {
        ntpBackgroundColorFingerprint.method.addInstructions(
            0,
            surfaceSmali(browserTheme ?: "device"),
        )
    }
}
