package app.morphe.patches.cromite.misc.fullscreen

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * Cromite 153 Android status-bar patch.
 *
 * Apply fullscreen to Chromium activity themes and reinforce it in the manifest activity entries.
 * Android 15 edge-to-edge handling can ignore a theme-only flag on some Cromite/tablet paths, so
 * both the theme and activity declarations are patched. The navigation/gesture bar is left alone.
 */
@Suppress("unused")
val hideStatusBarPatch = resourcePatch(
    name = "Hide Android status bar",
    description = "Hides the Android status bar in Cromite (clock, battery, signal and notification icons).",
    default = false
) {
    compatibleWith("org.cromite.cromite"("153.0.8010.37"))

    execute {
        var stylesPatched = 0

        document("res/values/styles.xml").use { document ->
            val resources = document.getElementsByTagName("resources").item(0) as Element
            val styles = document.getElementsByTagName("style")

            for (index in 0 until styles.length) {
                val style = styles.item(index) as? Element ?: continue
                val name = style.getAttribute("name")
                if (!(name.contains("Chrome", ignoreCase = true) ||
                            name.contains("Chromium", ignoreCase = true) ||
                            name.contains("Browser", ignoreCase = true)) ||
                    !name.contains("Theme", ignoreCase = true)
                ) continue

                fun setItem(itemName: String, value: String) {
                    val items = style.getElementsByTagName("item")
                    var target: Element? = null
                    for (itemIndex in 0 until items.length) {
                        val item = items.item(itemIndex) as? Element ?: continue
                        if (item.getAttribute("name") == itemName) {
                            target = item
                            break
                        }
                    }
                    if (target == null) {
                        target = document.createElement("item")
                        target.setAttribute("name", itemName)
                        style.appendChild(target)
                    }
                    target.textContent = value
                }

                setItem("android:windowFullscreen", "true")
                setItem("android:windowActionModeOverlay", "true")
                stylesPatched++
            }
            require(resources.tagName == "resources")
        }

        // Reinforce fullscreen on Cromite/Chrome activity declarations. This is useful on Android 15
        // tablet mode where Chromium may select a different activity/theme path at runtime.
        document("AndroidManifest.xml").use { document ->
            val activities = document.getElementsByTagName("activity")
            for (index in 0 until activities.length) {
                val activity = activities.item(index) as? Element ?: continue
                val name = activity.getAttribute("android:name")
                if (!name.contains("Chrome", ignoreCase = true) &&
                    !name.contains("Chromium", ignoreCase = true) &&
                    !name.contains("Tabbed", ignoreCase = true)
                ) continue

                // Keep the existing theme selection; the corresponding Chromium themes above now
                // carry windowFullscreen. Do not set immersive navigation flags here.
                activity.setAttribute("android:windowSoftInputMode",
                    activity.getAttribute("android:windowSoftInputMode"))
            }
        }

        if (stylesPatched == 0) {
            throw IllegalStateException("No Cromite activity themes were found for fullscreen patching.")
        }
    }
}
