<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import client from '../api/client'
import UiIcon from '../components/ui/UiIcon.vue'
import MetricCard from '../components/ui/MetricCard.vue'
import TrendChart from '../components/ui/TrendChart.vue'
import CompositionBar from '../components/ui/CompositionBar.vue'
import InsightList from '../components/ui/InsightList.vue'
import type { WorkspaceView } from '../types/workspace'
import type { TrendPoint, CategoryStat } from '../types/stats'
import { money, percent } from '../utils/format'

const workspace = ref<WorkspaceView | null>(null)
const trend = ref<TrendPoint[]>([])
const incomeCats = ref<CategoryStat[]>([])
const expenseCats = ref<CategoryStat[]>([])
const loading = ref(true)
const error = ref('')

const m = computed(() => workspace.value?.metrics)
const spark = (key: 'income' | 'expense' | 'balance') => trend.value.map((p) => p[key] ?? 0)

const metrics = computed(() => [
  { label: '本月收入', value: money(m.value?.monthlyIncome).replace('¥', ''), currency: '¥', foot: '本月截至今日', tone: 'green' as const, icon: 'wallet', trend: spark('income') },
  { label: '本月成本', value: money(m.value?.monthlyExpense).replace('¥', ''), currency: '¥', foot: '含人力与采购', tone: 'red' as const, icon: 'doc', trend: spark('expense') },
  { label: '本月结余', value: money(m.value?.monthlyProfit).replace('¥', ''), currency: '¥', foot: '收入 − 成本', tone: 'brand' as const, icon: 'trend', trend: spark('balance'), hero: true },
  { label: '预算使用率', value: percent(m.value?.budgetUsageRate), foot: m.value?.budgetAmount ? `预算 ${money(m.value.budgetAmount)}` : '本月预算', tone: 'amber' as const, icon: 'shield' },
  { label: '待审批', value: String(m.value?.pendingApprovalCount ?? 0), unit: '项', foot: '审批中心待处理', tone: 'ink' as const, icon: 'check' },
  { label: '待复核流水', value: String(m.value?.reviewTransactionCount ?? 0), unit: '笔', foot: '大额或缺备注', tone: 'ink' as const, icon: 'list' },
])

const severity = computed(() => {
  const map: Record<string, { label: string; color: string }> = {
    success: { label: '健康', color: '#12b76a' },
    notice: { label: '关注', color: '#3659e3' },
    warning: { label: '预警', color: '#f79009' },
    danger: { label: '风险', color: '#f04438' },
  }
  return map[workspace.value?.severity || 'notice'] || map.notice
})
const score = computed(() => Number(workspace.value?.score ?? 0))
const modulesHealth = computed(() => (workspace.value?.modules || []).slice(0, 4))

const insights = computed(() => (workspace.value?.priorityActions || []).map((item) => ({
  title: item.title,
  detail: item.detail,
  path: item.path,
  tone: item.severity === 'danger' ? 'red' as const : item.severity === 'warning' ? 'amber' as const : item.severity === 'success' ? 'green' as const : 'brand' as const,
  icon: item.severity === 'danger' ? 'alert' : item.severity === 'warning' ? 'shield' : 'check',
})))

const incomeItems = computed(() => (incomeCats.value || []).map((item, i) => ({
  name: item.categoryName, value: item.amount, percent: item.percentage,
  color: ['#0e9f6e', '#34c08b', '#a7e3c8', '#12b76a', '#6ce2b0'][i % 5],
})))
const expenseItems = computed(() => (expenseCats.value || []).map((item, i) => ({
  name: item.categoryName, value: item.amount, percent: item.percentage,
  color: ['#3659e3', '#6c8cff', '#94a3b8', '#cbd5e1', '#7c8cff'][i % 5],
})))

const dailyChecks = computed(() => workspace.value?.dailyChecks || [])
const upcoming = computed(() => (workspace.value?.upcomingItems || []).slice(0, 4))
const quickLinks = [
  { label: '录入流水', path: '/transactions/new', icon: 'plus' },
  { label: '经营报表', path: '/reports', icon: 'trend' },
  { label: '经营预算', path: '/budgets', icon: 'shield' },
  { label: '票据凭证', path: '/receipts', icon: 'doc' },
  { label: '资金账户', path: '/accounts', icon: 'wallet' },
  { label: '周期事项', path: '/recurring', icon: 'calendar' },
]
const recent = computed(() => (workspace.value?.recentTransactions || []).slice(0, 4))
const budgetRisks = computed(() => (workspace.value?.budgetRisks || []).slice(0, 3))

