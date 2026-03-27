import axios from 'axios'

const AGENT_BASE = (import.meta as any).env?.VITE_AGENT_BASE_URL || 'http://localhost:9002'
// const AGENT_BASE = (import.meta as any).env?.VITE_AGENT_BASE_URL || 'http://118.195.165.9:9002'
const http = axios.create({ baseURL: AGENT_BASE, timeout: 120_000 })

// ---------- 类型 ----------

export interface ConversationVO {
  id: number
  title?: string
  createTime: string
  updateTime: string
}

export interface MessageVO {
  id: number
  conversationId: number
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

export const apiCreateConversation = (userId: number) =>
  http.post<R<{ id: number }>>('/agent/conversation/create', { user_id: userId })

export const apiListConversations = (userId: number) =>
  http.get<R<ConversationVO[]>>('/agent/conversation/list', { params: { user_id: userId } })

export const apiDeleteConversation = (userId: number, conversationId: number) =>
  http.post<R>('/agent/conversation/delete', null, {
    params: { user_id: userId, conversation_id: conversationId },
  })

export const apiListMessages = (conversationId: number) =>
  http.get<R<MessageVO[]>>('/agent/conversation/messages', {
    params: { conversation_id: conversationId },
  })

// ---------- 聊天 ----------

export const apiSendMessage = (body: {
  user_id: number
  conversation_id: number
  content: string
  image_url?: string
}) => http.post<R<ChatReply>>('/agent/chat', body)
