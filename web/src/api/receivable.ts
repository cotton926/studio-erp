import { request } from './http'

/** 催收清单一行（与后端 CollectionVO 对齐；record 序列化成小驼峰） */
export interface CollectionVO {
  planId: number
  orderId: number
  orderNo: string
  clientName: string
  title: string
  seq: number
  planName: string
  planAmount: number
  receivedAmount: number
  unpaidAmount: number
  planDate: string | null
  planStatus: number
  planStatusText: string
  /** 距应收日天数：正数未到期，0 今天到期，负数已逾期 */
  daysToDue: number
  overdueDays: number
  inWindow: boolean
  remindCount: number
  lastRemindAt: string | null
  promiseDate: string | null
  promiseAmount: number | null
  promiseBroken: boolean
  csName: string | null
  csPhone: string | null
  orderTotal: number
  orderPaid: number
}

export interface CollectionSummaryVO {
  windowText: string
  windowActive: boolean
  daysToWindow: number
  totalUnpaid: number
  windowUnpaid: number
  windowCount: number
  overdueUnpaid: number
  overdueCount: number
  monthCollected: number
  promisedCount: number
  remindedThisMonth: number
}

export interface CollectLogVO {
  id: number
  orderId: number
  planId: number | null
  action: number
  actionText: string
  result: number
  resultText: string
  promiseDate: string | null
  promiseAmount: number | null
  remark: string | null
  operatorName: string
  createdAt: string
}

export interface PlanBriefVO {
  id: number
  seq: number
  planName: string
  planAmount: number
  receivedAmount: number
  unpaidAmount: number
  planDate: string | null
  status: number
  statusText: string
  remindCount: number
  lastRemindAt: string | null
  promiseDate: string | null
}

/** WINDOW=本期窗口 / OVERDUE=已逾期 / ALL=全部未结清 */
export type CollectScope = 'WINDOW' | 'OVERDUE' | 'ALL'

export function getCollectionSummary() {
  return request<CollectionSummaryVO>({ url: '/receivables/summary', method: 'get' })
}

export function getCollection(scope: CollectScope, keyword?: string) {
  return request<CollectionVO[]>({
    url: '/receivables/collection',
    method: 'get',
    params: { scope, keyword },
  })
}

export interface CollectPayload {
  action: number
  result: number
  promiseDate?: string | null
  promiseAmount?: number | null
  remark?: string
}

export function collect(planId: number, data: CollectPayload) {
  return request<number>({ url: `/receivables/${planId}/collect`, method: 'post', data })
}

export function getCollectLogs(orderId: number) {
  return request<CollectLogVO[]>({ url: `/receivables/orders/${orderId}/logs`, method: 'get' })
}

/** 催收方式 / 结果：与后端 CollectAction、CollectResult 的码值一一对应 */
export const COLLECT_ACTIONS = [
  { value: 1, label: '电话' },
  { value: 2, label: '微信/QQ' },
  { value: 3, label: '邮件' },
  { value: 4, label: '上门' },
  { value: 5, label: '其他' },
]

export const COLLECT_RESULTS = [
  { value: 1, label: '承诺付款' },
  { value: 2, label: '推诿拖延' },
  { value: 3, label: '已付款' },
  { value: 4, label: '联系不上' },
  { value: 5, label: '需内部协调' },
]
