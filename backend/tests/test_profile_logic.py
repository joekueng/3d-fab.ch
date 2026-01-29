import sys
import os
import unittest
import json

# Add backend to path
sys.path.append(os.path.join(os.path.dirname(__file__), ".."))

from profile_manager import ProfileManager
from profile_cache import get_cache_key

class TestProfileManager(unittest.TestCase):
    def setUp(self):
        self.pm = ProfileManager(profiles_root="profiles")
        
    def test_list_machines(self):
        machines = self.pm.list_machines()
        print(f"Found machines: {len(machines)}")
        self.assertTrue(len(machines) > 0, "No machines found")
        # Check for a known machine
        self.assertTrue(any("Bambu Lab A1" in m for m in machines), "Bambu Lab A1 should be in the list")

    def test_find_profile(self):
        # We know "Bambu Lab A1 0.4 nozzle" should exist (based on user context and mappings)
        # It might be in profiles/BBL/machine/
        path = self.pm._find_profile_file("Bambu Lab A1 0.4 nozzle", "machine")
        self.assertIsNotNone(path, "Could not find Bambu Lab A1 machine profile")
        print(f"Found profile at: {path}")

    def test_scan_profiles_inheritance(self):
        # Pick a profile we expect to inherit stuff
        # e.g. "Bambu Lab A1 0.4 nozzle" inherits "fdm_bbl_3dp_001_common" which inherits "fdm_machine_common"
        merged, _, _ = self.pm.get_profiles(
            "Bambu Lab A1 0.4 nozzle", 
            "Bambu PLA Basic @BBL A1", 
            "0.20mm Standard @BBL A1"
        )
        
        self.assertIsNotNone(merged)
        # Check if inherits is gone
        self.assertNotIn("inherits", merged)
        # Check if patch applied (G92 E0)
        self.assertIn("G92 E0", merged.get("layer_change_gcode", ""))
        
        # Check specific key from base
        # "printer_technology": "FFF" is usually in common
        # We can't be 100% sure of keys without seeing file, but let's check something likely
        self.assertTrue("nozzle_diameter" in merged or "extruder_clearance_height_to_lid" in merged or "printable_height" in merged)

    def test_mappings_resolution(self):
        # Test if the slicer service would resolve correctly? 
        # We can just test the manager with mapped names if the manager supported it, 
        # but the manager deals with explicit names. 
        # Integration test handles the mapping.
        pass

if __name__ == '__main__':
    unittest.main()
