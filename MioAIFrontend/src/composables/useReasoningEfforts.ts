import { ref, watch, type Ref } from 'vue'
import { getReasoningEfforts } from '@/api/chat'
import type { FreeModel, ModelProvider, ModelSelection } from '@/types'

/** 思考档位的本地记忆键（首页与对话页共用一份） */
const EFFORT_KEY = 'reasoning-effort'

/**
 * 按当前模型解析可用思考档位（首页与对话页共用）。
 *
 * 声明数据优先（供应商条目/免费目录元数据，即选即切），无声明才走后端探测；
 * 本地记忆的档位不被新模型支持时回退到最接近"高"的档。
 * supported: null=未解析（选择器展示兜底档位），[]=该模型不支持任何档位（隐藏选择器）。
 *
 * 档位标签与兜底列表由 ChatInput 自己渲染，这里只管状态与解析。
 */
export function useReasoningEfforts(
  selection: Ref<ModelSelection>,
  sources: { freeModels: Ref<FreeModel[]>; providers: Ref<ModelProvider[]> }
) {
  const effort = ref<string>(localStorage.getItem(EFFORT_KEY) || 'high')
  // 首页选定的档位要能被随后的对话页读到：写回本地记忆
  watch(effort, (v) => localStorage.setItem(EFFORT_KEY, v))

  const supported = ref<string[] | null>(null)
  /** 请求序号：切换模型后丢弃过期探测结果 */
  let reqSeq = 0

  /** 自定义供应商端点配置（仅存浏览器本地；随请求瞬态经过后端，不落库） */
  function providerOf(next: ModelSelection) {
    if (next.source !== 'custom' || next.providerId == null) return null
    const found = sources.providers.value.find(p => p.id === next.providerId)
    return found ? { baseUrl: found.baseUrl, apiKey: found.apiKey, apiFormat: found.apiFormat } : null
  }

  async function resolve(next: ModelSelection): Promise<void> {
    const seq = ++reqSeq
    let declared: string[] = []
    if (next.source === 'custom' && next.providerId != null) {
      const entry = sources.providers.value
        .find(p => p.id === next.providerId)
        ?.models.find(m => m.id === next.model)
      if (entry?.reasoningLevels?.length) {
        declared = [...entry.reasoningLevels]
      }
    } else {
      // 免费档：目录元数据说明是否推理模型——不推理只有"关闭"档，推理给标准三档+关闭
      const meta = sources.freeModels.value.find(f => f.id === next.model)
      if (meta) {
        declared = meta.reasoning === false ? ['none'] : ['none', 'low', 'medium', 'high']
      }
    }
    if (!declared.length) {
      try {
        const res = await getReasoningEfforts(next, providerOf(next))
        if (seq !== reqSeq) return
        declared = res.efforts ?? []
      } catch (e) {
        // 探测失败：回到未解析态（展示兜底档位），不误判为"该模型无档位"
        console.error('拉取思考档位失败，使用兜底列表', e)
        if (seq !== reqSeq) return
        supported.value = null
        return
      }
    }
    if (seq !== reqSeq) return
    supported.value = declared
    if (declared.length && !declared.includes(effort.value)) {
      effort.value = declared.includes('high') ? 'high' : declared[declared.length - 1]!
    }
  }

  return { effort, supported, resolve }
}
