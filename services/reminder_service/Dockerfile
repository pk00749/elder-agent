# reminder-service 镜像（PRD §8.1 / AGENTS.md §19）
FROM python:3.11-slim
ENV PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1 UV_LINK_MODE=copy UV_PROJECT_ENVIRONMENT=/app/.venv
WORKDIR /app
RUN pip install --no-cache-dir uv==0.11.16
COPY pyproject.toml uv.lock* ./
COPY packages/ ./packages/
COPY services/reminder_service/ ./services/reminder_service/
RUN uv sync --frozen --no-dev --package reminder-service
ENV PATH="/app/.venv/bin:$PATH"
EXPOSE 8002
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8002"]
