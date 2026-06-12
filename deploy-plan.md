# 生产环境部署方案：前后端分离 + Nginx + Docker

## Context

从一体化部署（Spring Boot 同时服务 API + 前端静态文件）改为前后端分离：
- **前端**：`npm run build` → `dist/` 目录，由宿主机 Nginx 直接托管
- **后端**：Spring Boot JAR 跑在 Docker 容器里，只负责 `/api/**`
- **Nginx**：装在宿主机，80 端口对外，反向代理 `/api/` 到后端容器，其余走前端静态文件

优势：前端更新无需重启后端；后端扩容/重启不影响前端；Nginx 处理 HTTPS/缓存更灵活。

## 部署架构

```
Linux 服务器
├── MySQL          (已有, 独立运行)
├── Nginx          (宿主机, 端口 80 对外)
│   ├── /          → /var/www/counselor-agent/ (前端 dist)
│   └── /api/      → proxy_pass http://127.0.0.1:39527 (后端容器)
└── Docker
    └── counselor-agent (Spring Boot, 内部 8081, 映射 39527)
        └── 通过 host.docker.internal 连宿主机 MySQL
```

外部访问：`http://服务器IP:80`（Nginx），最终可轻松加 HTTPS。

## 实施步骤

### 1. 添加 Actuator 依赖（健康检查）
**修改**: `pom.xml`
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### 2. 创建 `application-prod.yml`（生产配置）
**新建**: `src/main/resources/application-prod.yml`

关键点：
- 所有密钥用 `${ENV_VAR}` 占位
- `server.port: 8081`（容器内部）
- CORS 不需要（Nginx 同源代理）
- Actuator 只暴露 health
- 日志输出到文件并滚动

```yaml
server:
  port: 8081

spring:
  ai:
    openai:
      api-key: ${DEEPSEEK_API_KEY}
      base-url: ${DEEPSEEK_BASE_URL:https://api.deepseek.com}
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: never

logging:
  level:
    root: info
    com.counselor: info
  file:
    name: /app/logs/application.log
  logback:
    rollingpolicy:
      max-file-size: 50MB
      max-history: 30

counselor:
  search:
    zhipu:
      api-key: ${ZHIPU_API_KEY}
```

### 3. 创建 `Dockerfile`（多阶段构建，只打 JAR）
**新建**: `Dockerfile`

| 阶段 | 基础镜像 | 做什么 |
|------|---------|--------|
| build | `eclipse-temurin:17-jdk-alpine` | Maven 缓存依赖 → 编译 → 打 fat JAR |
| runtime | `eclipse-temurin:17-jre-alpine` | 只复制 JAR，`--spring.profiles.active=prod` |

注意：不再在 Docker 里构建前端。前端由宿主机 `npm run build` 产出 dist。

```dockerfile
# ── Build stage ──
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY pom.xml ./
COPY .mvn/ .mvn/
COPY mvnw ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q
COPY src/ src/
RUN ./mvnw package -DskipTests -q

# ── Runtime stage ──
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN mkdir -p /app/logs
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
```

### 4. 创建 `docker-compose.yml`
**新建**: `docker-compose.yml`

```yaml
services:
  app:
    build: .
    container_name: counselor-agent
    restart: unless-stopped
    ports:
      - "127.0.0.1:39527:8081"   # 只绑定 loopback，外部走 Nginx
    extra_hosts:
      - "host.docker.internal:host-gateway"
    env_file:
      - .env
    volumes:
      - app-logs:/app/logs
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 5s
      retries: 3

volumes:
  app-logs:
```

关键：`127.0.0.1:39527` 只允许本机访问，外部流量必须过 Nginx。

### 5. 创建 `.env.example`
**新建**: `.env.example`

```bash
# DeepSeek
DEEPSEEK_API_KEY=sk-xxx
DEEPSEEK_BASE_URL=https://api.deepseek.com

# MySQL (容器通过 host.docker.internal 访问宿主机)
DB_URL=jdbc:mysql://host.docker.internal:3306/counselor-agent?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
DB_USERNAME=root
DB_PASSWORD=xxx

# 智谱搜索
ZHIPU_API_KEY=xxx
```

### 6. 创建 `.dockerignore`
**新建**: `.dockerignore`

```
.git/
.idea/
target/
frontend/node_modules/
frontend/dist/
src/main/resources/application.yml
.env
*.md
plans/
```

### 7. 创建 Nginx 配置
**新建**: `nginx/counselor-agent.conf`

