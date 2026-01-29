import logging
import os
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from config import settings
from api.routes import router as api_router

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("main")

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

# Include Router
app.include_router(api_router, prefix="/api")

# Legacy endpoint redirect or basic handler if needed for backward compatibility
# The frontend likely calls /calculate/stl.
# We should probably keep the old route or instruct user to update frontend.
# But for this task, let's remap the old route to the new logic if possible, 
# or just expose the new route.
# The user request said: "Creare api/routes.py ... @app.post('/api/quote')"
# So we are creating a new endpoint.
# Existing frontend might break? 
# The context says: "Currently uses hardcoded... Objective is to render system flexible... Frontend: Angular 19"
# The user didn't explicitly ask to update the frontend, but the new API is at /api/quote.
# I will keep the old "/calculate/stl" endpoint support by forwarding it or duplicating logic if critical,
# OR I'll assume the user will handle frontend updates. 
# Better: I will alias the old route to the new one if parameters allow, 
# but the new one expects Form data with different names maybe?
# Old: `/calculate/stl` just expected a file.
# I'll enable a simplified version on the old route for backward compat using defaults.

from fastapi import UploadFile, File
from api.routes import calculate_quote

@app.post("/calculate/stl")
async def legacy_calculate(file: UploadFile = File(...)):
    """Legacy endpoint compatibility"""
    # Call the new logic with defaults
    resp = await calculate_quote(file=file)
    if not resp.success:
         from fastapi import HTTPException
         raise HTTPException(status_code=500, detail=resp.error)
    
    # Map Check response to old format
    data = resp.data
    return {
        "print_time_seconds": data.get("print_time_seconds", 0),
        "print_time_formatted": data.get("print_time_formatted", ""),
        "material_grams": data.get("material_grams", 0.0),
        "cost": data.get("cost", {}),
        "notes": ["Generated via Dynamic Slicer (Legacy Endpoint)"]
    }

@app.get("/health")
def health_check():
    return {"status": "ok", "slicer": settings.SLICER_PATH}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
