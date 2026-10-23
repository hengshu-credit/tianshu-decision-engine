/**
 * varPickerMixin
 * 在设计器页面中混入此 mixin，自动加载当前规则所属项目的变量/常量/对象字段。
 *
 * 提供分层联动选择：
 *   - 普通变量：如 taxAmount（rule_variable 且非 CONSTANT）
 *   - 常量：如 scriptName（var_source=CONSTANT，不再使用 组.常量）
 *   - 对象字段：如 对象 TaxRequest 中的 amount
 *
 * 提供：
 *   - projectRefs: { refCode, refLabel, varType, varObj, category }[] — 可选的引用列表
 *   - projectVars: RuleVariable[]  — 兼容旧逻辑，为 projectRefs 的扁平变量
 *   - loadingVars: boolean
 *   - getVarByIdentity(id, type)   — 按 ID + ref_type 精确查找
 *   - getVarOptions(refCode)       — ENUM 选项
 */

import { getDefinition, getRuleReferenceCatalog } from '@/api/definition'
import { syncWorkspaceTabTitle } from '@/mixins/workspaceTabTitleMixin'
import { listVariablesByProject, getVariableOptions } from '@/api/variable'
import { getVariableTree, getDataObjectFieldOptions } from '@/api/dataObject'
import { listAllFunctionsByProject } from '@/api/function'
import { listAllModelsByProject } from '@/api/model'
import { listLibraries } from '@/api/ruleList'
import { listAvailableApiConfigs } from '@/api/datasource'
import { API_MODULE_FIELDS, parseExecution } from '@/utils/apiExecution'
import { apiStagePaths } from '@/utils/apiContext'
import { varTypeTagColor, varTypeLabel as _varTypeLabel } from '@/constants/varTypes'
import { makeRefLabel } from '@/utils/varDisplay'
import { buildPickerOptions, buildReferenceCatalog } from '@/utils/referenceCatalog'
import { collectOperandReferences, syncOperandReference } from '@/utils/operand'
import { requireExecutionIdentity } from '@/utils/ruleTestExecutionSource'
import { hasPermission } from '@/security/permissionState'

function designerCatalogRequest(view, definitionId, refreshToken) {
  const source = view?.state === 'VERSION'
    ? { sourceType: 'VERSION', sourceId: String(view.sourceId || view.id) }
    : view?.state === 'LEGACY' || view?.sourceType === 'LEGACY_CONTENT'
      ? { sourceType: 'WORKSPACE', sourceId: String(definitionId) }
      : { sourceType: 'REVISION', sourceId: String(view?.id || '') }
  return { source, key: `${definitionId}:${source.sourceType}:${source.sourceId}:${refreshToken}` }
}

// 变量选择 Mixin

