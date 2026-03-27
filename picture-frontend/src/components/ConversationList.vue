<template>
  <div class="conv-sidebar">
    <div class="sidebar-top">
      <div class="brand">智能助手</div>
      <a-button class="new-btn" type="primary" block @click="$emit('create')">
        <template #icon><PlusOutlined /></template>
        新对话
      </a-button>
    </div>

    <div class="sidebar-section-title" v-if="conversations.length">历史对话</div>

    <div class="conv-scroll">
      <div
        v-for="conv in conversations"
        :key="conv.id"
        class="conv-item"
        :class="{ active: conv.id === activeId }"
        @click="$emit('select', conv.id)"
      >
        <MessageOutlined class="conv-icon" />
        <span class="conv-title">{{ conv.title || '新对话' }}</span>
        <a-popconfirm
          title="确定删除这个对话？"
          ok-text="删除"
          cancel-text="取消"
          @confirm="$emit('delete', conv.id)"
        >
          <DeleteOutlined class="conv-delete" @click.stop />
        </a-popconfirm>
      </div>

      <div v-if="!conversations.length" class="empty-hint">
        <CommentOutlined style="font-size: 32px; color: #ddd" />
        <span>暂无对话记录</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  PlusOutlined,
  MessageOutlined,
  DeleteOutlined,
  CommentOutlined,
} from '@ant-design/icons-vue'
import type { ConversationVO } from '@/api/agentController'

defineProps<{
  conversations: ConversationVO[]
  activeId?: number
}>()

defineEmits<{
  create: []
  select: [id: number]
  delete: [id: number]
}>()
</script>

<style scoped>
.conv-sidebar {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f7f8fa;
  border-right: 1px solid #ebebeb;
}

.sidebar-top {
  padding: 16px;
  flex-shrink: 0;
}

.brand {
  font-size: 17px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 12px;
}

.new-btn {
  border-radius: 8px;
  height: 38px;
  font-size: 14px;
}

.sidebar-section-title {
  padding: 12px 16px 6px;
  font-size: 12px;
  color: #999;
  flex-shrink: 0;
}

.conv-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px 12px;
}

.conv-item {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
  margin-bottom: 2px;
}
.conv-item:hover {
  background: #eaeaea;
}
.conv-item.active {
  background: #e1ecff;
}

.conv-icon {
  flex-shrink: 0;
  margin-right: 8px;
  color: #888;
  font-size: 14px;
}
.conv-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: #333;
}
.conv-delete {
  flex-shrink: 0;
  color: transparent;
  margin-left: 4px;
  font-size: 12px;
  transition: color 0.15s;
}
.conv-item:hover .conv-delete {
  color: #bbb;
}
.conv-delete:hover {
  color: #ff4d4f !important;
}

.empty-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin-top: 60px;
  color: #ccc;
  font-size: 13px;
}
</style>
