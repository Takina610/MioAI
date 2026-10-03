import { ref, type Ref } from 'vue'
import { message } from 'ant-design-vue'
import { addAgentKnowledge, removeAgentKnowledgeByKbId } from '@/api/agentKnowledge'
import { addAgentMcp, removeAgentMcpByMcpId } from '@/api/agentMcp'
import { getPublicKnowledgeBases, queryKnowledgeBases, queryDocuments, type Document } from '@/api/knowledgeBase'
import { getPublicMcpTools, queryMcpTools } from '@/api/mcpTool'
import { parseMcpTools } from '@/utils/mcpTool'
import type { AgentDetail, KnowledgeBase, McpTool } from '@/types'

/** 展开列表的作用域：主列表/抽屉各自缓存一份文档数据 */
export type DocScope = 'main' | 'drawer'

/** 智能体资源配置：知识库/MCP 的浏览、选择与挂载/移除 */
export function useAgentResources(agentId: number, agentDetail: Ref<AgentDetail | null>, reloadDetail: () => Promise<void>) {
  const knowledgeDrawerVisible = ref(false)
  const mcpDrawerVisible = ref(false)
  const addKnowledgeLoading = ref(false)
  const addMcpLoading = ref(false)

  const publicKnowledgeBases = ref<KnowledgeBase[]>([])
  const customKnowledgeBases = ref<KnowledgeBase[]>([])
  const publicMcpTools = ref<McpTool[]>([])
  const customMcpTools = ref<McpTool[]>([])
  const selectedKnowledgeIds = ref<number[]>([])
  const selectedMcpIds = ref<number[]>([])
  const knowledgeDocuments = ref<Map<number, Document[]>>(new Map())
  const drawerKnowledgeDocuments = ref<Map<number, Document[]>>(new Map())

  function isKnowledgeAlreadyAdded(kbId: number): boolean {
    return agentDetail.value?.knowledgeBases?.some(kb => kb.id === kbId) || false
  }

  function isMcpAlreadyAdded(mcpId: number): boolean {
    return agentDetail.value?.mcpTools?.some(mcp => mcp.id === mcpId) || false
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

  return {
    knowledgeDrawerVisible,
    mcpDrawerVisible,
    addKnowledgeLoading,
    addMcpLoading,
    publicKnowledgeBases,
    customKnowledgeBases,
    publicMcpTools,
    customMcpTools,
    selectedKnowledgeIds,
    selectedMcpIds,
    isKnowledgeAlreadyAdded,
    isMcpAlreadyAdded,
    toggleKnowledgeSelection,
    toggleMcpSelection,
    fetchKnowledgeDocuments,
    getDocuments,
    getTools,
    showKnowledgeDrawer,
    showMcpDrawer,
    handleAddKnowledge,
    handleRemoveKnowledge,
    handleAddMcp,
    handleRemoveMcp
  }
}
