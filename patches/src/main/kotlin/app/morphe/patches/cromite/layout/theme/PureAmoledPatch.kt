package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Forces Cromite's compiled Android night-mode surfaces to true OLED black.
 *
 * The release APK stores many Chromium colors as individual compiled XML resources rather than
 * source values XML. Patch a wider set of known UI surfaces, including settings, home, dialogs,
 * menus, tab/hub surfaces and omnibox/search surfaces. Foreground/accent resources are excluded.
 */
@Suppress("unused")
val pureAmoledPatch = resourcePatch(
    name = "Pure AMOLED theme",
    description = "Use true black (#000000) for Cromite dark interface surfaces.",
) {
    compatibleWith("org.cromite.cromite"("153.0.8010.37"))

    execute {
        val exactFiles = setOf(
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
        val surfaceTokens = listOf(
            "background", "surface", "toolbar", "omnibox", "search_box", "searchbox",
            "tab", "hub", "menu", "dialog", "sheet", "popup", "card", "settings",
            "incognito", "ntp", "new_tab", "dropdown"
        )
        val excludedTokens = listOf(
            "text", "icon", "foreground", "accent", "link", "tint", "stroke", "divider",
            "outline", "disabled", "selected", "checked", "error", "warning"
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

        // Cromite 153 packages night resources in this compiled resource directory. Enumerating the
        // resource directory lets us cover Chromium surfaces that were missed by the first version.
        val directory = get("res/color-night-v8")
        directory.list().forEach { fileName ->
            val lower = fileName.lowercase()
            val isSurface = fileName in exactFiles || surfaceTokens.any(lower::contains)
            val isExcluded = excludedTokens.any(lower::contains)
            if (!lower.endsWith(".xml") || !isSurface || isExcluded) return@forEach

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
                "Cromite night-mode surfaces were not patchable in this APK version."
            )
        }
    }
}
