import { mount, shallowMount } from '@test-utils'
import ProjectList from '@/views/project/ProjectList.vue'
import ProjectDetail from '@/views/project/ProjectDetail.vue'
import RuleList from '@/views/rule/RuleList.vue'
import VariableList from '@/views/variable/VariableList.vue'
import ListLibrary from '@/views/ruleList/ListLibrary.vue'
import DatasourceList from '@/views/datasource/DatasourceList.vue'
import DatabaseList from '@/views/database/DatabaseList.vue'
import ModelList from '@/views/model/ModelList.vue'
import FunctionList from '@/views/function/FunctionList.vue'
import ExperimentList from '@/views/experiment/ExperimentList.vue'
import BillingList from '@/views/billing/BillingList.vue'

const SlotStub = {
  template: '<div><slot /></div>',
}

const TabsStub = {
  name: 'ElTabs',
  props: {
    type: {
      type: String,
      default: '',
    },
  },
  template: '<div class="tabs-probe" :data-tab-type="type || \'default\'"><slot /></div>',
}

const FormStub = {
  name: 'ElForm',
  template: '<form><slot /></form>',
}

const commonOptions = {
  mocks: {
    $route: { params: {}, query: {} },
    $router: { push: vi.fn(), replace: vi.fn() },
    $store: { state: { currentProject: null } },
    $message: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    $confirm: vi.fn(),
  },
  directives: {
    permission: () => {},
  },
  renderStubDefaultSlot: true,
  stubs: {
    'router-link': SlotStub,
    'el-tabs': TabsStub,
    'el-tab-pane': SlotStub,
    'el-form': FormStub,
    'el-form-item': SlotStub,
    'el-button': SlotStub,
    'el-dropdown': SlotStub,
    'el-dropdown-menu': SlotStub,
    'el-dropdown-item': SlotStub,
    'el-icon': SlotStub,
    'el-alert': SlotStub,
    'el-row': SlotStub,
    'el-col': SlotStub,
    'el-table': true,
    'el-table-column': true,
    'el-pagination': true,
    'el-dialog': true,
    'el-select': true,
    'el-option': true,
    'el-input': true,
    'el-input-number': true,
    'el-date-picker': true,
    'el-switch': true,
    'el-tag': true,
    'el-tooltip': true,
    'el-upload': true,
    'el-collapse': true,
    'el-collapse-item': true,
    'el-radio-group': true,
    'el-radio': true,
    'el-checkbox': true,
    'el-checkbox-group': true,
    'el-empty': true,
    'variable-toolbar-actions': {
      template: '<div class="uiue-btn-bar variable-toolbar-actions" />',
    },
  },
}

function renderPage(component, factory = shallowMount) {
  return factory(
    {
      ...component,
      mixins: [],
      created() {},
      mounted() {},
    },
    commonOptions
  )
}

const toolbarPages = [
  ['项目管理', ProjectList, 1],
  ['项目规则', ProjectDetail, 1],
  ['规则管理', RuleList, 1],
  ['名单管理', ListLibrary, 1],
  ['外数管理', DatasourceList, 2],
  ['数据库管理', DatabaseList, 1],
  ['模型管理', ModelList, 1],
  ['函数管理', FunctionList, 1],
  ['分流实验', ExperimentList, 1],
  ['账单管理', BillingList, 2],
]

describe('管理页面筛选工具栏布局', () => {
  test.each(toolbarPages)('%s 的操作按钮位于筛选表单右侧', (_name, component, count) => {
    const wrapper = renderPage(component)
    const toolbars = wrapper.findAll('.uiue-filter-toolbar')

    expect(toolbars).toHaveLength(count)
    toolbars.forEach((toolbar) => {
      const form = toolbar.find('form')
      const actions = toolbar.find('.uiue-btn-bar')

      expect(form.exists()).toBe(true)
      expect(actions.exists()).toBe(true)
      expect(
        form.element.compareDocumentPosition(actions.element) &
          Node.DOCUMENT_POSITION_FOLLOWING
      ).not.toBe(0)
    })

    wrapper.unmount()
  })

  test('变量管理的每个页签都在筛选项右侧展示同一操作组', () => {
    const wrapper = renderPage(VariableList, mount)
    const filterRows = wrapper.findAll('.tab-filter-row')

    expect(filterRows).toHaveLength(4)
    filterRows.forEach((filterRow) => {
      const fields = filterRow.find('.tab-filter-fields')
      const actions = filterRow.find('.variable-toolbar-actions')

      expect(fields.exists()).toBe(true)
      expect(actions.exists()).toBe(true)
      expect(actions.element.parentElement).toBe(filterRow.element)
    })

    wrapper.unmount()
  })
})

describe('管理页面 Tab 风格', () => {
  test.each([
    ['外数管理', DatasourceList],
    ['模型管理', ModelList],
    ['名单管理', ListLibrary],
    ['变量管理', VariableList],
  ])('%s 使用普通下划线 Tab', (_name, component) => {
    const wrapper = renderPage(component)

    expect(wrapper.find('.tabs-probe').attributes('data-tab-type')).toBe('default')

    wrapper.unmount()
  })
})
