package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import org.w3c.dom.Node

private const val CROMITE_VERSION = "153.0.8010.37"
private const val SEMANTIC_COLOR_UTILS = "Lpac;"
private const val CHROME_COLORS = "Lq72;"
private const val DYNAMIC_COLORS = "Lqb4;"
private const val DYNAMIC_COLORS_OPTIONS = "Ltb4;"
private const val CONTEXT = "Landroid/content/Context;"
private const val ACTIVITY = "Landroid/app/Activity;"
private const val AMOLED_BLACK = "#FF000000"

private fun semanticSurfaceFingerprint(name: String) = Fingerprint(
    definingClass = SEMANTIC_COLOR_UTILS,
    name = name,
    returnType = "I",
    parameters = listOf(CONTEXT),
)

private val colorSurfaceFingerprint = semanticSurfaceFingerprint("c")
private val colorSurfaceBrightFingerprint = semanticSurfaceFingerprint("d")
private val colorSurfaceContainerFingerprint = semanticSurfaceFingerprint("e")
private val colorSurfaceContainerHighestFingerprint = semanticSurfaceFingerprint("f")
private val colorSurfaceContainerLowFingerprint = semanticSurfaceFingerprint("g")
private val defaultBgColorFingerprint = semanticSurfaceFingerprint("h")
private val settingsBackgroundColorFingerprint = semanticSurfaceFingerprint("l")

private val primaryBackgroundColorFingerprint = Fingerprint(
    definingClass = CHROME_COLORS,
    name = "b",
    returnType = "I",
    parameters = listOf(CONTEXT, "Z"),
)

private val dynamicColorsApplyFingerprint = Fingerprint(
    definingClass = DYNAMIC_COLORS,
    name = "a",
    returnType = "V",
    parameters = listOf(ACTIVITY, DYNAMIC_COLORS_OPTIONS),
)

private val pureAmoledResourcesPatch = resourcePatch(
    description = "Internal resource component for Cromite Pure AMOLED."
) {
    compatibleWith("org.cromite.cromite"(CROMITE_VERSION))

    execute {
        // These files were verified directly in the Cromite 153 arm64 APK.
        val nightFiles = listOf(
            "bottom_sheet_bg_color.xml",
            "context_menu_header_circle_bg_color.xml",
            "dialog_bg_color.xml",
            "distilled_page_pref_background_color.xml",
            "fake_search_box_base_color.xml",
            "home_surface_background_color.xml",
            "home_surface_ui_background_color.xml",
            "hub_search_box_bg_color.xml",
            "interactable_chip_bg_color.xml",
            "message_bg_color.xml",
            "omnibox_popover_suggestion_bg_color.xml",
            "omnibox_suggestion_dropdown_bg.xml",
            "search_suggestion_bg_color.xml",
            "single_tab_favicon_bg_color.xml",
            "tab_group_dialog_bg_color.xml",
            "tab_hover_card_bg_color.xml",
            "tab_strip_ntb_bg_tint_list.xml",
            "tile_view_icon_background_color.xml",
            "toast_color.xml",
            "toolbar_text_box_bg_color.xml",
            "widget_bg.xml",
            "widget_button_bg.xml",
            "widget_searchbox_bg.xml",
            "xr_toolbar_bg.xml",
        )

        // Settings/menu Material components are compiled as color selectors in
        // res/color (not values-night XML). Their leaves commonly reference
        // ?attr/colorSurface*; force those selector outputs to AMOLED black.
        val materialBackgroundFiles = listOf(
            "bottom_sheet_bg_color.xml",
            "context_menu_header_circle_bg_color.xml",
            "dialog_bg_color.xml",
            "distilled_page_pref_background_color.xml",
            "home_surface_background_color.xml",
            "home_surface_ui_background_color.xml",
            "hub_search_box_bg_color.xml",
            "interactable_chip_bg_color.xml",
            "item_chooser_row_background.xml",
            "m3_list_item_background_color_selector.xml",
            "m3_segmented_list_item_background_color_selector.xml",
            "m3_textfield_filled_background_color.xml",
            "mtrl_filled_background_color.xml",
            "mtrl_popupmenu_overlay_color.xml",
            "message_bg_color.xml",
            "omnibox_popover_suggestion_bg_color.xml",
            "omnibox_suggestion_dropdown_bg.xml",
            "search_suggestion_bg_color.xml",
            "tab_group_dialog_bg_color.xml",
            "tab_hover_card_bg_color.xml",
            "toolbar_text_box_bg_color.xml",
        )

        var filesPatched = 0
        var valuesPatched = 0

        fun blacken(element: Element, forceLeaf: Boolean) {
            val attributes = element.attributes
            for (i in 0 until attributes.length) {
                val attribute = attributes.item(i)
                if (attribute.nodeName.endsWith(":color") || attribute.nodeName == "color") {
                    attribute.nodeValue = AMOLED_BLACK
                    valuesPatched++
                }
            }

            val children = element.childNodes
            var hasElementChildren = false
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child.nodeType == Node.ELEMENT_NODE) {
                    hasElementChildren = true
                    blacken(child as Element, forceLeaf)
                }
            }

            if (!hasElementChildren) {
                val value = element.textContent.trim()
                if (forceLeaf || value.startsWith("#")) {
                    element.textContent = AMOLED_BLACK
                    valuesPatched++
                }
            }
        }

        nightFiles.forEach { fileName ->
            runCatching {
                document("res/color-night-v8/$fileName").use { document ->
                    val before = valuesPatched
                    blacken(document.documentElement, false)
                    if (valuesPatched > before) filesPatched++
                }
            }
        }

        materialBackgroundFiles.forEach { fileName ->
            runCatching {
                document("res/color/$fileName").use { document ->
                    val before = valuesPatched
                    blacken(document.documentElement, true)
                    if (valuesPatched > before) filesPatched++
                }
            }
        }

        if (filesPatched == 0) {
            throw IllegalStateException("No verified Cromite 153 AMOLED resources could be patched.")
        }
    }
}

@Suppress("unused")
val pureAmoledPatch = bytecodePatch(
    name = "Pure AMOLED theme",
    description = "Forces Cromite 153 dark UI surfaces to true black (#000000). Use with dark mode.",
) {
    compatibleWith("org.cromite.cromite"(CROMITE_VERSION))
    dependsOn(pureAmoledResourcesPatch)

    execute {
        // Exact methods present in the Cromite 153 R8 mapping. ChromeColors in
        // this build contains only getDefaultThemeColor/getPrimaryBackgroundColor;
        // getSurfaceColor(Context,int) is not present in the mapping, so do not
        // invent a q72 fingerprint for a method that does not exist.
        val targets = listOf(
            colorSurfaceFingerprint,
            colorSurfaceBrightFingerprint,
            colorSurfaceContainerFingerprint,
            colorSurfaceContainerHighestFingerprint,
            colorSurfaceContainerLowFingerprint,
            defaultBgColorFingerprint,
            settingsBackgroundColorFingerprint,
            primaryBackgroundColorFingerprint,
        )

        targets.forEach { fingerprint ->
            fingerprint.method.addInstructions(
                0,
                """
                    const v0, -0x1000000
                    return v0
                """
            )
        }

        dynamicColorsApplyFingerprint.method.addInstructions(0, "return-void")
    }
}
