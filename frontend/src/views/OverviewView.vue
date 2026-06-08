<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import client from '../api/client'
import UiIcon from '../components/ui/UiIcon.vue'
import MetricCard from '../components/ui/MetricCard.vue'
import TrendChart from '../components/ui/TrendChart.vue'
import InsightList from '../components/ui/InsightList.vue'
import CompositionBar from '../components/ui/CompositionBar.vue'
import type { WorkspaceView } from '../types/workspace'
import type { OverviewStats, TrendPoint, CategoryStat, AdvancedInsight } from '../types/stats'
import type { WorkforceCostView } from '../types/workforce'
import type { Account, AccountSummary } from '../types/account'
import type { ReceiptSummary } from '../types/receipt'
import { money, percent } from '../utils/format'

type Kind = 'dashboard' | 'operations' | 'finance' | 'workforce'
const props = defineProps<{ kind: Kind }>()

const workspace = ref<WorkspaceView | null>(null)
const stats = ref<OverviewStats | null>(null)
const trend = ref<TrendPoint[]>([])
const incomeCategories = ref<CategoryStat[]>([])
const expenseCategories = ref<CategoryStat[]>([])
const insights = ref<AdvancedInsight | null>(null)
const workforce = ref<WorkforceCostView | null>(null)
const accounts = ref<Account[]>([])
const accountSummary = ref<AccountSummary | null>(null)
const receiptSummary = ref<ReceiptSummary | null>(null)
const budgets = ref<Array<{ id: number; name: string; usageRate: number; warningReached: boolean; riskLevel: string }>>([])
const financeStats = ref<{ monthlyIncome: number; monthlyExpense: number; monthlyBalance: number } | null>(null)
const loading = ref(false)
const error = ref('')

const titles: Record<Kind, [string, string]> = {
  dashboard: ['经营概览', '本月经营重点与待办'],
  operations: ['经营总览', '收入、成本与经营健康度'],
  finance: ['财务总览', '资金余额、账户与票据风险'],
  workforce: ['人力成本', '人员规模、成本构成与部门分布'],
}
const title = computed(() => titles[props.kind])
const icon = computed(() => (props.kind === 'finance' ? 'wallet' : props.kind === 'workforce' ? 'users' : 'trend'))

const metrics = computed(() => {
  const m = workspace.value?.metrics
  if (props.kind === 'dashboard') {
    return [
      { label: '本月收入', value: money(m?.monthlyIncome), tone: 'green' as const, icon: 'wallet' },
      { label: '本月成本', value: money(m?.monthlyExpense), tone: 'red' as const, icon: 'doc' },
      { label: '本月结余', value: money(m?.monthlyProfit), tone: 'brand' as const, icon: 'trend', hero: true },
      { label: '预算使用率', value: percent(m?.budgetUsageRate), tone: 'amber' as const, icon: 'shield' },
      { label: '待审批', value: String(m?.pendingApprovalCount ?? '—'), tone: 'ink' as const, icon: 'check' },
      { label: '待复核流水', value: String(m?.reviewTransactionCount ?? '—'), tone: 'ink' as const, icon: 'list' },
    ]
  }
  if (props.kind === 'operations') {
    return [
      { label: '经营收入', value: money(stats.value?.monthlyIncome), tone: 'green' as const, icon: 'wallet', trend: trend.value.map((p) => p.income) },
      { label: '经营成本', value: money(stats.value?.monthlyExpense), tone: 'red' as const, icon: 'doc', trend: trend.value.map((p) => p.expense) },
      { label: '经营结余', value: money(stats.value?.monthlyBalance), tone: 'brand' as const, icon: 'trend', hero: true, trend: trend.value.map((p) => p.balance) },
      { label: '预算使用率', value: percent(stats.value?.budgetUsageRate), tone: 'amber' as const, icon: 'shield' },
      { label: '大额流水', value: String(insights.value?.largeTransactions.length ?? '—'), tone: 'ink' as const, icon: 'alert' },
      { label: '预算预警', value: String(insights.value?.budgetAlerts.length ?? '—'), tone: 'ink' as const, icon: 'alert' },
    ]
  }
  if (props.kind === 'finance') {
    return [
      { label: '总资产', value: money(accountSummary.value?.totalAssets), tone: 'green' as const, icon: 'wallet' },
      { label: '总负债', value: money(accountSummary.value?.totalLiabilities), tone: 'red' as const, icon: 'doc' },
      { label: '净资产', value: money(accountSummary.value?.netWorth), tone: 'brand' as const, icon: 'trend', hero: true },
      { label: '可用余额', value: money(accountSummary.value?.availableBalance), tone: 'ink' as const, icon: 'wallet' },
      { label: '待对账', value: String(accountSummary.value?.pendingReconciliationCount ?? '—'), tone: 'amber' as const, icon: 'shield' },
      { label: '票据待处理', value: String(receiptSummary.value?.pendingReviewCount ?? '—'), tone: 'amber' as const, icon: 'doc' },
    ]
  }
  const w = workforce.value
  return [
    { label: '月人力成本', value: money(w?.costs.total), tone: 'brand' as const, icon: 'wallet', hero: true },
    { label: '人均成本', value: money(w?.costs.average), tone: 'ink' as const, icon: 'users' },
    { label: '在职人数', value: String(w?.headcount.active ?? '—'), tone: 'green' as const, icon: 'users' },
    { label: '社保公积金', value: money(w ? w.costs.employerSocial + w.costs.employerHousing : null), tone: 'amber' as const, icon: 'shield' },
    { label: '部门数', value: String(w?.departments.length ?? '—'), tone: 'ink' as const, icon: 'grid' },
    { label: '本月关注', value: String(w?.attentionItems.length ?? '—'), tone: 'ink' as const, icon: 'alert' },
  ]
})

