<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import UiIcon from '../components/ui/UiIcon.vue'
import client from '../api/client'
import type { CategoryStat, TrendPoint } from '../types/stats'
import { money, percent } from '../utils/format'
import { ElMessage } from 'element-plus'

use([CanvasRenderer, BarChart, GridComponent, TooltipComponent])

type Period = 'month' | 'quarter' | 'year'
const period = ref<Period>('month')
const side = ref<'income' | 'expense'>('income')
const rows = ref<CategoryStat[]>([])
const trend = ref<TrendPoint[]>([])
const loading = ref(false)
const error = ref('')

const total = computed(() => rows.value.reduce((n, r) => n + r.amount, 0))
const sorted = computed(() => [...rows.value].sort((a, b) => b.amount - a.amount))

const chartOption = computed(() => ({
  grid: { left: 8, right: 16, top: 16, bottom: 8, containLabel: true },
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  xAxis: { type: 'value', splitLine: { lineStyle: { color: '#eef2f7', type: 'dashed' } }, axisLabel: { color: '#8798a8', fontSize: 11 } },
  yAxis: {
    type: 'category', inverse: true,
    data: sorted.value.slice(0, 8).map((r) => r.categoryName),
    axisLine: { show: false }, axisTick: { show: false },
    axisLabel: { color: '#425466', fontSize: 11 },
  },
  series: [{
    type: 'bar', barWidth: 14,
    itemStyle: {
      borderRadius: [0, 4, 4, 0],
      color: {
        type: 'linear', x: 0, y: 0, x2: 1, y2: 0,
        colorStops: side.value === 'income'
          ? [{ offset: 0, color: '#0e9f6e' }, { offset: 1, color: '#6ce2b0' }]
          : [{ offset: 0, color: '#3659e3' }, { offset: 1, color: '#6c8cff' }],
      },
    },
    data: sorted.value.slice(0, 8).map((r) => r.amount),
  }],
}))

function periodRange() {
  const now = new Date()
  const start = new Date(now)
  if (period.value === 'year') start.setMonth(0, 1)
  else if (period.value === 'quarter') start.setMonth(Math.floor(now.getMonth() / 3) * 3, 1)
  else start.setDate(1)
  return { startDate: start.toISOString().slice(0, 10), endDate: now.toISOString().slice(0, 10) }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [c, t] = await Promise.all([
      client.get<CategoryStat[]>('/stats/category', { params: { type: side.value, ...periodRange() } }),
      client.get<TrendPoint[]>('/stats/trend', { params: { period: period.value, limit: period.value === 'month' ? 6 : period.value === 'quarter' ? 4 : 5 } }),
    ])
    rows.value = c.data || []
    trend.value = t.data || []
  } catch {
    error.value = '报表数据加载失败，请检查后端服务与账号权限。'
  } finally {
    loading.value = false
  }
}

