<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  ElTable, ElTableColumn, ElButton, ElDialog, ElForm, ElFormItem,
  ElSelect, ElOption, ElInputNumber, ElMessage, ElTag, ElAlert
} from 'element-plus'
import { peakCapacityApi, categoryApi } from '@/api'
import type { PeakWindow, PeakCapacityLimit } from '@/types'

const windows = ref<PeakWindow[]>([])
const limits = ref<PeakCapacityLimit[]>([])
const loading = ref(false)
const terminalAreas = ref<string[]>([])
const deviceTypes = ref<string[]>([])

const showDialog = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const form = ref({
  terminalArea: '',
  deviceType: '',
  maxConcurrent: 1,
})

const showDeleteDialog = ref(false)
const deleteId = ref<number | null>(null)

const fmtTime = (t: string) => (t ? t.slice(0, 5) : '')

const loadWindows = async () => {
  try {
    const res = await peakCapacityApi.windows()
    windows.value = res.data.data
  } catch {
    console.error('Failed to load peak windows')
  }
}

const loadLimits = async () => {
  loading.value = true
  try {
    const res = await peakCapacityApi.list()
    limits.value = res.data.data
  } catch {
    ElMessage.error('加载高峰上限配置失败')
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

const handleAdd = () => {
  isEdit.value = false
  editId.value = null
  form.value = { terminalArea: '', deviceType: '', maxConcurrent: 1 }
  showDialog.value = true
}

const handleEdit = (row: PeakCapacityLimit) => {
  isEdit.value = true
  editId.value = row.id
  form.value = {
    terminalArea: row.terminalArea,
    deviceType: row.deviceType,
    maxConcurrent: row.maxConcurrent,
  }
  showDialog.value = true
}

const handleSubmit = async () => {
  if (!form.value.terminalArea || !form.value.deviceType) {
    ElMessage.warning('请选择航站楼分区和设备类型')
    return
  }
  try {
    const res = isEdit.value && editId.value !== null
      ? await peakCapacityApi.update(editId.value, form.value)
      : await peakCapacityApi.create(form.value)
    if (res.data.code !== 200) {
      ElMessage.error(res.data.message || '保存失败')
      return
    }
    ElMessage.success('保存成功')
    showDialog.value = false
    loadLimits()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  }
}

const handleDelete = (id: number) => {
  deleteId.value = id
  showDeleteDialog.value = true
}

const confirmDelete = async () => {
  if (deleteId.value === null) return
  try {
    await peakCapacityApi.delete(deleteId.value)
    ElMessage.success('删除成功')
    showDeleteDialog.value = false
    loadLimits()
  } catch {
    ElMessage.error('删除失败')
  }
}

onMounted(() => {
  loadWindows()
  loadLimits()
  loadCategories()
})
</script>

<template>
  <div class="page-container" style="margin-left: 220px;">
    <div class="page-header">
      <h2>高峰同时在用上限</h2>
      <ElButton type="primary" @click="handleAdd">新增上限配置</ElButton>
    </div>
    <ElAlert type="info" :closable="false" class="window-banner">
      <template #title>
        运营高峰窗：
        <span v-for="(w, i) in windows" :key="w.id">
          <b>{{ w.name }}</b> {{ fmtTime(w.startTime) }}-{{ fmtTime(w.endTime) }}<span v-if="i < windows.length - 1">；</span>
        </span>
        。高峰窗内同一分区同一类型设备的「生效中」时段叠加后，同时在用数不得超过上限。
      </template>
    </ElAlert>
    <ElTable :data="limits" :loading="loading" border style="width: 100%; margin-top: 16px;">
      <ElTableColumn prop="terminalArea" label="航站楼分区" width="160" />
      <ElTableColumn prop="deviceType" label="设备类型" width="140" />
      <ElTableColumn prop="maxConcurrent" label="高峰同时在用上限" width="160">
        <template #default="scope">
          {{ scope.row.maxConcurrent }} 台
        </template>
      </ElTableColumn>
      <ElTableColumn label="今日高峰同时在用" width="160">
        <template #default="scope">
          <ElTag :type="scope.row.currentUsage >= scope.row.maxConcurrent ? 'danger' : 'success'">
            {{ scope.row.currentUsage }} 台
          </ElTag>
          <span v-if="scope.row.currentUsage >= scope.row.maxConcurrent" class="full-tag">已满</span>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="updatedAt" label="更新时间" width="180" />
      <ElTableColumn label="操作" width="160" fixed="right">
        <template #default="scope">
          <ElButton type="primary" size="small" @click="handleEdit(scope.row as PeakCapacityLimit)">编辑</ElButton>
          <ElButton type="danger" size="small" @click="handleDelete(scope.row.id)">删除</ElButton>
        </template>
      </ElTableColumn>
      <template #empty>
        <span>暂未配置上限。未配置的分区+类型在高峰窗内不限制同时在用数。</span>
      </template>
    </ElTable>

    <ElDialog :title="isEdit ? '编辑高峰上限' : '新增高峰上限'" v-model="showDialog" width="480px">
      <ElForm :model="form" label-width="140px">
        <ElFormItem label="航站楼分区" required>
          <ElSelect v-model="form.terminalArea" placeholder="请选择航站楼分区" style="width: 100%;">
            <ElOption v-for="area in terminalAreas" :key="area" :label="area" :value="area" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="设备类型" required>
          <ElSelect v-model="form.deviceType" placeholder="请选择设备类型" style="width: 100%;">
            <ElOption v-for="type in deviceTypes" :key="type" :label="type" :value="type" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="高峰同时在用上限" required>
          <ElInputNumber v-model="form.maxConcurrent" :min="0" :max="999" style="width: 100%;" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="showDialog = false">取消</ElButton>
        <ElButton type="primary" @click="handleSubmit">保存</ElButton>
      </template>
    </ElDialog>

    <ElDialog title="确认删除" v-model="showDeleteDialog" @close="showDeleteDialog = false">
      <p>确定要删除该上限配置吗？删除后该分区该类型在高峰窗内不再限制同时在用数。</p>
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

.window-banner {
  margin-bottom: 4px;
}

.full-tag {
  margin-left: 8px;
  color: #E53935;
  font-weight: bold;
}
</style>
