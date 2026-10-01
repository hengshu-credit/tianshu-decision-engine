import * as testUtils from '@vue/test-utils'

export * from '@vue/test-utils'

const GLOBAL_OPTION_KEYS = [
  'components',
  'config',
  'directives',
  'mocks',
  'plugins',
  'provide',
  'renderStubDefaultSlot',
  'stubs',
]

const ElTableColumnStub = {
  name: 'ElTableColumn',
  props: ['prop', 'label'],
  template: '<div><slot name="header" :column="{}" /><slot :row="{}" :$index="0" /></div>'
}

export function normalizeMountOptions(options = {}) {
  const normalized = { ...options }
  const global = { ...(normalized.global || {}) }

  GLOBAL_OPTION_KEYS.forEach((key) => {
    if (normalized[key] === undefined) return
    global[key] = normalized[key]
    delete normalized[key]
  })

  if (Array.isArray(global.stubs)) {
    global.stubs = global.stubs.reduce((stubs, name) => {
      stubs[name] = true
      return stubs
    }, {})
  }

  ['el-input', 'el-input-number'].forEach((name) => {
    const stub = global.stubs && global.stubs[name]
    if (!stub || typeof stub !== 'object' || Array.isArray(stub)) return
    const props = Array.isArray(stub.props)
      ? [...new Set([...stub.props, 'size'])]
      : { ...(stub.props || {}), size: String }
    global.stubs = { ...global.stubs, [name]: { ...stub, props } }
  })

  if (global.stubs && global.stubs['el-table-column'] === true) {
    global.stubs = { ...global.stubs, 'el-table-column': ElTableColumnStub }
  }

  if (Object.keys(global).length) normalized.global = global
  return normalized
}

export function mount(component, options) {
  return testUtils.mount(component, normalizeMountOptions(options))
}

export function shallowMount(component, options) {
  return testUtils.shallowMount(component, normalizeMountOptions(options))
}
