<template>
  <div class="page">
    <div class="kpis">
      <div class="kpi hl">
        <div class="k">待开票金额</div>
        <div class="v">{{ int(summary?.pendingAmount) }}<small>元</small></div>
        <div class="s">{{ summary?.pendingCount ?? 0 }} 张等待财务开票</div>
      </div>
      <div class="kpi" :class="{ warn: (summary?.pendingCount ?? 0) > 0 }">
        <div class="k">待开票张数</div>
        <div class="v">{{ summary?.pendingCount ?? 0 }}<small>张</small></div>
        <div class="s">财务的待办，越早开出越不容易被客户催</div>
      </div>
      <div class="kpi">
        <div class="k">本月已开</div>
        <div class="v">{{ int(summary?.monthIssuedAmount) }}<small>元</small></div>
        <div class="s">{{ summary?.monthIssuedCount ?? 0 }} 张 · 按开票日期统计</div>
      </div>
      <div class="kpi">
        <div class="k">累计已开</div>
        <div class="v">{{ int(summary?.totalIssuedAmount) }}<small>元</small></div>
        <div class="s">不含作废发票</div>
      </div>
    </div>

    <div class="toolbar">
      <el-select v-model="status" placeholder="全部状态" clearable style="width: 130px" @change="reload">
        <el-option :value="1" label="待开" />
        <el-option :value="2" label="已开" />
        <el-option :value="3" label="已作废" />
      </el-select>
      <el-date-picker
        v-model="period"
        type="month"
        value-format="YYYY-MM"
        placeholder="申请月份"
        clearable
        style="width: 150px"
        @change="reload"
      />
      <el-input
        v-model="keyword"
        placeholder="票号 / 订单号 / 客户 / 抬头"
        clearable
        style="width: 220px"
        @keyup.enter="reload"
        @clear="reload"
      />
      <el-button :loading="loading" @click="reload">查询</el-button>
      <div class="spacer"></div>
      <el-button :loading="exporting" @click="doExport">导出 Excel</el-button>
      <el-button type="primary" @click="openApply">申请开票</el-button>
    </div>

    <div class="card">
      <el-table v-loading="loading" :data="rows" size="small" empty-text="还没有开票记录">
        <el-table-column label="发票号" width="150">
          <template #default="{ row }">
            <span v-if="row.invoiceNo" class="mono">{{ row.invoiceNo }}</span>
            <span v-else class="muted">待开</span>
          </template>
        </el-table-column>
        <el-table-column label="订单 / 客户" min-width="200">
          <template #default="{ row }">
            <div class="mono">{{ row.orderNo || '—' }}</div>
            <div class="muted">{{ row.clientName || '' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="发票抬头" min-width="180">
          <template #default="{ row }">
            <div>{{ row.title }}</div>
            <div class="muted mono">{{ row.taxNo || '' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="96">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="row.invoiceType === 2 ? 'warning' : 'info'">
              {{ row.invoiceTypeText }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="含税金额" width="112" align="right">
          <template #default="{ row }"><span class="cell-amount strong">{{ money(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column label="税率 / 税额" width="120" align="right">
          <template #default="{ row }">
            <div>{{ row.taxRatePercent ? row.taxRatePercent + '%' : '—' }}</div>
            <div class="muted cell-amount">{{ money(row.taxAmount) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)" effect="plain">{{ row.statusText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="开票日 / 申请人" width="130">
          <template #default="{ row }">
            <div>{{ row.issueDate || '—' }}</div>
            <div class="muted">{{ row.applyByName || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 1 && canIssue" link type="primary" @click="openIssue(row)">
              开票
            </el-button>
            <el-button
              v-if="row.status !== 3 && canIssue"
              link
              type="danger"
              @click="openVoid(row)"
            >
              作废
            </el-button>
            <span v-if="!canIssue" class="muted">仅财务可操作</span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        style="margin-top: 12px; justify-content: flex-end"
        @current-change="load"
        @size-change="reload"
      />
    </div>

    <!-- 申请开票 -->
    <el-dialog v-model="applyVisible" title="申请开票" width="560px" :close-on-click-modal="false">
      <el-form label-width="96px">
        <el-form-item label="订单" required>
          <el-select
            v-model="applyForm.orderId"
            filterable
            remote
            :remote-method="searchOrders"
            :loading="orderLoading"
            placeholder="输入订单号 / 客户名搜索"
            style="width: 100%"
            @change="onOrderChange"
          >
            <el-option
              v-for="o in orderOptions"
              :key="o.id"
              :label="`${o.orderNo} · ${o.clientName} · ${o.title}`"
              :value="o.id"
            />
          </el-select>
        </el-form-item>

        <div class="order-brief" v-if="selectedOrder">
          <span>订单总额 <b>{{ money(selectedOrder.totalAmount) }}</b></span>
          <span>已开票 <b>{{ money(invoiced) }}</b></span>
          <span>已收 <b>{{ money(selectedOrder.paidAmount) }}</b></span>
          <span>可开余额 <b class="text-blue">{{ money(available) }}</b></span>
        </div>

        <el-form-item label="开票金额" required>
          <el-input-number v-model="applyForm.amount" :min="0.01" :precision="2" style="width: 100%" />
          <div class="hint-line">
            含税金额；累计开票不能超过订单总额，本单最多可开 {{ money(available) }} 元
          </div>
        </el-form-item>
        <el-form-item label="发票类型">
          <el-radio-group v-model="applyForm.invoiceType">
            <el-radio v-for="t in INVOICE_TYPES" :key="t.value" :value="t.value">{{ t.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="税率">
          <el-input-number v-model="applyForm.taxRatePercent" :min="0" :max="100" :precision="2" style="width: 160px" />
          <span class="muted" style="margin-left: 8px">%，设计服务一般 6%</span>
        </el-form-item>
        <el-form-item label="发票抬头">
          <el-input v-model="applyForm.title" maxlength="120" placeholder="营业执照全称" />
        </el-form-item>
        <el-form-item label="纳税人识别号">
          <el-input v-model="applyForm.taxNo" maxlength="50" placeholder="专票必填" />
        </el-form-item>
        <el-form-item label="开票内容">
          <el-input v-model="applyForm.content" maxlength="120" placeholder="如 设计服务费" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="applyForm.remark" maxlength="200" placeholder="选填，如「客户要求 12 月 20 日前寄到」" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" :loading="applying" @click="submitApply">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 开票回填 -->
    <el-dialog v-model="issueVisible" title="开票完成，回填发票号" width="440px">
      <el-form label-width="88px">
        <el-form-item label="发票号" required>
          <el-input v-model="issueForm.invoiceNo" placeholder="税控系统里的发票号码" maxlength="50" />
        </el-form-item>
        <el-form-item label="开票日期">
          <el-date-picker
            v-model="issueForm.issueDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="默认今天"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <div class="callout" v-if="issueTarget">
        {{ issueTarget.orderNo }} · {{ issueTarget.title }} · 含税
        <b>{{ money(issueTarget.amount) }}</b> 元
      </div>
      <template #footer>
        <el-button @click="issueVisible = false">取消</el-button>
        <el-button type="primary" :loading="issuing" @click="submitIssue">确认开票</el-button>
      </template>
    </el-dialog>

    <!-- 作废 -->
    <el-dialog v-model="voidVisible" title="作废发票" width="440px">
      <p class="dialog-tip">
        作废后这张票不再计入该订单的已开金额，但记录会保留 —— 税务核查时要能解释为什么作废。
      </p>
      <el-input
        v-model="voidReason"
        type="textarea"
        :rows="3"
        maxlength="200"
        show-word-limit
        placeholder="必填，如「客户抬头写错，重开」"
      />
      <template #footer>
        <el-button @click="voidVisible = false">取消</el-button>
        <el-button type="danger" :loading="voiding" @click="submitVoid">确认作废</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  INVOICE_TYPES,
  applyInvoice,
  getInvoiceSummary,
  getInvoices,
  getOrderInvoices,
  issueInvoice,
  voidInvoice,
  type InvoiceSummaryVO,
  type InvoiceVO,
} from '@/api/invoice'
import { getOrders, type OrderListVO } from '@/api/order'
import { exportInvoices } from '@/api/report'
import { useUserStore } from '@/stores/user'

const user = useUserStore()
const canIssue = computed(() => user.roles.includes('ADMIN') || user.roles.includes('FINANCE'))

const status = ref<number | null>(null)
const period = ref('')
const keyword = ref('')
const rows = ref<InvoiceVO[]>([])
const summary = ref<InvoiceSummaryVO | null>(null)
const loading = ref(false)
const exporting = ref(false)

const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)

/** 换筛选条件或每页条数时回到第一页 */
function reload() {
  pageNum.value = 1
  load()
}

const applyVisible = ref(false)
const applying = ref(false)
const orderOptions = ref<OrderListVO[]>([])
const orderLoading = ref(false)
const selectedOrder = ref<OrderListVO | null>(null)
const invoiced = ref(0)
const available = computed(() => {
  const total = selectedOrder.value?.totalAmount ?? 0
  return Math.max(total - invoiced.value, 0)
})

const applyForm = reactive({
  orderId: null as number | null,
  amount: 0,
  invoiceType: 1,
  taxRatePercent: 6,
  title: '',
  taxNo: '',
  content: '设计服务费',
  remark: '',
})

const issueVisible = ref(false)
const issuing = ref(false)
const issueTarget = ref<InvoiceVO | null>(null)
const issueForm = reactive({ invoiceNo: '', issueDate: '' })

const voidVisible = ref(false)
const voiding = ref(false)
const voidTarget = ref<InvoiceVO | null>(null)
const voidReason = ref('')

function money(n?: number | null) {
  if (n === undefined || n === null) return '0.00'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function int(n?: number | null) {
  if (n === undefined || n === null) return '0'
  return Math.round(n).toLocaleString('zh-CN')
}

function statusTag(s: number) {
  if (s === 1) return 'warning'
  if (s === 2) return 'success'
  return 'info'
}

async function load() {
  loading.value = true
  try {
    const [s, page] = await Promise.all([
      getInvoiceSummary(),
      getInvoices({
        status: status.value ?? undefined,
        period: period.value || undefined,
        keyword: keyword.value || undefined,
        pageNum: pageNum.value,
        pageSize: pageSize.value,
      }),
    ])
    summary.value = s
    rows.value = page.list
    total.value = page.total
  } finally {
    loading.value = false
  }
}

async function doExport() {
  exporting.value = true
  try {
    await exportInvoices(status.value, period.value || undefined, keyword.value || undefined)
  } finally {
    exporting.value = false
  }
}

function openApply() {
  applyForm.orderId = null
  applyForm.amount = 0
  applyForm.invoiceType = 1
  applyForm.taxRatePercent = 6
  applyForm.title = ''
  applyForm.taxNo = ''
  applyForm.content = '设计服务费'
  applyForm.remark = ''
  selectedOrder.value = null
  invoiced.value = 0
  orderOptions.value = []
  applyVisible.value = true
  // 打开就预取最近 20 单，省得用户先打字才能看到选项
  searchOrders('')
}

/** 远程搜索订单：只在输入时查，避免把几百单全推给下拉框 */
async function searchOrders(kw: string) {
  orderLoading.value = true
  try {
    const res = await getOrders({ keyword: kw || undefined, pageNum: 1, pageSize: 20 })
    orderOptions.value = res.list || []
  } finally {
    orderLoading.value = false
  }
}

async function onOrderChange(id: number) {
  const order = orderOptions.value.find((o) => o.id === id) || null
  selectedOrder.value = order
  if (!order) return
  // 已开票金额要排除作废票，所以直接取该单的开票记录来算，
  // 不用后端再加一个"按订单汇总"的接口
  const list = await getOrderInvoices(id)
  invoiced.value = list.filter((i) => i.status !== 3).reduce((sum, i) => sum + i.amount, 0)
  applyForm.amount = Math.max((order.totalAmount ?? 0) - invoiced.value, 0)
  applyForm.title = order.clientName || ''
}

async function submitApply() {
  if (!applyForm.orderId) {
    ElMessage.warning('请先选择订单')
    return
  }
  if (!applyForm.amount || applyForm.amount <= 0) {
    ElMessage.warning('请填写开票金额')
    return
  }
  applying.value = true
  try {
    await applyInvoice({
      orderId: applyForm.orderId,
      amount: applyForm.amount,
      taxRatePercent: applyForm.taxRatePercent,
      invoiceType: applyForm.invoiceType,
      title: applyForm.title || undefined,
      taxNo: applyForm.taxNo || undefined,
      content: applyForm.content || undefined,
      remark: applyForm.remark || undefined,
    })
    ElMessage.success('已提交，等待财务开票')
    applyVisible.value = false
    load()
  } finally {
    applying.value = false
  }
}

function openIssue(row: InvoiceVO) {
  issueTarget.value = row
  issueForm.invoiceNo = ''
  issueForm.issueDate = ''
  issueVisible.value = true
}

async function submitIssue() {
  if (!issueTarget.value) return
  if (!issueForm.invoiceNo.trim()) {
    ElMessage.warning('请填写发票号')
    return
  }
  issuing.value = true
  try {
    await issueInvoice(issueTarget.value.id, issueForm.invoiceNo.trim(), issueForm.issueDate || undefined)
    ElMessage.success('已标记为已开票')
    issueVisible.value = false
    load()
  } finally {
    issuing.value = false
  }
}

function openVoid(row: InvoiceVO) {
  voidTarget.value = row
  voidReason.value = ''
  voidVisible.value = true
}

async function submitVoid() {
  if (!voidTarget.value) return
  if (!voidReason.value.trim()) {
    ElMessage.warning('请填写作废原因')
    return
  }
  voiding.value = true
  try {
    await voidInvoice(voidTarget.value.id, voidReason.value.trim())
    ElMessage.success('已作废')
    voidVisible.value = false
    load()
  } finally {
    voiding.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.order-brief {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin: 0 0 14px 96px;
  padding: 10px 12px;
  background: #fafbfc;
  border: 1px solid var(--line-2);
  border-radius: 8px;
  font-size: 12px;
  color: var(--t2);
}

.hint-line {
  font-size: 11px;
  color: var(--t3);
  line-height: 1.6;
  margin-top: 2px;
}

.callout {
  background: #fafbfc;
  border: 1px solid var(--line-2);
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 12.5px;
  color: var(--t2);
}

.dialog-tip {
  font-size: 12px;
  color: var(--t2);
  line-height: 1.7;
  margin-bottom: 10px;
}
</style>
