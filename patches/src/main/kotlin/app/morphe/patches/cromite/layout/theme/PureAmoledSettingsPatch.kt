package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val SETTINGS_VERSION = "153.0.8010.37"
private const val SETTINGS_BLACK = "#FF000000"
private const val SETTINGS_WHITE = "#FFFFFFFF"

private val pureAmoledSettingsResourcesPatch = resourcePatch(
    description = "Scoped native Settings resources for the selectable Cromite AMOLED palette.",
) {
    compatibleWith("org.cromite.cromite"(SETTINGS_VERSION))
    val browserTheme by browserThemeOption()

    execute {
        val valueDirectories = get("res").listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("values") }
            .orEmpty()

        fun isNight(directory: java.io.File) = directory.name.contains("night")
        fun surface(directory: java.io.File) = when (browserTheme) {
            "black" -> SETTINGS_BLACK
            "white" -> SETTINGS_WHITE
            else -> if (isNight(directory)) SETTINGS_BLACK else SETTINGS_WHITE
        }
        fun foreground(directory: java.io.File) =
            if (surface(directory) == SETTINGS_BLACK) SETTINGS_WHITE else SETTINGS_BLACK

        val settingsSurfaceNames = setOf(
            "settings_background_color",
            "settings_background_color_dark",
            "settings_bg_color",
            "settings_container_background_color",
            "settings_card_background_color",
            "settings_card_bg_color",
        )
        val settingsForegroundNames = setOf(
            "settings_text_color",
            "settings_primary_text_color",
            "settings_icon_color",
        )

        var hits = 0
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
                                if (!isColorNode) continue
                                when (name) {
                                    in settingsSurfaceNames -> {
                                        element.textContent = surface(directory)
                                        hits++
                                    }
                                    in settingsForegroundNames -> {
                                        element.textContent = foreground(directory)
                                        hits++
                                    }
                                }
                            }
                        }
                    }
                }
        }

        if (hits == 0) {
            throw IllegalStateException(
                "AMOLED Settings resource verification failed for Cromite $SETTINGS_VERSION: no whitelisted Settings resources were found."
            )
        }
    }
}

/**
 * Settings is intentionally a separate selectable patch.
 *
 * Cromite 153 keeps SemanticColorUtils.getSettingsBackgroundColor(Context)
 * as pac.l. Patching that semantic boundary plus only Settings-named resources
 * avoids coupling SettingsActivity to the main-browser/NTP resource scopes.
 */
@Suppress("unused")
val pureAmoledSettingsPatch = bytecodePatch(
    name = "AMOLED - Settings",
    description = "Applies the selected Device default / Pure Black / Pure White palette to native Cromite Settings independently from the main browser UI and NTP.",
) {
    compatibleWith("org.cromite.cromite"(SETTINGS_VERSION))
    val browserTheme by browserThemeOption()
    dependsOn(pureAmoledSettingsResourcesPatch)

    execute {
        settingsBackgroundColorFingerprint.method.addInstructions(
            0,
            surfaceSmali(browserTheme ?: "device"),
        )
    }
}