const insightItems = computed(() => {
  if (props.kind === 'dashboard') {
    return (workspace.value?.priorityActions || []).map((item) => ({
      title: item.title, detail: item.detail, path: item.path, icon: 'alert',
      tone: item.severity === 'danger' ? 'red' as const : item.severity === 'warning' ? 'amber' as const : 'green' as const,
    }))
  }
  if (props.kind === 'workforce') {
    return (workforce.value?.attentionItems || []).map((item) => ({ title: item.title, detail: item.detail, path: item.path, icon: 'alert', tone: 'amber' as const }))
  }
  if (props.kind === 'operations') {
    return (insights.value?.budgetAlerts || []).map((item) => ({ title: item.name, detail: `预算使用率 ${percent(item.usageRate)}`, path: '/budgets', icon: 'shield', tone: 'amber' as const }))
  }
  return accounts.value.filter((item) => item.reconciliationStatus !== 'reconciled').slice(0, 5)
    .map((item) => ({ title: item.name, detail: `对账状态：${item.reconciliationStatus}`, path: '/accounts', icon: 'wallet', tone: 'amber' as const }))
})

const closingTasks = computed(() => {
  if (props.kind !== 'finance') return []
  const pendingRecon = accountSummary.value?.pendingReconciliationCount ?? 0
  const pendingReceipt = receiptSummary.value?.pendingReviewCount ?? 0
  const missingLink = receiptSummary.value?.missingTransactionCount ?? 0
  const overBudgets = budgets.value.filter((b) => b.usageRate >= 1 || b.riskLevel === 'high' || b.riskLevel === 'critical').length
  const profit = financeStats.value?.monthlyBalance ?? 0
  return [
    { label: '资金账户对账', done: pendingRecon === 0, detail: pendingRecon === 0 ? '账户余额已完成核对' : pendingRecon + ' 个账户待对账', path: '/accounts' },
    { label: '票据核验归档', done: pendingReceipt === 0, detail: pendingReceipt === 0 ? '暂无待复核票据' : pendingReceipt + ' 张票据待复核', path: '/receipts' },
    { label: '凭证流水匹配', done: missingLink === 0, detail: missingLink === 0 ? '票据与流水已闭环' : missingLink + ' 张票据缺少流水关联', path: '/receipts' },
    { label: '预算偏差复盘', done: overBudgets === 0, detail: overBudgets === 0 ? '预算执行在阈值内' : overBudgets + ' 项预算超支或高风险', path: '/budgets' },
    { label: '月度利润检查', done: profit > 0, detail: profit > 0 ? '本月结余为正' : '本月结余未转正，需检查成本结构', path: '/reports' },
  ]
})
const accountTypes = computed(() => {
  const map = new Map<string, number>()
  for (const item of accounts.value) {
    const key = item.type || 'other'
    map.set(key, (map.get(key) || 0) + Number(item.balance || 0))
  }
  const total = Array.from(map.values()).reduce((n, v) => n + Math.abs(v), 0) || 1
  const colors = ['#3659e3', '#6c8cff', '#22b8a0', '#f0a63a', '#94a3b8']
  const labels: Record<string, string> = { cash: '现金', bank: '银行', credit: '信用', digital: '数字钱包', investment: '理财', debt: '负债' }
  return Array.from(map.entries()).map(([key, value], i) => ({
    name: labels[key] || key,
    value: Math.abs(value),
    percent: (Math.abs(value) / total) * 100,
    color: colors[i % colors.length],
  }))
})
const chartPoints = computed<TrendPoint[]>(() => {
  if (props.kind === 'workforce') {
    return (workforce.value?.trend || []).map((p) => ({ month: p.period, income: p.total, expense: p.average, balance: p.total }) as TrendPoint)
  }
  return trend.value
})
const showChart = computed(() => props.kind !== 'finance' && chartPoints.value.length > 0)

