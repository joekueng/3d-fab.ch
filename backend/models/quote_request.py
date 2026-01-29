from pydantic import BaseModel, Field, validator
from typing import Optional, Literal, Dict, Any

class QuoteRequest(BaseModel):
    # File STL (base64 or path)
    file_path: Optional[str] = None
    file_base64: Optional[str] = None
    
    # Parametri slicing
    machine: str = Field(default="bambu_a1", description="Machine type")
    filament: str = Field(default="pla_basic", description="Filament type")
    quality: Literal["draft", "standard", "fine"] = Field(default="standard")
    
    # Parametri opzionali
    layer_height: Optional[float] = Field(None, ge=0.08, le=0.32)
    infill_density: Optional[int] = Field(None, ge=0, le=100)
    support_enabled: Optional[bool] = None
    print_speed: Optional[int] = Field(None, ge=20, le=300)
    
    # Pricing overrides
    filament_cost_override: Optional[float] = None
    
    @validator('machine')
    def validate_machine(cls, v):
        # This list should ideally be dynamic, but for validation purposes we start with known ones.
        # Logic in ProfileManager can be looser or strict.
        # For now, we allow the string through and let ProfileManager validate availability.
        return v
    
    @validator('filament')
    def validate_filament(cls, v):
        return v

class QuoteResponse(BaseModel):
    success: bool
    data: Optional[Dict[str, Any]] = None
    error: Optional[str] = None
