const fs = require('fs')
const path = require('path')

const repoRoot = path.resolve(__dirname, '../../..')

function readSource(relativePath) {
  return fs.readFileSync(path.join(repoRoot, relativePath), 'utf8')
}

describe('flow designer style regressions', () => {
  test('执行动作节点使用浅色主题背景和深色文字', () => {
    const source = readSource('src/components/flow/nodes.js')

    expect(source).toContain("fill: '#EAF2FF'")
    expect(source).toContain("stroke: '#2F54EB'")
    expect(source).toContain("fill: '#1D39C4'")
  })

  test('决策树和决策流工具栏主按钮在深色栏内保持可读', () => {
    const tree = readSource('src/views/designer/DecisionTree.vue')
    const flow = readSource('src/views/designer/DecisionFlow.vue')

    ;[tree, flow].forEach(source => {
      expect(source).toContain('&.el-button--primary {')
      expect(source).toContain('background: #FFFFFF;')
      expect(source).toContain('color: #1D39C4;')
      expect(source).toContain('min-width: 88px;')
    })
  })

  test('属性面板和脚本面板的模式切换选中态使用主题色', () => {
    const tree = readSource('src/views/designer/DecisionTree.vue')
    const flow = readSource('src/views/designer/DecisionFlow.vue')
    const scriptPanel = readSource('src/components/common/ScriptPanel.vue')

    ;[tree, flow, scriptPanel].forEach(source => {
      expect(source).toContain('.el-radio-button__orig-radio:checked + .el-radio-button__inner')
      expect(source).toContain('background: #2639E9;')
      expect(source).toContain('color: #FFFFFF;')
    })
  })

  test('脚本面板深色状态栏按钮使用亮底深字', () => {
    const source = readSource('src/components/common/ScriptPanel.vue')

    expect(source).toContain('.sp-statusbar ::v-deep .el-button')
    expect(source).toContain('background: #F8FAFF;')
    expect(source).toContain('color: #1D39C4 !important;')
  })

  test('决策树和决策流属性面板最多占页面百分之八十', () => {
    const tree = readSource('src/views/designer/DecisionTree.vue')
    const flow = readSource('src/views/designer/DecisionFlow.vue')

    ;[tree, flow].forEach(source => {
      expect(source).toContain('clampDesignerPanelWidth(width, window.innerWidth)')
      expect(source).toContain('max-width: 80vw;')
    })
  })

  test('执行规则动作先选规则，再说明共享上下文并按需展开单字段映射', () => {
    const source = readSource('src/components/flow/ActionBlockEditor.vue')
    const selectorIndex = source.indexOf('<rule-execution-selector')
    const sharedContextIndex = source.indexOf('子规则全部输出会写入当前共享上下文')
    const mappingSwitchIndex = source.indexOf('额外映射单个输出字段')

    expect(selectorIndex).toBeGreaterThan(-1)
    expect(sharedContextIndex).toBeGreaterThan(selectorIndex)
    expect(mappingSwitchIndex).toBeGreaterThan(sharedContextIndex)
  })

  test('决策流、决策树和规则集统一接入规则调用上下文', () => {
    const files = ['DecisionFlow.vue', 'DecisionTree.vue', 'RuleSet.vue']

    files.forEach(file => {
      const source = readSource('src/views/designer/' + file)
      expect(source).toContain("import ruleCallMixin from '@/mixins/ruleCallMixin'")
      expect(source).toContain('mixins: [varPickerMixin, ruleCallMixin]')
      expect(source).toContain(':rules="projectRules"')
      expect(source).toContain(':current-rule-id="definitionId"')
      expect(source).toContain(':current-rule-code="currentRuleCode"')
      expect(source).toContain(':validate-rule-call-cycle="validateRuleCallCycle"')
      expect(source).toContain('loadRuleCallOptions(this.definitionId)')
    })
  })

  test('决策树和决策流添加结束节点时统一二次确认并持久化结束范围', () => {
    const files = ['DecisionFlow.vue', 'DecisionTree.vue']

    files.forEach(file => {
      const source = readSource('src/views/designer/' + file)
      expect(source).toContain('<end-node-scope-dialog')
      expect(source).toContain('@confirm="confirmEndNode"')
      expect(source).toContain("if (type === 'end-event')")
      expect(source).toContain('backendNode.terminationScope = normalizeEndScope(props.terminationScope)')
    })

    const flow = readSource('src/views/designer/DecisionFlow.vue')
    expect(flow).not.toContain("errors.push('缺少结束节点')")
  })
})
