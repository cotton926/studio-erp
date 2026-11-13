import { request } from './http'

/**
 * 分页结果，与后端 PageResult 对齐。
 *
 * 【为什么字段叫 list 而不是 records】
 * 后端返回的是 MyBatis-Plus 风格：{pageNum, pageSize, total, list}。
 * 一开始按常见的 records 写，结果表格永远空 —— 而且不报错，
 * 只是 rows 拿到 undefined。类型定义必须跟真实响应一字不差。
 */
export interface PageResult<T> {
  pageNum: number
  pageSize: number
  total: number
  list: T[]
}

export interface OrderListVO {
  id: number
  orderNo: string
  clientName?: string
  title?: string
  category?: string
  totalAmount?: number
  myAmount?: number
  status?: number
  statusText?: string
  paymentStatus?: number
  paymentStatusText?: string
  signDate?: string
  expectDeliverDate?: string
  designerNames?: string
  paidAmount?: number
}

export interface AssigneeItem {
  employeeId: number
  employeeName?: string
  ratioBp?: number
  ratioText?: string
}

export interface OrderItemVO {
  id: number
  lineNo?: number
  itemName: string
  spec?: string
  unit?: string
  qty?: number
  unitPrice?: number
  amount?: number
  itemType?: number
  itemTypeText?: string
  remark?: string
  mine?: boolean
  assignees?: AssigneeItem[]
}

export interface OrderDetailVO extends OrderListVO {
  unpaidAmount?: number
  actualDeliverDate?: string
  remark?: string
  items: OrderItemVO[]
  /** 状态流转时间线，两种角色都能看到（不含金额） */
  timeline?: TimelineVO[]
  /** 收款计划：设计师视角后端不返回（字段缺省，不是空数组） */
  plans?: import('./receivable').PlanBriefVO[]
  /** 开票记录：同上，带金额，只对能看金额的角色返回 */
  invoices?: import('./invoice').InvoiceVO[]
}

export interface TimelineVO {
  fromStatus: number | null
  fromStatusText: string | null
  toStatus: number
  toStatusText: string
  operatorId: number
  operatorName: string
  remark: string | null
  createdAt: string
}

export interface OrderQuery {
  keyword?: string
  status?: number | null
  paymentStatus?: number | null
  pageNum?: number
  pageSize?: number
}

export function getOrders(query: OrderQuery) {
  return request<PageResult<OrderListVO>>({ url: '/orders', method: 'get', params: query })
}

export function getOrderDetail(id: number) {
  return request<OrderDetailVO>({ url: `/orders/${id}`, method: 'get' })
}

export function getPendingPricing() {
  return request<OrderListVO[]>({ url: '/orders/pending-pricing', method: 'get' })
}

/** 提单：客服只填客户名与项目，不出价 */
export interface OrderCreatePayload {
  clientName: string
  title: string
  category?: string
  expectDeliverDate?: string
  remark?: string
}

export function createOrder(data: OrderCreatePayload) {
  return request<number>({ url: '/orders', method: 'post', data })
}

/** 定价：主管录明细与单价、指定分成人 */
export interface PricingItem {
  itemName: string
  spec?: string
  unit?: string
  qty: number
  unitPrice: number
  itemType?: number
  assignees: { employeeId: number; ratioBp: number }[]
}

export interface OrderPricingPayload {
  signDate?: string
  items: PricingItem[]
  remark?: string
}

export function priceOrder(id: number, data: OrderPricingPayload) {
  return request<void>({ url: `/orders/${id}/pricing`, method: 'put', data })
}

export function changeOrderStatus(id: number, status: number) {
  return request<void>({
    url: `/orders/${id}/status`,
    method: 'put',
    params: { status },
  })
}

export function registerPayment(id: number, amount: number, remark?: string) {
  return request<number>({
    url: `/orders/${id}/payments`,
    method: 'post',
    data: { amount, remark },
  })
}

/** 下单可选的参与人（在职且参与分成） */
export interface EmployeeOption {
  id: number
  name: string
  empNo?: string
  deptName?: string
  position?: string
}

export function getDesignerOptions() {
  return request<EmployeeOption[]>({ url: '/employees/options', method: 'get' })
}
