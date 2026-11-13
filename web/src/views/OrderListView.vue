<template>
  <div class="page">
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="订单号 / 客户 / 项目"
        clearable
        style="width: 220px"
        :prefix-icon="Search"
        @keyup.enter="reload"
        @clear="reload"
      />
      <el-select v-model="query.status" placeholder="执行状态" clearable style="width: 130px" @change="reload">
        <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>
      <el-select v-model="query.paymentStatus" placeholder="收款状态" clearable style="width: 130px" @change="reload">
        <el-option :value="0" label="未收" />
        <el-option :value="1" label="部分收款" />
        <el-option :value="2" label="已结清" />
      </el-select>
      <el-button @click="reload">查询</el-button>
      <div class="spacer"></div>
      <el-button v-if="canExportOrders" :loading="exporting" @click="doExport">导出 Excel</el-button>
      <el-button v-if="user.isManager" type="primary" @click="router.push('/orders/new')">+ 新建订单</el-button>
    </div>

    <div class="card">
      <el-table v-loading="loading" :data="rows" size="small" @row-click="openDetail" class="clickable">
        <el-table-column prop="orderNo" label="订单号" width="150">
          <template #default="{ row }">
            <span class="mono">{{ row.orderNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="客户 / 项目" min-width="200">
          <template #default="{ row }">
            <div>{{ row.clientName }}</div>
            <div class="muted">{{ row.title }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="signDate" label="签单日" width="100" />
        <el-table-column label="设计师" width="130">
          <template #default="{ row }">
            <span class="muted">{{ row.designerNames || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="user.isManager" label="订单金额" width="120" align="right">
          <template #default="{ row }">
            <span class="cell-amount">{{ money(row.totalAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="我的分成" width="120" align="right">
          <template #default="{ row }">
            <span class="cell-amount text-blue">{{ money(row.myAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="user.isManager" label="已收" width="120" align="right">
          <template #default="{ row }">
            <span class="cell-amount text-green">{{ money(row.paidAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="150">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="orderTagType(row.status)">{{ row.statusText }}</el-tag>
            <el-tag
              size="small"
              effect="plain"
              :type="payTagType(row.paymentStatus)"
              style="margin-left: 4px"
            >
              {{ row.paymentStatusText }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openDetail(row)">详情</el-button>
            <el-button
              v-if="user.isManager && row.status === 1"
              link
              type="warning"
              @click.stop="openPricing(row)"
            >
              定价
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty">没有符合条件的订单</div>
        </template>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="load"
          @size-change="reload"
        />
      </div>
    </div>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" size="760px" :title="detail?.orderNo || '订单详情'">
      <div v-if="detail" class="detail">
        <div class="meta">
          <div><span class="k">客户</span>{{ detail.clientName }}</div>
          <div><span class="k">项目</span>{{ detail.title }}</div>
          <div><span class="k">签单日</span>{{ detail.signDate || '—' }}</div>
          <div><span class="k">状态</span>{{ detail.statusText }} / {{ detail.paymentStatusText }}</div>
        </div>

        <el-tabs v-model="detailTab">
          <!-- 明细 -->
          <el-tab-pane label="明细" name="items">
            <el-table :data="detail.items" size="small">
              <el-table-column prop="lineNo" label="#" width="46" />
              <el-table-column label="品项" min-width="160">
                <template #default="{ row }">
                  <div>{{ row.itemName }}</div>
                  <div class="muted" v-if="row.spec">{{ row.spec }}</div>
                </template>
              </el-table-column>
              <el-table-column label="数量" width="70" align="right">
                <template #default="{ row }">{{ row.qty }}</template>
              </el-table-column>
              <el-table-column label="单价" width="100" align="right">
                <template #default="{ row }">
                  <span class="cell-amount">{{ row.unitPrice == null ? '—' : money(row.unitPrice) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="金额" width="110" align="right">
                <template #default="{ row }">
                  <span class="cell-amount">{{ row.amount == null ? '—' : money(row.amount) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="分成人" min-width="140">
                <template #default="{ row }">
                  <div v-for="a in row.assignees || []" :key="a.employeeId" class="muted">
                    {{ a.employeeName }} · {{ a.ratioText }}
                  </div>
                  <span v-if="!row.assignees || row.assignees.length === 0" class="muted">未派单</span>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <!-- 收款计划与开票：只有能看金额的角色才有数据（后端对设计师不返回这两个字段） -->
          <el-tab-pane v-if="detail.plans" label="收款 / 开票" name="money">
            <div class="card-h" style="padding: 6px 0 10px">
              <h2>收款计划</h2>
              <span class="hint">尾款于交付月月末到期，每月最后三天集中催收</span>
            </div>
            <el-table :data="detail.plans" size="small" empty-text="还没有收款计划">
              <el-table-column prop="planName" label="期次" width="80" />
              <el-table-column label="应收" width="110" align="right">
                <template #default="{ row }"><span class="cell-amount">{{ money(row.planAmount) }}</span></template>
              </el-table-column>
              <el-table-column label="已收" width="110" align="right">
                <template #default="{ row }"><span class="cell-amount">{{ money(row.receivedAmount) }}</span></template>
              </el-table-column>
              <el-table-column label="未收" width="110" align="right">
                <template #default="{ row }">
                  <span class="cell-amount" :class="{ 'text-red': row.unpaidAmount > 0 }">
                    {{ money(row.unpaidAmount) }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="应收日" width="110">
                <template #default="{ row }">{{ row.planDate || '—' }}</template>
              </el-table-column>
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag size="small" effect="plain" :type="row.status === 3 ? 'success' : row.status === 2 ? 'warning' : 'info'">
                    {{ row.statusText }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="催收" min-width="120">
                <template #default="{ row }">
                  <span v-if="row.remindCount">已催 {{ row.remindCount }} 次</span>
                  <span v-else class="muted">未催</span>
                  <span v-if="row.promiseDate" class="muted"> · 承诺 {{ row.promiseDate }}</span>
                </template>
              </el-table-column>
            </el-table>

            <div class="card-h" style="padding: 18px 0 10px">
              <h2>开票记录</h2>
              <span class="hint">累计已开 {{ money(invoicedOfDetail) }} 元 / 订单总额 {{ money(detail.totalAmount) }} 元</span>
            </div>
            <el-table :data="detail.invoices || []" size="small" empty-text="还没有开票">
              <el-table-column label="发票号" width="140">
                <template #default="{ row }">
                  <span v-if="row.invoiceNo" class="mono">{{ row.invoiceNo }}</span>
                  <span v-else class="muted">待开</span>
                </template>
              </el-table-column>
              <el-table-column label="类型" width="96">
                <template #default="{ row }">{{ row.invoiceTypeText }}</template>
              </el-table-column>
              <el-table-column label="含税金额" width="110" align="right">
                <template #default="{ row }"><span class="cell-amount">{{ money(row.amount) }}</span></template>
              </el-table-column>
              <el-table-column label="税额" width="100" align="right">
                <template #default="{ row }"><span class="cell-amount">{{ money(row.taxAmount) }}</span></template>
              </el-table-column>
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag size="small" effect="plain" :type="row.status === 2 ? 'success' : row.status === 3 ? 'info' : 'warning'">
                    {{ row.statusText }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="开票日" min-width="100">
                <template #default="{ row }">{{ row.issueDate || '—' }}</template>
              </el-table-column>
            </el-table>

            <div class="drawer-foot" style="margin-top: 12px">
              <el-button size="small" @click="router.push('/invoices')">去开票管理</el-button>
              <el-button size="small" @click="router.push('/receivables')">去催收</el-button>
            </div>
          </el-tab-pane>

          <!-- 稿件版本 -->
          <el-tab-pane :label="`稿件 ${deliverables.length}`" name="files">
            <div class="files-bar">
              <el-upload :show-file-list="false" :http-request="doUpload" :disabled="uploading">
                <el-button size="small" type="primary" :loading="uploading">上传新版本</el-button>
              </el-upload>
              <span class="muted">psd / ai / pdf / 图片 / 压缩包，单文件 200MB 内</span>
            </div>

            <el-empty
              v-if="!deliverables.length"
              description="还没有上传稿件"
              :image-size="70"
            />

            <div v-for="d in deliverables" :key="d.id" class="dver">
              <div class="dver-h">
                <span class="vtag">{{ d.versionText }}</span>
                <span class="dname">{{ d.fileName }}</span>
                <span class="muted">{{ d.fileSizeText }}</span>
                <div class="spacer"></div>
                <el-tag size="small" :type="reviewTag(d.reviewResult)">
                  {{ d.reviewResultText }}
                </el-tag>
              </div>
              <div class="dver-m">
                {{ d.uploadedByName || '—' }} 上传于 {{ d.createdAt }}
                <template v-if="d.reviewedByName"> · {{ d.reviewedByName }} 录入反馈</template>
              </div>
              <div v-if="d.feedback" class="dfb" :class="{ bad: d.reviewResult === 3 }">
                {{ d.reviewResult === 3 ? '退回原因：' : '客户反馈：' }}{{ d.feedback }}
              </div>
              <div v-for="r in d.reworks" :key="r.id" class="drew">
                返工 · {{ r.reworkByName || '—' }} · {{ r.createdAt }}
              </div>
              <div class="dver-a">
                <el-button link type="primary" size="small" @click="doDownload(d)">下载</el-button>
                <template v-if="d.reviewResult === 1 && user.isManager">
                  <el-button link type="success" size="small" @click="openReview(d, 2)">客户通过</el-button>
                  <el-button link type="danger" size="small" @click="openReview(d, 3)">客户退回</el-button>
                </template>
              </div>
            </div>
          </el-tab-pane>

          <!-- 流转 -->
          <el-tab-pane label="流转" name="timeline">
            <el-timeline v-if="(detail.timeline || []).length">
              <el-timeline-item
                v-for="(t, i) in detail.timeline"
                :key="i"
                :timestamp="t.createdAt"
                placement="top"
              >
                <span v-if="t.fromStatusText" class="muted">{{ t.fromStatusText }} → </span>
                <b>{{ t.toStatusText }}</b>
                <span class="muted"> · {{ t.operatorName }}</span>
                <div v-if="t.remark" class="muted">{{ t.remark }}</div>
              </el-timeline-item>
            </el-timeline>
            <el-empty
              v-else
              description="暂无流转记录（该功能上线前的历史订单没有）"
              :image-size="70"
            />
          </el-tab-pane>
        </el-tabs>

        <div class="drawer-foot">
          <el-button v-if="user.isManager" @click="openPayment">登记回款</el-button>
        </div>
      </div>
    </el-drawer>

    <!-- 稿件审核 -->
    <el-dialog
      v-model="reviewVisible"
      :title="reviewForm.result === 2 ? '确认客户通过' : '登记客户退回'"
      width="460px"
    >
      <el-form label-width="88px">
        <el-form-item label="版本">
          {{ reviewTarget?.versionText }} · {{ reviewTarget?.fileName }}
        </el-form-item>
        <el-form-item
          :label="reviewForm.result === 3 ? '退回原因' : '备注'"
          :required="reviewForm.result === 3"
        >
          <el-input
            v-model="reviewForm.feedback"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            :placeholder="
              reviewForm.result === 3
                ? '客户为什么退回？写清楚才能统计返工原因'
                : '选填，例如「客户微信确认可交付」'
            "
          />
        </el-form-item>
        <div v-if="reviewForm.result === 3" class="callout warn">
          退回会自动生成一条返工记录，并计入本月的返工统计。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="reviewSubmitting"
          @click="submitReview"
        >
          确认
        </el-button>
      </template>
    </el-dialog>

    <!-- 定价对话框（待定价订单） -->
    <el-dialog v-model="pricingVisible" title="录入明细与单价" width="900px" :close-on-click-modal="false">
      <div class="pricing-head" v-if="pricingOrder">
        <span class="mono">{{ pricingOrder.orderNo }}</span>
        <span>{{ pricingOrder.clientName }}</span>
        <span class="muted">{{ pricingOrder.title }}</span>
      </div>
      <OrderItemsEditor :items="pricingItems" :designers="designers" />
      <template #footer>
        <el-button @click="pricingVisible = false">取消</el-button>
        <el-button type="primary" :loading="pricingSubmitting" @click="submitPricing">保存定价</el-button>
      </template>
    </el-dialog>

    <!-- 回款登记 -->
    <el-dialog v-model="payVisible" title="登记回款" width="420px">
      <el-form label-width="80px">
        <el-form-item label="金额">
          <el-input-number v-model="payAmount" :min="0.01" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="payRemark" placeholder="如 订金 / 尾款" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="payVisible = false">取消</el-button>
        <el-button type="primary" :loading="paySubmitting" @click="submitPayment">确认登记</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import OrderItemsEditor from '@/components/OrderItemsEditor.vue'
import { newItem, type EditItem } from '@/components/types'
import {
  getDesignerOptions,
  getOrderDetail,
  getOrders,
  priceOrder,
  registerPayment,
  type EmployeeOption,
  type OrderDetailVO,
  type OrderListVO,
} from '@/api/order'
import {
  downloadDeliverable,
  getDeliverables,
  reviewDeliverable,
  uploadDeliverable,
  type DeliverableVO,
} from '@/api/deliverable'
import { exportOrders } from '@/api/report'
import { useUserStore } from '@/stores/user'

const user = useUserStore()
const router = useRouter()
const route = useRoute()

/** 订单台账导出只开放给能看客户金额的角色；设计师导出等于泄露总额 */
const canExportOrders = computed(
  () => user.isManager || user.roles.includes('FINANCE'),
)
const exporting = ref(false)

/** 抽屉里"累计已开票"：作废票不算，和后端 sumValidByOrder 口径一致 */
const invoicedOfDetail = computed(() =>
  (detail.value?.invoices || [])
    .filter((i) => i.status !== 3)
    .reduce((sum, i) => sum + (i.amount || 0), 0),
)

async function doExport() {
  exporting.value = true
  try {
    await exportOrders({
      keyword: query.keyword || undefined,
      status: query.status ?? undefined,
      paymentStatus: query.paymentStatus ?? undefined,
    })
  } finally {
    exporting.value = false
  }
}

const query = reactive({
  keyword: '',
  status: null as number | null,
  paymentStatus: null as number | null,
  pageNum: 1,
  pageSize: 20,
})

const rows = ref<OrderListVO[]>([])
const total = ref(0)
const loading = ref(false)

const statusOptions = [
  { value: 0, label: '草稿' },
  { value: 1, label: '待定价' },
  { value: 2, label: '已确认' },
  { value: 3, label: '执行中' },
  { value: 4, label: '已交付' },
  { value: 9, label: '已取消' },
]

function money(n?: number) {
  if (n === undefined || n === null) return '—'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function orderTagType(code?: number) {
  if (code === 4) return 'success'
  if (code === 9) return 'info'
  if (code === 1) return 'warning'
  return 'primary'
}

function payTagType(code?: number) {
  if (code === 2) return 'success'
  if (code === 1) return 'warning'
  return 'info'
}

async function load() {
  loading.value = true
  try {
    const res = await getOrders({
      keyword: query.keyword || undefined,
      status: query.status,
      paymentStatus: query.paymentStatus,
      pageNum: query.pageNum,
      pageSize: query.pageSize,
    })
    rows.value = res.list || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function reload() {
  query.pageNum = 1
  load()
}

/* ---------------- 详情 ---------------- */
const detailVisible = ref(false)
const detail = ref<OrderDetailVO | null>(null)
const detailTab = ref('items')

async function openDetail(row: OrderListVO) {
  detailVisible.value = true
  detail.value = null
  detailTab.value = 'items'
  deliverables.value = []
  detail.value = await getOrderDetail(row.id)
  loadDeliverables(row.id)
}

/* ---------------- 稿件版本 ---------------- */
const deliverables = ref<DeliverableVO[]>([])
const uploading = ref(false)

async function loadDeliverables(orderId: number) {
  try {
    deliverables.value = await getDeliverables(orderId)
  } catch {
    // 设计师看自己没参与的单会 403，此时稿件区留空即可，不要把整个抽屉打挂
    deliverables.value = []
  }
}

/**
 * el-upload 的自定义上传。
 * 不走它默认的 action：默认上传带不上 studio-token 头，且返回结构也不是我们的 R<T>。
 */
async function doUpload(options: { file: File }) {
  if (!detail.value) return
  uploading.value = true
  try {
    await uploadDeliverable(detail.value.id, options.file)
    ElMessage.success('已上传新版本')
    await loadDeliverables(detail.value.id)
  } finally {
    uploading.value = false
  }
}

function reviewTag(code: number) {
  if (code === 2) return 'success'
  if (code === 3) return 'danger'
  return 'warning'
}

async function doDownload(d: DeliverableVO) {
  try {
    await downloadDeliverable(d.id, d.fileName)
  } catch {
    // 文件丢失等错误已由拦截器提示
  }
}

/* ---------------- 稿件审核 ---------------- */
const reviewVisible = ref(false)
const reviewSubmitting = ref(false)
const reviewTarget = ref<DeliverableVO | null>(null)
const reviewForm = reactive({ result: 2 as 2 | 3, feedback: '' })

function openReview(d: DeliverableVO, result: 2 | 3) {
  reviewTarget.value = d
  reviewForm.result = result
  reviewForm.feedback = ''
  reviewVisible.value = true
}

async function submitReview() {
  if (!reviewTarget.value) return
  if (reviewForm.result === 3 && !reviewForm.feedback.trim()) {
    ElMessage.warning('退回必须填写原因')
    return
  }
  reviewSubmitting.value = true
  try {
    await reviewDeliverable(reviewTarget.value.id, {
      result: reviewForm.result,
      feedback: reviewForm.feedback.trim() || undefined,
    })
    ElMessage.success(reviewForm.result === 2 ? '已标记为客户通过' : '已登记退回，并生成返工记录')
    reviewVisible.value = false
    if (detail.value) {
      await loadDeliverables(detail.value.id)
      // 时间线不受影响，但状态可能已变，顺手刷新详情
      detail.value = await getOrderDetail(detail.value.id)
    }
  } finally {
    reviewSubmitting.value = false
  }
}

/* ---------------- 定价 ---------------- */
const pricingVisible = ref(false)
const pricingOrder = ref<OrderListVO | null>(null)
const pricingItems = ref<EditItem[]>([newItem()])
const pricingSubmitting = ref(false)
const designers = ref<EmployeeOption[]>([])

async function openPricing(row: OrderListVO) {
  pricingOrder.value = row
  pricingItems.value = [newItem()]
  pricingVisible.value = true
  if (designers.value.length === 0) {
    designers.value = await getDesignerOptions()
  }
}

async function submitPricing() {
  if (!pricingOrder.value) return
  for (let i = 0; i < pricingItems.value.length; i++) {
    const it = pricingItems.value[i]
    if (!it.itemName.trim()) {
      ElMessage.warning(`第 ${i + 1} 行还没填品项名称`)
      return
    }
    if (it.itemType === 1) {
      const sum = it.assignees.reduce((s, a) => s + a.ratioPct, 0)
      if (sum !== 100) {
        ElMessage.warning(`第 ${i + 1} 行分成比例合计是 ${sum}%，必须是 100%`)
        return
      }
      if (it.assignees.some((a) => !a.employeeId)) {
        ElMessage.warning(`第 ${i + 1} 行还有分成人没选`)
        return
      }
    }
  }

  pricingSubmitting.value = true
  try {
    await priceOrder(pricingOrder.value.id, {
      items: pricingItems.value.map((it) => ({
        itemName: it.itemName.trim(),
        qty: it.qty,
        unitPrice: it.unitPrice,
        itemType: it.itemType,
        assignees:
          it.itemType === 1
            ? it.assignees.map((a) => ({
                employeeId: a.employeeId as number,
                ratioBp: Math.round(a.ratioPct * 100),
              }))
            : [],
      })),
    })
    ElMessage.success('定价完成')
    pricingVisible.value = false
    load()
  } finally {
    pricingSubmitting.value = false
  }
}

/* ---------------- 回款 ---------------- */
const payVisible = ref(false)
const payAmount = ref<number>(0)
const payRemark = ref('')
const paySubmitting = ref(false)

function openPayment() {
  payAmount.value = 0
  payRemark.value = ''
  payVisible.value = true
}

async function submitPayment() {
  if (!detail.value) return
  if (!payAmount.value || payAmount.value <= 0) {
    ElMessage.warning('请输入回款金额')
    return
  }
  paySubmitting.value = true
  try {
    await registerPayment(detail.value.id, payAmount.value, payRemark.value || undefined)
    ElMessage.success('回款已登记，业绩按到账月份计入')
    payVisible.value = false
    await openDetail({ id: detail.value.id } as OrderListVO)
    load()
  } finally {
    paySubmitting.value = false
  }
}

onMounted(async () => {
  await load()
  // 从催收页/开票页点"看订单"跳过来时带 orderId，直接打开详情抽屉。
  // 打开后把 query 清掉：用户关掉抽屉再刷新，不应该又弹出来。
  const orderId = Number(route.query.orderId)
  if (orderId) {
    await openDetail({ id: orderId } as OrderListVO)
    router.replace({ path: '/orders' })
  }
})
</script>

<style scoped>
.clickable :deep(.el-table__row) {
  cursor: pointer;
}

.pager {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px;
}

.empty {
  padding: 24px;
  color: var(--t3);
  font-size: 12px;
}

.detail {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.meta {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 16px;
  font-size: 12.5px;
}

.meta .k {
  display: inline-block;
  width: 48px;
  color: var(--t3);
}

.drawer-foot {
  display: flex;
  justify-content: flex-end;
  padding-top: 4px;
}

/* ---------- 稿件版本 ---------- */
.files-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.dver {
  border: 1px solid var(--line, #e8edf3);
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 10px;
  background: #fff;
}

.dver-h {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  min-width: 0;
}

.dver-h .spacer {
  flex: 1;
}

.vtag {
  flex: none;
  font-family: var(--mono, ui-monospace, monospace);
  font-size: 11.5px;
  font-weight: 600;
  color: #2563eb;
  background: #eef4ff;
  border-radius: 4px;
  padding: 1px 6px;
}

.dname {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.dver-m {
  margin-top: 4px;
  font-size: 12px;
  color: var(--t3, #8b95a5);
}

.dfb {
  margin-top: 6px;
  font-size: 12.5px;
  padding: 6px 8px;
  border-radius: 6px;
  background: #f4f7fa;
  color: #334155;
}

.dfb.bad {
  background: #fef2f2;
  color: #b42318;
}

.drew {
  margin-top: 6px;
  font-size: 12px;
  color: #b45309;
}

.dver-a {
  display: flex;
  gap: 4px;
  margin-top: 6px;
}

.pricing-head {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 8px 12px;
  margin-bottom: 12px;
  background: #f7f9fb;
  border-radius: 8px;
  font-size: 12.5px;
}
</style>
