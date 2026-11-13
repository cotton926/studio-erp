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
      <el-tag v-if="salary?.empLevelLabel" type="primary" effect="plain">{{ salary.empLevelLabel }}</el-tag>
      <el-tag v-if="salary?.tierModeLabel" type="info" effect="plain">{{ salary.tierModeLabel }}</el-tag>
      <span class="muted">只按已到账回款核算，签单额不参与提成</span>
      <div class="spacer"></div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="kpis" v-loading="loading">
      <div class="kpi hl">
        <div class="k">本月业绩</div>
        <div class="v">{{ int(salary?.performance) }}<small>元</small></div>
        <div class="s">到账回款按分成确认</div>
      </div>
      <div class="kpi">
        <div class="k">命中比例</div>
        <div class="v">{{ (salary?.rateBp ?? 0) / 100 }}<small>%</small></div>
        <div class="s">{{ salary?.tierRemark || '—' }}</div>
      </div>
      <div class="kpi">
        <div class="k">提成</div>
        <div class="v">{{ money(salary?.commission) }}<small>元</small></div>
        <div class="s">业绩 {{ money(salary?.performance) }} × {{ (salary?.rateBp ?? 0) / 100 }}%</div>
      </div>
      <div class="kpi">
        <div class="k">应发工资</div>
        <div class="v">{{ money(salary?.payable) }}<small>元</small></div>
        <div class="s">{{ salary?.baseSalaryNote || '底薪 + 提成' }}</div>
      </div>
    </div>

    <div class="grid2">
      <div class="col">
        <div class="card">
          <div class="card-h">
            <h2>工资构成</h2>
            <span class="hint">算错一位就要重新发工资，所以每一行都能对上</span>
          </div>
          <el-table :data="breakdownRows" size="small" :show-header="true">
            <el-table-column prop="label" label="项目" width="100" />
            <el-table-column prop="formula" label="计算方式" />
            <el-table-column label="金额" width="130" align="right">
              <template #default="{ row }">
                <span class="cell-amount" :class="row.strong ? 'text-blue strong' : ''">{{ money(row.amount) }}</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="card-b" style="padding-top: 0">
            <el-alert
              v-if="salary?.clawback"
              type="warning"
              :closable="false"
              show-icon
              title="本月业绩为负（退款或冲销大于新增回款），提成按最低档比例扣回，会在后续月份补回"
            />
            <div v-else class="callout">
              本月签单额 <b>{{ money(salary?.signAmount) }} 元</b>，业绩 <b>{{ money(salary?.performance) }} 元</b>。
              两者不同是正常的：业绩只认已到账的钱，未收到的尾款要等下月收到才计入下月业绩。
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-h">
            <h2>提成阶梯</h2>
            <span class="hint">命中哪一档，整笔业绩都按该档比例</span>
          </div>
          <el-table :data="tiers" size="small" :row-class-name="tierRowClass">
            <el-table-column label="档位" width="80">
              <template #default="{ row }">第 {{ row.levelNo }} 档</template>
            </el-table-column>
            <el-table-column label="月业绩区间">
              <template #default="{ row }">
                <span class="mono">
                  {{ int(row.minAmount) }} ~ {{ row.maxAmount ? int(row.maxAmount) : '以上' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="rateText" label="比例" width="80" align="right" />
            <el-table-column label="" width="90" align="center">
              <template #default="{ row }">
                <el-tag v-if="isCurrentTier(row)" size="small" type="primary" effect="plain">当前</el-tag>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="card-b" v-if="salary?.nextTierAt">
            <div class="progress-head">
              <span>距离下一档（{{ int(salary.nextTierAt) }} 元）</span>
              <span>
                <b class="text-blue">{{ money(salary.performance) }}</b> / {{ int(salary.nextTierAt) }}
              </span>
            </div>
            <el-progress
              :percentage="progressPct"
              :show-text="false"
              :stroke-width="8"
              color="#185fa5"
            />
            <p class="note">
              再做 <b class="text-blue">{{ money(salary.gapToNextTier) }} 元</b>回款业绩就能进下一档，
              提成变为 <b>{{ money((salary.commission || 0) + (salary.gainIfReachNext || 0)) }}</b>
              <template v-if="salary.gainIfReachNext">
                ，<b class="text-green">多拿 {{ money(salary.gainIfReachNext) }} 元</b>
              </template>。
              跳点算法下升档是一整笔跳，所以这一下会比平时高一截，不是算错。
            </p>
          </div>
        </div>
      </div>

      <div class="col">
        <div class="card">
          <div class="card-h">
            <h2>底薪政策</h2>
            <span class="hint">按级别发放</span>
          </div>
          <el-table :data="policies" size="small" :row-class-name="policyRowClass">
            <el-table-column prop="levelName" label="级别" width="110" />
            <el-table-column label="月底薪" align="right">
              <template #default="{ row }">
                <span class="cell-amount">{{ money(row.baseSalary) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="" width="72" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.levelCode === salary?.empLevel" size="small" type="primary" effect="plain">当前</el-tag>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="card-b">
            <div class="callout">
              底薪按级别固定发放，跟业绩无关：业绩为 0 的月份也照发，只是提成为 0。
              个别谈好特殊待遇的，由主管单独指定金额，撤销后自动回到级别金额。
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-h"><h2>规则说明</h2></div>
          <div class="card-b">
            <p class="note">
              1. 业绩 = 本月到账回款按分成比例确认的金额，含冲销与调整；<br />
              2. 订金计入到账当月，尾款计入实际到账当月，不并到签单月；<br />
              3. 退款冲销会产生负业绩，提成按最低档比例扣回，下月补回；<br />
              4. 阶梯与底薪政策由管理员在后台维护，调整后立即生效。
            </p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getBasePolicy, getMySalary, getTiers, type BasePolicyVO, type SalaryVO, type TierVO } from '@/api/salary'

const period = ref(currentMonth())
const salary = ref<SalaryVO | null>(null)
const tiers = ref<TierVO[]>([])
const policies = ref<BasePolicyVO[]>([])
const loading = ref(false)

function currentMonth() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

function money(n?: number) {
  if (n === undefined || n === null) return '0.00'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function int(n?: number) {
  if (n === undefined || n === null) return '0'
  return n.toLocaleString('zh-CN', { maximumFractionDigits: 0 })
}

const breakdownRows = computed(() => {
  const s = salary.value
  return [
    {
      label: '底薪',
      formula: s?.baseSalaryNote || '按级别',
      amount: s?.baseSalary ?? 0,
      strong: false,
    },
    {
      label: '提成',
      formula: `业绩 ${money(s?.performance)} × ${((s?.rateBp ?? 0) / 100).toFixed(0)}%`,
      amount: s?.commission ?? 0,
      strong: false,
    },
    {
      label: '应发工资',
      formula: '底薪 + 提成',
      amount: s?.payable ?? 0,
      strong: true,
    },
  ]
})

function isCurrentTier(t: TierVO) {
  const p = salary.value?.performance ?? 0
  const min = t.minAmount ?? 0
  const max = t.maxAmount
  return p >= min && (max === undefined || max === null || p < max)
}

function tierRowClass({ row }: { row: TierVO }) {
  return isCurrentTier(row) ? 'row-active' : ''
}

function policyRowClass({ row }: { row: BasePolicyVO }) {
  return row.levelCode === salary.value?.empLevel ? 'row-active' : ''
}

const progressPct = computed(() => {
  const target = salary.value?.nextTierAt
  const p = salary.value?.performance ?? 0
  if (!target || target <= 0) return 0
  return Math.min(100, Math.round((p / target) * 1000) / 10)
})

async function load() {
  loading.value = true
  try {
    // 三个请求都不重，并行发；工资单是主体，另两个是解释用的字典
    const [s, t, b] = await Promise.all([getMySalary(period.value), getTiers(), getBasePolicy()])
    salary.value = s
    tiers.value = t
    policies.value = b
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.grid2 {
  display: grid;
  /* 两列等宽：之前用 1.15fr/1fr，右列装不下「底薪政策」表格，
     表格把卡片撑破、金额被裁掉一截。grid item 默认 min-width:auto，
     内容再宽也不会收缩，所以必须显式给 .col 加 min-width: 0。 */
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  align-items: start;
}

.col {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.callout {
  background: #f7f9fb;
  border: 1px solid var(--line-2);
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 12px;
  color: var(--t2);
  line-height: 1.75;
}

.callout b {
  color: var(--t1);
}

.note {
  font-size: 12px;
  color: var(--t2);
  line-height: 1.8;
  margin-top: 10px;
}

.progress-head {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--t2);
  margin-bottom: 6px;
}

:deep(.row-active) {
  background: var(--blue-bg) !important;
}

@media (max-width: 1100px) {
  .grid2 {
    grid-template-columns: 1fr;
  }
}
</style>
