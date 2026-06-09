<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import LedgerEditor from '../components/LedgerEditor.vue'
import TransactionFormModal from '../components/TransactionFormModal.vue'
import TransactionImportDialog from '../components/TransactionImportDialog.vue'
import MetricCard from '../components/ui/MetricCard.vue'
import CompositionBar from '../components/ui/CompositionBar.vue'
import UiIcon from '../components/ui/UiIcon.vue'
import client from '../api/client'
import { useRoute, useRouter } from 'vue-router'
import { ledgerApi, type RecurringItem, type TransactionSummary } from '../api/ledgers'
import type { Transaction } from '../types/transaction'
import type { Budget } from '../types/budget'
import type { ReceiptVoucher, ReceiptSummary } from '../types/receipt'
import type { Account, AccountSummary } from '../types/account'

type Kind = 'transactions' | 'budgets' | 'receipts' | 'accounts' | 'recurring'
type Row = Transaction | Budget | ReceiptVoucher | Account | RecurringItem
type Summary = TransactionSummary | ReceiptSummary | AccountSummary | null
type Column = { key: string; label: string; format?: 'money' | 'date' | 'type' | 'status' | 'percent' }
const props = defineProps<{ kind: Kind }>()
const route=useRoute()
const router=useRouter()
const definitions: Record<Kind, { title: string; subtitle: string; columns: Column[]; statuses: Array<{ label: string; value: string }> }> = {
  transactions: { title: '经营流水', subtitle: '查看收入、成本、退款与关联账户', columns: [{key:'date',label:'日期',format:'date'},{key:'note',label:'摘要'},{key:'categoryName',label:'分类'},{key:'accountName',label:'账户'},{key:'amount',label:'金额',format:'money'},{key:'type',label:'类型',format:'type'}], statuses:[{label:'全部',value:''},{label:'收入',value:'1'},{label:'支出',value:'2'},{label:'退款',value:'3'}] },
  budgets: { title: '经营预算', subtitle: '追踪预算使用与超支风险', columns: [{key:'name',label:'预算名'},{key:'startDate',label:'开始日期',format:'date'},{key:'endDate',label:'结束日期',format:'date'},{key:'amount',label:'预算额',format:'money'},{key:'spent',label:'已用',format:'money'},{key:'remainingAmount',label:'剩余',format:'money'},{key:'usageRate',label:'进度',format:'percent'},{key:'status',label:'状态',format:'status'}], statuses:[{label:'全部',value:''},{label:'进行中',value:'1'},{label:'已完成',value:'2'},{label:'超支',value:'3'}] },
  receipts: { title: '票据凭证', subtitle: '管理凭证、流水关联与财税状态', columns: [{key:'voucherNo',label:'票据号'},{key:'title',label:'摘要'},{key:'counterparty',label:'开票方'},{key:'issueDate',label:'开票日期',format:'date'},{key:'amount',label:'金额',format:'money'},{key:'taxAmount',label:'税额',format:'money'},{key:'status',label:'状态',format:'status'}], statuses:[{label:'全部',value:''},{label:'待复核',value:'pending_review'},{label:'已核验',value:'verified'},{label:'已关联',value:'linked'},{label:'已归档',value:'archived'},{label:'已驳回',value:'rejected'}] },
  accounts: { title: '经营账户', subtitle: '查看资金余额与账户对账情况', columns: [{key:'name',label:'账户名'},{key:'type',label:'类型',format:'type'},{key:'balance',label:'余额',format:'money'},{key:'availableBalance',label:'可用余额',format:'money'},{key:'currency',label:'币种'},{key:'includeInNetWorth',label:'计入净资产',format:'status'},{key:'lastReconciledAt',label:'最近对账',format:'date'}], statuses:[{label:'全部',value:''},{label:'现金',value:'cash'},{label:'银行',value:'bank'},{label:'信用',value:'credit'},{label:'数字账户',value:'digital'},{label:'投资',value:'investment'},{label:'债务',value:'debt'}] },
  recurring: { title: '周期事项', subtitle: '管理定期收入与支出计划', columns: [{key:'name',label:'名称'},{key:'frequency',label:'频率',format:'type'},{key:'type',label:'收支类型',format:'type'},{key:'amount',label:'金额',format:'money'},{key:'nextExecution',label:'下次执行',format:'date'},{key:'executionCount',label:'执行次数'},{key:'status',label:'状态',format:'status'}], statuses:[{label:'全部',value:''},{label:'已启用',value:'1'},{label:'已停用',value:'0'}] },
}

const config = computed(() => definitions[props.kind])
const keyword = ref(String(route.query.keyword||''))
const status = ref('')
const view = ref<'all'|'income'|'expense'|'large'>('all')
const categoryId = ref<number>(0)
const accountId = ref<number>(0)
const minAmount = ref<string>('')
const maxAmount = ref<string>('')
const dateStart = ref('')
const dateEnd = ref('')
const categories = ref<Array<{ id: number; name: string; type: string }>>([])
const accounts = ref<Array<{ id: number; name: string }>>([])
const largeThreshold = 10000
const budgetView = ref<'all'|'active'|'warning'|'overrun'|'thisMonth'>('all')
const recurringView = ref<'all'|'today'|'week'|'overdue'|'active'|'income'|'expense'>('all')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const rows = ref<Row[]>([])
const selection = ref<Row[]>([])
const summary = ref<Summary>(null)
const selected = ref<Row | null>(null)
const editorVisible = ref(false)
const importVisible = ref(false)
const batchUploading = ref(false)
const editRecord = ref<Record<string,unknown> | null>(null)
const drawerOpen = computed({ get: () => selected.value !== null, set: value => { if (!value) selected.value = null } })
const auditLogs = ref<Array<{ action: string; summary: string; actorName: string; createdAt: string }>>([])
const loading = ref(false)
const error = ref('')
let requestId = 0

