<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElTable, ElTableColumn, ElSelect, ElOption, ElDatePicker, ElPagination, ElButton, ElMessage } from 'element-plus'
import { changeLogApi, deviceApi } from '@/api'
import type { ChangeLog, Device } from '@/types'

const changeLogs = ref<ChangeLog[]>([])
const devices = ref<Device[]>([])
const loading = ref(false)
const page = ref(0)
const size = ref(10)
const total = ref(0)
const selectedDeviceId = ref<number | undefined>(undefined)
const startDate = ref('')
const endDate = ref('')

const deviceMap = computed(() => {
  const map: Record<number, Device> = {}
  devices.value.forEach(d => { map[d.id] = d })
  return map
})

const loadChangeLogs = async () => {
  loading.value = true
  try {
    const res = await changeLogApi.list({
      page: page.value,
      size: size.value,
      deviceId: selectedDeviceId.value,
      startDate: startDate.value || undefined,
      endDate: endDate.value || undefined,
    })
    changeLogs.value = res.data.data.content
    total.value = res.data.data.totalElements
  } catch {
    ElMessage.error('加载变更记录失败')
  } finally {
    loading.value = false
  }
}

const loadDevices = async () => {
  try {
    const res = await deviceApi.list({ page: 0, size: 100 })
    devices.value = res.data.data.content
  } catch {
    console.error('Failed to load devices')
  }
}

const handlePageChange = (p: number) => {
  page.value = p - 1
  loadChangeLogs()
}

const handleSizeChange = (s: number) => {
  size.value = s
  page.value = 0
  loadChangeLogs()
}

const handleSearch = () => {
  page.value = 0
  loadChangeLogs()
}

const getChangeTypeClass = (type: string) => {
  switch (type) {
    case '时段绑定': return 'type-bind'
    case '时段调整': return 'type-adjust'
    case '时段解绑': return 'type-unbind'
    default: return ''
  }
}

onMounted(() => {
  loadDevices()
  loadChangeLogs()
})
</script>

<template>
  <div class="page-container" style="margin-left: 220px;">
    <div class="page-header">
      <h2>变更记录</h2>
    </div>
    <div class="search-bar">
      <ElSelect
        v-model="selectedDeviceId"
        placeholder="选择设备"
        style="width: 200px; margin-right: 12px;"
        clearable
      >
        <ElOption v-for="d in devices" :key="d.id" :label="d.deviceCode + ' - ' + d.deviceType" :value="d.id" />
      </ElSelect>
      <ElDatePicker
        v-model="startDate"
        type="date"
        placeholder="开始日期"
        style="width: 160px; margin-right: 12px;"
      />
      <ElDatePicker
        v-model="endDate"
        type="date"
        placeholder="结束日期"
        style="width: 160px; margin-right: 12px;"
      />
      <ElButton type="primary" @click="handleSearch">搜索</ElButton>
    </div>
    <ElTable :data="changeLogs" :loading="loading" border style="width: 100%; margin-top: 16px;">
      <ElTableColumn prop="id" label="ID" width="60" />
      <ElTableColumn label="设备编号" width="160">
        <template #default="scope">
          {{ deviceMap[scope.row.deviceId]?.deviceCode || '-' }}
        </template>
      </ElTableColumn>
      <ElTableColumn label="设备类型" width="120">
        <template #default="scope">
          {{ deviceMap[scope.row.deviceId]?.deviceType || '-' }}
        </template>
      </ElTableColumn>
      <ElTableColumn prop="changeType" label="变更类型" width="100">
        <template #default="scope">
          <span :class="getChangeTypeClass(scope.row.changeType)">
            {{ scope.row.changeType }}
          </span>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="beforeValue" label="变更前" width="200">
        <template #default="scope">
          {{ scope.row.beforeValue || '-' }}
        </template>
      </ElTableColumn>
      <ElTableColumn prop="afterValue" label="变更后" width="200">
        <template #default="scope">
          {{ scope.row.afterValue || '-' }}
        </template>
      </ElTableColumn>
      <ElTableColumn prop="operator" label="操作人" width="100" />
      <ElTableColumn prop="changeTime" label="变更时间" width="180" />
    </ElTable>
    <div class="pagination" style="margin-top: 16px; text-align: right;">
      <ElPagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>
  </div>
</template>

<style scoped>
.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  font-size: 22px;
  color: #333;
}

.search-bar {
  display: flex;
  align-items: center;
}

.type-bind {
  color: #43A047;
  font-weight: bold;
}

.type-adjust {
  color: #FB8C00;
  font-weight: bold;
}

.type-unbind {
  color: #E53935;
  font-weight: bold;
}
</style>