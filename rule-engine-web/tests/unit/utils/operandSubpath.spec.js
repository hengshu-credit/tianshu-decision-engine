import { createPathOperand, createReferenceOperand, createRelativePathOperand, resolvePathOperand, syncOperandReference } from '@/utils/operand'
import { buildReferenceCatalog, buildPickerOptions } from '@/utils/referenceCatalog'
import { buildFieldTree } from '@/utils/fieldPicker'

const source = { varCode: 'request.payload', varLabel: '动态内容', varType: 'OBJECT', _varId: '9007199254740993', _refType: 'DATA_OBJECT' }

test('动态子路径使用独立的结果类型，保留原始键名、数组下标和来源 ID', () => {
  const operand = createRelativePathOperand(createReferenceOperand(source), 'items[0]["User.Name"]', 'STRING')
  expect(operand).toMatchObject({ kind: 'PATH', refType: 'DATA_OBJECT', refId: source._varId,
    code: 'request.payload.items[0]["User.Name"]', relativePath: '.items[0]["User.Name"]', valueType: 'STRING' })
  const next = syncOperandReference(operand, [{ ...source, varCode: 'renamed.payload' }]).operand
  expect(next).toMatchObject({ code: 'renamed.payload.items[0]["User.Name"]', valueType: 'STRING', refId: source._varId })
})

test('完整路径匹配到容器时不能把容器类型当作动态叶子类型', () => {
  expect(resolvePathOperand(createPathOperand('request.payload.score'), [source]).operand.valueType).toBe('')
})

test('已选模型来源不会被同名目录或更长路径抢走，版本信息保持不变', () => {
  const anchor = createReferenceOperand({ varCode: 'model', varType: 'OBJECT', _varId: '9', _refType: 'MODEL', versionMode: 'FIXED', versionId: '11' })
  const operand = createRelativePathOperand(anchor, 'extra.score', 'NUMBER')
  expect(resolvePathOperand(operand, [{ ...source, varCode: 'model.extra' }]).operand).toMatchObject({
    refId: '9', refType: 'MODEL', relativePath: '.extra.score', valueType: 'NUMBER', versionId: '11',
  })
})

test('API 子路径从调用结果根计算，保留选中的响应体作为编辑来源', () => {
  const api = createReferenceOperand({ varCode: 'credit.response.body', apiCode: 'credit', relativePath: 'response.body', varType: 'OBJECT', _varId: '8', _refType: 'EXTERNAL_API' })
  expect(createRelativePathOperand(api, 'items[0].score', 'NUMBER')).toMatchObject({
    refId: '8', refType: 'EXTERNAL_API', relativePath: 'response.body.items[0].score', anchorPath: 'credit.response.body', valueType: 'NUMBER',
  })
})

test.each(['score + 1', 'items[-1]', 'items[*]', 'items[', 'a..b'])('拒绝不确定或不完整的路径 %s', path => {
  expect(() => createRelativePathOperand(createReferenceOperand(source), path, 'STRING')).toThrow()
})

test('没有字段的对象和模型也出现在目录，根对象 ID 不与字段 ID 混用', () => {
  const catalog = buildReferenceCatalog([], [{ object: { id: '7', objectCode: 'Request' }, variables: [] }], [{ id: '8', modelCode: 'Model', outputFields: [] }])
  const options = buildPickerOptions(catalog)
  expect(options).toEqual(expect.arrayContaining([
    expect.objectContaining({ _varId: '7', _refType: 'DATA_OBJECT_ROOT', varCode: 'Request' }),
    expect.objectContaining({ _varId: '8', _refType: 'MODEL', varCode: 'Model', varType: 'OBJECT' }),
  ]))
  const roots = buildFieldTree(options).roots
  expect(roots).toHaveLength(2)
  expect(roots.every(root => root.selectable && root.children.length === 0)).toBe(true)
})
