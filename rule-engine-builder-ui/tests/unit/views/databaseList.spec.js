import { mount } from '@test-utils'
import { nextTick } from 'vue'
import * as databaseApi from '@/api/database'
import * as projectApi from '@/api/project'
import ProjectFilterSelect from '@/components/ProjectFilterSelect.vue'
import DatabaseList from '@/views/database/DatabaseList.vue'



async function mountPage(route) {
  projectApi.listProjects.mockResolvedValue({ data: { records: [{ id: 1, projectName: '项目A' }] } })
  databaseApi.listDbDatasources.mockResolvedValue({ data: { records: [], total: 0 } })
  const router = { push: vi.fn() }

  const wrapper = mount(DatabaseList, {
    mocks: {
      $route: route || { path: '/database', query: {}, params: {} },
      $router: router,
      $store: { state: { currentProject: null } },
      $message: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
      $confirm: vi.fn().mockResolvedValue(true)
    },
    stubs: {
      'el-form': { template: '<form><slot /></form>', methods: { validate: vi.fn(cb => cb(true)) } },
      'el-form-item': true,
      'el-select': true,
      'el-option': true,
      'el-input': true,
      'el-input-number': true,
      'el-button': true,
      'el-tag': true,
      'el-table': true,
      'el-table-column': true,
      'el-pagination': true,
      'el-dialog': true,
      'el-row': true,
      'el-col': true,
      'el-switch': true,
      'el-radio-group': true,
      'el-radio-button': true,
      'el-checkbox': true
    }
  })
  await nextTick()
  await new Promise(resolve => setTimeout(resolve, 0))
  return wrapper
}

