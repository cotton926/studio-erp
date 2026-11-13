import { defineStore } from 'pinia'
import { getMe, login as loginApi, logout as logoutApi, type CurrentUser } from '@/api/auth'
import { TOKEN_KEY } from '@/api/http'

/**
 * 登录态。
 *
 * 【为什么用户信息不落 localStorage】
 * 只存 token。用户信息每次刷新页面重新拉一次 /auth/me。
 * 如果连角色一起缓存，管理员把某人的主管角色撤掉后，
 * 这个人只要不重新登录就还能看到管理菜单 —— 界面和行为不一致，
 * 而且没人会想到是缓存问题。多一次 5ms 的请求换这个确定性，很划算。
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: null as CurrentUser | null,
    loading: false,
  }),

  getters: {
    isLogin: (s) => !!s.token,
    roles: (s) => s.user?.roleCodes || [],
    displayName: (s) => s.user?.employeeName || s.user?.username || '',
    /** 主管或管理员：能看团队数据、能定价 */
    isManager(): boolean {
      return this.roles.includes('ADMIN') || this.roles.includes('SUPERVISOR')
    },
    isAdmin(): boolean {
      return this.roles.includes('ADMIN')
    },
    /** 设计业务里"岗位"用于展示，角色用于权限，两者别混 */
    roleText(): string {
      const r = this.roles
      if (r.includes('ADMIN')) return '管理员'
      if (r.includes('SUPERVISOR')) return '主管'
      if (r.includes('FINANCE')) return '财务'
      if (r.includes('CS')) return '客服'
      if (r.includes('DESIGNER')) return '设计师'
      return '员工'
    },
  },

  actions: {
    async login(username: string, password: string) {
      const res = await loginApi(username, password)
      this.token = res.token
      this.user = res.user
      localStorage.setItem(TOKEN_KEY, res.token)
      return res
    },

    async fetchMe() {
      if (!this.token) return null
      this.loading = true
      try {
        this.user = await getMe()
        return this.user
      } finally {
        this.loading = false
      }
    },

    async logout() {
      try {
        if (this.token) await logoutApi()
      } catch {
        // 后端已失效时登出会 401，这里吞掉：本地状态无论如何都要清
      }
      this.reset()
    },

    reset() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
    },
  },
})
