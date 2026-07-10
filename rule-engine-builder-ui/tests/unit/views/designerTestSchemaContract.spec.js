import fs from 'fs'
import path from 'path'

const contracts = [
  ['DecisionTable.vue', 'TABLE', ':model-json="model"'],
  ['DecisionTree.vue', 'TREE', ':model-json-provider="buildBackendModel"'],
  ['DecisionFlow.vue', 'FLOW', ':model-json-provider="buildBackendModel"'],
  ['RuleSet.vue', 'RULE_SET', ':model-json-provider="serializeModel"'],
  ['CrossTable.vue', 'CROSS', ':model-json="model"'],
  ['Scorecard.vue', 'SCORE', ':model-json="model"'],
  ['AdvancedCrossTable.vue', 'CROSS_ADV', ':model-json-provider="buildSaveModel"'],
  ['AdvancedScorecard.vue', 'SCORE_ADV', ':model-json="model"'],
  ['ScriptEditor.vue', 'SCRIPT', ':model-json-provider="buildModelJson"']
]

describe('all designers use unified test schema', () => {
  test.each(contracts)('%s passes current model as %s', (fileName, modelType, modelBinding) => {
    const source = fs.readFileSync(path.resolve(__dirname, '../../../src/views/designer', fileName), 'utf8')
    expect(source).toContain('<designer-test-dialog')
    expect(source).toContain(':project-id="projectIdForRefs"')
    expect(source).toContain(`model-type="${modelType}"`)
    expect(source).toContain(modelBinding)
  })
})
