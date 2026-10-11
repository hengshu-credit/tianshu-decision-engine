import { h } from '@logicflow/core'
import { DynamicGroup, DynamicGroupNode, DynamicGroupNodeModel } from '@logicflow/extension'

export function createGroupMiniMap(MiniMap) {
  return class GroupMiniMap extends MiniMap {
    initMiniMap() {
      super.initMiniMap()
      // 小地图只复制节点类型，不复制实例插件；折叠分组仍需插件处理成员与虚拟连线。
      this.lfMap.installPlugin(DynamicGroup)
      registerThemedDynamicGroup(this.lfMap)
    }
  }
}

export function registerThemedDynamicGroup(lf) {
  if (!lf.extension.dynamicGroup) return

  class ThemedDynamicGroupNode extends DynamicGroupNode {
    getOperateIcon() {
      const { model, graphModel } = this.props
      if (!model.collapsible || graphModel.isMiniMap) return null
      const x = model.x - model.width / 2 + 8
      const y = model.y - model.height / 2 + 8
      const label = model.isCollapsed ? '展开分组' : '折叠分组'
      const stopPropagation = event => event.stopPropagation()
      const toggle = event => {
        event.stopPropagation()
        model.toggleCollapse(!model.isCollapsed)
      }
      return h('g', {
        className: 'lf-group-toggle',
        role: 'button',
        tabindex: 0,
        'aria-label': label,
        'aria-expanded': !model.isCollapsed,
        onPointerDown: stopPropagation,
        onDblClick: stopPropagation,
        onClick: toggle,
        onKeyDown: event => {
          if (event.key !== 'Enter' && event.key !== ' ') return
          event.preventDefault()
          toggle(event)
        },
      }, [
        h('title', {}, label),
        h('rect', { x, y, width: 22, height: 22, rx: 4, ry: 4 }),
        h('path', {
          d: `M ${x + 6} ${y + 11} h 10${model.isCollapsed ? ` M ${x + 11} ${y + 6} v 10` : ''}`,
          fill: 'none',
          strokeWidth: 1.5,
          strokeLinecap: 'round',
          pointerEvents: 'none',
        }),
      ])
    }
  }

  lf.register({ type: 'dynamic-group', view: ThemedDynamicGroupNode, model: DynamicGroupNodeModel })
}
