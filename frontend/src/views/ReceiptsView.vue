<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import client from '../api/client'
import UiIcon from '../components/ui/UiIcon.vue'
import LedgerEditor from '../components/LedgerEditor.vue'
import type { ReceiptVoucher, ReceiptSummary } from '../types/receipt'
import { money, date } from '../utils/format'

const route = useRoute()
const router = useRouter()
const rows = ref<ReceiptVoucher[]>([])
const summary = ref<ReceiptSummary | null>(null)
const loading = ref(false)
const error = ref('')
const page = ref(1)
const size = ref(20)
const selected = ref<ReceiptVoucher | null>(null)
const editorVisible = ref(false)
const uploading = ref(false)
const busy = ref(false)

const keyword = ref('')
const typeFilter = ref('')
const statusFilter = ref('')
const directionFilter = ref('')
const dateFrom = ref('')
const dateTo = ref('')
const minAmount = ref('')
const maxAmount = ref('')
const linkage = ref('')
const invoiceCheck = ref('')
const deduction = ref('')
const reimburseFilter = ref('')

const TYPE_LABEL: Record<string, string> = {
  sales_invoice: '销项发票', purchase_invoice: '进项发票', receipt: '收据', bank_slip: '银行回单',
  contract: '合同', reimbursement: '报销单', tax_receipt: '税票',
}
const INVOICE_LABEL: Record<string, [string, string]> = {
  verified: ['发票已验', 'green'], pending: ['待查验', 'amber'], failed: ['核验失败', 'red'], not_required: ['无需查验', 'ink'],
}
const DEDUCTION_LABEL: Record<string, [string, string]> = {
  deductible: ['可抵扣', 'green'], deducted: ['已抵扣', 'green'], pending: ['待抵扣确认', 'amber'],
  transferred_out: ['已转出', 'ink'], not_applicable: ['不适用', 'ink'],
}
const APPROVAL_LABEL: Record<string, [string, string]> = {
  approved: ['审批通过', 'green'], pending: ['待审批', 'amber'], rejected: ['已驳回', 'red'],
  not_submitted: ['待发起审批', 'ink'], not_required: ['无需审批', 'ink'],
}
const ACCOUNTING_LABEL: Record<string, [string, string]> = {
  posted: ['已过账', 'green'], draft: ['待制证', 'amber'], not_started: ['未制证', 'ink'], reversed: ['已冲销', 'red'],
}
const REIMBURSE_LABEL: Record<string, [string, string]> = {
  submitted: ['报销已提交', 'amber'], approved: ['报销已批准', 'green'], paid: ['已付款', 'green'],
  archived: ['已归档', 'ink'], rejected: ['报销驳回', 'red'], not_applicable: ['非报销', 'ink'],
}

const filtered = computed(() => rows.value.filter((r) => {
  const text = keyword.value.trim().toLowerCase()
  if (text && ![r.voucherNo, r.title, r.counterparty, r.businessPurpose].some((v) => String(v || '').toLowerCase().includes(text))) return false
  if (typeFilter.value && r.voucherType !== typeFilter.value) return false
  if (statusFilter.value && r.status !== statusFilter.value) return false
  if (directionFilter.value && r.direction !== directionFilter.value) return false
  if (dateFrom.value && (r.issueDate || '') < dateFrom.value) return false
  if (dateTo.value && (r.issueDate || '') > dateTo.value) return false
  if (minAmount.value && Number(r.amount) < Number(minAmount.value)) return false
  if (maxAmount.value && Number(r.amount) > Number(maxAmount.value)) return false
  if (linkage.value === 'linked' && !r.transactionId) return false
  if (linkage.value === 'unlinked' && r.transactionId) return false
  if (invoiceCheck.value && r.invoiceCheckStatus !== invoiceCheck.value) return false
  if (deduction.value && r.deductionStatus !== deduction.value) return false
  if (reimburseFilter.value && r.reimbursementStatus !== reimburseFilter.value) return false
  return true
}))

const visible = computed(() => filtered.value.slice((page.value - 1) * size.value, page.value * size.value))
const total = computed(() => filtered.value.length)