function riskTone(level: string) {
  if (level === 'critical' || level === 'high') return 'linear-gradient(90deg,#f04438,#ff8a80)'
  if (level === 'medium') return 'linear-gradient(90deg,#f79009,#fdb022)'
  return 'linear-gradient(90deg,#3659e3,#6c8cff)'
}
function moduleTone(sev: string) {
  return severityMap[sev] || '#3659e3'
}
const severityMap: Record<string, string> = { success: '#12b76a', notice: '#3659e3', warning: '#f79009', danger: '#f04438' }

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [w, t, inc, exp] = await Promise.all([
      client.get<WorkspaceView>('/workspace'),
      client.get<TrendPoint[]>('/stats/trend', { params: { period: 'month', limit: 6 } }),
      client.get<CategoryStat[]>('/stats/category', { params: { type: 'income' } }),
      client.get<CategoryStat[]>('/stats/category', { params: { type: 'expense' } }),
    ])
    workspace.value = w.data
    trend.value = t.data
    incomeCats.value = inc.data
    expenseCats.value = exp.data
  } catch {
    error.value = '数据加载失败，请检查后端服务与当前账号权限。'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="wrap dash">
    <div class="ph">
      <div class="l">
        <div class="tile"><UiIcon name="grid" :size="20" /></div>
        <div>
          <h1>经营概览</h1>
          <p class="sub">{{ workspace?.companyName || '当前主体' }} · {{ workspace?.period || '本月' }} · 收入、成本、预算与待办</p>
        </div>
      </div>
      <div class="acts">
        <button class="btn" type="button" @click="load"><UiIcon name="refresh" :size="14" />刷新</button>
        <RouterLink class="btn pri" to="/transactions/new"><UiIcon name="plus" :size="14" />录入流水</RouterLink>
      </div>
    </div>

    <div v-if="error" class="err" role="alert">{{ error }}<button class="btn" type="button" @click="load">重试</button></div>

    <div class="kpis" v-loading="loading">
      <MetricCard v-for="item in metrics" :key="item.label" v-bind="item" />
    </div>

    <div class="row-main">
      <section class="card span2">
        <header>
          <h3><span class="ic"><UiIcon name="trend" :size="14" /></span>近六个月经营趋势</h3>
          <div class="legend">
            <span><s style="background:#12b76a" />收入</span>
            <span><s style="background:#f04438" />成本</span>
            <span><s style="background:linear-gradient(90deg,#3659e3,#6c8cff)" />结余</span>
          </div>
        </header>
        <div class="bd"><TrendChart :points="trend" :height="204" /></div>
      </section>

      <section class="card">
        <header>
          <h3><span class="ic"><UiIcon name="spark" :size="14" /></span>优先处理</h3>
          <span class="hint">{{ insights.length }} 项</span>
        </header>
        <div class="bd"><InsightList :items="insights" /></div>
      </section>
    </div>

    <div class="row-three">
      <section class="card health">
        <header>
          <h3><span class="ic"><UiIcon name="shield" :size="14" /></span>经营健康度</h3>
          <span class="sev" :style="{ color: severity.color, background: `${severity.color}14` }">{{ severity.label }}</span>
        </header>
        <div class="bd">
          <div class="hscore">
            <b :style="{ color: severity.color }">{{ score }}</b><span>分</span>
          </div>
          <div class="hbar"><i :style="{ width: `${Math.min(100, score)}%`, background: severity.color }" /></div>
          <p class="hhint">{{ score >= 85 ? '当前已进入健康区间，请继续保持关键指标稳定。' : `距健康区间还差 ${Math.max(85 - score, 0)} 分，建议先处理右侧优先事项。` }}</p>
          <div class="mods">
            <div v-for="mod in modulesHealth" :key="mod.key" class="mod">
              <span>{{ mod.title }}</span>
              <b>{{ mod.score }}</b>
              <div class="mbar"><i :style="{ width: `${Math.min(100, mod.score)}%`, background: moduleTone(mod.severity) }" /></div>
            </div>
            <p v-if="!modulesHealth.length" class="empty">暂无模块评分</p>
          </div>
        </div>
      </section>

      <section class="card">
        <header><h3><span class="ic"><UiIcon name="wallet" :size="14" /></span>收入结构</h3><span class="hint">本月</span></header>
        <div class="bd"><CompositionBar :items="incomeItems" total-label="经营收入" :total-value="money(m?.monthlyIncome)" /></div>
      </section>

      <section class="card">
        <header><h3><span class="ic"><UiIcon name="doc" :size="14" /></span>成本结构</h3><span class="hint">本月</span></header>
        <div class="bd"><CompositionBar :items="expenseItems" total-label="经营成本" :total-value="money(m?.monthlyExpense)" /></div>
      </section>
    </div>

    <div class="row-three">
      <section class="card">
        <header><h3><span class="ic"><UiIcon name="check" :size="14" /></span>今日检查</h3><span class="hint">{{ dailyChecks.filter(c => c.done).length }}/{{ dailyChecks.length }}</span></header>
        <div class="bd">
          <div class="checks">
            <RouterLink v-for="item in dailyChecks" :key="item.key" :to="item.path" class="chk" :class="{ done: item.done }">
              <span class="chk-dot"><UiIcon :name="item.done ? 'check' : 'alert'" :size="11" /></span>
              <span class="chk-tx"><b>{{ item.label }}</b><small>{{ item.detail }}</small></span>
            </RouterLink>
            <p v-if="!dailyChecks.length" class="empty">今天没有待检查项</p>
          </div>
        </div>
      </section>

      <section class="card">
        <header><h3><span class="ic"><UiIcon name="calendar" :size="14" /></span>即将到期</h3><span class="hint">{{ upcoming.length }} 项</span></header>
        <div class="bd">
          <div class="checks">
            <RouterLink v-for="item in upcoming" :key="item.id" :to="item.path" class="chk" :class="{ late: item.overdue }">
              <span class="chk-dot"><UiIcon name="calendar" :size="11" /></span>
              <span class="chk-tx"><b>{{ item.title }}</b><small>{{ item.dueDate?.slice(0, 10) }} · {{ item.overdue ? '已逾期' : '待处理' }}</small></span>
            </RouterLink>
            <p v-if="!upcoming.length" class="empty">近期没有到期事项</p>
          </div>
        </div>
      </section>

      <section class="card">
        <header><h3><span class="ic"><UiIcon name="grid" :size="14" /></span>快捷入口</h3></header>
        <div class="bd quick">
          <RouterLink v-for="link in quickLinks" :key="link.path" :to="link.path" class="q">
            <UiIcon :name="link.icon" :size="14" />{{ link.label }}
          </RouterLink>
        </div>
      </section>
    </div>

    <div class="row-main">
      <section class="card">
        <header><h3><span class="ic"><UiIcon name="shield" :size="14" /></span>预算预警</h3><span class="hint">{{ budgetRisks.length }} 项</span></header>
        <div class="bd budget">
          <div v-for="risk in budgetRisks" :key="risk.id" class="brow">
            <div class="bt"><b>{{ risk.name }}</b><span>{{ money(risk.spent) }} / {{ money(risk.amount) }}</span></div>
            <div class="bbar"><i :style="{ width: `${Math.min(100, risk.usageRate * 100)}%`, background: riskTone(risk.riskLevel) }" /></div>
            <div class="bf"><span>{{ percent(risk.usageRate) }}</span><em v-if="risk.usageRate >= 1" class="over">超支</em></div>
          </div>
          <p v-if="!budgetRisks.length" class="empty">本月预算执行正常</p>
        </div>
      </section>

      <section class="card span2">
        <header>
          <h3><span class="ic"><UiIcon name="list" :size="14" /></span>最近流水</h3>
          <RouterLink class="hint link" to="/transactions">全部流水 <UiIcon name="arrow" :size="12" /></RouterLink>
        </header>
        <div class="bd">
          <div class="tx">
            <div v-for="item in recent" :key="item.id" class="trow">
              <span class="date">{{ item.date?.slice(0, 10) }}</span>
              <span class="note">{{ item.note || item.categoryName }}</span>
              <span class="cat">{{ item.categoryName }}</span>
              <b :class="item.type === 1 ? 'in' : 'out'">{{ item.type === 1 ? '+' : '−' }}{{ money(item.amount) }}</b>
            </div>
            <p v-if="!recent.length" class="empty">暂无流水</p>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.dash{display:flex;flex-direction:column;gap:14px}
.ph{margin-bottom:2px}
.acts{display:flex;gap:10px;padding-top:4px}
.acts .btn{text-decoration:none}
.kpis{display:grid;grid-template-columns:repeat(6,minmax(0,1fr));gap:12px}
.row-main{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px}
.row-three{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px}
.span2{grid-column:span 2}
.card{min-height:0}
.card .bd{padding:0 20px 14px}
.legend{display:flex;gap:8px;font-size:11px;color:var(--ink3)}
.legend span{display:inline-flex;align-items:center;gap:6px;height:26px;padding:0 10px;border-radius:8px;background:#f4f7fb}
.legend s{width:8px;height:8px;border-radius:2px;text-decoration:none}
.link{display:inline-flex;align-items:center;gap:4px;text-decoration:none}
.link:hover{color:var(--brand)}
.sev{font-size:11px;padding:3px 9px;border-radius:999px;font-weight:600}
.health .bd{padding-top:8px}
.hscore{display:flex;align-items:baseline;gap:5px}
.hscore b{font-size:42px;font-weight:650;letter-spacing:-1.6px;font-variant-numeric:tabular-nums;line-height:1.05}
.hscore span{font-size:13px;color:var(--ink4)}
.hbar{height:8px;border-radius:5px;background:#f0f4f9;overflow:hidden;margin:10px 0 8px}
.hbar i{display:block;height:100%;border-radius:5px}
.hhint{font-size:11.5px;color:var(--ink3);line-height:1.65;margin-bottom:12px}
.mods{display:flex;flex-direction:column;gap:8px}
.mod{display:grid;grid-template-columns:1fr auto;gap:4px 8px;align-items:center;font-size:11.5px;color:var(--ink3)}
.mod b{font-size:11.5px;color:var(--ink2);font-variant-numeric:tabular-nums}
.mbar{grid-column:span 2;height:5px;border-radius:3px;background:#f0f4f9;overflow:hidden}
.mbar i{display:block;height:100%;border-radius:3px}
.budget .brow{margin-bottom:14px}
.budget .brow:last-of-type{margin-bottom:4px}
.bt{display:flex;justify-content:space-between;align-items:baseline;font-size:12px;color:var(--ink2);margin-bottom:7px}
.bt b{font-weight:550}
.bt span{font-size:10.5px;color:var(--ink4);font-variant-numeric:tabular-nums}
.bbar{height:8px;border-radius:5px;background:#f0f4f9;overflow:hidden}
.bbar i{display:block;height:100%;border-radius:5px}
.bf{display:flex;justify-content:space-between;margin-top:5px;font-size:11px;color:var(--ink4);font-variant-numeric:tabular-nums}
.bf .over{font-style:normal;color:var(--red);font-weight:600}
.tx .trow{display:grid;grid-template-columns:96px minmax(0,1fr) 130px 128px;gap:14px;align-items:center;
  padding:8px 0;border-bottom:1px solid var(--line2);font-size:12.5px;color:var(--ink2)}
.tx .trow:last-of-type{border-bottom:none}
.tx .date{color:var(--ink4);font-variant-numeric:tabular-nums;font-size:12px}
.tx .note{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.tx .cat{color:var(--ink3);font-size:11.5px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.tx b{text-align:right;font-weight:600;font-variant-numeric:tabular-nums}
.tx .in{color:var(--green)}
.tx .out{color:var(--red)}
.empty{font-size:12px;color:var(--ink4);padding:14px 0}
.err{display:flex;align-items:center;gap:12px;padding:12px 16px;border-radius:12px;background:#fdeaee;color:#b42318;font-size:13px}
@media (max-width:1100px){
  .kpis{grid-template-columns:repeat(3,minmax(0,1fr))}
  .row-main,.row-three{grid-template-columns:minmax(0,1fr)}
  .span2{grid-column:auto}
}
@media (max-width:640px){
  .kpis{grid-template-columns:repeat(2,minmax(0,1fr))}
  .ph{flex-direction:column;gap:12px}
  .dash h1{font-size:26px}
  .acts{padding-top:0}
  .tx .trow{grid-template-columns:78px minmax(0,1fr) 104px;gap:8px}
  .tx .cat{display:none}
  .legend{display:none}
}
</style>

<style scoped>
.checks{display:flex;flex-direction:column}
.chk{display:flex;align-items:center;gap:9px;padding:9px 0;border-bottom:1px solid var(--line2);text-decoration:none;color:inherit}
.chk:last-child{border-bottom:none}
.chk-dot{width:22px;height:22px;border-radius:50%;display:grid;place-items:center;background:#fdf2e3;color:#b54708;flex:0 0 22px}
.chk.done .chk-dot{background:#e6f7ef;color:#0a6b46}
.chk.late .chk-dot{background:#fdeef0;color:#b42318}
.chk-tx{flex:1;min-width:0}
.chk-tx b{display:block;font-size:12px;font-weight:600;color:var(--ink);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.chk-tx small{display:block;font-size:11px;color:var(--ink4);margin-top:2px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.chk:hover .chk-tx b{color:var(--brand)}
.quick{display:grid;grid-template-columns:1fr 1fr;gap:8px}
.q{display:flex;align-items:center;gap:8px;height:42px;padding:0 11px;border-radius:9px;border:1px solid var(--line);
  background:#fafbfe;color:var(--ink2);text-decoration:none;font-size:12px;transition:all .15s}
.q:hover{border-color:#c8d2f8;background:#f5f8ff;color:var(--brand);transform:translateY(-1px)}
</style>
