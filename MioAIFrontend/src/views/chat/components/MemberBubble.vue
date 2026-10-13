<template>
  <!-- 群聊成员消息：与群主平等的顶层消息（左头像 + 名称 + 气泡正文）。
       running 且无正文 = 正在输入态（三点动画）；流式期间正文渐进落位 -->
  <div class="member-message">
    <span class="member-avatar">
      <BotIcon
        v-if="iconConfig"
        :shape="iconConfig.shape"
        :fill="iconConfig.fill"
        :size="30"
        :live="true"
      />
      <span v-else class="member-initial">{{ memberName?.charAt(0)?.toUpperCase() }}</span>
    </span>
    <div class="member-body">
      <span class="member-name">{{ memberName }}</span>
      <div class="member-bubble">
        <MarkdownView v-if="block.text" class="member-text" :content="block.text" />
        <div v-if="typing" class="typing-dots">
          <span v-for="i in 3" :key="i" class="dot" :style="{ animationDelay: (i - 1) * 0.18 + 's' }" />
        </div>
        <div v-if="block.failed" class="member-failed">执行失败</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import BotIcon from '@/components/bot-icon/BotIcon.vue'
import { parseBotIcon, type BotIconConfig } from '@/components/bot-icon/types'
import MarkdownView from '@/components/MarkdownView.vue'
import type { MessageBlock } from '@/types'

type MemberBlock = Extract<MessageBlock, { type: 'member' }>

const props = defineProps<{
  block: MemberBlock
}>()

const memberName = computed(() => props.block.memberName)

const iconConfig = computed<BotIconConfig | null>(() => parseBotIcon(props.block.memberIcon))

/** 正在输入：执行中即显示（有正文时与正文并存，收尾后消失） */
const typing = computed(() => props.block.status === 'running')
</script>

<style lang="scss" scoped>
.member-message {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin: 10px 0;
}

.member-avatar {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.member-initial {
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 25%;
  background: rgba($primary-color, 0.14);
  color: $primary-color;
  font-size: 13px;
  font-weight: 600;
}

.member-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.member-name {
  font-size: 12px;
  color: #8c8a82;
  line-height: 1;
}

.member-bubble {
  max-width: 86%;
  padding: 10px 14px;
  border: 1px solid #e8e6dc;
  border-radius: 4px 14px 14px 14px;
  background: #fff;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.member-text {
  font-size: 14px;
  line-height: 1.6;
  color: #141413;
  word-break: break-word;
}

.member-failed {
  font-size: 12px;
  color: #b0aea5;
}

// 正在输入三点（与消息列表 loading-bounce 同一动画语言）
.typing-dots {
  display: inline-flex;
  gap: 4px;
  padding: 4px 0;

  .dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: #b0aea5;
    animation: member-dot-bounce 1.2s infinite ease-in-out;
  }
}

@keyframes member-dot-bounce {
  0%,
  80%,
  100% {
    transform: scale(0.5);
    opacity: 0.4;
  }

  40% {
    transform: scale(1);
    opacity: 1;
  }
}
</style>
