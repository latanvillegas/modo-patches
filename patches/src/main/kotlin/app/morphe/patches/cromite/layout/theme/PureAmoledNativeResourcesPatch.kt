package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val CROMITE_VERSION = "153.0.8010.37"
private const val COLORS_XML = "res/values/colors.xml"
private const val STYLES_XML = "res/values/styles.xml"
private const val PREFIX = "_0_resource_name_obfuscated_res_"

private fun org.w3c.dom.Document.findResource(tag: String, id: String): Element {
    val name = PREFIX + id
    val matches = (0 until getElementsByTagName(tag).length)
        .map { getElementsByTagName(tag).item(it) as Element }
        .filter { it.getAttribute("name") == name }
    check(matches.size == 1) { "Cromite 153: expected exactly one $tag/$name, found ${matches.size}" }
    return matches.single()
}

/**
 * Edits only decoded Android resources, not DEX or web page styles.
 *
 * Fail closed when the expected Cromite 153 resource map differs. This patch
 * deliberately does not claim to recolor programmatically painted toolbar UI.
 */
@Suppress("unused")
val pureAmoledNativeResourcesPatch = resourcePatch(
    name = "Pure AMOLED native theme resources",
    description = "Sets two verified dark native theme colors to #000000 while preserving light theme.",
) {
    compatibleWith("org.cromite.cromite"(CROMITE_VERSION))

    execute {
        check(get(COLORS_XML).exists()) { "Cromite 153: missing $COLORS_XML" }
        check(get(STYLES_XML).exists()) { "Cromite 153: missing $STYLES_XML" }

        // Validate the complete style-to-color map before writing either value.
        document(STYLES_XML).use { doc ->
            val expected = mapOf(
                "0x7f1500b4" to "0x7f07017c",
                "0x7f1500b6" to "0x7f07017c",
                "0x7f1505d7" to "0x7f07017c",
                "0x7f1500b8" to "0x7f070189",
                "0x7f1500bb" to "0x7f070189",
                "0x7f1505dd" to "0x7f070189",
            )
            expected.forEach { (styleId, colorId) ->
                val style = doc.findResource("style", styleId)
                val items = style.getElementsByTagName("item")
                val actual = (0 until items.length)
                    .map { items.item(it) as Element }
                    .filter { it.getAttribute("name") == PREFIX + "0x7f050154" }
                check(actual.size == 1 && actual.single().textContent.trim() == "@color/" + PREFIX + colorId) {
                    "Cromite 153: unexpected theme background mapping in $styleId"
                }
            }
        }

        document(COLORS_XML).use { doc ->
            val dark = listOf("0x7f070170", "0x7f07017c")
            val light = listOf("0x7f07017d", "0x7f070189")
            val darkNodes = dark.map { doc.findResource("color", it) }
            val lightNodes = light.map { doc.findResource("color", it) }
            darkNodes.forEach {
                check(it.textContent.trim().lowercase() in setOf("#121212", "#000000")) {
                    "Cromite 153: unexpected dark resource value"
                }
            }
            lightNodes.forEach {
                check(it.textContent.trim().equals("#ffffff", ignoreCase = true)) {
                    "Cromite 153: unexpected light resource value"
                }
            }
            darkNodes.forEach { it.textContent = "#000000" }
        }
    }
}
