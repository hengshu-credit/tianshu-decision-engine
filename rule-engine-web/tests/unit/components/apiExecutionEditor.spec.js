import { shallowMount, flushPromises } from '@test-utils'
import ApiExecutionEditor from '@/views/datasource/components/ApiExecutionEditor.vue'
import { ElInputNumber } from 'element-plus/es/components/input-number/index.mjs'
import { ElInput } from 'element-plus/es/components/input/index.mjs'

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

  test.each([0, 3])('真实轮询次数控件保留已有值%s，零值表示不限制次数而不是自动改成1', async maxAttempts => {
    const empty = () => ({ type: 'group', operator: 'AND', children: [] })
    const wrapper = shallowMount(ApiExecutionEditor, {
      props: { asyncMode: true, activeTab: 'steps', value: JSON.stringify({ version: 2, steps: [{
        id: 'poll', name: '查询结果', type: 'HTTP', endpointUrl: '/poll', requestMethod: 'GET', requestFields: [], when: empty(),
        poll: { maxAttempts, intervalMs: 1000, failure: empty(), until: { type: 'group', operator: 'AND', children: [{ path: 'body.status', operator: '==', value: 'DONE' }] } },
      }] }) },
      stubs: { 'el-input-number': ElInputNumber, 'el-input': ElInput },
    })
    try {
      await flushPromises()
      expect(wrapper.vm.config.steps[0].poll.maxAttempts).toBe(maxAttempts)
      const control = wrapper.find('[label="最多尝试"] input[role="spinbutton"]')
      expect(control.element.value).toBe(String(maxAttempts))
      await control.setValue(maxAttempts === 0 ? '4' : '0')
      expect(wrapper.vm.config.steps[0].poll.maxAttempts).toBe(maxAttempts === 0 ? 4 : 0)
      expect(wrapper.text()).toContain('0表示不限制查询次数，仍受等待超时约束')
    } finally { wrapper.unmount() }
  })
})
