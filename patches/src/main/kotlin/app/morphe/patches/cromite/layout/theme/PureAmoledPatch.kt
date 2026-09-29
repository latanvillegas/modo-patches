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

// Cromite 153 mapping:
// com.google.android.material.color.DynamicColors -> qb4
// applyToActivityIfAvailable(Activity, DynamicColorsOptions) -> a
// Android 12+ can replace Material surface attributes at runtime. That happens
// after XML resources are loaded, so it can restore gray surfaces even when the
// night resources were patched. Disable only this dynamic-palette application;
// Chromium's normal dark theme and accent resources remain intact.
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

        var filesPatched = 0
        var valuesPatched = 0

        fun blacken(element: Element) {
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
                    blacken(child as Element)
                }
            }
            if (!hasElementChildren && element.textContent.trim().startsWith("#")) {
                element.textContent = AMOLED_BLACK
                valuesPatched++
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

        runCatching {
            document("res/values-night/colors.xml").use { document ->
                val colors = document.getElementsByTagName("color")
                for (index in 0 until colors.length) {
                    val color = colors.item(index) as? Element ?: continue
                    val name = color.getAttribute("name").lowercase()
                    val isBackground = name.contains("background") || name.contains("_bg") ||
                        name.contains("surface") || name.startsWith("gm3_baseline_surface") ||
                        name.startsWith("default_bg_color_dark_elev_")
                    if (isBackground) {
                        color.textContent = AMOLED_BLACK
                        valuesPatched++
                    }
                }
            }
        }

        val surfaceAttrs = setOf(
            "android:colorBackground",
            "colorBackground",
            "colorSurface",
            "colorSurfaceBright",
            "colorSurfaceDim",
            "colorSurfaceContainerLow",
            "colorSurfaceContainer",
            "colorSurfaceContainerHigh",
            "colorSurfaceContainerHighest",
        )

        // In a values-night table every style is already dark-mode-specific, so
        // patch the surface roles regardless of the style's name.
        runCatching {
            document("res/values-night/styles.xml").use { document ->
                val styles = document.getElementsByTagName("style")
                for (index in 0 until styles.length) {
                    val style = styles.item(index) as? Element ?: continue
                    val items = style.getElementsByTagName("item")
                    for (itemIndex in 0 until items.length) {
                        val item = items.item(itemIndex) as? Element ?: continue
                        if (item.getAttribute("name") in surfaceAttrs) {
                            item.textContent = AMOLED_BLACK
                            valuesPatched++
                        }
                    }
                }
            }
        }

        // Some dark themes are declared in the default values table.
        runCatching {
            document("res/values/styles.xml").use { document ->
                val styles = document.getElementsByTagName("style")
                for (index in 0 until styles.length) {
                    val style = styles.item(index) as? Element ?: continue
                    val styleName = style.getAttribute("name")
                    if (!styleName.contains("dark", ignoreCase = true) &&
                        !styleName.contains("night", ignoreCase = true)) continue

                    val items = style.getElementsByTagName("item")
                    for (itemIndex in 0 until items.length) {
                        val item = items.item(itemIndex) as? Element ?: continue
                        if (item.getAttribute("name") in surfaceAttrs) {
                            item.textContent = AMOLED_BLACK
                            valuesPatched++
                        }
                    }
                }
            }
        }

        if (filesPatched == 0) {
            throw IllegalStateException("No verified Cromite 153 night resources could be patched.")
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

        // Prevent Android 12+ Material DynamicColors from overwriting our AMOLED
        // surface roles with wallpaper-derived gray tones at runtime.
        dynamicColorsApplyFingerprint.method.addInstructions(
            0,
            "return-void"
        )
    }
}
