import { downloadExcel } from './download'

/**
 * 报表导出。
 *
 * 【为什么每个函数都要传 fallbackName】
 * 正常情况下文件名由后端响应头给（带期间、日期），前端这个只作兜底：
 * 某些反向代理会吞掉 Content-Disposition，那时用户至少还能得到一个合理的文件名。
 */

export function exportSalary(period: string, keyword?: string) {
  return downloadExcel('/reports/salary', { period, keyword }, `工资表-${period}.xlsx`)
}

export function exportOrders(query: {
  keyword?: string
  status?: number | null
  paymentStatus?: number | null
  dateFrom?: string
  dateTo?: string
}) {
  return downloadExcel('/reports/orders', { ...query }, '订单台账.xlsx')
}

export function exportReceivables(scope: string, keyword?: string) {
  return downloadExcel('/reports/receivables', { scope, keyword }, '催收清单.xlsx')
}

export function exportInvoices(status?: number | null, period?: string, keyword?: string) {
  return downloadExcel('/reports/invoices', { status, period, keyword }, '开票台账.xlsx')
}

export function exportPerformance(period: string) {
  return downloadExcel('/reports/performance', { period }, `我的业绩-${period}.xlsx`)
}
