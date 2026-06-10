<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import client from '../api/client'
import type { Page } from '../api/ledgers'
import type { ApprovalDetail, ApprovalRequest, ApprovalSummary } from '../types/approval'
import type { Department, Employee, EnterpriseSummary, PayrollRun, TaxItem, TaxItemPayload } from '../types/enterprise'
import type { User } from '../types/user'
import { money, date } from '../utils/format'
import MetricCard from '../components/ui/MetricCard.vue'
import UiIcon from '../components/ui/UiIcon.vue'

type Kind='tax'|'approvals'|'organization'|'benefits'|'performance'|'compensation'|'users'
type Row=Record<string,unknown>
const props=defineProps<{kind:Kind}>()
const config:Record<Kind,{title:string;subtitle:string;columns:Array<[string,string]>}>={
  tax:{title:'税务管理',subtitle:'税项、应缴余额和申报状态',columns:[['name','税项'],['period','所属期'],['taxType','税种'],['taxAmount','应缴'],['paidAmount','已缴'],['dueDate','截止日'],['status','状态']]},
  approvals:{title:'审批事项',subtitle:'待处理、已办与我发起的申请',columns:[['title','事项'],['requestType','类型'],['amount','金额'],['createdAt','提交时间'],['status','状态']]},
  organization:{title:'组织与人员',subtitle:'部门结构与员工档案',columns:[['name','姓名'],['employeeNo','员工编号'],['departmentName','部门'],['status','状态'],['employmentType','用工类型'],['email','邮箱']]},
  benefits:{title:'人才福利参考台',subtitle:'根据员工档案检查基础字段；不代表政策资格认定',columns:[['name','姓名'],['departmentName','部门'],['educationLevel','学历'],['socialInsuranceRegion','参保地区'],['socialInsuranceBase','社保基数'],['profileVerifiedAt','档案复核']]},
  performance:{title:'绩效周期准备台',subtitle:'当前后端没有绩效记录；此页只核对员工基础字段',columns:[['name','姓名'],['departmentName','部门'],['status','状态'],['position','岗位'],['email','邮箱'],['skillTags','技能标签']]},
  compensation:{title:'薪酬月结',subtitle:'薪酬运行记录与月结状态',columns:[['period','月份'],['name','名称'],['employeeCount','人数'],['salaryTotal','应发'],['taxTotal','个税'],['netPayTotal','实发'],['status','状态']]},
  users:{title:'成员与权限',subtitle:'管理登录成员及角色',columns:[['nickname','姓名'],['email','邮箱'],['role','角色'],['permissions','权限位'],['createdAt','创建时间']]},
}
const heading=computed(()=>config[props.kind])
const rows=ref<Row[]>([])
const departments=ref<Department[]>([])
const enterprise=ref<EnterpriseSummary|null>(null)
const approvalSummary=ref<ApprovalSummary|null>(null)
const selected=ref<Row|null>(null)
const approvalActions=ref<ApprovalDetail['actions']>([])
const loading=ref(false)
const error=ref('')
const keyword=ref('')
const orgDept=ref('')
const tab=ref('')
const page=ref(1)
const size=ref(20)
const total=ref(0)
const taxForm=ref(false)
const taxEditingId=ref<number|null>(null)
const taxDraft=ref<TaxItemPayload>({name:'',period:'',taxType:'',taxableAmount:0,taxAmount:0,paidAmount:0,deductibleAmount:0,taxRate:0,dueDate:'',status:'pending'})
const organizationForm=ref(false)
const departmentDraft=ref({name:'',costCenter:'',budget:0,managerEmployeeId:0,status:1})
const employeeDraft=ref({employeeNo:'',name:'',email:'',phone:'',position:'',jobLevel:'',workLocation:'',departmentId:0,employmentType:'full_time',status:'active',hireDate:new Date().toISOString().slice(0,10),contractType:'',salary:0,socialInsurance:0,housingFund:0})
const payrollPeriod=ref(new Date().toISOString().slice(0,7))
let requestId=0
const filtered=computed(()=>rows.value.filter(row=>{
  const text=keyword.value.trim().toLocaleLowerCase()
  if(text && !Object.values(row).some(value=>String(value??'').toLocaleLowerCase().includes(text))) return false
  if(tab.value && String(row.status??'')!==tab.value) return false
  if(props.kind==='organization' && orgDept.value && String(row.departmentName??'')!==orgDept.value) return false
  return true
}))
const localPage=computed(()=>props.kind!=='approvals'&&props.kind!=='users')
const visible=computed(()=>localPage.value?filtered.value.slice((page.value-1)*size.value,page.value*size.value):filtered.value)
const count=computed(()=>localPage.value?filtered.value.length:total.value)
const drawerOpen=computed({get:()=>selected.value!==null,set(value:boolean){if(!value)selected.value=null}})
const metrics=computed(()=>{
  if(props.kind==='tax'){const data=rows.value as unknown as TaxItem[];return [['应缴税额',money(data.reduce((sum,item)=>sum+item.taxAmount,0))],['已缴税额',money(data.reduce((sum,item)=>sum+item.paidAmount,0))],['待缴税额',money(data.reduce((sum,item)=>sum+Math.max(0,item.taxAmount-item.paidAmount),0))],['税项数',data.length]]}
  if(props.kind==='approvals'){const s=approvalSummary.value;return [['待审批',s?.pending??'—'],['我待审批',s?.minePending??'—'],['已通过',s?.approved??'—'],['已驳回',s?.rejected??'—']]}
  if(props.kind==='organization')return [['部门数',departments.value.length],['员工数',enterprise.value?.employeeCount??'—'],['在职人数',enterprise.value?.activeEmployeeCount??'—']]
  if(props.kind==='benefits'){const data=rows.value as unknown as Employee[];return [['在职员工',data.length],['有参保地区',data.filter(x=>x.socialInsuranceRegion).length],['有社保基数',data.filter(x=>x.socialInsuranceBase>0).length],['已人工复核',data.filter(x=>x.profileVerifiedAt).length]]}
  if(props.kind==='performance'){const data=rows.value as unknown as Employee[];return [['参与准备',data.length],['有部门',data.filter(x=>x.departmentId).length],['有岗位',data.filter(x=>x.position).length],['有技能记录',data.filter(x=>x.skillTags).length]]}
  if(props.kind==='compensation'){const data=rows.value as unknown as PayrollRun[];return [['月结批次',data.length],['已关闭',data.filter(x=>x.status==='closed').length],['薪资合计',money(data.reduce((n,x)=>n+x.salaryTotal,0))],['公司成本',money(data.reduce((n,x)=>n+x.companyCostTotal,0))]]}
  return [['成员数',count.value],['管理员',rows.value.filter(x=>x.role===1).length]]
})
const metricCards=computed(()=>{
  const icons=['tax','check','users','doc','trend','wallet']
  const tones=['green','red','brand','ink','amber','ink'] as const
  return metrics.value.map((item,i)=>{
    const raw=String(item[1]??'—')
    return { label:String(item[0]), value:raw.startsWith('¥')?raw.slice(1):raw, currency:raw.startsWith('¥')?'¥':undefined,
      tone:tones[i%tones.length], icon:icons[i%icons.length] }
  })
})
const pageIcon=computed(()=>({tax:'tax',approvals:'check',organization:'users',benefits:'shield',performance:'spark',compensation:'wallet',users:'users'} as Record<string,string>)[props.kind]||'grid')
function render(row:Row,key:string){const value=row[key];if(value==null||value==='')return '—';if(['amount','taxAmount','paidAmount','taxableAmount','socialInsuranceBase','salaryTotal','taxTotal','netPayTotal'].includes(key))return money(Number(value));if(key.endsWith('At')||key.endsWith('Date'))return date(String(value));if(key==='role')return value===1?'管理员':'普通成员';return String(value)}
const company=()=>Number(localStorage.getItem('activeCompanyId'))||undefined
async function load(){
  const current=++requestId;loading.value=true;error.value=''
  try {
    if(props.kind==='tax'){const [s,t]=await Promise.all([client.get<EnterpriseSummary>('/enterprise/summary',{params:{companyId:company()}}),client.get<TaxItem[]>('/enterprise/tax-items',{params:{companyId:company()}})]);if(current===requestId){enterprise.value=s.data;rows.value=t.data as unknown as Row[]}}
    if(props.kind==='approvals'){const [l,s]=await Promise.all([client.get<Page<ApprovalRequest>>('/approvals',{params:{page:page.value-1,size:size.value,keyword:keyword.value||undefined,status:tab.value||undefined}}),client.get<ApprovalSummary>('/approvals/summary')]);if(current===requestId){rows.value=l.data.content as unknown as Row[];total.value=l.data.totalElements;approvalSummary.value=s.data}}
    if(['organization','benefits','performance'].includes(props.kind)){const [s,d,e]=await Promise.all([client.get<EnterpriseSummary>('/enterprise/summary',{params:{companyId:company()}}),client.get<Department[]>('/enterprise/departments',{params:{companyId:company()}}),client.get<Employee[]>('/enterprise/employees',{params:{companyId:company(),status:props.kind==='benefits'?'active':undefined}})]);if(current===requestId){enterprise.value=s.data;departments.value=d.data;rows.value=e.data as unknown as Row[]}}
    if(props.kind==='compensation'){const runs=await client.get<PayrollRun[]>('/payroll-runs',{params:{companyId:company()}});if(current===requestId)rows.value=runs.data as unknown as Row[]}
    if(props.kind==='users'){const result=await client.get<{content:User[];totalElements:number}>('/admin/users',{params:{keyword:keyword.value||undefined,page:page.value-1,size:size.value}});if(current===requestId){rows.value=result.data.content as unknown as Row[];total.value=result.data.totalElements}}
  }catch{if(current===requestId){rows.value=[];error.value='无法加载数据，请检查后端服务与当前账号权限。'}}
  finally{if(current===requestId)loading.value=false}
}
async function open(row:Row){selected.value=row;approvalActions.value=[];if(props.kind==='approvals')try{const result=await client.get<ApprovalDetail>(`/approvals/${row.id}`);if(selected.value?.id===row.id)approvalActions.value=result.data.actions}catch{ElMessage.warning('审批记录加载失败')}}
async function decide(action:'approve'|'reject'){
  if(!selected.value)return
  const id=Number(selected.value.id)
  const comment=await ElMessageBox.prompt(action==='reject'?'请填写驳回原因':'可填写审批意见',action==='reject'?'驳回申请':'通过申请',{inputValidator:value=>action==='approve'||Boolean(value.trim()),inputErrorMessage:'请填写原因'}).then(result=>result.value).catch(()=>null)
  if(comment===null)return
  try{await client.post(`/approvals/${id}/${action}`,{comment});selected.value=null;ElMessage.success('审批已处理');await load()}catch{ElMessage.error('审批操作失败，请刷新后重试')}
}
async function saveTax(){
  if(!taxDraft.value.name||!taxDraft.value.period||!taxDraft.value.taxType||!taxDraft.value.dueDate){ElMessage.warning('请填写必填字段');return}
  try{if(taxEditingId.value)await client.put(`/enterprise/tax-items/${taxEditingId.value}`,taxDraft.value);else await client.post('/enterprise/tax-items',{...taxDraft.value,companyId:company()});taxForm.value=false;taxEditingId.value=null;ElMessage.success('税项已保存');await load()}catch{ElMessage.error('保存税项失败')}
}
function editTax(){if(!selected.value)return;const row=selected.value as unknown as TaxItem;taxEditingId.value=row.id;taxDraft.value={name:row.name,period:row.period,taxType:row.taxType,taxableAmount:row.taxableAmount,taxAmount:row.taxAmount,paidAmount:row.paidAmount,dueDate:row.dueDate,status:row.status};selected.value=null;taxForm.value=true}
async function deleteTax(){if(!selected.value)return;const id=Number(selected.value.id);try{await ElMessageBox.confirm('删除税项后无法恢复，确认删除？','删除税项',{type:'warning'})}catch{return}try{await client.delete(`/enterprise/tax-items/${id}`);selected.value=null;await load();ElMessage.success('税项已删除')}catch{ElMessage.error('删除失败')}}
async function saveDepartment(){const d=departmentDraft.value;if(!d.name.trim())return;try{await client.post('/enterprise/departments',{...d,managerEmployeeId:d.managerEmployeeId||null,status:Number(d.status),companyId:company()});departmentDraft.value={name:'',costCenter:'',budget:0,managerEmployeeId:0,status:1};organizationForm.value=false;await load();ElMessage.success('部门已创建')}catch{ElMessage.error('部门创建失败')}}
async function saveEmployee(){const d=employeeDraft.value;if(!d.name||!d.email||!d.position)return;try{await client.post('/enterprise/employees',{...d,companyId:company(),departmentId:d.departmentId||null,managerEmployeeId:null,accessRole:'employee',accessScope:'self',taxEstimate:0,salary:Number(d.salary||0),socialInsurance:Number(d.socialInsurance||0),housingFund:Number(d.housingFund||0)});employeeDraft.value={employeeNo:'',name:'',email:'',phone:'',position:'',jobLevel:'',workLocation:'',departmentId:0,employmentType:'full_time',status:'active',hireDate:new Date().toISOString().slice(0,10),contractType:'',salary:0,socialInsurance:0,housingFund:0};organizationForm.value=false;await load();ElMessage.success('员工已创建')}catch{ElMessage.error('员工创建失败，请检查必填字段')}}
async function createPayroll(){try{await ElMessageBox.confirm(`为 ${payrollPeriod.value} 创建薪酬批次？`,'生成月结',{type:'warning'});await client.post('/payroll-runs',{companyId:company(),period:payrollPeriod.value});await load();ElMessage.success('月结批次已创建')}catch{/* Cancelled or request failed */}}
async function changeRole(role:number){if(!selected.value)return;const id=Number(selected.value.id);try{await ElMessageBox.confirm(`将该成员角色改为${role===1?'管理员':'普通成员'}？`,'修改角色',{type:'warning'});await client.put(`/admin/users/${id}`,{role});selected.value=null;await load();ElMessage.success('角色已更新')}catch{/* Cancelled or request failed */}}
async function closePayroll(){
  if(!selected.value)return
  const id=Number(selected.value.id)
  try{await ElMessageBox.confirm('关闭后将锁定本批次。确认继续？','关闭月结',{type:'warning'});await client.post(`/payroll-runs/${id}/close`);selected.value=null;await load();ElMessage.success('月结已关闭')}catch{/* User cancelled or request failed; source status remains unchanged */}
}
const inviteOpen = ref(false)
const inviting = ref(false)
const invites = ref<Array<{ id: number; email: string; role: number; expiresAt: string; acceptedUserId?: number | null }>>([])
const inviteDraft = ref({ email: '', role: 2, permissions: 1, expiresInDays: 7 })

