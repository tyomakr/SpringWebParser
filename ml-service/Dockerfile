FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

ARG OCR_ENABLED=false
RUN if [ "$OCR_ENABLED" = "true" ]; then \
    apt-get update && \
    apt-get install -y --no-install-recommends tesseract-ocr && \
    rm -rf /var/lib/apt/lists/*; \
    fi
ARG CLIP_ENABLED=false
COPY requirements-clip.txt .
RUN if [ "$CLIP_ENABLED" = "true" ]; then \
    pip install --no-cache-dir --extra-index-url https://download.pytorch.org/whl/cpu -r requirements-clip.txt; \
    fi

COPY app ./app

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--log-level", "debug"]