const filtered = computed(() => {
  if (props.kind !== 'accounts' && props.kind !== 'recurring') return rows.value
  const needle = keyword.value.trim().toLocaleLowerCase()
  if (props.kind === 'recurring') {
    const now = Date.now()
    const weekEnd = now + 7 * 86400000
    return (rows.value as RecurringItem[]).filter((row) => {
      const name = String(row.name || '').toLocaleLowerCase()
      if (needle && !name.includes(needle)) return false
      const due = Date.parse(row.nextExecution)
      if (recurringView.value === 'today') return Number.isFinite(due) && new Date(due).toDateString() === new Date().toDateString() && row.status === 1
      if (recurringView.value === 'week') return Number.isFinite(due) && due >= now && due <= weekEnd && row.status === 1
      if (recurringView.value === 'overdue') return Number.isFinite(due) && due < now && row.status === 1
      if (recurringView.value === 'active') return row.status === 1
      if (recurringView.value === 'income') return row.type === 1
      if (recurringView.value === 'expense') return row.type === 2
      return true
    })
  }
  return rows.value.filter(row => {
    const item = row as unknown as Record<string, unknown>
    const matchesText = !needle || [item.name,item.type,item.bank,item.note].some(v => String(v ?? '').toLocaleLowerCase().includes(needle))
    const matchesStatus = !status.value || String(props.kind === 'accounts' ? item.type : item.status) === status.value
    return matchesText && matchesStatus
  })
})
const recurringSummary = computed(()=>{
  if(props.kind!=='recurring')return null
  const list=rows.value as RecurringItem[]
  const now=Date.now()
  const weekEnd=now+7*86400000
  return {
    today:list.filter(r=>{const d=Date.parse(r.nextExecution);return r.status===1&&Number.isFinite(d)&&new Date(d).toDateString()===new Date().toDateString()}).length,
    week:list.filter(r=>{const d=Date.parse(r.nextExecution);return r.status===1&&Number.isFinite(d)&&d>=now&&d<=weekEnd}).length,
    overdue:list.filter(r=>{const d=Date.parse(r.nextExecution);return r.status===1&&Number.isFinite(d)&&d<now}).length,
    active:list.filter(r=>r.status===1).length,
  }
})
function receiptChips(row: Row) {
  const r = row as ReceiptVoucher
  const chips: Array<{ text: string; tone: string }> = []
  if (r.invoiceCheckStatus === 'verified') chips.push({ text: '发票已验', tone: 'green' })
  else if (r.invoiceCheckStatus === 'failed') chips.push({ text: '核验失败', tone: 'red' })
  else if (r.invoiceCheckStatus === 'pending') chips.push({ text: '发票待验', tone: 'amber' })
  if (r.deductionStatus === 'deductible' || r.deductionStatus === 'deducted') chips.push({ text: '可抵扣', tone: 'green' })
  else if (r.deductionStatus === 'pending') chips.push({ text: '抵扣待定', tone: 'amber' })
  chips.push(r.transactionId ? { text: '已关联流水', tone: 'green' } : { text: '未关联流水', tone: 'amber' })
  if (r.approvalStatus === 'approved') chips.push({ text: '审批通过', tone: 'green' })
  else if (r.approvalStatus === 'rejected') chips.push({ text: '审批驳回', tone: 'red' })
  else if (r.approvalStatus === 'pending') chips.push({ text: '待审批', tone: 'amber' })
  else if (r.approvalStatus === 'not_submitted') chips.push({ text: '未提交审批', tone: 'ink' })
  if (['submitted', 'approved', 'paid'].includes(String(r.reimbursementStatus))) chips.push({ text: '报销进行中', tone: 'violet' })
  return chips
}
function receiptChain(row: ReceiptVoucher) {
  const approvalLabel: Record<string, [string, string]> = {
    approved: ['已通过', 'green'], pending: ['待审批', 'amber'], rejected: ['已驳回', 'red'],
    not_submitted: ['未提交', 'ink'], not_required: ['无需审批', 'ink'],
  }
  const a = approvalLabel[String(row.approvalStatus)] || ['—', 'ink']
  return [
    { label: '票据', value: row.voucherNo || '已归档', tone: 'green' },
    { label: '发票核验', value: row.invoiceCheckStatus === 'verified' ? '已核验' : row.invoiceCheckStatus === 'failed' ? '核验失败' : row.invoiceCheckStatus === 'pending' ? '待核验' : '无需核验', tone: row.invoiceCheckStatus === 'verified' ? 'green' : row.invoiceCheckStatus === 'failed' ? 'red' : 'amber' },
    { label: '流水关联', value: row.transactionId ? '已关联' : '未关联', tone: row.transactionId ? 'green' : 'amber' },
    { label: '审批', value: a[0], tone: a[1] },
    { label: '报销', value: (() => {
        const map: Record<string, string> = { not_applicable: '无需报销', submitted: '已提交', approved: '已通过', paid: '已支付', archived: '已归档', rejected: '已驳回' }
        return map[String(row.reimbursementStatus)] || '—'
      })(), tone: ['approved', 'paid', 'archived'].includes(String(row.reimbursementStatus)) ? 'green' : String(row.reimbursementStatus) === 'rejected' ? 'red' : 'amber' },
  ]
}
const accountsRows = computed(() => (rows.value as Account[]))
const accountTypeBars = computed(() => {
  if (props.kind !== 'accounts') return []
  const labels: Record<string, string> = { cash: '现金', bank: '银行', credit: '信用', digital: '数字钱包', investment: '理财', debt: '负债' }
  const colors = ['#3659e3', '#6c8cff', '#22b8a0', '#f0a63a', '#94a3b8', '#e2605c']
  const map = new Map<string, number>()
  for (const row of accountsRows.value) map.set(row.type, (map.get(row.type) || 0) + Math.abs(Number(row.balance || 0)))
  const total = Array.from(map.values()).reduce((n, v) => n + v, 0) || 1
  return Array.from(map.entries()).map(([type, value], i) => ({
    name: labels[type] || type, value, percent: (value / total) * 100, color: colors[i % colors.length],
  }))
})
const accountRiskBars = computed(() => {
  if (props.kind !== 'accounts') return []
  const list = accountsRows.value
  const count = (fn: (a: Account) => boolean) => list.filter(fn).length
  const items = [
    { name: '已对账', value: count((a) => a.reconciliationStatus === 'reconciled'), color: '#12b76a' },
    { name: '待对账', value: count((a) => a.reconciliationStatus === 'pending'), color: '#f79009' },
    { name: '有异常', value: count((a) => a.reconciliationStatus === 'exception'), color: '#f04438' },
  ]
  const total = items.reduce((n, i) => n + i.value, 0) || 1
  return items.map((i) => ({ ...i, percent: (i.value / total) * 100 }))
})
function statusChips(row: Row) {
  const item = row as unknown as Record<string, unknown>
  const chips: Array<{ text: string; tone: string }> = []
  if (props.kind === 'budgets') {
    const status = Number(item.status)
    chips.push(status === 3 ? { text: '超支', tone: 'red' } : status === 2 ? { text: '已完成', tone: 'ink' } : status === 1 ? { text: '进行中', tone: 'green' } : { text: '已停用', tone: 'ink' })
    if (['high', 'critical'].includes(String(item.riskLevel))) chips.push({ text: '高风险', tone: 'red' })
    else if (item.warningReached) chips.push({ text: '已预警', tone: 'amber' })
    return chips.slice(0, 2)
  }
  if (props.kind === 'receipts') {
    const map: Record<string, [string, string]> = {
      pending_review: ['待复核', 'amber'], verified: ['已核验', 'green'], linked: ['已关联', 'brand'],
      archived: ['已归档', 'ink'], rejected: ['已驳回', 'red'],
    }
    const [text, tone] = map[String(item.status)] || [String(item.status || '—'), 'ink']
    chips.push({ text, tone })
    if (item.riskLevel === 'high' || item.riskLevel === 'critical') chips.push({ text: '高风险', tone: 'red' })
    return chips
  }
  if (props.kind === 'recurring') {
    chips.push(Number(item.status) === 1 ? { text: '已启用', tone: 'green' } : { text: '已停用', tone: 'ink' })
    const due = Date.parse(String(item.nextExecution || ''))
    if (Number(item.status) === 1 && Number.isFinite(due) && due < Date.now()) chips.push({ text: '已逾期', tone: 'red' })
    return chips
  }
  return chips
}
function accountStatusChips(row: Row) {
  const a = row as Account
  const chips: Array<{ text: string; tone: string }> = []
  chips.push(a.status === 1 ? { text: '启用', tone: 'green' } : a.status === 2 ? { text: '冻结', tone: 'amber' } : { text: '停用', tone: 'ink' })
  if (a.reconciliationStatus === 'reconciled') chips.push({ text: '已对账', tone: 'green' })
  else if (a.reconciliationStatus === 'exception') chips.push({ text: '对账异常', tone: 'red' })
  else chips.push({ text: '待对账', tone: 'amber' })
  if (a.riskLevel === 'high' || a.riskLevel === 'critical') chips.push({ text: '高风险', tone: 'red' })
  return chips
}
const budgetRows = computed(()=>{
  const list=rows.value as Budget[]
  const now=new Date()
  const monthEnd=new Date(now.getFullYear(),now.getMonth()+1,0,23,59,59)
  return list.filter(b=>{
    if(budgetView.value==='active')return b.status===1
    if(budgetView.value==='overrun')return b.status===3
    if(budgetView.value==='warning')return b.warningReached||b.riskLevel==='high'||b.riskLevel==='critical'
    if(budgetView.value==='thisMonth'){const end=Date.parse(b.endDate);return Number.isFinite(end)&&end>=now.getTime()&&end<=monthEnd.getTime()}
    return true
  })
})
function deadlineText(b:Budget){
  const end=Date.parse(b.endDate)
  if(!Number.isFinite(end))return '—'
  const days=Math.ceil((end-Date.now())/86400000)
  if(days<0)return '已逾期 '+Math.abs(days)+' 天'
  if(days===0)return '今日截止'
  return days+' 天后截止'
}
function deadlineTone(b:Budget){
  const end=Date.parse(b.endDate)
  const days=Math.ceil((end-Date.now())/86400000)
  if(days<0)return 'red'
  if(days<=7)return 'amber'
  return ''
}
const budgetSummary = computed(()=>{
  if(props.kind!=='budgets')return null
  const rows=budgetRows.value
  const warning=rows.filter(b=>b.warningReached||b.riskLevel==='high'||b.riskLevel==='critical').length
  const nearest=rows.slice().sort((a,b)=>Date.parse(a.endDate)-Date.parse(b.endDate)).find(b=>Date.parse(b.endDate)>=Date.now())
  return {warning,nearest:nearest?nearest.name+' · '+deadlineText(nearest):'—'}
})
const visibleRows = computed(() => { if (props.kind === 'budgets') return budgetRows.value.slice((page.value-1)*pageSize.value,page.value*pageSize.value); return props.kind === 'accounts' || props.kind === 'recurring' ? filtered.value.slice((page.value-1)*pageSize.value,page.value*pageSize.value) : filtered.value })
const displayTotal = computed(() => props.kind === 'budgets' ? budgetRows.value.length : props.kind === 'accounts' || props.kind === 'recurring' ? filtered.value.length : total.value)
const metrics = computed(() => {
  if (props.kind === 'transactions') { const s=summary.value as TransactionSummary | null; return [['筛选收入',money(s?.income)],['筛选成本',money(s?.expense)],['净额',money(s?.net)],['笔数',s?.rows ?? '—'],['大额',s?.largeCount ?? '—'],['待复核',s?.reviewCount ?? '—']] }
  if (props.kind === 'receipts') { const s=summary.value as ReceiptSummary | null; return [['票据金额',money(s?.totalAmount)],['待处理金额',money(s?.pendingAmount)],['待复核',s?.pendingReviewCount ?? '—'],['缺流水关联',s?.missingTransactionCount ?? '—']] }
  if (props.kind === 'accounts') { const s=summary.value as AccountSummary | null; return [['总资产',money(s?.totalAssets)],['总负债',money(s?.totalLiabilities)],['净资产',money(s?.netWorth)],['可用资金',money(s?.availableBalance)],['本月净流',money((s?.currentMonthIncome ?? 0) - (s?.currentMonthExpense ?? 0))],['待对账',String(s?.pendingReconciliationCount ?? '—') + ' 个']] }
  if (props.kind === 'budgets') { const b=rows.value as Budget[]; return [['预算总额',money(b.reduce((n,r)=>n+r.amount,0))],['已用合计',money(b.reduce((n,r)=>n+r.spent,0))],['剩余合计',money(b.reduce((n,r)=>n+r.remainingAmount,0))],['超支项',b.filter(r=>r.status===3).length]] }
  const r=rows.value as RecurringItem[]; return [['已启用',r.filter(x=>x.status===1).length],['本周待执行',r.filter(x=>x.nextExecution && Date.parse(x.nextExecution)<=Date.now()+7*86400000 && x.status===1).length],['总计划数',r.length]]
})

