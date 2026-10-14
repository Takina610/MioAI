import { ref, type Ref } from 'vue'
import { message } from 'ant-design-vue'
import { addAgentKnowledge, removeAgentKnowledgeByKbId } from '@/api/agentKnowledge'
import { addAgentMcp, removeAgentMcpByMcpId } from '@/api/agentMcp'
import { addAgentSkill, removeAgentSkillBySkillId } from '@/api/agentSkill'
import { getPublicKnowledgeBases, queryKnowledgeBases, queryDocuments, type Document } from '@/api/knowledgeBase'
import { getPublicMcpTools, queryMcpTools } from '@/api/mcpTool'
import { getPlazaSkills, installPlazaSkill, installSkill, querySkills } from '@/api/skill'
import { parseMcpTools } from '@/utils/mcpTool'
import type { AgentDetail, KnowledgeBase, McpTool, Skill } from '@/types'

/** 展开列表的作用域：主列表/抽屉各自缓存一份文档数据 */
export type DocScope = 'main' | 'drawer'

/** 智能体资源配置：知识库/MCP 的浏览、选择与挂载/移除 */
export function useAgentResources(agentId: number, agentDetail: Ref<AgentDetail | null>, reloadDetail: () => Promise<void>) {
  const knowledgeDrawerVisible = ref(false)
  const mcpDrawerVisible = ref(false)
  const skillDrawerVisible = ref(false)
  const addKnowledgeLoading = ref(false)
  const addMcpLoading = ref(false)
  const addSkillLoading = ref(false)

  const publicKnowledgeBases = ref<KnowledgeBase[]>([])
  const customKnowledgeBases = ref<KnowledgeBase[]>([])
  const publicMcpTools = ref<McpTool[]>([])
  const customMcpTools = ref<McpTool[]>([])
  const publicSkills = ref<Skill[]>([])
  const customSkills = ref<Skill[]>([])
  const selectedKnowledgeIds = ref<number[]>([])
  const selectedMcpIds = ref<number[]>([])
  const selectedSkillIds = ref<number[]>([])
  const knowledgeDocuments = ref<Map<number, Document[]>>(new Map())
  const drawerKnowledgeDocuments = ref<Map<number, Document[]>>(new Map())

  function isKnowledgeAlreadyAdded(kbId: number): boolean {
    return agentDetail.value?.knowledgeBases?.some(kb => kb.id === kbId) || false
  }

  function isMcpAlreadyAdded(mcpId: number): boolean {
    return agentDetail.value?.mcpTools?.some(mcp => mcp.id === mcpId) || false
  }

  function isSkillAlreadyAdded(skillId: number): boolean {
    return agentDetail.value?.skills?.some(skill => skill.id === skillId) || false
  }

  function toggleId(ids: Ref<number[]>, id: number): void {
    const index = ids.value.indexOf(id)
    if (index > -1) {
      ids.value.splice(index, 1)
    } else {
      ids.value.push(id)
    }
  }

  const toggleKnowledgeSelection = (id: number) => toggleId(selectedKnowledgeIds, id)
  const toggleMcpSelection = (id: number) => toggleId(selectedMcpIds, id)
  const toggleSkillSelection = (id: number) => toggleId(selectedSkillIds, id)

  async function fetchKnowledgeBases(): Promise<void> {
    try {
      const [publicRes, customRes] = await Promise.all([
        getPublicKnowledgeBases({ current: 1, size: 100 }),
        queryKnowledgeBases({ current: 1, pageSize: 100 })
      ])
      publicKnowledgeBases.value = publicRes.records || []
      customKnowledgeBases.value = customRes.records || []
    } catch (e) {
      console.error(e)
    }
  }

  async function fetchMcpTools(): Promise<void> {
    try {
      const [publicRes, customRes] = await Promise.all([
        getPublicMcpTools({ current: 1, size: 100 }),
        queryMcpTools({ current: 1, pageSize: 100 })
      ])
      publicMcpTools.value = publicRes.records || []
      customMcpTools.value = customRes.records || []
    } catch (e) {
      console.error(e)
    }
  }

  async function fetchSkills(): Promise<void> {
    try {
      const [plazaRes, mineRes] = await Promise.all([
        getPlazaSkills({ current: 1, size: 100 }),
        querySkills({ current: 1, pageSize: 100 })
      ])
      publicSkills.value = plazaRes.records || []
      customSkills.value = mineRes.records || []
    } catch (e) {
      console.error(e)
    }
  }

  /** 已拥有判定（与后端同规则）：GitHub 来源按仓库坐标，自导入按名称 */
  function findOwnedCopy(plaza: Skill): Skill | undefined {
    if (plaza.repoOwner) {
      const base = (p?: string) => (p || '').split('/').pop() || ''
      return customSkills.value.find(s =>
        s.repoOwner === plaza.repoOwner &&
        s.repoName === plaza.repoName &&
        base(s.skillPath) === base(plaza.skillPath))
    }
    const name = plaza.name?.toLowerCase()
    return customSkills.value.find(s => s.name?.toLowerCase() === name)
  }

  async function fetchKnowledgeDocuments(kbId: number, scope: DocScope): Promise<void> {
    const map = scope === 'drawer' ? drawerKnowledgeDocuments : knowledgeDocuments
    if (map.value.has(kbId)) return
    try {
      const res = await queryDocuments({ current: 1, pageSize: 100, kbId })
      map.value.set(kbId, res.records || [])
    } catch (e) {
      console.error(e)
    }
  }

  function getDocuments(kbId: number, scope: DocScope): { name: string }[] {
    const map = scope === 'drawer' ? drawerKnowledgeDocuments : knowledgeDocuments
    return (map.value.get(kbId) || []).map(doc => ({ name: doc.fileName }))
  }

  function getTools(mcp: McpTool): { name: string; description?: string }[] {
    return parseMcpTools(mcp.toolInfo).map(tool => ({ name: tool.name, description: tool.description }))
  }

  async function showKnowledgeDrawer(): Promise<void> {
    knowledgeDrawerVisible.value = true
    selectedKnowledgeIds.value = []
    await fetchKnowledgeBases()
  }

  async function showMcpDrawer(): Promise<void> {
    mcpDrawerVisible.value = true
    selectedMcpIds.value = []
    await fetchMcpTools()
  }

  async function showSkillDrawer(): Promise<void> {
    skillDrawerVisible.value = true
    selectedSkillIds.value = []
    await fetchSkills()
  }

  async function handleAddKnowledge(): Promise<void> {
    if (selectedKnowledgeIds.value.length === 0) {
      message.warning('请选择要添加的知识库')
      return
    }

    try {
      addKnowledgeLoading.value = true
      await Promise.all(
        selectedKnowledgeIds.value.map(kbId => addAgentKnowledge({ agentId, kbId, enabled: 1 }))
      )
      message.success('添加成功')
      knowledgeDrawerVisible.value = false
      reloadDetail()
    } catch (e) {
      console.error(e)
      message.error('添加失败')
    } finally {
      addKnowledgeLoading.value = false
    }
  }

  async function handleRemoveKnowledge(kbId: number): Promise<void> {
    try {
      await removeAgentKnowledgeByKbId(agentId, kbId)
      message.success('移除成功')
      reloadDetail()
    } catch (e) {
      console.error(e)
      message.error('移除失败')
    }
  }

  async function handleAddMcp(): Promise<void> {
    if (selectedMcpIds.value.length === 0) {
      message.warning('请选择要添加的MCP工具')
      return
    }

    try {
      addMcpLoading.value = true
      await Promise.all(
        selectedMcpIds.value.map(mcpId => addAgentMcp({ agentId, mcpId, enabled: 1 }))
      )
      message.success('添加成功')
      mcpDrawerVisible.value = false
      reloadDetail()
    } catch (e) {
      console.error(e)
      message.error('添加失败')
    } finally {
      addMcpLoading.value = false
    }
  }

  async function handleRemoveMcp(mcpId: number): Promise<void> {
    try {
      await removeAgentMcpByMcpId(agentId, mcpId)
      message.success('移除成功')
      reloadDetail()
    } catch (e) {
      console.error(e)
      message.error('移除失败')
    }
  }

  async function handleAddSkill(): Promise<void> {
    if (selectedSkillIds.value.length === 0) {
      message.warning('请选择要添加的技能')
      return
    }
    addSkillLoading.value = true
    try {
      await Promise.all(
        selectedSkillIds.value.map(async skillId => {
          const mine = customSkills.value.find(s => s.id === skillId)
          if (!mine) {
            // 广场技能：已拥有则绑自己的副本，否则先安装一份副本再绑
            const plaza = publicSkills.value.find(s => s.id === skillId)
            if (!plaza) {
              throw new Error('skill not found')
            }
            const owned = plaza.owned ? findOwnedCopy(plaza) : undefined
            skillId = owned ? owned.id : (await installPlazaSkill(skillId)).id
          }
          // 未安装的技能（GitHub 引入仅登记）先安装拿到内容，再绑定
          const target = customSkills.value.find(s => s.id === skillId)
          if (target && target.installed !== 1) {
            await installSkill(skillId)
          }
          return addAgentSkill({ agentId, skillId, enabled: 1 })
        })
      )
      message.success('添加成功')
      skillDrawerVisible.value = false
      reloadDetail()
    } catch (e) {
      console.error(e)
      message.error('添加失败')
    } finally {
      addSkillLoading.value = false
    }
  }

  async function handleRemoveSkill(skillId: number): Promise<void> {
    try {
      await removeAgentSkillBySkillId(agentId, skillId)
      message.success('移除成功')
      reloadDetail()
    } catch (e) {
      console.error(e)
      message.error('移除失败')
    }
  }

  return {
    knowledgeDrawerVisible,
    mcpDrawerVisible,
    skillDrawerVisible,
    addKnowledgeLoading,
    addMcpLoading,
    addSkillLoading,
    publicKnowledgeBases,
    customKnowledgeBases,
    publicMcpTools,
    customMcpTools,
    publicSkills,
    customSkills,
    selectedKnowledgeIds,
    selectedMcpIds,
    selectedSkillIds,
    isKnowledgeAlreadyAdded,
    isMcpAlreadyAdded,
    isSkillAlreadyAdded,
    toggleKnowledgeSelection,
    toggleMcpSelection,
    toggleSkillSelection,
    fetchKnowledgeDocuments,
    getDocuments,
    getTools,
    showKnowledgeDrawer,
    showMcpDrawer,
    showSkillDrawer,
    handleAddKnowledge,
    handleRemoveKnowledge,
    handleAddMcp,
    handleRemoveMcp,
    handleAddSkill,
    handleRemoveSkill
  }
}
