<template>
  <div class="chat-sidebar">
    <!-- 顶部 -->
    <div class="sb-top">
      <span class="sb-brand">智能助手</span>
      <a-button class="sb-new" type="primary" size="small" @click="$emit('create')">
        <template #icon><PlusOutlined /></template>
        新对话
      </a-button>
    </div>

    <!-- 对话列表 -->
    <div class="sb-list">
      <div v-if="list.length" class="sb-label">历史对话</div>
      <div
        v-for="c in list"
        :key="c.id"
        class="sb-item"
        :class="{ active: c.id === activeId }"
        @click="$emit('select', c.id)"
      >
        <span class="sb-title">{{ c.title || '新对话' }}</span>
        <a-popconfirm title="删除此对话？" ok-text="删除" cancel-text="取消" @confirm="$emit('delete', c.id)">
          <DeleteOutlined class="sb-del" @click.stop />
        </a-popconfirm>
      </div>
      <div v-if="!list.length" class="sb-empty">暂无对话</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import type { ConversationVO, SnowflakeId } from '@/api/agentApi'

defineProps<{ list: ConversationVO[]; activeId?: SnowflakeId }>()
defineEmits<{ create: []; select: [id: SnowflakeId]; delete: [id: SnowflakeId] }>()
</script>

<style scoped>
.chat-sidebar {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f7f8fa;
  border-right: 1px solid #e8e8e8;
}
.sb-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 14px 10px;
}
.sb-brand { font-weight: 600; font-size: 16px; color: #1a1a1a; }
.sb-new { border-radius: 6px; }
.sb-list { flex: 1; overflow-y: auto; padding: 0 8px 8px; }
.sb-label { font-size: 11px; color: #aaa; padding: 8px 8px 4px; }
.sb-item {
  display: flex;
  align-items: center;
  padding: 9px 10px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 2px;
  transition: background .12s;
}
.sb-item:hover { background: #eaeaea; }
.sb-item.active { background: #dbeafe; }
.sb-title {
  flex: 1;
  font-size: 13px;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sb-del { color: transparent; font-size: 12px; margin-left: 4px; transition: color .12s; }
.sb-item:hover .sb-del { color: #bbb; }
.sb-del:hover { color: #ff4d4f !important; }
.sb-empty { text-align: center; color: #ccc; margin-top: 48px; font-size: 13px; }
</style>
