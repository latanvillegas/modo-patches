package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Forces Cromite's compiled Android night-mode surfaces to true OLED black.
 */
@Suppress("unused")
val pureAmoledPatch = resourcePatch(
    name = "Pure AMOLED theme",
    description = "Use true black (#000000) for Cromite dark interface surfaces.",
) {
    compatibleWith("org.cromite.cromite"("153.0.8010.37"))

    execute {
        // Morphe's resource filesystem does not guarantee that File.list() is available for APK
        // virtual directories. Use the concrete compiled resources verified in Cromite 153 instead.
        val candidateFiles = listOf(
            "bottom_sheet_bg_color.xml",
            "context_menu_header_circle_bg_color.xml",
            "dialog_bg_color.xml",
            "distilled_page_pref_background_color.xml",
            "fake_search_box_base_color.xml",
            "home_surface_background_color.xml",
            "home_surface_ui_background_color.xml",
            "hub_search_box_bg_color.xml",
            "interactable_chip_bg_color.xml",
        )

        var filesPatched = 0
        var valuesPatched = 0

        fun blacken(element: Element) {
            val attributes = element.attributes
            for (i in 0 until attributes.length) {
                val attribute = attributes.item(i)
                if (attribute.nodeName.endsWith(":color") || attribute.nodeName == "color") {
                    attribute.nodeValue = "#FF000000"
                    valuesPatched++
                }
            }

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

        candidateFiles.forEach { fileName ->
            runCatching {
                document("res/color-night-v8/$fileName").use { document ->
                    val before = valuesPatched
                    blacken(document.documentElement)
                    if (valuesPatched > before) filesPatched++
                }
            }
        }

        if (filesPatched == 0 || valuesPatched == 0) {
            throw IllegalStateException(
                "No verified Cromite 153 night-mode color resources could be patched."
            )
        }
    }
}
