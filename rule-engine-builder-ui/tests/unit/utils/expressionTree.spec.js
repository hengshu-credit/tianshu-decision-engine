import {
  createFunctionTemplate,
  firstEditablePath,
  getExpressionNode,
  removeExpressionNode,
  setExpressionNode,
  wrapExpressionNode
} from '@/components/expression/expressionTree'

describe('expressionTree', () => {
  test('按路径替换和删除递归参数且不修改原值', () => {
    const source = { kind: 'FUNCTION', functionCode: 'max', args: [{ kind: 'LITERAL', value: '1', valueType: 'NUMBER' }, null] }
    const replaced = setExpressionNode(source, ['args', 1], { kind: 'LITERAL', value: '2', valueType: 'NUMBER' })

    expect(source.args[1]).toBeNull()
    expect(getExpressionNode(replaced, ['args', 1]).value).toBe('2')
    expect(removeExpressionNode(replaced, ['args', 0]).args[0]).toBeNull()
  })

  test('函数元数据自动生成参数并定位首个参数', () => {
    const fn = createFunctionTemplate({ id: 7, funcCode: 'max', returnType: 'NUMBER', paramsJson: '[{"name":"a","type":"NUMBER"},{"name":"b","type":"NUMBER"}]' })

    expect(fn).toMatchObject({ kind: 'FUNCTION', functionId: 7, functionCode: 'max', valueType: 'NUMBER' })
    expect(fn.args).toHaveLength(2)
    expect(firstEditablePath(fn)).toEqual(['args', 0])
  })

  test('运算、访问和转换模板会包裹当前节点', () => {
    const field = { kind: 'PATH', value: 'request.amount' }
    expect(wrapExpressionNode(field, { kind: 'OPERATION', operator: '+', operands: [] }).operands[0]).toEqual(field)
    expect(wrapExpressionNode(field, { kind: 'ACCESS', accessType: 'KEY' }).target).toEqual(field)
    expect(wrapExpressionNode(field, { kind: 'CAST', targetType: 'NUMBER' }).operand).toEqual(field)
  })
})
