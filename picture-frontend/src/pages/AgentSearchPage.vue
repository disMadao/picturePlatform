<template>
  <div id="agentSearchPage">
    <!-- 统一输入：可输入文本，也可粘贴图片链接（或包含图片链接的描述） -->
    <div class="search-bar">
      <a-input-search
        v-model:value="smartInput"
        placeholder="输入文本描述，或直接粘贴图片链接（也可：描述 + 图片链接）"
        enter-button="智能搜索"
        size="large"
        @search="doSearch"
      />
      <div class="hint">会自动从输入中提取可能的图片链接，并在文本搜图 / 以图搜图之间自动选择。</div>
    </div>

    <!-- 调试信息：展示 Agent 选择的模式和 ReAct 轨迹 -->
    <a-alert
      v-if="agentData"
      type="info"
      show-icon
      style="margin-bottom: 16px"
      :message="`当前模式：${agentModeLabel}`"
      :description="reactDescription"
    />

    <!-- 结果列表 -->
    <PictureList :dataList="pictures" :loading="loading" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { message } from 'ant-design-vue'
import PictureList from '@/components/PictureList.vue'
import { agentSearchUsingPost, type AgentSearchData } from '@/api/agentController.ts'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'

const smartInput = ref('')
const loginUserStore = useLoginUserStore()

const loading = ref(false)
const pictures = ref<API.PictureVO[]>([])
const agentData = ref<AgentSearchData>()

const agentModeLabel = computed(() => {
  if (!agentData.value) return ''
  const map: Record<string, string> = {
    backend: '原有后端关键词搜索',
    vector_text: '向量文本搜图',
    vector_image: '向量以图搜图',
    auto: '自动路由',
  }
  return map[agentData.value.mode] || agentData.value.mode
})

const reactDescription = computed(() => {
  if (!agentData.value?.steps?.length) return ''
  // 只展示前几步思考过程，保持信息量合适
  return agentData.value.steps
    .slice(0, 3)
    .map((s) => `Thought: ${s.thought} | Action: ${s.action} | Observation: ${s.observation}`)
    .join('；')
})

const IMAGE_EXT_RE = /\.(png|jpe?g|webp|gif|bmp|svg)(\?.*)?$/i

function cleanupUrlCandidate(url: string) {
  // 去掉常见的尾部标点/括号
  return url.replace(/[)\]}'"，。,.;；！!？?\s]+$/g, '')
}

function extractFirstUrl(input: string): string | undefined {
  // 尽量保守：只抓 http(s) URL；避免把普通文本误识别
  const match = input.match(/https?:\/\/[^\s]+/i)
  if (!match?.[0]) return undefined
  return cleanupUrlCandidate(match[0])
}

function extractSmartParams(input: string): { query_text?: string; image_url?: string } {
  const raw = (input || '').trim()
  if (!raw) return {}

  const url = extractFirstUrl(raw)
  if (!url) {
    return { query_text: raw }
  }

  // 只在“看起来像图片链接”时才走以图搜图；否则仍以文本为主，避免误判
  const looksLikeImage = IMAGE_EXT_RE.test(url)
  const textWithoutUrl = raw.replace(url, '').trim()

  return {
    query_text: textWithoutUrl || (looksLikeImage ? undefined : raw),
    image_url: looksLikeImage ? url : undefined,
  }
}

const doSearch = async () => {
  const { query_text, image_url } = extractSmartParams(smartInput.value)
  if (!query_text && !image_url) {
    message.warning('请输入搜索内容或图片链接')
    return
  }

  loading.value = true
  try {
    const res = await agentSearchUsingPost({
      user_id:
        loginUserStore.loginUser.id != null && loginUserStore.loginUser.id !== ''
          ? String(loginUserStore.loginUser.id)
          : undefined,
      query_text,
      image_url,
      mode: 'auto',
      top_k: 20,
    })
    if (res.data.code === 0 && res.data.data) {
      agentData.value = res.data.data
      pictures.value = res.data.data.pictures ?? []
    } else {
      message.error('智能搜索失败，' + (res.data.message || ''))
    }
  } catch (e: any) {
    message.error('智能搜索失败：' + e.message)
  }
  loading.value = false
}
</script>

<style scoped>
#agentSearchPage {
  margin-bottom: 16px;
}

.search-bar {
  max-width: 640px;
  margin: 0 auto 16px;
}

.hint {
  margin-top: 8px;
  color: #999;
  font-size: 12px;
}
</style>


