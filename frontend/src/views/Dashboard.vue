<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { deviceApi, timeSlotApi, changeLogApi } from '@/api'

const stats = ref({
  deviceCount: 0,
  timeSlotCount: 0,
  changeLogCount: 0,
})

onMounted(async () => {
  try {
    const [deviceRes, timeSlotRes, changeLogRes] = await Promise.all([
      deviceApi.list({ page: 0, size: 1 }),
      timeSlotApi.list({ page: 0, size: 1 }),
      changeLogApi.list({ page: 0, size: 1 }),
    ])
    stats.value.deviceCount = deviceRes.data.data.totalElements
    stats.value.timeSlotCount = timeSlotRes.data.data.totalElements
    stats.value.changeLogCount = changeLogRes.data.data.totalElements
  } catch {
    console.error('Failed to load stats')
  }
})
</script>

<template>
  <div class="dashboard">
    <h2>系统概览</h2>
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon devices">
          <i class="el-icon-s-tools"></i>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ stats.deviceCount }}</div>
          <div class="stat-label">设备总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon slots">
          <i class="el-icon-time"></i>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ stats.timeSlotCount }}</div>
          <div class="stat-label">时段绑定数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon logs">
          <i class="el-icon-document"></i>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ stats.changeLogCount }}</div>
          <div class="stat-label">变更记录数</div>
        </div>
      </div>
    </div>
    <div class="info-card">
      <h3>系统简介</h3>
      <p>机场航站楼母婴室配套设备使用时段关联登记系统，用于管理母婴室配套设备的使用时段分配与变更记录。</p>
      <ul>
        <li>设备档案管理：设备编号、功能类型、航站楼分区</li>
        <li>时段绑定管理：运营时段区间初始分配与调整</li>
        <li>变更记录：时段调整历史记录查询</li>
        <li>统计分析：按时段筛选设备、单设备全时段分配明细</li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.dashboard {
  margin-left: 220px;
}

h2 {
  font-size: 24px;
  margin-bottom: 24px;
  color: #333;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-bottom: 24px;
}

.stat-card {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 20px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.08);
}

.stat-icon {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  color: #fff;
}

.stat-icon.devices {
  background: linear-gradient(135deg, #1E88E5, #42A5F5);
}

.stat-icon.slots {
  background: linear-gradient(135deg, #43A047, #66BB6A);
}

.stat-icon.logs {
  background: linear-gradient(135deg, #FB8C00, #FFA726);
}

.stat-content {
  flex: 1;
}

.stat-value {
  font-size: 36px;
  font-weight: bold;
  color: #333;
}

.stat-label {
  font-size: 14px;
  color: #999;
  margin-top: 4px;
}

.info-card {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.08);
}

.info-card h3 {
  font-size: 18px;
  margin-bottom: 16px;
  color: #333;
}

.info-card p {
  color: #666;
  line-height: 1.6;
  margin-bottom: 16px;
}

.info-card ul {
  list-style: none;
  padding: 0;
}

.info-card li {
  padding: 8px 0;
  color: #666;
  padding-left: 20px;
  position: relative;
}

.info-card li::before {
  content: '•';
  position: absolute;
  left: 0;
  color: #1E88E5;
}
</style>