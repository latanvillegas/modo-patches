#!/usr/bin/env python3
import argparse, json, re
from pathlib import Path

def load(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("catalog")
    ap.add_argument("metadata")
    ap.add_argument("output")
    args=ap.parse_args()
    catalog=load(args.catalog)
    metadata=load(args.metadata)
    by_name={p["name"]:p for p in catalog.get("patches",[])}
    seen=set()
    out=[]
    for meta in metadata.get("patches",[]):
        pid=meta["id"]
        if not re.fullmatch(r"[a-z0-9]+(?:[.-][a-z0-9]+)*",pid):
            raise SystemExit(f"invalid stable patch id: {pid}")
        if pid in seen: raise SystemExit(f"duplicate stable patch id: {pid}")
        seen.add(pid)
        name=meta["name"]
        src=by_name.get(name)
        if src is None: raise SystemExit(f"manifest patch not found in executable catalog: {name}")
        packages=[]; exact=[]; version_codes=set(); input_types=set()
        for pkg in src.get("compatiblePackages") or []:
            if pkg.get("packageName"): packages.append(pkg["packageName"])
            ft=pkg.get("apkFileType")
            if ft=="APK_REQUIRED": input_types.add("APK")
            elif ft: input_types.add(ft)
            for target in pkg.get("targets") or []:
                if target.get("version"): exact.append(target["version"])
                for code in target.get("versionCodes") or []: version_codes.add(int(code))
        item={
            "schemaVersion":1,
            "id":pid,
            "name":name,
            "description":src.get("description") or "",
            "applications":meta.get("applications",[]),
            "packageNames":sorted(set(packages)),
            "versionRules":{"exact":sorted(set(exact))},
            "versionCodes":sorted(version_codes),
            "abis":meta.get("abis",[]),
            "inputTypes":sorted(input_types or set(meta.get("inputTypes",["APK","UNIVERSAL_APK","SPLIT"]))),
            "dependencies":meta.get("dependencies",[]),
            "conflicts":meta.get("conflicts",[]),
            "minimumPatcherVersion":meta.get("minimumPatcherVersion"),
            "mutations":meta["mutations"],
            "signatureImpact":meta["signatureImpact"],
            "risk":meta["risk"],
            "reversible":bool(meta["reversible"]),
            "tests":meta.get("tests",[]),
            "documentation":meta.get("documentation",[]),
        }
        out.append(item)
    result={"schemaVersion":1,"catalogVersion":catalog.get("version"),"patches":sorted(out,key=lambda x:x["id"])}
    Path(args.output).write_text(json.dumps(result,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
if __name__=="__main__": main()
