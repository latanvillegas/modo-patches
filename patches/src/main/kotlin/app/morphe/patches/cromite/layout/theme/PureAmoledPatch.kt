package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.Opcode
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

/**
 * Locates the Chromium AppMenu popup structurally instead of relying on its R8 name.
 *
 * AppMenu configures the same PopupWindow with a background drawable and then installs
 * the inflated root View as its content. Requiring both framework calls makes this far
 * more specific than matching every PopupWindow.setContentView() call in Cromite.
 *
 * instructionMatches[1] is the setContentView(View) invoke. Its second invoke register
 * is the exact root content View that will be forced to AMOLED black in the next step.
 */
private val appMenuPopupContentFingerprint = Fingerprint(
    returnType = "V",
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            smali = "Landroid/widget/PopupWindow;->setBackgroundDrawable(Landroid/graphics/drawable/Drawable;)V",
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            smali = "Landroid/widget/PopupWindow;->setContentView(Landroid/view/View;)V",
        ),
    ),
)

private val pureAmoledResourcesPatch = resourcePatch(
    description = "Internal resource component for Cromite Pure AMOLED."
) {
    compatibleWith("org.cromite.cromite"(CROMITE_VERSION))

    execute {
        val explicitColorFiles = setOf(
            "bottom_sheet_bg_color.xml",
            "context_menu_header_circle_bg_color.xml",
            "dialog_bg_color.xml",
            "distilled_page_pref_background_color.xml",
            "fake_search_box_base_color.xml",
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

        val appMenuDrawableFiles = setOf(
            "popup_bg.xml",
            "popup_bg_8dp.xml",
            "popup_bg_24dp.xml",
        )

        val drawableDirectories = listOf(
            "res/drawable",
            "res/drawable-night",
            "res/drawable-v21",
            "res/drawable-night-v21",
            "res/drawable-v23",
            "res/drawable-night-v23",
            "res/drawable-v31",
            "res/drawable-night-v31",
            "res/drawable-v35",
            "res/drawable-night-v35",
        )

        val colorDirectories = listOf(
            "res/color",
            "res/color-night",
            "res/color-night-v8",
            "res/color-v31",
            "res/color-night-v31",
            "res/color-v35",
            "res/color-night-v35",
        )

        val gm3DarkSurfaces = setOf(
            "gm3_baseline_surface_dark",
            "gm3_baseline_surface_bright_dark",
            "gm3_baseline_surface_dim_dark",
            "gm3_baseline_surface_container_lowest_dark",
            "gm3_baseline_surface_container_low_dark",
            "gm3_baseline_surface_container_dark",
            "gm3_baseline_surface_container_high_dark",
            "gm3_baseline_surface_container_highest_dark",
        )

        val directSurfaceColorNames = setOf(
            "default_bg_color_dark",
            "settings_background_color_dark",
            "primary_background_color_dark",
            "color_surface_dark",
            "color_surface_bright_dark",
            "color_surface_dim_dark",
            "color_surface_container_lowest_dark",
            "color_surface_container_low_dark",
            "color_surface_container_dark",
            "color_surface_container_high_dark",
            "color_surface_container_highest_dark",
            "menu_bg_color",
            "menu_bg_color_baseline",
            "menu_bg_color_dark_baseline",
            "mtrl_popupmenu_overlay_color",
            "mtrl_filled_background_color",
        )

        val surfaceThemeAttrs = setOf(
            "android:colorBackground",
            "colorBackground",
            "colorSurface",
            "colorSurfaceBright",
            "colorSurfaceDim",
            "colorSurfaceContainerLowest",
            "colorSurfaceContainerLow",
            "colorSurfaceContainer",
            "colorSurfaceContainerHigh",
            "colorSurfaceContainerHighest",
        )

        var valuesPatched = 0
        var popupDrawableHits = 0
        val verifiedHits = mutableListOf<String>()

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

        explicitColorFiles.forEach { fileName ->
            colorDirectories.forEach { directory ->
                runCatching {
                    document("$directory/$fileName").use { document ->
                        val before = valuesPatched
                        blacken(document.documentElement, true)
                        if (valuesPatched > before) verifiedHits += "$directory/$fileName"
                    }
                }
            }
        }

        appMenuDrawableFiles.forEach { fileName ->
            drawableDirectories.forEach { directory ->
                runCatching {
                    document("$directory/$fileName").use { document ->
                        val before = valuesPatched
                        blacken(document.documentElement, false)
                        if (valuesPatched > before) {
                            popupDrawableHits++
                            verifiedHits += "$directory/$fileName"
                        }
                    }
                }
            }
        }

        val valueQualifiers = listOf(
            "res/values",
            "res/values-night",
            "res/values-v31",
            "res/values-night-v31",
            "res/values-v35",
            "res/values-night-v35",
        )

        valueQualifiers.forEach { directory ->
            runCatching {
                document("$directory/colors.xml").use { document ->
                    val colors = document.getElementsByTagName("color")
                    var localHits = 0
                    for (index in 0 until colors.length) {
                        val color = colors.item(index) as? Element ?: continue
                        val name = color.getAttribute("name")
                        if (name in gm3DarkSurfaces || name in directSurfaceColorNames) {
                            color.textContent = AMOLED_BLACK
                            localHits++
                            valuesPatched++
                        }
                    }
                    if (localHits > 0) verifiedHits += "$directory/colors.xml:$localHits"
                }
            }

            listOf("styles.xml", "themes.xml").forEach { fileName ->
                runCatching {
                    document("$directory/$fileName").use { document ->
                        val items = document.getElementsByTagName("item")
                        var localHits = 0
                        for (index in 0 until items.length) {
                            val item = items.item(index) as? Element ?: continue
                            if (item.getAttribute("name") in surfaceThemeAttrs) {
                                item.textContent = AMOLED_BLACK
                                localHits++
                                valuesPatched++
                            }
                        }
                        if (localHits > 0) verifiedHits += "$directory/$fileName:$localHits"
                    }
                }
            }
        }

        if (verifiedHits.isEmpty() || valuesPatched == 0) {
            throw IllegalStateException(
                "Pure AMOLED resource verification failed for Cromite $CROMITE_VERSION: " +
                    "none of the expected Material/Chromium surface resources were found."
            )
        }
    }
}

@Suppress("unused")
val pureAmoledPatch = bytecodePatch(
    name = "Pure AMOLED theme",
    description = "Forces Cromite 153 dark UI surfaces and AppMenu popup background to true black (#000000). Use with dark mode.",
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

        dynamicColorsApplyFingerprint.method.addInstructions(0, "return-void")

        // Resolve the structural AppMenu matcher during patching. This deliberately
        // performs no bytecode mutation yet; if the two-call signature is absent,
        // patching fails instead of silently targeting an unrelated PopupWindow.
        appMenuPopupContentFingerprint.method
    }
}
