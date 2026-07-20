import { mount } from '@vue/test-utils'
import { buildRuleVersionDiff } from '@/utils/ruleVersionDiff'
import RuleListVisualDiff from '@/components/rule/versionDiff/RuleListVisualDiff.vue'
import RuleGraphVisualDiff from '@/components/rule/versionDiff/RuleGraphVisualDiff.vue'
import RuleMatrixVisualDiff from '@/components/rule/versionDiff/RuleMatrixVisualDiff.vue'
import RuleScoreVisualDiff from '@/components/rule/versionDiff/RuleScoreVisualDiff.vue'

function sections(modelType, leftModel, rightModel = leftModel) {
  return buildRuleVersionDiff({
    modelType,
    leftModelJson: leftModel,
    rightModelJson: rightModel
  }).sections
}

describe('rule version visual renderers', () => {
  test.each([
    ['TABLE', { hitPolicy: 'FIRST', rules: [] }, '规则', '命中策略'],
    ['RULE_SET', { executionMode: 'SERIAL', rules: [] }, '业务规则', '执行模式']
  ])('%s 使用规则卡片视图', (modelType, model, sectionTitle, fieldLabel) => {
    const wrapper = mount(RuleListVisualDiff, {
      propsData: { modelType, sections: sections(modelType, model) }
    })

    expect(wrapper.classes()).toContain('rule-list-visual-diff')
    expect(wrapper.text()).toContain(sectionTitle)
    expect(wrapper.text()).toContain(fieldLabel)
    expect(wrapper.find('.rule-diff-lane').exists()).toBe(true)
  })

  test.each([
    ['TREE', '节点'],
    ['FLOW', '流程节点']
  ])('%s 使用带连接线的节点步骤视图', (modelType, sectionTitle) => {
    const model = {
      nodes: [{ id: 'start', type: 'start-event', name: '开始' }],
      edges: []
    }
    const wrapper = mount(RuleGraphVisualDiff, {
      propsData: { modelType, sections: sections(modelType, model) }
    })

    expect(wrapper.classes()).toContain('rule-graph-visual-diff')
    expect(wrapper.text()).toContain(sectionTitle)
    expect(wrapper.find('.rule-graph-step').exists()).toBe(true)
    expect(wrapper.text()).toContain('开始')
  })

  test.each([
    ['CROSS', { rowHeaders: ['18岁以上'], colHeaders: ['高收入'], cells: [['PASS']] }, '结果矩阵'],
    ['CROSS_ADV', { rowDimensions: [], colDimensions: [], cells: [['PASS']] }, '结果矩阵']
  ])('%s 使用业务矩阵视图', (modelType, model, title) => {
    const wrapper = mount(RuleMatrixVisualDiff, {
      propsData: { modelType, sections: sections(modelType, model) }
    })

    expect(wrapper.classes()).toContain('rule-matrix-visual-diff')
    expect(wrapper.text()).toContain(title)
    expect(wrapper.find('.rule-matrix-board').exists()).toBe(true)
    expect(wrapper.text()).toContain('PASS')
  })

  test.each([
    ['SCORE', { initialScore: 0, scoreItems: [{ id: 's1', varCode: 'age', _varId: 1, operator: '>=', value: 18, score: 10 }], thresholds: [] }, '评分项'],
    ['SCORE_ADV', { initialScore: 100, dimensionGroups: [{ id: 'g1', groupLabel: '身份风险', weight: 1, dimensions: [] }], thresholds: [] }, '维度组']
  ])('%s 使用评分层级视图', (modelType, model, title) => {
    const wrapper = mount(RuleScoreVisualDiff, {
      propsData: { modelType, sections: sections(modelType, model) }
    })

    expect(wrapper.classes()).toContain('rule-score-visual-diff')
    expect(wrapper.text()).toContain(title)
    expect(wrapper.find('.rule-score-sheet').exists()).toBe(true)
    expect(wrapper.find('.rule-diff-lane').exists()).toBe(true)
  })

  test('规则中间新增后可视化仍按三个共享行展示', () => {
    const left = { hitPolicy: 'FIRST', rules: [{ id: 'r1' }, { id: 'r2' }] }
    const right = { hitPolicy: 'FIRST', rules: [{ id: 'r1' }, { id: 'new' }, { id: 'r2' }] }
    const wrapper = mount(RuleListVisualDiff, {
      propsData: { modelType: 'TABLE', sections: sections('TABLE', left, right) }
    })
    const ruleSection = wrapper.find('[data-section="rules"]')

    expect(ruleSection.findAll(':scope > .rule-visual-lanes > .rule-condition-diff > .rule-diff-lane')).toHaveLength(3)
    expect(ruleSection.text()).toContain('+ 新增')
  })
})
