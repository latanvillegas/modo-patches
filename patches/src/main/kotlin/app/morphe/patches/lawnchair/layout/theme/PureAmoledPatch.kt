package app.morphe.patches.lawnchair.layout.theme

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.findMutableMethodOf
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import org.w3c.dom.Element

// Independently selectable diagnostic patches for Lawnchair 15 Beta 3.
// Each patch validates its original resources before writing pure black.

@Suppress("unused")
val lawnchairAmoledMaterialBasePatch = resourcePatch(
    name = "Lawnchair - AMOLED Material base",
    description = "Verified default and night Material surfaces only.",
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
        val additionalOpaqueBackgrounds = emptyMap<String, String>()
        val baseExpected = expected

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

        
    }
}

@Suppress("unused")
val lawnchairAmoledMaterialDynamicPatch = resourcePatch(
    name = "Lawnchair - AMOLED Material dynamic",
    description = "Android 12+ Material surface selectors only.",
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

// DEX anchor verified against the original Lawnchair 15 Beta 3 APK.
// Override the drawer destination only; never mutate the shared t9 palette.
private val lawnchairAmoledDrawerDexPatch = bytecodePatch {
    compatibleWith("app.lawnchair"("15.Beta 3"))
    execute {
        val targetType = "Lcom/android/launcher3/allapps/ActivityAllAppsContainerView;"
        val fieldName = "mBottomSheetBackgroundColor"
        var matched = 0
        classDefForEach { classDef ->
            if (classDef.type != targetType) return@classDefForEach
            val mutableClass = mutableClassDefBy(classDef)
            classDef.methods.forEach { method ->
                val instructions = method.implementation?.instructions?.toList() ?: return@forEach
                for (index in 3 until instructions.size) {
                    val put = instructions[index]
                    if (put.opcode != Opcode.IPUT) continue
                    val field = (put as? ReferenceInstruction)?.reference as? FieldReference ?: continue
                    if (field.definingClass != targetType || field.name != fieldName || field.type != "I") continue
                    val move = instructions[index - 1]
                    val call = instructions[index - 2]
                    if (move.opcode != Opcode.MOVE_RESULT || call.opcode != Opcode.INVOKE_VIRTUAL) continue
                    val called = (call as? ReferenceInstruction)?.reference as? MethodReference ?: continue
                    if (called.definingClass != "Lt9/j;" || called.name != "e" ||
                        called.returnType != "I" || called.parameterTypes.map { it.toString() } != listOf("Landroid/content/Context;")) continue
                    val fieldRegister = (put as? TwoRegisterInstruction)?.registerA ?: continue
                    val resultRegister = (move as? OneRegisterInstruction)?.registerA ?: continue
                    if (fieldRegister != resultRegister) continue
                    // Verify this value originates from the audited H0 token.
                    val hasH0 = instructions.subList(0, index - 2).any { instruction ->
                        if (instruction.opcode != Opcode.SGET_OBJECT) false else {
                            val ref = (instruction as? ReferenceInstruction)?.reference as? FieldReference
                            ref?.definingClass == "Lt9/g;" && ref.name == "H0" && ref.type == "Lt9/j;"
                        }
                    }
                    if (!hasH0) continue
                    matched++
                    check(matched == 1) { "Lawnchair: ambiguous drawer background DEX anchor" }
                    mutableClass.findMutableMethodOf(method).replaceInstruction(
                        index - 1,
                        "const v$resultRegister, -0x1000000"
                    )
                }
            }
        }
        check(matched == 1) { "Lawnchair: expected one drawer background DEX anchor; found $matched" }
    }
}

@Suppress("unused")
val lawnchairAmoledAppDrawerPatch = resourcePatch(
    name = "Lawnchair - AMOLED App Drawer",
    description = "Audited pure-black backgrounds for this launcher area only.",
    default = false,
) {
    dependsOn(lawnchairAmoledDrawerDexPatch)
    compatibleWith("app.lawnchair"("15.Beta 3"))
    execute {
        val baseExpected = mapOf(
            "cardview_dark_background" to "#ff424242",
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

        val auditedBackgroundSelectors = mapOf(
            "res/color-night-v31/all_apps_tabs_background.xml" to Pair("@0x01060027", "22"),
            "res/color-v31/all_apps_tabs_background.xml" to Pair("@0x01060020", null),
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
                // Morphe's XML decoder can expand Android resource IDs to
                // symbolic references. Only allow exact audited equivalents.
                val auditedAndroidColors = mapOf(
                    "@0x01060027" to "@android:color/system_neutral1_800",
                    "@0x01060020" to "@android:color/system_neutral1_100",
                    "@0x01060035" to "@android:color/system_neutral2_900",
                    "@0x01060028" to "@android:color/system_neutral1_900",
                    "@0x0106001f" to "@android:color/system_neutral1_50",
                )
                val validColor = originalColor == expectedId ||
                    originalColor == auditedAndroidColors[expectedId] ||
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

    }
}

@Suppress("unused")
val lawnchairAmoledFoldersPatch = resourcePatch(
    name = "Lawnchair - AMOLED Folders",
    description = "Audited pure-black backgrounds for this launcher area only.",
    default = false,
) {
    compatibleWith("app.lawnchair"("15.Beta 3"))
    execute {
        val baseExpected = mapOf(
            "folder_background_dark" to "#ff1f2020",
            "folder_preview_dark" to "#ff1e1f20",
            "folder_background_light" to "#ffefeded",
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

        val auditedBackgroundSelectors = mapOf(
            "res/color-night-v31/folder_background_dark.xml" to Pair("@0x01060035", "12"),
            "res/color-night-v31/folder_preview_dark.xml" to Pair("@0x01060028", "12"),
            "res/color-v31/folder_background_light.xml" to Pair("@0x0106001f", "94"),
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
                // Morphe's XML decoder can expand Android resource IDs to
                // symbolic references. Only allow exact audited equivalents.
                val auditedAndroidColors = mapOf(
                    "@0x01060027" to "@android:color/system_neutral1_800",
                    "@0x01060020" to "@android:color/system_neutral1_100",
                    "@0x01060035" to "@android:color/system_neutral2_900",
                    "@0x01060028" to "@android:color/system_neutral1_900",
                    "@0x0106001f" to "@android:color/system_neutral1_50",
                )
                val validColor = originalColor == expectedId ||
                    originalColor == auditedAndroidColors[expectedId] ||
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

    }
}

@Suppress("unused")
val lawnchairAmoledPopupMenusPatch = resourcePatch(
    name = "Lawnchair - AMOLED Popup Menus",
    description = "Audited pure-black backgrounds for this launcher area only.",
    default = false,
) {
    compatibleWith("app.lawnchair"("15.Beta 3"))
    execute {

        // LauncherTheme declares literal popup colors. Editing color resources
        // alone cannot override these values.
        document("res/values/styles.xml").use { doc ->
            val expectedPopup = mapOf(
                "LauncherTheme" to "#efeded",
                "LauncherTheme.Dark" to "#1f2020",
            )
            val matched = mutableSetOf<String>()
            val styles = doc.getElementsByTagName("style")
            for (i in 0 until styles.length) {
                val style = styles.item(i) as? Element ?: continue
                val styleName = style.getAttribute("name")
                val expectedColor = expectedPopup[styleName] ?: continue
                val items = style.getElementsByTagName("item")
                val popupItems = (0 until items.length).mapNotNull { index ->
                    (items.item(index) as? Element)?.takeIf {
                        it.getAttribute("name") == "popupColorBackground"
                    }
                }
                check(popupItems.size == 1) {
                    "Lawnchair: expected one popupColorBackground in $styleName"
                }
                val item = popupItems.single()
                check(item.textContent.trim().lowercase() == expectedColor) {
                    "Lawnchair: unexpected popupColorBackground in $styleName: ${item.textContent.trim()}"
                }
                check(matched.add(styleName)) { "Lawnchair: duplicate style $styleName" }
                item.textContent = "#FF000000"
            }
            check(matched == expectedPopup.keys) {
                "Lawnchair: missing popup styles: ${expectedPopup.keys - matched}"
            }
        }
        val baseExpected = mapOf(
            "popup_shade_first_dark" to "#ff303030",
            "popup_shade_second_dark" to "#ff262626",
            "popup_shade_third_dark" to "#ff1b1b1b",
            "popup_shade_first_light" to "#fff9f9f9",
            "popup_shade_second_light" to "#fff1f1f1",
            "popup_shade_third_light" to "#ffe2e2e2",
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

        val auditedBackgroundSelectors = mapOf(
            "res/color-night-v31/popup_color_background.xml" to Pair("@0x01060028", "12"),
            "res/color-v31/popup_color_background.xml" to Pair("@0x0106001f", "94"),
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
                // Morphe's XML decoder can expand Android resource IDs to
                // symbolic references. Only allow exact audited equivalents.
                val auditedAndroidColors = mapOf(
                    "@0x01060027" to "@android:color/system_neutral1_800",
                    "@0x01060020" to "@android:color/system_neutral1_100",
                    "@0x01060035" to "@android:color/system_neutral2_900",
                    "@0x01060028" to "@android:color/system_neutral1_900",
                    "@0x0106001f" to "@android:color/system_neutral1_50",
                )
                val validColor = originalColor == expectedId ||
                    originalColor == auditedAndroidColors[expectedId] ||
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

    }
}

// Original Lawnchair 15 Beta 3: Snackbar.show(ActivityContext, CharSequence, int,
// Runnable, Runnable) obtains u9.n.j's rounded Drawable and assigns it to its layout.
// Tint that one Drawable only, preserving its XML corners and foreground colors.
@Suppress("unused")
val lawnchairAmoledSnackbarPatch = bytecodePatch(
    name = "Lawnchair - AMOLED Snackbar",
    description = "Tint only the Snackbar rounded background pure black.",
    default = false,
) {
    compatibleWith("app.lawnchair"("15.Beta 3"))
    execute {
        val target = "Lcom/android/launcher3/views/Snackbar;"
        var matches = 0
        classDefForEach { classDef ->
            if (classDef.type != target) return@classDefForEach
            val mutableClass = mutableClassDefBy(classDef)
            classDef.methods.forEach { method ->
                if (method.name != "show" || method.parameterTypes.map { it.toString() } != listOf(
                    "Lcom/android/launcher3/views/ActivityContext;",
                    "Ljava/lang/CharSequence;",
                    "I", "Ljava/lang/Runnable;", "Ljava/lang/Runnable;"
                )) return@forEach
                val instructions = method.implementation?.instructions?.toList() ?: return@forEach
                for (index in 2 until instructions.size - 1) {
                    val call = instructions[index - 1]
                    val move = instructions[index]
                    val assign = instructions[index + 1]
                    if (call.opcode != Opcode.INVOKE_VIRTUAL ||
                        move.opcode != Opcode.MOVE_RESULT_OBJECT ||
                        assign.opcode != Opcode.INVOKE_VIRTUAL) continue
                    val callRef = (call as? ReferenceInstruction)?.reference as? MethodReference ?: continue
                    val assignRef = (assign as? ReferenceInstruction)?.reference as? MethodReference ?: continue
                    if (callRef.definingClass != "Lu9/o;" || callRef.name != "g" ||
                        callRef.returnType != "Landroid/graphics/drawable/Drawable;" ||
                        assignRef.definingClass != "Landroid/widget/LinearLayout;" ||
                        assignRef.name != "setBackground") continue
                    val drawableRegister = (move as? OneRegisterInstruction)?.registerA ?: continue
                    // The original method uses v7 for u9.n.j and v3 for the
                    // resulting Drawable. Reusing v7 needs no extra registers.
                    check(drawableRegister == 3) { "Lawnchair: unexpected Snackbar drawable register" }
                    val assignRegisters = assign as? FiveRegisterInstruction
                        ?: error("Lawnchair: unexpected Snackbar setBackground instruction format")
                    check(assignRegisters.registerCount == 2 &&
                          assignRegisters.registerC == 1 &&
                          assignRegisters.registerD == drawableRegister) {
                        "Lawnchair: unexpected Snackbar setBackground registers"
                    }
                    val factoryCall = call as? FiveRegisterInstruction
                        ?: error("Lawnchair: unexpected Snackbar factory invocation format")
                    check(factoryCall.registerCount == 2 &&
                          factoryCall.registerC == 7 &&
                          factoryCall.registerD == 3) {
                        "Lawnchair: unexpected Snackbar factory registers"
                    }
                    val hasFactory = instructions.subList(0, index - 1).any { ins ->
                        val ref = (ins as? ReferenceInstruction)?.reference as? FieldReference
                        ins.opcode == Opcode.SGET_OBJECT && ref?.definingClass == "Lu9/n;" &&
                            ref.name == "j" && ref.type == "Lu9/o;"
                    }
                    check(hasFactory) { "Lawnchair: Snackbar factory not found" }
                    matches++
                    check(matches == 1) { "Lawnchair: ambiguous Snackbar background" }
                    // Avoid modifying a potentially shared Drawable constant state.
                    // mutate() returns the isolated drawable in v3 before tinting.
                    mutableClass.findMutableMethodOf(method).addInstructions(
                        index + 1,
                        """
                        invoke-virtual {v3}, Landroid/graphics/drawable/Drawable;->mutate()Landroid/graphics/drawable/Drawable;
                        move-result-object v3
                        const v7, -0x1000000
                        invoke-virtual {v3, v7}, Landroid/graphics/drawable/Drawable;->setTint(I)V
                        """.trimIndent()
                    )
                }
            }
        }
        check(matches == 1) { "Lawnchair: expected one Snackbar background; found $matches" }
    }
}

// CardView has its own background attribute; Material surface resource
// overrides do not cover android:colorBackgroundFloating.
@Suppress("unused")
val lawnchairAmoledCardsPatch = resourcePatch(
    name = "Lawnchair - AMOLED CardView backgrounds",
    description = "Set only audited CardView background style items to OLED black.",
    default = false,
) {
    compatibleWith("app.lawnchair"("15.Beta 3"))
    execute {
        val expected = mapOf(
            "CardView" to "?android:attr/colorBackgroundFloating",
            "Widget.MaterialComponents.CardView" to "?colorSurface",
        )
        document("res/values/styles.xml").use { doc ->
            val found = mutableSetOf<String>()
            val styles = doc.getElementsByTagName("style")
            for (i in 0 until styles.length) {
                val style = styles.item(i) as? Element ?: continue
                val name = style.getAttribute("name")
                val original = expected[name] ?: continue
                val items = style.getElementsByTagName("item")
                val matches = (0 until items.length).mapNotNull { j ->
                    (items.item(j) as? Element)?.takeIf {
                        it.getAttribute("name") == "cardBackgroundColor"
                    }
                }
                check(matches.size == 1) { "Lawnchair: ambiguous CardView background in $name" }
                val item = matches.single()
                check(item.textContent.trim() == original) {
                    "Lawnchair: unexpected CardView background in $name: ${item.textContent.trim()}"
                }
                check(found.add(name)) { "Lawnchair: duplicate CardView style $name" }
                item.textContent = "#FF000000"
            }
            check(found == expected.keys) {
                "Lawnchair: missing CardView styles: ${expected.keys - found}"
            }
        }
    }
}

// This overlay drawable layers an 11%-alpha elevation tint over ?colorSurface.
// Replacing only the base surface leaves the second gray overlay visible.
@Suppress("unused")
val lawnchairAmoledPopupOverlayPatch = resourcePatch(
    name = "Lawnchair - AMOLED Popup overlay",
    description = "Blacken both layers of the audited Material popup overlay drawable.",
    default = false,
) {
    compatibleWith("app.lawnchair"("15.Beta 3"))
    execute {
        val path = "res/drawable/mtrl_popupmenu_background_overlay.xml"
        document(path).use { doc ->
            val root = doc.documentElement
            check(root.tagName == "layer-list") { "Lawnchair: unexpected popup overlay root" }
            val items = root.getElementsByTagName("item")
            check(items.length == 2) { "Lawnchair: expected two popup overlay layers" }
            val expected = listOf("?colorSurface", "@color/mtrl_popupmenu_overlay_color")
            for (i in 0 until 2) {
                val item = items.item(i) as Element
                val shapes = item.getElementsByTagName("shape")
                check(shapes.length == 1) { "Lawnchair: unexpected shape in popup overlay layer $i" }
                val solids = (shapes.item(0) as Element).getElementsByTagName("solid")
                check(solids.length == 1) { "Lawnchair: unexpected solid in popup overlay layer $i" }
                val solid = solids.item(0) as Element
                val actual = solid.getAttributeNS("http://schemas.android.com/apk/res/android", "color")
                    .ifEmpty { solid.getAttribute("android:color") }
                check(actual == expected[i]) {
                    "Lawnchair: unexpected popup overlay layer $i: $actual"
                }
                solid.setAttributeNS(
                    "http://schemas.android.com/apk/res/android",
                    "android:color",
                    "#FF000000"
                )
            }
        }
    }
}

// The Material dialog background is an inset drawable with rounded corners.
// Change only its solid fill; preserve the inset and corner dimensions.
@Suppress("unused")
val lawnchairAmoledDialogBackgroundPatch = resourcePatch(
    name = "Lawnchair - AMOLED Dialog background",
    description = "Blacken the audited Material dialog background without altering its geometry.",
    default = false,
) {
    compatibleWith("app.lawnchair"("15.Beta 3"))
    execute {
        val path = "res/drawable/mtrl_dialog_background.xml"
        document(path).use { doc ->
            val root = doc.documentElement
            check(root.tagName == "inset") { "Lawnchair: unexpected dialog background root" }
            val shapes = root.getElementsByTagName("shape")
            check(shapes.length == 1) { "Lawnchair: expected one dialog shape" }
            val shape = shapes.item(0) as Element
            val corners = shape.getElementsByTagName("corners")
            check(corners.length == 1) { "Lawnchair: missing dialog corners" }
            val solids = shape.getElementsByTagName("solid")
            check(solids.length == 1) { "Lawnchair: expected one dialog solid" }
            val solid = solids.item(0) as Element
            val ns = "http://schemas.android.com/apk/res/android"
            val actual = solid.getAttributeNS(ns, "color")
                .ifEmpty { solid.getAttribute("android:color") }
            check(actual == "?colorSurface") {
                "Lawnchair: unexpected dialog background color: $actual"
            }
            solid.setAttributeNS(ns, "android:color", "#FF000000")
        }
    }
}

// Target only ArrowTip backgrounds. Preserve text and taskbar foregrounds.
@Suppress("unused")
val lawnchairAmoledArrowTipsPatch = resourcePatch(
    name = "Lawnchair - AMOLED ArrowTip backgrounds",
    description = "Pure-black ArrowTip and taskbar tooltip backgrounds without altering text.",
    default = false,
) {
    compatibleWith("app.lawnchair"("15.Beta 3"))
    execute {
        val expected = mapOf(
            "ArrowTipStyle" to "@color/arrow_tip_view_bg",
            "ArrowTipTaskbarStyle" to "?attr/materialColorSurfaceContainer",
        )
        document("res/values/styles.xml").use { doc ->
            val matched = mutableSetOf<String>()
            val styles = doc.getElementsByTagName("style")
            for (i in 0 until styles.length) {
                val style = styles.item(i) as? Element ?: continue
                val name = style.getAttribute("name")
                val original = expected[name] ?: continue
                val items = style.getElementsByTagName("item")
                val targets = (0 until items.length).mapNotNull { j ->
                    (items.item(j) as? Element)?.takeIf {
                        it.getAttribute("name") == "arrowTipBackground"
                    }
                }
                check(targets.size == 1) { "Lawnchair: ambiguous arrowTipBackground in $name" }
                val item = targets.single()
                check(item.textContent.trim() == original) {
                    "Lawnchair: unexpected arrowTipBackground in $name: ${item.textContent.trim()}"
                }
                check(matched.add(name)) { "Lawnchair: duplicate ArrowTip style $name" }
                item.textContent = "#FF000000"
            }
            check(matched == expected.keys) {
                "Lawnchair: missing ArrowTip styles: ${expected.keys - matched}"
            }
        }
    }
}

val lawnchairAmoledSettingsHeaderPatch = resourcePatch(
    name = "Lawnchair - AMOLED Settings header",
    description = "Blackens verified Android 12+ Home Settings header colors; leaves titles, icons, accents and switches unchanged.",
    default = false,
) {
    compatibleWith("app.lawnchair"("15.Beta 3"))
    execute {
        // The Settings page background is opaque white in the default
        // configuration and already pure black in the night configuration.
        // Do not alter its text, accents, or switch colors.
        document("res/values/colors.xml").use { doc ->
            val colors = doc.getElementsByTagName("color")
            var found = false
            for (i in 0 until colors.length) {
                val color = colors.item(i) as? Element ?: continue
                if (color.getAttribute("name") != "all_set_page_background") continue
                check(!found) { "Lawnchair: duplicate all_set_page_background" }
                val original = color.textContent.trim().lowercase()
                check(original == "#ffffffff" || original == "#ffffff") {
                    "Lawnchair: unexpected all_set_page_background: ${color.textContent.trim()}"
                }
                color.textContent = "#FF000000"
                found = true
            }
            check(found) { "Lawnchair: missing all_set_page_background" }
        }

        val expected = mapOf(
            "res/values-v31/colors.xml" to mapOf(
                "home_settings_header_collapsed" to "@android:color/system_neutral1_100",
                "home_settings_header_expanded" to "@android:color/system_neutral1_50",
            ),
            "res/values-night-v31/colors.xml" to mapOf(
                "home_settings_header_collapsed" to "@android:color/system_neutral1_700",
                "home_settings_header_expanded" to "@android:color/system_neutral1_900",
            ),
        )
        val numericEquivalents = mapOf(
            "@android:color/system_neutral1_100" to "@0x01060020",
            "@android:color/system_neutral1_50" to "@0x0106001f",
            "@android:color/system_neutral1_700" to "@0x01060026",
            "@android:color/system_neutral1_900" to "@0x01060028",
        )
        for ((path, colorsExpected) in expected) {
            document(path).use { doc ->
                val found = mutableSetOf<String>()
                val colors = doc.getElementsByTagName("color")
                for (i in 0 until colors.length) {
                    val color = colors.item(i) as? Element ?: continue
                    val name = color.getAttribute("name")
                    val reference = colorsExpected[name] ?: continue
                    check(found.add(name)) {
                        "Lawnchair: duplicate Settings color $name in $path"
                    }
                    val actual = color.textContent.trim()
                    check(actual == reference || actual == numericEquivalents[reference]) {
                        "Lawnchair: unexpected Settings color $name in $path: $actual"
                    }
                    color.textContent = "#FF000000"
                }
                check(found == colorsExpected.keys) {
                    "Lawnchair: missing Settings header colors in $path: ${colorsExpected.keys - found}"
                }
            }
        }
    }
}
