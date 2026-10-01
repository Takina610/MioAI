export interface MetricValue {
  value: number
  unit: string
  trend: number
}

export interface TimePoint {
  time: string
  fullTime?: string
  value: number
}

export interface NameValue {
  id: string
  name: string
  avatar?: string
  value: number
}

export interface UsageStats {
  metrics: Record<string, MetricValue>
  trend: TimePoint[]
  trendMap: Record<string, TimePoint[]>
  prevTrendMap: Record<string, TimePoint[]>
  distribution: NameValue[]
  rank: NameValue[]
}
