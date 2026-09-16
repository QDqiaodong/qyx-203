<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  ElTable, ElTableColumn, ElButton, ElDialog, ElForm, ElFormItem,
  ElSelect, ElOption, ElInput, ElRadioGroup, ElRadio, ElMessage, ElTag, ElAlert, ElPagination
} from 'element-plus'
import { calibrationApi, dutyRosterApi, deviceApi } from '@/api'
import type { CalibrationRecord, Device, DutyRosterEntry } from '@/types'

// ---------------- 当班可用名单 ----------------
const roster = ref<DutyRosterEntry[]>([])
// 今天全部名单行（含撤下）：挂入候选去重用，不受「只看当班可用」开关影响
const rosterAllToday = ref<Set<number>>(new Set())
const rosterLoading = ref(false)
const showRemoved = ref(false)

const loadRoster = async () => {
  rosterLoading.value = true
  try {
    const [shown, all] = await Promise.all([
      dutyRosterApi.list({ all: showRemoved.value }),
      dutyRosterApi.list({ all: true }),
    ])
    roster.value = shown.data.data
    rosterAllToday.value = new Set(all.data.data.map(r => r.deviceId))
  } catch {
    ElMessage.error('加载当班可用名单失败')
  } finally {
    rosterLoading.value = false
  }
}

// ---------------- 挂入当班名单 ----------------
const showRosterDialog = ref(false)
const guns = ref<Device[]>([])
const rosterForm = ref<{ deviceId: number | null; operator: string }>({
  deviceId: null,
  operator: '',
})

const loadGuns = async () => {
  try {
    // 体温枪数量不大，一次取全；只列体温枪
    const res = await deviceApi.list({ page: 0, size: 200, deviceType: '体温枪' })
    guns.value = res.data.data.content
  } catch {
    ElMessage.error('加载体温枪档案失败')
  }
}

// 已在今日名单（含撤下留痕）的枪不再作为可挂入候选；校准没过撤下的由后端 409 兜底
const addableGuns = computed(() => {
  return guns.value.filter(g => !rosterAllToday.value.has(g.id) && g.status !== '停用')
})

const openRosterDialog = async () => {
  await loadGuns()
  rosterForm.value = { deviceId: null, operator: '' }
  showRosterDialog.value = true
}

const submitRoster = async () => {
  if (!rosterForm.value.deviceId) {
    ElMessage.warning('请选择要列入当班可用名单的枪号')
    return
  }
  try {
    const res = await dutyRosterApi.add({
      deviceId: rosterForm.value.deviceId!,
      operator: rosterForm.value.operator || undefined,
    })
    ElMessage.success(res.data.message || '已列入当班可用名单')
    showRosterDialog.value = false
    loadRoster()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '挂入当班名单失败')
  }
}

const removeReason = ref('')
const removeTarget = ref<DutyRosterEntry | null>(null)
const showRemoveDialog = ref(false)
const openRemove = (row: DutyRosterEntry) => {
  removeTarget.value = row
  removeReason.value = ''
  showRemoveDialog.value = true
}
const confirmRemove = async () => {
  if (!removeTarget.value) return
  try {
    const res = await dutyRosterApi.remove(removeTarget.value.id, removeReason.value || undefined)
    ElMessage.success(res.data.message || '已撤下')
    showRemoveDialog.value = false
    loadRoster()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '撤下失败')
  }
}

// ---------------- 校准登记 ----------------
const calForm = ref<{ deviceId: number | null; calibrator: string; result: string; remark: string }>({
  deviceId: null,
  calibrator: '',
  result: '通过',
  remark: '',
})
const submitting = ref(false)

// 校准枪号候选：所有正常/停用的体温枪都能被选中登记（含名单外的枪）
const allGuns = computed(() => guns.value)

const resetCalForm = () => {
  calForm.value = { deviceId: null, calibrator: '', result: '通过', remark: '' }
}

const submitCalibration = async () => {
  if (!calForm.value.deviceId) {
    ElMessage.warning('请选择校准枪号')
    return
  }
  if (!calForm.value.calibrator.trim()) {
    ElMessage.warning('请填写校准人')
    return
  }
  if (submitting.value) return
  submitting.value = true
  try {
    const res = await calibrationApi.create({
      deviceId: calForm.value.deviceId,
      calibrator: calForm.value.calibrator.trim(),
      result: calForm.value.result,
      remark: calForm.value.remark.trim() || undefined,
    })
    ElMessage.success(res.data.message || '校准结论已登记')
    resetCalForm()
    loadRoster()
    loadLedger()
  } catch (e: any) {
    const code = e?.response?.data?.code
    const message = e?.response?.data?.message || '校准登记失败'
    if (code === 409) {
      // 两名值机抢写：先落地的为准，后到的结论没有落库，表单原样保留以便核对
      ElMessage.error(message)
    } else {
      ElMessage.error(message)
    }
  } finally {
    submitting.value = false
  }
}

