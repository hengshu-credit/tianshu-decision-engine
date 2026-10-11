import { buildFieldTree } from '@/utils/fieldPicker'
import { FIELD_CATEGORY_TAGS, fieldPickerTags, matchesFieldTags, filterFieldTreeByTags, canUsePickerAction } from '@/utils/fieldPickerPolicy'
import { fieldPickerDetails } from '@/utils/fieldPickerDetails'

const field = (id, category, extra = {}) => ({ id, _varId: id, _refType: category === 'constant' ? 'CONSTANT' : 'VARIABLE', varCode: `field${id}`, varLabel: `字段 ${id}`, varType: 'NUMBER', _ref: { category }, ...extra })

test('默认不限制，显式空白名单不展示，组合标签按任一、全部、排除处理', () => {
  const tags = fieldPickerTags(field(1, 'standalone', { varType: 'INTEGER', tags: ['custom'] }))
  expect(tags).toEqual(expect.arrayContaining(['variable', 'read', 'write', 'type:integer', 'type:number', 'custom']))
  expect(matchesFieldTags(tags, null)).toBe(true)
  expect(matchesFieldTags(tags, [])).toBe(false)
  expect(matchesFieldTags(tags, { anyOf: ['constant', 'variable'], allOf: ['type:number'], noneOf: ['external'] })).toBe(true)
  expect(matchesFieldTags(tags, { allOf: ['variable', 'http-context'] })).toBe(false)
  expect(FIELD_CATEGORY_TAGS.constant).not.toContain('write')
})

test('可见和可选分别控制，隐藏项不进入目录，可见不可选项不能通过分类或最近使用绕过', () => {
  const tree = filterFieldTreeByTags(buildFieldTree([field(1, 'standalone'), field(2, 'constant'), field(3, 'external')]), ['variable', 'constant'], ['write'])
  expect([...tree.nodes.keys()]).toEqual(['VARIABLE:1', 'CONSTANT:2'])
  expect(tree.nodes.get('VARIABLE:1').selectable).toBe(true)
  expect(tree.nodes.get('CONSTANT:2').selectable).toBe(false)
})

test('内容级白名单保留结构父节点但禁止通过父节点选入隐藏对象', () => {
  const values = [field(1, 'object', { _refType: 'DATA_OBJECT', varCode: 'report.items', varType: 'LIST', _ref: { category: 'object', objectId: 5, objectCode: 'report' } }), field(2, 'object', { _refType: 'DATA_OBJECT', varCode: 'report.items.score', parentFieldId: 1, tags: ['allowed'], _ref: { category: 'object', objectId: 5, objectCode: 'report' } })]
  const tree = filterFieldTreeByTags(buildFieldTree(values), ['allowed'], null)
  expect(tree.nodes.get('DATA_OBJECT:2').parent.key).toBe('DATA_OBJECT:1')
  expect(tree.nodes.get('DATA_OBJECT:1').selectable).toBe(false)
  expect(tree.nodes.get('DATA_OBJECT:2').selectable).toBe(true)
})

test('写入上下文只开放路径，阈值及表达式按钮遵循同一 tag 规则', () => {
  expect(canUsePickerAction('PATH', ['write'], ['write'])).toBe(true)
  expect(canUsePickerAction('LITERAL', ['write'], ['write'])).toBe(false)
  expect(canUsePickerAction('EXPRESSION', ['write'], ['write'])).toBe(false)
})

test('字段详情包括默认值、参数、说明和版本，零和 false 不会丢失', () => {
  const zero = field(1, 'constant', { constantValue: 0, varObj: { description: '完整说明' } })
  expect(fieldPickerDetails(buildFieldTree([zero]).roots[0], 'v3')).toContain('值：0 · 说明：完整说明')
  const fn = { _function: true, varCode: 'calc', varLabel: '计算', varType: 'NUMBER', function: { id: 9, scope: 'GLOBAL', returnType: 'NUMBER', paramsJson: '[{"name":"value","type":"NUMBER"}]', description: '长说明，不截断原始 hover 内容' } }
  const text = fieldPickerDetails(buildFieldTree([fn]).roots[0], '最新 · v2')
  expect(text).toContain('版本：最新 · v2')
  expect(text).toContain('参数：value:NUMBER')
  expect(text).toContain('全局')
  expect(text).toContain('长说明，不截断原始 hover 内容')
})
