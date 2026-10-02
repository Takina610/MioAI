<template>
  <div class="dash-page">
    <PageHeader
      back-label="MCP广场"
      back-to="/dashboard/mcp-market"
      :title="mcpDetail?.name || 'MCP广场详情'"
    />

    <div class="dash-page-content">
      <div v-if="loading" class="loading-container">
        <a-spin size="large" />
      </div>

      <template v-else>
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
                <McpCodeEditor :model-value="formattedConfig" readonly style="min-height: 350px; max-height: 360px;" />
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

              <McpToolList :tools="toolInfos" />
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
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getMcpToolById } from '@/api/mcpTool'
import { parseMcpTools } from '@/utils/mcpTool'
import type { McpTool } from '@/types'
import dayjs from 'dayjs'
import PageHeader from '../components/PageHeader.vue'
import McpCodeEditor from './components/McpCodeEditor.vue'
import McpToolList from './components/McpToolList.vue'

const router = useRouter()
const route = useRoute()

const loading = ref(true)
const mcpDetail = ref<McpTool | null>(null)

const formattedConfig = computed(() => {
  if (!mcpDetail.value?.config) return ''
  try {
    return JSON.stringify(JSON.parse(mcpDetail.value.config), null, 2)
  } catch {
    return mcpDetail.value.config
  }
})

const toolInfos = computed(() => parseMcpTools(mcpDetail.value?.toolInfo))

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
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function formatTime(time?: string): string {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD HH:mm')
}

onMounted(() => {
  fetchMcpDetail()
})
</script>

<style lang="scss" scoped>
.loading-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
}

.info-card,
.tools-card,
.meta-card {
  margin-bottom: 16px;

  :deep(.ant-card-head-title) {
    font-weight: 600;
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
</style>
