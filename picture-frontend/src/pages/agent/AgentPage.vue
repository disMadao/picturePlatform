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
            <div class="th-text"><a-spin size="small" /> &nbsp;{{ sendingHint }}</div>
          </div>
        </div>

        <!-- 输入栏 -->
        <div class="input-bar">
          <div class="input-toolbar">
            <a-radio-group v-model:value="sessionMode" size="small" :disabled="sending">
              <a-radio-button value="search">图片搜索</a-radio-button>
              <a-radio-button value="video">视频生成</a-radio-button>
            </a-radio-group>
            <a-select
              v-if="sessionMode === 'video'"
              v-model:value="selectedSpaceId"
              class="space-select"
              placeholder="保存到哪个空间"
              :disabled="sending"
              :options="spaceOptions"
            />
            <span v-if="sessionMode === 'video' && !spaceOptions.length" class="space-hint">
              暂无可用空间，请先在网站「空间」中创建私有空间
            </span>
            <a-input
              v-if="sessionMode === 'video'"
              v-model:value="firstFrameUrl"
              class="frame-input"
              placeholder="首帧图 URL（可选；若填写，方舟要求图高≥300px）"
              :disabled="sending"
              allow-clear
            />
          </div>
          <div class="input-card" :class="{ focus: inputFocus }">
            <a-textarea
              ref="inputEl"
              v-model:value="draft"
              :disabled="sending"
              :auto-size="{ minRows: 1, maxRows: 5 }"
              :placeholder="inputPlaceholder"
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
import { ref, computed, watch, nextTick, onMounted } from 'vue'
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
  type SnowflakeId,
} from '@/api/agentApi'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import { listSpaceVoByPageUsingPost } from '@/api/spaceController'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

/** 当前用户 id（字符串），雪花 Long 勿转 Number，否则丢精度导致查错用户 */
const uid = (): string | undefined => {
  const raw = loginUserStore.loginUser.id as unknown
  if (raw == null || raw === '') return undefined
  return typeof raw === 'string' ? raw : String(raw)
}

const convList = ref<ConversationVO[]>([])
const activeId = ref<SnowflakeId>()
const msgs = ref<MessageVO[]>([])
const draft = ref('')
const sending = ref(false)
const loadingMsgs = ref(false)
const inputFocus = ref(false)
const scrollRef = ref<HTMLElement>()
const inputEl = ref()
const sessionMode = ref<'search' | 'video'>('search')
const selectedSpaceId = ref<string>()
const firstFrameUrl = ref('')
const spaceOptions = ref<{ label: string; value: string }[]>([])

const inputPlaceholder = computed(() => {
  if (sending.value) return '等待回复...'
  return sessionMode.value === 'video'
    ? '描述要生成的视频内容（耗时较长，请耐心等待）'
    : '输入消息，例如「帮我找 10 张海边的图片」'
})

const sendingHint = computed(() =>
  sessionMode.value === 'video' ? '正在生成视频，请稍候…' : '正在思考...',
)

const toBottom = () => nextTick(() => {
  if (scrollRef.value) scrollRef.value.scrollTop = scrollRef.value.scrollHeight
})

const syncQuery = (id?: SnowflakeId) =>
  router.replace({ path: '/agent/chat', query: id != null && id !== '' ? { id: String(id) } : {} })

// ---- 数据加载 ----
const loadConvs = async () => {
  const u = uid()
  if (!u) return
  try {
    const r = await apiListConversations(u)
    if (r.data.code === 0) {
      const rows = r.data.data ?? []
      convList.value = rows.map(c => ({
        ...c,
        id: typeof c.id === 'string' ? c.id : String(c.id),
      }))
    } else {
      message.warning(r.data.message || '加载对话列表失败')
    }
  } catch (e: any) {
    console.error(e)
    message.error(
      '无法连接智能助手服务（请确认本机已启动 Agent:9002，且用 npm run dev 走 Vite 代理）：' +
        (e?.message || String(e)),
    )
  }
}

/** 空间 id 与 user id 同为雪花，禁止 Number()，否则 Java getById 查不到 →「空间不存在」 */
const parseSpaceId = (s: API.SpaceVO): string | undefined => {
  const raw = s.id as unknown
  if (raw == null || raw === '') return undefined
  return typeof raw === 'string' ? raw : String(raw)
}

