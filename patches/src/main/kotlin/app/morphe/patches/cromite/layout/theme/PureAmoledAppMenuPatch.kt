package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import org.w3c.dom.Element
import org.w3c.dom.Node

private const val APP_MENU_VERSION = "153.0.8010.37"
private const val APP_MENU_BLACK = "#FF000000"
private const val APP_MENU_WHITE = "#FFFFFFFF"


/**
 * Chromium AppMenu visible-surface runtime owner.
 *
 * Requires the exact setTint -> setTintMode -> View.setBackground chain that
 * Cromite 153 uses to paint the visible AppMenu surface.
 */
private val appMenuVisibleSurfaceFingerprint = Fingerprint(
    filters = listOf(
        // Unique pre-anchor of the real AppMenu PopupWindow owner.
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, smali = "Landroid/widget/PopupWindow;->setBackgroundDrawable(Landroid/graphics/drawable/Drawable;)V"),
        methodCall(opcode = Opcode.INVOKE_STATIC, smali = "Lf50;->b(Landroid/view/ContextThemeWrapper;Z)Landroid/view/ViewGroup;"),
        // Unique post-anchor chain for the visible AppMenu Nine-Patch.
        methodCall(opcode = Opcode.INVOKE_STATIC, smali = "Ldr7;->e(Landroid/content/Context;ILjava/lang/String;)Landroid/util/TypedValue;"),
        methodCall(opcode = Opcode.INVOKE_STATIC, smali = "Lis7;->f(Landroid/content/Context;Landroid/util/TypedValue;)I"),
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, smali = "Landroid/graphics/drawable/Drawable;->setTint(I)V"),
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, smali = "Landroid/graphics/drawable/Drawable;->setTintMode(Landroid/graphics/PorterDuff\$Mode;)V"),
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, smali = "Landroid/view/View;->setBackground(Landroid/graphics/drawable/Drawable;)V"),
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, smali = "Landroid/view/View;->findViewById(I)Landroid/view/View;"),
    ),
)

