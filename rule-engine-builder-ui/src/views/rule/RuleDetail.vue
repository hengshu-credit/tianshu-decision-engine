<template>
  <div class="uiue-list-page">
    <!-- 页面头部 -->
    <div style="margin-bottom:16px;display:flex;align-items:center;justify-content:space-between;">
      <h2 style="margin:0;">{{ rule.ruleName || '规则详情' }}</h2>
      <div>
        <el-button size="small" icon="el-icon-edit" @click="openBaseEditDialog">编辑基本信息</el-button>
        <el-button size="small" type="primary" icon="el-icon-video-play" @click="openTestDialog">规则测试</el-button>
        <el-button size="small" icon="el-icon-time" @click="openVersionDialog">版本历史</el-button>
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

    <el-dialog title="编辑规则基本信息" :visible.sync="baseEditVisible" width="560px" :close-on-click-modal="false">
      <el-form :model="baseForm" label-width="90px" size="small">
        <el-form-item label="规则编码">
          <el-input :value="rule.ruleCode" disabled />
        </el-form-item>
        <el-form-item label="规则名称" required>
          <el-input v-model="baseForm.ruleName" placeholder="请输入规则名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="baseForm.description" type="textarea" :rows="4" placeholder="请输入规则功能描述" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="baseEditVisible = false">取消</el-button>
        <el-button size="small" type="primary" :loading="baseSaving" @click="saveBaseInfo">保存</el-button>
      </div>
    </el-dialog>

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

        <el-table :data="pagedRuleInputFields" border size="small" max-height="500" v-loading="loading" :row-class-name="inputRowClassName">
          <el-table-column label="序号" width="60" align="center">
            <template slot-scope="{ $index }">{{ inputFieldOffset + $index + 1 }}</template>
          </el-table-column>
          <el-table-column label="变量编码" min-width="120">
            <template slot-scope="{row}">
              <span v-if="row.varId && getFieldVarMap(row)" class="script-name-text">{{ getFieldVarMap(row).varCode }}</span>
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
              <span v-if="row.varId && getFieldVarMap(row)">{{ getFieldVarMap(row).varCodeText }}</span>
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
        <el-pagination
          v-if="inputFieldNeedsPaging"
          style="margin-top:12px;text-align:right;"
          :current-page="inputFieldPage"
          :page-size="fieldPageSize"
          :total="inputFieldsTotal"
          layout="total,prev,pager,next"
          @current-change="inputFieldPage = $event"
        />
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

        <el-table :data="pagedRuleOutputFields" border size="small" max-height="500" v-loading="loading" :row-class-name="outputRowClassName">
          <el-table-column label="序号" width="60" align="center">
            <template slot-scope="{ $index }">{{ outputFieldOffset + $index + 1 }}</template>
          </el-table-column>
          <el-table-column label="变量编码" min-width="120">
            <template slot-scope="{row}">
              <span v-if="row.varId && getFieldVarMap(row)" class="script-name-text">{{ getFieldVarMap(row).varCode }}</span>
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
              <span v-if="row.varId && getFieldVarMap(row)">{{ getFieldVarMap(row).varCodeText }}</span>
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
        <el-pagination
          v-if="outputFieldNeedsPaging"
          style="margin-top:12px;text-align:right;"
          :current-page="outputFieldPage"
          :page-size="fieldPageSize"
          :total="outputFieldsTotal"
          layout="total,prev,pager,next"
          @current-change="outputFieldPage = $event"
        />
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
            <div v-for="field in testFields" :key="fieldParamKey(field)" class="test-field-cell">
              <div class="test-field-label">{{ field.fieldLabel || field.fieldName }}</div>
              <el-input-number
                v-if="field.fieldType === 'NUMBER' || field.fieldType === 'DOUBLE' || field.fieldType === 'INTEGER'"
                v-model="testParams[fieldParamKey(field)]"
                placeholder="输入值"
                controls-position="right"
                :precision="field.fieldType === 'INTEGER' ? 0 : undefined"
                :step="field.fieldType === 'INTEGER' ? 1 : 0.01"
                clearable style="width:100%;"
              />
              <el-select
                v-else-if="field.fieldType === 'ENUM' && field.validValues && field.validValues.length"
                v-model="testParams[fieldParamKey(field)]"
                style="width:100%;" clearable filterable placeholder="选择值"
              >
                <el-option v-for="v in field.validValues" :key="v" :label="v" :value="v" />
              </el-select>
              <el-select v-else-if="field.fieldType === 'BOOLEAN'" v-model="testParams[fieldParamKey(field)]" style="width:100%;">
                <el-option label="true" :value="true" />
                <el-option label="false" :value="false" />
              </el-select>
              <el-date-picker
                v-else-if="field.fieldType === 'DATE'"
                v-model="testParams[fieldParamKey(field)]"
                type="date" placeholder="选择日期"
                style="width:100%;" format="yyyy-MM-dd" value-format="yyyy-MM-dd"
              />
              <el-input v-else v-model="testParams[fieldParamKey(field)]" placeholder="输入值" />
              <div class="test-field-hint">{{ fieldParamKey(field) }}</div>
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

    <el-dialog title="版本历史" :visible.sync="versionVisible" width="960px" :close-on-click-modal="false">
      <el-table :data="versions" border size="small" v-loading="versionLoading" max-height="360">
        <el-table-column prop="version" label="版本" width="80" align="center">
          <template slot-scope="{ row }">v{{ row.version }}</template>
        </el-table-column>
        <el-table-column prop="changeLog" label="变更说明" min-width="180">
          <template slot-scope="{ row }">{{ row.changeLog || '-' }}</template>
        </el-table-column>
        <el-table-column prop="publishBy" label="发布人" width="120">
          <template slot-scope="{ row }">{{ row.publishBy || '-' }}</template>
        </el-table-column>
        <el-table-column prop="publishTime" label="发布时间" width="170">
          <template slot-scope="{ row }">{{ formatVersionTime(row.publishTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center">
          <template slot-scope="{ row, $index }">
            <el-button type="text" size="mini" :disabled="$index === versions.length - 1" @click="compareVersion(row, versions[$index + 1])">对比上一版</el-button>
            <el-button type="text" size="mini" @click="rollbackVersion(row)">回滚</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="versionCompare" style="margin-top:12px;">
        <el-alert
          :title="'v' + versionCompare.left.version + ' 与 v' + versionCompare.right.version + ' 对比'"
          :type="versionCompare.modelJsonChanged || versionCompare.compiledScriptChanged ? 'warning' : 'success'"
          :closable="false"
          show-icon
        />
        <div class="version-diff-panel">
          <div class="version-diff-head">
            <span>业务配置差异</span>
            <span class="version-diff-count">{{ versionDiffRows.length }} 项变化</span>
          </div>
          <el-table :data="versionDiffRows" border size="mini" max-height="360" empty-text="两个版本的业务配置一致">
            <el-table-column prop="path" label="配置项" min-width="220" show-overflow-tooltip />
            <el-table-column label="左侧版本" min-width="240" show-overflow-tooltip>
              <template slot-scope="{ row }"><span :class="diffValueClass(row, 'left')">{{ row.leftText }}</span></template>
            </el-table-column>
            <el-table-column label="右侧版本" min-width="240" show-overflow-tooltip>
              <template slot-scope="{ row }"><span :class="diffValueClass(row, 'right')">{{ row.rightText }}</span></template>
            </el-table-column>
            <el-table-column label="变化" width="90" align="center">
              <template slot-scope="{ row }"><el-tag size="mini" :type="diffTagType(row.type)">{{ diffTypeLabel(row.type) }}</el-tag></template>
            </el-table-column>
          </el-table>
        </div>
        <el-collapse class="version-tech-collapse">
          <el-collapse-item title="技术内容（原始 JSON / 编译脚本）" name="raw">
            <div class="version-compare-grid">
              <div>
                <div class="version-compare-title">左侧模型 JSON</div>
                <pre>{{ formatVersionJson(versionCompare.left.modelJson) }}</pre>
              </div>
              <div>
                <div class="version-compare-title">右侧模型 JSON</div>
                <pre>{{ formatVersionJson(versionCompare.right.modelJson) }}</pre>
              </div>
              <div>
                <div class="version-compare-title">左侧编译脚本</div>
                <pre>{{ versionCompare.left.compiledScript || '' }}</pre>
              </div>
              <div>
                <div class="version-compare-title">右侧编译脚本</div>
                <pre>{{ versionCompare.right.compiledScript || '' }}</pre>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>
      <div slot="footer">
        <el-button size="small" @click="versionVisible = false">关闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import * as api from '@/api/definition'
import { listVariablesByProject, listVariables } from '@/api/variable'
import { getVariableTree } from '@/api/dataObject'
import { listAllModelsByProject } from '@/api/model'

const MODEL_TYPE_LABELS = {
  TABLE: '决策表',
  TREE: '决策树',
  FLOW: '决策流',
  RULE_SET: '规则集',
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
        { label: '数据对象字段', options: [] },
        { label: '模型', options: [] }
      ],
      baseEditVisible: false,
      baseSaving: false,
      baseForm: {
        ruleName: '',
        description: ''
      },
      fieldPageSize: 100,
      inputFieldPage: 1,
      outputFieldPage: 1,
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
      testDialogKey: 1,
      versionVisible: false,
      versionLoading: false,
      versions: [],
      versionCompare: null
    }
  },
  computed: {
    inputFieldsTotal() {
      return this.rule && this.rule.inputFieldsJson ? this.rule.inputFieldsJson.length : 0
    },
    outputFieldsTotal() {
      return this.rule && this.rule.outputFieldsJson ? this.rule.outputFieldsJson.length : 0
    },
    inputFieldNeedsPaging() {
      return this.inputFieldsTotal > this.fieldPageSize
    },
    outputFieldNeedsPaging() {
      return this.outputFieldsTotal > this.fieldPageSize
    },
    inputFieldOffset() {
      return this.inputFieldNeedsPaging ? (this.inputFieldPage - 1) * this.fieldPageSize : 0
    },
    outputFieldOffset() {
      return this.outputFieldNeedsPaging ? (this.outputFieldPage - 1) * this.fieldPageSize : 0
    },
    pagedRuleInputFields() {
      const fields = (this.rule && this.rule.inputFieldsJson) || []
      if (!this.inputFieldNeedsPaging) return fields
      return fields.slice(this.inputFieldOffset, this.inputFieldOffset + this.fieldPageSize)
    },
    pagedRuleOutputFields() {
      const fields = (this.rule && this.rule.outputFieldsJson) || []
      if (!this.outputFieldNeedsPaging) return fields
      return fields.slice(this.outputFieldOffset, this.outputFieldOffset + this.fieldPageSize)
    },
    versionDiffRows() {
      if (!this.versionCompare || !this.versionCompare.left || !this.versionCompare.right) return []
      const leftMap = this.flattenVersionObject(this.parseVersionObject(this.versionCompare.left.modelJson))
      const rightMap = this.flattenVersionObject(this.parseVersionObject(this.versionCompare.right.modelJson))
      const keys = Array.from(new Set(Object.keys(leftMap).concat(Object.keys(rightMap)))).sort()
      return keys.filter(key => !this.sameVersionValue(leftMap[key], rightMap[key])).map(key => {
        const hasLeft = Object.prototype.hasOwnProperty.call(leftMap, key)
        const hasRight = Object.prototype.hasOwnProperty.call(rightMap, key)
        return {
          path: this.businessPathLabel(key),
          leftText: hasLeft ? this.displayVersionValue(leftMap[key]) : '未配置',
          rightText: hasRight ? this.displayVersionValue(rightMap[key]) : '未配置',
          type: !hasLeft ? 'rightOnly' : (!hasRight ? 'leftOnly' : 'changed')
        }
      })
    }
  },
  created() {
    this.load()
  },
  watch: {
    '$route.params.id'(id, oldId) {
      if (id && id !== oldId) {
        this.inputFieldPage = 1
        this.outputFieldPage = 1
        this.load()
      }
    }
  },
  methods: {
    async load() {
      const id = this.$route.params.id
      if (!id) return
      this.loading = true
      try {
        // 先调用 refreshFields 从 modelJson 重新解析输入/输出字段（持久化到数据库）
        // 不传 modelJson，由后端从数据库读取当前规则内容进行解析
        await api.refreshFields(id)
        // 再加载最新详情（含刷新后的字段列表）
        // 注意：request 拦截器已展开 R.data，生产环境 res 是对象本身；测试环境 mock 返回 {data: {...}} 需要 .data
        const res = await api.getDefinitionDetail(id)
        this.rule = (res.data !== undefined ? res.data : res) || {}
        if (this.rule.inputFieldsJson) {
          this.rule.inputFieldsJson.forEach(f => this.$set(f, '_editing', false))
        }
        if (this.rule.outputFieldsJson) {
          this.rule.outputFieldsJson.forEach(f => this.$set(f, '_editing', false))
        }
        this.normalizeFieldPages()
        await this.loadVars()
      } catch (e) {
        this.$message.error(e.message || '加载规则详情失败')
      } finally {
        this.loading = false
      }
    },
    openBaseEditDialog() {
      this.baseForm = {
        ruleName: this.rule.ruleName || '',
        description: this.rule.description || ''
      }
      this.baseEditVisible = true
    },
    async saveBaseInfo() {
      const ruleName = (this.baseForm.ruleName || '').trim()
      if (!ruleName) {
        this.$message.warning('规则名称不能为空')
        return
      }
      this.baseSaving = true
      try {
        await api.updateDefinition({
          id: this.rule.id,
          projectId: this.rule.projectId,
          ruleName,
          description: this.baseForm.description || '',
          status: this.rule.status
        })
        this.$set(this.rule, 'ruleName', ruleName)
        this.$set(this.rule, 'description', this.baseForm.description || '')
        this.baseEditVisible = false
        this.$message.success('规则基本信息已更新')
      } catch (e) {
        this.$message.error(e.message || '保存规则基本信息失败')
      } finally {
        this.baseSaving = false
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
        const [varsRes, constRes, treeRes, modelRes] = await Promise.all([
          listVariablesByProject(projectId),
          listVariables({ projectId, varSource: 'CONSTANT', pageNum: 1, pageSize: 5000 }),
          getVariableTree(projectId),
          listAllModelsByProject(projectId)
        ])
        const vars = Array.isArray(varsRes.data) ? varsRes.data : []
        const consts = (constRes.data && Array.isArray(constRes.data.records))
          ? constRes.data.records
          : (Array.isArray(constRes.data) ? constRes.data : [])
        const tree = this.normalizeVariableTree(treeRes.data)
        const models = this.normalizeListResponse(modelRes)
        this.buildVarOptions([...vars, ...consts], tree, models)
      } catch (e) {
        this.varMap = {}
        this.varPickerGroups.splice(0, this.varPickerGroups.length, ...[
          { label: '普通变量', options: [] },
          { label: '常量', options: [] },
          { label: '数据对象字段', options: [] },
          { label: '模型', options: [] }
        ])
      }
    },
    async loadGlobalVars() {
      try {
        const [varsRes, constRes, treeRes, modelRes] = await Promise.all([
          listVariables({ scope: 'GLOBAL', pageNum: 1, pageSize: 5000 }),
          listVariables({ scope: 'GLOBAL', varSource: 'CONSTANT', pageNum: 1, pageSize: 5000 }),
          getVariableTree(0),
          listAllModelsByProject(0)
        ])
        const vars = (varsRes.data && Array.isArray(varsRes.data.records))
          ? varsRes.data.records
          : (Array.isArray(varsRes.data) ? varsRes.data : [])
        const consts = (constRes.data && Array.isArray(constRes.data.records))
          ? constRes.data.records
          : (Array.isArray(constRes.data) ? constRes.data : [])
        const tree = this.normalizeVariableTree(treeRes.data)
        const models = this.normalizeListResponse(modelRes)
        this.buildVarOptions([...vars, ...consts], tree, models)
      } catch (e) {
        this.varMap = {}
        this.varPickerGroups.splice(0, this.varPickerGroups.length, ...[
          { label: '普通变量', options: [] },
          { label: '常量', options: [] },
          { label: '数据对象字段', options: [] },
          { label: '模型', options: [] }
        ])
      }
    },
    normalizeVariableTree(data) {
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
      const visit = rows => {
        const list = rows || []
        list.forEach(row => {
          result.push(row)
          if (row.children && row.children.length) visit(row.children)
        })
      }
      visit(vars)
      return result
    },
    stripObjectPrefix(text, objectCode) {
      if (!text || !objectCode) return text || ''
      const prefix = objectCode + '.'
      return text.indexOf(prefix) === 0 ? text.substring(prefix.length) : text
    },
    refKey(id, refType) {
      if (!id) return ''
      return (refType || 'VARIABLE') + ':' + id
    },
    putVarMap(item) {
      this.$set(this.varMap, this.refKey(item.id, item.refType), item)
      if (!this.varMap[String(item.id)]) this.$set(this.varMap, String(item.id), item)
    },
    getFieldVarMap(row) {
      if (!row || !row.varId) return null
      return this.varMap[this.refKey(row.varId, row.refType)] || this.varMap[String(row.varId)] || null
    },
    buildVarOptions(vars, doTree, models = []) {
      this.varMap = {}
      const seenIds = new Set()
      /** @type {Array} 普通变量选项 */
      const varOptions = []
      /** @type {Array} 常量选项 */
      const constOptions = []
      /** @type {Array} 数据对象字段选项 */
      const objOptions = []
      const modelOptions = []
      vars.forEach(v => {
        const refType = v.varSource === 'CONSTANT' ? 'CONSTANT' : 'VARIABLE'
        const seenKey = this.refKey(v.id, refType)
        if (!v.id || seenIds.has(seenKey)) return
        seenIds.add(seenKey)
        const labelText = v.varLabel || ''
        const codeText = v.scriptName || v.varCode || ''
        const item = {
          id: v.id,
          refType,
          varCode: v.varCode || '',
          varCodeText: v.scriptName || v.varCode || '',
          scriptName: codeText,
          varLabel: labelText + (codeText ? ' ' + codeText : ''),
          varLabelText: labelText,
          varType: v.varType,
          varSource: v.varSource,
          sourceType: v.varSource === 'CONSTANT' ? 'constant' : 'variable',
          varObj: { ...v, refType }
        }
        this.putVarMap(item)
        if (v.varSource === 'CONSTANT') {
          constOptions.push(item)
        } else {
          varOptions.push(item)
        }
      })
      doTree.forEach(group => {
        const obj = group.object || {}
        const fields = group.flatVariables || this.flattenObjectVariables(group.variables)
        fields.forEach(f => {
          const refType = 'DATA_OBJECT'
          const seenKey = this.refKey(f.id, refType)
          if (!f.id || seenIds.has(seenKey)) return
          seenIds.add(seenKey)
          const objCode = obj.scriptName || obj.objectCode || ''
          const labelText = this.stripObjectPrefix(f.varLabel || '', objCode)
          const codeText = f.scriptName || f.varCode || ''
          const objLabel = obj.objectLabel || obj.objectCode || '数据对象'
          const item = {
            id: f.id,
            refType,
            varCode: codeText,
            varCodeText: f.scriptName || f.varCode || '',
            scriptName: codeText,
            varLabel: labelText + (codeText ? ' ' + codeText : ''),
            varLabelText: labelText,
            varType: f.varType,
            varSource: 'INPUT',
            sourceType: 'dataObject',
            sourceLabel: objLabel,
            sourceCode: objCode,
            varObj: { ...f, refType }
          }
          this.putVarMap(item)
          objOptions.push(item)
        })
      })
      models.forEach(m => {
        const refType = 'MODEL'
        const seenKey = this.refKey(m.id, refType)
        if (!m.id || seenIds.has(seenKey)) return
        seenIds.add(seenKey)
        const codeText = m.modelCode || ''
        if (!codeText) return
        const labelText = m.modelName || codeText
        const item = {
          id: m.id,
          refType,
          varCode: codeText,
          varCodeText: codeText,
          scriptName: codeText,
          varLabel: labelText + ' ' + codeText,
          varLabelText: labelText,
          varType: 'MODEL',
          varSource: 'MODEL',
          sourceType: 'model',
          varObj: { ...m, id: m.id, varCode: codeText, varLabel: labelText, scriptName: codeText, varType: 'MODEL', refType }
        }
        this.putVarMap(item)
        modelOptions.push(item)
      })
      this.varPickerGroups.splice(0, this.varPickerGroups.length, ...[
        { label: '普通变量', options: varOptions },
        { label: '常量', options: constOptions },
        { label: '数据对象字段', options: objOptions },
        { label: '模型', options: modelOptions }
      ])
    },
    normalizeFieldPages() {
      const inputMax = Math.max(1, Math.ceil(this.inputFieldsTotal / this.fieldPageSize))
      const outputMax = Math.max(1, Math.ceil(this.outputFieldsTotal / this.fieldPageSize))
      if (this.inputFieldPage > inputMax) this.inputFieldPage = inputMax
      if (this.outputFieldPage > outputMax) this.outputFieldPage = outputMax
    },
    onVarClear(row) {
      this.$set(row, 'varId', null)
      this.$set(row, '_varId', null)
      this.$set(row, 'refType', '')
      this.$set(row, 'fieldLabel', '')
      this.$set(row, 'scriptName', '')
    },
    onVarChange(row, varId) {
      if (!varId) return
      // 从 varPickerGroups 所有选项中查找
      let opt = null
      for (const group of this.varPickerGroups) {
        const found = group.options.find(o => o.id === varId && (!row.refType || o.refType === row.refType))
        if (found) { opt = found; break }
      }
      if (!opt) return
      this.$set(row, 'varId', opt.id)
      this.$set(row, 'fieldLabel', opt.varLabel)
      this.$set(row, 'scriptName', opt.varCode)
      this.$set(row, 'varSource', opt.sourceType)
      this.$set(row, 'refType', opt.refType || '')
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
    fieldParamKey(field) {
      return (field && (field.scriptName || field.fieldName)) || ''
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
          refType: row.refType,
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
          refType: row.refType,
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
    async openVersionDialog() {
      this.versionVisible = true
      this.versionCompare = null
      await this.loadVersions()
    },
    async loadVersions() {
      if (!this.rule.id) return
      this.versionLoading = true
      try {
        const res = await api.listVersions(this.rule.id)
        this.versions = Array.isArray(res.data) ? res.data : (Array.isArray(res) ? res : [])
      } catch (e) {
        this.$message.error(e.message || '加载版本历史失败')
      } finally {
        this.versionLoading = false
      }
    },
    async compareVersion(left, right) {
      if (!left || !right) return
      try {
        const res = await api.compareVersions(this.rule.id, left.version, right.version)
        this.versionCompare = res.data || res
      } catch (e) {
        this.$message.error(e.message || '版本对比失败')
      }
    },
    async rollbackVersion(row) {
      if (!row || !row.version) return
      try {
        await this.$confirm('回滚会覆盖当前草稿内容，但不会自动发布，确认回滚到 v' + row.version + '？', '确认回滚', { type: 'warning' })
        await api.rollbackVersion(this.rule.id, row.version)
        this.$message.success('回滚成功')
        await this.load()
        await this.loadVersions()
      } catch (e) {
        if (e !== 'cancel') {
          this.$message.error(e.message || '回滚失败')
        }
      }
    },
    formatVersionTime(value) {
      return value ? String(value).replace('T', ' ') : '-'
    },
    formatVersionJson(value) {
      if (!value) return ''
      try {
        return JSON.stringify(JSON.parse(value), null, 2)
      } catch (e) {
        return value
      }
    },
    parseVersionObject(value) {
      if (!value) return {}
      if (typeof value === 'object') return value
      try {
        return JSON.parse(value)
      } catch (e) {
        return { raw: value }
      }
    },
    flattenVersionObject(value, prefix = '') {
      const result = {}
      if (value == null || typeof value !== 'object') {
        result[prefix || '规则内容'] = value
        return result
      }
      if (Array.isArray(value)) {
        if (value.length === 0) {
          result[prefix || '列表'] = []
          return result
        }
        value.forEach((item, index) => {
          Object.assign(result, this.flattenVersionObject(item, prefix ? prefix + '[' + (index + 1) + ']' : '[' + (index + 1) + ']'))
        })
        return result
      }
      const keys = Object.keys(value)
      if (keys.length === 0) {
        result[prefix || '对象'] = {}
        return result
      }
      keys.forEach(key => {
        const label = prefix ? prefix + '.' + key : key
        Object.assign(result, this.flattenVersionObject(value[key], label))
      })
      return result
    },
    businessPathLabel(path) {
      return String(path || '')
        .replace(/\brules\b/g, '规则行')
        .replace(/\bconditions\b/g, '条件')
        .replace(/\bactions\b/g, '执行动作')
        .replace(/\bdefaultActions\b/g, '默认动作')
        .replace(/\bnodes\b/g, '节点')
        .replace(/\bedges\b/g, '连线')
        .replace(/\bvarCode\b/g, '字段编码')
        .replace(/\bvarLabel\b/g, '字段名称')
        .replace(/\boperator\b/g, '判断关系')
        .replace(/\bvalue\b/g, '取值')
        .replace(/\bresultVar\b/g, '结果字段')
    },
    displayVersionValue(value) {
      if (value == null) return '空值'
      if (typeof value === 'object') return JSON.stringify(value)
      return String(value)
    },
    sameVersionValue(left, right) {
      return this.displayVersionValue(left) === this.displayVersionValue(right)
    },
    diffTypeLabel(type) {
      return { leftOnly: '左侧独有', rightOnly: '右侧独有', changed: '变更' }[type] || '变更'
    },
    diffTagType(type) {
      return { leftOnly: 'success', rightOnly: 'info', changed: 'warning' }[type] || 'warning'
    },
    diffValueClass(row, side) {
      if (!row) return ''
      if (row.type === 'leftOnly' && side === 'left') return 'diff-value-added'
      if (row.type === 'rightOnly' && side === 'right') return 'diff-value-added'
      if (row.type === 'changed') return 'diff-value-changed'
      return ''
    },
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
        freshRule = (res.data !== undefined ? res.data : res) || freshRule
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
        const key = this.fieldParamKey(f)
        if (f.defaultValue !== undefined && f.defaultValue !== null && f.defaultValue !== '') {
          testParams[key] = f.defaultValue
        } else if (f.fieldType === 'BOOLEAN') {
          testParams[key] = false
        } else if (f.fieldType === 'NUMBER' || f.fieldType === 'DOUBLE' || f.fieldType === 'INTEGER') {
          testParams[key] = 0
        } else {
          testParams[key] = ''
        }
      })

      const jsonObj = {}
      testFields.forEach(f => { jsonObj[this.fieldParamKey(f)] = testParams[this.fieldParamKey(f)] })
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
        const key = this.fieldParamKey(f)
        const val = this.testParams[key]
        obj[key] = (val !== '' && val !== null) ? val : null
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
        const inputFieldNames = new Set(this.testFields.map(f => this.fieldParamKey(f)))
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
        const key = this.fieldParamKey(f)
        if (f.fieldType === 'BOOLEAN') this.testParams[key] = false
        else if (f.fieldType === 'NUMBER' || f.fieldType === 'DOUBLE' || f.fieldType === 'INTEGER') this.testParams[key] = 0
        else this.testParams[key] = ''
      })
      const jsonObj = {}
      this.testFields.forEach(f => { jsonObj[this.fieldParamKey(f)] = this.testParams[this.fieldParamKey(f)] })
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
.version-diff-panel {
  margin-top: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  overflow: hidden;
}
.version-diff-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: #f8fafc;
  color: #334155;
  font-weight: 700;
}
.version-diff-count {
  color: #64748b;
  font-size: 12px;
  font-weight: 500;
}
.version-tech-collapse {
  margin-top: 12px;
}
.diff-value-added {
  color: #047857;
  font-weight: 600;
}
.diff-value-removed {
  color: #b91c1c;
  text-decoration: line-through;
}
.diff-value-changed {
  color: #92400e;
  font-weight: 600;
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
.version-compare-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-top: 12px;
}
.version-compare-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 6px;
}
.version-compare-grid pre {
  margin: 0;
  padding: 10px;
  height: 220px;
  overflow: auto;
  background: #f5f7fa;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.5;
}
</style>
