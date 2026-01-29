import os
import json
import logging
from typing import Dict, List, Tuple, Optional
from profile_cache import get_cache_key


logger = logging.getLogger(__name__)

class ProfileManager:
    def __init__(self, profiles_root: str = "profiles"):
        # Assuming profiles_root is relative to backend or absolute
        if not os.path.isabs(profiles_root):
            base_dir = os.path.dirname(os.path.abspath(__file__))
            self.profiles_root = os.path.join(base_dir, profiles_root)
        else:
            self.profiles_root = profiles_root
            
        if not os.path.exists(self.profiles_root):
            logger.warning(f"Profiles root not found: {self.profiles_root}")

    def get_profiles(self, machine: str, filament: str, process: str) -> Tuple[Dict, Dict, Dict]:
        """
        Main entry point to get merged profiles.
        Args:
            machine: e.g. "Bambu Lab A1 0.4 nozzle"
            filament: e.g. "Bambu PLA Basic @BBL A1"
            process: e.g. "0.20mm Standard @BBL A1"
        """
        # Try cache first (although specific logic is needed if we cache the *result* or the *files*)
        # Since we implemented a simple external cache helper, let's use it if we want, 
        # but for now we will rely on internal logic or the lru_cache decorator on a helper method.
        # But wait, the `get_cached_profiles` in profile_cache.py calls `build_merged_profiles` which is logic WE need to implement.
        # So we should probably move the implementation here and have the cache wrapper call it, 
        # OR just implement it here and wrap it.
        
        return self._build_merged_profiles(machine, filament, process)

    def _build_merged_profiles(self, machine_name: str, filament_name: str, process_name: str) -> Tuple[Dict, Dict, Dict]:
        # We need to find the files. 
        # The naming convention in OrcaSlicer profiles usually involves the Vendor (e.g. BBL).
        # We might need a mapping or search.
        # For this implementation, we will assume we know the relative paths or search for them.
        
        # Strategy: Search in all vendor subdirs for the specific JSON files.
        # Because names are usually unique enough or we can specify the expected vendor.
        # However, to be fast, we can map "machine_name" to a file path.
        
        machine_file = self._find_profile_file(machine_name, "machine")
        filament_file = self._find_profile_file(filament_name, "filament")
        process_file = self._find_profile_file(process_name, "process")
        
        if not machine_file:
            raise FileNotFoundError(f"Machine profile not found: {machine_name}")
        if not filament_file:
            raise FileNotFoundError(f"Filament profile not found: {filament_name}")
        if not process_file:
            raise FileNotFoundError(f"Process profile not found: {process_name}")
            
        machine_profile = self._merge_chain(machine_file)
        filament_profile = self._merge_chain(filament_file)
        process_profile = self._merge_chain(process_file)
        
        # Apply patches
        machine_profile = self._apply_patches(machine_profile, "machine")
        process_profile = self._apply_patches(process_profile, "process")
        
        return machine_profile, process_profile, filament_profile

    def _find_profile_file(self, profile_name: str, profile_type: str) -> Optional[str]:
        """
        Searches for a profile file by name in the profiles directory.
        The name should match the filename (without .json possibly) or be a precise match.
        """
        # Add .json if missing
        filename = profile_name if profile_name.endswith(".json") else f"{profile_name}.json"
        
        for root, dirs, files in os.walk(self.profiles_root):
            if filename in files:
                # Check if it is in the correct type folder (machine, filament, process)
                # OrcaSlicer structure: Vendor/process/file.json
                # We optionally verify parent dir
                if os.path.basename(root) == profile_type or profile_type in root:
                    return os.path.join(root, filename)
                
                # Fallback: if we simply found it, maybe just return it? 
                # Some common files might be in root or other places.
                # Let's return it if we are fairly sure.
                return os.path.join(root, filename)
        
        return None

    def _merge_chain(self, final_file_path: str) -> Dict:
        """
        Resolves inheritance and merges.
        """
        chain = []
        current_path = final_file_path
        
        # 1. Build chain
        while current_path:
            chain.insert(0, current_path) # Prepend
            
            with open(current_path, 'r', encoding='utf-8') as f:
                try:
                    data = json.load(f)
                except json.JSONDecodeError as e:
                    logger.error(f"Failed to decode JSON: {current_path}")
                    raise e
            
            inherits = data.get("inherits")
            if inherits:
                # Resolve inherited file
                # It is usually in the same directory or relative.
                # OrcaSlicer logic: checks same dir, then parent, etc.
                # Usually it's in the same directory.
                parent_dir = os.path.dirname(current_path)
                inherited_path = os.path.join(parent_dir, inherits)
                
                # Special case: if not found, it might be in a common folder? 
                # But OrcaSlicer usually keeps them local or in specific common dirs.
                if not os.path.exists(inherited_path) and not inherits.endswith(".json"):
                     inherited_path += ".json"
                
                if os.path.exists(inherited_path):
                    current_path = inherited_path
                else:
                    # Could be a system common file not in the same dir?
                    # For simplicty, try to look up in the same generic type folder across the vendor?
                    # Or just fail for now. 
                    # Often "fdm_machine_common.json" is at the Vendor root or similar?
                    # Let's try searching recursively if not found in place.
                    found = self._find_profile_file(inherits, "any") # "any" type
                    if found:
                        current_path = found
                    else:
                        logger.warning(f"Inherited profile '{inherits}' not found for '{current_path}' (Root: {self.profiles_root})")
                        current_path = None
            else:
                current_path = None

        # 2. Merge
        merged = {}
        for path in chain:
            with open(path, 'r', encoding='utf-8') as f:
                data = json.load(f)
                # Shallow update
                merged.update(data)
                
        # Remove metadata
        merged.pop("inherits", None)
        
        return merged

    def _apply_patches(self, profile: Dict, profile_type: str) -> Dict:
        if profile_type == "machine":
            # Patch: G92 E0 to ensure extrusion reference text matches
            lcg = profile.get("layer_change_gcode", "")
            if "G92 E0" not in lcg:
                # Append neatly
                if lcg and not lcg.endswith("\n"):
                    lcg += "\n"
                lcg += "G92 E0"
                profile["layer_change_gcode"] = lcg
            
            # Patch: ensure printable height is sufficient? 
            # Only if necessary. For now, trust the profile.

        elif profile_type == "process":
            # Optional: Disable skirt/brim if we want a "clean" print estimation?
            # Actually, for accurate cost, we SHOULD include skirt/brim if the profile has it.
            pass
            
        return profile
    
    def list_machines(self) -> List[str]:
        # Simple helper to list available machine JSONs
        return self._list_profiles_by_type("machine")
        
    def list_filaments(self) -> List[str]:
        return self._list_profiles_by_type("filament")

    def list_processes(self) -> List[str]:
        return self._list_profiles_by_type("process")

    def _list_profiles_by_type(self, ptype: str) -> List[str]:
        results = []
        for root, dirs, files in os.walk(self.profiles_root):
            if os.path.basename(root) == ptype:
                for f in files:
                    if f.endswith(".json") and "common" not in f:
                         results.append(f.replace(".json", ""))
        return sorted(results)
