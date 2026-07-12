import {
  collectOperandReferences,
  compileOperand,
  createFunctionOperand,
  createLiteralOperand,
  createPathOperand,
  createReferenceOperand,
  operandDisplay,
  resolvePathOperand
} from '@/utils/operand'

describe('operand', () => {
  const references = [
    {
      varCode: 'request.customer.age',
      varLabel: '客户年龄',
      varType: 'INTEGER',
      _varId: 12,
      _refType: 'DATA_OBJECT'
    },
    {
      varCode: 'riskModel.score',
      varLabel: '风险评分',
      varType: 'DOUBLE',
      _varId: 21,
      _refType: 'MODEL_OUTPUT'
    }
  ]

  test('创建阈值时保留原始值和显式类型', () => {
    expect(createLiteralOperand('threshold', 'STRING')).toEqual({
      kind: 'LITERAL',
      value: 'threshold',
      valueType: 'STRING'
    })
  })

  test('唯一完整路径匹配后保留 PATH 并补齐 ID', () => {
    const source = createPathOperand('request.customer.age')
    const result = resolvePathOperand(source, references)

    expect(result.candidates).toHaveLength(0)
    expect(result.operand).toMatchObject({
      kind: 'PATH',
      value: 'request.customer.age',
      code: 'request.customer.age',
      refId: 12,
      refType: 'DATA_OBJECT',
      valueType: 'INTEGER',
      label: '客户年龄',
      resolved: true
    })
  })

  test('路径匹配 refCode 和 scriptName', () => {
    const result = resolvePathOperand(createPathOperand('account.balance'), [
      {
        refCode: 'account.balance',
        scriptName: 'account.balance',
        refLabel: { label: '账户余额', code: 'account.balance' },
        varType: 'NUMBER',
        id: 31,
        refType: 'DATA_OBJECT'
      }
    ])

    expect(result.operand).toMatchObject({
      refId: 31,
      refType: 'DATA_OBJECT',
      label: '账户余额',
      resolved: true
    })
  })

  test('重复路径不猜测资源', () => {
    const result = resolvePathOperand(createPathOperand('score'), [
      { varCode: 'score', _varId: 1, _refType: 'VARIABLE' },
      { varCode: 'score', _varId: 2, _refType: 'MODEL_OUTPUT' }
    ])

    expect(result.candidates).toHaveLength(2)
    expect(result.operand).toMatchObject({
      kind: 'PATH',
      value: 'score',
      resolved: false,
      refId: null,
      refType: ''
    })
  })

  test('未匹配路径作为外部输入原样保留', () => {
    const result = resolvePathOperand(createPathOperand('payload.Custom_Path'), references)

    expect(result.candidates).toEqual([])
    expect(result.operand).toMatchObject({
      kind: 'PATH',
      value: 'payload.Custom_Path',
      code: 'payload.Custom_Path',
      resolved: false
    })
  })

  test('引用操作数必须保留 ID 和 refType', () => {
    expect(createReferenceOperand(references[1])).toMatchObject({
      kind: 'REFERENCE',
      refId: 21,
      refType: 'MODEL_OUTPUT',
      code: 'riskModel.score',
      valueType: 'DOUBLE',
      resolved: true
    })
  })

  test('字符串阈值始终加引号，路径不加引号', () => {
    expect(compileOperand(createLiteralOperand('threshold', 'STRING'))).toBe('"threshold"')
    expect(compileOperand(createPathOperand('request.score'))).toBe('request.score')
  })

  test('函数参数递归编译并递归收集依赖', () => {
    const fn = createFunctionOperand({ id: 9, funcCode: 'max', funcName: '最大值' }, [
      resolvePathOperand(createPathOperand('riskModel.score'), references).operand,
      createLiteralOperand('600', 'NUMBER')
    ])

    expect(compileOperand(fn)).toBe('max(riskModel.score, 600)')
    expect(collectOperandReferences(fn)).toEqual([
      expect.objectContaining({ refId: 21, refType: 'MODEL_OUTPUT' })
    ])
    expect(operandDisplay(fn)).toBe('最大值(...)')
  })
})
