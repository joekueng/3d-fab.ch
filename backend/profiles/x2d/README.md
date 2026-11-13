# X2D profiles for OrcaSlicer 2.4.2

This isolated bundle contains the official X2D 0.4/0.6 mm machine and process
presets, Generic PA-CF/ASA and Bambu PET-CF. Inheritance is flattened against
upstream commit `8500fcdccaa10b5099ac20d252af3a7c560046f1` so the older shared
profiles in `profiles/` cannot change X2D speeds, temperatures or nozzle variants.
It does not replace the other printers' presets.

Regenerate with `python3 scripts/import-orca-x2d-profiles.py` from the repository
root. The importer verifies the upstream Git blob IDs and records source/output
SHA-256 hashes in `manifest.json`. Runtime job adjustments belong in
`SlicerProfilePreparation`, not in these imported presets.

Source: [OrcaSlicer v2.4.2](https://github.com/OrcaSlicer/OrcaSlicer/tree/v2.4.2/resources/profiles/BBL).
The upstream license is preserved in `UPSTREAM-LICENSE.txt`.