const sum = (fn: (r: ReceiptVoucher) => boolean, key: 'amount' | 'taxAmount' = 'amount') =>
  rows.value.filter(fn).reduce((n, r) => n + Number(r[key] || 0), 0)
const count = (fn: (r: ReceiptVoucher) => boolean) => rows.value.filter(fn).length

const cards = computed(() => {
  const totalAmount = summary.value?.totalAmount ?? sum(() => true)
  const pendingAmount = summary.value?.pendingAmount ?? sum((r) => r.status === 'pending_review')
  const deductible = sum((r) => ['deductible', 'deducted'].includes(String(r.deductionStatus)), 'taxAmount')
  const flowPending = count((r) => r.approvalStatus === 'pending') + count((r) => ['draft', 'not_started'].includes(String(r.accountingStatus)))
  return [
    { label: '票据价税合计', value: money(totalAmount), foot: `销项 ${money(sum((r) => r.voucherType === 'sales_invoice'))}`, tone: 'brand', icon: 'doc' },
    { label: '待核验 / 待补票', value: money(pendingAmount), foot: `${count((r) => r.status === 'pending_review')} 张待核验 · ${count((r) => r.invoiceCheckStatus === 'pending')} 张待查验`, tone: 'amber', icon: 'clock' },
    { label: '进项可抵扣', value: money(deductible), foot: `${count((r) => r.deductionStatus === 'pending')} 张待抵扣确认`, tone: 'green', icon: 'check' },
    { label: '流程待处理', value: String(flowPending), foot: `${count((r) => r.approvalStatus === 'pending')} 张待审批 · ${count((r) => ['draft', 'not_started'].includes(String(r.accountingStatus)))} 张待制证`, tone: 'ink', icon: 'shield' },
  ]
})

const chain = computed(() => {
  const sales = rows.value.filter((r) => r.voucherType === 'sales_invoice')
  const purchase = rows.value.filter((r) => r.voucherType === 'purchase_invoice')
  return [
    { label: '销项发票', value: money(sales.reduce((n, r) => n + Number(r.amount || 0), 0)), note: `销项税额 ${money(sales.reduce((n, r) => n + Number(r.taxAmount || 0), 0))}` },
    { label: '进项发票', value: money(purchase.reduce((n, r) => n + Number(r.amount || 0), 0)), note: `可抵扣 ${money(purchase.reduce((n, r) => n + Number(r.taxAmount || 0), 0))}` },
    { label: '流水关联缺口', value: `${summary.value?.missingTransactionCount ?? count((r) => !r.transactionId)} 张`, note: '发票、报销和回单需关联流水' },
    { label: '税期缺口', value: `${count((r) => !r.taxPeriod)} 张`, note: '影响增值税与申报归档' },
  ]
})

const reimbursementRows = computed(() => {
  const list = rows.value.filter((r) => String(r.reimbursementStatus) !== 'not_applicable' || r.voucherType === 'reimbursement')
  return [
    { label: '报销总额', value: money(list.reduce((n, r) => n + Number(r.amount || 0), 0)) },
    { label: '待审批 / 待付款', value: money(list.filter((r) => ['submitted', 'approved'].includes(String(r.reimbursementStatus))).reduce((n, r) => n + Number(r.amount || 0), 0)) },
    { label: '待制证 / 过账', value: `${count((r) => ['draft', 'not_started'].includes(String(r.accountingStatus)))} 张` },
    { label: '附件缺口', value: `${count((r) => !r.fileName)} 张` },
  ]
})

