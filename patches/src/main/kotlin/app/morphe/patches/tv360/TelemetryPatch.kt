package app.morphe.patches.tv360

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch

/**
 * Reserved for verified optional analytics event dispatch only.
 * Preserve crash diagnostics, playback metrics, and account/session behavior.
 */
@Suppress("unused")
val tv360TelemetryPatch = bytecodePatch(
    name = "TV360 Bitel - Optional Analytics Controls",
    description = "Reserved for independently verified optional analytics changes.",
    default = false,
) {
    compatibleWith("com.bitel.tv360"("3.5"))

    execute {
        throw PatchException(
            "TV360 telemetry: no verified optional analytics dispatch anchor; " +
                "refusing to modify essential app diagnostics or session behavior"
        )
    }
}
