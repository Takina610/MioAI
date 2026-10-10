<template>
  <a-modal
    :open="visible"
    title="从 GitHub 导入技能"
    :width="760"
    :footer="null"
    :destroyOnClose="true"
    class="skill-import-modal"
    @cancel="handleClose"
  >
    <div class="url-row">
      <a-input
        v-model:value="url"
        placeholder="https://github.com/owner/repo（含 SKILL.md 的仓库或目录）"
        allow-clear
        :disabled="importing"
        @pressEnter="handlePreview"
      />
      <a-button
        type="primary"
        :loading="parsing"
        :disabled="!url.trim() || importing"
        @click="handlePreview"
      >
        解析
      </a-button>
    </div>

    <template v-if="preview">
      <div class="preview-meta">
        <GithubOutlined />
        <span class="repo-name">{{ preview.owner }}/{{ preview.repo }}</span>
        <a-tag color="green">{{ preview.branch }}</a-tag>
        <span v-if="preview.totalFound > preview.skills.length" class="meta-note">
          共 {{ preview.totalFound }} 个技能，仅展示前 {{ preview.skills.length }} 个
        </span>
      </div>

      <a-table
        size="small"
        :columns="columns"
        :data-source="preview.skills"
        row-key="path"
        :pagination="false"
        :scroll="{ y: 280 }"
        :loading="parsing"
        :row-selection="{ selectedRowKeys, onChange: handleSelectionChange }"
      />

      <div class="import-actions">
        <span class="selected-info">已选 {{ selectedRowKeys.length }} 个技能，添加后可在列表中安装</span>
        <a-button
          type="primary"
          :loading="importing"
          :disabled="selectedRowKeys.length === 0"
          @click="handleImport"
        >
          添加
        </a-button>
      </div>
    </template>
  </a-modal>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { GithubOutlined } from '@ant-design/icons-vue'
import { previewGithubSkills, importGithubSkills } from '@/api/skill'
import type { GithubSkillPreview } from '@/types/skill'
import { formatFileSize } from '@/utils/format'

defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success'): void
}>()

const columns = [
  { title: '技能', dataIndex: 'name', width: 180, ellipsis: true },
  { title: '描述', dataIndex: 'description', ellipsis: true },
  { title: '文件', dataIndex: 'fileCount', width: 70 },
  { title: '大小', dataIndex: 'totalSize', width: 90, customRender: ({ text }: { text: number }) => formatFileSize(text) }
]

const url = ref('')
const parsing = ref(false)
const importing = ref(false)
const preview = ref<GithubSkillPreview | null>(null)
const selectedRowKeys = ref<string[]>([])

async function handlePreview(): Promise<void> {
  if (!url.value.trim()) return
  parsing.value = true
  preview.value = null
  selectedRowKeys.value = []
  try {
    const result = await previewGithubSkills(url.value.trim())
    preview.value = result
    selectedRowKeys.value = result.skills.map(s => s.path)
    if (result.skills.length === 0) {
      message.warning('该链接下没有发现技能（未找到 SKILL.md）')
    }
  } catch (e) {
    console.error(e)
  } finally {
    parsing.value = false
  }
}

function handleSelectionChange(keys: (number | string)[]): void {
  selectedRowKeys.value = keys as string[]
}

async function handleImport(): Promise<void> {
  if (!preview.value || selectedRowKeys.value.length === 0) return
  importing.value = true
  try {
    const registered = await importGithubSkills(url.value.trim(), selectedRowKeys.value)
    if (registered.length === 0) {
      message.error('添加失败，请稍后重试')
      return
    }
    message.success(`已添加 ${registered.length} 个技能，点击「安装」获取内容`)
    emit('update:visible', false)
    emit('success')
  } catch (e) {
    console.error(e)
  } finally {
    importing.value = false
  }
}

function handleClose(): void {
  emit('update:visible', false)
}
</script>

<style lang="scss" scoped>
.skill-import-modal {
  .url-row {
    display: flex;
    gap: 12px;
    margin-bottom: 16px;
  }

  .preview-meta {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 12px;
    font-size: 13px;
    color: $text-dark;

    .repo-name {
      font-weight: 500;
    }

    .meta-note {
      color: #8c8a82;
    }
  }

  .import-actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 16px;
    padding-top: 16px;
    border-top: 1px solid #ece9de;

    .selected-info {
      font-size: 13px;
      color: #6e6b62;
    }

    :deep(.ant-btn-primary) {
      background: $primary-color;
      border-color: $primary-color;

      &:hover {
        background: darken($primary-color, 10%);
        border-color: darken($primary-color, 10%);
      }
    }
  }
}
</style>