function money(value: number | null | undefined) { return value == null ? '—' : `${value < 0 ? '−' : ''}¥${new Intl.NumberFormat('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2}).format(Math.abs(value))}` }
function formatted(row: Row, column: Column) {
  const value = (row as unknown as Record<string, unknown>)[column.key]
  if (value == null || value === '') return '—'
  if (column.format === 'money') return money(Number(value))
  if (column.format === 'date') return String(value).slice(0,10)
  if (column.format === 'percent') return `${(Number(value)*100).toFixed(1)}%`
  if (column.format === 'type') {
    if (props.kind==='transactions' || props.kind==='recurring' && column.key==='type') return ({1:'收入',2:'支出',3:'退款'} as Record<string,string>)[String(value)] || String(value)
    if (props.kind==='recurring') return ({daily:'每日',weekly:'每周',monthly:'每月',yearly:'每年'} as Record<string,string>)[String(value)] || String(value)
    return ({cash:'现金',bank:'银行',credit:'信用',digital:'数字账户',investment:'投资',debt:'债务'} as Record<string,string>)[String(value)] || String(value)
  }
  if (column.format==='status') {
    if (typeof value==='boolean') return value ? '是' : '否'
    const labels:Record<string,string>={0:'已停用',1:'进行中',2:'已完成',3:'超支',pending_review:'待复核',verified:'已核验',linked:'已关联',archived:'已归档',rejected:'已驳回'}
    return props.kind==='recurring' && value===1 ? '已启用' : labels[String(value)] || String(value)
  }
  return String(value)
}

async function load() {
  const current = ++requestId
  loading.value = true; error.value = ''; selection.value = []
  try {
    if (props.kind==='transactions') {
      const query={page:page.value-1,size:pageSize.value,keyword:keyword.value||undefined,
        type:(view.value==='income'?1:view.value==='expense'?2:status.value?Number(status.value) as 1|2|3:undefined) as 1|2|3|undefined,
        categoryId:categoryId.value||undefined,accountId:accountId.value||undefined,
        startDate:dateStart.value||undefined,endDate:dateEnd.value||undefined,
        minAmount:view.value==='large'?Math.max(Number(minAmount.value)||0,largeThreshold):(minAmount.value?Number(minAmount.value):undefined),
        maxAmount:maxAmount.value?Number(maxAmount.value):undefined}
      const summaryQuery = { keyword: query.keyword, type: query.type }
      const [list,sum]=await Promise.all([ledgerApi.transactions.list(query),ledgerApi.transactions.summary(summaryQuery)])
      if (current!==requestId) return
      rows.value=list.data.content; total.value=list.data.totalElements; summary.value=sum.data
    } else if (props.kind==='budgets') {
      const query={page:0,size:200,keyword:keyword.value||undefined,status:undefined as 0|1|2|3|undefined}
      const list=await ledgerApi.budgets.list(query)
      if (current!==requestId) return
      rows.value=list.data.content; total.value=list.data.totalElements; summary.value=null
    } else if (props.kind==='receipts') {
      const query={page:page.value-1,size:pageSize.value,keyword:keyword.value||undefined,status:status.value ? status.value as 'pending_review'|'verified'|'linked'|'archived'|'rejected' : undefined}
      const [list,sum]=await Promise.all([ledgerApi.receipts.list(query),ledgerApi.receipts.summary()])
      if (current!==requestId) return
      rows.value=list.data.content; total.value=list.data.totalElements; summary.value=sum.data
    } else if (props.kind==='accounts') {
      const [list,sum]=await Promise.all([ledgerApi.accounts.list(),ledgerApi.accounts.summary()])
      if (current!==requestId) return
      rows.value=list.data; summary.value=sum.data
    } else {
      const list=await ledgerApi.recurring.list()
      if (current!==requestId) return
      rows.value=list.data; summary.value=null
    }
  } catch { if(current===requestId){ rows.value=[]; summary.value=null; error.value='数据加载失败。请检查后端服务、登录状态和网络连接。' } }
  finally { if(current===requestId) loading.value=false }
}

