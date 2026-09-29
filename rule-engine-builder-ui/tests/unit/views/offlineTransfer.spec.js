import { shallowMount } from '@test-utils'

vi.mock('@/api/transfer', () => ({
  exportResourceTransfer: vi.fn(),
  previewResourceTransfer: vi.fn(),
  importResourceTransfer: vi.fn(),
}))

import * as transferApi from '@/api/transfer'
import OfflineTransfer from '@/views/transfer/OfflineTransfer.vue'

describe('OfflineTransfer', () => {
  beforeEach(() => vi.clearAllMocks())

  test('项目级导入可选择新建项目并保留冲突与规则发布策略', () => {
    const wrapper = shallowMount(OfflineTransfer)
    wrapper.vm.options = {
      ...wrapper.vm.options,
      targetScope: 'PROJECT',
      targetProjectId: '',
      createProject: true,
      projectCode: 'new_credit',
      projectName: '新授信项目',
      publishRules: true,
      variablePolicy: 'SUFFIX',
      resourcePolicy: 'OVERWRITE',
      suffix: '_staging',
    }

    expect(wrapper.vm.normalizedOptions()).toEqual(expect.objectContaining({
      targetScope: 'PROJECT',
      targetProjectId: null,
      projectCode: 'new_credit',
      projectName: '新授信项目',
      publishRules: true,
      variablePolicy: 'SUFFIX',
      resourcePolicy: 'OVERWRITE',
      suffix: '_staging',
    }))
    expect(wrapper.vm.validateTargetOptions()).toBe(true)
    wrapper.unmount()
  })

  test('项目级导入未绑定项目且未选择新建时阻止预检', () => {
    const wrapper = shallowMount(OfflineTransfer)
    wrapper.vm.options.targetScope = 'PROJECT'
    wrapper.vm.options.targetProjectId = ''
    wrapper.vm.options.createProject = false

    expect(wrapper.vm.validateTargetOptions()).toBe(false)
    wrapper.unmount()
  })

  test('预检使用目标作用域和冲突选项发送配置包', async () => {
    transferApi.previewResourceTransfer.mockResolvedValue({
      data: { packageDigest: 'digest-1', conflictCount: 0, resources: [], roots: [] },
    })
    const wrapper = shallowMount(OfflineTransfer)
    const file = new File(['zip'], 'transfer.zip', { type: 'application/zip' })
    wrapper.vm.selectFile({ raw: file })
    wrapper.vm.options.targetScope = 'GLOBAL'

    await wrapper.vm.previewPackage()

    expect(transferApi.previewResourceTransfer).toHaveBeenCalledWith(
      file,
      expect.objectContaining({ targetScope: 'GLOBAL', targetProjectId: null })
    )
    expect(wrapper.vm.preview.packageDigest).toBe('digest-1')
    wrapper.unmount()
  })
})
