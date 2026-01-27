import os
import sys

class Settings:
    # Directories
    BASE_DIR = os.path.dirname(os.path.abspath(__file__))
    TEMP_DIR = os.environ.get("TEMP_DIR", os.path.join(BASE_DIR, "temp"))
    PROFILES_DIR = os.environ.get("PROFILES_DIR", os.path.join(BASE_DIR, "profiles"))

    # Slicer Paths
    if sys.platform == "darwin":
        _DEFAULT_SLICER_PATH = "/Applications/OrcaSlicer.app/Contents/MacOS/OrcaSlicer"
    else:
        _DEFAULT_SLICER_PATH = "/opt/orcaslicer/AppRun"

    SLICER_PATH = os.environ.get("SLICER_PATH", _DEFAULT_SLICER_PATH)
    ORCA_HOME = os.environ.get("ORCA_HOME", "/opt/orcaslicer")
    
    # Defaults Profiles (Bambu A1)
    MACHINE_PROFILE = os.path.join(PROFILES_DIR, "Bambu_Lab_A1_machine.json")
    PROCESS_PROFILE = os.path.join(PROFILES_DIR, "Bambu_Process_0.20_Standard.json")
    FILAMENT_PROFILE = os.path.join(PROFILES_DIR, "Bambu_PLA_Basic.json")

    # Pricing
    FILAMENT_COST_PER_KG = float(os.environ.get("FILAMENT_COST_PER_KG", 25.0))
    MACHINE_COST_PER_HOUR = float(os.environ.get("MACHINE_COST_PER_HOUR", 2.0))
    ENERGY_COST_PER_KWH = float(os.environ.get("ENERGY_COST_PER_KWH", 0.30))
    PRINTER_POWER_WATTS = float(os.environ.get("PRINTER_POWER_WATTS", 150.0))
    MARKUP_PERCENT = float(os.environ.get("MARKUP_PERCENT", 20.0))

settings = Settings()
