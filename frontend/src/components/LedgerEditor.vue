<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import client from '../api/client'
import UiIcon from './ui/UiIcon.vue'
import type { Category } from '../types/category'
import type { Account } from '../types/account'

type Kind = 'transactions' | 'budgets' | 'receipts' | 'accounts' | 'recurring'
type Value = string | number | null
type Field = {
  key: string
  label: string
  type?: 'number' | 'date' | 'select' | 'textarea' | 'checkbox' | 'money'
  required?: boolean
  group: string
  placeholder?: string
  hint?: string
  options?: Array<[string, string]>
}

const props = defineProps<{ kind: Kind; record: Record<string, unknown> | null }>()
const emit = defineEmits<{ saved: []; cancel: [] }>()

const form = ref<Record<string, Value>>({})
const categories = ref<Category[]>([])
const accounts = ref<Account[]>([])
const saving = ref(false)
const attachment = ref<File | null>(null)

const fields: Record<Kind, Field[]> = {
  transactions: [
    { key: 'type', label: '收支类型', type: 'select', required: true, group: '类型与金额', options: [['1', '收入'], ['2', '支出']] },
    { key: 'amount', label: '金额', type: 'money', required: true, group: '类型与金额' },
    { key: 'categoryId', label: '分类', type: 'select', required: true, group: '分类与账户' },
    { key: 'accountId', label: '账户', type: 'select', required: true, group: '分类与账户' },
    { key: 'date', label: '发生日期', type: 'date', required: true, group: '日期与备注' },
    { key: 'note', label: '备注', group: '日期与备注', placeholder: '写清用途，后续可按关键词归类' },
  ],
  budgets: [
    { key: 'name', label: '预算名称', required: true, group: '基本信息', placeholder: '例如：市场投放预算' },
    { key: 'amount', label: '预算金额', type: 'money', required: true, group: '基本信息' },
    { key: 'startDate', label: '开始日期', type: 'date', required: true, group: '周期与预警' },
    { key: 'endDate', label: '结束日期', type: 'date', required: true, group: '周期与预警' },
    { key: 'warningThreshold', label: '预警阈值（%）', type: 'number', required: true, group: '周期与预警', placeholder: '80', hint: '达到该比例时提醒' },
    { key: 'categoryId', label: '关联分类', type: 'select', group: '周期与预警' },
  ],
  receipts: [
    { key: 'title', label: '凭证标题', required: true, group: '基本信息', placeholder: '例如：办公采购进项发票' },
    { key: 'voucherNo', label: '凭证编号', group: '基本信息', placeholder: '留空则系统生成' },
    { key: 'voucherType', label: '凭证类型', type: 'select', required: true, group: '基本信息', options: [['sales_invoice', '销项发票'], ['purchase_invoice', '进项发票'], ['receipt', '收据'], ['bank_slip', '银行回单'], ['contract', '合同'], ['reimbursement', '报销单'], ['tax_receipt', '税务回执']] },
    { key: 'direction', label: '收支方向', type: 'select', required: true, group: '基本信息', options: [['income', '收入'], ['expense', '支出']] },
    { key: 'counterparty', label: '对方主体', required: true, group: '金额与税', placeholder: '客户、供应商、员工、税务机关' },
    { key: 'status', label: '凭证状态', type: 'select', group: '金额与税', options: [['pending_review', '待核验'], ['verified', '已核验'], ['linked', '已关联'], ['archived', '已归档']] },
    { key: 'amount', label: '价税合计', type: 'money', required: true, group: '金额与税' },
    { key: 'taxAmount', label: '税额', type: 'money', group: '金额与税' },
    { key: 'taxRate', label: '税率（%）', type: 'number', group: '金额与税', placeholder: '6' },
    { key: 'taxPeriod', label: '税期', group: '金额与税', placeholder: '2026-11', hint: '用于增值税申报归档' },
    { key: 'invoiceCheckStatus', label: '发票查验', type: 'select', group: '税务口径', options: [['not_required', '无需查验'], ['pending', '待查验'], ['verified', '已查验'], ['failed', '查验失败']] },
    { key: 'deductionStatus', label: '进项抵扣', type: 'select', group: '税务口径', options: [['not_applicable', '不适用'], ['pending', '待确认'], ['deductible', '可抵扣'], ['deducted', '已抵扣']] },
    { key: 'reimbursementStatus', label: '报销状态', type: 'select', group: '税务口径', options: [['not_applicable', '不适用'], ['submitted', '已提交'], ['approved', '已通过'], ['paid', '已付款'], ['rejected', '已驳回']] },
    { key: 'accountingStatus', label: '会计状态', type: 'select', group: '税务口径', options: [['not_started', '未制证'], ['draft', '待制证'], ['posted', '已过账']] },
    { key: 'businessPurpose', label: '业务用途', group: '关联与经办', placeholder: '例如：办公采购、差旅、客户项目交付' },
    { key: 'expenseOwner', label: '经办人 / 报销人', group: '关联与经办', placeholder: '员工、部门或负责人' },
    { key: 'issueDate', label: '开具日期', type: 'date', required: true, group: '关联与经办' },
    { key: 'dueDate', label: '截止日期', type: 'date', group: '关联与经办' },
    { key: 'transactionId', label: '关联流水 ID', type: 'number', group: '关联与经办', placeholder: '可选', hint: '填写后自动挂到该笔流水' },
    { key: 'note', label: '备注', type: 'textarea', group: '补充说明', placeholder: '补充说明，200 字以内' },
  ],
  accounts: [
    { key: 'name', label: '账户名称', required: true, group: '基本信息', placeholder: '例如：公司基本户' },
    { key: 'type', label: '账户类型', type: 'select', required: true, group: '基本信息', options: [['bank', '银行账户'], ['cash', '现金'], ['credit', '信用账户'], ['digital', '数字钱包'], ['investment', '理财账户'], ['debt', '负债账户']] },
    { key: 'subType', label: '账户子类', group: '基本信息', placeholder: '对公账户、备用金、信用卡…' },
    { key: 'currency', label: '币种', type: 'select', required: true, group: '基本信息', options: [['CNY', 'CNY 人民币'], ['USD', 'USD 美元'], ['HKD', 'HKD 港币'], ['EUR', 'EUR 欧元'], ['JPY', 'JPY 日元']] },
    { key: 'bank', label: '银行 / 机构', group: '开户信息', placeholder: '招商银行、支付宝、现金…' },
    { key: 'openingBank', label: '开户地址', group: '开户信息', placeholder: '开户地址或平台主体' },
    { key: 'accountNo', label: '账号尾号', group: '开户信息', placeholder: '只建议记录尾号' },
    { key: 'ownerName', label: '负责人', group: '开户信息', placeholder: '财务负责人' },
    { key: 'balance', label: '账面余额', type: 'money', required: true, group: '余额与授信' },
    { key: 'availableBalance', label: '可用余额', type: 'money', group: '余额与授信', placeholder: '0.00', hint: '留空则与账面余额一致' },
    { key: 'creditLimit', label: '授信额度', type: 'money', group: '余额与授信', placeholder: '0.00' },
    { key: 'frozenAmount', label: '冻结金额', type: 'money', group: '余额与授信', placeholder: '0.00' },
    { key: 'openedAt', label: '开户日期', type: 'date', group: '状态与归属' },
    { key: 'status', label: '账户状态', type: 'select', group: '状态与归属', options: [['1', '启用'], ['2', '冻结'], ['0', '停用']] },
    { key: 'includeInNetWorth', label: '纳入净资产', type: 'select', group: '状态与归属', options: [['1', '纳入'], ['0', '不纳入']] },
    { key: 'purpose', label: '账户用途', type: 'textarea', group: '状态与归属', placeholder: '例如：客户回款、供应商付款和税费缴纳' },
  ],
  recurring: [
    { key: 'name', label: '事项名称', required: true, group: '基本信息', placeholder: '例如：办公室租金' },
    { key: 'type', label: '收支类型', type: 'select', required: true, group: '基本信息', options: [['1', '收入'], ['2', '支出']] },
    { key: 'amount', label: '金额', type: 'money', required: true, group: '基本信息' },
    { key: 'frequency', label: '频率', type: 'select', required: true, group: '周期设置', options: [['daily', '每日'], ['weekly', '每周'], ['monthly', '每月'], ['yearly', '每年']] },
    { key: 'interval', label: '间隔', type: 'number', required: true, group: '周期设置', placeholder: '1' },
    { key: 'dayOfWeek', label: '每周几', type: 'select', group: '周期设置', options: [['1', '周一'], ['2', '周二'], ['3', '周三'], ['4', '周四'], ['5', '周五'], ['6', '周六'], ['7', '周日']], hint: '按周执行时生效' },
    { key: 'dayOfMonth', label: '每月几号', type: 'number', group: '周期设置', placeholder: '1-31', hint: '按月执行时生效' },
    { key: 'monthOfYear', label: '每年几月', type: 'number', group: '周期设置', placeholder: '1-12', hint: '按年执行时生效' },
    { key: 'startDate', label: '开始日期', type: 'date', required: true, group: '周期设置' },
    { key: 'endDate', label: '结束日期', type: 'date', group: '周期设置' },
    { key: 'note', label: '备注', type: 'textarea', group: '补充说明', placeholder: '补充说明' },
  ],
}