describe('DatabaseList — JDBC URL 生成', () => {
  let wrapper

  beforeEach(async () => {
    wrapper = await mountPage()
  })

  afterEach(() => {
    if (wrapper) wrapper.unmount()
    vi.clearAllMocks()
  })

  test('uses unified fuzzy filters for project code and name', () => {
    expect(DatabaseList.components.ProjectFilterSelect).toBe(ProjectFilterSelect)
    expect(wrapper.vm.qp).toEqual(expect.objectContaining({ projectCode: '', projectName: '' }))
  })

  test('项目工作台进入时限定数据库列表并让新建页继承项目', async () => {
    wrapper.unmount()
    wrapper = await mountPage({
      path: '/database',
      query: { projectId: '2' },
      params: {},
    })

    expect(databaseApi.listDbDatasources).toHaveBeenCalledWith(
      expect.objectContaining({ projectId: 2 })
    )

    wrapper.vm.resetQuery()
    expect(wrapper.vm.qp.projectId).toBe(2)

    wrapper.vm.handleCreate()
    expect(wrapper.vm.$router.push).toHaveBeenCalledWith({
      path: '/database/new',
      query: { projectId: 2 },
    })
  })

  test('MySQL 表单字段能生成 JDBC URL 并追加扩展参数', () => {
    wrapper.vm.form = wrapper.vm.emptyForm()
    wrapper.vm.form.host = 'mysql.internal'
    wrapper.vm.form.port = 3307
    wrapper.vm.form.databaseName = 'riskdb'
    wrapper.vm.form.jdbcParams = 'useUnicode=true&serverTimezone=Asia/Shanghai'

    wrapper.vm.generateJdbcUrl(false)

    expect(wrapper.vm.form.jdbcUrl).toBe('jdbc:mysql://mysql.internal:3307/riskdb?useUnicode=true&serverTimezone=Asia/Shanghai')
  })

  test('SQL Server 表单字段使用分号参数生成 JDBC URL', () => {
    wrapper.vm.form = wrapper.vm.emptyForm()
    wrapper.vm.form.dbType = 'SQLSERVER'
    wrapper.vm.form.host = 'sql.internal'
    wrapper.vm.form.port = 1433
    wrapper.vm.form.databaseName = 'riskdb'
    wrapper.vm.form.jdbcParams = 'encrypt=false;trustServerCertificate=true'

    wrapper.vm.generateJdbcUrl(false)

    expect(wrapper.vm.form.jdbcUrl).toBe('jdbc:sqlserver://sql.internal:1433;databaseName=riskdb;encrypt=false;trustServerCertificate=true')
  })

  test('关闭自动生成后手写 JDBC URL 不会被表单字段覆盖', () => {
    wrapper.vm.form = wrapper.vm.emptyForm()
    wrapper.vm.form.jdbcAutoBuild = false
    wrapper.vm.form.jdbcUrl = 'jdbc:mysql://custom-host:3306/custom_db'
    wrapper.vm.form.host = 'mysql.internal'
    wrapper.vm.form.databaseName = 'riskdb'

    wrapper.vm.onJdbcPartChange()

    expect(wrapper.vm.form.jdbcUrl).toBe('jdbc:mysql://custom-host:3306/custom_db')
  })

  test('normalizeForm 移除前端自动生成标记并保留 SSH 配置', () => {
    wrapper.vm.form = wrapper.vm.emptyForm()
    wrapper.vm.form.connectionMode = 'SSH_TUNNEL'
    wrapper.vm.form.host = 'mysql.internal'
    wrapper.vm.form.databaseName = 'riskdb'
    wrapper.vm.form.sshHost = 'bastion.internal'
    wrapper.vm.form.sshUsername = 'deploy'
    wrapper.vm.generateJdbcUrl(false)

    const data = wrapper.vm.normalizeForm(wrapper.vm.form)

    expect(data.jdbcAutoBuild).toBeUndefined()
    expect(data.jdbcUrl).toBe('jdbc:mysql://mysql.internal:3306/riskdb?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false')
    expect(data.connectionMode).toBe('SSH_TUNNEL')
    expect(data.sshHost).toBe('bastion.internal')
    expect(data.sshUsername).toBe('deploy')
  })

  test('提供数据库变量配置说明和只读查询提示', () => {
    expect(wrapper.vm.databaseGuideCards.map(item => item.title)).toEqual([
      '只读连接',
      '查询模板',
      '数据库变量'
    ])
    expect(wrapper.vm.databaseGuideCards[0].text).toContain('只读账号')
    expect(wrapper.vm.databaseGuideCards[2].text).toContain('sourceConfig')
  })

  test('SQL 占位符统计会忽略字符串和注释中的问号', () => {
    const sql = [
      "SELECT ? AS score, '?' AS literal, \"?\" AS quoted",
      'FROM risk_score',
      'WHERE enabled = ? -- ? in comment',
      'AND note = /* ? block */ ?'
    ].join('\n')

    expect(wrapper.vm.countSqlPlaceholders(sql)).toBe(3)
  })

  test('SQL 占位符统计兼容 PostgreSQL、SQL Server 与 Oracle 引用语法', () => {
    const sql = [
      'SELECT $$?$$ AS pg_plain, $tag$?$tag$ AS pg_tagged,',
      "[?] AS sql_server_identifier, q'[?]' AS oracle_text, ? AS bound_value"
    ].join('\n')

    expect(wrapper.vm.countSqlPlaceholders(sql)).toBe(1)
  })

  test('SQL 改变时按占位符数量调整参数行并保留已有值', () => {
    wrapper.vm.queryParamRows = [
      { type: 'NUMBER', value: 88 },
      { type: 'BOOLEAN', value: true }
    ]

    wrapper.vm.syncQueryParamsToSql('SELECT ? AS score, ? AS enabled, ? AS name')

    expect(wrapper.vm.queryParamRows).toEqual([
      { type: 'NUMBER', value: 88 },
      { type: 'BOOLEAN', value: true },
      { type: 'STRING', value: '' }
    ])

    wrapper.vm.syncQueryParamsToSql('SELECT ? AS score')
    expect(wrapper.vm.queryParamRows).toEqual([{ type: 'NUMBER', value: 88 }])
  })

  test('只读查询按参数类型构建数组并展示成功结果', async () => {
    databaseApi.queryDbDatasource.mockResolvedValueOnce({
      data: [{ score: 88, enabled: false, note: null }]
    })
    wrapper.vm.openQuery({ id: 31, datasourceName: '风控库' })
    wrapper.vm.queryForm.sql = 'SELECT ? AS score, ? AS enabled, ? AS note'
    wrapper.vm.syncQueryParamsToSql(wrapper.vm.queryForm.sql)
    wrapper.vm.queryParamRows = [
      { type: 'NUMBER', value: 88 },
      { type: 'BOOLEAN', value: false },
      { type: 'NULL', value: null }
    ]

    await wrapper.vm.runQuery()

    expect(databaseApi.queryDbDatasource).toHaveBeenCalledWith(31, {
      sql: 'SELECT ? AS score, ? AS enabled, ? AS note',
      params: [88, false, null],
      queryTimeoutSeconds: 5,
      maxRows: 100
    })
    expect(wrapper.vm.queryStatus).toBe('SUCCESS')
    expect(wrapper.vm.queryColumns).toEqual(['score', 'enabled', 'note'])
  })

  test('非只读 SQL 在前端阻止且保留可修正错误', async () => {
    wrapper.vm.openQuery({ id: 31, datasourceName: '风控库' })
    wrapper.vm.queryForm.sql = 'UPDATE customer SET level = ?'
    wrapper.vm.syncQueryParamsToSql(wrapper.vm.queryForm.sql)

    await wrapper.vm.runQuery()

    expect(databaseApi.queryDbDatasource).not.toHaveBeenCalled()
    expect(wrapper.vm.queryStatus).toBe('ERROR')
    expect(wrapper.vm.queryError).toContain('SELECT')
  })

  test('查询失败与成功零行使用不同状态', async () => {
    wrapper.vm.openQuery({ id: 31, datasourceName: '风控库' })
    wrapper.vm.queryForm.sql = 'SELECT 1 WHERE 1 = 0'
    databaseApi.queryDbDatasource.mockResolvedValueOnce({ data: [] })
    await wrapper.vm.runQuery()
    expect(wrapper.vm.queryStatus).toBe('EMPTY')

    databaseApi.queryDbDatasource.mockRejectedValueOnce(
      new Error('数据库连接暂时不可用')
    )
    await wrapper.vm.runQuery()
    expect(wrapper.vm.queryStatus).toBe('ERROR')
    expect(wrapper.vm.queryError).toContain('数据库连接暂时不可用')
  })

  test('数值参数未填写时不会被误当成 0 发送', async () => {
    wrapper.vm.openQuery({ id: 31, datasourceName: '风控库' })
    wrapper.vm.queryForm.sql = 'SELECT ? AS score'
    wrapper.vm.syncQueryParamsToSql(wrapper.vm.queryForm.sql)
    wrapper.vm.queryParamRows[0] = { type: 'NUMBER', value: null }

    await wrapper.vm.runQuery()

    expect(databaseApi.queryDbDatasource).not.toHaveBeenCalled()
    expect(wrapper.vm.queryStatus).toBe('ERROR')
    expect(wrapper.vm.queryError).toContain('参数 1')
  })

  test('切换数据源后旧查询响应不能覆盖当前结果', async () => {
    let resolveFirst
    let resolveSecond
    databaseApi.queryDbDatasource
      .mockReturnValueOnce(new Promise(resolve => { resolveFirst = resolve }))
      .mockReturnValueOnce(new Promise(resolve => { resolveSecond = resolve }))

    wrapper.vm.openQuery({ id: 31, datasourceName: '旧数据源' })
    wrapper.vm.queryForm.sql = 'SELECT 1 AS value'
    const first = wrapper.vm.runQuery()
    wrapper.vm.openQuery({ id: 32, datasourceName: '当前数据源' })
    wrapper.vm.queryForm.sql = 'SELECT 2 AS value'
    const second = wrapper.vm.runQuery()
    resolveSecond({ data: [{ value: 2 }] })
    await second
    resolveFirst({ data: [{ value: 1 }] })
    await first

    expect(wrapper.vm.queryRows).toEqual([{ value: 2 }])
    expect(wrapper.vm.queryStatus).toBe('SUCCESS')
  })
})
