<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElTable, ElTableColumn, ElButton, ElSelect, ElOption, ElPagination, ElDialog, ElMessage, ElTag } from 'element-plus'
import { useRouter } from 'vue-router'
import { timeSlotApi, deviceApi, peakCapacityApi } from '@/api'
import type { TimeSlot, Device, PeakWindow } from '@/types'

const router = useRouter()
const timeSlots = ref<TimeSlot[]>([])
const devices = ref<Device[]>([])
const peakWindows = ref<PeakWindow[]>([])
const loading = ref(false)
const page = ref(0)
const size = ref(10)
const total = ref(0)
const selectedDeviceId = ref<number | undefined>(undefined)
const showDeleteDialog = ref(false)
const deleteId = ref<number | null>(null)

const deviceMap = computed(() => {
  const map: Record<number, Device> = {}
  devices.value.forEach(d => { map[d.id] = d })
  return map
})

// 时段的每日时间范围是否与任一高峰窗相交（仅作展示，拦截以后端为准）
const overlapsPeak = (row: TimeSlot) => {
  const s = row.startTime?.slice(0, 5)
  const e = row.endTime?.slice(0, 5)
  if (!s || !e) return false
  return peakWindows.value.some(w => {
    const ws = w.startTime.slice(0, 5)
    const we = w.endTime.slice(0, 5)
    return s < we && ws < e
  })
}

const statusClass = (status: string) => {
  if (status === '生效中') return 'status-active'
  if (status === '已失效') return 'status-invalidated'
  return 'status-inactive'
}

const loadTimeSlots = async () => {
  loading.value = true
  try {
    const res = await timeSlotApi.list({
      page: page.value,
      size: size.value,
      deviceId: selectedDeviceId.value,
    })
    timeSlots.value = res.data.data.content
    total.value = res.data.data.totalElements
  } catch {
    ElMessage.error('加载时段绑定列表失败')
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

const loadPeakWindows = async () => {
  try {
    const res = await peakCapacityApi.windows()
    peakWindows.value = res.data.data
  } catch {
    console.error('Failed to load peak windows')
  }
}

const handlePageChange = (p: number) => {
  page.value = p - 1
  loadTimeSlots()
}

const handleSizeChange = (s: number) => {
  size.value = s
  page.value = 0
  loadTimeSlots()
}

const handleSearch = () => {
  page.value = 0
  loadTimeSlots()
}

const handleAdd = () => {
  router.push('/time-slots/allocate')
}

const handleEdit = (id: number) => {
  router.push(`/time-slots/edit/${id}`)
}

const handleDelete = (id: number) => {
  deleteId.value = id
  showDeleteDialog.value = true
}

const confirmDelete = async () => {
  if (deleteId.value === null) return
  try {
    await timeSlotApi.delete(deleteId.value)
    ElMessage.success('删除成功')
    showDeleteDialog.value = false
    loadTimeSlots()
  } catch {
    ElMessage.error('删除失败')
  }
}

onMounted(() => {
  loadDevices()
  loadPeakWindows()
  loadTimeSlots()
})
</script>

<template>
  <div class="page-container" style="margin-left: 220px;">
    <div class="page-header">
      <h2>时段绑定管理</h2>
      <ElButton type="primary" @click="handleAdd">分配时段</ElButton>
    </div>
    <div class="search-bar">
      <ElSelect
        v-model="selectedDeviceId"
        placeholder="选择设备"
        style="width: 200px; margin-right: 12px;"
        clearable
        @change="handleSearch"
      >
        <ElOption v-for="d in devices" :key="d.id" :label="d.deviceCode + ' - ' + d.deviceType" :value="d.id" />
      </ElSelect>
      <ElButton type="primary" @click="handleSearch">搜索</ElButton>
    </div>
    <ElTable :data="timeSlots" :loading="loading" border style="width: 100%; margin-top: 16px;">
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
      <ElTableColumn label="航站楼" width="120">
        <template #default="scope">
          {{ deviceMap[scope.row.deviceId]?.terminalArea || '-' }}
        </template>
      </ElTableColumn>
      <ElTableColumn label="日期范围" width="180">
        <template #default="scope">
          {{ scope.row.startDate }} ~ {{ scope.row.endDate }}
        </template>
      </ElTableColumn>
      <ElTableColumn label="时段" width="140">
        <template #default="scope">
          {{ scope.row.startTime?.slice(0, 5) }} - {{ scope.row.endTime?.slice(0, 5) }}
        </template>
      </ElTableColumn>
      <ElTableColumn label="高峰重叠" width="90">
        <template #default="scope">
          <ElTag v-if="overlapsPeak(scope.row as TimeSlot)" type="warning" size="small">高峰</ElTag>
          <span v-else>-</span>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="status" label="状态" width="80">
        <template #default="scope">
          <span :class="statusClass(scope.row.status)">
            {{ scope.row.status }}
          </span>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="createdAt" label="创建时间" width="180" />
      <ElTableColumn label="操作" width="160" fixed="right">
        <template #default="scope">
          <ElButton type="primary" size="small" @click="handleEdit(scope.row.id)">编辑</ElButton>
          <ElButton type="danger" size="small" @click="handleDelete(scope.row.id)">删除</ElButton>
        </template>
      </ElTableColumn>
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
    <ElDialog title="确认删除" v-model="showDeleteDialog" @close="showDeleteDialog = false">
      <p>确定要删除该时段绑定吗？此操作不可撤销。</p>
      <template #footer>
        <ElButton @click="showDeleteDialog = false">取消</ElButton>
        <ElButton type="danger" @click="confirmDelete">确认删除</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
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

.status-active {
  color: #43A047;
  font-weight: bold;
}

.status-inactive {
  color: #E53935;
  font-weight: bold;
}

.status-invalidated {
  color: #FB8C00;
  font-weight: bold;
}
</style>