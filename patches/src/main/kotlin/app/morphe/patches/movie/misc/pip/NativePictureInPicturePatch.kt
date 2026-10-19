package app.morphe.patches.movie.misc.pip

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import org.w3c.dom.Element

private const val ACTIVITY = "Lcom/mgs/carparking/ui/homecontent/videodetail/VideoPlayDetailActivity;"
private const val BASE = "Lcom/mgs/carparking/app/BaseActivity;"

private val moviePipBytecodePatch = bytecodePatch {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    execute {
        val activity = mutableClassDefBy(ACTIVITY)
        check(activity.methods.none { it.name == "onUserLeaveHint" }) {
            "Movie PiP: onUserLeaveHint already declared"
        }

        // Android 12+: allow seamless system-controlled auto-enter on Home/recents.
        val resume = activity.methods.single {
            it.name == "onResume" && it.parameterTypes.isEmpty()
        }
        check(resume.implementation!!.registerCount >= 5)
        resume.addInstructions(0, """
            sget v0, Landroid/os/Build${';->SDK_INT:I
            const/16 v1, 0x1f
            if-lt v0, v1, :morphe_pip_resume_end
            new-instance v0, Landroid/app/PictureInPictureParams${';
            invoke-direct {v0}, Landroid/app/PictureInPictureParams$Builder;-><init>()V
            const/4 v1, 0x1
            invoke-virtual {v0, v1}, Landroid/app/PictureInPictureParams$Builder;->setAutoEnterEnabled(Z)Landroid/app/PictureInPictureParams$Builder;
            move-result-object v0
            invoke-virtual {v0}, Landroid/app/PictureInPictureParams$Builder;->build()Landroid/app/PictureInPictureParams;
            move-result-object v0
            invoke-virtual {p0, v0}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
            :morphe_pip_resume_end
            nop
        """.trimIndent())

        // Skip only the player pause call, preserving history and the rest of onPause.
        val pause = activity.methods.single {
            it.name == "onPause" && it.parameterTypes.isEmpty()
        }
        check(pause.implementation!!.registerCount >= 2)
        val pauseCalls = pause.implementation!!.instructions
            .withIndex()
            .filter { (_, instruction) ->
                instruction.toString().contains("Lw/a;->L()V")
            }
        check(pauseCalls.size == 1) {
            "Movie PiP: expected one player pause invocation, found ${pauseCalls.size}"
        }
        val pauseIndex = pauseCalls.single().index
        pause.addInstructions(pauseIndex + 1, """
            :morphe_pip_after_player_pause
            nop
        """.trimIndent())
        pause.addInstructions(pauseIndex, """
            invoke-virtual {p0}, Landroid/app/Activity;->isInPictureInPictureMode()Z
            move-result v0
            if-nez v0, :morphe_pip_after_player_pause
        """.trimIndent())

        // Android 8-11: explicit entry when leaving, only if a player exists.
        val leave = ImmutableMethod(
            ACTIVITY, "onUserLeaveHint", emptyList(), "V",
            AccessFlags.PROTECTED.value, null, null,
            MutableMethodImplementation(3)
        ).toMutable()
        leave.addInstructions(0, """
            invoke-super {p0}, $BASE->onUserLeaveHint()V
            sget v0, Landroid/os/Build$VERSION;->SDK_INT:I
            const/16 v1, 0x1a
            if-lt v0, v1, :morphe_pip_leave_done
            const/16 v1, 0x1f
            if-ge v0, v1, :morphe_pip_leave_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {p0}, Landroid/app/Activity;->enterPictureInPictureMode()Z
            :morphe_pip_leave_done
            return-void
        """.trimIndent())
        activity.methods.add(leave)
    }
}

@Suppress("unused")
val movieNativePictureInPicturePatch = resourcePatch(
    name = "Movie - Native Picture-in-Picture",
    description = "Automatically enter native Android PiP when leaving the video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipBytecodePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length)
                .mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: playback activity missing from manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges")
                .split('|')
                .filter { it.isNotBlank() }
                .toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}VERSION;->SDK_INT:I
            const/16 v1, 0x1f
            if-lt v0, v1, :morphe_pip_resume_end
            new-instance v0, Landroid/app/PictureInPictureParams$Builder;
            invoke-direct {v0}, Landroid/app/PictureInPictureParams$Builder;-><init>()V
            const/4 v1, 0x1
            invoke-virtual {v0, v1}, Landroid/app/PictureInPictureParams$Builder;->setAutoEnterEnabled(Z)Landroid/app/PictureInPictureParams$Builder;
            move-result-object v0
            invoke-virtual {v0}, Landroid/app/PictureInPictureParams$Builder;->build()Landroid/app/PictureInPictureParams;
            move-result-object v0
            invoke-virtual {p0, v0}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
            :morphe_pip_resume_end
            nop
        """.trimIndent())

        // Keep the player alive in PiP. Outside PiP, preserve Movie's original pause.
        val pause = activity.methods.single {
            it.name == "onPause" && it.parameterTypes.isEmpty()
        }
        check(pause.implementation!!.registerCount >= 2)
        pause.addInstructions(0, """
            invoke-virtual {p0}, Landroid/app/Activity;->isInPictureInPictureMode()Z
            move-result v0
            if-eqz v0, :morphe_pip_normal_pause
            invoke-super {p0}, $BASE->onPause()V
            return-void
            :morphe_pip_normal_pause
            nop
        """.trimIndent())

        // Android 8-11: explicit entry when leaving, only if a player exists.
        val leave = ImmutableMethod(
            ACTIVITY, "onUserLeaveHint", emptyList(), "V",
            AccessFlags.PROTECTED.value, null, null,
            MutableMethodImplementation(3)
        ).toMutable()
        leave.addInstructions(0, """
            invoke-super {p0}, $BASE->onUserLeaveHint()V
            sget v0, Landroid/os/Build${';->SDK_INT:I
            const/16 v1, 0x1a
            if-lt v0, v1, :morphe_pip_leave_done
            const/16 v1, 0x1f
            if-ge v0, v1, :morphe_pip_leave_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {p0}, Landroid/app/Activity;->enterPictureInPictureMode()Z
            :morphe_pip_leave_done
            return-void
        """.trimIndent())
        activity.methods.add(leave)
    }
}

@Suppress("unused")
val movieNativePictureInPicturePatch = resourcePatch(
    name = "Movie - Native Picture-in-Picture",
    description = "Automatically enter native Android PiP when leaving the video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipBytecodePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length)
                .mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: playback activity missing from manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges")
                .split('|')
                .filter { it.isNotBlank() }
                .toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}VERSION;->SDK_INT:I
            const/16 v1, 0x1a
            if-lt v0, v1, :morphe_pip_leave_done
            const/16 v1, 0x1f
            if-ge v0, v1, :morphe_pip_leave_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {p0}, Landroid/app/Activity;->enterPictureInPictureMode()Z
            :morphe_pip_leave_done
            return-void
        """.trimIndent())
        activity.methods.add(leave)
    }
}

