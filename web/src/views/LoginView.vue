<template>
  <div class="login">
    <!-- 左侧品牌区：内部系统也要有点体面，纯表单页会让员工觉得"这是个临时工具" -->
    <div class="brand-side">
      <div class="brand-inner">
        <div class="logo">设</div>
        <h1>设计业务管理系统</h1>
        <p class="sub">订单 · 回款 · 业绩 · 工资，一套账算到底</p>

        <ul class="points">
          <li><b>业绩按回款确认</b>　订金与尾款分别计入到账当月，签单额只做展示</li>
          <li><b>分成自动分摊</b>　多人协作按比例拆分，合计永远等于总额</li>
          <li><b>工资当月可查</b>　底薪 + 业绩×阶梯比例，每一行都能对上</li>
        </ul>

        <p class="foot">内部使用 · 请勿外传账号</p>
      </div>
    </div>

    <div class="form-side">
      <div class="form-box">
        <h2>{{ step === 'login' ? '登录' : '首次登录，请设置新密码' }}</h2>
        <p class="tip">
          {{ step === 'login' ? '使用管理员分配的账号登录' : '初始密码是临时的，修改后才能进入系统' }}
        </p>

        <el-form v-if="step === 'login'" :model="form" @submit.prevent="submit" size="large">
          <el-form-item>
            <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" autofocus />
          </el-form-item>
          <el-form-item>
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
              :prefix-icon="Lock"
              show-password
              @keyup.enter="submit"
            />
          </el-form-item>
          <el-button type="primary" class="submit" :loading="loading" @click="submit">登 录</el-button>
        </el-form>

        <el-form v-else :model="pwdForm" size="large">
          <el-form-item>
            <el-input v-model="pwdForm.newPassword" type="password" placeholder="新密码（至少 8 位，含字母和数字）" show-password />
          </el-form-item>
          <el-form-item>
            <el-input v-model="pwdForm.confirm" type="password" placeholder="确认新密码" show-password />
          </el-form-item>
          <el-button type="primary" class="submit" :loading="loading" @click="submitPassword">保存并重新登录</el-button>
        </el-form>

        <p class="hint">忘记密码请联系管理员重置</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { changePassword } from '@/api/auth'

const router = useRouter()
const route = useRoute()
const user = useUserStore()

const step = ref<'login' | 'pwd'>('login')
const loading = ref(false)
const form = reactive({ username: '', password: '' })
const pwdForm = reactive({ newPassword: '', confirm: '' })

async function submit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const res = await user.login(form.username.trim(), form.password)
    if (res.mustChangePwd) {
      // 首登必须改密：先别跳转，否则进去后每个接口都返回 40104，用户会以为系统坏了
      step.value = 'pwd'
      return
    }
    ElMessage.success(`欢迎回来，${user.displayName || form.username}`)
    const redirect = (route.query.redirect as string) || '/perf'
    router.replace(redirect)
  } catch {
    // 错误提示已由请求拦截器统一给出，这里不需要重复弹
  } finally {
    loading.value = false
  }
}

async function submitPassword() {
  if (pwdForm.newPassword !== pwdForm.confirm) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  loading.value = true
  try {
    await changePassword(form.password, pwdForm.newPassword)
    ElMessage.success('密码已修改，请用新密码重新登录')
    user.reset()
    step.value = 'login'
    form.password = ''
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login {
  display: flex;
  min-height: 100vh;
  background: #fff;
}

.brand-side {
  flex: 1.1;
  background: linear-gradient(145deg, #185fa5 0%, #12456f 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
}

.brand-inner {
  max-width: 420px;
}

.logo {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.16);
  display: grid;
  place-items: center;
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 28px;
}

.brand-inner h1 {
  font-size: 26px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.sub {
  margin-top: 10px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.72);
}

.points {
  margin-top: 40px;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.points li {
  font-size: 12.5px;
  color: rgba(255, 255, 255, 0.78);
  line-height: 1.7;
  padding-left: 14px;
  border-left: 2px solid rgba(255, 255, 255, 0.24);
}

.points b {
  color: #fff;
  font-weight: 600;
}

.foot {
  margin-top: 56px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.44);
}

.form-side {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
}

.form-box {
  width: 100%;
  max-width: 320px;
}

.form-box h2 {
  font-size: 20px;
  font-weight: 600;
}

.tip {
  margin: 6px 0 24px;
  font-size: 12px;
  color: var(--t3);
}

.submit {
  width: 100%;
  margin-top: 4px;
}

.hint {
  margin-top: 20px;
  font-size: 11px;
  color: var(--t3);
  text-align: center;
}

@media (max-width: 900px) {
  .brand-side {
    display: none;
  }
}
</style>
