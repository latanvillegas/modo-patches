package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val MATERIAL_FILLED_VERSION = "153.0.8010.37"
private const val MATERIAL_FILLED_BLACK = "#FF000000"
private const val MATERIAL_FILLED_WHITE = "#FFFFFFFF"

@Suppress("unused")
val pureAmoledMaterialFilledFieldsPatch = resourcePatch(
    name = "AMOLED - Material filled fields",
    description = "Applies Device default / Pure Black / Pure White only to verified Cromite Material filled-field backgrounds.",
) {
    compatibleWith("org.cromite.cromite"(MATERIAL_FILLED_VERSION))
    val browserTheme by browserThemeOption()

    execute {
        val selectedTheme = browserTheme ?: "device"
        if (selectedTheme == "device") return@execute
        val target = when (selectedTheme) {
            "black" -> MATERIAL_FILLED_BLACK
            "white" -> MATERIAL_FILLED_WHITE
            else -> return@execute
        }

        val exactFiles = setOf(
            "m3_textfield_filled_background_color.xml",
            "mtrl_filled_background_color.xml",
        )
        val exactNames = exactFiles.map { it.removeSuffix(".xml") }.toSet()
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
                "AMOLED Material filled fields verification failed for Cromite $MATERIAL_FILLED_VERSION: verified resources were not found."
            )
        }
    }
}
