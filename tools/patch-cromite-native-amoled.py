#!/usr/bin/env python3
"""Targeted AMOLED color-resource edit for decoded Cromite 153 APK resources.

Usage:
  python3 tools/patch-cromite-native-amoled.py ~/cromite-native-colors/resources
  python3 tools/patch-cromite-native-amoled.py ~/cromite-native-colors/resources --apply

Dry run by default. This edits decoded XML resources only; it does not rebuild
or sign an APK. Run against a working copy before APK packaging.
"""
import argparse
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

DARK = {"0x7f070170": "#121212", "0x7f07017c": "#121212"}
LIGHT = {"0x7f07017d": "#ffffff", "0x7f070189": "#ffffff"}
ATTRIBUTE = "0x7f050154"
DARK_STYLES = ("0x7f1500b4", "0x7f1500b6", "0x7f1505d7")
LIGHT_STYLES = ("0x7f1500b8", "0x7f1500bb", "0x7f1505dd")


def resource_name(resource_id):
    return "_0_resource_name_obfuscated_res_" + resource_id


def validate(resources):
    colors_file = resources / "values" / "colors.xml"
    styles_file = resources / "values" / "styles.xml"
    if not colors_file.is_file() or not styles_file.is_file():
        raise ValueError("Expected decoded res/values/colors.xml and styles.xml")

    colors = ET.parse(colors_file).getroot()
    styles = ET.parse(styles_file).getroot()
    color_nodes = {node.get("name"): node for node in colors.findall("color")}
    for resource_id, expected in {**DARK, **LIGHT}.items():
        node = color_nodes.get(resource_name(resource_id))
        if node is None:
            raise ValueError("Missing color " + resource_id)
        current = (node.text or "").strip().lower()
        allowed = {expected, "#000000"} if resource_id in DARK else {expected}
        if current not in allowed:
            raise ValueError(f"Unexpected {resource_id}={current}; expected {sorted(allowed)}")

    style_nodes = {node.get("name"): node for node in styles.findall("style")}
    for resource_id in DARK_STYLES + LIGHT_STYLES:
        node = style_nodes.get(resource_name(resource_id))
        if node is None:
            raise ValueError("Missing style " + resource_id)
        attrs = {
            item.get("name"): (item.text or "").strip()
            for item in node.findall("item")
        }
        expected_color = "0x7f07017c" if resource_id in DARK_STYLES else "0x7f070189"
        if attrs.get(resource_name(ATTRIBUTE)) != "@color/" + resource_name(expected_color):
            raise ValueError("Unexpected background attribute in style " + resource_id)

    return colors_file, color_nodes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("resources", type=Path, help="JADX decoded resources directory containing res/")
    parser.add_argument("--apply", action="store_true", help="Write changes after validation")
    args = parser.parse_args()
    root = args.resources
    if (root / "res" / "values" / "colors.xml").exists():
        root = root / "res"
    try:
        colors_file, color_nodes = validate(root)
        pending = [
            resource_id for resource_id in DARK
            if (color_nodes[resource_name(resource_id)].text or "").strip().lower() != "#000000"
        ]
        for resource_id in DARK:
            current = (color_nodes[resource_name(resource_id)].text or "").strip()
            print(f"{resource_id}: {current} -> #000000")
        for resource_id in LIGHT:
            print(f"{resource_id}: unchanged #ffffff")
        if not args.apply:
            print("DRY RUN: no files modified. Pass --apply to write.")
            return 0
        if not pending:
            print("Already patched; no changes.")
            return 0

        # Preserve all other XML content and formatting, replacing exact lines only.
        original = colors_file.read_text(encoding="utf-8")
        updated = original
        for resource_id in pending:
            name = resource_name(resource_id)
            import re
            expression = r'(<color\s+name="' + re.escape(name) + r'">)\s*#121212\s*(</color>)'
            updated, count = re.subn(expression, r'\g<1>#000000\g<2>', updated, flags=re.I)
            if count != 1:
                raise ValueError(f"Expected exactly one XML color entry for {resource_id}; got {count}")
        if updated == original:
            raise ValueError("No changes produced")
        colors_file.write_text(updated, encoding="utf-8")
        validate(root)
        print(f"APPLIED: {colors_file} (decoded XML only, APK not rebuilt)")
        return 0
    except (OSError, ET.ParseError, ValueError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
