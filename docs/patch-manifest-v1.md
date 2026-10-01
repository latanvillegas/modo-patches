# Patch manifest contract

`patches-list.json` remains the executable patch catalog emitted from the patcher metadata.
The patcher remains authoritative for dependency resolution and application order. A manifest
consumer MUST NOT execute a patch merely because this document says it is compatible.

## PatchManifest v1

A manifest is declarative metadata associated with one patch. It may only narrow preflight/UI
eligibility; it cannot replace `dependsOn`, fingerprints, resource operations, bytecode operations,
or patch execution.

| Field | Type | Meaning |
| --- | --- | --- |
| schemaVersion | integer | Contract version; currently 1. |
| id | string | Stable machine identifier, unique in the bundle. |
| name | string | Display name. |
| description | string | Human-readable purpose. |
| applications | string[] | Logical groups such as all, cromite, youtube, reddit, music, shared. |
| packageNames | string[] | Allowed package IDs; empty only for genuinely universal patches. |
| versionRules | object | Exact/range rules used by preflight. |
| versionCodes | long[] | Optional allowed version codes. |
| abis | string[] | Optional allowed ABIs. |
| inputTypes | string[] | APK, UNIVERSAL_APK and/or SPLIT. |
| dependencies | string[] | Informational/stable patch IDs; patcher dependencies remain authoritative. |
| conflicts | string[] | Patch IDs that must not be selected together. |
| minimumPatcherVersion | string? | Minimum compatible patcher version. |
| mutations | string[] | RESOURCE, BYTECODE and/or MANIFEST. |
| signatureImpact | string | NONE, RESIGN_REQUIRED or SAME_CERTIFICATE_REQUIRED. |
| risk | string | LOW, MEDIUM or HIGH. |
| reversible | boolean | Whether repatching/restoring the original can reverse the change. |
| tests | string[] | Stable test IDs required for this patch. |
| documentation | string[] | Repository-relative documentation references. |

## Fail-closed rules

A specific patch is eligible only when all declared package, version/versionCode, ABI and input-type
rules match. Unknown manifest versions, malformed restrictions, declared conflicts, or an unmet
minimum patcher version are incompatible. A missing optional restriction does not invent one.

`application-profiles-v1.json` contains optional application-specific preflight restrictions owned by
`modo-patches`. Modo-Manager consumes profiles as generic data and MUST NOT compile application names,
package IDs, versions, hashes, Android code, Smali or fingerprints into its own source.

## Ownership

- `modo-patches`: executable patches, patcher-native compatibility, PatchManifest metadata and declarative application profiles.
- `Modo-Manager`: generic APK inspection, profile consumption, UI/preflight, transaction/report/export.
- `morphe-patcher`: dependency resolution, ordering and patch execution.

The source layout `all`, `cromite`, `youtube`, `reddit`, `music`, `shared` remains unchanged.
New application families follow the same layout.
