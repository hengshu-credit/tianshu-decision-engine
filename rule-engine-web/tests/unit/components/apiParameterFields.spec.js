import { shallowMount } from '@test-utils'
import ApiParameterFields from '@/components/common/ApiParameterFields.vue'
import { requestFieldPosition, requestFieldFormat, requestFieldContent } from '@/utils/apiRequestFields'

describe('请求字段的位置、格式和文件内容', () => {
  test('切换请求体格式保持 ID、原始字段名和取值，且不改变请求参数', () => {
    const value = { kind: 'REFERENCE', refId: '9007199254740993', refType: 'VARIABLE' }
    const fields = [{ id: 'a', location: 'JSON', path: 'Customer.Mobile', value }, { id: 'b', location: 'JSON', path: '资料[0].编号' }, { id: 'q', location: 'QUERY', path: 'page' }]
    const wrapper = shallowMount(ApiParameterFields, { props: { fields } })
    wrapper.vm.setBodyFormat('FORM_DATA')
    expect(fields.map(field => field.location)).toEqual(['FORM_DATA', 'FORM_DATA', 'QUERY'])
    expect(fields[0]).toMatchObject({ id: 'a', path: 'Customer.Mobile', value })
    expect(fields[1].path).toBe('资料[0].编号')
    wrapper.vm.add()
    const added = wrapper.emitted('update:fields')[0][0].at(-1)
    expect(added.location).toBe('FORM_DATA')
    expect(fields.some(field => field.id === added.id)).toBe(false)
    wrapper.unmount()
  })
  test('历史文件字段不能移动到请求头，新增配置不再提供文件类型控件', () => {
    const file = { mode: 'ZIP_BASE64', kind: 'PDF', name: '申请.pdf', maxBytes: 2048 }
    const field = { id: 'document', path: 'document', location: 'JSON', file, overridable: true }
    const wrapper = shallowMount(ApiParameterFields, { props: { fields: [field] } })
    wrapper.vm.setPosition(field, 'HEADER')
    expect(field.location).toBe('JSON')
    expect(field.file).toEqual(file)
    expect(field.file).toEqual(file)
    expect(field.location).toBe('JSON')
    wrapper.unmount()
  })
  test('相同字段名能通过位置和格式区分，旧映射入参不假装成请求体', () => {
    expect(requestFieldPosition({ location: 'HEADER' }).label).toBe('Headers')
    expect(requestFieldPosition({ location: 'QUERY' }).label).toBe('Params')
    expect(requestFieldPosition({ location: 'COOKIE' }).label).toBe('Cookies')
    for (const location of ['JSON', 'FORM', 'FORM_DATA']) expect(requestFieldPosition({ location }).label).toBe('Body')
    expect(requestFieldFormat({ location: 'FORM' }).label).toBe('x-www-form-urlencoded')
    expect(requestFieldFormat({ location: 'FORM_DATA' }).label).toBe('form-data')
    expect(requestFieldFormat({ location: 'FORM_DATA' }).hint).toContain('multipart/form-data')
    expect(requestFieldPosition({ location: 'INPUT' }).label).toBe('调用入参')
    expect(requestFieldFormat({ location: 'INPUT' }).label).toBe('由 API 映射')
    expect(requestFieldContent({ file: { mode: 'BASE64', kind: 'IMAGE' } }).hint).toContain('图片')
    expect(requestFieldContent({ file: { mode: 'ZIP_BASE64' } }).hint).toContain('ZIP')
  })
  test('默认值是单输入框；清空即删除默认处理，填写保留原文', () => {
    const field = { id: 'default', path: 'queryPhone', location: 'JSON', value: null }
    const wrapper = shallowMount(ApiParameterFields, { props: { fields: [field] } })
    expect(wrapper.vm.defaultValueText(field)).toBe('')
    wrapper.vm.setDefaultValue(field, '0000')
    expect(field.defaultValue).toBe('0000')
    expect(wrapper.vm.defaultValueText(field)).toBe('0000')
    wrapper.vm.setDefaultValue(field, '')
    expect(field.defaultValue).toBeUndefined()
    wrapper.unmount()
  })
})
