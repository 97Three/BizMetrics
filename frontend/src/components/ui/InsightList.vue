<script setup lang="ts">
import { RouterLink } from 'vue-router'
import UiIcon from './UiIcon.vue'

interface Item { title: string; detail: string; tone?: 'green' | 'amber' | 'red' | 'brand'; icon?: string; path?: string }

withDefaults(defineProps<{ items: Item[]; empty?: string }>(), { empty: '目前没有需要特别关注的事项' })

const tones: Record<string, string> = { green: '#12b76a', amber: '#f79009', red: '#f04438', brand: '#3659e3' }
const toneOf = (t?: string) => tones[t || 'brand'] || tones.brand
</script>

<template>
  <div class="ins">
    <component
      :is="item.path ? RouterLink : 'div'"
      v-for="item in items"
      :key="item.title + item.detail"
      :to="item.path"
      class="it"
      v-bind="item.path ? {} : { role: 'listitem' }"
    >
      <span class="ic" :style="{ background: `${toneOf(item.tone)}14`, color: toneOf(item.tone) }">
        <UiIcon :name="item.icon || 'alert'" :size="13" />
      </span>
      <span class="tx">
        <b>{{ item.title }}</b>
        <small>{{ item.detail }}</small>
      </span>
      <UiIcon v-if="item.path" name="arrow" :size="13" class="go" />
    </component>
    <p v-if="!items.length" class="empty">{{ empty }}</p>
  </div>
</template>

<style scoped>
.ins{display:flex;flex-direction:column}
.it{display:flex;gap:11px;align-items:flex-start;padding:13px 0;border-bottom:1px solid var(--line2);text-decoration:none;color:inherit}
.it:last-of-type{border-bottom:none}
.ic{width:26px;height:26px;border-radius:8px;display:grid;place-items:center;flex:0 0 26px;margin-top:1px}
.tx{flex:1;min-width:0}
.tx b{display:block;font-size:12.5px;font-weight:650;color:var(--ink);line-height:1.5}
.tx small{display:block;font-size:11.5px;color:var(--ink3);line-height:1.6;margin-top:3px}
.go{color:var(--ink4);margin-top:5px;opacity:0;transition:opacity .15s}
.it:hover .go{opacity:1}
.it:hover .tx b{color:var(--brand)}
.empty{font-size:12px;color:var(--ink4);padding:18px 0}
</style>
