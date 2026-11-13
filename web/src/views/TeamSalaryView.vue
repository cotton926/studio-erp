<template>
  <div class="page">
    <div class="toolbar">
      <el-date-picker
        v-model="period"
        type="month"
        value-format="YYYY-MM"
        :clearable="false"
        style="width: 150px"
        @change="load"
      />
      <el-input
        v-model="keyword"
        placeholder="姓名 / 工号"
        clearable
        style="width: 180px"
        @keyup.enter="load"
        @clear="load"
      />
      <el-button :loading="loading" @click="load">查询</el-button>
      <div class="spacer"></div>
      <span class="muted">按应发降序；底薪按级别带出，个别特例可单独指定</span>
      <el-button :loading="exporting" @click="doExport">导出工资表</el-button>
    </div>

    <div class="card">
      <el-table v-loading="loading" :data="rows" size="small">
        <el-table-column prop="empNo" label="工号" width="100">
          <template #default="{ row }"><span class="mono">{{ row.empNo }}</span></template>
        </el-table-column>
        <el-table-column prop="employeeName" label="姓名" width="100" />
        <el-table-column prop="deptName" label="部门" width="100" />
        <el-table-column label="级别" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" type="info">{{ row.empLevelLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="本月业绩" width="120" align="right">
          <template #default="{ row }"><span class="cell-amount">{{ money(row.performance) }}</span></template>
        </el-table-column>
        <el-table-column label="比例" width="80" align="right">
          <template #default="{ row }">{{ row.rateText }}</template>
        </el-table-column>
        <el-table-column label="提成" width="120" align="right">
          <template #default="{ row }"><span class="cell-amount">{{ money(row.commission) }}</span></template>
        </el-table-column>
        <el-table-column label="底薪" width="150" align="right">
          <template #default="{ row }">
            <div class="cell-amount">{{ money(row.baseSalary) }}</div>
            <div class="muted" style="font-size: 11px">{{ row.baseSalarySourceLabel }}</div>
          </template>
        </el-table-column>
        <el-table-column label="应发" width="130" align="right">
          <template #default="{ row }">
            <span class="cell-amount strong text-blue">{{ money(row.payable) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openBase(row)">底薪</el-button>
          </template>
        </el-table-column>
        <template #empty><div class="empty">没有数据</div></template>
      </el-table>
    </div>

    <el-dialog v-model="baseVisible" title="调整底薪" width="420px">
      <p class="dialog-tip">
        {{ current?.employeeName }}（{{ current?.empLevelLabel }}）当前底薪
        <b>{{ money(current?.baseSalary) }}</b> 元，来源：{{ current?.baseSalarySourceLabel }}
      </p>
      <el-radio-group v-model="baseMode" style="margin-bottom: 12px">
        <el-radio value="level">按级别（跟随政策）</el-radio>
        <el-radio value="personal">单独指定</el-radio>
      </el-radio-group>
      <el-input-number
        v-if="baseMode === 'personal'"
        v-model="baseAmount"
        :min="0"
        :precision="2"
        style="width: 100%"
      />
      <p class="dialog-tip" v-else>
        将清除该员工的个人底薪，自动回到 {{ current?.empLevelLabel }} 对应的政策金额。
      </p>
      <template #footer>
        <el-button @click="baseVisible = false">取消</el-button>
        <el-button type="primary" :loading="baseSubmitting" @click="submitBase">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getTeamSalary, updateBaseSalary, type SalaryVO } from '@/api/salary'
import { exportSalary } from '@/api/report'

const period = ref(currentMonth())
const keyword = ref('')
const rows = ref<SalaryVO[]>([])
const loading = ref(false)
const exporting = ref(false)

const baseVisible = ref(false)
const baseSubmitting = ref(false)
const current = ref<SalaryVO | null>(null)
const baseMode = ref<'level' | 'personal'>('level')
const baseAmount = ref(0)

function currentMonth() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

function money(n?: number) {
  if (n === undefined || n === null) return '0.00'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function load() {
  loading.value = true
  try {
    rows.value = await getTeamSalary(period.value, keyword.value || undefined)
  } finally {
    loading.value = false
  }
}

function openBase(row: SalaryVO) {
  current.value = row
  baseMode.value = row.baseSalarySource === 'PERSONAL' ? 'personal' : 'level'
  baseAmount.value = row.baseSalary || 0
  baseVisible.value = true
}

async function submitBase() {
  if (!current.value?.employeeId) return
  baseSubmitting.value = true
  try {
    // 传 null = 清除个人特例，回到级别政策。这是后端约定的语义，不是"没填"
    await updateBaseSalary(current.value.employeeId, baseMode.value === 'personal' ? baseAmount.value : null)
    ElMessage.success('底薪已更新')
    baseVisible.value = false
    load()
  } finally {
    baseSubmitting.value = false
  }
}

async function doExport() {
  exporting.value = true
  try {
    await exportSalary(period.value, keyword.value || undefined)
  } finally {
    exporting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.empty {
  padding: 24px;
  color: var(--t3);
  font-size: 12px;
}

.dialog-tip {
  font-size: 12px;
  color: var(--t2);
  line-height: 1.7;
}
</style>
