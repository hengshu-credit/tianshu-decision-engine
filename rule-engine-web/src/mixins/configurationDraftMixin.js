import { registerDesignerLeaveGuard } from '@/utils/designerLeaveGuard'

// 与工作区关闭/刷新共用现有离开保护入口；配置和凭据仅留在组件内存中。
export default function configurationDraftMixin(readDraft) {
  return {
    data() { return { configurationBaseline: null, configurationLeaveApproved: false, configurationGuardUnregister: null } },
    computed: {
      configurationFingerprint() {
        const draft = readDraft(this)
        return JSON.stringify(draft, function (key, value) {
          if (['_editing', '_origin', '_saving'].includes(key)) return undefined
          // 新建成功分配的根资源 ID 不算用户修改；嵌套引用 ID 仍参与比较。
          if (key === 'id' && (this === draft.form || this === draft.model)) return undefined
          if (this === draft.form && ['_secretConfigured', 'configuredSecrets'].includes(key)) return undefined
          return value
        })
      },
      configurationHasUnsavedChanges() {
        return this.configurationBaseline !== null && this.configurationFingerprint !== this.configurationBaseline
      },
    },
    mounted() {
      this.registerConfigurationGuard()
      window.addEventListener('beforeunload', this.handleConfigurationBeforeUnload)
    },
    activated() { this.configurationLeaveApproved = false; this.registerConfigurationGuard() },
    beforeUnmount() {
      this.configurationGuardUnregister?.()
      window.removeEventListener('beforeunload', this.handleConfigurationBeforeUnload)
    },
    async beforeRouteLeave(to) {
      if (to.path?.startsWith('/expression/')) return true
      if (this.configurationLeaveApproved) { this.configurationLeaveApproved = false; return true }
      return this.confirmConfigurationLeave()
    },
    async beforeRouteUpdate(to, from) {
      if (to.params?.id === from.params?.id) return true
      return this.confirmConfigurationLeave()
    },
    methods: {
      markConfigurationSaved(fingerprint = this.configurationFingerprint) {
        this.configurationBaseline = fingerprint
        this.configurationLeaveApproved = false
      },
      registerConfigurationGuard() {
        if (!this.$route?.path) return
        this.configurationGuardUnregister?.()
        this.configurationGuardUnregister = registerDesignerLeaveGuard(this.$route.path, async () => {
          const accepted = await this.confirmConfigurationLeave()
          this.configurationLeaveApproved = accepted
          return accepted
        }, () => { this.configurationLeaveApproved = false })
      },
      async confirmConfigurationLeave() {
        if (!this.configurationHasUnsavedChanges) return true
        try {
          await this.$confirm('当前配置有未保存修改，离开或刷新可能丢失这些修改。仍要继续吗？', '未保存提醒', {
            type: 'warning', confirmButtonText: '继续离开', cancelButtonText: '继续编辑',
          })
          return true
        } catch { return false }
      },
      handleConfigurationBeforeUnload(event) {
        if (!this.configurationHasUnsavedChanges) return
        event.preventDefault()
        event.returnValue = ''
      },
    },
  }
}
