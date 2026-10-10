<template>
  <div class="usage-record-page">
    <div class="page-header">
      <div class="header-content">
        <div class="header-left">
          <h2>使用记录管理</h2>
        </div>
      </div>
      <div class="header-line"></div>
    </div>

    <div class="tabs-header">
      <a-tabs v-model:activeKey="activeTab" class="usage-tabs" @change="onTabChange">
        <a-tab-pane key="agent" tab="MioBot"></a-tab-pane>
        <a-tab-pane key="knowledge" tab="知识库"></a-tab-pane>
        <a-tab-pane key="mcp" tab="MCP"></a-tab-pane>
      </a-tabs>
    </div>

    <div class="page-scroll-content">
      <a-spin :spinning="loading">
        <div class="top-section">
          <div class="filter-bar">
            <span class="filter-label">选择时间</span>
            <a-range-picker
              v-model:value="dateRange"
              :presets="presets"
              :placeholder="['开始时间', '结束时间']"
              format="YYYY-MM-DD HH:mm:ss"
              show-time
              @change="onDateChange"
            />
          </div>

          <div class="metrics-row">
            <a-card
              class="metric-card"
              v-for="item in metricCards"
              :key="item.key"
              :bordered="false"
            >
              <a-statistic :title="item.label" :value="item.displayValue">
                <template #suffix>
                  <span class="metric-unit">{{ item.unit }}</span>
                </template>
              </a-statistic>
              <div class="trend-row">
                <span class="trend" :class="item.trend >= 0 ? 'up' : 'down'">
                  {{ Math.abs(item.trend).toFixed(1) }}%
                  <ArrowUpOutlined v-if="item.trend >= 0" />
                  <ArrowDownOutlined v-else />
                </span>
                <span class="trend-label">同比</span>
              </div>
            </a-card>
          </div>
        </div>

        <div class="charts-row">
          <a-card :title="chartTitle" class="line-chart-card">
            <div class="chart-toolbar">
              <a-radio-group v-model:value="lineMetric" size="small" @change="renderLineChart">
                <a-radio-button value="agentCount">{{ metricLabelMap[activeTab].agentCount }}</a-radio-button>
                <a-radio-button value="count">{{ metricLabelMap[activeTab].count }}</a-radio-button>
                <a-radio-button value="tokens">{{ metricLabelMap[activeTab].tokens }}</a-radio-button>
                <a-radio-button value="avgTokens">{{ metricLabelMap[activeTab].avgTokens }}</a-radio-button>
              </a-radio-group>
            </div>
            <div ref="lineChartRef" class="chart-container"></div>
          </a-card>
        </div>

        <div class="bottom-row">
          <a-card :title="pieChartTitle" class="pie-chart-card">
            <div ref="pieChartRef" class="chart-container"></div>
          </a-card>

          <a-card :title="rankTitle" class="rank-card">
            <div class="rank-list" v-if="agentRankList.length > 0">
              <div class="rank-item" v-for="(item, index) in agentRankList" :key="item.id">
                <div class="rank-index" :class="{ top: index < 3 }">{{ index + 1 }}</div>
                <template v-if="activeTab === 'agent'">
                  <a-avatar
                    v-if="item.avatar"
                    :src="item.avatar"
                    :size="32"
                    class="rank-avatar"
                  />
                  <a-avatar v-else :size="32" class="rank-avatar rank-avatar-default">
                    {{ item.name?.charAt(0)?.toUpperCase() }}
                  </a-avatar>
                </template>
                <div class="rank-info">
                  <div class="rank-name">{{ item.name }}</div>
                  <a-progress :percent="item.percent" size="small" :show-info="false" />
                </div>
                <div class="rank-value">{{ item.value }}</div>
              </div>
            </div>
            <div v-else class="empty-rank">
              <a-empty description="暂无数据" />
            </div>
          </a-card>
        </div>
      </a-spin>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import dayjs, { type Dayjs } from 'dayjs'
import { ArrowUpOutlined, ArrowDownOutlined } from '@ant-design/icons-vue'
import * as echarts from 'echarts'
import type { ECharts } from 'echarts'
import { getAdminUsageStats } from '@/api/usageStats'
import type { UsageStats, MetricValue, TimePoint, NameValue } from '@/types'

