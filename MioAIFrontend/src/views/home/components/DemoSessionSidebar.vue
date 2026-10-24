<template>
  <!-- 会话侧边栏复刻件：视觉对齐 ChatSidebar 胶囊。头部为实时状态的智能体宠物
       （情绪随演示消息执行情况切换，同对话页左下角 BotPet），会话项可点击切换 -->
  <aside class="dsb">
    <div class="dsb-head">
      <!-- 群聊：静态群头像；普通智能体：实时情绪宠物 -->
      <GroupAvatar v-if="groupMembers?.length" class="dsb-group-avatar" :members="groupMembers" :size="30" />
      <template v-else>
        <BotPet
          class="dsb-pet"
          :config="petConfig"
          :mood="petMood"
          :fx-signal="petFx"
          :size="26"
          :label="agentName"
        />
      </template>
      <span class="dsb-name">{{ agentName }}</span>
    </div>

    <div class="dsb-actions">
      <span class="dsb-btn"><SearchOutlined class="dsb-btn-icon" />搜索</span>
      <span class="dsb-btn primary"><FormOutlined class="dsb-btn-icon" />新对话</span>
    </div>

    <div class="dsb-list">
      <template v-for="group in groups" :key="group.label">
        <div v-if="group.label" class="dsb-group-label">{{ group.label }}</div>
        <div
          v-for="session in group.items"
          :key="session.id"
          class="dsb-item"
          :class="{ active: session.id === currentId, streaming: session.id === streamingId }"
          role="button"
          tabindex="0"
          @click="emit('select', session.id)"
          @keydown.enter="emit('select', session.id)"
        >
          <span class="dsb-item-dot" />
          <span class="dsb-item-title">{{ session.title }}</span>
          <span class="dsb-item-time">{{ formatTime(session.updateTime) }}</span>
        </div>
      </template>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { FormOutlined, SearchOutlined } from '@ant-design/icons-vue'
import BotPet from '@/components/agent/bot-icon/BotPet.vue'
import GroupAvatar from '@/components/agent/GroupAvatar.vue'
import type { BotIconConfig } from '@/components/agent/bot-icon/types'
import type { AgentMemberBrief, ChatSession } from '@/types'

const props = defineProps<{
  /** 当前智能体的会话列表（演示会话定义即可：id/标题/时间/置顶） */
  sessions: Array<Pick<ChatSession, 'id' | 'title' | 'updateTime' | 'pinned'>>
  currentId: string
  streamingId: string | null
  agentName: string
  /** 群聊成员（非空时头部显示静态群头像，替代动态宠物） */
  groupMembers?: AgentMemberBrief[] | null
  /** 头部宠物的图标与实时情绪（由父级 useBotMood 驱动） */
  petConfig: BotIconConfig
  petMood: string
  petFx: number
}>()

const emit = defineEmits<{
  (e: 'select', sessionId: string): void
}>()

/** 置顶组在前，与真实会话列表同序 */
const groups = computed(() => {
  const pinned = props.sessions.filter(s => s.pinned === 1)
  const normal = props.sessions.filter(s => s.pinned !== 1)
  return [
    ...(pinned.length ? [{ label: '置顶', items: pinned }] : []),
    { label: '', items: normal }
  ]
})

function formatTime(d: Date): string {
  return `${d.getMonth() + 1}/${d.getDate()}`
}
</script>

<style lang="scss" scoped>
.dsb {
  width: 264px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  padding: 14px 12px;
  border-right: 1px solid rgba(216, 213, 204, 0.45);
}

.dsb-head {
  display: flex;
  align-items: center;
  gap: 6px;
  // 头顶留白：状态提示泡在宠物上方弹出所需的净空（泡高 + 6px 间距）
  padding: 34px 2px 12px;
  pointer-events: none; // 纯展示：头部与操作行不可交互，仅会话项可点击

  .dsb-group-avatar {
    margin-left: 2px;
    flex-shrink: 0;
  }

  .dsb-pet {
    margin-left: -8px;
    padding: 0;
    border: none;
    background: transparent;
  }

  /* 状态提示泡保持默认向上弹出；宠物贴侧栏左缘，泡改从左缘起排避免左侧溢出，
     层级抬高避免被相邻内容盖住 */
  :deep(.bot-pet-tip) {
    left: -6px;
    transform: none;
    z-index: 20;
  }

  .dsb-name {
    font-size: 14px;
    font-weight: 600;
    color: var(--hm-text);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.dsb-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 14px;
  pointer-events: none;
}

.dsb-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 10px;
  font-size: 13px;
  color: var(--hm-text-2);
  border: 1px solid var(--hm-border-subtle);

  &.primary {
    color: var(--hm-green-deep);
    border-color: rgba(42, 161, 169, 0.35);
    background: var(--hm-green-soft);
  }

  .dsb-btn-icon {
    font-size: 13px;
  }
}

.dsb-list {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow: hidden;
}

.dsb-group-label {
  padding: 8px 8px 4px;
  font-size: 11px;
  color: var(--hm-text-muted);
}

.dsb-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
  border-radius: 10px;
  font-size: 13px;
  color: var(--hm-text-2);
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;

  &:hover {
    background: rgba(20, 20, 19, 0.05);
    color: var(--hm-text);
  }

  .dsb-item-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--hm-border);
    flex-shrink: 0;
  }

  .dsb-item-title {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .dsb-item-time {
    font-size: 11px;
    color: var(--hm-text-muted);
  }

  &.active {
    background: var(--hm-green-soft);
    color: var(--hm-text);

    .dsb-item-dot {
      background: var(--hm-green);
    }
  }

  /* 流式执行中的会话：圆点脉冲（与真实列表的执行动画同语言） */
  &.streaming .dsb-item-dot {
    background: var(--hm-green);
    animation: dsb-pulse 1.2s ease-in-out infinite;
  }
}

@keyframes dsb-pulse {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.4;
    transform: scale(0.72);
  }
}
</style>
