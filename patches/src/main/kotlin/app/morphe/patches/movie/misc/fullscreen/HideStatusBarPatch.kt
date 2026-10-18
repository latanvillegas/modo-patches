package app.morphe.patches.movie.misc.fullscreen

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/** Enables fullscreen only for Movie-owned activity themes; runtime overrides are out of scope. */
@Suppress("unused")
val movieHideStatusBarPatch = resourcePatch(
    name = "Movie - Hide status bar",
    description = "Hides the Android status bar in Movie-owned screens without hiding navigation.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))

    execute {
        val targets = setOf("BrowserTheme", "AppTheme_Main", "AppTheme_Splash")
        val manifestThemes = mutableSetOf<String>()
        document("AndroidManifest.xml").use { manifest ->
            val application = manifest.getElementsByTagName("application").item(0) as? Element
                ?: error("Movie: application element not found")
            manifestThemes += application.getAttribute("android:theme").removePrefix("@style/")
            val activities = manifest.getElementsByTagName("activity")
            for (index in 0 until activities.length) {
                val activity = activities.item(index) as? Element ?: continue
                if (!activity.getAttribute("android:name").startsWith("com.mgs.carparking.")) continue
                val theme = activity.getAttribute("android:theme")
                if (theme.startsWith("@style/")) manifestThemes += theme.removePrefix("@style/")
            }
        }
        check(targets.all { it in manifestThemes }) {
            "Movie: expected themes not referenced in manifest: ${targets - manifestThemes}"
        }

        for (path in listOf("res/values/styles.xml", "res/values-v26/styles.xml", "res/values-v27/styles.xml")) {
            document(path).use { doc ->
                val styles = doc.getElementsByTagName("style")
                val found = mutableSetOf<String>()
                for (index in 0 until styles.length) {
                    val style = styles.item(index) as? Element ?: continue
                    if (style.getAttribute("name") !in targets) continue
                    found += style.getAttribute("name")
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
                        fullscreen = doc.createElement("item")
                        fullscreen.setAttribute("name", "android:windowFullscreen")
                        style.appendChild(fullscreen)
                    }
                    fullscreen.textContent = "true"
                }
                check(found.isNotEmpty()) { "Movie: no target themes in $path" }
            }
        }
    }
}
