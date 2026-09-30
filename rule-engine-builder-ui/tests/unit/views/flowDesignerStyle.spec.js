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

  test('决策树和决策流工具栏跟随主题渐变并保持操作按钮可读', () => {
    const tree = readSource('src/views/designer/DecisionTree.vue')
    const flow = readSource('src/views/designer/DecisionFlow.vue')

    ;[tree, flow].forEach(source => {
      expect(source).toContain('&.el-button--primary {')
      expect(source).toContain('background: var(--tianshu-brand-background);')
      expect(source).toContain('color: var(--tianshu-brand-foreground) !important;')
      expect(source).toContain('border-color: var(--tianshu-designer-toolbar-accent) !important;')
      expect(source).toContain('min-width: 88px;')
      expect(source).toContain('&.el-button--primary:hover,')
      expect(source).toContain('&.el-button--primary:focus {')
      expect(source).toContain('filter: brightness(1.08);')
      expect(source).toContain('&.el-button--primary:active {')
      expect(source).toContain('background: var(--tianshu-brand-background);')
      expect(source).toContain('color: var(--designer-toolbar-foreground) !important;')
      expect(source).toContain('border-color: var(--el-color-primary) !important;')
      expect(source).toContain('--designer-toolbar-control-background: color-mix(')
      expect(source).toContain('--designer-toolbar-control-border: color-mix(')
      expect(source).toContain('background: var(--designer-toolbar-control-hover-background) !important;')
      expect(source).toContain('box-shadow: 0 0 0 1px color-mix(in srgb, var(--tianshu-designer-toolbar-accent) 22%, transparent);')
      expect(source).toContain('&.is-tool-active:hover,')
      expect(source).toContain('box-shadow: 0 0 0 2px color-mix(in srgb, var(--tianshu-designer-toolbar-accent) 28%, transparent) !important;')
      expect(source).toContain(':deep(.toolbar-action:hover)')
      expect(source).toContain(':deep(.rule-designer-actions__button:not(.rule-designer-actions__publish))')
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

  test('动态分组的容器和标题使用主题语义变量', () => {
    const graph = readSource('src/components/flow/flowDesignerGraph.js')

    expect(graph).toContain("fill: 'var(--tianshu-designer-accent-bg)'")
    expect(graph).toContain("stroke: 'var(--tianshu-designer-accent-border)'")
    expect(graph).toContain("color: 'var(--tianshu-text-primary)'")
    expect(graph).toContain('style: { ...DYNAMIC_GROUP_STYLE }')
    expect(graph).toContain('textStyle: { ...DYNAMIC_GROUP_TEXT_STYLE }')
  })

  test('设计器节点引用按钮使用外层工具栏的主题变量', () => {
    const source = readSource('src/components/flow/GraphDesignerNavigator.vue')

    expect(source).toContain('var(--designer-toolbar-control-border, var(--tianshu-border))')
    expect(source).toContain('var(--designer-toolbar-control-background, var(--tianshu-bg-surface))')
    expect(source).toContain('var(--designer-toolbar-foreground, var(--tianshu-text-primary))')
    expect(source).toContain('border-color: var(--tianshu-designer-toolbar-accent, var(--el-color-primary));')
  })

  test('决策树和决策流工具栏拆成上下两行并提供网格、比例和统一控件', () => {
    ;['DecisionTree.vue', 'DecisionFlow.vue'].forEach(file => {
      const source = readSource('src/views/designer/' + file)
      expect(source).toContain('toolbar-row-primary')
      expect(source).toContain('toolbar-row-actions')
      expect(source).toContain('check-label="检查"')
      expect(source).toContain(':show-check="false"')
      expect(source).toContain('toolbar-check-button')
      expect(source).toContain('toolbar-node-button')
      expect(source).toContain('toolbar-edge-select')
      expect(source).toContain('gridVisible')
      expect(source).toContain('snapGridEnabled')
      expect(source).toContain('onZoomInputChange')
      expect(source).toContain('zoomPresets')
      expect(source).toContain('updateGridOptions')
    })

    const actionBar = readSource('src/components/rule/RuleDesignerActionBar.vue')
    expect(actionBar).toContain('rule-designer-actions__icon')
    expect(actionBar).toContain('DocumentChecked as ElIconDocumentChecked')
    expect(actionBar).toContain('Promotion as ElIconPromotion')
  })

  test('画布背景设置与选中边动画复用 LogicFlow 能力', () => {
    const canvas = readSource('src/components/flow/CanvasBackgroundSettings.vue')
    const graphCanvas = readSource('src/components/flow/graphCanvas.js')
    const tree = readSource('src/views/designer/DecisionTree.vue')
    const flow = readSource('src/views/designer/DecisionFlow.vue')

    ;[tree, flow].forEach(source => {
      expect(source).toContain('<canvas-background-settings')
      expect(source).toContain('edgeAnimationEnabled')
      expect(source).toContain('openEdgeAnimation')
      expect(source).toContain('closeEdgeAnimation')
      expect(source).toContain('updateBackgroundOptions')
    })
    expect(canvas).toContain('网格吸附')
    expect(canvas).toContain('网格类型')
    expect(canvas).toContain('背景透明度')
    expect(canvas).toContain('背景颜色')
    expect(graphCanvas).toContain('edgeAnimation:')
    expect(graphCanvas).toContain('backgroundColor:')
  })
})
