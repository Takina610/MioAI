<template>
  <!-- 智能体身份图标：bot-icon 优先 → 群聊堆叠头像 → 普通头像/首字母 → 默认 bot 脸。
       静态渲染一帧（live=false），供分享页等公开轻量场景复用 -->
  <span class="agent-face" :style="{ width: size + 'px', height: size + 'px' }">
    <BotIcon v-if="icon" :shape="icon.shape" :fill="icon.fill" :size="size" :live="false" />
    <GroupAvatar v-else-if="agent?.type === 2" :members="agent?.members ?? []" :size="size" />
    <a-avatar v-else-if="agent?.avatar" :size="size" :src="agent.avatar">
      {{ agent.name?.charAt(0)?.toUpperCase() }}
    </a-avatar>
    <BotIcon v-else :shape="DEFAULT_BOT_ICON.shape" :fill="DEFAULT_BOT_ICON.fill" :size="size" :live="false" />
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import BotIcon from '@/components/bot-icon/BotIcon.vue'
import GroupAvatar from '@/components/GroupAvatar.vue'
import { DEFAULT_BOT_ICON, parseBotIcon } from '@/components/bot-icon/types'
import type { AgentMemberBrief } from '@/types'

/** 智能体展示子集（分享接口下发的字段即可，不要求完整 Agent） */
export interface AgentDisplayBrief {
  id: number
  name?: string
  avatar?: string
  icon?: string
  type?: number
  members?: AgentMemberBrief[]
}

const props = withDefaults(
  defineProps<{
    /** null 时按未配置图标兜底（默认 bot 脸） */
    agent: AgentDisplayBrief | null
    size?: number
  }>(),
  { size: 24 }
)

const icon = computed(() => parseBotIcon(props.agent?.icon))
</script>

<style lang="scss" scoped>
.agent-face {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
</style>
