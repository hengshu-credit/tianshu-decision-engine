import { createRouter, createWebHashHistory } from 'vue-router'
import axios from 'axios'
import Layout from '@/layout/index.vue'
import { createConsoleAuthGuard } from './authGuard'

/**
 * 与后端会话 Cookie 配合的裸 axios，避免与 response 封装循环依赖。
 */
const authAxios = axios.create({
  baseURL: '/api',
  timeout: 15000,
  withCredentials: true
})

/**
 * 只缓存成功的登录配置；后端短暂不可用后允许再次登录重试。
 */
var _consoleConfigPromise = null

export function fetchConsoleConfig() {
  if (!_consoleConfigPromise) {
    _consoleConfigPromise = authAxios.get('/auth/console/config')
      .then(function (res) {
        if (res.data?.code !== 200 || !res.data.data) _consoleConfigPromise = null
        return res.data
      })
      .catch(function () { _consoleConfigPromise = null; return null })
  }
  return _consoleConfigPromise
}

function fetchMe() {
  return authAxios.get('/auth/console/me')
    .then(function (res) { return res.data })
    .catch(function () { return null })
}

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'DashboardHome',
        component: () => import('@/views/dashboard/DashboardHome.vue'),
        meta: { title: '数据看板' }
      },
      {
        path: 'dashboard/settings',
        name: 'DashboardSettings',
        component: () => import('@/views/dashboard/DashboardSettings.vue'),
        meta: { title: '数据看板设置', menu: '/dashboard' }
      },
      {
        path: 'project',
        name: 'ProjectList',
        component: () => import('@/views/project/ProjectList.vue'),
        meta: { title: '项目管理' }
      },
      {
        path: 'rule',
        name: 'RuleList',
        component: () => import('@/views/rule/RuleList.vue'),
        meta: { title: '规则管理' }
      },
      {
        path: 'rule/:id',
        name: 'RuleDetail',
        component: () => import('@/views/rule/RuleDetail.vue'),
        meta: { title: '规则详情' }
      },
      {
        path: 'project/:id',
        name: 'ProjectDetail',
        component: () => import('@/views/project/ProjectDetail.vue'),
        meta: { title: '项目详情' }
      },
      {
        path: 'designer/table/:id',
        name: 'DecisionTable',
        component: () => import('@/views/designer/DecisionTable.vue'),
        meta: { title: '决策表设计器', keepAlive: true }
      },
      {
        path: 'designer/tree/:id',
        name: 'DecisionTree',
        component: () => import('@/views/designer/DecisionTree.vue'),
        meta: { title: '决策树设计器', keepAlive: true }
      },
      {
        path: 'designer/flow/:id',
        name: 'DecisionFlow',
        component: () => import('@/views/designer/DecisionFlow.vue'),
        meta: { title: '决策流设计器', keepAlive: true }
      },
      {
        path: 'designer/ruleset/:id',
        name: 'RuleSet',
        component: () => import('@/views/designer/RuleSet.vue'),
        meta: { title: '规则集设计器', keepAlive: true }
      },
      {
        path: 'designer/cross/:id',
        name: 'CrossTable',
        component: () => import('@/views/designer/CrossTable.vue'),
        meta: { title: '交叉表设计器', keepAlive: true }
      },
      {
        path: 'designer/score/:id',
        name: 'Scorecard',
        component: () => import('@/views/designer/Scorecard.vue'),
        meta: { title: '评分卡设计器', keepAlive: true }
      },
      {
        path: 'designer/cross-adv/:id',
        name: 'AdvancedCrossTable',
        component: () => import('@/views/designer/AdvancedCrossTable.vue'),
        meta: { title: '复杂交叉表设计器', keepAlive: true }
      },
      {
        path: 'designer/score-adv/:id',
        name: 'AdvancedScorecard',
        component: () => import('@/views/designer/AdvancedScorecard.vue'),
        meta: { title: '复杂评分卡设计器', keepAlive: true }
      },
      {
        path: 'designer/script/:id',
        name: 'ScriptEditor',
        component: () => import('@/views/designer/ScriptEditor.vue'),
        meta: { title: 'QL脚本编辑器', keepAlive: true }
      },
      {
        path: 'designer/expression/:ruleId/:sessionId',
        name: 'ExpressionEditor',
        component: () => import('@/views/expression/ExpressionEditorPage.vue'),
        meta: { title: '配置表达式', keepAlive: true, menu: '/rule' }
      },
      {
        path: 'variable',
        name: 'VariableList',
        component: () => import('@/views/variable/VariableList.vue'),
        meta: { title: '变量管理' }
      },
      {
        path: 'list',
        name: 'ListLibrary',
        component: () => import('@/views/ruleList/ListLibrary.vue'),
        meta: { title: '名单管理' }
      },
      {
        path: 'list/:id',
        name: 'ListDetail',
        component: () => import('@/views/ruleList/ListDetail.vue'),
        meta: { title: '名单详情' }
      },
      {
        path: 'datasource',
        name: 'DatasourceList',
        component: () => import('@/views/datasource/DatasourceList.vue'),
        meta: { title: '外数管理' }
      },
      {
        path: 'datasource/source/new',
        name: 'DatasourceCreate',
        component: () => import('@/views/datasource/DatasourceDetail.vue'),
        meta: { title: '新建外数数据源' }
      },
      {
        path: 'datasource/source/:id',
        name: 'DatasourceDetail',
        component: () => import('@/views/datasource/DatasourceDetail.vue'),
        meta: { title: '外数数据源详情' }
      },
      {
        path: 'datasource/api/new',
        name: 'ApiCreate',
        component: () => import('@/views/datasource/ApiDetail.vue'),
        meta: { title: '新建外数 API' }
      },
      {
        path: 'datasource/api/:id',
        name: 'ApiDetail',
        component: () => import('@/views/datasource/ApiDetail.vue'),
        meta: { title: '外数 API 详情' }
      },
      {
        path: 'database',
        name: 'DatabaseList',
        component: () => import('@/views/database/DatabaseList.vue'),
        meta: { title: '数据库管理' }
      },
      {
        path: 'database/new',
        name: 'DatabaseCreate',
        component: () => import('@/views/database/DatabaseDetail.vue'),
        meta: { title: '新建数据库数据源' }
      },
      {
        path: 'database/:id',
        name: 'DatabaseDetail',
        component: () => import('@/views/database/DatabaseDetail.vue'),
        meta: { title: '数据库数据源详情' }
      },
      {
        path: 'model',
        name: 'ModelList',
        component: () => import('@/views/model/ModelList.vue'),
        meta: { title: '模型管理' }
      },
      {
        path: 'model/:id',
        name: 'ModelDetail',
        component: () => import('@/views/model/ModelDetail.vue'),
        meta: { title: '模型详情' }
      },
      {
        path: 'function',
        name: 'FunctionList',
        component: () => import('@/views/function/FunctionList.vue'),
        meta: { title: '函数管理' }
      },
      {
        path: 'test',
        name: 'RuleTest',
        component: () => import('@/views/test/RuleTest.vue'),
        meta: { title: '规则测试' }
      },
      {
        path: 'lineage',
        name: 'LineageGraph',
        component: () => import('@/views/lineage/LineageGraph.vue'),
        meta: { title: '血缘分析' }
      },
      {
        path: 'experiment',
        name: 'ExperimentList',
        component: () => import('@/views/experiment/ExperimentList.vue'),
        meta: { title: '分流实验' }
      },
      {
        path: 'experiment/new',
        name: 'ExperimentCreate',
        component: () => import('@/views/experiment/ExperimentDetail.vue'),
        meta: { title: '新建分流实验' }
      },
      {
        path: 'experiment/detail/:experimentId',
        name: 'ExperimentDetail',
        component: () => import('@/views/experiment/ExperimentDetail.vue'),
        meta: { title: '分流实验详情' }
      },
      {
        path: 'log',
        name: 'ExecutionLog',
        component: () => import('@/views/log/ExecutionLog.vue'),
        meta: { title: '执行日志' }
      },
      {
        path: 'billing',
        name: 'BillingList',
        component: () => import('@/views/billing/BillingList.vue'),
        meta: { title: '账单管理' }
      },
      {
        path: 'approval',
        name: 'ApprovalList',
        component: () => import('@/views/approval/ApprovalList.vue'),
        meta: { title: '审批管理', permission: 'approval:view' }
      },
      {
        path: 'approval/:id',
        name: 'ApprovalDetail',
        component: () => import('@/views/approval/ApprovalDetail.vue'),
        meta: { title: '审批详情', permission: 'approval:view' }
      },
      {
        path: 'account',
        name: 'AccountManagement',
        component: () => import('@/views/account/AccountManagement.vue'),
        meta: { title: '账户管理', permission: 'account:view' }
      }
    ]
  }
]

function inferRoutePermission(path) {
  const value = String(path || '')
  if (value.startsWith('project') || value.startsWith('billing')) return 'project:view'
  if (value.startsWith('rule') || value.startsWith('designer') ||
      value.startsWith('test') || value.startsWith('log')) return 'rule:view'
  if (value.startsWith('variable') || value.startsWith('list')) return 'field:view'
  if (value.startsWith('datasource')) return 'datasource:view'
  if (value.startsWith('database')) return 'database:view'
  if (value.startsWith('model')) return 'model:view'
  if (value.startsWith('function')) return 'function:view'
  if (value.startsWith('lineage') || value.startsWith('approval')) return 'approval:view'
  if (value.startsWith('experiment')) return 'experiment:view'
  if (value.startsWith('account')) return 'account:view'
  return null
}

routes.forEach(route => {
  ;(route.children || []).forEach(child => {
    if (!child.meta) child.meta = {}
    if (!child.meta.permission) {
      child.meta.permission = inferRoutePermission(child.path)
    }
  })
})

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

/**
 * 若后端启用控制台登录，则每次进入受保护路由都校验会话；
 * 未启用时与改造前行为一致。
 */
router.beforeEach(createConsoleAuthGuard(fetchConsoleConfig, fetchMe))

export default router
