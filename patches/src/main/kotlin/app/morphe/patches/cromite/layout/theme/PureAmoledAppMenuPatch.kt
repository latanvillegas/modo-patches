package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import org.w3c.dom.Element
import org.w3c.dom.Node

private const val APP_MENU_VERSION = "153.0.8010.37"
private const val APP_MENU_BLACK = "#FF000000"
private const val APP_MENU_WHITE = "#FFFFFFFF"


/**
 * Chromium AppMenu-specific runtime owner.
 *
 * The matched method configures the same PopupWindow with its background Drawable and
 * then installs the inflated AppMenu root View. Requiring both calls avoids matching
 * BasicListMenu globally, which is also used by unrelated native popups.
 */
private val appMenuPopupContentFingerprint = Fingerprint(
    returnType = "V",
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            smali = "Landroid/widget/PopupWindow;->setBackgroundDrawable(Landroid/graphics/drawable/Drawable;)V",
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            smali = "Landroid/widget/PopupWindow;->setContentView(Landroid/view/View;)V",
        ),
    ),
)

private fun appMenuSurfaceSmali(theme: String, viewRegister: Int, scratchRegister: Int): String = when (theme) {
    "black" -> """
        const v$scratchRegister, -0x1000000
        invoke-virtual { v$viewRegister, v$scratchRegister }, Landroid/view/View;->setBackgroundColor(I)V
    """
    "white" -> """
        const v$scratchRegister, -0x1
        invoke-virtual { v$viewRegister, v$scratchRegister }, Landroid/view/View;->setBackgroundColor(I)V
    """
    else -> """
        invoke-virtual { v$viewRegister }, Landroid/view/View;->getContext()Landroid/content/Context;
        move-result-object v$scratchRegister
        invoke-virtual { v$scratchRegister }, Landroid/content/Context;->getResources()Landroid/content/res/Resources;
        move-result-object v$scratchRegister
        invoke-virtual { v$scratchRegister }, Landroid/content/res/Resources;->getConfiguration()Landroid/content/res/Configuration;
        move-result-object v$scratchRegister
        iget v$scratchRegister, v$scratchRegister, Landroid/content/res/Configuration;->uiMode:I
        and-int/lit8 v$scratchRegister, v$scratchRegister, 0x30
        xor-int/lit8 v$scratchRegister, v$scratchRegister, 0x20
        if-nez v$scratchRegister, :amoled_app_menu_light
        const v$scratchRegister, -0x1000000
        goto :amoled_app_menu_apply
        :amoled_app_menu_light
        const v$scratchRegister, -0x1
        :amoled_app_menu_apply
        invoke-virtual { v$viewRegister, v$scratchRegister }, Landroid/view/View;->setBackgroundColor(I)V
    """
}

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

        appMenuPopupContentFingerprint.apply {
            val backgroundIndex = instructionMatches[0].index
            val contentIndex = instructionMatches[1].index
            val backgroundCall = method.getInstruction<FiveRegisterInstruction>(backgroundIndex)
            val contentCall = method.getInstruction<FiveRegisterInstruction>(contentIndex)

            // The Drawable argument has already been consumed before contentIndex, so its
            // register is safe scratch space. No register-count change means parameter
            // registers and the R8-optimized method layout remain intact.
            val scratchRegister = backgroundCall.registerD
            val contentViewRegister = contentCall.registerD

            method.addInstructions(
                contentIndex,
                appMenuSurfaceSmali(selectedTheme, contentViewRegister, scratchRegister),
            )
        }
    }
}
