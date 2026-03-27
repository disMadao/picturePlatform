<template>
  <div id="basicLayout">
    <a-layout style="min-height: 100vh">
      <a-layout-header class="header">
        <GlobalHeader />
      </a-layout-header>
      <a-layout>
        <GlobalSider v-if="!isChatPage" class="sider" />
        <a-layout-content :class="isChatPage ? 'content-chat' : 'content'">
          <router-view />
        </a-layout-content>
      </a-layout>
      <a-layout-footer v-if="!isChatPage" class="footer">
        <span>忆存云图</span>
      </a-layout-footer>
    </a-layout>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalSider from '@/components/GlobalSider.vue'

const route = useRoute()
const isChatPage = computed(() => route.path === '/agent/chat')
</script>

<style scoped>
#basicLayout .header {
  padding-inline: 20px;
  background: #ffffff;
  color: unset;
  margin-bottom: 1px;
  border-bottom: 1px solid #f0f0f0;
}

#basicLayout .sider {
  background: #ffffff;
  border-right: 1px solid #f5f5f5;
  padding-top: 20px;
}

#basicLayout :deep(.ant-menu-root) {
  border-bottom: none !important;
  border-inline-end: none !important;
}

#basicLayout .content {
  padding: 28px;
  background: #f7f7f7;
  margin-bottom: 28px;
}

#basicLayout .content-chat {
  padding: 0;
  background: #fff;
  margin-bottom: 0;
  overflow: hidden;
}

#basicLayout .footer {
  background: #f7f7f7;
  padding: 16px;
  text-align: center;
}
</style>
