package app.morphe.patches.lawnchair.layout.theme

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * First-stage AMOLED surface patch for Lawnchair 15 Beta 3.
 *
 * Only explicit Material surface color resources are changed. Dynamic system
 * accents, text/on-surface colors, inverse surfaces and wallpaper are preserved.
 * The patch fails if its expected base resources are missing.
 */
@Suppress("unused")
val lawnchairPureAmoledPatch = resourcePatch(
    name = "Lawnchair - Pure AMOLED surfaces",
    description = "Makes verified Material surface backgrounds pure black while preserving accents and text.",
    default = false,
) {
    compatibleWith("app.lawnchair"("15.Beta 3"))

    execute {
        val surfaces = setOf(
            "material_color_surface",
            "material_color_surface_bright",
            "material_color_surface_container",
            "material_color_surface_container_high",
            "material_color_surface_container_highest",
            "material_color_surface_container_low",
            "material_color_surface_container_lowest",
            "material_color_surface_dim",
            "material_color_surface_variant",
        )

        // Both default and night resources are expected in the audited APK.
        // Android 12+ dynamic resource XML is deliberately not rewritten here.
        for (path in listOf("res/values/colors.xml", "res/values-night/colors.xml")) {
            document(path).use { doc ->
                val found = mutableSetOf<String>()
                val colors = doc.getElementsByTagName("color")
                for (index in 0 until colors.length) {
                    val color = colors.item(index) as? Element ?: continue
                    val name = color.getAttribute("name")
                    if (name !in surfaces) continue
                    val previous = color.textContent.trim()
                    check(Regex("^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$").matches(previous)) {
                        "Lawnchair: unexpected nonliteral surface $name in $path: $previous"
                    }
                    found += name
                    color.textContent = "#FF000000"
                }
                check(found == surfaces) {
                    "Lawnchair: missing Material surfaces in $path: ${surfaces - found}"
                }
            }
        }
    }
}
