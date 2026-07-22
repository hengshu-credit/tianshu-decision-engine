import { shallowMount } from '@test-utils'
import ProjectFilterSelect from '@/components/ProjectFilterSelect.vue'
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'
import { listProjects } from '@/api/project'

vi.mock('@/api/project', () => ({
  listProjects: vi.fn()
}))

function mountFilter(field) {
  return shallowMount(ProjectFilterSelect, {
    props: { value: '', field }
  })
}

describe('ProjectFilterSelect', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    listProjects.mockResolvedValue({ data: { records: [], total: 0 } })
  })

  test('项目编码筛选固定支持自由输入并按编码远程查询', async () => {
    const wrapper = mountFilter('projectCode')
    const remote = wrapper.findComponent(RemoteFilterSelect)

    expect(remote.props('allowFreeInput')).toBe(true)
    expect(remote.props('optionLabelKey')).toBe('projectCode')
    expect(remote.props('optionValueKey')).toBe('projectCode')

    await wrapper.vm.fetchOptions({ query: 'risk', pageNum: 2, pageSize: 20 })

    expect(listProjects).toHaveBeenCalledWith({
      pageNum: 2,
      pageSize: 20,
      projectCode: 'risk'
    })
  })

  test('项目名称筛选按名称远程查询并透传输入值', async () => {
    const wrapper = mountFilter('projectName')
    const remote = wrapper.findComponent(RemoteFilterSelect)

    await wrapper.vm.fetchOptions({ query: '风控', pageNum: 1, pageSize: 10 })
    remote.vm.$emit('update:value', '风控项目')
    remote.vm.$emit('change', '风控项目')

    expect(listProjects).toHaveBeenCalledWith({
      pageNum: 1,
      pageSize: 10,
      projectName: '风控'
    })
    expect(wrapper.emitted('input')[0]).toEqual(['风控项目'])
    expect(wrapper.emitted('update:value')[0]).toEqual(['风控项目'])
    expect(wrapper.emitted('change')[0]).toEqual(['风控项目'])
  })

  test('仅允许项目编码或项目名称字段', () => {
    expect(ProjectFilterSelect.props.field.validator('projectCode')).toBe(true)
    expect(ProjectFilterSelect.props.field.validator('projectName')).toBe(true)
    expect(ProjectFilterSelect.props.field.validator('projectId')).toBe(false)
  })
})
