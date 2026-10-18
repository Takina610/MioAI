<template>
  <!-- 群聊堆叠头像：2x2 等大网格，格子透明（成员图标原样透出）。
       2 个=左右；3 个=上中+下二；4 个=满格；5+ =左上/右上/左下 + 右下省略号 -->
  <span class="group-avatar" :style="{ width: size + 'px', height: size + 'px' }">
    <span
      v-for="(m, i) in shown"
      :key="m.id"
      class="ga-cell"
      :class="cellClass(i)"
    >
      <BotIcon
        v-if="iconOf(m)"
        :shape="iconOf(m)!.shape"
        :fill="iconOf(m)!.fill"
        :size="cellSize"
        :live="false"
        :state="moodOf(m.id)"
      />
      <img v-else-if="m.avatar" :src="m.avatar" alt="" class="ga-img" />
      <span v-else class="ga-initial">{{ m.name?.charAt(0)?.toUpperCase() }}</span>
    </span>
    <span v-if="overflow" class="ga-cell ga-ellipsis" :class="cellClass(shown.length)">
      <span class="ga-dots">···</span>
    </span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import BotIcon from '@/components/agent/bot-icon/BotIcon.vue'
import { parseBotIcon, type BotIconConfig } from '@/components/agent/bot-icon/types'

export interface GroupAvatarMember {
  id: number
  name?: string
  icon?: string
  avatar?: string
}

const props = withDefaults(defineProps<{
  members: GroupAvatarMember[]
  /** 容器边长（px），格子等分 */
  size?: number
  /** 群聊绑定状态（非空时全体成员小脸切到该状态，如群会话执行中） */
  state?: string
}>(), {
  size: 40
})

const MAX_SHOWN = 4

/** 2~4 全量；5+ 只展示 3（第 4 格放省略号） */
const shown = computed(() =>
  props.members.slice(0, overflow.value ? 3 : Math.min(props.members.length, MAX_SHOWN)))
const overflow = computed(() => props.members.length > MAX_SHOWN)

/** 网格槽位：2=左右（垂直居中）；3=上中+左下+右下；4=满格；5+=左上/右上/左下+右下省略号 */
function cellClass(index: number): string {
  if (overflow.value) {
    return ['ga-l ga-t', 'ga-r ga-t', 'ga-l ga-b', 'ga-r ga-b'][index] ?? 'ga-r ga-b'
  }
  if (props.members.length === 2) {
    return index === 0 ? 'ga-l ga-c' : 'ga-r ga-c'
  }
  if (props.members.length === 3) {
    return ['ga-m ga-t', 'ga-l ga-b', 'ga-r ga-b'][index] ?? 'ga-l ga-t'
  }
  return ['ga-l ga-t', 'ga-r ga-t', 'ga-l ga-b', 'ga-r ga-b'][index] ?? 'ga-l ga-t'
}

const cellSize = computed(() => Math.round(props.size / 2) - 2)

const iconCache = new Map<number, BotIconConfig | null>()
function iconOf(m: GroupAvatarMember): BotIconConfig | null {
  if (!iconCache.has(m.id)) {
    iconCache.set(m.id, parseBotIcon(m.icon))
  }
  return iconCache.get(m.id) ?? null
}

/** 成员小图标情绪：群聊绑定状态优先（执行中全体同步），空闲一律中性 idle */
function moodOf(id: number): string {
  void id
  return props.state || 'idle'
}
</script>

<style lang="scss" scoped>
.group-avatar {
  position: relative;
  display: inline-block;
  flex-shrink: 0;
}

// 2x2 等分格子（2 个成员时上下留白、左右居中）
.ga-cell {
  position: absolute;
  width: calc(50% - 1.5px);
  height: calc(50% - 1.5px);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  // 透明展示：不铺底色，成员图标原样透出（首字/省略号格子自带底色）
  background: transparent;

  &.ga-l { left: 0; }
  &.ga-r { right: 0; }
  &.ga-m { left: 25%; }
  &.ga-t { top: 0; }
  &.ga-b { bottom: 0; }

  // 两成员：垂直居中一行
  &.ga-c { top: 25%; }

  &.ga-l.ga-t { border-radius: 4px 0 0 0; }
  &.ga-r.ga-t { border-radius: 0 4px 0 0; }
  &.ga-l.ga-b { border-radius: 0 0 0 4px; }
  &.ga-r.ga-b { border-radius: 0 0 4px 0; }

  // 两成员独占半区时圆角完整
  &.ga-c.ga-l { border-radius: 4px 0 0 4px; }
  &.ga-c.ga-r { border-radius: 0 4px 4px 0; }
}

.ga-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.ga-initial {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba($primary-color, 0.14);
  color: $primary-color;
  font-weight: 600;
  font-size: 1em;
}

// 右下角大省略号（5+ 成员）
.ga-ellipsis {
  background: #ece9de;
  color: #5f5d55;

  .ga-dots {
    font-weight: 700;
    line-height: 1;
    letter-spacing: -0.08em;
    transform: translateY(-0.05em);
  }
}
</style>
