<template>
  <div class="agent-page">
    <!-- 左栏：对话列表 -->
    <ChatSidebar
      class="agent-sidebar"
      :list="convList"
      :activeId="activeId"
      @create="onNewConv"
      @select="onSelectConv"
      @delete="onDeleteConv"
    />

    <!-- 右栏：聊天 -->
    <div class="agent-chat">
      <!-- ---- 有选中对话 ---- -->
      <template v-if="activeId">
        <div ref="scrollRef" class="chat-scroll">
          <!-- 欢迎态 -->
          <div v-if="!msgs.length && !loadingMsgs" class="welcome">
            <div class="w-icon"><RobotOutlined /></div>
            <div class="w-title">你好，我是智能助手</div>
            <div class="w-sub">你可以让我搜索图片、生成视频，或者随便聊聊</div>
            <div class="w-chips">
              <span class="chip" @click="quickSend('帮我找 5 张星空的图片')">帮我找 5 张星空的图片</span>
              <span class="chip" @click="quickSend('推荐 3 张适合做壁纸的风景照')">推荐 3 张壁纸风景照</span>
              <span class="chip" @click="quickSend('有没有夕阳相关的图片')">有没有夕阳相关的图片</span>
            </div>
          </div>

          <!-- 消息加载态 -->
          <div v-if="loadingMsgs" class="loading-box"><a-spin /></div>

          <!-- 消息列表 -->
          <ChatBubble v-for="m in msgs" :key="m.id" :msg="m" />

          <!-- 正在思考 -->
          <div v-if="sending" class="thinking">
            <div class="th-avatar"><RobotOutlined /></div>
            <div class="th-text"><a-spin size="small" /> &nbsp;正在思考...</div>
          </div>
        </div>

        <!-- 输入栏 -->
        <div class="input-bar">
          <div class="input-card" :class="{ focus: inputFocus }">
            <a-textarea
              ref="inputEl"
              v-model:value="draft"
              :disabled="sending"
              :auto-size="{ minRows: 1, maxRows: 5 }"
              :placeholder="sending ? '等待回复...' : '输入消息，例如「帮我找 10 张海边的图片」'"
              @focus="inputFocus = true"
              @blur="inputFocus = false"
              @pressEnter="onEnter"
            />
            <a-button
              type="primary"
              shape="circle"
              size="small"
              :disabled="!draft.trim() || sending"
              :loading="sending"
              @click="doSend"
            >
              <template v-if="!sending" #icon><SendOutlined /></template>
            </a-button>
          </div>
        </div>
      </template>

      <!-- ---- 无选中 ---- -->
      <div v-else class="empty-box">
        <div class="w-icon big"><RobotOutlined /></div>
        <div class="w-title">智能助手</div>
        <div class="w-sub">选择一个历史对话，或开始新对话</div>
        <a-button type="primary" @click="onNewConv">
          <template #icon><PlusOutlined /></template>
          新对话
        </a-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { RobotOutlined, SendOutlined, PlusOutlined } from '@ant-design/icons-vue'
import ChatSidebar from '@/components/agent/ChatSidebar.vue'
import ChatBubble from '@/components/agent/ChatBubble.vue'
import {
  apiCreateConversation,
  apiListConversations,
  apiDeleteConversation,
  apiListMessages,
  apiSendMessage,
  type ConversationVO,
  type MessageVO,
} from '@/api/agentApi'
import { useLoginUserStore } from '@/stores/useLoginUserStore'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()
const uid = () => loginUserStore.loginUser.id as number | undefined

const convList = ref<ConversationVO[]>([])
const activeId = ref<number>()
const msgs = ref<MessageVO[]>([])
const draft = ref('')
const sending = ref(false)
const loadingMsgs = ref(false)
const inputFocus = ref(false)
const scrollRef = ref<HTMLElement>()
const inputEl = ref()

const toBottom = () => nextTick(() => {
  if (scrollRef.value) scrollRef.value.scrollTop = scrollRef.value.scrollHeight
})

const syncQuery = (id?: number) =>
  router.replace({ path: '/agent/chat', query: id ? { id: String(id) } : {} })

// ---- 数据加载 ----
const loadConvs = async () => {
  const u = uid()
  if (!u) return
  try {
    const r = await apiListConversations(u)
    if (r.data.code === 0) convList.value = r.data.data ?? []
  } catch (e) {
    console.error(e)
  }
}

const loadMsgs = async (cid: number) => {
  loadingMsgs.value = true
  try {
    const r = await apiListMessages(cid)
    if (r.data.code === 0) { msgs.value = r.data.data ?? []; toBottom() }
  } catch (e) {
    console.error(e)
  }
  loadingMsgs.value = false
}

// ---- 对话管理 ----
const onNewConv = async () => {
  const u = uid()
  if (!u) { message.warning('请先登录'); return }
  try {
    const r = await apiCreateConversation(u)
    if (r.data.code === 0 && r.data.data) {
      const nid = r.data.data.id
      await loadConvs()
      activeId.value = nid
      msgs.value = []
      syncQuery(nid)
      nextTick(() => inputEl.value?.focus?.())
    }
  } catch (e: any) {
    message.error('创建失败')
  }
}

