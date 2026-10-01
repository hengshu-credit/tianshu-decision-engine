import { mount } from '@test-utils'
import { analyzeModelImpact } from '@/api/model'
import ModelImpactDialog from '@/components/model/ModelImpactDialog.vue'

describe('ModelImpactDialog', () => {
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
