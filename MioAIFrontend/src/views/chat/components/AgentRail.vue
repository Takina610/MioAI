<template>
  <!-- 智能体左轨：与悬浮胶囊左缘融合（相接侧圆角清零，融合样式由页面 .rail-open 统一驱动）。
       默认 64px 图标窄轨，底部加号展开为 240px 信息面板（左 icon、右上名称、右下描述） -->
  <aside class="agent-rail" :class="{ hidden: !visible, expanded }">
    <div class="rail-capsule">
      <button class="rail-btn rail-create" title="创建群聊" aria-label="创建群聊" @click="emit('create-group')">
        <TeamOutlined class="rail-btn-icon" />
        <span v-if="expanded" class="rail-btn-text">创建群聊</span>
      </button>

      <div class="rail-list">
        <button
          v-for="agent in agents"
          :key="agent.id"
          class="rail-agent"
          :class="{ active: agent.id === currentAgentId }"
          :title="agent.name"
          :aria-label="agent.name"
          @click="emit('select', agent.id)"
        >
          <span class="agent-icon-wrap">
            <BotIcon
              v-if="iconByAgent.get(agent.id)"
              :shape="iconByAgent.get(agent.id)!.shape"
              :fill="iconByAgent.get(agent.id)!.fill"
              :size="expanded ? 34 : 38"
            />
            <span v-else-if="agent.type === 2" class="group-tile">
              <TeamOutlined />
            </span>
            <a-avatar v-else :size="expanded ? 34 : 38" :src="agent.avatar">
              {{ agent.name?.charAt(0)?.toUpperCase() }}
            </a-avatar>
          </span>
          <span v-if="expanded" class="agent-meta">
            <span class="agent-name">{{ agent.name }}</span>
            <span v-if="agent.description" class="agent-desc">{{ agent.description }}</span>
          </span>
        </button>
      </div>

      <button
        class="rail-btn rail-expand"
        :title="expanded ? '收起信息面板' : '展开信息面板'"
        :aria-label="expanded ? '收起信息面板' : '展开信息面板'"
        @click="emit('toggle-expanded')"
      >
        <PlusOutlined class="rail-btn-icon" :class="{ rotated: expanded }" />
      </button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { PlusOutlined, TeamOutlined } from '@ant-design/icons-vue'
import BotIcon from '@/components/bot-icon/BotIcon.vue'
import { parseBotIcon, type BotIconConfig } from '@/components/bot-icon/types'
import type { Agent } from '@/types'

const props = defineProps<{
  /** 我的智能体（自定义 + 群聊， updateTime 倒序） */
  agents: Agent[]
  /** 当前对话的智能体 id（高亮锚点） */
  currentAgentId: number
  /** 轨道是否展示（登录 + 自身开关 + 会话胶囊未收起） */
  visible: boolean
  /** 信息面板模式（true = 240px 宽轨带名称/描述） */
  expanded: boolean
}>()

const emit = defineEmits<{
  (e: 'select', agentId: number): void
  (e: 'create-group'): void
  (e: 'toggle-expanded'): void
}>()

/** agent.icon JSON → BotIconConfig（缺失/非法回退头像，群聊回退群图标） */
const iconByAgent = computed(() => {
  const map = new Map<number, BotIconConfig | null>()
  for (const agent of props.agents) {
    map.set(agent.id, agent.type === 2 ? null : parseBotIcon(agent.icon))
  }
  return map
})
</script>

<style lang="scss" scoped>
.agent-rail {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  z-index: 60;
  // 12 出血 + 64 胶囊（信息面板 240），宽度变化与会话胶囊位移同曲线同步
  width: 76px;
  padding: 12px;
  box-sizing: border-box;
  transform: translateX(0);
  transition: transform 0.28s cubic-bezier(0.32, 0.72, 0.35, 1),
    width 0.28s cubic-bezier(0.32, 0.72, 0.35, 1);
  will-change: transform;
  pointer-events: none;

  &.expanded {
    width: 252px;
  }

  &.hidden {
    transform: translateX(-110%);
  }

  // 右缘直角 + 无右边线：与会话胶囊融合成一体（融合对侧由页面 .rail-open 控制）
  .rail-capsule {
    pointer-events: auto;
    width: 100%;
    height: 100%;
    display: flex;
    flex-direction: column;
    background: #f5f3ec;
    border: 1px solid $border-warm-subtle;
    border-right: none;
    border-radius: 18px 0 0 18px;
    box-shadow: 0 8px 24px rgba(20, 20, 19, 0.05);
    overflow: hidden;
  }
}

.rail-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  height: 42px;
  margin: 8px 8px 0;
  border: 1px solid rgba($primary-color, 0.45);
  border-radius: 10px;
  background: linear-gradient(135deg, rgba($primary-color, 0.1) 0%, rgba($primary-color, 0.05) 100%);
  color: $primary-color;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;

  &:hover {
    background: linear-gradient(135deg, rgba($primary-color, 0.18) 0%, rgba($primary-color, 0.1) 100%);
  }

  .rail-btn-icon {
    font-size: 16px;
  }
}

// 创建群聊：窄轨为图标按钮，宽轨带文案
.rail-create {
  flex-shrink: 0;
}

.agent-rail.expanded .rail-create {
  justify-content: flex-start;
  padding: 0 14px;
}

.rail-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 8px;
}

.rail-agent {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px;
  border: 1px solid transparent;
  border-radius: 12px;
  background: transparent;
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s;

  &:hover {
    background: rgba($primary-color, 0.08);
  }

  &.active {
    background: rgba($primary-color, 0.12);
    border-color: rgba($primary-color, 0.4);
  }

  .agent-icon-wrap {
    flex-shrink: 0;
    width: 40px;
    height: 40px;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .group-tile {
    width: 36px;
    height: 36px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 25%;
    background: linear-gradient(135deg, rgba($primary-color, 0.16) 0%, rgba($primary-color, 0.07) 100%);
    color: $primary-color;
    font-size: 17px;
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
}

// 窄轨：整钮居中只留图标
.agent-rail:not(.expanded) .rail-agent {
  justify-content: center;
  padding: 6px 0;
}

.rail-expand {
  flex-shrink: 0;
  margin: 0 8px 8px;
  height: 38px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: #8c8a82;
  cursor: pointer;
  transition: color 0.2s, background 0.2s;

  &:hover {
    color: $primary-color;
    background: rgba($primary-color, 0.08);
  }

  .rail-btn-icon {
    transition: transform 0.28s cubic-bezier(0.32, 0.72, 0.35, 1);

    &.rotated {
      transform: rotate(45deg);
    }
  }
}

@media (max-width: 768px) {
  // 窄屏：轨道不出场（会话胶囊本就以覆盖层出现，无让位语义）
  .agent-rail {
    display: none;
  }
}
</style>
