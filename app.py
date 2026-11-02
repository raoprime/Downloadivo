import os
import sys
from pathlib import Path

# Ensure application root is in python path
sys.path.insert(0, str(Path(__file__).parent))

import gradio as gr
from main import app as fastapi_app

# Mount Gradio onto Downloadivo FastAPI engine
# Root "/" serves our custom Downloadivo dark UI, /file/{id} handles streaming
demo = gr.Blocks(title="Downloadivo - Universal Media Downloader")

app = gr.mount_gradio_app(fastapi_app, demo, path="/gradio")

if __name__ == "__main__":
    import uvicorn
    port = int(os.environ.get("PORT", 7860))
    uvicorn.run("app:app", host="0.0.0.0", port=port)
