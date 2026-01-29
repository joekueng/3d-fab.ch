from functools import lru_cache
import hashlib
from typing import Dict, Tuple

# We can't cache the profile manager instance itself easily if it's not a singleton,
# but we can cache the result of a merge function if we pass simple types.
# However, to avoid circular imports or complex dependency injection, 
# we will just provide a helper to generate cache keys and a holder for logic if needed.
# For now, the ProfileManager will strictly determine *what* to merge.
# Validating the cache strategy: since file I/O is the bottleneck, we want to cache the *content*.

def get_cache_key(machine: str, filament: str, process: str) -> str:
    """Helper to create a unique cache key"""
    data = f"{machine}|{filament}|{process}"
    return hashlib.md5(data.encode()).hexdigest()
