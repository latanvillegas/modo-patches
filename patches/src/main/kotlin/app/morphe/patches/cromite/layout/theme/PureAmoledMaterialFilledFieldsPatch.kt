package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Reconstructed from the original 1.50.0 PureAmoledMaterialFilledFieldsPatchKt.class.
 * Original whitelist: m3_textfield_filled_background_color.xml and
 * mtrl_filled_background_color.xml.
 */
@Suppress("unused")
val pureAmoledMaterialFilledFieldsPatch = resourcePatch(
    name = "AMOLED - Material filled fields",
    description = "Applies Device default / Pure Black / Pure White only to verified Cromite Material filled-field backgrounds.",
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
        val fileNames = setOf(
            "m3_textfield_filled_background_color.xml",
            "mtrl_filled_background_color.xml",
        )
        val names = fileNames.map { it.removeSuffix(".xml") }.toSet()
        val folders = get("res").listFiles()?.filter { it.isDirectory }.orEmpty()
        var changed = 0

        folders.filter { it.name.startsWith("color") }.forEach { folder ->
            fileNames.forEach { filename ->
                val path = "res/${folder.name}/$filename"
                if (!get(path).isFile) return@forEach
                document(path).use { xml ->
                    val nodes = xml.getElementsByTagName("*")
                    for (i in 0 until nodes.length) {
                        val element = nodes.item(i) as? Element ?: continue
                        val attrs = element.attributes
                        for (j in 0 until attrs.length) {
                            val attr = attrs.item(j)
                            if (attr.nodeName == "color" || attr.nodeName.endsWith(":color")) {
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

        folders.filter { it.name.startsWith("values") }.forEach { folder ->
            folder.listFiles()?.filter { it.isFile && it.extension == "xml" }.orEmpty().forEach { file ->
                document("res/${folder.name}/${file.name}").use { xml ->
                    val nodes = xml.getElementsByTagName("*")
                    for (i in 0 until nodes.length) {
                        val element = nodes.item(i) as? Element ?: continue
                        if (element.getAttribute("name") !in names) continue
                        val type = element.getAttribute("type")
                        if (element.tagName == "color" || element.tagName == "macro" ||
                            (element.tagName == "item" && type == "color")
                        ) {
                            element.textContent = color
                            changed++
                        }
                    }
                }
            }
        }
        check(changed > 0) {
            "AMOLED Material filled fields verification failed for Cromite 153.0.8010.37: verified resources were not found."
        }
    }
}
