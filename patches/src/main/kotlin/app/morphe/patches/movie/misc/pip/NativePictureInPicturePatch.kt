package app.morphe.patches.movie.misc.pip

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import org.w3c.dom.Element

private const val ACTIVITY = "Lcom/mgs/carparking/ui/homecontent/videodetail/VideoPlayDetailActivity;"
private const val BASE = "Lcom/mgs/carparking/app/BaseActivity;"

private val moviePipRuntimePatch = bytecodePatch {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    execute {
        val activity = mutableClassDefBy(ACTIVITY)
        val resume = activity.methods.single { it.name == "onResume" && it.parameterTypes.isEmpty() }
        check(resume.implementation != null && resume.implementation!!.registerCount >= 5) {
            "Movie PiP: unexpected onResume register layout"
        }

        // Enable auto-enter only when an ExoPlayer instance exists and is playing.
        resume.addInstructions(0, """
            sget v0, Landroid/os/Build${';->SDK_INT:I
            const/16 v1, 0x1f
            if-lt v0, v1, :morphe_pip_resume_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_resume_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_resume_done
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_resume_done
            new-instance v0, Landroid/app/PictureInPictureParams${';
            invoke-direct {v0}, Landroid/app/PictureInPictureParams\$Builder;-><init>()V
            const/4 v1, 0x1
            invoke-virtual {v0, v1}, Landroid/app/PictureInPictureParams\$Builder;->setAutoEnterEnabled(Z)Landroid/app/PictureInPictureParams\$Builder;
            move-result-object v0
            invoke-virtual {v0}, Landroid/app/PictureInPictureParams\$Builder;->build()Landroid/app/PictureInPictureParams;
            move-result-object v0
            invoke-virtual {p0, v0}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
            :morphe_pip_resume_done
            nop
        """.trimIndent())

        // Preserve history and other onPause side effects; skip only the player pause in PiP.
        val pause = activity.methods.single { it.name == "onPause" && it.parameterTypes.isEmpty() }
        val pauseImpl = pause.implementation ?: error("Movie PiP: onPause has no implementation")
        check(pauseImpl.registerCount >= 2)
        val calls = pauseImpl.instructions.withIndex().filter { (_, instruction) ->
            (instruction as? ReferenceInstruction)?.reference?.toString() == "Lw/a;->L()V"
        }
        check(calls.size == 1) { "Movie PiP: expected one player pause call, found ${calls.size}" }
        val index = calls.single().index
        pause.addInstructions(index + 1, """
            :morphe_pip_after_pause
            nop
        """.trimIndent())
        pause.addInstructions(index, """
            invoke-virtual {p0}, Landroid/app/Activity;->isInPictureInPictureMode()Z
            move-result v0
            if-nez v0, :morphe_pip_after_pause
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
        """.trimIndent())

        check(activity.methods.none { it.name == "onUserLeaveHint" }) {
            "Movie PiP: onUserLeaveHint already exists"
        }
        val leave = ImmutableMethod(
            ACTIVITY, "onUserLeaveHint", emptyList(), "V",
            AccessFlags.PROTECTED.value, null, null, MutableMethodImplementation(3)
        ).toMutable()
        leave.addInstructions(0, """
            invoke-super {p0}, $BASE->onUserLeaveHint()V
            sget v0, Landroid/os/Build\$VERSION;->SDK_INT:I
            const/16 v1, 0x1a
            if-lt v0, v1, :morphe_pip_leave_done
            const/16 v1, 0x1f
            if-ge v0, v1, :morphe_pip_leave_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_leave_done
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
    description = "Enter native Android PiP when leaving the Movie video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipRuntimePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length).mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: target activity not found in manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges").split('|')
                .filter { it.isNotBlank() }.toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}VERSION;->SDK_INT:I
            const/16 v1, 0x1f
            if-lt v0, v1, :morphe_pip_resume_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_resume_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_resume_done
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_resume_done
            new-instance v0, Landroid/app/PictureInPictureParams\$Builder;
            invoke-direct {v0}, Landroid/app/PictureInPictureParams\$Builder;-><init>()V
            const/4 v1, 0x1
            invoke-virtual {v0, v1}, Landroid/app/PictureInPictureParams\$Builder;->setAutoEnterEnabled(Z)Landroid/app/PictureInPictureParams\$Builder;
            move-result-object v0
            invoke-virtual {v0}, Landroid/app/PictureInPictureParams\$Builder;->build()Landroid/app/PictureInPictureParams;
            move-result-object v0
            invoke-virtual {p0, v0}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
            :morphe_pip_resume_done
            nop
        """.trimIndent())

        // Preserve history and other onPause side effects; skip only the player pause in PiP.
        val pause = activity.methods.single { it.name == "onPause" && it.parameterTypes.isEmpty() }
        val pauseImpl = pause.implementation ?: error("Movie PiP: onPause has no implementation")
        check(pauseImpl.registerCount >= 2)
        val calls = pauseImpl.instructions.withIndex().filter { (_, instruction) ->
            (instruction as? ReferenceInstruction)?.reference?.toString() == "Lw/a;->L()V"
        }
        check(calls.size == 1) { "Movie PiP: expected one player pause call, found ${calls.size}" }
        val index = calls.single().index
        pause.addInstructions(index + 1, """
            :morphe_pip_after_pause
            nop
        """.trimIndent())
        pause.addInstructions(index, """
            invoke-virtual {p0}, Landroid/app/Activity;->isInPictureInPictureMode()Z
            move-result v0
            if-nez v0, :morphe_pip_after_pause
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
        """.trimIndent())

        check(activity.methods.none { it.name == "onUserLeaveHint" }) {
            "Movie PiP: onUserLeaveHint already exists"
        }
        val leave = ImmutableMethod(
            ACTIVITY, "onUserLeaveHint", emptyList(), "V",
            AccessFlags.PROTECTED.value, null, null, MutableMethodImplementation(3)
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
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_leave_done
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
    description = "Enter native Android PiP when leaving the Movie video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipRuntimePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length).mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: target activity not found in manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges").split('|')
                .filter { it.isNotBlank() }.toMutableSet()
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
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_leave_done
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
    description = "Enter native Android PiP when leaving the Movie video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipRuntimePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length).mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: target activity not found in manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges").split('|')
                .filter { it.isNotBlank() }.toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}Builder;
            invoke-direct {v0}, Landroid/app/PictureInPictureParams${';-><init>()V
            const/4 v1, 0x1
            invoke-virtual {v0, v1}, Landroid/app/PictureInPictureParams\$Builder;->setAutoEnterEnabled(Z)Landroid/app/PictureInPictureParams\$Builder;
            move-result-object v0
            invoke-virtual {v0}, Landroid/app/PictureInPictureParams\$Builder;->build()Landroid/app/PictureInPictureParams;
            move-result-object v0
            invoke-virtual {p0, v0}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
            :morphe_pip_resume_done
            nop
        """.trimIndent())

        // Preserve history and other onPause side effects; skip only the player pause in PiP.
        val pause = activity.methods.single { it.name == "onPause" && it.parameterTypes.isEmpty() }
        val pauseImpl = pause.implementation ?: error("Movie PiP: onPause has no implementation")
        check(pauseImpl.registerCount >= 2)
        val calls = pauseImpl.instructions.withIndex().filter { (_, instruction) ->
            (instruction as? ReferenceInstruction)?.reference?.toString() == "Lw/a;->L()V"
        }
        check(calls.size == 1) { "Movie PiP: expected one player pause call, found ${calls.size}" }
        val index = calls.single().index
        pause.addInstructions(index + 1, """
            :morphe_pip_after_pause
            nop
        """.trimIndent())
        pause.addInstructions(index, """
            invoke-virtual {p0}, Landroid/app/Activity;->isInPictureInPictureMode()Z
            move-result v0
            if-nez v0, :morphe_pip_after_pause
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
        """.trimIndent())

        check(activity.methods.none { it.name == "onUserLeaveHint" }) {
            "Movie PiP: onUserLeaveHint already exists"
        }
        val leave = ImmutableMethod(
            ACTIVITY, "onUserLeaveHint", emptyList(), "V",
            AccessFlags.PROTECTED.value, null, null, MutableMethodImplementation(3)
        ).toMutable()
        leave.addInstructions(0, """
            invoke-super {p0}, $BASE->onUserLeaveHint()V
            sget v0, Landroid/os/Build\$VERSION;->SDK_INT:I
            const/16 v1, 0x1a
            if-lt v0, v1, :morphe_pip_leave_done
            const/16 v1, 0x1f
            if-ge v0, v1, :morphe_pip_leave_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_leave_done
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
    description = "Enter native Android PiP when leaving the Movie video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipRuntimePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length).mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: target activity not found in manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges").split('|')
                .filter { it.isNotBlank() }.toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}VERSION;->SDK_INT:I
            const/16 v1, 0x1f
            if-lt v0, v1, :morphe_pip_resume_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_resume_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_resume_done
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_resume_done
            new-instance v0, Landroid/app/PictureInPictureParams\$Builder;
            invoke-direct {v0}, Landroid/app/PictureInPictureParams\$Builder;-><init>()V
            const/4 v1, 0x1
            invoke-virtual {v0, v1}, Landroid/app/PictureInPictureParams\$Builder;->setAutoEnterEnabled(Z)Landroid/app/PictureInPictureParams\$Builder;
            move-result-object v0
            invoke-virtual {v0}, Landroid/app/PictureInPictureParams\$Builder;->build()Landroid/app/PictureInPictureParams;
            move-result-object v0
            invoke-virtual {p0, v0}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
            :morphe_pip_resume_done
            nop
        """.trimIndent())

        // Preserve history and other onPause side effects; skip only the player pause in PiP.
        val pause = activity.methods.single { it.name == "onPause" && it.parameterTypes.isEmpty() }
        val pauseImpl = pause.implementation ?: error("Movie PiP: onPause has no implementation")
        check(pauseImpl.registerCount >= 2)
        val calls = pauseImpl.instructions.withIndex().filter { (_, instruction) ->
            (instruction as? ReferenceInstruction)?.reference?.toString() == "Lw/a;->L()V"
        }
        check(calls.size == 1) { "Movie PiP: expected one player pause call, found ${calls.size}" }
        val index = calls.single().index
        pause.addInstructions(index + 1, """
            :morphe_pip_after_pause
            nop
        """.trimIndent())
        pause.addInstructions(index, """
            invoke-virtual {p0}, Landroid/app/Activity;->isInPictureInPictureMode()Z
            move-result v0
            if-nez v0, :morphe_pip_after_pause
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
        """.trimIndent())

        check(activity.methods.none { it.name == "onUserLeaveHint" }) {
            "Movie PiP: onUserLeaveHint already exists"
        }
        val leave = ImmutableMethod(
            ACTIVITY, "onUserLeaveHint", emptyList(), "V",
            AccessFlags.PROTECTED.value, null, null, MutableMethodImplementation(3)
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
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_leave_done
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
    description = "Enter native Android PiP when leaving the Movie video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipRuntimePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length).mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: target activity not found in manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges").split('|')
                .filter { it.isNotBlank() }.toMutableSet()
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
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_leave_done
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
    description = "Enter native Android PiP when leaving the Movie video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipRuntimePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length).mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: target activity not found in manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges").split('|')
                .filter { it.isNotBlank() }.toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}Builder;-><init>()V
            const/4 v1, 0x1
            invoke-virtual {v0, v1}, Landroid/app/PictureInPictureParams${';->setAutoEnterEnabled(Z)Landroid/app/PictureInPictureParams\$Builder;
            move-result-object v0
            invoke-virtual {v0}, Landroid/app/PictureInPictureParams\$Builder;->build()Landroid/app/PictureInPictureParams;
            move-result-object v0
            invoke-virtual {p0, v0}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
            :morphe_pip_resume_done
            nop
        """.trimIndent())

        // Preserve history and other onPause side effects; skip only the player pause in PiP.
        val pause = activity.methods.single { it.name == "onPause" && it.parameterTypes.isEmpty() }
        val pauseImpl = pause.implementation ?: error("Movie PiP: onPause has no implementation")
        check(pauseImpl.registerCount >= 2)
        val calls = pauseImpl.instructions.withIndex().filter { (_, instruction) ->
            (instruction as? ReferenceInstruction)?.reference?.toString() == "Lw/a;->L()V"
        }
        check(calls.size == 1) { "Movie PiP: expected one player pause call, found ${calls.size}" }
        val index = calls.single().index
        pause.addInstructions(index + 1, """
            :morphe_pip_after_pause
            nop
        """.trimIndent())
        pause.addInstructions(index, """
            invoke-virtual {p0}, Landroid/app/Activity;->isInPictureInPictureMode()Z
            move-result v0
            if-nez v0, :morphe_pip_after_pause
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
        """.trimIndent())

        check(activity.methods.none { it.name == "onUserLeaveHint" }) {
            "Movie PiP: onUserLeaveHint already exists"
        }
        val leave = ImmutableMethod(
            ACTIVITY, "onUserLeaveHint", emptyList(), "V",
            AccessFlags.PROTECTED.value, null, null, MutableMethodImplementation(3)
        ).toMutable()
        leave.addInstructions(0, """
            invoke-super {p0}, $BASE->onUserLeaveHint()V
            sget v0, Landroid/os/Build\$VERSION;->SDK_INT:I
            const/16 v1, 0x1a
            if-lt v0, v1, :morphe_pip_leave_done
            const/16 v1, 0x1f
            if-ge v0, v1, :morphe_pip_leave_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_leave_done
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
    description = "Enter native Android PiP when leaving the Movie video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipRuntimePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length).mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: target activity not found in manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges").split('|')
                .filter { it.isNotBlank() }.toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}VERSION;->SDK_INT:I
            const/16 v1, 0x1f
            if-lt v0, v1, :morphe_pip_resume_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_resume_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_resume_done
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_resume_done
            new-instance v0, Landroid/app/PictureInPictureParams\$Builder;
            invoke-direct {v0}, Landroid/app/PictureInPictureParams\$Builder;-><init>()V
            const/4 v1, 0x1
            invoke-virtual {v0, v1}, Landroid/app/PictureInPictureParams\$Builder;->setAutoEnterEnabled(Z)Landroid/app/PictureInPictureParams\$Builder;
            move-result-object v0
            invoke-virtual {v0}, Landroid/app/PictureInPictureParams\$Builder;->build()Landroid/app/PictureInPictureParams;
            move-result-object v0
            invoke-virtual {p0, v0}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
            :morphe_pip_resume_done
            nop
        """.trimIndent())

        // Preserve history and other onPause side effects; skip only the player pause in PiP.
        val pause = activity.methods.single { it.name == "onPause" && it.parameterTypes.isEmpty() }
        val pauseImpl = pause.implementation ?: error("Movie PiP: onPause has no implementation")
        check(pauseImpl.registerCount >= 2)
        val calls = pauseImpl.instructions.withIndex().filter { (_, instruction) ->
            (instruction as? ReferenceInstruction)?.reference?.toString() == "Lw/a;->L()V"
        }
        check(calls.size == 1) { "Movie PiP: expected one player pause call, found ${calls.size}" }
        val index = calls.single().index
        pause.addInstructions(index + 1, """
            :morphe_pip_after_pause
            nop
        """.trimIndent())
        pause.addInstructions(index, """
            invoke-virtual {p0}, Landroid/app/Activity;->isInPictureInPictureMode()Z
            move-result v0
            if-nez v0, :morphe_pip_after_pause
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
        """.trimIndent())

        check(activity.methods.none { it.name == "onUserLeaveHint" }) {
            "Movie PiP: onUserLeaveHint already exists"
        }
        val leave = ImmutableMethod(
            ACTIVITY, "onUserLeaveHint", emptyList(), "V",
            AccessFlags.PROTECTED.value, null, null, MutableMethodImplementation(3)
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
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_leave_done
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
    description = "Enter native Android PiP when leaving the Movie video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipRuntimePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length).mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: target activity not found in manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges").split('|')
                .filter { it.isNotBlank() }.toMutableSet()
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
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_leave_done
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
    description = "Enter native Android PiP when leaving the Movie video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipRuntimePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length).mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: target activity not found in manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges").split('|')
                .filter { it.isNotBlank() }.toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}Builder;->setAutoEnterEnabled(Z)Landroid/app/PictureInPictureParams${';
            move-result-object v0
            invoke-virtual {v0}, Landroid/app/PictureInPictureParams\$Builder;->build()Landroid/app/PictureInPictureParams;
            move-result-object v0
            invoke-virtual {p0, v0}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
            :morphe_pip_resume_done
            nop
        """.trimIndent())

        // Preserve history and other onPause side effects; skip only the player pause in PiP.
        val pause = activity.methods.single { it.name == "onPause" && it.parameterTypes.isEmpty() }
        val pauseImpl = pause.implementation ?: error("Movie PiP: onPause has no implementation")
        check(pauseImpl.registerCount >= 2)
        val calls = pauseImpl.instructions.withIndex().filter { (_, instruction) ->
            (instruction as? ReferenceInstruction)?.reference?.toString() == "Lw/a;->L()V"
        }
        check(calls.size == 1) { "Movie PiP: expected one player pause call, found ${calls.size}" }
        val index = calls.single().index
        pause.addInstructions(index + 1, """
            :morphe_pip_after_pause
            nop
        """.trimIndent())
        pause.addInstructions(index, """
            invoke-virtual {p0}, Landroid/app/Activity;->isInPictureInPictureMode()Z
            move-result v0
            if-nez v0, :morphe_pip_after_pause
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
        """.trimIndent())

        check(activity.methods.none { it.name == "onUserLeaveHint" }) {
            "Movie PiP: onUserLeaveHint already exists"
        }
        val leave = ImmutableMethod(
            ACTIVITY, "onUserLeaveHint", emptyList(), "V",
            AccessFlags.PROTECTED.value, null, null, MutableMethodImplementation(3)
        ).toMutable()
        leave.addInstructions(0, """
            invoke-super {p0}, $BASE->onUserLeaveHint()V
            sget v0, Landroid/os/Build\$VERSION;->SDK_INT:I
            const/16 v1, 0x1a
            if-lt v0, v1, :morphe_pip_leave_done
            const/16 v1, 0x1f
            if-ge v0, v1, :morphe_pip_leave_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_leave_done
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
    description = "Enter native Android PiP when leaving the Movie video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipRuntimePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length).mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: target activity not found in manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges").split('|')
                .filter { it.isNotBlank() }.toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}VERSION;->SDK_INT:I
            const/16 v1, 0x1f
            if-lt v0, v1, :morphe_pip_resume_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_resume_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_resume_done
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_resume_done
            new-instance v0, Landroid/app/PictureInPictureParams\$Builder;
            invoke-direct {v0}, Landroid/app/PictureInPictureParams\$Builder;-><init>()V
            const/4 v1, 0x1
            invoke-virtual {v0, v1}, Landroid/app/PictureInPictureParams\$Builder;->setAutoEnterEnabled(Z)Landroid/app/PictureInPictureParams\$Builder;
            move-result-object v0
            invoke-virtual {v0}, Landroid/app/PictureInPictureParams\$Builder;->build()Landroid/app/PictureInPictureParams;
            move-result-object v0
            invoke-virtual {p0, v0}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
            :morphe_pip_resume_done
            nop
        """.trimIndent())

        // Preserve history and other onPause side effects; skip only the player pause in PiP.
        val pause = activity.methods.single { it.name == "onPause" && it.parameterTypes.isEmpty() }
        val pauseImpl = pause.implementation ?: error("Movie PiP: onPause has no implementation")
        check(pauseImpl.registerCount >= 2)
        val calls = pauseImpl.instructions.withIndex().filter { (_, instruction) ->
            (instruction as? ReferenceInstruction)?.reference?.toString() == "Lw/a;->L()V"
        }
        check(calls.size == 1) { "Movie PiP: expected one player pause call, found ${calls.size}" }
        val index = calls.single().index
        pause.addInstructions(index + 1, """
            :morphe_pip_after_pause
            nop
        """.trimIndent())
        pause.addInstructions(index, """
            invoke-virtual {p0}, Landroid/app/Activity;->isInPictureInPictureMode()Z
            move-result v0
            if-nez v0, :morphe_pip_after_pause
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
        """.trimIndent())

        check(activity.methods.none { it.name == "onUserLeaveHint" }) {
            "Movie PiP: onUserLeaveHint already exists"
        }
        val leave = ImmutableMethod(
            ACTIVITY, "onUserLeaveHint", emptyList(), "V",
            AccessFlags.PROTECTED.value, null, null, MutableMethodImplementation(3)
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
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_leave_done
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
    description = "Enter native Android PiP when leaving the Movie video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipRuntimePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length).mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: target activity not found in manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges").split('|')
                .filter { it.isNotBlank() }.toMutableSet()
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
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_leave_done
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
    description = "Enter native Android PiP when leaving the Movie video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipRuntimePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length).mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: target activity not found in manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges").split('|')
                .filter { it.isNotBlank() }.toMutableSet()
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
            :morphe_pip_resume_done
            nop
        """.trimIndent())

        // Preserve history and other onPause side effects; skip only the player pause in PiP.
        val pause = activity.methods.single { it.name == "onPause" && it.parameterTypes.isEmpty() }
        val pauseImpl = pause.implementation ?: error("Movie PiP: onPause has no implementation")
        check(pauseImpl.registerCount >= 2)
        val calls = pauseImpl.instructions.withIndex().filter { (_, instruction) ->
            (instruction as? ReferenceInstruction)?.reference?.toString() == "Lw/a;->L()V"
        }
        check(calls.size == 1) { "Movie PiP: expected one player pause call, found ${calls.size}" }
        val index = calls.single().index
        pause.addInstructions(index + 1, """
            :morphe_pip_after_pause
            nop
        """.trimIndent())
        pause.addInstructions(index, """
            invoke-virtual {p0}, Landroid/app/Activity;->isInPictureInPictureMode()Z
            move-result v0
            if-nez v0, :morphe_pip_after_pause
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
        """.trimIndent())

        check(activity.methods.none { it.name == "onUserLeaveHint" }) {
            "Movie PiP: onUserLeaveHint already exists"
        }
        val leave = ImmutableMethod(
            ACTIVITY, "onUserLeaveHint", emptyList(), "V",
            AccessFlags.PROTECTED.value, null, null, MutableMethodImplementation(3)
        ).toMutable()
        leave.addInstructions(0, """
            invoke-super {p0}, $BASE->onUserLeaveHint()V
            sget v0, Landroid/os/Build\$VERSION;->SDK_INT:I
            const/16 v1, 0x1a
            if-lt v0, v1, :morphe_pip_leave_done
            const/16 v1, 0x1f
            if-ge v0, v1, :morphe_pip_leave_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_leave_done
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
    description = "Enter native Android PiP when leaving the Movie video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipRuntimePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length).mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: target activity not found in manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges").split('|')
                .filter { it.isNotBlank() }.toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}VERSION;->SDK_INT:I
            const/16 v1, 0x1f
            if-lt v0, v1, :morphe_pip_resume_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_resume_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_resume_done
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_resume_done
            new-instance v0, Landroid/app/PictureInPictureParams\$Builder;
            invoke-direct {v0}, Landroid/app/PictureInPictureParams\$Builder;-><init>()V
            const/4 v1, 0x1
            invoke-virtual {v0, v1}, Landroid/app/PictureInPictureParams\$Builder;->setAutoEnterEnabled(Z)Landroid/app/PictureInPictureParams\$Builder;
            move-result-object v0
            invoke-virtual {v0}, Landroid/app/PictureInPictureParams\$Builder;->build()Landroid/app/PictureInPictureParams;
            move-result-object v0
            invoke-virtual {p0, v0}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
            :morphe_pip_resume_done
            nop
        """.trimIndent())

        // Preserve history and other onPause side effects; skip only the player pause in PiP.
        val pause = activity.methods.single { it.name == "onPause" && it.parameterTypes.isEmpty() }
        val pauseImpl = pause.implementation ?: error("Movie PiP: onPause has no implementation")
        check(pauseImpl.registerCount >= 2)
        val calls = pauseImpl.instructions.withIndex().filter { (_, instruction) ->
            (instruction as? ReferenceInstruction)?.reference?.toString() == "Lw/a;->L()V"
        }
        check(calls.size == 1) { "Movie PiP: expected one player pause call, found ${calls.size}" }
        val index = calls.single().index
        pause.addInstructions(index + 1, """
            :morphe_pip_after_pause
            nop
        """.trimIndent())
        pause.addInstructions(index, """
            invoke-virtual {p0}, Landroid/app/Activity;->isInPictureInPictureMode()Z
            move-result v0
            if-nez v0, :morphe_pip_after_pause
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
        """.trimIndent())

        check(activity.methods.none { it.name == "onUserLeaveHint" }) {
            "Movie PiP: onUserLeaveHint already exists"
        }
        val leave = ImmutableMethod(
            ACTIVITY, "onUserLeaveHint", emptyList(), "V",
            AccessFlags.PROTECTED.value, null, null, MutableMethodImplementation(3)
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
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_leave_done
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
    description = "Enter native Android PiP when leaving the Movie video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipRuntimePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length).mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: target activity not found in manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges").split('|')
                .filter { it.isNotBlank() }.toMutableSet()
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
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_leave_done
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
    description = "Enter native Android PiP when leaving the Movie video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipRuntimePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length).mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: target activity not found in manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges").split('|')
                .filter { it.isNotBlank() }.toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}Builder;->build()Landroid/app/PictureInPictureParams;
            move-result-object v0
            invoke-virtual {p0, v0}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
            :morphe_pip_resume_done
            nop
        """.trimIndent())

        // Preserve history and other onPause side effects; skip only the player pause in PiP.
        val pause = activity.methods.single { it.name == "onPause" && it.parameterTypes.isEmpty() }
        val pauseImpl = pause.implementation ?: error("Movie PiP: onPause has no implementation")
        check(pauseImpl.registerCount >= 2)
        val calls = pauseImpl.instructions.withIndex().filter { (_, instruction) ->
            (instruction as? ReferenceInstruction)?.reference?.toString() == "Lw/a;->L()V"
        }
        check(calls.size == 1) { "Movie PiP: expected one player pause call, found ${calls.size}" }
        val index = calls.single().index
        pause.addInstructions(index + 1, """
            :morphe_pip_after_pause
            nop
        """.trimIndent())
        pause.addInstructions(index, """
            invoke-virtual {p0}, Landroid/app/Activity;->isInPictureInPictureMode()Z
            move-result v0
            if-nez v0, :morphe_pip_after_pause
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
        """.trimIndent())

        check(activity.methods.none { it.name == "onUserLeaveHint" }) {
            "Movie PiP: onUserLeaveHint already exists"
        }
        val leave = ImmutableMethod(
            ACTIVITY, "onUserLeaveHint", emptyList(), "V",
            AccessFlags.PROTECTED.value, null, null, MutableMethodImplementation(3)
        ).toMutable()
        leave.addInstructions(0, """
            invoke-super {p0}, $BASE->onUserLeaveHint()V
            sget v0, Landroid/os/Build\$VERSION;->SDK_INT:I
            const/16 v1, 0x1a
            if-lt v0, v1, :morphe_pip_leave_done
            const/16 v1, 0x1f
            if-ge v0, v1, :morphe_pip_leave_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_leave_done
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_leave_done
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
    description = "Enter native Android PiP when leaving the Movie video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipRuntimePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length).mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: target activity not found in manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges").split('|')
                .filter { it.isNotBlank() }.toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}VERSION;->SDK_INT:I
            const/16 v1, 0x1f
            if-lt v0, v1, :morphe_pip_resume_done
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :morphe_pip_resume_done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :morphe_pip_resume_done
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_resume_done
            new-instance v0, Landroid/app/PictureInPictureParams${';
            invoke-direct {v0}, Landroid/app/PictureInPictureParams\$Builder;-><init>()V
            const/4 v1, 0x1
            invoke-virtual {v0, v1}, Landroid/app/PictureInPictureParams\$Builder;->setAutoEnterEnabled(Z)Landroid/app/PictureInPictureParams\$Builder;
            move-result-object v0
            invoke-virtual {v0}, Landroid/app/PictureInPictureParams\$Builder;->build()Landroid/app/PictureInPictureParams;
            move-result-object v0
            invoke-virtual {p0, v0}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
            :morphe_pip_resume_done
            nop
        """.trimIndent())

        // Preserve history and other onPause side effects; skip only the player pause in PiP.
        val pause = activity.methods.single { it.name == "onPause" && it.parameterTypes.isEmpty() }
        val pauseImpl = pause.implementation ?: error("Movie PiP: onPause has no implementation")
        check(pauseImpl.registerCount >= 2)
        val calls = pauseImpl.instructions.withIndex().filter { (_, instruction) ->
            (instruction as? ReferenceInstruction)?.reference?.toString() == "Lw/a;->L()V"
        }
        check(calls.size == 1) { "Movie PiP: expected one player pause call, found ${calls.size}" }
        val index = calls.single().index
        pause.addInstructions(index + 1, """
            :morphe_pip_after_pause
            nop
        """.trimIndent())
        pause.addInstructions(index, """
            invoke-virtual {p0}, Landroid/app/Activity;->isInPictureInPictureMode()Z
            move-result v0
            if-nez v0, :morphe_pip_after_pause
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
        """.trimIndent())

        check(activity.methods.none { it.name == "onUserLeaveHint" }) {
            "Movie PiP: onUserLeaveHint already exists"
        }
        val leave = ImmutableMethod(
            ACTIVITY, "onUserLeaveHint", emptyList(), "V",
            AccessFlags.PROTECTED.value, null, null, MutableMethodImplementation(3)
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
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_leave_done
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
    description = "Enter native Android PiP when leaving the Movie video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipRuntimePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length).mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: target activity not found in manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges").split('|')
                .filter { it.isNotBlank() }.toMutableSet()
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
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_leave_done
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
    description = "Enter native Android PiP when leaving the Movie video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipRuntimePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length).mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: target activity not found in manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges").split('|')
                .filter { it.isNotBlank() }.toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}Builder;
            invoke-direct {v0}, Landroid/app/PictureInPictureParams${';-><init>()V
            const/4 v1, 0x1
            invoke-virtual {v0, v1}, Landroid/app/PictureInPictureParams\$Builder;->setAutoEnterEnabled(Z)Landroid/app/PictureInPictureParams\$Builder;
            move-result-object v0
            invoke-virtual {v0}, Landroid/app/PictureInPictureParams\$Builder;->build()Landroid/app/PictureInPictureParams;
            move-result-object v0
            invoke-virtual {p0, v0}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
            :morphe_pip_resume_done
            nop
        """.trimIndent())

        // Preserve history and other onPause side effects; skip only the player pause in PiP.
        val pause = activity.methods.single { it.name == "onPause" && it.parameterTypes.isEmpty() }
        val pauseImpl = pause.implementation ?: error("Movie PiP: onPause has no implementation")
        check(pauseImpl.registerCount >= 2)
        val calls = pauseImpl.instructions.withIndex().filter { (_, instruction) ->
            (instruction as? ReferenceInstruction)?.reference?.toString() == "Lw/a;->L()V"
        }
        check(calls.size == 1) { "Movie PiP: expected one player pause call, found ${calls.size}" }
        val index = calls.single().index
        pause.addInstructions(index + 1, """
            :morphe_pip_after_pause
            nop
        """.trimIndent())
        pause.addInstructions(index, """
            invoke-virtual {p0}, Landroid/app/Activity;->isInPictureInPictureMode()Z
            move-result v0
            if-nez v0, :morphe_pip_after_pause
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
        """.trimIndent())

        check(activity.methods.none { it.name == "onUserLeaveHint" }) {
            "Movie PiP: onUserLeaveHint already exists"
        }
        val leave = ImmutableMethod(
            ACTIVITY, "onUserLeaveHint", emptyList(), "V",
            AccessFlags.PROTECTED.value, null, null, MutableMethodImplementation(3)
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
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_leave_done
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
    description = "Enter native Android PiP when leaving the Movie video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipRuntimePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length).mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: target activity not found in manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges").split('|')
                .filter { it.isNotBlank() }.toMutableSet()
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
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_leave_done
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
    description = "Enter native Android PiP when leaving the Movie video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipRuntimePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length).mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: target activity not found in manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges").split('|')
                .filter { it.isNotBlank() }.toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}Builder;-><init>()V
            const/4 v1, 0x1
            invoke-virtual {v0, v1}, Landroid/app/PictureInPictureParams${';->setAutoEnterEnabled(Z)Landroid/app/PictureInPictureParams\$Builder;
            move-result-object v0
            invoke-virtual {v0}, Landroid/app/PictureInPictureParams\$Builder;->build()Landroid/app/PictureInPictureParams;
            move-result-object v0
            invoke-virtual {p0, v0}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
            :morphe_pip_resume_done
            nop
        """.trimIndent())

        // Preserve history and other onPause side effects; skip only the player pause in PiP.
        val pause = activity.methods.single { it.name == "onPause" && it.parameterTypes.isEmpty() }
        val pauseImpl = pause.implementation ?: error("Movie PiP: onPause has no implementation")
        check(pauseImpl.registerCount >= 2)
        val calls = pauseImpl.instructions.withIndex().filter { (_, instruction) ->
            (instruction as? ReferenceInstruction)?.reference?.toString() == "Lw/a;->L()V"
        }
        check(calls.size == 1) { "Movie PiP: expected one player pause call, found ${calls.size}" }
        val index = calls.single().index
        pause.addInstructions(index + 1, """
            :morphe_pip_after_pause
            nop
        """.trimIndent())
        pause.addInstructions(index, """
            invoke-virtual {p0}, Landroid/app/Activity;->isInPictureInPictureMode()Z
            move-result v0
            if-nez v0, :morphe_pip_after_pause
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
        """.trimIndent())

        check(activity.methods.none { it.name == "onUserLeaveHint" }) {
            "Movie PiP: onUserLeaveHint already exists"
        }
        val leave = ImmutableMethod(
            ACTIVITY, "onUserLeaveHint", emptyList(), "V",
            AccessFlags.PROTECTED.value, null, null, MutableMethodImplementation(3)
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
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_leave_done
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
    description = "Enter native Android PiP when leaving the Movie video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipRuntimePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length).mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: target activity not found in manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges").split('|')
                .filter { it.isNotBlank() }.toMutableSet()
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
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_leave_done
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
    description = "Enter native Android PiP when leaving the Movie video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipRuntimePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length).mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: target activity not found in manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges").split('|')
                .filter { it.isNotBlank() }.toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}Builder;->setAutoEnterEnabled(Z)Landroid/app/PictureInPictureParams${';
            move-result-object v0
            invoke-virtual {v0}, Landroid/app/PictureInPictureParams\$Builder;->build()Landroid/app/PictureInPictureParams;
            move-result-object v0
            invoke-virtual {p0, v0}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
            :morphe_pip_resume_done
            nop
        """.trimIndent())

        // Preserve history and other onPause side effects; skip only the player pause in PiP.
        val pause = activity.methods.single { it.name == "onPause" && it.parameterTypes.isEmpty() }
        val pauseImpl = pause.implementation ?: error("Movie PiP: onPause has no implementation")
        check(pauseImpl.registerCount >= 2)
        val calls = pauseImpl.instructions.withIndex().filter { (_, instruction) ->
            (instruction as? ReferenceInstruction)?.reference?.toString() == "Lw/a;->L()V"
        }
        check(calls.size == 1) { "Movie PiP: expected one player pause call, found ${calls.size}" }
        val index = calls.single().index
        pause.addInstructions(index + 1, """
            :morphe_pip_after_pause
            nop
        """.trimIndent())
        pause.addInstructions(index, """
            invoke-virtual {p0}, Landroid/app/Activity;->isInPictureInPictureMode()Z
            move-result v0
            if-nez v0, :morphe_pip_after_pause
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
        """.trimIndent())

        check(activity.methods.none { it.name == "onUserLeaveHint" }) {
            "Movie PiP: onUserLeaveHint already exists"
        }
        val leave = ImmutableMethod(
            ACTIVITY, "onUserLeaveHint", emptyList(), "V",
            AccessFlags.PROTECTED.value, null, null, MutableMethodImplementation(3)
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
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_leave_done
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
    description = "Enter native Android PiP when leaving the Movie video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipRuntimePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length).mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: target activity not found in manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges").split('|')
                .filter { it.isNotBlank() }.toMutableSet()
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
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_leave_done
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
    description = "Enter native Android PiP when leaving the Movie video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipRuntimePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length).mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: target activity not found in manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges").split('|')
                .filter { it.isNotBlank() }.toMutableSet()
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
            :morphe_pip_resume_done
            nop
        """.trimIndent())

        // Preserve history and other onPause side effects; skip only the player pause in PiP.
        val pause = activity.methods.single { it.name == "onPause" && it.parameterTypes.isEmpty() }
        val pauseImpl = pause.implementation ?: error("Movie PiP: onPause has no implementation")
        check(pauseImpl.registerCount >= 2)
        val calls = pauseImpl.instructions.withIndex().filter { (_, instruction) ->
            (instruction as? ReferenceInstruction)?.reference?.toString() == "Lw/a;->L()V"
        }
        check(calls.size == 1) { "Movie PiP: expected one player pause call, found ${calls.size}" }
        val index = calls.single().index
        pause.addInstructions(index + 1, """
            :morphe_pip_after_pause
            nop
        """.trimIndent())
        pause.addInstructions(index, """
            invoke-virtual {p0}, Landroid/app/Activity;->isInPictureInPictureMode()Z
            move-result v0
            if-nez v0, :morphe_pip_after_pause
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
        """.trimIndent())

        check(activity.methods.none { it.name == "onUserLeaveHint" }) {
            "Movie PiP: onUserLeaveHint already exists"
        }
        val leave = ImmutableMethod(
            ACTIVITY, "onUserLeaveHint", emptyList(), "V",
            AccessFlags.PROTECTED.value, null, null, MutableMethodImplementation(3)
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
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_leave_done
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
    description = "Enter native Android PiP when leaving the Movie video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipRuntimePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length).mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: target activity not found in manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges").split('|')
                .filter { it.isNotBlank() }.toMutableSet()
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
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_leave_done
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
    description = "Enter native Android PiP when leaving the Movie video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipRuntimePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length).mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: target activity not found in manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges").split('|')
                .filter { it.isNotBlank() }.toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
}Builder;->build()Landroid/app/PictureInPictureParams;
            move-result-object v0
            invoke-virtual {p0, v0}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
            :morphe_pip_resume_done
            nop
        """.trimIndent())

        // Preserve history and other onPause side effects; skip only the player pause in PiP.
        val pause = activity.methods.single { it.name == "onPause" && it.parameterTypes.isEmpty() }
        val pauseImpl = pause.implementation ?: error("Movie PiP: onPause has no implementation")
        check(pauseImpl.registerCount >= 2)
        val calls = pauseImpl.instructions.withIndex().filter { (_, instruction) ->
            (instruction as? ReferenceInstruction)?.reference?.toString() == "Lw/a;->L()V"
        }
        check(calls.size == 1) { "Movie PiP: expected one player pause call, found ${calls.size}" }
        val index = calls.single().index
        pause.addInstructions(index + 1, """
            :morphe_pip_after_pause
            nop
        """.trimIndent())
        pause.addInstructions(index, """
            invoke-virtual {p0}, Landroid/app/Activity;->isInPictureInPictureMode()Z
            move-result v0
            if-nez v0, :morphe_pip_after_pause
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
        """.trimIndent())

        check(activity.methods.none { it.name == "onUserLeaveHint" }) {
            "Movie PiP: onUserLeaveHint already exists"
        }
        val leave = ImmutableMethod(
            ACTIVITY, "onUserLeaveHint", emptyList(), "V",
            AccessFlags.PROTECTED.value, null, null, MutableMethodImplementation(3)
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
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_leave_done
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
    description = "Enter native Android PiP when leaving the Movie video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipRuntimePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length).mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: target activity not found in manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges").split('|')
                .filter { it.isNotBlank() }.toMutableSet()
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
            invoke-virtual {v0}, Lf4/u0;->getPlayWhenReady()Z
            move-result v1
            if-eqz v1, :morphe_pip_leave_done
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
    description = "Enter native Android PiP when leaving the Movie video player.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(moviePipRuntimePatch)
    execute {
        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val target = (0 until activities.length).mapNotNull { activities.item(it) as? Element }
                .singleOrNull {
                    it.getAttribute("android:name") ==
                        "com.mgs.carparking.ui.homecontent.videodetail.VideoPlayDetailActivity"
                } ?: error("Movie PiP: target activity not found in manifest")
            target.setAttribute("android:supportsPictureInPicture", "true")
            val changes = target.getAttribute("android:configChanges").split('|')
                .filter { it.isNotBlank() }.toMutableSet()
            changes.addAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation"))
            target.setAttribute("android:configChanges", changes.joinToString("|"))
        }
    }
}
