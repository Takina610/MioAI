/** 使用记录页共享格式化：Token 缩写 / 时长拟人 / 数量千分位 */

/** 大数缩写（1.2K / 3.4M / 1.2B），负数与 0 原样 */
export function formatCompact(value: number): string {
  if (value >= 1_000_000_000) {
    return trimZero(value / 1_000_000_000) + 'B'
  }
  if (value >= 1_000_000) {
    return trimZero(value / 1_000_000) + 'M'
  }
  if (value >= 1_000) {
    return trimZero(value / 1_000) + 'K'
  }
  return String(value)
}

function trimZero(value: number): string {
  return value.toFixed(value >= 100 ? 0 : 1).replace(/\.0$/, '')
}

/** 毫秒时长拟人（x天x小时 / x小时x分钟 / x分钟 / x秒） */
export function formatDuration(ms: number): string {
  if (ms <= 0) {
    return '0分钟'
  }
  const minutes = Math.floor(ms / 60_000)
  if (minutes < 1) {
    return Math.max(1, Math.round(ms / 1000)) + '秒'
  }
  if (minutes < 60) {
    return minutes + '分钟'
  }
  const hours = Math.floor(minutes / 60)
  if (hours < 24) {
    const rest = minutes % 60
    return rest > 0 ? `${hours}小时${rest}分钟` : `${hours}小时`
  }
  const days = Math.floor(hours / 24)
  const restHours = hours % 24
  return restHours > 0 ? `${days}天${restHours}小时` : `${days}天`
}

/** 千分位 */
export function formatNumber(value: number): string {
  return value.toLocaleString('zh-CN')
}
