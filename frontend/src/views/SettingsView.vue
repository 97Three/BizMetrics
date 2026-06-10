<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import client from '../api/client'
import { useAuthStore } from '../stores/auth'
import type { NotificationPreference } from '../types/notification'
import type { User } from '../types/user'
import UiIcon from '../components/ui/UiIcon.vue'
import { locale, setLocale } from '../i18n/locale'

const auth = useAuthStore()
const nickname = ref(auth.user?.nickname || '')
const oldPassword = ref('')
const newPassword = ref('')
const preference = ref<NotificationPreference | null>(null)
const error = ref('')
const saving = ref(false)
type ThemeMode = 'light' | 'dark' | 'auto'
const storedTheme = (localStorage.getItem('bizmetrics-theme') || 'light') as ThemeMode
const dark = ref(document.documentElement.classList.contains('dark'))
const themeMode = ref<ThemeMode>(storedTheme === 'auto' ? 'auto' : storedTheme)

const types = [
  { value: 'tax', label: '税务' },
  { value: 'receipt', label: '票据' },
  { value: 'payroll', label: '薪酬' },
  { value: 'people', label: '人事' },
  { value: 'finance', label: '财务' },
  { value: 'system', label: '系统' },
]

async function load() {
  try {
    const res = await client.get<NotificationPreference>('/notifications/preferences')
    preference.value = res.data
  } catch { error.value = '通知设置暂时无法读取' }
}
async function saveProfile() {
  saving.value = true
  try {
    const res = await client.put<User>('/auth/profile', { nickname: nickname.value })
    auth.user = res.data
    ElMessage.success('个人信息已保存')
  } catch { ElMessage.error('保存失败') } finally { saving.value = false }
}
async function changePassword() {
  if (newPassword.value.length < 8) { ElMessage.warning('新密码至少 8 位'); return }
  try {
    await client.put('/auth/password', { oldPassword: oldPassword.value, newPassword: newPassword.value })
    oldPassword.value = ''
    newPassword.value = ''
    ElMessage.success('密码已更新')
  } catch { ElMessage.error('密码修改失败') }
}
const testing = ref(false)
async function testWebhook() {
  if (!preference.value?.webhookUrl) { ElMessage.warning('请先填写 Webhook 地址'); return }
  testing.value = true
  try {
    await client.post('/notifications/preferences/test-webhook')
    ElMessage.success('测试消息已发送，请检查对应群聊')
  } catch { ElMessage.error('测试投递失败，请检查地址与服务状态') } finally { testing.value = false }
}
async function saveNotifications() {
  if (!preference.value) return
  saving.value = true
  try {
    const data = preference.value
    const res = await client.put<NotificationPreference>('/notifications/preferences', {
      enabled: data.enabled, webhookEnabled: data.webhookEnabled, webhookProvider: data.webhookProvider,
      webhookUrl: data.webhookUrl || '', minSeverity: data.minSeverity, mutedTypes: data.mutedTypes,
    })
    preference.value = res.data
    ElMessage.success('通知设置已保存')
  } catch { ElMessage.error('通知设置保存失败') } finally { saving.value = false }
}
function applyTheme(mode: ThemeMode) {
  themeMode.value = mode
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
  const next = mode === 'auto' ? prefersDark : mode === 'dark'
  dark.value = next
  document.documentElement.classList.toggle('dark', next)
  localStorage.setItem('bizmetrics-theme', mode === 'auto' ? 'auto' : mode)
}
function toggleMuted(type: string, on: boolean) {
  if (!preference.value) return
  const list = new Set(preference.value.mutedTypes || [])
  if (on) list.add(type); else list.delete(type)
  preference.value.mutedTypes = Array.from(list)
}
onMounted(load)
</script>

