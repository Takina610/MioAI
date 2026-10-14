/** ApexCharts 共享主题：暖米色系配色 + 统一字体/网格/提示框（颜色全部取自 variables.scss 既有 token） */

export const CHART_PALETTE = ['#2aa1a9', '#d98f45', '#8b6fd6', '#4f9e63', '#263749', '#c0453a']

export const CHART_FONT_FAMILY =
  "'AnthropicSans Text', 'PingFang SC', 'Microsoft YaHei UI', 'Microsoft YaHei', sans-serif"

/** 折线/面积图公共配置片段 */
export function baseLineChartColors(): Partial<import('apexcharts').ApexOptions> {
  return {
    chart: {
      fontFamily: CHART_FONT_FAMILY,
      toolbar: { show: false },
      zoom: { enabled: false },
      animations: { speed: 450 }
    },
    colors: [CHART_PALETTE[0]],
    stroke: {
      curve: 'smooth',
      width: 2.5,
      lineCap: 'round'
    },
    dataLabels: { enabled: false },
    grid: {
      borderColor: '#e8e6dc',
      strokeDashArray: 4,
      padding: { left: 8, right: 8, top: 0 }
    },
    xaxis: {
      axisBorder: { show: false },
      axisTicks: { show: false },
      labels: { style: { colors: '#8c8a82', fontSize: '11px' } },
      tooltip: { enabled: false }
    },
    yaxis: {
      labels: {
        style: { colors: '#8c8a82', fontSize: '11px' },
        formatter: (value: number) => compactAxis(value)
      }
    },
    tooltip: {
      theme: 'light',
      style: { fontFamily: CHART_FONT_FAMILY, fontSize: '12px' }
    },
    legend: { show: false }
  }
}

/** 坐标轴大数缩写（完整数值在 tooltip 里） */
function compactAxis(value: number): string {
  if (Math.abs(value) >= 1_000_000) {
    return (value / 1_000_000).toFixed(1).replace(/\.0$/, '') + 'M'
  }
  if (Math.abs(value) >= 1_000) {
    return (value / 1_000).toFixed(1).replace(/\.0$/, '') + 'K'
  }
  return String(value)
}
