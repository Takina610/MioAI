<template>
  <div class="tool-block">
    <div class="tool-row" :class="{ expandable: rowClickable }" @click="onRowClick">
      <span class="tool-status">
        <component :is="toolIcon(block.tool)" class="tool-icon" />
      </span>
      <span class="tool-verb" :class="{ running: block.status === 'running' }">{{ toolVerb(block) }}</span>
      <!-- Agent 行带 zcode GUI 同款类型标签：子智能体 subagent · 描述 -->
      <span v-if="isAgent" class="agent-tag">subagent</span>
      <span v-if="meta" class="tool-meta">{{ meta }}</span>
      <CaretRightOutlined
        v-if="block.result && !isAgent && !typing"
        :rotate="expanded ? 90 : 0"
        class="caret-icon tool-caret"
      />
    </div>
    <CollapseTransition :open="expanded">
      <pre v-if="block.result" class="tool-result">{{ block.result }}</pre>
    </CollapseTransition>

    <!-- 来源链接（DeepSeek 式：点击直接跳转网页） -->
    <div v-if="block.status === 'done' && chips.length" class="tool-sources">
      <a
        v-for="(chip, ci) in chips"
        :key="ci"
        :href="chip.url"
        target="_blank"
        rel="noopener noreferrer"
        class="source-chip"
        :title="chip.title"
      >
        <GlobalOutlined class="chip-icon" />
        <span class="chip-title">{{ chip.title || chip.host }}</span>
        <span class="chip-idx">{{ ci + 1 }}</span>
      </a>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { CaretRightOutlined, GlobalOutlined } from '@ant-design/icons-vue'
import CollapseTransition from '@/components/CollapseTransition.vue'
import { sourceChips, toolIcon, toolMeta, toolVerb, type ToolBlock } from '../toolDisplay'

/**
 * 工具语义行（zcode 约定：图标恒静态，运行态由动词文案扫光表达）：
 * 点击展开看执行结果；Agent 行（子智能体）改为上抛 open 由页面打开子代理面板，
 * 面板内（panel-mode）则退化为普通可展开行。
 */
const props = defineProps<{
  block: ToolBlock
  /** 所在消息是否流式进行中（运行态行不显示展开箭头） */
  streaming?: boolean
  /** 面板模式：Agent 行不再上抛，按普通行展开结果 */
  panelMode?: boolean
}>()

const emit = defineEmits<{
  /** Agent 行被点击（主消息流模式）：打开该子代理的只读面板 */
  (e: 'open'): void
}>()

const expanded = ref(false)

const isAgent = computed(() => props.block.tool === 'Agent')
const meta = computed(() => toolMeta(props.block))
const chips = computed(() => sourceChips(props.block))
const typing = computed(() => !!props.streaming && props.block.status === 'running')
const rowClickable = computed(() => (isAgent.value && !props.panelMode) || !!props.block.result)

function onRowClick(): void {
  if (isAgent.value && !props.panelMode) {
    emit('open')
    return
  }
  if (props.block.result) {
    expanded.value = !expanded.value
  }
}
</script>

<style lang="scss" scoped>
// zcode animated-gradient-text（styles.css）：运行态文案渐变扫光。
// 代替旋转图标——长期运行的工具行里旋转动画持续占用渲染资源，zcode 只让文字扫光
@keyframes gradient-flow {
  0% {
    background-position: 100% 0;
  }
  50% {
    background-position: 0% 0;
  }
  100% {
    background-position: 100% 0;
  }
}

.tool-block {
  animation: tool-in 0.18s ease;

  .tool-row {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 3px 8px;
    margin: 0 -8px;
    border-radius: 6px;
    min-width: 0;
    user-select: none;

    &.expandable {
      cursor: pointer;

      &:hover {
        background: #f0ede4;
      }
    }

    .tool-caret {
      flex-shrink: 0;
      font-size: 10px;
    }
  }

  // 工具执行结果（点击工具行展开）：与工具行左对齐，内容可选中复制
  .tool-result {
    margin: 4px 0 2px 0;
    padding: 8px 12px;
    background: #f5f3ec;
    border-radius: 8px;
    font-size: 12px;
    line-height: 1.55;
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    color: #5f5d55;
    white-space: pre-wrap;
    word-break: break-word;
    user-select: text;
    max-height: 220px;
    overflow-y: auto;
    @include thin-scrollbar;
  }

  .tool-status {
    width: 16px;
    flex-shrink: 0;
    display: flex;
    justify-content: center;
    align-items: center;
  }

  .tool-icon {
    font-size: 13px;
    color: $primary-color;
  }

  // 动词标签（正在执行/已执行…）：zcode kindLabel——运行态扫光 + medium，完成态浅色
  .tool-verb {
    flex-shrink: 0;
    font-size: 13px;
    font-weight: 500;
    white-space: nowrap;
    color: #5f5d55;

    &.running {
      @include animated-gradient-text($primary-color, rgba(42, 161, 169, 0.25));
    }
  }

  // Agent 行的类型标签（zcode GUI「subagent」高亮词）；描述前补间隔点
  .agent-tag {
    flex-shrink: 0;
    font-size: 13px;
    font-weight: 600;
    white-space: nowrap;
    color: $primary-color;

    + .tool-meta::before {
      content: '· ';
    }
  }

  .tool-meta {
    flex: 1;
    min-width: 0;
    font-size: 12px;
    color: #8c8a82;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  // 来源链接：点击直接跳转网页
  .tool-sources {
    margin-top: 4px;
    display: flex;
    flex-wrap: wrap;
    gap: 6px;

    .source-chip {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      max-width: 240px;
      padding: 4px 10px;
      background: #f5f3ec;
      border-radius: 8px;
      text-decoration: none;
      transition: background 0.2s;

      &:hover {
        background: #ece9de;

        .chip-title {
          color: $primary-color;
        }
      }

      .chip-icon {
        font-size: 12px;
        color: #8c8a82;
        flex-shrink: 0;
      }

      .chip-title {
        font-size: 12px;
        color: #5f5d55;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .chip-idx {
        flex-shrink: 0;
        min-width: 14px;
        height: 14px;
        padding: 0 3px;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        font-size: 10px;
        color: #8c8a82;
        background: #e8e6dc;
        border-radius: 7px;
      }
    }
  }
}

@keyframes tool-in {
  from {
    opacity: 0;
    transform: translateY(-3px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
