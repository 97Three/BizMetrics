<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  values: number[]
  color?: string
  width?: number
  height?: number
}>(), { color: '#3659e3', width: 84, height: 26 })

const uid = `sp-${Math.random().toString(36).slice(2, 8)}`

const geometry = computed(() => {
  const vals = props.values.length ? props.values : [0, 0]
  const lo = Math.min(...vals)
  const hi = Math.max(...vals)
  const range = hi - lo || 1
  const pts = vals.map((v, i) => {
    const x = 2 + (i * (props.width - 4)) / Math.max(1, vals.length - 1)
    const y = props.height - 3 - ((v - lo) / range) * (props.height - 8)
    return [x, y] as const
  })
  const line = pts.map(([x, y], i) => `${i === 0 ? 'M' : 'L'} ${x.toFixed(1)} ${y.toFixed(1)}`).join(' ')
  const area = `${line} L ${pts[pts.length - 1][0].toFixed(1)} ${props.height} L ${pts[0][0].toFixed(1)} ${props.height} Z`
  return { line, area }
})
</script>

<template>
  <svg :viewBox="`0 0 ${width} ${height}`" :width="width" :height="height" aria-hidden="true">
    <defs>
      <linearGradient :id="uid" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" :stop-color="color" stop-opacity=".28" />
        <stop offset="100%" :stop-color="color" stop-opacity="0" />
      </linearGradient>
    </defs>
    <path :d="geometry.area" :fill="`url(#${uid})`" />
    <path :d="geometry.line" fill="none" :stroke="color" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
  </svg>
</template>
