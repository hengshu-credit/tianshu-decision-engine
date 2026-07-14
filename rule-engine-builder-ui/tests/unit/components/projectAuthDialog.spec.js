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
      'el-alert': true, 'el-empty': true
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
