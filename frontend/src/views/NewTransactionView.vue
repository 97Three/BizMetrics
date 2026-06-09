<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import client from '../api/client'
import UiIcon from '../components/ui/UiIcon.vue'
import type { Category } from '../types/category'
import type { Account } from '../types/account'
import type { CreateTransactionDTO } from '../types/transaction'
import { money } from '../utils/format'

const router = useRouter()
const form = ref<CreateTransactionDTO>({ type: 2, amount: 0, categoryId: 0, accountId: 0, date: new Date().toISOString().slice(0, 10), note: '' })
const categories = ref<Category[]>([])
const accounts = ref<Account[]>([])
const saving = ref(false)
const error = ref('')

const filteredCategories = computed(() => categories.value.filter((item) => item.type === (form.value.type === 1 ? 'income' : 'expense')))
const categoryName = computed(() => categories.value.find((c) => c.id === form.value.categoryId)?.name || '未选择')
const accountName = computed(() => accounts.value.find((a) => a.id === form.value.accountId)?.name || '未选择')
const typeLabel = computed(() => (form.value.type === 1 ? '收入' : '支出'))
const previewAmount = computed(() => {
  const value = Number(form.value.amount || 0)
  if (!value) return money(0)
  return `${form.value.type === 1 ? '+' : '−'}${money(value)}`
})

watch(() => form.value.type, () => { form.value.categoryId = 0 })

async function load() {
  try {
    const [c, a] = await Promise.all([client.get<Category[]>('/categories'), client.get<Account[]>('/accounts')])
    categories.value = c.data
    accounts.value = a.data
  } catch { error.value = '分类或账户加载失败，请检查后端连接。' }
}

async function save() {
  if (form.value.amount <= 0 || !form.value.categoryId || !form.value.accountId) {
    ElMessage.warning('请填写金额、分类和账户')
    return
  }
  saving.value = true
  try {
    await client.post('/transactions', form.value, { headers: { 'Idempotency-Key': crypto.randomUUID() } })
    ElMessage.success('流水已保存')
    await router.push('/transactions')
  } catch (e) {
    ElMessage.error(axios.isAxiosError(e) && e.response?.status === 409
      ? '当前支出可能触及预算上限，请检查预算额度或关联账户'
      : '流水保存失败，请检查字段和权限')
  } finally { saving.value = false }
}

onMounted(load)
</script>

<template>
  <div class="wrap newtx">
    <div class="ph">
      <div class="l">
        <div class="tile"><UiIcon name="plus" :size="20" /></div>
        <div><h1>录入流水</h1><p class="sub">记录一笔收入或支出，保存后可随时补充票据</p></div>
      </div>
      <RouterLink class="btn" to="/transactions">返回流水</RouterLink>
    </div>

    <div v-if="error" class="err" role="alert">{{ error }}</div>

    <div class="layout">
      <form class="main" @submit.prevent="save">
        <section class="card sec">
          <header><h3><span class="ic"><UiIcon name="wallet" :size="14" /></span>类型与金额</h3></header>
          <div class="body">
            <div class="type-seg">
              <button type="button" :class="{ on: form.type === 1 }" @click="form.type = 1">收入</button>
              <button type="button" :class="{ on: form.type === 2 }" @click="form.type = 2">支出</button>
            </div>
            <label class="fld amount">
              <span>金额（元）</span>
              <input v-model.number="form.amount" type="number" min="0.01" step="0.01" placeholder="0.00" required />
            </label>
          </div>
        </section>

        <section class="card sec">
          <header><h3><span class="ic"><UiIcon name="grid" :size="14" /></span>分类与账户</h3></header>
          <div class="body two">
            <label class="fld"><span>分类</span>
              <select v-model.number="form.categoryId" required>
                <option :value="0">请选择分类</option>
                <option v-for="item in filteredCategories" :key="item.id" :value="item.id">{{ item.name }}</option>
              </select>
            </label>
            <label class="fld"><span>账户</span>
              <select v-model.number="form.accountId" required>
                <option :value="0">请选择账户</option>
                <option v-for="item in accounts" :key="item.id" :value="item.id">{{ item.name }}</option>
              </select>
            </label>
          </div>
        </section>

        <section class="card sec">
          <header><h3><span class="ic"><UiIcon name="calendar" :size="14" /></span>日期与备注</h3></header>
          <div class="body two">
            <label class="fld"><span>发生日期</span><input v-model="form.date" type="date" required /></label>
            <label class="fld"><span>备注</span><input v-model="form.note" placeholder="这笔钱花在哪、从哪来" /></label>
          </div>
        </section>

        <div class="actions">
          <RouterLink class="btn" to="/transactions">取消</RouterLink>
          <button class="btn pri" type="submit" :disabled="saving">{{ saving ? '正在保存…' : '保存流水' }}</button>
        </div>
      </form>

      <aside class="side">
        <div class="card preview">
          <header><h3><span class="ic"><UiIcon name="doc" :size="14" /></span>保存预览</h3></header>
          <div class="pv">
            <span class="tag" :class="form.type === 1 ? 'in' : 'out'">{{ typeLabel }}</span>
            <div class="amount" :class="form.type === 1 ? 'in' : 'out'">{{ previewAmount }}</div>
            <dl>
              <div><dt>分类</dt><dd>{{ categoryName }}</dd></div>
              <div><dt>账户</dt><dd>{{ accountName }}</dd></div>
              <div><dt>日期</dt><dd>{{ form.date }}</dd></div>
              <div><dt>备注</dt><dd class="note">{{ form.note || '未填写' }}</dd></div>
            </dl>
          </div>
        </div>
        <div class="tip">
          <UiIcon name="alert" :size="14" />
          <p>保存后可在流水详情里关联票据、提交审批；支出超过预算时服务端会提示。</p>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.layout{display:grid;grid-template-columns:minmax(0,1fr) 328px;gap:16px;align-items:start}
