<template>
  <div class="mcp-market-detail">
    <div class="page-header">
      <div class="header-content">
        <div class="header-left">
          <div class="back-btn" @click="goBack">
            <h2>MCP广场</h2>
          </div>
          <h2> / {{ mcpDetail?.name || 'MCP广场详情' }}</h2>
        </div>
      </div>
      <div class="header-line"></div>
    </div>

    <div class="page-content">
      <template v-if="loading">
        <div class="loading-container">
          <a-spin size="large" />
        </div>
      </template>

      <template v-else>
        <div class="detail-content">
          <a-row :gutter="24">
            <a-col :span="14">
              <a-card title="基本信息" class="info-card">
                <a-descriptions :column="1" :label-style="{ width: '100px' }">
                  <a-descriptions-item label="工具名称">{{ mcpDetail?.name }}</a-descriptions-item>
                  <a-descriptions-item label="描述">{{ mcpDetail?.description || '暂无描述' }}</a-descriptions-item>
                  <a-descriptions-item label="可见性">
                    <a-tag :color="mcpDetail?.isPublic === 1 ? 'blue' : 'default'">
                      {{ mcpDetail?.isPublic === 1 ? '公开' : '私有' }}
                    </a-tag>
                  </a-descriptions-item>
                </a-descriptions>
                
                <a-divider style="margin: 16px 0;" />
                
                <div class="config-section">
                  <div class="section-title">MCP配置</div>
                  <div class="code-editor">
                    <div class="line-numbers" ref="lineNumbersRef">
                      <div class="line-number" v-for="line in lineCount" :key="line">{{ line }}</div>
                    </div>
                    <pre class="code-content">{{ formattedConfig }}</pre>
                  </div>
                </div>
              </a-card>
            </a-col>

            <a-col :span="10">
              <a-card title="工具列表" class="tools-card">
                <template #extra>
                  <a-tag :color="mcpDetail?.status === 1 ? 'green' : 'default'">
                    {{ mcpDetail?.status === 1 ? '启用' : '禁用' }}
                  </a-tag>
                </template>
                
                <template v-if="toolInfos.length > 0">
                  <div class="tool-list">
                    <div class="tool-item" v-for="tool in toolInfos" :key="tool.name">
                      <div class="tool-header">
                        <ToolOutlined class="tool-icon" />
                        <span class="tool-name">{{ tool.name }}</span>
                      </div>
                      <p class="tool-desc">{{ tool.description || '暂无描述' }}</p>
                    </div>
                  </div>
                </template>
                <template v-else>
                  <a-empty description="暂无工具信息" />
                </template>
              </a-card>

              <a-card title="元信息" class="meta-card">
                <div class="meta-item">
                  <span class="meta-label">创建者</span>
                  <span class="meta-value">{{ mcpDetail?.userName || '匿名' }}</span>
                </div>
                <div class="meta-item">
                  <span class="meta-label">创建时间</span>
                  <span class="meta-value">{{ formatTime(mcpDetail?.createTime) }}</span>
                </div>
                <div class="meta-item">
                  <span class="meta-label">更新时间</span>
                  <span class="meta-value">{{ formatTime(mcpDetail?.updateTime) }}</span>
                </div>
              </a-card>
            </a-col>
          </a-row>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ToolOutlined } from '@ant-design/icons-vue'
import { getMcpToolById } from '@/api/mcpTool'
import type { McpTool, McpToolInfo } from '@/types'
import { useUserStore } from '@/store/user'
import dayjs from 'dayjs'

const userStore = useUserStore()

const router = useRouter()
const route = useRoute()

const loading = ref(true)

const mcpDetail = ref<McpTool | null>(null)

const formattedConfig = computed(() => {
  if (!mcpDetail.value?.config) return ''
  try {
    const parsed = JSON.parse(mcpDetail.value.config)
    return JSON.stringify(parsed, null, 2)
  } catch {
    return mcpDetail.value.config
  }
})

