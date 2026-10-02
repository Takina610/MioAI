import { ref, reactive } from 'vue'
import { queryDocuments, type Document } from '@/api/knowledgeBase'

/** 知识库文档分页加载（详情/编辑页共用） */
export function useKbDocuments() {
  const docLoading = ref(false)
  const documentList = ref<Document[]>([])

  const pagination = reactive({
    current: 1,
    pageSize: 10,
    total: 0,
    showSizeChanger: true,
    showTotal: (total: number) => `共 ${total} 条`
  })

  async function fetchDocuments(params: { kbId: number; userId?: number }): Promise<void> {
    docLoading.value = true
    try {
      const res = await queryDocuments({
        current: pagination.current,
        pageSize: pagination.pageSize,
        kbId: params.kbId,
        userId: params.userId
      })
      documentList.value = res.records || []
      pagination.total = res.total || 0
    } catch (e) {
      console.error(e)
    } finally {
      docLoading.value = false
    }
  }

  function handleTableChange(pag: { current: number; pageSize: number }): void {
    pagination.current = pag.current
    pagination.pageSize = pag.pageSize
  }

  return { docLoading, documentList, pagination, fetchDocuments, handleTableChange }
}