@Suppress("unused")
val movieNativePictureInPicturePatch = resourcePatch(
    name = "Movie - Native Picture-in-Picture",
    description = "Automatically enter native Android PiP when leaving the video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipBytecodePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length)
                .mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: playback activity missing from manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges")
                .split('|')
                .filter { it.isNotBlank() }
                .toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}Builder;
            invoke-direct {v0}, Landroid/app/PictureInPictureParams${';-><init>()V
            const/4 v1, 0x1
            invoke-virtual {v0, v1}, Landroid/app/PictureInPictureParams$Builder;->setAutoEnterEnabled(Z)Landroid/app/PictureInPictureParams$Builder;
            move-result-object v0
            invoke-virtual {v0}, Landroid/app/PictureInPictureParams$Builder;->build()Landroid/app/PictureInPictureParams;
            move-result-object v0
            invoke-virtual {p0, v0}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
            :morphe_pip_resume_end
            nop
        """.trimIndent())

        // Keep the player alive in PiP. Outside PiP, preserve Movie's original pause.
        val pause = activity.methods.single {
            it.name == "onPause" && it.parameterTypes.isEmpty()
        }
        check(pause.implementation!!.registerCount >= 2)
        pause.addInstructions(0, """
            invoke-virtual {p0}, Landroid/app/Activity;->isInPictureInPictureMode()Z
            move-result v0
            if-eqz v0, :morphe_pip_normal_pause
            invoke-super {p0}, $BASE->onPause()V
            return-void
            :morphe_pip_normal_pause
            nop
        """.trimIndent())

        // Android 8-11: explicit entry when leaving, only if a player exists.
        val leave = ImmutableMethod(
            ACTIVITY, "onUserLeaveHint", emptyList(), "V",
            AccessFlags.PROTECTED.value, null, null,
            MutableMethodImplementation(3)
        ).toMutable()
        leave.addInstructions(0, """
            invoke-super {p0}, $BASE->onUserLeaveHint()V
            sget v0, Landroid/os/Build$VERSION;->SDK_INT:I
            const/16 v1, 0x1a
            if-lt v0, v1, :morphe_pip_leave_done
            const/16 v1, 0x1f
            if-ge v0, v1, :morphe_pip_leave_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {p0}, Landroid/app/Activity;->enterPictureInPictureMode()Z
            :morphe_pip_leave_done
            return-void
        """.trimIndent())
        activity.methods.add(leave)
    }
}

@Suppress("unused")
val movieNativePictureInPicturePatch = resourcePatch(
    name = "Movie - Native Picture-in-Picture",
    description = "Automatically enter native Android PiP when leaving the video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipBytecodePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length)
                .mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: playback activity missing from manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges")
                .split('|')
                .filter { it.isNotBlank() }
                .toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}VERSION;->SDK_INT:I
            const/16 v1, 0x1f
            if-lt v0, v1, :morphe_pip_resume_end
            new-instance v0, Landroid/app/PictureInPictureParams$Builder;
            invoke-direct {v0}, Landroid/app/PictureInPictureParams$Builder;-><init>()V
            const/4 v1, 0x1
            invoke-virtual {v0, v1}, Landroid/app/PictureInPictureParams$Builder;->setAutoEnterEnabled(Z)Landroid/app/PictureInPictureParams$Builder;
            move-result-object v0
            invoke-virtual {v0}, Landroid/app/PictureInPictureParams$Builder;->build()Landroid/app/PictureInPictureParams;
            move-result-object v0
            invoke-virtual {p0, v0}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
            :morphe_pip_resume_end
            nop
        """.trimIndent())

        // Keep the player alive in PiP. Outside PiP, preserve Movie's original pause.
        val pause = activity.methods.single {
            it.name == "onPause" && it.parameterTypes.isEmpty()
        }
        check(pause.implementation!!.registerCount >= 2)
        pause.addInstructions(0, """
            invoke-virtual {p0}, Landroid/app/Activity;->isInPictureInPictureMode()Z
            move-result v0
            if-eqz v0, :morphe_pip_normal_pause
            invoke-super {p0}, $BASE->onPause()V
            return-void
            :morphe_pip_normal_pause
            nop
        """.trimIndent())

        // Android 8-11: explicit entry when leaving, only if a player exists.
        val leave = ImmutableMethod(
            ACTIVITY, "onUserLeaveHint", emptyList(), "V",
            AccessFlags.PROTECTED.value, null, null,
            MutableMethodImplementation(3)
        ).toMutable()
        leave.addInstructions(0, """
            invoke-super {p0}, $BASE->onUserLeaveHint()V
            sget v0, Landroid/os/Build${';->SDK_INT:I
            const/16 v1, 0x1a
            if-lt v0, v1, :morphe_pip_leave_done
            const/16 v1, 0x1f
            if-ge v0, v1, :morphe_pip_leave_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {p0}, Landroid/app/Activity;->enterPictureInPictureMode()Z
            :morphe_pip_leave_done
            return-void
        """.trimIndent())
        activity.methods.add(leave)
    }
}

