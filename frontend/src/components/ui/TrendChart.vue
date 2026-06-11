<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import type { TrendPoint } from '../../types/stats'

use([CanvasRenderer, BarChart, LineChart, GridComponent, TooltipComponent])

const props = withDefaults(defineProps<{
  points: TrendPoint[]
  height?: number
}>(), { height: 300 })

const option = computed(() => {
  const src = props.points.length ? props.points : [{ month: '', income: 0, expense: 0, balance: 0 } as TrendPoint]
  const months = src.map((p) => (p.month || '').slice(-2) + ' 月')
  return {
    animationDuration: 500,
    grid: { left: 56, right: 16, top: 16, bottom: 30 },
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#0a1b2e',
      borderWidth: 0,
      padding: [8, 12],
      textStyle: { color: '#fff', fontSize: 12 },
    },
    xAxis: {
      type: 'category',
      data: months,
      axisLine: { lineStyle: { color: '#e6ecf3' } },
      axisTick: { show: false },
      axisLabel: { color: '#8798a8', fontSize: 11, margin: 12 },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#edf1f7', type: 'dashed' } },
      axisLabel: {
        color: '#8798a8',
        fontSize: 11,
        formatter: (v: number) => (v >= 10000 ? `${v / 10000}万` : String(v)),
      },
    },
    series: [
      {
        name: '收入',
        type: 'bar',
        barWidth: 18,
        barGap: '30%',
        itemStyle: {
          borderRadius: [5, 5, 0, 0],
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [{ offset: 0, color: '#12b76a' }, { offset: 1, color: '#6ce2b0' }],
          },
        },
        data: src.map((p) => p.income ?? 0),
      },
      {
        name: '成本',
        type: 'bar',
        barWidth: 18,
        itemStyle: {
          borderRadius: [5, 5, 0, 0],
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [{ offset: 0, color: '#f04438' }, { offset: 1, color: '#ffa39a' }],
          },
        },
        data: src.map((p) => p.expense ?? 0),
      },
      {
        name: '结余',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 8,
        showSymbol: true,
        itemStyle: { color: '#fff', borderColor: '#6c8cff', borderWidth: 2.2 },
        lineStyle: {
          width: 2.4,
          color: {
            type: 'linear', x: 0, y: 0, x2: 1, y2: 0,
            colorStops: [{ offset: 0, color: '#3659e3' }, { offset: 1, color: '#6c8cff' }],
          },
        },
        data: src.map((p) => p.balance ?? 0),
      },
    ],
  }
})
</script>

<template>
  <VChart :option="option" autoresize :style="{ height: `${height}px`, width: '100%' }" />
</template>
