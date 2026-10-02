package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import org.w3c.dom.Element
import org.w3c.dom.Node

private const val CROMITE_VERSION = "153.0.8010.37"
private const val SEMANTIC_COLOR_UTILS = "Lpac;"
private const val CHROME_COLORS = "Lq72;"
private const val DYNAMIC_COLORS = "Lqb4;"
private const val DYNAMIC_COLORS_OPTIONS = "Ltb4;"
private const val TAB_UI_THEME_UTILS = "Laqe;"
private const val CONTEXT = "Landroid/content/Context;"
private const val ACTIVITY = "Landroid/app/Activity;"
private const val PURE_BLACK = "#FF000000"
private const val PURE_WHITE = "#FFFFFFFF"
private const val THEME_DEVICE = "device"
private const val THEME_BLACK = "black"
private const val THEME_WHITE = "white"

internal val browserThemeOption = stringOption(
    key = "browserTheme",
    default = THEME_DEVICE,
    values = mapOf(
        "Device default" to THEME_DEVICE,
        "Pure Black" to THEME_BLACK,
        "Pure White" to THEME_WHITE,
    ),
    title = "Browser theme",
    description = "Choose Device default, Pure Black (#000000), or Pure White (#FFFFFF).",
)

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
private val defaultIconColorFingerprint = semanticSurfaceFingerprint("i")
private val defaultTextColorFingerprint = semanticSurfaceFingerprint("j")
internal val settingsBackgroundColorFingerprint = semanticSurfaceFingerprint("l")

private val primaryBackgroundColorFingerprint = Fingerprint(
    definingClass = CHROME_COLORS,
    name = "b",
    returnType = "I",
    parameters = listOf(CONTEXT, "Z"),
)

