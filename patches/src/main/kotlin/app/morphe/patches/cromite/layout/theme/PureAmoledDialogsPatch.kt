package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val DIALOGS_VERSION = "153.0.8010.37"
private const val DIALOGS_BLACK = "#FF000000"
private const val DIALOGS_WHITE = "#FFFFFFFF"

@Suppress("unused")
val pureAmoledDialogsPatch = resourcePatch(
    name = "AMOLED - Dialogs and bottom sheets",
    description = "Applies Device default / Pure Black / Pure White only to verified Cromite dialog and bottom-sheet background resources.",
) {
    compatibleWith("org.cromite.cromite"(DIALOGS_VERSION))
    val browserTheme by browserThemeOption()

    execute {
        val selectedTheme = browserTheme ?: "device"
        if (selectedTheme == "device") return@execute

        val target = when (selectedTheme) {
            "black" -> DIALOGS_BLACK
            "white" -> DIALOGS_WHITE
            else -> return@execute
        }

        val exactColorFiles = setOf(
            "bottom_sheet_bg_color.xml",
            "dialog_bg_color.xml",
            "tab_group_dialog_bg_color.xml",
        )
        val exactValueNames = setOf(
            "bottom_sheet_bg_color",
            "dialog_bg_color",
            "tab_group_dialog_bg_color",
        )

        val resourceDirectories = get("res").listFiles()
            ?.filter { it.isDirectory }
            .orEmpty()
        val colorDirectories = resourceDirectories.filter { it.name.startsWith("color") }
        val valueDirectories = resourceDirectories.filter { it.name.startsWith("values") }

        var hits = 0

        colorDirectories.forEach { directory ->
            exactColorFiles.forEach { fileName ->
                val file = java.io.File(directory, fileName)
                if (!file.isFile) return@forEach
                document(file.absolutePath).use { document ->
                    val nodes = document.getElementsByTagName("*")
                    for (index in 0 until nodes.length) {
                        val element = nodes.item(index) as? Element ?: continue
                        val attributes = element.attributes
                        for (attributeIndex in 0 until attributes.length) {
                            val attribute = attributes.item(attributeIndex)
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
                                if (name in exactValueNames &&
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
                "AMOLED dialog/sheet resource verification failed for Cromite $DIALOGS_VERSION: no whitelisted resources were found."
            )
        }
    }
}
