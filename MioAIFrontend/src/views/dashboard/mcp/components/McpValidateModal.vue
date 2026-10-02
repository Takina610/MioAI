<template>
  <a-modal
    :open="open"
    @update:open="emit('update:open', $event)"
    title="校验结果"
    :footer="null"
    width="500px"
  >
    <div class="validate-result">
      <template v-if="result">
        <template v-if="result.success">
          <div class="result-header success">
            <CheckCircleOutlined class="result-icon" />
            <span class="result-title">校验成功</span>
            <span class="result-count">发现 {{ result.tools?.length || 0 }} 个工具</span>
          </div>
          <div class="server-info" v-if="result.serverInfo">
            <span class="info-label">服务器：</span>
            <span class="info-value">{{ result.serverInfo.name || '-' }}</span>
            <span class="info-divider">|</span>
            <span class="info-label">版本：</span>
            <span class="info-value">{{ result.serverInfo.version || '-' }}</span>
          </div>
          <McpToolList v-if="result.tools && result.tools.length > 0" :tools="result.tools" />
        </template>
        <template v-else>
          <div class="result-header error">
            <CloseCircleOutlined class="result-icon" />
            <span class="result-title">校验失败</span>
          </div>
          <div class="error-message">{{ getErrorMessage(result) }}</div>
        </template>
      </template>
      <template v-else>
        <div class="validating">
          <a-spin size="large" />
          <p>正在校验配置...</p>
        </div>
      </template>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons-vue'
import type { McpValidateResult } from '@/types'
import McpToolList from './McpToolList.vue'

defineProps<{
  open: boolean
  result?: McpValidateResult | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
}>()

function getErrorMessage(result: McpValidateResult): string {
  const messages: Record<string, string> = {
    CONFIG_INVALID: '配置无效',
    CONNECTION_FAILED: '连接失败',
    AUTH_FAILED: '认证失败',
    TIMEOUT: '连接超时'
  }
  const prefix = result.errorType ? messages[result.errorType] : undefined
  const errorMsg = result.errorMessage || '未知错误'
  return prefix ? `${prefix}: ${errorMsg}` : errorMsg
}
</script>

<style lang="scss" scoped>
.validate-result {
  padding: 8px 0;
}

.result-header {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  border-radius: 8px;
  margin-bottom: 12px;

  &.success {
    background: #f6ffed;
    border: 1px solid #b7eb8f;

    .result-icon,
    .result-title {
      color: #52c41a;
    }
  }

  &.error {
    background: #fff2f0;
    border: 1px solid #ffccc7;

    .result-icon,
    .result-title {
      color: #ff4d4f;
    }
  }

  .result-icon {
    font-size: 20px;
    margin-right: 8px;
  }

  .result-title {
    font-weight: 600;
    margin-right: 12px;
  }

  .result-count {
    color: #666;
    font-size: 13px;
  }
}

.server-info {
  padding: 8px 12px;
  background: #fafafa;
  border-radius: 6px;
  margin-bottom: 12px;
  font-size: 13px;

  .info-label {
    color: #999;
  }

  .info-value {
    color: #333;
    font-weight: 500;
  }

  .info-divider {
    margin: 0 12px;
    color: #e8e8e8;
  }
}

.error-message {
  padding: 12px;
  background: #fafafa;
  border-radius: 6px;
  color: #666;
  font-size: 13px;
}

.validating {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 150px;

  p {
    margin-top: 12px;
    color: #666;
  }
}
</style>
