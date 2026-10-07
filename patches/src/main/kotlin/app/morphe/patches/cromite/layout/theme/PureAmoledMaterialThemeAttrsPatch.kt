package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val MATERIAL_THEME_ATTRS_VERSION = "153.0.8010.37"
private const val MATERIAL_THEME_ATTRS_BLACK = "#FF000000"
private const val MATERIAL_THEME_ATTRS_WHITE = "#FFFFFFFF"

@Suppress("unused")
val pureAmoledMaterialThemeAttrsPatch = resourcePatch(
    name = "AMOLED - Material theme attrs",
    description = "Applies Pure Black / Pure White to existing native Material/Chromium surface and foreground theme attributes without affecting WebContents.",
) {
    compatibleWith("org.cromite.cromite"(MATERIAL_THEME_ATTRS_VERSION))
    val browserTheme by browserThemeOption()

    execute {
        val selectedTheme = browserTheme ?: "device"
        if (selectedTheme == "device") return@execute
        val surface = when (selectedTheme) {
            "black" -> MATERIAL_THEME_ATTRS_BLACK
            "white" -> MATERIAL_THEME_ATTRS_WHITE
            else -> return@execute
        }
        val foreground = if (selectedTheme == "black") MATERIAL_THEME_ATTRS_WHITE else MATERIAL_THEME_ATTRS_BLACK

        val surfaceAttrs = setOf(
            "android:colorBackground", "android:windowBackground", "colorBackground",
            "colorSurface", "colorSurfaceBright", "colorSurfaceDim", "colorSurfaceVariant",
            "colorSurfaceContainerLowest", "colorSurfaceContainerLow", "colorSurfaceContainer",
            "colorSurfaceContainerHigh", "colorSurfaceContainerHighest",
        )
        val foregroundAttrs = setOf(
            "android:textColorPrimary", "android:textColorSecondary",
            "colorOnBackground", "colorOnSurface", "colorOnSurfaceVariant",
        )

        fun isNativeTheme(style: Element): Boolean {
            val name = style.getAttribute("name").lowercase()
            val parent = style.getAttribute("parent").lowercase()
            if (name.isBlank()) return false
            val isolated =
                "settings" in name || "preference" in name ||
                "newtab" in name || "new_tab" in name || "ntp" in name || "home_surface" in name ||
                "appmenu" in name || "menu_" in name || "popup_" in name || "mtrl_popupmenu" in name ||
                "dialog" in name || "sheet" in name || "card" in name || "snackbar" in name ||
                "message_bg" in name || "toast_color" in name || "widget" in name || "xr_" in name ||
                "omnibox" in name || "search_box" in name || "search_suggestion" in name || "toolbar_text_box" in name ||
                "tab_strip" in name || "single_tab" in name || "toolbar_background" in name || "top_toolbar" in name ||
                "context_menu" in name || "item_chooser" in name || "interactable_chip" in name ||
                "m3_list_item" in name || "m3_segmented_list_item" in name ||
                "m3_textfield_filled" in name || "mtrl_filled_background" in name ||
                "default_bg_color" in name || "primary_background_color" in name
            if (isolated) return false

            var declaresRole = false
            val children = style.childNodes
            for (index in 0 until children.length) {
                val child = children.item(index) as? Element ?: continue
                if (child.tagName == "item" &&
                    (child.getAttribute("name") in surfaceAttrs || child.getAttribute("name") in foregroundAttrs)) {
                    declaresRole = true
                    break
                }
            }
            return "material" in parent || "chromium" in parent || "browser" in parent ||
                "chromium" in name || "browser" in name || "material" in name || declaresRole
        }

        fun setExisting(style: Element, attr: String, value: String): Boolean {
            val children = style.childNodes
            for (index in 0 until children.length) {
                val child = children.item(index) as? Element ?: continue
                if (child.tagName == "item" && child.getAttribute("name") == attr) {
                    child.textContent = value
                    return true
                }
            }
            return false
        }

        val directories = get("res").listFiles()?.filter {
            it.isDirectory && it.name.startsWith("values")
        }.orEmpty()
        var hits = 0

        directories.forEach { directory ->
            directory.listFiles()?.filter { it.isFile && it.extension == "xml" }.orEmpty().forEach { file ->
                runCatching {
                    document(file.absolutePath).use { document ->
                        val styles = document.getElementsByTagName("style")
                        for (index in 0 until styles.length) {
                            val style = styles.item(index) as? Element ?: continue
                            if (!isNativeTheme(style)) continue
                            surfaceAttrs.forEach { if (setExisting(style, it, surface)) hits++ }
                            foregroundAttrs.forEach { if (setExisting(style, it, foreground)) hits++ }
                        }
                    }
                }
            }
        }

        if (hits == 0) {
            throw IllegalStateException(
                "AMOLED Material theme attrs verification failed for Cromite $MATERIAL_THEME_ATTRS_VERSION: no existing native theme attrs were found."
            )
        }
    }
}