@Suppress("unused")
val movieNativePictureInPicturePatch = resourcePatch(
    name = "Movie - Native Picture-in-Picture",
    description = "Automatically enter native Android PiP when leaving the video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipBytecodePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length)
                .mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: playback activity missing from manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges")
                .split('|')
                .filter { it.isNotBlank() }
                .toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}VERSION;->SDK_INT:I
            const/16 v1, 0x1a
            if-lt v0, v1, :morphe_pip_leave_done
            const/16 v1, 0x1f
            if-ge v0, v1, :morphe_pip_leave_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {p0}, Landroid/app/Activity;->enterPictureInPictureMode()Z
            :morphe_pip_leave_done
            return-void
        """.trimIndent())
        activity.methods.add(leave)
    }
}

@Suppress("unused")
val movieNativePictureInPicturePatch = resourcePatch(
    name = "Movie - Native Picture-in-Picture",
    description = "Automatically enter native Android PiP when leaving the video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipBytecodePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length)
                .mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: playback activity missing from manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges")
                .split('|')
                .filter { it.isNotBlank() }
                .toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}Builder;-><init>()V
            const/4 v1, 0x1
            invoke-virtual {v0, v1}, Landroid/app/PictureInPictureParams${';->setAutoEnterEnabled(Z)Landroid/app/PictureInPictureParams$Builder;
            move-result-object v0
            invoke-virtual {v0}, Landroid/app/PictureInPictureParams$Builder;->build()Landroid/app/PictureInPictureParams;
            move-result-object v0
            invoke-virtual {p0, v0}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
            :morphe_pip_resume_end
            nop
        """.trimIndent())

        // Keep the player alive in PiP. Outside PiP, preserve Movie's original pause.
        val pause = activity.methods.single {
            it.name == "onPause" && it.parameterTypes.isEmpty()
        }
        check(pause.implementation!!.registerCount >= 2)
        pause.addInstructions(0, """
            invoke-virtual {p0}, Landroid/app/Activity;->isInPictureInPictureMode()Z
            move-result v0
            if-eqz v0, :morphe_pip_normal_pause
            invoke-super {p0}, $BASE->onPause()V
            return-void
            :morphe_pip_normal_pause
            nop
        """.trimIndent())

        // Android 8-11: explicit entry when leaving, only if a player exists.
        val leave = ImmutableMethod(
            ACTIVITY, "onUserLeaveHint", emptyList(), "V",
            AccessFlags.PROTECTED.value, null, null,
            MutableMethodImplementation(3)
        ).toMutable()
        leave.addInstructions(0, """
            invoke-super {p0}, $BASE->onUserLeaveHint()V
            sget v0, Landroid/os/Build$VERSION;->SDK_INT:I
            const/16 v1, 0x1a
            if-lt v0, v1, :morphe_pip_leave_done
            const/16 v1, 0x1f
            if-ge v0, v1, :morphe_pip_leave_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {p0}, Landroid/app/Activity;->enterPictureInPictureMode()Z
            :morphe_pip_leave_done
            return-void
        """.trimIndent())
        activity.methods.add(leave)
    }
}

@Suppress("unused")
val movieNativePictureInPicturePatch = resourcePatch(
    name = "Movie - Native Picture-in-Picture",
    description = "Automatically enter native Android PiP when leaving the video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipBytecodePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length)
                .mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: playback activity missing from manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges")
                .split('|')
                .filter { it.isNotBlank() }
                .toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}VERSION;->SDK_INT:I
            const/16 v1, 0x1f
            if-lt v0, v1, :morphe_pip_resume_end
            new-instance v0, Landroid/app/PictureInPictureParams$Builder;
            invoke-direct {v0}, Landroid/app/PictureInPictureParams$Builder;-><init>()V
            const/4 v1, 0x1
            invoke-virtual {v0, v1}, Landroid/app/PictureInPictureParams$Builder;->setAutoEnterEnabled(Z)Landroid/app/PictureInPictureParams$Builder;
            move-result-object v0
            invoke-virtual {v0}, Landroid/app/PictureInPictureParams$Builder;->build()Landroid/app/PictureInPictureParams;
            move-result-object v0
            invoke-virtual {p0, v0}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
            :morphe_pip_resume_end
            nop
        """.trimIndent())

        // Keep the player alive in PiP. Outside PiP, preserve Movie's original pause.
        val pause = activity.methods.single {
            it.name == "onPause" && it.parameterTypes.isEmpty()
        }
        check(pause.implementation!!.registerCount >= 2)
        pause.addInstructions(0, """
            invoke-virtual {p0}, Landroid/app/Activity;->isInPictureInPictureMode()Z
            move-result v0
            if-eqz v0, :morphe_pip_normal_pause
            invoke-super {p0}, $BASE->onPause()V
            return-void
            :morphe_pip_normal_pause
            nop
        """.trimIndent())

        // Android 8-11: explicit entry when leaving, only if a player exists.
        val leave = ImmutableMethod(
            ACTIVITY, "onUserLeaveHint", emptyList(), "V",
            AccessFlags.PROTECTED.value, null, null,
            MutableMethodImplementation(3)
        ).toMutable()
        leave.addInstructions(0, """
            invoke-super {p0}, $BASE->onUserLeaveHint()V
            sget v0, Landroid/os/Build${';->SDK_INT:I
            const/16 v1, 0x1a
            if-lt v0, v1, :morphe_pip_leave_done
            const/16 v1, 0x1f
            if-ge v0, v1, :morphe_pip_leave_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {p0}, Landroid/app/Activity;->enterPictureInPictureMode()Z
            :morphe_pip_leave_done
            return-void
        """.trimIndent())
        activity.methods.add(leave)
    }
}

@Suppress("unused")
val movieNativePictureInPicturePatch = resourcePatch(
    name = "Movie - Native Picture-in-Picture",
    description = "Automatically enter native Android PiP when leaving the video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipBytecodePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length)
                .mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: playback activity missing from manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges")
                .split('|')
                .filter { it.isNotBlank() }
                .toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}VERSION;->SDK_INT:I
            const/16 v1, 0x1a
            if-lt v0, v1, :morphe_pip_leave_done
            const/16 v1, 0x1f
            if-ge v0, v1, :morphe_pip_leave_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {p0}, Landroid/app/Activity;->enterPictureInPictureMode()Z
            :morphe_pip_leave_done
            return-void
        """.trimIndent())
        activity.methods.add(leave)
    }
}

