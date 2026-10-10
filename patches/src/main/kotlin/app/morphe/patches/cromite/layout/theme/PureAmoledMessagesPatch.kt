package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Restored against PureAmoledMessagesPatchKt.class from patches-1.50.0.mpp.
 * Resource allowlist, version, colors and failure behavior match the original.
 */
@Suppress("unused")
val pureAmoledMessagesPatch = resourcePatch(
    name = "AMOLED - Messages and toasts",
    description = "Applies Device default / Pure Black / Pure White only to verified Cromite transient message and toast backgrounds.",
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
        val files = setOf("message_bg_color.xml", "toast_color.xml")
        val names = setOf("message_bg_color", "toast_color")
        val directories = get("res").listFiles()?.filter { it.isDirectory }.orEmpty()
        var hits = 0

        directories.filter { it.name.startsWith("color") }.forEach { directory ->
            files.forEach { filename ->
                val file = directory.resolve(filename)
                if (!file.isFile) return@forEach
                document("res/${directory.name}/${file.name}").use { xml ->
                    val nodes = xml.getElementsByTagName("*")
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
        }

        directories.filter { it.name.startsWith("values") }.forEach { directory ->
            directory.listFiles()?.filter { it.isFile && it.extension == "xml" }.orEmpty().forEach { file ->
                document("res/${directory.name}/${file.name}").use { xml ->
                    val nodes = xml.getElementsByTagName("*")
                    for (i in 0 until nodes.length) {
                        val element = nodes.item(i) as? Element ?: continue
                        val name = element.getAttribute("name")
                        if (name !in names) continue
                        val type = element.getAttribute("type")
                        if (element.tagName == "color" || element.tagName == "macro" ||
                            (element.tagName == "item" && type == "color")
                        ) {
                            element.textContent = target
                            hits++
                        }
                    }
                }
            }
        }

        check(hits > 0) {
            "AMOLED messages/toasts verification failed for Cromite 153.0.8010.37: no whitelisted resources were found."
        }
    }
}