const currentFields = computed(() => fields[props.kind])
const title = computed(() => `${props.record ? '编辑' : '新增'}${({ transactions: '流水', budgets: '预算', receipts: '凭证', accounts: '账户', recurring: '周期事项' } as Record<Kind, string>)[props.kind]}`)
const filteredCategories = computed(() => categories.value.filter((x) => props.kind !== 'transactions' || x.type === (Number(form.value.type) === 1 ? 'income' : 'expense')))

const groups = computed(() => {
  const order: string[] = []
  const map = new Map<string, Field[]>()
  for (const field of currentFields.value) {
    if (!map.has(field.group)) { map.set(field.group, []); order.push(field.group) }
    map.get(field.group)!.push(field)
  }
  return order.map((name) => ({ name, fields: map.get(name)! }))
})

function options(field: Field): Array<[string, string]> {
  if (field.key === 'categoryId') return filteredCategories.value.map((x) => [String(x.id), x.name])
  if (field.key === 'accountId') return accounts.value.map((x) => [String(x.id), x.name])
  return field.options || []
}

function reset() {
  const today = new Date().toISOString().slice(0, 10)
  const defaults: Record<Kind, Record<string, Value>> = {
    transactions: { type: 2, amount: 0, categoryId: '', accountId: '', date: today, note: '' },
    budgets: { name: '', amount: 0, startDate: today, endDate: '', warningThreshold: 80, categoryId: '' },
    receipts: { title: '', voucherNo: '', voucherType: 'purchase_invoice', direction: 'expense', status: 'pending_review', counterparty: '', amount: 0, taxAmount: 0, taxRate: 0, taxPeriod: '', invoiceCheckStatus: 'pending', deductionStatus: 'pending', reimbursementStatus: 'not_applicable', accountingStatus: 'not_started', businessPurpose: '', expenseOwner: '', issueDate: today, dueDate: '', transactionId: null, note: '' },
    accounts: { name: '', type: 'bank', subType: '', currency: 'CNY', bank: '', openingBank: '', accountNo: '', ownerName: '', balance: 0, availableBalance: null, creditLimit: null, frozenAmount: null, openedAt: '', status: '1', includeInNetWorth: '1', purpose: '' },
    recurring: { name: '', type: 2, amount: 0, frequency: 'monthly', interval: 1, dayOfWeek: '', dayOfMonth: '', monthOfYear: '', startDate: today, endDate: '', note: '' },
  }
  attachment.value = null
  const source = props.record || {}
  form.value = { ...defaults[props.kind] }
  for (const field of fields[props.kind]) {
    if (source[field.key] !== undefined && source[field.key] !== null) {
      const raw = source[field.key]
      form.value[field.key] = typeof raw === 'boolean'
        ? Number(raw)
        : (field.type === 'select' && typeof raw === 'number' ? String(raw) : raw as Value)
    }
  }
}
watch(() => [props.kind, props.record], reset, { immediate: true })

