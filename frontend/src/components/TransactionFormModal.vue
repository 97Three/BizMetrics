<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import client from '../api/client'
import type { Category } from '../types/category'
import type { Account } from '../types/account'
import type { Transaction } from '../types/transaction'

const props = defineProps<{ record: Transaction | null }>()
const emit = defineEmits<{ saved: []; cancel: [] }>()

const categories = ref<Category[]>([])
const accounts = ref<Account[]>([])
const saving = ref(false)
const type = ref<1 | 2>(2)
const amount = ref<string>('')
const categoryId = ref<number>(0)
const accountId = ref<number>(0)
const date = ref(new Date().toISOString().slice(0, 10))
const note = ref('')

const isExpense = computed(() => type.value === 2)
const accent = computed(() => (isExpense.value ? '#f04438' : '#12b76a'))
const accentGrad = computed(() => (isExpense.value ? 'linear-gradient(180deg,#f4554a,#e5382f)' : 'linear-gradient(180deg,#17c07a,#0e9f6e)'))
const list = computed(() => categories.value.filter((c) => c.type === (isExpense.value ? 'expense' : 'income')))
const amountNumber = computed(() => Number(amount.value || 0))
const canSubmit = computed(() => amountNumber.value > 0 && categoryId.value > 0 && accountId.value > 0 && !!date.value)

const quickNotes = computed(() => (isExpense.value
  ? ['客户退款：冲减收入', '离职补偿：N+1 经济补偿', '办公采购：日常耗材']
  : ['客户回款：项目阶段款', '预收款：下季度服务', '退款冲回：原路退回']))

async function load() {
  try {
    const [c, a] = await Promise.all([client.get<Category[]>('/categories'), client.get<Account[]>('/accounts')])
    categories.value = c.data
    accounts.value = a.data
  } catch { ElMessage.warning('分类或账户加载失败') }
}

function hydrate() {
  const r = props.record
  if (r) {
    type.value = r.type === 1 ? 1 : 2
    amount.value = String(r.amount ?? '')
    categoryId.value = r.categoryId
    accountId.value = r.accountId
    date.value = (r.date || '').slice(0, 10) || new Date().toISOString().slice(0, 10)
    note.value = r.note || ''
  }
}
watch(() => props.record, hydrate, { immediate: true })
watch(type, () => { if (!list.value.some((c) => c.id === categoryId.value)) categoryId.value = 0 })
onMounted(async () => { await load(); hydrate() })

async function submit() {
  if (!canSubmit.value) { ElMessage.warning('请填写金额、分类和账户'); return }
  saving.value = true
  try {
    const payload = {
      type: type.value,
      amount: amountNumber.value,
      categoryId: categoryId.value,
      accountId: accountId.value,
      date: date.value,
      note: note.value,
    }
    if (props.record) await client.put(`/transactions/${props.record.id}`, { ...payload, version: props.record.version })
    else await client.post('/transactions', payload, { headers: { 'Idempotency-Key': crypto.randomUUID() } })
    ElMessage.success(props.record ? '流水已更新' : '流水已记录')
    emit('saved')
  } catch (error) {
    ElMessage.error(axios.isAxiosError(error) && error.response?.status === 409
      ? '当前支出可能触及预算上限，请检查预算额度或关联账户'
      : '保存失败，请检查字段与权限')
  } finally { saving.value = false }
}
</script>

<template>
  <el-dialog :model-value="true" :show-close="true" width="760px" align-center class="tx-modal"
             :title="record ? '编辑流水' : '录入流水'" @close="emit('cancel')">
    <form class="txf" @submit.prevent="submit">
      <div class="type-toggle" :style="{ '--accent': accent, '--accent-grad': accentGrad }">
        <button type="button" :class="{ on: isExpense }" @click="type = 2">💸 成本支出</button>
        <button type="button" :class="{ on: !isExpense }" @click="type = 1">💰 收入</button>
      </div>

      <label class="fld">
        <span class="lb">金额 <i>*</i></span>
        <div class="amount" :style="{ '--accent': accent }">
          <span class="sign">{{ isExpense ? '−' : '+' }}</span>
          <input v-model="amount" type="number" min="0" step="0.01" placeholder="0.00" inputmode="decimal" autofocus />
        </div>
      </label>

      <div class="fld">
        <span class="lb">分类 <i>*</i></span>
        <div class="cat-grid">
          <button
            v-for="cat in list" :key="cat.id" type="button" class="cat"
            :class="{ on: categoryId === cat.id }" :style="{ '--accent': accent }"
            @click="categoryId = cat.id"
          >
            <span class="ico">{{ cat.icon || '•' }}</span>
            <span class="nm">{{ cat.name }}</span>
          </button>
          <p v-if="!list.length" class="cat-empty">当前类型还没有分类，请先在设置里添加</p>
        </div>
      </div>

      <div class="two">
        <label class="fld">
          <span class="lb">账户 <i>*</i></span>
          <select v-model.number="accountId">
            <option :value="0">选择账户</option>
            <option v-for="a in accounts" :key="a.id" :value="a.id">{{ a.name }}</option>
          </select>
        </label>
        <label class="fld">
          <span class="lb">日期 <i>*</i></span>
          <input v-model="date" type="date" />
        </label>
      </div>

      <label class="fld">
        <span class="lb">备注</span>
        <div class="note-wrap">
          <textarea v-model="note" rows="3" maxlength="200" placeholder="添加备注（可选）" />
          <span class="counter">{{ note.length }}/200</span>
        </div>
        <div class="chips">
          <button v-for="q in quickNotes" :key="q" type="button" @click="note = q">{{ q }}</button>
        </div>
      </label>

      <div class="foot">
        <button class="btn ghost" type="button" @click="emit('cancel')">取消</button>
        <button class="btn solid" type="submit" :disabled="saving" :style="{ background: accentGrad }">
          {{ saving ? '保存中…' : (record ? '保存修改' : isExpense ? '记录成本' : '记录收入') }}
        </button>
      </div>
    </form>
  </el-dialog>
