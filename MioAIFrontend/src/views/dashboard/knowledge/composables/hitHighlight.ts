/**
 * 命中测试查询词高亮：从查询中提取词项，在结果文本上切分命中区间。
 * 中文无分词器，采用「整词 + CJK 相邻二元组」启发式，覆盖绝大多数真实命中场景。
 */

const CJK_RE = /[\u4e00-\u9fff\u3400-\u4dbf]/

/** 提取用于高亮的词项：空白分词，英文词取原词，CJK 串取整串+二元组 */
export function extractQueryTerms(query: string): string[] {
  const terms = new Set<string>()
  for (const raw of query.split(/\s+/)) {
    const token = raw.trim()
    if (token.length < 2) continue
    terms.add(token)
    for (const run of token.match(/[\u4e00-\u9fff\u3400-\u4dbf]+/g) || []) {
      if (run.length > 2) terms.add(run)
      for (let i = 0; i + 2 <= run.length; i++) {
        terms.add(run.slice(i, i + 2))
      }
    }
  }
  return [...terms]
}

export interface HitSegment {
  text: string
  hit: boolean
}

/** 按词项切分文本，命中段标 hit；区间先合并再输出，避免嵌套标记 */
export function splitHitSegments(text: string, terms: string[]): HitSegment[] {
  if (!text || !terms.length) return [{ text, hit: false }]

  const escaped = terms
    .map((t) => t.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'))
    .sort((a, b) => b.length - a.length)
  const re = new RegExp(`(?:${escaped.join('|')})`, 'gi')

  const ranges: Array<[number, number]> = []
  for (const m of text.matchAll(re)) {
    ranges.push([m.index, m.index + m[0].length - 1])
  }
  if (!ranges.length) return [{ text, hit: false }]

  const merged: Array<[number, number]> = []
  for (const [s, e] of ranges) {
    const last = merged[merged.length - 1]
    if (last && s <= last[1] + 1) {
      last[1] = Math.max(last[1], e)
    } else {
      merged.push([s, e])
    }
  }

  const out: HitSegment[] = []
  let pos = 0
  for (const [s, e] of merged) {
    if (s > pos) out.push({ text: text.slice(pos, s), hit: false })
    out.push({ text: text.slice(s, e + 1), hit: true })
    pos = e + 1
  }
  if (pos < text.length) out.push({ text: text.slice(pos), hit: false })
  return out
}
