<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search, Bell, Menu as MenuIcon, Sunny, Moon } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import client from '../api/client'
import type { NotificationItem, NotificationSummary } from '../types/notification'
import { brand } from '../brand'
import { canVisit } from '../utils/navigationAccess'
import CompanySwitcher from '../components/ui/CompanySwitcher.vue'
import UiIcon from '../components/ui/UiIcon.vue'
import { pick } from '../i18n/locale'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const mobileOpen = ref(false)
const scrolled = ref(false)
const quickOpen = ref(false)
watch(() => route.path, () => { void nextTick(measureInk) })
const menuEl = ref<HTMLElement | null>(null)
const ink = ref({ left: 0, width: 0, ready: false })
function measureInk() {
  const menu = menuEl.value
  const el = menu?.querySelector('.mi.on') as HTMLElement | null
  if (!menu || !el) { ink.value.ready = false; return }
  const r = el.getBoundingClientRect()
  const m = menu.getBoundingClientRect()
  ink.value = { left: r.left - m.left + 14, width: Math.max(r.width - 28, 18), ready: true }
}
const quickItems = [
  { label: '流水', path: '/transactions', icon: 'list', desc: '收入 / 支出 / 退款' },
  { label: '凭证', path: '/receipts', icon: 'doc', desc: '发票 / 报销单 / 回单' },
  { label: '预算', path: '/budgets', icon: 'shield', desc: '额度与预警' },
  { label: '账户', path: '/accounts', icon: 'wallet', desc: '余额与对账' },
  { label: '周期事项', path: '/recurring', icon: 'calendar', desc: '固定收支计划' },
]
function goCreate(path: string) {
  quickOpen.value = false
  void router.push({ path, query: { new: '1' } })
}
const dark = ref(document.documentElement.classList.contains('dark'))

const notifications = ref<NotificationItem[]>([])
const unread = ref(0)
let timer: number | undefined

async function loadNotifications() {
  if (document.hidden) return
  try {
    const [s, l] = await Promise.all([
      client.get<NotificationSummary>('/notifications/summary'),
      client.get<{ content: NotificationItem[] }>('/notifications', { params: { page: 0, size: 5 } }),
    ])
    unread.value = s.data.unreadCount
    notifications.value = l.data.content
  } catch { /* keep last known state */ }
}
async function openNotification(item: NotificationItem) {
  if (!item.readAt) {
    try { await client.put(`/notifications/${item.id}/read`); await loadNotifications() } catch { /* navigation still works */ }
  }
  if (item.targetUrl?.startsWith('/') && !item.targetUrl.startsWith('//')) await router.push(item.targetUrl)
}
async function markAllRead() {
  try { await client.put('/notifications/read-all'); await loadNotifications() } catch { /* keep list */ }
}

// ---------- 分组导航（按权限收敛） ----------
interface NavItem { label: string; path: string; icon: string }
interface NavGroup { key: string; label: string; items: NavItem[] }

const navGroupsDef: NavGroup[] = [
  {
    key: 'workspace', label: '经营工作台', items: [
      { label: '首页', path: '/dashboard', icon: 'home' },
      { label: '审批中心', path: '/approvals', icon: 'check' },
    ],
  },
  {
    key: 'operations', label: '经营管理', items: [
      { label: '经营总览', path: '/operations', icon: 'trend' },
      { label: '经营流水', path: '/transactions', icon: 'list' },
      { label: '经营预算', path: '/budgets', icon: 'shield' },
      { label: '经营报表', path: '/reports', icon: 'chart' },
      { label: '周期事项', path: '/recurring', icon: 'calendar' },
    ],
  },
  {
    key: 'finance', label: '财务管理', items: [
      { label: '财务总览', path: '/finance', icon: 'wallet' },
      { label: '资金账户', path: '/accounts', icon: 'wallet' },
      { label: '票据凭证', path: '/receipts', icon: 'doc' },
      { label: '税务管理', path: '/tax', icon: 'tax' },
    ],
  },
  {
    key: 'people', label: '组织与人事', items: [
      { label: '组织人员', path: '/hr/organization', icon: 'users' },
      { label: '薪酬月结', path: '/admin/compensation', icon: 'wallet' },
      { label: '人力成本', path: '/hr/workforce-cost', icon: 'trend' },
      { label: '人才福利', path: '/hr/benefits', icon: 'shield' },
      { label: '绩效管理', path: '/hr/performance', icon: 'spark' },
    ],
  },
  {
    key: 'system', label: '系统管理', items: [
      { label: '设置', path: '/settings', icon: 'grid' },
      { label: '公司与权限', path: '/admin/users', icon: 'users' },
      { label: '政策中心', path: '/policy-center', icon: 'doc' },
      { label: '数据备份', path: '/backup', icon: 'list' },
    ],
  },
]

