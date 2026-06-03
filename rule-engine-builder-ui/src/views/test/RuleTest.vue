<template>
  <div class="uiue-list-page">
    <div class="test-layout">
      <!-- 左侧：选择规则 + 参数输入 -->
      <div class="test-left">
        <div class="uiue-card">
          <div class="uiue-card-title">选择规则</div>
          <el-form size="small" label-width="80px">
            <el-form-item label="项目">
              <el-select v-model="selectedProjectId" placeholder="请选择项目" clearable style="width: 100%;" @change="onProjectChange">
                <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="规则">
              <el-select v-model="selectedRuleId" placeholder="请先选择项目" :disabled="!selectedProjectId" style="width: 100%;" filterable @change="onRuleChange">
                <el-option v-for="r in rules" :key="r.id" :label="r.ruleName + ' (' + r.ruleCode + ')'" :value="r.id" />
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
            <el-button type="text" size="small" style="margin-left: 12px;" @click="loadVariables" v-if="selectedProjectId">
              <i class="el-icon-refresh" /> 加载项目变量
            </el-button>
            <el-button type="text" size="small" style="margin-left: 8px;" @click="addParam">
              <i class="el-icon-plus" /> 手动添加
            </el-button>
          </div>
          <div v-if="params.length === 0" style="color: #999; padding: 12px 0; text-align: center;">
            请选择项目后加载变量，或手动添加参数
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
              <div class="result-section-title">执行追踪</div>
              <el-collapse>
                <el-collapse-item v-for="(trace, idx) in result.traces" :key="idx" :title="'步骤 ' + (idx + 1)">
                  <pre class="result-pre">{{ formatJson(trace) }}</pre>
                </el-collapse-item>
              </el-collapse>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
<script>
import { listProjects } from '@/api/project'
import { listDefinitions, executeRule, getContent } from '@/api/definition'
import { listVariablesByProject, getVariableOptions } from '@/api/variable'

