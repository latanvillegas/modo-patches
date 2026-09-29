package app.morphe.patches.cromite.misc.fullscreen

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * Cromite 153 Android fullscreen patch.
 *
 * Applies Android's windowFullscreen theme flag to Cromite activities. This hides the Android
 * status bar (clock, battery, signal and notification icons) while leaving Chromium's own toolbar
 * untouched. Navigation-bar/gesture-area hiding is intentionally not included in this first patch.
 */
@Suppress("unused")
val hideStatusBarPatch = resourcePatch(
    name = "Hide Android status bar",
    description = "Hides the Android status bar in Cromite (clock, battery, signal and notification icons).",
    default = false
) {
    compatibleWith("org.cromite.cromite"("153.0.8010.37"))

    execute {
        document("res/values/styles.xml").use { document ->
            val resources = document.getElementsByTagName("resources").item(0) as Element
            val styles = document.getElementsByTagName("style")

            for (index in 0 until styles.length) {
                val style = styles.item(index) as? Element ?: continue
                val name = style.getAttribute("name")

                // Chromium's activity themes are named with Chrome/Chromium/Browser/Theme.
                // Restricting the edit avoids changing unrelated library/dialog themes.
                if (!(name.contains("Chrome", ignoreCase = true) ||
                            name.contains("Chromium", ignoreCase = true) ||
                            name.contains("Browser", ignoreCase = true)) ||
                    !name.contains("Theme", ignoreCase = true)
                ) continue

                var fullscreenItem: Element? = null
                val items = style.getElementsByTagName("item")
                for (itemIndex in 0 until items.length) {
                    val item = items.item(itemIndex) as? Element ?: continue
                    if (item.getAttribute("name") == "android:windowFullscreen") {
                        fullscreenItem = item
                        break
                    }
                }

                if (fullscreenItem == null) {
                    fullscreenItem = document.createElement("item")
                    fullscreenItem.setAttribute("name", "android:windowFullscreen")
                    style.appendChild(fullscreenItem)
                }
                fullscreenItem.textContent = "true"
            }

            // Keep reference alive for resource serializers that validate the root.
            require(resources.tagName == "resources")
        }
    }
}