const EN_GROUP: Record<string, string> = {
  workspace: 'Workspace', operations: 'Operations', finance: 'Finance', people: 'People', system: 'System',
}
const EN_PATH: Record<string, string> = {
  '/dashboard': 'Home', '/approvals': 'Approvals', '/operations': 'Overview', '/transactions': 'Transactions',
  '/budgets': 'Budgets', '/reports': 'Reports', '/recurring': 'Recurring', '/finance': 'Finance overview',
  '/accounts': 'Accounts', '/receipts': 'Receipts', '/tax': 'Tax', '/hr/organization': 'Organization',
  '/admin/compensation': 'Payroll', '/hr/workforce-cost': 'Workforce cost', '/hr/benefits': 'Benefits',
  '/hr/performance': 'Performance', '/settings': 'Settings', '/admin/users': 'Members',
  '/policy-center': 'Policies', '/backup': 'Backup',
}
const groups = computed(() => navGroupsDef
  .map((g) => ({ ...g, items: g.items.filter((i) => canVisit(i.path, auth)) }))
  .filter((g) => g.items.length))

const openGroup = ref('')
function isActive(path: string) {
  return route.path === path || route.path.startsWith(`${path}/`)
}
function isGroupActive(group: NavGroup) {
  return group.items.some((i) => isActive(i.path))
}
function toggleGroup(key: string) {
  openGroup.value = openGroup.value === key ? '' : key
}

// ---------- 命令面板 ----------
const paletteOpen = ref(false)
const keyword = ref('')
const ledgerHits = ref<Array<{ type: string; id: number; title: string; subtitle: string; path: string }>>([])
const searching = ref(false)
let searchTimer: number | undefined

const pageHits = computed(() => {
  const all = groups.value.flatMap((g) => g.items)
  const q = keyword.value.trim()
  if (!q) return all.slice(0, 6)
  return all.filter((l) => l.label.includes(q) || l.path.includes(q)).slice(0, 6)
})

function openPalette() { paletteOpen.value = true; keyword.value = ''; ledgerHits.value = [] }
function closePalette() { paletteOpen.value = false }
async function runSearch() {
  const q = keyword.value.trim()
  if (q.length < 2) { ledgerHits.value = []; return }
  searching.value = true
  try {
    const res = await client.get<{ results: typeof ledgerHits.value }>('/search', { params: { keyword: q, limit: 6 } })
    ledgerHits.value = res.data.results || []
  } catch { ledgerHits.value = [] } finally { searching.value = false }
}
function onKeyword() {
  if (searchTimer) window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(runSearch, 220)
}
async function go(path: string) { closePalette(); await router.push(path) }

function toggleTheme() {
  dark.value = !dark.value
  document.documentElement.classList.toggle('dark', dark.value)
  localStorage.setItem('bizmetrics-theme', dark.value ? 'dark' : 'light')
}

async function logout() { await auth.logout(); await router.replace('/login') }

function onGlobalKey(e: KeyboardEvent) {
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); openPalette() }
  if (e.key === 'Escape') { closePalette(); openGroup.value = ''; mobileOpen.value = false }
}

