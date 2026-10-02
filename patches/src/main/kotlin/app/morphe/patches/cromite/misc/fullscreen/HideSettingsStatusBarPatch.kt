package app.morphe.patches.cromite.misc.fullscreen

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val CROMITE_SETTINGS_VERSION = "153.0.8010.37"
private const val SETTINGS_ACTIVITY =
    "org.chromium.chrome.browser.settings.SettingsActivity"

/**
 * Independent Settings-only status-bar patch.
 *
 * This deliberately does not touch pc0/ng5 or any bytecode lifecycle method.
 * SettingsActivity owns a dedicated manifest theme, so the status bar can be
 * suppressed at the window-theme boundary without risking Activity registers.
 */
@Suppress("unused")
val hideSettingsStatusBarPatch = resourcePatch(
    name = "Hide status bar in Settings",
    description = "Independent Cromite Settings patch. Hides the Android status bar only in SettingsActivity and does not modify the working main-browser status-bar patch.",
    default = false,
) {
    compatibleWith("org.cromite.cromite"(CROMITE_SETTINGS_VERSION))

    execute {
        var settingsTheme: String? = null

        document("AndroidManifest.xml").use { document ->
            val activities = document.getElementsByTagName("activity")
            for (index in 0 until activities.length) {
                val activity = activities.item(index) as? Element ?: continue
                if (activity.getAttribute("android:name") != SETTINGS_ACTIVITY) continue

                settingsTheme = activity.getAttribute("android:theme")
                    .takeIf { it.startsWith("@style/") }
                    ?: throw IllegalStateException(
                        "Cromite $CROMITE_SETTINGS_VERSION SettingsActivity has no explicit @style theme"
                    )
                break
            }
        }

        val targetTheme = settingsTheme
            ?.removePrefix("@style/")
            ?: throw IllegalStateException(
                "Cromite $CROMITE_SETTINGS_VERSION SettingsActivity was not found in AndroidManifest.xml"
            )

        var patched = false
        document("res/values/styles.xml").use { document ->
            val styles = document.getElementsByTagName("style")
            for (index in 0 until styles.length) {
                val style = styles.item(index) as? Element ?: continue
                if (style.getAttribute("name") != targetTheme) continue

                val items = style.getElementsByTagName("item")
                var fullscreen: Element? = null
                for (itemIndex in 0 until items.length) {
                    val item = items.item(itemIndex) as? Element ?: continue
                    if (item.getAttribute("name") == "android:windowFullscreen") {
                        fullscreen = item
                        break
                    }
                }

                if (fullscreen == null) {
                    fullscreen = document.createElement("item")
                    fullscreen.setAttribute("name", "android:windowFullscreen")
                    style.appendChild(fullscreen)
                }
                fullscreen.textContent = "true"
                patched = true
                break
            }
        }

        if (!patched) {
            throw IllegalStateException(
                "Cromite $CROMITE_SETTINGS_VERSION Settings theme '$targetTheme' was not found in res/values/styles.xml"
            )
        }
    }
}
