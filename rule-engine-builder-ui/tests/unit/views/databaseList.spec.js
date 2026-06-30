import { mount, createLocalVue } from '@vue/test-utils'
import Vue from 'vue'

jest.unmock('element-ui')
import ElementUI from 'element-ui'

import * as databaseApi from '@/api/database'
import * as projectApi from '@/api/project'
import DatabaseList from '@/views/database/DatabaseList.vue'

function createTestVue() {
  const localVue = createLocalVue()
  localVue.use(ElementUI)
  return localVue
}

async function mountPage() {
  projectApi.listProjects.mockResolvedValue({ data: { records: [{ id: 1, projectName: '项目A' }] } })
  databaseApi.listDbDatasources.mockResolvedValue({ data: { records: [], total: 0 } })

  const wrapper = mount(DatabaseList, {
    localVue: createTestVue(),
    mocks: {
      $message: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
      $confirm: jest.fn().mockResolvedValue(true)
    },
    stubs: {
      'el-form': { template: '<form><slot /></form>', methods: { validate: jest.fn(cb => cb(true)) } },
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
  await Vue.nextTick()
  await new Promise(resolve => setTimeout(resolve, 0))
  return wrapper
}

describe('DatabaseList — JDBC URL 生成', () => {
  let wrapper

  beforeEach(async () => {
    wrapper = await mountPage()
  })

  afterEach(() => {
    if (wrapper) wrapper.destroy()
    jest.clearAllMocks()
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
})