const lineCount = computed(() => {
  const lines = formattedConfig.value.split('\n').length
  return Math.max(lines, 1)
})

const toolInfos = computed<McpToolInfo[]>(() => {
  if (!mcpDetail.value?.toolInfo) return []
  try {
    return JSON.parse(mcpDetail.value.toolInfo)
  } catch {
    return []
  }
})

function formatTime(time?: string): string {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD HH:mm')
}

function goBack(): void {
  router.push('/dashboard/mcp-market')
}

async function fetchMcpDetail(): Promise<void> {
  const id = route.params.id as string
  if (!id) {
    router.push('/404')
    return
  }

  try {
    const data = await getMcpToolById(Number(id))

    if (!data) {
      router.push('/404')
      return
    }

    if (!data.isPublic) {
      router.push('/403')
      return
    }

    mcpDetail.value = data
  } catch (e: any) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchMcpDetail()
})
</script>

<style lang="scss" scoped>
.mcp-market-detail {
  height: 100%;
  
  .page-header {
    .header-content {
      padding: 16px 24px;
      display: flex;
      justify-content: space-between;
      align-items: center;

      .header-left {
        display: flex;
        align-items: center;
        
        .back-btn {
          &:hover {
            cursor: pointer;
          }
          h2 {
            color: #5f6368;
            font-weight: 400;
            margin-right: 8px;
          }
        }
        
        h2 {
          font-size: 24px;
          font-weight: 600;
          color: #202124;
        }
      }
    }

    .header-line {
      height: 1px;
      background: #e8eaed;
    }
  }

  .page-content {
    overflow-y: auto;
    padding: 24px;
  }
  
  .loading-container {
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: 400px;
  }
  
  .detail-content {
    .info-card,
    .tools-card,
    .meta-card {
      margin-bottom: 16px;
      :deep(.ant-card-head-title) {
        font-weight: 600;
      }
    }
  }
  
  .config-section {
    .section-title {
      font-size: 14px;
      font-weight: 500;
      color: #333;
      margin-bottom: 12px;
    }
  }
  
  .code-editor {
    display: flex;
    border: 1px solid #d9d9d9;
    border-radius: 6px;
    overflow: hidden;
    background: #1e1e1e;
    font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
    min-height: 350px;
    max-height: 360px;

    .line-numbers {
      background: #252526;
      color: #858585;
      padding: 8px 0;
      text-align: right;
      user-select: none;
      min-width: 40px;
      overflow-y: hidden;
      border-right: 1px solid #3c3c3c;
      flex-shrink: 0;

      .line-number {
        padding: 0 12px;
        line-height: 22px;
        font-size: 13px;
      }
    }

    .code-content {
      flex: 1;
      background: #1e1e1e;
      color: #d4d4d4;
      margin: 0;
      padding: 8px 12px;
      font-family: inherit;
      font-size: 13px;
      line-height: 22px;
      overflow: auto;
      white-space: pre;
    }
  }
  
  .tool-list {
    max-height: 280px;
    overflow-y: auto;
  }
  
  .tool-item {
    padding: 10px 12px;
    background: #fafafa;
    border-radius: 6px;
    margin-bottom: 6px;
    
    &:last-child {
      margin-bottom: 0;
    }
    
    .tool-header {
      display: flex;
      align-items: center;
      margin-bottom: 2px;
      
      .tool-icon {
        color: $primary-color;
        margin-right: 6px;
        font-size: 12px;
      }
      
      .tool-name {
        font-weight: 500;
        color: #333;
        font-size: 13px;
      }
    }
    
    .tool-desc {
      font-size: 12px;
      color: #666;
      margin: 0;
      padding-left: 20px;
    }
  }
  
  .meta-card {
    .meta-item {
      display: flex;
      justify-content: space-between;
      padding: 8px 0;
      border-bottom: 1px solid #f0f0f0;
      
      &:last-child {
        border-bottom: none;
      }
      
      .meta-label {
        color: #666;
      }
      
      .meta-value {
        color: #333;
      }
    }
  }
}
</style>
