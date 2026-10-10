<template>
  <div class="metrics-row">
    <a-card class="metric-card" v-for="item in cards" :key="item.key" :bordered="false">
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

@media (max-width: 1200px) {
  .metrics-row {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .metrics-row {
    grid-template-columns: 1fr;
  }
}
</style>