async function loadInvites() {
  if (props.kind !== 'users') return
  try {
    const res = await client.get<typeof invites.value>('/auth/invitations')
    invites.value = res.data || []
  } catch { invites.value = [] }
}
async function sendInvite() {
  const email = inviteDraft.value.email.trim()
  if (!email) { ElMessage.warning('请填写受邀人邮箱'); return }
  inviting.value = true
  try {
    await client.post('/auth/invitations', {
      email, role: Number(inviteDraft.value.role), permissions: Number(inviteDraft.value.permissions),
      expiresInDays: Number(inviteDraft.value.expiresInDays),
    })
    inviteOpen.value = false
    inviteDraft.value = { email: '', role: 2, permissions: 1, expiresInDays: 7 }
    ElMessage.success('邀请已创建，请把邀请链接发给对方')
    await loadInvites()
  } catch { ElMessage.error('邀请失败，请检查邮箱是否已存在') } finally { inviting.value = false }
}
const PERMS = [
  { bit: 1, label: '流水' },
  { bit: 2, label: '账户' },
  { bit: 4, label: '分类' },
  { bit: 8, label: '预算' },
]
function userMask(row: Row) { return Number((row as unknown as User).permissions || 0) }
function userPermChips(row: Row) {
  const mask = userMask(row)
  return PERMS.map((p) => ({ text: p.label, on: (mask & p.bit) !== 0 }))
}
async function saveAccess(role: number, mask: number) {
  if (!selected.value) return
  const id = Number(selected.value.id)
  try {
    await client.put(`/admin/users/${id}`, { role, permissions: mask })
    selected.value = { ...(selected.value as Record<string, unknown>), role, permissions: mask }
    await load()
    ElMessage.success('权限已更新')
  } catch { ElMessage.error('权限更新失败，请检查管理员权限') }
}
function togglePermission(bit: number) {
  if (!selected.value) return
  const row = selected.value as unknown as User
  const mask = userMask(selected.value)
  const next = (mask & bit) !== 0 ? mask & ~bit : mask | bit
  void saveAccess(Number(row.role), next)
}
function deptCount(name: string) {
  return (rows.value as unknown as Employee[]).filter((e) => String(e.departmentName || '') === name).length
}
const employeeOptions = computed(() => (rows.value as unknown as Employee[]).filter((e) => e.name))
const deptOptions = computed(() => departments.value.filter((d) => deptCount(d.name) > 0))
function search(){page.value=1;void load()}
function pageChange(value:number){page.value=value;if(!localPage.value)void load()}
watch(()=>props.kind,()=>{page.value=1;keyword.value='';tab.value='';void load();void loadInvites()})
onMounted(()=>{void load();void loadInvites()})
</script>
<template><div class="wrap ledger-page"><div class="ph"><div class="l"><div class="tile"><UiIcon :name="pageIcon" :size="20" /></div><div><h1>{{ heading.title }}</h1><p class="sub">{{ heading.subtitle }}</p></div></div><div class="acts"><button class="btn" type="button" @click="load">刷新</button><button v-if="kind==='tax'" class="btn pri" type="button" @click="taxEditingId=null;taxDraft={name:'',period:'',taxType:'',taxableAmount:0,taxAmount:0,dueDate:'',status:'pending'};taxForm=true">新增税项</button><button v-if="kind==='organization'" class="btn pri" type="button" @click="organizationForm=true">新增部门或员工</button><label v-if="kind==='compensation'" class="inline-period">月份<input v-model="payrollPeriod" type="month"></label><button v-if="kind==='compensation'" class="btn pri" type="button" @click="createPayroll">生成月结</button><button v-if="kind==='users'" class="btn pri" type="button" @click="inviteOpen=true">邀请成员</button></div></div>
<div class="ledger-metrics"><MetricCard v-for="item in metricCards" :key="item.label" v-bind="item" /></div>
<div v-if="kind==='performance'||kind==='benefits'" class="notice-card">{{ kind==='performance'?'目前后端没有绩效周期、目标和评分记录。以下仅展示员工档案的准备情况，不生成绩效结论。':'以下统计只说明员工资料是否填写，不代表已获得补贴资格或完成政策审核。' }}</div>
<div v-if="kind==='organization'" class="dept-strip"><span class="lb">部门</span><button type="button" :class="{ on: !orgDept }" @click="orgDept='';page=1">全部 <b>{{ rows.length }}</b></button><button v-for="d in deptOptions" :key="d.id" type="button" :class="{ on: orgDept === d.name }" @click="orgDept=d.name;page=1">{{ d.name }} <b>{{ deptCount(d.name) }}</b></button><p v-if="!deptOptions.length" class="dept-empty">暂无部门数据</p></div><div v-if="kind==='users'" class="users-extra">
        <section class="card">
          <header><h3><span class="ic"><UiIcon name="shield" :size="14" /></span>权限矩阵</h3><span class="hint">点击即时保存</span></header>
          <div class="matrix">
            <div class="mrow mhead"><span>成员</span><span v-for="p in PERMS" :key="p.bit">{{ p.label }}</span></div>
            <div v-for="member in rows" :key="String(member.id)" class="mrow">
              <span class="mname" :title="String(member.nickname || member.email)">{{ member.nickname || member.email }}</span>
              <button v-for="p in PERMS" :key="p.bit" type="button" class="mcell"
                :class="{ on: (userMask(member) & p.bit) !== 0 }"
                :disabled="Number(member.role) === 1"
                @click="selected = member; togglePermission(p.bit)">{{ (userMask(member) & p.bit) !== 0 ? '✓' : '—' }}</button>
            </div>
            <p v-if="!rows.length" class="mempty">暂无成员</p>
          </div>
        </section>
        <section class="card">
          <header><h3><span class="ic"><UiIcon name="doc" :size="14" /></span>待接受邀请</h3><span class="hint">{{ invites.length }} 条</span></header>
          <div class="invs">
            <div v-for="inv in invites" :key="inv.id" class="inv">
              <b>{{ inv.email }}</b>
              <small>{{ inv.role === 1 ? '管理员' : '普通成员' }} · {{ String(inv.expiresAt).slice(0, 10) }} 到期</small>
            </div>
            <p v-if="!invites.length" class="mempty">还没有待接受的邀请</p>
          </div>
        </section>
      </div>
      <section class="ledger-card"><form class="ledger-filters" @submit.prevent="search"><div v-if="kind==='approvals'" class="ledger-segments"><button v-for="item in [{label:'全部',value:''},{label:'待处理',value:'pending'},{label:'已通过',value:'approved'},{label:'已驳回',value:'rejected'}]" :key="item.value" type="button" :class="{active:tab===item.value}" @click="tab=item.value;search()">{{ item.label }}</button></div><div v-else class="ledger-segments"><span>{{ enterprise?.company.name || heading.title }}</span></div><div class="ledger-search"><input v-model="keyword" type="search" placeholder="搜索当前数据" aria-label="搜索"><button class="btn" type="submit">筛选</button></div></form>
