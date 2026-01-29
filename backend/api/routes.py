from fastapi import APIRouter, UploadFile, File, HTTPException, Form
from models.quote_request import QuoteRequest, QuoteResponse
from slicer import slicer_service
from calculator import GCodeParser, QuoteCalculator
from config import settings
from profile_manager import ProfileManager
import os
import shutil
import uuid
import logging
import json

router = APIRouter()
logger = logging.getLogger("api")
profile_manager = ProfileManager()

def cleanup_files(files: list):
    for f in files:
        try:
            if os.path.exists(f):
                os.remove(f)
        except Exception as e:
            logger.warning(f"Failed to delete temp file {f}: {e}")

def format_time(seconds: int) -> str:
    m, s = divmod(seconds, 60)
    h, m = divmod(m, 60)
    if h > 0:
        return f"{int(h)}h {int(m)}m"
    return f"{int(m)}m {int(s)}s"

@router.post("/quote", response_model=QuoteResponse)
async def calculate_quote(
    file: UploadFile = File(...),
    # Compatible with form data if we parse manually or use specific dependencies.
    # FastAPI handling of mixed File + JSON/Form is tricky.
    # Easiest is to use Form(...) for fields.
    machine: str = Form("bambu_a1"),
    filament: str = Form("pla_basic"),
    quality: str = Form("standard"),
    layer_height: str = Form(None), # Form data comes as strings usually
    infill_density: int = Form(None),
    infill_pattern: str = Form(None),
    support_enabled: bool = Form(False),
    print_speed: int = Form(None)
):
    """
    Endpoint for calculating print quote.
    Accepts Multipart Form Data:
    - file: The STL file
    - machine, filament, quality: strings
    - other overrides
    """
    if not file.filename.lower().endswith(".stl"):
        raise HTTPException(status_code=400, detail="Only .stl files are supported.")
    if machine != "bambu_a1":
        raise HTTPException(status_code=400, detail="Unsupported machine.")

    req_id = str(uuid.uuid4())
    input_filename = f"{req_id}.stl"
    output_filename = f"{req_id}.gcode"
    
    input_path = os.path.join(settings.TEMP_DIR, input_filename)
    output_path = os.path.join(settings.TEMP_DIR, output_filename)

    try:
        # 1. Save File
        with open(input_path, "wb") as buffer:
            shutil.copyfileobj(file.file, buffer)
            
        # 2. Build Overrides
        overrides = {}
        if layer_height is not None and layer_height != "":
            overrides["layer_height"] = layer_height
        if infill_density is not None:
            overrides["sparse_infill_density"] = f"{infill_density}%"
        if infill_pattern:
            overrides["sparse_infill_pattern"] = infill_pattern
        if support_enabled: overrides["enable_support"] = "1"
        if print_speed is not None:
            overrides["default_print_speed"] = str(print_speed)

        # 3. Slice
        # Pass parameters to slicer service
        slicer_service.slice_stl(
            input_stl_path=input_path,
            output_gcode_path=output_path,
            machine=machine,
            filament=filament,
            quality=quality,
            overrides=overrides
        )
        
        # 4. Parse
        stats = GCodeParser.parse_metadata(output_path)
        if stats["print_time_seconds"] == 0 and stats["filament_weight_g"] == 0:
             raise HTTPException(status_code=500, detail="Slicing returned empty stats.")

        # 5. Calculate
        # We could allow filament cost override here too if passed in params
        quote = QuoteCalculator.calculate(stats)
        
        return QuoteResponse(
            success=True,
            data={
                "print_time_seconds": stats["print_time_seconds"],
                "print_time_formatted": format_time(stats["print_time_seconds"]),
                "material_grams": stats["filament_weight_g"],
                "cost": {
                    "material": quote["breakdown"]["material_cost"],
                    "machine": quote["breakdown"]["machine_cost"],
                    "energy": quote["breakdown"]["energy_cost"],
                    "markup": quote["breakdown"]["markup_amount"],
                    "total": quote["total_price"]
                },
                "parameters": {
                    "machine": machine,
                    "filament": filament,
                    "quality": quality
                }
            }
        )

    except Exception as e:
        logger.error(f"Quote error: {e}", exc_info=True)
        return QuoteResponse(success=False, error=str(e))
    
    finally:
        cleanup_files([input_path, output_path])

@router.get("/profiles/available")
def get_profiles():
    return {
        "machines": profile_manager.list_machines(),
        "filaments": profile_manager.list_filaments(),
        "processes": profile_manager.list_processes()
    }
