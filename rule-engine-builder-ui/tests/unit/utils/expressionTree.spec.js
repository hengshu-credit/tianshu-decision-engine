import {
  collapsedExpressionPaths,
  createFunctionTemplate,
  existingCollapsedPaths,
  expressionAncestorKeys,
  expressionDescendantCount,
  expressionPathKey,
  firstEditablePath,
  getExpressionNode,
  indentExpressionTerm,
  insertExpressionOperation,
  moveExpressionNode,
  moveExpressionSibling,
  outdentExpressionOperation,
  removeExpressionNode,
  setExpressionNode,
  wrapExpressionNode
} from '@/components/expression/expressionTree'
import { createLiteralOperand, createOperationOperand } from '@/utils/operand'
import { compileOperand } from '@/utils/operand'

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

  test('函数数组和对象示例生成结构化参数而不是错误字符串', () => {
    const arrayFn = createFunctionTemplate({
      id: 8,
      funcCode: 'arrMax',
      params: [{ name: 'values', type: 'LIST', example: [3, 1, 2] }]
    })
    const objectFn = createFunctionTemplate({
      id: 9,
      funcCode: 'objGet',
      params: [{ name: 'object', type: 'MAP', example: { customer: { age: 36 } } }]
    })

    expect(arrayFn.args[0]).toMatchObject({ kind: 'ARRAY', valueType: 'LIST' })
    expect(arrayFn.args[0].items).toHaveLength(3)
    expect(objectFn.args[0]).toMatchObject({ kind: 'LITERAL', valueType: 'MAP', value: '{"customer":{"age":36}}' })
  })

  test('运算、访问和转换模板会包裹当前节点', () => {
    const field = { kind: 'PATH', value: 'request.amount' }
    expect(wrapExpressionNode(field, createOperationOperand([
      { operand: null },
      { operator: '+', operand: null }
    ])).terms[0].operand).toEqual(field)
    expect(wrapExpressionNode(field, { kind: 'ACCESS', accessType: 'KEY' }).target).toEqual(field)
    expect(wrapExpressionNode(field, { kind: 'CAST', targetType: 'NUMBER' }).operand).toEqual(field)
  })

  test('在运算项后插入混合运算符时保持同级', () => {
    const source = createOperationOperand([
      { operand: createLiteralOperand('1', 'NUMBER') },
      { operator: '+', operand: createLiteralOperand('2', 'NUMBER') }
    ])
    const result = insertExpressionOperation(source, ['terms', 0, 'operand'], '*')

    expect(result.root.terms.map(term => term.operator || '')).toEqual(['', '*', '+'])
    expect(result.root.terms[1].operand).toBeNull()
    expect(result.selectedPath).toEqual(['terms', 1, 'operand'])
  })

  test('选中普通节点时创建两项运算序列', () => {
    const source = createLiteralOperand('1', 'NUMBER')
    const result = insertExpressionOperation(source, [], '-')

    expect(result.root.terms).toEqual([
      { operand: source },
      { operator: '-', operand: null }
    ])
    expect(result.selectedPath).toEqual(['terms', 1, 'operand'])
  })

  test('按语义层级生成默认折叠路径并统计隐藏节点', () => {
    const source = {
      kind: 'FUNCTION',
      functionCode: 'outer',
      args: [{
        kind: 'OPERATION',
        terms: [{
          operand: {
          kind: 'FUNCTION',
          functionCode: 'inner',
          args: [
            { kind: 'LITERAL', value: '1', valueType: 'NUMBER' },
            { kind: 'LITERAL', value: '2', valueType: 'NUMBER' }
          ]
          }
        }, { operator: '+', operand: { kind: 'LITERAL', value: '3', valueType: 'NUMBER' } }]
      }]
    }

    expect(collapsedExpressionPaths(source, 2)).toEqual(['args.0'])
    expect(expressionDescendantCount(source.args[0].terms[0].operand)).toBe(2)
  })

  test('折叠到两层时折叠第二层而不是第三层', () => {
    const source = {
      kind: 'FUNCTION', functionCode: 'outer', args: [{
        kind: 'FUNCTION', functionCode: 'middle', args: [{
          kind: 'FUNCTION', functionCode: 'inner', args: [createLiteralOperand('1', 'NUMBER')]
        }]
      }]
    }

    expect(collapsedExpressionPaths(source, 2)).toEqual(['args.0'])
  })

  test('路径键支持根节点、反解祖先，并清理已失效的折叠状态', () => {
    const source = { kind: 'FUNCTION', functionCode: 'max', args: [{ kind: 'LITERAL', value: '1', valueType: 'NUMBER' }] }

    expect(expressionPathKey([])).toBe('$')
    expect(expressionAncestorKeys(['args', 0, 'items', 1])).toEqual(['$', 'args', 'args.0', 'args.0.items'])
    expect(existingCollapsedPaths(source, ['$', 'args.0', 'missing'])).toEqual(['$'])
  })

  test('缩进运算项会把它与前一项组成显式括号', () => {
    const source = createOperationOperand([
      { operand: { kind: 'PATH', value: 'a', code: 'a' } },
      { operator: '+', operand: { kind: 'PATH', value: 'b', code: 'b' } },
      { operator: '*', operand: { kind: 'PATH', value: 'c', code: 'c' } }
    ])

    const groupedLeft = indentExpressionTerm(source, ['terms', 1, 'operand'])
    expect(groupedLeft.changed).toBe(true)
    expect(compileOperand(groupedLeft.root)).toBe('((a + b) * c)')
    expect(groupedLeft.selectedPath).toEqual(['terms', 0, 'operand', 'terms', 1, 'operand'])

    const groupedRight = indentExpressionTerm(source, ['terms', 2, 'operand'])
    expect(compileOperand(groupedRight.root)).toBe('(a + (b * c))')
  })

  test('反缩进会展开当前嵌套运算但保留运算符顺序', () => {
    const source = createOperationOperand([
      { operand: { kind: 'PATH', value: 'a', code: 'a' } },
      {
        operator: '+',
        operand: createOperationOperand([
          { operand: { kind: 'PATH', value: 'b', code: 'b' } },
          { operator: '*', operand: { kind: 'PATH', value: 'c', code: 'c' } }
        ])
      }
    ])

    const result = outdentExpressionOperation(source, ['terms', 1, 'operand'])

    expect(result.changed).toBe(true)
    expect(compileOperand(result.root)).toBe('(a + b * c)')
    expect(result.selectedPath).toEqual(['terms', 1, 'operand'])
  })

  test('同级移动只交换操作数，运算符继续属于原槽位', () => {
    const source = createOperationOperand([
      { operand: { kind: 'PATH', value: 'a', code: 'a' } },
      { operator: '+', operand: { kind: 'PATH', value: 'b', code: 'b' } },
      { operator: '*', operand: { kind: 'PATH', value: 'c', code: 'c' } }
    ])

    const result = moveExpressionSibling(source, ['terms', 2, 'operand'], -1)

    expect(result.changed).toBe(true)
    expect(compileOperand(result.root)).toBe('(a + c * b)')
    expect(result.selectedPath).toEqual(['terms', 1, 'operand'])
  })

  test('拖拽可移入空槽但拒绝覆盖、后代循环和不合法缩进', () => {
    const source = {
      kind: 'FUNCTION',
      functionCode: 'max',
      args: [
        { kind: 'PATH', value: 'a', code: 'a' },
        null,
        { kind: 'PATH', value: 'c', code: 'c' }
      ]
    }

    const moved = moveExpressionNode(source, ['args', 0], ['args', 1])
    expect(moved.changed).toBe(true)
    expect(moved.root.args[0]).toBeNull()
    expect(moved.root.args[1].code).toBe('a')
    expect(moveExpressionNode(source, ['args', 0], ['args', 2]).changed).toBe(false)
    expect(moveExpressionNode(source, [], ['args', 1]).changed).toBe(false)
    expect(indentExpressionTerm(source, ['args', 0]).changed).toBe(false)
    expect(outdentExpressionOperation(source, []).changed).toBe(false)
  })
})
