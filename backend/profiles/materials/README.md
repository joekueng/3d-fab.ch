# Additional stocked-material presets

These self-contained TPU 85A P2S/X2D presets come from official OrcaSlicer 2.4.2,
commit `8500fcdccaa10b5099ac20d252af3a7c560046f1`. They preserve upstream values
and declare compatibility with the respective **0.6 mm** nozzle only. Their
maximum volumetric speed is 2.2 mm³/s, not the generic TPU/PLA flow limit.

Regenerate with `python3 scripts/import-orca-stock-material-profiles.py`.
It reuses the existing verified importer, checks upstream Git blob IDs, flattens
inheritance, records SHA-256 evidence in `manifest.json`, and includes the license.

Deploy the matching backend and frontend restrictions before enabling TPU 85A.
The current fleet policy permits only 0.6 mm, with 0.24 mm layers in quick mode;
advanced layers also obey the existing technical-material policy. This remains
true even if the admin Technical flag changes. P2S is the configured quoting
machine for this material; the X2D preset is retained as an upstream reference.

Official
source: [OrcaSlicer v2.4.2 BBL profiles](https://github.com/OrcaSlicer/OrcaSlicer/tree/v2.4.2/resources/profiles/BBL/filament).
