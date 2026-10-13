<template>
  <!-- 群聊堆叠头像：成员数 2~4 全量展示（一大+若干小）；5+ 展示 3 个 + 右下角大省略号 -->
  <span class="group-avatar" :style="{ width: size + 'px', height: size + 'px' }">
    <span
      v-for="(m, i) in shown"
      :key="m.id"
      class="ga-item"
      :class="`ga-${i === 0 ? 'main' : slotOf(i)}`"
      :style="itemStyle(i)"
    >
      <BotIcon
        v-if="iconOf(m)"
        :shape="iconOf(m)!.shape"
        :fill="iconOf(m)!.fill"
        :size="Math.round(size * (i === 0 ? 0.66 : 0.42))"
        :live="live"
      />
      <img v-else-if="m.avatar" :src="m.avatar" alt="" class="ga-img" />
      <span v-else class="ga-initial" :style="{ fontSize: Math.round(size * 0.2) + 'px' }">
        {{ m.name?.charAt(0)?.toUpperCase() }}
      </span>
    </span>
    <span v-if="overflow" class="ga-item ga-ellipsis" :style="ellipsisStyle">
      <span class="ga-dots">···</span>
    </span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import BotIcon from '@/components/bot-icon/BotIcon.vue'
import { parseBotIcon, type BotIconConfig } from '@/components/bot-icon/types'

export interface GroupAvatarMember {
  id: number
  name?: string
  icon?: string
  avatar?: string
}

const props = withDefaults(defineProps<{
  members: GroupAvatarMember[]
  /** 容器边长（px） */
  size?: number
  /** 小图标是否动画（列表里跟随整轨动态时开） */
  live?: boolean
}>(), {
  size: 40,
  live: false
})

const MAX_SHOWN = 4

/** 2~4 个成员全量展示；5+ 只展示前 3 个（右下角省略号示意更多） */
const shown = computed(() =>
  props.members.slice(0, overflow.value ? 3 : Math.min(props.members.length, MAX_SHOWN)))
const overflow = computed(() => props.members.length > MAX_SHOWN)

/** 布局槽位：0=主头像(左上大)，其余按 1=右下 2=左下 3=右上；溢出时省略号占右下 */
function slotOf(index: number): string {
  return ['br', 'bl', 'tr'][index - 1] ?? 'br'
}

const SMALL = 0.42
const MAIN = 0.66

function itemStyle(index: number): Record<string, string> {
  const s = props.size
  const small = Math.round(s * SMALL)
  const main = Math.round(s * MAIN)
  if (index === 0) {
    return { width: main + 'px', height: main + 'px', left: '0', top: '0' }
  }
  const slot = slotOf(index)
  const pos: Record<string, { left: string; top: string }> = {
    br: { left: Math.round(s * 0.56) + 'px', top: Math.round(s * 0.56) + 'px' },
    bl: { left: '0', top: Math.round(s * 0.58) + 'px' },
    tr: { left: Math.round(s * 0.58) + 'px', top: Math.round(s * 0.04) + 'px' }
  }
  return { width: small + 'px', height: small + 'px', ...pos[slot] }
}

const ellipsisStyle = computed(() => {
  const s = props.size
  const small = Math.round(s * SMALL)
  return {
    width: small + 'px',
    height: small + 'px',
    left: Math.round(s * 0.56) + 'px',
    top: Math.round(s * 0.56) + 'px',
    fontSize: Math.round(s * 0.3) + 'px'
  }
})

const iconCache = new Map<number, BotIconConfig | null>()
function iconOf(m: GroupAvatarMember): BotIconConfig | null {
  if (!iconCache.has(m.id)) {
    iconCache.set(m.id, parseBotIcon(m.icon))
  }
  return iconCache.get(m.id) ?? null
}
</script>

<style lang="scss" scoped>
.group-avatar {
  position: relative;
  display: inline-block;
  flex-shrink: 0;
}

.ga-item {
  position: absolute;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #fff;
  overflow: hidden;

  // 白描边分离重叠的成员头像
  &:not(.ga-main) {
    border: 1.5px solid #fff;
    box-shadow: 0 1px 3px rgba(20, 20, 19, 0.12);
  }
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
}

// 右下角大省略号（5+ 成员）：字号随容器尺寸放大，醒目于普通文字
.ga-ellipsis {
  background: #ece9de;
  color: #5f5d55;

  .ga-dots {
    font-weight: 700;
    line-height: 1;
    letter-spacing: -0.06em;
    transform: translateY(-0.06em);
  }
}
</style>
