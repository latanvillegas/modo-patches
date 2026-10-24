#!/usr/bin/env bash
# Read-only evidence collection for Cromite 153.0.8010.37 Theme settings.
set -euo pipefail
JADX_ROOT="${1:-$HOME/cromite-patch14-analysis/jadx}"
OUT="${2:-$HOME/cromite-theme-evidence}"
if [[ ! -d "$JADX_ROOT" ]]; then echo "JADX directory not found: $JADX_ROOT" >&2; exit 1; fi
mkdir -p "$OUT"
echo "Source: $JADX_ROOT" > "$OUT/manifest.txt"
echo "Date (UTC): $(date -u +%FT%TZ)" >> "$OUT/manifest.txt"
# Search names, resources and preference keys; never alter the existing JADX tree.
find "$JADX_ROOT" -type f \( -iname '*theme*' -o -iname '*night*mode*' \) | sort > "$OUT/theme-filenames.txt"
grep -RInE --include='*.java' --include='*.xml' --include='*.kt' \
  'ThemeSettingsFragment|theme_settings|darken_websites|night_mode|dark_theme|web_contents_dark_mode|Apply dark theme|Tema oscuro|theme_preference' \
  "$JADX_ROOT" > "$OUT/theme-references.txt" || true
grep -RIlE --include='*.java' --include='*.xml' \
  'ThemeSettingsFragment|theme_settings|darken_websites' "$JADX_ROOT" \
  | sort > "$OUT/candidate-files.txt" || true
echo "Candidates: $(wc -l < "$OUT/candidate-files.txt")" >> "$OUT/manifest.txt"
echo "References: $(wc -l < "$OUT/theme-references.txt")" >> "$OUT/manifest.txt"
echo "Saved evidence in: $OUT"
echo "Share manifest.txt, theme-filenames.txt, candidate-files.txt and theme-references.txt."
