<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import AuthShell from '../layouts/AuthShell.vue'
const route=useRoute();const router=useRouter();const auth=useAuthStore()
const nickname=ref('');const email=ref('');const password=ref('');const confirm=ref('');const loading=ref(false)
async function submit(){
  if(password.value!==confirm.value){ElMessage.warning('两次密码不一致');return}
  if(password.value.length<8){ElMessage.warning('密码至少 8 位');return}
  loading.value=true
  try{await auth.register({nickname:nickname.value,email:email.value,password:password.value,inviteToken:String(route.query.invite||route.query.inviteToken||'')||undefined});await router.replace('/dashboard')}
  catch{ElMessage.error('注册失败，请检查邮箱、密码和后端服务')}
  finally{loading.value=false}
}
</script>
<template><AuthShell mode="register"><form class="auth-panel" @submit.prevent="submit"><span class="form-kicker">GET STARTED <i></i> 开始使用</span><h2>创建工作台账号</h2><p class="form-intro">填写基础信息，开始管理你的经营数据。</p><div class="form-divider"></div><label for="nickname">姓名或昵称</label><input id="nickname" v-model="nickname" required autocomplete="name" placeholder="团队中显示的姓名"><label for="register-email">工作邮箱</label><input id="register-email" v-model="email" required type="email" autocomplete="email" placeholder="name@company.com"><label for="register-password">密码</label><input id="register-password" v-model="password" required type="password" autocomplete="new-password" placeholder="至少 8 位"><label for="confirm">确认密码</label><input id="confirm" v-model="confirm" required type="password" autocomplete="new-password" placeholder="再次输入密码"><button class="auth-submit" type="submit" :disabled="loading"><span>{{ loading?'正在创建…':'创建账号' }}</span><span aria-hidden="true">↗</span></button><div class="auth-form-end"><span>已有账号？</span><RouterLink to="/login">返回登录 →</RouterLink></div><p class="auth-security">账号创建后即可登录工作台</p></form></AuthShell></template>

