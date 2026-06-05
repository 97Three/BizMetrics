<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import TopNav from './TopNav.vue'
import MobileTabBar from './MobileTabBar.vue'
const online=ref(navigator.onLine)
const sync=()=>{online.value=navigator.onLine}
onMounted(()=>{window.addEventListener('online',sync);window.addEventListener('offline',sync)})
onUnmounted(()=>{window.removeEventListener('online',sync);window.removeEventListener('offline',sync)})
</script>
<template><div class="app-shell"><a class="skip-link" href="#main">跳到主要内容</a><TopNav/><div v-if="!online" class="offline-banner" role="status">当前网络已断开。已加载内容仍可查看，请恢复网络后重试写入操作。</div><main id="main"><RouterView/></main><MobileTabBar/></div></template>
