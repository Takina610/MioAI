<template>
  <a-spin :spinning="loading">
    <div v-if="detail" class="day-detail">
      <div class="day-chips">
        <div class="chip">
          <span class="chip-label">Token</span>
          <span class="chip-value">{{ formatCompact(detail.tokens) }}</span>
        </div>
        <div class="chip">
          <span class="chip-label">输入 / 输出</span>
          <span class="chip-value">{{ formatCompact(detail.inputTokens) }} / {{ formatCompact(detail.outputTokens) }}</span>
        </div>
        <div class="chip">
          <span class="chip-label">轮次</span>
          <span class="chip-value">{{ detail.calls.toLocaleString() }}</span>
        </div>
        <div class="chip">
          <span class="chip-label">会话</span>
          <span class="chip-value">{{ detail.conversations.toLocaleString() }}</span>
        </div>
        <div class="chip">
          <span class="chip-label">聊天时长</span>
          <span class="chip-value">{{ formatDuration(detail.chatDurationMs) }}</span>
        </div>
      </div>

      <div class="day-grid">
        <div class="day-block">
          <div class="block-title">时段分布</div>
          <div v-show="hasHours" ref="chartRef" class="hour-chart"></div>
          <div v-if="!hasHours" class="block-empty">当天没有记录</div>
        </div>

        <div class="day-block">
          <div class="block-title">智能体</div>
          <div class="mini-list" v-if="detail.agents.length > 0">
            <div class="mini-item" v-for="item in detail.agents" :key="item.id">
              <span class="mini-name">{{ item.name }}</span>
              <span class="mini-value">{{ formatCompact(Number(item.value)) }}</span>
              <span class="mini-calls">{{ item.calls ?? 0 }} 次</span>
            </div>
          </div>
          <div v-else class="block-empty">无</div>
        </div>

        <div class="day-block">
          <div class="block-title">模型</div>
          <div class="mini-list" v-if="detail.models.length > 0">
            <div class="mini-item" v-for="item in detail.models" :key="item.id">
              <span class="mini-name">{{ item.name }}</span>
              <span class="mini-value">{{ formatCompact(Number(item.value)) }}</span>
              <span class="mini-calls">{{ item.calls ?? 0 }} 次</span>
            </div>
          </div>
          <div v-else class="block-empty">无</div>
        </div>
      </div>

      <div class="resource-strip">
        <div class="resource-item">
          <span class="resource-label">智能体</span>
          <span class="resource-value">{{ detail.agentCalls }} 次 / {{ detail.agentCount }} 个</span>
        </div>
        <div class="resource-item">
          <span class="resource-label">知识库检索</span>
          <span class="resource-value">{{ detail.knowledgeCalls }} 次 / {{ detail.knowledgeCount }} 个</span>
        </div>
        <div class="resource-item">
          <span class="resource-label">MCP 调用</span>
          <span class="resource-value">{{ detail.mcpCalls }} 次 / {{ detail.mcpCount }} 个</span>
        </div>
        <div class="resource-item">
          <span class="resource-label">技能使用</span>
          <span class="resource-value">{{ detail.skillCalls }} 次 / {{ detail.skillCount }} 个</span>
        </div>
      </div>
    </div>
    <div v-else class="day-empty">未选择日期</div>
  </a-spin>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import type { EChartsOption } from 'echarts'
import type { UsageDayDetail } from '@/types'
import { useEChart } from '@/composables/charts/useEChart'
import { formatCompact, formatDuration } from '../composables/format'
import {
  ECHARTS_FONT_FAMILY,
  axisStyle,
  chartTooltip,
  compactAxis,
  escapeHtml
} from '../composables/echartsTheme'

const props = defineProps<{
  detail: UsageDayDetail | null
  loading: boolean
  measureLabel: string
  measureUnit: string
}>()

const chartRef = ref<HTMLElement | null>(null)
const { render } = useEChart(chartRef)

const hasHours = computed(() =>
  (props.detail?.hours ?? []).some(hour => hour.measure > 0 || hour.calls > 0)
)

