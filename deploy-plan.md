# 生产环境部署方案：前后端分离 + Docker Nginx + Docker 后端

## Context

从一体化部署（Spring Boot 同时服务 API + 前端静态文件）改为前后端分离：
- **前端**：`npm run build` → `dist/` 目录，通过 volume 挂载到 Nginx 容器
- **后端**：Spring Boot JAR 跑在 Docker 容器里，只负责 `/api/**`
- **Nginx**：也跑 Docker（`nginx:alpine`），反向代理 `/api/` 到后端容器，其余走前端静态文件

两个容器通过 Docker 内部网络 `counselor-net` 互通，后端不对外暴露端口。

## 部署架构

```
Linux 服务器
├── MySQL                    (已有, 独立运行)
└── Docker Compose
    ├── counselor-nginx      (nginx:alpine, 外部端口 39527 → 内部 80)
    │   ├── /                → /usr/share/nginx/html (volume: frontend/dist/)
    │   └── /api/            → proxy_pass http://app:8081 (Docker 内部网络)
    └── counselor-agent      (Spring Boot, 内部 8081, 不对外暴露)
        └── host.docker.internal → 宿主机 MySQL
```

外部访问：`http://服务器IP:39527`

## 服务器部署步骤

```bash
# 1. 克隆代码
git clone <repo> && cd counselor-agent

# 2. 配置环境变量
cp .env.example .env
vim .env  # 填入真实密钥

# 3. 一键部署
chmod +x deploy.sh && ./deploy.sh

# 4. 访问
curl http://服务器IP:39527
```

## .env 配置项

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

# 对外端口（Nginx 容器映射，默认 39527）
HOST_PORT=39527
```

## 验证

1. `docker compose ps` — 两个容器都 running，后端 healthy
2. `curl http://服务器IP:39527/` — 看到前端页面
3. `curl http://服务器IP:39527/actuator/health` — 返回 `{"status":"UP"}`
4. 在页面发一条消息 — SSE 流式正常工作
5. 刷新页面 — 历史消息正常加载

## 文件清单

| 文件 | 说明 |
|------|------|
| `Dockerfile` | 两阶段构建（JDK 编译 → JRE 运行） |
| `.dockerignore` | 排除前端、密钥、构建产物 |
| `docker-compose.yml` | Nginx + 后端 容器编排，内部网络互通 |
| `.env.example` | 环境变量模板 |
| `deploy.sh` | 一键部署脚本 |
| `src/main/resources/application-prod.yml` | 生产配置（env var 占位） |
| `nginx/counselor-agent.conf` | Nginx 反向代理 + SPA + SSE 无缓冲 |
| `pom.xml` | 添加了 actuator 依赖 |
| `WebConfig.java` | CORS 加了 `@Profile("!prod")` |
| `.gitignore` | 添加了 `.env`、`frontend/dist/` |

## 后续扩展

- 加域名 + HTTPS：Nginx 加 `server_name`，用 certbot 自动签 Let's Encrypt 证书
- 加 CDN：静态资源丢 CDN，Nginx 只代理 API
- 后端多实例：Nginx upstream 负载均衡