const metricCards = computed(() => {
  const icons = ['wallet', 'doc', 'trend', 'list', 'alert', 'shield']
  const tones = ['green', 'red', 'brand', 'ink', 'amber', 'ink']
  return metrics.value.map((item, i) => {
    const raw = String(item[1] ?? '—')
    return {
      label: String(item[0]),
      value: raw.startsWith('¥') ? raw.slice(1) : raw,
      currency: raw.startsWith('¥') ? '¥' : undefined,
      tone: tones[i % tones.length] as 'green' | 'red' | 'brand' | 'ink' | 'amber',
      icon: icons[i % icons.length],
    }
  })
})
const pageIcon = computed(() => ({ transactions: 'list', budgets: 'shield', receipts: 'doc', accounts: 'wallet', recurring: 'calendar' } as Record<string, string>)[props.kind] || 'list')
function search(){ page.value=1; void load() }
function resetFilters(){ view.value='all';status.value='';categoryId.value=0;accountId.value=0;minAmount.value='';maxAmount.value='';dateStart.value='';dateEnd.value='';keyword.value='';search() }
async function loadFilterOptions(){
  if(props.kind!=='transactions')return
  try{
    const [c,a]=await Promise.all([client.get('/categories'),client.get('/accounts')])
    categories.value=(c.data||[]).map((x:Record<string,unknown>)=>({id:Number(x.id),name:String(x.name),type:String(x.type)}))
    accounts.value=(a.data||[]).map((x:Record<string,unknown>)=>({id:Number(x.id),name:String(x.name)}))
  }catch{/* filters stay empty when options fail */}
}
function noteTags(row:Row){
  const tx=row as Transaction
  const note=tx.note||''
  const tags:Array<{text:string;tone:string}>=[]
  if(Number(tx.amount)>=largeThreshold)tags.push({text:'大额',tone:'red'})
  if(Number(tx.refundedAmount)>0)tags.push({text:'已退款',tone:'amber'})
  if(!note.trim())tags.push({text:'缺备注',tone:'amber'})
  if(/(待回款|应收|尾款|分期)/.test(note))tags.push({text:'待回款',tone:'brand'})
  if(/(退款|退货)/.test(note))tags.push({text:'退款',tone:'violet'})
  if(/(离职补偿|N\+1)/.test(note))tags.push({text:'离职补偿',tone:'ink'})
  return tags
}
const noteSummary = computed(()=>{
  if(props.kind!=='transactions')return []
  const rows=visibleRows.value as Transaction[]
  const hit=(re:RegExp)=>rows.filter(r=>re.test(r.note||'')).length
  return [
    {label:'待回款',count:hit(/(待回款|应收|尾款|分期)/)},
    {label:'退款',count:hit(/(退款|退货)/)},
    {label:'离职补偿',count:hit(/(离职补偿|N\+1)/)},
    {label:'缺备注',count:rows.filter(r=>!(r.note||'').trim()).length},
    {label:'大额',count:rows.filter(r=>Number(r.amount)>=largeThreshold).length},
  ]
})
function changePage(value:number){page.value=value;if(props.kind!=='accounts'&&props.kind!=='recurring'&&props.kind!=='budgets') void load()}
function changeSize(value:number){pageSize.value=value;page.value=1;if(props.kind!=='accounts'&&props.kind!=='recurring'&&props.kind!=='budgets') void load()}
function choose(rows:Row[]){selection.value=rows}
async function open(row:Row){
  selected.value=row; auditLogs.value=[]
  if(props.kind==='receipts') try { const response=await ledgerApi.receipts.auditLogs(Number(row.id)); if(selected.value?.id===row.id) auditLogs.value=response.data } catch { ElMessage.warning('变更记录暂时无法加载') }
}
function exportSelected(){
  if(!selection.value.length) return
  const columns=config.value.columns
  const safe=(v:string)=>`"${(/^[=+\-@]/.test(v)?"'":'')+v.replaceAll('"','""')}"`
  const lines=[columns.map(c=>safe(c.label)).join(','),...selection.value.map(row=>columns.map(c=>safe(formatted(row,c))).join(','))]
  const blob=new Blob(['\uFEFF'+lines.join('\r\n')],{type:'text/csv;charset=utf-8'})
  const url=URL.createObjectURL(blob); const link=document.createElement('a'); link.href=url; link.download=`bizmetrics-${props.kind}.csv`; link.click(); URL.revokeObjectURL(url)
}
function create(){editRecord.value=null;editorVisible.value=true}
function openEdit(row:Row){selected.value=row;edit()}
function openDelete(row:Row){selected.value=row;void remove()}
function edit(){if(!selected.value)return;editRecord.value=selected.value as unknown as Record<string,unknown>;selected.value=null;editorVisible.value=true}
async function saved(){editorVisible.value=false;await load()}
async function batchReceipt(nextStatus: 'verified' | 'archived'){
  if(!selection.value.length)return
  const label = nextStatus === 'verified' ? '核验' : '归档'
  try{await ElMessageBox.confirm(`将${label}选中的 ${selection.value.length} 张票据？`,'批量' + label,{type:'warning'})}catch{return}
  let ok=0;let fail=0
  for(const row of selection.value){
    const voucher=row as ReceiptVoucher
    try{
      await client.put(`/receipts/${voucher.id}`,{
        version:voucher.version,
        status:nextStatus,
        invoiceCheckStatus:voucher.invoiceCheckStatus,
        deductionStatus:voucher.deductionStatus,
      })
      ok++
    }catch{fail++}
  }
  selection.value=[]
  await load()
  if(fail)ElMessage.warning(`已${label} ${ok} 张，${fail} 张失败（可能需要先关联流水）`)
  else ElMessage.success(`已${label} ${ok} 张票据`)
}
async function batchRemove(){
  if(!selection.value.length)return
  try{await ElMessageBox.confirm(`将删除选中的 ${selection.value.length} 条流水，删除后无法恢复。确认继续？`,'批量删除',{type:'warning'})}catch{return}
  let ok=0;let fail=0
  for(const row of selection.value){
    const tx=row as Transaction
    try{await client.delete(`/transactions/${tx.id}`,{params:{version:tx.version}});ok++}catch{fail++}
  }
  selection.value=[]
  await load()
  if(fail)ElMessage.warning(`已删除 ${ok} 条，${fail} 条失败（可能被票据或预算引用）`)
  else ElMessage.success(`已删除 ${ok} 条流水`)
}
async function remove(){
  if(!selected.value||props.kind==='receipts')return
  const item=selected.value
  try{await ElMessageBox.confirm('删除后无法在页面中恢复，确认删除？','删除记录',{type:'warning'})}catch{return}
  try{const url=`/${props.kind}/${item.id}`;if(props.kind==='transactions')await clientDelete(url,{version:(item as Transaction).version});else await clientDelete(url);selected.value=null;ElMessage.success('已删除');await load()}catch{ElMessage.error('删除失败，请确认权限和关联记录')}
}
async function clientDelete(url:string,params?:Record<string,unknown>){return client.delete(url,{params})}
async function recurringAction(action:'toggle'|'execute'){
  if(!selected.value||props.kind!=='recurring')return
  try{await client.post(`/recurring/${selected.value.id}/${action}`);selected.value=null;await load();ElMessage.success(action==='toggle'?'状态已更新':'已执行')}catch{ElMessage.error('操作失败')}
}
async function refund(){
  if(!selected.value||props.kind!=='transactions')return
  const item=selected.value as Transaction
  const amount=await ElMessageBox.prompt(`可退款金额：${money(item.amount-item.refundedAmount)}`,'录入退款',{inputPattern:/^\d+(?:\.\d{1,2})?$/,inputErrorMessage:'请输入有效金额'}).then(result=>Number(result.value)).catch(()=>null)
  if(amount===null)return
  if(amount<=0||amount>item.amount-item.refundedAmount){ElMessage.warning('退款金额超出可退范围');return}
  try{await client.post(`/transactions/${item.id}/refund`,{amount,date:new Date().toISOString().slice(0,10)});selected.value=null;await load();ElMessage.success('退款已记录')}catch{ElMessage.error('退款操作失败')}
}
async function reconcile(){
  if(!selected.value||props.kind!=='accounts')return
  const balance=await ElMessageBox.prompt('请输入银行对账单余额','账户对账',{inputPattern:/^-?\d+(?:\.\d{1,2})?$/,inputErrorMessage:'请输入有效金额'}).then(result=>Number(result.value)).catch(()=>null)
  if(balance===null)return
  try{await client.post(`/accounts/${selected.value.id}/reconciliations`,{statementDate:new Date().toISOString().slice(0,10),statementBalance:balance});selected.value=null;await load();ElMessage.success('对账记录已保存')}catch{ElMessage.error('对账失败')}
}
async function downloadReceipt(){
  if(!selected.value||props.kind!=='receipts')return
  try{const result=await client.get<Blob>(`/receipts/${selected.value.id}/file-download`,{responseType:'blob'});const url=URL.createObjectURL(result.data);const link=document.createElement('a');link.href=url;link.download=String((selected.value as ReceiptVoucher).fileName||`receipt-${selected.value.id}`);link.click();URL.revokeObjectURL(url)}catch{ElMessage.error('附件下载失败')}
}
async function verifyReceipt(){
  if(!selected.value||props.kind!=='receipts')return
  const voucher=selected.value as ReceiptVoucher
  try{
    await client.put(`/receipts/${voucher.id}`,{
      version:voucher.version,
      status:voucher.transactionId?'linked':'verified',
      invoiceCheckStatus:['sales_invoice','purchase_invoice'].includes(voucher.voucherType)?'verified':voucher.invoiceCheckStatus,
      deductionStatus:voucher.voucherType==='purchase_invoice'&&voucher.deductionStatus==='pending'?'deductible':voucher.deductionStatus,
    })
    selected.value=null;await load();ElMessage.success('票据已核验')
  }catch{ElMessage.error('核验失败，请刷新后确认当前状态')}
}
async function submitReceiptApproval(){
  if(!selected.value||props.kind!=='receipts')return
  const voucher=selected.value as ReceiptVoucher
  try{
    await client.post('/approvals',{
      requestType:voucher.voucherType==='reimbursement'?'reimbursement':'payment',
      entityType:'receipt_voucher',entityId:voucher.id,
      title:`${voucher.voucherType==='reimbursement'?'报销':'付款'}审批：${voucher.title}`,
      amount:Number(voucher.amount||0),
      description:[voucher.counterparty,voucher.businessPurpose,voucher.voucherNo].filter(Boolean).join(' · '),
    })
    selected.value=null;await load();ElMessage.success('审批申请已提交')
  }catch{ElMessage.error('提交失败，请检查是否已有待处理申请')}
}
async function uploadReceipts(event:Event){
  const input=event.target as HTMLInputElement
  const files=Array.from(input.files||[])
  if(!files.length)return
  batchUploading.value=true
  try{
    const form=new FormData()
    files.forEach(file=>form.append('files',file))
    const metadata={companyId:Number(localStorage.getItem('activeCompanyId'))||undefined,voucherType:'purchase_invoice',direction:'expense',counterparty:'待补充',amount:0,taxAmount:0,issueDate:new Date().toISOString().slice(0,10),status:'pending_review'}
    Object.entries(metadata).forEach(([key,value])=>{if(value!==undefined)form.append(key,String(value))})
    const response=await client.post<{successCount:number;failureCount:number;failures:Array<{status:number}>}>('/receipts/batch-upload',form)
    if(response.data.successCount)ElMessage.success(`已归档 ${response.data.successCount} 个附件`)
    if(response.data.failureCount)ElMessage.warning(`${response.data.failureCount} 个文件未导入，请检查格式或重复附件`)
    await load()
  }catch{ElMessage.error('批量归档失败，请检查格式和文件大小')}
  finally{batchUploading.value=false;input.value=''}
}
watch(()=>props.kind,()=>{keyword.value='';status.value='';view.value='all';categoryId.value=0;accountId.value=0;minAmount.value='';maxAmount.value='';dateStart.value='';dateEnd.value='';page.value=1;void loadFilterOptions();void load()})
onMounted(()=>{void loadFilterOptions();void load();if(route.query.new==='1'){editorVisible.value=true;void router.replace({ path:'/' + props.kind })}})
</script>

