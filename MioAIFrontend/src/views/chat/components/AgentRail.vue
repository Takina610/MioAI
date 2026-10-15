<template>
  <!-- 智能体左轨：与悬浮胶囊左缘融合（融合样式由页面 .rail-open 统一驱动）。
       默认 64px 图标窄轨，底部加号展开为 240px 信息面板（左 icon、右上名称、右下描述）。
       排序模式（宽轨顶部按钮切换）：免长按直接拖动，点击与图钉暂停；
       双栏 peek 时创建按钮下移，给悬浮的会话开关按钮让位 -->
  <aside class="agent-rail" :class="{ hidden: !visible, expanded, peek, overlay }">
    <div class="rail-capsule">
      <div class="rail-top-actions">
        <button class="rail-btn rail-create" title="创建群聊" aria-label="创建群聊" @click="emit('create-group')">
          <TeamOutlined class="rail-btn-icon" />
          <span v-if="expanded" class="rail-btn-text">创建群聊</span>
        </button>
        <!-- 排序模式开关：圆形图标按钮，仅信息面板态显示（窄轨无位） -->
        <button
          v-if="expanded"
          class="rail-sort"
          :class="{ active: sortMode }"
          :title="sortMode ? '退出排序' : '拖动排序'"
          :aria-label="sortMode ? '退出排序模式' : '进入排序模式'"
          @click="toggleSortMode"
        >
          <DragOutlined class="rail-btn-icon" />
        </button>
      </div>

      <div class="rail-list">
        <div v-if="pinnedAgents.length && expanded" class="rail-group-label">置顶</div>
        <draggable
          v-if="sortMode"
          v-model="pinnedAgents"
          :key="`sort-pinned-${sortMode}`"
          :animation="220"
          :delay="0"
          item-key="id"
          class="rail-drag-zone rail-sort-pinned"
          ghost-class="rail-ghost"
          chosen-class="rail-chosen"
          drag-class="rail-dragging"
          @end="emitReorder"
        >
          <template #item="{ element }">
            <RailAgentItem
            :group-state="element.type === 2 ? agentExecutingStates.get(element.id) : undefined"
              :agent="element"
              :expanded="expanded"
              :current-agent-id="currentAgentId"
              :icon="iconByAgent.get(element.id)"
              :sorting="sortMode"
              @open="emit('select', $event)"
              @toggle-pin="emit('toggle-pin', $event)"
            />
          </template>
        </draggable>
        <div v-if="!sortMode" class="rail-drag-zone">
          <RailAgentItem
            :group-state="element.type === 2 ? agentExecutingStates.get(element.id) : undefined"
            v-for="element in pinnedAgents"
            :key="element.id"
            :agent="element"
            :expanded="expanded"
            :current-agent-id="currentAgentId"
            :icon="iconByAgent.get(element.id)"
            @open="emit('select', $event)"
            @toggle-pin="emit('toggle-pin', $event)"
          />
        </div>
        <div v-if="pinnedAgents.length && normalAgents.length && expanded" class="rail-group-divider" />
        <draggable
          v-if="sortMode"
          v-model="normalAgents"
          :key="`sort-normal-${sortMode}`"
          :animation="220"
          :delay="0"
          item-key="id"
          class="rail-drag-zone rail-sort-normal"
          ghost-class="rail-ghost"
          chosen-class="rail-chosen"
          drag-class="rail-dragging"
          @end="emitReorder"
        >
          <template #item="{ element }">
            <RailAgentItem
            :group-state="element.type === 2 ? agentExecutingStates.get(element.id) : undefined"
              :agent="element"
              :expanded="expanded"
              :current-agent-id="currentAgentId"
              :icon="iconByAgent.get(element.id)"
              :sorting="sortMode"
              @open="emit('select', $event)"
              @toggle-pin="emit('toggle-pin', $event)"
            />
          </template>
        </draggable>
        <div v-if="!sortMode" class="rail-drag-zone">
          <RailAgentItem
            :group-state="element.type === 2 ? agentExecutingStates.get(element.id) : undefined"
            v-for="element in normalAgents"
            :key="element.id"
            :agent="element"
            :expanded="expanded"
            :current-agent-id="currentAgentId"
            :icon="iconByAgent.get(element.id)"
            @open="emit('select', $event)"
            @toggle-pin="emit('toggle-pin', $event)"
          />
        </div>

        <!-- 群聊独立区块（不入"我的智能体"排序）：按服务端顺序静态展示，不参与拖动排序 -->
        <template v-if="groups?.length">
          <div v-if="expanded" class="rail-group-label">群聊</div>
          <div v-else class="rail-group-divider" />
          <div class="rail-drag-zone">
            <RailAgentItem
              v-for="element in groups"
              :key="element.id"
              :group-state="agentExecutingStates.get(element.id)"
              :agent="element"
              :expanded="expanded"
              :current-agent-id="currentAgentId"
              :icon="iconByAgent.get(element.id)"
              @open="emit('select', $event)"
              @toggle-pin="emit('toggle-pin', $event)"
            />
          </div>
        </template>
      </div>

      <button
        class="rail-expand"
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
import { DragOutlined, PlusOutlined, TeamOutlined } from '@ant-design/icons-vue'
import draggable from 'vuedraggable'
import RailAgentItem from './RailAgentItem.vue'
import { agentExecutingStates } from '../composables/useAgentActivity'
import { parseBotIcon, type BotIconConfig } from '@/components/bot-icon/types'
import type { Agent } from '@/types'

