<template>
  <div class="adm-page overview-page">
    <div class="adm-page-header">
      <span class="adm-page-title">总览</span>
      <div class="header-controls">
        <a-radio-group v-model:value="chartDays" button-style="solid" size="small" @change="fetchCharts">
          <a-radio-button :value="7">近 7 天</a-radio-button>
          <a-radio-button :value="30">近 30 天</a-radio-button>
        </a-radio-group>
        <a-button size="small" :loading="statsLoading || chartsLoading" @click="refreshAll">刷新</a-button>
      </div>
    </div>

    <div class="adm-stat-strip">
      <div class="adm-stat-card">
        <div class="adm-stat-value">{{ stats.users }}</div>
        <div class="adm-stat-label">用户</div>
      </div>
      <div class="adm-stat-card">
        <div class="adm-stat-value">{{ stats.agents }}</div>
        <div class="adm-stat-label">智能体</div>
      </div>
      <div class="adm-stat-card">
        <div class="adm-stat-value">{{ stats.knowledgeBases }}</div>
        <div class="adm-stat-label">知识库</div>
      </div>
      <div class="adm-stat-card">
        <div class="adm-stat-value">{{ stats.mcpTools }}</div>
        <div class="adm-stat-label">MCP</div>
      </div>
      <div class="adm-stat-card">
        <div class="adm-stat-value">{{ stats.skills }}</div>
        <div class="adm-stat-label">技能</div>
      </div>
    </div>

    <div class="adm-stat-strip">
      <div class="adm-stat-card">
        <div class="adm-stat-value">{{ stats.conversations }}</div>
        <div class="adm-stat-label">会话</div>
      </div>
      <div class="adm-stat-card">
        <div class="adm-stat-value">{{ stats.messages }}</div>
        <div class="adm-stat-label">消息</div>
      </div>
      <div class="adm-stat-card">
        <div class="adm-stat-value">{{ stats.chatFiles }}</div>
        <div class="adm-stat-label">聊天文件</div>
      </div>
      <div class="adm-stat-card">
        <div class="adm-stat-value">{{ stats.runningTasks }}</div>
        <div class="adm-stat-label">运行中任务</div>
      </div>
      <div class="adm-stat-card accent">
        <div class="adm-stat-value">{{ formatCompact(stats.todayInputTokens + stats.todayOutputTokens) }}</div>
        <div class="adm-stat-label">今日 Token</div>
      </div>
      <div class="adm-stat-card accent">
        <div class="adm-stat-value">{{ stats.todayFailedTurns }}</div>
        <div class="adm-stat-label">今日失败轮次</div>
      </div>
    </div>

    <div class="adm-chart-card">
      <div class="adm-chart-head">
        <span class="adm-chart-title">每日趋势</span>
        <a-radio-group v-model:value="trendMetric" size="small">
          <a-radio-button value="turns">对话轮次</a-radio-button>
          <a-radio-button value="tokens">Token 总量</a-radio-button>
          <a-radio-button value="failedTurns">失败轮次</a-radio-button>
        </a-radio-group>
      </div>
      <a-spin :spinning="chartsLoading">
        <DailyTrendChart :points="charts?.trend ?? []" :metric="trendMetric" />
      </a-spin>
    </div>

    <div class="adm-chart-grid">
      <div class="adm-chart-card">
        <div class="adm-chart-head">
          <span class="adm-chart-title">模型 Token 趋势</span>
        </div>
        <a-spin :spinning="chartsLoading">
          <TokenTrendChart :series="charts?.modelTrend ?? []" />
        </a-spin>
      </div>
      <div class="adm-chart-card">
        <div class="adm-chart-head">
          <span class="adm-chart-title">智能体调用分布</span>
        </div>
        <a-spin :spinning="chartsLoading">
          <AgentDistPie :items="charts?.agentDistribution ?? []" />
        </a-spin>
      </div>
      <div class="adm-chart-card">
        <div class="adm-chart-head">
          <span class="adm-chart-title">用户 Token 排行</span>
        </div>
        <a-spin :spinning="chartsLoading">
          <UsageRankList :items="userRankItems" show-avatar />
        </a-spin>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import MioMessage from '@/utils/message'
import { getOverviewCharts, getOverviewStats } from '@/api/adminOverview'
import type { OverviewCharts, OverviewStats, TrendMetric } from '@/api/adminOverview'
import { formatCompact } from '@/views/dashboard/usage/composables/format'
import TokenTrendChart from '@/views/dashboard/usage/components/TokenTrendChart.vue'
import UsageRankList from '@/views/dashboard/usage/components/UsageRankList.vue'
import DailyTrendChart from './components/DailyTrendChart.vue'
import AgentDistPie from './components/AgentDistPie.vue'

const statsLoading = ref(false)
const chartsLoading = ref(false)
const chartDays = ref<7 | 30>(30)
const trendMetric = ref<TrendMetric>('turns')

const stats = reactive<OverviewStats>({
  users: 0,
  agents: 0,
  knowledgeBases: 0,
  mcpTools: 0,
  skills: 0,
  conversations: 0,
  messages: 0,
  chatFiles: 0,
  todayTurns: 0,
  todayInputTokens: 0,
  todayOutputTokens: 0,
  todayFailedTurns: 0,
  runningTasks: 0
})

const charts = ref<OverviewCharts | null>(null)

const userRankItems = computed(() => {
  const items = charts.value?.userRank ?? []
  const max = Math.max(...items.map(item => Number(item.value)), 1)
  return items.map(item => ({
    id: item.id,
    name: item.name,
    avatar: item.avatar ?? undefined,
    icon: item.icon ?? null,
    value: Number(item.value),
    percent: Math.round((Number(item.value) / max) * 100)
  }))
})

async function fetchStats() {
  statsLoading.value = true
  try {
    Object.assign(stats, await getOverviewStats())
  } catch {
    MioMessage.error('获取总览统计失败')
  } finally {
    statsLoading.value = false
  }
}

async function fetchCharts() {
  chartsLoading.value = true
  try {
    charts.value = await getOverviewCharts(chartDays.value)
  } catch {
    MioMessage.error('获取图表数据失败')
  } finally {
    chartsLoading.value = false
  }
}

function refreshAll() {
  fetchStats()
  fetchCharts()
}

onMounted(refreshAll)
</script>

<style lang="scss" scoped>
.overview-page {
  .header-controls {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .adm-stat-card.accent {
    border-color: rgba(42, 161, 169, 0.35);
    background: linear-gradient(180deg, rgba(42, 161, 169, 0.06), $bg-ivory);
  }
}
</style>
