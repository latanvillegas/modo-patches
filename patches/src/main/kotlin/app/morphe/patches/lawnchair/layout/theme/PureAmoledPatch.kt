package app.morphe.patches.lawnchair.layout.theme

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * Conservative first-stage AMOLED patch for Lawnchair 15 Beta 3.
 *
 * Only verified opaque Material surfaces in the default colors table are
 * changed. Night-qualified and Android 12+ dynamic resources are intentionally
 * out of scope until their actual editable paths have been verified.
 */
@Suppress("unused")
val lawnchairPureAmoledPatch = resourcePatch(
    name = "Lawnchair - Pure AMOLED surfaces",
    description = "Blackens verified opaque Material surfaces; preserves accents, text and dynamic colors.",
    default = false,
) {
    compatibleWith("app.lawnchair"("15.Beta 3"))

    execute {
        val expected = mapOf(
            "material_color_surface" to "#fffaf9fd",
            "material_color_surface_bright" to "#fffaf9fd",
            "material_color_surface_container" to "#ffefedf1",
            "material_color_surface_container_high" to "#ffe9e7ec",
            "material_color_surface_container_highest" to "#ffe3e2e6",
            "material_color_surface_container_low" to "#fff5f3f7",
            "material_color_surface_container_lowest" to "#ffffffff",
            "material_color_surface_dim" to "#ffdbd9dd",
            "material_color_surface_variant" to "#ffe1e2ec",
        )

        // This path follows the verified Movie resource-patch API pattern.
        // If the target APK exposes a different resource structure, fail
        // instead of silently modifying an unintended value.
        val path = "res/values/colors.xml"
        document(path).use { doc ->
            val found = mutableSetOf<String>()
            val colors = doc.getElementsByTagName("color")
            for (index in 0 until colors.length) {
                val color = colors.item(index) as? Element ?: continue
                val name = color.getAttribute("name")
                val expectedValue = expected[name] ?: continue
                check(found.add(name)) {
                    "Lawnchair: duplicate color resource $name in $path"
                }
                val actual = color.textContent.trim()
                check(actual.equals(expectedValue, ignoreCase = true)) {
                    "Lawnchair: unexpected $name in $path: $actual (expected $expectedValue)"
                }
                color.textContent = "#FF000000"
            }
            check(found == expected.keys) {
                "Lawnchair: missing verified surface resources in $path: ${expected.keys - found}"
            }
        }
    }
}
