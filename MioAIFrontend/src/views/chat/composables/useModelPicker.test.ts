import { describe, it, expect } from 'vitest'
import { ref } from 'vue'
import { useModelPicker } from './useModelPicker'
import type { FreeModel, ModelProvider, ModelSelection } from '@/types'

function setup(over: { current?: ModelSelection | null; effort?: string; supported?: string[] | null } = {}) {
  const freeModels = ref<FreeModel[]>([
    { id: 'gpt-free', vision: true },
    { id: 'qwen-free', vision: false }
  ])
  const providers = ref<ModelProvider[]>([
    {
      id: 1, userId: 1, name: '我的供应商', baseUrl: '', apiFormat: 'openai', apiKey: '',
      models: [
        { id: 'm1', enabled: true },
        { id: 'm2', enabled: false }
      ]
    } as unknown as ModelProvider
  ])
  const selected: ModelSelection[] = []
  let managed = 0
  const picker = useModelPicker({
    currentModel: () => over.current ?? null,
    freeModels: () => freeModels.value,
    providers: () => providers.value,
    supportedEfforts: () => over.supported,
    effort: () => over.effort,
    onSelect: s => selected.push(s),
    onManage: () => managed++
  })
  return { picker, selected, getManaged: () => managed }
}

describe('useModelPicker', () => {
  it('免费模型单独成组，自定义供应商按项目分组并过滤停用模型', () => {
    const { picker } = setup()
    const groups = picker.cascadeGroups.value
    expect(groups[0]!.key).toBe('opencode')
    expect(groups[0]!.models.map(m => m.id)).toEqual(['gpt-free', 'qwen-free'])
    const p = groups.find(g => g.key === 'p_1')!
    expect(p.label).toBe('我的供应商')
    // enabled:false 的 m2 被过滤
    expect(p.models.map(m => m.id)).toEqual(['m1'])
  })

  it('当前选择指针与高亮', () => {
    const { picker } = setup({ current: { source: 'custom', providerId: 1, model: 'm1' } })
    expect(picker.currentModelKey.value).toBe('custom|1|m1')
    expect(picker.currentModelLabel.value).toBe('我的供应商/m1')
    expect(picker.cascadeGroups.value.find(g => g.key === 'p_1')!.current).toBe(true)
  })

  it('未选择模型时标签回退为“模型”', () => {
    const { picker } = setup({ current: null })
    expect(picker.currentModelLabel.value).toBe('模型')
    expect(picker.currentModelKey.value).toBe('')
  })

  it('超长模型名截断为 31 字符 + 省略号', () => {
    const long = 'x'.repeat(40)
    const { picker } = setup({ current: { source: 'opencode', providerId: null, model: long } })
    expect(picker.currentModelLabel.value.length).toBe(32)
    expect(picker.currentModelLabel.value.endsWith('…')).toBe(true)
  })

  it('档位：未解析走兜底并倒序展示', () => {
    const { picker } = setup({ supported: null })
    expect(picker.effortOptions.value.map(o => o.value)).toEqual(['none', 'low', 'medium', 'high'])
  })

  it('档位：模型不支持时为空数组', () => {
    const { picker } = setup({ supported: [] })
    expect(picker.effortOptions.value).toEqual([])
  })

  it('档位：默认 high 且标签正确', () => {
    const { picker } = setup({ supported: ['high', 'low'] })
    expect(picker.effort.value).toBe('high')
    expect(picker.effortLabel.value).toBe('最高')
  })

  it('菜单点击：选中模型会上抛并关闭菜单', () => {
    const { picker, selected } = setup()
    picker.modelMenuOpen.value = true
    picker.onModelMenuClick({ key: 'opencode|gpt-free' })
    expect(selected).toEqual([{ source: 'opencode', providerId: null, model: 'gpt-free' }])
    expect(picker.modelMenuOpen.value).toBe(false)
  })

  it('菜单点击：管理模型走上抛', () => {
    const { picker, selected, getManaged } = setup()
    picker.modelMenuOpen.value = true
    picker.onModelMenuClick({ key: '__manage__' })
    expect(getManaged()).toBe(1)
    expect(selected).toEqual([])
    expect(picker.modelMenuOpen.value).toBe(false)
  })
})
