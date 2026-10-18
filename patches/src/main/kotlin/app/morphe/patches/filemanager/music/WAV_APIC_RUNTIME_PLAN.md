# File Manager Plus 3.8.2 — WAV/APIC runtime integration plan

Status: **design only; runtime fallback NOT enabled**. Branch: `feature/filemanager-privacy-v2`.

## Verified baseline

- Decoder: `Lax/V4/a$b;->r([Ljava/lang/Void;)[Landroid/graphics/Bitmap;`, `classes5.dex`.
- 11 registers, 2 incoming, 4 outgoing; 164 instructions, 600 bytes; conditional and unconditional branch targets valid in the current working APK.
- `getEmbeddedPicture()[B` is followed by `move-result-object v2` and `if-eqz v2` (byte offsets 0x00bc, 0x00c2, 0x00c4).
- There are 12 try regions; changing instruction sizes or branch offsets without relocation can cause Android 15 `VerifyError`.
- The current patch changes callback/cache selection and `RGB_565` to `ARGB_8888`. Do not disturb these stable edits.
- `WavApicExtractor.kt` in `patches/src/main/kotlin` is **host-side only**. It is not automatically shipped into the patched APK.
- Existing `APIC` classes `Lax/E1/a;` and `Lax/z6/b2;` are metadata frame objects, not standalone WAV parsers. `RIFF` in `Lax/y0/a;` is not evidence of WAV/APIC support.

## Runtime implementation requirements

1. Confirm the exact extension DEX injection API in the version of Morphe used by this repository; extension support elsewhere in the repo does **not** prove compatibility with File Manager Plus.
2. Build a target-side helper with an explicit, stable descriptor such as `Lapp/morphe/extension/filemanager/WavApic;->extract(Landroid/content/Context;Ljava/lang/String;)[B`. Ensure the helper is included in the output APK and verify it by DEX inspection before enabling any call site.
3. Support both local file paths and `content://` URIs (via `ContentResolver.openInputStream`). Bound total reads and allocations; reject unsupported RIFF/ID3 variants without throwing into the player.
4. Preserve the original `MediaMetadataRetriever.getEmbeddedPicture()` result. Only attempt WAV/APIC fallback when that result is null and the source is a WAV; return null on unsupported or malformed content.
5. Insert the call using the patcher's instruction and label-aware facilities; preserve the invoke/move-result adjacency, register types, existing monitor regions, and all try/catch boundaries. Do not hand-calculate branch targets.
6. Verify all branch targets, try handler starts and ends, registers, and helper method resolution in the output DEX **before** installing.
7. Test WAV with ID3v2.3 APIC JPEG/PNG, WAV without APIC, malformed/truncated RIFF, ordinary MP3/FLAC, and local vs provider URIs. Recheck playback, cache, and absence of `VerifyError`.

## Gate

Do not activate WAV/APIC fallback or run an APK build merely because the host-side extractor compiles. First establish and validate target-side DEX inclusion. Preserve the existing working patch until that gate is passed.