/** 默认选私有空间，否则第一条（与 MySpacePage 一致：个人空间 spaceType=0） */
const pickDefaultSpaceId = (records: API.SpaceVO[]) => {
  const withId = records
    .map(s => ({ s, id: parseSpaceId(s) }))
    .filter((x): x is { s: API.SpaceVO; id: string } => x.id != null)
  if (!withId.length) return undefined
  const priv = withId.find(x => x.s.spaceType === 0)
  return (priv ?? withId[0]).id
}

/** 仅调 Java /space/list/page/vo（pageSize≤20），与「我的空间」同一数据源 */
const loadSpaces = async () => {
  const u = uid()
  if (!u) return
  try {
    const r = await listSpaceVoByPageUsingPost({
      current: 1,
      pageSize: 20,
      userId: u,
    })
    if (r.data.code !== 0) {
      spaceOptions.value = []
      message.warning(r.data.message || '加载空间失败')
      return
    }
    const records = r.data.data?.records ?? []
    spaceOptions.value = records
      .map(s => {
        const id = parseSpaceId(s)
        if (id == null) return null
        return {
          label:
            (s.spaceType === 0 ? '【私有】' : '【团队】') +
            (s.spaceName || `空间 ${id}`),
          value: id,
        }
      })
      .filter((o): o is { label: string; value: string } => o != null)
    selectedSpaceId.value = pickDefaultSpaceId(records)
  } catch (e) {
    console.error(e)
    spaceOptions.value = []
    message.error('加载空间列表失败')
  }
}

const loadMsgs = async (cid: SnowflakeId) => {
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
      const nid = String(r.data.data.id)
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

const onSelectConv = async (id: SnowflakeId) => {
  if (String(id) === String(activeId.value ?? '')) return
  activeId.value = id
  syncQuery(id)
  await loadMsgs(id)
}

const onDeleteConv = async (id: SnowflakeId) => {
  const u = uid()
  if (!u) return
  try {
    await apiDeleteConversation(u, id)
    if (String(activeId.value ?? '') === String(id)) { activeId.value = undefined; msgs.value = []; syncQuery() }
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

  if (sessionMode.value === 'video') {
    if (selectedSpaceId.value == null) {
      message.warning('请先选择保存视频的空间')
      return
    }
  }

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
  const videoTimeout = 600_000
  try {
    const r = await apiSendMessage(
      {
        user_id: u,
        conversation_id: activeId.value,
        content: text,
        session_intent: sessionMode.value,
        space_id: sessionMode.value === 'video' ? selectedSpaceId.value : undefined,
        first_frame_url: sessionMode.value === 'video' && firstFrameUrl.value.trim()
          ? firstFrameUrl.value.trim()
          : undefined,
      },
      { timeout: sessionMode.value === 'video' ? videoTimeout : 120_000 },
    )
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

watch(sessionMode, mode => {
  if (mode === 'video' && !spaceOptions.value.length) loadSpaces()
})

/** 拉会话 + 空间 + 选中对话 */
const bootstrapAgentPage = async () => {
  if (uid() == null) return

  await loadConvs()
  await loadSpaces()

  const raw = route.query.id
  const qid = Array.isArray(raw) ? raw[0] : raw
  const match = (cid: string) => convList.value.some(c => String(c.id) === cid)
  if (qid && typeof qid === 'string' && match(qid)) {
    activeId.value = qid
    await loadMsgs(qid)
  } else if (convList.value.length) {
    const first = convList.value[0].id
    activeId.value = first
    syncQuery(first)
    await loadMsgs(first)
  }
}

// 挂载后再拉：保证路由守卫里 fetchLoginUser 已执行完毕，且本页再 merge 一次登录态
onMounted(async () => {
  await nextTick()
  await loginUserStore.fetchLoginUser()
  if (uid() == null) return
  await bootstrapAgentPage()
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
.input-toolbar {
  display: flex; flex-wrap: wrap; align-items: center; gap: 10px;
  max-width: 780px; margin: 0 auto 10px;
}
.space-select { min-width: 160px; flex: 1; max-width: 240px; }
.space-hint { font-size: 12px; color: #fa8c16; width: 100%; }
.frame-input { flex: 2; min-width: 180px; }
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
