import { mount } from '@test-utils'
import { nextTick } from 'vue'
import * as projectApi from '@/api/project'
import * as ruleListApi from '@/api/ruleList'
import ProjectFilterSelect from '@/components/ProjectFilterSelect.vue'
import ListLibrary from '@/views/ruleList/ListLibrary.vue'

const FormStub = {
  template: '<form><slot /></form>',
  methods: { validate: vi.fn(cb => cb(true)) }
}

async function flush() {
  await nextTick()
  await new Promise(resolve => setTimeout(resolve, 0))
}

async function mountPage(permissionValues) {
  projectApi.listProjects.mockResolvedValue({
    data: { records: [{ id: 1, projectName: 'Project A' }] }
  })
  ruleListApi.listLibraries.mockResolvedValue({
    data: {
      records: [{
        id: 9,
        projectId: 1,
        scope: 'PROJECT',
        listCode: 'mobile_black',
        listName: 'Mobile blacklist',
        listType: 'BLACK',
        status: 1,
        recordCount: 2
      }],
      total: 1
    }
  })
  const wrapper = mount(ListLibrary, {
    mocks: {
      $router: { push: vi.fn() },
      $message: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
      $confirm: vi.fn().mockResolvedValue(true)
    },
    stubs: {
      'el-form': FormStub,
      'el-form-item': true,
      'el-select': true,
      'el-option': true,
      'el-input': true,
      'el-button': true,
      'el-tag': true,
      'el-table': true,
      'el-table-column': true,
      'el-pagination': true,
      'el-dialog': true,
      'el-row': true,
      'el-col': true,
      'el-switch': true
    },
    directives: permissionValues ? {
      permission: {
        mounted: (_element, binding) => permissionValues.push(binding.value)
      }
    } : undefined
  })
  await flush()
  return wrapper
}

describe('ListLibrary governed changes', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountPage() })
  afterEach(() => {
    if (wrapper) wrapper.unmount()
    vi.clearAllMocks()
  })

  test('uses unified fuzzy filters and loads effective libraries', () => {
    expect(ListLibrary.components.ProjectFilterSelect).toBe(ProjectFilterSelect)
    expect(projectApi.listProjects).toHaveBeenCalled()
    expect(ruleListApi.listLibraries).toHaveBeenCalled()
    expect(wrapper.vm.tableData[0].listCode).toBe('mobile_black')
    expect(wrapper.vm.activeTab).toBe('list')
  })

  test('名单库变更入口受字段编辑权限控制', async () => {
    wrapper.unmount()
    const permissions = []
    wrapper = await mountPage(permissions)

    expect(permissions).toContain('field:edit')
  })

  test('new library creates an approval draft and opens it', async () => {
    ruleListApi.createLibraryDraft.mockResolvedValue({ data: { id: 81 } })
    wrapper.vm.handleCreate()
    wrapper.vm.form.projectId = 1
    wrapper.vm.form.listCode = 'id_black'
    wrapper.vm.form.listName = 'ID blacklist'

    wrapper.vm.submit()
    await flush()

    expect(ruleListApi.createLibraryDraft).toHaveBeenCalledWith(
      expect.objectContaining({
        projectId: 1,
        listCode: 'id_black',
        listName: 'ID blacklist'
      })
    )
    expect(wrapper.vm.$router.push).toHaveBeenCalledWith('/approval/81')
  })

  test('status change creates approval and restores effective switch state', async () => {
    ruleListApi.changeLibraryStatusDraft.mockResolvedValue({ data: { id: 82 } })
    const row = { ...wrapper.vm.tableData[0], status: 0 }

    await wrapper.vm.toggleStatus(row, 0)

    expect(ruleListApi.changeLibraryStatusDraft).toHaveBeenCalledWith(
      expect.objectContaining({ id: 9 }), 0
    )
    expect(row.status).toBe(1)
    expect(wrapper.vm.$router.push).toHaveBeenCalledWith('/approval/82')
  })

  test('delete creates approval instead of removing effective library', async () => {
    ruleListApi.deleteLibraryDraft.mockResolvedValue({ data: { id: 83 } })

    wrapper.vm.handleDelete(wrapper.vm.tableData[0])
    await flush()

    expect(ruleListApi.deleteLibraryDraft).toHaveBeenCalledWith(
      expect.objectContaining({ id: 9 })
    )
    expect(wrapper.vm.$router.push).toHaveBeenCalledWith('/approval/83')
  })
})
