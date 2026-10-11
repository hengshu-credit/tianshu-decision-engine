import { actionDataToBlocks, blocksToActionData, generateScript, newBlock, BLOCK_TYPES } from '@/utils/actionDataCodegen'
import { createReferenceOperand, syncOperandReference, compileOperand, createPathOperand, resolvePathOperand } from '@/utils/operand'
import { collectActionDataInputCodes } from '@/utils/testSampleParams'

describe('规则外数与名单动作', () => {
  test('API 结果不会误列为调用方入参，名单内容仍参与输入收集', () => {
    const actions = [{ type: 'assign', valueOperand: { kind: 'REFERENCE', refType: 'EXTERNAL_API', refId: 12, code: 'credit.status' } },
      { type: 'list-write', entries: [{ itemType: 'MOBILE', valueOperand: { kind: 'REFERENCE', refType: 'VARIABLE', refId: 5, code: 'mobile' } }] }]
    expect([...collectActionDataInputCodes(actions, [])]).toEqual(['mobile'])
  })
  test('外数嵌套路径保留 API ID，重命名后仍读取相同响应内容', () => {
    const field = { id: 12, refType: 'EXTERNAL_API', varCode: 'credit.response.body', relativePath: 'response.body', apiCode: 'credit', varType: 'OBJECT' }
    const path = resolvePathOperand(createPathOperand('credit.response.body.extra.preserved'), [field]).operand
    expect(compileOperand(path)).toBe('externalApiValue(12, "response.body.extra.preserved")')
    const renamed = { ...field, apiCode: 'creditV2', varCode: 'creditV2.response.body' }
    const synced = syncOperandReference(path, [renamed]).operand
    expect(synced.refId).toBe(12)
    expect(synced.code).toBe('creditV2.response.body.extra.preserved')
    expect(compileOperand(synced)).toBe('externalApiValue(12, "response.body.extra.preserved")')
  })
  test('API 字段用 ID 与响应路径定位，目录刷新不能将响应改成完整结果', () => {
    const options = [{ id: 12, refType: 'EXTERNAL_API', varCode: 'credit', relativePath: '$', apiCode: 'credit', varType: 'OBJECT' },
      { id: 12, refType: 'EXTERNAL_API', varCode: 'credit.response.body', relativePath: 'response.body', apiCode: 'credit', varType: 'OBJECT' }]
    const operand = createReferenceOperand(options[1])
    expect(syncOperandReference(operand, options).operand.relativePath).toBe('response.body')
    expect(compileOperand(operand)).toBe('externalApiValue(12, "response.body")')
    expect(compileOperand(createReferenceOperand(options[0]))).toBe('externalApiValue(12, "$")')
  })
  test('外数以 ID 调用并整体赋值，配置往返不丢失', () => {
    const action = { ...newBlock('api-call'), apiId: '12', targetOperand: { kind: 'PATH', value: 'apiResult' } }
    expect(generateScript([action])).toBe('apiResult = externalApiValue(12, "$")')
    expect(blocksToActionData(actionDataToBlocks([action]))).toEqual([action])
    expect(BLOCK_TYPES.some(item => item.type === 'api-call')).toBe(true)
  })
  test('名单动作保留多个名单及字段身份并生成可执行调用', () => {
    const action = { ...newBlock('list-write'), listIds: ['8', '9'], entries: [{ itemType: 'MOBILE', valueOperand: { kind: 'REFERENCE', refId: 5, refType: 'VARIABLE', code: 'mobile', resolved: true } }] }
    expect(generateScript([action])).toContain('writeRuleList(')
    expect(generateScript([action])).toContain('[mobile]')
    expect(blocksToActionData(actionDataToBlocks([action]))).toEqual([action])
  })
})