@Suppress("unused")
val movieNativePictureInPicturePatch = resourcePatch(
    name = "Movie - Native Picture-in-Picture",
    description = "Automatically enter native Android PiP when leaving the video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipBytecodePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length)
                .mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: playback activity missing from manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges")
                .split('|')
                .filter { it.isNotBlank() }
                .toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}Builder;->setAutoEnterEnabled(Z)Landroid/app/PictureInPictureParams${';
            move-result-object v0
            invoke-virtual {v0}, Landroid/app/PictureInPictureParams$Builder;->build()Landroid/app/PictureInPictureParams;
            move-result-object v0
            invoke-virtual {p0, v0}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
            :morphe_pip_resume_end
            nop
        """.trimIndent())

        // Keep the player alive in PiP. Outside PiP, preserve Movie's original pause.
        val pause = activity.methods.single {
            it.name == "onPause" && it.parameterTypes.isEmpty()
        }
        check(pause.implementation!!.registerCount >= 2)
        pause.addInstructions(0, """
            invoke-virtual {p0}, Landroid/app/Activity;->isInPictureInPictureMode()Z
            move-result v0
            if-eqz v0, :morphe_pip_normal_pause
            invoke-super {p0}, $BASE->onPause()V
            return-void
            :morphe_pip_normal_pause
            nop
        """.trimIndent())

        // Android 8-11: explicit entry when leaving, only if a player exists.
        val leave = ImmutableMethod(
            ACTIVITY, "onUserLeaveHint", emptyList(), "V",
            AccessFlags.PROTECTED.value, null, null,
            MutableMethodImplementation(3)
        ).toMutable()
        leave.addInstructions(0, """
            invoke-super {p0}, $BASE->onUserLeaveHint()V
            sget v0, Landroid/os/Build$VERSION;->SDK_INT:I
            const/16 v1, 0x1a
            if-lt v0, v1, :morphe_pip_leave_done
            const/16 v1, 0x1f
            if-ge v0, v1, :morphe_pip_leave_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {p0}, Landroid/app/Activity;->enterPictureInPictureMode()Z
            :morphe_pip_leave_done
            return-void
        """.trimIndent())
        activity.methods.add(leave)
    }
}

@Suppress("unused")
val movieNativePictureInPicturePatch = resourcePatch(
    name = "Movie - Native Picture-in-Picture",
    description = "Automatically enter native Android PiP when leaving the video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipBytecodePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length)
                .mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: playback activity missing from manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges")
                .split('|')
                .filter { it.isNotBlank() }
                .toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}VERSION;->SDK_INT:I
            const/16 v1, 0x1f
            if-lt v0, v1, :morphe_pip_resume_end
            new-instance v0, Landroid/app/PictureInPictureParams$Builder;
            invoke-direct {v0}, Landroid/app/PictureInPictureParams$Builder;-><init>()V
            const/4 v1, 0x1
            invoke-virtual {v0, v1}, Landroid/app/PictureInPictureParams$Builder;->setAutoEnterEnabled(Z)Landroid/app/PictureInPictureParams$Builder;
            move-result-object v0
            invoke-virtual {v0}, Landroid/app/PictureInPictureParams$Builder;->build()Landroid/app/PictureInPictureParams;
            move-result-object v0
            invoke-virtual {p0, v0}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
            :morphe_pip_resume_end
            nop
        """.trimIndent())

        // Keep the player alive in PiP. Outside PiP, preserve Movie's original pause.
        val pause = activity.methods.single {
            it.name == "onPause" && it.parameterTypes.isEmpty()
        }
        check(pause.implementation!!.registerCount >= 2)
        pause.addInstructions(0, """
            invoke-virtual {p0}, Landroid/app/Activity;->isInPictureInPictureMode()Z
            move-result v0
            if-eqz v0, :morphe_pip_normal_pause
            invoke-super {p0}, $BASE->onPause()V
            return-void
            :morphe_pip_normal_pause
            nop
        """.trimIndent())

        // Android 8-11: explicit entry when leaving, only if a player exists.
        val leave = ImmutableMethod(
            ACTIVITY, "onUserLeaveHint", emptyList(), "V",
            AccessFlags.PROTECTED.value, null, null,
            MutableMethodImplementation(3)
        ).toMutable()
        leave.addInstructions(0, """
            invoke-super {p0}, $BASE->onUserLeaveHint()V
            sget v0, Landroid/os/Build${';->SDK_INT:I
            const/16 v1, 0x1a
            if-lt v0, v1, :morphe_pip_leave_done
            const/16 v1, 0x1f
            if-ge v0, v1, :morphe_pip_leave_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {p0}, Landroid/app/Activity;->enterPictureInPictureMode()Z
            :morphe_pip_leave_done
            return-void
        """.trimIndent())
        activity.methods.add(leave)
    }
}

@Suppress("unused")
val movieNativePictureInPicturePatch = resourcePatch(
    name = "Movie - Native Picture-in-Picture",
    description = "Automatically enter native Android PiP when leaving the video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipBytecodePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length)
                .mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: playback activity missing from manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges")
                .split('|')
                .filter { it.isNotBlank() }
                .toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}VERSION;->SDK_INT:I
            const/16 v1, 0x1a
            if-lt v0, v1, :morphe_pip_leave_done
            const/16 v1, 0x1f
            if-ge v0, v1, :morphe_pip_leave_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {p0}, Landroid/app/Activity;->enterPictureInPictureMode()Z
            :morphe_pip_leave_done
            return-void
        """.trimIndent())
        activity.methods.add(leave)
    }
}

@Suppress("unused")
val movieNativePictureInPicturePatch = resourcePatch(
    name = "Movie - Native Picture-in-Picture",
    description = "Automatically enter native Android PiP when leaving the video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipBytecodePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length)
                .mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: playback activity missing from manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges")
                .split('|')
                .filter { it.isNotBlank() }
                .toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}Builder;
            move-result-object v0
            invoke-virtual {v0}, Landroid/app/PictureInPictureParams${';->build()Landroid/app/PictureInPictureParams;
            move-result-object v0
            invoke-virtual {p0, v0}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
            :morphe_pip_resume_end
            nop
        """.trimIndent())

        // Keep the player alive in PiP. Outside PiP, preserve Movie's original pause.
        val pause = activity.methods.single {
            it.name == "onPause" && it.parameterTypes.isEmpty()
        }
        check(pause.implementation!!.registerCount >= 2)
        pause.addInstructions(0, """
            invoke-virtual {p0}, Landroid/app/Activity;->isInPictureInPictureMode()Z
            move-result v0
            if-eqz v0, :morphe_pip_normal_pause
            invoke-super {p0}, $BASE->onPause()V
            return-void
            :morphe_pip_normal_pause
            nop
        """.trimIndent())

        // Android 8-11: explicit entry when leaving, only if a player exists.
        val leave = ImmutableMethod(
            ACTIVITY, "onUserLeaveHint", emptyList(), "V",
            AccessFlags.PROTECTED.value, null, null,
            MutableMethodImplementation(3)
        ).toMutable()
        leave.addInstructions(0, """
            invoke-super {p0}, $BASE->onUserLeaveHint()V
            sget v0, Landroid/os/Build$VERSION;->SDK_INT:I
            const/16 v1, 0x1a
            if-lt v0, v1, :morphe_pip_leave_done
            const/16 v1, 0x1f
            if-ge v0, v1, :morphe_pip_leave_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {p0}, Landroid/app/Activity;->enterPictureInPictureMode()Z
            :morphe_pip_leave_done
            return-void
        """.trimIndent())
        activity.methods.add(leave)
    }
}