<template><div class="wrap ledger-page"><div class="ph"><div class="l"><div class="tile"><UiIcon :name="pageIcon" :size="20" /></div><div><h1>{{ config.title }}</h1><p class="sub">{{ config.subtitle }}</p></div></div><div class="acts"><button class="btn" type="button" @click="load">刷新</button><button class="btn" type="button" :disabled="!selection.length" @click="exportSelected">导出所选 CSV</button><button v-if="kind==='receipts'" class="btn" type="button" :disabled="!selection.length" @click="batchReceipt('verified')">批量核验</button><button v-if="kind==='receipts'" class="btn" type="button" :disabled="!selection.length" @click="batchReceipt('archived')">批量归档</button><button v-if="kind==='transactions'" class="btn" type="button" :disabled="!selection.length" @click="batchRemove">批量删除</button><button v-if="kind==='transactions'" class="btn" type="button" @click="importVisible=true">批量导入</button><label v-if="kind==='receipts'" class="btn receipt-batch-button">{{ batchUploading?'归档中…':'批量归档附件' }}<input type="file" multiple accept=".pdf,.png,.jpg,.jpeg" :disabled="batchUploading" @change="uploadReceipts"></label><button class="btn pri" type="button" @click="create">新增{{ config.title }}</button></div></div>
<div class="ledger-metrics"><MetricCard v-for="metric in metricCards" :key="metric.label" v-bind="metric" /></div>
<div v-if="kind==='recurring'" class="note-strip"><span class="lb">事项概况</span><span>今日待办<b>{{ recurringSummary?.today }}</b></span><span>7 天内<b>{{ recurringSummary?.week }}</b></span><span>已逾期<b>{{ recurringSummary?.overdue }}</b></span><span>启用中<b>{{ recurringSummary?.active }}</b></span></div><div v-if="kind==='budgets'" class="note-strip"><span class="lb">预算概况</span><span>预警预算<b>{{ budgetSummary?.warning }}</b></span><span>最近截止<b>{{ budgetSummary?.nearest }}</b></span><em>按已加载预算统计</em></div><div v-if="kind==='transactions'" class="note-strip"><span class="lb">备注关键词归类</span><span v-for="s in noteSummary" :key="s.label">{{ s.label }}<b>{{ s.count }}</b></span><em>仅统计当前页</em></div><div v-if="kind==='accounts'" class="acct-top"><section class="card"><header><h3><span class="ic"><UiIcon name="wallet" :size="14" /></span>账户结构</h3><span class="hint">按类型</span></header><div class="bd"><CompositionBar :items="accountTypeBars" total-label="账户余额" :total-value="money((summary as AccountSummary | null)?.totalAssets)" /></div></section><section class="card"><header><h3><span class="ic"><UiIcon name="shield" :size="14" /></span>对账状态</h3><span class="hint">{{ accountsRows.length }} 个账户</span></header><div class="bd"><CompositionBar :items="accountRiskBars" total-label="账户数" :total-value="String(accountsRows.length)" :as-money="false" /></div></section></div><section class="ledger-card"><form class="ledger-filters" @submit.prevent="search"><template v-if="kind==='transactions'"><div class="ledger-segments"><button v-for="option in [{key:'all',label:'全部'},{key:'income',label:'收入'},{key:'expense',label:'成本'},{key:'large',label:'大额'}]" :key="option.key" type="button" :class="{active:view===option.key}" @click="view=option.key as typeof view;search()">{{ option.label }}</button></div><select v-model.number="categoryId" class="fsel" aria-label="分类"><option :value="0">全部分类</option><option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option></select><select v-model.number="accountId" class="fsel" aria-label="账户"><option :value="0">全部账户</option><option v-for="a in accounts" :key="a.id" :value="a.id">{{ a.name }}</option></select><input v-model="dateStart" class="fdate" type="date" aria-label="起始日期"><span class="fdash">-</span><input v-model="dateEnd" class="fdate" type="date" aria-label="结束日期"><input v-model="minAmount" class="fnum" type="number" min="0" placeholder="最小金额" aria-label="最小金额"><input v-model="maxAmount" class="fnum" type="number" min="0" placeholder="最大金额" aria-label="最大金额"><input v-model="keyword" class="fkw" type="search" placeholder="搜索摘要" aria-label="搜索关键词"><button class="btn" type="submit">筛选</button><button class="btn" type="button" @click="resetFilters">重置</button></template><template v-else-if="kind==='recurring'"><div class="ledger-segments"><button v-for="option in [{key:'all',label:'全部事项'},{key:'today',label:'今日待办'},{key:'week',label:'7 天内'},{key:'overdue',label:'已逾期'},{key:'active',label:'启用中'},{key:'income',label:'周期收入'},{key:'expense',label:'周期支出'}]" :key="option.key" type="button" :class="{active:recurringView===option.key}" @click="recurringView=option.key as typeof recurringView;page=1">{{ option.label }}</button></div><div class="ledger-search"><input v-model="keyword" type="search" placeholder="搜索事项名称" aria-label="搜索周期事项"><button class="btn" type="submit">筛选</button></div></template><template v-else-if="kind==='budgets'"><div class="ledger-segments"><button v-for="option in [{key:'all',label:'全部'},{key:'active',label:'进行中'},{key:'warning',label:'预警预算'},{key:'overrun',label:'超支'},{key:'thisMonth',label:'本月到期'}]" :key="option.key" type="button" :class="{active:budgetView===option.key}" @click="budgetView=option.key as typeof budgetView;page=1">{{ option.label }}</button></div><div class="ledger-search"><input v-model="keyword" type="search" placeholder="搜索预算名称" aria-label="搜索预算"><button class="btn" type="submit">筛选</button></div></template><template v-else><div class="ledger-segments"><button v-for="option in config.statuses" :key="option.value" type="button" :class="{active:status===option.value}" @click="status=option.value;search()">{{ option.label }}</button></div><div class="ledger-search"><input v-model="keyword" type="search" placeholder="搜索名称或关键词" aria-label="搜索关键词"><button class="btn" type="submit">筛选</button></div></template></form>
<div v-if="error" class="ledger-state" role="alert">{{ error }} <button class="btn" type="button" @click="load">重试</button></div>
<div v-else class="ledger-table"><el-table v-loading="loading" :data="visibleRows" row-key="id" @selection-change="choose" @row-click="open"><template #empty><div class="empty"><UiIcon name="list" :size="26" /><p>暂无符合条件的数据</p><small>调整筛选条件，或先新增一条记录</small></div></template><el-table-column type="selection" width="48" fixed="left"/><el-table-column v-for="(column,index) in config.columns" :key="column.key" :label="column.label" :min-width="column.format==='money'?132:140" :fixed="index===0?'left':undefined" :align="column.format==='money'?'right':'left'"><template #default="scope"><div v-if="column.format==='percent'" class="pcell"><div class="pbar"><i :style="{width:`${Math.min(100,Number((scope.row as unknown as Record<string,unknown>)[column.key])*100)}%`}"></i></div><span>{{ formatted(scope.row as Row,column) }}</span></div><div v-else-if="kind==='transactions' && column.key==='note'" class="note-cell"><span class="note-tx">{{ (scope.row as Transaction).note || '无备注' }}</span><span class="meta">#{{ (scope.row as Transaction).id }}<i class="ty" :class="(scope.row as Transaction).type===1?'in':'out'">{{ (scope.row as Transaction).type===1?'收入':'支出' }}</i></span></div><span v-else-if="kind==='transactions' && column.key==='amount'" class="amt" :class="(scope.row as Transaction).type===1?'in':'out'">{{ (scope.row as Transaction).type===1?'+':'−' }}{{ money((scope.row as Transaction).amount) }}</span><span v-else-if="kind==='transactions' && column.key==='type'" class="chip" :class="(scope.row as Transaction).type===1?'in':((scope.row as Transaction).type===3?'warn':'out')">{{ (scope.row as Transaction).type===1?'收入':((scope.row as Transaction).type===3?'退款':'支出') }}</span><div v-else-if="column.key==='status' && kind!=='transactions' && kind!=='accounts'" class="tags"><span v-for="chip in statusChips(scope.row as Row)" :key="chip.text" class="tg" :class="chip.tone">{{ chip.text }}</span></div><span v-else-if="column.format==='money' && Number((scope.row as unknown as Record<string,unknown>)[column.key])<0" class="amt out">{{ formatted(scope.row as Row,column) }}</span><template v-else>{{ formatted(scope.row as Row,column) }}</template></template></el-table-column><el-table-column v-if="kind==='transactions'" label="标签" width="176"><template #default="scope"><div class="tags"><span v-for="tag in noteTags(scope.row as Row)" :key="tag.text" class="tg" :class="tag.tone">{{ tag.text }}</span></div></template></el-table-column><el-table-column v-if="kind==='accounts'" label="状态" width="190"><template #default="scope"><div class="tags"><span v-for="chip in accountStatusChips(scope.row as Row)" :key="chip.text" class="tg" :class="chip.tone">{{ chip.text }}</span></div></template></el-table-column><el-table-column v-if="kind==='receipts'" label="证据链" width="250"><template #default="scope"><div class="tags"><span v-for="chip in receiptChips(scope.row as Row)" :key="chip.text" class="tg" :class="chip.tone">{{ chip.text }}</span></div></template></el-table-column><el-table-column v-if="kind==='budgets'" label="到期" width="140"><template #default="scope"><span class="dl" :class="deadlineTone(scope.row as Budget)">{{ deadlineText(scope.row as Budget) }}</span></template></el-table-column><el-table-column label="操作" width="108" fixed="right"><template #default="scope"><div class="ops"><button class="iop" type="button" title="查看" @click.stop="open(scope.row as Row)"><UiIcon name="eye" :size="14" /></button><button class="iop" type="button" title="编辑" @click.stop="openEdit(scope.row as Row)"><UiIcon name="edit" :size="14" /></button><button v-if="kind!=='receipts'" class="iop danger" type="button" title="删除" @click.stop="openDelete(scope.row as Row)"><UiIcon name="trash" :size="14" /></button></div></template></el-table-column></el-table></div>
<footer class="ledger-footer"><span>{{ selection.length ? `已选 ${selection.length} 项` : `共 ${displayTotal} 项` }}</span><el-pagination background layout="sizes, prev, pager, next" :page-sizes="[10,20,50]" :page-size="pageSize" :current-page="page" :total="displayTotal" @current-change="changePage" @size-change="changeSize"/></footer></section>
<el-drawer v-model="drawerOpen" :title="config.title + '详情'" size="480px"><template v-if="selected"><div v-if="kind==='transactions'" class="dsum"><span class="dtype" :class="(selected as Transaction).type===1?'in':(selected as Transaction).type===3?'warn':'out'">{{ (selected as Transaction).type===1?'收入':(selected as Transaction).type===3?'退款':'支出' }}</span><div class="damt" :class="(selected as Transaction).type===1?'in':'out'">{{ money((selected as Transaction).amount) }}</div><p>{{ (selected as Transaction).note || '无备注' }}</p><small>{{ (selected as Transaction).date?.slice(0,10) }} · {{ (selected as Transaction).accountName }}</small></div><dl class="detail-fields"><div v-for="column in config.columns" :key="column.key"><dt>{{ column.label }}</dt><dd>{{ formatted(selected,column) }}</dd></div></dl><div v-if="kind==='transactions'" class="linked"><h4>关联信息</h4><div class="lrow"><span>关联预算</span><b>{{ (selected as Transaction).budgetId ? '已关联预算 #' + (selected as Transaction).budgetId : '未关联' }}</b></div><div class="lrow"><span>退款状态</span><b>{{ Number((selected as Transaction).refundedAmount) > 0 ? '已退 ' + money((selected as Transaction).refundedAmount) : ((selected as Transaction).isRefundable ? '可退 ' + money((selected as Transaction).amount - (selected as Transaction).refundedAmount) : '不可退') }}</b></div><div class="lrow"><span>原始流水</span><b>{{ (selected as Transaction).originalTransactionId ? '#' + (selected as Transaction).originalTransactionId : '—' }}</b></div></div><div v-if="kind==='receipts'" class="chain"><h4>证据链</h4><div class="steps"><span v-for="step in receiptChain(selected as ReceiptVoucher)" :key="step.label" class="step" :class="step.tone"><b>{{ step.label }}</b><small>{{ step.value }}</small></span></div></div><section v-if="kind==='receipts' && auditLogs.length" class="audit-logs"><h3>变更记录</h3><p v-for="(log,index) in auditLogs" :key="index">{{ log.createdAt.slice(0,16) }} · {{ log.actorName }} · {{ log.summary }}</p></section><div class="drawer-actions"><button class="btn" type="button" @click="edit">编辑</button><button v-if="kind!=='receipts'" class="btn" type="button" @click="remove">删除</button><button v-if="kind==='transactions' && (selected as Transaction).isRefundable" class="btn" type="button" @click="refund">录入退款</button><button v-if="kind==='accounts'" class="btn" type="button" @click="reconcile">账户对账</button><button v-if="kind==='receipts' && (selected as ReceiptVoucher).status==='pending_review'" class="btn pri" type="button" @click="verifyReceipt">核验票据</button><button v-if="kind==='receipts' && ['not_required','not_submitted','rejected'].includes((selected as ReceiptVoucher).approvalStatus)" class="btn" type="button" @click="submitReceiptApproval">提交审批</button><button v-if="kind==='receipts' && (selected as ReceiptVoucher).fileName" class="btn" type="button" @click="downloadReceipt">下载附件</button><button v-if="kind==='recurring'" class="btn" type="button" @click="recurringAction('toggle')">切换启用状态</button><button v-if="kind==='recurring'" class="btn pri" type="button" @click="recurringAction('execute')">立即执行</button></div></template></el-drawer>
<TransactionFormModal v-if="editorVisible && kind==='transactions'" :record="editRecord as unknown as Transaction | null" @saved="saved" @cancel="editorVisible=false"/>
<LedgerEditor v-else-if="editorVisible" :kind="kind" :record="editRecord" @saved="saved" @cancel="editorVisible=false"/>
<TransactionImportDialog v-if="kind==='transactions'" v-model="importVisible" @saved="load"/>
</div></template>

