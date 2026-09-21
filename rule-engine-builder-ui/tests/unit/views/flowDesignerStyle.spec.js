const fs = require('fs')
const path = require('path')

const repoRoot = path.resolve(__dirname, '../../..')

function readSource(relativePath) {
  return fs.readFileSync(path.join(repoRoot, relativePath), 'utf8')
}

describe('flow designer style regressions', () => {
  test('执行动作节点的背景、边框和文字跟随当前主题', () => {
    const source = readSource('src/components/flow/nodes.js')

    expect(source).toContain("fill: 'var(--tianshu-flow-node-bg)'")
    expect(source).toContain("stroke: 'var(--tianshu-flow-node-border)'")
    expect(source).toContain("fill: 'var(--tianshu-flow-node-text)'")
  })

  test('决策树和决策流工具栏主按钮在深色栏内保持可读', () => {
    const tree = readSource('src/views/designer/DecisionTree.vue')
    const flow = readSource('src/views/designer/DecisionFlow.vue')

    ;[tree, flow].forEach(source => {
      expect(source).toContain('&.el-button--primary {')
      expect(source).toContain('background: var(--tianshu-brand-background);')
      expect(source).toContain('color: var(--tianshu-brand-foreground) !important;')
      expect(source).toContain('border-color: var(--el-color-primary) !important;')
      expect(source).toContain('min-width: 88px;')
      expect(source).toContain('&.el-button--primary:hover,')
      expect(source).toContain('&.el-button--primary:focus {')
      expect(source).toContain('filter: brightness(1.08);')
      expect(source).toContain('&.el-button--primary:active {')
    })
  })

  test('属性面板的模式切换选中态使用主题色', () => {
    const tree = readSource('src/views/designer/DecisionTree.vue')
    const flow = readSource('src/views/designer/DecisionFlow.vue')

    ;[tree, flow].forEach(source => {
      expect(source).toContain('.el-radio-button__orig-radio:checked + .el-radio-button__inner')
      expect(source).toContain('background: var(--tianshu-brand-background);')
      expect(source).toContain('color: var(--tianshu-brand-foreground);')
    })
  })

  test('脚本面板状态栏按钮使用随日夜和自定义主题适配的可读配色', () => {
    const source = readSource('src/components/common/ScriptPanel.vue')

    expect(source).toContain('.sp-statusbar :deep(.el-button)')
    expect(source).toContain('background: var(--tianshu-info-bg);')
    expect(source).toContain('color: var(--tianshu-info-text);')
    expect(source).toContain('border-color: var(--tianshu-info-border);')
  })

  test('决策树和决策流属性面板最多占页面百分之八十', () => {
    const tree = readSource('src/views/designer/DecisionTree.vue')
    const flow = readSource('src/views/designer/DecisionFlow.vue')

    ;[tree, flow].forEach(source => {
      expect(source).toMatch(/clampDesignerPanelWidth\(\s*width,\s*window\.innerWidth\s*\)/)
      expect(source).toContain('max-width: 80vw;')
      expect(source).toContain('@media (max-width: 1200px)')
      expect(source).toContain('max-width: 60%;')
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
      expect(source).toContain('mixins: [varPickerMixin, ruleCallMixin')
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
      expect(source).toMatch(/backendNode\.terminationScope\s*=\s*normalizeEndScope\(\s*props\.terminationScope\s*\)/)
    })

    const flow = readSource('src/views/designer/DecisionFlow.vue')
    expect(flow).not.toContain("errors.push('缺少结束节点')")
  })

  test('决策流结束节点说明显示在节点属性而不是连线属性中', () => {
    const source = readSource('src/views/designer/DecisionFlow.vue')
    const edgeTemplateIndex = source.indexOf('<template v-if="isEdge">')
    const nodeTemplateIndex = source.indexOf('<template v-else>', edgeTemplateIndex)
    const alertIndex = source.indexOf("activeElement.type === 'end-event'")
    const conditionNodeIndex = source.indexOf("activeElement.type === 'exclusive-gateway'", nodeTemplateIndex)

    expect(alertIndex).toBeGreaterThan(nodeTemplateIndex)
    expect(alertIndex).toBeLessThan(conditionNodeIndex)
  })

  test('决策树和决策流使用 LogicFlow 2 官方插件与主题色图样式', () => {
    const packageJson = JSON.parse(readSource('package.json'))
    const tree = readSource('src/views/designer/DecisionTree.vue')
    const flow = readSource('src/views/designer/DecisionFlow.vue')

    expect(packageJson.dependencies['@logicflow/core']).toBe('2.2.4')
    expect(packageJson.dependencies['@logicflow/extension']).toBe('2.3.0')
    expect(packageJson.dependencies['@logicflow/layout']).toBe('2.1.4')
    ;[tree, flow].forEach(source => {
      expect(source).toMatch(/plugins:\s*\[\s*SelectionSelect,\s*Menu,\s*Snapshot,\s*DynamicGroup,\s*MiniMap,\s*Dagre,?\s*\]/)
      expect(source).toContain("this.lf.on('anchor:click'")
      expect(source).toContain('cascadeDeleteChildren: false')
      expect(source).toContain('disallowEdgeConnectToGroup: true')
      expect(source).toContain("stroke: FLOW_THEME_COLOR")
      expect(source).toContain("fill: FLOW_THEME_COLOR")
      expect(source).toContain('选区')
      expect(source).toContain('分组')
      expect(source).toContain('一键美化')
      expect(source).toContain('小地图')
      expect(source).toContain("'is-node-active': activeElement && activeElement.baseType === 'node'")
      expect(source).toContain('.is-node-active :deep(.lf-mini-map)')
      expect(source).toContain('getPersistableGraphData(this.lf)')
      expect(source).toContain('getBusinessGraphData(canvasGraph)')
    })
  })
})
