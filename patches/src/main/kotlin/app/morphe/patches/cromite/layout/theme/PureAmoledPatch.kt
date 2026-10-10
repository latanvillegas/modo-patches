package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.VariableRegisterInstruction
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
private const val PURE_BLACK = "#FF000000"
private const val PURE_WHITE = "#FFFFFFFF"

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

/**
 * Recovered from the original patches-1.50.0.mpp, PureAmoledPatchKt.class.
 * The Settings search drawable is tinted after SemanticColorUtils resolves its color.
 */
private val settingsSearchSurfaceFingerprint = Fingerprint(
    definingClass = "Ldic;",
    filters = listOf(
        methodCall("Landroid/content/Context;->getDrawable(I)Landroid/graphics/drawable/Drawable;", Opcode.INVOKE_VIRTUAL),
        methodCall("Lis7;->c(Landroid/content/Context;ILjava/lang/String;)I", Opcode.INVOKE_STATIC),
        methodCall("Landroid/graphics/drawable/Drawable;->setTint(I)V", Opcode.INVOKE_VIRTUAL),
        methodCall("Landroid/view/View;->setBackground(Landroid/graphics/drawable/Drawable;)V", Opcode.INVOKE_VIRTUAL),
        methodCall("Landroid/view/View;->setBackground(Landroid/graphics/drawable/Drawable;)V", Opcode.INVOKE_VIRTUAL),
    ),
)

private val dynamicColorsApplyFingerprint = Fingerprint(
    definingClass = DYNAMIC_COLORS,
    name = "a",
    returnType = "V",
    parameters = listOf(ACTIVITY, DYNAMIC_COLORS_OPTIONS),
)

