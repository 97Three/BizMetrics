<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import VChart from 'vue-echarts'
import { ElMessage } from 'element-plus'
import client from '../api/client'
import { useAuthStore } from '../stores/auth'
import type { OverviewStats, TrendPoint, CategoryStat, AssetLiability, AdvancedInsight } from '../types/stats'
import type { Budget } from '../types/budget'
import type { TaxItem } from '../types/enterprise'
import type { WorkforceCostView } from '../types/workforce'
import { money, percent, date } from '../utils/format'
import MetricCard from '../components/ui/MetricCard.vue'
import TrendChart from '../components/ui/TrendChart.vue'
import InsightList from '../components/ui/InsightList.vue'
import DonutChart from '../components/ui/DonutChart.vue'
import UiIcon from '../components/ui/UiIcon.vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
use([CanvasRenderer,BarChart,LineChart,GridComponent,LegendComponent,TooltipComponent])

type Period='month'|'quarter'|'year'
const period=ref<Period>('month')
const periodLabel=computed(()=>period.value==='month'?'本月':period.value==='quarter'?'近 4 季度':'近 5 年')
const activeTab=ref('经营总览')
const allTabs=['经营总览','利润分析','现金流','预算执行','税务分析','人力成本','异常洞察']
const auth=useAuthStore()
const isHousehold=computed(()=>localStorage.getItem('activeSubjectType')==='household')
const hasTax=computed(()=>!isHousehold.value && !!auth.accessContext?.modules.enabled.includes('tax'))
const hasWorkforce=computed(()=>!isHousehold.value && !!auth.accessContext?.modules.enabled.includes('workforce-cost'))
const tabs=computed(()=>allTabs.filter(x=>(x!=='税务分析'||hasTax.value)&&(x!=='人力成本'||hasWorkforce.value)))
const overview=ref<OverviewStats|null>(null)
const trend=ref<TrendPoint[]|null>(null)
const incomeCategories=ref<CategoryStat[]|null>(null)
const expenseCategories=ref<CategoryStat[]|null>(null)
const assets=ref<AssetLiability|null>(null)
const insights=ref<AdvancedInsight|null>(null)
const budgets=ref<Budget[]|null>(null)
const taxItems=ref<TaxItem[]|null>(null)
const workforce=ref<WorkforceCostView|null>(null)
const failed=ref<string[]>([])
const loading=ref(false)
const companyId=()=>Number(localStorage.getItem('activeCompanyId'))||undefined
const range=()=>{const now=new Date();const start=new Date(now);if(period.value==='year')start.setMonth(0,1);else if(period.value==='quarter')start.setMonth(Math.floor(now.getMonth()/3)*3,1);else start.setDate(1);return {startDate:start.toISOString().slice(0,10),endDate:now.toISOString().slice(0,10)}}
const budgetUsage=computed(()=>{if(!budgets.value)return null;const total=budgets.value.reduce((n,x)=>n+x.amount,0);return total?budgets.value.reduce((n,x)=>n+x.spent,0)/total:0})
const periodFinancials=computed(()=>period.value==='month'?overview.value:trend.value?{monthlyIncome:trend.value.reduce((n,x)=>n+x.income,0),monthlyExpense:trend.value.reduce((n,x)=>n+x.expense,0),monthlyBalance:trend.value.reduce((n,x)=>n+x.balance,0)}:null)
const cashSupport=computed(()=>assets.value && overview.value && overview.value.monthlyExpense>0 ? assets.value.netWorth/overview.value.monthlyExpense : null)
const metrics=computed<Array<{label:string;value:string;foot:string;color:string;hero?:boolean}>>(()=>[
  {label:'经营收入',value:money(periodFinancials.value?.monthlyIncome),foot:periodLabel.value,color:'var(--green)'},
  {label:'经营成本',value:money(periodFinancials.value?.monthlyExpense),foot:periodLabel.value,color:'var(--red)'},
  {label:'经营利润',value:money(periodFinancials.value?.monthlyBalance),foot:periodFinancials.value?.monthlyIncome ? `利润率 ${percent(periodFinancials.value.monthlyBalance/periodFinancials.value.monthlyIncome)}`:'—',color:'var(--brand)',hero:true},
  {label:'预算使用率',value:percent(budgetUsage.value),foot:budgets.value?`${budgets.value.filter(x=>x.warningReached).length} 项预警`:'数据不可用',color:'var(--amber)'},
  ...(hasTax.value?[{label:'待处理税费',value:money(taxItems.value?.reduce((n,x)=>n+Math.max(0,x.taxAmount-x.paidAmount),0)),foot:taxItems.value?`${taxItems.value.length} 个税项`:'数据不可用',color:'var(--ink4)'}]:[]),
  {label:'现金支撑',value:cashSupport.value==null?'—':`${cashSupport.value.toFixed(1)} 月`,foot:assets.value?`净资产 ${money(assets.value.netWorth)}`:'数据不可用',color:'var(--ink4)'},
])
const metricCards=computed(()=>{
  const tones=['green','red','brand','amber','ink','ink'] as const
  const icons=['wallet','doc','trend','shield','tax','chart']
  return metrics.value.map((item,i:number)=>{
    const raw=String(item.value||'')
    const hasYuan=raw.startsWith('¥')
    return { label:item.label, value:hasYuan?raw.slice(1):raw, currency:hasYuan?'¥':undefined, foot:item.foot,
      hero:!!item.hero, tone:tones[i]||'ink', icon:icons[i]||'trend', trend:(trend.value||[]).map((p:TrendPoint)=> i===0?p.income:i===1?p.expense:p.balance) }
  })
})
const conclusionItems=computed(()=>conclusions.value.map((item,i:number)=>({
  title:item.title, detail:item.detail, icon:i===0?'check':'shield',
  tone:(/负|预警|偏紧|暂停/.test(item.title)?'amber':'green') as 'amber'|'green',
  path:'/reports'
})))
const incomeBars=computed(()=>(incomeCategories.value||[]).map((item,i)=>({name:item.categoryName,value:item.amount,percent:item.percentage,color:['#0e9f6e','#34c08b','#a7e3c8','#12b76a'][i%4]})))
const expenseBars=computed(()=>(expenseCategories.value||[]).map((item,i)=>({name:item.categoryName,value:item.amount,percent:item.percentage,color:['#3659e3','#6c8cff','#22b8a0','#f0a63a','#94a3b8'][i%5]})))
const chartOption=computed(()=>({color:['#12b76a','#f04438','#3659e3'],tooltip:{trigger:'axis'},grid:{left:65,right:24,top:18,bottom:34},legend:{show:false},xAxis:{type:'category',data:trend.value?.map(x=>x.month)||[],axisLine:{show:false},axisTick:{show:false}},yAxis:{type:'value',splitLine:{lineStyle:{color:'#e6ecf3',type:'dashed'}}},series:[{name:'收入',type:'bar',barMaxWidth:20,itemStyle:{borderRadius:[5,5,0,0]},data:trend.value?.map(x=>x.income)||[]},{name:'成本',type:'bar',barMaxWidth:20,itemStyle:{borderRadius:[5,5,0,0]},data:trend.value?.map(x=>x.expense)||[]},{name:'利润',type:'line',smooth:true,data:trend.value?.map(x=>x.balance)||[]}]}))
const conclusions=computed(()=>{
  const result:Array<{title:string;detail:string}>=[]
  if(failed.value.length)result.push({title:'部分结论已暂停',detail:`${failed.value.join('、')} 暂不可用；不会用 0 补齐缺失数据。`})
  if(periodFinancials.value)result.push({title:periodFinancials.value.monthlyBalance>=0?'经营利润为正':'经营利润为负',detail:`本期结余 ${money(periodFinancials.value.monthlyBalance)}`})
  if(budgets.value?.some(x=>x.warningReached))result.push({title:'预算存在预警',detail:`${budgets.value.filter(x=>x.warningReached).length} 项预算已达到预警阈值。`})
  if(cashSupport.value!=null && cashSupport.value<1)result.push({title:'现金支撑偏紧',detail:`按当前成本估算可支撑 ${cashSupport.value.toFixed(1)} 个月。`})
  return result
})
const expenseTotal=computed(()=>expenseCategories.value?.reduce((n,x)=>n+x.amount,0)||0)
const incomeTotal=computed(()=>incomeCategories.value?.reduce((n,x)=>n+x.amount,0)||0)
async function load(){
  loading.value=true;failed.value=[];overview.value=null;trend.value=null;incomeCategories.value=null;expenseCategories.value=null;assets.value=null;insights.value=null;budgets.value=null;taxItems.value=null;workforce.value=null
  const requests=[
    ['经营概览',()=>client.get<OverviewStats>('/stats/overview')],
    ['趋势',()=>client.get<TrendPoint[]>('/stats/trend',{params:{period:period.value,limit:period.value==='month'?6:period.value==='quarter'?4:5}})],
    ['收入结构',()=>client.get<CategoryStat[]>('/stats/category',{params:{type:'income',...range()}})],
    ['成本结构',()=>client.get<CategoryStat[]>('/stats/category',{params:{type:'expense',...range()}})],
    ['资产负债',()=>client.get<AssetLiability>('/stats/asset-liability')],
    ['异常洞察',()=>client.get<AdvancedInsight>('/stats/insights')],
    ['预算',()=>client.get<{content:Budget[]}>('/budgets',{params:{page:0,size:500}})],
    ...(hasTax.value?[['税务',()=>client.get<TaxItem[]>('/enterprise/tax-items',{params:{companyId:companyId()}})]] as Array<[string,()=>Promise<unknown>]>:[]),
    ...(hasWorkforce.value?[['人力成本',()=>client.get<WorkforceCostView>('/workforce-cost')]] as Array<[string,()=>Promise<unknown>]>:[]),
  ] as Array<[string,()=>Promise<unknown>]>
  const results=await Promise.allSettled(requests.map(([,fn])=>fn()))
  results.forEach((result,index)=>{const name=requests[index][0];if(result.status==='rejected'){failed.value.push(name);return}const data=(result.value as {data:unknown}).data
    if(name==='经营概览')overview.value=data as OverviewStats
    else if(name==='趋势')trend.value=data as TrendPoint[]
    else if(name==='收入结构')incomeCategories.value=data as CategoryStat[]
    else if(name==='成本结构')expenseCategories.value=data as CategoryStat[]
    else if(name==='资产负债')assets.value=data as AssetLiability
    else if(name==='异常洞察')insights.value=data as AdvancedInsight
    else if(name==='预算')budgets.value=(data as {content:Budget[]}).content
    else if(name==='税务')taxItems.value=data as TaxItem[]
    else if(name==='人力成本')workforce.value=data as WorkforceCostView
  })
  loading.value=false
}
function csvCell(value:string){return `"${(/^[=+\-@]/.test(value)?"'":'')+value.replaceAll('"','""')}"`}
function exportCsv(){if(failed.value.length){ElMessage.warning('报表数据不完整，完整导出已暂停');return}const rows=[['项目','数值'],...metrics.value.map(x=>[x.label,x.value]),['预算预警数',String(budgets.value?.filter(x=>x.warningReached).length||0)]];const blob=new Blob(['\uFEFF'+rows.map(x=>x.map(csvCell).join(',')).join('\r\n')],{type:'text/csv;charset=utf-8'});const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download=`bizmetrics-report-${new Date().toISOString().slice(0,10)}.csv`;a.click();URL.revokeObjectURL(url)}
function printReport(){window.print()}
watch(period,load);onMounted(load)
</script>
<template><div class="wrap reports-live"><div class="ph"><div class="l"><div class="tile"><UiIcon name="chart" :size="20" /></div><div><h1>{{ isHousehold?'家庭报表':'经营报表' }}</h1><p class="sub">当前主体 · 收入、成本、现金流与预算分析</p></div></div><div class="acts"><button class="btn" type="button" :disabled="loading||!!failed.length" @click="exportCsv">导出 CSV</button><button class="btn pri" type="button" @click="printReport">打印报表</button></div></div>
<div class="report-period"><button v-for="item in [{key:'month',label:'月度'},{key:'quarter',label:'季度'},{key:'year',label:'年度'}]" :key="item.key" type="button" :class="{active:period===item.key}" @click="period=item.key as Period">{{ item.label }}</button><button class="refresh" type="button" @click="load">刷新</button></div>
<div v-if="failed.length" class="notice-card" role="alert">{{ failed.join('、') }} 暂不可用，相关指标显示为“—”。<button class="detail-link" type="button" @click="load">重新加载</button></div>
<div class="kpis" v-loading="loading"><MetricCard v-for="item in metricCards" :key="item.label" v-bind="item" /></div>
<div class="tabs" role="tablist" aria-label="报表分析维度"><button v-for="tab in tabs" :key="tab" class="tab" :class="{on:activeTab===tab}" type="button" role="tab" :aria-selected="activeTab===tab" @click="activeTab=tab">{{ tab }}</button></div>
<div v-if="activeTab==='经营总览'" class="grid report-panels"><section class="card report-trend"><header><h3><span class="ic"><UiIcon name="trend" :size="14" /></span>本期经营趋势</h3><div class="lg"><span class="lgi"><s style="background:#12b76a" />收入</span><span class="lgi"><s style="background:#f04438" />成本</span><span class="lgi"><s style="background:linear-gradient(90deg,#3659e3,#6c8cff)" />利润</span></div></header><TrendChart v-if="trend" :points="trend" :height="300"/><div v-else class="ledger-state">趋势数据不可用</div></section><section class="card report-conclusions"><header><h3><span class="ic"><UiIcon name="spark" :size="14" /></span>经营结论</h3><span class="hint">{{ conclusions.length }} 条</span></header><div class="bd"><InsightList :items="conclusionItems" empty="暂无可验证结论" /></div></section>
<section class="card"><header><h3><span class="ic"><UiIcon name="doc" :size="14" /></span>成本结构</h3><span class="hint">{{ money(expenseCategories ? expenseTotal : null) }}</span></header><div class="bd"><DonutChart :items="expenseBars" :size="148" :thickness="15" :center-label="money(expenseTotal).replace('¥','')" center-sub="经营成本" /></div></section>
<section class="card"><header><h3><span class="ic"><UiIcon name="wallet" :size="14" /></span>收入结构</h3><span class="hint">{{ money(incomeCategories ? incomeTotal : null) }}</span></header><div class="bd"><DonutChart :items="incomeBars" :size="148" :thickness="15" :center-label="money(incomeTotal).replace('¥','')" center-sub="经营收入" /></div></section>
<section class="card"><header><h3><span class="ic"><UiIcon name="shield" :size="14" /></span>预算执行</h3></header><div class="bd bg"><div v-for="item in budgets?.slice(0,3)||[]" :key="item.id" class="row"><div class="t"><b>{{ item.name }}</b><span>{{ money(item.spent) }} / {{ money(item.amount) }} · {{ percent(item.usageRate) }}</span></div><div class="bar"><i :style="{width:`${Math.min(item.usageRate*100,100)}%`,background:item.usageRate>=1?'var(--amber)':'var(--brand)'}"></i></div></div><p v-if="!budgets?.length" class="empty-note">暂无预算</p></div></section></div>
<div v-else class="report-tab-content"><template v-if="activeTab==='利润分析'"><section class="card"><header><h3>利润趋势</h3></header><VChart v-if="trend" :option="chartOption" autoresize class="report-vchart"/></section><section class="card"><header><h3>利润口径</h3></header><div class="overview-list"><div><span>收入</span><b>{{ money(overview?.monthlyIncome) }}</b></div><div><span>成本</span><b>{{ money(overview?.monthlyExpense) }}</b></div><div><span>结余</span><b>{{ money(overview?.monthlyBalance) }}</b></div></div></section></template>
<template v-else-if="activeTab==='现金流'"><section class="card"><header><h3>资金概况</h3></header><div class="overview-list"><div><span>总资产</span><b>{{ money(assets?.totalAssets) }}</b></div><div><span>总负债</span><b>{{ money(assets?.totalLiabilities) }}</b></div><div><span>净资产</span><b>{{ money(assets?.netWorth) }}</b></div></div></section><section class="card"><header><h3>账户余额</h3></header><div class="overview-list"><div v-for="item in assets?.accounts||[]" :key="item.name"><span>{{ item.name }}</span><b>{{ money(item.balance) }}</b></div></div></section></template>
<template v-else-if="activeTab==='预算执行'"><section class="card"><header><h3>预算进度</h3></header><div class="bd bg"><div v-for="item in budgets||[]" :key="item.id" class="row"><div class="t"><b>{{ item.name }}</b><span>{{ money(item.spent) }} / {{ money(item.amount) }} · {{ percent(item.usageRate) }}</span></div><div class="bar"><i :style="{width:`${Math.min(item.usageRate*100,100)}%`,background:item.usageRate>=1?'var(--amber)':'var(--brand)'}"></i></div></div></div></section></template>
<template v-else-if="activeTab==='税务分析'"><section class="card"><header><h3>税项明细</h3></header><el-table :data="taxItems||[]"><el-table-column prop="name" label="税项"/><el-table-column prop="period" label="所属期"/><el-table-column label="应缴"><template #default="scope">{{ money(scope.row.taxAmount) }}</template></el-table-column><el-table-column label="已缴"><template #default="scope">{{ money(scope.row.paidAmount) }}</template></el-table-column><el-table-column prop="dueDate" label="截止日"/></el-table></section></template>
<template v-else-if="activeTab==='人力成本'"><section class="card"><header><h3>人力成本构成</h3></header><div class="overview-list"><div><span>薪资</span><b>{{ money(workforce?.costs.salary) }}</b></div><div><span>企业社保</span><b>{{ money(workforce?.costs.employerSocial) }}</b></div><div><span>企业公积金</span><b>{{ money(workforce?.costs.employerHousing) }}</b></div><div><span>总成本</span><b>{{ money(workforce?.costs.total) }}</b></div></div></section><section class="card"><header><h3>部门分布</h3></header><div class="overview-list"><div v-for="item in workforce?.departments||[]" :key="item.departmentId||item.departmentName"><span>{{ item.departmentName }}</span><b>{{ money(item.total) }}</b></div></div></section></template>
<template v-else><section class="card"><header><h3>异常洞察</h3></header><div class="overview-list"><RouterLink v-for="item in insights?.largeTransactions||[]" :key="item.id" to="/transactions"><span>大额流水 · {{ date(item.date) }} · {{ item.category }}</span><b>{{ money(item.amount) }}</b></RouterLink><RouterLink v-for="item in insights?.budgetAlerts||[]" :key="item.name" to="/budgets"><span>预算预警 · {{ item.name }}</span><b>{{ percent(item.usageRate) }}</b></RouterLink><div v-for="item in insights?.categorySpikes||[]" :key="item.category"><span>分类波动 · {{ item.category }}</span><b>{{ money(item.change) }}</b></div><p v-if="!insights?.largeTransactions.length&&!insights?.budgetAlerts.length&&!insights?.categorySpikes.length">暂无异常记录</p></div></section></template></div>
</div></template>




<style scoped>
.kpis{display:grid;grid-template-columns:repeat(auto-fit,minmax(178px,1fr));gap:12px;margin-bottom:16px}
.lg{display:flex;gap:8px;font-size:11px;color:var(--ink3)}
.lgi{display:inline-flex;align-items:center;gap:6px;height:26px;padding:0 10px;border-radius:8px;background:#f4f7fb}
.lgi s{width:8px;height:8px;border-radius:2px;text-decoration:none}
.card .bd{padding:0 20px 14px}
</style>
