package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val CONTEXT_MENU_VERSION = "153.0.8010.37"
private const val CONTEXT_MENU_BLACK = "#FF000000"
private const val CONTEXT_MENU_WHITE = "#FFFFFFFF"

@Suppress("unused")
val pureAmoledContextMenuPatch = resourcePatch(
    name = "AMOLED - Context menu",
    description = "Applies Device default / Pure Black / Pure White only to the verified Cromite context-menu header surface.",
) {
    compatibleWith("org.cromite.cromite"(CONTEXT_MENU_VERSION))
    val browserTheme by browserThemeOption()

    execute {
        val selectedTheme = browserTheme ?: "device"
        if (selectedTheme == "device") return@execute
        val target = when (selectedTheme) {
            "black" -> CONTEXT_MENU_BLACK
            "white" -> CONTEXT_MENU_WHITE
            else -> return@execute
        }

        val exactFile = "context_menu_header_circle_bg_color.xml"
        val exactName = "context_menu_header_circle_bg_color"
        val directories = get("res").listFiles()?.filter { it.isDirectory }.orEmpty()
        var hits = 0

        directories.filter { it.name.startsWith("color") }.forEach { directory ->
            val file = java.io.File(directory, exactFile)
            if (!file.isFile) return@forEach
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
                "AMOLED Context menu verification failed for Cromite $CONTEXT_MENU_VERSION: verified resource was not found."
            )
        }
    }
}
