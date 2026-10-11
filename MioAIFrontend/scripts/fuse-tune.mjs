// fuse.js 调参实验：真实数据（本机会话 94+ / 公开智能体）→ 拼写容错 + 分类匹配 → 观察并选参
import Fuse from 'fuse.js'

const BASE = 'http://127.0.0.1:8081'
const TOKEN = 'smoke-keyword-test-2026'

async function getJSON(url, token) {
  const r = await fetch(BASE + url, { headers: token ? { token } : {} })
  const j = await r.json()
  if (j.code !== 0) throw new Error(url + ' -> ' + JSON.stringify(j).slice(0, 120))
  return j.data
}

const sessPage = await getJSON('/memory/getChatIdsPage/1?current=1&size=200', TOKEN)
// /agent/market 接口当前 52000（缓存故障），智能体名取自本地 agent 表真实快照
const agentNames = ['MioBot', 'Code Buddy', '技能测试员', '测试小助手', '径向宝石', '云朵径向', '未命名智能体', '1bot', 'mem-smoke-bot']

const sessions = (sessPage.records || []).map(x => ({ kind: 'session', id: x.conversationId, title: x.title || '新对话', category: '会话' }))
const agents = agentNames.map((n, i) => ({ kind: 'agent', id: String(i), title: n, category: '智能体' }))
const items = [...sessions, ...agents]

console.log(`数据：会话 ${sessions.length} 条，智能体 ${agents} 个`.replace('${agents}', agents.length))
console.log('智能体样例:', agents.slice(0, 6).map(a => a.title).join(' / '))
console.log('')

function makeFuse(opts) {
  return new Fuse(items, {
    includeScore: true,
    includeMatches: true,
    ignoreLocation: true,
    minMatchCharLength: 1,
    threshold: 0.4,
    keys: [
      { name: 'title', weight: 0.7 },
      { name: 'category', weight: 0.3 }
    ],
    ...opts
  })
}

const queries = [
  ['精确-英文', 'Redis'],
  ['精确-中文', '线程'],
  ['拼写容错-英文', 'Dokcer'],   // Docker 转置
  ['拼写容错-英文2', 'Reds'],    // Redis 缺字母
  ['大小写', 'redis'],
  ['分类', '智能体'],
  ['分类2', '会话'],
  ['中文容错', '容其化'],        // 容器化 变体
  ['无结果预期', 'zzzzqqq']
]

for (const th of [0.3, 0.4, 0.5]) {
  console.log(`======== threshold=${th} ========`)
  const fuse = makeFuse({ threshold: th })
  for (const [tag, q] of queries) {
    const res = fuse.search(q)
    const top = res.slice(0, 4).map(r => {
      const keys = r.matches.map(m => m.key).join('+')
      return `"${r.item.title}"(${r.item.category}, score=${r.score?.toFixed(3)}, keys=${keys})`
    })
    console.log(`[${tag}] "${q}" -> ${res.length} 条 | ${top.join('  ') || '(空)'}`)
  }
  console.log('')
}

// 高亮索引验证：matches[].indices 是否可用于切分高亮
const fuse = makeFuse({ threshold: 0.4 })
const demo = fuse.search('Reds')[0]
console.log('高亮索引样例:', JSON.stringify(demo?.matches?.map(m => ({ key: m.key, value: m.value, indices: m.indices }))))
