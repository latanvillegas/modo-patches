package app.morphe.patches.movie.misc.quality

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import org.w3c.dom.Element

/**
 * Independent ExoPlayer quality selector.
 * PopupMenu item IDs encode the video group and track; all options come from
 * the currently mapped ExoPlayer formats, never from a fixed quality list.
 */
private const val ACTIVITY = "Lcom/mgs/carparking/ui/homecontent/videodetail/VideoPlayDetailActivity;"
private const val SELECTOR = "Lcom/google/android/exoplayer/trackselection/DefaultTrackSelector;"
private const val GROUPS = "Lcom/google/android/exoplayer/source/TrackGroupArray;"

private val movieQualityRuntimePatch = bytecodePatch {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    execute {
        val activity = mutableClassDefBy(ACTIVITY)
        check(activity.methods.none { it.name == "morpheShowQuality" || it.name == "onMenuItemClick" }) {
            "Movie: quality selector methods already exist"
        }
        activity.interfaces.add("Landroid/widget/PopupMenu\$OnMenuItemClickListener;")
        fun addMethod(name: String, param: String, result: String, locals: Int, code: String) {
            activity.methods.add(
                ImmutableMethod(
                    ACTIVITY, name,
                    listOf(ImmutableMethodParameter(param, null, null)),
                    result, AccessFlags.PUBLIC.value,
                    null, null, MutableMethodImplementation(locals)
                ).toMutable().apply { addInstructions(0, code.trimIndent()) }
            )
        }
        addMethod("morpheShowQuality", "Landroid/view/View;", "V", 15, """
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :done
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :done
            invoke-virtual {v0}, Lf4/u0;->Q()Lx5/d;
            move-result-object v0
            instance-of v1, v0, $SELECTOR
            if-eqz v1, :done
            check-cast v0, $SELECTOR
            invoke-virtual {v0}, $SELECTOR->g()Lcom/google/android/exoplayer/trackselection/b\$a;
            move-result-object v1
            if-eqz v1, :done
            new-instance v2, Landroid/widget/PopupMenu;
            invoke-direct {v2, p0, p1}, Landroid/widget/PopupMenu;-><init>(Landroid/content/Context;Landroid/view/View;)V
            invoke-virtual {v2, p0}, Landroid/widget/PopupMenu;->setOnMenuItemClickListener(Landroid/widget/PopupMenu\$OnMenuItemClickListener;)V
            invoke-virtual {v2}, Landroid/widget/PopupMenu;->getMenu()Landroid/view/Menu;
            move-result-object v3
            const/4 v4, 0x0
            const-string v5, "Auto"
            invoke-interface {v3, v4, v4, v4, v5}, Landroid/view/Menu;->add(IIILjava/lang/CharSequence;)Landroid/view/MenuItem;
            const/4 v4, 0x0
            :renderer
            invoke-virtual {v1}, Lcom/google/android/exoplayer/trackselection/b\$a;->c()I
            move-result v5
            if-ge v4, v5, :show
            invoke-virtual {v1, v4}, Lcom/google/android/exoplayer/trackselection/b\$a;->d(I)I
            move-result v5
            const/4 v6, 0x2
            if-ne v5, v6, :next_renderer
            invoke-virtual {v1, v4}, Lcom/google/android/exoplayer/trackselection/b\$a;->e(I)$GROUPS
            move-result-object v5
            const/4 v6, 0x0
            :group
            iget v7, v5, $GROUPS->b:I
            if-ge v6, v7, :next_renderer
            invoke-virtual {v5, v6}, $GROUPS->b(I)Lcom/google/android/exoplayer/source/TrackGroup;
            move-result-object v7
            const/4 v8, 0x0
            :track
            iget v9, v7, Lcom/google/android/exoplayer/source/TrackGroup;->b:I
            if-ge v8, v9, :next_group
            invoke-virtual {v7, v8}, Lcom/google/android/exoplayer/source/TrackGroup;->b(I)Lcom/google/android/exoplayer/Format;
            move-result-object v9
            iget v9, v9, Lcom/google/android/exoplayer/Format;->p:I
            if-lez v9, :next_track
            const/16 v10, 0x63
            if-gt v6, v10, :next_track
            if-gt v8, v10, :next_track
            mul-int/lit16 v10, v6, 0x64
            add-int/2addr v10, v8
            add-int/lit8 v10, v10, 0x1
            new-instance v11, Ljava/lang/StringBuilder;
            invoke-direct {v11}, Ljava/lang/StringBuilder;-><init>()V
            invoke-virtual {v11, v9}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
            move-result-object v11
            const-string v12, "p"
            invoke-virtual {v11, v12}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
            move-result-object v11
            invoke-virtual {v11}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
            move-result-object v11
            invoke-interface {v3, v4, v10, v9, v11}, Landroid/view/Menu;->add(IIILjava/lang/CharSequence;)Landroid/view/MenuItem;
            :next_track
            add-int/lit8 v8, v8, 0x1
            goto :track
            :next_group
            add-int/lit8 v6, v6, 0x1
            goto :group
            :next_renderer
            add-int/lit8 v4, v4, 0x1
            goto :renderer
            :show
            invoke-virtual {v2}, Landroid/widget/PopupMenu;->show()V
            :done
            return-void
        """)
        addMethod("onMenuItemClick", "Landroid/view/MenuItem;", "Z", 14, """
            iget-object v0, p0, $ACTIVITY->p:Lw/a;
            if-eqz v0, :fail
            invoke-virtual {v0}, Lw/a;->D()Lf4/u0;
            move-result-object v0
            if-eqz v0, :fail
            invoke-virtual {v0}, Lf4/u0;->Q()Lx5/d;
            move-result-object v0
            instance-of v1, v0, $SELECTOR
            if-eqz v1, :fail
            check-cast v0, $SELECTOR
            invoke-virtual {v0}, $SELECTOR->g()Lcom/google/android/exoplayer/trackselection/b\$a;
            move-result-object v1
            if-eqz v1, :fail
            invoke-interface {p1}, Landroid/view/MenuItem;->getGroupId()I
            move-result v2
            invoke-interface {p1}, Landroid/view/MenuItem;->getItemId()I
            move-result v3
            invoke-virtual {v0}, $SELECTOR->v()Lcom/google/android/exoplayer/trackselection/DefaultTrackSelector\$Parameters;
            move-result-object v4
            invoke-virtual {v4}, Lcom/google/android/exoplayer/trackselection/DefaultTrackSelector\$Parameters;->g()Lcom/google/android/exoplayer/trackselection/DefaultTrackSelector\$d;
            move-result-object v4
            iget-object v5, v4, Lcom/google/android/exoplayer/trackselection/DefaultTrackSelector\$d;->z:Landroid/util/SparseArray;
            if-nez v3, :manual
            const/4 v6, 0x0
            :auto_loop
            invoke-virtual {v1}, Lcom/google/android/exoplayer/trackselection/b\$a;->c()I
            move-result v7
            if-ge v6, v7, :apply
            invoke-virtual {v1, v6}, Lcom/google/android/exoplayer/trackselection/b\$a;->d(I)I
            move-result v7
            const/4 v8, 0x2
            if-ne v7, v8, :auto_next
            invoke-virtual {v5, v6}, Landroid/util/SparseArray;->remove(I)V
            :auto_next
            add-int/lit8 v6, v6, 0x1
            goto :auto_loop
            :manual
            invoke-virtual {v1}, Lcom/google/android/exoplayer/trackselection/b\$a;->c()I
            move-result v6
            if-ltz v2, :fail
            if-ge v2, v6, :fail
            invoke-virtual {v1, v2}, Lcom/google/android/exoplayer/trackselection/b\$a;->d(I)I
            move-result v6
            const/4 v7, 0x2
            if-ne v6, v7, :fail
            add-int/lit8 v3, v3, -0x1
            div-int/lit8 v6, v3, 0x64
            rem-int/lit8 v7, v3, 0x64
            invoke-virtual {v1, v2}, Lcom/google/android/exoplayer/trackselection/b\$a;->e(I)$GROUPS
            move-result-object v8
            iget v9, v8, $GROUPS->b:I
            if-ltz v6, :fail
            if-ge v6, v9, :fail
            invoke-virtual {v8, v6}, $GROUPS->b(I)Lcom/google/android/exoplayer/source/TrackGroup;
            move-result-object v9
            iget v10, v9, Lcom/google/android/exoplayer/source/TrackGroup;->b:I
            if-ltz v7, :fail
            if-ge v7, v10, :fail
            invoke-virtual {v9, v7}, Lcom/google/android/exoplayer/source/TrackGroup;->b(I)Lcom/google/android/exoplayer/Format;
            move-result-object v9
            iget v9, v9, Lcom/google/android/exoplayer/Format;->p:I
            if-lez v9, :fail
            new-instance v9, Lcom/google/android/exoplayer/trackselection/DefaultTrackSelector\$SelectionOverride;
            const/4 v10, 0x1
            new-array v10, v10, [I
            const/4 v11, 0x0
            aput v7, v10, v11
            invoke-direct {v9, v6, v10}, Lcom/google/android/exoplayer/trackselection/DefaultTrackSelector\$SelectionOverride;-><init>(I[I)V
            invoke-virtual {v5, v2}, Landroid/util/SparseArray;->get(I)Ljava/lang/Object;
            move-result-object v10
            check-cast v10, Ljava/util/Map;
            if-nez v10, :existing_map
            new-instance v10, Ljava/util/HashMap;
            invoke-direct {v10}, Ljava/util/HashMap;-><init>()V
            :existing_map
            invoke-interface {v10, v8, v9}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
            invoke-virtual {v5, v2, v10}, Landroid/util/SparseArray;->put(ILjava/lang/Object;)V
            :apply
            invoke-virtual {v0, v4}, $SELECTOR->L(Lcom/google/android/exoplayer/trackselection/DefaultTrackSelector\$d;)V
            const/4 v0, 0x1
            return v0
            :fail
            const/4 v0, 0x0
            return v0
        """)
    }
}