<div v-if="error" class="ledger-state" role="alert">{{ error }} <button class="btn" type="button" @click="load">重试</button></div><div v-else class="ledger-table"><el-table v-loading="loading" :data="visible" row-key="id" @row-click="open"><template #empty><div class="empty"><UiIcon name="list" :size="26" /><p>暂无数据</p><small>当前主体下还没有相关记录</small><el-dialog v-model="inviteOpen" title="邀请成员" width="560px" align-center><form class="ed-grid form-grid" @submit.prevent="sendInvite">
<label class="fld"><span>受邀人邮箱 <i>*</i></span><input v-model="inviteDraft.email" type="email" required placeholder="name@company.com"></label>
<label class="fld"><span>角色</span><select v-model.number="inviteDraft.role"><option :value="2">普通成员</option><option :value="1">管理员</option></select></label>
<label class="fld"><span>数据权限</span><select v-model.number="inviteDraft.permissions"><option :value="1">仅流水</option><option :value="3">流水 + 账户</option><option :value="7">流水 + 账户 + 分类</option><option :value="15">全部（含预算）</option></select></label>
<label class="fld"><span>有效期（天）</span><input v-model.number="inviteDraft.expiresInDays" type="number" min="1" max="30" placeholder="7"></label>
<div class="form-foot"><button class="btn" type="button" @click="inviteOpen=false">取消</button><button class="btn pri" type="submit" :disabled="inviting">{{ inviting ? '发送中…' : '创建邀请' }}</button></div>
</form></el-dialog>
</div></template><el-table-column v-for="column in heading.columns" :key="column[0]" :label="column[1]" min-width="145"><template #default="scope">{{ render(scope.row,column[0]) }}</template></el-table-column><el-table-column v-if="kind==='users'" label="数据权限" width="210"><template #default="scope"><div class="tags"><span v-for="chip in userPermChips(scope.row as Row)" :key="chip.text" class="tg" :class="chip.on ? 'green' : 'ink'">{{ chip.text }}</span></div></template></el-table-column><el-table-column label="操作" width="94" fixed="right"><template #default="scope"><button class="detail-link" type="button" @click.stop="open(scope.row)">查看详情</button></template></el-table-column></el-table></div><footer class="ledger-footer"><span>共 {{ count }} 项</span><el-pagination background layout="prev, pager, next" :page-size="size" :current-page="page" :total="count" @current-change="pageChange"/></footer></section>
<el-drawer v-model="drawerOpen" :title="heading.title+'详情'" size="480px"><template v-if="selected"><dl class="detail-fields"><div v-for="column in heading.columns" :key="column[0]"><dt>{{ column[1] }}</dt><dd>{{ render(selected,column[0]) }}</dd></div></dl><section v-if="kind==='approvals'" class="audit-logs"><h3>处理记录</h3><p v-for="action in approvalActions" :key="action.id">{{ date(action.createdAt) }} · {{ action.action }} · {{ action.comment||'无备注' }}</p><div v-if="selected.status==='pending'" class="drawer-actions"><button class="btn" type="button" @click="decide('reject')">驳回</button><button class="btn pri" type="button" @click="decide('approve')">通过</button></div></section><div v-if="kind==='tax'" class="drawer-actions"><button class="btn" type="button" @click="editTax">编辑</button><button class="btn" type="button" @click="deleteTax">删除</button></div><div v-if="kind==='users'" class="perm"><h4>权限矩阵</h4><div class="perm-row"><span>角色</span><div class="seg2"><button type="button" :class="{on: Number((selected as unknown as User).role) === 1}" @click="saveAccess(1, userMask(selected as Row))">管理员</button><button type="button" :class="{on: Number((selected as unknown as User).role) !== 1}" @click="saveAccess(2, userMask(selected as Row))">普通成员</button></div></div><div class="perm-row"><span>数据权限</span><div class="chips"><button v-for="p in PERMS" :key="p.bit" type="button" :class="{on: (userMask(selected as Row) & p.bit) !== 0}" @click="togglePermission(p.bit)">{{ p.label }}</button></div></div><p class="perm-tip">点击即保存；管理员默认拥有全部权限。</p></div><div v-if="kind==='users'" class="drawer-actions"><button class="btn" type="button" @click="changeRole(2)">设为普通成员</button><button class="btn pri" type="button" @click="changeRole(1)">设为管理员</button></div><div v-if="kind==='compensation'&&selected.status==='draft'" class="drawer-actions"><button class="btn pri" type="button" @click="closePayroll">关闭月结</button></div></template></el-drawer>
<el-dialog v-model="taxForm" title="新增税项" width="720px" align-center><form class="ed-grid form-grid" @submit.prevent="saveTax">
<label class="fld"><span>税项名称 <i>*</i></span><input v-model="taxDraft.name" required placeholder="例如：2026-11 增值税"></label>
<label class="fld"><span>所属期 <i>*</i></span><input v-model="taxDraft.period" required placeholder="2026-11"></label>
<label class="fld"><span>税种 <i>*</i></span><select v-model="taxDraft.taxType" required><option value="" disabled>选择税种</option><option value="vat">增值税</option><option value="corporate_income_tax">企业所得税</option><option value="personal_income_tax">个人所得税</option><option value="surcharge">附加税</option><option value="stamp_duty">印花税</option></select></label>
<label class="fld"><span>状态</span><select v-model="taxDraft.status"><option value="estimated">预估</option><option value="pending">待处理</option><option value="paid">已缴清</option><option value="overdue">已逾期</option></select></label>
<label class="fld"><span>应税金额</span><input v-model.number="taxDraft.taxableAmount" type="number" min="0" step="0.01" placeholder="0.00"></label>
<label class="fld"><span>税率（%）</span><input v-model.number="taxDraft.taxRate" type="number" min="0" max="100" step="0.01" placeholder="6"></label>
<label class="fld"><span>应缴税额 <i>*</i></span><input v-model.number="taxDraft.taxAmount" type="number" min="0" step="0.01" placeholder="0.00"></label>
<label class="fld"><span>已缴税额</span><input v-model.number="taxDraft.paidAmount" type="number" min="0" step="0.01" placeholder="0.00"></label>
<label class="fld"><span>可抵扣金额</span><input v-model.number="taxDraft.deductibleAmount" type="number" min="0" step="0.01" placeholder="0.00"></label>
<label class="fld"><span>截止日期 <i>*</i></span><input v-model="taxDraft.dueDate" type="date" required></label>
<div class="form-foot"><button class="btn" type="button" @click="taxForm=false">取消</button><button class="btn pri" type="submit">保存税项</button></div>
</form></el-dialog>
<el-dialog v-model="organizationForm" title="新增组织资料" width="760px" align-center><el-tabs>
<el-tab-pane label="部门"><form class="ed-grid form-grid" @submit.prevent="saveDepartment">
<label class="fld"><span>部门名称 <i>*</i></span><input v-model="departmentDraft.name" required placeholder="例如：市场部"></label>
<label class="fld"><span>成本中心</span><input v-model="departmentDraft.costCenter" placeholder="例如：CC-1001"></label>
<label class="fld"><span>部门预算</span><input v-model.number="departmentDraft.budget" type="number" min="0" placeholder="0"></label>
<label class="fld"><span>部门负责人</span><select v-model.number="departmentDraft.managerEmployeeId"><option :value="0">未指定</option><option v-for="e in employeeOptions" :key="e.id" :value="e.id">{{ e.name }}</option></select></label>
<label class="fld"><span>状态</span><select v-model.number="departmentDraft.status"><option :value="1">启用</option><option :value="0">停用</option></select></label>
<div class="form-foot"><button class="btn pri" type="submit">创建部门</button></div>
</form></el-tab-pane>
<el-tab-pane label="员工"><form class="ed-grid form-grid" @submit.prevent="saveEmployee">
<label class="fld"><span>姓名 <i>*</i></span><input v-model="employeeDraft.name" required placeholder="例如：张三"></label>
<label class="fld"><span>工号</span><input v-model="employeeDraft.employeeNo" placeholder="例如：E-1001"></label>
<label class="fld"><span>邮箱 <i>*</i></span><input v-model="employeeDraft.email" type="email" required placeholder="name@company.com"></label>
<label class="fld"><span>手机号</span><input v-model="employeeDraft.phone" placeholder="可选"></label>
<label class="fld"><span>岗位 <i>*</i></span><input v-model="employeeDraft.position" required placeholder="例如：前端工程师"></label>
<label class="fld"><span>职级</span><input v-model="employeeDraft.jobLevel" placeholder="例如：P6"></label>
<label class="fld"><span>部门</span><select v-model.number="employeeDraft.departmentId"><option :value="0">未指定</option><option v-for="d in departments" :key="d.id" :value="d.id">{{ d.name }}</option></select></label>
<label class="fld"><span>工作地点</span><input v-model="employeeDraft.workLocation" placeholder="例如：深圳"></label>
<label class="fld"><span>用工类型</span><select v-model="employeeDraft.employmentType"><option value="full_time">全职</option><option value="part_time">兼职</option><option value="intern">实习</option><option value="contractor">外包</option></select></label>
<label class="fld"><span>在职状态</span><select v-model="employeeDraft.status"><option value="active">在职</option><option value="probation">试用</option><option value="pending">待入职</option><option value="left">离职</option></select></label>
<label class="fld"><span>入职日期 <i>*</i></span><input v-model="employeeDraft.hireDate" type="date" required></label>
<label class="fld"><span>合同类型</span><input v-model="employeeDraft.contractType" placeholder="例如：固定期限"></label>
<label class="fld"><span>基本薪资</span><input v-model.number="employeeDraft.salary" type="number" min="0" placeholder="0"></label>
<label class="fld"><span>企业社保</span><input v-model.number="employeeDraft.socialInsurance" type="number" min="0" placeholder="0"></label>
<label class="fld"><span>企业公积金</span><input v-model.number="employeeDraft.housingFund" type="number" min="0" placeholder="0"></label>
<div class="form-foot"><button class="btn pri" type="submit">创建员工</button></div>
</form></el-tab-pane>
</el-tabs></el-dialog>
</div></template>


