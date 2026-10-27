import { h } from 'vue'
import { mount } from '@test-utils'
import ContentReference from '@/components/common/ContentReference.vue'
import { contentReferenceParts, renderContentReference } from '@/utils/contentReference'

test('编辑器隐藏无实际版本的说明，有效版本及显式固定版本异常仍保留', () => {
  const reference = { kind: 'REFERENCE', refType: 'VARIABLE', refId: 1, code: 'Original_Code', label: '原始名称', valueType: 'STRING' }
  const versions = value => contentReferenceParts(value, [], false, 'editor').filter(part => part.key === 'version')
  expect(versions(reference)).toEqual([])
  expect(versions({ ...reference, version: 3 }).map(part => part.text)).toEqual(['v3'])
  expect(versions({ ...reference, versionMode: 'LATEST' }).map(part => part.text)).toEqual(['最新'])
  expect(versions({ ...reference, versionMode: 'FIXED' }).map(part => part.text)).toEqual(['版本未提供'])
  expect(versions({ ...reference, refType: 'FUNCTION', builtin: true })).toEqual([])
  expect(contentReferenceParts(reference).find(part => part.key === 'version').text).toBe('版本未提供')
  expect(reference.code).toBe('Original_Code')
})

test('通用组件和图形适配使用同一来源、类型、名称、编码、版本分段', () => {
  const value = { kind: 'REFERENCE', refType: 'MODEL', refId: '9007199254740993', label: '风险模型', code: 'risk', valueType: 'OBJECT', versionMode: 'FIXED', version: 3 }
  const wrapper = mount(ContentReference, { props: { value } })
  expect(wrapper.findAll('[data-content-part]').map(part => part.text())).toEqual(['模型', '{}', '风险模型', 'risk', 'v3'])
  const graph = mount({ render: () => renderContentReference(h, value) })
  expect(graph.text()).toBe(wrapper.text())
  graph.unmount(); wrapper.unmount()
  expect(value.refId).toBe('9007199254740993')
})
test.each([0, false, null, ''])('阈值 %s 不被空值回退吞掉', value => {
  expect(contentReferenceParts({ kind: 'LITERAL', value, valueType: typeof value === 'boolean' ? 'BOOLEAN' : 'NUMBER' }).at(-1).text).toBe(value === null ? 'null' : value === '' ? '""' : String(value))
})
test('秘密在可见内容、title 和 DOM 中都不出现，名称中的 HTML 不执行', () => {
  const wrapper = mount(ContentReference, { props: { value: { kind: 'LITERAL', value: 'secret-123', valueType: 'STRING' }, sensitive: true } })
  expect(wrapper.html()).not.toContain('secret-123')
  const graph = mount({ render: () => renderContentReference(h, { fieldCode: 'risk', fieldName: '<img onerror=alert(1)>', fieldType: 'STRING' }) })
  const element = graph.element
  expect(element.querySelector('img')).toBeNull()
  expect(element.textContent).toContain('<img onerror=alert(1)>')
  graph.unmount(); wrapper.unmount()
})

test('敏感组合表达式不在展示分段中泄露子节点阈值', () => {
  const value = { kind: 'OPERATION', terms: [{ operand: { kind: 'LITERAL', value: 'secret-in-expression', valueType: 'STRING' } }, { operator: '+', operand: { kind: 'LITERAL', value: 'suffix', valueType: 'STRING' } }] }
  const wrapper = mount(ContentReference, { props: { value, sensitive: true } })
  expect(wrapper.html()).not.toContain('secret-in-expression')
  expect(wrapper.html()).not.toContain('suffix')
  expect(wrapper.get('.content-ref__value').text()).toBe('••••••••')
})

test('没有引用 ID 时不能借另一个无 ID 的目录项补名称或类型', () => {
  const parts = contentReferenceParts({ kind: 'REFERENCE', refType: 'VARIABLE', code: 'unbound', label: '未绑定', valueType: 'STRING' }, [{ _refType: 'VARIABLE', varCode: 'other', varLabel: '别的字段', varType: 'NUMBER' }])
  expect(parts.find(part => part.key === 'code').text).toBe('unbound')
  expect(parts.find(part => part.key === 'name').text).toBe('未绑定')
  expect(parts.find(part => part.key === 'type').text).toBe('Abc')
})
