<template>
  <!-- 移动端智能体切换抽屉：补位桌面左轨（移动端左轨整体隐藏）。
       数据与事件由对话页注入，与桌面 AgentRail 共用同一份 myAgents 状态与导航回调，
       排序/拖拽等桌面专属能力不在此重复（移动端用图钉置顶即足够） -->
  <aside class="m-agent-drawer" :class="{ open: visible }">
    <div class="m-agent-capsule">
      <div class="m-agent-head">
        <span class="m-agent-title">智能体</span>
        <button type="button" class="m-agent-close" aria-label="关闭" @click="emit('close')">
          <CloseOutlined />
        </button>
      </div>

      <div class="m-agent-body">
        <button type="button" class="m-agent-create" @click="emit('create-group')">
          <TeamOutlined class="m-agent-create-icon" />
          <span>创建群聊</span>
        </button>

        <div v-if="agents.length" class="m-agent-group">
          <div class="m-agent-group-label">我的智能体</div>
          <div
            v-for="agent in agents"
            :key="agent.id"
            class="m-agent-item"
            :class="{ active: agent.id === currentAgentId }"
            @click="emit('select', agent.id)"
          >
            <BotIcon
              v-if="iconOf(agent)"
              :shape="iconOf(agent)!.shape"
              :fill="iconOf(agent)!.fill"
              :size="34"
              :live="false"
            />
            <a-avatar v-else :size="34" :src="agent.avatar">
              {{ agent.name?.charAt(0)?.toUpperCase() }}
            </a-avatar>
            <span class="m-agent-meta">
              <span class="m-agent-name">{{ agent.name }}</span>
              <span v-if="agent.description" class="m-agent-desc">{{ agent.description }}</span>
            </span>
            <button
              type="button"
              class="m-agent-pin"
              :class="{ pinned: agent.pinned === 1 }"
              :aria-label="agent.pinned === 1 ? '取消置顶' : '置顶'"
              @click.stop="emit('toggle-pin', agent)"
            >
              <PushpinFilled v-if="agent.pinned === 1" />
              <PushpinOutlined v-else />
            </button>
          </div>
        </div>

        <div v-if="groups?.length" class="m-agent-group">
          <div class="m-agent-group-label">群聊</div>
          <div
            v-for="group in groups"
            :key="group.id"
            class="m-agent-item"
            :class="{ active: group.id === currentAgentId }"
            @click="emit('select', group.id)"
          >
            <GroupAvatar :members="group.members ?? []" :size="34" />
            <span class="m-agent-meta">
              <span class="m-agent-name">{{ group.name }}</span>
              <span v-if="group.description" class="m-agent-desc">{{ group.description }}</span>
            </span>
          </div>
        </div>

        <div v-if="!agents.length && !groups?.length" class="m-agent-empty">暂无智能体</div>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { CloseOutlined, PushpinFilled, PushpinOutlined, TeamOutlined } from '@ant-design/icons-vue'
import BotIcon from '@/components/bot-icon/BotIcon.vue'
import GroupAvatar from '@/components/GroupAvatar.vue'
import { parseBotIcon, type BotIconConfig } from '@/components/bot-icon/types'
import type { Agent } from '@/types'

const props = defineProps<{
  visible: boolean
  /** 我的智能体（不含群聊） */
  agents: Agent[]
  /** 群聊（type=2） */
  groups?: Agent[]
  currentAgentId: number
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'select', agentId: number): void
  (e: 'create-group'): void
  (e: 'toggle-pin', agent: Agent): void
}>()

/** agent.icon JSON → BotIconConfig（缺失/非法回退头像） */
function iconOf(agent: Agent): BotIconConfig | null {
  return parseBotIcon(agent.icon)
}
</script>

<style lang="scss" scoped>
.m-agent-drawer {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  z-index: 66;
  width: min(88vw, 340px);
  padding: 12px 0 12px 12px;
  box-sizing: border-box;
  transform: translateX(-110%);
  transition: transform 0.28s cubic-bezier(0.32, 0.72, 0.35, 1);
  pointer-events: none;

  &.open {
    transform: translateX(0);
    pointer-events: auto;
  }
}

.m-agent-capsule {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f5f3ec;
  border: 1px solid $border-warm-subtle;
  border-radius: 18px;
  box-shadow: 0 16px 44px rgba(20, 20, 19, 0.18);
  overflow: hidden;
}

.m-agent-head {
  height: 56px;
  min-height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 10px 0 16px;
  border-bottom: 1px solid #e8e6dc;

  .m-agent-title {
    font-size: 16px;
    font-weight: 600;
    color: #141413;
  }

  .m-agent-close {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    border-radius: 10px;
    color: #5f5d55;
    font-size: 15px;

    &:active {
      background: rgba(20, 20, 19, 0.06);
    }
  }
}

.m-agent-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px;

  .m-agent-create {
    width: 100%;
    height: 44px;
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 0 14px;
    margin-bottom: 12px;
    background: #fff;
    border: 1px solid #e8e6dc;
    border-radius: 10px;
    color: #5f5d55;
    font-size: 14px;
    font-weight: 500;

    &:active {
      border-color: $primary-color;
      color: $primary-color;
    }

    .m-agent-create-icon {
      color: $primary-color;
    }
  }
}

.m-agent-group {
  & + & {
    margin-top: 8px;
  }

  .m-agent-group-label {
    font-size: 12px;
    color: #8c8a82;
    padding: 8px 4px 4px;
    font-weight: 500;
  }
}

.m-agent-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-radius: 12px;
  cursor: pointer;

  &:active {
    background: rgba(42, 161, 169, 0.1);
  }

  &.active {
    background: rgba(42, 161, 169, 0.12);
  }

  .m-agent-meta {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .m-agent-name {
    font-size: 14px;
    font-weight: 600;
    color: #141413;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .m-agent-desc {
    font-size: 11px;
    line-height: 1.4;
    color: #8c8a82;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .m-agent-pin {
    flex-shrink: 0;
    width: 40px;
    height: 40px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 10px;
    color: #b0aea5;
    font-size: 14px;

    &.pinned {
      color: $primary-color;
    }
  }
}

.m-agent-empty {
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
  color: #8c8a82;
}
</style>
