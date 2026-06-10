<template>
  <div class="uiue-list-page">
    <!-- 页面头部 -->
    <div style="margin-bottom:16px;display:flex;align-items:center;justify-content:space-between;">
      <h2 style="margin:0;">{{ rule.ruleName || '规则详情' }}</h2>
      <div>
        <el-button size="small" type="primary" icon="el-icon-video-play" @click="openTestDialog">规则测试</el-button>
        <el-button size="small" icon="el-icon-back" @click="$router.push('/rule')">返回</el-button>
      </div>
    </div>

    <!-- 基本信息 -->
    <el-descriptions :column="2" border size="small" style="margin-bottom:16px;" v-loading="loading">
      <el-descriptions-item label="规则编码">{{ rule.ruleCode }}</el-descriptions-item>
      <el-descriptions-item label="规则名称">{{ rule.ruleName }}</el-descriptions-item>
      <el-descriptions-item label="决策模型">{{ modelTypeLabel(rule.modelType) }}</el-descriptions-item>
      <el-descriptions-item label="作用范围">{{ rule.scope === 'GLOBAL' ? '全局' : '项目级' }}</el-descriptions-item>
      <el-descriptions-item label="所属项目">{{ rule.projectName || '—' }}</el-descriptions-item>
      <el-descriptions-item label="设计版本">v{{ rule.currentVersion }}</el-descriptions-item>
      <el-descriptions-item label="发布版本">{{ rule.publishedVersion ? 'v' + rule.publishedVersion : '-' }}</el-descriptions-item>
      <el-descriptions-item label="状态">
        <el-tag size="mini" :type="statusType(rule.status)">{{ statusLabel(rule.status) }}</el-tag>
      </el-descriptions-item>
    </el-descriptions>

    <!-- 描述 -->
    <el-card v-if="rule.description" shadow="never" style="margin-bottom:16px;">
      <div slot="header" style="font-weight:600;">描述</div>
      <div style="color:#606266;font-size:14px;line-height:1.6;">{{ rule.description }}</div>
    </el-card>

    <!-- 输入输出字段 -->
    <el-tabs type="border-card">
      <!-- 输入字段 tab -->
      <el-tab-pane>
        <span slot="label"><i class="el-icon-arrow-down" /> 输入字段</span>
        <div style="margin-bottom:10px;display:flex;align-items:center;justify-content:space-between;">
          <span style="color:#909399;font-size:12px;">
            共 {{ rule.inputFieldsJson ? rule.inputFieldsJson.length : 0 }} 个字段，请关联引擎变量
          </span>
          <el-button size="mini" icon="el-icon-refresh" @click="load">刷新</el-button>
        </div>

        <el-table :data="rule.inputFieldsJson" border size="small" max-height="500" v-loading="loading" :row-class-name="inputRowClassName">
          <el-table-column label="序号" width="60" align="center">
            <template slot-scope="{ $index }">{{ $index + 1 }}</template>
          </el-table-column>
          <el-table-column label="变量编码" min-width="120">
            <template slot-scope="{row}">
              <span v-if="row.varId && varMap[row.varId]" class="script-name-text">{{ varMap[row.varId].varCode }}</span>
              <span v-else style="color:#c0c4cc;">—</span>
            </template>
          </el-table-column>
          <el-table-column label="变量名称" min-width="130">
            <template slot-scope="{row}">
              <span style="font-weight:500;">{{ row.fieldLabel || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="脚本名称" min-width="130">
            <template slot-scope="{row}">
              <span v-if="row.varId && varMap[row.varId]">{{ varMap[row.varId].varCodeText }}</span>
              <span v-else-if="row.scriptName">{{ row.scriptName }}</span>
              <span v-else style="color:#c0c4cc;">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="fieldType" label="类型" width="90" align="center">
            <template slot-scope="{row}">
              <el-tag size="mini" type="info">{{ typeLabel(row.fieldType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="默认值" min-width="130">
            <template slot-scope="{row}">
              <span v-if="row.defaultValue" style="color:#606266;">{{ row.defaultValue }}</span>
              <span v-else style="color:#c0c4cc;">—</span>
            </template>
          </el-table-column>
          <el-table-column label="取值范围" min-width="130">
            <template slot-scope="{row}">
              <span v-if="row.validValues" style="color:#606266;">{{ row.validValues }}</span>
              <span v-else style="color:#c0c4cc;">—</span>
            </template>
          </el-table-column>
          <el-table-column label="修改时间" width="140" align="center">
            <template slot-scope="{row}">
              <span v-if="row.updateTime">{{ row.updateTime.replace('T',' ') }}</span>
              <span v-else-if="row.createTime">{{ row.createTime.replace('T',' ') }}</span>
              <span v-else style="color:#c0c4cc;">—</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" align="center" fixed="right">
            <template slot-scope="{row, $index}">
              <template v-if="row._editing">
                <el-button type="text" size="mini" style="color:#67c23a;" :loading="row._saving" @click="saveInputField(row, $index)">保存</el-button>
                <el-button type="text" size="mini" style="color:#909399;" @click="cancelEditInput(row)">取消</el-button>
              </template>
              <el-button v-else type="text" size="mini" @click="editInputField(row)">
                <i class="el-icon-edit" /> 编辑
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="!rule.inputFieldsJson || rule.inputFieldsJson.length === 0" style="text-align:center;padding:40px 0;color:#909399;">暂无输入字段</div>
      </el-tab-pane>

      <!-- 输出字段 tab -->
      <el-tab-pane>
        <span slot="label"><i class="el-icon-arrow-up" /> 输出字段</span>
        <div style="margin-bottom:10px;display:flex;align-items:center;justify-content:space-between;">
          <span style="color:#909399;font-size:12px;">
            共 {{ rule.outputFieldsJson ? rule.outputFieldsJson.length : 0 }} 个字段，请关联引擎变量
          </span>
          <el-button size="mini" icon="el-icon-refresh" @click="load">刷新</el-button>
        </div>

        <el-table :data="rule.outputFieldsJson" border size="small" max-height="500" v-loading="loading" :row-class-name="outputRowClassName">
          <el-table-column label="序号" width="60" align="center">
            <template slot-scope="{ $index }">{{ $index + 1 }}</template>
          </el-table-column>
          <el-table-column label="变量编码" min-width="120">
            <template slot-scope="{row}">
              <span v-if="row.varId && varMap[row.varId]" class="script-name-text">{{ varMap[row.varId].varCode }}</span>
              <span v-else style="color:#c0c4cc;">—</span>
            </template>
          </el-table-column>
          <el-table-column label="变量名称" min-width="130">
            <template slot-scope="{row}">
              <span style="font-weight:500;">{{ row.fieldLabel || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="脚本名称" min-width="130">
            <template slot-scope="{row}">
              <span v-if="row.varId && varMap[row.varId]">{{ varMap[row.varId].varCodeText }}</span>
              <span v-else-if="row.scriptName">{{ row.scriptName }}</span>
              <span v-else style="color:#c0c4cc;">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="fieldType" label="类型" width="90" align="center">
            <template slot-scope="{row}">
              <el-tag size="mini" type="info">{{ typeLabel(row.fieldType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="默认值" min-width="120">
            <span style="color:#c0c4cc;">—</span>
          </el-table-column>
          <el-table-column label="取值范围" min-width="130">
            <template slot-scope="{row}">
              <span v-if="row.validValues" style="color:#606266;">{{ row.validValues }}</span>
              <span v-else style="color:#c0c4cc;">—</span>
            </template>
          </el-table-column>
          <el-table-column label="修改时间" width="140" align="center">
            <template slot-scope="{row}">
              <span v-if="row.updateTime">{{ row.updateTime.replace('T',' ') }}</span>
              <span v-else-if="row.createTime">{{ row.createTime.replace('T',' ') }}</span>
              <span v-else style="color:#c0c4cc;">—</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" align="center" fixed="right">
            <template slot-scope="{row, $index}">
              <template v-if="row._editing">
                <el-button type="text" size="mini" style="color:#67c23a;" :loading="row._saving" @click="saveOutputField(row, $index)">保存</el-button>
                <el-button type="text" size="mini" style="color:#909399;" @click="cancelEditOutput(row)">取消</el-button>
              </template>
              <el-button v-else type="text" size="mini" @click="editOutputField(row)">
                <i class="el-icon-edit" /> 编辑
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="!rule.outputFieldsJson || rule.outputFieldsJson.length === 0" style="text-align:center;padding:40px 0;color:#909399;">暂无输出字段</div>
      </el-tab-pane>
    </el-tabs>

    <!-- 规则测试对话框 -->
    <el-dialog title="规则测试" :visible.sync="testVisible" width="900px" :close-on-click-modal="false">
      <div v-if="!testReady" style="padding:40px;text-align:center;color:#909399;">正在加载...</div>
      <template v-else>
        <div style="margin-bottom:12px;display:flex;align-items:center;gap:8px;flex-wrap:wrap;">
          <el-button size="mini" type="primary" icon="el-icon-video-play" :loading="testExecuting" @click="doTest">执行测试</el-button>
          <el-button size="mini" icon="el-icon-delete" @click="handleClearParams">清空参数</el-button>
          <el-tooltip content="从输入字段自动生成表单填写" placement="top">
            <el-button size="mini" :type="testMode === 'manual' ? 'primary' : ''" @click="switchToManualMode">表单填写</el-button>
          </el-tooltip>
          <el-tooltip content="直接编辑 JSON 参数" placement="top">
            <el-button size="mini" :type="testMode === 'json' ? 'primary' : ''" @click="switchToJsonMode">JSON 编辑</el-button>
          </el-tooltip>
        </div>

        <div v-if="testMode === 'manual'" class="test-form-wrapper">
          <div v-if="testFields.length > 0" class="test-form-grid">
            <div v-for="field in testFields" :key="field.fieldName" class="test-field-cell">
              <div class="test-field-label">{{ field.fieldLabel || field.fieldName }}</div>
              <el-input-number
                v-if="field.fieldType === 'NUMBER' || field.fieldType === 'DOUBLE' || field.fieldType === 'INTEGER'"
                v-model="testParams[field.fieldName]"
                placeholder="输入值"
                controls-position="right"
                :precision="field.fieldType === 'INTEGER' ? 0 : undefined"
                :step="field.fieldType === 'INTEGER' ? 1 : 0.01"
                clearable style="width:100%;"
              />
              <el-select
                v-else-if="field.fieldType === 'ENUM' && field.validValues && field.validValues.length"
                v-model="testParams[field.fieldName]"
                style="width:100%;" clearable filterable placeholder="选择值"
              >
                <el-option v-for="v in field.validValues" :key="v" :label="v" :value="v" />
              </el-select>
              <el-select v-else-if="field.fieldType === 'BOOLEAN'" v-model="testParams[field.fieldName]" style="width:100%;">
                <el-option label="true" :value="true" />
                <el-option label="false" :value="false" />
              </el-select>
              <el-date-picker
                v-else-if="field.fieldType === 'DATE'"
                v-model="testParams[field.fieldName]"
                type="date" placeholder="选择日期"
                style="width:100%;" format="yyyy-MM-dd" value-format="yyyy-MM-dd"
              />
              <el-input v-else v-model="testParams[field.fieldName]" placeholder="输入值" />
              <div class="test-field-hint">{{ field.fieldName }}</div>
            </div>
          </div>
          <div v-else style="text-align:center;padding:30px 0;color:#909399;">暂无输入字段，请切换到 JSON 模式手动编辑参数</div>
        </div>

        <div v-else class="test-form-wrapper">
          <monaco-editor v-model="testJsonStr" language="json" height="300px" :key="testDialogKey" @change="onJsonInput" />
          <div v-if="jsonError" style="color:#f56c6c;font-size:12px;margin-top:4px;">{{ jsonError }}</div>
        </div>

        <div v-if="testResult" style="margin-top:16px;">
          <el-divider content-position="left">执行结果</el-divider>
          <el-alert :title="testResult.success ? '执行成功' : '执行失败'" :type="testResult.success ? 'success' : 'error'" :closable="false" show-icon style="margin-bottom:8px;">
            <span v-if="testResult.executeTimeMs">耗时 {{ testResult.executeTimeMs }} ms</span>
          </el-alert>
          <div v-if="testResult.error" style="color:#f56c6c;margin-bottom:8px;">{{ testResult.error }}</div>
          <div v-if="testResult.message" style="color:#e6a23c;margin-bottom:8px;">{{ testResult.message }}</div>
          <pre v-if="testResult.outputs" style="background:#f5f7fa;padding:12px;border-radius:4px;font-size:13px;max-height:200px;overflow:auto;">{{ formatResult(testResult.outputs) }}</pre>
        </div>
      </template>

      <div slot="footer">
        <el-button size="small" @click="testVisible = false">关闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import * as api from '@/api/definition'
import { listVariablesByProject, listVariables } from '@/api/variable'
import { getVariableTree } from '@/api/dataObject'

const MODEL_TYPE_LABELS = {
  TABLE: '决策表',
  TREE: '决策树',
  FLOW: '决策流',
  CROSS: '交叉表',
  SCORE: '评分卡',
  CROSS_ADV: '复杂交叉表',
  SCORE_ADV: '复杂评分卡',
  SCRIPT: 'QL 脚本'
}

export default {
  name: 'RuleDetail',
  components: {},
  data() {
    return {
      loading: false,
      rule: {},
      /** varId -> 变量对象映射 */
      varMap: {},
      /** VarPicker 分层下拉选项（普通变量 / 常量 / 数据对象字段） */
      varPickerGroups: [
        { label: '普通变量', options: [] },
        { label: '常量', options: [] },
        { label: '数据对象字段', options: [] }
      ],
      // 测试相关
      testVisible: false,
      testReady: false,
      testMode: 'manual',
      testFields: [],
      testParams: {},
      testJsonStr: '{}',
      jsonEdited: false,
      jsonError: '',
      testExecuting: false,
      testResult: null,
      testDialogKey: 1
    }
  },
  created() {
    this.load()
  },
  methods: {
    async load() {
      const id = this.$route.params.id
      if (!id) return
      this.loading = true
      try {
        const res = await api.getDefinitionDetail(id)
        this.rule = res.data || {}
        if (this.rule.inputFieldsJson) {
          this.rule.inputFieldsJson.forEach(f => this.$set(f, '_editing', false))
        }
        if (this.rule.outputFieldsJson) {
          this.rule.outputFieldsJson.forEach(f => this.$set(f, '_editing', false))
        }
        await this.loadVars()
      } catch (e) {
        this.$message.error(e.message || '加载规则详情失败')
      } finally {
        this.loading = false
      }
    },
    async loadVars() {
      const projectId = this.rule.projectId
      if (projectId && projectId > 0) {
        await this.loadVarsByProject(projectId)
      } else {
        await this.loadGlobalVars()
      }
    },
    async loadVarsByProject(projectId) {
      try {
        const [varsRes, constRes, treeRes] = await Promise.all([
          listVariablesByProject(projectId),
          listVariables({ projectId, varSource: 'CONSTANT', pageNum: 1, pageSize: 5000 }),
          getVariableTree(projectId)
        ])
        const vars = Array.isArray(varsRes.data) ? varsRes.data : []
        const consts = (constRes.data && Array.isArray(constRes.data.records))
          ? constRes.data.records
          : (Array.isArray(constRes.data) ? constRes.data : [])
        const tree = Array.isArray(treeRes.data) ? treeRes.data : []
        this.buildVarOptions([...vars, ...consts], tree)
      } catch (e) {
        this.varMap = {}; this.varPickerOptions = []
      }
    },
    async loadGlobalVars() {
      try {
        const [varsRes, constRes, treeRes] = await Promise.all([
          listVariables({ scope: 'GLOBAL', pageNum: 1, pageSize: 5000 }),
          listVariables({ scope: 'GLOBAL', varSource: 'CONSTANT', pageNum: 1, pageSize: 5000 }),
          getVariableTree(0)
        ])
        const vars = (varsRes.data && Array.isArray(varsRes.data.records))
          ? varsRes.data.records
          : (Array.isArray(varsRes.data) ? varsRes.data : [])
        const consts = (constRes.data && Array.isArray(constRes.data.records))
          ? constRes.data.records
          : (Array.isArray(constRes.data) ? constRes.data : [])
        const tree = Array.isArray(treeRes.data) ? treeRes.data : []
        this.buildVarOptions([...vars, ...consts], tree)
      } catch (e) {
        this.varMap = {}
        this.varPickerGroups.splice(0, this.varPickerGroups.length, ...[
          { label: '普通变量', options: [] },
          { label: '常量', options: [] },
          { label: '数据对象字段', options: [] }
        ])
      }
    },
    buildVarOptions(vars, doTree) {
      this.varMap = {}
      const seenIds = new Set()
      /** @type {Array} 普通变量选项 */
      const varOptions = []
      /** @type {Array} 常量选项 */
      const constOptions = []
      /** @type {Array} 数据对象字段选项 */
      const objOptions = []
      vars.forEach(v => {
        if (!v.id || seenIds.has(v.id)) return
        seenIds.add(v.id)
        const labelText = v.varLabel || ''
        const codeText = v.scriptName || v.varCode || ''
        const item = {
          id: v.id,
          varCode: v.varCode || '',
          varCodeText: v.scriptName || v.varCode || '',
          scriptName: codeText,
          varLabel: labelText + (codeText ? ' ' + codeText : ''),
          varLabelText: labelText,
          varType: v.varType,
          varSource: v.varSource,
          sourceType: v.varSource === 'CONSTANT' ? 'constant' : 'variable',
          varObj: v
        }
        this.varMap[v.id] = item
        if (v.varSource === 'CONSTANT') {
          constOptions.push(item)
        } else {
          varOptions.push(item)
        }
      })
      doTree.forEach(group => {
        const obj = group.object || {}
        const fields = group.variables || []
        fields.forEach(f => {
          if (!f.id || seenIds.has(f.id)) return
          seenIds.add(f.id)
          const labelText = f.varLabel || ''
          const codeText = f.scriptName || f.varCode || ''
          const item = {
            id: f.id,
            varCode: f.varCode || '',
            varCodeText: f.scriptName || f.varCode || '',
            scriptName: codeText,
            varLabel: labelText + (codeText ? ' ' + codeText : ''),
            varLabelText: labelText,
            varType: f.varType,
            varSource: 'INPUT',
            sourceType: 'dataObject',
            sourceLabel: obj.objectLabel || obj.objectCode || '数据对象',
            varObj: f
          }
          this.varMap[f.id] = item
          objOptions.push(item)
        })
      })
      this.varPickerGroups.splice(0, this.varPickerGroups.length, ...[
        { label: '普通变量', options: varOptions },
        { label: '常量', options: constOptions },
        { label: '数据对象字段', options: objOptions }
      ])
    },
    onVarClear(row) {
      this.$set(row, 'varId', null)
      this.$set(row, '_varId', null)
      this.$set(row, 'fieldLabel', '')
      this.$set(row, 'scriptName', '')
    },
    onVarChange(row, varId) {
      if (!varId) return
      // 从 varPickerGroups 所有选项中查找
      let opt = null
      for (const group of this.varPickerGroups) {
        const found = group.options.find(o => o.id === varId)
        if (found) { opt = found; break }
      }
      if (!opt) return
      this.$set(row, 'varId', opt.id)
      this.$set(row, 'fieldLabel', opt.varLabel)
      this.$set(row, 'scriptName', opt.varCode)
      this.$set(row, 'varSource', opt.sourceType)
    },
    modelTypeLabel(t) {
      return MODEL_TYPE_LABELS[t] || t || '—'
    },
    statusLabel(s) {
      return { 0: '草稿', 1: '已发布', 2: '已下线' }[s] || '—'
    },
    statusType(s) {
      return { 0: 'info', 1: 'success', 2: 'warning' }[s] || 'info'
    },
    typeLabel(t) {
      return { NUMBER: '数字', INTEGER: '整数', DOUBLE: '浮点', STRING: '字符串', BOOLEAN: '布尔', ENUM: '枚举', DATE: '日期', OBJECT: '对象', LIST: '列表' }[t] || t || '—'
    },

    // ========== 输入字段编辑 ==========
    editInputField(row) {
      if (this.rule.inputFieldsJson) {
        this.rule.inputFieldsJson.forEach(f => {
          if (f !== row) this.$set(f, '_editing', false)
        })
      }
      this.$set(row, '_editing', true)
      this.$set(row, '_varId', row.varId)
      this.$set(row, '_origin', { varId: row.varId, _varId: row.varId, missingValue: row.missingValue })
    },
    async saveInputField(row) {
      this.$set(row, '_saving', true)
      try {
        await api.updateInputField(row.id, {
          varId: row.varId,
          scriptName: row.scriptName,
          fieldLabel: row.fieldLabel,
          fieldType: row.fieldType,
          missingValue: row.missingValue,
          defaultValue: row.defaultValue,
          transformType: row.transformType,
          transformParams: row.transformParams,
          validValues: row.validValues
        })
        this.$set(row, '_editing', false)
        this.$set(row, '_saving', false)
        this.$message.success('保存成功')
      } catch (e) {
        this.$set(row, '_saving', false)
        this.$message.error('保存失败: ' + (e.message || e))
      }
    },
    cancelEditInput(row) {
      if (row._origin) {
        this.$set(row, 'varId', row._origin.varId)
        this.$set(row, '_varId', row._origin._varId)
        this.$set(row, 'missingValue', row._origin.missingValue)
      }
      this.$set(row, '_editing', false)
    },
    inputRowClassName({ row }) {
      return row._editing ? 'editing-row' : ''
    },

    // ========== 输出字段编辑 ==========
    editOutputField(row) {
      if (this.rule.outputFieldsJson) {
        this.rule.outputFieldsJson.forEach(f => {
          if (f !== row) this.$set(f, '_editing', false)
        })
      }
      this.$set(row, '_editing', true)
      this.$set(row, '_varId', row.varId)
      this.$set(row, '_origin', { varId: row.varId, _varId: row.varId, transformType: row.transformType })
    },
    async saveOutputField(row) {
      this.$set(row, '_saving', true)
      try {
        await api.updateOutputField(row.id, {
          varId: row.varId,
          scriptName: row.scriptName,
          fieldLabel: row.fieldLabel,
          fieldType: row.fieldType,
          transformType: row.transformType,
          transformParams: row.transformParams
        })
        this.$set(row, '_editing', false)
        this.$set(row, '_saving', false)
        this.$message.success('保存成功')
      } catch (e) {
        this.$set(row, '_saving', false)
        this.$message.error('保存失败: ' + (e.message || e))
      }
    },
    cancelEditOutput(row) {
      if (row._origin) {
        this.$set(row, 'varId', row._origin.varId)
        this.$set(row, '_varId', row._origin._varId)
        this.$set(row, 'transformType', row._origin.transformType)
      }
      this.$set(row, '_editing', false)
    },
    outputRowClassName({ row }) {
      return row._editing ? 'editing-row' : ''
    },

    // ========== 规则测试 ==========
    async openTestDialog() {
      this.testVisible = true
      this.testReady = false
      this.testResult = null
      this.testMode = 'manual'
      this.jsonEdited = false
      this.jsonError = ''
      this.testDialogKey++

      let freshRule = this.rule
      try {
        const res = await api.getDefinitionDetail(this.rule.id)
        if (res.data) freshRule = res.data
      } catch (e) { /* fallback */ }

      const testFields = (freshRule.inputFieldsJson || []).filter(f => f.status !== 0).map(f => {
        if (f.validValues && typeof f.validValues === 'string') {
          try { f.validValues = JSON.parse(f.validValues) } catch { f.validValues = [] }
        }
        if (!f.validValues) f.validValues = []
        return f
      })

      const testParams = {}
      testFields.forEach(f => {
        if (f.defaultValue !== undefined && f.defaultValue !== null && f.defaultValue !== '') {
          testParams[f.fieldName] = f.defaultValue
        } else if (f.fieldType === 'BOOLEAN') {
          testParams[f.fieldName] = false
        } else if (f.fieldType === 'NUMBER' || f.fieldType === 'DOUBLE' || f.fieldType === 'INTEGER') {
          testParams[f.fieldName] = 0
        } else {
          testParams[f.fieldName] = ''
        }
      })

      const jsonObj = {}
      testFields.forEach(f => { jsonObj[f.fieldName] = testParams[f.fieldName] })
      const testJsonStr = JSON.stringify(jsonObj, null, 2)

      this.testFields = testFields
      this.testParams = testParams
      this.testJsonStr = testJsonStr
      this.testReady = true
    },
    switchToJsonMode() {
      if (this.testMode === 'json') return
      this.testMode = 'json'
      this.syncParamsToJson()
    },
    switchToManualMode() {
      if (this.testMode === 'manual') return
      this.testMode = 'manual'
      this.syncJsonToParams()
    },
    syncParamsToJson() {
      const obj = {}
      this.testFields.forEach(f => {
        const val = this.testParams[f.fieldName]
        obj[f.fieldName] = (val !== '' && val !== null) ? val : null
      })
      this.testJsonStr = JSON.stringify(obj, null, 2)
      this.jsonEdited = false
      this.jsonError = ''
    },
    onJsonInput() {
      this.jsonEdited = true
      this.jsonError = ''
      try { JSON.parse(this.testJsonStr) } catch (e) { this.jsonError = 'JSON 格式错误: ' + e.message }
    },
    syncJsonToParams() {
      try {
        const obj = JSON.parse(this.testJsonStr)
        const inputFieldNames = new Set(this.testFields.map(f => f.fieldName))
        Object.keys(obj).forEach(k => {
          if (inputFieldNames.has(k) && this.testParams[k] === undefined) {
            this.testParams[k] = obj[k]
          }
        })
        this.jsonError = ''
      } catch (e) { this.jsonError = 'JSON 格式错误: ' + e.message }
    },
    async doTest() {
      this.testResult = null
      this.testExecuting = true
      let params
      if (this.testMode === 'json') {
        try { params = JSON.parse(this.testJsonStr) } catch (e) {
          this.$message.error('JSON 格式错误: ' + e.message)
          this.testExecuting = false
          return
        }
      } else {
        params = { ...this.testParams }
        Object.keys(params).forEach(k => { if (params[k] === '' || params[k] === null) delete params[k] })
      }
      try {
        const res = await api.executeRule({ id: this.rule.id, params })
        this.testResult = res.data || {}
        if (this.testResult.success) {
          this.testJsonStr = JSON.stringify(params, null, 2)
          this.jsonEdited = true
        }
      } catch (e) {
        this.testResult = { success: false, error: e.message || '测试执行失败' }
      } finally {
        this.testExecuting = false
      }
    },
    handleClearParams() {
      this.testParams = {}
      this.testFields.forEach(f => {
        if (f.fieldType === 'BOOLEAN') this.testParams[f.fieldName] = false
        else if (f.fieldType === 'NUMBER' || f.fieldType === 'DOUBLE' || f.fieldType === 'INTEGER') this.testParams[f.fieldName] = 0
        else this.testParams[f.fieldName] = ''
      })
      const jsonObj = {}
      this.testFields.forEach(f => { jsonObj[f.fieldName] = this.testParams[f.fieldName] })
      this.testJsonStr = JSON.stringify(jsonObj, null, 2)
      this.jsonEdited = false
      this.testResult = null
      this.jsonError = ''
    },
    formatResult(outputs) {
      if (typeof outputs === 'object') return JSON.stringify(outputs, null, 2)
      return outputs
    }
  }
}
</script>

<style scoped>
.script-name-text {
  font-family: 'Courier New', monospace;
  font-size: 13px;
  color: #409eff;
}
.script-unbound {
  color: #c0c4cc;
  font-style: italic;
}
::v-deep .editing-row { background-color: #f0f9eb; }
::v-deep .el-table .editing-row td { background-color: #f0f9eb; }

.test-form-wrapper {
  max-height: 420px;
  overflow-y: auto;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 4px 0;
}
.test-form-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0;
  padding: 8px;
}
.test-field-cell {
  padding: 8px 12px;
  border-radius: 4px;
  transition: background-color 0.15s;
}
.test-field-cell:hover { background-color: #f5f7fa; }
.test-field-label {
  font-size: 13px;
  color: #303133;
  font-weight: 500;
  margin-bottom: 6px;
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.test-field-hint {
  font-size: 11px;
  color: #c0c4cc;
  margin-top: 4px;
  font-family: 'Courier New', monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>