type TabKey = 'agent' | 'knowledge' | 'mcp'
type LineMetric = 'count' | 'agentCount' | 'tokens' | 'avgTokens'

const activeTab = ref<TabKey>('agent')
const dateRange = ref<[Dayjs, Dayjs]>([dayjs().subtract(7, 'day'), dayjs()])
const lineMetric = ref<LineMetric>('agentCount')
const loading = ref(false)
const statsData = ref<UsageStats | null>(null)

const lineChartRef = ref<HTMLDivElement | null>(null)
const pieChartRef = ref<HTMLDivElement | null>(null)
let lineChart: ECharts | null = null
let pieChart: ECharts | null = null

const metricKeyMap: Record<LineMetric, string> = {
  count: 'count',
  agentCount: 'agentCount',
  tokens: 'tokens',
  avgTokens: 'avgTokens'
}

const metricLabelMap: Record<TabKey, Record<LineMetric, string>> = {
  agent: {
    count: '使用成功次数',
    agentCount: '对话次数',
    tokens: 'Token 总量',
    avgTokens: '平均单次请求 Token 量'
  },
  knowledge: {
    count: '检索次数',
    agentCount: '使用知识库数',
    tokens: 'Chunk 总量',
    avgTokens: '平均每次检索 Chunk 量'
  },
  mcp: {
    count: '调用次数',
    agentCount: '使用 MCP 工具数',
    tokens: '成功调用次数',
    avgTokens: '平均执行耗时'
  }
}

const presets = [
  { label: '最近3小时', value: [dayjs().subtract(3, 'hour'), dayjs()] },
  { label: '最近6小时', value: [dayjs().subtract(6, 'hour'), dayjs()] },
  { label: '最近12小时', value: [dayjs().subtract(12, 'hour'), dayjs()] },
  { label: '最近24小时', value: [dayjs().subtract(24, 'hour'), dayjs()] },
  { label: '今天', value: [dayjs().startOf('day'), dayjs()] },
  { label: '昨天', value: [dayjs().subtract(1, 'day').startOf('day'), dayjs().subtract(1, 'day').endOf('day')] },
  { label: '最近7天', value: [dayjs().subtract(7, 'day'), dayjs()] },
  { label: '最近30天', value: [dayjs().subtract(30, 'day'), dayjs()] }
]

interface MetricCardItem {
  key: string
  label: string
  value: number
  displayValue: string | number
  unit: string
  trend: number
}

const metricCards = ref<MetricCardItem[]>([
  { key: 'agentCount', label: '对话次数', value: 0, displayValue: 0, unit: '个', trend: 0 },
  { key: 'successCount', label: '使用成功次数', value: 0, displayValue: 0, unit: '次', trend: 0 },
  { key: 'tokenCount', label: 'Token 总量', value: 0, displayValue: 0, unit: 'tokens', trend: 0 },
  { key: 'avgTokens', label: '平均单次请求 Token 量', value: 0, displayValue: 0, unit: 'tokens', trend: 0 }
])

const trendMap = ref<Record<string, TimePoint[]>>({})
const prevTrendMap = ref<Record<string, TimePoint[]>>({})
const pieData = ref<NameValue[]>([])

const chartTitle = computed(() => {
  return `${metricLabelMap[activeTab.value][lineMetric.value]}分布`
})

const pieChartTitle = computed(() => {
  const map: Record<TabKey, string> = {
    agent: 'MioBot 调用次数分布',
    knowledge: '知识库检索次数分布',
    mcp: 'MCP 调用次数分布'
  }
  return map[activeTab.value]
})

const rankTitle = computed(() => {
  const map: Record<TabKey, string> = {
    agent: 'MioBot 调用排行榜',
    knowledge: '知识库检索排行榜',
    mcp: 'MCP 调用排行榜'
  }
  return map[activeTab.value]
})

const agentRankList = computed(() => {
  const max = Math.max(...pieData.value.map(d => d.value), 1)
  return pieData.value.map(item => ({
    id: item.id,
    name: item.name,
    avatar: item.avatar,
    value: item.value,
    percent: Math.round((item.value / max) * 100)
  }))
})

