import { createGroupMiniMap, registerThemedDynamicGroup } from '@/components/flow/dynamicGroupNode'
import { DynamicGroup } from '@logicflow/extension'

// Vitest 将 LogicFlow core 和 extension 都映射到同一个空模块。
vi.mock('@logicflow/core', () => ({
  h: (type, props, ...children) => ({ type, props: { ...props, children } }),
  DynamicGroupNode: class {
    constructor(props) { this.props = props }
  },
  DynamicGroupNodeModel: class {},
  DynamicGroup: class {},
}))

function createToggle(collapsed = false) {
  const lf = { extension: { dynamicGroup: {} }, register: vi.fn() }
  registerThemedDynamicGroup(lf)
  const { view } = lf.register.mock.calls[0][0]
  const model = {
    x: 200, y: 150, width: 300, height: 200, collapsible: true, isCollapsed: collapsed,
    toggleCollapse: vi.fn(function (value) { this.isCollapsed = value }),
  }
  return { model, view: new view({ model, graphModel: { isMiniMap: false } }) }
}

test('保留插件的折叠模型，点击可收起并重新展开分组', () => {
  const { model, view } = createToggle()
  const event = { stopPropagation: vi.fn() }
  let control = view.getOperateIcon()
  expect(control.props['aria-label']).toBe('折叠分组')
  expect(control.props['aria-expanded']).toBe(true)
  control.props.onClick(event)
  expect(event.stopPropagation).toHaveBeenCalledOnce()
  expect(model.toggleCollapse).toHaveBeenLastCalledWith(true)
  control = view.getOperateIcon()
  expect(control.props['aria-label']).toBe('展开分组')
  expect(control.props['aria-expanded']).toBe(false)
  control.props.onClick(event)
  expect(model.toggleCollapse).toHaveBeenLastCalledWith(false)
})

test.each(['Enter', ' '])('键盘 %s 切换分组且不触发画布快捷键', key => {
  const { model, view } = createToggle(true)
  const control = view.getOperateIcon()
  expect(control.props.role).toBe('button')
  expect(control.props.tabindex).toBe(0)
  const event = { key, preventDefault: vi.fn(), stopPropagation: vi.fn() }
  control.props.onKeyDown(event)
  expect(model.toggleCollapse).toHaveBeenCalledWith(false)
  expect(event.preventDefault).toHaveBeenCalledOnce()
  expect(event.stopPropagation).toHaveBeenCalledOnce()
})

test('拖动和双击按钮不冒泡到画布，其他按键不切换分组', () => {
  const { model, view } = createToggle()
  const control = view.getOperateIcon()
  const event = { key: 'Tab', preventDefault: vi.fn(), stopPropagation: vi.fn() }
  control.props.onPointerDown(event)
  control.props.onDblClick(event)
  control.props.onKeyDown(event)
  expect(event.stopPropagation).toHaveBeenCalledTimes(2)
  expect(event.preventDefault).not.toHaveBeenCalled()
  expect(model.toggleCollapse).not.toHaveBeenCalled()
  model.collapsible = false
  expect(view.getOperateIcon()).toBeNull()
})

test('未启用分组插件的画布不注册分组节点', () => {
  const lf = { extension: {}, register: vi.fn() }
  registerThemedDynamicGroup(lf)
  expect(lf.register).not.toHaveBeenCalled()
})

test('小地图启用分组插件后复用主题节点，预览不提供折叠按钮', () => {
  const installPlugin = vi.fn(function (plugin) {
    expect(plugin).toBe(DynamicGroup)
    this.extension.dynamicGroup = {}
  })
  class MiniMap {
    initMiniMap() {
      this.lfMap = { extension: {}, installPlugin, register: vi.fn() }
    }
  }
  const GroupMiniMap = createGroupMiniMap(MiniMap)
  const preview = new GroupMiniMap()
  preview.initMiniMap()
  expect(installPlugin).toHaveBeenCalledOnce()
  const { view } = preview.lfMap.register.mock.calls[0][0]
  const control = new view({ model: { collapsible: true }, graphModel: { isMiniMap: true } })
  expect(control.getOperateIcon()).toBeNull()
})
