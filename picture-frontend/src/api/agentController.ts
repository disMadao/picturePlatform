// @ts-ignore
/* eslint-disable */
import axios from 'axios'

const AGENT_BASE_URL =
  // (import.meta as any).env?.VITE_AGENT_BASE_URL || 'http://118.195.165.9:9002'
   (import.meta as any).env?.VITE_AGENT_BASE_URL || 'http://localhost:9002'

const agentRequest = axios.create({
  baseURL: AGENT_BASE_URL,
  timeout: 60000,
})

// --------------- 旧版搜索（保留兼容） ---------------

export interface AgentSearchRequest {
  user_id?: number
  query_text?: string
  image_url?: string
  mode?: 'auto' | 'backend' | 'vector_text' | 'vector_image'
  top_k?: number
}

export interface AgentSearchStep {
  thought: string
  action: string
  observation: string
}

export interface AgentSearchData {
  mode: 'auto' | 'backend' | 'vector_text' | 'vector_image'
  pictures: API.PictureVO[]
  steps: AgentSearchStep[]
}

export interface BaseResponseAgentSearchData_ {
  code: number
  data?: AgentSearchData
  message?: string
}

export async function agentSearchUsingPost(body: AgentSearchRequest) {
  return agentRequest<BaseResponseAgentSearchData_>('/agent/search', {
    method: 'POST',
    data: body,
  })
}

// --------------- 对话管理 ---------------

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
    pictures?: API.PictureVO[]
    steps?: AgentSearchStep[]
    image_url?: string
    [key: string]: any
  }
  createTime: string
}

interface BaseResponse<T = any> {
  code: number
  data?: T
  message?: string
}

export async function createConversation(userId: number) {
  return agentRequest<BaseResponse<{ id: number }>>('/agent/conversation/create', {
    method: 'POST',
    data: { user_id: userId },
  })
}

export async function listConversations(userId: number) {
  return agentRequest<BaseResponse<ConversationVO[]>>('/agent/conversation/list', {
    method: 'GET',
    params: { user_id: userId },
  })
}

export async function deleteConversation(userId: number, conversationId: number) {
  return agentRequest<BaseResponse>('/agent/conversation/delete', {
    method: 'POST',
    params: { user_id: userId, conversation_id: conversationId },
  })
}

export async function listMessages(conversationId: number) {
  return agentRequest<BaseResponse<MessageVO[]>>('/agent/conversation/messages', {
    method: 'GET',
    params: { conversation_id: conversationId },
  })
}

// --------------- 聊天 ---------------

export interface SendMessageRequest {
  user_id: number
  conversation_id: number
  content: string
  image_url?: string
}

export interface ChatResponseData {
  role: string
  contentType: 'text' | 'search_result' | 'image' | 'video'
  content?: string
  extra?: {
    mode?: string
    pictures?: API.PictureVO[]
    steps?: AgentSearchStep[]
    [key: string]: any
  }
}

export async function sendMessage(body: SendMessageRequest) {
  return agentRequest<BaseResponse<ChatResponseData>>('/agent/chat', {
    method: 'POST',
    data: body,
  })
}
