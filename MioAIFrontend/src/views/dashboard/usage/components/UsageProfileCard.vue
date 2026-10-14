<template>
  <div class="profile-card" v-if="profile">
    <div class="profile-grid">
      <div class="profile-item">
        <div class="item-label">注册时间</div>
        <div class="item-value wrap">{{ registerText }}</div>
      </div>
      <div class="profile-item">
        <div class="item-label">累计会话</div>
        <div class="item-value">{{ formatNumber(profile.totalConversations) }}</div>
      </div>
      <div class="profile-item">
        <div class="item-label">累计轮次</div>
        <div class="item-value">{{ formatNumber(profile.totalTurns) }}</div>
      </div>
      <div class="profile-item">
        <div class="item-label">累计消息</div>
        <div class="item-value">{{ formatNumber(profile.totalMessages) }}</div>
      </div>
      <div class="profile-item">
        <div class="item-label">活跃天数</div>
        <div class="item-value">{{ formatNumber(profile.activeDays) }}</div>
      </div>
      <div class="profile-item">
        <div class="item-label">日均 Token</div>
        <div class="item-value">{{ formatCompact(profile.avgDailyTokens) }}</div>
      </div>
      <div class="profile-item">
        <div class="item-label">常用模型</div>
        <div class="item-value" :title="profile.favoriteModel ?? undefined">
          {{ profile.favoriteModel || '—' }}
        </div>
      </div>
      <div class="profile-item">
        <div class="item-label">常用智能体</div>
        <div class="item-value with-avatar" v-if="profile.favoriteAgentName">
          <a-avatar
            v-if="profile.favoriteAgentAvatar"
            :src="profile.favoriteAgentAvatar"
            :size="18"
          />
          <span class="name-text">{{ profile.favoriteAgentName }}</span>
        </div>
        <div class="item-value" v-else>—</div>
      </div>
      <div class="profile-item">
        <div class="item-label">智能体</div>
        <div class="item-value">{{ formatNumber(profile.agentCount) }}</div>
      </div>
      <div class="profile-item">
        <div class="item-label">知识库</div>
        <div class="item-value">{{ formatNumber(profile.knowledgeCount) }}</div>
      </div>
      <div class="profile-item">
        <div class="item-label">MCP</div>
        <div class="item-value">{{ formatNumber(profile.mcpCount) }}</div>
      </div>
      <div class="profile-item">
        <div class="item-label">技能</div>
        <div class="item-value">{{ formatNumber(profile.skillCount) }}</div>
      </div>
      <div class="profile-item">
        <div class="item-label">工具调用</div>
        <div class="item-value">{{ formatNumber(profile.toolCallCount) }}</div>
      </div>
      <div class="profile-item">
        <div class="item-label">知识检索</div>
        <div class="item-value">{{ formatNumber(profile.retrievalCount) }}</div>
      </div>
      <div class="profile-item">
        <div class="item-label">技能使用</div>
        <div class="item-value">{{ formatNumber(profile.skillUseCount) }}</div>
      </div>
      <div class="profile-item">
        <div class="item-label">执行日志</div>
        <div class="item-value">{{ formatNumber(profile.activityCount) }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import dayjs from 'dayjs'
import type { UsageProfile } from '@/types'
import { formatCompact, formatNumber } from '../composables/format'

const props = defineProps<{
  profile: UsageProfile | null
}>()

const registerText = computed(() => {
  if (!props.profile?.registerTime) {
    return '—'
  }
  const date = dayjs(props.profile.registerTime)
  const days = props.profile.registerDays
  return `${date.format('YYYY-MM-DD')}（第 ${days} 天）`
})
</script>

<style lang="scss" scoped>
.profile-card {
  background: #faf9f5;
  border: 1px solid #e8e6dc;
  border-radius: 12px;
  padding: 16px 18px;
}

.profile-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 12px 16px;

  .profile-item {
    min-width: 0;

    .item-label {
      font-size: 12px;
      color: #8c8a82;
      margin-bottom: 4px;
    }

    .item-value {
      font-size: 14px;
      font-weight: 500;
      color: #141413;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-variant-numeric: tabular-nums;

      /* 注册时间全文显示，不允许省略号截断 */
      &.wrap {
        white-space: normal;
        word-break: break-all;
        line-height: 1.4;
      }

      &.with-avatar {
        display: flex;
        align-items: center;
        gap: 6px;

        .name-text {
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
      }
    }
  }
}
</style>
