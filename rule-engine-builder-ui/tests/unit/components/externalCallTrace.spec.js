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
})
