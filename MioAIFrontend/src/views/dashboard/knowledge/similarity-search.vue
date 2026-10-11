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
              <label class="config-label">返回数量 (TopK)</label>
              <div class="config-control">
                <a-input-number
                  v-model:value="config.topK"
                  :min="1"
                  :max="6"
                  :step="1"
                  style="width: 100%"
                />
                <span class="config-hint">最多返回 6 条结果</span>
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
              </div>
            </div>
          </div>

          <div class="search-section dash-section">
            <h3 class="section-title">检索内容</h3>
            <div class="search-input-wrapper">
              <a-textarea
                v-model:value="searchContent"
                placeholder="请输入要检索的内容（限20字以内）"
                :maxlength="20"
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
                    <span class="card-title">切片 {{ index + 1 }}</span>
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
import { similaritySearch, getKnowledgeBaseById } from '@/api/knowledgeBase'
import { truncateText } from '@/utils/format'
import PageHeader from '../components/PageHeader.vue'

interface SearchResult {
  id: string
  text: string
  score: number
  fileName: string
  metadata?: Record<string, unknown>
}

const route = useRoute()

/** 公共/私有知识库共用本页，按路由前缀区分面包屑与返回地址 */
const isPublic = route.path.startsWith('/dashboard/public-knowledge')

const loading = ref(false)
const searching = ref(false)
const hasSearched = ref(false)
const thresholdPercent = ref(0.5)
const searchContent = ref('')
const searchResults = ref<SearchResult[]>([])

const config = reactive({
  topK: 3
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
    const res = await similaritySearch(
      searchContent.value.trim(),
      thresholdPercent.value,
      config.topK,
      kbId.value
    )
    searchResults.value = res || []
    hasSearched.value = true
  } catch (e) {
    console.error(e)
    message.error('检索失败，请重试')
  } finally {
    searching.value = false
  }
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
        grid-template-columns: repeat(3, 1fr);
        gap: 16px;
      }
    }

    .result-card {
      background: #f5f3ec;
      border-radius: 8px;
      padding: 16px;
      border: 1px solid #ece9de;

      .card-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 12px;

        .card-title {
          font-size: 14px;
          font-weight: 600;
          color: #141413;
        }

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
      }
    }

    .empty-result {
      padding: 60px 0;
      text-align: center;
    }
  }
}
</style>
