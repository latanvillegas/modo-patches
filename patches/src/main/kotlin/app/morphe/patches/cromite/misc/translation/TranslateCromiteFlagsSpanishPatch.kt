package app.morphe.patches.cromite.misc.translation

import app.morphe.patcher.patch.resourcePatch

/**
 * Translates existing Android string resources used by chrome://flags.
 * Does not alter feature identifiers, command-line switches, or flag state.
 * Chromium's native flags catalog may require a separate native-resource solution.
 */
@Suppress("unused")
val translateCromiteFlagsSpanishPatch = resourcePatch(
    name = "Translate Cromite Flags to Spanish",
    description = "Translates available chrome://flags interface strings without changing experimental flags.",
    default = false,
) {
    compatibleWith("org.cromite.cromite"("153.0.8010.37"))

    execute {
        val translations = mapOf(
            "Experiments" to "Experimentos",
            "Available" to "Disponibles",
            "Unavailable" to "No disponibles",
            "Search flags" to "Buscar funciones experimentales",
            "Search experiments" to "Buscar experimentos",
            "Reset all" to "Restablecer todo",
            "Reset all to default" to "Restablecer todos los valores predeterminados",
            "Default" to "Predeterminado",
            "Enabled" to "Habilitado",
            "Disabled" to "Deshabilitado",
            "Relaunch" to "Reiniciar",
            "Relaunch to apply changes" to "Reiniciar para aplicar los cambios",
            "Your changes will take effect the next time you relaunch Cromite." to
                "Los cambios se aplicarán la próxima vez que reinicies Cromite.",
            "Warning: Experimental features ahead!" to "Advertencia: funciones experimentales",
            "These experimental features may change, break, or disappear at any time." to
                "Estas funciones experimentales pueden cambiar, dejar de funcionar o desaparecer en cualquier momento.",
            "Temporarily unexpire M flags." to "Restaurar temporalmente funciones experimentales caducadas.",
            "Not available on your platform." to "No disponible en tu plataforma.",
        )

        val root = get("res")
        val entry = Regex("""<string\s+name="([^"]+)"([^>]*)>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        val replacements = linkedMapOf<String, String>()
        root.resolve("values").listFiles()
            ?.filter { it.isFile && it.extension == "xml" }
            ?.forEach { file ->
                entry.findAll(file.readText()).forEach { match ->
                    translations[match.groupValues[3].trim()]?.let {
                        replacements[match.groupValues[1]] = it
                    }
                }
            }

        // Edit existing entries, including existing locale overrides. Creating
        // synthetic string_0x... entries breaks arsclib resource encoding.
        fun xml(value: String) = value.replace("&", "&amp;")
            .replace("<", "&lt;").replace(">", "&gt;")
        var changed = 0
        root.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("values") }
            ?.flatMap { dir -> dir.listFiles()?.filter { it.isFile && it.extension == "xml" } ?: emptyList() }
            ?.forEach { file ->
                val original = file.readText()
                val updated = entry.replace(original) { match ->
                    val translation = replacements[match.groupValues[1]]
                    if (translation == null) match.value else {
                        changed++
                        match.value.replaceRange(
                            match.groups[3]!!.range.first - match.range.first,
                            match.groups[3]!!.range.last - match.range.first + 1,
                            xml(translation),
                        )
                    }
                }
                if (updated != original) file.writeText(updated)
            }
        // Do not abort APK patching if flags text lives exclusively in native
        // Chromium resources rather than Android values XML.
        println("Translate Cromite Flags to Spanish: updated $changed existing resource entries")
    }
}
