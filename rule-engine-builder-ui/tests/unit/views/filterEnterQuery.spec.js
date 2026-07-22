import fs from 'fs'
import path from 'path'

const srcRoot = path.resolve(__dirname, '../../../src')

function readSource(relativePath) {
  return fs.readFileSync(path.join(srcRoot, relativePath), 'utf8')
}

function countOccurrences(source, fragment) {
  return source.split(fragment).length - 1
}

const expectedBindings = [
  ['views/project/ProjectList.vue', '@keyup.enter="handleQuery"', 1],
  ['views/project/ProjectDetail.vue', '@keyup.enter="handleQuery"', 1],
  ['views/rule/RuleList.vue', '@keyup.enter="handleQuery"', 1],
  ['views/variable/VariableList.vue', '@keyup.enter="handleQuery"', 1],
  ['views/variable/VariableList.vue', '@keyup.enter="onObjFilterChange"', 1],
  ['views/variable/VariableList.vue', '@keyup.enter="handleConstQuery"', 1],
  ['views/variable/VariableList.vue', '@keyup.enter="handleFieldValidationQuery"', 1],
  ['views/model/ModelList.vue', '@keyup.enter="handleQuery"', 1],
  ['views/function/FunctionList.vue', '@keyup.enter="handleQuery"', 1],
  ['views/database/DatabaseList.vue', '@keyup.enter="handleQuery"', 1],
  ['views/datasource/DatasourceList.vue', '@keyup.enter="handleDatasourceQuery"', 1],
  ['views/datasource/DatasourceList.vue', '@keyup.enter="handleApiQuery"', 1],
  ['views/billing/BillingList.vue', '@keyup.enter="handleConfigQuery"', 1],
  ['views/billing/BillingList.vue', '@keyup.enter="handleRecordQuery"', 1],
  ['views/billing/BillingList.vue', '@keyup.enter="handleSummaryQuery"', 1],
  ['views/experiment/ExperimentList.vue', '@keyup.enter="handleQuery"', 1],
  ['views/ruleList/ListLibrary.vue', '@keyup.enter="handleQuery"', 1],
  ['views/ruleList/ListDetail.vue', '@keyup.enter="handleQuery"', 1],
  ['views/log/ExecutionLog.vue', '@keyup.enter="handleQuery"', 1],
  ['components/common/ModuleCallLog.vue', '@keyup.enter="handleQuery"', 1]
]

const expectedBindingTotals = {
  'views/project/ProjectList.vue': 1,
  'views/project/ProjectDetail.vue': 1,
  'views/rule/RuleList.vue': 1,
  'views/variable/VariableList.vue': 4,
  'views/model/ModelList.vue': 1,
  'views/function/FunctionList.vue': 1,
  'views/database/DatabaseList.vue': 1,
  'views/datasource/DatasourceList.vue': 2,
  'views/billing/BillingList.vue': 3,
  'views/experiment/ExperimentList.vue': 1,
  'views/ruleList/ListLibrary.vue': 1,
  'views/ruleList/ListDetail.vue': 1,
  'views/log/ExecutionLog.vue': 1,
  'components/common/ModuleCallLog.vue': 1
}

describe('页面顶部筛选栏回车查询契约', () => {
  test.each(expectedBindings)('%s 绑定 %s', (relativePath, binding, count) => {
    expect(countOccurrences(readSource(relativePath), binding)).toBe(count)
  })

  test.each(Object.entries(expectedBindingTotals))('%s 只绑定目标筛选栏', (relativePath, count) => {
    expect(countOccurrences(readSource(relativePath), '@keyup.enter')).toBe(count)
  })

  test('实验详情页二级日志筛选区不绑定回车查询', () => {
    expect(readSource('views/experiment/ExperimentDetail.vue')).not.toContain('@keyup.enter')
  })
})
