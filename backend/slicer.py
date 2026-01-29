import subprocess
import os
import json
import logging
import shutil
import tempfile
from typing import Optional, Dict
from config import settings
from profile_manager import ProfileManager

logger = logging.getLogger(__name__)

class SlicerService:
    def __init__(self):
        self.profile_manager = ProfileManager()
    
    def slice_stl(
        self,
        input_stl_path: str,
        output_gcode_path: str,
        machine: str = "bambu_a1",
        filament: str = "pla_basic",
        quality: str = "standard",
        overrides: Optional[Dict] = None
    ) -> bool:
        """
        Runs OrcaSlicer in headless mode with dynamic profiles.
        """
        if not os.path.exists(settings.SLICER_PATH):
             raise RuntimeError(f"Slicer executable not found at: {settings.SLICER_PATH}")

        if not os.path.exists(input_stl_path):
            raise FileNotFoundError(f"STL file not found: {input_stl_path}")

        # 1. Get Merged Profiles
        # Use simple mapping if the input is short code (bambu_a1) vs full name
        # For now, we assume the caller solves the mapping or passes full names?
        # Actually, the user wants "Bambu A1" from API to map to "Bambu Lab A1 0.4 nozzle"
        # We should use the mapping logic here or in the caller?
        # The implementation plan said "profile_mappings.json" maps keys.
        # It's better to handle mapping in the Service layer or Manager.
        # Let's load the mapping in the service for now, or use a helper.
        
        # We'll use a helper method to resolve names to full profile names using the loaded mapping.
        machine_p, filament_p, quality_p = self._resolve_profile_names(machine, filament, quality)
        
        try:
            m_profile, p_profile, f_profile = self.profile_manager.get_profiles(machine_p, filament_p, quality_p)
        except FileNotFoundError as e:
            logger.error(f"Profile error: {e}")
            raise RuntimeError(f"Profile generation failed: {e}")

        # 2. Apply Overrides
        if overrides:
            p_profile = self._apply_overrides(p_profile, overrides)
            # Some overrides might apply to machine or filament, but mostly process.
            # E.g. layer_height is in process.

        # 3. Write Temp Profiles
        # We create a temp dir for this slice job
        output_dir = os.path.dirname(output_gcode_path)
        # We keep temp profiles in a hidden folder or just temp
        # Using a context manager for temp dir might be safer but we need it for the subprocess duration
        
        with tempfile.TemporaryDirectory() as temp_dir:
            m_path = os.path.join(temp_dir, "machine.json")
            p_path = os.path.join(temp_dir, "process.json")
            f_path = os.path.join(temp_dir, "filament.json")
            
            with open(m_path, 'w') as f: json.dump(m_profile, f)
            with open(p_path, 'w') as f: json.dump(p_profile, f)
            with open(f_path, 'w') as f: json.dump(f_profile, f)
            
            # 4. Build Command
            command = self._build_slicer_command(input_stl_path, output_dir, m_path, p_path, f_path)
            
            logger.info(f"Starting slicing for {input_stl_path} [M:{machine_p} F:{filament_p} Q:{quality_p}]")
            try:
                self._run_command(command)
                self._finalize_output(output_dir, input_stl_path, output_gcode_path)
                logger.info("Slicing completed successfully.")
                return True
            except subprocess.CalledProcessError as e:
                # Cleanup is automatic via tempfile, but we might want to preserve invalid gcode?
                raise RuntimeError(f"Slicing failed: {e.stderr if e.stderr else e.stdout}")

    def _resolve_profile_names(self, m: str, f: str, q: str) -> tuple[str, str, str]:
        # Load mappings
        # Allow passing full names if they don't exist in mapping
        mapping_path = os.path.join(os.path.dirname(__file__), "profile_mappings.json")
        try:
            with open(mapping_path, 'r') as fp:
                mappings = json.load(fp)
        except Exception:
            logger.warning("Could not load profile_mappings.json, using inputs as raw names.")
            return m, f, q
            
        m_real = mappings.get("machine_to_profile", {}).get(m, m)
        f_real = mappings.get("filament_to_profile", {}).get(f, f)
        q_real = mappings.get("quality_to_process", {}).get(q, q)
        
        return m_real, f_real, q_real

    def _apply_overrides(self, profile: Dict, overrides: Dict) -> Dict:
        for k, v in overrides.items():
            # OrcaSlicer values are often strings
            profile[k] = str(v)
        return profile

    def _build_slicer_command(self, input_path: str, output_dir: str, m_path: str, p_path: str, f_path: str) -> list:
        # Settings format: "machine_file;process_file" (filament separate)
        settings_arg = f"{m_path};{p_path}"
        
        return [
            settings.SLICER_PATH,
            "--load-settings", settings_arg,
            "--load-filaments", f_path,
            "--ensure-on-bed",
            "--arrange", "1",
            "--slice", "0",
            "--outputdir", output_dir,
            input_path
        ]

    def _run_command(self, command: list):
        # logging and running logic similar to before
        logger.debug(f"Exec: {' '.join(command)}")
        result = subprocess.run(
            command,
            check=False,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )
        if result.returncode != 0:
            logger.error(f"Slicer Error: {result.stderr}")
            raise subprocess.CalledProcessError(
                result.returncode, command, output=result.stdout, stderr=result.stderr
            )

    def _finalize_output(self, output_dir: str, input_path: str, target_path: str):
        input_basename = os.path.basename(input_path)
        expected_name = os.path.splitext(input_basename)[0] + ".gcode"
        generated_path = os.path.join(output_dir, expected_name)

        if not os.path.exists(generated_path):
            alt_path = os.path.join(output_dir, "plate_1.gcode")
            if os.path.exists(alt_path):
                generated_path = alt_path

        if os.path.exists(generated_path) and generated_path != target_path:
            shutil.move(generated_path, target_path)

slicer_service = SlicerService()
