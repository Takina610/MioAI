<template>
  <div class="dash-page usage-page">
    <PageHeader title="使用记录" />

    <div class="tabs-header">
      <a-tabs v-model:activeKey="activeTab" class="usage-tabs">
        <a-tab-pane key="agent" tab="智能体"></a-tab-pane>
        <a-tab-pane key="knowledge" tab="知识库"></a-tab-pane>
        <a-tab-pane key="mcp" tab="MCP"></a-tab-pane>
        <a-tab-pane key="skill" tab="技能"></a-tab-pane>
      </a-tabs>
    </div>

    <div class="page-scroll-content">
      <!-- Token 概览：智能体专属（贡献图 + 五项汇总 + 趋势 + 模型用量） -->
      <template v-if="activeTab === 'agent'">
        <a-card title="Token 概览" class="overview-card">
          <template #extra>
            <a-radio-group v-model:value="contributionMode" size="small">
              <a-radio-button value="daily">每日</a-radio-button>
              <a-radio-button value="weekly">每周</a-radio-button>
              <a-radio-button value="cumulative">累计</a-radio-button>
            </a-radio-group>
          </template>
          <TokenSummaryChips :summary="contribution?.summary ?? null" />
          <ContributionGraph
            :days="contribution?.days ?? []"
            :mode="contributionMode"
            :selected-date="selectedDate"
            @select-date="selectDate"
          />
        </a-card>

        <UsageDayDetailCard
          v-if="selectedDate"
          :date="selectedDate"
          :detail="dayDetail"
          :loading="dayLoading"
          :measure-label="analytics?.measureLabel ?? 'Token 数'"
          :measure-unit="analytics?.measureUnit ?? 'tokens'"
          @close="clearDate"
        />

        <!-- 每日 Token 趋势 + 模型/智能体用量 -->
        <div class="insight-grid">
          <a-card title="每日 Token 趋势" class="chart-card">
            <template #extra>
              <div class="card-toggles">
                <a-radio-group v-model:value="tokenDimension" size="small">
                  <a-radio-button value="model">模型</a-radio-button>
                  <a-radio-button value="agent">智能体</a-radio-button>
                </a-radio-group>
                <a-radio-group v-model:value="tokenRange" size="small">
                  <a-radio-button :value="7">最近7天</a-radio-button>
                  <a-radio-button :value="30">最近30天</a-radio-button>
                </a-radio-group>
              </div>
            </template>
            <a-spin :spinning="trendLoading">
              <TokenTrendChart :series="trend?.series ?? []" />
            </a-spin>
          </a-card>

          <a-card :title="tokenDimension === 'model' ? '模型用量' : '智能体用量'" class="chart-card">
            <ModelUsagePie :series="trend?.series ?? []" />
          </a-card>
        </div>

        <!-- 数据画像：智能体 tab 专属（与 Token 概览同属用户级聚合） -->
        <UsageProfileCard :profile="profile" />
      </template>

      <!-- Tab 分类统计 -->
      <a-spin :spinning="loading">
        <div class="tab-stats">
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

          <div class="bottom-row">
            <a-card :title="pieChartTitle" class="chart-card">
              <UsageDistPie :data="pieData" />
            </a-card>

            <a-card :title="rankTitle" class="chart-card">
              <UsageRankList :items="rankList" :show-avatar="activeTab === 'agent'" />
            </a-card>
          </div>

          <UsageAnalyticsSection :analytics="analytics" :loading="analyticsLoading" />
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
import TokenSummaryChips from './components/TokenSummaryChips.vue'
import ContributionGraph from './components/ContributionGraph.vue'
import TokenTrendChart from './components/TokenTrendChart.vue'
import ModelUsagePie from './components/ModelUsagePie.vue'
import UsageProfileCard from './components/UsageProfileCard.vue'
import UsageAnalyticsSection from './components/UsageAnalyticsSection.vue'
import UsageDayDetailCard from './components/UsageDayDetailCard.vue'
import {
  useUsageStats,
  metricLabelMap,
  lineMetricColorMap,
  datePresets
} from './composables/useUsageStats'
import { useTokenInsights } from './composables/useTokenInsights'
import { useUsageAnalytics } from './composables/useUsageAnalytics'
import { getUsageAnalytics, getUsageDayDetail } from '@/api/usageStats'

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

const {
  profile,
  contribution,
  contributionMode,
  trend,
  trendLoading,
  tokenRange,
  tokenDimension
} = useTokenInsights()

const {
  analytics,
  loading: analyticsLoading,
  selectedDate,
  dayDetail,
  dayLoading,
  selectDate,
  clearDate
} = useUsageAnalytics(
  { analytics: getUsageAnalytics, dayDetail: getUsageDayDetail },
  { type: activeTab, range: dateRange }
)
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
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    padding: 20px 24px 24px;
    background: #f0eee6;
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .overview-card {
    border-radius: 12px;

    :deep(.ant-card-head-title) {
      font-weight: 600;
    }
  }

  .insight-grid {
    display: grid;
    grid-template-columns: 3fr 2fr;
    gap: 16px;
    align-items: start;

    > :deep(.ant-card) {
      min-width: 0;
    }
  }

  .filter-bar {
    display: flex;
    align-items: center;
    gap: 12px;

    .filter-label {
      font-size: 14px;
      color: #141413;
      font-weight: 500;
    }
  }

  .chart-card {
    border-radius: 12px;

    :deep(.ant-card-head-title) {
      font-weight: 600;
    }

    .chart-toolbar {
      margin-bottom: 12px;
      display: flex;
      justify-content: flex-end;
    }

    :deep(.ant-card-extra) {
      .card-toggles {
        display: flex;
        gap: 8px;
      }
    }
  }

  .tab-stats {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .bottom-row {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 16px;
    align-items: start;

    > :deep(.ant-card) {
      min-width: 0;
    }
  }
}

@media (max-width: 1200px) {
  .usage-page {
    .insight-grid,
    .bottom-row {
      grid-template-columns: 1fr;
    }
  }
}
</style>
