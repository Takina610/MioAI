<template>
  <div class="rank-list" v-if="items.length > 0">
    <div class="rank-item" v-for="(item, index) in items" :key="item.id">
      <div class="rank-index" :class="{ top: index < 3 }">{{ index + 1 }}</div>
      <BotIcon
        v-if="iconConfig(item)"
        :shape="iconConfig(item)!.shape"
        :fill="iconConfig(item)!.fill"
        :size="28"
        :live="false"
        class="rank-bot-icon"
      />
      <a-avatar v-else-if="item.avatar" :src="item.avatar" :size="28" class="rank-avatar" />
      <a-avatar v-else :size="28" class="rank-avatar rank-avatar-default">
        {{ item.name?.charAt(0)?.toUpperCase() }}
      </a-avatar>
      <div class="rank-info">
        <div class="rank-name">{{ item.name }}</div>
        <a-progress :percent="item.percent" size="small" :show-info="false" />
      </div>
      <div class="rank-value">{{ formatNumber(item.value) }}</div>
    </div>
  </div>
  <div v-else class="empty-rank">
    <a-empty description="暂无数据" />
  </div>
</template>

<script setup lang="ts">
import BotIcon from '@/components/bot-icon/BotIcon.vue'
import type { BotIconConfig } from '@/components/bot-icon/types'
import { formatNumber } from '../composables/format'

withDefaults(defineProps<{
  items: {
    id: number | string
    name: string
    avatar?: string
    icon?: string | null
    value: number
    percent: number
  }[]
  /** 智能体榜单显示头像 */
  showAvatar?: boolean
}>(), {
  showAvatar: false
})

/** bot-icon 配置解析：损坏/缺失配置返回 null 回退头像 */
function iconConfig(item: { icon?: string | null }): BotIconConfig | null {
  if (!item.icon) {
    return null
  }
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
.rank-list {
  max-height: 280px;
  overflow-y: auto;
  padding-right: 4px;
}

.rank-item {
  display: flex;
  align-items: center;
  padding: 9px 0;
  border-bottom: 1px solid #ece9de;

  &:last-child {
    border-bottom: none;
  }

  .rank-index {
    width: 22px;
    height: 22px;
    border-radius: 6px;
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

  .rank-bot-icon {
    margin-right: 12px;
    flex-shrink: 0;
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
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .rank-value {
    font-size: 14px;
    font-weight: 500;
    color: #141413;
    margin-left: 16px;
    flex-shrink: 0;
    font-variant-numeric: tabular-nums;
  }
}

.empty-rank {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
}
</style>
