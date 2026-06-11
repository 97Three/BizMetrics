<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import client from '../api/client'
import { money } from '../utils/format'

interface PreviewRow {
  rowNumber: number; date: string; type: number; amount: number
  categoryName: string; accountName: string; note: string
  duplicate: boolean; errors: string[]
}
interface ImportResult {
  committed: boolean; totalRows: number; validRows: number; invalidRows: number
  duplicateRows: number; importedRows: number; skippedRows: number; rows: PreviewRow[]
}

defineProps<{ modelValue: boolean }>()
const emit=defineEmits<{ 'update:modelValue':[value:boolean]; saved:[] }>()
const file=ref<File|null>(null)
const preview=ref<ImportResult|null>(null)
const skipDuplicates=ref(true)
const busy=ref(false)

function close(){if(busy.value)return;file.value=null;preview.value=null;emit('update:modelValue',false)}
async function downloadTemplate(){
  try{
    const response=await client.get<Blob>('/transactions/import/template',{responseType:'blob'})
    const url=URL.createObjectURL(response.data)
    const link=document.createElement('a');link.href=url;link.download='bizmetrics-transaction-import.csv';link.click()
    URL.revokeObjectURL(url)
  }catch{ElMessage.error('模板下载失败')}
}
async function chooseFile(event:Event){
  const next=(event.target as HTMLInputElement).files?.[0]||null
  file.value=next;preview.value=null
  if(!next)return
  busy.value=true
  try{
    const form=new FormData();form.append('file',next)
    preview.value=(await client.post<ImportResult>('/transactions/import/preview',form)).data
    if(preview.value.invalidRows)ElMessage.warning(`有 ${preview.value.invalidRows} 行需要修正`)
  }catch{file.value=null;ElMessage.error('预检失败，请检查 CSV 格式与编码')}
  finally{busy.value=false}
}
async function commit(){
  if(!file.value||!preview.value||preview.value.invalidRows||!preview.value.validRows)return
  busy.value=true
  try{
    const form=new FormData();form.append('file',file.value)
    const result=(await client.post<ImportResult>('/transactions/import',form,{params:{skipDuplicates:skipDuplicates.value}})).data
    if(!result.committed){preview.value=result;ElMessage.warning('文件内容已变化，请重新检查');return}
    ElMessage.success(`已导入 ${result.importedRows} 笔流水`)
    emit('saved');busy.value=false;close()
  }catch{ElMessage.error('批量导入失败，数据未写入')}
  finally{busy.value=false}
}
</script>

<template>
  <el-dialog :model-value="modelValue" title="批量导入经营流水" width="min(900px, 96vw)" :close-on-click-modal="!busy" @close="close">
    <div class="import-head"><div><strong>使用 UTF-8 CSV 批量录入</strong><p>必需列：日期、类型、金额、分类、账户；单次最多 500 行。先预检，再确认导入。</p></div><button class="btn" type="button" @click="downloadTemplate">下载模板</button></div>
    <label class="import-file">选择 CSV 并预检<input type="file" accept=".csv,text/csv" :disabled="busy" @change="chooseFile"></label>
    <p v-if="file" class="hint">{{ file.name }}</p>
    <template v-if="preview">
      <div class="import-summary"><span>共 {{ preview.totalRows }} 行</span><span>有效 {{ preview.validRows }}</span><span>错误 {{ preview.invalidRows }}</span><span>疑似重复 {{ preview.duplicateRows }}</span></div>
      <el-table :data="preview.rows" max-height="300" size="small" empty-text="文件中没有可预览的记录">
        <el-table-column prop="rowNumber" label="行" width="55"/><el-table-column prop="date" label="日期" width="112"/>
        <el-table-column label="类型" width="75"><template #default="scope">{{ scope.row.type===1?'收入':'支出' }}</template></el-table-column>
        <el-table-column label="金额" width="115"><template #default="scope">{{ money(scope.row.amount) }}</template></el-table-column>
        <el-table-column prop="categoryName" label="分类"/><el-table-column prop="accountName" label="账户"/>
        <el-table-column label="校验"><template #default="scope">{{ scope.row.errors.length?scope.row.errors.join('；'):scope.row.duplicate?'疑似重复':scope.row.note||'通过' }}</template></el-table-column>
      </el-table>
    </template>
    <div class="import-actions"><label><input v-model="skipDuplicates" type="checkbox"> 跳过疑似重复</label><button class="btn" type="button" :disabled="busy" @click="close">取消</button><button class="btn pri" type="button" :disabled="busy||!preview||!!preview.invalidRows||!preview.validRows" @click="commit">{{ busy?'处理中…':'确认导入' }}</button></div>
  </el-dialog>
</template>
