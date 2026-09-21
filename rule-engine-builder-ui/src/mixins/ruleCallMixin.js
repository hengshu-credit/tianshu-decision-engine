import { getDefinition, listProjectDefinitions, validateCallCycle } from '@/api/definition'
import {
  normalizeRuleOptions,
  repairLegacyRuleCallRefs,
  validateRuleCallsInModel
} from '@/utils/ruleCallConfig'

function unwrap(response) {
  return response && response.data ? response.data : response
}

function records(response) {
  const data = unwrap(response)
  if (Array.isArray(data)) return data
  return data && Array.isArray(data.records) ? data.records : []
}

export default {
  data() {
    return {
      projectRules: [],
      currentRuleId: null,
      currentRuleCode: '',
      loadingRuleOptions: false,
      ruleOptionsLoadError: false,
      ruleOptionsRequestId: 0
    }
  },
  activated() {
    const id = this.definitionId || this.$route?.params?.id
    if (this.isOwnDesignerRoute && !this.isOwnDesignerRoute()) return
    if (id && /^\/designer\/(?!expression\/)/.test(this.$route?.path || '')) return this.loadRuleCallOptions(id)
  },
  beforeUnmount() { this.ruleOptionsRequestId++ },
  methods: {
    async loadRuleCallOptions(definitionId) {
      const requestId = ++this.ruleOptionsRequestId
      this.loadingRuleOptions = true
      this.ruleOptionsLoadError = false
      try {
        const definition = unwrap(await getDefinition(definitionId))
        if (requestId !== this.ruleOptionsRequestId) return
        if (!definition || definition.projectId == null) {
          this.projectRules = []
          return
        }
        this.currentRuleId = definition.id != null ? definition.id : definitionId
        this.currentRuleCode = definition.ruleCode || ''
        this.projectIdForRefs = definition.projectId
        const params = { pageNum: 1, pageSize: 1000 }
        if (Number(definition.projectId) === 0) params.scope = 'GLOBAL'
        const response = await listProjectDefinitions(definition.projectId, params)
        if (requestId !== this.ruleOptionsRequestId) return
        this.projectRules = normalizeRuleOptions(records(response))
      } catch (e) {
        if (requestId !== this.ruleOptionsRequestId) return
        this.projectRules = []
        this.ruleOptionsLoadError = true
      } finally {
        if (requestId === this.ruleOptionsRequestId) this.loadingRuleOptions = false
      }
    },
    repairLegacyRuleCallRefs(model) {
      return repairLegacyRuleCallRefs(model, this.projectRules)
    },
    validateRuleCallsInModel(model) {
      return validateRuleCallsInModel(model, {
        rules: this.projectRules,
        currentRuleId: this.currentRuleId != null ? this.currentRuleId : this.definitionId,
        currentRuleCode: this.currentRuleCode
      })
    },
    showRuleCallErrors(errors) {
      if (!errors || !errors.length) return
      this.$alert(errors.map((error, index) => (index + 1) + '. ' + error).join('\n'), '规则调用配置错误', { type: 'warning' })
    },
    validateRuleCallsBeforeSave(model) {
      this.repairLegacyRuleCallRefs(model)
      const errors = this.validateRuleCallsInModel(model)
      if (errors.length) this.showRuleCallErrors(errors)
      return errors.length === 0
    },
    async validateRuleCallCycle() {
      if (!this.definitionId || typeof this.buildRuleCallValidationModel !== 'function') return true
      const model = this.buildRuleCallValidationModel()
      this.repairLegacyRuleCallRefs(model)
      const localErrors = this.validateRuleCallsInModel(model)
      if (localErrors.length) return localErrors[0]
      try {
        const data = unwrap(await validateCallCycle(this.definitionId, JSON.stringify(model)))
        return data && data.valid === false ? (data.message || '规则调用存在环路') : true
      } catch (e) {
        return (e && e.message) || '规则调用环校验失败'
      }
    }
  }
}
