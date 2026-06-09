/* ═══════════════════════════════════════════
   辅导员 AI 助手 — 前端逻辑
   SSE 流式对话 + Agent 路由可视化
   ═══════════════════════════════════════════ */

/* ── Agent 定义 ── */
const AGENTS = [
  { id: "mingli",    name: "明理", duty: "思政引领",   icon: "明", desc: "辅助主题教育、价值引领和谈心谈话材料撰写", keywords: ["思政","班会","价值观","形势政策"] },
  { id: "tongxin",   name: "同心", duty: "党团班级",   icon: "同", desc: "辅助班会、团日、组织生活和班级建设", keywords: ["入党","团日","班级","推优"] },
  { id: "duxue",     name: "笃学", duty: "学风建设",   icon: "笃", desc: "辅助学业预警、出勤反馈和学习帮辅方案", keywords: ["挂科","成绩","学业","考勤"] },
  { id: "youxu",     name: "有序", duty: "日常事务",   icon: "有", desc: "辅助通知起草、材料收发、政策问答和台账整理", keywords: ["材料","通知","评奖","评优"] },
  { id: "xinqing",   name: "心晴", duty: "心理支持",   icon: "心", desc: "辅助支持性回应、初步风险识别和转介提醒", keywords: ["心理","焦虑","情绪","谈心"] },
  { id: "yunlan",    name: "云澜", duty: "网络思政",   icon: "云", desc: "辅助群公告编辑、舆情研判和网络素养教育", keywords: ["舆情","网络","新媒体","群公告"] },
  { id: "shouwang",  name: "守望", duty: "危机应对",   icon: "守", desc: "辅助应急清单生成、事实记录和上报提醒", keywords: ["危机","突发事件","上报","应急"] },
  { id: "qihang",    name: "启航", duty: "就业创业",   icon: "启", desc: "辅助职业规划、就业去向分析和简历指导", keywords: ["就业","创业","简历","职业规划"] },
  { id: "yanxing",   name: "研行", duty: "实践研究",   icon: "研", desc: "辅助案例凝练、课题提纲和调研报告撰写", keywords: ["调研","课题","案例","论文"] },
];
const AGENT_MAP = Object.fromEntries(AGENTS.map(a => [a.id, a]));

/* ── DOM 引用 ── */
const $ = id => document.getElementById(id);
const messagesEl   = $("messages");
const welcomeEl   = $("welcome");
const chatEl      = $("chat");
const userInput   = $("userInput");
const sendBtn     = $("sendBtn");
const stopBtn     = $("stopBtn");
const clearBtn    = $("clearBtn");
const teacherIdEl = $("teacherId");
const statusDot   = $("statusDot");
const statusText  = $("statusText");
const menuBtn     = $("menuBtn");
const sidebar     = $("sidebar");
const topbarTitle = document.querySelector(".topbar h2");

let isStreaming = false;
let abortController = null;

/* ── 流式文本节流：单一 textNode 复用，rAF 合并写入 ── */
let rafId = null;
let pendingText = "";
// 单个可复用的 textNode，避免每帧新建节点；finalize 时整段替换为 Markdown
let currentStreamNode = null;
let currentStreamBubble = null;

function ensureStreamNode(article) {
  if (currentStreamNode) return;
  const bubble = article.querySelector(".msg-bubble");
  if (!bubble) return;
  const label = bubble.querySelector(".stream-label");
  if (label) label.remove();
  currentStreamNode = document.createTextNode("");
  const cursor = bubble.querySelector(".stream-cursor");
  if (cursor) bubble.insertBefore(currentStreamNode, cursor);
  else bubble.appendChild(currentStreamNode);
  currentStreamBubble = bubble;
}

function flushStreamContent() {
  rafId = null;
  if (!pendingText) return;
  if (currentStreamNode) currentStreamNode.appendData(pendingText);
  pendingText = "";
  scheduleScroll();
}
/* ── Agent 列表渲染 ── */
const agentListEl = $("agentList");
AGENTS.forEach(a => {
  const card = document.createElement("article");
  card.className = "agent-card";
  card.dataset.agentId = a.id;
  card.innerHTML = `
    <span class="agent-avatar">${a.icon}</span>
    <div class="agent-meta">
      <div class="agent-name">${a.name}</div>
      <div class="agent-duty">${a.duty}</div>
    </div>`;
  card.addEventListener("click", () => showAgentDetail(a));
  agentListEl.appendChild(card);
});

