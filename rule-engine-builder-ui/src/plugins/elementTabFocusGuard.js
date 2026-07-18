function isTabPane(vm) {
  return vm.$options && vm.$options.name === 'ElTabPane'
}

function activeTabElement(vm) {
  const tabs = vm.$parent
  if (!tabs || !tabs.$el) return null
  const paneId = `pane-${tabs.currentName}`
  return Array.from(tabs.$el.querySelectorAll('[role="tab"]'))
    .find(tab => tab.getAttribute('aria-controls') === paneId) || null
}

function syncPaneAccessibility(vm, active) {
  const pane = vm.$el
  if (!pane || !pane.setAttribute) return
  if (active) {
    pane.removeAttribute('inert')
    return
  }
  const focused = typeof document === 'undefined' ? null : document.activeElement
  if (focused && pane.contains(focused)) {
    const target = activeTabElement(vm)
    if (target && typeof target.focus === 'function') target.focus()
    else if (typeof focused.blur === 'function') focused.blur()
  }
  pane.setAttribute('inert', '')
}

export default {
  install(Vue) {
    Vue.mixin({
      mounted() {
        if (!isTabPane(this)) return
        syncPaneAccessibility(this, this.active)
        this._tabFocusGuardUnwatch = this.$watch(
          'active',
          active => syncPaneAccessibility(this, active),
          { sync: true }
        )
      },
      beforeDestroy() {
        if (this._tabFocusGuardUnwatch) this._tabFocusGuardUnwatch()
      }
    })
  }
}