@Suppress("unused")
val movieNativePictureInPicturePatch = resourcePatch(
    name = "Movie - Native Picture-in-Picture",
    description = "Automatically enter native Android PiP when leaving the video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipBytecodePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length)
                .mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: playback activity missing from manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges")
                .split('|')
                .filter { it.isNotBlank() }
                .toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}VERSION;->SDK_INT:I
            const/16 v1, 0x1f
            if-lt v0, v1, :morphe_pip_resume_end
            new-instance v0, Landroid/app/PictureInPictureParams$Builder;
            invoke-direct {v0}, Landroid/app/PictureInPictureParams$Builder;-><init>()V
            const/4 v1, 0x1
            invoke-virtual {v0, v1}, Landroid/app/PictureInPictureParams$Builder;->setAutoEnterEnabled(Z)Landroid/app/PictureInPictureParams$Builder;
            move-result-object v0
            invoke-virtual {v0}, Landroid/app/PictureInPictureParams$Builder;->build()Landroid/app/PictureInPictureParams;
            move-result-object v0
            invoke-virtual {p0, v0}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
            :morphe_pip_resume_end
            nop
        """.trimIndent())

        // Keep the player alive in PiP. Outside PiP, preserve Movie's original pause.
        val pause = activity.methods.single {
            it.name == "onPause" && it.parameterTypes.isEmpty()
        }
        check(pause.implementation!!.registerCount >= 2)
        pause.addInstructions(0, """
            invoke-virtual {p0}, Landroid/app/Activity;->isInPictureInPictureMode()Z
            move-result v0
            if-eqz v0, :morphe_pip_normal_pause
            invoke-super {p0}, $BASE->onPause()V
            return-void
            :morphe_pip_normal_pause
            nop
        """.trimIndent())

        // Android 8-11: explicit entry when leaving, only if a player exists.
        val leave = ImmutableMethod(
            ACTIVITY, "onUserLeaveHint", emptyList(), "V",
            AccessFlags.PROTECTED.value, null, null,
            MutableMethodImplementation(3)
        ).toMutable()
        leave.addInstructions(0, """
            invoke-super {p0}, $BASE->onUserLeaveHint()V
            sget v0, Landroid/os/Build${';->SDK_INT:I
            const/16 v1, 0x1a
            if-lt v0, v1, :morphe_pip_leave_done
            const/16 v1, 0x1f
            if-ge v0, v1, :morphe_pip_leave_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {p0}, Landroid/app/Activity;->enterPictureInPictureMode()Z
            :morphe_pip_leave_done
            return-void
        """.trimIndent())
        activity.methods.add(leave)
    }
}

@Suppress("unused")
val movieNativePictureInPicturePatch = resourcePatch(
    name = "Movie - Native Picture-in-Picture",
    description = "Automatically enter native Android PiP when leaving the video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipBytecodePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length)
                .mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: playback activity missing from manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges")
                .split('|')
                .filter { it.isNotBlank() }
                .toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}VERSION;->SDK_INT:I
            const/16 v1, 0x1a
            if-lt v0, v1, :morphe_pip_leave_done
            const/16 v1, 0x1f
            if-ge v0, v1, :morphe_pip_leave_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {p0}, Landroid/app/Activity;->enterPictureInPictureMode()Z
            :morphe_pip_leave_done
            return-void
        """.trimIndent())
        activity.methods.add(leave)
    }
}

