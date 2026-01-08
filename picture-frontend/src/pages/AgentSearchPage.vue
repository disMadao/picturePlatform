<template>
  <div id="agentSearchPage">
    <!-- 简单模式切换：文本搜图 / 以图搜图 -->
    <a-segmented
      v-model:value="mode"
      :options="[
        { label: '文本搜图', value: 'text' },
        { label: '以图搜图', value: 'image' },
      ]"
      style="margin-bottom: 16px"
    />

    <!-- 文本搜图输入框 -->
    <div v-if="mode === 'text'" class="search-bar">
      <a-input-search
        v-model:value="queryText"
        placeholder="用自然语言描述你想要的图片，如：夕阳下的城市街景，氛围感照片"
        enter-button="智能搜索"
        size="large"
        @search="doSearch"
      />
      <div class="hint">会自动在普通搜索和向量语义搜索之间选择合适的方式。</div>
    </div>

    <!-- 以图搜图：这里只支持输入图片地址，占位形式 -->
    <div v-else class="search-bar">
      <a-input-search
        v-model:value="imageUrl"
        placeholder="输入图片地址，使用向量数据库做以图搜图"
        enter-button="以图搜图"
        size="large"
        @search="doSearch"
      />
      <div class="hint">图片上传和向量入库逻辑请按自己的向量数据库接入。</div>
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

const mode = ref<'text' | 'image'>('text')
const queryText = ref('')
const imageUrl = ref('')
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

const doSearch = async () => {
  if (mode.value === 'text' && !queryText.value) {
    message.warning('请输入搜索内容')
    return
  }
  if (mode.value === 'image' && !imageUrl.value) {
    message.warning('请输入图片地址')
    return
  }

  loading.value = true
  try {
    const res = await agentSearchUsingPost({
      user_id: loginUserStore.loginUser.id,
      query_text: mode.value === 'text' ? queryText.value : undefined,
      image_url: mode.value === 'image' ? imageUrl.value : undefined,
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


