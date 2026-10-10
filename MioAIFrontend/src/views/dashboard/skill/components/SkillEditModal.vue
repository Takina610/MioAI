<template>
  <a-modal
    :open="visible"
    :title="isEdit ? '编辑技能' : '新建技能'"
    :width="720"
    :footer="null"
    :destroyOnClose="true"
    class="skill-edit-modal"
    @cancel="handleClose"
  >
    <a-form :model="formData" layout="vertical">
      <a-form-item label="技能名称" required>
        <a-input
          v-model:value="formData.name"
          placeholder="例如：网页风格克隆"
          :maxlength="100"
          show-count
        />
      </a-form-item>

      <a-form-item label="技能描述">
        <a-textarea
          v-model:value="formData.description"
          placeholder="描述该技能的用途和适用场景，智能体据此判断何时使用"
          :rows="2"
          :maxlength="500"
          show-count
        />
      </a-form-item>

      <a-form-item label="SKILL.md 内容">
        <a-textarea
          v-model:value="formData.content"
          placeholder="技能的详细说明，支持 Markdown"
          :rows="12"
          class="content-editor"
        />
      </a-form-item>

      <a-form-item>
        <template #label>
          <span>
            附属文件
            <a-button type="link" size="small" @click="addFile">
              <PlusOutlined /> 添加
            </a-button>
          </span>
        </template>
        <div v-for="(file, index) in formData.files" :key="index" class="file-row">
          <a-input v-model:value="file.path" placeholder="相对路径，如 scripts/run.py" class="file-path" />
          <a-textarea v-model:value="file.content" placeholder="文件内容" :rows="2" class="file-content" />
          <a-button type="text" danger @click="formData.files.splice(index, 1)">
            <DeleteOutlined />
          </a-button>
        </div>
        <div v-if="formData.files.length === 0" class="files-empty">无附属文件</div>
      </a-form-item>

      <a-form-item label="可见性">
        <a-radio-group v-model:value="formData.isPublic">
          <a-radio :value="0">私有</a-radio>
          <a-radio :value="1">公开</a-radio>
        </a-radio-group>
      </a-form-item>
    </a-form>

    <div class="modal-footer">
      <a-button @click="handleClose">取消</a-button>
      <a-button type="primary" :loading="saving" @click="handleSave">保存</a-button>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { addSkill, updateSkill } from '@/api/skill'
import type { Skill, SkillFile } from '@/types/skill'

interface Props {
  visible: boolean
  skill?: Skill | null
}

const props = defineProps<Props>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success'): void
}>()

const saving = ref(false)
const isEdit = ref(false)

const formData = reactive({
  name: '',
  description: '',
  content: '',
  files: [] as SkillFile[],
  isPublic: 0
})

watch(() => props.visible, (visible) => {
  if (!visible) return
  const skill = props.skill
  isEdit.value = !!skill
  formData.name = skill?.name || ''
  formData.description = skill?.description || ''
  formData.content = skill?.content || ''
  formData.files = (skill?.files || []).map(f => ({ path: f.path, content: f.content }))
  formData.isPublic = skill?.isPublic ?? 0
})

function addFile(): void {
  formData.files.push({ path: '', content: '' })
}

async function handleSave(): Promise<void> {
  if (!formData.name.trim()) {
    message.warning('请输入技能名称')
    return
  }
  saving.value = true
  try {
    if (isEdit.value && props.skill) {
      await updateSkill({
        id: props.skill.id,
        name: formData.name.trim(),
        description: formData.description,
        content: formData.content,
        files: formData.files.filter(f => f.path.trim()),
        isPublic: formData.isPublic
      })
      message.success('保存成功')
    } else {
      await addSkill({
        name: formData.name.trim(),
        description: formData.description,
        content: formData.content,
        files: formData.files.filter(f => f.path.trim()),
        isPublic: formData.isPublic
      })
      message.success('创建成功')
    }
    emit('update:visible', false)
    emit('success')
  } catch (e) {
    console.error(e)
  } finally {
    saving.value = false
  }
}

function handleClose(): void {
  emit('update:visible', false)
}
</script>

<style lang="scss" scoped>
.skill-edit-modal {
  .file-row {
    display: flex;
    flex-direction: column;
    gap: 6px;
    padding: 10px;
    margin-bottom: 8px;
    background: #f5f3ec;
    border-radius: 8px;

    .file-path {
      font-family: monospace;
    }
  }

  .files-empty {
    font-size: 13px;
    color: #8c8a82;
  }

  .modal-footer {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
    margin-top: 24px;
    padding-top: 16px;
    border-top: 1px solid #ece9de;

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
