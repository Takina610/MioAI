<template>
  <div class="adm-page usage-admin-page">
    <div class="adm-page-header">
      <span class="adm-page-title">使用统计</span>
    </div>

    <div class="tabs-header">
      <a-tabs v-model:activeKey="activeTab" class="usage-tabs">
        <a-tab-pane key="agent" tab="智能体"></a-tab-pane>
        <a-tab-pane key="knowledge" tab="知识库"></a-tab-pane>
        <a-tab-pane key="mcp" tab="MCP"></a-tab-pane>
        <a-tab-pane key="skill" tab="技能"></a-tab-pane>
      </a-tabs>
    </div>

    <!-- 全用户维度筛选 -->
    <div class="adm-filter-bar">
      <a-input-number
        v-model:value="filterUserId"
        placeholder="用户 ID（可空）"
        class="adm-filter-item"
        :controls="false"
        @press-enter="fetchStats"
      />
      <a-input-number
        v-if="activeTab === 'agent'"
        v-model:value="filterAgentId"
        placeholder="智能体 ID（可空）"
        class="adm-filter-item"
        :controls="false"
        @press-enter="fetchStats"
      />
      <a-button type="primary" @click="fetchStats">查询</a-button>
    </div>

    <!-- Token 概览：智能体 tab（全用户聚合） -->
    <template v-if="activeTab === 'agent'">
      <a-card title="Token 概览（全用户）" class="chart-card">
        <template #extra>
          <a-radio-group v-model:value="contributionMode" size="small">
            <a-radio-button value="daily">每日</a-radio-button>
            <a-radio-button value="weekly">每周</a-radio-button>
            <a-radio-button value="cumulative">累计</a-radio-button>
          </a-radio-group>
        </template>
        <a-spin :spinning="insightsLoading">
          <TokenSummaryChips :summary="contribution?.summary ?? null" />
          <ContributionGraph :days="contribution?.days ?? []" :mode="contributionMode" />
        </a-spin>
      </a-card>

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
    </template>

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
      </div>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import UsageMetricCards from '@/views/dashboard/usage/components/UsageMetricCards.vue'
import UsageTrendChart from '@/views/dashboard/usage/components/UsageTrendChart.vue'
import UsageDistPie from '@/views/dashboard/usage/components/UsageDistPie.vue'
import UsageRankList from '@/views/dashboard/usage/components/UsageRankList.vue'
import TokenSummaryChips from '@/views/dashboard/usage/components/TokenSummaryChips.vue'
import ContributionGraph from '@/views/dashboard/usage/components/ContributionGraph.vue'
import TokenTrendChart from '@/views/dashboard/usage/components/TokenTrendChart.vue'
import ModelUsagePie from '@/views/dashboard/usage/components/ModelUsagePie.vue'
import {
  useUsageStats,
  metricLabelMap,
  lineMetricColorMap,
  datePresets
} from '@/views/dashboard/usage/composables/useUsageStats'
import { useTokenInsights } from '@/views/dashboard/usage/composables/useTokenInsights'
import { getAdminUsageStats, getAdminContribution, getAdminTokenTrend } from '@/api/usageStats'

// 全用户维度筛选：注入 admin 数据源，闭包读取最新筛选值
const filterUserId = ref<number | undefined>(undefined)
const filterAgentId = ref<number | undefined>(undefined)

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
} = useUsageStats((type, params) =>
  getAdminUsageStats(type, {
    ...params,
    userId: filterUserId.value || undefined,
    agentId: filterAgentId.value || undefined
  })
)

const {
  contribution,
  contributionMode,
  trend,
  trendLoading,
  tokenRange,
  tokenDimension
} = useTokenInsights({
  contribution: (year?: number) => getAdminContribution(year),
  tokenTrend: (days, dimension) => getAdminTokenTrend(days, dimension),
  withProfile: false
})

const insightsLoading = computed(() => trendLoading.value)

// 切回 agent tab 时若贡献图未拉过则重取
watch(activeTab, tab => {
  if (tab === 'agent' && !contribution.value) {
    fetchStats()
  }
})
</script>

<style lang="scss" scoped>
.usage-admin-page {
  .tabs-header {
    background: $bg-cream;
    border-bottom: 1px solid $border-warm-subtle;
    border-radius: 10px;

    .usage-tabs {
      :deep(.ant-tabs-nav) {
        margin-bottom: 0;
      }

      :deep(.ant-tabs-tab) {
        padding: 10px 16px;
      }
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

  .tab-stats {
    display: flex;
    flex-direction: column;
    gap: 18px;
  }

  .chart-card {
    border-radius: 12px;

    :deep(.ant-card-head-title) {
      font-weight: 600;
    }

    .card-toggles {
      display: flex;
      gap: 8px;
    }
  }

  .adm-filter-bar {
    margin-bottom: 4px;
  }

  .filter-bar {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 4px 0;

    .filter-label {
      font-size: 14px;
      color: $text-secondary;
    }
  }

  .bottom-row {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 16px;
    align-items: start;
  }

  // ≤960px 单列见 mobile/styles/admin.scss
}
</style>
