import axios from 'axios'

// 开发：走 Vite 代理到 9002（与页面同源），避免 127.0.0.1:5173 → localhost:9002 被浏览器 CORS 拦截
// 生产：可设 VITE_AGENT_ORIGIN，例如 http://your-host:9002
const AGENT_BASE = import.meta.env.DEV
  ? ''
  : ((import.meta as any).env?.VITE_AGENT_ORIGIN as string | undefined) || 'http://localhost:9002'

const http = axios.create({ baseURL: AGENT_BASE, timeout: 120_000 })

// ---------- 类型 ----------

/** 雪花 ID：接口与 DB 为数字，JSON 侧统一用字符串，勿用 JS Number 运算 */
export type SnowflakeId = string | number

export interface ConversationVO {
  id: SnowflakeId
  title?: string
  createTime: string
  updateTime: string
}

export interface MessageVO {
  id: SnowflakeId
  conversationId: SnowflakeId
  role: 'user' | 'assistant' | 'system'
  contentType: 'text' | 'search_result' | 'image' | 'video'
  content?: string
  extra?: {
    mode?: string
    pictures?: PictureItem[]
    steps?: any[]
    videoUrl?: string
    [k: string]: any
  }
  createTime: string
}

export interface PictureItem {
  id?: number | string
  url?: string
  thumbnailUrl?: string
  name?: string
  [k: string]: any
}

export interface ChatReply {
  role: string
  contentType: 'text' | 'search_result' | 'image' | 'video'
  content?: string
  extra?: MessageVO['extra']
}

interface R<T = any> {
  code: number
  data?: T
  message?: string
}

// ---------- 对话管理 ----------

export const apiCreateConversation = (userId: SnowflakeId) =>
  http.post<R<{ id: string }>>('/agent/conversation/create', { user_id: userId })

export const apiListConversations = (userId: SnowflakeId) =>
  http.get<R<ConversationVO[]>>('/agent/conversation/list', { params: { user_id: userId } })

export const apiDeleteConversation = (userId: SnowflakeId, conversationId: SnowflakeId) =>
  http.post<R>('/agent/conversation/delete', null, {
    params: { user_id: userId, conversation_id: conversationId },
  })

export const apiListMessages = (conversationId: SnowflakeId) =>
  http.get<R<MessageVO[]>>('/agent/conversation/messages', {
    params: { conversation_id: conversationId },
  })

// ---------- 聊天 ----------

export type SendMessageBody = {
  user_id: SnowflakeId
  conversation_id: SnowflakeId
  content: string
  image_url?: string
  session_intent?: 'search' | 'video'
  /** 空间雪花 id，勿用 JS Number */
  space_id?: SnowflakeId
  first_frame_url?: string
  last_frame_url?: string
}

export const apiSendMessage = (body: SendMessageBody, axiosConfig?: { timeout?: number }) =>
  http.post<R<ChatReply>>('/agent/chat', body, {
    timeout: axiosConfig?.timeout ?? 120_000,
  })