function buildOption(): EChartsOption {
  const hours = props.detail?.hours ?? []
  return {
    backgroundColor: 'transparent',
    tooltip: {
      ...chartTooltip(),
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: unknown) => {
        const list = params as Array<{ dataIndex: number }>
        const index = list[0]?.dataIndex ?? 0
        const hour = hours[index]
        if (!hour) return ''
        return `<div style="font-weight:500;margin-bottom:2px">${String(hour.hour).padStart(2, '0')}:00</div>`
          + `<div style="color:#6e6b62">${escapeHtml(props.measureLabel)} <b style="color:#141413">${hour.measure.toLocaleString()}</b> ${escapeHtml(props.measureUnit)}</div>`
          + `<div style="color:#6e6b62">记录 <b style="color:#141413">${hour.calls}</b> 条</div>`
      }
    },
    grid: { left: 8, right: 12, top: 16, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      data: hours.map(hour => `${String(hour.hour).padStart(2, '0')}:00`),
      ...axisStyle(),
      splitLine: { show: false },
      axisLabel: {
        color: '#8c8a82',
        fontSize: 10,
        fontFamily: ECHARTS_FONT_FAMILY,
        interval: 2
      }
    },
    yAxis: {
      type: 'value',
      ...axisStyle(),
      axisLabel: {
        color: '#8c8a82',
        fontSize: 10,
        fontFamily: ECHARTS_FONT_FAMILY,
        formatter: (value: number) => compactAxis(value)
      }
    },
    series: [
      {
        type: 'bar',
        data: hours.map(hour => hour.measure),
        barMaxWidth: 18,
        itemStyle: { color: 'rgba(42, 161, 169, 0.75)', borderRadius: [3, 3, 0, 0] },
        emphasis: { itemStyle: { color: '#2aa1a9' } }
      }
    ]
  }
}

function rerender(): void {
  if (hasHours.value) {
    // v-show 切换与容器尺寸更新完成后渲染
    nextTick(() => render(buildOption()))
  }
}

onMounted(rerender)
watch(() => [props.detail, props.measureLabel], rerender, { deep: true })
</script>

<style lang="scss" scoped>
.day-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.day-chips {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;

  .chip {
    background: #f5f3ec;
    border-radius: 10px;
    padding: 10px 12px;
    display: flex;
    flex-direction: column;
    gap: 3px;
  }

  .chip-label {
    font-size: 12px;
    color: #6e6b62;
  }

  .chip-value {
    font-size: 16px;
    font-weight: 600;
    color: #141413;
    font-variant-numeric: tabular-nums;
  }
}

.day-grid {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr;
  gap: 16px;

  .day-block {
    min-width: 0;
    background: #faf9f5;
    border: 1px solid #e8e6dc;
    border-radius: 10px;
    padding: 12px 14px;
  }

  .block-title {
    font-size: 13px;
    font-weight: 500;
    color: #141413;
    margin-bottom: 8px;
  }

  .block-empty {
    font-size: 12px;
    color: #8c8a82;
    padding: 20px 0;
    text-align: center;
  }

  .hour-chart {
    width: 100%;
    height: 170px;
  }

  .mini-list {
    display: flex;
    flex-direction: column;
    gap: 6px;
    max-height: 170px;
    overflow: auto;
  }

  .mini-item {
    display: flex;
    align-items: baseline;
    gap: 8px;
    font-size: 12px;

    .mini-name {
      flex: 1;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      color: #141413;
    }

    .mini-value {
      color: #141413;
      font-weight: 500;
      font-variant-numeric: tabular-nums;
    }

    .mini-calls {
      color: #8c8a82;
      font-size: 11px;
      flex-shrink: 0;
    }
  }
}

.resource-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 22px;
  padding: 10px 14px;
  background: #f5f3ec;
  border-radius: 10px;

  .resource-item {
    display: flex;
    align-items: baseline;
    gap: 6px;
  }

  .resource-label {
    font-size: 12px;
    color: #6e6b62;
  }

  .resource-value {
    font-size: 13px;
    font-weight: 500;
    color: #141413;
    font-variant-numeric: tabular-nums;
  }
}

.day-empty {
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
  color: #8c8a82;
}

@media (max-width: 1200px) {
  .day-chips {
    grid-template-columns: repeat(3, 1fr);
  }

  .day-grid {
    grid-template-columns: 1fr;
  }
}
</style>
