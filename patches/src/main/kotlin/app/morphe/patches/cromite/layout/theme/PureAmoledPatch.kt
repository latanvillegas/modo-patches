package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Forces Cromite's compiled Android night-mode surface resources to true OLED black.
 *
 * Cromite's release APK does not expose res/values-night/colors.xml. aapt2 compiles the
 * relevant colors into individual XML resources under res/color-night-v8, so patch those
 * files directly instead of looking for source-only values XML files.
 */
@Suppress("unused")
val pureAmoledPatch = resourcePatch(
    name = "Pure AMOLED theme",
    description = "Use true black (#000000) for Cromite dark interface surfaces.",
) {
    compatibleWith("org.cromite.cromite")

    execute {
        val nightSurfaceFiles = listOf(
            "res/color-night-v8/bottom_sheet_bg_color.xml",
            "res/color-night-v8/context_menu_header_circle_bg_color.xml",
            "res/color-night-v8/dialog_bg_color.xml",
            "res/color-night-v8/distilled_page_pref_background_color.xml",
            "res/color-night-v8/fake_search_box_base_color.xml",
            "res/color-night-v8/home_surface_background_color.xml",
            "res/color-night-v8/home_surface_ui_background_color.xml",
            "res/color-night-v8/hub_search_box_bg_color.xml",
            "res/color-night-v8/interactable_chip_bg_color.xml",
        )

        var filesPatched = 0
        var valuesPatched = 0

        fun blacken(element: Element) {
            // Compiled color XML commonly stores the color in android:color on <item>.
            val attributes = element.attributes
            for (i in 0 until attributes.length) {
                val attribute = attributes.item(i)
                if (attribute.nodeName.endsWith(":color") || attribute.nodeName == "color") {
                    attribute.nodeValue = "#FF000000"
                    valuesPatched++
                }
            }

            // Also support decoded simple color nodes if the patcher exposes them as text.
            val children = element.childNodes
            var hasElementChildren = false
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child.nodeType == Node.ELEMENT_NODE) {
                    hasElementChildren = true
                    blacken(child as Element)
                }
            }
            if (!hasElementChildren) {
                val text = element.textContent.trim()
                if (text.startsWith("#")) {
                    element.textContent = "#FF000000"
                    valuesPatched++
                }
            }
        }

        nightSurfaceFiles.forEach { path ->
            runCatching {
                document(path).use { document ->
                    blacken(document.documentElement)
                    filesPatched++
                }
            }
        }

        if (filesPatched == 0) {
            throw IllegalStateException(
                "Cromite night-mode color resources were not found; this APK version is unsupported."
            )
        }
        if (valuesPatched == 0) {
            throw IllegalStateException(
                "Cromite night-mode resources were found but contained no patchable color values."
            )
        }
    }
}
