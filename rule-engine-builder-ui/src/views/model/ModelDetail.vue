<template>
  <div class="uiue-list-page">
    <!-- 页面头部 -->
    <div style="margin-bottom:16px;display:flex;align-items:center;justify-content:space-between;">
      <h2 style="margin:0;">{{ model.modelName || '模型详情' }}</h2>
      <div>
        <el-button size="small" type="primary" icon="el-icon-video-play" @click="openTestDialog">模型测试</el-button>
        <el-button size="small" icon="el-icon-back" @click="$router.push('/model')">返回</el-button>
      </div>
    </div>

    <!-- 基本信息 -->
    <el-descriptions :column="2" border size="small" style="margin-bottom:16px;" v-loading="loading">
      <el-descriptions-item label="模型编码">{{ model.modelCode }}</el-descriptions-item>
      <el-descriptions-item label="模型名称">{{ model.modelName }}</el-descriptions-item>
      <el-descriptions-item label="模型大类">{{ modelTypeLabel(model.modelType) }}</el-descriptions-item>
      <el-descriptions-item label="模型格式">{{ model.modelFormat }}</el-descriptions-item>
      <el-descriptions-item label="作用范围">{{ model.scope === 'GLOBAL' ? '全局' : '项目级' }}</el-descriptions-item>
      <el-descriptions-item label="所属项目">{{ model.projectName || '—' }}</el-descriptions-item>
      <el-descriptions-item label="文件名">{{ model.modelFileName }}</el-descriptions-item>
      <el-descriptions-item label="文件大小">{{ formatFileSize(model.modelFileSize) }}</el-descriptions-item>
      <el-descriptions-item label="设计版本">{{ model.currentVersion }}</el-descriptions-item>
      <el-descriptions-item label="发布版本">{{ model.publishedVersion || '-' }}</el-descriptions-item>
    </el-descriptions>

    <!-- 描述 -->
    <el-card v-if="model.description" shadow="never" style="margin-bottom:16px;">
      <div slot="header" style="font-weight:600;">描述</div>
      <div style="color:#606266;font-size:14px;line-height:1.6;">{{ model.description }}</div>
    </el-card>

    <!-- 输入输出字段 -->
    <el-tabs type="border-card">
      <!-- 输入字段 tab -->
      <el-tab-pane>
        <span slot="label"><i class="el-icon-arrow-down" /> 输入字段</span>
        <div style="margin-bottom:10px;display:flex;align-items:center;justify-content:space-between;">
          <span style="color:#909399;font-size:12px;">
            共 {{ model.inputFields ? model.inputFields.length : 0 }} 个字段，请关联引擎变量
          </span>
          <el-button size="mini" icon="el-icon-refresh" @click="load">刷新</el-button>
        </div>

        <el-table :data="model.inputFields" border size="small" max-height="500" v-loading="loading" :row-class-name="inputRowClassName">
          <!-- 序号 -->
          <el-table-column label="序号" width="60" align="center">
            <template slot-scope="{ $index }">{{ $index + 1 }}</template>
          </el-table-column>
          <!-- 字段名称 -->
          <el-table-column prop="fieldName" label="字段名称" min-width="130">
            <template slot-scope="{row}">
              <span style="font-weight:500;">{{ row.fieldName }}</span>
              <!-- <span v-if="row.fieldLabel" style="color:#909399;font-size:11px;margin-left:4px;">{{ row.fieldLabel }}</span> -->
            </template>
          </el-table-column>
          <!-- 对应变量（通过 varId 关联变量管理） -->
          <el-table-column label="对应变量" min-width="240">
            <template slot-scope="{row}">
              <div v-if="row._editing">
                <el-select
                  v-if="varPickerOptions.length"
                  v-model="row.varId"
                  filterable
                  clearable
                  placeholder="模糊搜索或选择变量..."
                  size="mini"
                  style="width:100%;"
                  popper-append-to-body
                  :filter-method="varFilterMethod"
                  @change="val => onVarChange(row, val)"
                  @clear="onVarClear(row)"
                >
                  <el-option-group
                    v-for="group in varSelectGroups"
                    :key="group.label"
                    :label="group.label"
                  >
                    <el-option
                      v-for="v in group.vars"
                      :key="v.id"
                      :value="v.id"
                      :label="v.varLabel + ' (' + v.varCode + ')'"
                    >
                      <span style="font-weight:500;">{{ v.varLabel }}</span>
                      <span style="color:#999;font-size:11px;margin-left:6px;font-family:monospace;">{{ v.varCode }}</span>
                      <el-tag size="mini" :type="v.sourceType === 'dataObject' ? 'warning' : (v.sourceType === 'constant' ? 'success' : 'info')" style="margin-left:6px;float:right;">{{ v.sourceType === 'dataObject' ? 'DO' : (v.sourceType === 'constant' ? 'CONST' : 'VAR') }}</el-tag>
                    </el-option>
                  </el-option-group>
                </el-select>
                <span v-else style="color:#999;font-size:12px;">暂无变量库</span>
              </div>
              <div v-else>
                <span v-if="row.varId && varMap[row.varId]" class="script-name-text">
                  {{ varMap[row.varId].varLabel }} ({{ varMap[row.varId].varCode }})
                </span>
                <span v-else class="script-name-text script-unbound">（未关联）</span>
              </div>
            </template>
          </el-table-column>
          <!-- 字段类型 -->
          <el-table-column prop="fieldType" label="字段类型" width="100" align="center">
            <template slot-scope="{row}">
              <el-tag size="mini" type="info">{{ row.fieldType || '-' }}</el-tag>
            </template>
          </el-table-column>
          <!-- 缺失值（可编辑） -->
          <el-table-column label="缺失值" min-width="130">
            <template slot-scope="{row}">
              <div v-if="row._editing">
                <el-input v-model="row.missingValue" size="mini" placeholder="默认值" />
              </div>
              <span v-else style="color:#909399;font-size:12px;">{{ row.missingValue || '-' }}</span>
            </template>
          </el-table-column>
          <!-- 操作 -->
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
        <div v-if="!model.inputFields || model.inputFields.length === 0" style="text-align:center;padding:40px 0;color:#909399;">暂无输入字段</div>
      </el-tab-pane>

      <!-- 输出字段 tab -->
      <el-tab-pane>
        <span slot="label"><i class="el-icon-arrow-up" /> 输出字段</span>
        <div style="margin-bottom:10px;display:flex;align-items:center;justify-content:space-between;">
          <span style="color:#909399;font-size:12px;">
            共 {{ model.outputFields ? model.outputFields.length : 0 }} 个字段，请关联引擎变量
          </span>
          <el-button size="mini" icon="el-icon-refresh" @click="load">刷新</el-button>
        </div>

        <el-table :data="model.outputFields" border size="small" max-height="500" v-loading="loading" :row-class-name="outputRowClassName">
          <!-- 序号 -->
          <el-table-column label="序号" width="60" align="center">
            <template slot-scope="{ $index }">{{ $index + 1 }}</template>
          </el-table-column>
          <!-- 字段名称 -->
          <el-table-column prop="fieldName" label="字段名称" min-width="130">
            <template slot-scope="{row}">
              <span style="font-weight:500;">{{ row.fieldName }}</span>
              <!-- <span v-if="row.fieldLabel" style="color:#909399;font-size:11px;margin-left:4px;">{{ row.fieldLabel }}</span> -->
            </template>
          </el-table-column>
          <!-- 对应变量（通过 varId 关联变量管理） -->
          <el-table-column label="对应变量" min-width="240">
            <template slot-scope="{row}">
              <div v-if="row._editing">
                <el-select
                  v-if="varPickerOptions.length"
                  v-model="row.varId"
                  filterable
                  clearable
                  placeholder="模糊搜索或选择变量..."
                  size="mini"
                  style="width:100%;"
                  popper-append-to-body
                  :filter-method="varFilterMethod"
                  @change="val => onVarChange(row, val)"
                  @clear="onVarClear(row)"
                >
                  <el-option-group
                    v-for="group in varSelectGroups"
                    :key="group.label"
                    :label="group.label"
                  >
                    <el-option
                      v-for="v in group.vars"
                      :key="v.id"
                      :value="v.id"
                      :label="v.varLabel + ' (' + v.varCode + ')'"
                    >
                      <span style="font-weight:500;">{{ v.varLabel }}</span>
                      <span style="color:#999;font-size:11px;margin-left:6px;font-family:monospace;">{{ v.varCode }}</span>
                      <el-tag size="mini" :type="v.sourceType === 'dataObject' ? 'warning' : (v.sourceType === 'constant' ? 'success' : 'info')" style="margin-left:6px;float:right;">{{ v.sourceType === 'dataObject' ? 'DO' : (v.sourceType === 'constant' ? 'CONST' : 'VAR') }}</el-tag>
                    </el-option>
                  </el-option-group>
                </el-select>
                <span v-else style="color:#999;font-size:12px;">暂无变量库</span>
              </div>
              <div v-else>
                <span v-if="row.varId && varMap[row.varId]" class="script-name-text">
                  {{ varMap[row.varId].varLabel }} ({{ varMap[row.varId].varCode }})
                </span>
                <span v-else class="script-name-text script-unbound">（未关联）</span>
              </div>
            </template>
          </el-table-column>
          <!-- 字段类型 -->
          <el-table-column prop="fieldType" label="字段类型" width="100" align="center">
            <template slot-scope="{row}">
              <el-tag size="mini" type="info">{{ row.fieldType || '-' }}</el-tag>
            </template>
          </el-table-column>
          <!-- 转换方法（可编辑） -->
          <el-table-column label="转换方法" min-width="160">
            <template slot-scope="{row}">
              <div v-if="row._editing">
                <el-select v-model="row.transformType" size="mini" style="width:100%;" popper-append-to-body placeholder="选择">
                  <el-option label="（无）" value="" />
                  <el-option label="NONE - 不转换" value="NONE" />
                  <el-option label="RENAME - 重命名" value="RENAME" />
                  <el-option label="SCALE - 缩放" value="SCALE" />
                  <el-option label="OHE - 独热编码" value="OHE" />
                </el-select>
              </div>
              <span v-else style="color:#606266;font-size:12px;">{{ row.transformType || '-' }}</span>
            </template>
          </el-table-column>
          <!-- 操作 -->
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
        <div v-if="!model.outputFields || model.outputFields.length === 0" style="text-align:center;padding:40px 0;color:#909399;">暂无输出字段</div>
      </el-tab-pane>
    </el-tabs>

    <!-- 模型测试对话框 -->
    <el-dialog title="模型测试" :visible.sync="testVisible" width="900px" :close-on-click-modal="false">
      <!-- 数据未就绪时显示加载中，防止旧数据闪烁 -->
      <div v-if="!testReady" style="padding:40px;text-align:center;color:#909399;">正在加载...</div>
      <template v-else>
        <div style="margin-bottom:12px;display:flex;align-items:center;gap:8px;flex-wrap:wrap;">
          <el-button size="mini" type="primary" icon="el-icon-video-play" :loading="testExecuting" @click="doTest">执行测试</el-button>
          <el-button size="mini" icon="el-icon-document" @click="handleSaveParams">保存测试参数</el-button>
          <el-button size="mini" icon="el-icon-delete" @click="handleClearParams">清空参数</el-button>
          <el-tooltip content="从输入字段自动生成表单填写" placement="top">
            <el-button size="mini" :type="testMode === 'manual' ? 'primary' : ''" @click="switchToManualMode">表单填写</el-button>
          </el-tooltip>
          <el-tooltip content="直接编辑 JSON 参数" placement="top">
            <el-button size="mini" :type="testMode === 'json' ? 'primary' : ''" @click="switchToJsonMode">JSON 编辑</el-button>
          </el-tooltip>
        </div>

        <el-alert v-if="model.modelFormat !== 'PMML'" :title="model.modelFormat + ' 格式暂不支持在线执行，仅 PMML 格式支持'" type="warning" :closable="false" style="margin-bottom:12px;" />

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
                clearable
                style="width:100%;"
              />
              <el-select
                v-else-if="field.fieldType === 'ENUM' && field.validValues && field.validValues.length"
                v-model="testParams[field.fieldName]"
                style="width:100%;"
                clearable filterable
                placeholder="选择值"
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
                type="date"
                placeholder="选择日期"
                style="width:100%;"
                format="yyyy-MM-dd"
                value-format="yyyy-MM-dd"
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
            <span v-if="testResult.modelType">，模型类型：{{ testResult.modelType }}</span>
          </el-alert>
          <div v-if="testResult.error" style="color:#f56c6c;margin-bottom:8px;">{{ testResult.error }}</div>
          <div v-if="testResult.message" style="color:#e6a23c;margin-bottom:8px;">{{ testResult.message }}</div>
          <div v-if="testResult.note" style="color:#909399;font-size:12px;margin-bottom:8px;">{{ testResult.note }}</div>
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
import * as api from '@/api/model'
import { listVariablesByProject, listVariables } from '@/api/variable'
import { getVariableTree } from '@/api/dataObject'

