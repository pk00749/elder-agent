# mock-upstream 镜像：本地 mock 所有上游服务（DSH/ASR/TTS/COS/TPUSH/SMS）
FROM python:3.11-slim
ENV PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1
WORKDIR /app
RUN pip install --no-cache-dir 'fastapi>=0.115.0' 'uvicorn[standard]>=0.32.0'
COPY app.py ./
EXPOSE 8080
HEALTHCHECK --interval=5s --timeout=3s --retries=5 CMD ["python", "-c", "import urllib.request,sys;sys.exit(0 if urllib.request.urlopen('http://localhost:8080/health',timeout=2).status==200 else 1)"]
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8080"]
