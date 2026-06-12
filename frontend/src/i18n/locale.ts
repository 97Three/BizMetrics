import { ref } from 'vue'

export type Locale = 'zh' | 'en'

const stored = (typeof localStorage !== 'undefined' ? localStorage.getItem('bizmetrics-locale') : null) as Locale | null
export const locale = ref<Locale>(stored === 'en' ? 'en' : 'zh')

export function setLocale(next: Locale) {
  locale.value = next
  try {
    localStorage.setItem('bizmetrics-locale', next)
    document.documentElement.setAttribute('lang', next === 'en' ? 'en' : 'zh-CN')
  } catch { /* storage may be unavailable */ }
}

/** Pick a string for the active locale. */
export function pick(zh: string, en: string) {
  return locale.value === 'en' ? en : zh
}
