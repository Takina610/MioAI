<template>
  <div class="rank-list" v-if="items.length > 0">
    <div class="rank-item" v-for="(item, index) in items" :key="item.id">
      <div class="rank-index" :class="{ top: index < 3 }">{{ index + 1 }}</div>
      <template v-if="showAvatar">
        <a-avatar v-if="item.avatar" :src="item.avatar" :size="32" class="rank-avatar" />
        <a-avatar v-else :size="32" class="rank-avatar rank-avatar-default">
          {{ item.name?.charAt(0)?.toUpperCase() }}
        </a-avatar>
      </template>
      <div class="rank-info">
        <div class="rank-name">{{ item.name }}</div>
        <a-progress :percent="item.percent" size="small" :show-info="false" />
      </div>
      <div class="rank-value">{{ item.value }}</div>
    </div>
  </div>
  <div v-else class="empty-rank">
    <a-empty description="暂无数据" />
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  items: {
    id: number | string
    name: string
    avatar?: string
    value: number
    percent: number
  }[]
  /** 智能体榜单显示头像 */
  showAvatar?: boolean
}>(), {
  showAvatar: false
})
</script>

<style lang="scss" scoped>
.rank-item {
  display: flex;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #ece9de;

  &:last-child {
    border-bottom: none;
  }

  .rank-index {
    width: 24px;
    height: 24px;
    border-radius: 50%;
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
    }
  }

  .rank-value {
    font-size: 14px;
    font-weight: 500;
    color: #141413;
    margin-left: 16px;
    flex-shrink: 0;
  }
}

.empty-rank {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
}
</style>