const MODEL_TYPE_LABELS = {
  LR: 'LR（逻辑回归）',
  XGBOOST: 'XGBoost',
  LIGHTGBM: 'LightGBM',
  CATBOOST: 'CatBoost',
  RANDOM_FOREST: 'RandomForest',
  NEURAL_NET: 'NeuralNet（神经网络）',
  SVM: 'SVM',
  CLASSIFICATION: '分类',
  REGRESSION: '回归',
  CLUSTERING: '聚类',
  ML: '机器学习'
}

export default {
  name: 'ModelDetail',
  components: {},
  data() {
    return {
      loading: false,
      model: {},
      /** varId -> 变量对象映射（从变量管理加载） */
      varMap: {},
      /** VarPicker 下拉选项列表（含 _ref / varObj，供级联选择器使用） */
      varPickerOptions: [],
      // 测试相关
      testVisible: false,
      testReady: false,
      testMode: 'manual',
      testFields: [],
      testParams: {},
      testJsonStr: '{}',
      testJsonSkeleton: '{}',
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
        const res = await api.getModel(id)
        this.model = res.data || {}
        // 初始化 _editing 标志
        if (this.model.inputFields) {
          this.model.inputFields.forEach(f => this.$set(f, '_editing', false))
        }
        if (this.model.outputFields) {
          this.model.outputFields.forEach(f => this.$set(f, '_editing', false))
        }
        // 模型加载后加载变量库
        await this.loadVars()
      } catch (e) {
        this.$message.error(e.message || '加载模型详情失败')
      } finally {
        this.loading = false
      }
    },
    /** 加载当前项目下的所有变量，建立 id->变量 映射供关联使用 */
    async loadVars() {
      const projectId = this.model.projectId
      if (this.model.scope === 'GLOBAL') {
        // 全局模型：加载全局变量
        await this.loadGlobalVars()
      } else if (projectId && projectId > 0) {
        // 项目级模型：加载项目变量 + 全局变量 + 常量
        await this.loadVarsByProject(projectId)
      }
    },
    async loadVarsByProject(projectId) {
      try {
        const [varsRes, constRes, treeRes] = await Promise.all([
          listVariablesByProject(projectId),
          listVariables({ projectId, varSource: 'CONSTANT', pageNum: 1, pageSize: 5000 }),
          getVariableTree(projectId)
        ])
        // listByProject 返回数组，listVariables 返回分页
        const vars = varsRes.data || []
        const consts = (constRes.data && constRes.data.records) ? constRes.data.records : (constRes.data || [])
        const tree = treeRes.data || []
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
        const vars = (varsRes.data && varsRes.data.records) ? varsRes.data.records : (varsRes.data || [])
        const consts = (constRes.data && constRes.data.records) ? constRes.data.records : (constRes.data || [])
        const tree = treeRes.data || []
        this.buildVarOptions([...vars, ...consts], tree)
      } catch (e) {
        this.varMap = {}; this.varPickerOptions = []
      }
    },
    buildVarOptions(vars, doTree) {
      this.varMap = {}
      vars.forEach(v => { if (v.id) this.varMap[v.id] = v })
      const options = []
      vars.forEach(v => {
        options.push({
          id: v.id,
          varCode: v.scriptName || v.varCode,
          varLabel: v.varLabel || v.varCode,
          varType: v.varType,
          varSource: v.varSource,
          sourceType: v.varSource === 'CONSTANT' ? 'constant' : 'variable',
          sourceLabel: v.varSource === 'CONSTANT' ? '常量' : '变量',
          varObj: v
        })
      })
      doTree.forEach(group => {
        const obj = group.object || {}
        const fields = group.variables || []
        fields.forEach(f => {
          options.push({
            id: f.id,
            varCode: f.scriptName || f.varCode,
            varLabel: f.varLabel || f.varCode,
            varType: f.varType,
            varSource: 'INPUT',
            sourceType: 'dataObject',
            sourceLabel: obj.objectName || obj.objectCode || '数据对象',
            varObj: f
          })
        })
      })
      this.varPickerOptions = options
    },
    /** 按变量来源分组的下拉选项 */
    varSelectGroups() {
      if (!this.varPickerOptions.length) return []
      const groups = []
      const bySource = {}
      this.varPickerOptions.forEach(v => {
        const key = v.sourceType || 'other'
        if (!bySource[key]) bySource[key] = []
        bySource[key].push(v)
      })
      if (bySource.variable) groups.push({ label: '引擎变量', sourceType: 'variable', vars: bySource.variable })
      if (bySource.constant) groups.push({ label: '常量', sourceType: 'constant', vars: bySource.constant })
      if (bySource.dataObject) groups.push({ label: '数据对象字段', sourceType: 'dataObject', vars: bySource.dataObject })
      if (bySource.other) groups.push({ label: '其他', sourceType: 'other', vars: bySource.other })
      return groups
    },
    /** 模糊搜索过滤：el-select 的 filterable 模式默认搜索 option label（即 varLabel + ' (' + varCode + ')'），同时匹配中文名称和英文字母编码 */
    varFilterMethod() {
      // el-select 内置搜索，无需额外处理；保留此方法防止控制台未定义警告
    },
    /** 清除变量关联时，同时清除自动填充的字段信息 */
    onVarClear(row) {
      this.$set(row, 'varId', null)
      this.$set(row, 'fieldLabel', '')
      this.$set(row, 'scriptName', '')
    },
    /** 选择变量后，自动带出变量编码和名称填充到字段信息 */
    onVarChange(row, varId) {
      if (!varId) return
      const opt = this.varPickerOptions.find(o => o.id === varId)
      if (!opt) return
      this.$set(row, 'fieldLabel', opt.varLabel)
      this.$set(row, 'scriptName', opt.varCode)
    },
    modelTypeLabel(t) {
      return MODEL_TYPE_LABELS[t] || t || '—'
    },
    formatFileSize(size) {
      if (!size) return '-'
      if (size < 1024) return size + ' B'
      if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
      return (size / 1024 / 1024).toFixed(2) + ' MB'
    },

    // ========== 输入字段编辑 ==========
    editInputField(row) {
      if (this.model.inputFields) {
        this.model.inputFields.forEach(f => {
          if (f !== row) this.$set(f, '_editing', false)
        })
      }
      this.$set(row, '_editing', true)
      this.$set(row, '_origin', { varId: row.varId, missingValue: row.missingValue })
    },
    async saveInputField(row) {
      this.$set(row, '_saving', true)
      try {
        await api.updateModelInputField(row.id, {
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
        this.$set(row, 'missingValue', row._origin.missingValue)
      }
      this.$set(row, '_editing', false)
    },
    inputRowClassName({ row }) {
      return row._editing ? 'editing-row' : ''
    },

    // ========== 输出字段编辑 ==========
    editOutputField(row) {
      if (this.model.outputFields) {
        this.model.outputFields.forEach(f => {
          if (f !== row) this.$set(f, '_editing', false)
        })
      }
      this.$set(row, '_editing', true)
      this.$set(row, '_origin', { varId: row.varId, transformType: row.transformType })
    },
    async saveOutputField(row) {
      this.$set(row, '_saving', true)
      try {
        await api.updateModelOutputField(row.id, {
          varId: row.varId,
          scriptName: row.scriptName,
          fieldLabel: row.fieldLabel,
          fieldType: row.fieldType,
          transformType: row.transformType,
          targetField: row.targetField
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
        this.$set(row, 'transformType', row._origin.transformType)
      }
      this.$set(row, '_editing', false)
    },
    outputRowClassName({ row }) {
      return row._editing ? 'editing-row' : ''
    },

    // ========== 模型测试 ==========
    async openTestDialog() {
      // 1. 先打开弹窗，此时 testReady=false，内容显示"正在加载..."，旧数据被隐藏
      this.testVisible = true
      this.testReady = false
      this.testResult = null
      this.testMode = 'manual'
      this.jsonEdited = false
      this.jsonError = ''
      this.testDialogKey++ // 递增 key 强制重新挂载 MonacoEditor

      // 2. 异步获取最新模型数据（不阻塞弹窗打开）
      let freshModel = this.model
      try {
        const res = await api.getModel(this.model.id)
        if (res.data) freshModel = res.data
      } catch (e) { /* fallback to cached */ }

      // 3. 初始化字段列表（解析 validValues）
      const testFields = (freshModel.inputFields || []).filter(f => f.status !== 0).map(f => {
        if (f.validValues && typeof f.validValues === 'string') {
          try { f.validValues = JSON.parse(f.validValues) } catch { f.validValues = [] }
        }
        if (!f.validValues) f.validValues = []
        return f
      })

      // 4. 从服务端获取已保存的测试参数（最高优先级）
      let savedParams = null
      try {
        const res = await api.getTestParams(this.model.id)
        if (res.data) {
          savedParams = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
        }
      } catch (e) { /* ignore */ }

      // 5. 从上传时设置的样例初始化（modelConfig.testParams，次优先级）
      let configParams = null
      if (!savedParams) {
        try {
          const rawConfig = freshModel.modelConfig
          const config = typeof rawConfig === 'string' ? JSON.parse(rawConfig) : (rawConfig || {})
          if (config && config.testParams) {
            configParams = typeof config.testParams === 'string'
              ? JSON.parse(config.testParams)
              : config.testParams
          }
        } catch (e) { /* ignore */ }
      }

      // 6. 优先级：已保存参数 > 上传样例 > 空对象
      const initObj = savedParams || configParams || {}

      // 7. 构建 testParams 和 testJsonStr
      //    数字字段默认 0（而非 null），避免 el-input-number 显示 0.000000
      const testParams = {}
      testFields.forEach(f => {
        if (initObj[f.fieldName] !== undefined) {
          testParams[f.fieldName] = initObj[f.fieldName]
        } else if (f.defaultValue !== undefined && f.defaultValue !== null && f.defaultValue !== '') {
          testParams[f.fieldName] = f.defaultValue
        } else if (f.fieldType === 'BOOLEAN') {
          testParams[f.fieldName] = false
        } else if (f.fieldType === 'NUMBER' || f.fieldType === 'DOUBLE' || f.fieldType === 'INTEGER') {
          testParams[f.fieldName] = 0
        } else {
          testParams[f.fieldName] = ''
        }
      })

      // 8. 构建初始 JSON（包含所有字段的当前值）
      const jsonObj = {}
      testFields.forEach(f => {
        jsonObj[f.fieldName] = testParams[f.fieldName]
      })
      const testJsonStr = JSON.stringify(jsonObj, null, 2)
      const testJsonSkeleton = JSON.stringify({}, null, 2)

      // 9. 一次性设置所有数据，然后标记为就绪，触发重新渲染
      this.testFields = testFields
      this.testParams = testParams
      this.testJsonStr = testJsonStr
      this.testJsonSkeleton = testJsonSkeleton
      this.testReady = true // ✅ 内容切换：隐藏加载中，显示实际表单/JSON编辑器
    },
    /**
     * 切换到 JSON 编辑模式：同步 testParams → testJsonStr
     */
    switchToJsonMode() {
      if (this.testMode === 'json') return
      this.testMode = 'json'
      this.syncParamsToJson()
    },
    /**
     * 切换到表单填写模式：同步 testJsonStr → testParams
     * 仅更新 testParams 中原本为 undefined 或已为默认值（非用户填写）的字段，
     * 保留用户已手动填写的值不被覆盖。
     */
    switchToManualMode() {
      if (this.testMode === 'manual') return
      this.testMode = 'manual'
      this.syncJsonToParams()
    },
    syncParamsToJson() {
      const obj = {}
      this.testFields.forEach(f => {
        const val = this.testParams[f.fieldName]
        if (val !== '' && val !== null) {
          obj[f.fieldName] = val
        } else {
          obj[f.fieldName] = null
        }
      })
      this.testJsonStr = JSON.stringify(obj, null, 2)
      this.jsonEdited = false
      this.jsonError = ''
    },
    buildJsonStr() {
      const obj = {}
      Object.keys(this.testParams).forEach(k => {
        const val = this.testParams[k]
        if (val !== '' && val !== null) obj[k] = val
        else obj[k] = null
      })
      return JSON.stringify(obj, null, 2)
    },
    onJsonInput() {
      this.jsonEdited = true
      this.jsonError = ''
      try {
        JSON.parse(this.testJsonStr)
      } catch (e) {
        this.jsonError = 'JSON 格式错误: ' + e.message
      }
    },
    /**
     * 从 JSON 同步到表单：只更新 testParams 中原本为 undefined 的字段，
     * 保留用户已在表单中填写的值不被覆盖。
     */
    syncJsonToParams() {
      try {
        const obj = JSON.parse(this.testJsonStr)
        const inputFieldNames = new Set(this.testFields.map(f => f.fieldName))
        Object.keys(obj).forEach(k => {
          if (inputFieldNames.has(k)) {
            // 仅当当前 testParams 中该字段为 undefined 时才更新
            if (this.testParams[k] === undefined) {
              this.testParams[k] = obj[k]
            }
          }
        })
        this.jsonError = ''
      } catch (e) {
        this.jsonError = 'JSON 格式错误: ' + e.message
      }
    },
    async doTest() {
      this.testResult = null
      this.testExecuting = true
      let params
      if (this.testMode === 'json') {
        try {
          params = JSON.parse(this.testJsonStr)
        } catch (e) {
          this.$message.error('JSON 格式错误: ' + e.message)
          this.testExecuting = false
          return
        }
      } else {
        params = { ...this.testParams }
        Object.keys(params).forEach(k => {
          if (params[k] === '' || params[k] === null) delete params[k]
        })
      }
      try {
        const res = await api.executeModel(this.model.id, params)
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
    async handleSaveParams() {
      let params
      if (this.testMode === 'json') {
        try {
          params = JSON.parse(this.testJsonStr)
        } catch (e) {
          this.$message.error('JSON 格式错误: ' + e.message)
          return
        }
      } else {
        params = { ...this.testParams }
        Object.keys(params).forEach(k => {
          if (params[k] === '' || params[k] === null) delete params[k]
        })
      }
      try {
        await api.saveTestParams(this.model.id, JSON.stringify(params))
        this.$message.success('测试参数已保存')
      } catch (e) {
        this.$message.error('保存失败: ' + (e.message || e))
      }
    },
    /**
     * 清空参数：数字字段重置为 0，布尔重置为 false，字符串重置为空
     */
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
/* 编辑行高亮 */
::v-deep .editing-row {
  background-color: #f0f9eb;
}
::v-deep .el-table .editing-row td {
  background-color: #f0f9eb;
}

/* 模型测试表单 - 多列网格布局 */
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
.test-field-cell:hover {
  background-color: #f5f7fa;
}
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