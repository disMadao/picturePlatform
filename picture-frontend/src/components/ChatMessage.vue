<template>
  <div class="chat-msg" :class="msg.role">
    <!-- assistant 消息 -->
    <template v-if="msg.role === 'assistant'">
      <div class="avatar assistant-avatar">
        <RobotOutlined />
      </div>
      <div class="msg-body">
        <div class="bubble assistant-bubble">
          <div v-if="msg.content" class="text-content" v-html="renderText(msg.content)" />
          <!-- 搜索结果图片网格 -->
          <div
            v-if="msg.contentType === 'search_result' && pictures.length"
            class="pic-grid"
          >
            <div
              v-for="pic in pictures"
              :key="pic.id"
              class="pic-item"
              @click="goToPicture(pic)"
            >
              <img :src="pic.thumbnailUrl || pic.url" :alt="pic.name" loading="lazy" />
              <div class="pic-label">{{ pic.name }}</div>
            </div>
          </div>
          <div
            v-if="msg.contentType === 'search_result' && !pictures.length && !msg.content"
            class="no-result"
          >
            未找到相关图片
          </div>
        </div>
        <span class="msg-time">{{ formatTime(msg.createTime) }}</span>
      </div>
    </template>

    <!-- user 消息 -->
    <template v-else>
      <div class="msg-body">
        <div class="bubble user-bubble">
          <div class="text-content">{{ msg.content }}</div>
        </div>
        <span class="msg-time">{{ formatTime(msg.createTime) }}</span>
      </div>
      <div class="avatar user-avatar">
        <UserOutlined />
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { RobotOutlined, UserOutlined } from '@ant-design/icons-vue'
import type { MessageVO } from '@/api/agentController'

const props = defineProps<{ msg: MessageVO }>()
const router = useRouter()

const pictures = computed(() => {
  if (props.msg.contentType !== 'search_result') return []
  return props.msg.extra?.pictures ?? []
})

const goToPicture = (pic: any) => {
  if (pic.id) {
    window.open(`/picture/${pic.id}`, '_blank')
  }
}

const renderText = (text?: string) => {
  if (!text) return ''
  return text.replace(/\n/g, '<br/>')
}

const formatTime = (t?: string) => {
  if (!t) return ''
  const d = new Date(t)
  if (isNaN(d.getTime())) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<style scoped>
.chat-msg {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
  max-width: 85%;
}
.chat-msg.user {
  align-self: flex-end;
  flex-direction: row;
}
.chat-msg.assistant {
  align-self: flex-start;
  flex-direction: row;
}

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 16px;
}
.assistant-avatar {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
}
.user-avatar {
  background: #1677ff;
  color: #fff;
}

.msg-body {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.bubble {
  padding: 12px 16px;
  border-radius: 12px;
  line-height: 1.6;
  word-break: break-word;
  font-size: 14px;
}
.assistant-bubble {
  background: #f4f4f5;
  color: #1a1a1a;
  border-top-left-radius: 4px;
}
.user-bubble {
  background: #1677ff;
  color: #fff;
  border-top-right-radius: 4px;
}

.msg-time {
  font-size: 11px;
  color: #bbb;
  margin-top: 4px;
  padding: 0 4px;
}
.user .msg-time {
  text-align: right;
}

.text-content {
  white-space: pre-wrap;
}

/* 图片搜索结果网格 */
.pic-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
  gap: 8px;
  margin-top: 10px;
}
.pic-item {
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  background: #fff;
  border: 1px solid #e8e8e8;
  transition: transform 0.15s, box-shadow 0.15s;
}
.pic-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}
.pic-item img {
  width: 100%;
  height: 96px;
  object-fit: cover;
  display: block;
}
.pic-label {
  padding: 4px 8px;
  font-size: 12px;
  color: #666;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.no-result {
  color: #999;
  font-size: 13px;
}
</style>
