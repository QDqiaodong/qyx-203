<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElTable, ElTableColumn, ElButton, ElSelect, ElOption, ElPagination, ElDialog, ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { deviceApi, categoryApi } from '@/api'
import type { Device } from '@/types'

const router = useRouter()
const devices = ref<Device[]>([])
const loading = ref(false)
const page = ref(0)
const size = ref(10)
const total = ref(0)
const terminalArea = ref('')
const deviceType = ref('')
const terminalAreas = ref<string[]>([])
const deviceTypes = ref<string[]>([])
const showDeleteDialog = ref(false)
const deleteId = ref<number | null>(null)

const loadDevices = async () => {
  loading.value = true
  try {
    const res = await deviceApi.list({
      page: page.value,
      size: size.value,
      terminalArea: terminalArea.value || undefined,
      deviceType: deviceType.value || undefined,
    })
    devices.value = res.data.data.content
    total.value = res.data.data.totalElements
  } catch (e) {
    ElMessage.error('加载设备列表失败')
  } finally {
    loading.value = false
  }
}

const loadCategories = async () => {
  try {
    const [areasRes, typesRes] = await Promise.all([
      categoryApi.getTerminalAreas(),
      categoryApi.getDeviceTypes(),
    ])
    terminalAreas.value = areasRes.data.data
    deviceTypes.value = typesRes.data.data
  } catch {
    console.error('Failed to load categories')
  }
}

const handlePageChange = (p: number) => {
  page.value = p - 1
  loadDevices()
}

const handleSizeChange = (s: number) => {
  size.value = s
  page.value = 0
  loadDevices()
}

const handleSearch = () => {
  page.value = 0
  loadDevices()
}

const handleAdd = () => {
  router.push('/devices/add')
}

const handleEdit = (id: number) => {
  router.push(`/devices/edit/${id}`)
}

const handleDelete = (id: number) => {
  deleteId.value = id
  showDeleteDialog.value = true
}

const confirmDelete = async () => {
  if (deleteId.value === null) return
  try {
    await deviceApi.delete(deleteId.value)
    ElMessage.success('删除成功')
    showDeleteDialog.value = false
    loadDevices()
  } catch {
    ElMessage.error('删除失败')
  }
}

onMounted(() => {
  loadCategories()
  loadDevices()
})
</script>

<template>
  <div class="page-container" style="margin-left: 220px;">
    <div class="page-header">
      <h2>设备管理</h2>
      <ElButton type="primary" @click="handleAdd">新增设备</ElButton>
    </div>
    <div class="search-bar">
      <ElSelect
        v-model="terminalArea"
        placeholder="航站楼分区"
        style="width: 160px; margin-right: 12px;"
        clearable
        @change="handleSearch"
      >
        <ElOption v-for="area in terminalAreas" :key="area" :label="area" :value="area" />
      </ElSelect>
      <ElSelect
        v-model="deviceType"
        placeholder="设备类型"
        style="width: 140px; margin-right: 12px;"
        clearable
        @change="handleSearch"
      >
        <ElOption v-for="type in deviceTypes" :key="type" :label="type" :value="type" />
      </ElSelect>
      <ElButton type="primary" @click="handleSearch">搜索</ElButton>
    </div>
    <ElTable :data="devices" :loading="loading" border style="width: 100%; margin-top: 16px;">
      <ElTableColumn prop="id" label="ID" width="60" />
      <ElTableColumn prop="deviceCode" label="设备编号" width="140" />
      <ElTableColumn prop="deviceType" label="设备类型" width="120" />
      <ElTableColumn prop="terminalArea" label="航站楼分区" width="140" />
      <ElTableColumn prop="status" label="状态" width="80">
        <template #default="scope">
          <span :class="scope.row.status === '正常' ? 'status-normal' : 'status-abnormal'">
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
      <p>确定要删除该设备吗？此操作不可撤销。</p>
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

.status-normal {
  color: #43A047;
  font-weight: bold;
}

.status-abnormal {
  color: #E53935;
  font-weight: bold;
}
</style>