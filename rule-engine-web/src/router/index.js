import { PLATFORM_SUCCESS_CODE } from '@/utils/platformResponse'
import { createRouter, createWebHashHistory } from 'vue-router'
import axios from 'axios'
import { createConsoleAuthGuard } from './authGuard'
import { installNavigationFeedback, navigationState, preloadableView } from './navigationState'

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
var _consoleConfigFetchedAt = 0

export function fetchConsoleConfig() {
  if (Date.now() - _consoleConfigFetchedAt > 10000) _consoleConfigPromise = null
  if (!_consoleConfigPromise) {
    _consoleConfigPromise = authAxios.get('/auth/console/config')
      .then(function (res) {
        _consoleConfigFetchedAt = Date.now()
        if (res.data?.code !== PLATFORM_SUCCESS_CODE || !res.data.data || res.data.data.initializationRequired === true) _consoleConfigPromise = null
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

// 工作台下载完成后，将全部一级入口注册为已就绪组件（含直接打开详情的情况）。
// 共享模块单独分包，公开登录/恢复页面不承担整个工作台的下载成本。
function loadMenuViews() {
  return import('./menuViews').then(views => {
    router.getRoutes().forEach(record => {
      if (Object.hasOwn(views, record.name)) record.components.default = views[record.name]
    })
    return views
  })
}

function menuView(name) {
  return () => loadMenuViews().then(views => views[name])
}

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/register',
    name: 'ConsoleRegister',
    component: () => import('@/views/Register.vue'),
    meta: { title: '注册账户' }
  },
  {
    path: '/forgot-password',
    name: 'ForgotConsolePassword',
    component: () => import('@/views/ForgotPassword.vue'),
    meta: { title: '忘记密码' }
  },
  {
    path: '/password-reset',
    name: 'ResetConsolePassword',
    component: () => import('@/views/PasswordReset.vue'),
    meta: { title: '重置密码' }
  },
  {
    path: '/admin-recovery',
    name: 'AdminRecovery',
    component: () => import('@/views/AdminRecovery.vue'),
    meta: { title: '超级管理员恢复' }
  },
  {
    path: '/no-permission',
    name: 'NoConsolePermission',
    component: () => import('@/views/NoPermission.vue'),
    meta: { title: '暂无访问权限', authOnly: true }
  },
  {
    path: '/',
    component: () => Promise.all([import('@/layout/index.vue'), loadMenuViews()]).then(([layout]) => layout),
    redirect: '/dashboard',
    children: [
      { path: 'ledger', name: 'LedgerHome', component: menuView('LedgerHome'), meta: { title: '台账记录', keepAlive: true } },
      { path: 'ledger/articles/new', name: 'LedgerArticleCreate', component: () => import('@/views/ledger/LedgerEditor.vue'), meta: { title: '新建台账文章', menu: '/ledger', permission: 'ledger:edit' } },
      { path: 'ledger/articles/:id/edit', name: 'LedgerArticleEdit', component: () => import('@/views/ledger/LedgerEditor.vue'), meta: { title: '编辑台账文章', menu: '/ledger', permission: 'ledger:edit' } },
      { path: 'ledger/articles/:id', name: 'LedgerArticleRead', component: () => import('@/views/ledger/LedgerReader.vue'), meta: { title: '台账文章', menu: '/ledger' } },
      {
        path: 'dashboard',
        name: 'DashboardHome',
        component: menuView('DashboardHome'),
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
        component: menuView('ProjectList'),
        meta: { title: '项目管理' }
      },
      {
        path: 'rule',
        name: 'RuleList',
        component: menuView('RuleList'),
        meta: { title: '规则管理' }
      },
      {
        path: 'rule/:id',
        name: 'RuleDetail',
        component: () => import('@/views/rule/RuleDetail.vue'),
        meta: { title: '规则详情', keepAlive: true }
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
        path: 'expression/:sessionId',
        name: 'ConfigurationExpressionEditor',
        component: () => import('@/views/expression/ExpressionEditorPage.vue'),
        meta: { title: '配置表达式', keepAlive: true }
      },
      {
        path: 'variable',
        name: 'VariableList',
        component: menuView('VariableList'),
        meta: { title: '变量管理', keepAlive: true }
      },
      {
        path: 'list',
        name: 'ListLibrary',
        component: menuView('ListLibrary'),
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
        component: menuView('DatasourceList'),
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
        meta: { title: '新建外数 API', keepAlive: true }
      },
      {
        path: 'datasource/api/:id',
        name: 'ApiDetail',
        component: () => import('@/views/datasource/ApiDetail.vue'),
        meta: { title: '外数 API 详情', keepAlive: true }
      },
      {
        path: 'database',
        name: 'DatabaseList',
        component: menuView('DatabaseList'),
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
        path: 'sftp',
        name: 'SftpDatasourceList',
        component: menuView('SftpDatasourceList'),
        meta: { title: 'SFTP数据源配置', keepAlive: true }
      },
      {
        path: 'sftp/logs',
        name: 'SftpQueryLog',
        component: () => import('@/views/sftp/SftpQueryLog.vue'),
        meta: { title: 'SFTP查询日志', keepAlive: true }
      },
      {
        path: 'model',
        name: 'ModelList',
        component: menuView('ModelList'),
        meta: { title: '模型管理' }
      },
      {
        path: 'model/:id',
        name: 'ModelDetail',
        component: () => import('@/views/model/ModelDetail.vue'),
        meta: { title: '模型详情', keepAlive: true }
      },
      {
        path: 'function',
        name: 'FunctionList',
        component: menuView('FunctionList'),
        meta: { title: '函数管理' }
      },
      {
        path: 'test',
        name: 'RuleTest',
        component: menuView('RuleTest'),
        meta: { title: '规则测试' }
      },
      {
        path: 'lineage',
        name: 'LineageGraph',
        component: menuView('LineageGraph'),
        props: route => ({
          initialNodeType: typeof route.query.nodeType === 'string' ? route.query.nodeType : '',
          initialNodeId: typeof route.query.nodeId === 'string' ? route.query.nodeId : '',
          initialDirection: typeof route.query.direction === 'string' ? route.query.direction : 'ALL',
        }),
        meta: { title: '血缘分析' }
      },
      {
        path: 'experiment',
        name: 'ExperimentList',
        component: menuView('ExperimentList'),
        meta: { title: '分流实验' }
      },
      {
        path: 'experiment/new',
        name: 'ExperimentCreate',
        component: () => import('@/views/experiment/ExperimentDetail.vue'),
        meta: { title: '新建分流实验', keepAlive: true }
      },
      {
        path: 'experiment/detail/:experimentId',
        name: 'ExperimentDetail',
        component: () => import('@/views/experiment/ExperimentDetail.vue'),
        meta: { title: '分流实验详情', keepAlive: true }
      },
      {
        path: 'log',
        name: 'ExecutionLog',
        component: menuView('ExecutionLog'),
        meta: { title: '执行日志' }
      },
      {
        path: 'billing',
        name: 'BillingList',
        component: menuView('BillingList'),
        meta: { title: '账单管理' }
      },
      {
        path: 'approval',
        name: 'ApprovalList',
        component: menuView('ApprovalList'),
        meta: { title: '审批管理', permission: 'approval:view' }
      },
      {
        path: 'transfer',
        name: 'OfflineTransfer',
        component: menuView('OfflineTransfer'),
        meta: { title: '离线迁移', permission: 'transfer:view' }
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
        component: menuView('AccountManagement'),
        meta: { title: '账户管理', permission: ['account:view', 'account:manage', 'account:role-assign', 'account:password-reset', 'role:view', 'role:manage', 'account:audit'] }
      },
      {
        path: 'account/profile',
        name: 'AccountProfile',
        component: () => import('@/views/account/AccountProfile.vue'),
        meta: { title: '个人设置', authOnly: true }
      },
      {
        path: 'system/settings',
        name: 'SystemSettings',
        component: menuView('SystemSettings'),
        meta: { title: '系统配置', permission: 'system:view' }
      }
    ]
  }
]

function inferRoutePermission(path) {
  const value = String(path || '')
  if (value.startsWith('project')) return 'project:view'
  if (value.startsWith('billing')) return 'billing:view'
  if (value.startsWith('log')) return 'log:view'
  if (value.startsWith('list')) return 'list:view'
  if (value.startsWith('lineage')) return 'lineage:view'
  if (value.startsWith('rule') || value.startsWith('designer') ||
      value.startsWith('test')) return 'rule:view'
  if (value.startsWith('variable')) return 'field:view'
  if (value.startsWith('datasource')) return 'datasource:view'
  if (value.startsWith('database')) return 'database:view'
  if (value.startsWith('sftp')) return 'database:view'
  if (value.startsWith('model')) return 'model:view'
  if (value.startsWith('function')) return 'function:view'
  if (value.startsWith('approval')) return 'approval:view'
  if (value.startsWith('experiment')) return 'experiment:view'
  if (value.startsWith('account')) return 'account:view'
  return null
}

routes.forEach(route => {
  if (typeof route.component === 'function') route.component = preloadableView(route.component)
  ;(route.children || []).forEach(child => {
    if (typeof child.component === 'function') child.component = preloadableView(child.component)
    if (!child.meta) child.meta = {}
    if (!child.meta.permission && !child.meta.authOnly) {
      child.meta.permission = inferRoutePermission(child.path)
    }
  })
})

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

/**
 * 首次进入严格验证会话；已验证会话的站内导航先按当前权限进入，再异步复核。
 */
installNavigationFeedback(router)
router.beforeEach(createConsoleAuthGuard(fetchConsoleConfig, fetchMe,
  to => (navigationState.pendingRoute || router.currentRoute.value) === to,
  { onRedirect: target => router.replace(target) }))
router.afterEach((to, from, failure) => {
  if (!failure && to.path !== '/login') {
    import('@/security/operationActivity').then(({ recordPageVisit }) => {
      recordPageVisit(to)
    })
  }
})

export default router
