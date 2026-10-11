import { mount } from '@test-utils'
import { analyzeModelImpact } from '@/api/model'
import ModelImpactDialog from '@/components/model/ModelImpactDialog.vue'

describe('ModelImpactDialog', () => {
  test('大模型ID保持字符串进入影响分析且不产生属性类型告警', async () => {
    const id = '9007199254740993'
    const warn = vi.fn()
    analyzeModelImpact.mockResolvedValue({ data: { analysisToken: 'large-id-token', impactDigest: 'digest', reportJson: '{}' } })
    const wrapper = mount(ModelImpactDialog, { props: { modelValue: true, modelId: id, action: 'REPLACE' }, global: { config: { warnHandler: warn } } })
    await Promise.resolve(); await wrapper.vm.$nextTick()
    expect(analyzeModelImpact).toHaveBeenCalledWith(id, 'REPLACE')
    expect(warn.mock.calls.map(([message]) => message).filter(message => message.includes('modelId'))).toEqual([])
    wrapper.vm.confirmed = true; wrapper.vm.confirm()
    expect(wrapper.emitted('confirmed')[0][0].impactToken).toBe('large-id-token')
    wrapper.unmount()
  })

  test('完成分析并人工确认后才返回一次性令牌', async () => {
    analyzeModelImpact.mockResolvedValue({ data: {
      analysisToken: 'impact-token',
      impactDigest: 'impact-digest',
      reportJson: '{"references":["RULE:1"]}'
    } })
    const wrapper = mount(ModelImpactDialog, { props: { modelValue: true, modelId: 10, action: 'DELETE' } })
    await Promise.resolve()
    await wrapper.vm.$nextTick()

    expect(analyzeModelImpact).toHaveBeenCalledWith(10, 'DELETE')
    expect(wrapper.vm.confirmed).toBe(false)
    wrapper.vm.confirmed = true
    wrapper.vm.confirm()
    expect(wrapper.emitted('confirmed')[0][0].impactToken).toBe('impact-token')
  })
})
