#!/usr/bin/env python3
"""Import self-contained X2D profiles from the verified OrcaSlicer release."""
import hashlib
import json
from pathlib import Path
import urllib.parse
import urllib.request

COMMIT = "8500fcdccaa10b5099ac20d252af3a7c560046f1"  # OrcaSlicer v2.4.2
ROOT = Path(__file__).resolve().parents[1]
DESTINATION = ROOT / "backend/profiles/x2d"
PREFIX = "resources/profiles/BBL/"
MACHINES = {f"Bambu Lab X2D {nozzle} nozzle" for nozzle in ("0.4", "0.6")}
FILAMENTS = {"Bambu PET-CF @BBL X2D", "Bambu PET-CF @BBL X2D 0.4 nozzle"} | {
    f"Generic {material} @BBL X2D{suffix}"
    for material in ("PA-CF", "ASA")
    for suffix in ("", " 0.4 nozzle")
}


def fetch(url):
    request = urllib.request.Request(url, headers={"User-Agent": "print-calculator-profile-import"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()


def main():
    tree = json.loads(fetch(f"https://api.github.com/repos/OrcaSlicer/OrcaSlicer/git/trees/{COMMIT}?recursive=1"))
    if tree.get("truncated"):
        raise RuntimeError("Incomplete upstream file index")
    entries = {entry["path"]: entry for entry in tree["tree"] if entry["type"] == "blob"}
    paths = sorted(path for path in entries if path.startswith(PREFIX) and path.endswith(".json"))
    cache = {}
    sources = {}

    def load(path):
        if path not in cache:
            data = fetch(f"https://raw.githubusercontent.com/OrcaSlicer/OrcaSlicer/{COMMIT}/{urllib.parse.quote(path)}")
            blob = hashlib.sha1(f"blob {len(data)}\0".encode() + data).hexdigest()
            if blob != entries[path]["sha"]:
                raise RuntimeError(f"Upstream Git blob verification failed: {path}")
            sources[path] = hashlib.sha256(data).hexdigest()
            cache[path] = json.loads(data)
        return cache[path]

    def merge(parent, child):
        result = dict(parent)
        for key, value in child.items():
            result[key] = merge(result[key], value) if isinstance(value, dict) and isinstance(result.get(key),
                                                                                              dict) else value
        return result

    def flatten(path, ancestors=()):
        if path in ancestors:
            raise RuntimeError(f"Inheritance cycle: {path}")
        profile = load(path)
        if "inherits" in profile:
            filename = profile["inherits"] + ".json"
            local = str(Path(path).parent / filename)
            candidates = [candidate for candidate in paths if Path(candidate).name == filename]
            if local in candidates:
                parent = local
            else:
                typed = [candidate for candidate in candidates if Path(path).parts[3] in Path(candidate).parts]
                if len(typed) != 1:
                    raise RuntimeError(f"Missing or ambiguous parent {filename} for {path}")
                parent = typed[0]
            profile = merge(flatten(parent, (*ancestors, path)), profile)
        return {key: value for key, value in profile.items() if key != "inherits"}

    selected = []
    for path in paths:
        filename = Path(path).stem
        if filename in MACHINES or filename in FILAMENTS:
            selected.append(path)
        elif "/process/" in path and "X2D" in filename:
            profile = flatten(path)
            if MACHINES.intersection(profile.get("compatible_printers", [])):
                selected.append(path)

    outputs = {}
    for path in selected:
        profile = flatten(path)
        category = profile["type"]
        output = DESTINATION / category / Path(path).name
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(profile, ensure_ascii=False, indent=4) + "\n")
        outputs[str(output.relative_to(DESTINATION))] = hashlib.sha256(output.read_bytes()).hexdigest()
    license_data = fetch(f"https://raw.githubusercontent.com/OrcaSlicer/OrcaSlicer/{COMMIT}/LICENSE.txt")
    if hashlib.sha1(f"blob {len(license_data)}\0".encode() + license_data).hexdigest() != entries["LICENSE.txt"]["sha"]:
        raise RuntimeError("Upstream license verification failed")
    (DESTINATION / "UPSTREAM-LICENSE.txt").write_bytes(license_data)
    sources["LICENSE.txt"] = hashlib.sha256(license_data).hexdigest()
    outputs["UPSTREAM-LICENSE.txt"] = sources["LICENSE.txt"]
    (DESTINATION / "manifest.json").write_text(json.dumps({
        "release": "2.4.2", "commit": COMMIT,
        "source_sha256": dict(sorted(sources.items())), "output_sha256": dict(sorted(outputs.items())),
    }, indent=2) + "\n")
    print(f"Imported {len(outputs)} flattened X2D profiles from {COMMIT}")


if __name__ == "__main__":
    main()
