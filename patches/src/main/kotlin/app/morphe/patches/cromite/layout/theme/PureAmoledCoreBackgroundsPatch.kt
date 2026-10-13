package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Reconstruction based on PureAmoledCoreBackgroundsPatchKt.class in
 * patches-1.50.0.mpp. The resource allowlist comes from the original
 * compiled patch; runtime equivalence is not yet established.
 */
@Suppress("unused")
val pureAmoledCoreBackgroundsPatch = resourcePatch(
    name = "AMOLED - Core native backgrounds",
    description = "Applies Device default / Pure Black / Pure White only to verified Cromite default and primary native backgrounds.",
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

        val resourceFiles = setOf("default_bg_color.xml", "primary_background_color.xml")
        val resourceNames = setOf(
            "default_bg_color",
            "default_bg_color_dark",
            "default_bg_color_elev_0",
            "primary_background_color",
            "primary_background_color_dark",
        )
        val directories = get("res").listFiles()?.filter { it.isDirectory }.orEmpty()
        val modified = mutableSetOf<String>()

        directories.filter { it.name.startsWith("color") }.forEach { directory ->
            resourceFiles.forEach { fileName ->
                val relative = "res/${directory.name}/$fileName"
                if (!get(relative).isFile) return@forEach
                document(relative).use { xml ->
                    val elements = xml.getElementsByTagName("*")
                    for (index in 0 until elements.length) {
                        val element = elements.item(index) as? Element ?: continue
                        val attributes = element.attributes
                        for (i in 0 until attributes.length) {
                            val attribute = attributes.item(i)
                            if (attribute.nodeName == "color" || attribute.nodeName.endsWith(":color")) {
                                attribute.nodeValue = color
                                modified += fileName
                            }
                        }
                        if (element.childNodes.length == 1 &&
                            element.firstChild?.nodeType == Node.TEXT_NODE &&
                            element.textContent.trim().startsWith("#")
                        ) {
                            element.textContent = color
                            modified += fileName
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
                        if (name !in resourceNames) continue
                        if (element.tagName == "color" ||
                            (element.tagName == "item" && element.getAttribute("type") == "color")
                        ) {
                            element.textContent = color
                            modified += name
                        }
                    }
                }
            }
        }
        check(modified.isNotEmpty()) {
            "AMOLED Core native backgrounds verification failed for Cromite 153.0.8010.37: verified resources were not found."
        }
    }
}
