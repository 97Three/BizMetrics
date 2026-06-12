export const money = (value: number | null | undefined) => {
  if (value == null || !Number.isFinite(value)) return '—'
  const sign = value < 0 ? '−' : ''
  return `${sign}¥${new Intl.NumberFormat('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(Math.abs(value))}`
}
export const percent = (value: number | null | undefined) => value == null || !Number.isFinite(value) ? '—' : `${(value * 100).toFixed(1)}%`
export const percentPoints = (value: number | null | undefined) => value == null || !Number.isFinite(value) ? '—' : `${value.toFixed(1)}%`
export const date = (value: string | null | undefined) => value ? value.slice(0, 10) : '—'
