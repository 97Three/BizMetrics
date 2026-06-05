<script setup lang="ts">
import { computed, ref } from 'vue'
import { useAuthStore } from '../stores/auth'
import { canVisit } from '../utils/navigationAccess'
const auth=useAuthStore()
const moreOpen=ref(false)
const links=[{label:'首页',path:'/dashboard',icon:'⌂'},{label:'流水',path:'/transactions',icon:'↔'},{label:'报表',path:'/reports',icon:'▥'}]
const extra=[
  {label:'经营总览',path:'/operations'},{label:'财务总览',path:'/finance'},
  {label:'预算',path:'/budgets'},{label:'账户',path:'/accounts'},
  {label:'票据',path:'/receipts'},{label:'税务',path:'/tax'},
  {label:'周期事项',path:'/recurring'},{label:'审批',path:'/approvals'},
  {label:'组织与人员',path:'/hr/organization'},{label:'人力成本',path:'/hr/workforce-cost'},
  {label:'福利',path:'/hr/benefits'},{label:'绩效',path:'/hr/performance'},
  {label:'薪酬月结',path:'/admin/compensation'},{label:'成员与权限',path:'/admin/users'},
  {label:'政策中心',path:'/policy-center'},{label:'数据备份',path:'/backup'},
  {label:'设置',path:'/settings'},
]
const visibleLinks=computed(()=>links.filter(item=>canVisit(item.path,auth)))
const visibleExtra=computed(()=>extra.filter(item=>canVisit(item.path,auth)))
</script>
<template><nav class="mobile-tabs" aria-label="移动导航"><RouterLink v-for="item in visibleLinks" :key="item.path" :to="item.path"><span aria-hidden="true">{{ item.icon }}</span>{{ item.label }}</RouterLink><button v-if="visibleExtra.length" type="button" :aria-expanded="moreOpen" @click="moreOpen=true"><span aria-hidden="true">☰</span>更多</button></nav><el-drawer v-model="moreOpen" title="更多功能" direction="btt" size="65%"><div class="mobile-extra"><RouterLink v-for="item in visibleExtra" :key="item.path" :to="item.path" @click="moreOpen=false">{{ item.label }}</RouterLink></div></el-drawer></template>
