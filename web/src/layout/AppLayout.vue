<template>
  <div class="app">
    <aside class="side">
      <div class="brand">
        <h1>设计业务管理系统</h1>
        <p>内部使用 · v2.0</p>
      </div>

      <nav class="nav">
        <div class="nav-group">工作台</div>
        <router-link to="/perf" active-class="on">
          <span class="dot"></span>我的业绩
        </router-link>
        <router-link to="/salary" active-class="on">
          <span class="dot"></span>我的工资
        </router-link>

        <div class="nav-group">业务</div>
        <router-link to="/orders" active-class="on">
          <span class="dot"></span>订单管理
        </router-link>
        <router-link v-if="user.isManager" to="/orders/new" active-class="on">
          <span class="dot"></span>订单录入
        </router-link>

        <div class="nav-group" v-if="canCollect">财务</div>
        <router-link v-if="canCollect" to="/receivables" active-class="on">
          <span class="dot"></span>收款与催收
        </router-link>
        <router-link v-if="canCollect" to="/invoices" active-class="on">
          <span class="dot"></span>开票管理
        </router-link>

        <div class="nav-group" v-if="user.isManager">管理</div>
        <router-link v-if="user.isManager" to="/team" active-class="on">
          <span class="dot"></span>团队工资
        </router-link>
      </nav>

      <div class="side-foot">
        {{ today }} · 数据实时
      </div>
    </aside>

    <main>
      <div class="topbar">
        <div class="crumb">工作台 / <b>{{ currentTitle }}</b></div>
        <el-dropdown trigger="click" @command="onCommand">
          <div class="user">
            <span>{{ user.displayName }} · {{ user.roleText }}</span>
            <div class="avatar">{{ avatarChar }}</div>
            <el-icon class="caret"><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="password">修改密码</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <div class="content">
        <router-view />
      </div>
    </main>

    <!-- 改密：首登强制修改、日常换密码都走这里 -->
    <el-dialog v-model="pwdVisible" title="修改密码" width="420px" :close-on-click-modal="false">
      <el-form :model="pwdForm" label-width="88px">
        <el-form-item label="原密码">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="至少 8 位，含字母和数字" />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="pwdForm.confirm" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdLoading" @click="submitPassword">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { changePassword } from '@/api/auth'

const user = useUserStore()
const route = useRoute()
const router = useRouter()

const currentTitle = computed(() => (route.meta.title as string) || '')
const avatarChar = computed(() => (user.displayName || '?').slice(0, 1))
const today = new Date().toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })

/**
 * 财务菜单的可见性。
 *
 * 【为什么单独一个 computed 而不是复用 isManager】
 * 客服（CS）不在 isManager 里 —— 但尾款是他在催。只按 isManager 显示菜单，
 * 客服就只能靠别人发链接进催收页，等于这个功能对他不存在。
 */
const canCollect = computed(
  () => user.isManager || user.roles.includes('FINANCE') || user.roles.includes('CS'),
)

const pwdVisible = ref(false)
const pwdLoading = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirm: '' })

function onCommand(cmd: string) {
  if (cmd === 'password') {
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirm = ''
    pwdVisible.value = true
    return
  }
  if (cmd === 'logout') {
    ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
      .then(async () => {
        await user.logout()
        router.replace('/login')
      })
      .catch(() => {})
  }
}

async function submitPassword() {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) {
    ElMessage.warning('请填写完整')
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirm) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  pwdLoading.value = true
  try {
    await changePassword(pwdForm.oldPassword, pwdForm.newPassword)
    ElMessage.success('密码已修改，请重新登录')
    pwdVisible.value = false
    // 后端改密后会让旧 token 失效，这里主动退出，避免用户下一次操作莫名其妙被踢
    await user.logout()
    router.replace('/login')
  } finally {
    pwdLoading.value = false
  }
}
</script>

<style scoped>
.app {
  display: flex;
  min-height: 100vh;
}

.side {
  width: 208px;
  flex: 0 0 208px;
  background: #fff;
  border-right: 1px solid var(--line);
  display: flex;
  flex-direction: column;
  position: sticky;
  top: 0;
  height: 100vh;
}

.brand {
  padding: 18px 18px 16px;
  border-bottom: 1px solid var(--line-2);
}

.brand h1 {
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.2px;
}

.brand p {
  font-size: 11px;
  color: var(--t3);
  margin-top: 2px;
}

.nav {
  padding: 10px;
  flex: 1;
  overflow: auto;
}

.nav-group {
  font-size: 11px;
  color: var(--t3);
  padding: 12px 8px 6px;
}

.nav a {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  color: var(--t2);
  text-decoration: none;
  font-size: 13px;
}

.nav a:hover {
  background: #f2f4f6;
  color: var(--t1);
}

.nav a.on {
  background: var(--blue-bg);
  color: var(--blue);
  font-weight: 500;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.5;
}

.side-foot {
  padding: 12px 16px;
  border-top: 1px solid var(--line-2);
  font-size: 11px;
  color: var(--t3);
}

main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.topbar {
  background: #fff;
  border-bottom: 1px solid var(--line);
  padding: 0 24px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: sticky;
  top: 0;
  z-index: 5;
}

.crumb {
  font-size: 12px;
  color: var(--t3);
}

.crumb b {
  color: var(--t1);
  font-weight: 500;
}

.user {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--t2);
  cursor: pointer;
  outline: none;
}

.avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--blue-bg);
  color: var(--blue);
  display: grid;
  place-items: center;
  font-size: 11px;
  font-weight: 600;
}

.caret {
  font-size: 11px;
  color: var(--t3);
}

.content {
  padding: 20px 24px 40px;
  flex: 1;
}

@media (max-width: 760px) {
  .side {
    display: none;
  }
}
</style>
