import { shallowMount } from '@test-utils'
import { nextTick } from 'vue'

import * as databaseApi from '@/api/database'
import * as projectApi from '@/api/project'
import DatabaseDetail from '@/views/database/DatabaseDetail.vue'

function flushPromises() {
  return new Promise(resolve => setTimeout(resolve, 0))
}

const FormItemStub = {
  props: ['label'],
  template: '<div class="form-item-stub" :data-label="label"><slot /></div>'
}

function mountPage(route) {
  return shallowMount(DatabaseDetail, {
    mocks: {
      $route: route || { params: { id: '1' }, query: {} },
      $router: { push: vi.fn() },
      $message: { success: vi.fn(), warning: vi.fn(), error: vi.fn() }
    },
    stubs: {
      'el-form': { template: '<form><slot /></form>', methods: { validate: vi.fn(cb => cb(true)) } },
      'el-form-item': FormItemStub,
      'el-select': true,
      'el-option': true,
      'el-input': true,
      'el-input-number': true,
      'el-button': true,
      'el-row': true,
      'el-col': true,
      'el-switch': true,
      'el-radio-group': true,
      'el-radio-button': true,
      'el-checkbox': true,
      'el-alert': true,
      'el-collapse': true,
      'el-collapse-item': true,
      'monaco-editor': true
    }
  })
}

