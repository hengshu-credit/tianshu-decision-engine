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
})
