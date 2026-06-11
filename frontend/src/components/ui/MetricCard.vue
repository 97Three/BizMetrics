<script setup lang="ts">
import Sparkline from './Sparkline.vue'
import UiIcon from './UiIcon.vue'

const props = withDefaults(defineProps<{
  label: string
  value: string
  currency?: string
  unit?: string
  foot?: string
  tone?: 'brand' | 'green' | 'red' | 'amber' | 'ink'
  icon?: string
  trend?: number[]
  hero?: boolean
}>(), { tone: 'brand', icon: 'trend', hero: false })

const tones: Record<string, string> = {
  brand: '#3659e3', green: '#12b76a', red: '#f04438', amber: '#f79009', ink: '#8798a8',
}
const tone = tones[props.tone]
</script>

<template>
  <div class="mcard" :class="{ hero }">
    <div class="top">
      <span class="lb">{{ label }}</span>
      <span class="chip" :style="hero ? {} : { background: `${tone}14`, color: tone }">
        <UiIcon :name="icon" :size="14" />
      </span>
    </div>
    <div class="val">
      <span v-if="currency" class="cur">{{ currency }}</span>{{ value }}<span v-if="unit" class="unit">{{ unit }}</span>
    </div>
    <div class="foot">
      <span class="ft">{{ foot }}</span>
      <Sparkline v-if="trend && trend.length > 1" :values="trend" :color="hero ? '#fff' : tone" :width="76" :height="24" />
    </div>
  </div>
</template>

<style scoped>
.mcard{
  background:var(--card);border:1px solid var(--line);border-radius:16px;padding:15px 16px 13px;min-height:112px;
  display:flex;flex-direction:column;justify-content:space-between;box-shadow:var(--sh);position:relative;overflow:hidden;
  transition:transform .15s ease, box-shadow .15s ease;
}
.mcard:hover{transform:translateY(-1px);box-shadow:var(--sh-lg)}
.top{display:flex;align-items:center;justify-content:space-between;gap:8px;font-size:12px;color:var(--ink3)}
.chip{width:26px;height:26px;border-radius:8px;display:grid;place-items:center;flex:0 0 26px}
.val{font-size:28px;font-weight:650;line-height:1.1;margin:8px 0 6px;color:var(--ink);
  font-variant-numeric:tabular-nums;letter-spacing:-.6px;display:flex;align-items:baseline;gap:2px;white-space:nowrap}
.cur{font-size:15px;font-weight:600;color:var(--ink4);margin-right:1px}
.unit{font-size:15px;font-weight:600;color:var(--ink4);margin-left:2px}
.foot{display:flex;align-items:center;justify-content:space-between;gap:6px;font-size:11px;color:var(--ink4);min-height:24px}
.ft{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.hero{background:linear-gradient(140deg,#3f63ea 0%,#6c8cff 100%);border:none;color:#fff;
  box-shadow:0 14px 30px -10px rgba(74,88,240,.55)}
.hero .top,.hero .foot{color:rgba(255,255,255,.78)}
.hero .val{color:#fff}
.hero .cur,.hero .unit{color:rgba(255,255,255,.72)}
.hero .chip{background:rgba(255,255,255,.16);color:#fff}
.hero::after{content:"";position:absolute;right:-40px;top:-60px;width:150px;height:150px;border-radius:50%;
  background:radial-gradient(circle,rgba(255,255,255,.28),transparent 66%)}
</style>