describe('DatabaseDetail — 项目选择', () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  test('编辑项目级数据源时 projectId 为 0 不自动选中项目', async () => {
    projectApi.listProjects.mockResolvedValue({ data: { records: [{ id: 1, projectName: '项目A' }] } })
    databaseApi.getDbDatasource.mockResolvedValue({
      data: {
        id: 10,
        scope: 'PROJECT',
        projectId: 0,
        datasourceCode: 'risk_db',
        datasourceName: '风险库',
        jdbcUrl: 'jdbc:mysql://127.0.0.1:3306/risk'
      }
    })

    const wrapper = mountPage()
    await flushPromises()
    await nextTick()

    expect(wrapper.vm.form.scope).toBe('PROJECT')
    expect(wrapper.vm.form.projectId).toBeNull()
  })

  test('从全局切回项目级时清空 projectId，要求用户重新选择项目', async () => {
    projectApi.listProjects.mockResolvedValue({ data: { records: [] } })
    databaseApi.getDbDatasource.mockResolvedValue({
      data: {
        id: 11,
        scope: 'GLOBAL',
        projectId: 0,
        datasourceCode: 'global_db',
        datasourceName: '全局库',
        jdbcUrl: 'jdbc:mysql://127.0.0.1:3306/rule'
      }
    })

    const wrapper = mountPage()
    await flushPromises()
    await nextTick()

    wrapper.vm.onScopeChange('PROJECT')

    expect(wrapper.vm.form.projectId).toBeNull()
  })

  test('项目内新建数据库返回列表时保留显式项目范围', async () => {
    projectApi.listProjects.mockResolvedValue({
      data: { records: [{ id: 3, projectName: '综合风控示例项目' }] }
    })
    const wrapper = mountPage({
      path: '/database/new',
      params: { id: 'new' },
      query: { projectId: '3' }
    })
    await flushPromises()

    wrapper.vm.goBack()

    expect(wrapper.vm.$router.push).toHaveBeenCalledWith({
      path: '/database',
      query: { projectId: 3 }
    })
  })

  test('数据库账号密码排在 SSH 配置之前', async () => {
    projectApi.listProjects.mockResolvedValue({ data: { records: [] } })
    const wrapper = mountPage({ path: '/database/new', params: { id: 'new' }, query: {} })
    await flushPromises()

    wrapper.vm.form.connectionMode = 'SSH_TUNNEL'
    await nextTick()

    const labels = wrapper.findAll('.form-item-stub').map(item => item.attributes('data-label'))
    expect(labels.indexOf('用户名')).toBeGreaterThanOrEqual(0)
    expect(labels.indexOf('密码')).toBeGreaterThanOrEqual(0)
    expect(labels.indexOf('SSH主机')).toBeGreaterThan(labels.indexOf('用户名'))
    expect(labels.indexOf('SSH主机')).toBeGreaterThan(labels.indexOf('密码'))
    wrapper.unmount()
  })

  test.each(['new', '1'])('账号密码移动后，%s 页面切换连接方式仍正确测试和保存', async (id) => {
    projectApi.listProjects.mockResolvedValue({ data: { records: [] } })
    databaseApi.getDbDatasource.mockResolvedValue({ data: {
      id: 1, scope: 'GLOBAL', datasourceCode: 'risk_db', datasourceName: '风险库',
      host: 'mysql.internal', port: 3306, databaseName: 'risk', jdbcParams: '',
      jdbcUrl: 'jdbc:mysql://mysql.internal:3306/risk', username: 'loaded_db_user', password: 'loaded_db_password'
    } })
    databaseApi.testDbDatasourceDraft.mockResolvedValue({ data: '连接成功' })
    databaseApi.createDbDatasource.mockResolvedValue({ data: {} })
    databaseApi.updateDbDatasource.mockResolvedValue({ data: {} })
    const wrapper = mountPage({ path: `/database/${id}`, params: { id }, query: {} })
    try {
      await flushPromises()
      if (id !== 'new') {
        expect(wrapper.vm.form).toMatchObject({ username: 'loaded_db_user', password: 'loaded_db_password' })
      }
      Object.assign(wrapper.vm.form, { scope: 'GLOBAL', host: 'mysql.internal', databaseName: 'risk' })
      const input = label => wrapper.find(`[data-label="${label}"]`).findComponent('el-input-stub')
      input('用户名').vm.$emit('update:modelValue', 'DB_User')
      input('密码').vm.$emit('update:modelValue', ' Db!@#密 码 ')
      const mode = wrapper.findComponent('el-radio-group-stub')
      mode.vm.$emit('update:modelValue', 'SSH_TUNNEL')
      await nextTick()
      input('SSH用户').vm.$emit('update:modelValue', 'ssh_user')
      input('SSH密码').vm.$emit('update:modelValue', 'SSH!pass')
      const credentials = { username: 'DB_User', password: ' Db!@#密 码 ', sshUsername: 'ssh_user', sshPassword: 'SSH!pass' }

      for (const connectionMode of ['DIRECT', 'SSH_TUNNEL']) {
        mode.vm.$emit('update:modelValue', connectionMode)
        await nextTick()
        expect(wrapper.find('.form-section').exists()).toBe(connectionMode === 'SSH_TUNNEL')
        expect(input('用户名').exists()).toBe(true)
        expect(input('密码').exists()).toBe(true)
        expect(wrapper.vm.form).toMatchObject(credentials)
        wrapper.vm.handleTestDraft()
        await flushPromises()
        expect(databaseApi.testDbDatasourceDraft.mock.lastCall[0]).toMatchObject({ ...credentials, connectionMode })
        wrapper.vm.handleSubmit()
        await flushPromises()
        const save = id === 'new' ? databaseApi.createDbDatasource : databaseApi.updateDbDatasource
        expect(save.mock.lastCall[0]).toMatchObject({ ...credentials, connectionMode })
      }
      expect(id === 'new' ? databaseApi.updateDbDatasource : databaseApi.createDbDatasource).not.toHaveBeenCalled()
    } finally {
      wrapper.unmount()
    }
  })

  test('连接测试成功后保留可见的成功状态', async () => {
    projectApi.listProjects.mockResolvedValue({ data: { records: [] } })
    databaseApi.testDbDatasourceDraft.mockResolvedValue({ data: '连接成功' })
    const wrapper = mountPage({ path: '/database/new', params: { id: 'new' }, query: {} })
    await flushPromises()

    wrapper.vm.form.host = '127.0.0.1'
    wrapper.vm.form.databaseName = 'risk'
    wrapper.vm.form.datasourceCode = 'risk_db'
    wrapper.vm.form.datasourceName = '风险库'
    wrapper.vm.handleTestDraft()
    await flushPromises()

    expect(databaseApi.testDbDatasourceDraft).toHaveBeenCalled()
    expect(wrapper.vm.connectionTestStatus).toBe('success')
    expect(wrapper.vm.connectionTesting).toBe(false)
  })

  test('连接测试失败后展示可读错误且允许再次测试', async () => {
    projectApi.listProjects.mockResolvedValue({ data: { records: [] } })
    databaseApi.testDbDatasourceDraft.mockRejectedValue(new Error('账号无权访问'))
    const wrapper = mountPage({ path: '/database/new', params: { id: 'new' }, query: {} })
    await flushPromises()

    wrapper.vm.form.host = '127.0.0.1'
    wrapper.vm.form.databaseName = 'risk'
    wrapper.vm.form.datasourceCode = 'risk_db'
    wrapper.vm.form.datasourceName = '风险库'
    wrapper.vm.handleTestDraft()
    await flushPromises()

    expect(wrapper.vm.connectionTestStatus).toBe('error')
    expect(wrapper.vm.connectionTestMessage).toBe('账号无权访问')
    expect(wrapper.vm.connectionTesting).toBe(false)
  })

  test('测试期间修改连接参数时，旧请求成功不能验证新配置', async () => {
    projectApi.listProjects.mockResolvedValue({ data: { records: [] } })
    let finishTest
    databaseApi.testDbDatasourceDraft.mockReturnValue(new Promise(resolve => { finishTest = resolve }))
    const wrapper = mountPage({ path: '/database/new', params: { id: 'new' }, query: {} })
    await flushPromises()
    Object.assign(wrapper.vm.form, {
      host: 'mysql-original', databaseName: 'risk', jdbcUrl: 'jdbc:mysql://mysql-original:3306/risk'
    })

    wrapper.vm.handleTestDraft()
    wrapper.vm.form.jdbcUrl = 'jdbc:mysql://mysql-changed:3306/risk'
    finishTest({ data: '连接成功' })
    await flushPromises()

    expect(databaseApi.testDbDatasourceDraft.mock.lastCall[0].jdbcUrl).toBe('jdbc:mysql://mysql-original:3306/risk')
    expect(wrapper.vm.configurationChecklist.find(item => item.key === 'connection-test').status).toBe('PENDING')
    expect(wrapper.vm.connectionTestOutdated).toBe(true)
    expect(wrapper.vm.$message.success).not.toHaveBeenCalled()
  })

  test('已验证的连接参数修改后明确提示重新测试，重新测试后恢复完成状态', async () => {
    projectApi.listProjects.mockResolvedValue({ data: { records: [] } })
    databaseApi.testDbDatasourceDraft.mockResolvedValue({ data: '连接成功' })
    const wrapper = mountPage({ path: '/database/new', params: { id: 'new' }, query: {} })
    await flushPromises()
    Object.assign(wrapper.vm.form, {
      host: 'mysql-original', databaseName: 'risk', jdbcUrl: 'jdbc:mysql://mysql-original:3306/risk'
    })
    wrapper.vm.handleTestDraft()
    await flushPromises()

    wrapper.vm.form.password = 'changed-password'
    await nextTick()

    expect(wrapper.vm.connectionTestOutdated).toBe(true)
    expect(wrapper.vm.configurationChecklist.find(item => item.key === 'connection-test').help).toContain('重新测试')
    expect(wrapper.find('el-alert-stub').attributes('title')).toBe('连接配置已变更，请重新测试')

    wrapper.vm.handleTestDraft()
    await flushPromises()
    expect(wrapper.vm.connectionTestOutdated).toBe(false)
    expect(wrapper.vm.configurationChecklist.find(item => item.key === 'connection-test').status).toBe('READY')
  })

  test('调整连接池超时也需要重新测试', async () => {
    projectApi.listProjects.mockResolvedValue({ data: { records: [] } })
    databaseApi.testDbDatasourceDraft.mockResolvedValue({ data: '连接成功' })
    const wrapper = mountPage({ path: '/database/new', params: { id: 'new' }, query: {} })
    await flushPromises()
    Object.assign(wrapper.vm.form, {
      host: 'mysql-original', databaseName: 'risk', jdbcUrl: 'jdbc:mysql://mysql-original:3306/risk'
    })
    wrapper.vm.handleTestDraft()
    await flushPromises()
    wrapper.vm.form.connectionTimeoutMs = 1000

    expect(wrapper.vm.configurationChecklist.find(item => item.key === 'connection-test').status).toBe('PENDING')
  })

  test('编辑详情未返回时不开放表单，保存和连接测试均不能发请求', async () => {
    projectApi.listProjects.mockResolvedValue({ data: { records: [] } })
    let resolveDetail
    databaseApi.getDbDatasource.mockReturnValue(new Promise(resolve => { resolveDetail = resolve }))
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.vm.loadDetail()
    expect(databaseApi.getDbDatasource).toHaveBeenCalledTimes(1)
    const snapshot = {
      formVisible: wrapper.find('.detail-form').exists(),
      saveDisabled: wrapper.find('[data-testid="save-database-datasource"]').attributes('disabled'),
      testDisabled: wrapper.find('[data-testid="test-database-connection"]').attributes('disabled'),
      preflightVisible: wrapper.findComponent({ name: 'ResourcePreflightPanel' }).exists(),
    }
    wrapper.vm.handleSubmit()
    wrapper.vm.handleTestDraft()
    const writes = databaseApi.createDbDatasource.mock.calls.length + databaseApi.updateDbDatasource.mock.calls.length + databaseApi.testDbDatasourceDraft.mock.calls.length
    resolveDetail({ data: { id: 1, jdbcUrl: 'jdbc:mysql://custom-host:3306/risk?custom=true' } })
    await flushPromises()
    try {
      expect(snapshot).toEqual({ formVisible: false, saveDisabled: 'true', testDisabled: 'true', preflightVisible: false })
      expect(writes).toBe(0)
      expect(wrapper.find('.detail-form').exists()).toBe(true)
      expect(wrapper.vm.form.jdbcAutoBuild).toBe(false)
      expect(wrapper.vm.form.jdbcUrl).toBe('jdbc:mysql://custom-host:3306/risk?custom=true')
      databaseApi.updateDbDatasource.mockResolvedValueOnce({ data: { id: 99 } })
      wrapper.vm.handleSubmit()
      await flushPromises()
      expect(databaseApi.updateDbDatasource).toHaveBeenCalledWith(expect.objectContaining({ id: 1, jdbcUrl: 'jdbc:mysql://custom-host:3306/risk?custom=true' }))
      expect(databaseApi.createDbDatasource).not.toHaveBeenCalled()
    } finally {
      wrapper.unmount()
    }
  })

  test('加载失败后禁止误创建，明确重试成功才恢复编辑', async () => {
    projectApi.listProjects.mockResolvedValue({ data: { records: [] } })
    databaseApi.getDbDatasource.mockResolvedValueOnce({ data: { id: 1 } })
    const wrapper = mountPage()
    await flushPromises()
    databaseApi.getDbDatasource.mockRejectedValueOnce(new Error('读取数据库配置超时'))
    // 旧实现会抛出未处理异常，捕获仅用于断言页面是否进入可重试错误态。
    await wrapper.vm.loadDetail().catch(() => {})
    await nextTick()
    const error = wrapper.find('[data-testid="database-detail-error"]')
    try {
      expect(error.exists()).toBe(true)
      expect(error.text()).toContain('读取数据库配置超时')
      expect(wrapper.find('.detail-form').exists()).toBe(false)
      wrapper.vm.handleSubmit()
      wrapper.vm.handleTestDraft()
      expect(databaseApi.createDbDatasource).not.toHaveBeenCalled()
      expect(databaseApi.updateDbDatasource).not.toHaveBeenCalled()
      expect(databaseApi.testDbDatasourceDraft).not.toHaveBeenCalled()
      databaseApi.getDbDatasource.mockResolvedValueOnce({ data: { id: 1, datasourceName: '恢复后的配置', jdbcUrl: 'jdbc:custom:unchanged' } })
      await error.find('[data-testid="retry-database-detail"]').trigger('click')
      await flushPromises()
      expect(wrapper.find('.detail-form').exists()).toBe(true)
      expect(wrapper.vm.form.datasourceName).toBe('恢复后的配置')
      expect(wrapper.vm.form.jdbcUrl).toBe('jdbc:custom:unchanged')
      expect(wrapper.vm.form.jdbcAutoBuild).toBe(false)
    } finally {
      wrapper.unmount()
    }
  })

  test('编辑空详情保持错误态，不能把空对象作为新建表单保存', async () => {
    projectApi.listProjects.mockResolvedValue({ data: { records: [] } })
    databaseApi.getDbDatasource.mockResolvedValue({ data: null })
    const wrapper = mountPage()
    await flushPromises()
    try {
      expect(wrapper.find('[data-testid="database-detail-error"]').exists()).toBe(true)
      wrapper.vm.handleSubmit()
      expect(databaseApi.createDbDatasource).not.toHaveBeenCalled()
      expect(wrapper.find('.detail-form').exists()).toBe(false)
    } finally {
      wrapper.unmount()
    }
  })

  test('项目列表慢响应不阻塞详情请求，也不重置新建页已填写内容', async () => {
    let resolveProjects
    projectApi.listProjects.mockReturnValue(new Promise(resolve => { resolveProjects = resolve }))
    databaseApi.getDbDatasource.mockResolvedValue({ data: { id: 1, datasourceName: '已加载详情' } })
    const edit = mountPage()
    const create = mountPage({ params: { id: 'new' }, query: { projectId: '3' } })
    await flushPromises()
    const requestStartedBeforeProjects = databaseApi.getDbDatasource.mock.calls.length > 0
    const createReadyBeforeProjects = create.find('.detail-form').exists()
    create.vm.form.datasourceName = '用户已经填写的名称'
    create.vm.form.jdbcUrl = 'jdbc:custom:keep-original'
    create.vm.form.jdbcAutoBuild = false
    resolveProjects({ data: { records: [{ id: 3 }] } })
    await flushPromises()
    try {
      expect(requestStartedBeforeProjects).toBe(true)
      expect(createReadyBeforeProjects).toBe(true)
      expect(create.vm.form.projectId).toBe(3)
      expect(create.vm.form.datasourceName).toBe('用户已经填写的名称')
      expect(create.vm.form.jdbcUrl).toBe('jdbc:custom:keep-original')
      expect(create.vm.form.jdbcAutoBuild).toBe(false)
    } finally {
      edit.unmount()
      create.unmount()
    }
  })
})

