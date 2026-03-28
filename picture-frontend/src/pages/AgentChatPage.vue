<template>
  <div class="agent-chat-page">
    <!-- 左侧：对话列表 -->
    <div class="sidebar">
      <ConversationList
        :conversations="conversations"
        :activeId="activeConvId"
        @create="handleCreate"
        @select="handleSelect"
        @delete="handleDelete"
      />
    </div>

    <!-- 右侧：聊天区域 -->
    <div class="chat-main">
      <!-- 有对话选中 -->
      <template v-if="activeConvId">
        <!-- 消息列表 -->
        <div ref="msgListRef" class="msg-list">
          <!-- 无消息时的欢迎态 -->
          <div v-if="!messages.length && !msgLoading" class="welcome">
            <div class="welcome-icon">
              <RobotOutlined />
            </div>
            <div class="welcome-title">你好，我是智能助手</div>
            <div class="welcome-desc">
              你可以让我帮你搜索图片，或者和我随便聊聊
            </div>
            <div class="welcome-tips">
              <div class="tip-item" @click="quickSend('帮我找几张星空的图片')">
                帮我找几张星空的图片
              </div>
              <div class="tip-item" @click="quickSend('推荐一些适合做壁纸的风景照')">
                推荐一些适合做壁纸的风景照
              </div>
              <div class="tip-item" @click="quickSend('有没有夕阳相关的图片')">
                有没有夕阳相关的图片
              </div>
            </div>
          </div>

          <!-- 消息加载中 -->
          <div v-if="msgLoading" class="msg-loading">
            <a-spin />
          </div>

          <!-- 消息列表 -->
          <ChatMessage v-for="m in messages" :key="m.id" :msg="m" />

          <!-- AI 正在思考 -->
          <div v-if="sending" class="thinking">
            <div class="thinking-avatar">
              <RobotOutlined />
            </div>
            <div class="thinking-bubble">
              <span class="dot-animate" />
              正在思考...
            </div>
          </div>
        </div>

        <!-- 输入区域 -->
        <div class="input-area">
          <div class="input-wrapper">
            <a-textarea
              ref="inputRef"
              v-model:value="inputText"
              :placeholder="sending ? '等待回复中...' : '发消息或描述你想搜索的图片...'"
              :disabled="sending"
              :auto-size="{ minRows: 1, maxRows: 6 }"
              @pressEnter="handleEnter"
            />
            <a-button
              class="send-btn"
              type="primary"
              shape="circle"
              :disabled="!inputText.trim() || sending"
              :loading="sending"
              @click="doSend"
            >
              <template v-if="!sending" #icon><SendOutlined /></template>
            </a-button>
          </div>
        </div>
      </template>

      <!-- 无对话选中 -->
      <template v-else>
        <div class="empty-state">
          <div class="empty-icon">
            <RobotOutlined />
          </div>
          <div class="empty-title">智能助手</div>
          <div class="empty-desc">选择一个历史对话，或点击「新对话」开始</div>
          <a-button type="primary" size="large" @click="handleCreate">
            <template #icon><PlusOutlined /></template>
            开始新对话
          </a-button>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  RobotOutlined,
  SendOutlined,
  PlusOutlined,
} from '@ant-design/icons-vue'
import ConversationList from '@/components/ConversationList.vue'
import ChatMessage from '@/components/ChatMessage.vue'
import {
  createConversation,
  listConversations,
  deleteConversation,
  listMessages,
  sendMessage,
  type ConversationVO,
  type MessageVO,
  type SnowflakeId,
} from '@/api/agentController'
import { useLoginUserStore } from '@/stores/useLoginUserStore'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()
/** 雪花 id 保持字符串，勿 Number() */
const userId = (): string | undefined => {
  const raw = loginUserStore.loginUser.id as unknown
  if (raw == null || raw === '') return undefined
  return typeof raw === 'string' ? raw : String(raw)
}

