<script setup lang="ts">
import { computed } from 'vue'
import { money } from '../../utils/format'

interface Item { name: string; value: number; percent: number; color: string }

const props = withDefaults(defineProps<{
  items: Item[]
  totalLabel: string
  totalValue: string
  asMoney?: boolean
}>(), { asMoney: true })

const rows = computed(() => props.items.slice(0, 4))
const width = (p: number) => `${Math.max(2, Math.min(100, p))}%`
</script>

<template>
  <div class="comp">
    <div class="bar">
      <i v-for="item in rows" :key="item.name" :style="{ width: width(item.percent), background: item.color }" />
    </div>
    <div class="head"><span>{{ totalLabel }}</span><b>{{ totalValue }}</b></div>
    <div class="rows">
      <div v-for="item in rows" :key="item.name" class="row">
        <s :style="{ background: item.color }" />
        <span class="nm">{{ item.name }}</span>
        <em>{{ asMoney ? money(item.value) : item.value }}</em>
        <b>{{ item.percent.toFixed(0) }}%</b>
      </div>
      <p v-if="!rows.length" class="empty">本月暂无数据</p>
    </div>
  </div>
</template>

<style scoped>
.comp{display:flex;flex-direction:column;height:100%}
.bar{display:flex;gap:2px;height:11px;margin:2px 0 14px}
.bar i{display:block;height:100%;border-radius:4px}
.head{display:flex;align-items:baseline;justify-content:space-between;margin-bottom:8px}
.head span{font-size:11.5px;color:var(--ink4)}
.head b{font-size:22px;font-weight:650;letter-spacing:-.8px;font-variant-numeric:tabular-nums}
.rows{flex:1}
.row{display:flex;align-items:center;gap:8px;font-size:12px;color:var(--ink2);padding:5px 0}
.row s{width:7px;height:7px;border-radius:2px;flex:0 0 7px}
.nm{flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.row em{font-style:normal;font-weight:550;color:var(--ink);font-variant-numeric:tabular-nums;font-size:12.5px}
.row b{width:38px;text-align:right;font-weight:500;color:var(--ink4);font-variant-numeric:tabular-nums;font-size:11.5px}
.empty{font-size:12px;color:var(--ink4);padding:16px 0}
</style>
