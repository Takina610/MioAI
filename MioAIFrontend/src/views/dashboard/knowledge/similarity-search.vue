<template>
  <div class="dash-page similarity-search">
    <PageHeader
      :back-label="isPublic ? '公共知识库' : '知识库管理'"
      :back-to="isPublic ? '/dashboard/public-knowledge' : '/dashboard/knowledge'"
      title="命中测试"
    >
      <template #actions>
        <a-button type="primary" :loading="searching" :disabled="!canSearch" @click="handleSearch">
          开始测试
        </a-button>
      </template>
    </PageHeader>

    <div class="dash-page-content">
      <a-spin :spinning="loading">
        <div class="search-container">
          <div class="config-section dash-section">
            <h3 class="section-title">检索配置</h3>
            <div class="config-item">
              <label class="config-label">检索方式</label>
              <div class="config-control">
                <a-radio-group v-model:value="config.method" class="method-group">
                  <a-radio-button value="hybrid">混合检索</a-radio-button>
                  <a-radio-button value="vector">向量检索</a-radio-button>
                </a-radio-group>
                <span class="config-hint">{{ config.method === 'hybrid' ? '语义 + 关键词双路召回并融合，专有名词命中更好' : '纯语义相似度召回' }}</span>
              </div>
            </div>
            <div class="config-item">
              <label class="config-label">返回数量 (TopK)</label>
              <div class="config-control">
                <a-input-number
                  v-model:value="config.topK"
                  :min="1"
                  :max="10"
                  :step="1"
                  style="width: 100%"
                />
              </div>
            </div>
            <div class="config-item">
              <label class="config-label">相似度阈值</label>
              <div class="config-control">
                <div class="threshold-input-wrapper">
                  <a-slider
                    v-model:value="thresholdPercent"
                    :min="0"
                    :max="1"
                    :step="0.01"
                    class="threshold-slider"
                  />
                  <a-input-number
                    v-model:value="thresholdPercent"
                    :min="0"
                    :max="1"
                    :step="0.01"
                    class="threshold-input"
                  />
                </div>
                <span class="config-hint">低于该相似度的结果将被过滤</span>
              </div>
            </div>
            <div class="config-item">
              <label class="config-label">重排（Rerank）</label>
              <div class="config-control rerank-control">
                <a-switch v-model:checked="config.enableRerank" size="small" />
                <span class="rerank-text">{{ config.enableRerank ? '对召回结果精排' : '直接使用召回排序' }}</span>
              </div>
            </div>
          </div>

          <div class="search-section dash-section">
            <h3 class="section-title">检索内容</h3>
            <div class="search-input-wrapper">
              <a-textarea
                v-model:value="searchContent"
                placeholder="输入要测试的问题或关键词"
                :maxlength="500"
                :rows="3"
                show-count
              />
            </div>

            <div class="results-section" v-if="searchResults.length > 0">
              <h4 class="results-title">召回结果 ({{ searchResults.length }} 条)</h4>
              <div class="results-list">
                <div
                  v-for="(result, index) in searchResults"
                  :key="result.id"
                  class="result-card"
                >
                  <div class="card-header">
                    <div class="card-title-wrap">
                      <span class="card-title">切片 {{ index + 1 }}</span>
                      <a-tag v-if="getMatchTag(result)" :color="getMatchTagColor(result)" class="match-tag">
                        {{ getMatchTag(result) }}
                      </a-tag>
                    </div>
                    <div class="card-score">
                      <a-progress
                        :percent="Math.round(result.score * 100)"
                        :stroke-color="getProgressColor(result.score)"
                        :show-info="false"
                        style="width: 100px"
                      />
                      <span class="score-text">相似值：{{ (result.score * 100).toFixed(1) }}%</span>
                    </div>
                  </div>
                  <a-tooltip :title="result.text" placement="topLeft">
                    <div class="card-content">
                      {{ truncateText(result.text, 100) }}
                    </div>
                  </a-tooltip>
                  <div class="card-footer">
                    <FileTextOutlined />
                    <span class="doc-name">{{ result.fileName || '未知文档' }}</span>
                    <span v-if="result.chunkIndex != null" class="chunk-index">#{{ result.chunkIndex }}</span>
                  </div>
                </div>
              </div>
            </div>

            <div class="empty-result" v-else-if="hasSearched">
              <a-empty description="未找到相关内容" />
            </div>
          </div>
        </div>
      </a-spin>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { FileTextOutlined } from '@ant-design/icons-vue'
import { similaritySearchV2, getKnowledgeBaseById, type SimilarityResult } from '@/api/knowledgeBase'
import { truncateText } from '@/utils/format'
import PageHeader from '../components/PageHeader.vue'

const route = useRoute()

/** 公共/私有知识库共用本页，按路由前缀区分面包屑与返回地址 */
const isPublic = route.path.startsWith('/dashboard/public-knowledge')

