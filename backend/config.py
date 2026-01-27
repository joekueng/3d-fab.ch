import os

class Settings:
    # Directories
    BASE_DIR = os.path.dirname(os.path.abspath(__file__))
    TEMP_DIR = os.environ.get("TEMP_DIR", os.path.join(BASE_DIR, "temp"))
    PROFILES_DIR = os.environ.get("PROFILES_DIR", os.path.join(BASE_DIR, "profiles"))

    # Slicer Paths
    SLICER_PATH = os.environ.get("SLICER_PATH", "/opt/orcaslicer/AppRun")
    ORCA_HOME = os.environ.get("ORCA_HOME", "/opt/orcaslicer")
    
    # Defaults Profiles (Bambu A1)
    MACHINE_PROFILE = os.path.join(ORCA_HOME, "resources/profiles/BBL/machine/Bambu Lab A1 0.4 nozzle.json")
    PROCESS_PROFILE = os.path.join(ORCA_HOME, "resources/profiles/BBL/process/0.20mm Standard @BBL A1.json")
    FILAMENT_PROFILE = os.path.join(ORCA_HOME, "resources/profiles/BBL/filament/Generic PLA @BBL A1.json")

    # Pricing
    FILAMENT_COST_PER_KG = float(os.environ.get("FILAMENT_COST_PER_KG", 25.0))
    MACHINE_COST_PER_HOUR = float(os.environ.get("MACHINE_COST_PER_HOUR", 2.0))
    ENERGY_COST_PER_KWH = float(os.environ.get("ENERGY_COST_PER_KWH", 0.30))
    PRINTER_POWER_WATTS = float(os.environ.get("PRINTER_POWER_WATTS", 150.0))
    MARKUP_PERCENT = float(os.environ.get("MARKUP_PERCENT", 20.0))

settings = Settings()
