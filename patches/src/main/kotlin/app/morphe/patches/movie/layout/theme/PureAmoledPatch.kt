package app.morphe.patches.movie.layout.theme

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/** Recolors verified Movie 4.0.0 UI surfaces without touching video or text resources. */
@Suppress("unused")
val moviePureAmoledPatch = resourcePatch(
    name = "Movie - Pure AMOLED",
    description = "Makes verified Movie interface surfaces pure black.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))

    execute {
        val surfaces = setOf(
            "color_202023", "color_272b30", "color_343940",
            "color_38383A", "color_commen_editbg", "color_home_but_bg",
            "color_home_gusslike_bg", "main_tab_bg", "theme_bg_color",
        )
        document("res/values/colors.xml").use { doc ->
            val found = mutableSetOf<String>()
            val colors = doc.getElementsByTagName("color")
            for (index in 0 until colors.length) {
                val color = colors.item(index) as? Element ?: continue
                val name = color.getAttribute("name")
                if (name !in surfaces) continue
                if (name == "theme_bg_color") {
                    check(color.textContent.trim().equals("#020d19", ignoreCase = true)) {
                        "Movie: unexpected theme_bg_color value: ${color.textContent.trim()}"
                    }
                }
                found += name
                color.textContent = "#FF000000"
            }
            check(found == surfaces) {
                "Movie: missing expected surface colors: ${surfaces - found}"
            }
        }

        for (path in listOf("res/values/styles.xml", "res/values-v27/styles.xml")) {
            document(path).use { doc ->
                val styles = doc.getElementsByTagName("style")
                var found = false
                for (index in 0 until styles.length) {
                    val style = styles.item(index) as? Element ?: continue
                    if (style.getAttribute("name") != "AppTheme_Main") continue
                    found = true
                    val items = style.getElementsByTagName("item")
                    for (itemIndex in 0 until items.length) {
                        val item = items.item(itemIndex) as? Element ?: continue
                        when (item.getAttribute("name")) {
                            "android:windowBackground", "colorPrimary", "colorPrimaryDark" ->
                                item.textContent = "#FF000000"
                        }
                    }
                }
                check(found) { "Movie: AppTheme_Main missing in $path" }
            }
        }
    }
}
