#!/usr/bin/env bash
# Repository-local install; never replaces /Applications or system binaries.
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
metadata="$repo_root/backend/orcaslicer-release.json"
version="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["version"])' "$metadata")"
install_dir="$repo_root/.tools/orcaslicer/$version"

if [[ "$(uname -s)" != Darwin ]]; then
  echo 'Set SLICER_PATH to the pinned OrcaSlicer binary on non-macOS hosts; Docker installs it automatically.' >&2
  exit 2
fi
executable="$install_dir/OrcaSlicer.app/Contents/MacOS/OrcaSlicer"
if [[ -x "$executable" ]]; then
  printf '%s\n' "$executable"
  exit 0
fi

archive_name="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["assets"]["macos"]["name"])' "$metadata")"
checksum="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["assets"]["macos"]["sha256"])' "$metadata")"
mkdir -p "$install_dir"
archive="$install_dir/$archive_name"
if [[ ! -f "$archive" ]]; then
  echo "Downloading official OrcaSlicer $version for local development..." >&2
  curl --fail --location --retry 2 \
    "https://github.com/OrcaSlicer/OrcaSlicer/releases/download/v$version/$archive_name" -o "$archive.partial"
  mv "$archive.partial" "$archive"
fi
printf '%s  %s\n' "$checksum" "$archive" | shasum -a 256 -c - >&2
mount_root="$(mktemp -d "${TMPDIR:-/tmp}/printcalc-orca.XXXXXX")"
staging_dir="$(mktemp -d "$install_dir/.install.XXXXXX")"
cleanup() {
  hdiutil detach "$mount_root/mount" >/dev/null 2>&1 || true
  rmdir "$mount_root" 2>/dev/null || true
  rm -rf "$staging_dir"
}
trap cleanup EXIT
hdiutil attach "$archive" -nobrowse -readonly -mountpoint "$mount_root/mount" >&2
cp -R "$mount_root/mount/OrcaSlicer.app" "$staging_dir/OrcaSlicer.app"
if [[ -e "$install_dir/OrcaSlicer.app" ]]; then
  echo "An incomplete installation exists at $install_dir/OrcaSlicer.app; move it aside before retrying." >&2
  exit 2
fi
mv "$staging_dir/OrcaSlicer.app" "$install_dir/OrcaSlicer.app"
test -x "$executable"
printf '%s\n' "$executable"
