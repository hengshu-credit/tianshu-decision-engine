import { resolveTraceReferences } from '@/utils/traceReference'

describe('traceReference', () => {
  test('还原完整规则帧中的绝对引用后允许安全截取 expressionTrace', () => {
    const raw = [{
      expressionTrace: [[
        { type: 'VALUE', value: { faces: [{ score: 0.98 }] } },
        { type: 'VARIABLE', token: 'faces', value: { $ref: '$[0].expressionTrace[0][0].value' } }
      ]]
    }]

    const resolved = resolveTraceReferences(raw)

    expect(resolved[0].expressionTrace[0][1].value).toEqual({ faces: [{ score: 0.98 }] })
    expect(JSON.stringify(resolved[0].expressionTrace)).not.toContain('$ref')
    expect(raw[0].expressionTrace[0][1].value).toEqual({ $ref: '$[0].expressionTrace[0][0].value' })
  })

  test('无效或循环引用不暴露内部路径', () => {
    const resolved = resolveTraceReferences({
      missing: { $ref: '$.notFound' },
      circular: { $ref: '$.circular' }
    })

    expect(JSON.stringify(resolved)).not.toContain('$ref')
  })
})
