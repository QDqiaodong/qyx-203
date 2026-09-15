<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElTable, ElTableColumn, ElButton, ElSelect, ElOption, ElPagination, ElDialog, ElMessage, ElCheckbox, ElAlert } from 'element-plus'
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

// 删除弹窗状态：普通确认 / 进行中占用被拦截
const showDeleteDialog = ref(false)
const deleteTarget = ref<Device | null>(null)
const blockedMessage = ref('')
const forceDelete = ref(false)
const deleting = ref(false)

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

const handleDelete = (row: Device) => {
  deleteTarget.value = row
  blockedMessage.value = ''
  forceDelete.value = false
  showDeleteDialog.value = true
}

const closeDeleteDialog = () => {
  if (deleting.value) return
  showDeleteDialog.value = false
  deleteTarget.value = null
  blockedMessage.value = ''
  forceDelete.value = false
}

const confirmDelete = async () => {
  if (deleteTarget.value === null || deleting.value) return
  deleting.value = true
  try {
    const res = await deviceApi.delete(deleteTarget.value.id, forceDelete.value)
    // 强制删除成功时后端返回带走了多少段占用
    ElMessage.success(res.data.message || '删除成功：设备与其名下时段已一并清除，未结束占用已置失效并写入变更记录')
    showDeleteDialog.value = false
    deleteTarget.value = null
    // 删掉的可能是当前页最后一条，退回上一页避免空页
    if (devices.value.length === 1 && page.value > 0) {
      page.value -= 1
    }
    loadDevices()
  } catch (e: any) {
    const code = e?.response?.data?.code
    const message = e?.response?.data?.message || '删除失败'
    if (code === 409) {
      // 有进行中的占用：留在弹窗里点名展示，等待用户确认强制删除
      blockedMessage.value = message
      forceDelete.value = false
    } else if (code === 404) {
      ElMessage.error('设备不存在或已被删除，列表即将刷新')
      showDeleteDialog.value = false
      loadDevices()
    } else {
      ElMessage.error(message)
    }
  } finally {
    deleting.value = false
  }
}

const statusClass = (status: string) => {
  if (status === '正常') return 'status-normal'
  if (status === '停用') return 'status-disabled'
  return 'status-abnormal'
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
          <span :class="statusClass((scope.row as Device).status)">
            {{ scope.row.status }}
          </span>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="createdAt" label="创建时间" width="180" />
      <ElTableColumn label="操作" width="160" fixed="right">
        <template #default="scope">
          <ElButton type="primary" size="small" @click="handleEdit(scope.row.id)">编辑</ElButton>
          <ElButton type="danger" size="small" @click="handleDelete(scope.row as Device)">删除</ElButton>
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

    <ElDialog
      :title="blockedMessage ? '删除被拦截：存在进行中的占用' : '确认删除'"
      v-model="showDeleteDialog"
      width="560px"
      @close="closeDeleteDialog"
    >
      <template v-if="!blockedMessage">
        <p>确定要删除设备【{{ deleteTarget?.deviceCode }}】吗？</p>
        <p class="delete-tip">
          删除会在同一事务内清掉该设备名下全部时段（不保留孤儿时段）；
          尚未结束的生效中占用会先置为「已失效」，本次停用/删除带走的占用会逐段写入变更记录。
        </p>
        <ElAlert
          v-if="deleteTarget?.status === '停用'"
          type="info"
          :closable="false"
          title="该设备已停用，其名下尚未结束的占用此前已随停用置为失效。"
          style="margin-top: 12px;"
        />
      </template>
      <template v-else>
        <ElAlert type="error" :closable="false" :title="blockedMessage" style="margin-bottom: 16px;" />
        <ElCheckbox v-model="forceDelete">
          我知晓以上占用正在进行中，仍要强制删除（先全部置为失效并写入变更，再删除设备与其名下时段）
        </ElCheckbox>
      </template>
      <template #footer>
        <ElButton :disabled="deleting" @click="closeDeleteDialog">取消</ElButton>
        <ElButton
          v-if="blockedMessage"
          type="danger"
          :loading="deleting"
          :disabled="!forceDelete"
          @click="confirmDelete"
        >强制删除</ElButton>
        <ElButton
          v-else
          type="danger"
          :loading="deleting"
          @click="confirmDelete"
        >确认删除</ElButton>
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
  color: #FB8C00;
  font-weight: bold;
}

.status-disabled {
  color: #E53935;
  font-weight: bold;
}

.delete-tip {
  color: #999;
  font-size: 13px;
  line-height: 1.6;
  margin-top: 8px;
}
</style>
