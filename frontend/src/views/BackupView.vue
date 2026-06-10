<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import client from '../api/client'
import UiIcon from '../components/ui/UiIcon.vue'
import MetricCard from '../components/ui/MetricCard.vue'
type Status=Record<string,number>
type Validation={valid:boolean;restorable:boolean;dryRun?:boolean;message:string;counts?:Record<string,number>;restored?:boolean}
const status=ref<Status|null>(null);const loading=ref(false);const error=ref('')
const file=ref<File|null>(null);const validation=ref<Validation|null>(null)
const validating=ref(false);const restoring=ref(false);const confirmText=ref('')
const labels:Record<string,string>={transactions:'流水',receipts:'票据',accounts:'账户',budgets:'预算',categories:'分类',companies:'公司主体',users:'成员',employees:'员工',departments:'部门',taxItems:'税项',approvals:'审批',recurring:'周期事项',files:'附件'}
const statusCards=computed(()=>Object.entries(status.value||{}).slice(0,6).map(([key,value],i)=>({
  label:labels[key]||key, value:String(value), tone:(['brand','green','ink','amber','ink','ink'] as const)[i%6], icon:['doc','wallet','users','shield','list','grid'][i%6],
})))
async function load(){loading.value=true;try{status.value=(await client.get<Status>('/backup/status')).data;error.value=''}catch{error.value='备份状态加载失败'}finally{loading.value=false}}
async function exportBackup(){try{const result=await client.get<Blob>('/backup/export',{responseType:'blob'});const url=URL.createObjectURL(result.data);const a=document.createElement('a');a.href=url;a.download=`bizmetrics-backup-${new Date().toISOString().slice(0,10)}.json`;a.click();URL.revokeObjectURL(url)}catch{ElMessage.error('备份导出失败')}}
async function chooseFile(event:Event){
  file.value=(event.target as HTMLInputElement).files?.[0]||null
  validation.value=null;confirmText.value=''
  if(!file.value)return
  validating.value=true
  try{const form=new FormData();form.append('file',file.value);validation.value=(await client.post<Validation>('/backup/validate',form)).data;ElMessage[validation.value.valid?'success':'warning'](validation.value.message||'校验完成')}
  catch{validation.value={valid:false,restorable:false,message:'文件校验失败'};ElMessage.error('无法校验此备份文件')}
  finally{validating.value=false}
}
async function restore(dryRun:boolean){
  if(!file.value||!validation.value?.valid||!validation.value.restorable)return
  if(!dryRun&&confirmText.value!=='RESTORE'){ElMessage.warning('请输入 RESTORE 确认');return}
  restoring.value=true
  try{
    const form=new FormData();form.append('file',file.value)
    const result=(await client.post<Validation>('/backup/restore',form,{params:{dryRun,confirmation:dryRun?'':confirmText.value},timeout:60000})).data
    if(dryRun){validation.value=result;ElMessage.success('恢复预演通过，尚未修改数据')}
    else if(result.restored){file.value=null;validation.value=null;confirmText.value='';ElMessage.success('结构化业务数据已恢复');await load()}
    else ElMessage.warning(result.message||'恢复未完成')
  }catch{ElMessage.error(dryRun?'恢复预演失败':'恢复失败，请核对备份文件与服务状态')}
  finally{restoring.value=false}
}
onMounted(load)
</script>
<template><div class="wrap"><div class="ph"><div class="l"><div class="tile"><UiIcon name="shield" :size="20" /></div><div><h1>备份、校验与恢复</h1><p class="sub">导出结构化快照，预检后进行受控恢复</p></div></div><button class="btn pri" type="button" @click="exportBackup">导出备份</button></div><div v-if="error" class="err" role="alert"><UiIcon name="alert" :size="16" /><span>{{ error }}</span><button class="btn" @click="load">重试</button></div><section v-else class="card"><header><h3><span class="ic"><UiIcon name="grid" :size="14" /></span>数据概况</h3><span class="hint">结构化业务数据计数</span></header><div v-loading="loading" class="backup-grid"><MetricCard v-for="card in statusCards" :key="card.label" v-bind="card" /></div></section><div class="notice-card">结构化快照包含敏感业务信息和附件元数据，不包含 MinIO 文件内容。请妥善保管下载文件；完整灾备仍需数据库与对象存储归档。</div><section class="card settings-section"><header><h3><span class="ic"><UiIcon name="shield" :size="14" /></span>恢复预检</h3><span class="hint">只读预演 → 正式恢复</span></header><div class="backup-restore"><p>先校验文件完整性与版本，再执行只读恢复预演。正式恢复会覆盖当前结构化业务数据。</p><label>选择 JSON 备份文件<input type="file" accept=".json,application/json" :disabled="validating||restoring" @change="chooseFile"></label><p v-if="validating">正在校验…</p><div v-if="validation" class="backup-validation"><strong>{{ validation.valid&&validation.restorable?'可恢复':'不可恢复' }}</strong><span>{{ validation.message }}</span><div v-if="validation.counts" class="backup-counts"><span v-for="(value,key) in validation.counts" :key="key">{{ key }}：{{ value }}</span></div></div><div v-if="validation?.valid&&validation.restorable" class="backup-actions"><button class="btn" type="button" :disabled="restoring" @click="restore(true)">{{ restoring?'处理中…':'执行只读预演' }}</button><label>输入 RESTORE 确认覆盖<input v-model="confirmText" autocomplete="off" placeholder="RESTORE"></label><button class="btn pri" type="button" :disabled="restoring||confirmText!=='RESTORE'" @click="restore(false)">正式恢复数据</button></div></div></section></div></template>


<style scoped>
.backup-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(176px,1fr));gap:12px;padding:16px 20px}
.backup-restore{display:flex;flex-direction:column;gap:12px;padding:16px 20px 4px;font-size:12.5px;color:var(--ink2)}
.backup-restore>p{color:var(--ink3);line-height:1.7}
.backup-restore label{display:flex;flex-direction:column;gap:6px;font-size:12px;color:var(--ink3)}
.backup-restore input[type=file],.backup-restore input[type=text]{height:38px;border:1px solid var(--line);border-radius:9px;padding:0 10px;font-size:12.5px;color:var(--ink);background:#fff}
.backup-validation{display:flex;flex-direction:column;gap:6px;padding:12px 14px;border-radius:10px;background:#f4f7fb;font-size:12px;color:var(--ink3)}
.backup-validation strong{color:var(--ink);font-size:13px}
.backup-counts{display:flex;flex-wrap:wrap;gap:10px;font-size:11.5px;color:var(--ink3)}
.backup-actions{display:flex;align-items:flex-end;gap:12px;flex-wrap:wrap;padding-top:4px}
</style>
<style scoped>
.err{display:flex;align-items:center;gap:12px;padding:14px 16px;border-radius:12px;background:#fdeaee;color:#b42318;font-size:13px;margin-bottom:14px}
.err button{margin-left:auto}
section.card>header{height:56px;display:flex;align-items:center;justify-content:space-between;padding:0 22px;border-bottom:1px solid var(--line2)}
section.card h3{font-size:14px;font-weight:650;display:flex;align-items:center;gap:9px}
section.card h3 .ic{width:26px;height:26px;border-radius:8px;background:#eef2ff;color:var(--brand);display:grid;place-items:center}
section.card .hint{font-size:11.5px;color:var(--ink4)}
section.card{margin-bottom:14px}
</style>