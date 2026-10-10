package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * Restored from PureAmoledCardsPatchKt.class in patches-1.50.0.mpp.
 * Original target: tab_hover_card_bg_color in res/color*.
 *
 * Device default deliberately leaves the resource unchanged.
 */
@Suppress("unused")
val pureAmoledCardsPatch = resourcePatch(
    name = "AMOLED - Cards",
    description = "Applies Device default / Pure Black / Pure White to verified Cromite native card surfaces only.",
) {
    compatibleWith("org.cromite.cromite"("153.0.8010.37"))
    val browserTheme = recoveredBrowserThemeOption

    execute {
        val target = when (browserTheme.value ?: "device") {
            "device" -> return@execute
            "black" -> "#FF000000"
            "white" -> "#FFFFFFFF"
            else -> return@execute
        }

        val fileName = "tab_hover_card_bg_color.xml"
        val resourceName = "tab_hover_card_bg_color"
        var hits = 0
        get("res").listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory && it.name.startsWith("color") }
            ?.forEach { directory ->
                val file = directory.resolve(fileName)
                if (!file.isFile) return@forEach
                document("res/${directory.name}/$fileName").use { document ->
                    val root = document.documentElement
                    if (root != null && root.tagName == "color" &&
                        root.getAttribute("name") == resourceName
                    ) {
                        root.textContent = target
                        hits++
                    } else {
                        val items = document.getElementsByTagName("color")
                        for (i in 0 until items.length) {
                            val item = items.item(i) as? Element ?: continue
                            if (item.getAttribute("name") == resourceName) {
                                item.textContent = target
                                hits++
                            }
                        }
                    }
                }
            }

        check(hits > 0) {
            "AMOLED Cards resource verification failed for Cromite 153.0.8010.37: tab_hover_card_bg_color was not found."
        }
    }
}
