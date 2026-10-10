<template>
  <div class="dash-page usage-page">
    <PageHeader title="使用记录" />

    <div class="tabs-header">
      <a-tabs v-model:activeKey="activeTab" class="usage-tabs">
        <a-tab-pane key="agent" tab="MioBot"></a-tab-pane>
        <a-tab-pane key="knowledge" tab="知识库"></a-tab-pane>
        <a-tab-pane key="mcp" tab="MCP"></a-tab-pane>
      </a-tabs>
    </div>

    <div class="page-scroll-content">
      <a-spin :spinning="loading">
        <div class="filter-bar">
          <span class="filter-label">选择时间</span>
          <a-range-picker
            v-model:value="dateRange"
            :presets="datePresets"
            :placeholder="['开始时间', '结束时间']"
            format="YYYY-MM-DD HH:mm:ss"
            show-time
            @change="fetchStats"
          />
        </div>

        <UsageMetricCards :cards="metricCards" />

        <div class="charts-row">
          <a-card :title="chartTitle" class="chart-card">
            <div class="chart-toolbar">
              <a-radio-group v-model:value="lineMetric" size="small">
                <a-radio-button value="agentCount">{{ metricLabelMap[activeTab].agentCount }}</a-radio-button>
                <a-radio-button value="count">{{ metricLabelMap[activeTab].count }}</a-radio-button>
                <a-radio-button value="tokens">{{ metricLabelMap[activeTab].tokens }}</a-radio-button>
                <a-radio-button value="avgTokens">{{ metricLabelMap[activeTab].avgTokens }}</a-radio-button>
              </a-radio-group>
            </div>
            <UsageTrendChart
              :current="trendMap[lineMetric] || []"
              :prev="prevTrendMap[lineMetric] || []"
              :color="lineMetricColorMap[lineMetric]"
            />
          </a-card>
        </div>

        <div class="bottom-row">
          <a-card :title="pieChartTitle" class="chart-card">
            <UsageDistPie :data="pieData" />
          </a-card>

          <a-card :title="rankTitle" class="chart-card">
            <UsageRankList :items="rankList" :show-avatar="activeTab === 'agent'" />
          </a-card>
        </div>
      </a-spin>
    </div>
  </div>
</template>

<script setup lang="ts">
import PageHeader from '../components/PageHeader.vue'
import UsageMetricCards from './components/UsageMetricCards.vue'
import UsageTrendChart from './components/UsageTrendChart.vue'
import UsageDistPie from './components/UsageDistPie.vue'
import UsageRankList from './components/UsageRankList.vue'
import {
  useUsageStats,
  metricLabelMap,
  lineMetricColorMap,
  datePresets
} from './composables/useUsageStats'

const {
  activeTab,
  dateRange,
  loading,
  metricCards,
  trendMap,
  prevTrendMap,
  pieData,
  chartTitle,
  pieChartTitle,
  rankTitle,
  rankList,
  fetchStats,
  lineMetric
} = useUsageStats()
</script>

<style lang="scss" scoped>
.usage-page {
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

    &::-webkit-scrollbar {
      display: none;
    }
  }

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

  .charts-row {
    margin-bottom: 20px;
  }

  .chart-card {
    :deep(.ant-card-head-title) {
      font-weight: 600;
    }

    .chart-toolbar {
      margin-bottom: 12px;
      display: flex;
      justify-content: flex-end;
    }
  }

  .bottom-row {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 16px;

    @media (max-width: 1200px) {
      grid-template-columns: 1fr;
    }
  }
}
</style>
