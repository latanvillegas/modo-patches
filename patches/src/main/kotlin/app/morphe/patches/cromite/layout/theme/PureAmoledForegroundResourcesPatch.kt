package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import java.util.Locale

/**
 * Reconstructed from PureAmoledForegroundResourcesPatchKt.class
 * in the original patches-1.50.0.mpp bundle.
 *
 * Foreground contrast is deliberately INVERTED relative to background:
 * Pure Black background -> white text/icons; Pure White -> black text/icons.
 */
@Suppress("unused")
val pureAmoledForegroundResourcesPatch = resourcePatch(
    name = "AMOLED - Semantic foreground resources",
    description = "Applies Pure Black / Pure White to verified native semantic text, icon and on-surface foreground resources.",
) {
    compatibleWith("org.cromite.cromite"("153.0.8010.37"))
    val browserTheme = recoveredBrowserThemeOption

    execute {
        val foreground = when (browserTheme.value ?: "device") {
            "device" -> return@execute
            "black" -> "#FFFFFFFF"
            "white" -> "#FF000000"
            else -> return@execute
        }
        val exactNames = setOf(
            "default_text_color",
            "default_icon_color",
            "color_on_background",
            "color_on_surface",
            "color_on_surface_variant",
            "gm3_baseline_on_surface",
            "gm3_baseline_on_surface_variant",
        )
        fun isSemanticForeground(name: String): Boolean {
            val lower = name.lowercase(Locale.ROOT)
            return lower in exactNames ||
                "on_surface" in lower || "onsurface" in lower ||
                "on_background" in lower || "onbackground" in lower
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
                            val elements = xml.getElementsByTagName("*")
                            for (index in 0 until elements.length) {
                                val element = elements.item(index) as? Element ?: continue
                                val name = element.getAttribute("name")
                                if (!isSemanticForeground(name)) continue
                                if (element.tagName == "color" ||
                                    (element.tagName == "item" && element.getAttribute("type") == "color")
                                ) {
                                    element.textContent = foreground
                                    changed++
                                }
                            }
                        }
                    }
            }
        check(changed > 0) {
            "AMOLED semantic foreground verification failed for Cromite 153.0.8010.37: no verified foreground resources were found."
        }
    }
}
