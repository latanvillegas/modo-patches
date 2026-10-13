package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import org.w3c.dom.Node

// Reconstructed from PureAmoledItemChooserPatchKt.class of patches-1.50.0.mpp.
@Suppress("unused")
val pureAmoledItemChooserPatch = resourcePatch(
    name = "AMOLED - Item chooser",
    description = "Applies Device default / Pure Black / Pure White to the Cromite item chooser row background.",
) {
    compatibleWith("org.cromite.cromite"("153.0.8010.37"))
    val browserTheme = recoveredBrowserThemeOption

    execute {
        val target = when (browserTheme.value ?: "device") {
            "device" -> return@execute
            "black" -> "#FF000000"
            "white" -> "#FFFFFFFF"
            else -> return@execute
        }
        val resource = "item_chooser_row_background"
        val fileName = "$resource.xml"
        var hits = 0
        val directories = get("res").listFiles()?.filter { it.isDirectory }.orEmpty()

        directories.filter { it.name.startsWith("color") }.forEach { directory ->
            val file = directory.resolve(fileName)
            if (!file.isFile) return@forEach
            document("res/${directory.name}/${file.name}").use { document ->
                val elements = document.getElementsByTagName("*")
                for (i in 0 until elements.length) {
                    val element = elements.item(i) as? Element ?: continue
                    val attrs = element.attributes
                    for (j in 0 until attrs.length) {
                        val attr = attrs.item(j)
                        if (attr.nodeName == "color" || attr.nodeName.endsWith(":color")) {
                            attr.nodeValue = target
                            hits++
                        }
                    }
                    if (element.childNodes.length == 1 &&
                        element.firstChild?.nodeType == Node.TEXT_NODE &&
                        element.textContent.trim().startsWith("#")
                    ) {
                        element.textContent = target
                        hits++
                    }
                }
            }
        }

        directories.filter { it.name.startsWith("values") }.forEach { directory ->
            directory.listFiles()?.filter { it.isFile && it.extension == "xml" }.orEmpty().forEach { file ->
                document("res/${directory.name}/${file.name}").use { document ->
                    val elements = document.getElementsByTagName("*")
                    for (i in 0 until elements.length) {
                        val element = elements.item(i) as? Element ?: continue
                        if (element.getAttribute("name") != resource) continue
                        if (element.tagName == "color" || element.tagName == "macro" ||
                            (element.tagName == "item" && element.getAttribute("type") == "color")
                        ) {
                            element.textContent = target
                            hits++
                        }
                    }
                }
            }
        }

        check(hits > 0) {
            "AMOLED Item chooser: verified resource $resource was not found in Cromite 153.0.8010.37."
        }
    }
}
