## Skill routing

When the user's request matches an available skill, invoke it via the Skill tool. When in doubt, invoke the skill.

Key routing rules:
- Product ideas/brainstorming → invoke /office-hours
- Strategy/scope → invoke /plan-ceo-review
- Architecture → invoke /plan-eng-review
- Design system/plan review → invoke /design-consultation or /plan-design-review
- Full review pipeline → invoke /autoplan
- Bugs/errors → invoke /investigate
- QA/testing site behavior → invoke /qa or /qa-only
- Code review/diff check → invoke /review
- Visual polish → invoke /design-review
- Ship/deploy/PR → invoke /ship or /land-and-deploy
- Save progress → invoke /context-save
- Resume context → invoke /context-restore
- Author a backlog-ready spec/issue → invoke /spec

## Reference projects

### DeerFlow (`/Users/luwenlong/Documents/后端项目/deerflow/deer-flow`)
- 字节跳动开源的 super agent harness（Deep Exploration and Efficient Research Flow）
- 架构：Python 后端 (FastAPI/LangGraph) + Next.js 前端
- 核心概念：sub-agents、memory、sandbox、可扩展 skills
- 当用户要求参考 deerflow 实现功能时，优先查阅此项目的代码结构和实现模式

### AI-Debate (`/Users/luwenlong/Documents/后端项目/ai-debate`)
- 多角度 AI 协作讨论平台（AI 圆桌会议），多 Agent 结构化辩论
- 架构：Python 后端 (FastAPI + SQLAlchemy async + SQLite) + React 前端 (Vite + TailwindCSS v4)
- 核心概念：多 Agent 协作（主持人/辩手/数据研究员/评委）、两阶段思考 (CoT)、[N] Citation 引用系统、SSE 流式传输、数据预筛查+迭代搜索、辩手跨轮次状态 (DebaterState)、共享数据池
- LLM：LiteLLM 多模型路由，支持 GLM/GPT/Claude/DeepSeek/Gemini 等
- 当用户要求参考 ai-debate 的多 Agent 协作、SSE 流式、结构化输出、辩论流程等实现时，优先查阅此项目

## 代码修改后自动重启

每次修改代码后，**自动执行**以下流程，无需用户提醒：

1. **Java 代码修改** → `./mvnw compile -q` 验证编译 → 如果有前端静态资源变动，也执行前端 build
2. **前端代码修改** → `cd frontend && npm run build` → `cp -r frontend/dist/* src/main/resources/static/`
3. **重启后端**：
   ```bash
   kill $(lsof -i :8081 -t) 2>/dev/null
   ./mvnw package -DskipTests -q
   nohup java -jar target/counselor-agent-0.1.0-SNAPSHOT.jar > /tmp/counselor-agent.log 2>&1 &
   ```
4. 检查日志确认启动成功：`tail -20 /tmp/counselor-agent.log`
5. 启动成功后告知用户服务已就绪，访问 http://localhost:8081
