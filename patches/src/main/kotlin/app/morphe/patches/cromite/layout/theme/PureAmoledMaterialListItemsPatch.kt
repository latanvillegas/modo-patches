package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Recovered from PureAmoledMaterialListItemsPatchKt.class in patches-1.50.0.mpp.
 * The two selector filenames, values-resource matching and recoloring operations
 * follow the original compiled patch.
 */
@Suppress("unused")
val pureAmoledMaterialListItemsPatch = resourcePatch(
    name = "AMOLED - Material list items",
    description = "Applies Device default / Pure Black / Pure White only to verified Cromite Material list-item backgrounds.",
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

        val filenames = setOf(
            "m3_list_item_background_color_selector.xml",
            "m3_segmented_list_item_background_color_selector.xml",
        )
        val names = filenames.map { it.removeSuffix(".xml") }.toSet()
        val directories = get("res").listFiles()?.filter { it.isDirectory }.orEmpty()
        var changed = 0

        directories.filter { it.name.startsWith("color") }.forEach { directory ->
            filenames.forEach { filename ->
                val path = "res/${directory.name}/$filename"
                if (!get(path).isFile) return@forEach
                document(path).use { xml ->
                    val elements = xml.getElementsByTagName("*")
                    for (i in 0 until elements.length) {
                        val element = elements.item(i) as? Element ?: continue
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

        directories.filter { it.name.startsWith("values") }.forEach { directory ->
            directory.listFiles()?.filter { it.isFile && it.extension == "xml" }.orEmpty().forEach { file ->
                document("res/${directory.name}/${file.name}").use { xml ->
                    val elements = xml.getElementsByTagName("*")
                    for (i in 0 until elements.length) {
                        val element = elements.item(i) as? Element ?: continue
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
            "AMOLED Material list items verification failed for Cromite 153.0.8010.37: verified resources were not found."
        }
    }
}
