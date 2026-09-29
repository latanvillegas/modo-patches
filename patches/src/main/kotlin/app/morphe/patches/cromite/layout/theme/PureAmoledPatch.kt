package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Forces Cromite's Android dark UI surfaces to true OLED black.
 *
 * Chromium/Material uses semantic surface attributes for Settings, cards, menus, tabs, search,
 * toolbars and other containers. Patch those attributes in night themes in addition to the
 * compiled color resources already verified in Cromite 153.
 */
@Suppress("unused")
val pureAmoledPatch = resourcePatch(
    name = "Pure AMOLED theme",
    description = "Use true black (#000000) for Cromite dark interface surfaces.",
) {
    compatibleWith("org.cromite.cromite"("153.0.8010.37"))

    execute {
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
        var themeItemsPatched = 0

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

        // Material 3 tonal surface roles are used by cards, dialogs, menus, search, tabs and app
        // bars. Chromium also relies on colorSurface for Settings/toolbar backgrounds.
        val surfaceAttributes = setOf(
            "colorBackground",
            "android:colorBackground",
            "windowBackground",
            "android:windowBackground",
            "colorSurface",
            "colorSurfaceDim",
            "colorSurfaceBright",
            "colorSurfaceVariant",
            "colorSurfaceContainer",
            "colorSurfaceContainerLow",
            "colorSurfaceContainerLowest",
            "colorSurfaceContainerHigh",
            "colorSurfaceContainerHighest",
        )

        // Patch only night-qualified styles. If Cromite stores the resolved dark styles in a
        // different qualifier this block safely does nothing and the verified color resources above
        // still apply.
        listOf("res/values-night/styles.xml", "res/values-night-v8/styles.xml").forEach { path ->
            runCatching {
                document(path).use { document ->
                    val styles = document.getElementsByTagName("style")
                    for (styleIndex in 0 until styles.length) {
                        val style = styles.item(styleIndex) as? Element ?: continue
                        val items = style.getElementsByTagName("item")
                        for (itemIndex in 0 until items.length) {
                            val item = items.item(itemIndex) as? Element ?: continue
                            if (item.getAttribute("name") in surfaceAttributes) {
                                item.textContent = "#FF000000"
                                themeItemsPatched++
                            }
                        }
                    }
                }
            }
        }

        if (filesPatched == 0 && themeItemsPatched == 0) {
            throw IllegalStateException(
                "No verified Cromite 153 AMOLED surfaces or Material surface tokens could be patched."
            )
        }
    }
}
