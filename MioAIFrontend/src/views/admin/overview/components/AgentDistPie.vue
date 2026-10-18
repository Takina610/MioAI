<template>
  <div class="dist-pie" v-if="items.length > 0">
    <div class="donut-box">
      <BaseDonutChart
        :items="donutItems"
        :colors="palette"
        center-label="调用总量"
        :format-value="formatNumber"
        unit="次"
        :height="260"
      />
    </div>
    <div class="pie-legend">
      <div class="legend-item" v-for="(entry, index) in items" :key="entry.id">
        <span class="legend-avatar" v-if="entry.avatar">
          <a-avatar :src="entry.avatar" :size="18" />
        </span>
        <BotIcon
          v-else-if="iconConfig(entry)"
          :shape="iconConfig(entry)!.shape"
          :fill="iconConfig(entry)!.fill"
          :size="20"
          :live="false"
        />
        <span class="dot" v-else :style="{ background: legendColor(index) }"></span>
        <span class="legend-name" :title="entry.name">{{ entry.name }}</span>
        <span class="legend-value">{{ formatNumber(Number(entry.value)) }}</span>
        <span class="legend-percent">{{ percentOf(index) }}%</span>
      </div>
    </div>
  </div>
  <div v-else class="chart-empty">暂无数据</div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { NameValue } from '@/types'
import type { BotIconConfig } from '@/components/bot-icon/types'
import BotIcon from '@/components/bot-icon/BotIcon.vue'
import BaseDonutChart from '@/components/charts/BaseDonutChart.vue'
import { ECHARTS_PALETTE } from '@/views/dashboard/usage/composables/echartsTheme'
import { formatNumber } from '@/views/dashboard/usage/composables/format'

const props = defineProps<{
  items: NameValue[]
}>()

const palette = ECHARTS_PALETTE

const donutItems = computed(() =>
  props.items.map(entry => ({ name: entry.name, value: Number(entry.value) }))
)

const totalValue = computed(() => props.items.reduce((sum, entry) => sum + Number(entry.value), 0))

function legendColor(index: number): string {
  return palette[index % palette.length]
}

function percentOf(index: number): string {
  if (totalValue.value === 0) return '0'
  return ((Number(props.items[index].value) / totalValue.value) * 100)
    .toFixed(1)
    .replace(/\.0$/, '')
}

/** bot-icon 配置解析：损坏/缺失配置返回 null 回退圆点 */
function iconConfig(item: NameValue): BotIconConfig | null {
  if (!item.icon) return null
  try {
    const parsed = JSON.parse(item.icon) as Partial<BotIconConfig>
    if (parsed && typeof parsed.shape === 'string' && parsed.shape) {
      return parsed as BotIconConfig
    }
  } catch {
    // 非 JSON 配置按无图标处理
  }
  return null
}
</script>

<style lang="scss" scoped>
.dist-pie {
  display: flex;
  align-items: center;
  gap: 12px;
}

.donut-box {
  flex: 0 0 46%;
  min-width: 0;
}

.pie-legend {
  flex: 1;
  min-width: 0;
  max-height: 260px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  padding-right: 4px;

  .legend-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 7px 8px;
    border-radius: 8px;

    &:hover {
      background: #f5f3ec;
    }

    .dot {
      width: 9px;
      height: 9px;
      border-radius: 50%;
      flex-shrink: 0;
    }

    .legend-avatar {
      flex-shrink: 0;
      display: flex;
    }

    .legend-name {
      flex: 1;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-size: 13px;
      color: $text-dark;
    }

    .legend-value {
      font-size: 13px;
      color: $text-secondary;
      font-variant-numeric: tabular-nums;
    }

    .legend-percent {
      font-size: 12px;
      color: $text-muted;
      width: 44px;
      text-align: right;
      font-variant-numeric: tabular-nums;
    }
  }
}

.chart-empty {
  height: 260px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $text-muted;
  font-size: 14px;
  width: 100%;
}
</style>
