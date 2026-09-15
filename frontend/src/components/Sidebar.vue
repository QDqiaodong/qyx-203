<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

const menuItems = [
  { path: '/', label: '首页', icon: 'el-icon-s-home' },
  { path: '/devices', label: '设备管理', icon: 'el-icon-s-tools' },
  { path: '/time-slots', label: '时段绑定', icon: 'el-icon-time' },
  { path: '/peak-capacity', label: '高峰上限', icon: 'el-icon-data-analysis' },
  { path: '/change-logs', label: '变更记录', icon: 'el-icon-document' },
  { path: '/statistics', label: '统计分析', icon: 'el-icon-data-line' },
]

const isActive = (path: string) => route.path === path || route.path.startsWith(path + '/')
</script>

<template>
  <aside class="sidebar">
    <div class="logo">
      <i class="el-icon-baby"></i>
      <span>母婴室设备管理</span>
    </div>
    <nav class="menu">
      <div
        v-for="item in menuItems"
        :key="item.path"
        :class="['menu-item', { active: isActive(item.path) }]"
        @click="router.push(item.path)"
      >
        <i :class="item.icon"></i>
        <span>{{ item.label }}</span>
      </div>
    </nav>
  </aside>
</template>

<style scoped>
.sidebar {
  width: 220px;
  background: linear-gradient(180deg, #1E88E5 0%, #1565C0 100%);
  color: #fff;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  position: fixed;
  left: 0;
  top: 0;
}

.logo {
  padding: 24px 20px;
  font-size: 18px;
  font-weight: bold;
  display: flex;
  align-items: center;
  gap: 10px;
  border-bottom: 1px solid rgba(255,255,255,0.1);
}

.logo i {
  font-size: 24px;
}

.menu {
  flex: 1;
  padding: 20px 0;
}

.menu-item {
  padding: 14px 24px;
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.menu-item:hover {
  background-color: rgba(255,255,255,0.1);
}

.menu-item.active {
  background-color: rgba(255,255,255,0.2);
  border-left: 4px solid #fff;
}

.menu-item i {
  font-size: 18px;
}
</style>