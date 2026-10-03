package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val TOOLBAR_VERSION = "153.0.8010.37"
private const val TOOLBAR_BLACK = "#FF000000"
private const val TOOLBAR_WHITE = "#FFFFFFFF"

@Suppress("unused")
val pureAmoledToolbarPatch = resourcePatch(
    name = "AMOLED - Toolbar",
    description = "Applies Device default / Pure Black / Pure White only to verified Cromite toolbar backgrounds.",
) {
    compatibleWith("org.cromite.cromite"(TOOLBAR_VERSION))
    val browserTheme by browserThemeOption()

    execute {
        val selectedTheme = browserTheme ?: "device"
        fun target(directory: java.io.File) = when (selectedTheme) {
            "black" -> TOOLBAR_BLACK
            "white" -> TOOLBAR_WHITE
            else -> if (directory.name.contains("night")) TOOLBAR_BLACK else TOOLBAR_WHITE
        }

        val exactColorFiles = setOf(
            "toolbar_background_primary.xml",
            "toolbar_background_primary_incognito.xml",
            "toolbar_background_color.xml",
            "top_toolbar_background_color.xml",
        )
        val exactNames = exactColorFiles.map { it.removeSuffix(".xml") }.toSet()
        val directories = get("res").listFiles()?.filter { it.isDirectory }.orEmpty()
        var hits = 0

        fun recolorFile(file: java.io.File, target: String) {
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

        directories.filter { it.name.startsWith("color") }.forEach { directory ->
            exactColorFiles.forEach files@{ fileName ->
                val file = java.io.File(directory, fileName)
                if (file.isFile) recolorFile(file, target(directory))
            }
        }

        directories.filter { it.name.startsWith("drawable") }.forEach { directory ->
            val file = java.io.File(directory, "toolbar_background.xml")
            if (file.isFile) recolorFile(file, target(directory))
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
                "AMOLED Toolbar verification failed for Cromite $TOOLBAR_VERSION: no whitelisted toolbar resources were found."
            )
        }
    }
}
