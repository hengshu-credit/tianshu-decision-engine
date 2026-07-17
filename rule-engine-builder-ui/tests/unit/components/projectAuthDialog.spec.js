import { createLocalVue, mount } from '@vue/test-utils'
import Vue from 'vue'
import * as projectApi from '@/api/project'
import ProjectAuthDialog from '@/views/project/ProjectAuthDialog.vue'

function localVue() {
  const vue = createLocalVue()
  vue.prototype.$message = { success: jest.fn(), error: jest.fn(), warning: jest.fn() }
  vue.prototype.$confirm = jest.fn().mockResolvedValue(true)
  return vue
}

async function mountDialog() {
  projectApi.listProjectAuths.mockResolvedValue({
    code: 200,
    data: [{
      id: 11,
      authCode: 'BASIC_MAIN',
      authName: '主账号',
      authType: 'BASIC',
      identifierMasked: 'part****tner',
      secretMasked: 'secr****cret',
      tokenTtlSeconds: 7200,
      tokenGraceSeconds: 600,
      status: 1
    }]
  })
  projectApi.listProjectAuthAccessLogs.mockResolvedValue({ data: { records: [], total: 0 } })
  const wrapper = mount(ProjectAuthDialog, {
    localVue: localVue(),
    propsData: {
      visible: true,
      project: { id: 7, projectCode: 'credit', projectName: '授信决策' }
    },
    stubs: {
      'el-dialog': true, 'el-tabs': true, 'el-tab-pane': true,
      'el-table': true, 'el-table-column': true, 'el-tag': true,
      'el-button': true, 'el-form': true, 'el-form-item': true,
      'el-input': true, 'el-select': true, 'el-option': true,
      'el-switch': true, 'el-input-number': true, 'el-pagination': true,
      'el-alert': true, 'el-empty': true, 'el-date-picker': true
    }
  })
  await Vue.nextTick()
  await new Promise(resolve => setTimeout(resolve, 20))
  return wrapper
}

describe('ProjectAuthDialog', () => {
  afterEach(() => jest.clearAllMocks())

  test('打开时按项目加载脱敏鉴权配置', async () => {
    const wrapper = await mountDialog()

    expect(projectApi.listProjectAuths).toHaveBeenCalledWith(7)
    expect(wrapper.vm.authList).toHaveLength(1)
    expect(wrapper.vm.authList[0].secret).toBeUndefined()
    expect(wrapper.vm.authList[0].secretMasked).toBe('secr****cret')
    wrapper.destroy()
  })

  test('新建 API Key 时允许服务端自动生成密钥', async () => {
    const wrapper = await mountDialog()
    projectApi.createProjectAuth.mockResolvedValue({ code: 200, data: { id: 12 } })
    wrapper.vm.openCreateAuth()
    wrapper.vm.authForm = {
      ...wrapper.vm.authForm,
      authCode: 'PARTNER_API',
      authName: '合作方',
      authType: 'API_KEY',
      placement: 'HEADER',
      parameterName: 'X-Partner-Key',
      secret: ''
    }
    wrapper.vm.$refs.authForm = { validate: callback => callback(true) }

    wrapper.vm.submitAuth()
    await new Promise(resolve => setTimeout(resolve, 20))

    expect(projectApi.createProjectAuth).toHaveBeenCalledWith(7, expect.objectContaining({
      authCode: 'PARTNER_API',
      authType: 'API_KEY',
      secret: ''
    }))
    wrapper.destroy()
  })

  test('按鉴权方式动态校验必填凭证', async () => {
    const wrapper = await mountDialog()
    wrapper.vm.authForm.authType = 'BASIC'
    let error
    wrapper.vm.authRules.identifier[0].validator({ field: 'identifier' }, '', value => { error = value })
    expect(error).toBeInstanceOf(Error)

    wrapper.vm.authForm.authType = 'HMAC_SHA256'
    error = 'pending'
    wrapper.vm.authRules.identifier[0].validator({ field: 'identifier' }, '', value => { error = value })
    expect(error).toBeUndefined()

    wrapper.vm.authForm.authType = 'API_KEY'
    wrapper.vm.authRules.parameterName[0].validator({ field: 'parameterName' }, '', value => { error = value })
    expect(error).toBeInstanceOf(Error)
    wrapper.destroy()
  })

  test('访问审计支持鉴权方式和日期筛选', async () => {
    const wrapper = await mountDialog()
    wrapper.vm.logQuery.authType = 'BASIC'
    wrapper.vm.logDateRange = ['2026-07-01', '2026-07-15']

    await wrapper.vm.loadAccessLogs()

    expect(projectApi.listProjectAuthAccessLogs).toHaveBeenLastCalledWith(7, expect.objectContaining({
      authType: 'BASIC', beginTime: '2026-07-01', endTime: '2026-07-15'
    }))
    wrapper.destroy()
  })

  test('可再次获取并展示完整账号密码', async () => {
    const wrapper = await mountDialog()
    projectApi.getProjectAuthFull.mockResolvedValue({
      code: 200,
      data: { id: 11, identifier: 'partner', secret: 'secret' }
    })

    await wrapper.vm.viewFullAuth({ id: 11 })

    expect(projectApi.getProjectAuthFull).toHaveBeenCalledWith(7, 11)
    expect(wrapper.vm.fullCredential.identifier).toBe('partner')
    expect(wrapper.vm.fullCredential.secret).toBe('secret')
    expect(wrapper.vm.fullDialogVisible).toBe(true)
    wrapper.destroy()
  })

  test('可重置 API Key 并立即展示新完整值', async () => {
    const wrapper = await mountDialog()
    projectApi.regenerateProjectAuthSecret.mockResolvedValue({
      data: { id: 12, authType: 'API_KEY', secret: 'new-api-key' }
    })

    await wrapper.vm.regenerateAuth({ id: 12, authType: 'API_KEY' })

    expect(projectApi.regenerateProjectAuthSecret).toHaveBeenCalledWith(7, 12)
    expect(wrapper.vm.fullCredential.secret).toBe('new-api-key')
    expect(wrapper.vm.fullDialogVisible).toBe(true)
    wrapper.destroy()
  })

  test('兼容令牌在鉴权弹窗内完成重置', async () => {
    const wrapper = await mountDialog()
    projectApi.regenerateToken.mockResolvedValue({ data: 'new-legacy-token' })

    await wrapper.vm.regenerateLegacyToken({ authType: 'LEGACY_TOKEN' })

    expect(projectApi.regenerateToken).toHaveBeenCalledWith(7)
    expect(wrapper.vm.fullCredential.secret).toBe('new-legacy-token')
    expect(wrapper.vm.fullDialogVisible).toBe(true)
    wrapper.destroy()
  })

  test('Token 列表和撤销操作都绑定当前鉴权', async () => {
    const wrapper = await mountDialog()
    projectApi.listProjectAuthTokens.mockResolvedValue({
      data: { records: [{ id: 21, tokenCode: 'TOKEN_A', status: 1 }], total: 1 }
    })
    projectApi.revokeProjectAuthToken.mockResolvedValue({ code: 200 })

    await wrapper.vm.openTokens({ id: 11, authCode: 'BASIC_MAIN' })
    await wrapper.vm.revokeToken({ id: 21 })

    expect(projectApi.listProjectAuthTokens).toHaveBeenCalledWith(7, 11, expect.objectContaining({ pageNum: 1 }))
    expect(projectApi.revokeProjectAuthToken).toHaveBeenCalledWith(7, 11, 21)
    wrapper.destroy()
  })
})