@Suppress("unused")
val movieNativePictureInPicturePatch = resourcePatch(
    name = "Movie - Native Picture-in-Picture",
    description = "Automatically enter native Android PiP when leaving the video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipBytecodePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length)
                .mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: playback activity missing from manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges")
                .split('|')
                .filter { it.isNotBlank() }
                .toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}Builder;->build()Landroid/app/PictureInPictureParams;
            move-result-object v0
            invoke-virtual {p0, v0}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
            :morphe_pip_resume_end
            nop
        """.trimIndent())

        // Keep the player alive in PiP. Outside PiP, preserve Movie's original pause.
        val pause = activity.methods.single {
            it.name == "onPause" && it.parameterTypes.isEmpty()
        }
        check(pause.implementation!!.registerCount >= 2)
        pause.addInstructions(0, """
            invoke-virtual {p0}, Landroid/app/Activity;->isInPictureInPictureMode()Z
            move-result v0
            if-eqz v0, :morphe_pip_normal_pause
            invoke-super {p0}, $BASE->onPause()V
            return-void
            :morphe_pip_normal_pause
            nop
        """.trimIndent())

        // Android 8-11: explicit entry when leaving, only if a player exists.
        val leave = ImmutableMethod(
            ACTIVITY, "onUserLeaveHint", emptyList(), "V",
            AccessFlags.PROTECTED.value, null, null,
            MutableMethodImplementation(3)
        ).toMutable()
        leave.addInstructions(0, """
            invoke-super {p0}, $BASE->onUserLeaveHint()V
            sget v0, Landroid/os/Build$VERSION;->SDK_INT:I
            const/16 v1, 0x1a
            if-lt v0, v1, :morphe_pip_leave_done
            const/16 v1, 0x1f
            if-ge v0, v1, :morphe_pip_leave_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {p0}, Landroid/app/Activity;->enterPictureInPictureMode()Z
            :morphe_pip_leave_done
            return-void
        """.trimIndent())
        activity.methods.add(leave)
    }
}

@Suppress("unused")
val movieNativePictureInPicturePatch = resourcePatch(
    name = "Movie - Native Picture-in-Picture",
    description = "Automatically enter native Android PiP when leaving the video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipBytecodePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length)
                .mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: playback activity missing from manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges")
                .split('|')
                .filter { it.isNotBlank() }
                .toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}VERSION;->SDK_INT:I
            const/16 v1, 0x1f
            if-lt v0, v1, :morphe_pip_resume_end
            new-instance v0, Landroid/app/PictureInPictureParams${';
            invoke-direct {v0}, Landroid/app/PictureInPictureParams$Builder;-><init>()V
            const/4 v1, 0x1
            invoke-virtual {v0, v1}, Landroid/app/PictureInPictureParams$Builder;->setAutoEnterEnabled(Z)Landroid/app/PictureInPictureParams$Builder;
            move-result-object v0
            invoke-virtual {v0}, Landroid/app/PictureInPictureParams$Builder;->build()Landroid/app/PictureInPictureParams;
            move-result-object v0
            invoke-virtual {p0, v0}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
            :morphe_pip_resume_end
            nop
        """.trimIndent())

        // Keep the player alive in PiP. Outside PiP, preserve Movie's original pause.
        val pause = activity.methods.single {
            it.name == "onPause" && it.parameterTypes.isEmpty()
        }
        check(pause.implementation!!.registerCount >= 2)
        pause.addInstructions(0, """
            invoke-virtual {p0}, Landroid/app/Activity;->isInPictureInPictureMode()Z
            move-result v0
            if-eqz v0, :morphe_pip_normal_pause
            invoke-super {p0}, $BASE->onPause()V
            return-void
            :morphe_pip_normal_pause
            nop
        """.trimIndent())

        // Android 8-11: explicit entry when leaving, only if a player exists.
        val leave = ImmutableMethod(
            ACTIVITY, "onUserLeaveHint", emptyList(), "V",
            AccessFlags.PROTECTED.value, null, null,
            MutableMethodImplementation(3)
        ).toMutable()
        leave.addInstructions(0, """
            invoke-super {p0}, $BASE->onUserLeaveHint()V
            sget v0, Landroid/os/Build${';->SDK_INT:I
            const/16 v1, 0x1a
            if-lt v0, v1, :morphe_pip_leave_done
            const/16 v1, 0x1f
            if-ge v0, v1, :morphe_pip_leave_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {p0}, Landroid/app/Activity;->enterPictureInPictureMode()Z
            :morphe_pip_leave_done
            return-void
        """.trimIndent())
        activity.methods.add(leave)
    }
}

@Suppress("unused")
val movieNativePictureInPicturePatch = resourcePatch(
    name = "Movie - Native Picture-in-Picture",
    description = "Automatically enter native Android PiP when leaving the video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipBytecodePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length)
                .mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: playback activity missing from manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges")
                .split('|')
                .filter { it.isNotBlank() }
                .toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}VERSION;->SDK_INT:I
            const/16 v1, 0x1a
            if-lt v0, v1, :morphe_pip_leave_done
            const/16 v1, 0x1f
            if-ge v0, v1, :morphe_pip_leave_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {p0}, Landroid/app/Activity;->enterPictureInPictureMode()Z
            :morphe_pip_leave_done
            return-void
        """.trimIndent())
        activity.methods.add(leave)
    }
}

@Suppress("unused")
val movieNativePictureInPicturePatch = resourcePatch(
    name = "Movie - Native Picture-in-Picture",
    description = "Automatically enter native Android PiP when leaving the video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipBytecodePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length)
                .mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: playback activity missing from manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges")
                .split('|')
                .filter { it.isNotBlank() }
                .toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}Builder;
            invoke-direct {v0}, Landroid/app/PictureInPictureParams${';-><init>()V
            const/4 v1, 0x1
            invoke-virtual {v0, v1}, Landroid/app/PictureInPictureParams$Builder;->setAutoEnterEnabled(Z)Landroid/app/PictureInPictureParams$Builder;
            move-result-object v0
            invoke-virtual {v0}, Landroid/app/PictureInPictureParams$Builder;->build()Landroid/app/PictureInPictureParams;
            move-result-object v0
            invoke-virtual {p0, v0}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
            :morphe_pip_resume_end
            nop
        """.trimIndent())

        // Keep the player alive in PiP. Outside PiP, preserve Movie's original pause.
        val pause = activity.methods.single {
            it.name == "onPause" && it.parameterTypes.isEmpty()
        }
        check(pause.implementation!!.registerCount >= 2)
        pause.addInstructions(0, """
            invoke-virtual {p0}, Landroid/app/Activity;->isInPictureInPictureMode()Z
            move-result v0
            if-eqz v0, :morphe_pip_normal_pause
            invoke-super {p0}, $BASE->onPause()V
            return-void
            :morphe_pip_normal_pause
            nop
        """.trimIndent())

        // Android 8-11: explicit entry when leaving, only if a player exists.
        val leave = ImmutableMethod(
            ACTIVITY, "onUserLeaveHint", emptyList(), "V",
            AccessFlags.PROTECTED.value, null, null,
            MutableMethodImplementation(3)
        ).toMutable()
        leave.addInstructions(0, """
            invoke-super {p0}, $BASE->onUserLeaveHint()V
            sget v0, Landroid/os/Build${';->SDK_INT:I
            const/16 v1, 0x1a
            if-lt v0, v1, :morphe_pip_leave_done
            const/16 v1, 0x1f
            if-ge v0, v1, :morphe_pip_leave_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {p0}, Landroid/app/Activity;->enterPictureInPictureMode()Z
            :morphe_pip_leave_done
            return-void
        """.trimIndent())
        activity.methods.add(leave)
    }
}