</template>

<style scoped>
.txf{display:flex;flex-direction:column;gap:18px;padding:2px 4px 0}
.lb{font-size:12px;color:var(--ink3);display:flex;gap:2px;margin-bottom:8px}
.lb i{color:var(--danger);font-style:normal}
.fld{display:flex;flex-direction:column}
.type-toggle{display:grid;grid-template-columns:1fr 1fr;gap:10px}
.type-toggle button{height:52px;border:1px solid var(--line);border-radius:12px;background:#fff;color:var(--ink3);
  font-size:15px;font-weight:600;cursor:pointer;font-family:inherit;transition:all .15s}
.type-toggle button.on{background:var(--accent-grad);border-color:transparent;color:#fff;
  box-shadow:0 10px 22px -10px var(--accent)}
.amount{display:flex;align-items:center;gap:8px;height:56px;border:1px solid var(--line);border-radius:12px;padding:0 14px;background:#fff}
.amount:focus-within{border-color:var(--accent);box-shadow:0 0 0 3px color-mix(in srgb, var(--accent) 18%, transparent)}
.amount .sign{font-size:22px;font-weight:600;color:var(--accent)}
.amount input{flex:1;border:none;outline:none;font-size:22px;font-weight:650;letter-spacing:-.4px;
  font-variant-numeric:tabular-nums;color:var(--ink);background:transparent;font-family:inherit}
.cat-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:8px}
.cat{display:flex;flex-direction:column;align-items:center;gap:6px;padding:14px 8px;border:1px solid transparent;border-radius:12px;
  background:#f5f7fa;cursor:pointer;font-family:inherit;transition:all .15s}
.cat:hover{background:#eef2f9}
.cat.on{background:#fff;border-color:var(--accent);box-shadow:0 6px 16px -10px var(--accent)}
.cat .ico{font-size:20px;line-height:1}
.cat .nm{font-size:12px;color:var(--ink2);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:100%}
.cat.on .nm{color:var(--accent);font-weight:600}
.cat-empty{grid-column:span 4;font-size:12px;color:var(--ink4);padding:10px 0}
.two{display:grid;grid-template-columns:1fr 1fr;gap:14px}
.fld select,.fld input[type=date]{height:44px;border:1px solid var(--line);border-radius:11px;padding:0 12px;font-size:13px;
  color:var(--ink);background:#fff;outline:none;font-family:inherit}
.fld select:focus,.fld input[type=date]:focus{border-color:#9fb1f2;box-shadow:0 0 0 3px rgba(54,89,227,.12)}
.note-wrap{position:relative}
.note-wrap textarea{width:100%;border:1px solid var(--line);border-radius:11px;padding:11px 12px 22px;font-size:13px;
  color:var(--ink);background:#fff;outline:none;resize:vertical;line-height:1.6;font-family:inherit}
.note-wrap textarea:focus{border-color:#9fb1f2;box-shadow:0 0 0 3px rgba(54,89,227,.12)}
.counter{position:absolute;right:12px;bottom:8px;font-size:10.5px;color:var(--ink4)}
.chips{display:flex;gap:8px;flex-wrap:wrap;margin-top:8px}
.chips button{height:28px;padding:0 12px;border-radius:999px;border:1px solid var(--line);background:#fff;
  color:var(--ink3);font-size:11.5px;cursor:pointer;font-family:inherit}
.chips button:hover{border-color:#c8d2f8;color:var(--brand);background:#f5f8ff}
.foot{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-top:4px}
.btn{height:46px;border-radius:11px;font-size:14px;font-weight:600;cursor:pointer;font-family:inherit;border:1px solid var(--line)}
.btn.ghost{background:#fff;color:var(--ink2)}
.btn.solid{border:none;color:#fff;box-shadow:0 10px 22px -12px rgba(240,68,56,.6)}
.btn.solid:disabled{opacity:.6}
@media (max-width:720px){.cat-grid{grid-template-columns:repeat(3,1fr)}.two{grid-template-columns:1fr}}
</style>
