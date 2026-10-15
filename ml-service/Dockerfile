FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    ML_MODEL_DIR=/models \
    PORT=8000

WORKDIR /srv

COPY requirements.txt .
RUN pip install --no-compile -r requirements.txt \
    && useradd --system --uid 10001 --no-create-home --shell /usr/sbin/nologin ml \
    && mkdir -p /models \
    && chown ml /models

COPY app ./app

USER ml
EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=3s --start-period=15s --retries=3 \
    CMD python -c "import os, urllib.request; urllib.request.urlopen(f'http://127.0.0.1:{os.environ[\"PORT\"]}/health', timeout=2)"

CMD ["sh", "-c", "exec uvicorn --factory app.main:create_app --host 0.0.0.0 --port \"$PORT\" --workers 1 --no-access-log"]