describe('DatabaseDetail — 实际连接地址确认', () => {
  let wrapper
  const originalUrl = 'jdbc:mysql://old-host:3306/risk?customParameter=keep%2FExact'

  beforeEach(async () => {
    projectApi.listProjects.mockResolvedValue({ data: { records: [] } })
    databaseApi.getDbDatasource.mockResolvedValue({ data: {
      id: 1, scope: 'GLOBAL', datasourceCode: 'custom_db', datasourceName: '自定义连接',
      dbType: 'MYSQL', connectionMode: 'DIRECT', host: 'old-host', port: 3306,
      databaseName: 'risk', jdbcParams: 'customParameter=keep%2FExact', jdbcUrl: originalUrl,
    } })
    databaseApi.testDbDatasourceDraft.mockResolvedValue({ data: '连接成功' })
    databaseApi.updateDbDatasource.mockResolvedValue({ data: {} })
    wrapper = mountPage()
    await flushPromises()
  })

  afterEach(() => {
    wrapper.unmount()
    vi.clearAllMocks()
  })

  test('自定义URL说明真实优先级，字段变更未生成时提示并阻止保存与测试', async () => {
    wrapper.vm.form.host = 'new-host'
    wrapper.vm.onJdbcPartChange()
    await nextTick()
    expect(wrapper.vm.form.jdbcUrl).toBe(originalUrl)
    wrapper.vm.handleSubmit()
    wrapper.vm.handleTestDraft()
    await flushPromises()
    expect(databaseApi.updateDbDatasource).not.toHaveBeenCalled()
    expect(databaseApi.createDbDatasource).not.toHaveBeenCalled()
    expect(databaseApi.testDbDatasourceDraft).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="jdbc-mode-hint"]').text()).toContain('实际连接以当前 JDBC URL 为准')
    const warning = wrapper.find('[data-testid="jdbc-resolution-required"]')
    expect(warning.exists()).toBe(true)
    expect(warning.text()).toContain('生成并替换URL')
    expect(warning.text()).toContain('保留自定义URL')
  })

  test('明确保留自定义URL后原值测试和保存，再次改字段要求重新处理', async () => {
    wrapper.vm.form.port = 3307
    wrapper.vm.onJdbcPartChange()
    await nextTick()
    await wrapper.find('[data-testid="keep-custom-jdbc-url"]').trigger('click')
    expect(wrapper.vm.form.jdbcUrl).toBe(originalUrl)
    expect(wrapper.vm.form.jdbcAutoBuild).toBe(false)
    expect(wrapper.find('[data-testid="jdbc-resolution-required"]').exists()).toBe(false)
    wrapper.vm.handleTestDraft()
    wrapper.vm.handleSubmit()
    await flushPromises()
    expect(databaseApi.testDbDatasourceDraft).toHaveBeenCalledWith(expect.objectContaining({ jdbcUrl: originalUrl, port: 3307 }))
    expect(databaseApi.updateDbDatasource).toHaveBeenCalledWith(expect.objectContaining({ jdbcUrl: originalUrl, port: 3307 }))
    wrapper.vm.form.databaseName = 'another'
    wrapper.vm.onJdbcPartChange()
    await nextTick()
    expect(wrapper.find('[data-testid="jdbc-resolution-required"]').exists()).toBe(true)
  })

  test('生成并替换使用当前字段，失败生成不会消除待处理状态', async () => {
    wrapper.vm.form.host = 'new-host'
    wrapper.vm.onJdbcPartChange()
    await nextTick()
    await wrapper.find('[data-testid="replace-jdbc-url"]').trigger('click')
    expect(wrapper.vm.form.jdbcUrl).toBe('jdbc:mysql://new-host:3306/risk?customParameter=keep%2FExact')
    expect(wrapper.find('[data-testid="jdbc-resolution-required"]').exists()).toBe(false)
    wrapper.vm.form.host = ''
    wrapper.vm.onJdbcPartChange()
    expect(wrapper.vm.generateJdbcUrl(true)).toBe(false)
    await nextTick()
    expect(wrapper.find('[data-testid="jdbc-resolution-required"]').exists()).toBe(true)
    wrapper.vm.handleTestDraft()
    expect(databaseApi.testDbDatasourceDraft).not.toHaveBeenCalled()
  })

  test('自动模式生成失败不会继续测试旧URL，补全后正常生成', async () => {
    wrapper.vm.form.jdbcAutoBuild = true
    wrapper.vm.onJdbcAutoBuildChange(true)
    wrapper.vm.form.databaseName = ''
    wrapper.vm.onJdbcPartChange()
    wrapper.vm.handleTestDraft()
    await flushPromises()
    expect(databaseApi.testDbDatasourceDraft).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="jdbc-resolution-required"]').exists()).toBe(true)
    wrapper.vm.form.databaseName = 'restored'
    wrapper.vm.onJdbcPartChange()
    await nextTick()
    expect(wrapper.vm.form.jdbcUrl).toContain('/restored?')
    expect(wrapper.find('[data-testid="jdbc-resolution-required"]').exists()).toBe(false)
  })

  test('复杂Oracle自定义值不解析或改写，字段改回原值自动解除提示', async () => {
    const customUrl = 'jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS_LIST=(ADDRESS=(HOST=a)(PORT=1521))(ADDRESS=(HOST=b)(PORT=1521)))(CONNECT_DATA=(SERVICE_NAME=CaseSensitive)))'
    databaseApi.getDbDatasource.mockResolvedValueOnce({ data: { id: 1, dbType: 'ORACLE', host: 'reference-only', port: 1521, databaseName: 'service', jdbcUrl: customUrl } })
    await wrapper.vm.loadDetail()
    await nextTick()
    expect(wrapper.find('[data-testid="jdbc-resolution-required"]').exists()).toBe(false)
    wrapper.vm.form.host = 'another-reference'
    wrapper.vm.onJdbcPartChange()
    await nextTick()
    expect(wrapper.find('[data-testid="jdbc-resolution-required"]').exists()).toBe(true)
    wrapper.vm.form.host = 'reference-only'
    wrapper.vm.onJdbcPartChange()
    await nextTick()
    expect(wrapper.find('[data-testid="jdbc-resolution-required"]').exists()).toBe(false)
    expect(wrapper.vm.form.jdbcUrl).toBe(customUrl)
  })

  test('SSH模式说明结构化参数优先，不承诺保留URL即作为实际连接地址', async () => {
    wrapper.vm.form.connectionMode = 'SSH_TUNNEL'
    wrapper.vm.form.host = 'tunnel-target'
    wrapper.vm.onJdbcPartChange()
    await nextTick()
    const hint = wrapper.find('[data-testid="jdbc-mode-hint"]').text()
    expect(hint).toContain('SSH')
    expect(hint).toContain('结构化连接参数')
    expect(hint).not.toContain('实际连接以当前 JDBC URL 为准')
    expect(wrapper.find('[data-testid="keep-custom-jdbc-url"]').exists()).toBe(false)
  })

  test('手写URL保留精确字符，未处理的字段修改不会因编辑URL而被默默确认', async () => {
    const exactUrl = 'jdbc:mysql:loadbalance://node-a:3306,node-b:3307/Risk?Key=Case%2FSensitive'
    wrapper.vm.form.host = 'changed-reference'
    wrapper.vm.onJdbcPartChange()
    wrapper.vm.form.jdbcUrl = exactUrl
    wrapper.vm.onJdbcUrlChange()
    await nextTick()
    expect(wrapper.find('[data-testid="jdbc-resolution-required"]').exists()).toBe(true)
    await wrapper.find('[data-testid="keep-custom-jdbc-url"]').trigger('click')
    expect(wrapper.vm.normalizeForm(wrapper.vm.form).jdbcUrl).toBe(exactUrl)
    expect(wrapper.vm.form.jdbcAutoBuild).toBe(false)
  })

  test('不支持生成的数据库类型不能假称生成成功，仍可明确保留自定义URL', async () => {
    wrapper.vm.form.dbType = 'OTHER'
    wrapper.vm.onDbTypeChange('OTHER')
    await nextTick()
    expect(wrapper.vm.generateJdbcUrl(true)).toBe(false)
    expect(wrapper.vm.form.jdbcUrl).toBe(originalUrl)
    expect(wrapper.find('[data-testid="replace-jdbc-url"]').attributes('disabled')).toBe('true')
    await wrapper.find('[data-testid="keep-custom-jdbc-url"]').trigger('click')
    expect(wrapper.find('[data-testid="jdbc-resolution-required"]').exists()).toBe(false)
    expect(wrapper.vm.normalizeForm(wrapper.vm.form).jdbcUrl).toBe(originalUrl)
  })

  test('异步表单校验等待期间改连接字段，也不能继续发出旧URL请求', async () => {
    const validations = []
    wrapper.vm.$refs.form.validate = callback => validations.push(callback)
    wrapper.vm.handleSubmit()
    wrapper.vm.handleTestDraft()
    wrapper.vm.form.host = 'changed-while-validating'
    wrapper.vm.onJdbcPartChange()
    for (const validate of validations) await validate(true)
    expect(validations).toHaveLength(2)
    expect(databaseApi.updateDbDatasource).not.toHaveBeenCalled()
    expect(databaseApi.testDbDatasourceDraft).not.toHaveBeenCalled()
  })

  test('切回直连后仍要求处理在SSH模式下修改的连接字段', async () => {
    wrapper.vm.form.connectionMode = 'SSH_TUNNEL'
    wrapper.vm.form.host = 'ssh-target'
    wrapper.vm.onJdbcPartChange()
    wrapper.vm.form.connectionMode = 'DIRECT'
    await nextTick()
    expect(wrapper.find('[data-testid="jdbc-resolution-required"]').exists()).toBe(true)
    expect(wrapper.vm.form.jdbcUrl).toBe(originalUrl)
  })

  test('OTHER类型不承诺自动生成，复杂直连URL无需结构化地址即可显示就绪', async () => {
    databaseApi.getDbDatasource.mockResolvedValueOnce({ data: {
      id: 1, scope: 'GLOBAL', dbType: 'OTHER', connectionMode: 'DIRECT',
      host: '', databaseName: '', jdbcUrl: 'jdbc:custom:CaseSensitiveEndpoint', driverClassName: 'example.CustomDriver',
    } })
    await wrapper.vm.loadDetail()
    await nextTick()
    expect(wrapper.find('[data-testid="jdbc-mode-hint"]').text()).toContain('不支持自动生成')
    expect(wrapper.vm.configurationChecklist.find(item => item.key === 'connection').status).toBe('READY')
    wrapper.vm.form.dbType = 'ORACLE'
    wrapper.vm.keepCustomJdbcUrl()
    expect(wrapper.vm.configurationChecklist.find(item => item.key === 'connection').status).toBe('READY')
    wrapper.vm.form.driverClassName = ''
    expect(wrapper.vm.configurationChecklist.find(item => item.key === 'connection').status).toBe('READY')
    wrapper.vm.form.connectionMode = 'SSH_TUNNEL'
    expect(wrapper.vm.configurationChecklist.find(item => item.key === 'connection').status).toBe('PENDING')
  })
})
