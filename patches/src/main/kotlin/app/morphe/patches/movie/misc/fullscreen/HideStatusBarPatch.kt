package app.morphe.patches.movie.misc.fullscreen

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.Opcode
import org.w3c.dom.Element

/**
 * Shared by Movie's MainActivity and VideoPlayDetailActivity.
 * Hide the status bar and lay out Movie's content through its former top inset.
 * Enable edge-to-edge window layout while keeping system navigation visible.
 */
private object MovieBaseActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/mgs/carparking/app/BaseActivity;",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(methodCall(name = "initParam")),
)

private val movieRuntimeFullscreenPatch = bytecodePatch {
    compatibleWith("com.movievn.cinevi"("4.0.0"))

    execute {
        MovieBaseActivityOnCreateFingerprint.method.apply {
            val returnIndices = instructions.withIndex()
                .filter { it.value.opcode == Opcode.RETURN_VOID }
                .map { it.index }
            check(returnIndices.size == 2) {
                "Movie: unexpected BaseActivity.onCreate return structure"
            }
            // Only the normal completion path; the earlier return redirects to Splash.
            addInstructions(
                returnIndices.last(),
                """
                    invoke-virtual {p0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;
                    move-result-object v0
                    const/16 p1, 0x400
                    invoke-virtual {v0, p1}, Landroid/view/Window;->addFlags(I)V
                    invoke-virtual {p0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;
                    move-result-object v0
                    invoke-virtual {v0}, Landroid/view/Window;->getDecorView()Landroid/view/View;
                    move-result-object v0
                    const/16 p1, 0x500
                    invoke-virtual {v0, p1}, Landroid/view/View;->setSystemUiVisibility(I)V
                    invoke-virtual {p0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;
                    move-result-object v0
                    const/4 p1, 0x0
                    invoke-virtual {v0, p1}, Landroid/view/Window;->setDecorFitsSystemWindows(Z)V
                """.trimIndent()
            )
        }
    }
}

/** Enables fullscreen in Movie-owned themes and the shared activity base. */
@Suppress("unused")
val movieHideStatusBarPatch = resourcePatch(
    name = "Movie - Hide status bar",
    description = "Hides the Android status bar and removes the window's top layout reservation without hiding bottom navigation.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(movieRuntimeFullscreenPatch, movieRemoveStatusBarInsetsPatch)

    execute {
        val targets = setOf("BrowserTheme", "AppTheme_Main", "AppTheme_Splash")
        val manifestThemes = mutableSetOf<String>()
        document("AndroidManifest.xml").use { manifest ->
            val application = manifest.getElementsByTagName("application").item(0) as? Element
                ?: error("Movie: application element not found")
            manifestThemes += application.getAttribute("android:theme").removePrefix("@style/")
            val activities = manifest.getElementsByTagName("activity")
            for (index in 0 until activities.length) {
                val activity = activities.item(index) as? Element ?: continue
                if (!activity.getAttribute("android:name").startsWith("com.mgs.carparking.")) continue
                val theme = activity.getAttribute("android:theme")
                if (theme.startsWith("@style/")) manifestThemes += theme.removePrefix("@style/")
            }
        }
        check(targets.all { it in manifestThemes }) {
            "Movie: expected themes not referenced in manifest: ${targets - manifestThemes}"
        }

        for (path in listOf("res/values/styles.xml", "res/values-v26/styles.xml", "res/values-v27/styles.xml")) {
            document(path).use { doc ->
                val styles = doc.getElementsByTagName("style")
                val found = mutableSetOf<String>()
                for (index in 0 until styles.length) {
                    val style = styles.item(index) as? Element ?: continue
                    if (style.getAttribute("name") !in targets) continue
                    found += style.getAttribute("name")
                    val attributes = mapOf(
                        "android:windowFullscreen" to "true",
                        "android:windowBackground" to "@android:color/black",
                        "android:statusBarColor" to "@android:color/black",
                        "android:windowLightStatusBar" to "false",
                    )
                    for ((name, value) in attributes) {
                        val items = style.getElementsByTagName("item")
                        var target: Element? = null
                        for (itemIndex in 0 until items.length) {
                            val item = items.item(itemIndex) as? Element ?: continue
                            if (item.getAttribute("name") == name) {
                                target = item
                                break
                            }
                        }
                        if (target == null) {
                            target = doc.createElement("item")
                            target.setAttribute("name", name)
                            style.appendChild(target)
                        }
                        target.textContent = value
                    }
                }
                check(found.isNotEmpty()) { "Movie: no target themes in $path" }
            }
        }
    }
}
