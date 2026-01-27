import subprocess
import os
import json
import logging
from config import settings

logger = logging.getLogger(__name__)

class SlicerService:
    def __init__(self):
        self._ensure_profiles_exist()

    def _ensure_profiles_exist(self):
        """
        Checks if the internal profiles exist. Low priority check to avoid noise.
        """
        if os.path.exists(settings.ORCA_HOME):
            for p in [settings.MACHINE_PROFILE, settings.PROCESS_PROFILE, settings.FILAMENT_PROFILE]:
                if not os.path.exists(p):
                    logger.warning(f"Internal profile not found: {p}")

    def slice_stl(self, input_stl_path: str, output_gcode_path: str) -> bool:
        """
        Runs OrcaSlicer in headless mode to slice the STL file.
        """
        if not os.path.exists(settings.SLICER_PATH):
             raise RuntimeError(f"Slicer executable not found at: {settings.SLICER_PATH}. Please install OrcaSlicer.")

        if not os.path.exists(input_stl_path):
            raise FileNotFoundError(f"STL file not found: {input_stl_path}")

        output_dir = os.path.dirname(output_gcode_path)
        override_path = self._create_override_machine_config(output_dir)
        
        # Prepare command
        command = self._build_slicer_command(input_stl_path, output_dir, override_path)
        
        logger.info(f"Slicing Command: {' '.join(command)}")
        logger.info(f"Using Profiles provided in command settings argument.")
        
        logger.info(f"Starting slicing for {input_stl_path}...")
        try:
            self._run_command(command)
            self._finalize_output(output_dir, input_stl_path, output_gcode_path)
            logger.info("Slicing completed successfully.")
            return True
        except subprocess.CalledProcessError as e:
            msg = f"Slicing failed. Return code: {e.returncode}\nSTDOUT:\n{e.stdout}\nSTDERR:\n{e.stderr}"
            logger.error(msg)
            raise RuntimeError(f"Slicing failed: {e.stderr if e.stderr else e.stdout}")

    def _create_override_machine_config(self, output_dir: str) -> str:
        """
        Creates an optionally modified machine config to fix relative addressing and bed size.
        Returns the path to the override config file.
        """
        override_path = os.path.join(output_dir, "machine_override.json")
        machine_config = {}

        if os.path.exists(settings.MACHINE_PROFILE):
            try:
                with open(settings.MACHINE_PROFILE, 'r') as f:
                    machine_config = json.load(f)
            except Exception as e:
                logger.warning(f"Failed to load machine profile: {e}")

        # Apply Fixes
        # 1. G92 E0 for relative extrusion safety
        gcode = machine_config.get("layer_change_gcode", "")
        if "G92 E0" not in gcode:
             machine_config["layer_change_gcode"] = (gcode + "\nG92 E0").strip()

        # 2. Expand bed size for large prints estimation
        machine_config.update({
             "printable_height": "1000",
             "printable_area": ["0x0", "1000x0", "1000x1000", "0x1000"],
             "bed_custom_model": "",
             "bed_exclude_area": []
        })

        # Save override
        try:
            with open(override_path, "w") as f:
                json.dump(machine_config, f)
        except Exception as e:
            logger.warning(f"Could not save override file: {e}")
            # If we fail to save, we might just return the original profile or a basic one
            # But here we return the path anyway as the slicer might fail later if this didn't work.
        
        return override_path

    def _build_slicer_command(self, input_path: str, output_dir: str, machine_profile: str) -> list:
        # Construct settings argument
        # Note: Order matters for some slicers, but here just loading them.
        settings_items = [machine_profile, settings.PROCESS_PROFILE, settings.FILAMENT_PROFILE]
        settings_arg = ";".join(settings_items)

        return [
            settings.SLICER_PATH,
            "--load-settings", settings_arg,
            "--ensure-on-bed",
            "--arrange", "1",
            "--slice", "0",
            "--outputdir", output_dir,
            input_path
        ]

    def _run_command(self, command: list):
        result = subprocess.run(
            command,
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )
        if result.stdout:
            logger.info(f"Slicer STDOUT:\n{result.stdout[:2000]}...") # Log first 2000 chars to avoid explosion
        if result.stderr:
            logger.warning(f"Slicer STDERR:\n{result.stderr}")

    def _finalize_output(self, output_dir: str, input_path: str, target_path: str):
        """
        Finds the generated G-code and renames it to the target path.
        """
        input_basename = os.path.basename(input_path)
        # OrcaSlicer usually outputs <basename>.gcode
        expected_name = os.path.splitext(input_basename)[0] + ".gcode"
        generated_path = os.path.join(output_dir, expected_name)

        # Fallback for plate_1.gcode
        if not os.path.exists(generated_path):
            alt_path = os.path.join(output_dir, "plate_1.gcode")
            if os.path.exists(alt_path):
                generated_path = alt_path

        if os.path.exists(generated_path) and generated_path != target_path:
            os.rename(generated_path, target_path)

slicer_service = SlicerService()
