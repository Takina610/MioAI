<template>
  <div class="chat-welcome">
    <div class="welcome-icon">
      <BotIcon
        v-if="iconConfig"
        :shape="iconConfig.shape"
        :fill="iconConfig.fill"
        :size="64"
        :live="false"
        eye-color="#faf9f5"
      />
      <img v-else-if="agent?.avatar" :src="agent.avatar" alt="" class="welcome-avatar" />
      <BotIcon v-else :shape="defaultIcon.shape" :fill="defaultIcon.fill" :size="64" :live="false" eye-color="#faf9f5" />
    </div>
    <h2 class="welcome-name">{{ agent?.name || 'MioBot' }}</h2>
    <p v-if="message" class="welcome-message">{{ message }}</p>
    <div v-if="questions.length" class="welcome-questions">
      <button
        v-for="q in questions"
        :key="q"
        type="button"
        class="welcome-question"
        @click="emit('ask', q)"
      >
        {{ q }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import BotIcon from '@/components/bot-icon/BotIcon.vue'
import { DEFAULT_BOT_ICON, parseBotIcon } from '@/components/bot-icon/types'
import type { Agent } from '@/types'

const props = defineProps<{
  agent: Agent | null
}>()

const emit = defineEmits<{
  (e: 'ask', question: string): void
}>()

const iconConfig = computed(() => parseBotIcon(props.agent?.icon))
const defaultIcon = DEFAULT_BOT_ICON

const message = computed(() => props.agent?.openingMessage || props.agent?.description || '')
const questions = computed(() => (props.agent?.openingQuestions || []).slice(0, 5))
</script>

<style lang="scss" scoped>
.chat-welcome {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px 16px 140px;
  text-align: center;
  pointer-events: none;

  .welcome-icon {
    pointer-events: auto;
    margin-bottom: 14px;

    .welcome-avatar {
      width: 64px;
      height: 64px;
      border-radius: 25%;
      object-fit: cover;
      display: block;
    }
  }

  .welcome-name {
    font-size: 20px;
    font-weight: 600;
    color: $text-dark;
    margin: 0 0 8px;
  }

  .welcome-message {
    max-width: 480px;
    font-size: 14px;
    line-height: 1.7;
    color: #5f5d55;
    margin: 0 0 20px;
    white-space: pre-wrap;
  }

  .welcome-questions {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 8px;
    max-width: 520px;
    pointer-events: auto;

    .welcome-question {
      padding: 7px 16px;
      border: 1px solid rgba(42, 161, 169, 0.35);
      border-radius: 18px;
      background: #faf9f5;
      color: $text-dark;
      font-size: 13px;
      cursor: pointer;
      transition: border-color 0.2s, background 0.2s;

      &:hover {
        border-color: $primary-color;
        background: rgba(42, 161, 169, 0.06);
      }
    }
  }
}
</style>
