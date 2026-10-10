package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.Opcode
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Partial source reconstruction from PureAmoledAppMenuPatchKt.class (1.50.0).
 * Restores the original resource allowlists and bytecode fingerprint anchors.
 * The original eight-anchor bytecode mutations still require reconstruction.
 */
private val appMenuVisibleSurfaceFingerprint = Fingerprint(
    filters = listOf(
        methodCall("Landroid/widget/PopupWindow;->setBackgroundDrawable(Landroid/graphics/drawable/Drawable;)V", Opcode.INVOKE_VIRTUAL),
        methodCall("Lf50;->b(Landroid/view/ContextThemeWrapper;Z)Landroid/view/ViewGroup;", Opcode.INVOKE_STATIC),
        methodCall("Ldr7;->e(Landroid/content/Context;ILjava/lang/String;)Landroid/util/TypedValue;", Opcode.INVOKE_STATIC),
        methodCall("Lis7;->f(Landroid/content/Context;Landroid/util/TypedValue;)I", Opcode.INVOKE_STATIC),
        methodCall("Landroid/graphics/drawable/Drawable;->setTint(I)V", Opcode.INVOKE_VIRTUAL),
        methodCall("Landroid/graphics/drawable/Drawable;->setTintMode(Landroid/graphics/PorterDuff\$Mode;)V", Opcode.INVOKE_VIRTUAL),
        methodCall("Landroid/view/View;->setBackground(Landroid/graphics/drawable/Drawable;)V", Opcode.INVOKE_VIRTUAL),
        methodCall("Landroid/view/View;->setBackgroundColor(I)V", Opcode.INVOKE_VIRTUAL),
    ),
)

private val appMenuColorFiles = setOf(
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

private val appMenuDrawableFiles = setOf(
    "list_menu_background.xml",
    "menu_bg_tinted.xml",
)

private val appMenuResourceNames = appMenuColorFiles.map { it.removeSuffix(".xml") }.toSet()

private val pureAmoledAppMenuResourcesPatch = resourcePatch(
    name = "AMOLED - App menu resources",
    description = "Restore original AppMenu resource colors and drawable tinting.",
) {
    compatibleWith("org.cromite.cromite"("153.0.8010.37"))
    val browserTheme = recoveredBrowserThemeOption

    execute {
        val color = when (browserTheme.value ?: "device") {
            "device" -> return@execute
            "black" -> "#FF000000"
            "white" -> "#FFFFFFFF"
            else -> return@execute
        }
        var changed = 0
        val directories = get("res").listFiles()?.filter { it.isDirectory }.orEmpty()

        directories.filter { it.name.startsWith("color") || it.name.startsWith("drawable") }
            .forEach { directory ->
                val allowlist = if (directory.name.startsWith("color")) appMenuColorFiles else appMenuDrawableFiles
                allowlist.forEach { filename ->
                    val path = "res/${directory.name}/$filename"
                    if (!get(path).isFile) return@forEach
                    document(path).use { xml ->
                        val elements = xml.getElementsByTagName("*")
                        for (i in 0 until elements.length) {
                            val element = elements.item(i) as? Element ?: continue
                            val attrs = element.attributes
                            for (j in 0 until attrs.length) {
                                val attr = attrs.item(j)
                                if (attr.nodeName == "color" || attr.nodeName.endsWith(":color") ||
                                    attr.nodeName == "tint" || attr.nodeName.endsWith(":tint")
                                ) {
                                    attr.nodeValue = color
                                    changed++
                                }
                            }
                            if (element.childNodes.length == 1 &&
                                element.firstChild?.nodeType == Node.TEXT_NODE &&
                                element.textContent.trim().startsWith("#")
                            ) {
                                element.textContent = color
                                changed++
                            }
                        }
                    }
                }
            }

        directories.filter { it.name.startsWith("values") }.forEach { directory ->
            directory.listFiles()?.filter { it.isFile && it.extension == "xml" }.orEmpty().forEach { file ->
                document("res/${directory.name}/${file.name}").use { xml ->
                    val elements = xml.getElementsByTagName("*")
                    for (i in 0 until elements.length) {
                        val element = elements.item(i) as? Element ?: continue
                        if (element.getAttribute("name") !in appMenuResourceNames) continue
                        if (element.tagName == "color" ||
                            (element.tagName == "item" && element.getAttribute("type") == "color")
                        ) {
                            element.textContent = color
                            changed++
                        }
                    }
                }
            }
        }
        check(changed > 0) {
            "AMOLED AppMenu: no verified resources found in Cromite 153.0.8010.37"
        }
    }
}

/**
 * The original patch depends on the resource patch and additionally modifies
 * eight bytecode sites. Do not report bytecode recovery as complete until
 * those original mutations have been reconstructed and tested.
 */
@Suppress("unused")
val pureAmoledAppMenuPatch = bytecodePatch(
    name = "AMOLED - App menu",
    description = "Restores native AppMenu resources; bytecode surface reconstruction remains pending.",
) {
    compatibleWith("org.cromite.cromite"("153.0.8010.37"))
    dependsOn(pureAmoledAppMenuResourcesPatch)
    val browserTheme = recoveredBrowserThemeOption
    execute {
        if ((browserTheme.value ?: "device") == "device") return@execute
        check(appMenuVisibleSurfaceFingerprint.instructionMatches.size == 8) {
            "AMOLED AppMenu: original eight bytecode anchors were not found."
        }
    }
}
