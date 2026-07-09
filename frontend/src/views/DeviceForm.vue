<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElForm, ElFormItem, ElInput, ElSelect, ElOption, ElButton, ElMessage } from 'element-plus'
import { useRouter, useRoute } from 'vue-router'
import { deviceApi, categoryApi } from '@/api'

const router = useRouter()
const route = useRoute()
const isEdit = ref(false)
const deviceId = ref<number | null>(null)

const form = ref({
  deviceCode: '',
  deviceType: '',
  terminalArea: '',
  status: '正常',
})

const terminalAreas = ref<string[]>([])
const deviceTypes = ref<string[]>([])

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

const loadDevice = async () => {
  const id = route.params.id
  if (id) {
    isEdit.value = true
    deviceId.value = Number(id)
    try {
      const res = await deviceApi.get(deviceId.value)
      form.value = {
        deviceCode: res.data.data.deviceCode,
        deviceType: res.data.data.deviceType,
        terminalArea: res.data.data.terminalArea,
        status: res.data.data.status,
      }
    } catch {
      ElMessage.error('加载设备信息失败')
    }
  }
}

const handleSubmit = async () => {
  try {
    if (isEdit.value && deviceId.value) {
      await deviceApi.update(deviceId.value, form.value)
      ElMessage.success('更新成功')
    } else {
      await deviceApi.create(form.value)
      ElMessage.success('创建成功')
    }
    router.push('/devices')
  } catch {
    ElMessage.error(isEdit.value ? '更新失败' : '创建失败')
  }
}

const handleCancel = () => {
  router.push('/devices')
}

onMounted(() => {
  loadCategories()
  loadDevice()
})
</script>

<template>
  <div class="page-container" style="margin-left: 220px;">
    <div class="page-header">
      <h2>{{ isEdit ? '编辑设备' : '新增设备' }}</h2>
    </div>
    <div class="form-container">
      <ElForm :model="form" label-width="120px" style="max-width: 600px;">
        <ElFormItem label="设备编号" required>
          <ElInput v-model="form.deviceCode" placeholder="请输入设备编号" />
        </ElFormItem>
        <ElFormItem label="设备类型" required>
          <ElSelect v-model="form.deviceType" placeholder="请选择设备类型" style="width: 100%;">
            <ElOption v-for="type in deviceTypes" :key="type" :label="type" :value="type" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="航站楼分区" required>
          <ElSelect v-model="form.terminalArea" placeholder="请选择航站楼分区" style="width: 100%;">
            <ElOption v-for="area in terminalAreas" :key="area" :label="area" :value="area" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="状态">
          <ElSelect v-model="form.status" style="width: 100%;">
            <ElOption label="正常" value="正常" />
            <ElOption label="维护中" value="维护中" />
            <ElOption label="停用" value="停用" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="handleSubmit">保存</ElButton>
          <ElButton @click="handleCancel">取消</ElButton>
        </ElFormItem>
      </ElForm>
    </div>
  </div>
</template>

<style scoped>
.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 22px;
  color: #333;
}

.form-container {
  background: #fff;
  padding: 32px;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.08);
}
</style>