private val tabStripBackgroundDefaultFingerprint = Fingerprint(
    definingClass = TAB_UI_THEME_UTILS,
    name = "c",
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
    description = "Internal resources for selectable Device default / Pure Black / Pure White Cromite UI."
) {
    compatibleWith("org.cromite.cromite"(CROMITE_VERSION))

    execute {
        if (browserThemeOption.value == THEME_DEVICE) return@execute

        val explicitColorFiles = setOf(
            "context_menu_header_circle_bg_color.xml",
            "hub_search_box_bg_color.xml", "interactable_chip_bg_color.xml", "item_chooser_row_background.xml",
            "m3_list_item_background_color_selector.xml", "m3_segmented_list_item_background_color_selector.xml",
            "m3_textfield_filled_background_color.xml", "mtrl_filled_background_color.xml",
            "omnibox_popover_suggestion_bg_color.xml",
            "omnibox_suggestion_dropdown_bg.xml", "search_suggestion_bg_color.xml", "single_tab_favicon_bg_color.xml",
            "tab_strip_ntb_bg_tint_list.xml",
            "toolbar_text_box_bg_color.xml",
            "widget_bg.xml", "widget_button_bg.xml", "widget_searchbox_bg.xml", "xr_toolbar_bg.xml",

            // Desktop-Android/Cromite toolbar and tab-strip surfaces. Missing files are intentionally ignored.
            "toolbar_background_primary.xml", "toolbar_background_primary_incognito.xml",
            "toolbar_background_color.xml", "top_toolbar_background_color.xml",
            "tab_strip_background_color.xml", "tab_strip_bg_color.xml", "tab_strip_background.xml",
            "primary_background_color.xml", "default_bg_color.xml",
        )

        val surfaceDrawableFiles = setOf(

            "toolbar_background.xml", "tab_strip_background.xml",
        )

        // Chromium resources are compiled into qualifier sets that can change between
        // releases (values-v34, values-sw600dp, color-night-vXX, etc.). Discover the
        // actual decoded directories instead of assuming a short fixed list.
        val resourceDirectories = get("res").listFiles()
            ?.filter { it.isDirectory }
            .orEmpty()
        val drawableDirectories = resourceDirectories
            .filter { it.name.startsWith("drawable") }
            .map { it.absolutePath }
        val colorDirectories = resourceDirectories
            .filter { it.name.startsWith("color") }
            .map { it.absolutePath }
        val valueDirectories = resourceDirectories
            .filter { it.name.startsWith("values") }
            .map { it.absolutePath }

        fun isNight(directory: String) = java.io.File(directory).name.contains("night")
        fun pureSurface(directory: String) = when (browserThemeOption.value) {
            THEME_BLACK -> PURE_BLACK
            THEME_WHITE -> PURE_WHITE
            else -> if (isNight(directory)) PURE_BLACK else PURE_WHITE
        }

        val surfaceColorNames = setOf(
            "default_bg_color", "default_bg_color_dark",
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
            "mtrl_filled_background_color",
            "omnibox_suggestion_bg_color", "omnibox_popover_suggestion_bg_color",
            "hub_search_box_bg_color",
        )

        fun isMaterialSurfaceColorName(name: String): Boolean {
            val normalized = name.lowercase()
            if ("on_surface" in normalized || "onsurface" in normalized) return false
            return normalized.startsWith("gm3_baseline_surface") ||
                normalized.startsWith("m3_sys_color_light_surface") ||
                normalized.startsWith("m3_sys_color_dark_surface") ||
                normalized.startsWith("m3_sys_color_dynamic_light_surface") ||
                normalized.startsWith("m3_sys_color_dynamic_dark_surface") ||
                normalized.startsWith("material_dynamic_neutral") && "surface" in normalized
        }

        // R8 in Cromite 153 inlines SemanticColorUtils.getSettingsBackgroundColor()
        // and several other surface getters into their consumers. Consequently a
        // bytecode hook on SemanticColorUtils alone cannot cover Settings. Match
        // decoded resources by semantic surface/background naming as well, while
        // deliberately excluding foreground, media and state/accent resources.
        fun isUiSurfaceResourceName(name: String): Boolean {
            val normalized = name.lowercase()
            if (normalized.isBlank()) return false
            if ("on_surface" in normalized || "onsurface" in normalized) return false
            if ("foreground" in normalized || "text" in normalized || "icon" in normalized) return false
            if ("ripple" in normalized || "divider" in normalized || "stroke" in normalized) return false
            if ("accent" in normalized || "primary" in normalized && "background" !in normalized) return false
            return "surface" in normalized ||
                "background" in normalized ||
                normalized.endsWith("_bg") ||
                "_bg_" in normalized ||
                normalized.endsWith("_bg_color") ||
                normalized.startsWith("menu_bg") ||
                normalized.startsWith("popup_bg") ||
                normalized.contains("container") && (
                    normalized.contains("card") ||
                    normalized.contains("menu") ||
                    normalized.contains("toolbar") ||
                    normalized.contains("omnibox") ||
                    normalized.contains("tab")
                )
        }

        fun isGrayUiColorLiteral(value: String): Boolean {
            if (browserThemeOption.value != THEME_BLACK) return false
            val hex = value.trim().removePrefix("#")
            val rgb = when (hex.length) {
                6 -> hex
                8 -> hex.substring(2)
                else -> return false
            }
            val red = rgb.substring(0, 2).toIntOrNull(16) ?: return false
            val green = rgb.substring(2, 4).toIntOrNull(16) ?: return false
            val blue = rgb.substring(4, 6).toIntOrNull(16) ?: return false
            // Only neutral dark grays used as UI surfaces. Excludes colored
            // accents/media and excludes light foreground text.
            return red == green && green == blue && red in 1..96
        }

        fun isSafeUiBackgroundName(name: String): Boolean {
            val normalized = name.lowercase()
            if (normalized.isBlank()) return false
            if ("text" in normalized || "icon" in normalized || "foreground" in normalized) return false
            if ("ripple" in normalized || "divider" in normalized || "stroke" in normalized) return false
            if ("accent" in normalized || "scrim" in normalized || "overlay" in normalized) return false
            return "background" in normalized ||
                "surface" in normalized ||
                "_bg" in normalized ||
                "container" in normalized ||
                "toolbar" in normalized ||
                "omnibox" in normalized ||
                "menu" in normalized ||
                "tab_strip" in normalized
        }

        val surfaceMacroNames = setOf(
            "default_bg_color", "default_bg_color_elev_0",
            "toolbar_background_primary",
            "history_chip_bg_color",
        )

        val surfaceThemeAttrs = setOf(
            "android:colorBackground", "android:windowBackground",
            "colorBackground", "colorSurface", "colorSurfaceBright", "colorSurfaceDim", "colorSurfaceVariant",
            "colorSurfaceContainerLowest", "colorSurfaceContainerLow", "colorSurfaceContainer",
            "colorSurfaceContainerHigh", "colorSurfaceContainerHighest",
        )

        val foregroundThemeAttrs = setOf(
            "android:textColorPrimary", "android:textColorSecondary",
            "colorOnBackground", "colorOnSurface", "colorOnSurfaceVariant",
        )

        // Material 3 inherits most surface roles from parent themes. Merely replacing
        // <item> nodes that already exist leaves those inherited gray roles untouched.
        // Restrict injected roles to the main Chrome UI theme boundary so
        // MaterialColors resolves the selected palette without coupling Settings or system bars.
        // AppMenu, omnibox and tab surfaces can be inflated through ContextThemeWrapper
        // using Material/BrowserUI overlays rather than a Theme.Chromium root. R8 mapping
        // for Cromite 153 confirms those consumers resolve surface-container roles after
        // inlining SemanticColorUtils. Cover the native Android theme boundary, not only
        // Theme.Chromium, while leaving WebContents completely untouched.
        fun isNativeBrowserUiTheme(style: Element): Boolean {
            val name = style.getAttribute("name").lowercase()
            val parent = style.getAttribute("parent").lowercase()
            if (name.isBlank()) return false

            // Main-browser themes must not absorb independently selectable domains.
            // Parent inheritance alone is insufficient because Settings/NTP overlays
            // commonly inherit Material/Chromium parents as well.
            val isolatedDomain =
                "settings" in name || "preference" in name ||
                "newtab" in name || "new_tab" in name || "ntp" in name ||
                "home_surface" in name ||
                "appmenu" in name || "menu_" in name || "popup_" in name ||
                "mtrl_popupmenu" in name ||
                "dialog" in name || "sheet" in name || "card" in name || "snackbar" in name ||
                "message_bg" in name || "toast_color" in name
            if (isolatedDomain) return false

            // Structural boundary first: Material/Chromium themes and overlays,
            // plus any style that already declares a whitelisted surface role.
            // This catches anonymous/specialized ContextThemeWrapper overlays whose
            // names do not contain "theme", without touching WebContents/media.
            val materialOrBrowserParent =
                "material" in parent || "chromium" in parent || "browser" in parent
            val nativeUiName =
                "chromium" in name || "browser" in name || "material" in name ||
                "appmenu" in name || "menu" in name || "toolbar" in name ||
                "omnibox" in name || "tab" in name ||
                false

            var declaresSurfaceRole = false
            val children = style.childNodes
            for (index in 0 until children.length) {
                val child = children.item(index) as? Element ?: continue
                if (child.tagName == "item" &&
                    child.getAttribute("name") in surfaceThemeAttrs) {
                    declaresSurfaceRole = true
                    break
                }
            }

            return materialOrBrowserParent || nativeUiName || declaresSurfaceRole
        }

        fun setExistingThemeItem(
            style: Element,
            name: String,
            value: String,
        ): Boolean {
            val children = style.childNodes
            for (index in 0 until children.length) {
                val child = children.item(index) as? Element ?: continue
                if (child.tagName == "item" && child.getAttribute("name") == name) {
                    child.textContent = value
                    return true
                }
            }
            // Arsclib can only re-encode attributes that exist in the target APK's
            // resource table. Never synthesize arbitrary Material/Android attrs here:
            // an unknown attr (for example unqualified colorBackground) makes the
            // patched resources impossible to encode.
            return false
        }

        val foregroundColorNames = setOf(
            "default_text_color", "default_icon_color",
            "color_on_background", "color_on_surface", "color_on_surface_variant",
            "gm3_baseline_on_surface", "gm3_baseline_on_surface_variant",
        )

        fun isMaterialForegroundColorName(name: String): Boolean {
            val normalized = name.lowercase()
            return "on_surface" in normalized ||
                "onsurface" in normalized ||
                "on_background" in normalized ||
                "onbackground" in normalized
        }

        fun pureForeground(directory: String) = when (browserThemeOption.value) {
            THEME_BLACK -> PURE_WHITE
            THEME_WHITE -> PURE_BLACK
            else -> if (isNight(directory)) PURE_WHITE else PURE_BLACK
        }

        val pureSurfaceResource = "morphe_pure_browser_surface"

        // Macros in semantic_colors_dynamic.xml are compiled aliases to Material surface
        // attributes. Give them one resource whose day/night variants implement Device
        // default, while explicit Pure Black/Pure White remain fixed in both qualifiers.
        listOf("res/values", "res/values-night").forEach { directory ->
            runCatching {
                document("$directory/colors.xml").use { document ->
                    val root = document.documentElement
                        ?: throw IllegalStateException("$directory/colors.xml has no root")
                    val colors = document.getElementsByTagName("color")
                    var existing: Element? = null
                    for (index in 0 until colors.length) {
                        val color = colors.item(index) as? Element ?: continue
                        if (color.getAttribute("name") == pureSurfaceResource) {
                            existing = color
                            break
                        }
                    }
                    val color = existing ?: document.createElement("color").also {
                        it.setAttribute("name", pureSurfaceResource)
                        root.appendChild(it)
                    }
                    color.textContent = pureSurface(directory)
                }
            }.getOrElse {
                throw IllegalStateException(
                    "Unable to define $pureSurfaceResource in $directory/colors.xml for Cromite $CROMITE_VERSION",
                    it,
                )
            }
        }

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

        // Decoded APKs do not preserve Chromium's source XML filenames reliably.
        // Walk every values*.xml file and patch by resource/attribute name instead of
        // assuming colors.xml, semantic_colors_dynamic.xml, styles.xml, or themes.xml.
        // This reaches direct XML references used by Settings containment and the NTP.
        valueDirectories.forEach { directory ->
            val target = pureSurface(directory)
            val foreground = pureForeground(directory)
            java.io.File(directory).listFiles()
                ?.filter { it.isFile && it.extension == "xml" }
                .orEmpty()
                .forEach { file ->
                    runCatching {
                        document(file.absolutePath).use { document ->
                            var localHits = 0
                            val all = document.getElementsByTagName("*")
                            for (index in 0 until all.length) {
                                val element = all.item(index) as? Element ?: continue
                                val name = element.getAttribute("name")
                                val type = element.getAttribute("type")

                                val isolatedResource =
                                    "settings" in name.lowercase() ||
                                    "preference" in name.lowercase() ||
                                    "newtab" in name.lowercase() ||
                                    "new_tab" in name.lowercase() ||
                                    "ntp" in name.lowercase() ||
                                    "home_surface" in name.lowercase() ||
                                    "appmenu" in name.lowercase() ||
                                    "menu_" in name.lowercase() || "popup_" in name.lowercase() ||
                                    "mtrl_popupmenu" in name.lowercase() ||
                                    "dialog" in name.lowercase() || "sheet" in name.lowercase() ||
                                    "card" in name.lowercase() || "snackbar" in name.lowercase() ||
                                    "message_bg" in name.lowercase() || "toast_color" in name.lowercase() ||
                                    "fake_search_box" in name.lowercase() ||
                                    "tile_view" in name.lowercase()
                                val isNamedSurface = !isolatedResource && (
                                    name in surfaceColorNames ||
                                    name in surfaceMacroNames ||
                                    isMaterialSurfaceColorName(name) ||
                                    isUiSurfaceResourceName(name)
                                )
                                val isNamedForeground = !isolatedResource && (
                                    name in foregroundColorNames ||
                                    isMaterialForegroundColorName(name)
                                )
                                val isDarkGrayUiBackground =
                                    !isolatedResource &&
                                    isSafeUiBackgroundName(name) &&
                                    isGrayUiColorLiteral(element.textContent)

                                when {
                                    (isNamedSurface || isDarkGrayUiBackground) &&
                                        (element.tagName == "color" ||
                                         element.tagName == "macro" ||
                                         (element.tagName == "item" && type == "color")) -> {
                                        element.textContent =
                                            if (element.tagName == "macro") "@color/$pureSurfaceResource" else target
                                        localHits++
                                        valuesPatched++
                                    }
                                    isNamedForeground &&
                                        (element.tagName == "color" ||
                                         (element.tagName == "item" && type == "color")) -> {
                                        element.textContent = foreground
                                        localHits++
                                        valuesPatched++
                                    }
                                    element.tagName == "item" && name in surfaceThemeAttrs -> {
                                        element.textContent = target
                                        localHits++
                                        valuesPatched++
                                    }
                                    element.tagName == "item" && name in foregroundThemeAttrs -> {
                                        element.textContent = foreground
                                        localHits++
                                        valuesPatched++
                                    }
                                }
                            }

                            // Rewrite only roles already declared by the target theme.
                            // Adding missing attr names is unsafe with arsclib because an
                            // attr absent from resources.arsc cannot be encoded.
                            val styles = document.getElementsByTagName("style")
                            for (styleIndex in 0 until styles.length) {
                                val style = styles.item(styleIndex) as? Element ?: continue
                                if (!isNativeBrowserUiTheme(style)) continue

                                surfaceThemeAttrs.forEach { attr ->
                                    if (setExistingThemeItem(style, attr, target)) {
                                        localHits++
                                        valuesPatched++
                                    }
                                }
                                foregroundThemeAttrs.forEach { attr ->
                                    if (setExistingThemeItem(style, attr, foreground)) {
                                        localHits++
                                        valuesPatched++
                                    }
                                }
                            }

                            if (localHits > 0) verifiedHits += "${file.absolutePath}:$localHits"
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

internal fun foregroundSmali(theme: String): String = when (theme) {
    THEME_BLACK -> """
        const v0, -0x1
        return v0
    """
    THEME_WHITE -> """
        const v0, -0x1000000
        return v0
    """
    else -> """
        invoke-virtual {p0}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;
        move-result-object v0
        invoke-virtual {v0}, Landroid/content/res/Resources;->getConfiguration()Landroid/content/res/Configuration;
        move-result-object v0
        iget v0, v0, Landroid/content/res/Configuration;->uiMode:I
        and-int/lit8 v0, v0, 0x30
        xor-int/lit8 v0, v0, 0x20
        if-nez v0, :pure_foreground_light
        const v0, -0x1
        return v0
        :pure_foreground_light
        const v0, -0x1000000
        return v0
    """
}

internal fun surfaceSmali(theme: String): String = when (theme) {
    THEME_BLACK -> """
        const v0, -0x1000000
        return v0
    """
    THEME_WHITE -> """
        const v0, -0x1
        return v0
    """
    else -> """
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
}

@Suppress("unused")
val pureAmoledPatch = bytecodePatch(
    name = "AMOLED - Main browser UI",
    description = "Main Cromite browser UI theme: toolbar/tab surfaces and shared native browser surfaces. Web content is not recolored.",
) {
    compatibleWith("org.cromite.cromite"(CROMITE_VERSION))
    val browserTheme by browserThemeOption()
    dependsOn(pureAmoledResourcesPatch)

    execute {
        val selectedTheme = browserTheme ?: THEME_DEVICE
        if (selectedTheme == THEME_DEVICE) return@execute

        val systemSurfaceTargets = listOf(
            colorSurfaceFingerprint, colorSurfaceBrightFingerprint, colorSurfaceContainerFingerprint,
            colorSurfaceContainerHighestFingerprint, colorSurfaceContainerLowFingerprint,
            defaultBgColorFingerprint, primaryBackgroundColorFingerprint,
        )
        systemSurfaceTargets.forEach { fingerprint ->
            fingerprint.method.addInstructions(0, surfaceSmali(selectedTheme))
        }

        // TabUiThemeUtil.getTabStripBackgroundColorDefault(Context, boolean) survives R8
        // as aqe.c. Its source path bypasses the surviving pac surface wrappers, so
        // patch it explicitly; this is native tab-strip UI only, never WebContents.
        tabStripBackgroundDefaultFingerprint.method.addInstructions(
            0,
            surfaceSmali(selectedTheme),
        )

        // These two SemanticColorUtils methods survive R8 in Cromite 153 as pac.i/pac.j.
        // Patch them independently from surfaces so Pure Black never leaves black
        // primary text/icons on a black browser surface (and vice versa for Pure White).
        listOf(defaultIconColorFingerprint, defaultTextColorFingerprint).forEach { fingerprint ->
            fingerprint.method.addInstructions(0, foregroundSmali(selectedTheme))
        }

        // Dynamic Material colors would re-introduce tinted gray/colored surfaces.
        dynamicColorsApplyFingerprint.method.addInstructions(0, "return-void")
    }
}
