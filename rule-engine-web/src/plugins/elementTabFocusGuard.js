import { onMounted, onUpdated } from 'vue'

function isTabPane(vm) {
  return vm.$options && vm.$options.name === 'ElTabPane'
}

function activeTabElement(vm) {
  const tabs = vm.$parent
  if (!tabs || !tabs.$el) return null
  const selectedTab = tabs.$el.querySelector(
    '[role="tab"][aria-selected="true"]'
  )
  if (selectedTab) return selectedTab
  const activePane = tabs.$el.querySelector(
    '[role="tabpanel"][aria-hidden="false"]'
  )
  if (!activePane || !activePane.id) return null
  return (
    Array.from(tabs.$el.querySelectorAll('[role="tab"]')).find(
      tab => tab.getAttribute('aria-controls') === activePane.id
    ) || null
  )
}

function syncPaneAccessibility(vm) {
  const pane = vm.$el
  if (!pane || !pane.setAttribute) return
  const active = pane.getAttribute('aria-hidden') !== 'true'
  if (active) {
    if (pane.hasAttribute('inert')) pane.removeAttribute('inert')
    return
  }
  const focused = typeof document === 'undefined' ? null : document.activeElement
  if (focused && pane.contains(focused)) {
    const target = activeTabElement(vm)
    if (target && typeof target.focus === 'function') target.focus()
    else if (typeof focused.blur === 'function') focused.blur()
  }
  if (!pane.hasAttribute('inert')) pane.setAttribute('inert', '')
}

export default {
  install(app) {
    app.mixin({
      created() {
        if (!isTabPane(this)) return
        // lazy 页签初始只有注释节点，实际面板会在首次选中时创建。
        const sync = () => syncPaneAccessibility(this)
        onMounted(sync)
        onUpdated(sync)
      }
    })
  }
}
