<template>
  <!-- 左轨单个智能体：窄轨图标态 / 宽轨信息态（左 icon、右上名称、右下描述）。
       hover 浮现图钉按钮（置顶/取消）；排序模式下暂停点击与图钉，改为可拖拽手柄 -->
  <div
    class="rail-agent"
    :class="{ active: agent.id === currentAgentId, pinned: agent.pinned === 1, sorting }"
    :title="agent.name"
    role="button"
    tabindex="0"
    @click="sorting || emit('open', agent.id)"
    @keydown.enter="sorting || emit('open', agent.id)"
  >
    <span class="agent-icon-wrap">
      <BotIcon
        v-if="icon"
        :shape="icon.shape"
        :fill="icon.fill"
        :size="expanded ? 34 : 38"
        :live="!sorting"
      />
      <GroupAvatar
        v-else-if="agent.type === 2"
        :members="agent.members ?? []"
        :size="expanded ? 38 : 42"
      />
      <a-avatar v-else :size="expanded ? 34 : 38" :src="agent.avatar">
        {{ agent.name?.charAt(0)?.toUpperCase() }}
      </a-avatar>
    </span>
    <span v-if="expanded" class="agent-meta">
      <span class="agent-name">{{ agent.name }}</span>
      <span v-if="agent.description" class="agent-desc">{{ agent.description }}</span>
    </span>
    <button
      v-if="!sorting"
      class="pin-btn"
      :class="{ pinned: agent.pinned === 1 }"
      :title="agent.pinned === 1 ? '取消置顶' : '置顶'"
      :aria-label="agent.pinned === 1 ? '取消置顶' : '置顶'"
      @click.stop="emit('toggle-pin', agent)"
    >
      <PushpinFilled v-if="agent.pinned === 1" />
      <PushpinOutlined v-else />
    </button>
    <HolderOutlined v-else class="sort-grip" />
  </div>
</template>

<script setup lang="ts">
import BotIcon from '@/components/bot-icon/BotIcon.vue'
import GroupAvatar from '@/components/GroupAvatar.vue'
import { HolderOutlined, PushpinFilled, PushpinOutlined } from '@ant-design/icons-vue'
import type { BotIconConfig } from '@/components/bot-icon/types'
import type { Agent } from '@/types'

defineProps<{
  agent: Agent
  expanded: boolean
  currentAgentId: number
  /** 解析后的 bot-icon 配置（null 时群聊回退群头像、普通回退头像） */
  icon?: BotIconConfig | null
  /** 排序模式：暂停点击导航与图钉，整项变为拖拽手柄 */
  sorting?: boolean
}>()

const emit = defineEmits<{
  (e: 'open', agentId: number): void
  (e: 'toggle-pin', agent: Agent): void
}>()
</script>

<style lang="scss" scoped>
.rail-agent {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px;
  border: 1px solid transparent;
  border-radius: 12px;
  cursor: pointer;
  // 拖动期间不选中文本
  user-select: none;
  transition: background 0.2s, border-color 0.2s;

  &:hover {
    background: rgba($primary-color, 0.08);

    .pin-btn {
      opacity: 1;
      pointer-events: auto;
    }
  }

  &.active {
    background: rgba($primary-color, 0.12);
    border-color: rgba($primary-color, 0.4);
  }

  // 排序模式：抓取手柄态（虚线描边示意可拖）
  &.sorting {
    cursor: grab;
    border-style: dashed;
    border-color: rgba($primary-color, 0.35);

    &:hover {
      background: rgba($primary-color, 0.06);
    }
  }

  .sort-grip {
    position: absolute;
    top: 2px;
    right: 2px;
    font-size: 11px;
    color: rgba($primary-color, 0.6);
  }

  .agent-icon-wrap {
    flex-shrink: 0;
    width: 40px;
    height: 40px;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .agent-meta {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;
    text-align: left;
  }

  .agent-name {
    font-size: 13px;
    font-weight: 600;
    color: #141413;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .agent-desc {
    font-size: 11px;
    line-height: 1.4;
    color: #8c8a82;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  // 图钉：hover 浮现（已置顶常显主色）
  .pin-btn {
    position: absolute;
    top: 2px;
    right: 2px;
    width: 22px;
    height: 22px;
    display: flex;
    align-items: center;
    justify-content: center;
    border: none;
    border-radius: 6px;
    background: transparent;
    color: #8c8a82;
    font-size: 11px;
    cursor: pointer;
    opacity: 0;
    pointer-events: none;
    transition: opacity 0.2s, color 0.2s, background 0.2s;

    &:hover {
      color: $primary-color;
      background: rgba($primary-color, 0.12);
    }

    &.pinned {
      opacity: 1;
      pointer-events: auto;
      color: $primary-color;
    }
  }

  // 窄轨：整钮居中只留图标，图钉右上角
  &:not(:has(.agent-meta)) {
    justify-content: center;
    padding: 6px 0;
  }
}
</style>
