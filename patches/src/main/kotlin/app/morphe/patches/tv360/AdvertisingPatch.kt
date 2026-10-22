package app.morphe.patches.tv360

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch

/**
 * Reserved for verified advertisement presentation anchors only.
 * Never patch playback, entitlement, billing, or authentication calls.
 */
@Suppress("unused")
val tv360AdvertisingPatch = bytecodePatch(
    name = "TV360 Bitel - Advertising Controls",
    description = "Reserved for independently verified advertisement UI changes.",
    default = false,
) {
    compatibleWith("com.bitel.tv360"("3.5"))

    execute {
        throw PatchException(
            "TV360 advertising: no verified DEX advertisement presentation anchor; " +
                "refusing to modify playback or authentication code"
        )
    }
}