<style scoped>
.ledger-metrics{display:grid;grid-template-columns:repeat(auto-fit,minmax(176px,1fr));gap:12px;margin-bottom:16px}
.ledger-card{background:var(--card);border:1px solid var(--line);border-radius:16px;box-shadow:var(--sh);overflow:hidden}
.ledger-filters{display:flex;align-items:center;gap:14px;padding:12px 18px;border-bottom:1px solid var(--line2);flex-wrap:wrap;margin:0}
.ledger-segments{display:flex;gap:4px;background:#f2f5f9;border-radius:10px;padding:3px;align-items:center}
.ledger-segments button{height:30px;padding:0 13px;border:none;border-radius:8px;background:transparent;color:var(--ink3);font-size:12.5px;cursor:pointer;font-family:inherit}
.ledger-segments button.active{background:#fff;color:var(--brand);font-weight:600;box-shadow:0 1px 3px rgba(16,24,40,.12)}
.ledger-segments span{font-size:12.5px;color:var(--ink3);padding:0 9px}
.ledger-search{margin-left:auto;display:flex;gap:8px}
.ledger-search input{height:34px;width:240px;border:1px solid var(--line);border-radius:9px;padding:0 11px;font-size:12.5px;color:var(--ink);outline:none;background:#fff}
.ledger-search input:focus{border-color:#9fb1f2;box-shadow:0 0 0 3px rgba(54,89,227,.12)}
.ledger-table :deep(.el-table){--el-table-border-color:var(--line2);font-size:12.5px}
.ledger-table :deep(.el-table th.el-table__cell){background:#fbfcfe;color:var(--ink3);font-weight:600;font-size:11.5px;height:44px;border-bottom:1px solid var(--line)}
.ledger-table :deep(.el-table td.el-table__cell){height:52px;border-bottom:1px solid var(--line2)}
.ledger-table :deep(.el-table__row:hover > td.el-table__cell){background:#f7fafd}
.ledger-footer{display:flex;align-items:center;justify-content:space-between;padding:12px 18px;border-top:1px solid var(--line2);font-size:12px;color:var(--ink3)}
.inline-period{display:flex;align-items:center;gap:8px;font-size:12px;color:var(--ink3)}
.inline-period input{height:34px;border:1px solid var(--line);border-radius:9px;padding:0 9px}
@media (max-width:900px){.ledger-search{margin-left:0;width:100%}.ledger-search input{flex:1;width:auto}}
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
  .ledger-search{width:100%}
  .ledger-search input{width:100%}
  .ledger-footer{flex-direction:column;gap:10px;align-items:flex-start}
}
</style>
<style scoped>
.tg.green{background:#e6f7ef;color:#0a6b46}
.tg.ink{background:#eef1f5;color:#8a97a6}
.perm{margin-top:16px;padding-top:14px;border-top:1px solid var(--line2)}
.perm h4{font-size:12px;color:var(--ink4);font-weight:600;margin-bottom:10px}
.perm-row{display:flex;align-items:center;gap:12px;margin-bottom:10px;font-size:12px;color:var(--ink3)}
.perm-row>span{width:64px;flex:0 0 64px}
.seg2{display:flex;gap:4px;background:#f2f5f9;border-radius:9px;padding:3px}
.seg2 button{height:28px;padding:0 12px;border:none;border-radius:7px;background:transparent;color:var(--ink3);font-size:12px;cursor:pointer;font-family:inherit}
.seg2 button.on{background:#fff;color:var(--brand);font-weight:600;box-shadow:0 1px 3px rgba(16,24,40,.12)}
.chips{display:flex;gap:6px;flex-wrap:wrap}
.chips button{height:28px;padding:0 11px;border:1px solid var(--line);border-radius:7px;background:#fff;color:var(--ink2);font-size:12px;cursor:pointer;font-family:inherit}
.chips button.on{border-color:#9fb1f2;background:#eef2ff;color:var(--brand);font-weight:600}
.perm-tip{font-size:11px;color:var(--ink4);margin-top:4px}
</style>

<style scoped>
.tags{display:flex;flex-wrap:wrap;gap:4px}
.tg{font-size:10.5px;padding:2px 7px;border-radius:5px;background:#f2f5f9;color:var(--ink3);line-height:1.5;white-space:nowrap}
</style>
<style scoped>
.dept-strip{display:flex;align-items:center;gap:8px;flex-wrap:wrap;padding:10px 14px;margin-bottom:12px;
  background:var(--card);border:1px solid var(--line);border-radius:12px}
.dept-strip .lb{font-size:11px;font-weight:600;color:var(--brand);background:#eef2ff;border-radius:6px;padding:3px 8px}
.dept-strip button{height:30px;padding:0 11px;border:1px solid var(--line);border-radius:8px;background:#fff;
  color:var(--ink2);font-size:12px;cursor:pointer;font-family:inherit}
.dept-strip button.on{border-color:#9fb1f2;background:#eef2ff;color:var(--brand);font-weight:600}
.dept-strip button b{font-weight:600;margin-left:4px;color:var(--ink4)}
.dept-strip button.on b{color:var(--brand)}
.dept-empty{font-size:11.5px;color:var(--ink4)}
</style>

<style scoped>
.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px 18px}
.fld{display:flex;flex-direction:column;gap:6px;font-size:12px;color:var(--ink3)}
.fld span i{color:var(--danger);font-style:normal}
.fld input,.fld select{height:40px;border:1px solid var(--line);border-radius:9px;padding:0 11px;font-size:13px;color:var(--ink);background:#fff;outline:none;font-family:inherit}
.fld input:focus,.fld select:focus{border-color:#9fb1f2;box-shadow:0 0 0 3px rgba(54,89,227,.12)}
.form-foot{grid-column:span 2;display:flex;justify-content:flex-end;gap:10px;padding-top:4px}
@media (max-width:720px){.form-grid{grid-template-columns:1fr}.form-foot{grid-column:auto}}
</style>

<style scoped>
.users-extra{display:grid;grid-template-columns:1.3fr 1fr;gap:14px;margin-bottom:14px}
.users-extra .card .bd,.users-extra .card{padding-bottom:0}
.matrix{padding:10px 18px 14px}
.mrow{display:grid;grid-template-columns:1.4fr repeat(4,64px);align-items:center;gap:8px;padding:7px 0;border-bottom:1px solid var(--line2)}
.mrow:last-child{border-bottom:none}
.mhead{font-size:11px;color:var(--ink4);font-weight:600}
.mhead span:not(:first-child){text-align:center}
.mname{font-size:12px;color:var(--ink2);overflow:hidden;text-overflow:ellipsis;white-space:nowrap;padding-right:8px}
.mcell{height:28px;border:1px solid var(--line);border-radius:7px;background:#fff;color:var(--ink4);font-size:12px;cursor:pointer}
.mcell.on{border-color:#9fb1f2;background:#eef2ff;color:var(--brand);font-weight:700}
.mcell:disabled{cursor:not-allowed;opacity:.6}
.mempty{font-size:12px;color:var(--ink4);padding:14px 0}
.invs{padding:6px 18px 14px}
.inv{padding:9px 0;border-bottom:1px solid var(--line2)}
.inv:last-child{border-bottom:none}
.inv b{display:block;font-size:12.5px;color:var(--ink)}
.inv small{display:block;font-size:11px;color:var(--ink4);margin-top:3px}
@media (max-width:1100px){.users-extra{grid-template-columns:1fr}}
</style>
