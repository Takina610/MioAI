<template>
  <div class="resource-block">
    <div class="resource-row" :class="{ expandable: expandable }" @click="toggle">
      <span class="resource-status">
        <BookOutlined v-if="block.type === 'kb'" class="resource-icon" />
        <ClusterOutlined v-else-if="block.type === 'mcp'" class="resource-icon" />
        <SparklesIcon v-else :size="13" class="resource-icon" />
      </span>
      <span class="resource-verb">{{ verb }}</span>
      <span v-if="meta" class="resource-meta">{{ meta }}</span>
      <CaretRightOutlined v-if="expandable" :rotate="expanded ? 90 : 0" class="caret-icon" />
    </div>

    <CollapseTransition :open="expanded">
      <!-- 知识库：按库分组，分数行首锚点 + 文件名 + 片段摘要 -->
      <div v-if="block.type === 'kb'" class="resource-detail">
        <div v-for="group in block.groups" :key="group.kbId" class="detail-group">
          <div class="group-title">{{ group.kbName }}<span class="group-count">{{ group.hits.length }} 个片段</span></div>
          <div v-for="(hit, hi) in group.hits" :key="hi" class="kb-hit">
            <div class="hit-head">
              <span class="hit-score">{{ formatScore(hit.score) }}</span>
              <span class="hit-file">{{ hit.fileName || '未命名文档' }}</span>
            </div>
            <div class="hit-snippet">{{ hit.snippet }}</div>
          </div>
        </div>
      </div>

      <!-- MCP：按服务分组，工具名 + 描述；连接失败项列在最后 -->
      <div v-else-if="block.type === 'mcp'" class="resource-detail">
        <div v-for="(server, si) in block.servers" :key="server.id ?? si" class="detail-group">
          <div class="group-title">
            {{ server.name }}
            <span class="group-count">{{ server.tools.length }} 个可用工具</span>
          </div>
          <div v-if="server.description" class="server-desc">{{ server.description }}</div>
          <div v-for="tool in server.tools" :key="tool.name" class="mcp-tool">
            <span class="tool-name">{{ tool.name }}</span>
            <span v-if="tool.description" class="tool-desc">{{ tool.description }}</span>
          </div>
        </div>
        <div v-if="block.failures?.length" class="detail-group">
          <div class="group-title failure">本轮不可用</div>
          <div v-for="(failure, fi) in block.failures" :key="fi" class="mcp-failure">{{ failure }}</div>
        </div>
      </div>
    </CollapseTransition>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { BookOutlined, CaretRightOutlined, ClusterOutlined } from '@ant-design/icons-vue'
import CollapseTransition from '@/components/CollapseTransition.vue'
import SparklesIcon from '@/components/SparklesIcon.vue'
import type { MessageBlock } from '@/types'

type KbBlock = Extract<MessageBlock, { type: 'kb' }>
type McpBlock = Extract<MessageBlock, { type: 'mcp' }>
type SkillBlock = Extract<MessageBlock, { type: 'skill' }>

const props = defineProps<{
  block: KbBlock | McpBlock | SkillBlock
}>()

const expanded = ref(false)

const expandable = computed(() => props.block.type !== 'skill')

const verb = computed(() => {
  if (props.block.type === 'kb') return '检索知识库'
  if (props.block.type === 'mcp') return '连接 MCP'
  return '加载技能'
})

const meta = computed(() => {
  if (props.block.type === 'kb') {
    const hits = props.block.groups.reduce((sum, g) => sum + g.hits.length, 0)
    return `${props.block.groups.length} 个知识库 · ${hits} 个片段`
  }
  if (props.block.type === 'mcp') {
    const tools = props.block.servers.reduce((sum, s) => sum + s.tools.length, 0)
    const parts = [`${props.block.servers.length} 个服务`, `${tools} 个可用工具`]
    if (props.block.failures?.length) parts.push(`${props.block.failures.length} 个失败`)
    return parts.join(' · ')
  }
  return props.block.skills.map(s => s.name).join('、')
})

function toggle(): void {
  if (expandable.value) {
    expanded.value = !expanded.value
  }
}

function formatScore(score?: number): string {
  return score == null ? '-' : score.toFixed(3)
}
</script>

<style lang="scss" scoped>
.resource-block {
  .resource-row {
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

    .caret-icon {
      flex-shrink: 0;
      font-size: 10px;
      color: #8c8a82;
      transition: transform 0.2s;
    }
  }

  .resource-status {
    width: 16px;
    flex-shrink: 0;
    display: flex;
    justify-content: center;
    align-items: center;
  }

  .resource-icon {
    font-size: 13px;
    color: $primary-color;
  }

  .resource-verb {
    flex-shrink: 0;
    font-size: 13px;
    font-weight: 500;
    white-space: nowrap;
    color: #5f5d55;
  }

  .resource-meta {
    flex: 1;
    min-width: 0;
    font-size: 12px;
    color: #8c8a82;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .resource-detail {
    margin: 4px 0 2px 8px;
    padding: 2px 0 4px 14px;
    border-left: 2px solid #e8e6dc;

    .detail-group {
      & + .detail-group {
        margin-top: 10px;
      }
    }

    .group-title {
      display: flex;
      align-items: baseline;
      gap: 8px;
      font-size: 13px;
      font-weight: 600;
      color: #141413;
      margin-bottom: 4px;

      .group-count {
        font-size: 12px;
        font-weight: 400;
        color: #8c8a82;
      }

      &.failure {
        color: #b3591f;
      }
    }

    .server-desc {
      font-size: 12px;
      color: #8c8a82;
      margin-bottom: 4px;
    }

    .kb-hit {
      padding: 6px 0;

      & + .kb-hit {
        border-top: 1px dashed #e8e6dc;
      }

      .hit-head {
        display: flex;
        align-items: baseline;
        gap: 8px;

        // 相关度分数行首锚点（AGENTS.md 约定：决策信息放行首）
        .hit-score {
          flex-shrink: 0;
          font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
          font-size: 12px;
          font-weight: 600;
          color: $primary-color;
        }

        .hit-file {
          font-size: 12px;
          font-weight: 500;
          color: #5f5d55;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
      }

      .hit-snippet {
        font-size: 12px;
        line-height: 1.6;
        color: #8c8a82;
        margin-top: 2px;
        display: -webkit-box;
        -webkit-line-clamp: 3;
        -webkit-box-orient: vertical;
        overflow: hidden;
      }
    }

    .mcp-tool {
      display: flex;
      align-items: baseline;
      gap: 8px;
      padding: 3px 0;
      min-width: 0;

      .tool-name {
        flex-shrink: 0;
        font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
        font-size: 12px;
        font-weight: 500;
        color: #141413;
      }

      .tool-desc {
        flex: 1;
        min-width: 0;
        font-size: 12px;
        color: #8c8a82;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }

    .mcp-failure {
      font-size: 12px;
      color: #b3591f;
      padding: 2px 0;
    }
  }
}
</style>
