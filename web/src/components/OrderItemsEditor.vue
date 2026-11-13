<template>
  <div class="editor">
    <!-- 列头：明细行的数量/单价只是两个数字框，没有标题用户分不清哪个是哪个 -->
    <div class="head">
      <span class="idx"></span>
      <span class="name">品项名称</span>
      <span class="type">类型</span>
      <span class="qty">数量</span>
      <span class="price">单价（元）</span>
      <span class="amount">金额</span>
      <span class="op"></span>
    </div>

    <div v-for="(item, i) in items" :key="i" class="item">
      <div class="line">
        <span class="idx">{{ i + 1 }}</span>
        <el-input v-model="item.itemName" placeholder="品项名称，如 画册设计 48P" class="name" />
        <el-select v-model="item.itemType" class="type" @change="onTypeChange(item)">
          <el-option :value="1" label="正常" />
          <el-option :value="2" label="赠品" />
          <el-option :value="3" label="返工" />
        </el-select>
        <el-input-number v-model="item.qty" :min="0.01" :precision="2" :controls="false" class="qty" placeholder="数量" />
        <el-input-number v-model="item.unitPrice" :min="0" :precision="2" :controls="false" class="price" placeholder="单价" />
        <span class="amount mono">{{ money(amountOf(item)) }}</span>
        <el-button link type="danger" :disabled="items.length === 1" @click="removeItem(i)">删除</el-button>
      </div>

      <div class="assignees">
        <span class="label">分成人</span>
        <div v-for="(a, j) in item.assignees" :key="j" class="assignee">
          <el-select v-model="a.employeeId" placeholder="选择员工" filterable class="who">
            <el-option v-for="d in designers" :key="d.id" :label="`${d.name}（${d.empNo}）`" :value="d.id" />
          </el-select>
          <el-input-number v-model="a.ratioPct" :min="0" :max="100" :step="5" :controls="false" class="ratio" />
          <span class="pct">%</span>
          <el-button link :disabled="item.assignees.length === 1" @click="removeAssignee(item, j)">×</el-button>
        </div>
        <el-button link type="primary" @click="addAssignee(item)">+ 添加协作者</el-button>
        <span class="sum" :class="{ bad: item.itemType === 1 && ratioSum(item) !== 100 }">
          合计 {{ ratioSum(item) }}%
        </span>
      </div>
    </div>

    <div class="foot">
      <el-button @click="addItem">+ 添加明细行</el-button>
      <span class="spacer"></span>
      <span class="total">合计 <b>{{ money(total) }}</b> 元</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { EmployeeOption } from '@/api/order'
import { amountOf, newItem, ratioSum, type EditItem } from './types'

/**
 * 明细编辑器：录单与定价共用一个组件。
 *
 * 【为什么提成比例让用户填整数百分比】
 * 后端存的是万分比（7000 = 70%）。让主管在界面上填 7000 一定会填错，
 * 填 70 则符合直觉；转换只在这一个地方做，不会出现"某个页面忘了乘 100"。
 *
 * 【为什么赠品/返工行不校验 100%】
 * 后端 {@code PerformanceAllocator} 只对 itemType=1 的行要求比例合计 100%，
 * 赠品行本来就不参与业绩。前端跟着放宽，否则主管为了通过校验
 * 只能在赠品行随便填个人，反而污染数据。
 */
const props = defineProps<{
  items: EditItem[]
  designers: EmployeeOption[]
}>()

const total = computed(() =>
  props.items.reduce((s, it) => s + (it.itemType === 1 ? amountOf(it) : 0), 0),
)

function money(n: number) {
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function addItem() {
  props.items.push(newItem())
}

function removeItem(i: number) {
  props.items.splice(i, 1)
}

function addAssignee(item: EditItem) {
  item.assignees.push({ employeeId: null, ratioPct: 0 })
}

function removeAssignee(item: EditItem, j: number) {
  item.assignees.splice(j, 1)
}

/** 切成赠品/返工时把比例清空，避免把"100%"这种无意义的值带到后端 */
function onTypeChange(item: EditItem) {
  if (item.itemType !== 1) {
    item.assignees = [{ employeeId: null, ratioPct: 0 }]
  } else if (item.assignees.length === 0) {
    item.assignees = [{ employeeId: null, ratioPct: 100 }]
  }
}

defineExpose({ total })
</script>

<style scoped>
.editor {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.head {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 0 12px;
  font-size: 11px;
  color: var(--t3);
}

.head .amount {
  text-align: right;
}

.op {
  width: 40px;
  flex: 0 0 40px;
}

.item {
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 10px 12px;
  background: #fcfcfd;
}

.line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.idx {
  width: 18px;
  color: var(--t3);
  font-size: 12px;
  text-align: center;
}

.name {
  flex: 1;
  min-width: 160px;
}

.type {
  width: 90px;
}

.qty {
  width: 90px;
}

.price {
  width: 110px;
}

.amount {
  width: 100px;
  text-align: right;
  font-size: 12.5px;
}

.assignees {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 8px;
  padding-left: 26px;
}

.label {
  font-size: 12px;
  color: var(--t3);
}

.assignee {
  display: flex;
  align-items: center;
  gap: 4px;
}

.who {
  width: 150px;
}

.ratio {
  width: 64px;
}

.pct {
  font-size: 12px;
  color: var(--t3);
}

.sum {
  margin-left: auto;
  font-size: 12px;
  color: var(--t3);
}

.sum.bad {
  color: var(--red);
  font-weight: 600;
}

.foot {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-top: 4px;
}

.spacer {
  flex: 1;
}

.total {
  font-size: 12px;
  color: var(--t2);
}

.total b {
  font-size: 15px;
  color: var(--blue);
  font-variant-numeric: tabular-nums;
  margin: 0 2px;
}
</style>
