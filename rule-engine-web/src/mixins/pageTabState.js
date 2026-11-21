import { restorePageState, savePageState } from '@/utils/pageStateCache'

// 与工作区共用会话/身份清理机制。每个详情地址、项目和直达查询独立记录。
export function pageTabState(property, allowed) {
  function options(vm) { return typeof allowed === 'function' ? allowed(vm) : allowed }
  function cacheKey(vm) {
    const route = vm.$route
    if (!route?.fullPath) return null
    const project = route.query?.projectId || vm.$store?.state?.currentProject?.id || ''
    return 'tabs:' + project + ':' + route.fullPath
  }
  return {
    methods: {
      rememberedPageTab() {
        const tab = restorePageState(cacheKey(this)).tab
        return options(this).includes(tab) ? tab : null
      },
    },
    created() {
      const key = cacheKey(this)
      if (!key) return
      const cached = this.rememberedPageTab()
      if (cached) this[property] = cached
      this.$watch(property, tab => {
        if (key === cacheKey(this) && options(this).includes(tab)) savePageState(key, { tab })
      }, { flush: 'sync' })
    },
  }
}
