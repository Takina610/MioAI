import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, onBeforeRouteLeave } from 'vue-router'
import { message } from 'ant-design-vue'
import { getAgentDetail, publishAgent, updateAgent } from '@/api/agent'
import type { AgentDetail, AgentUpdateRequest } from '@/types'

/** 智能体编辑页：详情加载、发布、自动保存 */
export function useAgentEdit(agentId: number) {
  const router = useRouter()

  const loading = ref(true)
  const publishLoading = ref(false)
  const hasSaved = ref(false)
  const agentDetail = ref<AgentDetail | null>(null)

  const formData = reactive({
    name: '',
    description: '',
    avatar: '',
    systemPrompt: '',
    isPublic: 0
  })

  async function fetchAgentDetail(): Promise<void> {
    try {
      loading.value = true
      const res = await getAgentDetail(agentId)
      agentDetail.value = res
      formData.name = res.name || ''
      formData.description = res.description || ''
      formData.avatar = res.avatar || ''
      formData.systemPrompt = res.systemPrompt || ''
      formData.isPublic = res.isPublic ?? 0
    } catch (e) {
      console.error(e)
      message.error('获取智能体详情失败')
    } finally {
      loading.value = false
    }
  }

  // 不携带开场白/温度等对话设置字段：编辑页已移除该配置卡，缺省不提交，避免自动保存把已有值抹掉
  function buildUpdateRequest(): AgentUpdateRequest {
    return {
      id: agentId,
      name: formData.name,
      description: formData.description,
      avatar: formData.avatar,
      systemPrompt: formData.systemPrompt,
      isPublic: formData.isPublic
    }
  }

  async function handlePublish(): Promise<{ version?: string }> {
    try {
      publishLoading.value = true
      const version = await publishAgent(agentId, buildUpdateRequest())
      message.success(`已发布 ${version || ''}`)
      return { version }
    } catch (e) {
      console.error(e)
      message.error('发布失败')
      return {}
    } finally {
      publishLoading.value = false
    }
  }

  async function autoSave(): Promise<void> {
    if (hasSaved.value) return
    try {
      await updateAgent(buildUpdateRequest())
      hasSaved.value = true
    } catch (e) {
      console.error('自动保存失败', e)
    }
  }

  function handleBeforeUnload(e: BeforeUnloadEvent): void {
    if (!hasSaved.value) {
      e.preventDefault()
      e.returnValue = ''
    }
  }

  onMounted(() => {
    fetchAgentDetail()
    window.addEventListener('beforeunload', handleBeforeUnload)
  })

  onBeforeUnmount(() => {
    autoSave()
    window.removeEventListener('beforeunload', handleBeforeUnload)
  })

  onBeforeRouteLeave(async (to, from, next) => {
    if (!hasSaved.value) {
      await autoSave()
      message.success('应用已自动保存')
    }
    next()
  })

  return { loading, publishLoading, agentDetail, formData, fetchAgentDetail, handlePublish }
}
