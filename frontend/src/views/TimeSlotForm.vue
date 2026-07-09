<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElForm, ElFormItem, ElSelect, ElOption, ElDatePicker, ElTimePicker, ElButton, ElMessage } from 'element-plus'
import { useRouter, useRoute } from 'vue-router'
import { timeSlotApi, deviceApi } from '@/api'
import type { Device } from '@/types'

const router = useRouter()
const route = useRoute()
const isEdit = ref(false)
const timeSlotId = ref<number | null>(null)

const form = ref({
  deviceId: 0,
  startTime: '',
  endTime: '',
  startDate: '',
  endDate: '',
  status: '生效中',
})

const devices = ref<Device[]>([])

const loadDevices = async () => {
  try {
    const res = await deviceApi.list({ page: 0, size: 100 })
    devices.value = res.data.data.content
  } catch {
    console.error('Failed to load devices')
  }
}

const loadTimeSlot = async () => {
  const id = route.params.id
  if (id) {
    isEdit.value = true
    timeSlotId.value = Number(id)
    try {
      const res = await timeSlotApi.get(timeSlotId.value)
      form.value = {
        deviceId: res.data.data.deviceId,
        startTime: res.data.data.startTime,
        endTime: res.data.data.endTime,
        startDate: res.data.data.startDate,
        endDate: res.data.data.endDate,
        status: res.data.data.status,
      }
    } catch {
      ElMessage.error('加载时段绑定信息失败')
    }
  }
}

const handleSubmit = async () => {
  try {
    if (isEdit.value && timeSlotId.value) {
      await timeSlotApi.update(timeSlotId.value, form.value)
      ElMessage.success('更新成功')
    } else {
      await timeSlotApi.create(form.value)
      ElMessage.success('创建成功')
    }
    router.push('/time-slots')
  } catch {
    ElMessage.error(isEdit.value ? '更新失败' : '创建失败')
  }
}

const handleCancel = () => {
  router.push('/time-slots')
}

onMounted(() => {
  loadDevices()
  loadTimeSlot()
})
</script>

<template>
  <div class="page-container" style="margin-left: 220px;">
    <div class="page-header">
      <h2>{{ isEdit ? '编辑时段绑定' : '分配时段' }}</h2>
    </div>
    <div class="form-container">
      <ElForm :model="form" label-width="120px" style="max-width: 600px;">
        <ElFormItem label="选择设备" required>
          <ElSelect v-model="form.deviceId" placeholder="请选择设备" style="width: 100%;">
            <ElOption v-for="d in devices" :key="d.id" :label="d.deviceCode + ' - ' + d.deviceType + ' (' + d.terminalArea + ')'" :value="d.id" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="日期范围" required>
          <ElDatePicker
            v-model="form.startDate"
            type="date"
            placeholder="开始日期"
            style="width: 48%; margin-right: 4%;"
          />
          <span style="margin-right: 4%;">~</span>
          <ElDatePicker
            v-model="form.endDate"
            type="date"
            placeholder="结束日期"
            style="width: 48%;"
          />
        </ElFormItem>
        <ElFormItem label="时段" required>
          <ElTimePicker
            v-model="form.startTime"
            format="HH:mm"
            value-format="HH:mm"
            placeholder="开始时间"
            style="width: 48%; margin-right: 4%;"
          />
          <span style="margin-right: 4%;">-</span>
          <ElTimePicker
            v-model="form.endTime"
            format="HH:mm"
            value-format="HH:mm"
            placeholder="结束时间"
            style="width: 48%;"
          />
        </ElFormItem>
        <ElFormItem label="状态">
          <ElSelect v-model="form.status" style="width: 100%;">
            <ElOption label="生效中" value="生效中" />
            <ElOption label="已停用" value="已停用" />
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