private val pureAmoledResourcesPatch = resourcePatch(
    description = "Internal resources for system-following pure black / pure white Cromite UI."
) {
    compatibleWith("org.cromite.cromite"(CROMITE_VERSION))

    execute {
        val explicitColorFiles = setOf(
            "bottom_sheet_bg_color.xml", "context_menu_header_circle_bg_color.xml", "dialog_bg_color.xml",
            "distilled_page_pref_background_color.xml", "fake_search_box_base_color.xml",
            "home_surface_background_color.xml", "home_surface_ui_background_color.xml",
            "hub_search_box_bg_color.xml", "interactable_chip_bg_color.xml", "item_chooser_row_background.xml",
            "m3_list_item_background_color_selector.xml", "m3_segmented_list_item_background_color_selector.xml",
            "m3_textfield_filled_background_color.xml", "mtrl_filled_background_color.xml",
            "mtrl_popupmenu_overlay_color.xml", "message_bg_color.xml", "omnibox_popover_suggestion_bg_color.xml",
            "omnibox_suggestion_dropdown_bg.xml", "search_suggestion_bg_color.xml", "single_tab_favicon_bg_color.xml",
            "tab_group_dialog_bg_color.xml", "tab_hover_card_bg_color.xml", "tab_strip_ntb_bg_tint_list.xml",
            "tile_view_icon_background_color.xml", "toast_color.xml", "toolbar_text_box_bg_color.xml",
            "widget_bg.xml", "widget_button_bg.xml", "widget_searchbox_bg.xml", "xr_toolbar_bg.xml",
            "menu_item_bg_color.xml", "menu_item_bg_color_baseline.xml", "menu_item_bg_color_dark_baseline.xml",
            "menu_bg_color.xml", "menu_bg_color_baseline.xml", "menu_bg_color_dark_baseline.xml",
            "list_menu_background_color.xml", "list_menu_background_tint.xml",
            // Desktop-Android/Cromite toolbar and tab-strip surfaces. Missing files are intentionally ignored.
            "toolbar_background_primary.xml", "toolbar_background_primary_incognito.xml",
            "toolbar_background_color.xml", "top_toolbar_background_color.xml",
            "tab_strip_background_color.xml", "tab_strip_bg_color.xml", "tab_strip_background.xml",
            "primary_background_color.xml", "default_bg_color.xml",
        )

        val surfaceDrawableFiles = setOf(
            "popup_bg.xml", "popup_bg_8dp.xml", "popup_bg_24dp.xml",
            "popup_bg_shape_16dp.xml", "popup_bg_shape_24dp.xml", "popup_bg_shape_24dp_baseline.xml",
            "list_menu_background.xml", "menu_bg_tinted.xml",
            "toolbar_background.xml", "tab_strip_background.xml",
        )

        val drawableDirectories = listOf(
            "res/drawable", "res/drawable-night", "res/drawable-v21", "res/drawable-night-v21",
            "res/drawable-v23", "res/drawable-night-v23", "res/drawable-v31", "res/drawable-night-v31",
            "res/drawable-v35", "res/drawable-night-v35",
        )
        val colorDirectories = listOf(
            "res/color", "res/color-night", "res/color-night-v8", "res/color-v31",
            "res/color-night-v31", "res/color-v35", "res/color-night-v35",
        )
        val valueDirectories = listOf(
            "res/values", "res/values-night", "res/values-v31", "res/values-night-v31",
            "res/values-v35", "res/values-night-v35",
        )

        fun isNight(directory: String) = directory.substringAfter("res/").contains("night")
        fun pureSurface(directory: String) = if (isNight(directory)) PURE_BLACK else PURE_WHITE

        val surfaceColorNames = setOf(
            "default_bg_color", "default_bg_color_dark",
            "settings_background_color", "settings_background_color_dark",
            "primary_background_color", "primary_background_color_dark",
            "toolbar_background_primary", "toolbar_background_primary_incognito", "toolbar_background_color",
            "top_toolbar_background_color", "tab_strip_background_color", "tab_strip_bg_color",
            "color_surface", "color_surface_dark", "color_surface_bright", "color_surface_bright_dark",
            "color_surface_dim", "color_surface_dim_dark", "color_surface_container_lowest",
            "color_surface_container_lowest_dark", "color_surface_container_low", "color_surface_container_low_dark",
            "color_surface_container", "color_surface_container_dark", "color_surface_container_high",
            "color_surface_container_high_dark", "color_surface_container_highest", "color_surface_container_highest_dark",
            "gm3_baseline_surface_dark", "gm3_baseline_surface_bright_dark", "gm3_baseline_surface_dim_dark",
            "gm3_baseline_surface_container_lowest_dark", "gm3_baseline_surface_container_low_dark",
            "gm3_baseline_surface_container_dark", "gm3_baseline_surface_container_high_dark",
            "gm3_baseline_surface_container_highest_dark",
            "menu_bg_color", "menu_bg_color_baseline", "menu_bg_color_dark_baseline",
            "menu_item_bg_color", "menu_item_bg_color_baseline", "menu_item_bg_color_dark_baseline",
            "list_menu_background_color", "list_menu_background_tint",
            "mtrl_popupmenu_overlay_color", "mtrl_filled_background_color",
            // Material 3 preference rows and grouped settings cards.
            "m3_list_item_background_color", "m3_segmented_list_item_background_color",
            "m3_textfield_filled_background_color",
        )

        val surfaceThemeAttrs = setOf(
            "android:colorBackground", "android:windowBackground", "android:navigationBarColor", "android:statusBarColor",
            "colorBackground", "colorSurface", "colorSurfaceBright", "colorSurfaceDim", "colorSurfaceVariant",
            "colorSurfaceContainerLowest", "colorSurfaceContainerLow", "colorSurfaceContainer",
            "colorSurfaceContainerHigh", "colorSurfaceContainerHighest",
        )

        var valuesPatched = 0
        val verifiedHits = mutableListOf<String>()

        fun recolor(element: Element, color: String, forceLeaf: Boolean) {
            val attributes = element.attributes
            for (i in 0 until attributes.length) {
                val attribute = attributes.item(i)
                if (attribute.nodeName.endsWith(":color") || attribute.nodeName == "color" ||
                    attribute.nodeName.endsWith(":tint") || attribute.nodeName == "tint") {
                    attribute.nodeValue = color
                    valuesPatched++
                }
            }
            val children = element.childNodes
            var hasElementChildren = false
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child.nodeType == Node.ELEMENT_NODE) {
                    hasElementChildren = true
                    recolor(child as Element, color, forceLeaf)
                }
            }
            if (!hasElementChildren) {
                val value = element.textContent.trim()
                if (forceLeaf || value.startsWith("#")) {
                    element.textContent = color
                    valuesPatched++
                }
            }
        }

        explicitColorFiles.forEach { fileName ->
            colorDirectories.forEach { directory ->
                runCatching {
                    document("$directory/$fileName").use { document ->
                        val before = valuesPatched
                        recolor(document.documentElement, pureSurface(directory), true)
                        if (valuesPatched > before) verifiedHits += "$directory/$fileName"
                    }
                }
            }
        }

        surfaceDrawableFiles.forEach { fileName ->
            drawableDirectories.forEach { directory ->
                runCatching {
                    document("$directory/$fileName").use { document ->
                        val before = valuesPatched
                        recolor(document.documentElement, pureSurface(directory), false)
                        if (valuesPatched > before) verifiedHits += "$directory/$fileName"
                    }
                }
            }
        }

        valueDirectories.forEach { directory ->
            val target = pureSurface(directory)
            runCatching {
                document("$directory/colors.xml").use { document ->
                    val colors = document.getElementsByTagName("color")
                    var localHits = 0
                    for (index in 0 until colors.length) {
                        val color = colors.item(index) as? Element ?: continue
                        if (color.getAttribute("name") in surfaceColorNames) {
                            // The qualifier, not a *_dark suffix in the resource name, decides the final color.
                            // This is required because Cromite can reference *_dark aliases from the light resource set.
                            color.textContent = target
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
                                item.textContent = target
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
                "Pure black/white resource verification failed for Cromite $CROMITE_VERSION: expected UI surface resources were not found."
            )
        }
    }
}

private const val SYSTEM_SURFACE_SMALI = """
    invoke-virtual {p0}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;
    move-result-object v0
    invoke-virtual {v0}, Landroid/content/res/Resources;->getConfiguration()Landroid/content/res/Configuration;
    move-result-object v0
    iget v0, v0, Landroid/content/res/Configuration;->uiMode:I
    and-int/lit8 v0, v0, 0x30
    xor-int/lit8 v0, v0, 0x20
    if-nez v0, :pure_light
    const v0, -0x1000000
    return v0
    :pure_light
    const v0, -0x1
    return v0
"""

@Suppress("unused")
val pureAmoledPatch = bytecodePatch(
    name = "Pure AMOLED / Pure White system theme",
    description = "Follows Android system theme: true black (#000000) in dark mode and true white (#FFFFFF) in light mode for Cromite 153 native UI surfaces.",
) {
    compatibleWith("org.cromite.cromite"(CROMITE_VERSION))
    dependsOn(pureAmoledResourcesPatch)

    execute {
        val systemSurfaceTargets = listOf(
            colorSurfaceFingerprint, colorSurfaceBrightFingerprint, colorSurfaceContainerFingerprint,
            colorSurfaceContainerHighestFingerprint, colorSurfaceContainerLowFingerprint,
            defaultBgColorFingerprint, settingsBackgroundColorFingerprint, primaryBackgroundColorFingerprint,
        )
        systemSurfaceTargets.forEach { fingerprint ->
            fingerprint.method.addInstructions(0, SYSTEM_SURFACE_SMALI)
        }

        // Original Settings search fix: replace the color just before Drawable.setTint.
        val searchMatches = settingsSearchSurfaceFingerprint.instructionMatches
        check(searchMatches.size == 5) {
            "AMOLED Settings search: expected 5 structural anchors, found ${searchMatches.size}"
        }
        val semanticIndex = searchMatches[1].index
        val tintIndex = searchMatches[2].index
        val firstBackgroundIndex = searchMatches[3].index
        val secondBackgroundIndex = searchMatches[4].index
        check(semanticIndex < tintIndex && tintIndex < firstBackgroundIndex &&
            firstBackgroundIndex < secondBackgroundIndex) {
            "AMOLED Settings search: structural anchors are out of order"
        }
        val searchMethod = settingsSearchSurfaceFingerprint.method
        val semanticCall = searchMethod.getInstruction(semanticIndex)
        val tintCall = searchMethod.getInstruction(tintIndex)
        check(semanticCall is FiveRegisterInstruction &&
            (semanticCall as VariableRegisterInstruction).registerCount == 3 &&
            tintCall is FiveRegisterInstruction &&
            (tintCall as VariableRegisterInstruction).registerCount == 2) {
            "AMOLED Settings search: unexpected color/tint register shape"
        }
        val contextReg = (semanticCall as FiveRegisterInstruction).registerC
        val tintReg = (tintCall as FiveRegisterInstruction).registerD
        // Reproduces the original 'device' option: ChromeColors primary background.
        searchMethod.addInstructions(
            tintIndex,
            """
                const/4 v$tintReg, 0x0
                invoke-static {v$contextReg, v$tintReg}, Lq72;->b(Landroid/content/Context;Z)I
                move-result v$tintReg
            """.trimIndent(),
        )

        // Dynamic Material colors would re-introduce tinted gray/colored surfaces.
        dynamicColorsApplyFingerprint.method.addInstructions(0, "return-void")
    }
}
