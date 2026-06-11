<script setup lang="ts">
import { computed } from 'vue'

interface Item { name: string; value: number; percent: number; color: string }

const props = withDefaults(defineProps<{
  items: Item[]
  size?: number
  thickness?: number
  centerLabel?: string
  centerSub?: string
}>(), { size: 148, thickness: 15, centerSub: '' })

const total = computed(() => props.items.reduce((n, i) => n + (i.percent || 0), 0) || 1)
const radius = computed(() => (props.size - props.thickness) / 2)
const circumference = computed(() => 2 * Math.PI * radius.value)

const segments = computed(() => {
  let offset = 0
  return props.items.map((item) => {
    const fraction = (item.percent || 0) / total.value
    const length = circumference.value * fraction
    const seg = { ...item, length: Math.max(0, length - 3), offset: -offset }
    offset += length
    return seg
  })
})

const rows = computed(() => props.items.slice(0, 5))
</script>

<template>
  <div class="donut-row">
    <svg :viewBox="`0 0 ${size} ${size}`" :width="size" :height="size" class="donut" role="img" :aria-label="centerLabel || '结构占比'">
      <circle :cx="size / 2" :cy="size / 2" :r="radius" fill="none" stroke="#f0f4f9" :stroke-width="thickness" />
      <circle
        v-for="seg in segments" :key="seg.name"
        :cx="size / 2" :cy="size / 2" :r="radius" fill="none"
        :stroke="seg.color" :stroke-width="thickness" stroke-linecap="butt"
        :stroke-dasharray="`${seg.length} ${circumference}`" :stroke-dashoffset="seg.offset"
        :transform="`rotate(-90 ${size / 2} ${size / 2})`"
      />
      <text :x="size / 2" :y="size / 2 - 2" text-anchor="middle" font-size="15" font-weight="600"
            fill="var(--ink)" style="font-variant-numeric:tabular-nums">{{ centerLabel }}</text>
      <text v-if="centerSub" :x="size / 2" :y="size / 2 + 15" text-anchor="middle" font-size="9.5" fill="var(--ink4)">{{ centerSub }}</text>
    </svg>
    <div class="legend">
      <div v-for="row in rows" :key="row.name" class="lrow">
        <s :style="{ background: row.color }" />
        <span class="nm">{{ row.name }}</span>
        <b>{{ Math.round(row.percent) }}%</b>
      </div>
      <p v-if="!rows.length" class="empty">暂无数据</p>
    </div>
  </div>
</template>

<style scoped>
.donut-row{display:flex;align-items:center;gap:18px}
.donut{flex:0 0 auto}
.legend{flex:1;min-width:0;display:flex;flex-direction:column;gap:8px}
.lrow{display:flex;align-items:center;gap:8px;font-size:11.5px;color:var(--ink2)}
.lrow s{width:7px;height:7px;border-radius:2px;flex:0 0 7px}
.lrow .nm{flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.lrow b{font-weight:500;color:var(--ink4);font-variant-numeric:tabular-nums}
.empty{font-size:12px;color:var(--ink4)}
</style>