private val pureAmoledAppMenuResourcesPatch = resourcePatch(
    description = "Scoped AppMenu, popup and native list-menu resources for the selectable Cromite AMOLED palette.",
) {
    compatibleWith("org.cromite.cromite"(APP_MENU_VERSION))
    val browserTheme by browserThemeOption()

    execute {
        val selectedTheme = browserTheme ?: "device"

        // The visible AppMenu layout has a second themed background on
        // app_menu_content_container. It sits above the Nine-Patch applied to
        // the root by x50.l(), so leaving it opaque hides the AMOLED tint.
        // Make only that inner container transparent; the root Nine-Patch
        // remains responsible for shape, shadow, padding and runtime color.
        var contentContainerHits = 0
        val layoutDirectories = get("res").listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("layout") }
            .orEmpty()

        layoutDirectories.forEach { directory ->
            directory.listFiles()
                ?.filter { it.isFile && it.extension == "xml" }
                .orEmpty()
                .forEach { file ->
                    document(file.absolutePath).use { document ->
                            val nodes = document.getElementsByTagName("*")
                            for (index in 0 until nodes.length) {
                                val element = nodes.item(index) as? Element ?: continue
                                val id = element.getAttribute("android:id")
                                if (id != "@id/app_menu_content_container" &&
                                    id != "@+id/app_menu_content_container") continue
                                if (!element.hasAttribute("android:background")) {
                                    throw IllegalStateException(
                                        "AMOLED AppMenu layout verification failed for Cromite $APP_MENU_VERSION: " +
                                            "app_menu_content_container has no android:background."
                                    )
                                }
                                val root = document.documentElement
                                if (root.tagName != "FrameLayout" ||
                                    !root.hasAttribute("android:background")) {
                                    throw IllegalStateException(
                                        "AMOLED AppMenu layout verification failed for Cromite $APP_MENU_VERSION: " +
                                            "app_menu_content_container is not under the expected root FrameLayout with a background."
                                    )
                                }

                                // The exact decoded attribute spelling can differ
                                // between apktool diagnostics and Morphe's resource
                                // representation. The child ID uniquely identifies
                                // this AppMenu layout; verify only the structural
                                // owner here, then neutralize its local background.
                                root.setAttribute("android:background", "@android:color/transparent")

                                // Runtime #285 proved this child is the visible AppMenu interior.
                                // Keep XML neutral; x50.l() assigns the opaque runtime color.
                                element.setAttribute("android:background", "@android:color/transparent")
                                contentContainerHits++
                        }
                    }
                }
        }

        if (contentContainerHits != 1) {
            throw IllegalStateException(
                "AMOLED AppMenu layout verification failed for Cromite $APP_MENU_VERSION: " +
                    "expected exactly 1 app_menu_content_container, found $contentContainerHits."
            )
        }

        if (selectedTheme == "device") return@execute

        val target = when (selectedTheme) {
            "black" -> APP_MENU_BLACK
            "white" -> APP_MENU_WHITE
            else -> return@execute
        }

        val exactColorFiles = setOf(
            "menu_item_bg_color.xml",
            "menu_item_bg_color_baseline.xml",
            "menu_item_bg_color_dark_baseline.xml",
            "menu_bg_color.xml",
            "menu_bg_color_baseline.xml",
            "menu_bg_color_dark_baseline.xml",
            "list_menu_background_color.xml",
            "list_menu_background_tint.xml",
            "mtrl_popupmenu_overlay_color.xml",
        )
        val exactDrawableFiles = setOf(
            "popup_bg.xml",
            "popup_bg_8dp.xml",
            "popup_bg_24dp.xml",
            "popup_bg_shape_16dp.xml",
            "popup_bg_shape_24dp.xml",
            "popup_bg_shape_24dp_baseline.xml",
            "list_menu_background.xml",
            "menu_bg_tinted.xml",
        )
        val exactValueNames = setOf(
            "menu_item_bg_color",
            "menu_item_bg_color_baseline",
            "menu_item_bg_color_dark_baseline",
            "menu_bg_color",
            "menu_bg_color_baseline",
            "menu_bg_color_dark_baseline",
            "list_menu_background_color",
            "list_menu_background_tint",
            "mtrl_popupmenu_overlay_color",
        )

        val resourceDirectories = get("res").listFiles()
            ?.filter { it.isDirectory }
            .orEmpty()
        val colorDirectories = resourceDirectories.filter { it.name.startsWith("color") }
        val drawableDirectories = resourceDirectories.filter { it.name.startsWith("drawable") }
        val valueDirectories = resourceDirectories.filter { it.name.startsWith("values") }

        var hits = 0

        fun recolor(element: Element) {
            val attributes = element.attributes
            for (index in 0 until attributes.length) {
                val attribute = attributes.item(index)
                if (attribute.nodeName.endsWith(":color") || attribute.nodeName == "color" ||
                    attribute.nodeName.endsWith(":tint") || attribute.nodeName == "tint") {
                    attribute.nodeValue = target
                    hits++
                }
            }
            val children = element.childNodes
            var hasElementChildren = false
            for (index in 0 until children.length) {
                val child = children.item(index)
                if (child.nodeType == Node.ELEMENT_NODE) {
                    hasElementChildren = true
                    recolor(child as Element)
                }
            }
            if (!hasElementChildren && element.textContent.trim().startsWith("#")) {
                element.textContent = target
                hits++
            }
        }

        colorDirectories.forEach { directory ->
            exactColorFiles.forEach { fileName ->
                val file = java.io.File(directory, fileName)
                if (!file.isFile) return@forEach
                document(file.absolutePath).use { document ->
                    recolor(document.documentElement)
                }
            }
        }

        drawableDirectories.forEach { directory ->
            exactDrawableFiles.forEach { fileName ->
                val file = java.io.File(directory, fileName)
                if (!file.isFile) return@forEach
                document(file.absolutePath).use { document ->
                    recolor(document.documentElement)
                }
            }
        }

        valueDirectories.forEach { directory ->
            directory.listFiles()
                ?.filter { it.isFile && it.extension == "xml" }
                .orEmpty()
                .forEach { file ->
                    runCatching {
                        document(file.absolutePath).use { document ->
                            val nodes = document.getElementsByTagName("*")
                            for (index in 0 until nodes.length) {
                                val element = nodes.item(index) as? Element ?: continue
                                val name = element.getAttribute("name")
                                val type = element.getAttribute("type")
                                if (name !in exactValueNames) continue
                                if (element.tagName == "color" ||
                                    element.tagName == "macro" ||
                                    (element.tagName == "item" && type == "color")) {
                                    element.textContent = target
                                    hits++
                                }
                            }
                        }
                    }
                }
        }

        if (hits == 0) {
            throw IllegalStateException(
                "AMOLED AppMenu resource verification failed for Cromite $APP_MENU_VERSION: no whitelisted menu/popup resources were found."
            )
        }
    }
}

