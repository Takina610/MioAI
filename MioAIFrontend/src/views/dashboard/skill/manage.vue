<template>
  <div class="dash-page">
    <PageHeader :title="`技能管理 ${skillList.length}`">
      <template #actions>
        <a-button v-if="userStore.isLoggedIn" @click="importModalVisible = true">
          <GithubOutlined /> GitHub 导入
        </a-button>
        <a-button v-if="userStore.isLoggedIn" type="primary" @click="zipModalVisible = true">
          <PlusOutlined /> 安装技能
        </a-button>
      </template>
    </PageHeader>

    <div class="dash-page-content">
      <LoginPrompt v-if="!userStore.isLoggedIn" @login="emit('login-required')" />

      <template v-else>
        <div class="skill-toolbar">
          <a-radio-group v-model:value="source" button-style="solid" size="small">
            <a-radio-button value="mine">我的技能</a-radio-button>
            <a-radio-button value="skillssh">skills.sh</a-radio-button>
          </a-radio-group>

          <template v-if="source === 'mine'">
            <a-select
              v-model:value="repoFilter"
              class="filter-repo"
              placeholder="按仓库筛选"
              allow-clear
              size="small"
              :options="repoOptions"
            />
            <a-select
              v-model:value="installedFilter"
              class="filter-installed"
              :options="installedOptions"
              size="small"
            />
            <a-input-search
              v-model:value="searchText"
              class="filter-search"
              placeholder="搜索技能"
              allow-clear
              size="small"
            />
          </template>
          <template v-else>
            <a-input-search
              v-model:value="skillsShQuery"
              class="filter-search"
              placeholder="搜索 skills.sh 技能，回车搜索"
              allow-clear
              size="small"
              :loading="skillsShLoading"
              @search="handleSkillsShSearch"
            />
          </template>
        </div>

        <!-- 我的技能 -->
        <template v-if="source === 'mine'">
          <a-row v-if="filteredSkills.length > 0" :gutter="[16, 16]">
            <a-col :xs="24" :sm="12" :md="8" :lg="6" v-for="skill in filteredSkills" :key="skill.id">
              <div class="dash-card skill-card">
                <div class="card-header">
                  <div class="card-icon-wrapper">
                    <ThunderboltOutlined />
                  </div>
                  <h3 class="card-title" :title="skill.name">{{ skill.name }}</h3>
                  <a-tag :color="skill.installed === 1 ? 'green' : 'orange'" class="vis-tag">
                    {{ skill.installed === 1 ? '已安装' : '未安装' }}
                  </a-tag>
                </div>
                <p class="card-desc">{{ skill.description || '暂无描述' }}</p>
                <div class="card-stats">
                  <a
                    v-if="skill.repoOwner"
                    class="repo-link"
                    :title="`${skill.repoOwner}/${skill.repoName}`"
                    @click="openDoc(skill)"
                  >
                    <GithubOutlined />
                    <span>{{ skill.repoOwner }}/{{ skill.repoName }}</span>
                  </a>
                  <span v-if="skill.installed === 1" class="stat-item">
                    <FileTextOutlined />
                    <span>{{ (skill.files?.length || 0) + 1 }} 文件</span>
                  </span>
                  <a-tag v-if="skill.status === 0" color="red" class="vis-tag">已禁用</a-tag>
                </div>
                <div class="card-footer">
                  <span class="update-time">更新于 {{ formatDate(skill.updateTime || skill.createTime) }}</span>
                </div>
                <div class="card-actions" @click.stop>
                  <a-button
                    v-if="skill.docUrl"
                    class="action-btn"
                    :disabled="installingIds.has(skill.id)"
                    @click="openDoc(skill)"
                  >
                    查看
                  </a-button>
                  <a-button
                    v-if="skill.installed !== 1"
                    class="action-btn primary-btn"
                    :loading="installingIds.has(skill.id)"
                    @click="handleInstall(skill)"
                  >
                    安装
                  </a-button>
                  <a-button
                    v-else
                    class="action-btn"
                    :disabled="installingIds.has(skill.id)"
                    @click="handleUninstall(skill)"
                  >
                    卸载
                  </a-button>
                  <a-button class="action-btn danger-btn" @click="confirmDelete(skill)">删除</a-button>
                </div>
              </div>
            </a-col>
          </a-row>

          <div v-else class="dash-empty">
            <a-empty :description="mineEmptyText" />
          </div>
        </template>

        <!-- skills.sh 搜索结果 -->
        <template v-else>
          <a-row v-if="skillsShSkills.length > 0" :gutter="[16, 16]">
            <a-col :xs="24" :sm="12" :md="8" :lg="6" v-for="item in skillsShSkills" :key="`${item.owner}/${item.repo}/${item.name}`">
              <div class="dash-card skill-card">
                <div class="card-header">
                  <div class="card-icon-wrapper">
                    <ThunderboltOutlined />
                  </div>
                  <h3 class="card-title" :title="item.name">{{ item.name }}</h3>
                  <a-tag v-if="item.installed" color="green" class="vis-tag">已安装</a-tag>
                </div>
                <div class="card-stats">
                  <a class="repo-link" :title="`${item.owner}/${item.repo}`" @click="openExternal(item.repoUrl)">
                    <GithubOutlined />
                    <span>{{ item.owner }}/{{ item.repo }}</span>
                  </a>
                  <span class="stat-item">
                    <DownloadOutlined />
                    <span>{{ formatInstalls(item.installs) }} 安装</span>
                  </span>
                </div>
                <div class="card-actions" @click.stop>
                  <a-button class="action-btn" @click="openExternal(item.repoUrl)">查看</a-button>
                  <a-button
                    v-if="!item.installed"
                    class="action-btn primary-btn"
                    :loading="installingShKeys.has(shKey(item))"
                    @click="handleInstallSkillsSh(item)"
                  >
                    安装
                  </a-button>
                </div>
              </div>
            </a-col>
          </a-row>

          <div v-else class="dash-empty">
            <a-empty :description="skillsShEmptyText" />
          </div>
        </template>

        <SkillZipInstallModal v-model:visible="zipModalVisible" @success="fetchSkills" />
        <SkillGithubImportModal v-model:visible="importModalVisible" @success="fetchSkills" />
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  PlusOutlined,
  GithubOutlined,
  ThunderboltOutlined,
  FileTextOutlined,
  DownloadOutlined
} from '@ant-design/icons-vue'
import { useUserStore } from '@/store/user'
import {
  querySkills,
  deleteSkill,
  installSkill,
  uninstallSkill,
  searchSkillsSh,
  installSkillsSh
} from '@/api/skill'
import { formatDate } from '@/utils/format'
import type { Skill, SkillsShSkill } from '@/types/skill'
import PageHeader from '../components/PageHeader.vue'
import LoginPrompt from '../components/LoginPrompt.vue'
import SkillZipInstallModal from './components/SkillZipInstallModal.vue'
import SkillGithubImportModal from './components/SkillGithubImportModal.vue'