async function loadOptions() {
  if (props.kind !== 'transactions' && props.kind !== 'budgets') return
  try {
    const [c, a] = await Promise.all([client.get<Category[]>('/categories'), client.get<Account[]>('/accounts')])
    categories.value = c.data
    accounts.value = a.data
  } catch { ElMessage.warning('分类或账户加载失败') }
}
void loadOptions()

async function save() {
  for (const field of currentFields.value) {
    const empty = form.value[field.key] === '' || form.value[field.key] == null || (['categoryId', 'accountId'].includes(field.key) && !form.value[field.key])
    if (field.required && empty) { ElMessage.warning(`请填写${field.label}`); return }
  }
  if (props.kind === 'transactions' && !filteredCategories.value.some((item) => item.id === Number(form.value.categoryId))) {
    ElMessage.warning('请为当前收支类型重新选择分类')
    return
  }
  const data: Record<string, Value | boolean> = { ...form.value }
  for (const key of ['amount', 'categoryId', 'accountId', 'warningThreshold', 'taxAmount', 'taxRate', 'transactionId', 'balance', 'availableBalance', 'creditLimit', 'frozenAmount', 'interval', 'type', 'status', 'dayOfWeek', 'dayOfMonth', 'monthOfYear']) {
    if (data[key] !== undefined && data[key] !== null && data[key] !== '') {
      if (key !== 'type' || props.kind === 'transactions' || props.kind === 'recurring') data[key] = Number(data[key])
    }
  }
  for (const key of ['categoryId', 'transactionId', 'endDate', 'dueDate', 'taxPeriod', 'dayOfWeek', 'dayOfMonth', 'monthOfYear', 'availableBalance', 'creditLimit', 'frozenAmount', 'openedAt']) if (data[key] === '' || data[key] === 0) delete data[key]
  if (props.kind === 'accounts') data.includeInNetWorth = Number(data.includeInNetWorth) === 1
  saving.value = true
  try {
    const id = props.record?.id
    if (props.kind === 'transactions') {
      if (id) await client.put(`/transactions/${id}`, { ...data, version: props.record?.version })
      else await client.post('/transactions', data, { headers: { 'Idempotency-Key': crypto.randomUUID() } })
    } else if (props.kind === 'budgets') {
      if (id) await client.put(`/budgets/${id}`, { ...data, version: props.record?.version })
      else await client.post('/budgets', data)
    } else if (props.kind === 'receipts') {
      if (id) await client.put(`/receipts/${id}`, { ...data, version: props.record?.version })
      else if (attachment.value) {
        const upload = new FormData()
        upload.append('file', attachment.value)
        for (const [key, value] of Object.entries({ ...data, companyId: Number(localStorage.getItem('activeCompanyId')) || undefined })) {
          if (value !== null && value !== undefined && String(value) !== '') upload.append(key, String(value))
        }
        await client.post('/receipts/upload', upload)
      } else await client.post('/receipts', { ...data, companyId: Number(localStorage.getItem('activeCompanyId')) || undefined })
    } else if (props.kind === 'accounts') {
      if (id) await client.put(`/accounts/${id}`, data)
      else await client.post('/accounts', data)
    } else if (props.kind === 'recurring') {
      if (id) await client.put(`/recurring/${id}`, data)
      else await client.post('/recurring', data)
    }
    ElMessage.success('保存成功')
    emit('saved')
  } catch (error) {
    ElMessage.error(props.kind === 'transactions' && axios.isAxiosError(error) && error.response?.status === 409
      ? '当前支出可能触及预算上限，请检查预算额度或关联账户'
      : '保存失败，请检查字段与当前权限')
  } finally { saving.value = false }
}
</script>

