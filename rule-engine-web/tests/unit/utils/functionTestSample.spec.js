import { describe, expect, it } from 'vitest'
import { functionSampleText, withFunctionSample } from '@/utils/functionTestSample'

const row = { id: '81', paramsJson: '[{"name":"Amount","type":"NUMBER","javaType":"long","example":9007199254740993},{"name":"ready","type":"BOOLEAN"}]' }

describe('函数参数样例', () => {
  it('从已有声明加载默认输入，只更新声明中的 example', () => {
    expect(functionSampleText(row)).toBe('{\n  "Amount": 9007199254740993,\n  "ready": false\n}')
    const updated = withFunctionSample(row, '{"Amount":9007199254740995,"ready":null}')
    expect(updated.paramsJson).toBe('[{"name":"Amount","type":"NUMBER","javaType":"long","example":9007199254740995},{"name":"ready","type":"BOOLEAN","example":null}]')
    expect(row.paramsJson).toContain('9007199254740993')
  })
  it.each(['[]', 'null', '3', '{"amount":1,"ready":true}', '{"Amount":1}'])('拒绝与函数声明不一致的输入 %s', text => {
    expect(() => withFunctionSample(row, text)).toThrow('参数')
  })
})
