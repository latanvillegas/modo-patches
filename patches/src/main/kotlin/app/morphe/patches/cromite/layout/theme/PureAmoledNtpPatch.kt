package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Partial recovery of PureAmoledNtpPatchKt.class from patches-1.50.0.mpp.
 * Reconstructs the exact two NTP tile resources and their light/night mapping.
 *
 * The original patch additionally modifies NewTabPageLayout and Most Visited
 * card bytecode. Those DEX mutations are not restored by this resource patch.
 */
@Suppress("unused")
val pureAmoledNtpPatch = resourcePatch(
    name = "AMOLED - New Tab Page (tile resources)",
    description = "Recovers original NTP tile icon background resources; NewTabPageLayout and Most Visited DEX changes remain pending.",
) {
    compatibleWith("org.cromite.cromite"("153.0.8010.37"))
    val browserTheme = recoveredBrowserThemeOption

    execute {
        val theme = browserTheme.value ?: "device"

        // Verified from the original 1.50.0 class: in device mode the
        // daytime and nighttime defaults are deliberately different.
        val targets = listOf(
            "res/color/tile_view_icon_background_color.xml" to
                (if (theme == "black") "#FF000000" else "#FFFFFFFF"),
            "res/color-night-v8/tile_view_icon_background_color.xml" to
                (if (theme == "white") "#FFFFFFFF" else "#FF000000"),
        )
        var changed = 0

        fun recolor(element: Element, color: String) {
            val attrs = element.attributes
            for (i in 0 until attrs.length) {
                val attr = attrs.item(i)
                if (attr.nodeName == "color" || attr.nodeName.endsWith(":color")) {
                    attr.nodeValue = color
                }
            }
            if (element.childNodes.length == 1 &&
                element.firstChild?.nodeType == Node.TEXT_NODE &&
                element.textContent.trim().startsWith("#")
            ) {
                element.textContent = color
            }
            val children = element.childNodes
            for (i in 0 until children.length) {
                (children.item(i) as? Element)?.let { recolor(it, color) }
            }
        }

        targets.forEach { (path, color) ->
            if (!get(path).isFile) return@forEach
            document(path).use { xml ->
                recolor(xml.documentElement, color)
                changed++
            }
        }
        check(changed > 0) {
            "AMOLED NTP tile resource verification failed for Cromite 153.0.8010.37: tile_view_icon_background_color.xml was not found."
        }
    }
}