<template>
  <el-dialog :model-value="true" :title="title" width="780px" align-center class="editor-modal" @close="emit('cancel')">
    <form class="ed" @submit.prevent="save">
      <section v-for="group in groups" :key="group.name" class="ed-group">
        <h4>{{ group.name }}</h4>
        <div class="ed-grid">
          <label
            v-for="field in group.fields" :key="field.key" class="ed-field"
            :class="{ wide: field.type === 'textarea' || field.type === 'checkbox' }"
          >
            <span class="lb">{{ field.label }}<i v-if="field.required">*</i></span>

            <div v-if="field.key === 'type' && (kind === 'transactions' || kind === 'recurring')" class="ed-seg">
              <button
                v-for="opt in options(field)" :key="opt[0]" type="button"
                :class="{ on: String(form[field.key]) === opt[0] }" @click="form[field.key] = Number(opt[0])"
              >{{ opt[1] }}</button>
            </div>

            <div v-else-if="field.type === 'money'" class="ed-money">
              <span class="cur">¥</span>
              <input v-model="form[field.key]" type="number" min="0" step="0.01" placeholder="0.00" :required="field.required" />
            </div>

            <select v-else-if="field.type === 'select'" v-model="form[field.key]" :required="field.required">
              <option v-if="!field.required" value="">不指定</option>
              <option v-for="item in options(field)" :key="item[0]" :value="item[0]">{{ item[1] }}</option>
            </select>

            <textarea v-else-if="field.type === 'textarea'" v-model="form[field.key]" rows="2" maxlength="200" :placeholder="field.placeholder || '补充说明（可选）'" />

            <label v-else-if="field.type === 'checkbox'" class="ed-check">
              <input type="checkbox" :checked="Boolean(form[field.key])" @change="form[field.key] = ($event.target as HTMLInputElement).checked ? 1 : 0" />
              <span>是</span>
            </label>

            <input v-else v-model="form[field.key]" :type="field.type || 'text'" :placeholder="field.placeholder" :required="field.required" />

            <small v-if="field.hint" class="hint">{{ field.hint }}</small>
          </label>
        </div>
      </section>

      <label v-if="kind === 'receipts' && !record" class="ed-field wide ed-file">
        <span class="lb">附件</span>
        <input type="file" accept=".pdf,.png,.jpg,.jpeg" @change="attachment = ($event.target as HTMLInputElement).files?.[0] || null" />
        <small class="hint">支持 JPG、PNG、PDF，单个文件不超过 10 MB</small>
      </label>

      <div class="ed-foot">
        <button class="btn" type="button" @click="emit('cancel')">取消</button>
        <button class="btn pri" type="submit" :disabled="saving"><UiIcon name="check" :size="14" />{{ saving ? '正在保存…' : '保存' }}</button>
      </div>
    </form>
  </el-dialog>
