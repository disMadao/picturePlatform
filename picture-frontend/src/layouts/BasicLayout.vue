<template>
  <div id="basicLayout">
    <a-layout style="min-height: 100vh">
      <a-layout-header class="header">
        <GlobalHeader />
      </a-layout-header>
      <a-layout>
        <GlobalSider class="sider" />
        <a-layout-content class="content">
          <router-view />
        </a-layout-content>
      </a-layout>
      <a-layout-footer class="footer">
        <!-- 极简风格：统一为中性文案，去除原作者与站点外链 -->
        <span>图片平台</span>
      </a-layout-footer>
    </a-layout>
  </div>
</template>

<script setup lang="ts">
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalSider from "@/components/GlobalSider.vue";
</script>

<style scoped>
#basicLayout .header {
  padding-inline: 20px;
  /* 极简风格：统一使用纯白背景和细分割线 */
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
  /* 极简风格：去掉渐变背景，改为统一浅灰色 */
  background: #f7f7f7;
  margin-bottom: 28px;
}

#basicLayout .footer {
  background: #f7f7f7;
  padding: 16px;
  text-align: center;
}
</style>
