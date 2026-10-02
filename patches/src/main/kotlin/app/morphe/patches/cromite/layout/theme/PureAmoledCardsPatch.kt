package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val CARDS_VERSION = "153.0.8010.37"
private const val CARDS_BLACK = "#FF000000"
private const val CARDS_WHITE = "#FFFFFFFF"

@Suppress("unused")
val pureAmoledCardsPatch = resourcePatch(
    name = "AMOLED - Cards",
    description = "Applies Device default / Pure Black / Pure White to verified Cromite native card surfaces only.",
) {
    compatibleWith("org.cromite.cromite"(CARDS_VERSION))
    val browserTheme by browserThemeOption()

    execute {
        val selectedTheme = browserTheme ?: "device"
        if (selectedTheme == "device") return@execute

        val target = when (selectedTheme) {
            "black" -> CARDS_BLACK
            "white" -> CARDS_WHITE
            else -> return@execute
        }

        val exactFile = "tab_hover_card_bg_color.xml"
        val exactName = "tab_hover_card_bg_color"
        val resourceDirectories = get("res").listFiles()
            ?.filter { it.isDirectory }
            .orEmpty()

        var hits = 0

        resourceDirectories
            .filter { it.name.startsWith("color") }
            .forEach { directory ->
                val file = java.io.File(directory, exactFile)
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

        resourceDirectories
            .filter { it.name.startsWith("values") }
            .forEach { directory ->
                directory.listFiles()
                    ?.filter { it.isFile && it.extension == "xml" }
                    .orEmpty()
                    .forEach { file ->
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
                "AMOLED Cards resource verification failed for Cromite $CARDS_VERSION: tab_hover_card_bg_color was not found."
            )
        }
    }
}