/* ── Agent 详情浮层 ── */
function showAgentDetail(agent) {
  const overlay = $("overlay");
  const detail = $("agentDetail");
  detail.innerHTML = `
    <div class="ad-header">
      <span class="ad-avatar">${agent.icon}</span>
      <div><h3>${agent.name}</h3><p class="ad-duty">${agent.duty}</p></div>
    </div>
    <p class="ad-desc">${agent.desc}</p>
    <div class="ad-keywords">${agent.keywords.map(k => `<span class="ad-keyword">${k}</span>`).join("")}</div>
    <button id="useAgentBtn">用「${agent.name}」处理任务</button>`;
  overlay.style.display = "grid";
  $("useAgentBtn").addEventListener("click", () => {
    overlay.style.display = "none";
    userInput.value = `帮我${agent.desc.slice(2, 18)}…`;
    userInput.focus();
  });
  overlay.onclick = e => { if (e.target === overlay) overlay.style.display = "none"; };
}

/* ── 状态指示 ── */
function setStatus(state, label) {
  statusDot.className = "status-dot";
  switch (state) {
    case "thinking":  statusDot.classList.add("thinking"); break;
    case "streaming": statusDot.classList.add("thinking"); break;
    case "error":     statusDot.classList.add("error");    break;
  }
  statusText.textContent = label || { thinking: "分析中…", streaming: "生成中…", error: "出错", ready: "就绪" }[state] || "就绪";
}

function highlightAgent(agentId) {
  document.querySelectorAll(".agent-card").forEach(c => c.classList.remove("active"));
  if (agentId) {
    const card = document.querySelector(`.agent-card[data-agent-id="${agentId}"]`);
    if (card) card.classList.add("active");
  }
}

/* ── 消息操作 ── */
function hideWelcome() {
  welcomeEl.style.display = "none";
  chatEl.style.display = "flex";
}

function addMessage(role, content) {
  hideWelcome();
  const article = document.createElement("article");
  article.className = `msg ${role}`;
  const avatarText = role === "user" ? (teacherIdEl.value || "我").slice(0, 1) : "枢";
  article.innerHTML = `
    <span class="msg-avatar">${avatarText}</span>
    <div class="msg-bubble">${escapeHtml(content)}</div>`;
  messagesEl.appendChild(article);
  scrollDown();
  return article;
}

function addRoutingMessage(agent) {
  hideWelcome();
  const div = document.createElement("div");
  div.className = "routing-notice";
  div.innerHTML = `
    <span class="routing-arrow">枢衡</span>
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--gold)" stroke-width="2"><path d="M5 12h14M13 5l7 7-7 7"/></svg>
    <span class="routing-arrow">${agent.name} · ${agent.duty}</span>`;
  messagesEl.appendChild(div);
  scrollDown();
}

function createStreamMessage(agent) {
  hideWelcome();
  const article = document.createElement("article");
  article.className = "msg system";
  const avatarText = agent ? agent.icon : "枢";
  const label = agent ? `${agent.name} 正在生成…` : "枢衡 正在生成…";
  article.innerHTML = `
    <span class="msg-avatar">${avatarText}</span>
    <div class="msg-bubble streaming">
      <span class="stream-label">${label}</span>
      <span class="stream-cursor"></span>
    </div>`;
  messagesEl.appendChild(article);
  scrollDown();
  return article;
}

function appendStreamContent(article, text) {
  // 第一次调用时懒创建单一 textNode，后续帧只 appendData
  ensureStreamNode(article);
  pendingText += text;
  if (rafId == null) {
    rafId = requestAnimationFrame(flushStreamContent);
  }
}

function finalizeStreamMessage(article, rawText) {
  // 结束前同步 flush 一次，把最后一次 rAF 里尚未落盘的内容写进 DOM
  if (rafId != null) {
    cancelAnimationFrame(rafId);
    rafId = null;
  }
  if (pendingText) {
    if (currentStreamNode) currentStreamNode.appendData(pendingText);
    pendingText = "";
  }
  currentStreamNode = null;
  currentStreamBubble = null;

  const cursor = article.querySelector(".stream-cursor");
  if (cursor) cursor.remove();
  article.querySelector(".msg-bubble").classList.remove("streaming");
  // 流式结束，用 Markdown 渲染最终内容
  if (rawText) renderBubble(article.querySelector(".msg-bubble"), rawText);
  scheduleScroll();
}