const loading = ref(false)
const searching = ref(false)
const hasSearched = ref(false)
const thresholdPercent = ref(0.5)
const searchContent = ref('')
const searchResults = ref<SimilarityResult[]>([])

const config = reactive({
  topK: 5,
  method: 'hybrid' as 'vector' | 'hybrid',
  enableRerank: false
})

const kbId = computed(() => Number(route.params.id))

const canSearch = computed(() => {
  return searchContent.value.trim().length > 0
})

async function fetchKnowledgeBase(): Promise<void> {
  loading.value = true
  try {
    await getKnowledgeBaseById(kbId.value)
  } catch (e) {
    console.error(e)
    message.error('获取知识库信息失败')
  } finally {
    loading.value = false
  }
}

async function handleSearch(): Promise<void> {
  if (!searchContent.value.trim()) {
    message.warning('请输入检索内容')
    return
  }

  searching.value = true
  hasSearched.value = false
  try {
    const res = await similaritySearchV2({
      query: searchContent.value.trim(),
      kbId: kbId.value,
      topK: config.topK,
      threshold: thresholdPercent.value,
      method: config.method,
      enableRerank: config.enableRerank
    })
    searchResults.value = res || []
    hasSearched.value = true
  } catch (e) {
    console.error(e)
    message.error('检索失败，请重试')
  } finally {
    searching.value = false
  }
}

function getMatchTag(result: SimilarityResult): string {
  if (result.matchType === 'keyword') return '关键词'
  if (result.matchType === 'rerank') return '重排'
  return ''
}

function getMatchTagColor(result: SimilarityResult): string {
  if (result.matchType === 'keyword') return 'orange'
  if (result.matchType === 'rerank') return 'geekblue'
  return 'default'
}

function getProgressColor(score: number): string {
  if (score >= 0.8) return '#52c41a'
  if (score >= 0.6) return '#2aa1a9'
  if (score >= 0.4) return '#faad14'
  return '#c0453a'
}

onMounted(() => {
  fetchKnowledgeBase()
})
</script>

<style lang="scss" scoped>
.similarity-search {
  .search-container {
    display: flex;
    gap: 24px;
  }

  .config-section {
    width: 320px;
    flex-shrink: 0;
    height: fit-content;

    .config-item {
      margin-bottom: 24px;

      &:last-child {
        margin-bottom: 0;
      }

      .config-label {
        display: block;
        font-size: 14px;
        font-weight: 500;
        color: #5f5d55;
        margin-bottom: 12px;
      }

      .config-control {
        .method-group {
          display: flex;
          width: 100%;

          :deep(.ant-radio-button-wrapper) {
            flex: 1;
            text-align: center;
          }
        }

        .config-hint {
          display: block;
          font-size: 12px;
          color: #8c8a82;
          margin-top: 8px;
        }

        .threshold-input-wrapper {
          display: flex;
          align-items: center;
          gap: 12px;

          .threshold-slider {
            flex: 1;
          }

          .threshold-input {
            width: 80px;
          }
        }

        .rerank-control {
          display: flex;
          align-items: center;
          gap: 10px;

          .rerank-text {
            font-size: 13px;
            color: #6e6b62;
          }
        }
      }
    }
  }

  .search-section {
    flex: 1;
    min-width: 0;

    .search-input-wrapper {
      margin-bottom: 24px;
    }

    .results-section {
      .results-title {
        font-size: 14px;
        font-weight: 500;
        color: #5f5d55;
        margin-bottom: 16px;
      }

      .results-list {
        display: grid;
        grid-template-columns: repeat(2, 1fr);
        gap: 16px;
      }
    }

    .result-card {
      background: #f5f3ec;
      border-radius: 8px;
      padding: 16px;
      border: 1px solid #ece9de;

      .card-title-wrap {
        display: flex;
        align-items: center;
        gap: 8px;

        .card-title {
          font-size: 14px;
          font-weight: 600;
          color: #141413;
        }

        .match-tag {
          margin: 0;
          font-size: 11px;
          line-height: 18px;
          padding: 0 6px;
        }
      }

      .card-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 12px;

        .card-score {
          display: flex;
          align-items: center;
          gap: 12px;

          .score-text {
            font-size: 14px;
            font-weight: 500;
            color: $primary-color;
            min-width: 50px;
            text-align: right;
          }
        }
      }

      .card-content {
        font-size: 14px;
        color: #5f5d55;
        line-height: 1.6;
        margin-bottom: 12px;
        cursor: pointer;

        &:hover {
          color: #141413;
        }
      }

      .card-footer {
        display: flex;
        align-items: center;
        gap: 8px;
        font-size: 12px;
        color: #8c8a82;

        .anticon {
          color: $primary-color;
        }

        .doc-name {
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .chunk-index {
          flex-shrink: 0;
          color: $primary-color;
          background: rgba($primary-color, 0.08);
          border-radius: 4px;
          padding: 0 5px;
        }
      }
    }

    .empty-result {
      padding: 60px 0;
      text-align: center;
    }
  }
}
</style>
