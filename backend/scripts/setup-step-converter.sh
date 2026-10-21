#!/usr/bin/env bash
set -eu

backend_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
requirements="$backend_root/step-requirements.txt"
converter_python="${STEP_PYTHON_PATH:-$backend_root/venv/bin/python}"

if [ -n "${STEP_PYTHON_PATH:-}" ]; then
  "$converter_python" -I -c 'from OCP.STEPControl import STEPControl_Reader; from OCP.BRepMesh import BRepMesh_IncrementalMesh; from OCP.StlAPI import StlAPI_Writer'
  exit 0
fi

requirements_hash="$(python3 -c 'import hashlib,sys; print(hashlib.sha256(open(sys.argv[1], "rb").read()).hexdigest())' "$requirements")"
stamp="$backend_root/venv/.step-requirements.sha256"
if [ ! -x "$converter_python" ]; then
  python3 -m venv "$backend_root/venv"
fi
if [ ! -f "$stamp" ] || [ "$(cat "$stamp")" != "$requirements_hash" ]; then
  "$converter_python" -m pip install --require-hashes --only-binary=:all: -r "$requirements"
fi
"$converter_python" -I -c 'from OCP.STEPControl import STEPControl_Reader; from OCP.BRepMesh import BRepMesh_IncrementalMesh; from OCP.StlAPI import StlAPI_Writer'
printf '%s\n' "$requirements_hash" > "$stamp"
