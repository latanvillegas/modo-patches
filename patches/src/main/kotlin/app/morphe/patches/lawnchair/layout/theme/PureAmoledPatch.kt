package app.morphe.patches.lawnchair.layout.theme

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * AMOLED surfaces for Lawnchair 15 Beta 3.
 *
 * Patches nine verified opaque Material surface resources and their Android
 * 12+ dynamic selectors. Accent, text and inverse surface colors are untouched.
 * Unexpected XML shapes fail closed rather than being modified blindly.
 */
@Suppress("unused")
val lawnchairPureAmoledPatch = resourcePatch(
    name = "Lawnchair - Pure AMOLED surfaces",
    description = "Blackens verified default Material surfaces and Android 12+ dynamic selectors.",
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

        // Morphe's decoded resources expose the default color table as XML.
        // Verify every original value before replacing it.
        val basePath = "res/values/colors.xml"
        document(basePath).use { doc ->
            val found = mutableSetOf<String>()
            val colors = doc.getElementsByTagName("color")
            for (index in 0 until colors.length) {
                val color = colors.item(index) as? Element ?: continue
                val name = color.getAttribute("name")
                val original = expected[name] ?: continue
                check(found.add(name)) { "Lawnchair: duplicate $name in $basePath" }
                check(color.textContent.trim().equals(original, ignoreCase = true)) {
                    "Lawnchair: unexpected $name in $basePath: ${color.textContent.trim()}"
                }
                color.textContent = "#FF000000"
            }
            check(found == expected.keys) {
                "Lawnchair: missing colors in $basePath: ${expected.keys - found}"
            }
        }

        // Android 12+ uses single-item selectors with a system dynamic color
        // and android:lStar. Validate the shape and reference before rewriting.
        val androidNs = "http://schemas.android.com/apk/res/android"
        for (directory in listOf("res/color-v31", "res/color-night-v31")) {
            for (name in expected.keys) {
                val path = "$directory/$name.xml"
                document(path).use { doc ->
                    val root = doc.documentElement
                    check(root.localName == "selector" || root.nodeName == "selector") {
                        "Lawnchair: unexpected selector root in $path"
                    }
                    val items = root.getElementsByTagName("item")
                    check(items.length == 1) {
                        "Lawnchair: expected one selector item in $path; found ${items.length}"
                    }
                    val item = items.item(0) as Element
                    val color = item.getAttributeNS(androidNs, "color")
                    check(color == "@android:color/system_neutral1_100" ||
                          color == "@0x01060024") {
                        "Lawnchair: unexpected dynamic color reference in $path: $color"
                    }
                    val lStar = item.getAttributeNS(androidNs, "lStar")
                    check(item.hasAttributeNS(androidNs, "lStar") &&
                          lStar.toFloatOrNull()?.let { it in 0f..100f } == true) {
                        "Lawnchair: missing or invalid lStar in $path: $lStar"
                    }
                    val expectedLStar = when (name) {
                        "material_color_surface" ->
                            if (directory.endsWith("night-v31")) "6" else "98"
                        "material_color_surface_container" ->
                            if (directory.endsWith("night-v31")) "12" else "94"
                        else -> null
                    }
                    check(expectedLStar == null || lStar == expectedLStar) {
                        "Lawnchair: unexpected lStar in $path: $lStar (expected $expectedLStar)"
                    }
                    check(item.attributes.length == 2) {
                        "Lawnchair: unexpected selector attributes in $path"
                    }
                    check(root.getElementsByTagName("*").length == 1) {
                        "Lawnchair: unexpected nested selector elements in $path"
                    }
                    item.setAttributeNS(androidNs, "android:color", "#FF000000")
                    item.removeAttributeNS(androidNs, "lStar")
                }
            }
        }
    }
}