const emit = defineEmits<{
  (e: 'login-required'): void
}>()

const userStore = useUserStore()

const source = ref<'mine' | 'skillssh'>('mine')
const skillList = ref<Skill[]>([])
const zipModalVisible = ref(false)
const importModalVisible = ref(false)
const installingIds = reactive(new Set<number>())

const repoFilter = ref<string | undefined>(undefined)
const installedFilter = ref<number>(-1)
const searchText = ref('')

const installedOptions = [
  { label: '全部', value: -1 },
  { label: '已安装', value: 1 },
  { label: '未安装', value: 0 }
]

const repoOptions = computed(() => {
  const repos = new Set<string>()
  for (const skill of skillList.value) {
    if (skill.repoOwner && skill.repoName) {
      repos.add(`${skill.repoOwner}/${skill.repoName}`)
    }
  }
  return Array.from(repos).map(repo => ({ label: repo, value: repo }))
})

const filteredSkills = computed(() => {
  const keyword = searchText.value.trim().toLowerCase()
  return skillList.value.filter(skill => {
    if (repoFilter.value && `${skill.repoOwner}/${skill.repoName}` !== repoFilter.value) return false
    if (installedFilter.value !== -1 && skill.installed !== installedFilter.value) return false
    if (keyword) {
      const haystack = `${skill.name} ${skill.description || ''} ${skill.repoOwner || ''}/${skill.repoName || ''}`.toLowerCase()
      if (!haystack.includes(keyword)) return false
    }
    return true
  })
})

