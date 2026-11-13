<template>
  <div class="page">
    <div class="toolbar">
      <span class="muted">客户名称与单价都由人工填写，系统只负责把账算准</span>
      <div class="spacer"></div>
      <el-button @click="fillDemo">填入示例</el-button>
      <el-button @click="router.back()">返回</el-button>
    </div>

    <div class="card">
      <div class="card-h">
        <h2>订单信息</h2>
        <span class="hint">签单日期决定业绩归属月份</span>
      </div>
      <div class="card-b">
        <el-form :model="form" label-width="88px" class="form">
          <el-form-item label="客户名称" required>
            <el-input v-model="form.clientName" placeholder="如 杭州青禾文化" maxlength="60" />
          </el-form-item>
          <el-form-item label="项目名称" required>
            <el-input v-model="form.title" placeholder="如 品牌画册设计" maxlength="120" />
          </el-form-item>
          <el-form-item label="品类">
            <el-input v-model="form.category" placeholder="如 画册 / 包装 / 海报" maxlength="40" />
          </el-form-item>
          <el-form-item label="签单日期">
            <el-date-picker v-model="form.signDate" type="date" value-format="YYYY-MM-DD" style="width: 180px" />
          </el-form-item>
          <el-form-item label="期望交付">
            <el-date-picker
              v-model="form.expectDeliverDate"
              type="date"
              value-format="YYYY-MM-DD"
              style="width: 180px"
            />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
          </el-form-item>
        </el-form>
      </div>
    </div>

    <div class="card">
      <div class="card-h">
        <h2>明细与分成</h2>
        <span class="hint">正常行必须指定分成人且合计 100%；赠品、返工行不参与业绩</span>
      </div>
      <div class="card-b">
        <OrderItemsEditor :items="items" :designers="designers" />
      </div>
    </div>

    <div class="submit-bar">
      <div class="summary">
        <span>共 {{ items.length }} 行</span>
        <span>计入业绩金额 <b>{{ money(totalAmount) }}</b> 元</span>
      </div>
      <div class="spacer"></div>
      <el-button @click="reset">清空</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">保存并定价</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import OrderItemsEditor from '@/components/OrderItemsEditor.vue'
import { amountOf, newItem, ratioSum, type EditItem } from '@/components/types'
import { createOrder, getDesignerOptions, priceOrder, type EmployeeOption } from '@/api/order'

const router = useRouter()

const form = reactive({
  clientName: '',
  title: '',
  category: '',
  signDate: today(),
  expectDeliverDate: '',
  remark: '',
})

const items = ref<EditItem[]>([newItem()])
const designers = ref<EmployeeOption[]>([])
const submitting = ref(false)

function today() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function money(n: number) {
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const totalAmount = computed(() =>
  items.value.reduce((s, it) => s + (it.itemType === 1 ? amountOf(it) : 0), 0),
)

function reset() {
  form.clientName = ''
  form.title = ''
  form.category = ''
  form.expectDeliverDate = ''
  form.remark = ''
  items.value = [newItem()]
}

function fillDemo() {
  form.clientName = '杭州青禾文化'
  form.title = '品牌画册设计'
  form.category = '画册'
  const first = designers.value[0]
  items.value = [
    {
      itemName: '画册内页设计 48P',
      qty: 48,
      unitPrice: 350,
      itemType: 1,
      assignees: [{ employeeId: first?.id ?? null, ratioPct: 70 }],
    },
    {
      itemName: '封面设计',
      qty: 1,
      unitPrice: 2000,
      itemType: 1,
      assignees: [{ employeeId: first?.id ?? null, ratioPct: 30 }],
    },
  ]
}

/**
 * 提交。
 *
 * 【为什么是"先创建、再定价"两次请求，而不是一个接口做完】
 * 后端把"提单"和"出价"设计成两个动作：客服提单时还没有价格，
 * 主管定价是另一个人、另一个时间点干的活。合并成一个接口后，
 * 权限就没法分了（谁能同时有提单权和定价权？）。
 * 前端在这里串起来，用户体验上仍是一次提交。
 *
 * 【失败会怎样】
 * 创建成功、定价失败时，会留下一条"待定价"订单。这是安全的失败方式：
 * 订单在列表里可见、可重新定价，不会出现"钱收了但系统里没这单"。
 */
async function submit() {
  if (!form.clientName.trim() || !form.title.trim()) {
    ElMessage.warning('请填写客户名称与项目名称')
    return
  }
  for (let i = 0; i < items.value.length; i++) {
    const it = items.value[i]
    if (!it.itemName.trim()) {
      ElMessage.warning(`第 ${i + 1} 行还没填品项名称`)
      return
    }
    if (it.itemType === 1) {
      if (it.unitPrice < 0) {
        ElMessage.warning(`第 ${i + 1} 行单价不能为负`)
        return
      }
      if (ratioSum(it) !== 100) {
        ElMessage.warning(`第 ${i + 1} 行分成比例合计是 ${ratioSum(it)}%，必须是 100%`)
        return
      }
      if (it.assignees.some((a) => !a.employeeId)) {
        ElMessage.warning(`第 ${i + 1} 行还有分成人没选`)
        return
      }
    }
  }

  submitting.value = true
  try {
    const orderId = await createOrder({
      clientName: form.clientName.trim(),
      title: form.title.trim(),
      category: form.category || undefined,
      expectDeliverDate: form.expectDeliverDate || undefined,
      remark: form.remark || undefined,
    })

    await priceOrder(orderId, {
      signDate: form.signDate || undefined,
      remark: form.remark || undefined,
      items: items.value.map((it) => ({
        itemName: it.itemName.trim(),
        qty: it.qty,
        unitPrice: it.unitPrice,
        itemType: it.itemType,
        assignees:
          it.itemType === 1
            ? it.assignees
                .filter((a) => a.employeeId)
                .map((a) => ({ employeeId: a.employeeId as number, ratioBp: Math.round(a.ratioPct * 100) }))
            : [],
      })),
    })

    ElMessage.success('订单已保存并定价')
    router.push('/orders')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  designers.value = await getDesignerOptions()
})
</script>

<style scoped>
.form {
  max-width: 640px;
}

.submit-bar {
  position: sticky;
  bottom: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 10px 16px;
  box-shadow: 0 -2px 12px rgba(31, 35, 41, 0.04);
}

.summary {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: var(--t2);
}

.summary b {
  color: var(--blue);
  font-size: 14px;
  font-variant-numeric: tabular-nums;
}

.spacer {
  flex: 1;
}
</style>