const props = defineProps<{
  /** 我的智能体（自定义 + 专属 MioBot，不含群聊；服务端已按 置顶→手动序→更新时间 排序） */
  agents: Agent[]
  /** 群聊独立区块（type=2），不参与"我的智能体"的置顶与拖动排序 */
  groups?: Agent[]
  /** 当前对话的智能体 id（高亮锚点） */
  currentAgentId: number
  /** 轨道是否展示（相位=都展开 或 悬浮 peek） */
  visible: boolean
  /** 信息面板模式（true = 240px 宽轨带名称/描述） */
  expanded: boolean
  /** 双栏同时 peek（会话开关按钮悬浮在轨道顶部，创建按钮让位下移） */
  peek?: boolean
  /** 覆盖态投影（仅左轨 peek、会话栏常驻时，轨道浮在会话胶囊左缘之上） */
  overlay?: boolean
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
  for (const agent of [...props.agents, ...(props.groups ?? [])]) {
    map.set(agent.id, agent.type === 2 ? null : parseBotIcon(agent.icon))
  }
  return map
})

/** 排序模式：免长按直接拖动，点击导航与图钉暂停（显式切换，避免长按与点击冲突） */
const sortMode = ref(false)

function toggleSortMode(): void {
  sortMode.value = !sortMode.value
}