<style scoped>
.ledger-metrics{display:grid;grid-template-columns:repeat(auto-fit,minmax(176px,1fr));gap:12px;margin-bottom:16px}
.ledger-card{background:var(--card);border:1px solid var(--line);border-radius:16px;box-shadow:var(--sh);overflow:hidden}
.ledger-filters{display:flex;align-items:center;gap:14px;padding:12px 18px;border-bottom:1px solid var(--line2);flex-wrap:wrap}
.ledger-segments{display:flex;gap:4px;background:#f2f5f9;border-radius:10px;padding:3px}
.ledger-segments button{height:30px;padding:0 13px;border:none;border-radius:8px;background:transparent;color:var(--ink3);font-size:12.5px;cursor:pointer;font-family:inherit}
.ledger-segments button.active{background:#fff;color:var(--brand);font-weight:600;box-shadow:0 1px 3px rgba(16,24,40,.12)}
.ledger-search{margin-left:auto;display:flex;gap:8px}
.ledger-search input{height:34px;width:240px;border:1px solid var(--line);border-radius:9px;padding:0 11px;font-size:12.5px;color:var(--ink);outline:none;background:#fff}
.ledger-search input:focus{border-color:#9fb1f2;box-shadow:0 0 0 3px rgba(54,89,227,.12)}
.ledger-table :deep(.el-table){--el-table-border-color:var(--line2);font-size:12.5px}
.ledger-table :deep(.el-table th.el-table__cell){background:#fbfcfe;color:var(--ink3);font-weight:600;font-size:11.5px;height:44px;border-bottom:1px solid var(--line)}
.ledger-table :deep(.el-table td.el-table__cell){height:52px;border-bottom:1px solid var(--line2)}
.ledger-table :deep(.el-table__row:hover > td.el-table__cell){background:#f7fafd}
.ledger-footer{display:flex;align-items:center;justify-content:space-between;padding:12px 18px;border-top:1px solid var(--line2);font-size:12px;color:var(--ink3)}
@media (max-width:900px){.ledger-search{margin-left:0;width:100%}.ledger-search input{flex:1;width:auto}}
</style>
<style scoped>
.pcell{display:flex;align-items:center;gap:8px}
.pbar{flex:1;height:6px;border-radius:3px;background:#f0f4f9;overflow:hidden;min-width:60px}
.pbar i{display:block;height:100%;border-radius:3px;background:linear-gradient(90deg,#3659e3,#6c8cff)}
.pcell span{font-size:11.5px;color:var(--ink3);font-variant-numeric:tabular-nums;min-width:44px;text-align:right}
</style>
<style scoped>
.empty{display:flex;flex-direction:column;align-items:center;gap:8px;padding:38px 0;color:var(--ink4)}
.empty p{font-size:12.5px}
.empty small{font-size:11.5px;color:var(--ink4);opacity:.85}
</style>
<style scoped>
@media (max-width:1100px){
  .ledger-metrics{grid-template-columns:repeat(3,minmax(0,1fr))}
}
@media (max-width:640px){
  .ledger-metrics{grid-template-columns:repeat(2,minmax(0,1fr))}
  .ph{flex-direction:column;gap:12px}
  .acts{flex-wrap:wrap;padding-top:0}
  .ledger-filters{padding:10px 12px}
  .ledger-search{width:100%}
  .ledger-search input{width:100%}
  .ledger-footer{flex-direction:column;gap:10px;align-items:flex-start}
}
</style>
<style scoped>
.dsum{padding:4px 0 16px;margin-bottom:6px;border-bottom:1px solid var(--line2)}
.dtype{display:inline-block;font-size:11px;padding:3px 9px;border-radius:6px;background:#f2f5f9;color:var(--ink3)}
.dtype.in{background:#e6f7ef;color:#0a6b46}
.dtype.out{background:#fdeaee;color:#b42318}
.dtype.warn{background:#fdf2e3;color:#8a5313}
.damt{font-size:30px;font-weight:650;font-variant-numeric:tabular-nums;letter-spacing:-.9px;margin:10px 0 6px}
.damt.in{color:var(--green)}
.damt.out{color:var(--red)}
.dsum p{font-size:13px;color:var(--ink2)}
.dsum small{display:block;font-size:11.5px;color:var(--ink4);margin-top:4px}
.detail-fields{display:grid;grid-template-columns:1fr 1fr;gap:0;margin:0}
.detail-fields>div{padding:11px 0;border-bottom:1px solid var(--line2)}
.detail-fields dt{font-size:11.5px;color:var(--ink4);margin-bottom:5px}
.detail-fields dd{font-size:13px;color:var(--ink);font-variant-numeric:tabular-nums}
.drawer-actions{position:sticky;bottom:0;display:flex;gap:8px;justify-content:flex-end;flex-wrap:wrap;
  padding:14px 0 calc(14px + env(safe-area-inset-bottom));background:var(--card);border-top:1px solid var(--line2);margin-top:16px}
</style>
<style scoped>
:deep(.el-drawer__body){display:flex;flex-direction:column;padding:16px 20px}
.drawer-actions{margin-top:auto}
</style>
<style scoped>
.fsel{height:34px;border:1px solid var(--line);border-radius:9px;padding:0 9px;font-size:12.5px;color:var(--ink2);background:#fff;max-width:150px;font-family:inherit}
.fnum{height:34px;width:104px;border:1px solid var(--line);border-radius:9px;padding:0 9px;font-size:12.5px;color:var(--ink);background:#fff}
.fkw{height:34px;width:180px;border:1px solid var(--line);border-radius:9px;padding:0 11px;font-size:12.5px;color:var(--ink);background:#fff}
.fsel:focus,.fnum:focus,.fkw:focus{outline:none;border-color:#9fb1f2;box-shadow:0 0 0 3px rgba(54,89,227,.12)}
.tags{display:flex;flex-wrap:wrap;gap:4px}
.tg{font-size:10.5px;padding:2px 7px;border-radius:5px;background:#f2f5f9;color:var(--ink3);line-height:1.5}
.tg.red{background:#fdeaee;color:#b42318}
.tg.amber{background:#fdf2e3;color:#8a5313}
.tg.brand{background:#eef2ff;color:#2845bd}
.tg.violet{background:#f3f0ff;color:#5b3fc4}
.tg.ink{background:#eef1f5;color:#475467}
.note-strip{display:flex;align-items:center;gap:14px;flex-wrap:wrap;padding:10px 16px;margin-bottom:12px;border-radius:12px;
  background:var(--card);border:1px solid var(--line);font-size:12px;color:var(--ink3)}
.note-strip .lb{font-size:11px;font-weight:600;color:var(--brand);background:#eef2ff;border-radius:6px;padding:3px 8px}
.note-strip b{margin-left:5px;color:var(--ink);font-variant-numeric:tabular-nums}
.note-strip em{margin-left:auto;font-style:normal;font-size:11px;color:var(--ink4)}
</style>
<style scoped>
.dl{font-size:11.5px;color:var(--ink3)}
.dl.amber{color:#8a5313}
.dl.red{color:#b42318;font-weight:600}
</style>
<style scoped>
.acct-top{display:grid;grid-template-columns:1fr 1fr;gap:14px;margin-bottom:14px}
.acct-top .card .bd{padding:14px 20px 18px}
@media (max-width:900px){.acct-top{grid-template-columns:1fr}}
</style>
<style scoped>
.tg.green{background:#e6f7ef;color:#0a6b46}
.chain{margin-top:16px}
.chain h4{font-size:12px;color:var(--ink4);font-weight:600;margin-bottom:10px}
.steps{display:grid;grid-template-columns:repeat(5,1fr);gap:6px}
.step{display:flex;flex-direction:column;gap:3px;padding:10px;border-radius:10px;background:#f7f9fc;font-size:11px}
.step b{font-size:11.5px;color:var(--ink2);font-weight:600}
.step small{color:var(--ink4)}
.step.green{background:#eefaf3}
.step.green small{color:#0a6b46}
.step.amber{background:#fdf6e9}
.step.amber small{color:#8a5313}
.step.red{background:#fdeef0}
.step.red small{color:#b42318}
</style>

<style scoped>
.fdate{height:34px;border:1px solid var(--line);border-radius:9px;padding:0 8px;font-size:12px;color:var(--ink2);background:#fff;font-family:inherit}
.fdash{color:var(--ink4);font-size:11px}
</style>

<style scoped>
.linked{margin-top:16px;padding-top:14px;border-top:1px solid var(--line2)}
.linked h4{font-size:12px;color:var(--ink4);font-weight:600;margin-bottom:8px}
.lrow{display:flex;justify-content:space-between;gap:12px;padding:7px 0;font-size:12px;color:var(--ink3)}
.lrow b{font-weight:500;color:var(--ink2)}
</style>

<style scoped>
.note-cell{display:flex;flex-direction:column;gap:4px;min-width:0}
.note-tx{color:var(--ink);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.meta{display:flex;align-items:center;gap:6px;font-size:10.5px;color:var(--ink4)}
.ty{font-style:normal;padding:1px 6px;border-radius:4px;font-size:10px}
.ty.in{background:#e6f7ef;color:#0a6b46}
.ty.out{background:#fdeef0;color:#b42318}
.amt{font-variant-numeric:tabular-nums;font-weight:600}
.amt.in{color:var(--green)}
.amt.out{color:var(--red)}
.chip{display:inline-block;padding:2px 8px;border-radius:5px;font-size:11px}
.chip.in{background:#e6f7ef;color:#0a6b46}
.chip.out{background:#fdeef0;color:#b42318}
.chip.warn{background:#fdf2e3;color:#8a5313}
.ops{display:flex;gap:4px}
.iop{width:26px;height:26px;border:1px solid var(--line);border-radius:7px;background:#fff;color:var(--ink3);
  display:grid;place-items:center;cursor:pointer;transition:all .12s}
.iop:hover{border-color:#c8d2f8;color:var(--brand);background:#f5f8ff}
.iop.danger:hover{border-color:#f6c9cd;color:#b42318;background:#fdf2f3}
</style>
