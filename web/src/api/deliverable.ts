import { request } from './http'

/** 稿件版本（与后端 DeliverableVO 对应） */
export interface DeliverableVO {
  id: number
  orderItemId: number | null
  versionNo: number
  versionText: string
  fileName: string
  fileType: string
  fileSize: number
  fileSizeText: string
  reviewResult: number
  reviewResultText: string
  feedback: string | null
  uploadedBy: number
  uploadedByName: string | null
  reviewedByName: string | null
  reviewedAt: string | null
  createdAt: string
  reworks: ReworkVO[]
}

export interface ReworkVO {
  id: number
  reason: string | null
  reworkByName: string | null
  createdAt: string
}

export interface ReworkStatVO {
  period: string
  totalVersions: number
  rejectedVersions: number
  reworkCount: number
  passRate: number
  persons: {
    employeeId: number | null
    employeeName: string
    versions: number
    rejected: number
    reworks: number
    passRate: number
  }[]
}

export function getDeliverables(orderId: number) {
  return request<DeliverableVO[]>({ url: `/orders/${orderId}/deliverables`, method: 'get' })
}

/**
 * 上传新版本。
 *
 * 【为什么版本号不在这里传】由服务端取 MAX(version_no)+1，
 * 前端即使传了也会被忽略；并发时数据库唯一索引兜底。
 */
export function uploadDeliverable(orderId: number, file: File, orderItemId?: number) {
  const form = new FormData()
  form.append('file', file)
  if (orderItemId) {
    form.append('orderItemId', String(orderItemId))
  }
  return request<number>({
    url: `/orders/${orderId}/deliverables`,
    method: 'post',
    data: form,
    // 交给浏览器自己带 boundary，手写 multipart 头会导致后端解析失败
    headers: { 'Content-Type': undefined as unknown as string },
    timeout: 120000,
  })
}

export function reviewDeliverable(
  id: number,
  body: { result: 2 | 3; feedback?: string; reworkBy?: number },
) {
  return request<void>({ url: `/deliverables/${id}/review`, method: 'put', data: body })
}

/**
 * 下载稿件。
 *
 * 【为什么不用 <a href> 直链】下载接口需要 studio-token 头，
 * 直链带不上请求头，后端会当成未登录拒绝。
 * 这里取回 blob 再造临时链接，token 由 axios 拦截器统一附加。
 */
export async function downloadDeliverable(id: number, fileName: string) {
  const blob = await request<Blob>({
    url: `/deliverables/${id}/file`,
    method: 'get',
    responseType: 'blob',
    timeout: 120000,
  })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  // 不释放会一直占着内存，连续下载几十个文件后页面明显变卡
  URL.revokeObjectURL(url)
}

export function getReworkStats(period?: string) {
  return request<ReworkStatVO>({
    url: '/deliverables/stats',
    method: 'get',
    params: period ? { period } : {},
  })
}
