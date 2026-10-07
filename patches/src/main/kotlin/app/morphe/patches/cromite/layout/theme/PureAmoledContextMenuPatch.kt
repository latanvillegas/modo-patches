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

private const val CONTEXT_MENU_VERSION = "153.0.8010.37"
private const val CONTEXT_MENU_BLACK = "#FF000000"
private const val CONTEXT_MENU_WHITE = "#FFFFFFFF"
private const val CONTEXT_MENU_OWNER = "Lg03;"
private const val CONTEXT_MENU_LAYOUT_ID = 0x7f0e00f7L

private val contextMenuSurfaceFingerprint = Fingerprint(
    definingClass = CONTEXT_MENU_OWNER,
    name = "b",
    returnType = "Ljava/lang/Object;",
    parameters = listOf(
        "Ljava/util/List;",
        "Landroid/view/View;",
        "Lh75;",
        "Ll75;",
    ),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            smali = "Landroid/view/LayoutInflater;->inflate(ILandroid/view/ViewGroup;)Landroid/view/View;",
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            smali = "Landroid/view/View;->findViewById(I)Landroid/view/View;",
        ),
    ),
)

private fun contextMenuBackgroundSmali(
    theme: String,
    viewRegister: Int,
    scratchRegister: Int,
): String {
    val view = "v$viewRegister"
    val scratch = "v$scratchRegister"

    return when (theme) {
        "black" -> """
            const $scratch, -0x1000000
            invoke-virtual {$view, $scratch}, Landroid/view/View;->setBackgroundColor(I)V
        """.trimIndent()
        "white" -> """
            const $scratch, -0x1
            invoke-virtual {$view, $scratch}, Landroid/view/View;->setBackgroundColor(I)V
        """.trimIndent()
        else -> """
            invoke-virtual {$view}, Landroid/view/View;->getResources()Landroid/content/res/Resources;
            move-result-object $scratch
            invoke-virtual {$scratch}, Landroid/content/res/Resources;->getConfiguration()Landroid/content/res/Configuration;
            move-result-object $scratch
            iget $scratch, $scratch, Landroid/content/res/Configuration;->uiMode:I
            and-int/lit8 $scratch, $scratch, 0x20
            ushr-int/lit8 $scratch, $scratch, 0x5
            neg-int $scratch, $scratch
            ushr-int/lit8 $scratch, $scratch, 0x8
            not-int $scratch, $scratch
            invoke-virtual {$view, $scratch}, Landroid/view/View;->setBackgroundColor(I)V
        """.trimIndent()
    }
}