let scrollScheduled = false;
function scheduleScroll() {
  if (scrollScheduled) return;
  scrollScheduled = true;
  requestAnimationFrame(() => {
    scrollScheduled = false;
    chatEl.scrollTop = chatEl.scrollHeight;
  });
}
function scrollDown() {
  scheduleScroll();
}

/* ── SSE 核心 ── */
async function sendMessage(content) {
  if (isStreaming || !content.trim()) return;
  isStreaming = true;
  abortController = new AbortController();
  sendBtn.style.display = "none";
  stopBtn.style.display = "grid";
  setStatus("thinking");
  topbarTitle.textContent = "分析中…";
  highlightAgent(null);

  addMessage("user", content.trim());

  const streamMsg = createStreamMessage(null);
  let rawText = "";
  let hasContent = false;
  let agentId = null;
  let agentName = null;
  let risk = null;
  let agentShown = false;

  try {
    const response = await fetch("/api/tasks", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Teacher-Id": teacherIdEl.value || "anonymous",
      },
      body: JSON.stringify({ content: content.trim() }),
      signal: abortController.signal,
    });

    if (!response.ok) throw new Error(`HTTP ${response.status}`);

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
	    let eventType = "";

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split("\n");
      buffer = lines.pop();

      for (const line of lines) {
        if (line.startsWith("event:")) eventType = line.slice(6).trim();
        else if (line.startsWith("data:")) {
          try {
            const payload = JSON.parse(line.slice(5).trim());

            // 路由到子 Agent 时，更新界面
            if (eventType === "stage" && payload.stage === "STREAMING" && payload.agent !== "chief") {
              if (!agentShown) {
                const agent = AGENT_MAP[payload.agent];
                if (agent) {
                  addRoutingMessage(agent);
                  // 更新流式消息的头像
                  streamMsg.querySelector(".msg-avatar").textContent = agent.icon;
                  const label = streamMsg.querySelector(".stream-label");
                  if (label) label.textContent = `${agent.name} 正在生成…`;
                  topbarTitle.textContent = `${agent.name} · ${agent.duty}`;
                  setStatus("streaming", `${agent.name} 生成中…`);
                  highlightAgent(agent.id);
                  agentShown = true;
                }
              }
              agentId = payload.agent;
              agentName = payload.agentName;
              if (payload.risk) risk = payload.risk;
            }

            if (eventType === "content" && payload.content) {
              rawText += payload.content;
              appendStreamContent(streamMsg, payload.content);
              hasContent = true;
            }

            if (eventType === "stage" && payload.stage === "DONE") {
              hasContent = true;
            }
            if (eventType === "stage" && payload.stage === "ERROR") {
              appendStreamContent(streamMsg, `\n\n[错误: ${payload.message || "未知错误"}]`);
              hasContent = true;
            }
          } catch (_) {}
        }
      }
    }

    finalizeStreamMessage(streamMsg, rawText);

    /* 元信息条 */
    if (agentId && agentId !== "chief") {
      const agent = AGENT_MAP[agentId];
      if (agent) {
        const metaEl = document.createElement("div");
        metaEl.className = "msg-meta";
        let badges = `<span class="badge agent-tag">🤖 ${agent.name}·${agent.duty}</span>`;
        if (risk === "HIGH")   badges += `<span class="badge risk-high">⚠ 高风险</span>`;
        if (risk === "MEDIUM") badges += `<span class="badge risk-medium">⚡ 中风险</span>`;
        if (risk === "LOW")    badges += `<span class="badge risk-low">✓ 低风险</span>`;
        metaEl.innerHTML = badges;
        messagesEl.appendChild(metaEl);
      }
    }

    if (!hasContent) streamMsg.querySelector(".msg-bubble").textContent = "（未获取到回复内容）";
    setStatus("ready");
    topbarTitle.textContent = "对话";
    highlightAgent(null);
  } catch (err) {
    if (err.name === "AbortError") {
      // 用户主动停止 — 保留已生成内容
      if (rawText) {
        finalizeStreamMessage(streamMsg, rawText);
        hasContent = true;
      } else {
        streamMsg.querySelector(".msg-bubble").textContent = "（已停止生成）";
        finalizeStreamMessage(streamMsg, null);
      }
      setStatus("ready");
      topbarTitle.textContent = "对话";
    } else {
      console.error("SSE error:", err);
      streamMsg.querySelector(".msg-bubble").textContent = `请求失败：${err.message}`;
      finalizeStreamMessage(streamMsg, null);
      setStatus("error");
      topbarTitle.textContent = "对话";
    }
  } finally {
    isStreaming = false;
    abortController = null;
    sendBtn.style.display = "grid";
    stopBtn.style.display = "none";
    userInput.focus();
  }
}