const lineMetricMap: Record<LineMetric, { color: string }> = {
  count: { color: '#2aa1a9' },
  agentCount: { color: '#5470c6' },
  tokens: { color: '#91cc75' },
  avgTokens: { color: '#fac858' }
}

function formatNumberWithUnit(value: number): { value: string; unit: string } {
  if (value >= 1_000_000_000) {
    return { value: String(Math.round(value / 1_000_000_000)), unit: 'B' }
  }
  if (value >= 1_000_000) {
    return { value: String(Math.round(value / 1_000_000)), unit: 'M' }
  }
  if (value >= 1_000) {
    return { value: String(Math.round(value / 1_000)), unit: 'K' }
  }
  return { value: String(value), unit: '' }
}

function buildMetricCards(metrics: Record<string, MetricValue>, type: TabKey) {
  const labelMap: Record<TabKey, Record<string, string>> = {
    agent: {
      agentCount: '对话次数',
      successCount: '使用成功次数',
      tokenCount: 'Token 总量',
      avgTokens: '平均单次请求 Token 量'
    },
    knowledge: {
      agentCount: '使用知识库数',
      successCount: '检索次数',
      tokenCount: 'Chunk 总量',
      avgTokens: '平均每次检索 Chunk 量'
    },
    mcp: {
      agentCount: '使用 MCP 工具数',
      successCount: '调用次数',
      tokenCount: '成功调用次数',
      avgTokens: '平均执行耗时'
    }
  }

  const units: Record<TabKey, Record<string, string>> = {
    agent: { agentCount: '个', successCount: '次', tokenCount: 'tokens', avgTokens: 'tokens' },
    knowledge: { agentCount: '个', successCount: '次', tokenCount: 'chunks', avgTokens: 'chunks' },
    mcp: { agentCount: '个', successCount: '次', tokenCount: '次', avgTokens: 'ms' }
  }

  metricCards.value = ['agentCount', 'successCount', 'tokenCount', 'avgTokens'].map(key => {
    const metric = metrics[key]
    const rawValue = metric?.value ?? 0
    const isTokenLike = key === 'tokenCount' || key === 'avgTokens'
    const formatted = isTokenLike ? formatNumberWithUnit(rawValue) : null
    const baseUnit = units[type][key]
    return {
      key,
      label: labelMap[type][key],
      value: rawValue,
      displayValue: formatted ? `${formatted.value}${formatted.unit}` : rawValue,
      unit: baseUnit,
      trend: metric?.trend ?? 0
    }
  })
}

async function fetchStats() {
  if (!dateRange.value || dateRange.value.length !== 2) return

  loading.value = true
  try {
    const [start, end] = dateRange.value
    const data = await getAdminUsageStats(activeTab.value, {
      startTime: start.format('YYYY-MM-DD HH:mm:ss'),
      endTime: end.format('YYYY-MM-DD HH:mm:ss')
    })
    statsData.value = data

    buildMetricCards(data.metrics, activeTab.value)
    trendMap.value = data.trendMap || {}
    prevTrendMap.value = data.prevTrendMap || {}
    if (Object.keys(trendMap.value).length === 0 && data.trend && data.trend.length > 0) {
      trendMap.value = { count: data.trend }
    }
    pieData.value = data.distribution || []

    nextTick(() => {
      renderLineChart()
      renderPieChart()
    })
  } catch (e: any) {
    console.error('获取使用记录统计失败', e)
  } finally {
    loading.value = false
  }
}

