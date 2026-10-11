import { shallowMount, flushPromises } from '@test-utils'
import Scorecard from '@/views/designer/Scorecard.vue'
import AdvancedScorecard from '@/views/designer/AdvancedScorecard.vue'
import { normalizeScorecardLevelVar } from '@/utils/scorecardLevel'
import { buildRuleVersionDiff } from '@/utils/ruleVersionDiff'

const grade = { kind: 'REFERENCE', refId: '9007199254740993', refType: 'VARIABLE', code: 'Risk_Grade', value: 'Risk_Grade', label: '风险等级', valueType: 'STRING' }

test('旧等级赋值只迁移已有ID，显式清空后重载不会恢复旧目标或默认riskLevel', () => {
  const thresholds = [{ resultVar: 'Risk_Grade', _varId: grade.refId, _refType: 'VARIABLE', min: 0, max: 100, result: '低风险' }]
  expect(normalizeScorecardLevelVar({ thresholds }).operand).toMatchObject({ refId: grade.refId, refType: 'VARIABLE', code: 'Risk_Grade' })
  expect(normalizeScorecardLevelVar({ thresholds, levelVar: null })).toMatchObject({ operand: null, varCode: '', _varId: null })
  expect(normalizeScorecardLevelVar({ thresholds: [{ min: 0, max: 100, result: '低风险' }] })).toMatchObject({ operand: null, varCode: '' })
})

test.each(['SCORE', 'SCORE_ADV'])('%s 版本对比识别等级字段ID变更，旧模型没有多余空配置行', modelType => {
  const original = buildRuleVersionDiff({ modelType, leftModelJson: {}, rightModelJson: {} })
  expect(original.sections.find(section => section.key === 'settings').lanes[0].fields.some(field => field.key === 'levelVar')).toBe(false)
  const result = buildRuleVersionDiff({ modelType,
    leftModelJson: { levelVar: { operand: { ...grade, refId: '21' } } },
    rightModelJson: { levelVar: { operand: { ...grade, refId: '22' } } },
  })
  const field = result.sections.find(section => section.key === 'settings').lanes[0].fields.find(field => field.key === 'levelVar')
  expect(field.leftText).toContain('VARIABLE #21')
  expect(field.rightText).toContain('VARIABLE #22')
  expect(result.summary.modified).toBeGreaterThan(0)
})

test.each([Scorecard, AdvancedScorecard])('$name 在基础配置选择或清空等级赋值字段，区间保持独立可选', async component => {
  const wrapper = shallowMount({ ...component, methods: { ...component.methods, loadContent() {} } }, {
    mocks: { $route: { params: {} }, $router: { back() {} } },
  })
  try {
    await flushPromises()
    const picker = wrapper.findComponent('[data-testid="scorecard-level-target"]')
    expect(picker.exists()).toBe(true)
    expect(picker.props('writableOnly')).toBe(true)
    picker.vm.$emit('input', grade)
    await flushPromises()
    expect(JSON.parse(wrapper.vm.serializeDesignerDraft()).levelVar).toMatchObject({ operand: grade, _varId: grade.refId, _refType: 'VARIABLE' })
    expect(wrapper.vm.collectSelectedVarItems()).toContainEqual(expect.objectContaining({ _varId: grade.refId }))
    wrapper.vm.addThreshold()
    picker.vm.$emit('input', null)
    await flushPromises()
    const saved = JSON.parse(wrapper.vm.serializeDesignerDraft())
    expect(saved.levelVar).toMatchObject({ operand: null, _varId: null, varCode: '' })
    expect(saved.thresholds).toHaveLength(1)
    expect(wrapper.vm.collectSelectedVarItems().some(item => item._varId === grade.refId)).toBe(false)
  } finally { wrapper.unmount() }
})
