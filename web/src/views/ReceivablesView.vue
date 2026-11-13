<template>
  <div class="page">
    <!-- 月末催收提醒：进窗口就高亮，平时只提示还有几天 -->
    <div class="banner" :class="summary?.windowActive ? 'on' : 'off'">
      <div class="banner-main">
        <span class="banner-tag">月末催收</span>
        <b>{{ summary?.windowText }}</b>
        <span v-if="summary?.windowActive">
          催收窗口已开启，本期有 <b>{{ summary?.windowCount }}</b> 期尾款待收，合计
          <b>{{ money(summary?.windowUnpaid) }}</b> 元
        </span>
        <span v-else-if="(summary?.daysToWindow ?? 0) > 0">
          距本期催收窗口还有 <b>{{ summary?.daysToWindow }}</b> 天，当前待收
          <b>{{ money(summary?.totalUnpaid) }}</b> 元
        </span>
        <span v-else>本月催收窗口已过，逾期未收 <b>{{ money(summary?.overdueUnpaid) }}</b> 元，请尽快跟进</span>
      </div>
      <div class="banner-side">
        本月已催 <b>{{ summary?.remindedThisMonth ?? 0 }}</b> 次 · 本月回款
        <b>{{ money(summary?.monthCollected) }}</b> 元
      </div>
    </div>

    <div class="kpis">
      <div class="kpi hl">
        <div class="k">待收合计</div>
        <div class="v">{{ int(summary?.totalUnpaid) }}<small>元</small></div>
        <div class="s">全部未收讫的订金与尾款</div>
      </div>
      <div class="kpi" :class="{ warn: (summary?.windowCount ?? 0) > 0 }">
        <div class="k">本期窗口待催</div>
        <div class="v">{{ int(summary?.windowUnpaid) }}<small>元</small></div>
        <div class="s">{{ summary?.windowCount ?? 0 }} 期 · 末三天集中催</div>
      </div>
      <div class="kpi" :class="{ warn: (summary?.overdueCount ?? 0) > 0 }">
        <div class="k">已逾期</div>
        <div class="v">{{ int(summary?.overdueUnpaid) }}<small>元</small></div>
        <div class="s">{{ summary?.overdueCount ?? 0 }} 期已过应收日</div>
      </div>
      <div class="kpi">
        <div class="k">客户已承诺</div>
        <div class="v">{{ summary?.promisedCount ?? 0 }}<small>期</small></div>
        <div class="s">承诺日未到，暂不必反复催</div>
      </div>
    </div>

    <div class="toolbar">
      <el-radio-group v-model="scope" @change="load">
        <el-radio-button value="ALL">全部未结清</el-radio-button>
        <el-radio-button value="WINDOW">本期窗口</el-radio-button>
        <el-radio-button value="OVERDUE">已逾期</el-radio-button>
      </el-radio-group>
      <el-input
        v-model="keyword"
        placeholder="订单号 / 客户 / 项目"
        clearable
        style="width: 200px"
        @keyup.enter="load"
        @clear="load"
      />
      <el-button :loading="loading" @click="load">刷新</el-button>
      <div class="spacer"></div>
      <el-button :loading="exporting" @click="doExport">导出 Excel</el-button>
    </div>

    <div class="card">
      <el-table
        v-loading="loading"
        :data="rows"
        size="small"
        :row-class-name="rowClass"
        empty-text="没有待催的款项"
      >
        <el-table-column label="订单 / 客户" min-width="210">
          <template #default="{ row }">
            <div class="mono">{{ row.orderNo }}</div>
            <div class="muted">{{ row.clientName }} · {{ row.title }}</div>
          </template>
        </el-table-column>
        <el-table-column label="期次" width="80">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="row.seq === 1 ? 'info' : 'warning'">
              {{ row.planName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="应收" width="110" align="right">
          <template #default="{ row }"><span class="cell-amount">{{ money(row.planAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="未收" width="110" align="right">
          <template #default="{ row }">
            <span class="cell-amount strong text-red">{{ money(row.unpaidAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="应收日" width="112">
          <template #default="{ row }">
            <div>{{ row.planDate || '—' }}</div>
            <div v-if="row.overdueDays > 0" class="muted text-red">逾期 {{ row.overdueDays }} 天</div>
            <div v-else-if="row.inWindow" class="muted text-amber">本月催收窗口内</div>
            <div v-else class="muted">还有 {{ row.daysToDue }} 天</div>
          </template>
        </el-table-column>
        <el-table-column label="催收" width="140">
          <template #default="{ row }">
            <div>{{ row.remindCount ? `已催 ${row.remindCount} 次` : '从未催过' }}</div>
            <div class="muted">{{ row.lastRemindAt ? row.lastRemindAt.slice(0, 10) : '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="客户承诺" width="130">
          <template #default="{ row }">
            <template v-if="row.promiseDate">
              <div :class="row.promiseBroken ? 'text-red' : ''">{{ row.promiseDate }}</div>
              <div class="muted">
                {{ row.promiseAmount ? money(row.promiseAmount) : '—' }}
                <span v-if="row.promiseBroken"> · 未兑现</span>
              </div>
            </template>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="客服" width="120">
          <template #default="{ row }">
            <div>{{ row.csName || '—' }}</div>
            <div class="muted mono">{{ row.csPhone || '' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openCollect(row)">催收</el-button>
            <el-button link @click="openOrder(row)">看订单</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 催收登记 -->
    <el-dialog v-model="collectVisible" title="登记一次催收" width="480px">
      <div class="callout" v-if="current">
        <b class="mono">{{ current.orderNo }}</b> · {{ current.clientName }} ·
        {{ current.planName }}未收 <b>{{ money(current.unpaidAmount) }}</b> 元，应收日
        {{ current.planDate }}
      </div>
      <el-form label-width="88px" style="margin-top: 12px">
        <el-form-item label="催收方式">
          <el-select v-model="form.action" style="width: 100%">
            <el-option v-for="a in COLLECT_ACTIONS" :key="a.value" :label="a.label" :value="a.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="客户反应">
          <el-select v-model="form.result" style="width: 100%">
            <el-option v-for="r in COLLECT_RESULTS" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.result === 1" label="承诺回款日">
          <el-date-picker
            v-model="form.promiseDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="客户说哪天付"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item v-if="form.result === 1" label="承诺金额">
          <el-input-number v-model="form.promiseAmount" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="沟通要点">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="2"
            maxlength="300"
            show-word-limit
            placeholder="选填，如「财务在走流程，下周一付」"
          />
        </el-form-item>
      </el-form>
      <div class="callout warn" v-if="form.result === 3">
        客户说已付款：这里只登记沟通记录，钱要以「登记回款」为准，否则计划状态和实际到账会对不上。
      </div>
      <template #footer>
        <el-button @click="collectVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCollect">保存记录</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  COLLECT_ACTIONS,
  COLLECT_RESULTS,
  collect,
  getCollection,
  getCollectionSummary,
  type CollectScope,
  type CollectionSummaryVO,
  type CollectionVO,
} from '@/api/receivable'
import { exportReceivables } from '@/api/report'

const router = useRouter()

const scope = ref<CollectScope>('ALL')
const keyword = ref('')
const rows = ref<CollectionVO[]>([])
const summary = ref<CollectionSummaryVO | null>(null)
const loading = ref(false)
const exporting = ref(false)

const collectVisible = ref(false)
const submitting = ref(false)
const current = ref<CollectionVO | null>(null)
const form = reactive({
  action: 1,
  result: 1,
  promiseDate: '' as string | null,
  promiseAmount: null as number | null,
  remark: '',
})

function money(n?: number | null) {
  if (n === undefined || n === null) return '0.00'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** KPI 只显示整数部分：这里看的是量级，多两位小数反而看不清 */
function int(n?: number | null) {
  if (n === undefined || n === null) return '0'
  return Math.round(n).toLocaleString('zh-CN')
}

function rowClass({ row }: { row: CollectionVO }) {
  if (row.overdueDays > 0) return 'row-overdue'
  if (row.inWindow) return 'row-window'
  return ''
}

async function load() {
  loading.value = true
  try {
    // 概览和列表并行取，页面不会出现"先空后跳"的两段式渲染
    const [s, list] = await Promise.all([
      getCollectionSummary(),
      getCollection(scope.value, keyword.value || undefined),
    ])
    summary.value = s
    rows.value = list
  } finally {
    loading.value = false
  }
}

function openCollect(row: CollectionVO) {
  current.value = row
  form.action = 1
  form.result = 1
  form.promiseDate = null
  form.promiseAmount = row.unpaidAmount
  form.remark = ''
  collectVisible.value = true
}

async function submitCollect() {
  if (!current.value) return
  submitting.value = true
  try {
    await collect(current.value.planId, {
      action: form.action,
      result: form.result,
      promiseDate: form.result === 1 ? form.promiseDate : null,
      promiseAmount: form.result === 1 ? form.promiseAmount : null,
      remark: form.remark || undefined,
    })
    ElMessage.success('已记录，催收次数 +1')
    collectVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

function openOrder(row: CollectionVO) {
  router.push({ path: '/orders', query: { orderId: String(row.orderId) } })
}

async function doExport() {
  exporting.value = true
  try {
    await exportReceivables(scope.value, keyword.value || undefined)
  } finally {
    exporting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 12px 16px;
  border-radius: var(--radius);
  border: 1px solid var(--line);
  background: var(--card);
  font-size: 12.5px;
  color: var(--t2);
}

.banner.on {
  background: var(--amber-bg);
  border-color: #f0d9b5;
}

.banner.off {
  background: var(--card);
}

.banner-tag {
  display: inline-block;
  margin-right: 8px;
  padding: 1px 7px;
  border-radius: 4px;
  background: var(--t1);
  color: #fff;
  font-size: 11px;
}

.banner.on .banner-tag {
  background: var(--amber);
}

.banner b {
  color: var(--t1);
}

.banner-side {
  font-size: 12px;
  color: var(--t3);
}

.callout {
  background: #fafbfc;
  border: 1px solid var(--line-2);
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 12.5px;
  color: var(--t2);
  line-height: 1.7;
}

.callout.warn {
  margin-top: 10px;
  background: var(--amber-bg);
  border-color: #f0d9b5;
  color: var(--amber);
}

:deep(.row-overdue) {
  background: #fdf3f3;
}

:deep(.row-window) {
  background: #fffaf1;
}
</style>
