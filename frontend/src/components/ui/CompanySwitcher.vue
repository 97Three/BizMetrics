<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import client from '../../api/client'
import { useAuthStore } from '../../stores/auth'

interface CompanyLite {
  id: number
  name: string
  entityType: string
  operatingRegion?: string | null
}

const auth = useAuthStore()
const open = ref(false)
const query = ref('')
const list = ref<CompanyLite[]>([])
const loading = ref(false)
const rootEl = ref<HTMLElement | null>(null)

const creating = ref(false)
const dialog = ref(false)
const form = ref({ name: '', entityType: 'company', province: '', city: '' })

const activeId = computed(() => Number(localStorage.getItem('activeCompanyId')) || auth.accessContext?.company.id || 0)
const active = computed<CompanyLite | undefined>(() =>
  list.value.find((c) => c.id === activeId.value) || auth.accessContext?.company as CompanyLite | undefined,
)
const health = ref<{ score: number; severity: string } | null>(null)
async function loadHealth() {
  try {
    const res = await client.get<{ score: number; severity: string }>('/workspace', { params: { companyId: activeId.value } })
    health.value = { score: Number(res.data.score), severity: String(res.data.severity || 'healthy') }
  } catch { health.value = null }
}
const canCreate = computed(() => auth.user?.role === 1)
const filtered = computed(() => {
  const q = query.value.trim().toLowerCase()
  if (!q) return list.value
  return list.value.filter((c) => c.name.toLowerCase().includes(q) || (c.operatingRegion || '').toLowerCase().includes(q))
})
const isHousehold = (c?: CompanyLite) => c?.entityType === 'household'

async function loadCompanies() {
  loading.value = true
  try {
    const res = await client.get<CompanyLite[]>('/enterprise/companies')
    list.value = res.data
  } catch {
    list.value = (auth.accessContext?.companies || []) as CompanyLite[]
  } finally {
    loading.value = false
  }
}

async function toggle() {
  open.value = !open.value
  if (open.value) {
    query.value = ''
    if (!list.value.length) await loadCompanies()
  }
}

async function pick(c: CompanyLite) {
  if (c.id === activeId.value) { open.value = false; return }
  open.value = false
  await auth.chooseCompany(c.id)
  ElMessage.success(`已切换到「${c.name}」`)
  window.setTimeout(() => window.location.reload(), 200)
}

function openCreate() {
  open.value = false
  form.value = { name: '', entityType: 'company', province: '', city: '' }
  dialog.value = true
}

async function submitCreate() {
  const name = form.value.name.trim()
  if (!name) { ElMessage.warning('请填写主体名称'); return }
  creating.value = true
  try {
    const isHome = form.value.entityType === 'household'
    const region = ['中国', form.value.province, form.value.city].filter(Boolean).join('/')
    const res = await client.post<{ id: number; name: string }>('/enterprise/companies', {
      name,
      entityType: form.value.entityType,
      currency: 'CNY',
      taxpayerType: isHome ? '非经营主体' : '小规模纳税人',
      industry: isHome ? '家庭资产管理' : '未设置',
      operatingRegion: region || '中国',
      fiscalYearStartMonth: 1,
    })
    dialog.value = false
    ElMessage.success('主体已创建')
    await auth.chooseCompany(res.data.id)
    window.setTimeout(() => window.location.reload(), 200)
  } catch {
    ElMessage.error('创建失败，请检查权限或稍后重试')
  } finally {
    creating.value = false
  }
}

function onDocClick(e: MouseEvent) {
  if (open.value && rootEl.value && !rootEl.value.contains(e.target as Node)) open.value = false
}
function onEsc(e: KeyboardEvent) {
  if (e.key === 'Escape') open.value = false
}
onMounted(() => {
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onEsc)
  void loadCompanies()
  void loadHealth()
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onEsc)
})
</script>

<template>
  <div ref="rootEl" class="cs">
    <button class="cs-trigger" type="button" :aria-expanded="open" aria-haspopup="listbox" @click="toggle">
      <s class="dot" />
      <span class="cs-name">{{ active?.name || '选择主体' }}</span>
      <span v-if="health" class="cs-health" :class="health.severity" :title="`经营健康度 ${health.score} 分`">{{ health.score }}</span>
      <span class="cs-badge" :class="{ home: isHousehold(active) }">{{ isHousehold(active) ? '家庭' : '公司' }}</span>
      <svg class="chev" :class="{ up: open }" viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 9l6 6 6-6"/></svg>
    </button>

    <transition name="pop">
      <div v-if="open" class="cs-menu" role="listbox">
        <div class="cs-search">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="11" cy="11" r="6.5"/><path d="M16 16l4.5 4.5"/></svg>
          <input v-model="query" type="search" placeholder="搜索主体名称或地区" aria-label="搜索主体" />
        </div>
        <div class="cs-list">
          <button
            v-for="c in filtered" :key="c.id" type="button" class="cs-item" :class="{ on: c.id === activeId }"
            role="option" :aria-selected="c.id === activeId" @click="pick(c)"
          >
            <span class="av" :class="{ home: isHousehold(c) }">{{ c.name.slice(0, 1) }}</span>
            <span class="tx">
              <b>{{ c.name }}</b>
              <small>{{ isHousehold(c) ? '家庭主体' : '公司主体' }}<template v-if="c.operatingRegion"> · {{ c.operatingRegion }}</template></small>
            </span>
            <svg v-if="c.id === activeId" class="ok" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 12l4.5 4.5L19 7"/></svg>
          </button>
          <p v-if="!filtered.length" class="cs-empty">{{ loading ? '加载中…' : '没有匹配的主体' }}</p>
        </div>
        <button v-if="canCreate" class="cs-add" type="button" @click="openCreate">+ 新增主体</button>
      </div>
    </transition>

    <el-dialog v-model="dialog" title="新增主体" width="440px" :close-on-click-modal="false" append-to-body>
      <div class="dlg">
        <label>主体名称<input v-model="form.name" placeholder="例如：上海默知文化传媒有限公司" maxlength="60" /></label>
        <label>主体类型
          <div class="seg">
            <button type="button" :class="{ on: form.entityType === 'company' }" @click="form.entityType = 'company'">公司主体</button>
            <button type="button" :class="{ on: form.entityType === 'household' }" @click="form.entityType = 'household'">家庭主体</button>
          </div>
        </label>
        <div class="two">
          <label>省份<input v-model="form.province" placeholder="广东省" /></label>
          <label>城市<input v-model="form.city" placeholder="深圳市" /></label>
        </div>
        <p class="tip">公司主体用于经营收支与税务；家庭主体只保留收支、账户与预算。</p>
      </div>
      <template #footer>
        <button class="btn" type="button" @click="dialog = false">取消</button>
        <button class="btn pri" type="button" :disabled="creating" @click="submitCreate">{{ creating ? '创建中…' : '创建并切换' }}</button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.cs{position:relative}