const mineEmptyText = computed(() => {
  if (skillList.value.length === 0) {
    return '还没有技能，可安装 zip 包或从 GitHub 导入'
  }
  return '没有符合筛选条件的技能'
})

async function fetchSkills(): Promise<void> {
  if (!userStore.isLoggedIn) return
  try {
    const res = await querySkills({ current: 1, pageSize: 100 })
    skillList.value = res.records || []
  } catch (e) {
    console.error(e)
  }
}

function openDoc(skill: Skill): void {
  const url = skill.docUrl || skill.sourceUrl
  if (url) window.open(url, '_blank')
}

function openExternal(url: string): void {
  window.open(url, '_blank')
}

async function handleInstall(skill: Skill): Promise<void> {
  installingIds.add(skill.id)
  try {
    const updated = await installSkill(skill.id)
    replaceSkill(updated)
    message.success(`已安装「${updated.name}」`)
  } catch (e) {
    console.error(e)
  } finally {
    installingIds.delete(skill.id)
  }
}

async function handleUninstall(skill: Skill): Promise<void> {
  installingIds.add(skill.id)
  try {
    const updated = await uninstallSkill(skill.id)
    replaceSkill(updated)
    message.success(`已卸载「${updated.name}」`)
  } catch (e) {
    console.error(e)
  } finally {
    installingIds.delete(skill.id)
  }
}

function replaceSkill(updated: Skill): void {
  const index = skillList.value.findIndex(s => s.id === updated.id)
  if (index >= 0) {
    skillList.value.splice(index, 1, updated)
  } else {
    skillList.value.unshift(updated)
  }
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

// ─── skills.sh ───────────────────────────────────────────
const skillsShQuery = ref('')
const skillsShLoading = ref(false)
const skillsShSkills = ref<SkillsShSkill[]>([])
const skillsShSearched = ref(false)
const installingShKeys = reactive(new Set<string>())

const skillsShEmptyText = computed(() => {
  if (!skillsShSearched.value) return '输入关键词搜索 skills.sh 上的技能'
  return '没有找到相关技能'
})

function shKey(item: SkillsShSkill): string {
  return `${item.owner}/${item.repo}/${item.name}`
}

async function handleSkillsShSearch(): Promise<void> {
  const query = skillsShQuery.value.trim()
  if (!query) return
  skillsShLoading.value = true
  try {
    const result = await searchSkillsSh(query)
    skillsShSkills.value = result.skills
    skillsShSearched.value = true
  } catch (e) {
    console.error(e)
  } finally {
    skillsShLoading.value = false
  }
}

function formatInstalls(count: number): string {
  if (count >= 10000) return `${(count / 10000).toFixed(1)}w`
  if (count >= 1000) return `${(count / 1000).toFixed(1)}k`
  return String(count)
}

async function handleInstallSkillsSh(item: SkillsShSkill): Promise<void> {
  const key = shKey(item)
  installingShKeys.add(key)
  try {
    await installSkillsSh(item.owner, item.repo, item.name)
    item.installed = true
    message.success(`已安装「${item.name}」`)
    fetchSkills()
  } catch (e) {
    console.error(e)
  } finally {
    installingShKeys.delete(key)
  }
}

onMounted(() => {
  fetchSkills()
})
</script>

<style lang="scss" scoped>
.skill-card {
  cursor: pointer;

  &:hover .card-footer {
    opacity: 0;
  }

  .card-stats {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
    min-height: 24px;
    margin-bottom: 40px;

    .vis-tag {
      margin-inline-end: 0;
    }

    .stat-item {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      font-size: 12px;
      color: #6e6b62;
    }
  }
}

.skill-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;

  .filter-repo {
    min-width: 180px;
  }

  .filter-installed {
    width: 110px;
  }

  .filter-search {
    width: 240px;
  }
}

.repo-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  max-width: 100%;
  font-size: 12px;
  color: $text-dark;

  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &:hover {
    color: $primary-color;
  }
}
</style>
