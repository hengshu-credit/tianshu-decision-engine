import Vue from 'vue'
import VueRouter from 'vue-router'
import axios from 'axios'
import Layout from '@/layout/index.vue'

Vue.use(VueRouter)

/**
 * 与后端会话 Cookie 配合的裸 axios，避免与 response 封装循环依赖。
 */
const authAxios = axios.create({
  baseURL: '/api',
  timeout: 15000,
  withCredentials: true
})

/**
 * 控制台登录配置缓存（模块级，整个页面生命周期只请求一次）
 */
var _consoleConfigPromise = null

function fetchConsoleConfig() {
  if (!_consoleConfigPromise) {
    _consoleConfigPromise = authAxios.get('/auth/console/config')
      .then(function (res) { return res.data })
      .catch(function () { return null })
  }
  return _consoleConfigPromise
}

/**
 * 会话校验结果缓存（模块级，整个页面生命周期只请求一次）
 */
var _mePromise = null

function fetchMe() {
  if (!_mePromise) {
    _mePromise = authAxios.get('/auth/console/me')
      .then(function (res) { return res.data })
      .catch(function () { return null })
  }
  return _mePromise
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
    redirect: '/project',
    children: [
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
        meta: { title: '决策表设计器' }
      },
      {
        path: 'designer/tree/:id',
        name: 'DecisionTree',
        component: () =>
          import(/* webpackChunkName: "designer-decision-tree" */ '@/views/designer/DecisionTree.vue'),
        meta: { title: '决策树设计器' }
      },
      {
        path: 'designer/flow/:id',
        name: 'DecisionFlow',
        component: () =>
          import(/* webpackChunkName: "designer-decision-flow" */ '@/views/designer/DecisionFlow.vue'),
        meta: { title: '决策流设计器' }
      },
      {
        path: 'designer/ruleset/:id',
        name: 'RuleSet',
        component: () => import('@/views/designer/RuleSet.vue'),
        meta: { title: '规则集设计器' }
      },
      {
        path: 'designer/cross/:id',
        name: 'CrossTable',
        component: () => import('@/views/designer/CrossTable.vue'),
        meta: { title: '交叉表设计器' }
      },
      {
        path: 'designer/score/:id',
        name: 'Scorecard',
        component: () => import('@/views/designer/Scorecard.vue'),
        meta: { title: '评分卡设计器' }
      },
      {
        path: 'designer/cross-adv/:id',
        name: 'AdvancedCrossTable',
        component: () => import('@/views/designer/AdvancedCrossTable.vue'),
        meta: { title: '复杂交叉表设计器' }
      },
      {
        path: 'designer/score-adv/:id',
        name: 'AdvancedScorecard',
        component: () => import('@/views/designer/AdvancedScorecard.vue'),
        meta: { title: '复杂评分卡设计器' }
      },
      {
        path: 'designer/script/:id',
        name: 'ScriptEditor',
        component: () => import('@/views/designer/ScriptEditor.vue'),
        meta: { title: 'QL脚本编辑器' }
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
      }
    ]
  }
]

const router = new VueRouter({
  routes
})

/**
 * 若后端启用控制台登录，则校验会话；未启用时与改造前行为一致。
 */
var _checked = false // 是否已完成登录校验（首次校验后不再重复触发重定向）
router.beforeEach(async (to, from, next) => {
  var body = await fetchConsoleConfig()
  if (to.path === '/login') {
    if (body && body.code === 200 && body.data && !body.data.loginEnabled) {
      // 登录已禁用，直接跳转（使用 Vue Router 而非 location 避免页面刷新）
      return next({ path: to.query.redirect || '/project', replace: true })
    }
    return next()
  }
  if (!body || body.code !== 200 || !body.data || !body.data.loginEnabled) {
    return next()
  }
  if (_checked) {
    // 首次校验后不再重复触发重定向，直接放行
    return next()
  }
  try {
    var me = await fetchMe()
    if (me && me.code === 200 && me.data && me.data.username) {
      return next()
    }
  } catch (e) { /* ignore */ }
  // 记录已触发重定向，防止刷新后再次重定向形成循环
  _checked = true
  return next({ path: '/login?redirect=' + encodeURIComponent(to.fullPath), replace: true })
})

export default router