onMounted(() => {
  void nextTick(measureInk)
  window.addEventListener('resize', measureInk)
  const mainEl = document.querySelector('main')
  const onScroll = () => { scrolled.value = (mainEl?.scrollTop || 0) > 8 }
  mainEl?.addEventListener('scroll', onScroll, { passive: true })
  onScroll()
  void loadNotifications()
  timer = window.setInterval(loadNotifications, 60000)
  document.addEventListener('visibilitychange', loadNotifications)
  document.addEventListener('keydown', onGlobalKey)
})
onUnmounted(() => { window.removeEventListener('resize', measureInk) 
  if (timer) window.clearInterval(timer)
  document.removeEventListener('visibilitychange', loadNotifications)
  document.removeEventListener('keydown', onGlobalKey)
})
</script>

<template>
  <nav class="nav" :class="{ scrolled }" aria-label="主导航">
    <div class="nav-in">
      <RouterLink to="/dashboard" class="brand" :aria-label="brand.name + ' 首页'">
        <span class="logo">{{ brand.initials }}</span>
        <span class="bt"><b>{{ brand.name }}</b><span>经营数据工作台</span></span>
      </RouterLink>

      <div ref="menuEl" class="menu">
        <span v-show="ink.ready" class="nav-ink" :style="{ transform: `translateX(${ink.left}px)`, width: ink.width + 'px' }" />
        <div
          v-for="g in groups" :key="g.key" class="mgroup"
          @mouseenter="openGroup = g.key" @mouseleave="openGroup = ''"
        >
          <button
            class="mi" :class="{ on: isGroupActive(g) }" type="button"
            :aria-expanded="openGroup === g.key" aria-haspopup="menu"
            @click="toggleGroup(g.key)"
          >
            {{ pick(g.label, EN_GROUP[g.key] || g.label) }}<span class="cv" :class="{ up: openGroup === g.key }">⌄</span>
          </button>
          <transition name="pop">
            <div v-if="openGroup === g.key" class="gmenu" role="menu">
              <RouterLink
                v-for="item in g.items" :key="item.path" :to="item.path" class="gitem"
                :class="{ on: isActive(item.path) }" role="menuitem" @click="openGroup = ''"
              >
                <UiIcon :name="item.icon" :size="15" />
                <span>{{ pick(item.label, EN_PATH[item.path] || item.label) }}</span>
                <em v-if="isActive(item.path)">当前</em>
              </RouterLink>
            </div>
          </transition>
        </div>
      </div>

      <div class="nav-r">
        <div class="quick" @mouseenter="quickOpen = true" @mouseleave="quickOpen = false">
          <button class="quick-btn" type="button" :aria-expanded="quickOpen" @click="quickOpen = !quickOpen">
            <UiIcon name="plus" :size="14" />新建
          </button>
          <transition name="pop">
            <div v-if="quickOpen" class="quick-menu">
              <button v-for="item in quickItems" :key="item.path" type="button" class="qitem" @click="goCreate(item.path)">
                <span class="qi"><UiIcon :name="item.icon" :size="14" /></span>
                <span class="qt"><b>{{ item.label }}</b><small>{{ item.desc }}</small></span>
              </button>
            </div>
          </transition>
        </div>
        <CompanySwitcher />

        <button class="search-pill" type="button" :aria-label="pick('搜索', 'Search')" @click="openPalette">
          <Search class="si" />
          <span>{{ pick('搜索流水、票据、页面', 'Search transactions, receipts, pages') }}</span>
          <kbd>Ctrl K</kbd>
        </button>

        <el-popover trigger="click" width="360" placement="bottom-end">
          <div class="notif">
            <header>
              <h3>{{ pick('通知', 'Notifications') }} <span>{{ unread }} {{ pick('条未读', 'unread') }}</span></h3>
              <button v-if="unread" type="button" @click="markAllRead">{{ pick('全部已读', 'Mark all read') }}</button>
            </header>
            <button v-for="item in notifications" :key="item.id" class="nitem" type="button" @click="openNotification(item)">
              <strong>{{ item.title }}</strong>
              <small>{{ item.content }}</small>
            </button>
            <p v-if="!notifications.length" class="nempty">{{ pick('暂无通知', 'No notifications') }}</p>
          </div>
          <template #reference>
            <button class="ibtn" type="button" :aria-label="`通知，${unread} 条未读`">
              <Bell style="width:16px" />
              <i v-if="unread" class="dot-badge">{{ unread > 9 ? '9+' : unread }}</i>
            </button>
          </template>
        </el-popover>

        <button class="ibtn" type="button" :aria-label="dark ? '切换为浅色' : '切换为深色'" @click="toggleTheme">
          <Moon v-if="!dark" style="width:16px" /><Sunny v-else style="width:16px" />
        </button>

        <button class="ibtn menu-toggle" type="button" aria-label="展开导航" :aria-expanded="mobileOpen" @click="mobileOpen = !mobileOpen">
          <MenuIcon style="width:17px" />
        </button>

        <el-dropdown trigger="click" placement="bottom-end">
          <button class="ava" type="button" aria-label="账户菜单">{{ auth.user?.nickname?.slice(0, 1) || brand.initials }}</button>
          <template #dropdown>
            <el-dropdown-menu>
              <div class="uinfo"><b>{{ auth.user?.nickname || '用户' }}</b><small>{{ auth.user?.email }}</small></div>
              <el-dropdown-item @click="router.push('/settings')">{{ pick('设置', 'Settings') }}</el-dropdown-item>
              <el-dropdown-item @click="router.push('/backup')">{{ pick('数据备份', 'Backup') }}</el-dropdown-item>
              <el-dropdown-item divided @click="logout">{{ pick('退出登录', 'Sign out') }}</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <div v-if="mobileOpen" class="mobile-menu">
      <template v-for="g in groups" :key="g.key">
        <div class="mgroup-label">{{ pick(g.label, EN_GROUP[g.key] || g.label) }}</div>
        <RouterLink v-for="item in g.items" :key="item.path" class="mitem" :class="{ on: isActive(item.path) }" :to="item.path" @click="mobileOpen = false">
          {{ item.label }}
        </RouterLink>
      </template>
    </div>

    <el-dialog v-model="paletteOpen" width="620px" append-to-body @closed="closePalette">
      <div class="palette">
        <div class="pinput">
          <Search class="si" />
          <input v-model="keyword" autofocus :placeholder="pick('搜索页面、流水、票据或账户…', 'Search pages, transactions, receipts…')" @input="onKeyword" @keyup.enter="runSearch" />
          <kbd>Esc</kbd>
        </div>
        <div class="pgroup">
          <div class="plabel">{{ pick('页面', 'Pages') }}</div>
          <button v-for="hit in pageHits" :key="hit.path" class="phit" type="button" @click="go(hit.path)">
            <UiIcon name="grid" :size="14" /><span>{{ hit.label }}</span><em>{{ hit.path }}</em>
          </button>
          <p v-if="!pageHits.length" class="pempty">{{ pick('没有匹配的页面', 'No matching pages') }}</p>
        </div>
        <div v-if="keyword.trim().length >= 2" class="pgroup">
          <div class="plabel">{{ searching ? pick('搜索中…', 'Searching…') : pick('业务记录', 'Records') }}</div>
          <button v-for="hit in ledgerHits" :key="hit.type + hit.id" class="phit" type="button" @click="go(hit.path)">
            <UiIcon name="doc" :size="14" /><span>{{ hit.title }}</span><em>{{ hit.subtitle }}</em>
          </button>
          <p v-if="!searching && !ledgerHits.length" class="pempty">{{ pick('没有匹配的流水或票据', 'No matching records') }}</p>
        </div>
      </div>
    </el-dialog>
  </nav>