const leftBars = computed(() => {
  if (props.kind === 'finance') {
    return accounts.value.slice(0, 4).map((item, i) => ({ name: item.name, value: item.balance, percent: 100 / Math.max(1, Math.min(4, accounts.value.length)) * (i === 0 ? 1 : 1), color: ['#3659e3', '#6c8cff', '#94a3b8', '#cbd5e1'][i % 4] }))
  }
  const src = props.kind === 'workforce' ? (workforce.value?.departments || []).map((d) => ({ categoryName: d.departmentName, amount: d.total, percentage: 0 })) : incomeCategories.value
  const total = src.reduce((n, x) => n + x.amount, 0) || 1
  return src.slice(0, 4).map((item, i) => ({
    name: item.categoryName, value: item.amount, percent: (item.percentage || (item.amount / total) * 100),
    color: ['#0e9f6e', '#34c08b', '#a7e3c8', '#12b76a'][i % 4],
  }))
})
const rightBars = computed(() => {
  let src: Array<{ name: string; value: number; percentage?: number }> = []
  if (props.kind === 'finance') {
    const summary = receiptSummary.value
    src = summary
      ? [
          { name: '待处理金额', value: summary.pendingAmount || 0 },
          { name: '已核验金额', value: Math.max(0, (summary.totalAmount || 0) - (summary.pendingAmount || 0)) },
        ]
      : []
  } else if (props.kind === 'workforce') {
    src = [
      { name: '薪资', value: workforce.value?.costs.salary || 0 },
      { name: '加班', value: workforce.value?.costs.overtime || 0 },
      { name: '企业社保', value: workforce.value?.costs.employerSocial || 0 },
      { name: '企业公积金', value: workforce.value?.costs.employerHousing || 0 },
    ]
  } else {
    src = (expenseCategories.value || []).map((c) => ({ name: c.categoryName, value: c.amount, percentage: c.percentage }))
  }
  const total = src.reduce((n, x) => n + x.value, 0) || 1
  return src.slice(0, 4).map((item, i) => ({
    name: item.name,
    value: item.value,
    percent: item.percentage ?? (item.value / total) * 100,
    color: ['#3659e3', '#6c8cff', '#94a3b8', '#cbd5e1'][i % 4],
  }))
})

