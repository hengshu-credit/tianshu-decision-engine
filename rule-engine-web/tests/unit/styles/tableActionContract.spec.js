import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { parse as parseSfc } from '@vue/compiler-sfc'
import { baseParse, NodeTypes } from '@vue/compiler-dom'

const currentDir = path.dirname(fileURLToPath(import.meta.url))
const uiRoot = path.resolve(currentDir, '../../..')
const targetViews = [
  'src/views/account/AccountManagement.vue',
  'src/views/billing/BillingList.vue',
  'src/views/experiment/ExperimentList.vue',
  'src/views/function/FunctionList.vue',
  'src/views/model/ModelList.vue',
  'src/views/database/DatabaseList.vue',
  'src/views/datasource/DatasourceList.vue',
  'src/views/ruleList/ListLibrary.vue',
  'src/views/variable/VariableList.vue',
  'src/views/rule/RuleList.vue',
  'src/views/project/ProjectList.vue',
]
const expectedActionByLabel = {
  API: 'docs',
  OpenAPI: 'openapi',
  下线: 'disable',
  详情: 'detail',
  删除: 'delete',
  加接口: 'add',
  回滚: 'rollback',
  对比: 'compare',
  查询: 'inspect',
  查看: 'inspect',
  权限调整: 'configure',
  测试: 'execute',
  版本: 'version',
  移除: 'delete',
  编辑: 'edit',
  转为全局: 'global',
  进入: 'detail',
  选项: 'configure',
  重置密码: 'reset',
  配置权限: 'configure',
  鉴权: 'configure',
  验证执行: 'execute',
}
const supportedActions = new Set([
  'edit',
  'detail',
  'execute',
  'inspect',
  'configure',
  'version',
  'compare',
  'rollback',
  'global',
  'publish',
  'reset',
  'disable',
  'add',
  'docs',
  'openapi',
  'delete',
])

describe('管理列表操作按钮语义契约', () => {
  test.each(targetViews)('%s 的每个操作列按钮都声明统一操作语义', relativePath => {
    const columns = operationColumns(relativePath)

    expect(columns.length, relativePath).toBeGreaterThan(0)
    for (const buttons of columns) {
      expect(buttons.length, relativePath).toBeGreaterThan(0)
      for (const button of buttons) {
        expect(button.action, `${relativePath}: ${button.label || '动态按钮'}`)
          .toBeTruthy()
        for (const action of referencedActions(button.action)) {
          expect(supportedActions.has(action), `${relativePath}: ${action}`)
            .toBe(true)
        }
        const expectedAction = expectedActionByLabel[button.label]
        if (expectedAction) {
          expect(button.action, `${relativePath}: ${button.label}`)
            .toBe(expectedAction)
        }
      }
    }
  })

  test.each(targetViews)('%s 的同一操作列不复用相同颜色语义', relativePath => {
    for (const buttons of operationColumns(relativePath)) {
      const actions = buttons.map(button => button.action)
      expect(new Set(actions).size, relativePath).toBe(actions.length)
    }
  })
})

function operationColumns(relativePath) {
  const filename = path.join(uiRoot, relativePath)
  const source = fs.readFileSync(filename, 'utf8')
  const template = parseSfc(source, { filename }).descriptor.template?.content || ''
  const ast = baseParse(template)
  const columns = []

  walk(ast, node => {
    if (
      node.type === NodeTypes.ELEMENT &&
      node.tag === 'el-table-column' &&
      staticAttribute(node, 'label') === '操作'
    ) {
      const buttons = []
      walk(node, child => {
        if (child.type !== NodeTypes.ELEMENT || child.tag !== 'el-button') return
        buttons.push({
          action: actionAttribute(child),
          label: textContent(child),
        })
      })
      columns.push(buttons)
    }
  })
  return columns
}

function walk(node, visitor) {
  visitor(node)
  for (const child of node.children || []) walk(child, visitor)
}

function staticAttribute(node, name) {
  return node.props.find(prop => prop.type === NodeTypes.ATTRIBUTE && prop.name === name)
    ?.value?.content
}

function actionAttribute(node) {
  const attribute = node.props.find(prop => {
    if (prop.type === NodeTypes.ATTRIBUTE) return prop.name === 'data-action'
    return prop.type === NodeTypes.DIRECTIVE &&
      prop.name === 'bind' &&
      prop.arg?.type === NodeTypes.SIMPLE_EXPRESSION &&
      prop.arg.content === 'data-action'
  })
  if (!attribute) return ''
  return attribute.type === NodeTypes.ATTRIBUTE
    ? attribute.value?.content || ''
    : attribute.exp?.content || ''
}

function textContent(node) {
  return (node.children || [])
    .filter(child => child.type === NodeTypes.TEXT)
    .map(child => child.content)
    .join('')
    .replace(/\s+/g, '')
}

function referencedActions(value) {
  const dynamicActions = [...value.matchAll(/['"]([a-z]+)['"]/g)]
    .map(match => match[1])
  return dynamicActions.length > 0 ? dynamicActions : [value]
}
