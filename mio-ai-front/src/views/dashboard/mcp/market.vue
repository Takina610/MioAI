<template>
  <div class="mcp-market">
    <div class="page-header">
      <div class="header-content">
        <h2>MCP广场</h2>
        <p class="desc">探索公开的MCP工具</p>
      </div>
      <div class="header-line"></div>
    </div>

    <div class="page-content" v-if="!loading && mcpList.length === 0">
      <a-empty  description="暂无公开MCP工具" />
    </div>
    
    <div class="mcp-list">
      <a-row :gutter="[16, 16]">
      <a-col :xs="24" :sm="12" :md="8" :lg="6" v-for="mcp in mcpList" :key="mcp.id">
        <div class="mcp-card" @click="viewTool(mcp)">
          <div class="card-header">
            <div class="icon-wrapper">
              <ToolOutlined />
            </div>
          </div>
          <h3 class="card-title">{{ mcp.name }}</h3>
          <p class="card-desc">{{ mcp.description || '暂无描述' }}</p>
          <div class="card-info">
            <div class="info-item">
              <span class="label">服务:</span>
              <span class="value">{{ mcp.serverName || '-' }}</span>
            </div>
          </div>
          <div class="card-footer">
            <span class="author">{{ mcp.userName || '匿名' }}</span>
            <a-tag :color="mcp.isPublic === 1 ? 'green' : 'orange'">
              {{ mcp.isPublic === 1 ? '公开' : '私有' }}
            </a-tag>
          </div>
        </div>
      </a-col>
      </a-row>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ToolOutlined } from '@ant-design/icons-vue'
import { getPublicMcpTools } from '@/api/mcpTool'
import type { McpTool, PageResponse } from '@/types'

interface McpToolWithUser extends McpTool {
  userName?: string
  type?: string
}

const loading = ref<boolean>(false)
const mcpList = ref<McpToolWithUser[]>([])

onMounted(() => {
  fetchMcpTools()
})

async function fetchMcpTools(): Promise<void> {
  loading.value = true
  try {
    const res: PageResponse<McpToolWithUser> = await getPublicMcpTools()
    mcpList.value = res?.records || []
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function viewTool(tool: McpToolWithUser): void {
  console.log('查看工具:', tool)
}
</script>

<style lang="scss" scoped>
.mcp-market {
  height: 100%;
  .page-header {
    .header-content {
      padding: 10px 24px;
      h2 {
        font-size: 24px;
        font-weight: 600;
        color: #202124;
        margin-bottom: 8px;
      }

      .desc {
        color: #5f6368;
        font-size: 14px;
        margin-bottom: 0px;
      }
    }
    .header-line {
      height: 1px;
      background: #e8eaed;
    }
  }

  .page-content {
    height: calc(100% - 140px);
    display: flex;
    flex-direction: column;
    justify-content: center;
  }

  .mcp-list {
    margin-top: 24px;
  }

  .mcp-card {
    background: #fff;
    border-radius: 12px;
    padding: 20px;
    cursor: pointer;
    transition: all 0.3s;
    border: 1px solid #f0f0f0;

    &:hover {
      transform: translateY(-4px);
      box-shadow: $shadow-medium;
    }

    .card-header {
      margin-bottom: 16px;

      .icon-wrapper {
        width: 48px;
        height: 48px;
        border-radius: 12px;
        background: rgba($primary-color, 0.1);
        display: flex;
        align-items: center;
        justify-content: center;

        .anticon {
          font-size: 24px;
          color: $primary-color;
        }
      }
    }

    .card-title {
      font-size: 16px;
      font-weight: 600;
      color: $text-dark;
      margin-bottom: 8px;
    }

    .card-desc {
      font-size: 13px;
      color: #666;
      margin-bottom: 16px;
      display: -webkit-box;
      line-clamp: 2;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }

    .card-info {
      margin-bottom: 16px;

      .info-item {
        font-size: 13px;
        color: #666;

        .label {
          color: #999;
          margin-right: 4px;
        }

        .value {
          color: $text-dark;
        }
      }
    }

    .card-footer {
      display: flex;
      justify-content: space-between;
      align-items: center;

      .author {
        font-size: 12px;
        color: #999;
      }
    }
  }
}
</style>
