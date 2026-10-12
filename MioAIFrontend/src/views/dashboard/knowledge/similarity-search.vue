<template>
  <div class="dash-page similarity-search">
    <PageHeader
      :back-label="isPublic ? '公共知识库' : '知识库管理'"
      :back-to="isPublic ? '/dashboard/public-knowledge' : '/dashboard/knowledge'"
      title="命中测试"
    />

    <div class="dash-page-content">
      <div class="hit-layout">
        <aside class="hit-rail">
          <div class="kb-chip">
            <div class="kb-icon"><DatabaseOutlined /></div>
            <div class="kb-info">
              <span class="kb-name" :title="kb?.name">{{ kb?.name || '加载中…' }}</span>
              <span class="kb-meta">{{ kb ? `${kb.documentCount || 0} 个文档` : ' ' }}</span>
            </div>
          </div>

          <a-textarea
            ref="queryRef"
            v-model:value="searchContent"
            class="query-input"
            placeholder="输入一个问题或关键词"
            :maxlength="500"
            :rows="5"
            show-count
            @keydown.ctrl.enter.prevent="runSearch"
            @keydown.meta.enter.prevent="runSearch"
          />

          <a-button
            type="primary"
            block
            class="run-btn"
            :loading="searching"
            :disabled="!canSearch"
            @click="runSearch"
          >
            开始测试
          </a-button>

          <div class="param">
            <span class="param-label">检索方式</span>
            <a-segmented
              v-model:value="config.method"
              class="param-segmented"
              size="small"
              :options="[{ label: '混合', value: 'hybrid' }, { label: '向量', value: 'vector' }]"
            />
          </div>
          <div class="param">
            <span class="param-label">返回数量</span>
            <a-input-number v-model:value="config.topK" :min="1" :max="10" size="small" class="param-number" />
          </div>
          <div class="param param-block">
            <div class="param-head">
              <span class="param-label">相似度阈值</span>
              <span class="param-value">{{ thresholdPercent }}%</span>
            </div>
            <a-slider
              v-model:value="thresholdPercent"
              :min="0"
              :max="100"
              :step="5"
              :tooltip-visible="false"
            />
          </div>
          <div class="param">
            <span class="param-label">重排</span>
            <a-switch v-model:checked="config.enableRerank" size="small" />
          </div>
        </aside>

        <section class="hit-results">
          <div v-if="!hasSearched && !searching" class="results-blank">
            <AimOutlined class="blank-icon" />
            <p>输入内容，测试知识库能召回什么</p>
          </div>

          <template v-else-if="searching">
            <div v-for="i in 3" :key="i" class="hit-row skeleton-row">
              <a-skeleton active :title="{ width: '30%' }" :paragraph="{ rows: 2, width: ['90%', '60%'] }" />
            </div>
          </template>

          <template v-else>
            <div v-if="results.length" class="results-head">
              <span>召回 {{ results.length }} 条</span>
              <span class="elapsed">{{ elapsedMs }} ms</span>
            </div>
            <a-empty v-else class="results-empty" description="未命中任何内容，试试降低阈值或换一种问法" />

            <div v-for="(result, index) in results" :key="result.id" class="hit-row">
              <span class="hit-rank">{{ String(index + 1).padStart(2, '0') }}</span>

              <div class="hit-main">
                <div class="hit-meta">
                  <span v-if="matchTypeText(result)" class="match-chip" :class="`chip-${result.matchType}`">
                    {{ matchTypeText(result) }}
                  </span>
                  <span class="hit-doc">
                    <FileTextOutlined />
                    <span class="doc-name">{{ result.fileName || '未知文档' }}</span>
                    <em v-if="result.chunkIndex != null">#{{ result.chunkIndex }}</em>
                  </span>
                </div>

                <div class="hit-body" :class="{ clamped: !expandedIds.has(result.id) }">
                  <HitText :text="result.text" :query="searchContent" />
                </div>
                <button
                  v-if="result.text.length > 240"
                  type="button"
                  class="expand-btn"
                  @click="toggleExpand(result.id)"
                >
                  {{ expandedIds.has(result.id) ? '收起' : '展开全文' }}
                </button>
              </div>

              <div class="hit-score">
                <span class="score-num">{{ (result.score * 100).toFixed(1) }}<em>%</em></span>
                <span class="score-track">
                  <i
                    class="score-fill"
                    :style="{ width: `${Math.round(result.score * 100)}%`, opacity: 0.4 + result.score * 0.6 }"
                  />
                </span>
              </div>
            </div>
          </template>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { AimOutlined, DatabaseOutlined, FileTextOutlined } from '@ant-design/icons-vue'