const leftTitle = computed(() => (props.kind === 'finance' ? '账户余额' : props.kind === 'workforce' ? '部门成本' : '收入结构'))
const rightTitle = computed(() => (props.kind === 'finance' ? '票据金额构成' : props.kind === 'workforce' ? '成本构成' : '成本结构'))
const leftTotal = computed(() => {
  if (props.kind === 'finance') return money(accountSummary.value?.totalAssets)
  if (props.kind === 'workforce') return money(workforce.value?.costs.total)
  return money(stats.value?.monthlyIncome)
})
const rightTotal = computed(() => {
  if (props.kind === 'finance') return money(receiptSummary.value?.totalAmount)
  if (props.kind === 'workforce') return money(workforce.value?.costs.total)
  return money(stats.value?.monthlyExpense)
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    if (props.kind === 'operations') {
      const [s, t, i, e, x] = await Promise.all([
        client.get<OverviewStats>('/stats/overview'),
        client.get<TrendPoint[]>('/stats/trend', { params: { period: 'month', limit: 6 } }),
        client.get<CategoryStat[]>('/stats/category', { params: { type: 'income' } }),
        client.get<CategoryStat[]>('/stats/category', { params: { type: 'expense' } }),
        client.get<AdvancedInsight>('/stats/insights'),
      ])
      stats.value = s.data; trend.value = t.data; incomeCategories.value = i.data; expenseCategories.value = e.data; insights.value = x.data
    }
    if (props.kind === 'finance') {
      const [s, a, r, b, st] = await Promise.all([
        client.get<AccountSummary>('/accounts/summary'),
        client.get<Account[]>('/accounts'),
        client.get<ReceiptSummary>('/receipts/summary', { params: { companyId: localStorage.getItem('activeCompanyId') } }),
        client.get<{ content: typeof budgets.value }>('/budgets', { params: { page: 0, size: 200 } }).catch(() => null),
        client.get<{ monthlyIncome: number; monthlyExpense: number; monthlyBalance: number }>('/stats/overview').catch(() => null),
      ])
      accountSummary.value = s.data; accounts.value = a.data; receiptSummary.value = r.data
      budgets.value = b?.data?.content || []
      financeStats.value = st?.data || null
    }
    if (props.kind === 'workforce') {
      const result = await client.get<WorkforceCostView>('/workforce-cost')
      workforce.value = result.data
    }
  } catch {
    error.value = '数据加载失败，请检查后端服务与当前账号权限。'
  } finally {
    loading.value = false
  }
}

watch(() => props.kind, load)
onMounted(load)
</script>

<template>
  <div class="wrap ov">
    <div class="ph">
      <div class="l">
        <div class="tile"><UiIcon :name="icon" :size="20" /></div>
        <div><h1>{{ title[0] }}</h1><p class="sub">{{ title[1] }}</p></div>
      </div>
      <button class="btn" type="button" @click="load"><UiIcon name="refresh" :size="14" />刷新数据</button>
    </div>

    <div v-if="error" class="err" role="alert">{{ error }}<button class="btn" type="button" @click="load">重试</button></div>

    <template v-else>
      <div class="kpis" v-loading="loading">
        <MetricCard v-for="item in metrics" :key="item.label" v-bind="item" />
      </div>

      <div class="row-main">
        <section v-if="showChart" class="card span2">
          <header>
            <h3><span class="ic"><UiIcon name="trend" :size="14" /></span>{{ kind === 'workforce' ? '人力成本趋势' : '经营趋势' }}</h3>
          </header>
          <div class="bd"><TrendChart :points="chartPoints" :height="248" /></div>
        </section>
        <section v-else-if="kind === 'finance'" class="card span2">
          <header><h3><span class="ic"><UiIcon name="check" :size="14" /></span>月结检查清单</h3><span class="hint">{{ closingTasks.filter(t => t.done).length }}/{{ closingTasks.length }} 已完成</span></header>
          <div class="bd">
            <div class="checklist">
              <RouterLink v-for="task in closingTasks" :key="task.label" :to="task.path" class="ck" :class="{ done: task.done }">
                <span class="ck-dot"><UiIcon :name="task.done ? 'check' : 'alert'" :size="12" /></span>
                <span class="ck-tx"><b>{{ task.label }}</b><small>{{ task.detail }}</small></span>
                <UiIcon name="arrow" :size="12" class="ck-go" />
              </RouterLink>
            </div>
          </div>
        </section>
        <section class="card">
          <header><h3><span class="ic"><UiIcon name="spark" :size="14" /></span>{{ kind === 'workforce' ? '人力关注' : '经营提醒' }}</h3><span class="hint">{{ insightItems.length }} 项</span></header>
          <div class="bd"><InsightList :items="insightItems" /></div>
        </section>
      </div>

      <div class="row-three">
        <section class="card">
          <header><h3><span class="ic"><UiIcon name="wallet" :size="14" /></span>{{ leftTitle }}</h3></header>
          <div class="bd"><CompositionBar :items="leftBars" :total-label="leftTitle" :total-value="leftTotal" /></div>
        </section>
        <section class="card">
          <header><h3><span class="ic"><UiIcon name="doc" :size="14" /></span>{{ rightTitle }}</h3></header>
          <div class="bd"><CompositionBar :items="rightBars" :total-label="rightTitle" :total-value="rightTotal" /></div>
        </section>
        <section class="card">
          <header><h3><span class="ic"><UiIcon :name="kind === 'finance' ? 'grid' : 'list'" :size="14" /></span>{{ kind === 'workforce' ? '人员概览' : kind === 'finance' ? '账户类型分布' : '指标摘要' }}</h3></header>
          <div v-if="kind === 'finance'" class="bd"><CompositionBar :items="accountTypes" total-label="资金合计" :total-value="money(accountSummary?.totalAssets)" /></div>
          <div v-else class="bd summary">
            <div v-for="item in metrics.slice(0, 4)" :key="item.label" class="srow">
              <span>{{ item.label }}</span><b>{{ item.value }}</b>
            </div>
          </div>
        </section>
      </div>
    </template>
  </div>
