<template>
  <div class="md-view" ref="rootRef" @click="onClick">
    <div v-for="(block, index) in blocks" :key="block.key" class="md-block" v-html="block.html"></div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { renderBlocks } from '@/utils/markdown/streaming'
import 'katex/dist/katex.min.css'
import 'highlight.js/styles/github.css'

const props = defineProps<{ content: string }>()

const rootRef = ref<HTMLElement | null>(null)

const blocks = computed(() => renderBlocks(props.content))

/** 代码块复制（事件委托：header 的复制按钮由 markdown 渲染生成，无法绑 Vue 事件） */
function onClick(e: MouseEvent): void {
  const target = e.target as HTMLElement
  if (!target.classList.contains('md-copy-btn')) return
  const wrap = target.closest('.md-code-wrap')
  const code = wrap?.querySelector('code')
  if (!code) return
  navigator.clipboard.writeText(code.textContent ?? '').then(() => {
    target.textContent = '已复制'
    setTimeout(() => { target.textContent = '复制' }, 1500)
  }).catch(() => {
    target.textContent = '复制失败'
    setTimeout(() => { target.textContent = '复制' }, 1500)
  })
}
</script>

<style lang="scss">
// 代码块外壳：header（语言标签 + 复制按钮）与代码区一体（全局样式：v-html 内容不吃 scoped）
.md-view .md-code-wrap {
  margin: 10px 0;
  border: 1px solid #e8e6dc;
  border-radius: 10px;
  overflow: hidden;
  background: #f5f3ec;

  .md-code-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 5px 12px;
    border-bottom: 1px solid #ece9de;
    background: #f0ede4;
    user-select: none;

    .md-code-lang {
      font-size: 12px;
      color: #8c8a82;
      font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    }

    .md-copy-btn {
      font-size: 12px;
      color: #5f5d55;
      cursor: pointer;
      padding: 1px 8px;
      border-radius: 6px;
      transition: all 0.15s;

      &:hover {
        color: $primary-color;
        background: rgba(42, 161, 169, 0.08);
      }
    }
  }

  pre.md-code {
    margin: 0;
    border: none;
    border-radius: 0;
  }
}
</style>
