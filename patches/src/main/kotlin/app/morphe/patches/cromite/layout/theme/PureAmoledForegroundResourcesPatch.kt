package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val FOREGROUND_VERSION = "153.0.8010.37"
private const val FOREGROUND_BLACK = "#FF000000"
private const val FOREGROUND_WHITE = "#FFFFFFFF"

@Suppress("unused")
val pureAmoledForegroundResourcesPatch = resourcePatch(
    name = "AMOLED - Semantic foreground resources",
    description = "Applies Pure Black / Pure White to verified native semantic text, icon and on-surface foreground resources.",
) {
    compatibleWith("org.cromite.cromite"(FOREGROUND_VERSION))
    val browserTheme by browserThemeOption()

    execute {
        val selectedTheme = browserTheme ?: "device"
        if (selectedTheme == "device") return@execute
        val foreground = when (selectedTheme) {
            "black" -> FOREGROUND_WHITE
            "white" -> FOREGROUND_BLACK
            else -> return@execute
        }

        val exactNames = setOf(
            "default_text_color", "default_icon_color",
            "color_on_background", "color_on_surface", "color_on_surface_variant",
            "gm3_baseline_on_surface", "gm3_baseline_on_surface_variant",
        )

        fun isSemanticForeground(name: String): Boolean {
            val normalized = name.lowercase()
            if (normalized in exactNames) return true
            return "on_surface" in normalized ||
                "onsurface" in normalized ||
                "on_background" in normalized ||
                "onbackground" in normalized
        }

        val directories = get("res").listFiles()?.filter {
            it.isDirectory && it.name.startsWith("values")
        }.orEmpty()
        var hits = 0

        directories.forEach { directory ->
            directory.listFiles()?.filter { it.isFile && it.extension == "xml" }.orEmpty().forEach { file ->
                runCatching {
                    document(file.absolutePath).use { document ->
                        val all = document.getElementsByTagName("*")
                        for (index in 0 until all.length) {
                            val element = all.item(index) as? Element ?: continue
                            val name = element.getAttribute("name")
                            val type = element.getAttribute("type")
                            if (!isSemanticForeground(name)) continue
                            if (element.tagName == "color" ||
                                (element.tagName == "item" && type == "color")) {
                                element.textContent = foreground
                                hits++
                            }
                        }
                    }
                }
            }
        }

        if (hits == 0) {
            throw IllegalStateException(
                "AMOLED semantic foreground verification failed for Cromite $FOREGROUND_VERSION: no verified foreground resources were found."
            )
        }
    }
}
