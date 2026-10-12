<template>
  <div class="agent-conversation-settings">
    <div class="field">
      <span class="field-label">开场白</span>
      <a-textarea
        :value="openingMessage"
        :maxlength="500"
        :rows="3"
        show-count
        placeholder="新会话首屏展示"
        @update:value="openingMessage = $event"
      />
    </div>

    <div class="field">
      <span class="field-label">推荐问题<span v-if="questions.length" class="field-count">{{ questions.length }}/5</span></span>
      <div class="question-tags">
        <a-tag
          v-for="(q, i) in questions"
          :key="`${i}-${q}`"
          class="question-tag"
          closable
          @close.prevent="removeQuestion(i)"
        >
          {{ q }}
        </a-tag>
        <a-input
          v-if="questions.length < 5"
          v-model:value="questionInput"
          class="question-input"
          :maxlength="50"
          placeholder="输入后回车添加"
          @press-enter="addQuestion"
        />
      </div>
    </div>

    <div class="field">
      <span class="field-label">温度</span>
      <div class="temp-row">
        <a-switch
          size="small"
          :checked="tempCustom"
          @change="(checked: any) => (temperature = checked ? 0.7 : null)"
        />
        <template v-if="tempCustom">
          <a-slider
            class="temp-slider"
            :value="tempValue"
            :min="0"
            :max="2"
            :step="0.1"
            :tooltip-open="false"
            @update:value="temperature = $event"
          />
          <span class="temp-value">{{ Number(temperature).toFixed(1) }}</span>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { message } from 'ant-design-vue'

const openingMessage = defineModel<string | undefined>('openingMessage')
const questions = defineModel<string[]>('openingQuestions', { default: () => [] })
const temperature = defineModel<number | null>('temperature')

const questionInput = ref('')

const MAX_QUESTIONS = 5

const tempCustom = computed(() => temperature.value != null)
const tempValue = computed(() => temperature.value ?? 0.7)

function addQuestion(): void {
  const value = questionInput.value.trim()
  if (!value) return
  if (questions.value.length >= MAX_QUESTIONS) {
    message.warning(`推荐问题最多 ${MAX_QUESTIONS} 条`)
    return
  }
  if (questions.value.includes(value)) {
    message.warning('该推荐问题已存在')
    return
  }
  questions.value = [...questions.value, value]
  questionInput.value = ''
}

function removeQuestion(index: number): void {
  questions.value = questions.value.filter((_, i) => i !== index)
}
</script>

<style lang="scss" scoped>
.agent-conversation-settings {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.field {
  .field-label {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    font-weight: 600;
    color: $text-dark;
    margin-bottom: 8px;

    .field-count {
      font-size: 11px;
      font-weight: 500;
      color: #8c8a82;
    }
  }
}

.question-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;

  .question-tag {
    margin: 0;
    border-radius: 12px;
    background: rgba(42, 161, 169, 0.08);
    border-color: rgba(42, 161, 169, 0.25);
    color: $text-dark;
  }

  .question-input {
    width: 180px;
    border-radius: 12px;
  }
}

.temp-row {
  display: flex;
  align-items: center;
  gap: 12px;

  .temp-slider {
    flex: 1;
    margin: 0 4px;
  }

  .temp-value {
    min-width: 28px;
    font-size: 12px;
    color: $text-dark;
    text-align: right;
    font-variant-numeric: tabular-nums;
  }
}
</style>