<template>
  <div class="wrap settings">
    <div class="ph">
      <div class="l">
        <div class="tile"><UiIcon name="grid" :size="20" /></div>
        <div><h1>设置</h1><p class="sub">管理个人信息、账号安全与通知偏好</p></div>
      </div>
    </div>

    <div class="settings-grid">
      <nav class="anchors" aria-label="设置分区">
        <a href="#profile"><UiIcon name="users" :size="15" />个人信息</a>
        <a href="#security"><UiIcon name="shield" :size="15" />账号安全</a>
        <a href="#appearance"><UiIcon name="spark" :size="15" />外观</a>
        <a href="#notifications"><UiIcon name="alert" :size="15" />通知与 Webhook</a>
      </nav>

      <div class="content">
        <section id="profile" class="card sec">
          <header><h3><span class="ic"><UiIcon name="users" :size="14" /></span>个人信息</h3><span class="hint">展示在工作台右上角</span></header>
          <form class="form" @submit.prevent="saveProfile">
            <label class="fld"><span>昵称</span><input v-model="nickname" required placeholder="你的名字" /></label>
            <label class="fld"><span>邮箱</span><input :value="auth.user?.email" disabled /></label>
            <div class="foot"><button class="btn pri" type="submit" :disabled="saving">保存个人信息</button></div>
          </form>
        </section>

        <section id="security" class="card sec">
          <header><h3><span class="ic"><UiIcon name="shield" :size="14" /></span>账号安全</h3><span class="hint">修改后需要重新登录</span></header>
          <form class="form two" @submit.prevent="changePassword">
            <label class="fld"><span>当前密码</span><input v-model="oldPassword" type="password" required autocomplete="current-password" placeholder="••••••••" /></label>
            <label class="fld"><span>新密码</span><input v-model="newPassword" type="password" required autocomplete="new-password" placeholder="至少 8 位" /></label>
            <div class="foot span2"><button class="btn pri" type="submit">更新密码</button></div>
          </form>
        </section>

        <section id="appearance" class="card sec">
          <header><h3><span class="ic"><UiIcon name="spark" :size="14" /></span>外观</h3></header>
          <div class="row"><div><b>界面主题</b><small>夜间查看账目时更柔和，图表会同步换色</small></div>
            <div class="theme-seg">
              <button type="button" :class="{ on: themeMode === 'light' }" @click="applyTheme('light')">浅色</button>
              <button type="button" :class="{ on: themeMode === 'dark' }" @click="applyTheme('dark')">深色</button>
              <button type="button" :class="{ on: themeMode === 'auto' }" @click="applyTheme('auto')">跟随系统</button>
            </div>
          </div>
          <div class="row"><div><b>界面语言</b><small>已覆盖导航、搜索、通知与菜单；页面正文仍在迁移</small></div><div class="theme-seg"><button type="button" :class="{ on: locale === 'zh' }" @click="setLocale('zh')">中文</button><button type="button" :class="{ on: locale === 'en' }" @click="setLocale('en')">English</button></div></div>
        </section>

        <section id="notifications" class="card sec">
          <header><h3><span class="ic"><UiIcon name="alert" :size="14" /></span>通知与 Webhook</h3></header>
          <p v-if="error" class="notice" role="alert">{{ error }}</p>
          <div v-if="preference" class="form">
            <div class="row"><div><b>启用站内通知</b><small>关闭后不再生成新的通知</small></div><el-switch v-model="preference.enabled" /></div>
            <div class="row"><div><b>外部 Webhook 投递</b><small>把重要提醒推送到飞书 / 企业微信</small></div><el-switch v-model="preference.webhookEnabled" /></div>
            <div class="two">
              <label class="fld"><span>Webhook 类型</span>
                <select v-model="preference.webhookProvider">
                  <option value="generic">通用 JSON</option><option value="feishu">飞书机器人</option><option value="wecom">企业微信机器人</option>
                </select>
              </label>
              <label class="fld"><span>最低通知级别</span>
                <select v-model="preference.minSeverity">
                  <option value="info">全部消息</option><option value="warning">仅提醒与紧急</option><option value="critical">仅紧急</option>
                </select>
              </label>
            </div>
            <label class="fld"><span>Webhook 地址</span><div class="wh-row"><input v-model="preference.webhookUrl" placeholder="https://..." /><button class="btn" type="button" :disabled="testing" @click="testWebhook">{{ testing ? '发送中…' : '测试投递' }}</button></div></label>
            <div class="muted">
              <span>静音类型</span>
              <div class="chips">
                <button v-for="t in types" :key="t.value" type="button" class="chip"
                  :class="{ on: preference.mutedTypes?.includes(t.value) }"
                  @click="toggleMuted(t.value, !preference.mutedTypes?.includes(t.value))">{{ t.label }}</button>
              </div>
            </div>
            <div class="foot"><button class="btn pri" type="submit" :disabled="saving" @click.prevent="saveNotifications">保存通知设置</button></div>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.settings-grid{display:grid;grid-template-columns:196px minmax(0,1fr);gap:20px;align-items:start}
