package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Recovered from PureAmoledWidgetsPatchKt.class in patches-1.50.0.mpp.
 * The original class identifies three widget backgrounds in color and values resources.
 */
@Suppress("unused")
val pureAmoledWidgetsPatch = resourcePatch(
    name = "AMOLED - Widgets",
    description = "Applies the selected AMOLED background to Cromite's three native widget surfaces.",
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
        val resources = setOf("widget_bg", "widget_button_bg", "widget_searchbox_bg")
        val directories = get("res").listFiles()?.filter { it.isDirectory }.orEmpty()
        val found = mutableSetOf<String>()

        directories.filter { it.name.startsWith("color") }.forEach { directory ->
            resources.forEach { name ->
                val path = "res/${directory.name}/$name.xml"
                if (!get(path).isFile) return@forEach
                document(path).use { xml ->
                    val elements = xml.getElementsByTagName("*")
                    for (index in 0 until elements.length) {
                        val element = elements.item(index) as? Element ?: continue
                        val attributes = element.attributes
                        for (j in 0 until attributes.length) {
                            val attribute = attributes.item(j)
                            if (attribute.nodeName == "color" || attribute.nodeName.endsWith(":color")) {
                                attribute.nodeValue = color
                                found += name
                            }
                        }
                        if (element.childNodes.length == 1 &&
                            element.firstChild?.nodeType == Node.TEXT_NODE &&
                            element.textContent.trim().startsWith("#")
                        ) {
                            element.textContent = color
                            found += name
                        }
                    }
                }
            }
        }

        directories.filter { it.name.startsWith("values") }.forEach { directory ->
            directory.listFiles()?.filter { it.isFile && it.extension == "xml" }.orEmpty().forEach { file ->
                document("res/${directory.name}/${file.name}").use { xml ->
                    val elements = xml.getElementsByTagName("*")
                    for (index in 0 until elements.length) {
                        val element = elements.item(index) as? Element ?: continue
                        val name = element.getAttribute("name")
                        if (name !in resources) continue
                        if (element.tagName == "color" ||
                            (element.tagName == "item" && element.getAttribute("type") == "color")
                        ) {
                            element.textContent = color
                            found += name
                        }
                    }
                }
            }
        }
        check(found.isNotEmpty()) {
            "AMOLED Widgets: none of the verified widget resources exist in Cromite 153.0.8010.37."
        }
    }
}
