"""
Downloadivo Application Entry Point (Fallback / Standalone)
"""
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from main import app

if __name__ == "__main__":
    import uvicorn
    port = int(os.environ.get("PORT", 8899))
    uvicorn.run("main:app", host="0.0.0.0", port=port, log_level="info")
