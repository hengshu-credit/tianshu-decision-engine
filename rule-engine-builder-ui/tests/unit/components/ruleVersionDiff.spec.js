import { mount } from '@vue/test-utils'
import * as monaco from 'monaco-editor'
import RuleVersionDiff from '@/components/rule/versionDiff/RuleVersionDiff.vue'

function version(version, modelJson, overrides = {}) {
  return {
    version,
    modelJson: JSON.stringify(modelJson),
    compiledScript: '',
    changeLog: version === 1 ? '初始版本' : '调整规则',
    publishBy: 'admin',
    publishTime: '2026-07-20T10:00:00',
    ...overrides
  }
}

function factory(modelType, leftModel, rightModel) {
  return mount(RuleVersionDiff, {
    propsData: {
      modelType,
      leftVersion: version(1, leftModel),
      rightVersion: version(2, rightModel)
    }
  })
}

describe('RuleVersionDiff', () => {
  test.each([
    ['TABLE', { hitPolicy: 'FIRST', rules: [] }, '.rule-list-visual-diff'],
    ['RULE_SET', { executionMode: 'SERIAL', rules: [] }, '.rule-list-visual-diff'],
    ['TREE', { nodes: [], edges: [] }, '.rule-graph-visual-diff'],
    ['FLOW', { nodes: [], edges: [] }, '.rule-graph-visual-diff'],
    ['CROSS', { rowHeaders: [], colHeaders: [], cells: [] }, '.rule-matrix-visual-diff'],
    ['CROSS_ADV', { rowDimensions: [], colDimensions: [], cells: [] }, '.rule-matrix-visual-diff'],
    ['SCORE', { scoreItems: [], thresholds: [] }, '.rule-score-visual-diff'],
    ['SCORE_ADV', { dimensionGroups: [], thresholds: [] }, '.rule-score-visual-diff']
  ])('%s 分发到对应的真实业务视图', (modelType, model, selector) => {
    const wrapper = factory(modelType, model, model)

    expect(wrapper.find(selector).exists()).toBe(true)
    expect(wrapper.find('.rule-version-side--left').text()).toContain('v1')
    expect(wrapper.find('.rule-version-side--right').text()).toContain('v2')
  })

  test('变化汇总显示修改新增删除数量', () => {
    const left = { rules: [{ id: 'r1', actions: [{ id: 'a1', value: 1 }] }, { id: 'old' }] }
    const right = { rules: [{ id: 'r1', actions: [{ id: 'a1', value: 2 }] }, { id: 'new' }] }
    const wrapper = factory('TABLE', left, right)

    const summary = wrapper.find('.rule-version-diff-summary').text()
    expect(summary).toContain('修改')
    expect(summary).toContain('新增')
    expect(summary).toContain('删除')
  })

  test('QL 脚本使用 Monaco Diff 并展示变量引用变化', async() => {
    const originalModel = { getValue: jest.fn(() => 'result = age;'), setValue: jest.fn(), dispose: jest.fn() }
    const modifiedModel = { getValue: jest.fn(() => 'result = age + 1;'), setValue: jest.fn(), dispose: jest.fn() }
    const diffEditor = { setModel: jest.fn(), layout: jest.fn(), dispose: jest.fn() }
    monaco.editor.createModel.mockReset().mockReturnValueOnce(originalModel).mockReturnValueOnce(modifiedModel)
    monaco.editor.createDiffEditor.mockReset().mockReturnValue(diffEditor)
    window.monaco = monaco

    const wrapper = factory(
      'SCRIPT',
      { script: 'result = age;', scriptVarRefs: [{ refCode: 'age', varId: 1 }] },
      { script: 'result = age + 1;', scriptVarRefs: [{ refCode: 'age', varId: 2 }] }
    )
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.monaco-diff-editor').exists()).toBe(true)
    expect(wrapper.find('.rule-script-ref-diff').text()).toContain('变量 ID')
    expect(wrapper.find('.rule-script-ref-diff').text()).toContain('1')
    expect(wrapper.find('.rule-script-ref-diff').text()).toContain('2')
    wrapper.destroy()
    delete window.monaco
  })

  test('无法解析的版本在对应侧显示错误', () => {
    const wrapper = mount(RuleVersionDiff, {
      propsData: {
        modelType: 'TABLE',
        leftVersion: version(1, {}, { modelJson: '{bad json' }),
        rightVersion: version(2, {})
      }
    })

    expect(wrapper.find('.rule-version-error--left').text()).toContain('版本内容无法解析')
  })
})
