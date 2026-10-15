import { reactive, ref } from 'vue'
import type { Ref } from 'vue'
import type { PageResponse } from '@/types'
import MioMessage from '@/utils/message'

/**
 * 管理后台分页表格通用状态机：筛选变更回第一页、翻页续查、异常统一提示
 */
export function useAdminTable<T, Q extends { current?: number; pageSize?: number }>(
  fetcher: (query: Q) => Promise<PageResponse<T>>
) {
  const loading = ref(false)
  const records = ref<T[]>([]) as Ref<T[]>
  const pagination = reactive({ current: 1, pageSize: 20, total: 0 })
  let lastQuery: Q | undefined

  async function fetch(query?: Partial<Q>) {
    if (query !== undefined) {
      lastQuery = { ...(lastQuery ?? ({} as Q)), ...query, current: pagination.current, pageSize: pagination.pageSize } as Q
    }
    if (!lastQuery) {
      lastQuery = { current: pagination.current, pageSize: pagination.pageSize } as Q
    }
    loading.value = true
    try {
      const res = await fetcher({ ...lastQuery, current: pagination.current, pageSize: pagination.pageSize })
      records.value = res.records ?? []
      pagination.total = res.total ?? 0
    } catch {
      MioMessage.error('获取列表失败')
    } finally {
      loading.value = false
    }
  }

  /** 筛选条件变化：重置到第一页后查询 */
  function search(query?: Partial<Q>) {
    pagination.current = 1
    return fetch(query)
  }

  function handlePageChange() {
    return fetch()
  }

  function refresh() {
    return fetch()
  }

  return { loading, records, pagination, search, refresh, handlePageChange }
}
