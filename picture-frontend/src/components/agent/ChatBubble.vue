<template>
  <div class="cb" :class="msg.role">
    <!-- ====== assistant ====== -->
    <template v-if="msg.role === 'assistant'">
      <div class="cb-avatar bot"><RobotOutlined /></div>
      <div class="cb-body">
        <div class="bubble bot-bubble">
          <!-- 文本 -->
          <div v-if="msg.content" class="cb-text" v-html="nl2br(msg.content)" />

          <!-- 搜索结果：图片网格 -->
          <div v-if="msg.contentType === 'search_result' && pics.length" class="pic-grid">
            <div v-for="p in pics" :key="p.id" class="pic-card">
              <div class="pic-img-wrap" @click="openPic(p)">
                <img :src="p.thumbnailUrl || p.url" :alt="p.name" loading="lazy" />
              </div>
              <div class="pic-footer">
                <span class="pic-name" :title="p.name">{{ p.name }}</span>
                <a-tooltip title="复制图片链接">
                  <CopyOutlined class="pic-action" @click="copyLink(p)" />
                </a-tooltip>
              </div>
            </div>
          </div>

          <!-- 有文案但无图时也要提示（否则像「只有一句话」） -->
          <div v-if="msg.contentType === 'search_result' && !pics.length" class="cb-empty">
            未找到可展示的图片
          </div>

          <!-- 视频 -->
          <div v-if="msg.contentType === 'video'" class="video-wrap">
            <video
              v-if="videoUrl"
              :src="videoUrl"
              controls
              preload="metadata"
              class="video-player"
            />
            <div v-else class="video-placeholder">视频生成中，请稍后...</div>
            <a-button
              v-if="videoUrl"
              type="link"
              class="video-download"
              @click="downloadVideo"
            >
              <template #icon><DownloadOutlined /></template>
              下载视频
            </a-button>
          </div>
        </div>
        <span class="cb-time">{{ fmtTime(msg.createTime) }}</span>
      </div>
    </template>

    <!-- ====== user ====== -->
    <template v-else>
      <div class="cb-body right">
        <div class="bubble user-bubble">{{ msg.content }}</div>
        <span class="cb-time right">{{ fmtTime(msg.createTime) }}</span>
      </div>
      <div class="cb-avatar user"><UserOutlined /></div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { message as antMsg } from 'ant-design-vue'
import {
  RobotOutlined,
  UserOutlined,
  CopyOutlined,
  DownloadOutlined,
} from '@ant-design/icons-vue'
import type { MessageVO, PictureItem } from '@/api/agentApi'

const props = defineProps<{ msg: MessageVO }>()

const pics = computed<PictureItem[]>(() =>
  props.msg.contentType === 'search_result' ? props.msg.extra?.pictures ?? [] : [],
)

const videoUrl = computed(() => props.msg.extra?.videoUrl ?? '')

const openPic = (p: PictureItem) => {
  if (p.id) window.open(`/picture/${p.id}`, '_blank')
}

const copyLink = async (p: PictureItem) => {
  const link = p.url || p.thumbnailUrl || ''
  if (!link) return
  try {
    await navigator.clipboard.writeText(link)
    antMsg.success('链接已复制')
  } catch {
    antMsg.error('复制失败，请手动复制')
  }
}

const downloadVideo = () => {
  if (!videoUrl.value) return
  const a = document.createElement('a')
  a.href = videoUrl.value
  a.download = ''
  a.target = '_blank'
  a.click()
}

const nl2br = (s?: string) => (s ?? '').replace(/\n/g, '<br/>')

const fmtTime = (t?: string) => {
  if (!t) return ''
  const d = new Date(t)
  if (isNaN(d.getTime())) return ''
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}
</script>

<style scoped>
.cb { display: flex; gap: 10px; margin-bottom: 22px; max-width: 88%; }
.cb.user { align-self: flex-end; }
.cb.assistant { align-self: flex-start; }

/* 头像 */
.cb-avatar {
  width: 34px; height: 34px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0; font-size: 15px;
}
.cb-avatar.bot { background: linear-gradient(135deg,#667eea,#764ba2); color: #fff; }
.cb-avatar.user { background: #1677ff; color: #fff; }

.cb-body { display: flex; flex-direction: column; min-width: 0; }
.cb-body.right { align-items: flex-end; }

/* 气泡 */
.bubble {
  padding: 12px 16px; border-radius: 14px;
  line-height: 1.65; word-break: break-word; font-size: 14px;
}
.bot-bubble { background: #f4f4f5; color: #1a1a1a; border-top-left-radius: 4px; }
.user-bubble { background: #1677ff; color: #fff; border-top-right-radius: 4px; white-space: pre-wrap; }

.cb-time { font-size: 11px; color: #c0c0c0; margin-top: 4px; padding: 0 2px; }
.cb-time.right { text-align: right; }

.cb-text { white-space: pre-wrap; }
.cb-empty { color: #999; font-size: 13px; }

/* ---- 图片网格 ---- */
.pic-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 10px;
  margin-top: 10px;
}
.pic-card {
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
  border: 1px solid #eaeaea;
  transition: box-shadow .15s;
}
.pic-card:hover { box-shadow: 0 3px 12px rgba(0,0,0,.08); }
.pic-img-wrap { cursor: pointer; }
.pic-img-wrap img { width: 100%; height: 100px; object-fit: cover; display: block; }
.pic-footer {
  display: flex; align-items: center; padding: 4px 8px; gap: 4px;
}
.pic-name {
  flex: 1; font-size: 12px; color: #555;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.pic-action {
  color: #aaa; font-size: 13px; cursor: pointer; flex-shrink: 0;
  transition: color .12s;
}
.pic-action:hover { color: #1677ff; }

/* ---- 视频 ---- */
.video-wrap { margin-top: 8px; }
.video-player { width: 100%; max-width: 420px; border-radius: 8px; }
.video-placeholder {
  padding: 32px 0; text-align: center; color: #bbb; font-size: 13px;
  background: #fafafa; border-radius: 8px;
}
.video-download { margin-top: 4px; padding-left: 0; }
</style>