const conversations = ref<ConversationVO[]>([])
const activeConvId = ref<SnowflakeId>()
const messages = ref<MessageVO[]>([])
const inputText = ref('')
const sending = ref(false)
const msgLoading = ref(false)
const msgListRef = ref<HTMLElement>()
const inputRef = ref()

const scrollToBottom = () => {
  nextTick(() => {
    if (msgListRef.value) {
      msgListRef.value.scrollTop = msgListRef.value.scrollHeight
    }
  })
}

// 将当前选中的对话 id 同步到 URL query，方便刷新后恢复
const syncUrlQuery = (convId?: SnowflakeId) => {
  const query = convId != null && convId !== '' ? { id: String(convId) } : {}
  router.replace({ path: '/agent/chat', query })
}

// 加载对话列表
const fetchConversations = async () => {
  const uid = userId()
  if (!uid) return
  try {
    const res = await listConversations(uid)
    if (res.data.code === 0 && res.data.data) {
      conversations.value = res.data.data.map(c => ({
        ...c,
        id: typeof c.id === 'string' ? c.id : String(c.id),
      }))
    }
  } catch (e: any) {
    console.error('加载对话列表失败', e)
  }
}

// 加载某个对话的消息
const fetchMessages = async (convId: SnowflakeId) => {
  msgLoading.value = true
  try {
    const res = await listMessages(convId)
    if (res.data.code === 0 && res.data.data) {
      messages.value = res.data.data
      scrollToBottom()
    }
  } catch (e: any) {
    console.error('加载消息失败', e)
  }
  msgLoading.value = false
}

// 创建新对话
const handleCreate = async () => {
  const uid = userId()
  if (!uid) {
    message.warning('请先登录')
    return
  }
  try {
    const res = await createConversation(uid)
    if (res.data.code === 0 && res.data.data) {
      const newId = String(res.data.data.id)
      await fetchConversations()
      activeConvId.value = newId
      messages.value = []
      syncUrlQuery(newId)
      nextTick(() => inputRef.value?.focus?.())
    }
  } catch (e: any) {
    message.error('创建对话失败：' + e.message)
  }
}

// 选中某个对话
const handleSelect = async (id: SnowflakeId) => {
  if (String(id) === String(activeConvId.value ?? '')) return
  activeConvId.value = id
  syncUrlQuery(id)
  await fetchMessages(id)
}

// 删除对话
const handleDelete = async (id: SnowflakeId) => {
  const uid = userId()
  if (!uid) return
  try {
    await deleteConversation(uid, id)
    if (String(activeConvId.value ?? '') === String(id)) {
      activeConvId.value = undefined
      messages.value = []
      syncUrlQuery()
    }
    await fetchConversations()
  } catch (e: any) {
    message.error('删除失败')
  }
}

// 快捷问题
const quickSend = (text: string) => {
  inputText.value = text
  doSend()
}

// Enter 发送（Shift+Enter 换行）
const handleEnter = (e: KeyboardEvent) => {
  if (e.shiftKey) return
  e.preventDefault()
  doSend()
}

// 发送消息
const doSend = async () => {
  const text = inputText.value.trim()
  if (!text || !activeConvId.value || sending.value) return

  const uid = userId()
  if (!uid) {
    message.warning('请先登录')
    return
  }

  // 先在界面追加用户消息
  const tempUserMsg: MessageVO = {
    id: Date.now(),
    conversationId: activeConvId.value,
    role: 'user',
    contentType: 'text',
    content: text,
    createTime: new Date().toISOString(),
  }
  messages.value.push(tempUserMsg)
  inputText.value = ''
  scrollToBottom()

  sending.value = true
  try {
    const res = await sendMessage({
      user_id: uid,
      conversation_id: activeConvId.value,
      content: text,
    })
    if (res.data.code === 0 && res.data.data) {
      const d = res.data.data
      const assistantMsg: MessageVO = {
        id: Date.now() + 1,
        conversationId: activeConvId.value!,
        role: 'assistant',
        contentType: d.contentType,
        content: d.content,
        extra: d.extra,
        createTime: new Date().toISOString(),
      }
      messages.value.push(assistantMsg)
      scrollToBottom()
      fetchConversations()
    } else {
      message.error(res.data.message || '发送失败')
    }
  } catch (e: any) {
    message.error('发送失败：' + e.message)
  }
  sending.value = false
}

