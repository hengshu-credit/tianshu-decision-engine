import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = path.dirname(fileURLToPath(import.meta.url))
const projectRoot = path.resolve(currentDir, '../../..')

function source(relativePath) {
  return fs.readFileSync(path.join(projectRoot, relativePath), 'utf8')
}

test('分流实验响应式栅格不会继承 Element 列宽上限', () => {
  const experiment = source('src/views/experiment/ExperimentDetail.vue')
  expect(experiment).toMatch(
    /\.base-form :deep\(\.el-row > \[class\*='el-col-'\]\)\s*\{[\s\S]*?width:\s*100%;[\s\S]*?max-width:\s*none;/
  )
})

test('设计器图标按钮提供清晰的名称和用途提示', () => {
  const designerFiles = [
    'DecisionTable.vue',
    'DecisionTree.vue',
    'DecisionFlow.vue',
    'RuleSet.vue',
    'CrossTable.vue',
    'Scorecard.vue',
    'AdvancedCrossTable.vue',
    'AdvancedScorecard.vue',
    'ScriptEditor.vue',
  ]

  for (const file of designerFiles) {
    const designer = source(`src/views/designer/${file}`)
    expect(designer).toMatch(
      /:icon="ElIconBack"[\s\S]*?aria-label="返回"[\s\S]*?title="返回"/
    )
  }

  const tree = source('src/views/designer/DecisionTree.vue')
  const flow = source('src/views/designer/DecisionFlow.vue')
  for (const designer of [tree, flow]) {
    expect(designer).toContain('aria-label="放大画布"')
    expect(designer).toContain('aria-label="缩小画布"')
    expect(designer).toContain('aria-label="重置画布缩放"')
  }

  const cross = source('src/views/designer/CrossTable.vue')
  expect(cross).toContain('aria-label="删除此列"')
  expect(cross).toContain('aria-label="添加列"')
  expect(cross).toContain('aria-label="删除此行"')

  const scriptPanel = source('src/components/common/ScriptPanel.vue')
  expect(scriptPanel).toContain(
    ":aria-label=\"expanded ? '收起脚本面板' : '展开脚本面板'\""
  )
})

test('九类设计器统一提供唯一发布前检查与生命周期入口', () => {
  const scriptPanel = source('src/components/common/ScriptPanel.vue')
  const actionBar = source('src/components/rule/RuleDesignerActionBar.vue')

  expect(scriptPanel).toContain('由顶部“保存并检查”生成')
  expect(scriptPanel).not.toContain('保存并编译仅更新草稿')
  expect(scriptPanel).not.toMatch(/>\s*保存并编译\s*</)
  expect(actionBar).toContain('保存并检查')
  expect(actionBar).toContain('仅保存草稿')
  expect(actionBar).toContain('aria-label="前往规则生命周期审核发布"')

  const designers = [
    'ScriptEditor.vue',
    'DecisionTable.vue',
    'DecisionTree.vue',
    'DecisionFlow.vue',
    'RuleSet.vue',
    'CrossTable.vue',
    'Scorecard.vue',
    'AdvancedCrossTable.vue',
    'AdvancedScorecard.vue',
  ]
  for (const file of designers) {
    const designer = source(`src/views/designer/${file}`)
    expect(designer).toMatch(
      /<rule-designer-action-bar[\s\S]*?@save-check="handleCompile"[\s\S]*?@lifecycle="goRuleLifecycle"[\s\S]*?\/>/
    )
  }
})

test('滚动条在全局统一使用主题令牌和直角细线样式', () => {
  const scrollbars = source('src/styles/scrollbars.scss')
  expect(source('src/main.js')).toContain("import './styles/scrollbars.scss'")
  expect(scrollbars).toMatch(
    /:where\(\*\)\s*\{[\s\S]*?scrollbar-color:\s*var\(--tianshu-scrollbar-thumb-solid\)[\s\S]*?var\(--tianshu-scrollbar-track\);/
  )
  expect(scrollbars).toMatch(
    /::-webkit-scrollbar-thumb\s*\{[\s\S]*?background:\s*var\(--tianshu-scrollbar-thumb-vertical\);[\s\S]*?border-radius:\s*0;/
  )
  expect(scrollbars).toContain('--tianshu-scrollbar-size: 4px;')
  expect(scrollbars).toContain('.el-scrollbar__bar')
  expect(scrollbars).toContain('.monaco-scrollable-element > .scrollbar')
})

test('业务页面与共用组件不再使用低对比度的旧文字灰色', () => {
  const sourceRoot = path.join(projectRoot, 'src')
  const files = []
  const visit = (directory) => {
    for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
      const entryPath = path.join(directory, entry.name)
      if (entry.isDirectory()) visit(entryPath)
      else if (/\.(vue|scss)$/.test(entry.name)) files.push(entryPath)
    }
  }
  visit(sourceRoot)

  const legacyTextColors = files.flatMap((file) => {
    const matches =
      fs
        .readFileSync(file, 'utf8')
        .match(/color:\s*#(?:999|909399|bfbfbf|c0c4cc|585b70)\b/gi) || []
    return matches.map((value) => ({
      file: path.relative(projectRoot, file),
      value,
    }))
  })

  expect(legacyTextColors).toEqual([])
})

test('Element Plus 日期控件统一使用 Day.js 大写年份格式', () => {
  const sourceRoot = path.join(projectRoot, 'src')
  const files = []
  const visit = (directory) => {
    for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
      const entryPath = path.join(directory, entry.name)
      if (entry.isDirectory()) visit(entryPath)
      else if (entry.name.endsWith('.vue')) files.push(entryPath)
    }
  }
  visit(sourceRoot)

  const legacyFormats = files.flatMap((file) => {
    const matches =
      fs.readFileSync(file, 'utf8').match(/(?:value-)?format="yyyy-[^"]+"/g) ||
      []
    return matches.map((value) => ({
      file: path.relative(projectRoot, file),
      value,
    }))
  })

  expect(legacyFormats).toEqual([])
})

test('窄容器条件组会换行且不会裁掉右值和删除操作', () => {
  const editor = source('src/components/decision/ConditionGroupEditor.vue')
  expect(editor).toContain('container-type: inline-size')
  expect(editor).toContain('@container (max-width: 820px)')
  expect(editor).toContain('overflow-x: visible')
  expect(editor).toMatch(
    /grid-template-columns:\s*minmax\(220px,\s*1\.35fr\)\s+minmax\(96px,\s*120px\)\s+minmax\(340px,\s*1\.75fr\)\s+auto/
  )
})

test('表达式页面在业务桌面宽度下压缩资源区与检查器', () => {
  const palette = source('src/components/expression/ExpressionPalette.vue')
  const dialog = source('src/components/expression/ExpressionEditorDialog.vue')
  expect(palette).toContain('categoryWidth: 128')
  expect(palette).toContain('contentWidth: 300')
  expect(dialog).toContain('min-width: 300px')
  expect(dialog).toContain('flex: 0 0 260px')
  expect(dialog).toContain('@container (max-width: 900px)')
})

test('关键业务列表将操作列固定在右侧', () => {
  const files = [
    'src/views/project/ProjectList.vue',
    'src/views/project/ProjectDetail.vue',
    'src/views/rule/RuleList.vue',
    'src/views/variable/VariableList.vue',
    'src/views/model/ModelList.vue',
    'src/views/datasource/DatasourceList.vue',
    'src/views/database/DatabaseList.vue',
    'src/views/log/ExecutionLog.vue',
    'src/views/experiment/ExperimentList.vue',
    'src/views/billing/BillingList.vue',
  ]

  for (const file of files) {
    expect(source(file)).toMatch(
      /<el-table-column[^>]*label="操作"[^>]*fixed="right"/
    )
  }
})