@Suppress("unused")
val movieNativePictureInPicturePatch = resourcePatch(
    name = "Movie - Native Picture-in-Picture",
    description = "Automatically enter native Android PiP when leaving the video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipBytecodePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length)
                .mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: playback activity missing from manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges")
                .split('|')
                .filter { it.isNotBlank() }
                .toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}VERSION;->SDK_INT:I
            const/16 v1, 0x1a
            if-lt v0, v1, :morphe_pip_leave_done
            const/16 v1, 0x1f
            if-ge v0, v1, :morphe_pip_leave_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {p0}, Landroid/app/Activity;->enterPictureInPictureMode()Z
            :morphe_pip_leave_done
            return-void
        """.trimIndent())
        activity.methods.add(leave)
    }
}

@Suppress("unused")
val movieNativePictureInPicturePatch = resourcePatch(
    name = "Movie - Native Picture-in-Picture",
    description = "Automatically enter native Android PiP when leaving the video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipBytecodePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length)
                .mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: playback activity missing from manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges")
                .split('|')
                .filter { it.isNotBlank() }
                .toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}Builder;-><init>()V
            const/4 v1, 0x1
            invoke-virtual {v0, v1}, Landroid/app/PictureInPictureParams${';->setAutoEnterEnabled(Z)Landroid/app/PictureInPictureParams$Builder;
            move-result-object v0
            invoke-virtual {v0}, Landroid/app/PictureInPictureParams$Builder;->build()Landroid/app/PictureInPictureParams;
            move-result-object v0
            invoke-virtual {p0, v0}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
            :morphe_pip_resume_end
            nop
        """.trimIndent())

        // Keep the player alive in PiP. Outside PiP, preserve Movie's original pause.
        val pause = activity.methods.single {
            it.name == "onPause" && it.parameterTypes.isEmpty()
        }
        check(pause.implementation!!.registerCount >= 2)
        pause.addInstructions(0, """
            invoke-virtual {p0}, Landroid/app/Activity;->isInPictureInPictureMode()Z
            move-result v0
            if-eqz v0, :morphe_pip_normal_pause
            invoke-super {p0}, $BASE->onPause()V
            return-void
            :morphe_pip_normal_pause
            nop
        """.trimIndent())

        // Android 8-11: explicit entry when leaving, only if a player exists.
        val leave = ImmutableMethod(
            ACTIVITY, "onUserLeaveHint", emptyList(), "V",
            AccessFlags.PROTECTED.value, null, null,
            MutableMethodImplementation(3)
        ).toMutable()
        leave.addInstructions(0, """
            invoke-super {p0}, $BASE->onUserLeaveHint()V
            sget v0, Landroid/os/Build${';->SDK_INT:I
            const/16 v1, 0x1a
            if-lt v0, v1, :morphe_pip_leave_done
            const/16 v1, 0x1f
            if-ge v0, v1, :morphe_pip_leave_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {p0}, Landroid/app/Activity;->enterPictureInPictureMode()Z
            :morphe_pip_leave_done
            return-void
        """.trimIndent())
        activity.methods.add(leave)
    }
}

@Suppress("unused")
val movieNativePictureInPicturePatch = resourcePatch(
    name = "Movie - Native Picture-in-Picture",
    description = "Automatically enter native Android PiP when leaving the video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipBytecodePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length)
                .mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: playback activity missing from manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges")
                .split('|')
                .filter { it.isNotBlank() }
                .toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}VERSION;->SDK_INT:I
            const/16 v1, 0x1a
            if-lt v0, v1, :morphe_pip_leave_done
            const/16 v1, 0x1f
            if-ge v0, v1, :morphe_pip_leave_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {p0}, Landroid/app/Activity;->enterPictureInPictureMode()Z
            :morphe_pip_leave_done
            return-void
        """.trimIndent())
        activity.methods.add(leave)
    }
}

@Suppress("unused")
val movieNativePictureInPicturePatch = resourcePatch(
    name = "Movie - Native Picture-in-Picture",
    description = "Automatically enter native Android PiP when leaving the video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipBytecodePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length)
                .mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: playback activity missing from manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges")
                .split('|')
                .filter { it.isNotBlank() }
                .toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}Builder;->setAutoEnterEnabled(Z)Landroid/app/PictureInPictureParams${';
            move-result-object v0
            invoke-virtual {v0}, Landroid/app/PictureInPictureParams$Builder;->build()Landroid/app/PictureInPictureParams;
            move-result-object v0
            invoke-virtual {p0, v0}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
            :morphe_pip_resume_end
            nop
        """.trimIndent())

        // Keep the player alive in PiP. Outside PiP, preserve Movie's original pause.
        val pause = activity.methods.single {
            it.name == "onPause" && it.parameterTypes.isEmpty()
        }
        check(pause.implementation!!.registerCount >= 2)
        pause.addInstructions(0, """
            invoke-virtual {p0}, Landroid/app/Activity;->isInPictureInPictureMode()Z
            move-result v0
            if-eqz v0, :morphe_pip_normal_pause
            invoke-super {p0}, $BASE->onPause()V
            return-void
            :morphe_pip_normal_pause
            nop
        """.trimIndent())

        // Android 8-11: explicit entry when leaving, only if a player exists.
        val leave = ImmutableMethod(
            ACTIVITY, "onUserLeaveHint", emptyList(), "V",
            AccessFlags.PROTECTED.value, null, null,
            MutableMethodImplementation(3)
        ).toMutable()
        leave.addInstructions(0, """
            invoke-super {p0}, $BASE->onUserLeaveHint()V
            sget v0, Landroid/os/Build${';->SDK_INT:I
            const/16 v1, 0x1a
            if-lt v0, v1, :morphe_pip_leave_done
            const/16 v1, 0x1f
            if-ge v0, v1, :morphe_pip_leave_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {p0}, Landroid/app/Activity;->enterPictureInPictureMode()Z
            :morphe_pip_leave_done
            return-void
        """.trimIndent())
        activity.methods.add(leave)
    }
}