export default {
  name: 'RuleTest',
  data() {
    return {
      projects: [],
      rules: [],
      selectedProjectId: null,
      selectedRuleId: null,
      selectedRule: null,
      params: [],
      executing: false,
      result: null
    }
  },
  created() {
    this.loadProjects()
  },
  methods: {
    async loadProjects() {
      try {
        const res = await listProjects({ pageNum: 1, pageSize: 1000 })
        this.projects = res.data.records || []
      } catch (e) { /* ignore */ }
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
    },
    async loadVariables() {
      if (!this.selectedProjectId) return
      // 每次加载前清空已有参数
      this.params = []
      try {
        const res = await listVariablesByProject(this.selectedProjectId)
        console.log('[变量加载] API 响应原始数据:', res)
        const vars = res.data || []
        console.log('[变量加载] API 返回的变量列表:', vars)
        console.log('[变量加载] API 变量代码列表:', vars.map(v => v.varCode))

        // 获取规则内容中使用的变量代码
        let ruleVarCodes = new Set()
        if (this.selectedRule) {
          ruleVarCodes = await this.getRuleVarCodes()
        }
        console.log('[变量加载] 规则需要的变量代码:', Array.from(ruleVarCodes))

        // 如果有规则且解析到变量，则只加载规则需要的变量（不限类型）
        // 否则加载项目中所有 INPUT 类型的变量
        let targetVars = vars
        if (ruleVarCodes.size > 0) {
          // 规范化函数：去除下划线转为驼峰，或反向转换，进行模糊匹配
          const normalize = s => s.replace(/_/g, '').toLowerCase()
          const ruleNormMap = {}
          Array.from(ruleVarCodes).forEach(code => { ruleNormMap[normalize(code)] = code })
          targetVars = vars.filter(v => {
            const vNorm = normalize(v.varCode)
            return vNorm in ruleNormMap
          })
        } else {
          targetVars = vars.filter(v => v.varSource === 'INPUT')
        }
        console.log('[变量加载] 过滤后匹配的变量:', targetVars.map(v => v.varCode))

        const existingKeys = new Set(this.params.map(p => p.key))
        for (const v of targetVars) {
          if (existingKeys.has(v.varCode)) continue
          const param = {
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
              const optRes = await getVariableOptions(v.id)
              param.options = optRes.data || []
            } catch (e) { /* ignore */ }
          }
          this.params.push(param)
        }
        if (targetVars.length === 0) {
          this.$message.info('该项目暂无符合条件的变量')
        }
      } catch (e) {
        this.$message.error('加载变量失败')
      }
    },
    /**
     * 从规则内容中解析出实际使用的变量代码集合
     */
    async getRuleVarCodes() {
      const codes = new Set()
      if (!this.selectedRuleId || !this.selectedRule) return codes
      try {
        const res = await getContent(this.selectedRuleId)
        const content = res.data
        if (!content || !content.modelJson) {
          console.warn('[变量加载] 规则内容为空或无 modelJson')
          return codes
        }
        const model = JSON.parse(content.modelJson)
        const modelType = this.selectedRule.modelType
        console.log('[变量加载] 开始解析规则:', this.selectedRule.ruleCode, '模型类型:', modelType)
        console.log('[变量加载] modelJson 结构:', JSON.stringify(model, null, 2))
        this.collectVarCodes(model, modelType, codes)
        console.log('[变量加载] 解析到的变量代码:', Array.from(codes))
      } catch (e) {
        console.error('[变量加载] 解析规则变量失败:', e)
      }
      return codes
    },
    /**
     * 根据模型类型收集变量代码
     */
    collectVarCodes(model, modelType, codes) {
      if (!model) return
      switch (modelType) {
        case 'TABLE': {
          // 决策表：从 rules 中的 conditionRoot 和 actions 提取
          ;(model.rules || []).forEach(rule => {
            // 从条件树提取
            this.extractFromConditionRoot(rule.conditionRoot, codes)
            // 从条件数组提取（兼容旧格式）
            this.extractFromConditions(rule.conditions, codes)
            // 从动作提取输出变量
            ;(rule.actions || []).forEach(a => {
              if (a.varCode) codes.add(a.varCode)
              if (a.target) codes.add(a.target)
            })
          })
          // 兼容旧格式：顶层 conditions 和 actions
          this.extractFromConditions(model.conditions, codes)
          ;(model.actions || []).forEach(a => {
            if (a.varCode) codes.add(a.varCode)
            if (a.target) codes.add(a.target)
          })
          break
        }
        case 'TREE':
          this.extractFromTreeNodes(model.nodes, model.edges, codes)
          break
        case 'FLOW':
          this.extractFromFlowNodes(model.nodes, model.edges, codes)
          break
        case 'TABLE': {
          // 决策表：conditions 中的变量是输入，actions 中的变量是输出
          this.extractFromConditions(model.conditions, codes)
          ;(model.actions || []).forEach(a => {
            if (a.varCode) codes.add(a.varCode)
          })
          break
        }
        case 'CROSS': {
          // 交叉表：行变量、列变量、结果变量
          if (model.rowVar && model.rowVar.varCode) codes.add(model.rowVar.varCode)
          if (model.colVar && model.colVar.varCode) codes.add(model.colVar.varCode)
          if (model.resultVar && model.resultVar.varCode) codes.add(model.resultVar.varCode)
          break
        }
        case 'CROSS_ADV': {
          // 复杂交叉表：行维度变量、列维度变量、结果变量
          ;(model.rowDimensions || []).forEach(dim => { if (dim.varCode) codes.add(dim.varCode) })
          ;(model.colDimensions || []).forEach(dim => { if (dim.varCode) codes.add(dim.varCode) })
          if (model.resultVar && model.resultVar.varCode) codes.add(model.resultVar.varCode)
          break
        }
        case 'SCORE': {
          // 评分卡：评分项条件变量、结果变量
          ;(model.scoreItems || []).forEach(item => {
            // 从 condition 表达式中提取变量
            if (item.condition && typeof item.condition === 'string') {
              this.extractFromConditionExpression(item.condition, codes)
            }
            if (item.condVar) codes.add(item.condVar)
          })
          if (model.resultVar && model.resultVar.varCode) codes.add(model.resultVar.varCode)
          break
        }
        case 'SCORE_ADV': {
          // 复杂评分卡：维度变量、结果变量
          ;(model.dimensionGroups || []).forEach(group => {
            ;(group.dimensions || []).forEach(dim => {
              if (dim.varCode) codes.add(dim.varCode)
              // 从维度规则中提取变量
              ;(dim.rules || []).forEach(rule => {
                if (rule.condVar) codes.add(rule.condVar)
                if (rule.condition && typeof rule.condition === 'string') {
                  this.extractFromConditionExpression(rule.condition, codes)
                }
                this.extractFromConditionRoot(rule.conditionRoot, codes)
              })
            })
          })
          if (model.resultVar && model.resultVar.varCode) codes.add(model.resultVar.varCode)
          break
        }
        case 'SCRIPT': {
          // QL脚本：inputVars + 从 script 内容提取变量引用
          ;(model.inputVars || []).forEach(v => { if (v.varCode) codes.add(v.varCode) })
          if (model.script) {
            // 从脚本内容中提取变量引用（简单匹配）
            const scriptVars = model.script.match(/[a-zA-Z_]\w*(?=\s*[!=<>+\-*/%()\[\]{}]|;|$)/g) || []
            scriptVars.forEach(v => codes.add(v))
          }
          break
        }
        default:
          // 兜底：从整个 model 中递归搜索所有 varCode 字段
          this.extractAllVarCodes(model, codes)
      }
    },
    /**
     * 兜底：递归搜索所有 varCode
     */
    extractAllVarCodes(obj, codes) {
      if (!obj || typeof obj !== 'object') return
      if (Array.isArray(obj)) {
        obj.forEach(item => this.extractAllVarCodes(item, codes))
      } else {
        if (obj.varCode) codes.add(obj.varCode)
        if (obj.conditionExpression) {
          // 从条件表达式中提取变量名（简单处理）
          const match = obj.conditionExpression.match(/^([a-zA-Z_]\w*)\s*[!=<>]/)
          if (match) codes.add(match[1])
        }
        if (obj.condition) {
          if (typeof obj.condition === 'string') {
            const match = obj.condition.match(/^([a-zA-Z_]\w*)\s*[!=<>]/)
            if (match) codes.add(match[1])
          } else {
            this.extractAllVarCodes(obj.condition, codes)
          }
        }
        if (obj.actions) {
          obj.actions.forEach(a => {
            if (a.varCode) codes.add(a.varCode)
            if (a.target) codes.add(a.target)
          })
        }
        for (const key in obj) {
          if (key !== 'varCode' && key !== 'condition' && key !== 'conditionExpression' && key !== 'actions') {
            this.extractAllVarCodes(obj[key], codes)
          }
        }
      }
    },
    /**
     * 从 conditions 数组提取变量代码
     */
    extractFromConditions(conditions, codes) {
      if (!conditions) return
      conditions.forEach(c => {
        if (c.varCode) codes.add(c.varCode)
        if (c.conditionExpression) {
          const match = c.conditionExpression.match(/^([a-zA-Z_]\w*)\s*[!=<>]/)
          if (match) codes.add(match[1])
        }
      })
    },
    /**
     * 从 conditionRoot 递归提取变量代码
     */
    extractFromConditionRoot(root, codes) {
      if (!root) return
      if (root.type === 'leaf' && root.varCode) {
        codes.add(root.varCode)
      }
      if (root.varCode) codes.add(root.varCode)
      if (root.conditionExpression) {
        const match = root.conditionExpression.match(/^([a-zA-Z_]\w*)\s*[!=<>]/)
        if (match) codes.add(match[1])
      }
      if (root.condition) {
        if (typeof root.condition === 'string') {
          const match = root.condition.match(/^([a-zA-Z_]\w*)\s*[!=<>]/)
          if (match) codes.add(match[1])
        } else {
          this.extractAllVarCodes(root.condition, codes)
        }
      }
      if (root.children) {
        root.children.forEach(child => this.extractFromConditionRoot(child, codes))
      }
    },
    /**
     * 从条件表达式（如 "creditLevel == \"A\"" 或 "annualRevenue >= 1000 && annualRevenue < 5000"）提取变量代码
     */
    extractFromConditionExpression(expr, codes) {
      if (!expr || typeof expr !== 'string') return
      // 匹配变量名：字母或下划线开头，后面是字母数字下划线
      // 排除常见的关键字和函数名
      const keywords = ['true', 'false', 'null', 'undefined', 'NaN', 'Infinity', 'and', 'or', 'not']
      const matches = expr.match(/[a-zA-Z_][a-zA-Z0-9_]*/g) || []
      matches.forEach(v => {
        if (!keywords.includes(v)) {
          codes.add(v)
        }
      })
    },
    /**
     * 从决策树 nodes + edges 递归提取变量代码
     */
    extractFromTreeNodes(nodes, edges, codes) {
      if (!nodes) return
      // 1. 从 nodes 中提取
      nodes.forEach(node => {
        // 优先从 varCode 提取
        if (node.varCode) codes.add(node.varCode)
        // 从 conditionExpression 提取（如 "loanApplyId == 1"）
        if (node.conditionExpression) {
          const match = node.conditionExpression.match(/^([a-zA-Z_]\w*)\s*[!=<>]/)
          if (match) codes.add(match[1])
        }
        // 从 condition 提取（可能是字符串或对象）
        if (node.condition) {
          if (typeof node.condition === 'string') {
            const match = node.condition.match(/^([a-zA-Z_]\w*)\s*[!=<>]/)
            if (match) codes.add(match[1])
          } else {
            this.extractAllVarCodes(node.condition, codes)
          }
        }
        // 从 actionData 提取变量
        if (node.actionData) {
          this.extractFromActionData(node.actionData, codes)
        }
        // 递归处理子节点
        if (node.children) {
          this.extractFromTreeNodes(node.children, null, codes)
        }
      })
      // 2. 从 edges 中提取条件表达式变量（如 "loanApplyId == 1"）
      if (edges) {
        edges.forEach(edge => {
          if (edge.conditionExpression) {
            const match = edge.conditionExpression.match(/^([a-zA-Z_]\w*)\s*[!=<>]/)
            if (match) codes.add(match[1])
          }
        })
      }
    },
    /**
     * 从决策流 nodes + edges 递归提取变量代码
     */
    extractFromFlowNodes(nodes, edges, codes) {
      if (!nodes) return
      nodes.forEach(node => {
        if (node.varCode) codes.add(node.varCode)
        if (node.conditionExpression) {
          const match = node.conditionExpression.match(/^([a-zA-Z_]\w*)\s*[!=<>]/)
          if (match) codes.add(match[1])
        }
        if (node.condition) {
          if (typeof node.condition === 'string') {
            const match = node.condition.match(/^([a-zA-Z_]\w*)\s*[!=<>]/)
            if (match) codes.add(match[1])
          } else {
            this.extractAllVarCodes(node.condition, codes)
          }
        }
        if (node.properties && node.properties.inputVars) {
          node.properties.inputVars.forEach(v => { if (v.varCode) codes.add(v.varCode) })
        }
        // 从 actionData 提取变量
        if (node.actionData) {
          this.extractFromActionData(node.actionData, codes)
        }
        // 兼容 logicflow 中的 properties.actionData
        if (node.properties && node.properties.actionData) {
          this.extractFromActionData(node.properties.actionData, codes)
        }
        if (node.children) {
          this.extractFromFlowNodes(node.children, null, codes)
        }
      })
      // 从 edges 中提取条件表达式
      if (edges) {
        edges.forEach(edge => {
          if (edge.conditionExpression) {
            const match = edge.conditionExpression.match(/^([a-zA-Z_]\w*)\s*[!=<>]/)
            if (match) codes.add(match[1])
          }
        })
      }
    },
    /**
     * 从 actionData 数组提取变量代码
     * 支持：assign, func-call, if-block 等类型
     */
    extractFromActionData(actionData, codes) {
      if (!actionData || !Array.isArray(actionData)) return
      actionData.forEach(a => {
        // 赋值动作：target 是输出变量，value 中可能包含输入变量
        if (a.type === 'assign') {
          if (a.target) codes.add(a.target)
          if (a.value && typeof a.value === 'string') {
            // 从 value 表达式中提取变量名（如 "totalAmount / (1 + taxRate)"）
            const vars = a.value.match(/[a-zA-Z_]\w*/g) || []
            vars.forEach(v => {
              // 排除常见的关键字和函数名
              if (!['true', 'false', 'null', 'undefined', 'NaN', 'Infinity'].includes(v)) {
                codes.add(v)
              }
            })
          }
        }
        // 函数调用：target 是输出变量，args 是输入参数
        else if (a.type === 'func-call') {
          if (a.target) codes.add(a.target)
          if (a.args && Array.isArray(a.args)) {
            a.args.forEach(arg => {
              if (typeof arg === 'string' && /^[a-zA-Z_]\w*$/.test(arg)) {
                codes.add(arg)
              }
            })
          }
        }
        // if-block：处理分支中的条件变量和动作
        else if (a.type === 'if-block' && a.branches && Array.isArray(a.branches)) {
          a.branches.forEach(branch => {
            // 条件变量（if/elseif 分支）
            if (branch.condVar) codes.add(branch.condVar)
            // 分支中的动作
            if (branch.actions && Array.isArray(branch.actions)) {
              branch.actions.forEach(action => {
                if (action.target) codes.add(action.target)
                if (action.value && typeof action.value === 'string') {
                  const vars = action.value.match(/[a-zA-Z_]\w*/g) || []
                  vars.forEach(v => {
                    if (!['true', 'false', 'null', 'undefined', 'NaN', 'Infinity'].includes(v)) {
                      codes.add(v)
                    }
                  })
                }
              })
            }
          })
        }
      })
    },
    addParam() {
      this.params.push({ key: '', label: '', value: '', type: 'STRING', example: '', fromVar: false, options: [] })
    },
    async handleExecute() {
      if (!this.selectedRuleId) return
      const paramMap = {}
      for (const p of this.params) {
        if (!p.key) continue
        let val = p.value
        if (p.type === 'NUMBER' && val !== '' && val !== null) {
          val = Number(val)
        } else if (p.type === 'BOOLEAN') {
          val = val === 'true'
        }
        paramMap[p.key] = val
      }
      this.executing = true
      this.result = null
      try {
        const res = await executeRule({ definitionId: this.selectedRuleId, params: paramMap })
        this.result = res.data
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
    mtl(t) {
      return { TABLE: '决策表', TREE: '决策树', FLOW: '决策流', CROSS: '交叉表', SCORE: '评分卡', CROSS_ADV: '复杂交叉表', SCORE_ADV: '复杂评分卡', SCRIPT: 'QL脚本' }[t] || t
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
