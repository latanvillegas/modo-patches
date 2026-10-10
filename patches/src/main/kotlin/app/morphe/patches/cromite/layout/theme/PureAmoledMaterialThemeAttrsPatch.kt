package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import java.util.Locale

/**
 * Recovery based on PureAmoledMaterialThemeAttrsPatchKt.class in patches-1.50.0.mpp.
 * Changes only EXISTING attributes in native Material/Chromium/Browser styles.
 */
@Suppress("unused")
val pureAmoledMaterialThemeAttrsPatch = resourcePatch(
    name = "AMOLED - Material theme attrs",
    description = "Applies Pure Black / Pure White to existing native Material/Chromium surface and foreground theme attributes without affecting WebContents.",
) {
    compatibleWith("org.cromite.cromite"("153.0.8010.37"))
    val browserTheme = recoveredBrowserThemeOption

    execute {
        val background = when (browserTheme.value ?: "device") {
            "device" -> return@execute
            "black" -> "#FF000000"
            "white" -> "#FFFFFFFF"
            else -> return@execute
        }
        val foreground = if (background == "#FF000000") "#FFFFFFFF" else "#FF000000"

        val backgroundAttrs = setOf(
            "android:colorBackground", "android:windowBackground", "colorBackground",
            "colorSurface", "colorSurfaceBright", "colorSurfaceDim", "colorSurfaceVariant",
            "colorSurfaceContainerLowest", "colorSurfaceContainerLow", "colorSurfaceContainer",
            "colorSurfaceContainerHigh", "colorSurfaceContainerHighest",
        )
        val foregroundAttrs = setOf(
            "android:textColorPrimary", "android:textColorSecondary",
            "colorOnBackground", "colorOnSurface", "colorOnSurfaceVariant",
        )
        val excludedNames = setOf(
            "settings", "preference", "newtab", "new_tab", "ntp", "home_surface",
            "appmenu", "menu_", "popup_", "mtrl_popupmenu", "dialog", "sheet",
            "card", "snackbar", "message_bg", "toast_color", "widget", "xr_",
            "omnibox", "search_box", "search_suggestion", "toolbar_text_box",
            "tab_strip", "single_tab", "toolbar_background", "top_toolbar",
            "context_menu", "item_chooser", "interactable_chip", "m3_list_item",
            "m3_segmented_list_item", "m3_textfield_filled",
            "mtrl_filled_background", "default_bg_color", "primary_background_color",
        )

        fun isNativeTheme(style: Element): Boolean {
            val name = style.getAttribute("name").lowercase(Locale.ROOT)
            val parent = style.getAttribute("parent").lowercase(Locale.ROOT)
            if (name.isBlank() || excludedNames.any { it in name }) return false
            val hasExistingAttrs = (0 until style.childNodes.length)
                .mapNotNull { style.childNodes.item(it) as? Element }
                .any { it.tagName == "item" && it.getAttribute("name") in (backgroundAttrs + foregroundAttrs) }
            return listOf("material", "chromium", "browser").any { it in name || it in parent } ||
                hasExistingAttrs
        }

        fun setExisting(style: Element, attr: String, value: String): Boolean {
            for (i in 0 until style.childNodes.length) {
                val child = style.childNodes.item(i) as? Element ?: continue
                if (child.tagName == "item" && child.getAttribute("name") == attr) {
                    child.textContent = value
                    return true
                }
            }
            return false
        }

        var changed = 0
        get("res").listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("values") }
            .orEmpty()
            .forEach { directory ->
                directory.listFiles()
                    ?.filter { it.isFile && it.extension == "xml" }
                    .orEmpty()
                    .forEach { file ->
                        document("res/${directory.name}/${file.name}").use { xml ->
                            val styles = xml.getElementsByTagName("style")
                            for (i in 0 until styles.length) {
                                val style = styles.item(i) as? Element ?: continue
                                if (!isNativeTheme(style)) continue
                                backgroundAttrs.forEach { if (setExisting(style, it, background)) changed++ }
                                foregroundAttrs.forEach { if (setExisting(style, it, foreground)) changed++ }
                            }
                        }
                    }
            }
        check(changed > 0) {
            "AMOLED Material theme attrs verification failed for Cromite 153.0.8010.37: no existing native theme attrs were found."
        }
    }
}