</template>

<style scoped>
.ov{display:flex;flex-direction:column;gap:14px}
.ph{margin-bottom:2px}
.kpis{display:grid;grid-template-columns:repeat(6,minmax(0,1fr));gap:12px}
.row-main{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px}
.row-three{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px}
.span2{grid-column:span 2}
.card .bd{padding:0 20px 14px}
.summary{display:flex;flex-direction:column;gap:2px}
.srow{display:flex;justify-content:space-between;align-items:baseline;padding:9px 0;border-bottom:1px solid var(--line2);font-size:12px;color:var(--ink3)}
.srow:last-child{border-bottom:none}
.srow b{font-size:13px;color:var(--ink);font-weight:600;font-variant-numeric:tabular-nums}
.err{display:flex;align-items:center;gap:12px;padding:12px 16px;border-radius:12px;background:#fdeaee;color:#b42318;font-size:13px}
</style>

<style scoped>
@media (max-width:1100px){
  .kpis{grid-template-columns:repeat(3,minmax(0,1fr))}
  .row-main,.row-three{grid-template-columns:minmax(0,1fr)}
  .span2{grid-column:auto}
}
@media (max-width:640px){
  .kpis{grid-template-columns:repeat(2,minmax(0,1fr))}
  .ph{flex-direction:column;gap:12px}
  .ov h1{font-size:26px}
}
</style>
<style scoped>
.checklist{display:flex;flex-direction:column}
.ck{display:flex;align-items:center;gap:11px;padding:11px 0;border-bottom:1px solid var(--line2);text-decoration:none;color:inherit}
.ck:last-child{border-bottom:none}
.ck-dot{width:24px;height:24px;border-radius:50%;display:grid;place-items:center;background:#fdf2e3;color:#b54708;flex:0 0 24px}
.ck.done .ck-dot{background:#e6f7ef;color:#0a6b46}
.ck-tx{flex:1;min-width:0}
.ck-tx b{display:block;font-size:12.5px;font-weight:600;color:var(--ink)}
.ck-tx small{display:block;font-size:11.5px;color:var(--ink3);margin-top:2px}
.ck-go{color:var(--ink4);opacity:0;transition:opacity .15s}
.ck:hover .ck-go{opacity:1}
.ck:hover .ck-tx b{color:var(--brand)}
</style>
