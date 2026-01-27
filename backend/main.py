import os
import shutil
import uuid
import logging
from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

# Import custom modules
from config import settings
from slicer import slicer_service
from calculator import GCodeParser, QuoteCalculator

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("api")

app = FastAPI(title="Print Calculator API")

# CORS Setup
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Ensure directories exist
os.makedirs(settings.TEMP_DIR, exist_ok=True)

class QuoteResponse(BaseModel):
    printer: str
    print_time_seconds: int
    print_time_formatted: str
    material_grams: float
    cost: dict
    notes: list[str] = []

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

@app.post("/calculate/stl", response_model=QuoteResponse)
async def calculate_from_stl(file: UploadFile = File(...)):
    if not file.filename.lower().endswith(".stl"):
        raise HTTPException(status_code=400, detail="Only .stl files are supported.")

    # Unique ID for this request
    req_id = str(uuid.uuid4())
    input_filename = f"{req_id}.stl"
    output_filename = f"{req_id}.gcode"
    
    input_path = os.path.join(settings.TEMP_DIR, input_filename)
    output_path = os.path.join(settings.TEMP_DIR, output_filename)

    try:
        # 1. Save Uploaded File
        with open(input_path, "wb") as buffer:
            shutil.copyfileobj(file.file, buffer)
        
        # 2. Slice
        # slicer_service methods raise exceptions on failure
        slicer_service.slice_stl(input_path, output_path)

        # 3. Parse Results
        stats = GCodeParser.parse_metadata(output_path)
        
        if stats["print_time_seconds"] == 0 and stats["filament_weight_g"] == 0:
            # Slicing likely failed or produced empty output without throwing error
            raise HTTPException(status_code=500, detail="Slicing completed but no stats found. Check mesh validity.")

        # 4. Calculate Costs
        quote = QuoteCalculator.calculate(stats)

        return {
            "printer": "BambuLab A1 (Estimated)",
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
            "notes": ["Estimation generated using OrcaSlicer headless."]
        }

    except Exception as e:
        logger.error(f"Error processing request: {e}")
        raise HTTPException(status_code=500, detail=str(e))
    
    finally:
        # Cleanup
        cleanup_files([input_path, output_path])

@app.get("/health")
def health_check():
    return {"status": "ok", "slicer": settings.SLICER_PATH}