function stopGeneration() {
  if (abortController) {
    abortController.abort();
  }
}

/* ── 事件绑定 ── */
sendBtn.addEventListener("click", () => sendMessage(userInput.value));
stopBtn.addEventListener("click", stopGeneration);
userInput.addEventListener("keydown", e => {
  if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); sendMessage(userInput.value); }
});

$("quickActions").addEventListener("click", e => {
  const btn = e.target.closest("button");
  if (!btn?.dataset.msg) return;
  userInput.value = btn.dataset.msg;
  sendMessage(btn.dataset.msg);
});

clearBtn.addEventListener("click", () => {
  if (abortController) { abortController.abort(); }
  messagesEl.innerHTML = "";
  welcomeEl.style.display = "flex";
  chatEl.style.display = "none";
  userInput.value = "";
  userInput.focus();
  setStatus("ready");
  topbarTitle.textContent = "对话";
  highlightAgent(null);
});

menuBtn.addEventListener("click", () => sidebar.classList.toggle("open"));

/* ── Markdown 渲染 ── */
function renderMarkdown(text) {
  const lines = text.split('\n');
  const out = [];
  let para = [];      // 当前段落的行
  let listItems = []; // 当前列表项
  let inList = false; // 是否在列表中

  function flushPara() {
    if (para.length > 0) {
      out.push(`<p>${inlineFormat(para.join('\n'))}</p>`);
      para = [];
    }
  }

  function flushList() {
    if (listItems.length > 0) {
      out.push(`<ul>${listItems.join('')}</ul>`);
      listItems = [];
    }
    inList = false;
  }

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];

    // 水平线
    if (/^---+\s*$/.test(line)) {
      flushList(); flushPara();
      out.push('<hr>');
      continue;
    }

    // 标题 — 注意要把标题后面的剩余行也处理了
    if (/^### (.+)/.test(line)) {
      flushList(); flushPara();
      out.push(`<h4>${inlineFormat(line.slice(4))}</h4>`);
      continue;
    }
    if (/^## (.+)/.test(line)) {
      flushList(); flushPara();
      out.push(`<h3>${inlineFormat(line.slice(3))}</h3>`);
      continue;
    }
    if (/^# (.+)/.test(line)) {
      flushList(); flushPara();
      out.push(`<h2>${inlineFormat(line.slice(2))}</h2>`);
      continue;
    }

    // 无序列表项
    if (/^- (.+)/.test(line)) {
      flushPara();
      inList = true;
      listItems.push(`<li>${inlineFormat(line.slice(2))}</li>`);
      continue;
    }

    // 有序列表项（紧凑格式: 1. xxx）
    if (/^\d+\.\s(.+)/.test(line) && !/[a-zA-Z]/.test(line.slice(0, 3))) {
      flushPara();
      inList = true;
      const content = line.replace(/^\d+\.\s/, '');
      listItems.push(`<li>${inlineFormat(content)}</li>`);
      continue;
    }

    // 空行：结束当前段落和列表
    if (line.trim() === '') {
      flushList();
      flushPara();
      continue;
    }

    // 列表中的续行（缩进或非列表标记的行）
    if (inList) {
      flushList();
    }

    // 普通文本行，累积到段落
    para.push(line);
  }

  // 收尾
  flushList();
  flushPara();

  return out.join('');
}

/** 行内格式：粗体、斜体 */
function inlineFormat(text) {
  return escapeHtml(text)
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.+?)\*/g, '<em>$1</em>');
}

function renderBubble(el, rawText) {
  el.innerHTML = renderMarkdown(rawText);
}

/* ── 工具 ── */
function escapeHtml(str) {
  const div = document.createElement("div");
  div.textContent = str;
  return div.innerHTML;
}
