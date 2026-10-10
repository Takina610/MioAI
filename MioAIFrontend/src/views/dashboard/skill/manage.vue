<template>
  <div class="dash-page">
    <PageHeader :title="`技能管理 ${skillList.length}`">
      <template #actions>
        <a-button v-if="userStore.isLoggedIn" @click="importModalVisible = true">
          <GithubOutlined /> GitHub 导入
        </a-button>
        <a-button v-if="userStore.isLoggedIn" type="primary" @click="openCreate">
          <PlusOutlined /> 新建技能
        </a-button>
      </template>
    </PageHeader>

    <div class="dash-page-content">
      <LoginPrompt v-if="!userStore.isLoggedIn" @login="emit('login-required')" />

      <template v-else>
        <a-row v-if="skillList.length > 0" :gutter="[16, 16]">
          <a-col :xs="24" :sm="12" :md="8" :lg="6" v-for="skill in skillList" :key="skill.id">
            <div class="dash-card skill-card" @click="openEdit(skill)">
              <div class="card-header">
                <div class="card-icon-wrapper">
                  <ThunderboltOutlined />
                </div>
                <h3 class="card-title">{{ skill.name }}</h3>
              </div>
              <p class="card-desc">{{ skill.description || '暂无描述' }}</p>
              <div class="card-stats">
                <div class="stat-item">
                  <FileTextOutlined />
                  <span>{{ (skill.files?.length || 0) + 1 }} 文件</span>
                </div>
                <a-tag v-if="skill.isPublic === 1" color="green" class="vis-tag">公开</a-tag>
                <a-tag v-else class="vis-tag">私有</a-tag>
                <a-tag v-if="skill.status === 0" color="red" class="vis-tag">已禁用</a-tag>
              </div>
              <div class="card-footer">
                <span class="update-time">更新于 {{ formatDate(skill.updateTime || skill.createTime) }}</span>
              </div>
              <div class="card-actions" @click.stop>
                <a-button class="action-btn primary-btn" @click="openEdit(skill)">
                  编辑
                </a-button>
                <a-button class="action-btn danger-btn" @click="confirmDelete(skill)">
                  删除
                </a-button>
              </div>
            </div>
          </a-col>
        </a-row>

        <div v-else class="dash-empty">
          <a-empty description="你还没有技能，可新建或从 GitHub 导入" />
        </div>

        <SkillEditModal v-model:visible="editModalVisible" :skill="editingSkill" @success="fetchSkills" />
        <SkillGithubImportModal v-model:visible="importModalVisible" @success="fetchSkills" />
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  PlusOutlined,
  GithubOutlined,
  ThunderboltOutlined,
  FileTextOutlined
} from '@ant-design/icons-vue'
import { useUserStore } from '@/store/user'
import { querySkills, deleteSkill } from '@/api/skill'
import { formatDate } from '@/utils/format'
import type { Skill } from '@/types/skill'
import PageHeader from '../components/PageHeader.vue'
import LoginPrompt from '../components/LoginPrompt.vue'
import SkillEditModal from './components/SkillEditModal.vue'
import SkillGithubImportModal from './components/SkillGithubImportModal.vue'

const emit = defineEmits<{
  (e: 'login-required'): void
}>()

const userStore = useUserStore()

const editModalVisible = ref(false)
const importModalVisible = ref(false)
const editingSkill = ref<Skill | null>(null)
const skillList = ref<Skill[]>([])

async function fetchSkills(): Promise<void> {
  if (!userStore.isLoggedIn) return
  try {
    const res = await querySkills({ current: 1, pageSize: 100 })
    skillList.value = res.records || []
  } catch (e) {
    console.error(e)
  }
}

function openCreate(): void {
  editingSkill.value = null
  editModalVisible.value = true
}

function openEdit(skill: Skill): void {
  editingSkill.value = skill
  editModalVisible.value = true
}

function confirmDelete(skill: Skill): void {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除技能「${skill.name}」吗？已绑定该技能的智能体将不再使用它。`,
    okText: '删除',
    cancelText: '取消',
    okButtonProps: { danger: true },
    async onOk() {
      await deleteSkill(skill.id)
      message.success('删除成功')
      fetchSkills()
    }
  })
}

onMounted(() => {
  fetchSkills()
})
</script>

<style lang="scss" scoped>
.skill-card {
  .card-stats {
    display: flex;
    align-items: center;
    gap: 8px;

    .vis-tag {
      margin-inline-end: 0;
    }
  }
}
</style>
