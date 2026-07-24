const fs = require('fs')
const path = require('path')

const projectRoot = path.resolve(__dirname, '../../..')
const stylePath = path.join(projectRoot, 'src/styles/compact-workbench.scss')

function expectDeclaration(source, selector, declaration) {
  const escapedSelector = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  expect(source).toMatch(new RegExp(escapedSelector + '\\s*\\{[^}]*' + declaration.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')))
}

describe('compact workbench styles', () => {
  test('loads scoped balanced-density controls after Element Plus overrides', () => {
    const mainSource = fs.readFileSync(path.join(projectRoot, 'src/main.js'), 'utf8')

    expect(fs.existsSync(stylePath)).toBe(true)
    expect(mainSource).toContain("import './styles/compact-workbench.scss'")
    expect(mainSource.indexOf("import './styles/compact-workbench.scss'"))
      .toBeGreaterThan(mainSource.indexOf("import './styles/element-override.scss'"))

    const styles = fs.readFileSync(stylePath, 'utf8')
    expect(styles).toContain('.uiue-compact-workbench')
    expect(styles).toMatch(/\.el-input__inner\s*\{[\s\S]*?padding-left:\s*8px;[\s\S]*?padding-right:\s*8px;/)
    expect(styles).not.toMatch(/\.el-input__inner\s*\{\s*min-width:\s*0;/)
    expect(styles).toMatch(/\.el-select__wrapper\s*\{[\s\S]*?gap:\s*4px;[\s\S]*?padding-left:\s*8px;[\s\S]*?padding-right:\s*8px;/)
    expect(styles).not.toContain('.el-input--prefix .el-input__inner')
    expect(styles).not.toContain('.el-input--suffix .el-input__inner')
    expect(styles).toContain('.el-input-number .el-input__inner')
    expect(styles).toMatch(
      /\.el-input-number \.el-input__inner\s*\{[\s\S]*?padding-left:\s*0;[\s\S]*?padding-right:\s*0;[\s\S]*?color:\s*#0f172a;/
    )
    expect(styles).toContain('.uiue-list-page.uiue-compact-workbench')
  })

  test('scopes all rule designers, model detail and experiment pages', () => {
    const designerFiles = [
      'DecisionTable.vue',
      'DecisionTree.vue',
      'DecisionFlow.vue',
      'RuleSet.vue',
      'CrossTable.vue',
      'Scorecard.vue',
      'AdvancedCrossTable.vue',
      'AdvancedScorecard.vue',
      'ScriptEditor.vue'
    ]

    designerFiles.forEach(file => {
      const source = fs.readFileSync(path.join(projectRoot, 'src/views/designer', file), 'utf8')
      expect(source).toContain('uiue-compact-workbench')
      expect(source).toContain('uiue-compact-designer')
    })

    ;[
      'src/views/model/ModelDetail.vue',
      'src/views/experiment/ExperimentList.vue',
      'src/views/experiment/ExperimentDetail.vue'
    ].forEach(file => {
      const source = fs.readFileSync(path.join(projectRoot, file), 'utf8')
      expect(source).toContain('uiue-compact-workbench')
    })
  })

  test('uses balanced spacing on the four dense rule editors', () => {
    const decisionTable = fs.readFileSync(path.join(projectRoot, 'src/views/designer/DecisionTable.vue'), 'utf8')
    const scorecard = fs.readFileSync(path.join(projectRoot, 'src/views/designer/Scorecard.vue'), 'utf8')
    const crossTable = fs.readFileSync(path.join(projectRoot, 'src/views/designer/CrossTable.vue'), 'utf8')
    const ruleSet = fs.readFileSync(path.join(projectRoot, 'src/views/designer/RuleSet.vue'), 'utf8')

    expectDeclaration(decisionTable, '.dt-designer', 'padding: 16px;')
    expectDeclaration(decisionTable, '.dt-rule-card', 'padding: 10px 12px;')
    expectDeclaration(scorecard, '.sc-designer', 'padding: 16px;')
    expectDeclaration(scorecard, '.sc-card', 'padding: 12px 16px;')
    expectDeclaration(crossTable, '.ct-designer', 'padding: 16px;')
    expectDeclaration(crossTable, '.ct-dim-panel', 'padding: 12px;')
    expectDeclaration(ruleSet, '.rs-designer', 'padding: 16px;')
    expectDeclaration(ruleSet, '.rs-rule-card', 'padding: 10px;')
  })

  test('wraps the experiment base form instead of clipping fields on narrow screens', () => {
    const experimentDetail = fs.readFileSync(path.join(projectRoot, 'src/views/experiment/ExperimentDetail.vue'), 'utf8')

    expect(experimentDetail).toContain('@media (max-width: 1600px)')
    expect(experimentDetail).toContain('.base-form :deep(.el-row)')
    expect(experimentDetail).toContain('grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));')
    expect(experimentDetail).toContain(".base-form :deep(.el-row > [class*='el-col-'])")
  })

  test('field pickers flex to available space and comparison selectors stay compact', () => {
    const picker = fs.readFileSync(path.join(projectRoot, 'src/components/common/VarPicker.vue'), 'utf8')
    const conditionEditor = fs.readFileSync(path.join(projectRoot, 'src/components/decision/ConditionGroupEditor.vue'), 'utf8')
    const advancedCross = fs.readFileSync(path.join(projectRoot, 'src/views/designer/AdvancedCrossTable.vue'), 'utf8')

    expect(picker).toMatch(/\.var-picker-wrap\s*\{[\s\S]*?display:\s*flex;[\s\S]*?flex:\s*1 1 auto;[\s\S]*?min-width:\s*0;/)
    expect(picker).toMatch(/<el-input\s+ref="reference"\s+class="vp-reference"/)
    expect(picker).not.toMatch(/<div\s+ref="reference"\s+class="vp-reference"/)
    expect(picker).not.toContain('referenceStyle')
    expect(picker).not.toContain('--vp-prefix-offset')
    expect(picker).toMatch(/\.vp-reference :deep\(\.el-input__wrapper\)\s*\{[\s\S]*?padding-left:\s*4px;[\s\S]*?padding-right:\s*4px;/)
    expect(picker).toMatch(/\.el-input__prefix-inner > :last-child\)\s*\{[\s\S]*?margin-right:\s*4px;/)
    expect(picker).toContain('v-if="!value || !allowCustom"')
    expect(conditionEditor).toMatch(/\.cg-field--op\s*\{\s*width:\s*96px;\s*\}/)
    expect(advancedCross).toMatch(/\.seg-op\s*\{\s*flex:\s*0 0 96px;\s*width:\s*96px;\s*\}/)
  })

  test('decision table action pickers use stacked full-width rows with compact spacing', () => {
    const source = fs.readFileSync(
      path.join(projectRoot, 'src/views/designer/DecisionTable.vue'),
      'utf8'
    )

    expect(source.match(/class="dt-act-operand/g)).toHaveLength(2)
    expect(source).toContain(
      `:title="operandDisplay(act.targetOperand) || ''"`
    )
    expect(source).toContain(`:title="operandDisplay(act.valueOperand) || ''"`)
    expect(source).toMatch(
      /\.dt-act-field\s*\{[\s\S]*?padding:\s*8px;/
    )
    expect(source).toMatch(
      /\.dt-act-body\s*\{[\s\S]*?display:\s*grid;[\s\S]*?grid-template-columns:\s*minmax\(0,\s*1fr\);[\s\S]*?gap:\s*4px;/
    )
    expect(source).toMatch(
      /\.dt-act-operand\s*\{[\s\S]*?width:\s*100%;[\s\S]*?min-width:\s*0;/
    )
    expect(source).toMatch(
      /\.dt-act-operand\s+:deep\(\.el-input__inner\)\s*\{[\s\S]*?overflow:\s*hidden;[\s\S]*?text-overflow:\s*ellipsis;[\s\S]*?white-space:\s*nowrap;/
    )
  })
})
