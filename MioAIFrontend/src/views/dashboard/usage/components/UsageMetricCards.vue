<template>
  <div class="metrics-row">
    <div class="metric-card" v-for="item in cards" :key="item.key">
      <div class="metric-label">{{ item.label }}</div>
      <div class="metric-value">
        <span class="value">{{ item.displayValue }}</span>
        <span class="unit" v-if="item.unit">{{ item.unit }}</span>
      </div>
      <div class="trend-row" v-if="item.trend !== 0">
        <span class="trend" :class="item.trend > 0 ? 'up' : 'down'">
          <ArrowUpOutlined v-if="item.trend > 0" />
          <ArrowDownOutlined v-else />
          {{ Math.abs(item.trend).toFixed(1) }}%
        </span>
        <span class="trend-label">较上周期</span>
      </div>
      <div class="trend-row" v-else>
        <span class="trend-label">与上周期持平</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ArrowUpOutlined, ArrowDownOutlined } from '@ant-design/icons-vue'

defineProps<{
  cards: {
    key: string
    label: string
    displayValue: string | number
    unit: string
    trend: number
  }[]
}>()
</script>

<style lang="scss" scoped>
.metrics-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;

  .metric-card {
    background: #faf9f5;
    border: 1px solid #e8e6dc;
    border-radius: 12px;
    padding: 16px 18px;
    display: flex;
    flex-direction: column;
    gap: 6px;

    .metric-label {
      font-size: 13px;
      color: #6e6b62;
    }

    .metric-value {
      display: flex;
      align-items: baseline;
      gap: 5px;

      .value {
        font-size: 26px;
        font-weight: 600;
        color: #141413;
        line-height: 1.2;
        font-variant-numeric: tabular-nums;
      }

      .unit {
        font-size: 13px;
        color: #8c8a82;
      }
    }

    .trend-row {
      display: flex;
      align-items: center;
      gap: 6px;
      min-height: 18px;

      .trend {
        font-size: 12px;
        font-weight: 500;
        display: inline-flex;
        align-items: center;
        gap: 2px;

        &.up {
          color: #c0453a;
        }

        &.down {
          color: #4f9e63;
        }
      }

      .trend-label {
        font-size: 12px;
        color: #8c8a82;
      }
    }
  }
}

@media (max-width: 1200px) {
  .metrics-row {
    grid-template-columns: repeat(2, 1fr);
  }
}

// ≤768px 的移动端单列见 mobile/styles/dashboard.scss
</style>