@Suppress("unused")
val pureAmoledAppMenuPatch = bytecodePatch(
    name = "AMOLED - App menu and popups",
    description = "Applies Device default / Pure Black / Pure White only to Cromite AppMenu and its popup surface.",
) {
    compatibleWith("org.cromite.cromite"(APP_MENU_VERSION))
    dependsOn(pureAmoledAppMenuResourcesPatch)
    val browserTheme by browserThemeOption()

    execute {
        val selectedTheme = browserTheme ?: "device"

        appMenuVisibleSurfaceFingerprint.apply {
            if (instructionMatches.size != 8) {
                throw IllegalStateException(
                    "AMOLED AppMenu bytecode verification failed for Cromite $APP_MENU_VERSION: " +
                        "expected 8 structural anchors, found ${instructionMatches.size}."
                )
            }

            val preAnchorIndex = instructionMatches[0].index
            val inflateIndex = instructionMatches[1].index
            val semanticIndex = instructionMatches[2].index
            val tintIndex = instructionMatches[4].index
            val tintModeIndex = instructionMatches[5].index
            val backgroundIndex = instructionMatches[6].index
            val contentLookupIndex = instructionMatches[7].index

            if (!(preAnchorIndex < inflateIndex &&
                    inflateIndex < semanticIndex &&
                    semanticIndex < tintIndex &&
                    tintIndex < tintModeIndex &&
                    tintModeIndex < backgroundIndex &&
                    backgroundIndex < contentLookupIndex)) {
                throw IllegalStateException(
                    "AMOLED AppMenu bytecode verification failed for Cromite $APP_MENU_VERSION: " +
                        "structural anchors are out of order."
                )
            }

            // Anchor directly on Drawable.setTint(I). In Cromite 153 this is
            // invoke-virtual {v12, v10}, so registerD is the tint color consumed by
            // the visible AppMenu drawable. This avoids all assumptions about the
            // Lis7.f() move-result position.
            val tintInstruction = method.getInstruction(tintIndex)
            if (tintInstruction.opcode != Opcode.INVOKE_VIRTUAL ||
                tintInstruction !is FiveRegisterInstruction ||
                tintInstruction.registerCount != 2) {
                throw IllegalStateException(
                    "AMOLED AppMenu bytecode verification failed for Cromite $APP_MENU_VERSION: " +
                        "Drawable.setTint(I) is not the expected two-register invoke-virtual."
                )
            }

            val colorRegister = tintInstruction.registerD

            // Cromite applies PorterDuff.MULTIPLY after setTint(). Runtime #280
            // proved that this leaves the visible Nine-Patch surface gray even
            // when q72.b() supplies #FF000000. Keep the original Nine-Patch
            // (shape, alpha, padding and shadow), but force SRC_IN for the tint
            // composition so the opaque white panel pixels become the requested
            // AMOLED color while retaining the drawable alpha mask.
            val tintModeInstruction = method.getInstruction(tintModeIndex)
            if (tintModeInstruction.opcode != Opcode.INVOKE_VIRTUAL ||
                tintModeInstruction !is FiveRegisterInstruction ||
                tintModeInstruction.registerCount != 2) {
                throw IllegalStateException(
                    "AMOLED AppMenu bytecode verification failed for Cromite $APP_MENU_VERSION: " +
                        "Drawable.setTintMode(PorterDuff.Mode) is not the expected two-register invoke-virtual."
                )
            }
            val tintModeRegister = tintModeInstruction.registerD

            val targetColorSmali = when (selectedTheme) {
                "black" -> "const v$colorRegister, -0x1000000"
                "white" -> "const v$colorRegister, -0x1"
                else -> """
                    const/4 v$colorRegister, 0x0
                    invoke-static {v1, v$colorRegister}, Lq72;->b(Landroid/content/Context;Z)I
                    move-result v$colorRegister
                """.trimIndent()
            }

            // Override only the tint argument immediately before setTint().
            method.addInstructions(
                tintIndex,
                targetColorSmali,
            )

            // Override Cromite's MULTIPLY mode immediately before setTintMode().
            // Account for the instructions inserted before setTint() when
            // addressing the original setTintMode anchor.
            val insertedInstructionCount = when (selectedTheme) {
                "black", "white" -> 1
                else -> 3
            }
            method.addInstructions(
                tintModeIndex + insertedInstructionCount,
                "sget-object v$tintModeRegister, Landroid/graphics/PorterDuff\$Mode;->SRC_IN:Landroid/graphics/PorterDuff\$Mode;",
            )

            // Clean Cromite 153 DEX proves that the first lookup after the
            // Nine-Patch background is app_menu_content_container:
            // const v10, 0x7f010101 -> findViewById -> move-result-object v12.
            // Verify that exact structure before injecting anything.
            val contentLookupShift = insertedInstructionCount + 1
            val shiftedContentLookupIndex = contentLookupIndex + contentLookupShift
            val contentIdInstruction = method.getInstruction(shiftedContentLookupIndex - 1)
            val contentLookupInstruction = method.getInstruction(shiftedContentLookupIndex)
            val contentResultInstruction = method.getInstruction(shiftedContentLookupIndex + 1)

            if (contentIdInstruction !is OneRegisterInstruction ||
                contentIdInstruction !is WideLiteralInstruction ||
                contentIdInstruction.wideLiteral != 0x7f010101L ||
                contentLookupInstruction.opcode != Opcode.INVOKE_VIRTUAL ||
                contentLookupInstruction !is FiveRegisterInstruction ||
                contentLookupInstruction.registerCount != 2 ||
                contentLookupInstruction.registerD != contentIdInstruction.registerA ||
                contentResultInstruction.opcode != Opcode.MOVE_RESULT_OBJECT ||
                contentResultInstruction !is OneRegisterInstruction) {
                throw IllegalStateException(
                    "AMOLED AppMenu bytecode verification failed for Cromite $APP_MENU_VERSION: " +
                        "app_menu_content_container lookup differs from the verified DEX."
                )
            }

            val contentIdRegister = contentIdInstruction.registerA
            val contentViewRegister = contentResultInstruction.registerA
            val contentColorSmali = when (selectedTheme) {
                "black" -> "const v$contentIdRegister, -0x1000000"
                "white" -> "const v$contentIdRegister, -0x1"
                else -> """
                    move-object/from16 v14, p1
                    const/4 v$contentIdRegister, 0x0
                    invoke-static {v14, v$contentIdRegister}, Lq72;->b(Landroid/content/Context;Z)I
                    move-result v$contentIdRegister
                """.trimIndent()
            }

            // v10/ID is dead after the lookup. v14 is dead at :cond_b and is
            // overwritten by Cromite before its next use; it only bridges p1
            // into an encodable low Context register for q72.b().
            method.addInstructions(
                shiftedContentLookupIndex + 2,
                """
                    $contentColorSmali
                    invoke-virtual {v$contentViewRegister, v$contentIdRegister}, Landroid/view/View;->setBackgroundColor(I)V
                """.trimIndent(),
            )

        }
    }
}
