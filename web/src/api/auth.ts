import { request } from './http'

/**
 * 认证相关接口。
 *
 * 【为什么类型里几乎所有字段都可选】
 * 后端全局配置了 default-property-inclusion: non_null，null 字段不会出现在 JSON 里。
 * 如果把类型写成必填，编译期一切正常，运行时却拿到 undefined，
 * 模板里一取值就报错 —— 这类问题在联调阶段最难查。宁可在类型上如实反映。
 */

export interface CurrentUser {
  userId?: number
  username?: string
  employeeId?: number
  employeeName?: string
  roleCodes?: string[]
  scope?: string
  mustChangePwd?: boolean
}

export interface LoginResult {
  token: string
  tokenName?: string
  mustChangePwd?: boolean
  user: CurrentUser
}

export function login(username: string, password: string) {
  return request<LoginResult>({
    url: '/auth/login',
    method: 'post',
    data: { username, password },
  })
}

export function getMe() {
  return request<CurrentUser>({ url: '/auth/me', method: 'get' })
}

export function logout() {
  return request<void>({ url: '/auth/logout', method: 'post' })
}

export function changePassword(oldPassword: string, newPassword: string) {
  return request<void>({
    url: '/auth/password',
    method: 'post',
    data: { oldPassword, newPassword },
  })
}
