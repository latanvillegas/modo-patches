package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
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
    ),
)

private val pureAmoledAppMenuResourcesPatch = resourcePatch(
    description = "Scoped AppMenu, popup and native list-menu resources for the selectable Cromite AMOLED palette.",
) {
    compatibleWith("org.cromite.cromite"(APP_MENU_VERSION))
    val browserTheme by browserThemeOption()

    execute {
        val selectedTheme = browserTheme ?: "device"
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
            if (instructionMatches.size != 7) {
                throw IllegalStateException(
                    "AMOLED AppMenu bytecode verification failed for Cromite $APP_MENU_VERSION: " +
                        "expected 7 structural anchors, found ${instructionMatches.size}."
                )
            }

            val preAnchorIndex = instructionMatches[0].index
            val inflateIndex = instructionMatches[1].index
            val semanticIndex = instructionMatches[2].index
            val tintIndex = instructionMatches[4].index
            val backgroundIndex = instructionMatches[6].index

            if (!(preAnchorIndex < inflateIndex &&
                    inflateIndex < semanticIndex &&
                    semanticIndex < tintIndex &&
                    tintIndex < backgroundIndex)) {
                throw IllegalStateException(
                    "AMOLED AppMenu bytecode verification failed for Cromite $APP_MENU_VERSION: " +
                        "structural anchors are out of order."
                )
            }

            val semanticResult = method.getInstruction<OneRegisterInstruction>(semanticIndex + 2)
            if (semanticResult.opcode != Opcode.MOVE_RESULT) {
                throw IllegalStateException(
                    "AMOLED AppMenu bytecode verification failed for Cromite $APP_MENU_VERSION: " +
                        "Lis7.f() is not followed by move-result."
                )
            }

            val colorRegister = semanticResult.registerA
            val targetColorSmali = when (selectedTheme) {
                "black" -> "const v$colorRegister, -0x1000000"
                "white" -> "const v$colorRegister, -0x1"
                else -> """
                    and-int/lit32 v$colorRegister, v$colorRegister, 0x00ffffff
                    ushr-int/lit8 v$colorRegister, v$colorRegister, 0x17
                    neg-int v$colorRegister, v$colorRegister
                    or-int/lit32 v$colorRegister, v$colorRegister, 0xff000000
                """.trimIndent()
            }

            // Reuse the semantic color register already produced by Lis7.f().
            // This avoids introducing any move-result or object scratch register into
            // x50.l(), while preserving the original Drawable/tint/background chain.
            method.addInstructions(
                semanticIndex + 3,
                targetColorSmali,
            )
        }
    }
}
