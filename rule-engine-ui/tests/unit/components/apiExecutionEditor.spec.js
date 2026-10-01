import { shallowMount } from '@test-utils'
import ApiExecutionEditor from '@/views/datasource/components/ApiExecutionEditor.vue'

describe('API response sample import', () => {
  test('重新导入公司响应样例保留已配置的 data 整体取值', () => {
    const value = { kind: 'PATH', value: 'response.body.data' }
    const sample = { code: '0000', data: Object.fromEntries(Array.from({ length: 250 }, (_, index) => [`QY_${index}`, index])) }
    const wrapper = shallowMount(ApiExecutionEditor, {
      props: { value: JSON.stringify({ responseBranches: [{ id: 'company', mode: 'VALUE', value, sample, outputFields: [], condition: { type: 'group', operator: 'AND', children: [] } }] }) },
      global: { stubs: { ApiSampleStructure: { props: ['sample', 'prefix'], template: '<div />' } } },
    })
    const branch = wrapper.vm.config.responseBranches[0]
    wrapper.vm.openSample(branch)
    wrapper.vm.importSample()
    expect(branch.mode).toBe('VALUE')
    expect(branch.value).toEqual(value)
    expect(branch.outputFields).toEqual([])
    expect(wrapper.vm.sampleVisible).toBe(false)
    wrapper.unmount()
  })
})