</template>

<style scoped>
.ed{display:flex;flex-direction:column;gap:16px;max-height:66vh;overflow:auto;padding:2px 4px 4px}
.ed-group h4{font-size:11.5px;font-weight:600;color:var(--ink4);letter-spacing:.4px;margin-bottom:10px}
.ed-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px 18px}
.ed-field{display:flex;flex-direction:column;gap:6px;font-size:12px;color:var(--ink3);min-width:0}
.ed-field.wide{grid-column:span 2}
.lb{display:flex;align-items:center;gap:3px}
.lb i{color:var(--danger);font-style:normal}
.ed-field input[type=text],.ed-field input[type=number],.ed-field input[type=date],.ed-field select,.ed-field textarea{
  width:100%;height:42px;border:1px solid var(--line);border-radius:10px;padding:0 12px;font-size:13px;color:var(--ink);
  background:#fff;outline:none;font-family:inherit;transition:border-color .15s, box-shadow .15s}
.ed-field textarea{height:auto;padding:10px 12px;resize:vertical;line-height:1.6}
.ed-field input::placeholder,.ed-field textarea::placeholder{color:#a9b4c2}
.ed-field input:focus,.ed-field select:focus,.ed-field textarea:focus{border-color:#9fb1f2;box-shadow:0 0 0 3px rgba(54,89,227,.12)}
.ed-money{position:relative;display:flex;align-items:center}
.ed-money .cur{position:absolute;left:12px;font-size:13px;color:var(--ink4)}
.ed-money input{padding-left:28px !important;font-size:15px !important;font-weight:600;font-variant-numeric:tabular-nums}
.ed-seg{display:flex;gap:4px;background:#f2f5f9;border-radius:10px;padding:3px;width:max-content}
.ed-seg button{height:34px;padding:0 18px;border:none;border-radius:8px;background:transparent;color:var(--ink3);font-size:12.5px;cursor:pointer;font-family:inherit}
.ed-seg button.on{background:#fff;color:var(--brand);font-weight:600;box-shadow:0 1px 3px rgba(16,24,40,.12)}
.ed-check{display:flex;align-items:center;gap:8px;height:42px}
.ed-check input{width:16px;height:16px}
.hint{font-size:11px;color:var(--ink4)}
.ed-file{padding-top:2px}
.ed-file input[type=file]{height:40px;padding:6px;border:1px dashed var(--line);border-radius:10px;background:#fafbfe;width:100%}
.ed-foot{position:sticky;bottom:0;display:flex;justify-content:flex-end;gap:10px;padding:12px 0 2px;
  background:#fff;border-top:1px solid var(--line2)}
.ed-foot .btn{height:38px;padding:0 18px}
@media (max-width:720px){.ed-grid{grid-template-columns:1fr}.ed-field.wide{grid-column:auto}}
</style>
