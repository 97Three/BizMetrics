<script setup lang="ts">
import { ref } from 'vue'
import axios from 'axios'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import AuthShell from '../layouts/AuthShell.vue'

const email = ref('')
const password = ref('')
const loading = ref(false)
const showDemo = import.meta.env.DEV || ['localhost', '127.0.0.1'].includes(window.location.hostname)
const demoPassword = showDemo ? '123456' : ''
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

function fillDemo(account: string) {
  email.value = account
  password.value = demoPassword
}

function loginError(error: unknown) {
  if (!axios.isAxiosError(error)) return '登录失败，请稍后重试'
  if (error.response?.status === 429) return '尝试次数过多，请稍后再试'
  if (error.response?.status === 403) return '登录请求被服务器拒绝，请检查访问地址或服务配置'
  if (!error.response) return '无法连接服务器，请检查后端服务'
  return '邮箱或密码不正确'
}

async function submit() {
  loading.value = true
  try {
    await auth.login(email.value, password.value)
    const requested = String(route.query.next || '/dashboard')
    const target = requested.startsWith('/') && !requested.startsWith('//') ? requested : '/dashboard'
    await router.replace(target).catch(() => { /* fall through to hard navigation */ })
    if (router.currentRoute.value.path === '/login') window.location.assign(target)
  } catch (error) {
    ElMessage.error(loginError(error))
  } finally { loading.value = false }
}
</script>

<template>
  <AuthShell mode="login">
    <form class="auth-panel" @submit.prevent="submit">
      <span class="form-kicker">WELCOME BACK <i></i> 欢迎回来</span>
      <h2>登录你的工作台</h2>
      <p class="form-intro">继续查看今天的经营进展与待办事项。</p>
      <div class="form-divider"></div>
      <label for="email">工作邮箱</label>
      <input id="email" v-model="email" type="email" autocomplete="email" required placeholder="name@company.com">
      <label for="password">密码</label>
      <input id="password" v-model="password" type="password" autocomplete="current-password" required placeholder="请输入密码">
      <button class="auth-submit" type="submit" :disabled="loading"><span>{{ loading ? '正在验证…' : '进入工作台' }}</span><span aria-hidden="true">↗</span></button>
      <div class="auth-form-end"><span>还没有账号？</span><RouterLink to="/register">创建账号 →</RouterLink></div>
      <section v-if="showDemo" class="demo-account-panel" aria-label="本地演示账号">
        <div><strong>体验演示环境</strong><span>一键填入本地演示账号</span></div>
        <div class="demo-account-actions">
          <button type="button" @click="fillDemo('test@bizmetrics.com')">管理员视角</button>
        </div>
      </section>
      <p class="auth-security">安全连接 · 登录后将按你的权限展示业务数据</p>
    </form>
  </AuthShell>
</template>
