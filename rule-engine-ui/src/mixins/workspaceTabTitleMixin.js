export function syncWorkspaceTabTitle(store, route, detailTitle) {
  if (!route?.fullPath || !store?.getters?.['workspaceTabs/tabs']) return
  store.dispatch('workspaceTabs/updateDetailTitle', {
    fullPath: route.fullPath,
    path: route.path,
    detailTitle: typeof detailTitle === 'string' ? detailTitle : '',
  })
}

export default function workspaceTabTitleMixin(readTitle) {
  return {
    created() {
      // 固定到本组件所属页签，避免异步加载或缓存页面误改当前活动页签。
      const route = this.$route
      this.$watch(() => readTitle(this), (title, previous) => {
        if (!title && !previous) return
        syncWorkspaceTabTitle(this.$store, route, title)
      }, { immediate: true })
    },
  }
}