.cs-trigger{height:34px;display:flex;align-items:center;gap:8px;padding:0 9px 0 11px;border:1px solid var(--line);
  border-radius:10px;background:#fff;box-shadow:var(--sh-sm);font-size:12px;color:var(--ink);cursor:pointer;
  transition:border-color .15s, box-shadow .15s}
.cs-trigger:hover{border-color:#c8d2f8;box-shadow:var(--sh)}
.dot{width:6px;height:6px;border-radius:50%;background:var(--green);box-shadow:0 0 0 3px rgba(18,183,106,.14);flex:0 0 6px}
.cs-name{max-width:220px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.cs-badge{font-size:9.5px;color:var(--ink3);background:#f2f5f9;border-radius:5px;padding:2px 6px}
.cs-badge.home{color:#8a5a12;background:#fdf2e3}
.chev{color:var(--ink4);transition:transform .18s}
.chev.up{transform:rotate(180deg)}
.cs-menu{position:absolute;right:0;top:44px;width:340px;background:#fff;border:1px solid var(--line);border-radius:14px;
  box-shadow:0 18px 40px -12px rgba(16,24,40,.22), 0 2px 6px rgba(16,24,40,.06);padding:8px;z-index:30}
.cs-search{display:flex;align-items:center;gap:8px;height:34px;padding:0 10px;border:1px solid var(--line);border-radius:9px;
  background:#f8fafc;color:var(--ink4);margin-bottom:6px}
.cs-search input{flex:1;border:none;outline:none;background:transparent;font-size:12.5px;color:var(--ink)}
.cs-list{max-height:264px;overflow:auto}
.cs-item{width:100%;display:flex;align-items:center;gap:10px;padding:8px 10px;border-radius:9px;border:none;background:transparent;
  text-align:left;cursor:pointer;color:var(--ink2)}
.cs-item:hover{background:#f5f8ff}
.cs-item.on{background:#eef2ff}
.av{width:26px;height:26px;border-radius:8px;display:grid;place-items:center;font-size:11.5px;font-weight:650;
  background:#eef2ff;color:var(--brand);flex:0 0 26px}
.av.home{background:#fdf2e3;color:#a16207}
.tx{flex:1;min-width:0}
.tx b{display:block;font-size:12.5px;font-weight:600;color:var(--ink);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.tx small{display:block;font-size:10.5px;color:var(--ink4);margin-top:2px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.ok{color:var(--brand)}
.cs-empty{font-size:12px;color:var(--ink4);padding:14px 10px}
.cs-add{width:100%;height:34px;margin-top:6px;border-top:1px solid var(--line2);border-radius:0 0 9px 9px;border:none;
  background:transparent;color:var(--brand);font-size:12.5px;font-weight:550;cursor:pointer;text-align:left;padding:0 10px}
.cs-add:hover{background:#f5f8ff}
.pop-enter-active,.pop-leave-active{transition:opacity .14s ease, transform .14s ease}
.pop-enter-from,.pop-leave-to{opacity:0;transform:translateY(-4px)}
.dlg{display:flex;flex-direction:column;gap:14px}
.dlg label{display:flex;flex-direction:column;gap:6px;font-size:12.5px;color:var(--ink2)}
.dlg input{height:36px;border:1px solid var(--line);border-radius:9px;padding:0 11px;font-size:13px;color:var(--ink);outline:none}
.dlg input:focus{border-color:#9fb1f2;box-shadow:0 0 0 3px rgba(54,89,227,.12)}
.seg{display:flex;gap:6px}
.seg button{flex:1;height:34px;border:1px solid var(--line);border-radius:9px;background:#fff;color:var(--ink2);font-size:12.5px;cursor:pointer}
.seg button.on{border-color:#9fb1f2;background:#eef2ff;color:var(--brand);font-weight:600}
.two{display:grid;grid-template-columns:1fr 1fr;gap:10px}
.tip{font-size:11.5px;color:var(--ink4);line-height:1.6}

<style scoped>
.cs-health{min-width:26px;height:18px;padding:0 6px;border-radius:6px;display:inline-flex;align-items:center;justify-content:center;
  font-size:10.5px;font-weight:700;font-variant-numeric:tabular-nums;letter-spacing:-.2px}
.cs-health.healthy{background:#e6f7ef;color:#0a6b46}
.cs-health.warning{background:#fdf2e3;color:#8a5313}
.cs-health.risk,.cs-health.critical{background:#fdeef0;color:#b42318}
</style>
