import { compileOperand, createOperationOperand, inferOperandType, validateOperand } from '@/utils/operand'
import { expressionChildEntries, expressionOperatorOptions, expressionRightType, insertExpressionOperation, replaceExpressionOperator } from '@/components/expression/expressionTree'
import { operationOptions } from '@/utils/contentOperation'
import { contentDataTypeIcon, contentSourceIcon } from '@/utils/contentDisplay'

const literal = (value, valueType = 'STRING') => ({ kind: 'LITERAL', value, valueType })
const operation = (operator, right) => ({ kind: 'OPERATION', terms: [{ operand: literal('abc') }, { operator, operand: right }] })

test('新建一元运算也不保留隐藏右值', () => {
  expect(createOperationOperand([{ operand: literal('abc') }, { operator: 'is_empty', operand: literal('unused') }]).terms[1]).toEqual({ operator: 'is_empty' })
  const inserted = insertExpressionOperation(operation('contains', literal('b')), [], 'is_true').root
  expect(inserted.terms.at(-1)).toEqual({ operator: 'is_true' })
})

test('同一 API 下的不同内容沿用各自类型图标，固定版本不被当前目录覆盖', () => {
  const options = [{ _varId: '7', _refType: 'EXTERNAL_API', relativePath: '$', varType: 'OBJECT' }, { _varId: '7', _refType: 'EXTERNAL_API', relativePath: 'response.status', varType: 'INTEGER' }]
  const value = { kind: 'REFERENCE', refId: '7', refType: 'EXTERNAL_API', relativePath: 'response.status', valueType: 'INTEGER' }
  expect(contentDataTypeIcon(value, options)).toBe('123')
  expect(contentDataTypeIcon({ ...value, versionMode: 'FIXED', valueType: 'STRING' }, options)).toBe('Abc')
})

test('操作符随左值类型变化，字符串不提供减法，列表提供数量和包含', () => {
  expect(operationOptions('STRING').map(item => item.value)).toEqual(expect.arrayContaining(['contains', 'is_empty', 'regex_match']))
  expect(operationOptions('STRING').map(item => item.value)).not.toContain('-')
  expect(operationOptions('NUMBER').map(item => item.value)).toEqual(expect.arrayContaining(['+', '*', '>']))
  expect(operationOptions('LIST').map(item => item.value)).toEqual(expect.arrayContaining(['size_gt', 'contains_any']))
})

test('混合比较与逻辑运算按优先级识别当前左值类型', () => {
  const tree = { kind: 'OPERATION', terms: [{ operand: literal(1, 'NUMBER') }, { operator: '>', operand: literal(0, 'NUMBER') }, { operator: '&&', operand: literal('abc') }, { operator: 'contains', operand: literal('b') }] }
  expect(expressionOperatorOptions(tree, 3).map(item => item.value)).toContain('contains')
  expect(expressionOperatorOptions(tree, 3).map(item => item.value)).not.toContain('-')
  expect(expressionRightType(tree, 3)).toBe('STRING')
  expect(expressionOperatorOptions(tree, 2).map(item => item.value)).toContain('&&')
})

test.each([
  [{ kind: 'REFERENCE', refType: 'DATA_OBJECT_ROOT' }, '对象'],
  [{ kind: 'REFERENCE', refType: 'EXTERNAL_API' }, '接口'],
  [{ kind: 'REFERENCE', refType: 'MODEL' }, '模型'],
  [{ kind: 'LITERAL' }, '阈值'],
  [{ kind: 'PATH', refType: 'DATA_OBJECT' }, '路径'],
])('来源用两个字符标识 %j', (operand, icon) => {
  expect(contentSourceIcon(operand)).toBe(icon)
  expect(contentSourceIcon(operand)).toHaveLength(2)
})
test('无需右值的运算删除右节点及其依赖，切回双目留下待配置项', () => {
  const source = operation('==', { kind: 'REFERENCE', refId: 7, refType: 'VARIABLE', code: 'secret' })
  const unary = replaceExpressionOperator(source, ['terms', 1, 'operand'], 'is_empty').root
  expect(unary.terms[1]).not.toHaveProperty('operand')
  expect(expressionChildEntries(unary)[1].noValue).toBe(true)
  expect(validateOperand(unary)).toEqual([])
  expect(inferOperandType(unary)).toBe('BOOLEAN')
  expect(compileOperand(unary)).toBe('(isBlank("abc"))')
  expect(replaceExpressionOperator(unary, ['terms', 1, 'operand'], 'contains').root.terms[1].operand).toBeNull()
})
test('谓词和逻辑组合生成与运行端一致的脚本', () => {
  const node = operation('contains', literal('b'))
  node.terms.push({ operator: '&&', operand: literal(true, 'BOOLEAN') })
  expect(compileOperand(node)).toBe('((containsValue("abc", "b") && true))')
})