const onSelectConv = async (id: number) => {
  if (id === activeId.value) return
  activeId.value = id
  syncQuery(id)
  await loadMsgs(id)
}

const onDeleteConv = async (id: number) => {
  const u = uid()
  if (!u) return
  try {
    await apiDeleteConversation(u, id)
    if (activeId.value === id) { activeId.value = undefined; msgs.value = []; syncQuery() }
    await loadConvs()
  } catch { message.error('删除失败') }
}

// ---- 发送消息 ----
const quickSend = (t: string) => { draft.value = t; doSend() }

const onEnter = (e: KeyboardEvent) => {
  if (e.shiftKey) return
  e.preventDefault()
  doSend()
}

const doSend = async () => {
  const text = draft.value.trim()
  if (!text || !activeId.value || sending.value) return
  const u = uid()
  if (!u) { message.warning('请先登录'); return }

  // 先追加用户消息到界面
  const tmpUser: MessageVO = {
    id: Date.now(),
    conversationId: activeId.value,
    role: 'user',
    contentType: 'text',
    content: text,
    createTime: new Date().toISOString(),
  }
  msgs.value.push(tmpUser)
  draft.value = ''
  toBottom()

  sending.value = true
  try {
    const r = await apiSendMessage({
      user_id: u,
      conversation_id: activeId.value,
      content: text,
    })
    if (r.data.code === 0 && r.data.data) {
      const d = r.data.data
      msgs.value.push({
        id: Date.now() + 1,
        conversationId: activeId.value!,
        role: 'assistant',
        contentType: d.contentType,
        content: d.content,
        extra: d.extra,
        createTime: new Date().toISOString(),
      })
      toBottom()
      loadConvs()
    } else {
      message.error(r.data.message || '发送失败')
    }
  } catch (e: any) {
    message.error('请求失败：' + (e.message || ''))
  }
  sending.value = false
}

// ---- 初始化 ----
onMounted(async () => {
  await loadConvs()
  const qid = Number(route.query.id)
  if (qid && convList.value.some(c => c.id === qid)) {
    activeId.value = qid
    await loadMsgs(qid)
  } else if (convList.value.length) {
    activeId.value = convList.value[0].id
    syncQuery(convList.value[0].id)
    await loadMsgs(convList.value[0].id)
  }
})
</script>

<style scoped>
.agent-page { display: flex; height: calc(100vh - 64px); overflow: hidden; }
.agent-sidebar { width: 240px; flex-shrink: 0; }
.agent-chat { flex: 1; display: flex; flex-direction: column; min-width: 0; background: #fff; }

/* 滚动消息区 */
.chat-scroll { flex: 1; overflow-y: auto; padding: 28px 44px; display: flex; flex-direction: column; }

/* 欢迎 */
.welcome, .empty-box { display: flex; flex-direction: column; align-items: center; justify-content: center; }
.welcome { margin-top: 60px; }
.empty-box { flex: 1; gap: 10px; }
.w-icon {
  width: 56px; height: 56px; border-radius: 50%;
  background: linear-gradient(135deg,#667eea,#764ba2); color: #fff;
  font-size: 24px; display: flex; align-items: center; justify-content: center;
  margin-bottom: 12px;
}
.w-icon.big { width: 68px; height: 68px; font-size: 30px; }
.w-title { font-size: 18px; font-weight: 600; color: #1a1a1a; }
.w-sub { font-size: 13px; color: #999; margin: 6px 0 18px; }
.w-chips { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; }
.chip {
  padding: 7px 16px; border: 1px solid #e0e0e0; border-radius: 18px;
  font-size: 13px; color: #555; cursor: pointer; transition: all .12s;
}
.chip:hover { border-color: #1677ff; color: #1677ff; background: #f0f5ff; }

/* 加载/思考 */
.loading-box { display: flex; justify-content: center; padding: 40px 0; }
.thinking { display: flex; gap: 10px; align-self: flex-start; margin-bottom: 18px; }
.th-avatar {
  width: 34px; height: 34px; border-radius: 50%;
  background: linear-gradient(135deg,#667eea,#764ba2); color: #fff;
  font-size: 15px; display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.th-text {
  padding: 10px 16px; background: #f4f4f5; border-radius: 14px;
  border-top-left-radius: 4px; font-size: 13px; color: #999;
  display: flex; align-items: center;
}

/* 输入栏 */
.input-bar { padding: 14px 44px 20px; border-top: 1px solid #f0f0f0; background: #fff; }
.input-card {
  display: flex; align-items: flex-end; gap: 8px;
  background: #f7f8fa; border: 1px solid #e0e0e0; border-radius: 12px;
  padding: 8px 12px; transition: border-color .15s;
  max-width: 780px; margin: 0 auto;
}
.input-card.focus { border-color: #1677ff; }
.input-card :deep(.ant-input) {
  background: transparent; border: none; box-shadow: none !important;
  font-size: 14px; padding: 4px 0; resize: none;
}
</style>