</template>

<style scoped>
.brand{text-decoration:none;color:var(--ink)}
.menu{position:relative;display:flex;align-items:center;gap:2px;height:64px}
.nav-ink{position:absolute;left:0;bottom:0;height:2.5px;border-radius:2px;background:var(--brand);transition:transform .3s cubic-bezier(.4,0,.2,1),width .3s cubic-bezier(.4,0,.2,1);pointer-events:none}
.mgroup{position:relative;height:64px;display:flex;align-items:center}
.mi{height:64px;display:flex;align-items:center;gap:4px;padding:0 14px;border:none;background:transparent;
  font-family:inherit;font-size:13px;color:var(--ink2);cursor:pointer;position:relative;white-space:nowrap}
.mi:hover{color:var(--ink)}
.mi.on{color:var(--ink);font-weight:600}
.mi.on:after{content:none;position:absolute;left:14px;right:14px;bottom:-1px;height:2.5px;border-radius:2px;
  background:linear-gradient(90deg,var(--brand),var(--brand2))}
.cv{font-size:10px;color:var(--ink4);transition:transform .18s}
.cv.up{transform:rotate(180deg)}
.gmenu{position:absolute;top:64px;left:0;width:228px;background:#fff;border:1px solid var(--line);border-radius:14px;
  box-shadow:0 18px 40px -12px rgba(16,24,40,.22), 0 2px 6px rgba(16,24,40,.06);padding:6px;z-index:40}
