import { ref } from 'vue'
import { getFreeCatalog } from '@/api/modelSetting'
import type { FreeModel, ModelEntry, ModelProvider, ModelSelection } from '@/types'

/** 本地存储键：自定义供应商与当前选择仅存浏览器本地，不上传任何服务端 */
const LOCAL_PROVIDERS_KEY = 'mio-model-providers'
const LOCAL_SELECTION_KEY = 'chat-model-selection'
/** 会话级模型记忆：chatId → 该会话使用的模型（进会话恢复，输入框不再全局统一） */
const LOCAL_SESSION_MODELS_KEY = 'mio-chat-session-models'

/**
 * 模型目录共享状态：OpenCode 免费模型（反代目录，纯展示数据）+ 自定义供应商（localStorage）
 * + 当前选择（localStorage）。个人中心与聊天页共用同一份内存态，一处加载处处同步。
 */
const freeModels = ref<FreeModel[]>([])
const defaultModel = ref('')
const providers = ref<ModelProvider[]>([])
const selection = ref<ModelSelection>({ source: 'opencode', providerId: null, model: '' })
const loaded = ref(false)
let loadPromise: Promise<void> | null = null

export function readLocalProviders(): ModelProvider[] {
  try {
    const raw = localStorage.getItem(LOCAL_PROVIDERS_KEY)
    const parsed = raw ? JSON.parse(raw) : []
    return Array.isArray(parsed) ? parsed.filter(p => p && typeof p.id === 'number') : []
  } catch {
    return []
  }
}

export function writeLocalProviders(rows: ModelProvider[]): void {
  localStorage.setItem(LOCAL_PROVIDERS_KEY, JSON.stringify(rows))
}

export function readLocalSelection(): ModelSelection | null {
  try {
    const raw = localStorage.getItem(LOCAL_SELECTION_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as ModelSelection
    if (parsed?.source !== 'opencode' && parsed?.source !== 'custom') return null
    return parsed
  } catch {
    return null
  }
}

export function writeLocalSelection(next: ModelSelection): void {
  localStorage.setItem(LOCAL_SELECTION_KEY, JSON.stringify(next))
}

function readSessionModelMap(): Record<string, ModelSelection> {
  try {
    const raw = localStorage.getItem(LOCAL_SESSION_MODELS_KEY)
    const parsed = raw ? JSON.parse(raw) : {}
    return parsed && typeof parsed === 'object' ? parsed : {}
  } catch {
    return {}
  }
}

/** 会话记住的模型（无记录回 null：沿用当前选择） */
export function readSessionModel(chatId: string): ModelSelection | null {
  if (!chatId) return null
  const remembered = readSessionModelMap()[chatId]
  return remembered?.source === 'opencode' || remembered?.source === 'custom' ? remembered : null
}

/** 记住会话使用的模型（首条消息发送时与切换时写入） */
export function writeSessionModel(chatId: string, selection: ModelSelection): void {
  if (!chatId) return
  const map = readSessionModelMap()
  map[chatId] = selection
  try {
    localStorage.setItem(LOCAL_SESSION_MODELS_KEY, JSON.stringify(map))
  } catch {
    // 配额满等异常：会话记忆退化为内存态，不影响对话
  }
}

/** 会话删除后清理其模型记忆（防长期累积） */
export function removeSessionModel(chatId: string): void {
  if (!chatId) return
  const map = readSessionModelMap()
  if (chatId in map) {
    delete map[chatId]
    localStorage.setItem(LOCAL_SESSION_MODELS_KEY, JSON.stringify(map))
  }
}

/** 模型选择的完整显示名（自定义供应商带供应商名前缀，与切换器/切换提示同口径） */
export function modelDisplayName(selection: ModelSelection | null | undefined, providerList: ModelProvider[] = providers.value): string {
  if (!selection?.model) return '默认模型'
  if (selection.source === 'custom' && selection.providerId != null) {
    const provider = providerList.find(p => p.id === selection.providerId)
    return provider ? `${provider.name}/${selection.model}` : selection.model
  }
  return selection.model
}

/** 新增供应商：本地生成 id（时间戳），返回新列表 */
export function addLocalProvider(input: { name: string; baseUrl: string; apiFormat: string; apiKey: string; models: ModelEntry[] }): ModelProvider {
  const rows = readLocalProviders()
  const provider: ModelProvider = {
    id: Date.now(),
    userId: 0,
    name: input.name,
    baseUrl: input.baseUrl,
    apiFormat: input.apiFormat,
    apiKey: input.apiKey,
    models: [...input.models],
  }
  rows.unshift(provider)
  writeLocalProviders(rows)
  providers.value = rows
  return provider
}

export function updateLocalProvider(provider: ModelProvider): void {
  const rows = readLocalProviders()
  const idx = rows.findIndex(r => r.id === provider.id)
  if (idx >= 0) {
    rows.splice(idx, 1, { ...provider })
    writeLocalProviders(rows)
    providers.value = rows
  }
}

export function deleteLocalProvider(id: number): void {
  const rows = readLocalProviders().filter(r => r.id !== id)
  writeLocalProviders(rows)
  providers.value = rows
  // 当前选择落在被删供应商上：清回默认免费档
  const sel = selection.value
  if (sel.source === 'custom' && sel.providerId === id) {
    const fallback: ModelSelection = { source: 'opencode', providerId: null, model: defaultModel.value }
    selection.value = fallback
    writeLocalSelection(fallback)
  }
}

/** 供应商下启用的模型条目（聊天页可选集合） */
export function enabledProviderModels(provider: ModelProvider) {
  return provider.models.filter(m => m.enabled !== false)
}

/** 拉取免费目录 + 装载本地供应商/选择（幂等） */
export function loadModelCatalog(force = false): Promise<void> {
  if (loadPromise && !force) return loadPromise
  loadPromise = (async () => {
    let models: FreeModel[] = []
    let def = ''
    try {
      const catalog = await getFreeCatalog()
      models = catalog.models
      def = catalog.default
    } catch {
      // 目录拉取失败：空态展示
    }
    freeModels.value = models
    defaultModel.value = def
    providers.value = readLocalProviders()
    const local = readLocalSelection()
    if (local && (local.model || local.source === 'custom')) {
      selection.value = local
    } else {
      selection.value = { source: 'opencode', providerId: null, model: def }
    }
    loaded.value = true
  })()
  return loadPromise
}

/** 目录变化后重拉（免费档目录带短缓存） */
export async function refreshModelCatalog(): Promise<void> {
  await loadModelCatalog(true)
}

export function useModelCatalog() {
  return { freeModels, defaultModel, providers, selection, loaded }
}