function vouchersToChips(r: ReceiptVoucher) {
  const chips: Array<{ text: string; tone: string }> = []
  if (!r.transactionId) chips.push({ text: '未关联流水', tone: 'amber' })
  if (!r.fileName) chips.push({ text: '附件缺失', tone: 'red' })
  const inv = INVOICE_LABEL[String(r.invoiceCheckStatus)]
  if (inv && r.invoiceCheckStatus !== 'not_required') chips.push({ text: inv[0], tone: inv[1] })
  return chips
}
function taxChips(r: ReceiptVoucher) {
  const chips: Array<{ text: string; tone: string }> = []
  const use = (map: Record<string, [string, string]>, key: string) => {
    const hit = map[key]
    if (hit && hit[0] !== '不适用' && hit[0] !== '无需查验' && hit[0] !== '无需审批' && hit[0] !== '非报销') chips.push({ text: hit[0], tone: hit[1] })
  }
  use(DEDUCTION_LABEL, String(r.deductionStatus))
  use(APPROVAL_LABEL, String(r.approvalStatus))
  use(ACCOUNTING_LABEL, String(r.accountingStatus))
  use(REIMBURSE_LABEL, String(r.reimbursementStatus))
  return chips.slice(0, 4)
}

function resetFilters() {
  keyword.value = ''; typeFilter.value = ''; statusFilter.value = ''; directionFilter.value = ''
  dateFrom.value = ''; dateTo.value = ''; minAmount.value = ''; maxAmount.value = ''
  linkage.value = ''; invoiceCheck.value = ''; deduction.value = ''; reimburseFilter.value = ''
  page.value = 1
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [list, s] = await Promise.all([
      client.get<{ content: ReceiptVoucher[] }>('/receipts', { params: { page: 0, size: 100, companyId: Number(localStorage.getItem('activeCompanyId')) || undefined } }),
      client.get<ReceiptSummary>('/receipts/summary', { params: { companyId: Number(localStorage.getItem('activeCompanyId')) || undefined } }),
    ])
    rows.value = list.data.content || []
    summary.value = s.data
  } catch { error.value = '票据数据加载失败，请检查后端服务与账号权限。' } finally { loading.value = false }
}

async function verify(row: ReceiptVoucher) {
  busy.value = true
  try {
    await client.put(`/receipts/${row.id}`, {
      version: row.version,
      status: row.transactionId ? 'linked' : 'verified',
      invoiceCheckStatus: ['sales_invoice', 'purchase_invoice'].includes(String(row.voucherType)) ? 'verified' : row.invoiceCheckStatus,
      deductionStatus: row.voucherType === 'purchase_invoice' && row.deductionStatus === 'pending' ? 'deductible' : row.deductionStatus,
    })
    selected.value = null
    await load()
    ElMessage.success('票据已核验')
  } catch { ElMessage.error('核验失败，请刷新后确认当前状态') } finally { busy.value = false }
}

async function upload(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  if (!files.length) return
  uploading.value = true
  try {
    const form = new FormData()
    files.forEach((f) => form.append('files', f))
    const meta = { companyId: Number(localStorage.getItem('activeCompanyId')) || undefined, voucherType: 'purchase_invoice', direction: 'expense', counterparty: '待补充', amount: 0, taxAmount: 0, issueDate: new Date().toISOString().slice(0, 10), status: 'pending_review' }
    Object.entries(meta).forEach(([k, v]) => { if (v !== undefined) form.append(k, String(v)) })
    const res = await client.post<{ successCount: number; failureCount: number }>('/receipts/batch-upload', form)
    if (res.data.successCount) ElMessage.success(`已归档 ${res.data.successCount} 个附件`)
    if (res.data.failureCount) ElMessage.warning(`${res.data.failureCount} 个文件未导入`)
    await load()
  } catch { ElMessage.error('批量归档失败，请检查格式和大小') } finally { uploading.value = false; input.value = '' }
}

async function download(row: ReceiptVoucher) {
  try {
    const res = await client.get<Blob>(`/receipts/${row.id}/file-download`, { responseType: 'blob' })
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url
    a.download = row.fileName || `receipt-${row.id}`
    a.click()
    URL.revokeObjectURL(url)
  } catch { ElMessage.error('附件下载失败') }
}

onMounted(() => { void load(); if (route.query.new === '1') { editorVisible.value = true; void router.replace({ path: '/receipts' }) } })
</script>

