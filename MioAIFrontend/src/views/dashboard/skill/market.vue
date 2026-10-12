<template>
  <div class="dash-page">
    <PageHeader :title="`技能广场 ${total}`" />

    <div class="dash-page-content">
      <div v-if="!loading && skills.length === 0" class="dash-empty">
        <a-empty description="暂无公开技能" />
      </div>

      <a-row v-else :gutter="[16, 16]">
        <a-col :xs="24" :sm="12" :md="8" :lg="6" v-for="skill in skills" :key="skill.id">
          <div class="dash-card skill-card">
            <div class="card-header">
              <div class="card-icon-wrapper">
                <ThunderboltOutlined />
              </div>
              <h3 class="card-title" :title="skill.name">{{ skill.name }}</h3>
            </div>
            <p class="card-desc">{{ skill.description || '暂无描述' }}</p>
            <div class="card-stats">
              <a
                v-if="skill.repoOwner"
                class="repo-link"
                :title="`${skill.repoOwner}/${skill.repoName}`"
                @click="openExternal(docUrlOf(skill))"
              >
                <GithubOutlined />
                <span>{{ skill.repoOwner }}/{{ skill.repoName }}</span>
              </a>
              <span v-else class="stat-item">社区分享</span>
              <span class="stat-item author" :title="skill.userName || ''">{{ skill.userName || '匿名' }}</span>
            </div>
            <div class="card-actions" @click.stop>
              <a-button v-if="skill.repoOwner" class="action-btn" @click="openExternal(docUrlOf(skill))">
                查看
              </a-button>
              <a-button
                v-if="!skill.owned"
                class="action-btn primary-btn"
                :loading="installingIds.has(skill.id)"
                @click="handleInstall(skill)"
              >
                安装
              </a-button>
              <a-button v-else class="action-btn" disabled>已拥有</a-button>
            </div>
            <div class="card-footer">
              <span class="update-time">更新于 {{ formatDate(skill.updateTime || skill.createTime) }}</span>
            </div>
          </div>
        </a-col>
      </a-row>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { ThunderboltOutlined, GithubOutlined } from '@ant-design/icons-vue'
import { getPlazaSkills, installPlazaSkill } from '@/api/skill'
import { useUserStore } from '@/store/user'
import { formatDate } from '@/utils/format'
import type { Skill } from '@/types/skill'
import PageHeader from '../components/PageHeader.vue'

const emit = defineEmits<{
  (e: 'login-required'): void
}>()

const userStore = useUserStore()

const loading = ref(false)
const skills = ref<Skill[]>([])
const total = ref(0)
const installingIds = reactive(new Set<number>())

async function fetchPlaza(): Promise<void> {
  loading.value = true
  try {
    const res = await getPlazaSkills({ current: 1, size: 100 })
    skills.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function docUrlOf(skill: Skill): string {
  return skill.docUrl || skill.sourceUrl || ''
}

/** 只放行 http(s)：来源链接入库前的历史数据可能带任意协议 */
function openExternal(url: string): void {
  if (!/^https?:\/\//i.test(url)) return
  window.open(url, '_blank', 'noopener')
}

async function handleInstall(skill: Skill): Promise<void> {
  if (!userStore.isLoggedIn) {
    emit('login-required')
    return
  }
  if (installingIds.has(skill.id)) return
  installingIds.add(skill.id)
  try {
    await installPlazaSkill(skill.id)
    skill.owned = true
    message.success(`已安装「${skill.name}」`)
  } catch (e) {
    console.error(e)
  } finally {
    installingIds.delete(skill.id)
  }
}

onMounted(() => {
  fetchPlaza()
})
</script>

<style lang="scss" scoped>
.skill-card {
  .card-header {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 14px;

    .card-title {
      flex: 1;
      min-width: 0;
      margin: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .card-stats {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
    min-height: 24px;
    margin-bottom: 12px;

    .stat-item {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      font-size: 12px;
      color: #6e6b62;

      &.author {
        max-width: 100%;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }
  }

  /* 覆盖 dash-card 公共样式的 hover 浮现：操作按钮常显 */
  .card-actions {
    position: static;
    opacity: 1;
    visibility: visible;
    display: flex;
    gap: 8px;
    height: auto;

    .action-btn {
      flex: 1;
      height: 32px;
      font-size: 13px;
      gap: 4px;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
    }
  }

  .card-footer {
    margin-top: 10px;

    .update-time {
      font-size: 12px;
      color: #8c8a82;
    }
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
