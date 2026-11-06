<template>
  <div class="ui-agent-card">
    <!-- 群聊：成员图标堆叠（与左轨群头像同源规则），不是单一图标 -->
    <GroupAvatar
      v-if="block.target === 'group' && members.length"
      :members="members"
      :size="52"
    />
    <BotIcon
      v-else
      :shape="iconConfig.shape"
      :fill="iconConfig.fill"
      :size="52"
      :live="false"
      eye-color="#faf9f5"
    />
    <div class="ui-agent-card-body">
      <div class="ui-agent-card-head">
        <span class="ui-agent-card-name">{{ name }}</span>
        <span v-if="block.status" class="ui-agent-card-status">{{ block.status }}</span>
      </div>
      <div v-if="block.description" class="ui-agent-card-desc">{{ block.description }}</div>
    </div>
    <div v-if="actions.length" class="ui-agent-card-actions">
      <button
        v-for="(action, i) in actions"
        :key="i"
        class="ui-agent-card-btn"
        :class="{ primary: i === 0 }"
        @click="emit('navigate', action.path)"
      >{{ action.label }}</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { MessageBlock } from '@/types'
import BotIcon from '@/components/agent/bot-icon/BotIcon.vue'
import GroupAvatar from '@/components/agent/GroupAvatar.vue'
import { DEFAULT_BOT_ICON, parseBotIcon, type BotIconConfig } from '@/components/agent/bot-icon/types'

type UiBlock = Extract<MessageBlock, { type: 'ui' }>

const props = defineProps<{ block: UiBlock; chatId?: string }>()
/** navigate：后端生成的站内路径（/chat/:id 或 /dashboard/agent/:id），不经模型手写 */
const emit = defineEmits<{ (e: 'navigate', path: string): void }>()

const name = computed(() => props.block.name || '')

/** 解析失败回退默认图标：卡片始终要有可辨认的视觉，不比空着 */
const iconConfig = computed<BotIconConfig>(() =>
  parseBotIcon(props.block.icon) ?? DEFAULT_BOT_ICON
)

/** 群成员（GroupAvatar 需要 number id；后端给的是字符串） */
const members = computed(() =>
  (props.block.members ?? [])
    .map(m => ({
      id: Number(m.id),
      name: m.name,
      icon: m.icon
    }))
    .filter(m => Number.isFinite(m.id))
)

/** 只接受站内绝对路径，杜绝 javascript:/外部域名 */
const actions = computed(() =>
  (props.block.jumpActions ?? []).filter(a => a?.label && a?.path?.startsWith('/'))
)
</script>

<style scoped lang="scss">
.ui-agent-card {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.ui-agent-card-body {
  flex: 1;
  min-width: 180px;
}

.ui-agent-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ui-agent-card-name {
  font-size: 15px;
  font-weight: 600;
  color: $text-secondary;
}

.ui-agent-card-status {
  padding: 1px 7px;
  font-size: 11px;
  line-height: 1.6;
  color: $text-muted;
  background: $bg-cream;
  border: 1px solid $border-warm-subtle;
  border-radius: 10px;
}

.ui-agent-card-desc {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: $text-muted;
}

.ui-agent-card-actions {
  display: flex;
  gap: 8px;
}

.ui-agent-card-btn {
  padding: 6px 14px;
  font-size: 13px;
  color: $text-secondary;
  background: #fff;
  border: 1px solid $border-warm-subtle;
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s;

  &:hover {
    border-color: $primary-color;
    color: $primary-color;
  }

  &.primary {
    color: #fff;
    background: $primary-color;
    border-color: $primary-color;

    &:hover {
      opacity: 0.9;
      color: #fff;
    }
  }
}
</style>
