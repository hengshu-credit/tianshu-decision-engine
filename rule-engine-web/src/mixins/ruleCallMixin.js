import { getDefinition, listProjectDefinitions, listPublishedVersions, validateCallCycle } from '@/api/definition'
import {
  normalizeRuleOptions,
  collectRuleCallsInModel,
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
  provide() { return {
    registerRuleCallPublishedVersions: this.registerRuleCallPublishedVersions,
    getRuleCallCatalog: () => ({ mode: this.referenceCatalogMode || 'LIVE', key: this.referenceCatalogKey || '', rules: this.projectRules })
  } },
  data() {
    return {
      projectRules: [],
      currentRuleId: null,
      currentRuleCode: '',
      loadingRuleOptions: false,
      ruleOptionsLoadError: false,
      ruleOptionsRequestId: 0,
      ruleFieldRequestId: 0
    }
  },
  activated() {
    const id = this.definitionId || this.$route?.params?.id
    if (this.isOwnDesignerRoute && !this.isOwnDesignerRoute()) return
    if (id && /^\/designer\/(?!expression\/)/.test(this.$route?.path || '')) return this.loadRuleCallOptions(id)
  },
  beforeUnmount() { this.ruleOptionsRequestId++; this.ruleFieldRequestId++ },
  methods: {
    invalidateRuleCallOptions() {
      this.ruleOptionsRequestId++; this.ruleFieldRequestId++
      this.projectRules = []; this.loadingRuleOptions = false; this.ruleOptionsLoadError = false
    },
    applyFrozenRuleCallOptions(rules, definitionId) {
      this.invalidateRuleCallOptions()
      const grouped = new Map()
      for (const entry of rules) {
        const id = String(entry.id)
        const current = grouped.get(id) || { ...entry, status: 1, executionVersions: [] }
        current.executionVersions.push({ bindingId: entry.versionBindingId, version: entry.version,
          revisionId: entry.revisionId, artifactDigest: entry.artifactDigest,
          available: Array.isArray(entry.inputFields) && Array.isArray(entry.outputFields),
          inputFields: entry.inputFields || [], outputFields: entry.outputFields || [] })
        grouped.set(id, current)
      }
      this.projectRules = normalizeRuleOptions([...grouped.values()])
      this.currentRuleId = definitionId; this.currentRuleCode = ''
    },
    registerRuleCallPublishedVersions({ ruleId, versions, catalogKey }) {
      if (this.referenceCatalogMode && this.referenceCatalogMode !== 'LIVE') return
      if (catalogKey != null && catalogKey !== (this.referenceCatalogKey || '')) return
      this.projectRules = this.projectRules.map(rule => String(rule.id) === String(ruleId)
        ? { ...rule, executionVersions: versions } : rule)
    },
    async loadRuleCallPublishedVersions(model) {
      if (this.referenceCatalogMode && this.referenceCatalogMode !== 'LIVE') return
      const context = this.ruleOptionsRequestId
      const requestId = ++this.ruleFieldRequestId
      const available = new Set(this.projectRules.map(rule => String(rule.id)))
      const ids = [...new Set(collectRuleCallsInModel(model).map(call => String(call.ruleId)).filter(id => available.has(id)))]
      for (let start = 0; start < ids.length; start += 4) {
        await Promise.all(ids.slice(start, start + 4).map(async ruleId => {
          const response = await listPublishedVersions(ruleId)
          if (context !== this.ruleOptionsRequestId || requestId !== this.ruleFieldRequestId) throw new Error('规则上下文已变化，请重新校验')
          const versions = unwrap(response)
          if (!Array.isArray(versions)) throw new Error('发布版本字段读取失败，请重试')
          this.registerRuleCallPublishedVersions({ ruleId, versions })
        }))
      }
    },
    async loadRuleCallOptions(definitionId, { catalogReady = false } = {}) {
      if (this.usesDesignerReferenceCatalog && !catalogReady) {
        if (this.draftGuardPromise) await this.draftGuardPromise
        if (this.referenceCatalogPromise) await this.referenceCatalogPromise
        return
      }
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
      if (this.referenceCatalogMode && this.referenceCatalogMode !== 'LIVE') return 0
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
      if (this.referenceCatalogMode === 'FROZEN') return true
      if (this.referenceCatalogMode === 'UNAVAILABLE') return false
      this.repairLegacyRuleCallRefs(model)
      const errors = this.validateRuleCallsInModel(model)
      if (errors.length) this.showRuleCallErrors(errors)
      return errors.length === 0
    },
    async validateRuleCallCycle() {
      if (this.referenceCatalogMode === 'FROZEN') return true
      if (this.referenceCatalogMode === 'UNAVAILABLE') return '所选来源的引用目录不可用，请重试或先完成离线迁移'
      if (!this.definitionId || typeof this.buildRuleCallValidationModel !== 'function') return true
      const model = this.buildRuleCallValidationModel()
      this.repairLegacyRuleCallRefs(model)
      const snapshot = JSON.stringify(model)
      try {
        await this.loadRuleCallPublishedVersions(model)
        if (JSON.stringify(this.buildRuleCallValidationModel()) !== snapshot) return '规则配置已变化，请重新校验'
        const localErrors = this.validateRuleCallsInModel(model)
        if (localErrors.length) return localErrors[0]
        const data = unwrap(await validateCallCycle(this.definitionId, JSON.stringify(model)))
        return data && data.valid === false ? (data.message || '规则调用存在环路') : true
      } catch (e) {
        return (e && e.message) || '规则调用环校验失败'
      }
    }
  }
}
