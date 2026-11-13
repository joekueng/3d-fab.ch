#!/usr/bin/env python3
"""Import verified TPU 85A presets using the existing pinned Orca profile importer."""
import importlib.util
import sys
from pathlib import Path

sys.dont_write_bytecode = True
script = Path(__file__).with_name('import-orca-x2d-profiles.py')
spec = importlib.util.spec_from_file_location('orca_profile_import', script)
importer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(importer)
importer.DESTINATION = importer.ROOT / 'backend/profiles/materials'
importer.MACHINES = set()
importer.FILAMENTS = {
    f'Bambu TPU 85A @BBL {machine}{suffix}'
    for machine in ('P2S', 'X2D') for suffix in ('', ' 0.8 nozzle')
}
if __name__ == '__main__':
    importer.main()
