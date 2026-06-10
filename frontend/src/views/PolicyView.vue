<script setup lang="ts">
import { computed, ref } from 'vue'
import { policies, policySnapshotUpdatedAt, type PolicyRecord } from '../data/policies'
import UiIcon from '../components/ui/UiIcon.vue'
const scopes=[['all','全部政策'],['social-insurance','社保'],['housing-fund','公积金'],['talent','人才福利'],['tax','税务'],['employment','就业创业']]
const scope=ref('all');const keyword=ref('');const selected=ref<PolicyRecord|null>(null)
const filtered=computed(()=>policies.filter(item=>(scope.value==='all'||item.scope===scope.value)&&(!keyword.value||`${item.title} ${item.summary} ${item.region}`.toLocaleLowerCase().includes(keyword.value.toLocaleLowerCase()))))
const drawerOpen=computed({get:()=>selected.value!==null,set(value:boolean){if(!value)selected.value=null}})
</script>
<template><div class="wrap"><div class="ph"><div class="l"><div class="tile"><UiIcon name="doc" :size="20" /></div><div><h1>政策中心</h1><p class="sub">按业务领域查阅原版维护的政策快照</p></div></div></div><div class="notice-card">资料快照更新于 {{ policySnapshotUpdatedAt }}。政策可能变化，请通过原始来源核验后再用于业务决策。</div><div class="policy-layout"><aside class="card policy-scopes"><button v-for="item in scopes" :key="item[0]" type="button" :class="{active:scope===item[0]}" @click="scope=item[0]">{{ item[1] }}</button></aside><section class="policy-main"><div class="ledger-search"><input v-model="keyword" type="search" placeholder="搜索政策标题、地区或摘要" aria-label="搜索政策"></div><div v-if="!filtered.length" class="ledger-state">暂无符合条件的政策资料</div><button v-for="item in filtered" :key="item.key" class="card policy-card" type="button" @click="selected=item"><span class="eyebrow">{{ item.region }} · {{ item.version }}</span><h2>{{ item.title }}</h2><p>{{ item.summary }}</p><span class="policy-source">来源：{{ item.sourceName }} · 查看详情 →</span></button></section></div><el-drawer v-model="drawerOpen" :title="selected?.title||'政策详情'" size="540px"><template v-if="selected"><p class="notice-card">政策快照仅供参考，请以官方原文为准。</p><div class="policy-detail"><h3>适用场景</h3><p>{{ selected.businessUse }}</p><h3>核对清单</h3><ul><li v-for="item in selected.checklist" :key="item">{{ item }}</li></ul><h3>来源</h3><a :href="selected.sourceUrl" target="_blank" rel="noopener noreferrer">{{ selected.sourceName }} ↗</a><p v-for="link in selected.sourceLinks||[]" :key="link.url"><a :href="link.url" target="_blank" rel="noopener noreferrer">{{ link.name }} ↗</a></p></div></template></el-drawer></div></template>

<style scoped>
.policy-layout{display:grid;grid-template-columns:220px minmax(0,1fr);gap:16px;align-items:start}
.policy-scopes{display:flex;flex-direction:column;gap:2px;padding:8px;position:sticky;top:16px}
.policy-scopes button{height:36px;border:none;border-radius:9px;background:transparent;color:var(--ink2);font-size:12.5px;text-align:left;padding:0 11px;cursor:pointer;font-family:inherit}
.policy-scopes button:hover{background:#f2f5f9}
.policy-scopes button.active{background:#eef2ff;color:var(--brand);font-weight:600}
.policy-main{display:flex;flex-direction:column;gap:12px;min-width:0}
.policy-main .ledger-search{margin:0}
.policy-main .ledger-search input{width:100%}
.policy-card{display:flex;flex-direction:column;gap:8px;padding:18px 20px;text-align:left;border:1px solid var(--line);border-radius:16px;background:var(--card);box-shadow:var(--sh);cursor:pointer;transition:transform .15s,box-shadow .15s}
.policy-card:hover{transform:translateY(-1px);box-shadow:var(--sh-lg)}
.policy-card .eyebrow{font-size:10.5px;color:var(--ink4);letter-spacing:.4px}
.policy-card h2{font-size:15px;font-weight:650;color:var(--ink)}
.policy-card p{font-size:12.5px;color:var(--ink3);line-height:1.7}
.policy-source{font-size:11.5px;color:var(--brand)}
.policy-detail h3{font-size:13px;font-weight:650;margin:14px 0 6px}
.policy-detail p,.policy-detail li{font-size:12.5px;color:var(--ink2);line-height:1.75}
.policy-detail ul{padding-left:18px}
@media (max-width:900px){.policy-layout{grid-template-columns:1fr}.policy-scopes{position:static;flex-direction:row;overflow:auto}}
</style>