// 初始化：加载对话列表，恢复上次选中的对话
onMounted(async () => {
  await loginUserStore.fetchLoginUser()
  await fetchConversations()
  const raw = route.query.id
  const queryId = Array.isArray(raw) ? raw[0] : raw
  if (queryId && typeof queryId === 'string' && conversations.value.some((c) => String(c.id) === queryId)) {
    activeConvId.value = queryId
    await fetchMessages(queryId)
  } else if (conversations.value.length) {
    const latest = conversations.value[0]
    activeConvId.value = latest.id
    syncUrlQuery(latest.id)
    await fetchMessages(latest.id)
  }
})
</script>

<style scoped>
.agent-chat-page {
  display: flex;
  height: calc(100vh - 64px);
  overflow: hidden;
}

/* 左侧边栏 */
.sidebar {
  width: 260px;
  flex-shrink: 0;
}

/* 右侧聊天主区域 */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: #fff;
}

/* 消息滚动区域 */
.msg-list {
  flex: 1;
  overflow-y: auto;
  padding: 24px 48px;
  display: flex;
  flex-direction: column;
}

/* 欢迎态 */
.welcome {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-top: 80px;
}
.welcome-icon {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  font-size: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}
.welcome-title {
  font-size: 20px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 8px;
}
.welcome-desc {
  font-size: 14px;
  color: #999;
  margin-bottom: 24px;
}
.welcome-tips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
  max-width: 500px;
}
.tip-item {
  padding: 8px 16px;
  border: 1px solid #e8e8e8;
  border-radius: 20px;
  font-size: 13px;
  color: #555;
  cursor: pointer;
  transition: all 0.15s;
}
.tip-item:hover {
  border-color: #1677ff;
  color: #1677ff;
  background: #f0f5ff;
}

/* 消息加载 */
.msg-loading {
  display: flex;
  justify-content: center;
  padding: 40px 0;
}

/* 思考中动画 */
.thinking {
  display: flex;
  gap: 10px;
  align-self: flex-start;
  margin-bottom: 20px;
}
.thinking-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.thinking-bubble {
  padding: 10px 16px;
  background: #f4f4f5;
  border-radius: 12px;
  border-top-left-radius: 4px;
  font-size: 14px;
  color: #999;
  display: flex;
  align-items: center;
  gap: 6px;
}
.dot-animate {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #999;
  animation: dotPulse 1.2s ease-in-out infinite;
}
@keyframes dotPulse {
  0%, 100% { opacity: 0.3; }
  50% { opacity: 1; }
}

/* 输入区域 */
.input-area {
  padding: 16px 48px 24px;
  border-top: 1px solid #f0f0f0;
  background: #fff;
}
.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  max-width: 800px;
  margin: 0 auto;
  background: #f7f8fa;
  border: 1px solid #e8e8e8;
  border-radius: 12px;
  padding: 8px 12px;
  transition: border-color 0.2s;
}
.input-wrapper:focus-within {
  border-color: #1677ff;
}
.input-wrapper :deep(.ant-input) {
  background: transparent;
  border: none;
  box-shadow: none !important;
  font-size: 14px;
  padding: 4px 0;
  resize: none;
}
.send-btn {
  flex-shrink: 0;
}

/* 空状态 */
.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
}
.empty-icon {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  font-size: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 8px;
}
.empty-title {
  font-size: 22px;
  font-weight: 600;
  color: #1a1a1a;
}
.empty-desc {
  font-size: 14px;
  color: #999;
  margin-bottom: 8px;
}
</style>
