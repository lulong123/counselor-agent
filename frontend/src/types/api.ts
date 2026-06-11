export interface Agent {
  id: string
  name: string
  duty: string
  icon: string
  desc: string
  keywords: string[]
}

export interface Thread {
  id: string
  teacherId: string
  title?: string
  status: 'idle' | 'busy' | 'interrupted' | 'error'
  createdAt: string
  updatedAt: string
}

export interface Message {
  id: string
  threadId: string
  teacherId: string
  role: 'user' | 'assistant'
  content: string
  agentId?: string
  risk?: string
  thinking?: string
  tokenUsage?: number
  seq: number
  createdAt: string
}

export interface Run {
  id: string
  threadId: string
  teacherId: string
  content: string
  intent?: string
  agentId?: string
  runName?: string
  risk?: string
  status: 'pending' | 'running' | 'success' | 'error' | 'cancelled'
  createdAt: string
  updatedAt: string
}

export const AGENTS: Agent[] = [
  { id: "mingli",    name: "明理", duty: "思政引领",   icon: "明", desc: "辅助主题教育、价值引领和谈心谈话材料撰写", keywords: ["思政","班会","价值观","形势政策"] },
  { id: "tongxin",   name: "同心", duty: "党团班级",   icon: "同", desc: "辅助班会、团日、组织生活和班级建设", keywords: ["入党","团日","班级","推优"] },
  { id: "duxue",     name: "笃学", duty: "学风建设",   icon: "笃", desc: "辅助学业预警、出勤反馈和学习帮辅方案", keywords: ["挂科","成绩","学业","考勤"] },
  { id: "youxu",     name: "有序", duty: "日常事务",   icon: "有", desc: "辅助通知起草、材料收发、政策问答和台账整理", keywords: ["材料","通知","评奖","评优"] },
  { id: "xinqing",   name: "心晴", duty: "心理支持",   icon: "心", desc: "辅助支持性回应、初步风险识别和转介提醒", keywords: ["心理","焦虑","情绪","谈心"] },
  { id: "yunlan",    name: "云澜", duty: "网络思政",   icon: "云", desc: "辅助群公告编辑、舆情研判和网络素养教育", keywords: ["舆情","网络","新媒体","群公告"] },
  { id: "shouwang",  name: "守望", duty: "危机应对",   icon: "守", desc: "辅助应急清单生成、事实记录和上报提醒", keywords: ["危机","突发事件","上报","应急"] },
  { id: "qihang",    name: "启航", duty: "就业创业",   icon: "启", desc: "辅助职业规划、就业去向分析和简历指导", keywords: ["就业","创业","简历","职业规划"] },
  { id: "yanxing",   name: "研行", duty: "实践研究",   icon: "研", desc: "辅助案例凝练、课题提纲和调研报告撰写", keywords: ["调研","课题","案例","论文"] },
]

export const AGENT_MAP: Record<string, Agent> = Object.fromEntries(AGENTS.map(a => [a.id, a]))
