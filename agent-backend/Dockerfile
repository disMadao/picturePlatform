ARG BASE_IMAGE=python:3.11-slim
FROM ${BASE_IMAGE}

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

# 系统依赖：curl 用于健康检查
RUN apt-get update \
  && apt-get install -y --no-install-recommends curl \
  && rm -rf /var/lib/apt/lists/*

# 依赖安装：
# - 默认使用 requirements-lock-pip.txt（与你本地 freeze 对齐，更可复现）
# - 如需使用最小依赖 requirements.txt：docker build --build-arg REQUIREMENTS_FILE=requirements.txt ...
# - 现在默认使用 requirements.txt（更轻量，构建更快）；如需完全复刻 freeze，用 build-arg 指定 lock 文件
ARG REQUIREMENTS_FILE=requirements.txt

COPY requirements.txt /app/requirements.txt
COPY requirements-lock-pip.txt /app/requirements-lock-pip.txt

RUN python -m pip install --no-cache-dir -U pip setuptools wheel \
  && pip install --no-cache-dir -r /app/${REQUIREMENTS_FILE}

COPY . /app

EXPOSE 9002

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "9002"]


