import fs from 'fs'
import path from 'path'

const files = [
  'src/components/flow/ActionBlockEditor.vue',
  'src/views/designer/DecisionTable.vue',
  'src/views/designer/CrossTable.vue',
  'src/views/designer/Scorecard.vue',
  'src/views/designer/AdvancedCrossTable.vue',
  'src/views/designer/AdvancedScorecard.vue',
  'src/views/model/ModelDetail.vue'
]

describe('统一复杂表达式入口覆盖', () => {
  test.each(files)('%s 的读值位置使用统一表达式上下文', file => {
    const source = fs.readFileSync(path.resolve(__dirname, '../../../' + file), 'utf8')
    expect(source).toContain("getExpressionContext('READ_EXPRESSION').allowedKinds")
    expect(source).not.toContain("['LITERAL', 'PATH', 'REFERENCE', 'FUNCTION']")
    expect(source).not.toContain("['PATH', 'REFERENCE', 'FUNCTION']")
  })

  test('模型转换参数也允许函数、运算、取值和类型转换', () => {
    const source = fs.readFileSync(path.resolve(__dirname, '../../../src/views/model/ModelDetail.vue'), 'utf8')
    expect(source).toContain("transformArgKinds: getExpressionContext('READ_EXPRESSION').allowedKinds")
  })
})
