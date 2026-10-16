<template>
  <form class="prompt-card" @submit.prevent="submit">
    <textarea
      ref="textareaRef"
      v-model="text"
      class="prompt-input"
      rows="1"
      maxlength="800"
      placeholder="问点什么，剩下的交给我"
      aria-label="输入问题"
      @input="autoGrow"
      @keydown.enter.exact.prevent="submit"
    ></textarea>
    <div class="prompt-bar">
      <button type="submit" class="prompt-send" :disabled="!text.trim()" aria-label="开始对话">
        <ArrowUpOutlined />
      </button>
    </div>
  </form>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowUpOutlined } from '@ant-design/icons-vue'

/** 输入框自增高的上限：超过后输入框内部滚动 */
const MAX_HEIGHT = 200

const router = useRouter()
const text = ref('')
const textareaRef = ref<HTMLTextAreaElement | null>(null)

function autoGrow(): void {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, MAX_HEIGHT)}px`
}

/** 问题随路由带到对话页输入框（不自动发送，登录后即可发出） */
function submit(): void {
  const value = text.value.trim()
  if (!value) return
  router.push({ path: '/chat', query: { q: value } })
}

onMounted(() => {
  nextTick(autoGrow)
})
</script>

<style lang="scss" scoped>
.prompt-card {
  width: 100%;
  display: flex;
  flex-direction: column;
  padding: 14px 14px 12px;
  border-radius: 20px;
  background: var(--hm-bg-raised);
  border: 1.5px solid var(--hm-border);
  box-shadow: 0 1px 2px rgba(20, 20, 19, 0.03), 0 12px 32px -18px rgba(20, 20, 19, 0.22);
  cursor: text;
  transition: border-color 0.25s var(--hm-ease), box-shadow 0.25s var(--hm-ease);

  &:focus-within {
    border-color: rgba(42, 161, 169, 0.5);
    box-shadow: 0 0 0 4px rgba(42, 161, 169, 0.08), 0 14px 36px -18px rgba(20, 20, 19, 0.24);
  }
}

.prompt-input {
  width: 100%;
  min-height: 56px;
  max-height: 200px;
  padding: 4px 6px;
  border: none;
  outline: none;
  background: transparent;
  resize: none;
  overflow-y: auto;
  font-family: var(--hm-font-text);
  font-size: 16px;
  line-height: 1.7;
  color: var(--hm-text);

  &::placeholder {
    color: var(--hm-text-muted);
  }
}

.prompt-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  min-height: 38px;
}

.prompt-send {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  border: none;
  background: var(--hm-green);
  color: #fff;
  font-size: 16px;
  cursor: pointer;
  transition: background 0.25s var(--hm-ease), transform 0.25s var(--hm-ease), opacity 0.25s var(--hm-ease);

  &:hover:not(:disabled) {
    background: var(--hm-green-deep);
    transform: translateY(-1px);
  }

  &:disabled {
    background: rgba(20, 20, 19, 0.1);
    color: var(--hm-bg-raised);
    cursor: default;
  }
}
</style>
