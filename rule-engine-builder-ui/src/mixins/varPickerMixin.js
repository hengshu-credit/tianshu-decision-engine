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

import { getDefinition } from '@/api/definition'
import { listVariablesByProject, getVariableOptions } from '@/api/variable'
import { getVariableTree, getDataObjectFieldOptions } from '@/api/dataObject'
import { listAllFunctionsByProject } from '@/api/function'
import { listAllModelsByProject } from '@/api/model'
import { listLibraries } from '@/api/ruleList'
import { varTypeTagColor, varTypeLabel as _varTypeLabel } from '@/constants/varTypes'
import { makeRefLabel } from '@/utils/varDisplay'
import { buildPickerOptions, buildReferenceCatalog } from '@/utils/referenceCatalog'
import { collectOperandReferences, syncOperandReference } from '@/utils/operand'

// 变量选择 Mixin

export default {
  data() {
    return {
      projectVars: [],
      projectRefs: [],
      projectFunctions: [],
      projectLists: [],
      loadingVars: false,
      /** 变量加载是否失败 */
      varsLoadError: false,
      varOptionsByCode: {},
      projectIdForRefs: null
    }
  },

  computed: {
    /** VarPicker 使用的选项列表（分层：普通变量 / 常量 / 对象字段） */
    varPickerOptions() {
      return buildPickerOptions({ refs: this.projectRefs })
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
    if (this.$route && this.$route.params && this.$route.params.id) {
      this.loadProjectVars(this.$route.params.id)
    }
    // 监听 contentLoaded 变化，确保竞态条件下也能触发同步
    this.$watch('contentLoaded', (val) => {
      if (val) this._trySyncModelVarRefs()
    })
  },

  methods: {
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
            modelInputFields: inputFields
          })
        })
      })
    },

    /**
     * 根据定义 ID 拉取项目下变量树、常量、对象字段与函数列表，并组装 projectRefs。
     */
    async loadProjectVars(definitionId) {
      this.loadingVars = true
      this.varsLoadError = false
      try {
        const defRes = await getDefinition(definitionId)
        const def = defRes && defRes.data ? defRes.data : defRes
        // projectId == null 表示规则数据不完整；projectId = 0 表示 GLOBAL 规则，需继续调用 API
        if (!def || def.projectId == null) {
          this.loadingVars = false
          return
        }
        this.projectIdForRefs = def.projectId
        const pid = def.projectId

        // request 拦截器已返回 res.data，无需再访问 .data
        const [varRes, objRes, funcRes, modelRes, listRes] = await Promise.all([
          listVariablesByProject(pid).catch(() => []),
          getVariableTree(pid).catch(() => []),
          listAllFunctionsByProject(pid).catch(() => []),
          listAllModelsByProject(pid).catch(() => []),
          Promise.resolve(listLibraries({ pageNum: 1, pageSize: 1000, projectId: pid, status: 1 })).catch(() => [])
        ])
        this.projectFunctions = this.normalizeListResponse(funcRes)
        this.projectLists = this.normalizeListResponse(listRes)

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

        // 1. 普通变量（排除常量）
        // 2. 常量：单段 scriptName（或 varCode）
        // 3. 对象字段：对象 scriptName.字段 scriptName
        this.projectRefs = refs

        this._trySyncModelVarRefs()

        const enumRefs = refs.filter(r => r.varType === 'ENUM')
        await Promise.all(enumRefs.map(r => this.loadVarOptionsForRef(r.refCode, r.varObj)))
      } catch (e) {
        this.projectVars = []
        this.projectRefs = []
        this.projectLists = []
        this.varsLoadError = true
      } finally {
        this.loadingVars = false
      }
    },

    /**
     * 拉取枚举选项：变量 ENUM 走 variable；数据对象字段 ENUM 走 dataobject field。
     */
    async loadVarOptionsForRef(refCode, varObj) {
      if (!varObj || !varObj.id) return
      try {
        const api = varObj.objectField ? getDataObjectFieldOptions : getVariableOptions
        const res = await api(varObj.id)
        // request 拦截器已返回 res.data，直接使用
        const opts = res || []
        this.$set(this.varOptionsByCode, refCode, opts)
      } catch (e) {
        this.$set(this.varOptionsByCode, refCode, [])
      }
    },

    /**
     * 刷新项目变量/函数列表（设计器保存后调用）。
     * 重新拉取当前项目下的所有数据，更新 projectVars、projectRefs、projectFunctions。
     */
    async refreshProjectRefs() {
      if (this.projectIdForRefs == null) return
      this.loadingVars = true
      this.varsLoadError = false
      try {
        const pid = this.projectIdForRefs
        const [varRes, objRes, funcRes, modelRes, listRes] = await Promise.all([
          listVariablesByProject(pid).catch(() => []),
          getVariableTree(pid).catch(() => []),
          listAllFunctionsByProject(pid).catch(() => []),
          listAllModelsByProject(pid).catch(() => []),
          Promise.resolve(listLibraries({ pageNum: 1, pageSize: 1000, projectId: pid, status: 1 })).catch(() => [])
        ])
        // request 拦截器已返回 res.data，直接使用
        this.projectFunctions = this.normalizeListResponse(funcRes)
        this.projectLists = this.normalizeListResponse(listRes)

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
        this.projectRefs = refs
        this._trySyncModelVarRefs()
        const enumRefs = refs.filter(r => r.varType === 'ENUM')
        await Promise.all(enumRefs.map(r => this.loadVarOptionsForRef(r.refCode, r.varObj)))
      } catch (e) {
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
      if (this.projectRefs && this.projectRefs.length > 0 && this.contentLoaded && typeof this._syncModelVarRefs === 'function') {
        this._syncModelVarRefs()
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
