import request from '@/utils/request'
import type { ConnectivityTestResult, FreeModel } from '@/types'

/** OpenCode 免费模型目录（-free 结尾，反代拉取并短缓存）+ 引擎默认模型名 */
export const getFreeCatalog = async (): Promise<{ models: FreeModel[]; default: string }> => {
  const res = await request.get<{ models: FreeModel[]; default: string }>('/model/free', { skipErrorMessage: true } as never)
  return {
    models: Array.isArray(res?.models)
      ? res.models.filter(m => m && typeof m.id === 'string').map(m => ({
          id: m.id,
          vision: !!m.vision,
          ...(Number(m.contextWindow) > 0 ? { contextWindow: Number(m.contextWindow) } : {}),
          ...(m.reasoning === true ? { reasoning: true } : {})
        }))
      : [],
    default: typeof res?.default === 'string' ? res.default : ''
  }
}

/** 供应商模型连通性测试：端点配置仅瞬态经过后端，不落库 */
export const testProviderModel = async (payload: {
  baseUrl: string
  apiKey: string
  apiFormat: string
  model: string
}): Promise<ConnectivityTestResult> => {
  return request.post<ConnectivityTestResult>('/model/provider/test', payload, { timeout: 30000 } as never)
}
