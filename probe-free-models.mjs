import { execSync } from 'node:child_process'

const FREE_MODELS = [
  'step-5-preview-free',
  'mimo-v2.6-flash-free',
  'longcat-2.5-preview-free',
  'nemotron-3.5-lightning-free',
  'space-bunny-free',
  'muse-spark-1.2-contributor-free',
  'muse-spark-1.3-contributor-free',
  'ling-3.1-flash-free',
  'jev-1.13-free'
]

const body = (m) => JSON.stringify({
  model: m,
  stream: true,
  messages: [
    { role: 'system', content: '你是通用 Agent。可用工具如下，按需调用。' },
    { role: 'user', content: '列出当前目录文件' }
  ],
  max_tokens: 60,
  reasoning_effort: 'high',
  tools: [{
    type: 'function',
    function: {
      name: 'Bash',
      description: 'Run a shell command',
      parameters: { type: 'object', properties: { command: { type: 'string' } }, required: ['command'] }
    }
  }]
})

function probe(model, timeoutMs = 30000) {
  const start = Date.now()
  try {
    const out = execSync(
      `curl -s -N -m ${timeoutMs / 1000} http://127.0.0.1:8090/v1/chat/completions -H "Authorization: Bearer mio-local-o2a-key-2026" -H "Content-Type: application/json" -d '${body(model).replace(/'/g, "'\\''")}' | head -c 2000`,
      { encoding: 'utf8', timeout: timeoutMs + 5000, shell: 'bash' }
    )
    const ttfb = Date.now() - start
    if (out.includes('Rate limit')) return { ok: false, reason: '429 rate-limited', ttfb }
    if (out.includes('Upstream request failed') || out.includes('unavailable')) return { ok: false, reason: 'upstream down', ttfb }
    const chunk = out.match(/"delta":\s*\{[^}]*\}/)
    if (!out.trim()) return { ok: false, reason: 'empty/timeout', ttfb }
    const hasToolCall = out.includes('tool_calls') || out.includes('tool_call')
    const hasTextOrReason = /"(content|reasoning)":\s*"[^]+/.test(out)
    if (chunk || hasTextOrReason) return { ok: true, ttfb, first: chunk ? 'tool_call' : 'text', preview: out.replace(/data: /g, '').slice(0, 80) }
    return { ok: false, reason: 'no delta: ' + out.slice(0, 100), ttfb }
  } catch (e) {
    return { ok: false, reason: e.status === undefined ? 'curl timeout' : String(e.message).slice(0, 60), ttfb: Date.now() - start }
  }
}

for (const m of FREE_MODELS) {
  const r1 = probe(m)
  const r2 = r1.ok ? null : probe(m)
  const best = r1.ok ? r1 : (r2 && r2.ok ? r2 : null)
  const verdict = best
    ? `OK  ttfb=${best.ttfb}ms first=${best.first}`
    : `FAIL ${r1.reason}${r2 ? ' / ' + r2.reason : ''} (${r1.ttfb}ms)`
  console.log(`${m.padEnd(34)} ${verdict}`)
}
