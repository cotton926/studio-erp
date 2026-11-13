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
      <span class="muted">口径：业绩按回款到账月确认，订金与尾款分别计入；签单额只做对照，不参与提成</span>
      <div class="spacer"></div>
      <el-button :loading="loading" @click="load">刷新</el-button>
      <el-button :loading="exporting" @click="doExport">导出明细</el-button>
    </div>

    <div class="kpis" v-loading="loading">
      <div class="kpi hl">
        <div class="k">本月业绩</div>
        <div class="v">{{ int(data?.performance) }}<small>元</small></div>
        <div class="s">
          上月 {{ int(data?.lastMonthPerformance) }}
          <template v-if="momText"> · {{ momText }}</template>
        </div>
      </div>
      <div class="kpi">
        <div class="k">本年累计</div>
        <div class="v">{{ int(data?.yearPerformance) }}<small>元</small></div>
        <div class="s">按自然年汇总</div>
      </div>
      <div class="kpi">
        <div class="k">本月单量</div>
        <div class="v">{{ data?.orderCount ?? 0 }}<small>单</small></div>
        <div class="s">本月签单、且我参与的单</div>
      </div>
      <div class="kpi" :class="{ warn: (data?.unpaidAmount ?? 0) > 0 }">
        <div class="k">我的未收业绩</div>
        <div class="v">{{ int(data?.unpaidAmount) }}<small>元</small></div>
        <div class="s">{{ (data?.unpaidAmount ?? 0) > 0 ? '已签单、客户尚未回款的部分' : '当前没有未收的业绩' }}</div>
      </div>
    </div>

    <div class="card">
      <div class="card-h">
        <h2>近 12 个月业绩</h2>
        <span class="hint">单位：元，深色为当前所选月份</span>
      </div>
      <div class="card-b">
        <div ref="chartEl" class="chart"></div>
      </div>
    </div>

    <div class="card">
      <div class="card-h">
        <h2>{{ periodLabel }} 订单明细</h2>
        <span class="hint">仅显示与你相关的单，金额是「你的分成」</span>
      </div>
      <el-table :data="data?.orders || []" size="small" empty-text="本月还没有与你相关的订单">
        <el-table-column prop="orderNo" label="订单号" width="150">
          <template #default="{ row }">
            <span class="mono">{{ row.orderNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="客户 / 项目" min-width="200">
          <template #default="{ row }">
            <div>{{ row.clientName }}</div>
            <div class="muted">{{ row.itemNames || row.title }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="signDate" label="签单日" width="100" />
        <el-table-column prop="ratioText" label="我的分成" width="90" align="center" />
        <el-table-column label="我的签单额" width="120" align="right">
          <template #default="{ row }">
            <span class="cell-amount">{{ money(row.mySignAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="我的已收" width="120" align="right">
          <template #default="{ row }">
            <span class="cell-amount text-green">{{ money(row.myPaidAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="未收" width="110" align="right">
          <template #default="{ row }">
            <span class="cell-amount" :class="row.myUnpaid > 0 ? 'text-amber' : 'muted'">
              {{ money(row.myUnpaid) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="150">
          <template #default="{ row }">
            <el-tag size="small" :type="orderTagType(row.statusCode)" effect="plain">
              {{ row.statusText }}
            </el-tag>
            <el-tag size="small" :type="payTagType(row.paymentStatusText)" effect="plain" style="margin-left: 4px">
              {{ row.paymentStatusText }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import * as echarts from 'echarts'
import { getPerfSummary, type PerfSummary } from '@/api/performance'
import { exportPerformance } from '@/api/report'

const period = ref(currentMonth())
const data = ref<PerfSummary | null>(null)
const loading = ref(false)
const exporting = ref(false)
const chartEl = ref<HTMLDivElement>()
const chart = shallowRef<echarts.ECharts>()

function currentMonth() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

const periodLabel = computed(() => {
  const [y, m] = period.value.split('-')
  return `${y} 年 ${Number(m)} 月`
})

const momText = computed(() => {
  const r = data.value?.momRate
  if (r === undefined || r === null) return ''
  const sign = r >= 0 ? '+' : ''
  return `环比 ${sign}${r}%`
})

async function doExport() {
  exporting.value = true
  try {
    await exportPerformance(period.value)
  } finally {
    exporting.value = false
  }
}

function money(n?: number) {
  if (n === undefined || n === null) return '0.00'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function int(n?: number) {
  if (n === undefined || n === null) return '0'
  return n.toLocaleString('zh-CN', { maximumFractionDigits: 0 })
}

function orderTagType(code?: number) {
  if (code === 4) return 'success'
  if (code === 9) return 'info'
  if (code === 1) return 'warning'
  return 'primary'
}

function payTagType(text?: string) {
  if (text === '已结清') return 'success'
  if (text === '部分收款') return 'warning'
  return 'info'
}

async function load() {
  loading.value = true
  try {
    data.value = await getPerfSummary(period.value)
    await nextTick()
    renderChart()
  } finally {
    loading.value = false
  }
}

/**
 * 趋势图。
 * 用柱状而不是折线：每月业绩是"离散的一笔账"，不是连续变化的过程；
 * 而且 0 业绩的月份在折线上会被连成一条斜线，看起来像"慢慢变少"，柱状则直接空着。
 */
function renderChart() {
  if (!chartEl.value) return
  if (!chart.value) chart.value = echarts.init(chartEl.value)

  const trend = data.value?.trend || []
  chart.value.setOption(
    {
      grid: { left: 8, right: 12, top: 20, bottom: 4, containLabel: true },
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        valueFormatter: (v: number) => `${money(v)} 元`,
      },
      xAxis: {
        type: 'category',
        data: trend.map((t) => t.label),
        axisTick: { show: false },
        axisLine: { lineStyle: { color: '#e5e6eb' } },
        axisLabel: { color: '#9aa1ac', fontSize: 11 },
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: '#f0f1f3' } },
        axisLabel: {
          color: '#9aa1ac',
          fontSize: 11,
          formatter: (v: number) => (v >= 10000 ? `${v / 10000}万` : String(v)),
        },
      },
      series: [
        {
          type: 'bar',
          barMaxWidth: 28,
          data: trend.map((t) => ({
            value: t.amount,
            itemStyle: {
              color: t.current ? '#185fa5' : '#b5d4f4',
              borderRadius: [4, 4, 0, 0],
            },
          })),
        },
      ],
    },
    true,
  )
}

function onResize() {
  chart.value?.resize()
}

onMounted(() => {
  load()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  // 不 dispose 会在反复进出页面时泄漏 canvas，切几次后滚动明显卡顿
  chart.value?.dispose()
  chart.value = undefined
})
</script>

<style scoped>
.chart {
  height: 220px;
  width: 100%;
}
</style>
