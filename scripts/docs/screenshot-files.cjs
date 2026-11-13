const fs = require('node:fs')
const path = require('node:path')

const root = path.resolve(__dirname, '../..')
const screenshotRoot = path.join(root, 'docs/project-usage')
const screenshotViewport = { width: 2560, height: 1440 }
const captureNames = {
  login: 'project-usage-01-login',
  project: 'project-usage-02-project-list',
  rules: 'project-usage-02-rule-list',
  variable: 'project-usage-03-variable',
  'data-object': 'project-usage-03-data-object',
  functions: 'project-usage-04-function',
  logs: 'project-usage-06-execution-log',
  'project-detail': 'project-usage-07-project-detail',
  'rule-detail': 'project-usage-08-rule-detail',
  lists: 'project-usage-09-list',
  datasource: 'project-usage-10-datasource',
  'api-list': 'project-usage-10-api-list',
  'external-call-trace': 'project-usage-10-external-call-trace',
  database: 'project-usage-11-database',
  models: 'project-usage-13-model',
  lineage: 'project-usage-14-lineage',
  experiment: 'project-usage-15-experiment',
  'experiment-trace': 'project-usage-15-experiment-trace',
  billing: 'project-usage-16-billing',
  dashboard: 'project-usage-18-dashboard',
  'theme-light': 'project-usage-19-theme-presets',
  'theme-dark': 'project-usage-19-theme-dark',
  'theme-custom': 'project-usage-19-theme-custom',
  'theme-top': 'project-usage-19-theme-top',
  approval: 'project-usage-21-approval',
  transfer: 'project-usage-22-transfer',
  account: 'project-usage-23-account'
}

function captureFileName(name) {
  const designer = /^rule-(.+)-config$/.exec(name)
  return `${designer ? `project-usage-designer-${designer[1]}` : captureNames[name] || `project-usage-${name}`}.png`
}

// 只写入文档实际引用的截图，避免重复运行后重新产生已清理的旧图。
function referencedScreenshots() {
  const names = new Set()
  const read = file => {
    const content = fs.readFileSync(file, 'utf8')
    for (const match of content.matchAll(/project-usage\/([^\s"'()<>/]+\.png)/g)) names.add(match[1])
  }
  const visit = directory => {
    for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
      const file = path.join(directory, entry.name)
      if (entry.isDirectory()) visit(file)
      else if (/\.(md|html)$/.test(entry.name)) read(file)
    }
  }
  read(path.join(root, 'README.md'))
  visit(path.join(root, 'docs'))
  return new Set([...names].filter(name => name !== '*.png'))
}

module.exports = { screenshotRoot, screenshotViewport, captureFileName, referencedScreenshots }