private val pureAmoledContextMenuResourcesPatch = resourcePatch(
    description = "Keeps the verified context-menu header surface aligned with the selectable pure AMOLED palette.",
) {
    compatibleWith("org.cromite.cromite"(CONTEXT_MENU_VERSION))
    val browserTheme by browserThemeOption()

    execute {
        val selectedTheme = browserTheme ?: "device"
        val exactFile = "context_menu_header_circle_bg_color.xml"
        val exactName = "context_menu_header_circle_bg_color"
        val directories = get("res").listFiles()?.filter { it.isDirectory }.orEmpty()
        var hits = 0

        fun targetFor(directoryName: String): String = when (selectedTheme) {
            "black" -> CONTEXT_MENU_BLACK
            "white" -> CONTEXT_MENU_WHITE
            else -> if (directoryName.contains("night")) CONTEXT_MENU_BLACK else CONTEXT_MENU_WHITE
        }

        directories.filter { it.name.startsWith("color") }.forEach { directory ->
            val file = java.io.File(directory, exactFile)
            if (!file.isFile) return@forEach
            val target = targetFor(directory.name)
            document(file.absolutePath).use { document ->
                val nodes = document.getElementsByTagName("*")
                for (index in 0 until nodes.length) {
                    val element = nodes.item(index) as? Element ?: continue
                    val attrs = element.attributes
                    for (attributeIndex in 0 until attrs.length) {
                        val attribute = attrs.item(attributeIndex)
                        if (attribute.nodeName.endsWith(":color") || attribute.nodeName == "color") {
                            attribute.nodeValue = target
                            hits++
                        }
                    }
                    if (element.childNodes.length == 1 &&
                        element.firstChild?.nodeType == org.w3c.dom.Node.TEXT_NODE &&
                        element.textContent.trim().startsWith("#")) {
                        element.textContent = target
                        hits++
                    }
                }
            }
        }

        directories.filter { it.name.startsWith("values") }.forEach { directory ->
            val target = targetFor(directory.name)
            directory.listFiles()?.filter { it.isFile && it.extension == "xml" }.orEmpty().forEach { file ->
                runCatching {
                    document(file.absolutePath).use { document ->
                        val nodes = document.getElementsByTagName("*")
                        for (index in 0 until nodes.length) {
                            val element = nodes.item(index) as? Element ?: continue
                            val type = element.getAttribute("type")
                            if (element.getAttribute("name") == exactName &&
                                (element.tagName == "color" ||
                                 element.tagName == "macro" ||
                                 (element.tagName == "item" && type == "color"))) {
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
                "AMOLED Context menu verification failed for Cromite $CONTEXT_MENU_VERSION: verified header resource was not found."
            )
        }
    }
}

@Suppress("unused")
val pureAmoledContextMenuPatch = bytecodePatch(
    name = "AMOLED - Context menu",
    description = "Applies Device default / Pure Black / Pure White to the verified Cromite context-menu content surface while preserving the transparent outer dialog window.",
) {
    compatibleWith("org.cromite.cromite"(CONTEXT_MENU_VERSION))
    dependsOn(pureAmoledContextMenuResourcesPatch)
    val browserTheme by browserThemeOption()

    execute {
        val selectedTheme = browserTheme ?: "device"

        contextMenuSurfaceFingerprint.apply {
            if (instructionMatches.size != 2) {
                throw IllegalStateException(
                    "AMOLED Context menu bytecode verification failed for Cromite $CONTEXT_MENU_VERSION: " +
                        "expected inflate/findViewById anchors, found ${instructionMatches.size}."
                )
            }

            val inflateIndex = instructionMatches[0].index
            val listLookupIndex = instructionMatches[1].index
            if (inflateIndex >= listLookupIndex) {
                throw IllegalStateException(
                    "AMOLED Context menu bytecode verification failed for Cromite $CONTEXT_MENU_VERSION: structural anchors are out of order."
                )
            }

            val inflateInstruction = method.getInstruction(inflateIndex)
            val layoutIdInstruction = method.getInstruction(inflateIndex - 2)
            val inflateResultInstruction = method.getInstruction(inflateIndex + 1)

            if (inflateInstruction.opcode != Opcode.INVOKE_VIRTUAL ||
                inflateInstruction !is FiveRegisterInstruction ||
                inflateInstruction.registerCount != 3 ||
                layoutIdInstruction !is OneRegisterInstruction ||
                layoutIdInstruction !is WideLiteralInstruction ||
                layoutIdInstruction.wideLiteral != CONTEXT_MENU_LAYOUT_ID ||
                inflateInstruction.registerD != layoutIdInstruction.registerA ||
                inflateResultInstruction.opcode != Opcode.MOVE_RESULT_OBJECT ||
                inflateResultInstruction !is OneRegisterInstruction) {
                throw IllegalStateException(
                    "AMOLED Context menu bytecode verification failed for Cromite $CONTEXT_MENU_VERSION: " +
                        "layout 0x7f0e00f7 inflate structure differs from the verified APK."
                )
            }

            val surfaceRegister = inflateResultInstruction.registerA
            val scratchRegister = layoutIdInstruction.registerA

            // g03.b() inflates the visible context-menu content into viewInflate.
            // n03 keeps its full-screen Window transparent, so recolor only this
            // content view. The transparent outer area remains untouched.
            method.addInstructions(
                inflateIndex + 2,
                contextMenuBackgroundSmali(selectedTheme, surfaceRegister, scratchRegister),
            )
        }
    }
}
