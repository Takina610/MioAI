import { computed, ref } from 'vue'
import type { FreeModel, ModelProvider, ModelSelection } from '@/types'

/**
 * 输入框模型切换器 + 思考档位选择：供应商瀑布菜单、当前模型标签、档位选项。
 * 从 ChatInput 抽出——纯派生状态，切换动作上抛父级联动思考档位。
 */

/** 思考档位全量标签（实际渲染哪些档由后端按模型能力返回） */
export const EFFORT_LABELS: Record<string, string> = {
  max: '极限',
  xhigh: '超高',
  high: '最高',
  medium: '中等',
  low: '较低',
  minimal: '极低',
  none: '关闭'
}

/** 探测失败时的兜底档位 */
export const FALLBACK_EFFORTS = ['high', 'medium', 'low', 'none']

export interface CascadeModel {
  key: string
  id: string
  selection: ModelSelection
}

interface CascadeGroup {
  key: string
  label: string
  models: CascadeModel[]
  current: boolean
}

export interface UseModelPickerOptions {
  currentModel: () => ModelSelection | null | undefined
  freeModels: () => FreeModel[]
  providers: () => ModelProvider[]
  /** null/缺省=未解析（兜底档位），[]=该模型不支持任何档位（隐藏选择器） */
  supportedEfforts: () => string[] | null | undefined
  effort: () => string | undefined
  onSelect: (selection: ModelSelection) => void
  onManage: () => void
}

export function useModelPicker(options: UseModelPickerOptions) {
  const modelMenuOpen = ref(false)

  const cascadeGroups = computed<CascadeGroup[]>(() => {
    const cur = options.currentModel()
    const groups: CascadeGroup[] = [
      {
        key: 'opencode',
        label: 'OpenCode 免费模型',
        models: options.freeModels().map(m => ({
          key: `opencode|${m.id}`,
          id: m.id,
          selection: { source: 'opencode' as const, providerId: null, model: m.id }
        })),
        current: cur?.source === 'opencode'
      }
    ]
    for (const p of options.providers()) {
      groups.push({
        key: `p_${p.id}`,
        label: p.name,
        models: p.models
          .filter(m => m.enabled !== false)
          .map(m => ({
            key: `custom|${p.id}|${m.id}`,
            id: m.id,
            selection: { source: 'custom' as const, providerId: p.id, model: m.id }
          })),
        current: cur?.source === 'custom' && cur.providerId === p.id
      })
    }
    return groups
  })

  const currentModelKey = computed(() => {
    const cur = options.currentModel()
    if (!cur) return ''
    return cur.source === 'custom' && cur.providerId != null
      ? `custom|${cur.providerId}|${cur.model}`
      : `opencode|${cur.model}`
  })

  const currentModelLabel = computed(() => {
    const cur = options.currentModel()
    if (!cur?.model) return '模型'
    const provider = cur.source === 'custom'
      ? options.providers().find(p => p.id === cur.providerId)
      : undefined
    const full = provider ? `${provider.name}/${cur.model}` : cur.model
    return full.length > 32 ? full.slice(0, 31) + '…' : full
  })

  function onModelMenuClick({ key }: { key: string | number }): void {
    if (key === '__manage__') {
      modelMenuOpen.value = false
      options.onManage()
      return
    }
    const target = cascadeGroups.value
      .flatMap(g => g.models)
      .find(m => m.key === String(key))
    if (target) {
      modelMenuOpen.value = false
      options.onSelect(target.selection)
    }
  }

  // 档位列表如实渲染：未解析（null）才用兜底；解析为空数组 = 该模型不支持思考，选择器隐藏
  const effortOptions = computed(() => {
    const supported = options.supportedEfforts()
    return (supported == null ? FALLBACK_EFFORTS : supported)
      .map(v => ({ value: v, label: EFFORT_LABELS[v] ?? v }))
      .reverse()
  })

  const effort = computed(() => options.effort() || 'high')

  const effortLabel = computed(
    () => effortOptions.value.find(o => o.value === effort.value)?.label ?? EFFORT_LABELS[effort.value] ?? effort.value
  )

  return {
    modelMenuOpen,
    cascadeGroups,
    currentModelKey,
    currentModelLabel,
    onModelMenuClick,
    effortOptions,
    effort,
    effortLabel
  }
}
