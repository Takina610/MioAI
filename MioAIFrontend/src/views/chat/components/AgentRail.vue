<template>
  <!-- 智能体左轨：与悬浮胶囊左缘融合（融合样式由页面 .rail-open 统一驱动）。
       默认 64px 图标窄轨，底部加号展开为 240px 信息面板（左 icon、右上名称、右下描述）。
       置顶分组在前（组内可长按拖动排序），hover 浮现图钉按钮 -->
  <aside class="agent-rail" :class="{ hidden: !visible, expanded }">
    <div class="rail-capsule">
      <button class="rail-btn rail-create" title="创建群聊" aria-label="创建群聊" @click="emit('create-group')">
        <TeamOutlined class="rail-btn-icon" />
        <span v-if="expanded" class="rail-btn-text">创建群聊</span>
      </button>

      <div class="rail-list">
        <div v-if="pinnedAgents.length && expanded" class="rail-group-label">置顶</div>
        <draggable
          v-model="pinnedAgents"
          :animation="220"
          :delay="260"
          item-key="id"
          class="rail-drag-zone"
          ghost-class="rail-ghost"
          chosen-class="rail-chosen"
          drag-class="rail-dragging"
          @end="emitReorder"
        >
          <template #item="{ element }">
            <RailAgentItem
              :agent="element"
              :expanded="expanded"
              :current-agent-id="currentAgentId"
              :icon="iconByAgent.get(element.id)"
              @open="emit('select', $event)"
              @toggle-pin="emit('toggle-pin', $event)"
            />
          </template>
        </draggable>
        <div v-if="pinnedAgents.length && normalAgents.length && expanded" class="rail-group-divider" />
        <draggable
          v-model="normalAgents"
          :animation="220"
          :delay="260"
          item-key="id"
          class="rail-drag-zone"
          ghost-class="rail-ghost"
          chosen-class="rail-chosen"
          drag-class="rail-dragging"
          @end="emitReorder"
        >
          <template #item="{ element }">
            <RailAgentItem
              :agent="element"
              :expanded="expanded"
              :current-agent-id="currentAgentId"
              :icon="iconByAgent.get(element.id)"
              @open="emit('select', $event)"
              @toggle-pin="emit('toggle-pin', $event)"
            />
          </template>
        </draggable>
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
import { computed, ref, watch } from 'vue'
import { PlusOutlined, TeamOutlined } from '@ant-design/icons-vue'
import draggable from 'vuedraggable'
import RailAgentItem from './RailAgentItem.vue'
import { parseBotIcon, type BotIconConfig } from '@/components/bot-icon/types'
import type { Agent } from '@/types'

const props = defineProps<{
  /** 我的智能体（自定义 + 群聊，服务端已按 置顶→手动序→更新时间 排序） */
  agents: Agent[]
  /** 当前对话的智能体 id（高亮锚点） */
  currentAgentId: number
  /** 轨道是否展示（登录 + 自身开关 + 会话胶囊未收起/peek） */
  visible: boolean
  /** 信息面板模式（true = 240px 宽轨带名称/描述） */
  expanded: boolean
}>()

const emit = defineEmits<{
  (e: 'select', agentId: number): void
  (e: 'create-group'): void
  (e: 'toggle-expanded'): void
  /** 图钉切换（父级乐观更新本地 + 落库） */
  (e: 'toggle-pin', agent: Agent): void
  /** 拖动结束后的完整顺序（置顶组在前；父级本地重排 + 落库） */
  (e: 'reorder', orderedIds: number[]): void
}>()

/** agent.icon JSON → BotIconConfig（缺失/非法回退头像，群聊回退群头像） */
const iconByAgent = computed(() => {
  const map = new Map<number, BotIconConfig | null>()
  for (const agent of props.agents) {
    map.set(agent.id, agent.type === 2 ? null : parseBotIcon(agent.icon))
  }
  return map
})

// 拖拽分组：本地数组随 props 重建（拖动中由 vuedraggable 接管，结束后上抛新顺序）
const pinnedAgents = ref<Agent[]>([])
const normalAgents = ref<Agent[]>([])

function syncFromProps(): void {
  pinnedAgents.value = props.agents.filter(a => a.pinned === 1)
  normalAgents.value = props.agents.filter(a => a.pinned !== 1)
}

watch(() => props.agents, syncFromProps, { immediate: true, deep: true })

function emitReorder(): void {
  emit('reorder', [...pinnedAgents.value, ...normalAgents.value].map(a => a.id))
}
</script>

<style lang="scss" scoped>
.agent-rail {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  z-index: 60;
  // 12 出血 + 64 胶囊（信息面板 240），宽度变化与会话胶囊位移同曲线同步。
  // 右侧零 padding：胶囊右缘必须紧贴会话胶囊左缘（融合无间隙）
  width: 76px;
  padding: 12px 0 12px 12px;
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
  // 大圆角胶囊；悬浮毛玻璃
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.4);
  color: $primary-color;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s, box-shadow 0.2s, backdrop-filter 0.2s;

  &:hover {
    background: rgba(255, 255, 255, 0.55);
    backdrop-filter: blur(12px);
    -webkit-backdrop-filter: blur(12px);
    box-shadow: 0 6px 18px rgba(20, 20, 19, 0.12);
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
  padding: 0 16px;
}

.rail-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  padding: 10px 8px;
}

.rail-group-label {
  padding: 2px 6px 6px;
  font-size: 11px;
  color: #8c8a82;
}

.rail-group-divider {
  height: 1px;
  margin: 8px 10px;
  background: #e8e6dc;
}

.rail-drag-zone {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

// 拖拽态：占位虚影与拾起项
.rail-ghost {
  opacity: 0.35;
}

.rail-chosen {
  cursor: grabbing;
}

.rail-dragging {
  box-shadow: 0 10px 28px rgba(20, 20, 19, 0.22);
}

.rail-expand {
  flex-shrink: 0;
  margin: 0 8px 8px;
  height: 38px;
  border: none;
  border-radius: 999px;
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
