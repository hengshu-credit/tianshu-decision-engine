import { mount, flushPromises } from '@test-utils'
import FunctionImpactPanel from '@/components/FunctionImpactPanel.vue'
import { inspectFunctionImpact } from '@/api/function'

describe('函数签名影响说明', () => {
  const form = { id: '9007199254740993', funcCode: 'My_Calc', returnType: 'NUMBER', implType: 'JAVA' }
  const params = [{ name: 'amount', type: 'NUMBER', label: '金额', javaType: 'double' }]
  const report = { beforeSignature: 'My_Calc(amount: NUMBER [double])', afterSignature: 'My_Calc(amount: NUMBER [java.lang.Double])',
    changes: ['参数名称、顺序或类型'], consumers: [{ id: 'RULE:9007199254740994', type: 'RULE', refId: '9007199254740994', label: '审批' }], message: '固定版本继续使用原快照' }
  beforeEach(() => { inspectFunctionImpact.mockReset() })
  function mountPanel(props, mocks = {}) {
    return mount(FunctionImpactPanel, { props, mocks, stubs: {
      'el-button': { emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
      'el-alert': { props: ['title'], template: '<div role="alert">{{ title }}</div>' },
    } })
  }
  test('按原始 ID 查看影响；修改后隐藏旧结论，引用规则可定位', async () => {
    inspectFunctionImpact.mockResolvedValue({ data: report })
    const push = vi.fn()
    const wrapper = mountPanel({ form, params }, { $router: { push } })
    expect(wrapper.text()).toContain('My_Calc(金额)')
    await wrapper.findAll('button').find(button => button.text() === '检查签名与引用影响').trigger('click')
    await flushPromises()
    expect(inspectFunctionImpact).toHaveBeenCalledWith({ ...form, paramsJson: JSON.stringify(params) })
    expect(wrapper.text()).toContain('java.lang.Double')
    await wrapper.findAll('button').find(button => button.text() === '审批').trigger('click')
    expect(push).toHaveBeenCalledWith('/rule/9007199254740994')
    await wrapper.setProps({ params: [{ ...params[0], name: 'changed' }] })
    expect(wrapper.text()).toContain('配置已变更')
    expect(wrapper.text()).not.toContain('生效签名')
    wrapper.unmount()
  })
  test('切换编辑对象后丢弃晚到响应', async () => {
    let resolve
    inspectFunctionImpact.mockReturnValue(new Promise(done => { resolve = done }))
    const wrapper = mountPanel({ form, params })
    await wrapper.find('button').trigger('click')
    await wrapper.setProps({ form: { ...form, id: '8' } })
    resolve({ data: report }); await flushPromises()
    expect(wrapper.text()).not.toContain('生效签名')
    wrapper.unmount()
  })
})
