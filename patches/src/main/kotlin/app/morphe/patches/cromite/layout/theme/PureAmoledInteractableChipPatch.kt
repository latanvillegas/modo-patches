package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import org.w3c.dom.Node

/** Reconstructed from PureAmoledInteractableChipPatchKt.class in patches-1.50.0.mpp. */
@Suppress("unused")
val pureAmoledInteractableChipPatch = resourcePatch(
    name = "AMOLED - Interactable chip",
    description = "Applies Device default / Pure Black / Pure White only to the verified Cromite interactable-chip background.",
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
        val name = "interactable_chip_bg_color"
        val fileName = "$name.xml"
        val directories = get("res").listFiles()?.filter { it.isDirectory }.orEmpty()
        var hits = 0
        directories.filter { it.name.startsWith("color") }.forEach { directory ->
            val file = directory.resolve(fileName)
            if (!file.isFile) return@forEach
            document("res/${directory.name}/$fileName").use { document ->
                val nodes = document.getElementsByTagName("*")
                for (i in 0 until nodes.length) {
                    val element = nodes.item(i) as? Element ?: continue
                    val attributes = element.attributes
                    for (j in 0 until attributes.length) {
                        val attribute = attributes.item(j)
                        if (attribute.nodeName == "color" || attribute.nodeName.endsWith(":color")) {
                            attribute.nodeValue = target
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
                    val nodes = document.getElementsByTagName("*")
                    for (i in 0 until nodes.length) {
                        val element = nodes.item(i) as? Element ?: continue
                        if (element.getAttribute("name") != name) continue
                        if (element.tagName == "color" ||
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
            "AMOLED Interactable chip: resource $name was not found in Cromite 153.0.8010.37."
        }
    }
}
