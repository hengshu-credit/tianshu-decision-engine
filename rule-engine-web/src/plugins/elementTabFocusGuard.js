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
  install(app) {
    app.mixin({
      mounted() {
        if (!isTabPane(this)) return
        syncPaneAccessibility(this)
        this._tabFocusGuardObserver = new MutationObserver(() => {
          syncPaneAccessibility(this)
        })
        this._tabFocusGuardObserver.observe(this.$el, {
          attributes: true,
          attributeFilter: ['aria-hidden']
        })
      },
      beforeUnmount() {
        if (this._tabFocusGuardObserver) {
          this._tabFocusGuardObserver.disconnect()
          this._tabFocusGuardObserver = null
        }
      }
    })
  }
}