function renderLineChart() {
  ensureCharts()
  if (!lineChartRef.value || !lineChart) return
  lineChart.clear()
  lineChart.resize()

  const metric = lineMetricMap[lineMetric.value]
  const metricKey = metricKeyMap[lineMetric.value]

  const currentTrend = trendMap.value[metricKey] || []
  const prevTrend = prevTrendMap.value[metricKey] || []

  if (currentTrend.length === 0) {
    lineChart.setOption({
      title: {
        text: '暂无数据',
        left: 'center',
        top: 'center',
        textStyle: { color: '#8c8a82', fontSize: 16, fontWeight: 'normal' }
      },
      xAxis: { type: 'category', data: [] },
      yAxis: { type: 'value' },
      series: [],
      graphic: []
    })
    return
  }

  const times = currentTrend.map(p => p.time)
  const currentValues = currentTrend.map(p => Number(p.value))
  const prevValues = times.map((_, index) => {
    const prevPoint = prevTrend[index]
    return prevPoint ? Number(prevPoint.value) : 0
  })

  lineChart.setOption({
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255,255,255,0.95)',
      borderColor: '#e8e6dc',
      textStyle: { color: '#141413' },
      formatter: (params: any) => {
        const index = params[0].dataIndex
        const time = currentTrend[index]?.time ?? '-'
        let html = `<div style="margin-bottom:4px;font-weight:500">${time}</div>`
        params.forEach((p: any) => {
          html += `<div style="margin:2px 0">
            <span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:${p.color};margin-right:6px"></span>
            <span><strong>${p.value}</strong></span>
          </div>`
        })
        return html
      }
    },
    legend: { top: 0, right: 0, data: ['本周期', '上周期'] },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '10%', containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: times,
      axisLine: { lineStyle: { color: '#e8e6dc' } },
      axisLabel: { color: '#6e6b62' }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#ece9de' } },
      axisLabel: { color: '#6e6b62' }
    },
    series: [
      {
        name: '本周期',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 8,
        showSymbol: false,
        lineStyle: { width: 3, color: metric.color },
        itemStyle: { color: metric.color },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: `${metric.color}40` },
            { offset: 1, color: `${metric.color}05` }
          ])
        },
        data: currentValues
      },
      {
        name: '上周期',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        showSymbol: false,
        lineStyle: { width: 2, color: '#8c8a82', type: 'dashed' },
        itemStyle: { color: '#8c8a82' },
        data: prevValues
      }
    ]
  })
}

function renderPieChart() {
  ensureCharts()
  if (!pieChartRef.value || !pieChart) return
  pieChart.clear()
  pieChart.resize()

  if (pieData.value.length === 0) {
    pieChart.setOption({
      title: {
        text: '暂无数据',
        left: 'center',
        top: 'center',
        textStyle: { color: '#8c8a82', fontSize: 16, fontWeight: 'normal' }
      },
      series: []
    })
    return
  }

  pieChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: '0%', left: 'center', icon: 'circle' },
    series: [{
      name: '调用次数',
      type: 'pie',
      radius: ['45%', '70%'],
      center: ['50%', '45%'],
      avoidLabelOverlap: false,
      itemStyle: {
        borderRadius: 6,
        borderColor: '#fff',
        borderWidth: 2
      },
      label: { show: false, position: 'center' },
      emphasis: {
        label: {
          show: true,
          fontSize: 16,
          fontWeight: 'bold',
          formatter: '{b}\n{c}'
        }
      },
      labelLine: { show: false },
      data: pieData.value
    }]
  })
}

function ensureCharts() {
  if (!lineChart && lineChartRef.value) {
    lineChart = echarts.init(lineChartRef.value)
  }
  if (!pieChart && pieChartRef.value) {
    pieChart = echarts.init(pieChartRef.value)
  }
}

function disposeCharts() {
  lineChart?.dispose()
  pieChart?.dispose()
  lineChart = null
  pieChart = null
}

function onDateChange() {
  fetchStats()
}

function onTabChange() {
  fetchStats()
}

function handleResize() {
  lineChart?.resize()
  pieChart?.resize()
}

onMounted(() => {
  nextTick(() => {
    ensureCharts()
    fetchStats()
  })
})

onUnmounted(() => {
  disposeCharts()
  window.removeEventListener('resize', handleResize)
})

watch(lineMetric, () => {
  nextTick(() => renderLineChart())
})

watch(activeTab, () => {
  lineMetric.value = 'agentCount'
})

window.addEventListener('resize', handleResize)
</script>

