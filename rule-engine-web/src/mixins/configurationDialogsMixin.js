import configurationDraftMixin from './configurationDraftMixin'

// 列表页编辑弹窗与详情页共用路由、工作区和浏览器离开保护。
export default function configurationDialogsMixin(dialogs) {
  return {
    mixins: [configurationDraftMixin(vm => Object.fromEntries(dialogs.map(dialog => [dialog.visible, vm[dialog.visible] ? dialog.draft(vm) : null])),
      vm => dialogs.some(dialog => vm[dialog.visible] && dialog.busy(vm)))],
    watch: Object.fromEntries(dialogs.map(dialog => [dialog.visible, { flush: 'sync', handler() { this.markConfigurationDialogSaved(dialog.visible) } }])),
    methods: {
      markConfigurationDialogSaved(visible) {
        const current = JSON.parse(this.configurationFingerprint)
        const baseline = this.configurationBaseline == null ? current : JSON.parse(this.configurationBaseline)
        baseline[visible] = current[visible]
        this.configurationBaseline = JSON.stringify(baseline)
        this.configurationLeaveApproved = false
      },
      configurationDialogChanged(visible) {
        if (this.configurationBaseline == null) return true
        const current = JSON.parse(this.configurationFingerprint)
        const baseline = JSON.parse(this.configurationBaseline)
        return JSON.stringify(current[visible]) !== JSON.stringify(baseline[visible])
      },
      finishUnchangedEdit(visible, existingId) {
        if (!existingId || !this[visible] || this.configurationDialogChanged(visible)) return false
        this.$message.info('配置未修改，无需保存')
        this[visible] = false
        return true
      },
      async closeConfigurationDialog(visible, done) {
        const changed = this.configurationDialogChanged(visible)
        if (!dialogs.some(dialog => dialog.visible === visible) || !(await this.confirmConfigurationLeave(changed))) return
        this[visible] = false
        if (typeof done === 'function') done()
      },
    },
  }
}
