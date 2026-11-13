/** 录单 / 定价时，页面上正在编辑的一条明细 */
export interface EditItem {
  itemName: string
  spec?: string
  qty: number
  unitPrice: number
  /** 1=正常 2=赠品 3=返工 */
  itemType: number
  /** 分成人；比例用"百分比数值"（70 表示 70%），提交时再乘 100 转万分比 */
  assignees: { employeeId: number | null; ratioPct: number }[]
}

export function newItem(): EditItem {
  return {
    itemName: '',
    qty: 1,
    unitPrice: 0,
    itemType: 1,
    assignees: [{ employeeId: null, ratioPct: 100 }],
  }
}

export function amountOf(item: EditItem): number {
  // 金额一律按"分"四舍五入，避免 3 × 333.33 在前端显示 999.99、后端算 999.99 却因为
  // 浮点误差显示成 999.9900000001
  return Math.round((item.qty || 0) * (item.unitPrice || 0) * 100) / 100
}

export function ratioSum(item: EditItem): number {
  return item.assignees.reduce((s, a) => s + (a.ratioPct || 0), 0)
}
