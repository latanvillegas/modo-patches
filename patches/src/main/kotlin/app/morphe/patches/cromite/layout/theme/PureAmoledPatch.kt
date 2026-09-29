package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.patch.resourcePatch
import app.morphe.util.forEachChildElement
import org.w3c.dom.Element

/**
 * Forces Android theme background/surface color resources used by Cromite to true OLED black.
 *
 * Targeted initially at Cromite 153.0.8010.37. This deliberately limits replacement to
 * dark/background/surface resources so foreground text, icons, link colors and accent colors
 * keep their original contrast.
 */
@Suppress("unused")
val pureAmoledPatch = resourcePatch(
    name = "Pure AMOLED theme",
    description = "Use true black (#000000) for Cromite dark interface surfaces.",
) {
    compatibleWith("org.cromite.cromite")

    execute {
        val candidateFiles = listOf(
            "res/values/colors.xml",
            "res/values-night/colors.xml",
        )

        var changed = 0

        candidateFiles.forEach { path ->
            runCatching {
                document(path).use { document ->
                    val resources = document.documentElement
                    resources.forEachChildElement { node ->
                        if (node !is Element || node.tagName != "color") return@forEachChildElement

                        val name = node.getAttribute("name").lowercase()
                        val value = node.textContent.trim().lowercase()

                        val isDarkNamedResource = name.contains("dark") ||
                            name.contains("night") ||
                            name.contains("black")

                        val isBackgroundSurface = name.contains("background") ||
                            name.contains("surface") ||
                            name.contains("toolbar") ||
                            name.contains("omnibox") ||
                            name.contains("tab") ||
                            name.contains("menu") ||
                            name.contains("dialog") ||
                            name.contains("sheet")

                        // Avoid replacing transparent colors and foreground/accent colors.
                        val opaqueDarkLiteral = value.matches(
                            Regex("#(?:ff)?(?:0[0-9a-f]|1[0-9a-f]|2[0-9a-f]){3}")
                        )

                        if ((isDarkNamedResource || isBackgroundSurface) && opaqueDarkLiteral) {
                            node.textContent = "#FF000000"
                            changed++
                        }
                    }
                }
            }
        }

        if (changed == 0) {
            throw IllegalStateException(
                "No Cromite dark surface colors were found; this APK layout is not supported yet."
            )
        }
    }
}