<style lang="scss" scoped>
.usage-record-page {
  height: 100%;
  display: flex;
  flex-direction: column;

  .page-header {
    flex-shrink: 0;
    background: #f5f3ec;

    .header-content {
      padding: 16px 24px;
      display: flex;
      justify-content: space-between;
      align-items: center;

      .header-left {
        h2 {
          font-size: 24px;
          font-weight: 600;
          color: #141413;
          margin: 0;
        }
      }
    }

    .header-line {
      height: 1px;
      background: #e8e6dc;
    }
  }

  .tabs-header {
    flex-shrink: 0;
    background: #f5f3ec;
    padding: 0 24px;
    border-bottom: 1px solid #e8e6dc;

    .usage-tabs {
      :deep(.ant-tabs-nav) {
        margin-bottom: 0;
      }

      :deep(.ant-tabs-tab) {
        padding: 12px 16px;
      }
    }
  }

  .page-scroll-content {
    max-height: 730px;
    flex: 1;
    overflow-y: auto;
    padding: 24px;
    background: #f0eee6;
    -ms-overflow-style: none;
    scrollbar-width: none;
  }

  .top-section {
    margin-bottom: 20px;

    .filter-bar {
      margin-bottom: 16px;
      display: flex;
      align-items: center;
      gap: 12px;

      .filter-label {
        font-size: 14px;
        color: #141413;
        font-weight: 500;
      }
    }

    .metrics-row {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 16px;

      .metric-card {
        border-radius: 8px;
        box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);

        :deep(.ant-statistic-title) {
          font-size: 13px;
          color: #6e6b62;
          margin-bottom: 8px;
        }

        :deep(.ant-statistic-content) {
          font-size: 28px;
          font-weight: 600;
          color: #141413;
        }

        :deep(.ant-statistic-content-suffix) {
          font-size: 16px;
          font-weight: 400;
          color: #6e6b62;
          margin-left: 4px;
        }

        .trend-row {
          margin-top: 8px;
          display: flex;
          align-items: center;
          gap: 8px;

          .trend {
            font-size: 13px;
            font-weight: 500;
            display: inline-flex;
            align-items: center;
            gap: 2px;

            &.up {
              color: #a83a30;
            }

            &.down {
              color: #3f8600;
            }
          }

          .trend-label {
            font-size: 12px;
            color: #8c8a82;
          }
        }
      }
    }
  }

  .charts-row {
    margin-bottom: 20px;

    .line-chart-card {
      :deep(.ant-card-head-title) {
        font-weight: 600;
      }

      .chart-toolbar {
        margin-bottom: 12px;
        display: flex;
        justify-content: flex-end;
      }

      .chart-container {
        width: 100%;
        height: 320px;
      }
    }
  }

  .bottom-row {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 16px;

    .pie-chart-card,
    .rank-card {
      :deep(.ant-card-head-title) {
        font-weight: 600;
      }
    }

    .chart-container {
      width: 100%;
      height: 320px;
    }

    .empty-rank {
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 200px;
    }

    .rank-list {
      .rank-item {
        display: flex;
        align-items: center;
        padding: 10px 0;
        border-bottom: 1px solid #ece9de;

        &:last-child {
          border-bottom: none;
        }

        .rank-index {
          width: 24px;
          height: 24px;
          border-radius: 50%;
          background: #ece9de;
          color: #6e6b62;
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 12px;
          font-weight: 500;
          margin-right: 12px;
          flex-shrink: 0;

          &.top {
            background: #2aa1a9;
            color: #fff;
          }
        }

        .rank-avatar {
          margin-right: 12px;
          flex-shrink: 0;

          &.rank-avatar-default {
            background: #2aa1a9;
            color: #fff;
            font-size: 13px;
          }
        }

        .rank-info {
          flex: 1;
          min-width: 0;

          .rank-name {
            font-size: 14px;
            color: #141413;
            margin-bottom: 4px;
          }
        }

        .rank-value {
          font-size: 14px;
          font-weight: 500;
          color: #141413;
          margin-left: 16px;
          flex-shrink: 0;
        }
      }
    }
  }
}

@media (max-width: 1200px) {
  .usage-record-page {
    .top-section {
      .metrics-row {
        grid-template-columns: repeat(2, 1fr);
      }
    }

    .bottom-row {
      grid-template-columns: 1fr;
    }
  }
}
</style>