@Suppress("unused")
val movieVideoQualitySelectorPatch = resourcePatch(
    name = "Movie - Video Quality Selector",
    description = "Adds a fullscreen video quality popup using real ExoPlayer tracks and preserves audio/subtitle overrides.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(movieQualityRuntimePatch)

    execute {
        document("res/layout/exo_playback_top_my_new.xml").use { doc ->
            val controls = doc.getElementsByTagName("LinearLayout")
            var audio: Element? = null
            for (index in 0 until controls.length) {
                val element = controls.item(index) as? Element ?: continue
                if (element.getAttribute("android:id") == "@id/ll_audio_subtitle") {
                    audio = element
                    break
                }
            }
            val anchor = checkNotNull(audio) {
                "Movie: audio/subtitle control missing from fullscreen player"
            }
            val parent = anchor.parentNode as? Element
                ?: error("Movie: invalid player controls container")
            val siblings = parent.childNodes
            var speedFound = false
            for (index in 0 until siblings.length) {
                val element = siblings.item(index) as? Element ?: continue
                if (element.getAttribute("android:id") == "@id/rl_speed") speedFound = true
            }
            check(speedFound) { "Movie: speed control missing from fullscreen player" }

            val quality = doc.createElement("TextView")
            quality.setAttribute("android:tag", "morphe_movie_video_quality")
            quality.setAttribute("android:text", "Calidad")
            quality.setAttribute("android:textSize", "@dimen/sp_12")
            quality.setAttribute("android:textColor", "@color/white")
            quality.setAttribute("android:gravity", "center")
            quality.setAttribute("android:background", "@drawable/detail_player_view_bot_button")
            quality.setAttribute("android:paddingLeft", "@dimen/dp_5")
            quality.setAttribute("android:paddingRight", "@dimen/dp_5")
            quality.setAttribute("android:layout_marginLeft", "@dimen/dp_4")
            quality.setAttribute("android:layout_width", "wrap_content")
            quality.setAttribute("android:layout_height", "match_parent")
            quality.setAttribute("android:visibility", "visible")
            quality.setAttribute("android:onClick", "morpheShowQuality")
            parent.insertBefore(quality, anchor.nextSibling)
        }
    }
}
