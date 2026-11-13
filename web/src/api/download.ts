import axios from 'axios'
import { ElMessage } from 'element-plus'
import { TOKEN_KEY } from './http'

/**
 * 文件下载（导出 Excel）。
 *
 * 【为什么不用 http.ts 里那个实例】
 * 那个实例的响应拦截器把 {code,msg,data} 解包，并且对错误统一弹提示。
 * 导出接口返回的是二进制流，一旦后端报错（比如"超过导出上限"），
 * 响应体是 JSON 但 responseType 是 blob，拦截器解不开，只会弹一句"请求失败（400）"，
 * 用户完全不知道要缩小日期范围。这里单独建一个实例，把 blob 里的 JSON 读出来提示。
 *
 * 【为什么文件名要从响应头取】
 * 后端按"工资表-2026-11.xlsx"命名，前端写死一个名字的话，
 * 用户连下三个月会得到三个同名文件，浏览器自动加 (1)(2)(3)，事后分不清谁是谁。
 */
const raw = axios.create({
  baseURL: '/api',
  timeout: 60000,
})

raw.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers['studio-token'] = token
  }
  return config
})

/** 从 Content-Disposition 里取文件名，优先 filename*（RFC 5987 的 UTF-8 形式） */
function filenameFrom(disposition?: string): string | null {
  if (!disposition) return null
  const star = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
  if (star?.[1]) {
    try {
      return decodeURIComponent(star[1])
    } catch {
      return null
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(disposition)
  return plain?.[1] ? decodeURIComponent(plain[1]) : null
}

function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  // 不释放会一直占内存：连续导出几十次后页面会明显变卡
  URL.revokeObjectURL(url)
}

export async function downloadExcel(
  url: string,
  params: Record<string, unknown>,
  fallbackName: string,
) {
  try {
    const res = await raw.get(url, { params, responseType: 'blob' })
    const name = filenameFrom(res.headers['content-disposition'] as string | undefined) || fallbackName
    saveBlob(res.data as Blob, name)
    ElMessage.success('已开始下载')
  } catch (e: any) {
    let msg = '导出失败，请稍后重试'
    const body = e?.response?.data
    if (body instanceof Blob) {
      const text = await body.text()
      try {
        msg = JSON.parse(text).msg || msg
      } catch {
        // 不是 JSON（网关错误页、404 HTML），保留默认提示
      }
    } else if (e?.response?.status === 401) {
      localStorage.removeItem(TOKEN_KEY)
      location.hash = '#/login'
      return
    }
    ElMessage.error(msg)
  }
}
