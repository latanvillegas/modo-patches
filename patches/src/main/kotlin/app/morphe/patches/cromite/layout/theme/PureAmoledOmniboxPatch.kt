package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val OMNIBOX_VERSION = "153.0.8010.37"
private const val OMNIBOX_BLACK = "#FF000000"
private const val OMNIBOX_WHITE = "#FFFFFFFF"

@Suppress("unused")
val pureAmoledOmniboxPatch = resourcePatch(
    name = "AMOLED - Omnibox and search UI",
    description = "Applies Device default / Pure Black / Pure White only to verified Cromite omnibox and native search surfaces.",
) {
    compatibleWith("org.cromite.cromite"(OMNIBOX_VERSION))
    val browserTheme by browserThemeOption()

    execute {
        val selectedTheme = browserTheme ?: "device"
        fun target(directory: java.io.File) = when (selectedTheme) {
            "black" -> OMNIBOX_BLACK
            "white" -> OMNIBOX_WHITE
            else -> if (directory.name.contains("night")) OMNIBOX_BLACK else OMNIBOX_WHITE
        }

        val exactFiles = setOf(
            "hub_search_box_bg_color.xml",
            "omnibox_popover_suggestion_bg_color.xml",
            "omnibox_suggestion_dropdown_bg.xml",
            "search_suggestion_bg_color.xml",
            "toolbar_text_box_bg_color.xml",
        )
        val exactNames = exactFiles.map { it.removeSuffix(".xml") }.toSet()
        val directories = get("res").listFiles()?.filter { it.isDirectory }.orEmpty()
        var hits = 0

        directories.filter { it.name.startsWith("color") }.forEach { directory ->
            exactFiles.forEach files@{ fileName ->
                val file = java.io.File(directory, fileName)
                if (!file.isFile) return@files
                document(file.absolutePath).use { document ->
                    val nodes = document.getElementsByTagName("*")
                    for (index in 0 until nodes.length) {
                        val element = nodes.item(index) as? Element ?: continue
                        val attrs = element.attributes
                        for (attributeIndex in 0 until attrs.length) {
                            val attribute = attrs.item(attributeIndex)
                            if (attribute.nodeName.endsWith(":color") || attribute.nodeName == "color") {
                                attribute.nodeValue = target(directory)
                                hits++
                            }
                        }
                        if (element.childNodes.length == 1 &&
                            element.firstChild?.nodeType == org.w3c.dom.Node.TEXT_NODE &&
                            element.textContent.trim().startsWith("#")) {
                            element.textContent = target(directory)
                            hits++
                        }
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
                            if (element.getAttribute("name") in exactNames &&
                                (element.tagName == "color" ||
                                 element.tagName == "macro" ||
                                 (element.tagName == "item" && type == "color"))) {
                                element.textContent = target(directory)
                                hits++
                            }
                        }
                    }
                }
            }
        }

        if (hits == 0) {
            throw IllegalStateException(
                "AMOLED Omnibox verification failed for Cromite $OMNIBOX_VERSION: no whitelisted omnibox/search resources were found."
            )
        }
    }
}
