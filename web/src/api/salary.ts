import { request } from './http'

export interface SalaryVO {
  employeeId?: number
  empNo?: string
  employeeName?: string
  deptName?: string
  position?: string
  period: string
  performance: number
  signAmount?: number
  rateBp?: number
  rateText?: string
  commission: number
  baseSalary: number
  empLevel?: string
  empLevelLabel?: string
  baseSalarySource?: 'LEVEL' | 'PERSONAL'
  baseSalarySourceLabel?: string
  baseSalaryNote?: string
  payable: number
  tierMode?: string
  tierModeLabel?: string
  clawback?: boolean
  nextTierAt?: number
  gapToNextTier?: number
  gainIfReachNext?: number
  nextTierRateBp?: number
  nextTierRateText?: string
  tierRemark?: string
}

export interface TierVO {
  id: number
  levelNo: number
  minAmount: number
  maxAmount?: number
  rateBp: number
  rateText: string
  remark?: string
}

export interface BasePolicyVO {
  id: number
  levelCode: string
  levelName: string
  baseSalary: number
  sortNo?: number
  remark?: string
}

export function getMySalary(period: string) {
  return request<SalaryVO>({ url: '/salary/my', method: 'get', params: { period } })
}

export function getTeamSalary(period: string, keyword?: string) {
  return request<SalaryVO[]>({
    url: '/salary/team',
    method: 'get',
    params: { period, keyword },
  })
}

export function getTiers() {
  return request<TierVO[]>({ url: '/salary/tiers', method: 'get' })
}

export function getBasePolicy() {
  return request<BasePolicyVO[]>({ url: '/salary/base-policy', method: 'get' })
}

/**
 * 调整某人底薪。
 *
 * 【为什么允许传 null】
 * 底薪有两层：级别政策是默认值，这里传数字表示"这个人是特例"，
 * 传 null 表示"撤销特例、回到级别政策"。用 0 表示撤销是错的 ——
 * 0 是合法特例（纯提成实习生），两者混在一起就再也分不清。
 */
export function updateBaseSalary(employeeId: number, baseSalary: number | null) {
  return request<void>({
    url: `/salary/base/${employeeId}`,
    method: 'put',
    data: { baseSalary },
  })
}
