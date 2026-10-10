package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Reconstruction of PureAmoledXrPatchKt.class from patches-1.50.0.mpp.
 * Original resource identifier: xr_toolbar_bg.
 */
@Suppress("unused")
val pureAmoledXrPatch = resourcePatch(
    name = "AMOLED - XR toolbar",
    description = "Applies Device default / Pure Black / Pure White to Cromite's XR toolbar background.",
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
        val resourceName = "xr_toolbar_bg"
        val fileName = "xr_toolbar_bg.xml"
        var hits = 0
        val directories = get("res").listFiles()?.filter { it.isDirectory }.orEmpty()

        directories.filter { it.name.startsWith("color") }.forEach { directory ->
            val file = directory.resolve(fileName)
            if (!file.isFile) return@forEach
            document("res/${directory.name}/$fileName").use { xml ->
                val elements = xml.getElementsByTagName("*")
                for (i in 0 until elements.length) {
                    val element = elements.item(i) as? Element ?: continue
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
                document("res/${directory.name}/${file.name}").use { xml ->
                    val elements = xml.getElementsByTagName("*")
                    for (i in 0 until elements.length) {
                        val element = elements.item(i) as? Element ?: continue
                        if (element.getAttribute("name") != resourceName) continue
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
            "AMOLED XR verification failed for Cromite 153.0.8010.37: xr_toolbar_bg was not found."
        }
    }
}
