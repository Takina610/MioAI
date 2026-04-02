export interface BaseResponse<T = unknown> {
  code: number
  data: T
  message: string
}

export interface PageResponse<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export interface PaginationParams {
  current?: number
  pageSize?: number
}