```nginx
server {
    listen 80;
    server_name _;   # 后续加域名时替换

    # 前端静态文件
    root /var/www/counselor-agent;
    index index.html;

    # 前端 SPA fallback
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 后端 API 反向代理
    location /api/ {
        proxy_pass http://127.0.0.1:39527;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # SSE 流式端点 — 关闭缓冲
    location ~ ^/api/.*/(runs/stream|tasks)$ {
        proxy_pass http://127.0.0.1:39527;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_http_version 1.1;
        proxy_set_header Connection '';
        proxy_buffering off;
        proxy_cache off;
        chunked_transfer_encoding off;
        proxy_read_timeout 300s;
    }

    # Actuator 健康检查（可选，外部不暴露）
    location /actuator/ {
        proxy_pass http://127.0.0.1:39527;
        # 可选：deny all; allow 127.0.0.1; 只允许本机
    }

    # 静态资源缓存
    location /assets/ {
        expires 30d;
        add_header Cache-Control "public, immutable";
    }
}
```

安装提示：
```bash
sudo cp nginx/counselor-agent.conf /etc/nginx/conf.d/
sudo nginx -t && sudo systemctl reload nginx
```

### 8. 创建 `deploy.sh`（一键部署脚本）
**新建**: `deploy.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

# 1. 检查 .env
if [ ! -f .env ]; then
  echo "❌ 缺少 .env 文件，请先: cp .env.example .env && vim .env"
  exit 1
fi

# 2. 构建前端（需要 Node.js）
echo "📦 构建前端..."
cd frontend && npm ci && npm run build && cd ..
echo "📄 复制前端到部署目录..."
sudo rm -rf /var/www/counselor-agent
sudo cp -r frontend/dist /var/www/counselor-agent

# 3. 构建并启动后端容器
echo "🐳 构建后端 Docker 镜像..."
docker compose build
docker compose down
docker compose up -d

# 4. 等待健康检查
echo "⏳ 等待后端启动..."
for i in $(seq 1 30); do
  if curl -sf http://127.0.0.1:39527/actuator/health > /dev/null 2>&1; then
    echo "✅ 后端就绪"
    break
  fi
  sleep 2
done

# 5. 检查 Nginx
sudo nginx -t && sudo systemctl reload nginx
echo "🚀 部署完成！访问 http://$(hostname -I | awk '{print $1}')"
```

### 9. 修改 `WebConfig.java`（生产环境跳过 CORS）
**修改**: `src/main/java/com/counselor/agent/config/WebConfig.java`

当前 CORS 只允许 `localhost:*`。前后端分离后，Nginx 同源代理，不需要 CORS。给 CORS 方法加 `@Profile("!prod")`：

```java
@Profile("!prod")  // 非 prod 环境才启用 CORS
@Override
public void addCorsMappings(CorsRegistry registry) { ... }
```

### 10. 更新 `.gitignore`
**修改**: `.gitignore` — 添加：
```
.env
frontend/dist/
```

## 文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `Dockerfile` | 新建 | 两阶段构建（JDK 编译 → JRE 运行） |
| `.dockerignore` | 新建 | 排除前端和密钥 |
| `docker-compose.yml` | 新建 | 后端容器编排 |
| `.env.example` | 新建 | 环境变量模板 |
| `deploy.sh` | 新建 | 一键部署脚本 |
| `application-prod.yml` | 新建 | 生产配置（env var 占位） |
| `nginx/counselor-agent.conf` | 新建 | Nginx 站点配置 |
| `pom.xml` | 修改 | 添加 actuator |
| `WebConfig.java` | 修改 | CORS 加 `@Profile("!prod")` |
| `.gitignore` | 修改 | 添加 `.env`、`frontend/dist/` |

## 服务器部署流程

```bash
# 1. 克隆代码
git clone <repo> && cd counselor-agent

# 2. 配置环境变量
cp .env.example .env && vim .env

# 3. 安装 Nginx（如果还没装）
sudo apt install -y nginx

# 4. 一键部署
chmod +x deploy.sh && ./deploy.sh

# 5. 访问
curl http://服务器IP/
```

## 验证

1. `docker compose ps` — 后端容器 `healthy`
2. `curl http://服务器IP/` — 看到前端页面
3. `curl http://服务器IP/actuator/health` — 返回 `{"status":"UP"}`
4. 在页面发一条消息 — SSE 流式正常工作
5. 刷新页面 — 历史消息正常加载

## 后续扩展

- 加域名 + HTTPS：Nginx 加 `server_name`，用 certbot 自动签 Let's Encrypt 证书
- 加 CDN：静态资源丢 CDN，Nginx 只代理 API
- 后端多实例：Nginx upstream 负载均衡
