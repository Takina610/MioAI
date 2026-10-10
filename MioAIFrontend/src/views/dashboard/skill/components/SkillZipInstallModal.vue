<template>
  <a-modal
    :open="visible"
    title="从 zip 包安装技能"
    :width="520"
    :confirm-loading="installing"
    ok-text="安装"
    cancel-text="取消"
    :ok-button-props="{ disabled: !file }"
    @ok="handleInstall"
    @cancel="handleClose"
  >
    <a-upload-dragger
      :max-count="1"
      accept=".zip"
      :before-upload="handleBeforeUpload"
      :file-list="fileList"
      :disabled="installing"
      @remove="handleRemove"
    >
      <p class="upload-hint">
        <InboxOutlined />
        <span>点击或拖拽 zip 包到此处</span>
      </p>
    </a-upload-dragger>

    <template v-if="result">
      <a-alert
        type="success"
        :message="`成功安装 ${result.installedCount} 个技能`"
        show-icon
        class="install-result"
      />
      <a-alert
        v-for="item in result.skipped"
        :key="item.name"
        type="warning"
        :message="`「${item.name}」已跳过：${item.reason}`"
        show-icon
        class="install-result"
      />
    </template>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { message } from 'ant-design-vue'
import { InboxOutlined } from '@ant-design/icons-vue'
import { installSkillZip } from '@/api/skill'
import type { SkillZipInstallResult } from '@/types/skill'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success'): void
}>()

const file = ref<File | null>(null)
const installing = ref(false)
const result = ref<SkillZipInstallResult | null>(null)

const fileList = computed(() => (file.value ? [{ uid: '1', name: file.value.name } as any] : []))

function handleBeforeUpload(rawFile: File): boolean {
  if (!rawFile.name.toLowerCase().endsWith('.zip')) {
    message.error('仅支持 .zip 文件')
    return false
  }
  file.value = rawFile
  result.value = null
  return false
}

function handleRemove(): void {
  file.value = null
  result.value = null
}

async function handleInstall(): Promise<void> {
  if (!file.value || installing.value) return
  installing.value = true
  try {
    result.value = await installSkillZip(file.value)
    if (result.value.installedCount > 0) {
      emit('success')
      if (result.value.skipped.length === 0) {
        message.success(`成功安装 ${result.value.installedCount} 个技能`)
        handleClose()
      }
    } else if (result.value.skipped.length > 0) {
      message.warning('没有新安装的技能')
    }
  } catch (e) {
    console.error(e)
  } finally {
    installing.value = false
  }
}

function handleClose(): void {
  emit('update:visible', false)
}
</script>

<style lang="scss" scoped>
.upload-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin: 24px 0;
  color: #6e6b62;

  .anticon {
    font-size: 32px;
    color: $primary-color;
  }
}

.install-result {
  margin-top: 12px;
}
</style>
