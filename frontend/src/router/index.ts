import { createRouter, createWebHistory } from 'vue-router'
import AppShell from '../layouts/AppShell.vue'
import ReportsView from '../views/ReportsLiveView.vue'
import AdvancedReportsView from '../views/AdvancedReportsView.vue'
import ReceiptsView from '../views/ReceiptsView.vue'
import LedgerView from '../views/LedgerView.vue'
import LoginView from '../views/LoginView.vue'
import OverviewView from '../views/OverviewView.vue'
import DashboardView from '../views/DashboardView.vue'
import EnterpriseView from '../views/EnterpriseView.vue'
import RegisterView from '../views/RegisterView.vue'
import SettingsView from '../views/SettingsView.vue'
import BackupView from '../views/BackupView.vue'
import PolicyView from '../views/PolicyView.vue'
import { useAuthStore } from '../stores/auth'
import { canVisit } from '../utils/navigationAccess'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/reports' },
    { path: '/login', component: LoginView },
    { path: '/register', component: RegisterView },
    { path: '/', component: AppShell, children: [
      { path: 'reports', component: ReportsView },
      { path: 'reports/advanced', component: AdvancedReportsView },
      { path: 'transactions/new', redirect: { path: '/transactions', query: { new: '1' } } },
      { path: 'policy-center', component: PolicyView },
      { path: 'settings', component: SettingsView },
      { path: 'backup', component: BackupView },
      { path: 'dashboard', component: DashboardView },
      ...(['operations','finance','hr/workforce-cost'] as const).map(path => ({ path, component: OverviewView, props: { kind: path==='hr/workforce-cost' ? 'workforce' : path } })),
      ...([{path:'tax',kind:'tax'},{path:'approvals',kind:'approvals'},{path:'hr/organization',kind:'organization'},{path:'hr/benefits',kind:'benefits'},{path:'hr/performance',kind:'performance'},{path:'admin/compensation',kind:'compensation'},{path:'admin/users',kind:'users'}] as const).map(item=>({path:item.path,component:EnterpriseView,props:{kind:item.kind}})),
      { path: 'receipts', component: ReceiptsView },
      ...(['transactions','budgets','accounts','recurring'] as const).map(kind => ({ path: kind, component: LedgerView, props: { kind } })),
    ] },
    { path: '/:pathMatch(.*)*', redirect: '/reports' },
  ],
})

router.beforeEach(async to => {
  const auth = useAuthStore()
  if (to.path === '/login' || to.path === '/register') {
    if (!auth.authenticated) return true
    if (!auth.ready) {
      try { await auth.hydrate() } catch { return true }
    }
    return auth.user ? '/dashboard' : true
  }
  if (!auth.authenticated) return { path: '/login', query: { next: to.fullPath } }
  if (!auth.ready) {
    try { await auth.hydrate() }
    catch { return { path: '/login', query: { next: to.fullPath } } }
  }
  if (!canVisit(to.path, auth)) return '/dashboard'
  return true
})

export default router