.main{display:flex;flex-direction:column;gap:14px}
.sec{padding-bottom:18px}
.sec>header{height:56px;display:flex;align-items:center;padding:0 22px;border-bottom:1px solid var(--line2)}
.sec h3{font-size:14px;font-weight:650;display:flex;align-items:center;gap:9px}
.sec h3 .ic{width:26px;height:26px;border-radius:8px;background:#eef2ff;color:var(--brand);display:grid;place-items:center}
.body{display:grid;gap:14px;padding:18px 22px 0}
.body.two{grid-template-columns:1fr 1fr}
.type-seg{display:flex;gap:6px;background:#f2f5f9;border-radius:10px;padding:3px;width:max-content}
.type-seg button{height:32px;padding:0 18px;border:none;border-radius:8px;background:transparent;color:var(--ink3);font-size:12.5px;cursor:pointer;font-family:inherit}
.type-seg button.on{background:#fff;color:var(--brand);font-weight:600;box-shadow:0 1px 3px rgba(16,24,40,.12)}
.fld{display:flex;flex-direction:column;gap:6px;font-size:12px;color:var(--ink3)}
.fld input,.fld select{height:40px;border:1px solid var(--line);border-radius:9px;padding:0 12px;font-size:13px;color:var(--ink);outline:none;background:#fff;font-family:inherit;width:100%}
.fld input:focus,.fld select:focus{border-color:#9fb1f2;box-shadow:0 0 0 3px rgba(54,89,227,.12)}
.amount input{font-size:22px;font-weight:650;font-variant-numeric:tabular-nums;height:52px}
.actions{display:flex;justify-content:flex-end;gap:10px;padding:2px 0 8px}
.side{position:sticky;top:16px;display:flex;flex-direction:column;gap:12px}
.preview{padding-bottom:14px}
.preview>header{height:52px;display:flex;align-items:center;padding:0 20px;border-bottom:1px solid var(--line2)}
.preview h3{font-size:13.5px;font-weight:650;display:flex;align-items:center;gap:8px}
.preview h3 .ic{width:24px;height:24px;border-radius:7px;background:#eef2ff;color:var(--brand);display:grid;place-items:center}
.pv{padding:16px 20px 0}
.tag{display:inline-block;font-size:11px;padding:3px 9px;border-radius:6px;background:#f2f5f9;color:var(--ink3)}
.tag.in{background:#e6f7ef;color:#0a6b46}
.tag.out{background:#fdeaee;color:#b42318}
.pv .amount{font-size:28px;font-weight:650;font-variant-numeric:tabular-nums;letter-spacing:-.8px;margin:10px 0 14px}
.pv .amount.in{color:var(--green)}
.pv .amount.out{color:var(--red)}
.pv dl{display:flex;flex-direction:column;gap:0}
.pv dl div{display:flex;justify-content:space-between;gap:12px;padding:9px 0;border-bottom:1px solid var(--line2);font-size:12px}
.pv dl div:last-child{border-bottom:none}
.pv dt{color:var(--ink4)}
.pv dd{color:var(--ink);text-align:right;max-width:180px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.pv dd.note{color:var(--ink3)}
.tip{display:flex;gap:9px;padding:12px 14px;border-radius:12px;background:#f4f7fb;color:var(--ink3);font-size:11.5px;line-height:1.6}
.tip svg{flex:0 0 14px;margin-top:1px;color:var(--ink4)}
.err{padding:12px 16px;border-radius:12px;background:#fdeaee;color:#b42318;font-size:13px;margin-bottom:12px}
@media (max-width:1024px){.layout{grid-template-columns:1fr}.side{position:static}.body.two{grid-template-columns:1fr}}
</style>
