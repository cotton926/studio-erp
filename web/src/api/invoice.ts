import { request } from './http'
import type { PageResult } from './order'

export interface InvoiceVO {
  id: number
  orderId: number
  orderNo: string | null
  clientName: string | null
  title: string | null
  taxNo: string | null
  invoiceNo: string | null
  amount: number
  taxRatePercent: number
  taxAmount: number
  netAmount: number
  invoiceType: number
  invoiceTypeText: string
  content: string | null
  issueDate: string | null
  status: number
  statusText: string
  applyBy: number | null
  applyByName: string | null
  appliedAt: string | null
  voidReason: string | null
  remark: string | null
  createdAt: string
  orderTotal: number | null
  orderInvoiced: number | null
  orderPaid: number | null
}

export interface InvoiceSummaryVO {
  pendingCount: number
  pendingAmount: number
  monthIssuedCount: number
  monthIssuedAmount: number
  totalIssuedAmount: number
}

export interface InvoiceApplyPayload {
  orderId: number
  amount: number
  taxRatePercent?: number
  invoiceType?: number
  title?: string
  taxNo?: string
  content?: string
  remark?: string
}

export function getInvoices(params: {
  status?: number
  period?: string
  keyword?: string
  pageNum?: number
  pageSize?: number
}) {
  // 台账已改为分页接口，避免发票越积越多后一次全量拉取拖慢页面
  return request<PageResult<InvoiceVO>>({ url: '/invoices', method: 'get', params })
}

export function getInvoiceSummary() {
  return request<InvoiceSummaryVO>({ url: '/invoices/summary', method: 'get' })
}

export function getOrderInvoices(orderId: number) {
  return request<InvoiceVO[]>({ url: `/invoices/orders/${orderId}`, method: 'get' })
}

export function applyInvoice(data: InvoiceApplyPayload) {
  return request<number>({ url: '/invoices', method: 'post', data })
}

export function issueInvoice(id: number, invoiceNo: string, issueDate?: string) {
  return request<void>({
    url: `/invoices/${id}/issue`,
    method: 'put',
    data: { invoiceNo, issueDate },
  })
}

export function voidInvoice(id: number, reason: string) {
  return request<void>({ url: `/invoices/${id}/void`, method: 'put', data: { reason } })
}

export const INVOICE_TYPES = [
  { value: 1, label: '普通发票' },
  { value: 2, label: '专用发票' },
]
