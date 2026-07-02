<template>
  <div class="uiue-list-page">
    <div class="test-layout">
      <!-- 左侧：选择规则 + 参数输入 -->
      <div class="test-left">
        <div class="uiue-card">
          <div class="uiue-card-title">选择规则</div>
          <el-form size="small" label-width="80px">
            <el-form-item label="筛选范围">
              <el-radio-group v-model="ruleScope" size="small" style="width: 100%;" @change="onScopeChange">
                <el-radio-button label="ALL">全部</el-radio-button>
                <el-radio-button label="PROJECT">项目级</el-radio-button>
                <el-radio-button label="GLOBAL">全局</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="项目" v-if="ruleScope === 'PROJECT'">
              <el-select v-model="selectedProjectId" placeholder="请选择项目" clearable style="width: 100%;" @change="onProjectChange">
                <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="规则">
              <el-select v-model="selectedRuleId" :placeholder="ruleScope === 'PROJECT' && !selectedProjectId ? '请先选择项目' : '请选择规则'" :disabled="ruleScope === 'PROJECT' && !selectedProjectId" style="width: 100%;" filterable @change="onRuleChange">
                <el-option v-for="r in rules" :key="r.id" :label="(r.scope === 'GLOBAL' ? '[全局] ' : '[项目] ') + r.ruleName + ' (' + r.ruleCode + ')'" :value="r.id" />
              </el-select>
            </el-form-item>
          </el-form>
          <div v-if="selectedRule" class="rule-info">
            <el-descriptions :column="2" size="mini" border>
              <el-descriptions-item label="规则编码">{{ selectedRule.ruleCode }}</el-descriptions-item>
              <el-descriptions-item label="模型类型">
                <el-tag size="mini">{{ mtl(selectedRule.modelType) }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="当前版本">v{{ selectedRule.currentVersion }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="{ 0: 'info', 1: 'success', 2: 'warning' }[selectedRule.status]" size="mini">
                  {{ ['草稿', '已发布', '已下线'][selectedRule.status] }}
                </el-tag>
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </div>

        <div class="uiue-card" style="margin-top: 12px;">
          <div class="uiue-card-title">
            输入参数
            <el-button type="text" size="small" style="margin-left: 12px;" @click="loadVariables" v-if="selectedRule">
              <i class="el-icon-refresh" /> 加载变量
            </el-button>
            <el-button type="text" size="small" style="margin-left: 8px;" @click="addParam">
              <i class="el-icon-plus" /> 手动添加
            </el-button>
            <el-button type="text" size="small" style="margin-left: 8px;" @click="applyRiskDemoParams">
              <i class="el-icon-magic-stick" /> 综合风控样例
            </el-button>
          </div>
          <div v-if="params.length === 0" style="color: #999; padding: 12px 0; text-align: center;">
            请选择规则后加载变量，或手动添加参数
          </div>
          <el-form v-else size="small" label-width="0">
            <div v-for="(p, idx) in params" :key="idx" class="param-row">
              <el-input v-model="p.key" placeholder="参数名" class="param-key" :disabled="p.fromVar" />
              <span class="param-label" v-if="p.label">({{ p.label }})</span>
              <template v-if="p.type === 'BOOLEAN'">
                <el-select v-model="p.value" class="param-value" placeholder="选择">
                  <el-option label="true" value="true" />
                  <el-option label="false" value="false" />
                </el-select>
              </template>
              <template v-else-if="p.type === 'ENUM' && p.options && p.options.length > 0">
                <el-select v-model="p.value" class="param-value" placeholder="选择枚举值" clearable filterable>
                  <el-option v-for="opt in p.options" :key="opt.optionValue" :label="opt.optionLabel + ' (' + opt.optionValue + ')'" :value="opt.optionValue" />
                </el-select>
              </template>
              <template v-else>
                <el-input v-model="p.value" :placeholder="p.example || '参数值'" class="param-value" />
              </template>
              <el-button type="text" size="small" class="btn-delete" style="margin-left: 4px;" @click="params.splice(idx, 1)">
                <i class="el-icon-delete" />
              </el-button>
            </div>
          </el-form>
        </div>

        <div style="margin-top: 16px; text-align: center;">
          <el-button type="primary" :loading="executing" :disabled="!selectedRuleId" @click="handleExecute">
            <i class="el-icon-video-play" /> 执行测试
          </el-button>
          <el-button @click="handleClear">清空</el-button>
        </div>
      </div>

      <!-- 右侧：执行结果 -->
      <div class="test-right">
        <div class="uiue-card" style="height: 100%;">
          <div class="uiue-card-title">执行结果</div>
          <div v-if="!result && !executing" class="result-empty">
            <i class="el-icon-video-play" style="font-size: 48px; color: #ddd;" />
            <p style="color: #999; margin-top: 12px;">点击「执行测试」查看结果</p>
          </div>
          <div v-else-if="executing" style="text-align: center; padding: 60px 0;">
            <i class="el-icon-loading" style="font-size: 32px; color: #2639E9;" />
            <p style="color: #999; margin-top: 12px;">规则执行中...</p>
          </div>
          <div v-else>
            <el-alert
              :title="result.success ? '执行成功' : '执行失败'"
              :type="result.success ? 'success' : 'error'"
              :closable="false"
              show-icon
              style="margin-bottom: 16px;"
            >
              <span>耗时 {{ result.executeTimeMs }} ms</span>
            </el-alert>

            <div v-if="result.errorMessage" style="margin-bottom: 16px;">
              <div class="result-section-title" style="color: #F76E6C;">错误信息</div>
              <pre class="result-pre" style="background: #fff2f2; border-color: #fde2e2;">{{ result.errorMessage }}</pre>
            </div>

            <div style="margin-bottom: 16px;">
              <div class="result-section-title">返回结果</div>
              <pre class="result-pre">{{ formatJson(result.result) }}</pre>
            </div>

            <div v-if="result.traces && result.traces.length > 0">
              <el-tabs v-model="traceTab" style="margin-top: 8px;">
                <el-tab-pane label="执行追踪（JSON）" name="json">
                  <el-collapse>
                    <el-collapse-item v-for="(trace, idx) in result.traces" :key="idx" :title="'步骤 ' + (idx + 1)">
                      <pre class="result-pre">{{ formatJson(trace) }}</pre>
                    </el-collapse-item>
                  </el-collapse>
                </el-tab-pane>
                <el-tab-pane label="表达式追踪树" name="tree">
                  <div class="trace-tree-wrap">
                    <trace-tree
                      :trace-info="traceInfoJson"
                      :var-map="varMap"
                      :function-name-map="functionNameMap"
                      :model-type="selectedRule ? selectedRule.modelType : ''"
                      :input-params="inputParamsJson"
                      :output-result="outputResultJson"
                      :rule-name="selectedRule ? selectedRule.ruleName : ''"
                      :rule-version="selectedRule ? selectedRule.currentVersion : ''"
                      :execute-time-ms="result.executeTimeMs"
                      :model-data="modelData"
                      :definition-model="definitionModel"
                    />
                  </div>
                </el-tab-pane>
              </el-tabs>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
<script>
import { listProjects } from '@/api/project'
import { listDefinitions, executeRule, getContent, listInputFields, refreshFields } from '@/api/definition'
import { listVariablesByProject, getVariableOptions, listVariables } from '@/api/variable'
import { getVariableTree, getDataObjectFieldOptions } from '@/api/dataObject'
import { listAllFunctionsByProject } from '@/api/function'
import { listAllModelsByProject, getModel } from '@/api/model'
import TraceTree from '@/components/common/TraceTree.vue'

export default {
  name: 'RuleTest',
  components: { TraceTree },
  data() {
    return {
      projects: [],
      rules: [],
      ruleScope: 'ALL',
      selectedProjectId: null,
      selectedRuleId: null,
      selectedRule: null,
      params: [],
      executing: false,
      result: null,
      traceTab: 'tree',
      varMap: {},
      functionNameMap: {},
      modelData: null,
      definitionModel: null
    }
  },
  created() {
    this.loadProjects()
    this.loadRulesByScope()
    this.loadVarMap()
    this.loadFunctionNameMap()
  },
  computed: {
    traceInfoJson: function () {
      if (!this.result || !this.result.traces || this.result.traces.length === 0) return ''
      return JSON.stringify(this.result.traces[0])
    },
    inputParamsJson: function () {
      // inputParamsJson 供 TraceTree 组件渲染入参，始终基于当前 params 构建
      return JSON.stringify(this.buildParamMap())
    },
    outputResultJson: function () {
      if (!this.result || this.result.result === null || this.result.result === undefined) return ''
      return JSON.stringify(this.result.result)
    }
  },
  methods: {
    async loadRulesByScope() {
      // 页面加载时根据当前 ruleScope 自动加载规则列表
      if (this.ruleScope === 'ALL') {
        try {
          const res = await listDefinitions({ pageNum: 1, pageSize: 1000 })
          this.rules = res.data.records || []
        } catch (e) { /* ignore */ }
      } else if (this.ruleScope === 'GLOBAL') {
        try {
          const res = await listDefinitions({ pageNum: 1, pageSize: 1000, scope: 'GLOBAL' })
          this.rules = res.data.records || []
        } catch (e) { /* ignore */ }
      }
      // PROJECT 模式由用户选择项目后触发，不在此加载
    },
    async loadProjects() {
      try {
        const res = await listProjects({ pageNum: 1, pageSize: 1000 })
        this.projects = res.data.records || []
      } catch (e) { /* ignore */ }
    },
    async loadVarMap() {
      try {
        var refs = await this.loadAllRuleRefs()
        var map = {}
        for (var i = 0; i < refs.length; i++) {
          if (refs[i].code && refs[i].label) {
            map[refs[i].code] = refs[i].label
          }
        }
        this.varMap = map
      } catch (e) { /* ignore */ }
    },
    async loadFunctionNameMap() {
      this.functionNameMap = {}
      if (!this.selectedRule) return
      var pid = this.selectedRule.projectId
      if (!pid) return
      try {
        var r = await listAllFunctionsByProject(pid)
        var funcData = (r && r.data) ? r.data : r
        var list = Array.isArray(funcData) ? funcData : (funcData && Array.isArray(funcData.records) ? funcData.records : [])
        var map = {}
        for (var j = 0; j < list.length; j++) {
          var f = list[j]
          if (f && f.funcCode && f.funcName) map[f.funcCode] = f.funcName
        }
        this.functionNameMap = map
      } catch (e) { /* ignore */ }
    },
    async loadModelJson() {
      this.modelData = null
      this.definitionModel = null
      if (!this.selectedRuleId || !this.selectedRule) return
      try {
        var r = await getContent(this.selectedRuleId)
        var content = r && r.data ? r.data : r
        if (content && content.modelJson) {
          var model = JSON.parse(content.modelJson)
          this.definitionModel = model
          if (model.nodes && model.edges) {
            this.modelData = { nodes: model.nodes, edges: model.edges }
          }
        }
      } catch (e) { /* ignore */ }
    },
    async onScopeChange() {
      this.selectedRuleId = null
      this.selectedRule = null
      this.rules = []
      this.params = []
      this.result = null
      this.selectedProjectId = null

      if (this.ruleScope === 'ALL') {
        try {
          const res = await listDefinitions({ pageNum: 1, pageSize: 1000 })
          this.rules = res.data.records || []
        } catch (e) { /* ignore */ }
      } else if (this.ruleScope === 'GLOBAL') {
        try {
          const res = await listDefinitions({ pageNum: 1, pageSize: 1000, scope: 'GLOBAL' })
          this.rules = res.data.records || []
        } catch (e) { /* ignore */ }
      } else if (this.ruleScope === 'PROJECT') {
        // 项目级规则由 onProjectChange 处理，此处不加载
      }
    },
    async onProjectChange() {
      this.selectedRuleId = null
      this.selectedRule = null
      this.rules = []
      this.params = []
      this.result = null
      if (!this.selectedProjectId) return
      try {
        const res = await listDefinitions({ pageNum: 1, pageSize: 1000, projectId: this.selectedProjectId })
        this.rules = res.data.records || []
      } catch (e) { /* ignore */ }
    },
    onRuleChange() {
      this.selectedRule = this.rules.find(r => r.id === this.selectedRuleId) || null
      this.result = null
      this.loadModelJson()
      this.loadFunctionNameMap()
      this.loadVarMap()
    },
    async loadVariables() {
      if (!this.selectedRule) return  // 未选择规则，提前返回
      try {
        var fields = await this.loadInputFieldsFromServer()
        if (fields.length > 0) {
          await this.applyInputFieldsToParams(fields)
          return
        }

        // 步骤1：解析 modelJson 中的变量 ID → varCode 映射
        var ruleVarIdMap = await this.getRuleVarInfos()
        var varIds = Object.keys(ruleVarIdMap)
        console.log('[变量加载] 规则引用的变量 ID:', varIds)

        if (varIds.length === 0) {
          this.notifyInfo('无法解析规则中的变量引用，请检查规则配置')
          return
        }

        // 步骤2：根据规则的 scope 加载项目中的所有变量
        var res
        if (this.selectedRule.scope === 'GLOBAL') {
          res = await listVariables({ scope: 'GLOBAL', pageNum: 1, pageSize: 10000 })
        } else {
          var ruleProjectId = this.selectedRule.projectId
          if (!ruleProjectId) {
            this.notifyWarning('无法获取规则所属项目，加载变量失败')
            return
          }
          res = await listVariablesByProject(ruleProjectId)
        }

        // listVariables 返回分页 { records, total }；listVariablesByProject 返回数组
        var varPayload = this.unwrapResponse(res)
        var allVars = varPayload && varPayload.records !== undefined ? varPayload.records : (varPayload || [])
        console.log('[变量加载] 项目变量总数:', allVars.length)

        // 步骤3：通过 varId 精确匹配，过滤出规则需要的变量
        // 步骤3.5：若 varId 匹配数为 0，改用 varCode 回退匹配（兼容交叉表/复杂交叉表等旧数据）
        var existingKeys = new Set(this.params.map(function (p) { return p.key }))
        var loadedCount = 0
        var idMatchedCount = 0
        for (var i = 0; i < allVars.length; i++) {
          var v = allVars[i]
          // 通过 varId 精确关联（modelJson 中 _varId → 变量表的 id）
          var info = ruleVarIdMap[v.id]
          if (!info) continue
          if (existingKeys.has(v.varCode)) continue
          var param = {
            key: v.varCode,
            label: v.varLabel,
            value: v.defaultValue || '',
            type: v.varType,
            example: v.exampleValue,
            fromVar: true,
            options: []
          }
          if (v.varType === 'ENUM') {
            try {
              var optRes = await getVariableOptions(v.id)
              param.options = this.unwrapResponse(optRes) || []
            } catch (e) { /* ignore */ }
          }
          this.params.push(param)
          existingKeys.add(v.varCode)
          loadedCount++
          idMatchedCount++
        }
        console.log('[变量加载] varId 精确匹配变量数:', idMatchedCount)
        // varId 匹配为 0 时，通过 varCode 回退匹配（交叉表/复杂交叉表等旧数据）
        if (idMatchedCount === 0) {
          console.log('[变量加载] varId 匹配数为 0，尝试通过 varCode 回退匹配')
          for (var vi = 0; vi < allVars.length; vi++) {
            var vv = allVars[vi]
            var vCode = vv.varCode || (vv.scriptName || '')
            if (!vCode) continue
            var vInfo = ruleVarIdMap[vCode]
            if (!vInfo) continue
            if (existingKeys.has(vCode)) continue
            var vparam = {
              key: vCode,
              label: vv.varLabel || vCode,
              value: vv.defaultValue || '',
              type: vv.varType,
              example: vv.exampleValue,
              fromVar: true,
              options: []
            }
            if (vv.varType === 'ENUM') {
              try {
                var vOptRes = await getVariableOptions(vv.id)
                vparam.options = this.unwrapResponse(vOptRes) || []
              } catch (e) { /* ignore */ }
            }
            this.params.push(vparam)
            existingKeys.add(vCode)
            loadedCount++
          }
          console.log('[变量加载] varCode 回退匹配后总变量数:', loadedCount)
        }
        if (loadedCount === 0) {
          this.notifyInfo('未匹配到规则引用的变量（检查变量 ID 是否一致）')
        }
      } catch (e) {
        console.error('[变量加载] 失败:', e)
        this.notifyError('加载变量失败')
      }
    },
    async loadInputFieldsFromServer() {
      try {
        await refreshFields(this.selectedRuleId)
      } catch (e) {
        // 旧数据或保存中状态下可能刷新失败，继续尝试读取已有字段/回退解析 modelJson
      }
      try {
        var res = await listInputFields(this.selectedRuleId)
        var fields = this.unwrapResponse(res)
        return Array.isArray(fields) ? fields.filter(function (f) { return f && f.scriptName }) : []
      } catch (e) {
        return []
      }
    },
    async applyInputFieldsToParams(fields) {
      var existingKeys = new Set(this.params.map(function (p) { return p.key }))
      var loadedCount = 0
      for (var i = 0; i < fields.length; i++) {
        var f = fields[i]
        var key = f.scriptName || f.fieldName
        if (!key || existingKeys.has(key)) continue
        var param = {
          key: key,
          label: f.fieldLabel || f.fieldName || key,
          value: f.defaultValue || '',
          type: await this.resolveInputFieldType(f),
          refType: f.refType || '',
          example: '',
          fromVar: true,
          options: []
        }
        if (param.type === 'ENUM') {
          try {
            if (f.refType === 'DATA_OBJECT') {
              var objOptRes = await getDataObjectFieldOptions(f.varId)
              param.options = this.unwrapResponse(objOptRes) || []
            } else {
              var optRes = await getVariableOptions(f.varId)
              param.options = this.unwrapResponse(optRes) || []
            }
          } catch (e) { /* ignore */ }
        }
        this.params.push(param)
        existingKeys.add(key)
        loadedCount++
      }
      if (loadedCount === 0) {
        this.notifyInfo('未匹配到规则引用的输入字段')
      }
    },
    async resolveInputFieldType(field) {
      var fieldType = field && field.fieldType ? field.fieldType : 'STRING'
      if (!field || field.refType !== 'MODEL' || !field.varId) return fieldType
      try {
        var res = await getModel(field.varId)
        var model = this.unwrapResponse(res)
        var outputs = model && Array.isArray(model.outputFields) ? model.outputFields : []
        if (outputs.length === 1 && outputs[0].fieldType) {
          return outputs[0].fieldType
        }
      } catch (e) { /* ignore */ }
      return fieldType
    },
    async loadAllRuleRefs() {
      var refs = []
      var pid = this.selectedRule && this.selectedRule.projectId != null ? this.selectedRule.projectId : 0
      try {
        var varRes
        if (this.selectedRule && this.selectedRule.scope === 'GLOBAL') {
          varRes = await listVariables({ scope: 'GLOBAL', pageNum: 1, pageSize: 10000 })
        } else if (pid) {
          varRes = await listVariablesByProject(pid)
        } else {
          varRes = await listVariables({ pageNum: 1, pageSize: 10000 })
        }
        var varData = this.unwrapResponse(varRes)
        var vars = Array.isArray(varData) ? varData : (varData && varData.records ? varData.records : [])
        vars.forEach(function (v) {
          refs.push({
            code: v.scriptName || v.varCode,
            label: v.varLabel || v.varCode,
            type: v.varType,
            refType: v.varSource === 'CONSTANT' ? 'CONSTANT' : 'VARIABLE'
          })
        })
      } catch (e) { /* ignore */ }

      try {
        var treeRes = await getVariableTree(pid || 0)
        var treeData = this.unwrapResponse(treeRes)
        var tree = Array.isArray(treeData) ? treeData : (treeData && treeData.tree ? treeData.tree : [])
        this.collectDataObjectRefs(tree, refs)
      } catch (e) { /* ignore */ }

      try {
        var modelRes = await listAllModelsByProject(pid || 0)
        var modelData = this.unwrapResponse(modelRes)
        var models = Array.isArray(modelData) ? modelData : (modelData && modelData.records ? modelData.records : [])
        models.forEach(function (m) {
          if (!m.modelCode) return
          refs.push({ code: m.modelCode, label: m.modelName || m.modelCode, type: 'MODEL', refType: 'MODEL' })
        })
      } catch (e) { /* ignore */ }
      return refs
    },
    collectDataObjectRefs(tree, refs) {
      var visit = function (rows, objScriptName, objectLabel) {
        (rows || []).forEach(function (row) {
          var scriptName = row.scriptName || row.varCode || ''
          var code = scriptName
          if (objScriptName && code.indexOf(objScriptName + '.') !== 0) {
            code = objScriptName + '.' + scriptName
          }
          refs.push({
            code: code,
            label: row.varLabel || row.varCode || code,
            type: row.varType,
            refType: 'DATA_OBJECT',
            objectLabel: objectLabel
          })
          if (row.children && row.children.length) visit(row.children, objScriptName, objectLabel)
        })
      }
      var rootNodes = tree || []
      rootNodes.forEach(function (node) {
        var obj = node.object || node
        var objScriptName = obj.scriptName || obj.objectCode || ''
        var objectLabel = obj.objectLabel || obj.objectCode || objScriptName
        var rows = node.flatVariables || node.variables || []
        visit(rows, objScriptName, objectLabel)
      })
    },
    /**
     * 从规则内容中解析出实际使用的变量信息（通过 varId 精确关联，避免 varCode 模糊匹配）
     * 回退策略：当 _varId 不存在时，通过 varCode 匹配
     * 返回 { varId → { varCode, varLabel, varType, defaultValue, exampleValue } }
     * 注意：varId 可能是字符串类型（如 "1"、"2"），匹配时需做类型兼容
     */
    async getRuleVarInfos() {
      if (!this.selectedRuleId || !this.selectedRule) return {}
      try {
        const res = await getContent(this.selectedRuleId)
        const content = this.unwrapResponse(res)
        if (!content || !content.modelJson) {
          console.warn('[变量加载] 规则内容为空或无 modelJson')
          return {}
        }
        const model = JSON.parse(content.modelJson)
        const modelType = this.selectedRule.modelType
        console.log('[变量加载] 开始解析规则:', this.selectedRule.ruleCode, '模型类型:', modelType)

        // 用 Map 收集，varId 相同时只保留第一个（兼容同一变量多处引用）
        // 注意：当无 _varId 时，也会将 varCode 作为 key 存入（用于回退匹配）
        const varIdMap = {}
        this.collectVarIds(model, modelType, varIdMap)
        console.log('[变量加载] 解析到的变量映射 keys:', Object.keys(varIdMap))
        return varIdMap
      } catch (e) {
        console.error('[变量加载] 解析规则变量失败:', e)
        return {}
      }
    },
    /**
     * 根据模型类型收集变量 ID / varCode 到 varIdMap
     * - 有 _varId 时：varIdMap[_varId] = { varCode }
     * - 无 _varId 但有 varCode 时：varIdMap[varCode] = { varCode }（用于回退匹配）
     * 仅收集带 _varId 或 varCode 的变量
     */
    collectVarIds(model, modelType, varIdMap) {
      if (!model) return
      switch (modelType) {
        case 'TABLE': {
          (model.rules || []).forEach(rule => {
            this.extractVarIdsFromConditionRoot(rule.conditionRoot, varIdMap)
            this.extractVarIdsFromConditions(rule.conditions, varIdMap)
          })
          this.extractVarIdsFromConditions(model.conditions, varIdMap)
          break
        }
        case 'TREE': {
          this.extractVarIdsFromTreeNodes(model.nodes, model.edges, varIdMap)
          this.extractVarIdsFromConditionRoot(model.conditionRoot, varIdMap)
          break
        }
        case 'FLOW': {
          this.extractVarIdsFromFlowNodes(model.nodes, model.edges, varIdMap)
          this.extractVarIdsFromConditionRoot(model.conditionRoot, varIdMap)
          break
        }
        case 'RULE_SET': {
          (model.rules || []).forEach(rule => {
            this.extractVarIdsFromConditionRoot(rule.conditionRoot, varIdMap)
            this.extractVarIdsFromConditions(rule.conditions, varIdMap)
            this.extractVarIdsFromActionData(rule.actionData, varIdMap)
          })
          break
        }
        case 'CROSS': {
          // 交叉表：行变量、列变量、结果变量
          // 优先用 _varId（精确匹配），回退用 varCode
          if (model.rowVar) {
            if (model.rowVar._varId && model.rowVar.varCode) varIdMap[model.rowVar._varId] = { varCode: model.rowVar.varCode }
            else if (model.rowVar.varCode) varIdMap[model.rowVar.varCode] = { varCode: model.rowVar.varCode }
          }
          if (model.colVar) {
            if (model.colVar._varId && model.colVar.varCode) varIdMap[model.colVar._varId] = { varCode: model.colVar.varCode }
            else if (model.colVar.varCode) varIdMap[model.colVar.varCode] = { varCode: model.colVar.varCode }
          }
          break
        }
        case 'CROSS_ADV': {
          (model.rowDimensions || []).forEach(dim => {
            if (dim._varId && dim.varCode) varIdMap[dim._varId] = { varCode: dim.varCode }
            else if (dim.varCode) varIdMap[dim.varCode] = { varCode: dim.varCode }
            this.extractVarIdsFromConditionRoot(dim.conditionRoot, varIdMap)
          })
          ;(model.colDimensions || []).forEach(dim => {
            if (dim._varId && dim.varCode) varIdMap[dim._varId] = { varCode: dim.varCode }
            else if (dim.varCode) varIdMap[dim.varCode] = { varCode: dim.varCode }
            this.extractVarIdsFromConditionRoot(dim.conditionRoot, varIdMap)
          })
          break
        }
        case 'SCORE': {
          // 评分卡：condVar 对应的 _varId 从评分项中提取
          (model.scoreItems || []).forEach(item => {
            this.extractVarIdsFromConditionRoot(item.conditionRoot, varIdMap)
            // 优先使用 item._varId（由 var-picker 选择时填充）
            if (item._varId && item.condVar) varIdMap[item._varId] = { varCode: item.condVar }
            else if (!item._varId && item.condVar) varIdMap[item.condVar] = { varCode: item.condVar }
          })
          break
        }
        case 'SCORE_ADV': {
          (model.dimensionGroups || []).forEach(group => {
            (group.dimensions || []).forEach(dim => {
              if (dim._varId && dim.varCode) varIdMap[dim._varId] = { varCode: dim.varCode }
              else if (dim.varCode) varIdMap[dim.varCode] = { varCode: dim.varCode }
              ;(dim.rules || []).forEach(rule => {
                if (rule._varId && rule.condVar) varIdMap[rule._varId] = { varCode: rule.condVar }
                else if (!rule._varId && rule.condVar) varIdMap[rule.condVar] = { varCode: rule.condVar }
              })
            })
          })
          break
        }
        case 'SCRIPT': {
          (model.inputVars || []).forEach(v => {
            if (v._varId && v.varCode) varIdMap[v._varId] = { varCode: v.varCode }
            else if (v.varCode) varIdMap[v.varCode] = { varCode: v.varCode }
          })
          break
        }
        default: {
          // 兜底：递归搜索所有 _varId 和 varCode
          this.extractAllVarIds(model, varIdMap)
        }
      }
    },
    /**
     * 兜底：递归搜索所有 _varId
     */
    extractAllVarIds(obj, varIdMap) {
      if (!obj || typeof obj !== 'object') return
      if (Array.isArray(obj)) {
        obj.forEach(item => this.extractAllVarIds(item, varIdMap))
      } else {
        if (obj._varId && obj.varCode && !varIdMap[obj._varId]) {
          varIdMap[obj._varId] = { varCode: obj.varCode }
        }
        for (const key in obj) {
          if (key !== '_varId' && key !== 'varCode') {
            this.extractAllVarIds(obj[key], varIdMap)
          }
        }
      }
    },
    /**
     * 从 conditions 数组提取变量 ID
     */
    extractVarIdsFromConditions(conditions, varIdMap) {
      if (!conditions) return
      conditions.forEach(c => {
        if (c._varId && c.varCode && !varIdMap[c._varId]) {
          varIdMap[c._varId] = { varCode: c.varCode }
        }
      })
    },
    /**
     * 从 conditionRoot 递归提取变量 ID
     */
    extractVarIdsFromConditionRoot(root, varIdMap) {
      if (!root) return
      if (root._varId && root.varCode && !varIdMap[root._varId]) {
        varIdMap[root._varId] = { varCode: root.varCode }
      }
      if (root.children) {
        root.children.forEach(child => this.extractVarIdsFromConditionRoot(child, varIdMap))
      }
    },
    /**
     * 从决策树 nodes + edges 递归提取变量 ID
     */
    extractVarIdsFromTreeNodes(nodes, edges, varIdMap) {
      if (!nodes) return
      nodes.forEach(node => {
        if (node._varId && node.varCode && !varIdMap[node._varId]) {
          varIdMap[node._varId] = { varCode: node.varCode }
        }
        if (node.actionData) {
          this.extractVarIdsFromActionData(node.actionData, varIdMap)
        }
        if (node.children) {
          this.extractVarIdsFromTreeNodes(node.children, null, varIdMap)
        }
      })
    },
    /**
     * 从决策流 nodes + edges 递归提取变量 ID
     */
    extractVarIdsFromFlowNodes(nodes, edges, varIdMap) {
      if (!nodes) return
      nodes.forEach(node => {
        if (node._varId && node.varCode && !varIdMap[node._varId]) {
          varIdMap[node._varId] = { varCode: node.varCode }
        }
        if (node.properties && node.properties.inputVars) {
          node.properties.inputVars.forEach(v => {
            if (v._varId && v.varCode && !varIdMap[v._varId]) {
              varIdMap[v._varId] = { varCode: v.varCode }
            }
          })
        }
        if (node.actionData) {
          this.extractVarIdsFromActionData(node.actionData, varIdMap)
        }
        if (node.properties && node.properties.actionData) {
          this.extractVarIdsFromActionData(node.properties.actionData, varIdMap)
        }
        if (node.children) {
          this.extractVarIdsFromFlowNodes(node.children, null, varIdMap)
        }
      })
    },
    /**
     * 从 actionData 数组提取变量 ID
     */
    extractVarIdsFromActionData(actionData, varIdMap) {
      if (!actionData || !Array.isArray(actionData)) return
      actionData.forEach(a => {
        const addFieldRef = (idKey, codeKey) => {
          if (a[idKey] && a[codeKey]) {
            varIdMap[a[idKey]] = { varCode: a[codeKey] }
          }
        }
        if (a._varId && a.varCode && !varIdMap[a._varId]) {
          varIdMap[a._varId] = { varCode: a.varCode }
        }
        addFieldRef('_condVarId', 'condVar')
        addFieldRef('_matchVarId', 'matchVar')
        addFieldRef('_checkVarId', 'checkVar')
        if (a.type === 'if-block' && a.branches) {
          a.branches.forEach(branch => {
            if (branch._condVarId && branch.condVar) {
              varIdMap[branch._condVarId] = { varCode: branch.condVar }
            } else if (branch._varId && branch.condVar && !varIdMap[branch._varId]) {
              varIdMap[branch._varId] = { varCode: branch.condVar }
            }
            this.extractVarIdsFromActionData(branch.actions, varIdMap)
          })
        }
        if (a.cases) {
          a.cases.forEach(item => this.extractVarIdsFromActionData(item.actions, varIdMap))
        }
        this.extractVarIdsFromActionData(a.actions, varIdMap)
        this.extractVarIdsFromActionData(a.defaultActions, varIdMap)
      })
    },
    addParam() {
      this.params.push({ key: '', label: '', value: '', type: 'STRING', example: '', fromVar: false, options: [] })
    },
    applyRiskDemoParams() {
      const demoParams = [
        { key: 'requestId', label: '请求流水号', value: 'REQ_DEMO_001', type: 'STRING' },
        { key: 'taxpayerType', label: '客商类型', value: '一般纳税人', type: 'STRING' },
        { key: 'goodsCategory', label: '产品总线', value: '货物', type: 'STRING' },
        { key: 'totalAmount', label: '交易金额', value: 113000, type: 'NUMBER' },
        { key: 'isExempt', label: '是否减免', value: false, type: 'BOOLEAN' },
        { key: 'annualRevenue', label: '年营收', value: 5000, type: 'NUMBER' },
        { key: 'taxComplianceScore', label: '合规评分', value: 85, type: 'NUMBER' },
        { key: 'yearsInBusiness', label: '经营年限', value: 10, type: 'NUMBER' },
        { key: 'hasViolation', label: '严重违规', value: false, type: 'BOOLEAN' },
        { key: 'creditLevel', label: '信用等级', value: 'A', type: 'STRING' },
        { key: 'taxBurdenDeviation', label: '指标偏离度', value: 0.08, type: 'NUMBER' },
        { key: 'violationCount', label: '历史风险事件次数', value: 0, type: 'NUMBER' },
        { key: 'serviceType', label: '业务类型', value: 'ICT服务', type: 'ENUM' },
        { key: 'paymentMode', label: '结算方式', value: '后付费', type: 'ENUM' },
        { key: 'customerType', label: '客户类型', value: '企业客户', type: 'ENUM' },
        { key: 'taxpayerQualification', label: '纳税人资格', value: '一般纳税人', type: 'ENUM' },
        { key: 'customerLevel', label: '客户等级', value: '金', type: 'ENUM' },
        { key: 'monthlyConsumption', label: '月消费金额', value: 5000, type: 'NUMBER' },
        { key: 'invoiceDeviationRate', label: '开票偏差率', value: 0.05, type: 'NUMBER' },
        { key: 'redInvoiceRatio', label: '红冲发票比例', value: 0.02, type: 'NUMBER' },
        { key: 'zeroRateInvoiceRatio', label: '零税率发票占比', value: 0.01, type: 'NUMBER' },
        { key: 'crossRegionInvoiceRatio', label: '跨地区开票比例', value: 0.08, type: 'NUMBER' },
        { key: 'billingAmount', label: '含税账单金额', value: 100000, type: 'NUMBER' },
        { key: 'basicServiceRatio', label: '基础通信占比', value: 0.6, type: 'NUMBER' },
        { key: 'vasServiceRatio', label: '增值业务占比', value: 0.4, type: 'NUMBER' }
      ]
      this.params = demoParams.map(item => ({
        key: item.key,
        label: item.label,
        value: item.value,
        type: item.type,
        example: String(item.value),
        fromVar: false,
        options: []
      }))
      this.result = null
    },
    async handleExecute() {
      if (!this.selectedRuleId) return
      const paramMap = this.buildParamMap()
      this.executing = true
      this.result = null
      try {
        const res = await executeRule({ definitionId: this.selectedRuleId, params: paramMap })
        this.result = this.unwrapResponse(res)
        // 执行完成后切换到追踪树标签页
        this.traceTab = 'tree'
      } catch (e) {
        this.result = { success: false, errorMessage: e.message || '执行异常', executeTimeMs: 0 }
      } finally {
        this.executing = false
      }
    },
    handleClear() {
      this.params = []
      this.result = null
    },
    handleClearParams() {
      this.params = []
    },
    unwrapResponse(res) {
      if (res && Object.prototype.hasOwnProperty.call(res, 'data')) return res.data
      return res
    },
    notifyInfo(message) {
      if (this.$message && this.$message.info) this.$message.info(message)
    },
    notifyWarning(message) {
      if (this.$message && this.$message.warning) this.$message.warning(message)
    },
    notifyError(message) {
      if (this.$message && this.$message.error) this.$message.error(message)
    },
    buildParamMap() {
      const paramMap = {}
      for (const p of this.params) {
        if (!p.key) continue
        let val = p.value
        if (this.isNumberType(p.type) && val !== '' && val !== null) {
          val = Number(val)
        } else if (p.type === 'BOOLEAN') {
          val = val === true || val === 'true'
        }
        this.setParamValue(paramMap, p.key, val)
      }
      return paramMap
    },
    isNumberType(type) {
      return ['NUMBER', 'INTEGER', 'DOUBLE', 'FLOAT', 'DECIMAL', 'LONG', 'PROBABILITY'].indexOf(type) >= 0
    },
    setParamValue(target, key, value) {
      if (!key || key.indexOf('.') < 0) {
        target[key] = value
        return
      }
      var parts = key.split('.').filter(Boolean)
      if (parts.length === 0) return
      var cur = target
      for (var i = 0; i < parts.length - 1; i++) {
        if (!cur[parts[i]] || typeof cur[parts[i]] !== 'object' || Array.isArray(cur[parts[i]])) {
          cur[parts[i]] = {}
        }
        cur = cur[parts[i]]
      }
      cur[parts[parts.length - 1]] = value
    },
    mtl(t) {
      return { TABLE: '决策表', TREE: '决策树', FLOW: '决策流', RULE_SET: '规则集', CROSS: '交叉表', SCORE: '评分卡', CROSS_ADV: '复杂交叉表', SCORE_ADV: '复杂评分卡', SCRIPT: 'QL脚本' }[t] || t
    },
    formatJson(obj) {
      if (obj === null || obj === undefined) return '(空)'
      try {
        if (typeof obj === 'string') {
          return JSON.stringify(JSON.parse(obj), null, 2)
        }
        return JSON.stringify(obj, null, 2)
      } catch (e) {
        return String(obj)
      }
    }
  }
}
</script>
<style lang="scss" scoped>
.test-layout {
  display: flex;
  gap: 16px;
  min-height: calc(100vh - 140px);
}
.test-left {
  flex: 0 0 480px;
  min-width: 380px;
}
.test-right {
  flex: 1;
  min-width: 0;
}
.rule-info {
  margin-top: 12px;
}
.param-row {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}
.param-key {
  flex: 0 0 140px;
  margin-right: 8px;
}
.param-label {
  flex: 0 0 auto;
  color: #999;
  font-size: 12px;
  margin-right: 8px;
  white-space: nowrap;
}
.param-value {
  flex: 1;
  min-width: 0;
}
.result-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
}
.result-section-title {
  font-weight: bold;
  font-size: 13px;
  margin-bottom: 8px;
  color: #282828;
}
.result-pre {
  background: #f5f7fa;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 12px;
  font-size: 13px;
  line-height: 1.5;
  overflow: auto;
  max-height: 300px;
  white-space: pre-wrap;
  word-break: break-all;
}
.trace-tree-wrap {
  padding: 8px 0;
  max-height: 500px;
  overflow: auto;
}
@media screen and (max-width: 1000px) {
  .test-layout {
    flex-direction: column;
  }
  .test-left {
    flex: none;
    min-width: 0;
  }
}
</style>
