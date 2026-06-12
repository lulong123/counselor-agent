# 生产环境部署方案：Docker 化 counselor-agent

> 创建时间：2026-06-11
> 状态：待实施

## Context

项目当前只在本地运行（`java -jar` 直接启动），需要部署到用户的 Linux 服务器。后端 Spring Boot + 前端 Vue 3 打包为一体，MySQL 已在服务器上，暂用 IP 访问（不需要域名/HTTPS）。

## 部署架构

```
Linux 服务器
├── MySQL (已有, 独立运行)
└── Docker 容器
    └── counselor-agent (Spring Boot 3.4 + 前端静态文件, 外部端口 39527 → 内部 8081)
        ├── API: /api/**
        └── 前端: SPA fallback → index.html
```

通过 `host.docker.internal` 让容器连接宿主机 MySQL。

## 实施步骤

### 1. 添加 Spring Boot Actuator（健康检查）
**文件**: `pom.xml` — 添加依赖：
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### 2. 创建 `src/main/resources/application-prod.yml`（生产配置）
- 所有密钥用 `${ENV_VAR}` 占位，不硬编码
- 日志级别默认 INFO，输出到文件并滚动
- 提交到 git（不含任何真实密钥）

关键配置项：
- `spring.ai.openai.api-key: ${DEEPSEEK_API_KEY}`
- `spring.datasource.*` → `${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}`
- `counselor.search.zhipu.api-key: ${ZHIPU_API_KEY}`
- `management.endpoints.web.exposure.include: health`

### 3. 创建 `Dockerfile`（多阶段构建）
| 阶段 | 基础镜像 | 做什么 |
|------|---------|--------|
| frontend-build | `node:20-alpine` | npm ci → npm run build |
| backend-build | `eclipse-temurin:17-jdk-alpine` | Maven 依赖缓存 → 复制前端 dist 到 static/ → 打 fat JAR |
| runtime | `eclipse-temurin:17-jre-alpine` | 只复制 JAR，ENTRYPOINT 激活 prod profile |

- `.dockerignore` 排除 `node_modules/`、`target/`、`application.yml`（本地密钥文件）
- 最终镜像约 270MB

### 4. 创建 `docker-compose.yml`
- 单服务 `app`，宿主机映射 `39527:8081`（外部访问端口 39527）
- `extra_hosts: host.docker.internal:host-gateway`（Linux 上让容器访问宿主机）
- 环境变量从 `.env` 文件注入
- 命名卷 `app-logs` 持久化日志
- 健康检查：`wget http://localhost:8081/actuator/health`

### 5. 创建 `.env.example`（环境变量模板）
```bash
DEEPSEEK_API_KEY=sk-xxx
DB_URL=jdbc:mysql://host.docker.internal:3306/counselor-agent?useSSL=false&...
DB_USERNAME=root
DB_PASSWORD=xxx
ZHIPU_API_KEY=xxx
HOST_PORT=39527
```

### 6. 创建 `.dockerignore`
排除 `.git/`、`target/`、`frontend/node_modules/`、`frontend/dist/`、`src/main/resources/application.yml`

### 7. 创建 `deploy.sh`（一键部署脚本）
- 检查 `.env` 是否存在
- 验证必填环境变量
- `docker build` → `docker compose down` → `docker compose up -d`
- 等待健康检查通过

### 8. 更新 `.gitignore`
添加 `.env` 条目

## 新增文件清单
| 文件 | 说明 |
|------|------|
| `Dockerfile` | 多阶段构建（前端→后端→运行时） |
| `.dockerignore` | 排除不必要的文件 |
| `docker-compose.yml` | 服务编排 |
| `.env.example` | 环境变量模板 |
| `deploy.sh` | 一键部署脚本 |
| `src/main/resources/application-prod.yml` | 生产配置（env var 占位） |

## 修改文件
| 文件 | 改动 |
|------|------|
| `pom.xml` | 添加 actuator 依赖 |
| `.gitignore` | 添加 `.env` |

## 服务器部署流程
```bash
# 1. 克隆代码到服务器
git clone <repo> && cd counselor-agent

# 2. 创建 .env 文件
cp .env.example .env
vim .env  # 填入真实密钥

# 3. 确保 MySQL 允许 Docker 网段连接（bind-address）

# 4. 一键部署
chmod +x deploy.sh && ./deploy.sh

# 5. 访问
curl http://服务器IP:39527
```

## 验证方式
1. 本地先 `docker compose build && docker compose up` 测试构建和启动
2. 检查 `docker compose ps` 状态为 `healthy`
3. 访问 `http://服务器IP:39527` 看到前端页面
4. 访问 `http://服务器IP:39527/actuator/health` 返回 `{"status":"UP"}`
5. 发送一条聊天消息测试 API 端到端
