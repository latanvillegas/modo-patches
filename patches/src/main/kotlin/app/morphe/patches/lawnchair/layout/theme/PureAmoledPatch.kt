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
    description = "Blackens verified Material surfaces, dark card/folder backgrounds and Android 12+ dynamic selectors.",
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

        // Additional opaque backgrounds confirmed in the APK resource table.
        // They have no corresponding Material dynamic selector loop.
        val additionalOpaqueBackgrounds = mapOf(
            "cardview_dark_background" to "#ff424242",
            "folder_background_dark" to "#ff1f2020",
            "folder_preview_dark" to "#ff1e1f20",
        )
        val baseExpected = expected + additionalOpaqueBackgrounds

        // Morphe's decoded resources expose the default color table as XML.
        // Verify every original value before replacing it.
        val basePath = "res/values/colors.xml"
        document(basePath).use { doc ->
            val found = mutableSetOf<String>()
            val colors = doc.getElementsByTagName("color")
            for (index in 0 until colors.length) {
                val color = colors.item(index) as? Element ?: continue
                val name = color.getAttribute("name")
                val original = baseExpected[name] ?: continue
                check(found.add(name)) { "Lawnchair: duplicate $name in $basePath" }
                // Android resource decoding may omit opaque FF alpha (#AARRGGBB -> #RRGGBB).
                // Normalize only these two valid formats; do not accept other values.
                fun normalizeOpaqueHex(value: String): String {
                    val hex = value.trim().removePrefix("#").lowercase()
                    check(hex.matches(Regex("[0-9a-f]{6}|[0-9a-f]{8}"))) {
                        "Lawnchair: invalid color literal for $name in $basePath: $value"
                    }
                    return if (hex.length == 6) "ff$hex" else hex
                }
                check(normalizeOpaqueHex(color.textContent) == normalizeOpaqueHex(original)) {
                    "Lawnchair: unexpected $name in $basePath: ${color.textContent.trim()}"
                }
                color.textContent = "#FF000000"
            }
            check(found == baseExpected.keys) {
                "Lawnchair: missing colors in $basePath: ${baseExpected.keys - found}"
            }
        }

        // The APK also defines independent night resources. Without this
        // override, dark and system-following-dark themes retain gray surfaces.
        val expectedNight = mapOf(
            "material_color_surface" to "#ff121316",
            "material_color_surface_bright" to "#ff38393c",
            "material_color_surface_container" to "#ff1f1f23",
            "material_color_surface_container_high" to "#ff292a2d",
            "material_color_surface_container_highest" to "#ff343538",
            "material_color_surface_container_low" to "#ff1b1b1f",
            "material_color_surface_container_lowest" to "#ff0d0e11",
            "material_color_surface_dim" to "#ff121316",
            "material_color_surface_variant" to "#ff44474f",
        )
        val nightPath = "res/values-night/colors.xml"
        document(nightPath).use { doc ->
            val foundNight = mutableSetOf<String>()
            val colors = doc.getElementsByTagName("color")
            for (index in 0 until colors.length) {
                val color = colors.item(index) as? Element ?: continue
                val name = color.getAttribute("name")
                val original = expectedNight[name] ?: continue
                check(foundNight.add(name)) { "Lawnchair: duplicate $name in $nightPath" }
                val hex = color.textContent.trim().removePrefix("#").lowercase()
                check(hex.matches(Regex("[0-9a-f]{6}|[0-9a-f]{8}"))) {
                    "Lawnchair: invalid night color $name in $nightPath"
                }
                val normalized = if (hex.length == 6) "ff$hex" else hex
                check(normalized == original.removePrefix("#")) {
                    "Lawnchair: unexpected night color $name in $nightPath: ${color.textContent.trim()}"
                }
                color.textContent = "#FF000000"
            }
            check(foundNight == expectedNight.keys) {
                "Lawnchair: missing night colors in $nightPath: ${expectedNight.keys - foundNight}"
            }
        }

                // Lawnchair-specific Android 12+ backgrounds from the Termux XMLTREE
        // audit. Only selectors with verified single opaque background items.
        // Do not change text, icon, preview accent or switch colors.
        val auditedBackgroundSelectors = mapOf(
            "res/color-night-v31/all_apps_tabs_background.xml" to Pair("@0x01060027", "22"),
            "res/color-v31/all_apps_tabs_background.xml" to Pair("@0x01060020", null),
            "res/color-night-v31/folder_background_dark.xml" to Pair("@0x01060035", "12"),
            "res/color-night-v31/folder_preview_dark.xml" to Pair("@0x01060028", "12"),
            "res/color-v31/folder_background_light.xml" to Pair("@0x0106001f", "94"),
            "res/color-night-v31/popup_color_background.xml" to Pair("@0x01060028", "12"),
            "res/color-v31/popup_color_background.xml" to Pair("@0x0106001f", "94"),
            "res/color-v31/surface_light.xml" to Pair("@0x7f0604b3", "98"),
        )
        val androidNsForBackgrounds = "http://schemas.android.com/apk/res/android"
        for ((path, expectedAttributes) in auditedBackgroundSelectors) {
            document(path).use { doc ->
                val root = doc.documentElement
                check(root.localName == "selector" || root.nodeName == "selector") {
                    "Lawnchair: unexpected background selector root in $path"
                }
                val items = root.getElementsByTagName("item")
                check(items.length == 1 && root.getElementsByTagName("*").length == 1) {
                    "Lawnchair: expected one background selector item in $path"
                }
                val item = items.item(0) as Element
                val nsAware = item.hasAttributeNS(androidNsForBackgrounds, "color")
                val originalColor = if (nsAware) {
                    item.getAttributeNS(androidNsForBackgrounds, "color")
                } else {
                    item.getAttribute("android:color")
                }
                // Resource decoders may resolve the audited ID to its symbolic name.
                val expectedId = expectedAttributes.first
                val validColor = originalColor == expectedId ||
                    (expectedId == "@0x7f0604b3" &&
                     originalColor == "@color/system_neutral1_500")
                check(validColor) {
                    "Lawnchair: unexpected background reference in $path: $originalColor"
                }
                val originalLStar = if (nsAware) {
                    item.getAttributeNS(androidNsForBackgrounds, "lStar")
                } else {
                    item.getAttribute("android:lStar")
                }
                val expectedLStar = expectedAttributes.second
                check(if (expectedLStar == null) originalLStar.isEmpty()
                      else originalLStar.toFloatOrNull() == expectedLStar.toFloat()) {
                    "Lawnchair: unexpected background lStar in $path: $originalLStar"
                }
                check(item.attributes.length == if (expectedLStar == null) 1 else 2) {
                    "Lawnchair: unexpected background selector attributes in $path"
                }
                if (nsAware) {
                    item.setAttributeNS(androidNsForBackgrounds, "android:color", "#FF000000")
                    item.removeAttributeNS(androidNsForBackgrounds, "lStar")
                } else {
                    item.setAttribute("android:color", "#FF000000")
                    item.removeAttribute("android:lStar")
                }
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
                    // Some Morphe DOM decoders preserve the android: prefix but
                    // do not expose namespace-aware attributes.
                    val namespaceAware = item.hasAttributeNS(androidNs, "color")
                    val color = if (namespaceAware) {
                        item.getAttributeNS(androidNs, "color")
                    } else {
                        item.getAttribute("android:color")
                    }
                    check(color == "@android:color/system_neutral1_500" ||
                          color == "@0x01060024") {
                        "Lawnchair: unexpected dynamic color reference in $path: $color"
                    }
                    val lStarPresent = if (namespaceAware) {
                        item.hasAttributeNS(androidNs, "lStar")
                    } else {
                        item.hasAttribute("android:lStar")
                    }
                    val lStar = if (namespaceAware) {
                        item.getAttributeNS(androidNs, "lStar")
                    } else {
                        item.getAttribute("android:lStar")
                    }
                    check(lStarPresent &&
                          lStar.toFloatOrNull()?.let { it in 0f..100f } == true) {
                        "Lawnchair: missing or invalid lStar in $path: $lStar"
                    }
                    val expectedLStar = when (directory) {
                        "res/color-v31" -> mapOf(
                            "material_color_surface" to "98",
                            "material_color_surface_bright" to "98",
                            "material_color_surface_container" to "94",
                            "material_color_surface_container_high" to "92",
                            "material_color_surface_container_highest" to "90",
                            "material_color_surface_container_low" to "96",
                            "material_color_surface_container_lowest" to "100",
                            "material_color_surface_dim" to "87",
                            "material_color_surface_variant" to "87",
                        )[name]
                        "res/color-night-v31" -> mapOf(
                            "material_color_surface" to "6",
                            "material_color_surface_bright" to "24",
                            "material_color_surface_container" to "12",
                            "material_color_surface_container_high" to "17",
                            "material_color_surface_container_highest" to "22",
                            "material_color_surface_container_low" to "10",
                            "material_color_surface_container_lowest" to "4",
                            "material_color_surface_dim" to "6",
                            "material_color_surface_variant" to "6",
                        )[name]
                        else -> null
                    }
                    check(expectedLStar != null) {
                        "Lawnchair: no audited lStar for $path"
                    }
                    check(lStar.toFloatOrNull() == expectedLStar.toFloat()) {
                        "Lawnchair: unexpected lStar in $path: $lStar (expected $expectedLStar)"
                    }
                    check(item.attributes.length == 2) {
                        "Lawnchair: unexpected selector attributes in $path"
                    }
                    check(root.getElementsByTagName("*").length == 1) {
                        "Lawnchair: unexpected nested selector elements in $path"
                    }
                    if (namespaceAware) {
                        item.setAttributeNS(androidNs, "android:color", "#FF000000")
                        item.removeAttributeNS(androidNs, "lStar")
                    } else {
                        item.setAttribute("android:color", "#FF000000")
                        item.removeAttribute("android:lStar")
                    }
                }
            }
        }
    }
}