@Suppress("unused")
val movieNativePictureInPicturePatch = resourcePatch(
    name = "Movie - Native Picture-in-Picture",
    description = "Automatically enter native Android PiP when leaving the video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipBytecodePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length)
                .mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: playback activity missing from manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges")
                .split('|')
                .filter { it.isNotBlank() }
                .toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}VERSION;->SDK_INT:I
            const/16 v1, 0x1a
            if-lt v0, v1, :morphe_pip_leave_done
            const/16 v1, 0x1f
            if-ge v0, v1, :morphe_pip_leave_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {p0}, Landroid/app/Activity;->enterPictureInPictureMode()Z
            :morphe_pip_leave_done
            return-void
        """.trimIndent())
        activity.methods.add(leave)
    }
}

@Suppress("unused")
val movieNativePictureInPicturePatch = resourcePatch(
    name = "Movie - Native Picture-in-Picture",
    description = "Automatically enter native Android PiP when leaving the video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipBytecodePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length)
                .mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: playback activity missing from manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges")
                .split('|')
                .filter { it.isNotBlank() }
                .toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}Builder;
            move-result-object v0
            invoke-virtual {v0}, Landroid/app/PictureInPictureParams${';->build()Landroid/app/PictureInPictureParams;
            move-result-object v0
            invoke-virtual {p0, v0}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
            :morphe_pip_resume_end
            nop
        """.trimIndent())

        // Keep the player alive in PiP. Outside PiP, preserve Movie's original pause.
        val pause = activity.methods.single {
            it.name == "onPause" && it.parameterTypes.isEmpty()
        }
        check(pause.implementation!!.registerCount >= 2)
        pause.addInstructions(0, """
            invoke-virtual {p0}, Landroid/app/Activity;->isInPictureInPictureMode()Z
            move-result v0
            if-eqz v0, :morphe_pip_normal_pause
            invoke-super {p0}, $BASE->onPause()V
            return-void
            :morphe_pip_normal_pause
            nop
        """.trimIndent())

        // Android 8-11: explicit entry when leaving, only if a player exists.
        val leave = ImmutableMethod(
            ACTIVITY, "onUserLeaveHint", emptyList(), "V",
            AccessFlags.PROTECTED.value, null, null,
            MutableMethodImplementation(3)
        ).toMutable()
        leave.addInstructions(0, """
            invoke-super {p0}, $BASE->onUserLeaveHint()V
            sget v0, Landroid/os/Build${';->SDK_INT:I
            const/16 v1, 0x1a
            if-lt v0, v1, :morphe_pip_leave_done
            const/16 v1, 0x1f
            if-ge v0, v1, :morphe_pip_leave_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {p0}, Landroid/app/Activity;->enterPictureInPictureMode()Z
            :morphe_pip_leave_done
            return-void
        """.trimIndent())
        activity.methods.add(leave)
    }
}

@Suppress("unused")
val movieNativePictureInPicturePatch = resourcePatch(
    name = "Movie - Native Picture-in-Picture",
    description = "Automatically enter native Android PiP when leaving the video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipBytecodePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length)
                .mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: playback activity missing from manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges")
                .split('|')
                .filter { it.isNotBlank() }
                .toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}VERSION;->SDK_INT:I
            const/16 v1, 0x1a
            if-lt v0, v1, :morphe_pip_leave_done
            const/16 v1, 0x1f
            if-ge v0, v1, :morphe_pip_leave_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {p0}, Landroid/app/Activity;->enterPictureInPictureMode()Z
            :morphe_pip_leave_done
            return-void
        """.trimIndent())
        activity.methods.add(leave)
    }
}

@Suppress("unused")
val movieNativePictureInPicturePatch = resourcePatch(
    name = "Movie - Native Picture-in-Picture",
    description = "Automatically enter native Android PiP when leaving the video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipBytecodePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length)
                .mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: playback activity missing from manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges")
                .split('|')
                .filter { it.isNotBlank() }
                .toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}Builder;->build()Landroid/app/PictureInPictureParams;
            move-result-object v0
            invoke-virtual {p0, v0}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
            :morphe_pip_resume_end
            nop
        """.trimIndent())

        // Keep the player alive in PiP. Outside PiP, preserve Movie's original pause.
        val pause = activity.methods.single {
            it.name == "onPause" && it.parameterTypes.isEmpty()
        }
        check(pause.implementation!!.registerCount >= 2)
        pause.addInstructions(0, """
            invoke-virtual {p0}, Landroid/app/Activity;->isInPictureInPictureMode()Z
            move-result v0
            if-eqz v0, :morphe_pip_normal_pause
            invoke-super {p0}, $BASE->onPause()V
            return-void
            :morphe_pip_normal_pause
            nop
        """.trimIndent())

        // Android 8-11: explicit entry when leaving, only if a player exists.
        val leave = ImmutableMethod(
            ACTIVITY, "onUserLeaveHint", emptyList(), "V",
            AccessFlags.PROTECTED.value, null, null,
            MutableMethodImplementation(3)
        ).toMutable()
        leave.addInstructions(0, """
            invoke-super {p0}, $BASE->onUserLeaveHint()V
            sget v0, Landroid/os/Build${';->SDK_INT:I
            const/16 v1, 0x1a
            if-lt v0, v1, :morphe_pip_leave_done
            const/16 v1, 0x1f
            if-ge v0, v1, :morphe_pip_leave_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {p0}, Landroid/app/Activity;->enterPictureInPictureMode()Z
            :morphe_pip_leave_done
            return-void
        """.trimIndent())
        activity.methods.add(leave)
    }
}

@Suppress("unused")
val movieNativePictureInPicturePatch = resourcePatch(
    name = "Movie - Native Picture-in-Picture",
    description = "Automatically enter native Android PiP when leaving the video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipBytecodePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length)
                .mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: playback activity missing from manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges")
                .split('|')
                .filter { it.isNotBlank() }
                .toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}VERSION;->SDK_INT:I
            const/16 v1, 0x1a
            if-lt v0, v1, :morphe_pip_leave_done
            const/16 v1, 0x1f
            if-ge v0, v1, :morphe_pip_leave_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {p0}, Landroid/app/Activity;->enterPictureInPictureMode()Z
            :morphe_pip_leave_done
            return-void
        """.trimIndent())
        activity.methods.add(leave)
    }
}

@Suppress("unused")
val movieNativePictureInPicturePatch = resourcePatch(
    name = "Movie - Native Picture-in-Picture",
    description = "Automatically enter native Android PiP when leaving the video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipBytecodePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length)
                .mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: playback activity missing from manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges")
                .split('|')
                .filter { it.isNotBlank() }
                .toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
