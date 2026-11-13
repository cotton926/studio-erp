import { request } from './http'

/** 趋势上的一个点；label 是给图表用的短文案（如 "11月"） */
export interface TrendPoint {
  period: string
  label: string
  amount: number
  current?: boolean
}

/**
 * 我参与的一张订单。
 *
 * 注意这里没有 orderTotal / 客户实收：后端对设计师做了金额遮蔽，
 * 只返回"我的分成"折算出来的三个数。前端也不应该显示整单金额。
 */
export interface MyPerfOrder {
  orderId: number
  orderNo: string
  clientName: string
  title?: string
  itemNames?: string
  signDate: string
  statusText?: string
  statusCode?: number
  paymentStatusText?: string
  ratioText?: string
  mySignAmount: number
  myPaidAmount: number
  myUnpaid: number
}

export interface PerfSummary {
  period: string
  employeeId?: number
  employeeName?: string
  performance: number
  lastMonthPerformance?: number
  momRate?: number
  yearPerformance?: number
  orderCount?: number
  unpaidAmount?: number
  signAmount?: number
  trend: TrendPoint[]
  orders: MyPerfOrder[]
}

export function getPerfSummary(period: string) {
  return request<PerfSummary>({
    url: '/performance/summary',
    method: 'get',
    params: { period },
  })
}

export interface LedgerRow {
  id: number
  employeeId: number
  orderId?: number
  orderItemId?: number
  ledgerType: number
  amount: number
  ratioBp?: number
  period: string
  occurDate?: string
  remark?: string
}

export function getLedger(period: string) {
  return request<LedgerRow[]>({
    url: '/performance/ledger',
    method: 'get',
    params: { period },
  })
}
