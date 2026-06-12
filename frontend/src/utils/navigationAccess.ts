import { useAuthStore } from '../stores/auth'

type AuthStore = ReturnType<typeof useAuthStore>
type Rule = { module?: string; permissions?: string[]; adminOnly?: boolean }

const coreModules = new Set([
  'workspace', 'approvals', 'operations', 'transactions', 'budgets', 'reports',
  'recurring', 'finance', 'accounts', 'evidence', 'access-management', 'settings',
])

const rules: Record<string, Rule> = {
  '/dashboard': { module: 'workspace' },
  '/approvals': { module: 'approvals', permissions: ['approval.manage'] },
  '/operations': { module: 'operations', permissions: ['operations.read', 'reports.read'] },
  '/transactions': { module: 'transactions', permissions: ['operations.read', 'operations.write', 'finance.read'] },
  '/budgets': { module: 'budgets', permissions: ['budget.manage', 'operations.read'] },
  '/reports': { module: 'reports', permissions: ['reports.read'] },
  '/recurring': { module: 'recurring', permissions: ['operations.read', 'operations.write'] },
  '/finance': { module: 'finance', permissions: ['finance.read'] },
  '/accounts': { module: 'accounts', permissions: ['finance.read'] },
  '/receipts': { module: 'evidence', permissions: ['finance.read'] },
  '/tax': { module: 'tax', permissions: ['tax.manage'] },
  '/hr/organization': { module: 'people-core', permissions: ['people.read'] },
  '/hr/workforce-cost': { module: 'workforce-cost', permissions: ['workforce.cost.read', 'workforce.cost.manage'] },
  '/hr/benefits': { module: 'talent-suite', permissions: ['people.read'] },
  '/hr/performance': { module: 'talent-suite', permissions: ['people.read'] },
  '/admin/compensation': { module: 'workforce-cost', permissions: ['workforce.cost.manage'] },
  '/admin/users': { module: 'access-management', permissions: ['admin.permissions', 'company.manage'] },
  '/policy-center': { module: 'policy', permissions: ['policy.read'] },
  '/backup': { module: 'backup', permissions: ['admin.permissions'], adminOnly: true },
  '/settings': { module: 'settings' },
}

const householdPaths = new Set([
  '/dashboard', '/transactions', '/budgets', '/reports', '/recurring',
  '/accounts', '/settings', '/backup',
])

export function canVisit(path: string, auth: AuthStore): boolean {
  const prefix = Object.keys(rules).sort((a, b) => b.length - a.length).find(key => path === key || path.startsWith(`${key}/`))
  if (!prefix) return false
  const rule = rules[prefix]
  if (rule.adminOnly && auth.user?.role !== 1) return false
  if (localStorage.getItem('activeSubjectType') === 'household') return householdPaths.has(prefix)
  const enabled = new Set(auth.accessContext?.modules.enabled || [...coreModules])
  if (rule.module && !enabled.has(rule.module)) return false
  if (auth.user?.role === 1 || !rule.permissions?.length) return true
  return rule.permissions.some(permission => auth.accessContext?.permissions.includes(permission))
}
