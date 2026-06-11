// tests/__mocks__/element-ui.js
const mockPlugin = {}
const components = {}
const install = (Vue) => {
  mockPlugin.installed = true
}

module.exports = {
  default: { install },
  install,
  // Element UI 组件模拟
  Button: { name: 'ElButton', template: '<button><slot /></button>' },
  Input: { name: 'ElInput', template: '<input />' },
  Select: { name: 'ElSelect', template: '<select><slot /></select>' },
  Option: { name: 'ElOption', template: '<option><slot /></option>' },
  Table: { name: 'ElTable', template: '<table><slot /></table>' },
  TableColumn: { name: 'ElTableColumn', template: '<td><slot /></td>' },
  Dialog: { name: 'ElDialog', template: '<div><slot /></div>' },
  Form: { name: 'ElForm', template: '<form><slot /></form>' },
  FormItem: { name: 'ElFormItem', template: '<div><slot /></div>' },
  Tabs: { name: 'ElTabs', template: '<div><slot /></div>' },
  TabPane: { name: 'ElTabPane', template: '<div><slot /></div>' },
  Card: { name: 'ElCard', template: '<div><slot /></div>' },
  Descriptions: { name: 'ElDescriptions', template: '<dl><slot /></dl>' },
  DescriptionsItem: { name: 'ElDescriptionsItem', template: '<dd><slot /></dd>' },
  Tag: { name: 'ElTag', template: '<span><slot /></span>' },
  RadioGroup: { name: 'ElRadioGroup', template: '<div><slot /></div>' },
  RadioButton: { name: 'ElRadioButton', template: '<label><slot /></label>' },
  InputNumber: { name: 'ElInputNumber', template: '<input type="number" />' },
  SelectOption: { name: 'ElSelectOption', template: '<option><slot /></option>' },
  Tooltip: { name: 'ElTooltip', template: '<span><slot /></span>' },
  Divider: { name: 'ElDivider', template: '<hr><slot /></hr>' },
  Alert: { name: 'ElAlert', template: '<div><slot /></div>' },
  Collapse: { name: 'ElCollapse', template: '<div><slot /></div>' },
  CollapseItem: { name: 'ElCollapseItem', template: '<div><slot /></div>' },
  Loading: { directive: { bind: () => {} } },
  Message: { success: () => {}, error: () => {}, warning: () => {}, info: () => {} },
  Notification: { success: () => {}, error: () => {}, warning: () => {}, info: () => {} },
  MessageBox: { confirm: () => Promise.resolve(), alert: () => Promise.resolve() }
}

// 为每个组件添加 install 方法
Object.keys(components).forEach(name => {
  components[name].install = install
})

module.exports = components