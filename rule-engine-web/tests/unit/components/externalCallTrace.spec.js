import { mount } from '@vue/test-utils'
import ExternalCallTrace from '@/components/common/ExternalCallTrace.vue'

describe('ExternalCallTrace', () => {
  test('连续编号并保留 null、false、0 以及赋值引用元数据', () => {
    const wrapper = mount(ExternalCallTrace, {
      props: {
        steps: [
          { sequence: 1, label: '第一次赋值', status: 'SUCCESS', attempt: 1, refType: 'VARIABLE', refId: 0, targetPath: 'risk', resultPath: 'body.risk', input: null, value: false },
          { sequence: 1, label: '第二次赋值', status: 'SUCCESS', attemptNo: 2, refType: 'DATA_OBJECT', refId: 12, targetPath: 'request.score', resultPath: 'body.score', output: 0 },
        ],
      },
    })

    expect(wrapper.findAll('.external-call-trace-index').map(item => item.text())).toEqual(['1', '2'])
    expect(wrapper.text()).toContain('引用类型VARIABLE')
    expect(wrapper.text()).toContain('引用 ID0')
    expect(wrapper.text()).toContain('null')
    expect(wrapper.text()).toContain('false')
    expect(wrapper.text()).toContain('0')
  })

  test('无历史阶段链时显示可解释提示，可收起和展开', async () => {
    const wrapper = mount(ExternalCallTrace, { props: { steps: [] } })
    expect(wrapper.find('.external-call-trace-empty').text()).toContain('历史记录未保存')
    await wrapper.setProps({ steps: [{ label: '请求入参', status: 'SUCCESS', input: { id: 1 } }] })
    await wrapper.find('.external-call-trace-toggle').trigger('click')
    expect(wrapper.find('.external-call-trace-steps').exists()).toBe(false)
    await wrapper.find('.external-call-trace-toggle').trigger('click')
    expect(wrapper.find('.external-call-trace-steps').exists()).toBe(true)
  })

  test('工作流大报文截断后仍递归显示全部六个子阶段并保留摘要', () => {
    const summary = { truncated: true, bytes: 255912, reason: '阶段追踪字段超过32KiB，仅保留摘要' }
    const children = [
      { type: 'REQUEST_INPUT', label: '规则请求入参', status: 'SUCCESS' },
      { type: 'API_REQUEST', label: 'API请求报文拼装', status: 'SUCCESS' },
      { type: 'AUTHENTICATION', label: '外数鉴权（已脱敏）', status: 'SUCCESS' },
      { type: 'EXTERNAL_REQUEST', label: '发起外部请求', status: 'SENT' },
      { type: 'EXTERNAL_RESPONSE', label: '外部数据响应', status: 'SUCCESS' },
      { type: 'RESPONSE_MAPPING', label: '响应字段映射', status: 'SUCCESS' },
    ]
    const wrapper = mount(ExternalCallTrace, {
      props: { steps: [{ type: 'WORKFLOW_STEP', label: '获取征信结果 R9005', status: 'SUCCESS', output: summary, children }] },
    })

    const nested = wrapper.find('.external-call-trace.is-compact')
    expect(nested.exists()).toBe(true)
    expect(nested.find('.external-call-trace-title').text()).toBe('步骤内调用过程')
    expect(nested.findAll('.external-call-trace-step-title span').map(item => item.text())).toEqual(children.map(step => step.label))
    expect(nested.findAll('.external-call-trace-status').map(item => item.text())).toEqual(['成功', '成功', '成功', '已发送', '成功', '成功'])
    expect(nested.findAll('.external-call-trace-index').map(item => item.text())).toEqual(['1', '2', '3', '4', '5', '6'])
    expect(JSON.parse(wrapper.find('.external-call-trace-values pre').text())).toEqual(summary)
    wrapper.unmount()
  })
})