// ---------------- 校准台账 ----------------
const ledger = ref<CalibrationRecord[]>([])
const ledgerLoading = ref(false)
const ledgerPage = ref(0)
const ledgerSize = ref(10)
const ledgerTotal = ref(0)
const filterDeviceId = ref<number | undefined>(undefined)

const loadLedger = async () => {
  ledgerLoading.value = true
  try {
    const res = await calibrationApi.list({
      page: ledgerPage.value,
      size: ledgerSize.value,
      deviceId: filterDeviceId.value || undefined,
    })
    ledger.value = res.data.data.content
    ledgerTotal.value = res.data.data.totalElements
  } catch {
    ElMessage.error('加载校准台账失败')
  } finally {
    ledgerLoading.value = false
  }
}

const onLedgerPage = (p: number) => {
  ledgerPage.value = p - 1
  loadLedger()
}
const onLedgerSize = (s: number) => {
  ledgerSize.value = s
  ledgerPage.value = 0
  loadLedger()
}
const searchLedger = () => {
  ledgerPage.value = 0
  loadLedger()
}

onMounted(() => {
  loadGuns()
  loadRoster()
  loadLedger()
})
</script>

<template>
  <div class="page-container" style="margin-left: 220px;">
    <div class="page-header">
      <h2>体温枪校准登记</h2>
    </div>

    <ElAlert type="info" :closable="false" style="margin-bottom: 16px;">
      <template #title>
        选中枪号、写下校准人、给出「通过 / 不通过」结论。校准不通过：当班可用名单里这把枪当场撤下，
        撤下原因写明就是本次校准没过，且本当班日内不能再列入；校准通过：名单上继续留用。
        同一把枪每个当班日只认<b>先落地</b>的那份结论，后到的登记会被拒绝、不能改先写的结论。
      </template>
    </ElAlert>

    <!-- 当班可用名单 -->
    <div class="section-header">
      <h3>当班可用名单</h3>
      <div>
        <ElRadioGroup v-model="showRemoved" size="small" @change="loadRoster" style="margin-right: 12px;">
          <ElRadio :value="false">只看当班可用</ElRadio>
          <ElRadio :value="true">含已撤下留痕</ElRadio>
        </ElRadioGroup>
        <ElButton type="primary" @click="openRosterDialog">列入当班名单</ElButton>
      </div>
    </div>
    <ElTable :data="roster" :loading="rosterLoading" border style="width: 100%;">
      <ElTableColumn prop="deviceCode" label="枪号" width="160" />
      <ElTableColumn prop="dutyDate" label="当班日" width="140" />
      <ElTableColumn label="名单状态" width="120">
        <template #default="scope">
          <ElTag :type="scope.row.status === '当班可用' ? 'success' : 'danger'">
            {{ scope.row.status }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="removeReason" label="撤下原因" min-width="260">
        <template #default="scope">
          <span v-if="scope.row.removeReason" :class="{ 'cal-fail': String(scope.row.removeReason).includes('校准') }">
            {{ scope.row.removeReason }}
          </span>
          <span v-else class="muted">—</span>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="createdAt" label="挂入时间" width="180" />
      <ElTableColumn label="操作" width="120" fixed="right">
        <template #default="scope">
          <ElButton
            v-if="scope.row.status === '当班可用'"
            type="danger"
            size="small"
            @click="openRemove(scope.row as DutyRosterEntry)"
          >撤下</ElButton>
        </template>
      </ElTableColumn>
      <template #empty>
        <span>今天的当班可用名单为空，点「列入当班名单」挂入体温枪。</span>
      </template>
    </ElTable>

    <!-- 校准登记表单 -->
    <div class="section-header" style="margin-top: 28px;">
      <h3>登记校准结论</h3>
    </div>
    <ElForm :model="calForm" label-width="100px" class="cal-form" @submit.prevent>
      <ElFormItem label="校准枪号" required>
        <ElSelect v-model="calForm.deviceId" filterable placeholder="请选择体温枪枪号" style="width: 320px;">
          <ElOption
            v-for="g in allGuns"
            :key="g.id"
            :label="`${g.deviceCode}（${g.terminalArea}）`"
            :value="g.id"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="校准人" required>
        <ElInput v-model="calForm.calibrator" maxlength="50" placeholder="当班值机姓名" style="width: 320px;" />
      </ElFormItem>
      <ElFormItem label="校准结论" required>
        <ElRadioGroup v-model="calForm.result">
          <ElRadio value="通过">通过</ElRadio>
          <ElRadio value="不通过">不通过</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem label="备注">
        <ElInput
          v-model="calForm.remark"
          type="textarea"
          :rows="2"
          maxlength="500"
          placeholder="不通过时可填写偏差情况（选填）"
          style="width: 420px;"
        />
      </ElFormItem>
      <ElFormItem v-if="calForm.result === '不通过'" label=" ">
        <ElAlert type="warning" :closable="false" style="width: 420px;"
          title="提交后该枪若在当班可用名单中，会在同一操作内当场撤下；本当班日内不能再列入。" />
      </ElFormItem>
      <ElFormItem>
        <ElButton type="primary" :loading="submitting" @click="submitCalibration">提交校准结论</ElButton>
      </ElFormItem>
    </ElForm>

    <!-- 校准登记台账 -->
    <div class="section-header" style="margin-top: 28px;">
      <h3>校准登记台账</h3>
    </div>
    <div class="filter-bar">
      <ElSelect
        v-model="filterDeviceId"
        filterable
        clearable
        placeholder="按枪号筛选"
        style="width: 240px; margin-right: 12px;"
        @change="searchLedger"
      >
        <ElOption v-for="g in allGuns" :key="g.id" :label="g.deviceCode" :value="g.id" />
      </ElSelect>
      <ElButton type="primary" @click="searchLedger">查询</ElButton>
    </div>
    <ElTable :data="ledger" :loading="ledgerLoading" border style="width: 100%; margin-top: 12px;">
      <ElTableColumn prop="id" label="ID" width="70" />
      <ElTableColumn prop="deviceCode" label="枪号" width="150" />
      <ElTableColumn prop="calibrator" label="校准人" width="120" />
      <ElTableColumn label="结论" width="110">
        <template #default="scope">
          <ElTag :type="scope.row.result === '通过' ? 'success' : 'danger'">
            {{ scope.row.result }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="dutyDate" label="当班日" width="130" />
      <ElTableColumn prop="remark" label="备注" min-width="180">
        <template #default="scope">{{ scope.row.remark || '—' }}</template>
      </ElTableColumn>
      <ElTableColumn prop="createdAt" label="登记时间" width="180" />
      <template #empty>
        <span>还没有校准登记记录。</span>
      </template>
    </ElTable>
    <div style="margin-top: 16px; text-align: right;">
      <ElPagination
        v-model:current-page="ledgerPage"
        v-model:page-size="ledgerSize"
        :total="ledgerTotal"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="onLedgerPage"
        @size-change="onLedgerSize"
      />
    </div>

    <!-- 列入当班名单弹窗 -->
    <ElDialog title="列入当班可用名单" v-model="showRosterDialog" width="480px">
      <ElForm :model="rosterForm" label-width="90px">
        <ElFormItem label="枪号" required>
          <ElSelect v-model="rosterForm.deviceId" filterable placeholder="请选择体温枪" style="width: 100%;">
            <ElOption
              v-for="g in addableGuns"
              :key="g.id"
              :label="`${g.deviceCode}（${g.terminalArea}）`"
              :value="g.id"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="操作人">
          <ElInput v-model="rosterForm.operator" maxlength="50" placeholder="当班值机（选填）" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="showRosterDialog = false">取消</ElButton>
        <ElButton type="primary" @click="submitRoster">列入</ElButton>
      </template>
    </ElDialog>

    <!-- 手工撤下弹窗 -->
    <ElDialog title="从当班可用名单撤下" v-model="showRemoveDialog" width="480px">
      <p>确定把枪【{{ removeTarget?.deviceCode }}】从当班可用名单撤下吗？</p>
      <ElInput
        v-model="removeReason"
        type="textarea"
        :rows="2"
        maxlength="200"
        placeholder="撤下原因（选填）"
        style="margin-top: 12px;"
      />
      <template #footer>
        <ElButton @click="showRemoveDialog = false">取消</ElButton>
        <ElButton type="danger" @click="confirmRemove">确认撤下</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.page-header h2 {
  font-size: 22px;
  color: #333;
  margin-bottom: 16px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.section-header h3 {
  font-size: 17px;
  color: #333;
}

.cal-form {
  background: #fafafa;
  border: 1px solid #eee;
  border-radius: 6px;
  padding: 20px 12px 0;
}

.filter-bar {
  display: flex;
  align-items: center;
}

.cal-fail {
  color: #E53935;
  font-weight: bold;
}

.muted {
  color: #aaa;
}
</style>