<template>
  <div class="wrap receipts">
    <div class="ph">
      <div class="l">
        <div class="tile"><UiIcon name="doc" :size="20" /></div>
        <div><h1>票据与报销中心</h1><p class="sub">发票、报销单、银行回单、合同付款与税务回执统一归档</p></div>
      </div>
      <div class="acts">
        <button class="btn" type="button" @click="load"><UiIcon name="refresh" :size="14" />刷新</button>
        <label class="btn">{{ uploading ? '归档中…' : '批量归档附件' }}<input type="file" multiple accept=".pdf,.png,.jpg,.jpeg" hidden :disabled="uploading" @change="upload" /></label>
        <button class="btn pri" type="button" @click="editorVisible = true"><UiIcon name="plus" :size="14" />新增凭证</button>
      </div>
    </div>

    <div v-if="error" class="err" role="alert">{{ error }}<button class="btn" @click="load">重试</button></div>

    <div class="kpis" v-loading="loading">
      <div v-for="card in cards" :key="card.label" class="kcard">
        <div class="kt"><span>{{ card.label }}</span><i :class="card.tone"><UiIcon :name="card.icon" :size="14" /></i></div>
        <div class="kv">{{ card.value }}</div>
        <div class="kf">{{ card.foot }}</div>
      </div>
    </div>

    <div class="panels">
      <section class="card">
        <header><h3><span class="ic"><UiIcon name="doc" :size="14" /></span>发票与税务证据链</h3></header>
        <div class="bd chain">
          <div v-for="item in chain" :key="item.label" class="citem">
            <span>{{ item.label }}</span>
            <b>{{ item.value }}</b>
            <small>{{ item.note }}</small>
          </div>
        </div>
      </section>
      <section class="card">
        <header><h3><span class="ic"><UiIcon name="wallet" :size="14" /></span>报销流程</h3></header>
        <div class="bd reim">
          <div v-for="item in reimbursementRows" :key="item.label" class="rrow"><span>{{ item.label }}</span><b>{{ item.value }}</b></div>
        </div>
      </section>
    </div>

    <section class="filters">
      <div class="frow">
        <label class="f"><UiIcon name="search" :size="14" /><input v-model="keyword" placeholder="搜索凭证号、标题、对方…" /></label>
        <select v-model="typeFilter"><option value="">全部类型</option><option v-for="(label, key) in TYPE_LABEL" :key="key" :value="key">{{ label }}</option></select>
        <select v-model="statusFilter"><option value="">全部状态</option><option value="pending_review">待复核</option><option value="verified">已核验</option><option value="linked">已关联</option><option value="archived">已归档</option><option value="rejected">已驳回</option></select>
        <select v-model="directionFilter"><option value="">收支方向</option><option value="income">收入</option><option value="expense">支出</option></select>
      </div>
      <div class="frow">
        <input v-model="dateFrom" type="date" />
        <span class="dash">-</span>
        <input v-model="dateTo" type="date" />
        <input v-model="minAmount" type="number" placeholder="最小金额" />
        <span class="dash">-</span>
        <input v-model="maxAmount" type="number" placeholder="最大金额" />
        <select v-model="linkage"><option value="">关联状态</option><option value="linked">已关联流水</option><option value="unlinked">未关联流水</option></select>
      </div>
      <div class="frow">
        <select v-model="invoiceCheck"><option value="">发票查验</option><option value="verified">已查验</option><option value="pending">待查验</option><option value="failed">查验失败</option></select>
        <select v-model="deduction"><option value="">进项抵扣</option><option value="deductible">可抵扣</option><option value="deducted">已抵扣</option><option value="pending">待确认</option></select>
        <select v-model="reimburseFilter"><option value="">报销状态</option><option value="submitted">已提交</option><option value="approved">已批准</option><option value="paid">已付款</option><option value="rejected">已驳回</option></select>
        <button class="btn pri" type="button" @click="page = 1">搜索</button>
        <button class="btn" type="button" @click="resetFilters">重置</button>
      </div>
    </section>

    <section class="card ledgertable">
      <header><h3><span class="ic"><UiIcon name="list" :size="14" /></span>税务凭证台账</h3><span class="hint">共 {{ total }} 张</span></header>
      <div class="thead">
        <span>凭证</span><span>类型</span><span>对方 / 用途</span><span class="r">金额</span><span>税务口径</span><span>日期</span>
      </div>
      <div v-loading="loading">
        <button v-for="row in visible" :key="row.id" class="trow" type="button" @click="selected = row">
          <span class="c-voucher">
            <b>{{ row.title }}</b>
            <small>{{ row.voucherNo || '—' }}<em v-for="chip in vouchersToChips(row)" :key="chip.text" :class="chip.tone">{{ chip.text }}</em></small>
            <small class="purpose">{{ row.businessPurpose || row.expenseOwner || '—' }}</small>
          </span>
          <span class="c-type">
            <i :class="row.direction === 'income' ? 'in' : 'out'">{{ TYPE_LABEL[String(row.voucherType)] || row.voucherType }}</i>
          </span>
          <span class="c-party">
            <b>{{ row.counterparty || '—' }}</b>
            <small>{{ row.taxPeriod || '税期待补' }} · {{ row.expenseOwner || '经办未填' }}</small>
          </span>
          <span class="c-amount">
            <b>{{ money(row.amount) }}</b>
            <small>税额 {{ money(row.taxAmount) }}</small>
          </span>
          <span class="c-tax">
            <i v-for="chip in taxChips(row)" :key="chip.text" :class="chip.tone">{{ chip.text }}</i>
            <small v-if="!taxChips(row).length">—</small>
          </span>
          <span class="c-date">{{ date(row.issueDate) }}</span>
        </button>
        <p v-if="!visible.length" class="empty">没有符合条件的凭证</p>
      </div>
      <footer class="tfoot">
        <span>共 {{ total }} 张 · 已选筛选</span>
        <el-pagination background layout="sizes, prev, pager, next" :page-sizes="[10, 20, 50]" :page-size="size"
          :current-page="page" :total="total" @current-change="(v:number) => page = v" @size-change="(v:number) => { size = v; page = 1 }" />
      </footer>
    </section>

    <el-drawer :model-value="!!selected" :title="selected?.title || '票据详情'" size="520px" @close="selected = null">
      <template v-if="selected">
        <div class="dhead">
          <span class="dtype">{{ TYPE_LABEL[String(selected.voucherType)] || selected.voucherType }}</span>
          <b>{{ money(selected.amount) }}</b>
          <small>{{ selected.counterparty }} · {{ date(selected.issueDate) }}</small>
        </div>
        <div class="chain2">
          <span v-for="(step, i) in [
            { label: '票据', value: selected.voucherNo || '已归档', tone: 'green' },
            { label: '发票查验', value: (INVOICE_LABEL[String(selected.invoiceCheckStatus)] || ['—', 'ink'])[0], tone: (INVOICE_LABEL[String(selected.invoiceCheckStatus)] || ['—', 'ink'])[1] },
            { label: '流水', value: selected.transactionId ? '已关联' : '未关联', tone: selected.transactionId ? 'green' : 'amber' },
            { label: '审批', value: (APPROVAL_LABEL[String(selected.approvalStatus)] || ['—', 'ink'])[0], tone: (APPROVAL_LABEL[String(selected.approvalStatus)] || ['—', 'ink'])[1] },
            { label: '报销', value: (REIMBURSE_LABEL[String(selected.reimbursementStatus)] || ['—', 'ink'])[0], tone: (REIMBURSE_LABEL[String(selected.reimbursementStatus)] || ['—', 'ink'])[1] },
          ]" :key="i" class="step" :class="step.tone"><b>{{ step.label }}</b><small>{{ step.value }}</small></span>
        </div>
        <dl class="dfields">
          <div><dt>税额</dt><dd>{{ money(selected.taxAmount) }}</dd></div>
          <div><dt>税率</dt><dd>{{ selected.taxRate }}%</dd></div>
          <div><dt>税期</dt><dd>{{ selected.taxPeriod || '—' }}</dd></div>
          <div><dt>报销状态</dt><dd>{{ (REIMBURSE_LABEL[String(selected.reimbursementStatus)] || ['—'])[0] }}</dd></div>
          <div><dt>制证状态</dt><dd>{{ (ACCOUNTING_LABEL[String(selected.accountingStatus)] || ['—'])[0] }}</dd></div>
          <div><dt>附件</dt><dd>{{ selected.fileName || '缺失' }}</dd></div>
        </dl>
        <div class="dactions">
          <button class="btn" type="button" @click="download(selected)">下载附件</button>
          <button v-if="selected.status === 'pending_review'" class="btn pri" type="button" :disabled="busy" @click="verify(selected)">核验票据</button>
        </div>
      </template>
    </el-drawer>

    <LedgerEditor v-if="editorVisible" kind="receipts" :record="null" @saved="editorVisible = false; load()" @cancel="editorVisible = false" />
  </div>
