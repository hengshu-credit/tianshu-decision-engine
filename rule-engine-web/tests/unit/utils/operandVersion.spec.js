import { describe, it, expect } from 'vitest'
import { createReferenceOperand, createFunctionOperand, syncOperandReference, compileOperand, operandDisplay, resolvePathOperand } from '@/utils/operand'

describe('字段固定版本', () => {
  const version = { versionMode: 'FIXED', versionId: '9007199254740993', version: 2, versionOwnerId: '19' }
  it('保存版本身份，刷新当前目录不覆盖历史名称和类型', () => {
    const original = createReferenceOperand({ id: '19', _refType: 'VARIABLE', varCode: 'oldCode', varType: 'INTEGER', ...version })
    expect(original).toMatchObject(version)
    const result = syncOperandReference(original, [{ id: '19', _refType: 'VARIABLE', varCode: 'newCode', varType: 'STRING' }])
    expect(result.operand).toMatchObject({ ...version, code: 'oldCode', valueType: 'INTEGER' })
    expect(result.changed).toBe(false)
  })
  it('方法保留版本和所选版本的参数定义', () => {
    const operand = createFunctionOperand({ id: '19', funcCode: 'oldFn', paramsJson: '[{"name":"n","type":"INTEGER"}]', ...version })
    expect(operand).toMatchObject({ ...version, parameterTypes: ['NUMBER'], parameterCount: 1, parameters: [{ name: 'n', type: 'INTEGER' }] })
  })
  it('分流等前端编译链路生成固定版本调用，输入框展示仍使用函数名称', () => {
    const operand = createFunctionOperand({ id: '19', funcCode: 'oldFn', ...version }, [{ kind: 'LITERAL', value: '2', valueType: 'NUMBER' }])
    expect(compileOperand(operand)).toBe('invokeFunctionVersionById("19", "9007199254740993", 2)')
    expect(operandDisplay(operand)).toBe('oldFn(2)')
    expect(compileOperand({ ...operand, versionSource: 'FUNCTION_HISTORY' })).toBe('invokeFunctionVersionById("19", "FUNCTION_HISTORY:9007199254740993", 2)')
    expect(compileOperand(createReferenceOperand({ id: '19', _refType: 'MODEL', varCode: 'model', relativePath: '.score', ...version })))
      .toBe('resourceVersionValue("MODEL", "19", "9007199254740993", ".score")')
  })
  it('在固定对象下追加取值路径时保留版本，改选其他路径时解除原绑定', () => {
    const value = { kind: 'PATH', refId: '19', refType: 'MODEL', code: 'model', value: 'model.scores[0]', anchorPath: 'model', ...version }
    expect(resolvePathOperand(value, []).operand).toMatchObject({ ...version, relativePath: '.scores[0]' })
    expect(resolvePathOperand({ ...value, value: 'different.score' }, []).operand.versionMode).toBeUndefined()
  })
})