// 轨道隐藏/收起时退出排序模式
watch(() => props.visible, v => {
  if (!v) sortMode.value = false
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
  // 以 DOM 实际顺序为准（Sortable 直接改 DOM；本地数组在个别时序下可能未同步，
  // 拖完的可见顺序就是用户要的顺序），并回写本地数组保持后续拖拽的索引基准正确
  const zoneIds = (cls: string): number[] => {
    const zone = document.querySelector(`.agent-rail .${cls}`)
    if (!zone) return []
    return [...zone.querySelectorAll('.rail-agent[data-id]')]
      .map(el => Number(el.getAttribute('data-id')))
      .filter(id => !!id)
  }
  const orderedIds = [...zoneIds('rail-sort-pinned'), ...zoneIds('rail-sort-normal')]
  if (orderedIds.length !== props.agents.length) {
    emit('reorder', [...pinnedAgents.value, ...normalAgents.value].map(a => a.id))
    return
  }
  const byId = new Map(props.agents.map(a => [a.id, a]))
  const ordered = orderedIds.map(id => byId.get(id)).filter((a): a is Agent => !!a)
  pinnedAgents.value = ordered.filter(a => a.pinned === 1)
  normalAgents.value = ordered.filter(a => a.pinned !== 1)
  emit('reorder', orderedIds)
}
</script>

<style lang="scss" scoped>
.agent-rail {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  // 高于会话胶囊（z-60）：仅左轨 peek 时轨道覆盖在会话胶囊左缘之上
  z-index: 61;
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

  // 右缘直角 + 右缘分隔线（无阴影）：与会话胶囊拼接，一条竖线分隔即可
  .rail-capsule {
    pointer-events: auto;
    width: 100%;
    height: 100%;
    display: flex;
    flex-direction: column;
    background: #f5f3ec;
    border: 1px solid $border-warm-subtle;
    border-right: 1px solid $border-warm-subtle;
    border-radius: 18px 0 0 18px;
    box-shadow: none;
    overflow: hidden;
  }

  // 覆盖态（仅左轨 peek）：加深投影示意浮层
  &.overlay .rail-capsule {
    box-shadow: 0 16px 44px rgba(20, 20, 19, 0.16);
  }

  // 双栏 peek：会话开关按钮悬浮在轨道顶部区域，创建/排序按钮下移让位
  &.peek .rail-top-actions {
    margin-top: 60px;
  }
}

.rail-top-actions {
  display: flex;
  gap: 6px;
  margin: 8px 8px 0;
  flex-shrink: 0;
}

.rail-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 42px;
  flex: 1;
  min-width: 0;
  border: 1px solid rgba($primary-color, 0.45);
  // 大圆角胶囊；悬浮毛玻璃
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.4);
  color: $primary-color;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  padding: 0 10px;
  transition: background 0.2s, box-shadow 0.2s, backdrop-filter 0.2s;

  &:hover {
    background: rgba(255, 255, 255, 0.55);
    backdrop-filter: blur(12px);
    -webkit-backdrop-filter: blur(12px);
    box-shadow: 0 6px 18px rgba(20, 20, 19, 0.12);
  }

  .rail-btn-icon {
    font-size: 15px;
    flex-shrink: 0;
  }

  .rail-btn-text {
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

// 窄轨：只留创建按钮（图标态）
.agent-rail:not(.expanded) .rail-sort {
  display: none;
}

.agent-rail:not(.expanded) .rail-create {
  flex: none;
  width: 52px;
  padding: 0;
}

.agent-rail.expanded .rail-create {
  justify-content: flex-start;
  padding: 0 14px;
}

// 排序模式开关：圆形图标按钮（无文字），激活态主色实底
.rail-sort {
  flex-shrink: 0;
  width: 42px;
  height: 42px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba($primary-color, 0.45);
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.4);
  color: $primary-color;
  cursor: pointer;
  transition: background 0.2s, box-shadow 0.2s;

  &:hover {
    background: rgba(255, 255, 255, 0.55);
    backdrop-filter: blur(12px);
    -webkit-backdrop-filter: blur(12px);
    box-shadow: 0 6px 18px rgba(20, 20, 19, 0.12);
  }

  &.active {
    background: $primary-color;
    border-color: $primary-color;
    color: #fff;

    &:hover {
      background: darken($primary-color, 6%);
    }
  }
}

.rail-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  padding: 10px 8px;
  // 滚动但不显示滚动条（窄轨空间有限，视觉保持干净）
  scrollbar-width: none;

  &::-webkit-scrollbar {
    display: none;
  }
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
  width: 38px;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: #8c8a82;
  font-size: 15px;
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

// 移动端（≤768px）左轨整体隐藏，职能由智能体切换抽屉承担：
// 见 mobile/styles/shell.scss 与 mobile/components/MobileAgentDrawer.vue
</style>
