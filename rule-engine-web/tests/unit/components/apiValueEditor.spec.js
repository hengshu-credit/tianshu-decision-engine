import { shallowMount } from '@test-utils'
import ApiValueEditor from '@/components/common/ApiValueEditor.vue'

describe('API operand binding', () => {
  test('外数模块引用在嵌套表达式内转换为协议路径，不保存临时选择器 ID', () => {
    const wrapper = shallowMount(ApiValueEditor)
    wrapper.vm.update({ kind: 'FUNCTION', functionCode: 'nvl', args: [{ kind: 'REFERENCE', refType: 'API_CONTEXT', refId: 8, value: 'response.httpStatus' }] })
    expect(wrapper.emitted('update:value')[0][0].args[0]).toEqual({ kind: 'PATH', value: 'response.httpStatus', protocolPath: true, resolved: true })
    wrapper.unmount()
  })
  test('无法对应业务字段的路径拒绝提交，原始响应子路径可以对应到响应模块', () => {
    const wrapper = shallowMount(ApiValueEditor)
    wrapper.vm.update({ kind: 'PATH', value: 'unknown.business.field' })
    expect(wrapper.emitted('update:value')).toBeUndefined()
    expect(wrapper.vm.error).toContain('无法对应')
    wrapper.vm.update({ kind: 'PATH', value: 'response.body.data.score' })
    expect(wrapper.emitted('update:value')[0][0].protocolPath).toBe(true)
    wrapper.unmount()
  })
  test('嵌套表达式和已标记的协议路径也不能引用当前阶段尚不可用的步骤', () => {
    const wrapper = shallowMount(ApiValueEditor, { props: { modules: [{ value: 'steps.first.body', label: '第一步结果', type: 'OBJECT' }] } })
    wrapper.vm.update({ kind: 'FUNCTION', functionCode: 'nvl', args: [{ kind: 'PATH', value: 'steps.future.body.score', protocolPath: true }] })
    expect(wrapper.emitted('update:value')).toBeUndefined()
    expect(wrapper.vm.error).toContain('当前')
    wrapper.vm.update({ kind: 'REFERENCE', refType: 'API_CONTEXT', refId: 'steps.first.body.score', value: 'steps.first.body.score' })
    expect(wrapper.emitted('update:value')[0][0]).toMatchObject({ kind: 'PATH', value: 'steps.first.body.score', protocolPath: true })
    wrapper.unmount()
  })
})