import { similaritySearchV2, getKnowledgeBaseById, type SimilarityResult } from '@/api/knowledgeBase'
import type { KnowledgeBase } from '@/types'
import PageHeader from '../components/PageHeader.vue'
import HitText from './components/HitText.vue'

const route = useRoute()

/** 公共/私有知识库共用本页，按路由前缀区分面包屑与返回地址 */
const isPublic = route.path.startsWith('/dashboard/public-knowledge')

const kb = ref<KnowledgeBase | null>(null)
const searching = ref(false)
const hasSearched = ref(false)
const elapsedMs = ref(0)
const thresholdPercent = ref(50)
const searchContent = ref('')
const results = ref<SimilarityResult[]>([])
const expandedIds = ref(new Set<string>())
const queryRef = ref()

const config = reactive({
  topK: 5,
  method: 'hybrid' as 'vector' | 'hybrid',
  enableRerank: false
})

const kbId = computed(() => Number(route.params.id))
const canSearch = computed(() => searchContent.value.trim().length > 0)

function matchTypeText(result: SimilarityResult): string {
  if (result.matchType === 'keyword') return '关键词'
  if (result.matchType === 'rerank') return '重排'
  return ''
}

function toggleExpand(id: string): void {
  const next = new Set(expandedIds.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  expandedIds.value = next
}

async function fetchKnowledgeBase(): Promise<void> {
  try {
    kb.value = await getKnowledgeBaseById(kbId.value)
  } catch (e) {
    console.error(e)
  }
}

async function runSearch(): Promise<void> {
  if (searching.value || !canSearch.value) return

  searching.value = true
  const t0 = performance.now()
  try {
    const res = await similaritySearchV2({
      query: searchContent.value.trim(),
      kbId: kbId.value,
      topK: config.topK,
      threshold: thresholdPercent.value / 100,
      method: config.method,
      enableRerank: config.enableRerank
    })
    results.value = res || []
    elapsedMs.value = Math.round(performance.now() - t0)
    expandedIds.value = new Set()
    hasSearched.value = true
  } catch (e) {
    console.error(e)
    message.error('检索失败，请重试')
  } finally {
    searching.value = false
  }
}

onMounted(() => {
  fetchKnowledgeBase()
  queryRef.value?.focus?.()
})
</script>

<style lang="scss" scoped>
.similarity-search {
  .hit-layout {
    display: flex;
    gap: 20px;
    align-items: flex-start;
  }

  .hit-rail {
    width: 320px;
    flex-shrink: 0;
    position: sticky;
    top: 24px;
    display: flex;
    flex-direction: column;
    background: $bg-ivory;
    border: 1px solid #ece9de;
    border-radius: 12px;
    padding: 20px;
  }

  .kb-chip {
    display: flex;
    align-items: center;
    gap: 12px;
    padding-bottom: 16px;
    margin-bottom: 16px;
    border-bottom: 1px solid $border-warm-subtle;

    .kb-icon {
      width: 40px;
      height: 40px;
      border-radius: 10px;
      background: rgba($primary-color, 0.1);
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;

      .anticon {
        font-size: 18px;
        color: $primary-color;
      }
    }

    .kb-info {
      min-width: 0;
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .kb-name {
      font-size: 14px;
      font-weight: 600;
      color: $text-dark;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .kb-meta {
      font-size: 12px;
      color: $text-muted;
    }
  }

  .query-input {
    margin-bottom: 12px;

    :deep(textarea) {
      resize: none;
    }
  }

  .run-btn {
    height: 38px;
    border-radius: 10px;
    font-weight: 500;
    margin-bottom: 20px;
  }

  .param {
    display: flex;
    align-items: center;
    justify-content: space-between;
    min-height: 32px;
    margin-bottom: 12px;

    &:last-child {
      margin-bottom: 0;
    }

    &.param-block {
      display: block;

      .param-head {
        display: flex;
        justify-content: space-between;
        align-items: baseline;
      }

      :deep(.ant-slider) {
        margin: 6px 0 2px;
      }
    }

    .param-label {
      font-size: 13px;
      color: $text-secondary;
    }

    .param-value {
      font-size: 13px;
      font-weight: 600;
      color: $text-dark;
      font-variant-numeric: tabular-nums;
    }

    .param-segmented {
      width: auto;
    }

    .param-number {
      width: 72px;
    }
  }

  .hit-results {
    flex: 1;
    min-width: 0;
  }

  .results-blank {
    height: 320px;
    border: 1px dashed $border-warm;
    border-radius: 12px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 12px;
    color: $text-muted;

    .blank-icon {
      font-size: 32px;
      color: rgba($primary-color, 0.5);
    }

    p {
      margin: 0;
      font-size: 14px;
    }
  }

  .results-head {
    display: flex;
    align-items: baseline;
    gap: 12px;
    margin-bottom: 12px;
    font-size: 13px;
    color: $text-secondary;

    .elapsed {
      font-variant-numeric: tabular-nums;
      color: $text-muted;
    }
  }

  .results-empty {
    padding: 80px 0;
  }

  .hit-row {
    display: flex;
    gap: 16px;
    padding: 16px 20px;
    background: $bg-ivory;
    border: 1px solid #ece9de;
    border-radius: 12px;
    margin-bottom: 12px;
    transition: border-color 0.2s, box-shadow 0.2s;

    &:hover {
      border-color: rgba($primary-color, 0.4);
      box-shadow: $shadow-light;
    }

    &.skeleton-row {
      padding: 20px;
    }
  }

  .hit-rank {
    flex-shrink: 0;
    width: 24px;
    padding-top: 2px;
    font-size: 13px;
    font-variant-numeric: tabular-nums;
    color: $text-muted;
  }

  .hit-main {
    flex: 1;
    min-width: 0;
  }

  .hit-meta {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 8px;

    .match-chip {
      flex-shrink: 0;
      font-size: 11px;
      line-height: 18px;
      padding: 0 7px;
      border-radius: 4px;

      &.chip-keyword {
        background: $background-light;
        color: #8c6d2f;
      }

      &.chip-rerank {
        background: rgba($primary-color, 0.12);
        color: darken($primary-color, 15%);
      }
    }

    .hit-doc {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 12px;
      color: $text-muted;
      min-width: 0;

      .anticon {
        color: $primary-color;
        flex-shrink: 0;
      }

      .doc-name {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      em {
        flex-shrink: 0;
        font-style: normal;
        font-variant-numeric: tabular-nums;
        color: $primary-color;
        background: rgba($primary-color, 0.08);
        border-radius: 4px;
        padding: 0 5px;
      }
    }
  }

  .hit-body {
    font-size: 14px;
    line-height: 1.75;
    color: $text-dark;
    white-space: pre-wrap;
    word-break: break-word;

    &.clamped {
      display: -webkit-box;
      -webkit-line-clamp: 6;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }
  }

  .expand-btn {
    display: block;
    margin-top: 6px;
    padding: 0;
    border: none;
    background: none;
    cursor: pointer;
    font-size: 12px;
    color: $primary-color;

    &:hover {
      color: darken($primary-color, 10%);
    }
  }

  .hit-score {
    flex-shrink: 0;
    width: 96px;
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 6px;
    padding-top: 2px;

    .score-num {
      font-size: 20px;
      font-weight: 600;
      line-height: 1;
      color: $text-dark;
      font-variant-numeric: tabular-nums;

      em {
        font-style: normal;
        font-size: 12px;
        font-weight: 500;
        color: $text-muted;
        margin-left: 1px;
      }
    }

    .score-track {
      width: 100%;
      height: 4px;
      border-radius: 2px;
      background: $border-warm-subtle;
      overflow: hidden;

      .score-fill {
        display: block;
        height: 100%;
        border-radius: 2px;
        background: $primary-color;
        transition: width 0.4s ease;
      }
    }
  }

  @media (max-width: 1100px) {
    .hit-layout {
      flex-direction: column;
    }

    .hit-rail {
      width: 100%;
      position: static;
    }
  }
}
</style>
