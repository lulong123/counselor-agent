#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

# 1. 检查 .env
if [ ! -f .env ]; then
  echo "❌ 缺少 .env 文件，请先: cp .env.example .env && vim .env"
  exit 1
fi

# 2. 构建前端
echo "📦 构建前端..."
cd frontend && npm ci && npm run build && cd ..

# 3. 构建并启动所有容器（Nginx + 后端）
echo "🐳 构建镜像并启动服务..."
docker compose build
docker compose down
docker compose up -d

# 4. 等待服务就绪
echo "⏳ 等待服务就绪..."
for i in $(seq 1 60); do
  if curl -sf http://127.0.0.1:${HOST_PORT:-39527}/ > /dev/null 2>&1; then
    echo "✅ 服务就绪"
    break
  fi
  if [ "$i" -eq 60 ]; then
    echo "⚠️  等待超时，请检查: docker compose logs"
  fi
  sleep 2
done

echo "🚀 部署完成！访问 http://$(hostname -I | awk '{print $1}'):${HOST_PORT:-39527}"