function exportCsv() {
  if (!rows.value.length) { ElMessage.warning('暂无数据可导出'); return }
  const head = ['分类', '金额', '占比', '笔数']
  const lines = [head.join(','), ...sorted.value.map((r) => [r.categoryName, r.amount, r.percentage?.toFixed(2), r.count].join(','))]
  const blob = new Blob(['\uFEFF' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `advanced-report-${side.value}-${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

onMounted(load)
</script>

<template>
  <div class="wrap adv">
    <div class="ph">
      <div class="l">
        <div class="tile"><UiIcon name="grid" :size="20" /></div>
        <div><h1>高级报表</h1><p class="sub">按维度拆解结构、对比趋势，并可导出明细</p></div>
      </div>
      <div class="acts">
        <button class="btn" type="button" @click="load"><UiIcon name="refresh" :size="14" />刷新</button>
        <button class="btn pri" type="button" @click="exportCsv">导出 CSV</button>
      </div>
    </div>

    <div class="toolbar">
      <div class="seg">
        <button v-for="p in [{ k: 'month', l: '月度' }, { k: 'quarter', l: '季度' }, { k: 'year', l: '年度' }]" :key="p.k"
          type="button" :class="{ on: period === p.k }" @click="period = p.k as Period; load()">{{ p.l }}</button>
      </div>
      <div class="seg">
        <button type="button" :class="{ on: side === 'income' }" @click="side = 'income'; load()">收入口径</button>
        <button type="button" :class="{ on: side === 'expense' }" @click="side = 'expense'; load()">成本口径</button>
      </div>
      <span class="hint">按分类维度拆解 · 共 {{ rows.length }} 个分类</span>
    </div>

    <div v-if="error" class="err" role="alert">{{ error }}<button class="btn" @click="load">重试</button></div>

    <div class="cols">
      <section class="card">
        <header><h3><span class="ic"><UiIcon name="trend" :size="14" /></span>{{ side === 'income' ? '收入' : '成本' }}分类排行</h3>
          <span class="hint">Top 8</span></header>
        <div class="bd">
          <VChart v-if="sorted.length" :option="chartOption" autoresize style="height: 280px" />
          <p v-else class="empty">本期暂无{{ side === 'income' ? '收入' : '成本' }}数据</p>
        </div>
      </section>

      <section class="card">
        <header><h3><span class="ic"><UiIcon name="list" :size="14" /></span>分类明细</h3>
          <span class="hint">合计 {{ money(total) }}</span></header>
        <div class="bd">
          <table class="tbl">
            <thead><tr><th>分类</th><th class="r">金额</th><th class="r">占比</th><th class="r">笔数</th></tr></thead>
            <tbody>
              <tr v-for="r in sorted" :key="r.categoryId">
                <td><span class="dot" :style="{ background: r.categoryColor || '#3659e3' }" />{{ r.categoryName }}</td>
                <td class="r num">{{ money(r.amount) }}</td>
                <td class="r">{{ percent((r.percentage || 0) / 100) }}</td>
                <td class="r">{{ r.count }}</td>
              </tr>
              <tr v-if="!sorted.length"><td colspan="4" class="empty">暂无数据</td></tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>

    <section class="card">
      <header><h3><span class="ic"><UiIcon name="chart" :size="14" /></span>期间趋势（{{ side === 'income' ? '收入' : '成本' }}）</h3></header>
      <div class="bd trend">
        <div v-for="p in trend" :key="p.month" class="tpoint">
          <span>{{ p.month }}</span>
          <b :style="{ color: side === 'income' ? 'var(--green)' : 'var(--red)' }">{{ money(side === 'income' ? p.income : p.expense) }}</b>
          <small>结余 {{ money(p.balance) }}</small>
        </div>
        <p v-if="!trend.length" class="empty">暂无趋势数据</p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.adv{display:flex;flex-direction:column;gap:14px}
.toolbar{display:flex;align-items:center;gap:12px;flex-wrap:wrap;padding:10px 14px;background:var(--card);
  border:1px solid var(--line);border-radius:14px}
.seg{display:flex;gap:4px;background:#f2f5f9;border-radius:9px;padding:3px}
.seg button{height:30px;padding:0 13px;border:none;border-radius:7px;background:transparent;color:var(--ink3);font-size:12.5px;cursor:pointer;font-family:inherit}
.seg button.on{background:#fff;color:var(--brand);font-weight:600;box-shadow:0 1px 3px rgba(16,24,40,.12)}
.hint{margin-left:auto;font-size:11.5px;color:var(--ink4)}
.cols{display:grid;grid-template-columns:1fr 1fr;gap:14px}
.card .bd{padding:12px 20px 16px}
.tbl{width:100%;border-collapse:collapse;font-size:12.5px}
.tbl th{text-align:left;font-size:11.5px;color:var(--ink4);font-weight:600;padding:8px 0;border-bottom:1px solid var(--line)}
.tbl td{padding:10px 0;border-bottom:1px solid var(--line2);color:var(--ink2)}
.tbl tr:last-child td{border-bottom:none}
.tbl .r{text-align:right}
.tbl .num{font-variant-numeric:tabular-nums;color:var(--ink)}
.dot{display:inline-block;width:7px;height:7px;border-radius:2px;margin-right:8px}
.trend{display:grid;grid-template-columns:repeat(auto-fit,minmax(120px,1fr));gap:10px}
.tpoint{display:flex;flex-direction:column;gap:4px;padding:10px 12px;border-radius:10px;background:#f8fafc;font-size:11.5px;color:var(--ink4)}
.tpoint b{font-size:14px;font-variant-numeric:tabular-nums}
.tpoint small{color:var(--ink4)}
.empty{font-size:12px;color:var(--ink4);padding:16px 0;text-align:center}
.err{display:flex;align-items:center;gap:12px;padding:12px 16px;border-radius:12px;background:#fdeaee;color:#b42318;font-size:13px}
@media (max-width:1000px){.cols{grid-template-columns:1fr}}
</style>
