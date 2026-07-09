<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElCard, ElTimePicker, ElSelect, ElOption, ElButton, ElTable, ElTableColumn, ElDialog, ElMessage, ElTabs, ElTabPane } from 'element-plus'
import { statisticsApi, deviceApi } from '@/api'
import type { Device, TimeSlot } from '@/types'

const devices = ref<Device[]>([])
const filteredDevices = ref<Device[]>([])
const startTime = ref('')
const endTime = ref('')
const selectedDeviceId = ref<number | undefined>(undefined)
const showDetailDialog = ref(false)
const deviceDetail = ref<Device | null>(null)
const deviceTimeSlots = ref<TimeSlot[]>([])

const loadDevices = async () => {
  try {
    const res = await deviceApi.list({ page: 0, size: 100 })
    devices.value = res.data.data.content
  } catch {
    console.error('Failed to load devices')
  }
}

const handleTimeSearch = async () => {
  if (!startTime.value || !endTime.value) {
    ElMessage.warning('请选择时段范围')
    return
  }
  try {
    const res = await statisticsApi.byTime({ startTime: startTime.value, endTime: endTime.value })
    filteredDevices.value = res.data.data
  } catch {
    ElMessage.error('查询失败')
  }
}

const handleDeviceDetail = async (deviceId: number) => {
  try {
    const res = await statisticsApi.deviceDetail(deviceId)
    deviceDetail.value = res.data.data.device
    deviceTimeSlots.value = res.data.data.timeSlots
    showDetailDialog.value = true
  } catch {
    ElMessage.error('获取设备详情失败')
  }
}

onMounted(() => {
  loadDevices()
})
</script>

<template>
  <div class="page-container" style="margin-left: 220px;">
    <h2>统计分析</h2>
    
    <ElTabs type="border-card" style="margin-top: 20px;">
      <ElTabPane label="按时段筛选设备">
        <div class="search-section">
          <ElTimePicker
            v-model="startTime"
            format="HH:mm"
            value-format="HH:mm"
            placeholder="开始时间"
            style="width: 140px; margin-right: 12px;"
          />
          <span style="margin-right: 12px;">-</span>
          <ElTimePicker
            v-model="endTime"
            format="HH:mm"
            value-format="HH:mm"
            placeholder="结束时间"
            style="width: 140px; margin-right: 12px;"
          />
          <ElButton type="primary" @click="handleTimeSearch">查询</ElButton>
        </div>
        <ElTable :data="filteredDevices" border style="width: 100%; margin-top: 16px;">
          <ElTableColumn prop="id" label="ID" width="60" />
          <ElTableColumn prop="deviceCode" label="设备编号" width="140" />
          <ElTableColumn prop="deviceType" label="设备类型" width="120" />
          <ElTableColumn prop="terminalArea" label="航站楼分区" width="140" />
          <ElTableColumn prop="status" label="状态" width="80" />
          <ElTableColumn label="操作" width="120">
            <template #default="scope">
              <ElButton type="primary" size="small" @click="handleDeviceDetail(scope.row.id)">查看明细</ElButton>
            </template>
          </ElTableColumn>
        </ElTable>
        <div v-if="filteredDevices.length === 0 && startTime && endTime" style="text-align: center; padding: 40px; color: #999;">
          该时段暂无绑定的设备
        </div>
      </ElTabPane>

      <ElTabPane label="单设备全时段分配明细">
        <div class="search-section">
          <ElSelect
            v-model="selectedDeviceId"
            placeholder="选择设备"
            style="width: 250px; margin-right: 12px;"
          >
            <ElOption v-for="d in devices" :key="d.id" :label="d.deviceCode + ' - ' + d.deviceType + ' (' + d.terminalArea + ')'" :value="d.id" />
          </ElSelect>
          <ElButton type="primary" @click="handleDeviceDetail(selectedDeviceId || 0)" :disabled="!selectedDeviceId">查看明细</ElButton>
        </div>
      </ElTabPane>
    </ElTabs>

    <ElDialog title="设备时段分配明细" v-model="showDetailDialog" width="700px">
      <div v-if="deviceDetail" class="detail-section">
        <ElCard class="device-info">
          <div class="info-row">
            <span class="label">设备编号</span>
            <span class="value">{{ deviceDetail.deviceCode }}</span>
          </div>
          <div class="info-row">
            <span class="label">设备类型</span>
            <span class="value">{{ deviceDetail.deviceType }}</span>
          </div>
          <div class="info-row">
            <span class="label">航站楼分区</span>
            <span class="value">{{ deviceDetail.terminalArea }}</span>
          </div>
          <div class="info-row">
            <span class="label">设备状态</span>
            <span :class="deviceDetail.status === '正常' ? 'status-normal' : 'status-abnormal'" class="value">
              {{ deviceDetail.status }}
            </span>
          </div>
        </ElCard>

        <ElCard class="time-slots-card" style="margin-top: 16px;">
          <h4>时段分配列表</h4>
          <ElTable :data="deviceTimeSlots" border style="width: 100%; margin-top: 12px;">
            <ElTableColumn label="日期范围" width="200">
              <template #default="scope">
                {{ scope.row.startDate }} ~ {{ scope.row.endDate }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="时段" width="140">
              <template #default="scope">
                {{ scope.row.startTime }} - {{ scope.row.endTime }}
              </template>
            </ElTableColumn>
            <ElTableColumn prop="status" label="状态" width="100">
              <template #default="scope">
                <span :class="scope.row.status === '生效中' ? 'status-active' : 'status-inactive'">
                  {{ scope.row.status }}
                </span>
              </template>
            </ElTableColumn>
            <ElTableColumn prop="createdAt" label="创建时间" width="180" />
          </ElTable>
          <div v-if="deviceTimeSlots.length === 0" style="text-align: center; padding: 20px; color: #999;">
            该设备暂无时段分配记录
          </div>
        </ElCard>
      </div>
    </ElDialog>
  </div>
</template>

<style scoped>
h2 {
  font-size: 22px;
  color: #333;
  margin-bottom: 20px;
}

.search-section {
  display: flex;
  align-items: center;
  padding: 20px;
  background: #fff;
  border-radius: 8px;
}

.detail-section {
  padding: 8px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.info-row:last-child {
  border-bottom: none;
}

.label {
  color: #999;
}

.value {
  font-weight: 500;
}

.status-normal {
  color: #43A047;
}

.status-abnormal {
  color: #E53935;
}

.status-active {
  color: #43A047;
  font-weight: bold;
}

.status-inactive {
  color: #E53935;
  font-weight: bold;
}
</style>