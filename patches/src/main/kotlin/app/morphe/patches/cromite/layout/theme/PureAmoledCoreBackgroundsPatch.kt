package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val CORE_BG_VERSION = "153.0.8010.37"
private const val CORE_BG_BLACK = "#FF000000"
private const val CORE_BG_WHITE = "#FFFFFFFF"

@Suppress("unused")
val pureAmoledCoreBackgroundsPatch = resourcePatch(
    name = "AMOLED - Core native backgrounds",
    description = "Applies Device default / Pure Black / Pure White only to verified Cromite default and primary native backgrounds.",
) {
    compatibleWith("org.cromite.cromite"(CORE_BG_VERSION))
    val browserTheme by browserThemeOption()

    execute {
        val selectedTheme = browserTheme ?: "device"
        if (selectedTheme == "device") return@execute
        val target = when (selectedTheme) {
            "black" -> CORE_BG_BLACK
            "white" -> CORE_BG_WHITE
            else -> return@execute
        }

        val exactFiles = setOf("default_bg_color.xml", "primary_background_color.xml")
        val exactNames = setOf(
            "default_bg_color", "default_bg_color_dark",
            "primary_background_color", "primary_background_color_dark",
        )
        val directories = get("res").listFiles()?.filter { it.isDirectory }.orEmpty()
        var hits = 0

        fun recolor(file: java.io.File) {
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
            exactFiles.forEach { fileName ->
                val file = java.io.File(directory, fileName)
                if (file.isFile) recolor(file)
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
                "AMOLED Core native backgrounds verification failed for Cromite $CORE_BG_VERSION: verified resources were not found."
            )
        }
    }
}
