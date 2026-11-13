import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

/**
 * 统一请求封装。
 *
 * 【为什么要在拦截器里就把 {code,msg,data} 拆开】
 * 后端所有接口都包了一层 R<T>。如果每个页面都写 res.data.data，
 * 迟早会有人写成 res.data（拿到整个包装体）然后在模板里取值失败，
 * 而报错信息只会说"cannot read property of undefined"，排查很久。
 * 这里统一解包，页面拿到的就是业务对象本身。
 *
 * 【为什么 401 要在这里处理，而不是每个页面自己判断】
 * token 过期是必然会发生的（默认有效期到了、服务重启、账号被停用）。
 * 分散处理的结果通常是：某个页面忘了判断，用户看到一片空白，
 * 以为系统坏了。集中跳登录页，用户重登一次就能继续。
 */

export const TOKEN_KEY = 'studio-token'

const http = axios.create({
  baseURL: '/api',
  timeout: 20000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    // 后端 Sa-Token 的自定义头，不是标准的 Authorization
    config.headers['studio-token'] = token
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data
    // 文件下载之类的非 R 结构直接返回
    if (!body || typeof body !== 'object' || !('success' in body)) {
      return body
    }
    if (body.success) {
      return body.data
    }
    // 业务失败：这里的 msg 是后端写好的中文，直接展示比"操作失败"有用得多
    ElMessage.error(body.msg || '操作失败')
    return Promise.reject(new Error(body.msg || '操作失败'))
  },
  (error) => {
    const status = error?.response?.status
    const body = error?.response?.data
    const code = body?.code
    const msg = body?.msg

    if (status === 401 || code === 40100) {
      localStorage.removeItem(TOKEN_KEY)
      ElMessage.warning(msg || '登录已失效，请重新登录')
      // 用整页跳转而不是 router.push：避免在拦截器里引入 router 造成循环依赖，
      // 顺便把内存里的旧状态全部清干净
      if (!location.hash.startsWith('#/login')) {
        location.hash = '#/login'
      }
      return Promise.reject(error)
    }

    if (status === 403) {
      ElMessage.error(msg || '没有权限执行此操作')
      return Promise.reject(error)
    }

    ElMessage.error(msg || (status ? `请求失败（${status}）` : '网络异常，请检查后端是否已启动'))
    return Promise.reject(error)
  },
)

/**
 * 带类型的请求入口。
 *
 * 拦截器已经把 R<T> 解成 T，但 axios 的类型仍以为返回的是 AxiosResponse，
 * 所以这里用泛型 + 断言把类型对齐。这是唯一一处"骗编译器"的地方，
 * 集中在这里比让每个调用方都写 as any 要好。
 */
export function request<T>(config: AxiosRequestConfig): Promise<T> {
  return http.request(config) as unknown as Promise<T>
}

export default http