.anchors{position:sticky;top:16px;display:flex;flex-direction:column;gap:2px;padding:8px}
.anchors a{display:flex;align-items:center;gap:9px;padding:9px 11px;border-radius:9px;text-decoration:none;color:var(--ink2);font-size:12.5px}
.anchors a:hover{background:#f2f5f9;color:var(--ink)}
.anchors a:first-child{background:#eef2ff;color:var(--brand);font-weight:600}
.content{display:flex;flex-direction:column;gap:14px;min-width:0}
.sec{padding:0 0 16px}
.sec>header{height:56px;display:flex;align-items:center;justify-content:space-between;padding:0 22px;border-bottom:1px solid var(--line2)}
.sec h3{font-size:14px;font-weight:650;display:flex;align-items:center;gap:9px}
.sec h3 .ic{width:26px;height:26px;border-radius:8px;background:#eef2ff;color:var(--brand);display:grid;place-items:center}
.hint{font-size:11.5px;color:var(--ink4)}
.form{display:grid;grid-template-columns:1fr;gap:14px;padding:18px 22px 0}
.form.two,.two{display:grid;grid-template-columns:1fr 1fr;gap:14px}
.fld{display:flex;flex-direction:column;gap:6px;font-size:12px;color:var(--ink3)}
.fld input,.fld select{height:38px;border:1px solid var(--line);border-radius:9px;padding:0 11px;font-size:13px;color:var(--ink);outline:none;background:#fff;font-family:inherit}
.fld input:focus,.fld select:focus{border-color:#9fb1f2;box-shadow:0 0 0 3px rgba(54,89,227,.12)}
.fld input:disabled{background:#f7f9fc;color:var(--ink4)}
.foot{display:flex;justify-content:flex-end;padding-top:2px}
.span2{grid-column:span 2}
.row{display:flex;align-items:center;justify-content:space-between;gap:20px;padding:14px 22px;border-bottom:1px solid var(--line2)}
.row b{display:block;font-size:13px;font-weight:600;color:var(--ink)}
.row small{display:block;font-size:11.5px;color:var(--ink4);margin-top:3px}
.muted{padding:14px 22px 0;display:flex;flex-direction:column;gap:8px;font-size:12px;color:var(--ink3)}
.chips{display:flex;gap:8px;flex-wrap:wrap}
.chip{height:30px;padding:0 12px;border-radius:8px;border:1px solid var(--line);background:#fff;color:var(--ink2);font-size:12px;cursor:pointer;font-family:inherit}
.chip.on{border-color:#9fb1f2;background:#eef2ff;color:var(--brand);font-weight:600}
.notice{margin:14px 22px 0;padding:10px 12px;border-radius:10px;background:#fdf2e3;color:#8a5313;font-size:12px}
@media (max-width:900px){.settings-grid{grid-template-columns:1fr}.anchors{position:static;flex-direction:row;overflow:auto}.two{grid-template-columns:1fr}.span2{grid-column:span 1}}
</style>

<style scoped>
.theme-seg{display:flex;gap:4px;background:#f2f5f9;border-radius:9px;padding:3px}
.theme-seg button{height:30px;padding:0 12px;border:none;border-radius:7px;background:transparent;color:var(--ink3);font-size:12px;cursor:pointer;font-family:inherit}
.theme-seg button.on{background:#fff;color:var(--brand);font-weight:600;box-shadow:0 1px 3px rgba(16,24,40,.12)}
.lang-tag{font-size:11.5px;color:var(--ink3);background:#f2f5f9;border-radius:6px;padding:4px 10px}
</style>

<style scoped>
.wh-row{display:flex;gap:8px;align-items:center}
.wh-row input{flex:1}
.wh-row .btn{height:38px;white-space:nowrap}
</style>
