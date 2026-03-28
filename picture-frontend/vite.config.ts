import { fileURLToPath, URL } from 'node:url'
import type { IncomingMessage } from 'node:http'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

/**
 * 前端路由 /agent、/agent/chat 与 FastAPI 的 POST /agent/chat 路径重叠。
 * 浏览器进入页面会发 GET /agent/chat，若全部代理到 9002 会得到 405（只允许 POST）。
 * 对「要 HTML 的 GET」返回 index.html；GET /agent/conversation/* 等仍走代理。
 */
function agentProxyBypass(req: IncomingMessage) {
  if (req.method !== 'GET') return undefined
  const path = (req.url || '').split('?')[0] || ''
  const accept = req.headers.accept || ''
  if (!accept.includes('text/html')) return undefined
  if (path === '/agent' || path === '/agent/chat') return '/index.html'
  return undefined
}

// https://vite.dev/config/
export default defineConfig({
  server: {
    proxy: {
      // 开发时走同源，避免用 127.0.0.1:5173 访问时请求 localhost:8123/9002 被 CORS 拦掉
      '/api': {
        target: 'http://localhost:8123',
        changeOrigin: true,
      },
      '/agent': {
        target: 'http://localhost:9002',
        changeOrigin: true,
        bypass: agentProxyBypass,
        // 视频：方舟轮询 + 转存可能数分钟，避免代理默认超时导致前端一直转圈后失败
        timeout: 600_000,
        proxyTimeout: 600_000,
      },
    },
  },
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
})
