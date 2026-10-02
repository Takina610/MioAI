/**
 * 通用格式化工具：日期、文件大小、文本截断
 */

/** 完整时间：2026/01/02 15:04:05 */
export function formatDateTime(dateStr?: string | null): string {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  const hh = String(date.getHours()).padStart(2, '0')
  const mm = String(date.getMinutes()).padStart(2, '0')
  const ss = String(date.getSeconds()).padStart(2, '0')
  return `${y}/${m}/${d} ${hh}:${mm}:${ss}`
}

/** 本地化日期：2026/1/2 */
export function formatDate(dateStr?: string | null): string {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleDateString('zh-CN')
}

/** 本地化日期时间：2026/1/2 15:04 */
export function formatLocaleDateTime(dateStr?: string | null): string {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN')
}

/** 文件大小：1.5 MB */
export function formatFileSize(bytes: number): string {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.min(Math.floor(Math.log(bytes) / Math.log(k)), sizes.length - 1)
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(2))} ${sizes[i]}`
}

/** 文本截断，超出部分以 ... 结尾 */
export function truncateText(text: string, maxLength: number): string {
  if (!text) return ''
  if (text.length <= maxLength) return text
  return `${text.substring(0, maxLength)}...`
}