</template>

<style scoped>
.receipts{display:flex;flex-direction:column;gap:14px}
.acts{display:flex;gap:10px;flex-wrap:wrap}
.acts .btn{cursor:pointer}
.kpis{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}
.kcard{background:var(--card);border:1px solid var(--line);border-radius:16px;padding:15px 16px 13px;box-shadow:var(--sh);position:relative}
.kt{display:flex;align-items:center;justify-content:space-between;font-size:12px;color:var(--ink3)}
.kt i{width:26px;height:26px;border-radius:8px;display:grid;place-items:center}
.kt i.brand{background:#eef2ff;color:#3659e3}
.kt i.amber{background:#fdf2e3;color:#b54708}
.kt i.green{background:#e6f7ef;color:#0a6b46}
.kt i.ink{background:#eef1f5;color:#667085}
.kv{font-size:26px;font-weight:650;letter-spacing:-.6px;margin:8px 0 6px;color:var(--ink);font-variant-numeric:tabular-nums}
.kf{font-size:11px;color:var(--ink4)}
.panels{display:grid;grid-template-columns:1.4fr 1fr;gap:14px}
.chain{display:grid;grid-template-columns:repeat(4,1fr);gap:0}
.citem{padding:10px 16px;border-left:1px solid var(--line2)}
.citem:first-child{border-left:none}
.citem span{display:block;font-size:11.5px;color:var(--ink4)}
.citem b{display:block;font-size:16px;font-weight:650;margin:6px 0 4px;color:var(--ink);font-variant-numeric:tabular-nums}
.citem small{font-size:10.5px;color:var(--ink4);line-height:1.5}
.reim{display:flex;flex-direction:column}
.rrow{display:flex;justify-content:space-between;align-items:baseline;padding:9px 0;border-bottom:1px solid var(--line2);font-size:12.5px;color:var(--ink3)}
.rrow:last-child{border-bottom:none}
.rrow b{color:var(--ink);font-variant-numeric:tabular-nums;font-weight:600}
.filters{display:flex;flex-direction:column;gap:10px;padding:12px 14px;background:var(--card);border:1px solid var(--line);border-radius:14px}
.frow{display:flex;gap:10px;flex-wrap:wrap;align-items:center}
.frow select,.frow input{height:34px;border:1px solid var(--line);border-radius:9px;padding:0 10px;font-size:12px;color:var(--ink2);background:#fff;font-family:inherit;min-width:140px}
.frow .f{display:flex;align-items:center;gap:7px;flex:1;min-width:240px;height:34px;border:1px solid var(--line);border-radius:9px;padding:0 10px;color:var(--ink4);background:#fff}
.frow .f input{border:none;flex:1;height:auto;padding:0;min-width:0}
.dash{color:var(--ink4);font-size:11px}
.ledgertable{background:var(--card);border:1px solid var(--line);border-radius:16px;box-shadow:var(--sh);overflow:hidden}
.ledgertable>header{height:54px;display:flex;align-items:center;justify-content:space-between;padding:0 20px}
.ledgertable h3{font-size:14px;font-weight:650;display:flex;align-items:center;gap:9px}
.ledgertable h3 .ic{width:26px;height:26px;border-radius:8px;background:#eef2ff;color:var(--brand);display:grid;place-items:center}
.hint{font-size:11.5px;color:var(--ink4)}
.thead,.trow{display:grid;grid-template-columns:2.2fr .9fr 1.3fr .9fr 1.4fr .7fr;gap:12px;align-items:center;padding:0 20px}
.thead{height:38px;background:#fbfcfe;border-top:1px solid var(--line);border-bottom:1px solid var(--line);font-size:11px;color:var(--ink4);font-weight:600}
.thead .r{text-align:right}
.trow{width:100%;text-align:left;border:none;border-bottom:1px solid var(--line2);background:transparent;cursor:pointer;padding-top:12px;padding-bottom:12px;font-family:inherit}
.trow:hover{background:#f7fafd}
.c-voucher b{display:block;font-size:12.5px;color:var(--ink);font-weight:600}
.c-voucher small{display:flex;gap:6px;align-items:center;font-size:10.5px;color:var(--ink4);margin-top:3px;flex-wrap:wrap}
.c-voucher .purpose{color:var(--ink4);display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.c-voucher em,.c-tax i{font-style:normal;padding:1px 6px;border-radius:4px;font-size:10px}
.c-type i{font-style:normal;font-size:11px;padding:2px 8px;border-radius:5px}
.c-type i.out{background:#fdeef0;color:#b42318}
.c-type i.in{background:#e6f7ef;color:#0a6b46}
.c-party b{display:block;font-size:12px;color:var(--ink2)}
.c-party small{font-size:10.5px;color:var(--ink4)}
.c-amount{text-align:right}
.c-amount b{display:block;font-size:12.5px;color:var(--ink);font-variant-numeric:tabular-nums}
.c-amount small{font-size:10.5px;color:var(--ink4)}
.c-tax{display:flex;flex-wrap:wrap;gap:4px}
.c-tax small{font-size:11px;color:var(--ink4)}
.c-date{font-size:11.5px;color:var(--ink3);font-variant-numeric:tabular-nums}
.green{background:#e6f7ef;color:#0a6b46}
.amber{background:#fdf2e3;color:#8a5313}
.red{background:#fdeef0;color:#b42318}
.brand{background:#eef2ff;color:#2845bd}
.ink{background:#eef1f5;color:#475467}
.tfoot{display:flex;align-items:center;justify-content:space-between;padding:12px 20px;border-top:1px solid var(--line2);font-size:12px;color:var(--ink3)}
.empty{font-size:12px;color:var(--ink4);padding:36px 0;text-align:center}
.dhead{display:flex;flex-direction:column;gap:6px;padding-bottom:14px;border-bottom:1px solid var(--line2)}
.dhead .dtype{align-self:flex-start;font-size:11px;padding:2px 8px;border-radius:5px;background:#eef2ff;color:#2845bd}
.dhead b{font-size:28px;font-weight:650;font-variant-numeric:tabular-nums;color:var(--ink)}
.dhead small{font-size:11.5px;color:var(--ink4)}
.chain2{display:grid;grid-template-columns:repeat(5,1fr);gap:6px;margin:14px 0}
.step{display:flex;flex-direction:column;gap:3px;padding:10px 8px;border-radius:10px;background:#f7f9fc;font-size:11px}
.step b{font-size:11px;color:var(--ink2)}
.step small{color:var(--ink4)}
.step.green{background:#eefaf3}.step.green small{color:#0a6b46}
.step.amber{background:#fdf6e9}.step.amber small{color:#8a5313}
.step.red{background:#fdeef0}.step.red small{color:#b42318}
.dfields{display:grid;grid-template-columns:1fr 1fr;gap:0}
.dfields>div{padding:10px 0;border-bottom:1px solid var(--line2)}
.dfields dt{font-size:11px;color:var(--ink4);margin-bottom:4px}
.dfields dd{font-size:13px;color:var(--ink)}
.dactions{display:flex;justify-content:flex-end;gap:8px;margin-top:16px}
.err{display:flex;align-items:center;gap:12px;padding:12px 16px;border-radius:12px;background:#fdeaee;color:#b42318;font-size:13px}
@media (max-width:1100px){.kpis{grid-template-columns:repeat(2,1fr)}.panels{grid-template-columns:1fr}.chain{grid-template-columns:repeat(2,1fr)}
  .thead,.trow{grid-template-columns:1.6fr 1fr 1fr .9fr}.c-tax,.c-date{display:none}}
</style>
