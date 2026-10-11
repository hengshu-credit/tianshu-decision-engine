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
  test('文件不能配置到请求头；改回普通字段后切换请求头会关闭覆盖', () => {
    const file = { mode: 'ZIP_BASE64', kind: 'PDF', name: '申请.pdf', maxBytes: 2048 }
    const field = { id: 'document', path: 'document', location: 'JSON', file, overridable: true }
    const wrapper = shallowMount(ApiParameterFields, { props: { fields: [field] } })
    wrapper.vm.setPosition(field, 'HEADER')
    expect(field.location).toBe('JSON')
    expect(field.file).toEqual(file)
    wrapper.vm.setFileMode(field, 'NONE')
    wrapper.vm.setPosition(field, 'HEADER')
    expect(field.file).toBeUndefined()
    expect(field.location).toBe('HEADER')
    expect(field.overridable).toBe(false)
    wrapper.unmount()
  })
  test('相同字段名能通过位置和格式区分，旧映射入参不假装成请求体', () => {
    expect(requestFieldPosition({ location: 'HEADER' }).label).toBe('Headers')
    expect(requestFieldPosition({ location: 'QUERY' }).label).toBe('Params')
    for (const location of ['JSON', 'FORM', 'FORM_DATA']) expect(requestFieldPosition({ location }).label).toBe('Body')
    expect(requestFieldFormat({ location: 'FORM' }).label).toBe('x-www-form-urlencoded')
    expect(requestFieldFormat({ location: 'FORM_DATA' }).label).toBe('form-data')
    expect(requestFieldFormat({ location: 'FORM_DATA' }).hint).toContain('multipart/form-data')
    expect(requestFieldPosition({ location: 'INPUT' }).label).toBe('调用入参')
    expect(requestFieldFormat({ location: 'INPUT' }).label).toBe('由 API 映射')
    expect(requestFieldContent({ file: { mode: 'BASE64', kind: 'IMAGE' } }).hint).toContain('图片')
    expect(requestFieldContent({ file: { mode: 'ZIP_BASE64' } }).hint).toContain('ZIP')
  })
})
