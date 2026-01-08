// @ts-ignore
/* eslint-disable */
import axios from 'axios'

// Agent 后端基础地址
// 注意：不要用 localhost，Windows 上可能优先解析到 IPv6 ::1，被 Docker/WSL relay 劫持到别的服务（如 MinIO Console:9001）这里应该使用agent的端口，我使用的是9002
// 支持通过 Vite 环境变量覆盖：VITE_AGENT_BASE_URL
const AGENT_BASE_URL = (import.meta as any).env?.VITE_AGENT_BASE_URL || 'http://127.0.0.1:9002'

const agentRequest = axios.create({
  baseURL: AGENT_BASE_URL,
  timeout: 60000,
})

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

/**
 * Agent 搜索
 * 与 Python Agent 后端的 /agent/search 对接
 */
export async function agentSearchUsingPost(body: AgentSearchRequest) {
  return agentRequest<BaseResponseAgentSearchData_>('/agent/search', {
    method: 'POST',
    data: body,
  })
}


