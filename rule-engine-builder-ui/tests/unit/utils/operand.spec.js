import {
  cloneOperand,
  collectOperandReferences,
  compileOperand,
  createAccessOperand,
  createArrayOperand,
  createCastOperand,
  createFunctionOperand,
  createLiteralOperand,
  createListQueryOperand,
  createOperationOperand,
  createPathOperand,
  createReferenceOperand,
  inferOperandType,
  operandChildren,
  operandDisplay,
  operandKindMeta,
  resolvePathOperand,
  syncOperandReference,
  validateOperand
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
    },
    {
      varCode: 'EMPTY_STRING',
      varLabel: '空字符串',
      varType: 'STRING',
      defaultValue: '',
      constantValue: '',
      _varId: 30,
      _refType: 'CONSTANT'
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

  test('常量引用保留值预览并按名称编码和值显示', () => {
    const operand = createReferenceOperand(references[2])

    expect(operand).toMatchObject({
      refId: 30,
      refType: 'CONSTANT',
      code: 'EMPTY_STRING',
      constantValue: ''
    })
    expect(operandDisplay(operand)).toBe("空字符串 EMPTY_STRING = ''")
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
    expect(operandDisplay(fn)).toBe('max(riskModel.score, 600)')
  })

  test('按稳定 ID 同步引用编码并自动解析 PATH', () => {
    const renamed = syncOperandReference({
      kind: 'REFERENCE', value: 'oldCode', code: 'oldCode', refId: 12, refType: 'DATA_OBJECT'
    }, references)
    expect(renamed.operand.code).toBe('request.customer.age')
    expect(renamed.changed).toBe(true)

    const path = syncOperandReference(createPathOperand('riskModel.score'), references)
    expect(path.operand.refId).toBe(21)
    expect(path.operand.resolved).toBe(true)

    const constant = syncOperandReference({
      kind: 'REFERENCE', value: 'EMPTY_STRING', code: 'EMPTY_STRING', refId: 30, refType: 'CONSTANT'
    }, references)
    expect(constant.operand.constantValue).toBe('')
  })

  test('显示类型能直观区分阈值、路径和资源来源', () => {
    expect(operandKindMeta(createLiteralOperand('10', 'NUMBER')).label).toBe('阈值')
    expect(operandKindMeta(createPathOperand('payload.score')).label).toBe('路径')
    expect(operandKindMeta(createReferenceOperand(references[0])).label).toBe('数据对象')
    expect(operandKindMeta(createFunctionOperand({ funcCode: 'max' })).label).toBe('方法')
    expect(operandKindMeta(createReferenceOperand({ varCode: 'risk.score', refType: 'MODEL_OUTPUT', id: 3 })).label).toBe('模型')
  })

  test('递归编译额度公式并收集所有稳定 ID 引用', () => {
    const repayment = createReferenceOperand({ varCode: 'monthlyRepayment', varLabel: '月成功还款额', varType: 'NUMBER', _varId: 101, _refType: 'VARIABLE' })
    const used = createReferenceOperand({ varCode: 'usedAmount', varLabel: '已使用额度', varType: 'NUMBER', _varId: 102, _refType: 'VARIABLE' })
    const factor = createReferenceOperand({ varCode: 'riskFactor', varLabel: '风险系数', varType: 'NUMBER', _varId: 103, _refType: 'VARIABLE' })
    const riskAmount = createReferenceOperand({ varCode: 'riskAmount', varLabel: '风险额度', varType: 'NUMBER', _varId: 104, _refType: 'VARIABLE' })
    const number = value => createLiteralOperand(String(value), 'NUMBER')
    const fn = (code, args) => createFunctionOperand({ funcCode: code, returnType: 'NUMBER' }, args)
    const op = (operator, operands) => createOperationOperand(operator, operands, 'NUMBER')
    const expression = op('*', [
      fn('numCeil', [op('/', [
        fn('max', [number(4200), fn('min', [
          op('+', [
            op('*', [fn('min', [fn('max', [repayment, used]), number(9000)]), factor, number(0.3)]),
            op('*', [riskAmount, number(0.5)])
          ]),
          number(7000)
        ])]),
        number(500)
      ])]),
      number(500)
    ])

    expect(compileOperand(expression)).toBe('(numCeil((max(4200, min(((min(max(monthlyRepayment, usedAmount), 9000) * riskFactor * 0.3) + (riskAmount * 0.5)), 7000)) / 500)) * 500)')
    expect(collectOperandReferences(expression).map(item => item.refId)).toEqual([101, 102, 103, 104])
    expect(inferOperandType(expression)).toBe('NUMBER')
    expect(validateOperand(expression)).toEqual([])
  })

  test('支持 Key、Index、类型转换、数组和名单配置节点', () => {
    const target = createPathOperand('request.payload')
    const key = createAccessOperand(target, 'KEY', createLiteralOperand('score', 'STRING'), 'NUMBER')
    const index = createAccessOperand(createPathOperand('request.items'), 'INDEX', createLiteralOperand('0', 'NUMBER'), 'OBJECT')
    const cast = createCastOperand('STRING', key)
    const array = createArrayOperand([cast, index])
    const listQuery = createListQueryOperand({
      listIds: [1, 2],
      itemTypes: ['MOBILE'],
      combinationMode: 'ANY_FIELD_ALL_LISTS',
      matchMode: 'IN_LIST'
    })

    expect(compileOperand(key)).toBe('objGet(request.payload, "score")')
    expect(compileOperand(index)).toBe('arrGet(request.items, 0)')
    expect(compileOperand(cast)).toBe('toStringValue(objGet(request.payload, "score"))')
    expect(compileOperand(array)).toBe('[toStringValue(objGet(request.payload, "score")), arrGet(request.items, 0)]')
    expect(compileOperand(listQuery)).toBe('listQuery([1, 2], ["MOBILE"], "ANY_FIELD_ALL_LISTS", "IN_LIST")')
    expect(operandChildren(array)).toHaveLength(2)
    expect(inferOperandType(array)).toBe('LIST')
  })

  test('深克隆不污染原节点，校验能定位缺少参数和非法写目标', () => {
    const expression = createOperationOperand('+', [createLiteralOperand('1', 'NUMBER'), null], 'NUMBER')
    const cloned = cloneOperand(expression)
    cloned.operands[0].value = '2'

    expect(expression.operands[0].value).toBe('1')
    expect(validateOperand(expression)).toEqual([
      expect.objectContaining({ path: 'root.operands[1]', message: '表达式参数不能为空' })
    ])
    expect(validateOperand(createFunctionOperand({ funcCode: 'max' }, []), { allowedKinds: ['PATH', 'REFERENCE'] }))
      .toEqual([expect.objectContaining({ path: 'root', message: '当前配置位置不支持方法' })])
  })

  test('递归同步访问器、运算符和数组中的引用', () => {
    const expression = createArrayOperand([
      createOperationOperand('+', [
        { kind: 'REFERENCE', value: 'old', code: 'old', refId: 12, refType: 'DATA_OBJECT' },
        createAccessOperand({ kind: 'REFERENCE', value: 'old2', code: 'old2', refId: 21, refType: 'MODEL_OUTPUT' }, 'KEY', createLiteralOperand('score'))
      ])
    ])
    const result = syncOperandReference(expression, references)

    expect(result.changed).toBe(true)
    expect(result.operand.items[0].operands[0].code).toBe('request.customer.age')
    expect(result.operand.items[0].operands[1].target.code).toBe('riskModel.score')
  })
})
