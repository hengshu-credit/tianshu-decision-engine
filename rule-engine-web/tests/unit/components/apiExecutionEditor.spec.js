import { shallowMount, flushPromises } from '@test-utils'
import ApiExecutionEditor from '@/views/datasource/components/ApiExecutionEditor.vue'
import ApiParameterFields from '@/components/common/ApiParameterFields.vue'
import { ElInputNumber } from 'element-plus/es/components/input-number/index.mjs'
import { ElInput } from 'element-plus/es/components/input/index.mjs'

describe('API response sample import', () => {
  test('各步骤读取自身的请求路径目录，并保留原始特殊键名', () => {
    const wrapper = shallowMount(ApiExecutionEditor, { props: { asyncMode: true, activeTab: 'steps', value: JSON.stringify({
      version: 2, requestFields: [], requestSample: { RootOnly: '' }, steps: [
        { id: 'first', type: 'HTTP', requestFields: [], requestSample: { 'Mobile.Number': '', Customer: { Age: 18 } } },
        { id: 'second', type: 'HTTP', requestFields: [], requestSample: { OrderId: '' } }
      ]
    }) } })
    try {
      const tables = wrapper.findAllComponents(ApiParameterFields)
      expect(tables[0].props('paths').map(item => item.value)).toContain('["Mobile.Number"]')
      expect(tables[0].props('paths').map(item => item.value)).toContain('Customer.Age')
      expect(tables[0].props('paths').map(item => item.value)).not.toContain('OrderId')
      expect(tables[1].props('paths').map(item => item.value)).toContain('OrderId')
      expect(tables[1].props('paths').map(item => item.value)).not.toContain('RootOnly')
    } finally { wrapper.unmount() }
  })
  test('未设置可选执行条件的当前步骤也能完整回显', () => {
    const wrapper = shallowMount(ApiExecutionEditor, { props: { value: '{"version":2,"requestFields":[],"steps":[{"id":"submit","type":"HTTP","requestFields":[]}]}' } })
    expect(wrapper.vm.config.steps[0].when).toEqual({ type: 'group', operator: 'AND', children: [] })
    wrapper.unmount()
  })
  test('离开旧接口时取消延迟更新，避免旧组件覆盖下一接口的配置', () => {
    const wrapper = shallowMount(ApiExecutionEditor)
    wrapper.vm.config.requestSample = { oldApi: true }
    wrapper.vm.queueConfigEmit(wrapper.vm.config)
    wrapper.unmount()
    expect(wrapper.emitted('update:value')).toBeUndefined()
  })
  test('步骤鉴权只保存当前方式的字段，避免隐藏的旧输入形成额外依赖', () => {
    const wrapper = shallowMount(ApiExecutionEditor)
    const step = { authMode: 'BEARER' }
    const token = { kind: 'REFERENCE', refType: 'VARIABLE', refId: '9007199254740993' }
    wrapper.vm.setStepAuth(step, { token, username: { kind: 'REFERENCE', refType: 'VARIABLE', refId: 1 }, nullPolicy: 'FAIL' })
    expect(JSON.parse(step.authApiConfig)).toEqual({ token, nullPolicy: 'FAIL' })
    wrapper.unmount()
  })
  test('保存或切换页签前同步最新配置，不依赖定时器', () => {
    const wrapper = shallowMount(ApiExecutionEditor, { props: { value: '{"version":2,"requestFields":[]}' } })
    wrapper.vm.config.requestFields.push({ id: 'latest', path: 'Mobile', location: 'JSON', value: { kind: 'LITERAL', value: '1' } })
    wrapper.vm.flushConfig()
    expect(JSON.parse(wrapper.emitted('update:value').at(-1)[0]).requestFields[0].id).toBe('latest')
    wrapper.unmount()
  })
  test('添加首个请求条件分支会把原有请求字段放入分支，拖拽可调整分支顺序', () => {
    const wrapper = shallowMount(ApiExecutionEditor, {
      props: { value: JSON.stringify({ requestFields: [{ id: 'base', path: 'mobile', location: 'JSON', value: { kind: 'LITERAL', value: '1' } }] }) },
    })
    wrapper.vm.addBranch('requestBranches')
    expect(wrapper.vm.config.requestFields).toEqual([])
    expect(wrapper.vm.config.requestBranches[0].requestFields[0].id).toBe('base')
    wrapper.vm.addBranch('requestBranches')
    const first = wrapper.vm.config.requestBranches[0].id
    wrapper.vm.startBranchDrag('requestBranches', 0, {})
    wrapper.vm.dropBranch('requestBranches', 1, { preventDefault() {} })
    expect(wrapper.vm.config.requestBranches[1].id).toBe(first)
    wrapper.unmount()
  })

  test('响应整体取值保留输出字段路径配置', () => {
    const wrapper = shallowMount(ApiExecutionEditor, { props: { value: JSON.stringify({ responseBranches: [{ id: 'result', mode: 'VALUE', outputPath: 'data.score', value: { kind: 'PATH', value: 'response.body.score' } }] }) } })
    expect(wrapper.vm.config.responseBranches[0].outputPath).toBe('data.score')
    wrapper.unmount()
  })

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