.gitem{display:flex;align-items:center;gap:10px;padding:9px 10px;border-radius:9px;text-decoration:none;
  color:var(--ink2);font-size:12.5px;transition:background .12s,color .12s}
.gitem:hover{background:#f5f8ff;color:var(--brand)}
.gitem.on{background:#eef2ff;color:var(--brand);font-weight:600}
.gitem span{flex:1}
.gitem em{font-style:normal;font-size:10.5px;color:var(--brand);background:#fff;border-radius:5px;padding:1px 5px}
.search-pill{height:34px;display:flex;align-items:center;gap:8px;padding:0 10px;border:1px solid var(--line);border-radius:10px;
  background:#fff;box-shadow:var(--sh-sm);color:var(--ink4);font-size:12px;cursor:pointer;min-width:200px;transition:all .15s}
.search-pill:hover{border-color:#c8d2f8;color:var(--ink2)}
.si{width:14px;height:14px}
.search-pill span{flex:1;text-align:left}
kbd{font-family:inherit;font-size:10px;color:var(--ink4);border:1px solid var(--line);border-radius:5px;padding:1px 5px;background:#f8fafc}
.ibtn{position:relative}
.dot-badge{position:absolute;top:-5px;right:-5px;min-width:16px;height:16px;border-radius:8px;background:#f04438;color:#fff;
  font-size:9.5px;font-weight:600;display:grid;place-items:center;padding:0 4px;font-style:normal;box-shadow:0 0 0 2px #fff}
.notif{padding:4px}
.notif header{display:flex;align-items:center;justify-content:space-between;padding:6px 8px 10px;border-bottom:1px solid var(--line2)}
.notif h3{font-size:13px;font-weight:650}
.notif h3 span{font-size:11px;color:var(--ink4);font-weight:400;margin-left:6px}
.notif header button{border:none;background:none;color:var(--brand);font-size:11.5px;cursor:pointer}
.nitem{width:100%;text-align:left;border:none;background:none;padding:10px 8px;border-radius:8px;cursor:pointer;display:block}
.nitem:hover{background:#f5f8ff}
.nitem strong{display:block;font-size:12.5px;font-weight:600;color:var(--ink)}
.nitem small{display:block;font-size:11.5px;color:var(--ink3);margin-top:3px;line-height:1.55}
.nempty{font-size:12px;color:var(--ink4);padding:16px 8px}
.uinfo{padding:8px 12px 10px;border-bottom:1px solid var(--line2);margin-bottom:4px}
.uinfo b{display:block;font-size:12.5px}
.uinfo small{display:block;font-size:11px;color:var(--ink4);margin-top:2px}
.palette{display:flex;flex-direction:column;gap:10px}
.pinput{display:flex;align-items:center;gap:10px;height:46px;padding:0 14px;border:1px solid var(--line);border-radius:12px;background:#f8fafc}
.pinput input{flex:1;border:none;outline:none;background:transparent;font-size:14px;color:var(--ink)}
.pgroup{max-height:260px;overflow:auto}
.plabel{font-size:10.5px;color:var(--ink4);letter-spacing:.6px;padding:6px 4px}
.phit{width:100%;display:flex;align-items:center;gap:10px;padding:9px 10px;border:none;border-radius:9px;background:none;
  cursor:pointer;color:var(--ink2);font-size:12.5px;text-align:left}
.phit:hover{background:#f5f8ff;color:var(--brand)}
.phit span{flex:1}
.phit em{font-style:normal;font-size:11px;color:var(--ink4)}
.pempty{font-size:12px;color:var(--ink4);padding:10px}
.mobile-menu{display:none}
.pop-enter-active,.pop-leave-active{transition:opacity .14s ease, transform .14s ease}
.pop-enter-from,.pop-leave-to{opacity:0;transform:translateY(-4px)}
@media (max-width:1100px){
  .nav-in{gap:10px;padding:0 14px}
  .brand{padding-right:10px;gap:7px}
  .brand .bt span{display:none}
  .brand b{font-size:13px}
  .nav-r{gap:6px}
  .ibtn{width:32px;height:32px}
  .ava{width:30px;height:30px}
  .menu{display:none}
  .mobile-menu{display:flex;flex-direction:column;position:absolute;left:0;right:0;top:64px;background:#fff;
    border-bottom:1px solid var(--line);padding:8px 16px 12px;box-shadow:var(--sh);max-height:70vh;overflow:auto}
  .mgroup-label{font-size:10.5px;color:var(--ink4);letter-spacing:.6px;padding:10px 4px 4px}
  .mitem{padding:9px 6px;border-radius:8px;color:var(--ink2);text-decoration:none;font-size:13px}
  .mitem.on{background:#eef2ff;color:var(--brand);font-weight:600}
  .search-pill span,kbd{display:none}
  .search-pill{min-width:34px;padding:0;justify-content:center}
}
@media (max-width:640px){
  .brand .bt{display:none}
  .brand{padding-right:4px;border-right:none}
  .search-pill{display:none}
  .nav-in{gap:8px;padding:0 12px}
  .nav-r{gap:4px}
}

.nav.scrolled{box-shadow:0 8px 24px -18px rgba(16,24,40,.5)}
.quick{position:relative}
.quick-btn{height:34px;display:flex;align-items:center;gap:6px;padding:0 12px;border-radius:10px;border:none;
  background:linear-gradient(180deg,#4d7cff,#3659e3);color:#fff;font-size:12.5px;font-weight:600;cursor:pointer;
  box-shadow:0 6px 16px -8px rgba(54,89,227,.7);font-family:inherit}
.quick-btn:hover{filter:brightness(1.05)}
.quick-menu{position:absolute;right:0;top:42px;width:250px;background:#fff;border:1px solid var(--line);border-radius:14px;
  box-shadow:0 18px 40px -12px rgba(16,24,40,.24);padding:6px;z-index:40}
.qitem{width:100%;display:flex;align-items:center;gap:10px;padding:8px 10px;border:none;border-radius:9px;background:none;
  cursor:pointer;text-align:left;font-family:inherit}
.qitem:hover{background:#f5f8ff}
.qi{width:28px;height:28px;border-radius:8px;background:#eef2ff;color:var(--brand);display:grid;place-items:center;flex:0 0 28px}
.qt b{display:block;font-size:12.5px;color:var(--ink);font-weight:600}
.qt small{display:block;font-size:10.5px;color:var(--ink4);margin-top:2px}
@media (max-width:1100px){.quick{display:none}}
</style>