export default {
  data() {
    return {
      projectVars: [],
      projectRefs: [],
      projectFunctions: [],
      projectLists: [],
      projectApis: [],
      loadingVars: false,
      /** 变量加载是否失败 */
      varsLoadError: false,
      varOptionsByCode: {},
      projectIdForRefs: null,
      referenceCatalogMode: 'LIVE',
      frozenReferenceCatalog: null,
      referenceCatalogPromise: null,
      referenceCatalogKey: '',
      referenceCatalogRequestId: 0,
      referenceCatalogError: '',
    }
  },

  computed: {
    /** VarPicker 使用的选项列表（分层：普通变量 / 常量 / 对象字段） */
    varPickerOptions() {
      return buildPickerOptions({ refs: this.projectRefs }).map(item => ({ ...item, catalogMode: this.referenceCatalogMode }))
    },
    selectedVarPickerOptions() {
      if (typeof this.collectSelectedVarItems !== 'function') return []
      const result = []
      const seen = {}
      ;(this.collectSelectedVarItems() || []).forEach(item => {
        const option = this.findVarPickerOptionByModelItem(item)
        if (!option || !option.varCode) return
        const key = this.varPickerOptionKey(option)
        if (seen[key]) return
        seen[key] = true
        result.push(option)
      })
      return result
    },
    inputVars() {
      return this.projectVars.filter(v => v.varSource === 'INPUT' || !v.varSource)
    },
    computedVars() {
      return this.projectVars.filter(v => v.varSource === 'COMPUTED')
    },
    enumVars() {
      return this.projectVars.filter(v => v.varType === 'ENUM')
    }
  },

  created() {
    if (this.usesDesignerReferenceCatalog) {
      this.referenceCatalogMode = 'UNAVAILABLE'
    } else if (this.$route && this.$route.params && this.$route.params.id) {
      this.loadProjectVars(this.$route.params.id)
    }
    // 监听 contentLoaded 变化，确保竞态条件下也能触发同步
    this.$watch('contentLoaded', (val) => {
      if (val) this._trySyncModelVarRefs()
    })
  },

  activated() {
    // 工作区页签会缓存设计器组件；字段或数据对象在审批中心生效后，
    // 回到旧页签必须重新拉取引用，避免仍显示审批前的字段库。
    if (this.usesDesignerReferenceCatalog) {
      if (this.draftGuardLoaded && this.viewRevision) return this.refreshProjectRefs()
    } else if (this.projectIdForRefs != null) {
      return this.refreshProjectRefs()
    }
  },
  beforeUnmount() { this.referenceCatalogRequestId++ },

  methods: {
    clearReferenceCatalog() {
      this.referenceCatalogRequestId++
      this.projectVars = []; this.projectRefs = []; this.projectFunctions = []
      this.projectLists = []; this.projectApis = []; this.varOptionsByCode = {}
      this.projectIdForRefs = null
      this.referenceCatalogMode = 'UNAVAILABLE'; this.frozenReferenceCatalog = null
      this.referenceCatalogError = ''; this.referenceCatalogKey = ''
      if (typeof this.invalidateRuleCallOptions === 'function') this.invalidateRuleCallOptions()
    },
    referenceRequestCurrent(requestId) {
      return requestId === this.referenceCatalogRequestId && (!this.usesDesignerReferenceCatalog || this.draftGuardActive)
    },
    loadDesignerDefinitionMetadata(definitionId, requestId) {
      const route = this.$route
      const pending = Promise.resolve().then(() => getDefinition(definitionId))
      pending.then(response => {
        if (!this.referenceRequestCurrent(requestId)) return
        const definition = response?.data || response
        if (/^\/designer\/(?!expression\/)/.test(route?.path || '') &&
            String(route.params.id) === String(definitionId) &&
            String(definition?.id) === String(definitionId) && definition.ruleName) {
          syncWorkspaceTabTitle(this.$store, route, definition.ruleName)
        }
      }).catch(() => { /* 标题读取失败不影响已验证的冻结引用；LIVE加载仍等待同一请求并处理失败。 */ })
      return pending
    },
    loadDesignerReferenceCatalog(view, { livePreview = false } = {}) {
      const definitionId = String(this.definitionId || this.$route?.params?.id || '')
      const { source, key } = designerCatalogRequest(view, definitionId, this.viewRefreshToken)
      const previous = this.frozenReferenceCatalog
      const previousKey = this.referenceCatalogKey
      this.clearReferenceCatalog()
      const requestId = this.referenceCatalogRequestId
      this.referenceCatalogKey = key
      this.loadingVars = true; this.varsLoadError = false
      const definitionPromise = this.loadDesignerDefinitionMetadata(definitionId, requestId)
      const load = async () => {
        try {
          const response = livePreview && previousKey === key && previous?.localEditingAllowed === true
            ? { data: previous } : await getRuleReferenceCatalog(definitionId, source)
          if (!this.referenceRequestCurrent(requestId)) return null
          const catalog = response?.data
          requireExecutionIdentity(catalog?.executionIdentity, definitionId, source)
          if (!catalog || !['LOCAL', 'ARTIFACT_SOURCE'].includes(catalog.referenceNamespace)
              || !['variables', 'objectTree', 'models', 'functions', 'rules', 'apis', 'lists'].every(field => Array.isArray(catalog[field]))) {
            throw new Error('所选版本引用目录不完整，不能使用当前环境目录替代')
          }
          this.frozenReferenceCatalog = catalog
          if (catalog.status !== 'AVAILABLE') throw new Error((catalog.diagnostics || []).join('；') || '所选版本没有可验证的引用目录')
          const localDraft = ['DRAFT', 'LEGACY'].includes(view?.state) || view?.sourceType === 'LEGACY_CONTENT'
          const live = livePreview || catalog.referenceNamespace === 'LOCAL' || localDraft
          if (live && catalog.localEditingAllowed !== true) throw new Error('该来源尚未建立可验证的本地引用，请先通过离线迁移处理')
          if (live) {
            this.referenceCatalogMode = 'LIVE'
            await this.loadProjectVars(definitionId, { requestId, managed: true, definitionPromise })
            if (this.referenceRequestCurrent(requestId) && typeof this.loadRuleCallOptions === 'function') {
              await this.loadRuleCallOptions(definitionId, { catalogReady: true })
            }
          } else {
            this.referenceCatalogMode = 'FROZEN'
            this.applyReferenceCatalog(catalog.variables, catalog.objectTree, catalog.models, catalog.functions, catalog.apis, catalog.lists)
            this.projectFunctions = this.projectFunctions.map(fn => ({ ...fn, catalogMode: 'FROZEN' }))
            for (const ref of this.projectRefs) {
              const label = catalog.displayLabels?.[`${ref.refType}:${ref.id}`]
              if (typeof label !== 'string' || !label) continue
              ref.refLabel = { ...ref.refLabel, label }
              ref.displayName = [label, ref.refCode].filter(Boolean).join(' ')
            }
            if (typeof this.applyFrozenRuleCallOptions === 'function') this.applyFrozenRuleCallOptions(catalog.rules, definitionId)
            for (const ref of this.projectRefs.filter(item => item.varType === 'ENUM')) {
              const raw = ref.varObj?.validValues || ref.varObj?.valueRange || []
              let options = []
              try { options = typeof raw === 'string' ? JSON.parse(raw) : raw } catch { /* 缺少冻结枚举时保留空目录，不查询当前ID。 */ }
              this.varOptionsByCode[ref.refCode] = Array.isArray(options) ? options.map(value => typeof value === 'object' && value !== null
                ? { optionValue: value.optionValue ?? value.value, optionLabel: value.optionLabel ?? value.label }
                : { optionValue: value, optionLabel: String(value) }) : []
            }
          }
          if (!this.referenceRequestCurrent(requestId)) return null
          return catalog
        } catch (error) {
          if (!this.referenceRequestCurrent(requestId)) return null
          this.projectVars = []; this.projectRefs = []; this.projectFunctions = []; this.projectApis = []; this.projectLists = []
          this.referenceCatalogMode = 'UNAVAILABLE'; this.varsLoadError = true
          this.referenceCatalogError = error.message || '引用目录读取失败'
          return null
        } finally {
          if (this.referenceRequestCurrent(requestId)) this.loadingVars = false
        }
      }
      this.referenceCatalogPromise = load()
      return this.referenceCatalogPromise
    },
    applyReferenceCatalog(variables, objectTree, models, functions, apis, lists) {
      this.projectFunctions = functions; this.projectApis = apis; this.projectLists = lists
      this.projectVars = variables.map(variable => {
        const label = makeRefLabel(variable, variable.varSource === 'CONSTANT' ? 'constant' : 'variable', '')
        return { ...variable, varCode: variable.scriptName || variable.varCode, varLabel: label.label + ' ' + label.code,
          varLabelText: label.label, varCodeText: label.code }
      })
      this.projectRefs = buildReferenceCatalog(variables, objectTree, models).refs
      this.appendExternalApiRefs(this.projectRefs, apis)
    },
    normalizeObjectFieldScriptName(field, objScriptName) {
      const raw = (field && (field.scriptName || field.varCode)) || ''
      return this.stripObjectPrefix(raw, objScriptName)
    },
    stripObjectPrefix(raw, objScriptName) {
      const prefix = objScriptName ? objScriptName + '.' : ''
      return prefix && raw.indexOf(prefix) === 0 ? raw.substring(prefix.length) : raw
    },
    normalizeVariableTreeResponse(res) {
      const data = res && res.data ? res.data : res
      if (Array.isArray(data)) return data
      if (data && Array.isArray(data.tree)) return data.tree
      return []
    },
    normalizeListResponse(res) {
      const data = res && res.data ? res.data : res
      if (Array.isArray(data)) return data
      if (data && Array.isArray(data.records)) return data.records
      return []
    },
    flattenObjectVariables(vars) {
      const result = []
      const visit = (rows) => {
        const list = rows || []
        list.forEach(row => {
          result.push(row)
          if (row.children && row.children.length) visit(row.children)
        })
      }
      visit(vars)
      return result
    },
    refTypeForVar(v) {
      return v && v.varSource === 'CONSTANT' ? 'CONSTANT' : 'VARIABLE'
    },
    appendModelOutputRefs(refs, models) {
      (models || []).forEach(m => {
        const modelCode = m.modelCode || ''
        if (!modelCode) return
        const modelLabel = m.modelName || modelCode
        const inputFields = m.inputFields || []
        const outputFields = m.outputFields || []
        outputFields.forEach(field => {
          const outputCode = (field && (field.scriptName || field.fieldName)) || ''
          if (!outputCode) return
          const refCode = `${modelCode}.${outputCode}`
          const fieldLabel = (field.fieldLabel || field.fieldName || outputCode)
          const labelSource = {
            id: field.id,
            modelId: m.id,
            modelCode,
            modelName: modelLabel,
            varCode: outputCode,
            scriptName: outputCode,
            varLabel: modelLabel + '/' + fieldLabel,
            varType: field.fieldType || 'STRING',
            refType: 'MODEL_OUTPUT',
            modelInputFields: inputFields
          }
          refs.push({
            refCode,
            refLabel: makeRefLabel(labelSource, 'model', ''),
            varType: labelSource.varType,
            varObj: Object.assign({}, field, labelSource),
            category: 'model',
            refType: 'MODEL_OUTPUT',
            modelCode,
            modelId: m.id,
            modelLabel,
            modelVersion: m.currentVersion ?? m.version,
            modelInputFields: inputFields
          })
        })
      })
    },
    appendExternalApiRefs(refs, apis) {
      ;(apis || []).forEach(api => {
        const apiId = api && api.id
        if (apiId == null) return
        const apiCode = api.apiCode || api.code || `api_${apiId}`
        const apiLabel = api.apiName || api.name || apiCode
        const paths = new Map(API_MODULE_FIELDS.map(field => [field.value, field]))
        ;(api.resultFields || []).forEach(field => { if (field?.value) paths.set(field.value, field) })
        if (api.executionConfig) {
          try {
            apiStagePaths(parseExecution(api.executionConfig), 'RESULT').forEach(field => {
              if (field?.value && !paths.has(field.value)) paths.set(field.value, field)
            })
          } catch (error) { /* 当前 API 结构无效时仍保留通用调用上下文。 */ }
        }
        paths.forEach(field => {
          const refCode = field.value === '$' ? apiCode : `${apiCode}.${field.value}`
          refs.push({
            id: apiId,
            refCode,
            refLabel: { label: `${apiLabel} · ${field.label}`, code: refCode },
            varType: field.type || 'OBJECT',
            category: 'external',
            refType: 'EXTERNAL_API',
            apiId,
            apiCode,
            apiLabel,
            relativePath: field.value,
            varObj: { id: apiId, apiCode, apiName: apiLabel, refType: 'EXTERNAL_API', relativePath: field.value },
          })
        })
      })
    },

    /**
     * 根据定义 ID 拉取项目下变量树、常量、对象字段与函数列表，并组装 projectRefs。
     */
    async loadProjectVars(definitionId, options = {}) {
      if (this.usesDesignerReferenceCatalog && !options.managed) {
        const guard = this.draftGuardPromise
        if (guard) await guard
        if (guard !== this.draftGuardPromise) return null
        const { key } = designerCatalogRequest(this.viewRevision, definitionId, this.viewRefreshToken)
        // The guard already loads this source before the page initializes dirty tracking.
        // Clearing it again here would temporarily disable editing and lose that initialization.
        if (key === this.referenceCatalogKey && this.referenceCatalogPromise) return this.referenceCatalogPromise
        return this.viewRevision ? this.loadDesignerReferenceCatalog(this.viewRevision, { livePreview: this.designerReferencePreview }) : null
      }
      const requestId = options.requestId ?? ++this.referenceCatalogRequestId
      const route = this.$route
      this.loadingVars = true
      this.varsLoadError = false
      try {
        const defRes = await (options.definitionPromise || getDefinition(definitionId))
        if (!this.referenceRequestCurrent(requestId)) return
        const def = defRes && defRes.data ? defRes.data : defRes
        if (!options.definitionPromise && /^\/designer\/(?!expression\/)/.test(route?.path || '') &&
            String(route.params.id) === String(definitionId) && def?.ruleName) {
          syncWorkspaceTabTitle(this.$store, route, def.ruleName)
        }
        // projectId == null 表示规则数据不完整；projectId = 0 表示 GLOBAL 规则，需继续调用 API
        if (!def || def.projectId == null) {
          this.loadingVars = false
          return
        }
        this.projectIdForRefs = def.projectId
        const pid = def.projectId
        const optionalDirectory = error => {
          if (options.managed) throw error
          return []
        }

        // request 拦截器已返回 res.data，无需再访问 .data
        const [varRes, objRes, funcRes, modelRes, listRes, apiRes] = await Promise.all([
          hasPermission('field:view') ? listVariablesByProject(pid).catch(optionalDirectory) : [],
          hasPermission('field:view') ? getVariableTree(pid).catch(optionalDirectory) : [],
          hasPermission('function:view') ? listAllFunctionsByProject(pid).catch(optionalDirectory) : [],
          hasPermission('model:view') ? listAllModelsByProject(pid).catch(optionalDirectory) : [],
          hasPermission('list:view') ? Promise.resolve(listLibraries({ pageNum: 1, pageSize: 1000, projectId: pid, status: 1 })).catch(optionalDirectory) : [],
          hasPermission('datasource:view') ? Promise.resolve(listAvailableApiConfigs(pid)).catch(optionalDirectory) : []
        ])
        if (!this.referenceRequestCurrent(requestId)) return
        this.projectFunctions = this.normalizeListResponse(funcRes)
        this.projectLists = this.normalizeListResponse(listRes)
        this.projectApis = this.normalizeListResponse(apiRes)

        // request 拦截器已返回 res.data，varRes 直接是数组，无需 .data
        const allVars = this.normalizeListResponse(varRes)

        // objRes 是 { tree: [...], objectIdMap: {...} }，需要取 tree 属性
        const objectTree = this.normalizeVariableTreeResponse(objRes)
        const models = this.normalizeListResponse(modelRes)

        // 兼容旧逻辑：projectVars 扁平存储，同时格式化 varLabel 供 PropertyPanel 等直接引用
        this.projectVars = allVars.map(v => {
          const refLabel = makeRefLabel(v, v.varSource === 'CONSTANT' ? 'constant' : 'variable', '')
          return {
            ...v,
            varCode: v.scriptName || v.varCode,
            varLabel: refLabel.label + ' ' + refLabel.code,
            varLabelText: refLabel.label,
            varCodeText: refLabel.code
          }
        })

        const refs = buildReferenceCatalog(allVars, objectTree, models).refs
        this.appendExternalApiRefs(refs, this.projectApis)

        // 1. 普通变量（排除常量）
        // 2. 常量：单段 scriptName（或 varCode）
        // 3. 对象字段：对象 scriptName.字段 scriptName
        this.projectRefs = refs

        this._trySyncModelVarRefs()

        const enumRefs = refs.filter(r => r.varType === 'ENUM')
        await Promise.all(enumRefs.map(r => this.loadVarOptionsForRef(r.refCode, r.varObj, requestId)))
      } catch (e) {
        if (!this.referenceRequestCurrent(requestId)) return
        if (options.managed) throw e
        this.projectVars = []
        this.projectRefs = []
        this.projectLists = []
        this.projectApis = []
        this.varsLoadError = true
      } finally {
        if (this.referenceRequestCurrent(requestId)) this.loadingVars = false
      }
    },

    /**
     * 拉取枚举选项：变量 ENUM 走 variable；数据对象字段 ENUM 走 dataobject field。
     */
    async loadVarOptionsForRef(refCode, varObj, requestId = this.referenceCatalogRequestId) {
      if (this.referenceCatalogMode !== 'LIVE') return
      if (!varObj || !varObj.id) return
      try {
        const api = varObj.objectField ? getDataObjectFieldOptions : getVariableOptions
        const res = await api(varObj.id)
        if (!this.referenceRequestCurrent(requestId) || this.referenceCatalogMode !== 'LIVE') return
        // request 拦截器已返回 res.data，直接使用
        const opts = res || []
        this.varOptionsByCode[refCode] = opts
      } catch (e) {
        if (!this.referenceRequestCurrent(requestId)) return
        this.varOptionsByCode[refCode] = []
      }
    },

    /**
     * 刷新项目变量/函数列表（设计器保存后调用）。
     * 重新拉取当前项目下的所有数据，更新 projectVars、projectRefs、projectFunctions。
     */
    async refreshProjectRefs() {
      if (this.usesDesignerReferenceCatalog) return this.viewRevision
        ? this.loadDesignerReferenceCatalog(this.viewRevision, { livePreview: this.designerReferencePreview }) : null
      if (this.projectIdForRefs == null) return
      this.loadingVars = true
      this.varsLoadError = false
      try {
        const pid = this.projectIdForRefs
        const [varRes, objRes, funcRes, modelRes, listRes, apiRes] = await Promise.all([
          listVariablesByProject(pid).catch(() => []),
          getVariableTree(pid).catch(() => []),
          listAllFunctionsByProject(pid).catch(() => []),
          listAllModelsByProject(pid).catch(() => []),
          hasPermission('list:view') ? Promise.resolve(listLibraries({ pageNum: 1, pageSize: 1000, projectId: pid, status: 1 })).catch(() => []) : [],
          hasPermission('datasource:view') ? Promise.resolve(listAvailableApiConfigs(pid)).catch(() => []) : []
        ])
        // request 拦截器已返回 res.data，直接使用
        this.projectFunctions = this.normalizeListResponse(funcRes)
        this.projectLists = this.normalizeListResponse(listRes)
        this.projectApis = this.normalizeListResponse(apiRes)

        const allVars = this.normalizeListResponse(varRes)

        // objRes 是 { tree: [...], objectIdMap: {...} }，需要取 tree 属性
        const objectTree = this.normalizeVariableTreeResponse(objRes)
        const models = this.normalizeListResponse(modelRes)

        // 兼容旧逻辑：projectVars 扁平存储，同时格式化 varLabel 供 PropertyPanel 等直接引用
        this.projectVars = allVars.map(v => {
          const refLabel = makeRefLabel(v, v.varSource === 'CONSTANT' ? 'constant' : 'variable', '')
          return {
            ...v,
            varCode: v.scriptName || v.varCode,
            varLabel: refLabel.label + ' ' + refLabel.code,
            varLabelText: refLabel.label,
            varCodeText: refLabel.code
          }
        })

        const refs = buildReferenceCatalog(allVars, objectTree, models).refs
        this.appendExternalApiRefs(refs, this.projectApis)
        this.projectRefs = refs
        this._trySyncModelVarRefs()
        const enumRefs = refs.filter(r => r.varType === 'ENUM')
        await Promise.all(enumRefs.map(r => this.loadVarOptionsForRef(r.refCode, r.varObj)))
      } catch (e) {
        this.projectApis = []
        this.varsLoadError = true
      } finally {
        this.loadingVars = false
      }
    },

    getVarByIdentity(id, refType) {
      if (id == null || id === '' || !refType) return null
      const ref = this.projectRefs.find(item => {
        const optionId = item._varId != null ? item._varId : (item.id != null ? item.id : (item.varObj && item.varObj.id))
        const optionType = item._refType || item.refType || (item.varObj && item.varObj.refType) || (item._ref && item._ref.refType)
        return String(optionId) === String(id) && optionType === refType
      })
      return ref ? ref.varObj : null
    },

    varPickerOptionKey(option) {
      if (!option) return ''
      const refType = option._refType || option.refType || (option.varObj && option.varObj.refType) || (option._ref && option._ref.refType) || ''
      const id = option._varId != null ? option._varId : (option.id != null ? option.id : (option.varObj && option.varObj.id))
      if (id != null && id !== '') return `${refType || 'REF'}:${id}`
      return ''
    },

    findVarPickerOptionByModelItem(item) {
      if (item == null || item === '') return null
      const source = typeof item === 'string' || typeof item === 'number' ? { varCode: item } : item
      const id = source._varId != null ? source._varId : (source.id != null ? source.id : (source.varObj && source.varObj.id))
      const refType = source._refType || source.refType || (source.varObj && source.varObj.refType) || (source._ref && source._ref.refType)
      if (id != null && id !== '' && refType) {
        const byId = this.varPickerOptions.find(option => {
          const optionId = option._varId != null ? option._varId : (option.id != null ? option.id : (option.varObj && option.varObj.id))
          const optionType = option._refType || option.refType || (option.varObj && option.varObj.refType) || (option._ref && option._ref.refType)
          return String(optionId) === String(id) && optionType === refType
        })
        if (byId) return byId
      }
      return null
    },

    collectActionDataVarItems(actionData) {
      const result = []
      const pushOperand = operand => {
        collectOperandReferences(operand).forEach(reference => result.push({
          varCode: reference.code || reference.path,
          varType: reference.valueType,
          _varId: reference.refId,
          _refType: reference.refType
        }))
      }
      const pushValue = value => {
        if (!value) return
        if (typeof value === 'string') result.push({ varCode: value })
        else result.push(value)
      }
      const pushField = (holder, field) => {
        if (!holder || !holder[field]) return
        const keys = this.actionDataFieldRefKeys(field)
        const item = { varCode: holder[field] }
        if (keys && holder[keys.id]) {
          item._varId = holder[keys.id]
          item._refType = holder[keys.refType]
        } else if (holder._varId) {
          item._varId = holder._varId
          item._refType = holder._refType
        }
        pushValue(item)
      }
      const walkActions = actions => {
        const list = actions || []
        list.forEach(action => {
          ['targetOperand', 'valueOperand', 'leftOperand', 'rightOperand', 'matchOperand', 'listOperand', 'checkOperand', 'trueOperand', 'falseOperand'].forEach(field => pushOperand(action[field]))
          ;(action.args || []).forEach(pushOperand)
          ;(action.inOperands || []).forEach(pushOperand)
          ;(action.parts || []).forEach(part => pushOperand(part.operand))
          ;(action.entries || []).forEach(entry => pushOperand(entry.valueOperand))
          pushField(action, 'target')
          pushField(action, 'condVar')
          pushField(action, 'matchVar')
          pushField(action, 'checkVar')
          if (action.varCode || action._varId) pushValue(action)
          if (Array.isArray(action.actions)) walkActions(action.actions)
          if (Array.isArray(action.defaultActions)) walkActions(action.defaultActions)
          ;(action.branches || []).forEach(branch => {
            pushOperand(branch.leftOperand)
            pushOperand(branch.rightOperand)
            pushField(branch, 'condVar')
            walkActions(branch.actions)
          })
          ;(action.cases || []).forEach(item => {
            pushOperand(item.valueOperand)
            walkActions(item.actions)
          })
        })
      }
      walkActions(actionData)
      return result
    },

    actionDataFieldRefKeys(field) {
      const map = {
        target: { id: '_targetVarId', refType: '_targetRefType' },
        condVar: { id: '_condVarId', refType: '_condVarRefType' },
        matchVar: { id: '_matchVarId', refType: '_matchVarRefType' },
        checkVar: { id: '_checkVarId', refType: '_checkVarRefType' }
      }
      return map[field] || null
    },

    syncActionDataVarRefs(actionData) {
      if (!Array.isArray(actionData)) return false
      let changed = false
      const syncOperand = (holder, field) => {
        if (!holder || !holder[field]) return
        const result = syncOperandReference(holder[field], this.varPickerOptions)
        if (result.changed) {
          holder[field] = result.operand
          changed = true
        }
      }
      const syncOperandArray = values => {
        (values || []).forEach((operand, index) => {
          const result = syncOperandReference(operand, this.varPickerOptions)
          if (result.changed) {
            values[index] = result.operand
            changed = true
          }
        })
      }
      const syncField = (holder, field) => {
        if (!holder || !holder[field]) return
        const keys = this.actionDataFieldRefKeys(field)
        const id = keys && holder[keys.id] ? holder[keys.id] : holder._varId
        const refType = keys && holder[keys.refType] ? holder[keys.refType] : holder._refType
        if (!id) return
        const ref = this.findRefByVarId(id, refType)
        if (!ref) return
        if (holder[field] !== ref.refCode) { holder[field] = ref.refCode; changed = true }
        if (keys && holder[keys.refType] !== ref.refType) { holder[keys.refType] = ref.refType; changed = true }
      }
      const walk = actions => {
        const rows = actions || []
        rows.forEach(action => {
          ['targetOperand', 'valueOperand', 'leftOperand', 'rightOperand', 'matchOperand', 'listOperand', 'checkOperand', 'trueOperand', 'falseOperand'].forEach(field => syncOperand(action, field))
          syncOperandArray(action.args)
          syncOperandArray(action.inOperands)
          ;(action.parts || []).forEach(part => syncOperand(part, 'operand'))
          ;(action.entries || []).forEach(entry => syncOperand(entry, 'valueOperand'))
          syncField(action, 'target')
          syncField(action, 'condVar')
          syncField(action, 'matchVar')
          syncField(action, 'checkVar')
          if (Array.isArray(action.actions)) walk(action.actions)
          if (Array.isArray(action.defaultActions)) walk(action.defaultActions)
          const branches = action.branches || []
          branches.forEach(branch => {
            syncOperand(branch, 'leftOperand')
            syncOperand(branch, 'rightOperand')
            syncField(branch, 'condVar')
            walk(branch.actions)
          })
          const cases = action.cases || []
          cases.forEach(item => {
            syncOperand(item, 'valueOperand')
            walk(item.actions)
          })
        })
      }
      walk(actionData)
      return changed
    },

    getVarOptions(refCode) {
      const opts = this.varOptionsByCode[refCode] || []
      return opts.map(o => ({ value: o.optionValue, label: o.optionLabel || o.optionValue }))
    },

    /** 与变量管理表格中类型标签配色一致 */
    varTypeColor(varType) {
      return varTypeTagColor(varType)
    },

    /** 与变量管理中类型中文名一致 */
    varTypeLabel(varType) {
      return _varTypeLabel(varType)
    },

    /**
     * 当 projectRefs 和 model 都加载完成后，尝试同步设计器 model 中的变量引用。
     * 各设计器可实现 _syncModelVarRefs() 来定义自身的同步逻辑。
     */
    _trySyncModelVarRefs() {
      if (this.usesDesignerReferenceCatalog && (this.loadingVars || this.referenceCatalogMode === 'UNAVAILABLE')) return
      if (this.projectRefs && this.projectRefs.length > 0 && this.contentLoaded && typeof this._syncModelVarRefs === 'function') {
        if (typeof this.syncDesignerReferenceMetadata === 'function') {
          this.syncDesignerReferenceMetadata(() => this._syncModelVarRefs())
        } else {
          this._syncModelVarRefs()
        }
      }
    },

    /**
     * 根据变量数据库 ID 在最新的 projectRefs 中查找对应引用
     */
    findRefByVarId(varId, refType) {
      if (!varId || !refType) return null
      return this.projectRefs.find(r => r.varObj && String(r.varObj.id) === String(varId) && r.refType === refType) || null
    },

    /**
     * 同步单个变量引用项，只根据 _varId + _refType 更新展示编码和标签。
     * @param {Object} item - 包含 {varCode, varLabel, _varId?} 的模型项
     * @returns {boolean} 是否有更新
     */
    syncVarItem(item) {
      if (!item || !item.varCode) return false
      if (item._varId && item._refType) {
        const ref = this.findRefByVarId(item._varId, item._refType)
        if (ref) {
          let changed = false
          if (item.varCode !== ref.refCode) { item.varCode = ref.refCode; changed = true }
          if (typeof ref.refLabel === 'object') {
            const newLabel = ref.refLabel.label + ' ' + ref.refLabel.code
            if (item.varLabel !== newLabel) { item.varLabel = newLabel; changed = true }
          } else {
            if (item.varLabel !== ref.refLabel) { item.varLabel = ref.refLabel; changed = true }
          }
          if (ref.varType && item.varType !== ref.varType) { item.varType = ref.varType; changed = true }
          if (item._refType !== ref.refType) { item._refType = ref.refType; changed = true }
          return changed
        }
      }
      return false
    }
  }
}
