<template>
  <div class="uiue-list-page rule-test-page">
    <div class="test-layout">
      <!-- 左侧：选择规则 + 参数输入 -->
      <div class="test-left">
        <div class="uiue-card">
          <div class="uiue-card-title">选择规则</div>
          <el-form size="small" label-width="80px">
            <el-form-item label="筛选范围">
              <el-radio-group
                v-model="ruleScope"
                size="small"
                style="width: 100%"
                @change="onScopeChange"
              >
                <el-radio-button value="ALL">全部</el-radio-button>
                <el-radio-button value="PROJECT">项目级</el-radio-button>
                <el-radio-button value="GLOBAL">全局</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="项目" v-if="ruleScope === 'PROJECT'">
              <el-select
                v-model="selectedProjectId"
                placeholder="请选择项目"
                clearable
                :loading="projectsLoading"
                :disabled="!!contextProjectId"
                style="width: 100%"
                @change="onProjectChange"
              >
                <el-option
                  v-for="p in projects"
                  :key="p.id"
                  :label="p.projectName"
                  :value="p.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="规则">
              <el-select
                v-model="selectedRuleId"
                :placeholder="
                  ruleScope === 'PROJECT' && !selectedProjectId
                    ? '请先选择项目'
                    : '请选择规则'
                "
                :disabled="ruleScope === 'PROJECT' && !selectedProjectId"
                :loading="ruleListLoading"
                style="width: 100%"
                filterable
                @change="onRuleChange"
              >
                <el-option
                  v-for="r in rules"
                  :key="r.id"
                  :label="ruleOptionLabel(r)"
                  :value="r.id"
                />
              </el-select>
            </el-form-item>
          </el-form>
          <div
            v-if="ruleScope === 'PROJECT' && projectsError"
            class="test-setup-state is-error"
          >
            <div>
              <strong>项目列表加载失败</strong>
              <span>{{ projectsError }}</span>
            </div>
            <el-button link size="small" @click="loadProjects">重试</el-button>
          </div>
          <div v-if="ruleListError" class="test-setup-state is-error">
            <div>
              <strong>规则列表加载失败</strong>
              <span>{{ ruleListError }}</span>
            </div>
            <el-button link size="small" @click="retryRuleOptions"
              >重试</el-button
            >
          </div>
          <div
            v-else-if="
              !ruleListLoading &&
              ((ruleScope !== 'PROJECT' || selectedProjectId) &&
                rules.length === 0)
            "
            class="test-setup-state is-empty"
          >
            <div>
              <strong>当前范围没有可测试规则</strong>
              <span>请先创建规则，或确认全局规则已绑定到当前项目。</span>
            </div>
            <el-button link size="small" @click="retryRuleOptions"
              >重新检查</el-button
            >
          </div>
          <div v-if="selectedRule" class="rule-info">
            <el-descriptions :column="2" size="small" border>
              <el-descriptions-item label="规则编码">{{
                selectedRule.ruleCode
              }}</el-descriptions-item>
              <el-descriptions-item label="模型类型">
                <el-tag size="small">{{ mtl(selectedRule.modelType) }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="当前版本"
                >v{{ selectedRule.currentVersion }}</el-descriptions-item
              >
              <el-descriptions-item label="状态">
                <el-tag
                  :type="
                    { 0: 'info', 1: 'success', 2: 'warning' }[
                      selectedRule.status
                    ]
                  "
                  size="small"
                >
                  {{ ['草稿', '已发布', '已下线'][selectedRule.status] }}
                </el-tag>
              </el-descriptions-item>
            </el-descriptions>
          </div>
          <ul v-if="contextWarnings.length" class="context-load-warnings">
            <li v-for="warning in contextWarnings" :key="warning">
              {{ warning }}
            </li>
          </ul>
        </div>

        <div
          class="uiue-card input-parameters-card"
          style="margin-top: 12px"
        >
          <div class="uiue-card-title">
            输入参数
            <el-button
              link
              size="small"
              style="margin-left: 12px"
              @click="loadVariables"
              v-if="selectedRule"
            >
              <el-icon><el-icon-refresh /></el-icon> 重新加载字段
            </el-button>
            <el-button
              link
              size="small"
              style="margin-left: 8px"
              @click="addParam"
            >
              <el-icon><el-icon-plus /></el-icon> 手动添加
            </el-button>
            <el-button
              link
              size="small"
              style="margin-left: 8px"
              @click="applyRiskDemoParams"
            >
              <el-icon><el-icon-magic-stick /></el-icon> 综合风控样例
            </el-button>
          </div>
          <div
            v-if="selectedRule && inputLoadState !== 'IDLE'"
            class="input-load-state"
            :class="'is-' + inputLoadState.toLowerCase()"
          >
            <div>
              <strong>{{ inputLoadStateTitle }}</strong>
              <span>{{ inputLoadStateDescription }}</span>
            </div>
            <el-button
              v-if="inputLoadState === 'ERROR' || inputLoadState === 'EMPTY'"
              link
              size="small"
              :loading="inputLoadState === 'LOADING'"
              @click="loadVariables"
              >重新加载</el-button
            >
          </div>
          <ul v-if="inputLoadWarnings.length" class="input-load-warnings">
            <li v-for="warning in inputLoadWarnings" :key="warning">
              {{ warning }}
            </li>
          </ul>
          <div
            v-if="params.length === 0 && inputLoadState !== 'LOADING'"
            style="color: var(--tianshu-text-tertiary); padding: 12px 0; text-align: center"
          >
            {{
              selectedRule
                ? '该规则暂无结构化输入，可直接执行或手动添加参数'
                : '请选择规则后加载字段，或手动添加参数'
            }}
          </div>
          <el-form v-else size="small" label-width="0">
            <div v-for="(p, idx) in params" :key="idx" class="param-row">
              <el-input
                v-model="p.key"
                placeholder="参数名"
                class="param-key"
                :disabled="p.fromVar"
              />
              <span class="param-label" v-if="p.label">({{ p.label }})</span>
              <template v-if="p.type === 'BOOLEAN'">
                <el-select
                  v-model="p.value"
                  class="param-value"
                  placeholder="选择"
                >
                  <el-option label="true" value="true" />
                  <el-option label="false" value="false" />
                </el-select>
              </template>
              <template
                v-else-if="
                  p.type === 'ENUM' && p.options && p.options.length > 0
                "
              >
                <el-select
                  v-model="p.value"
                  class="param-value"
                  placeholder="选择枚举值"
                  clearable
                  filterable
                >
                  <el-option
                    v-for="opt in p.options"
                    :key="opt.optionValue"
                    :label="opt.optionLabel + ' (' + opt.optionValue + ')'"
                    :value="opt.optionValue"
                  />
                </el-select>
              </template>
              <template v-else>
                <el-input
                  v-model="p.value"
                  :placeholder="p.example || '参数值'"
                  class="param-value"
                />
              </template>
              <el-button
                link
                size="small"
                class="btn-delete"
                style="margin-left: 4px"
                @click="params.splice(idx, 1)"
              >
                <el-icon><el-icon-delete /></el-icon>
              </el-button>
            </div>
          </el-form>
        </div>

        <div
          v-if="selectedRule"
          class="uiue-card fixed-cases-card"
          :class="{
            'is-empty':
              !testCasesLoading && !testCasesError && testCases.length === 0,
          }"
        >
          <div class="uiue-card-title fixed-cases-title">
            <span>固定测试用例收藏</span>
            <span>
              <el-button link size="small" @click="saveCurrentTestCase"
                ><el-icon><el-icon-star-off /></el-icon>
                收藏当前输入与结果</el-button
              >
              <el-button
                link
                size="small"
                :loading="batchExecuting"
                :disabled="selectedTestCaseIds.length === 0"
                @click="executeSelectedTestCases"
                ><el-icon><el-icon-video-play /></el-icon> 批量执行</el-button
              >
            </span>
          </div>
          <div
            v-if="
              !testCasesLoading && !testCasesError && testCases.length === 0
            "
            class="fixed-case-empty"
          >
            暂无收藏用例，可将当前参数与结果保存为固定用例
          </div>
          <async-state
            v-else
            :loading="testCasesLoading"
            :error="testCasesError"
            :empty="false"
            compact
            @retry="loadTestCases"
          >
            <el-checkbox-group
              v-model="selectedTestCaseIds"
              class="fixed-case-list"
            >
              <div
                v-for="testCase in testCases"
                :key="testCase.id"
                class="fixed-case-row"
              >
                <el-checkbox :value="testCase.id">{{
                  testCase.scenarioName
                }}</el-checkbox>
                <el-button
                  link
                  size="small"
                  @click="applyTestCase(testCase)"
                  >载入</el-button
                >
              </div>
            </el-checkbox-group>
          </async-state>
          <el-table show-overflow-tooltip
            v-if="batchResults.length"
            :data="batchResults"
            border
            size="small"
            class="batch-result-table"
          >
            <el-table-column prop="scenarioName" label="用例" min-width="130" />
            <el-table-column label="执行结果" min-width="90">
              <template v-slot="{ row }"
                ><el-tag
                  :type="row.success ? 'success' : 'danger'"
                  size="small"
                  >{{ row.success ? '成功' : '失败' }}</el-tag
                ></template
              >
            </el-table-column>
            <el-table-column label="结果 Diff" min-width="220">
              <template v-slot="{ row }">
                <span v-if="row.diffs.length === 0" class="diff-pass"
                  >与收藏结果一致</span
                >
                <div v-else class="diff-lines">
                  <div v-for="(diff, index) in row.diffs" :key="index">
                    <code>{{ diff.path }}</code
                    >：{{ formatJson(diff.expected) }} →
                    {{ formatJson(diff.actual) }}
                  </div>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div style="margin-top: 16px; text-align: center">
          <span style="margin-right: 6px; color: var(--tianshu-text-secondary)">页面请求超时</span>
          <el-input-number
            v-model="requestTimeoutMs"
            :min="1000"
            :max="1800000"
            :step="1000"
            size="small"
            style="width: 150px; margin-right: 6px"
          />
          <span style="margin-right: 12px; color: var(--tianshu-text-tertiary)">毫秒</span>
          <el-button
            type="primary"
            :loading="executing"
            :disabled="!selectedRuleId"
            @click="handleExecute"
          >
            <el-icon><el-icon-video-play /></el-icon> 执行测试
          </el-button>
          <el-button @click="handleClear">清空</el-button>
        </div>
      </div>

      <!-- 右侧：执行结果 -->
      <div class="test-right">
        <div class="uiue-card test-result-card">
          <div class="uiue-card-title">执行结果</div>
          <div v-if="!result && !executing" class="result-empty">
            <el-icon
              style="font-size: 48px; color: var(--tianshu-text-disabled)"
              ><el-icon-video-play
            /></el-icon>
            <p style="color: var(--tianshu-text-tertiary); margin-top: 12px">
              点击「执行测试」查看结果
            </p>
          </div>
          <div
            v-else-if="executing"
            style="text-align: center; padding: 60px 0"
          >
            <el-icon style="font-size: 32px; color: var(--el-color-primary)"
              ><el-icon-loading
            /></el-icon>
            <p style="color: var(--tianshu-text-tertiary); margin-top: 12px">规则执行中...</p>
          </div>
          <div v-else class="test-result-content">
            <el-alert
              :title="result.success ? '执行成功' : '执行失败'"
              :type="result.success ? 'success' : 'error'"
              :closable="false"
              show-icon
              style="margin-bottom: 16px"
            >
              <span>耗时 {{ result.executeTimeMs }} ms</span>
            </el-alert>

            <div v-if="result.errorMessage" style="margin-bottom: 16px">
              <div class="result-section-title is-error">错误信息</div>
              <pre class="result-pre is-error">{{ result.errorMessage }}</pre>
            </div>

            <div style="margin-bottom: 16px">
              <div class="result-section-title">返回结果</div>
              <pre class="result-pre">{{ formatJson(result.output) }}</pre>
            </div>

            <div
              v-if="result.traces && result.traces.length > 0"
              class="trace-section"
            >
              <div class="trace-filter-bar">
                <span>追踪树筛选</span>
                <el-select
                  v-model="traceStatusFilter"
                  size="small"
                  style="width: 110px"
                >
                  <el-option label="全部状态" value="ALL" />
                  <el-option label="成功" value="SUCCESS" />
                  <el-option label="失败" value="FAILED" />
                  <el-option label="命中" value="HIT" />
                  <el-option label="未命中" value="MISS" />
                </el-select>
                <el-input
                  v-model="traceKeyword"
                  size="small"
                  clearable
                  placeholder="规则、节点、表达式关键字"
                  style="width: 220px"
                />
              </div>
              <el-tabs v-model="traceTab" class="trace-tabs">
                <el-tab-pane
                  label="执行追踪（JSON）"
                  name="json"
                  class="trace-pane trace-json-pane"
                >
                  <el-collapse>
                    <el-collapse-item
                      v-for="(trace, idx) in result.traces"
                      :key="idx"
                      :title="'步骤 ' + (idx + 1)"
                    >
                      <pre class="result-pre">{{ formatJson(trace) }}</pre>
                    </el-collapse-item>
                  </el-collapse>
                </el-tab-pane>
                <el-tab-pane
                  label="表达式追踪树"
                  name="tree"
                  class="trace-pane trace-tree-pane"
                >
                  <div class="trace-tree-wrap">
                    <trace-tree
                      :trace-info="traceInfoJson"
                      :var-map="varMap"
                      :function-name-map="functionNameMap"
                      :model-type="selectedRule ? selectedRule.modelType : ''"
                      :input-params="inputParamsJson"
                      :output-result="outputResultJson"
                      :rule-name="selectedRule ? selectedRule.ruleName : ''"
                      :rule-version="
                        selectedRule ? selectedRule.currentVersion : ''
                      "
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
import {
  Star as ElIconStarOff,
  VideoPlay as ElIconVideoPlay,
  Refresh as ElIconRefresh,
  Plus as ElIconPlus,
  MagicStick as ElIconMagicStick,
  Delete as ElIconDelete,
  Loading as ElIconLoading,
} from '@element-plus/icons-vue'
import { listProjects } from '@/api/project'
import {
  listDefinitions,
  listProjectDefinitions,
  executeRule,
  getContent,
  listInputFields,
  getRuleTestSchema,
  listApiScenarios,
  createApiScenario,
  executeApiScenario,
} from '@/api/definition'
import { getVariableOptions } from '@/api/variable'
import { getDataObjectFieldOptions } from '@/api/dataObject'
import { listAllFunctionsByProject } from '@/api/function'
import { getModel } from '@/api/model'
import TraceTree from '@/components/common/TraceTree.vue'
import AsyncState from '@/components/common/AsyncState.vue'
import { sampleValueForVarType } from '@/utils/testParamTemplate'
import { normalizeTestResult } from '@/utils/testResult'
import { routeProjectId } from '@/utils/projectContext'
import {
  normalizeTestSchema,
  schemaFieldsToTestFields,
  flattenSchemaSample,
} from '@/utils/testSchema'
import {
  diffTestResults,
  filterTraceTree,
  scenarioParams,
} from '@/utils/testCaseTools'

export default {
  components: {
    TraceTree,
    AsyncState,
    ElIconStarOff,
    ElIconVideoPlay,
    ElIconRefresh,
    ElIconPlus,
    ElIconMagicStick,
    ElIconDelete,
    ElIconLoading,
  },
  name: 'RuleTest',
  data() {
    return {
      projects: [],
      projectsLoading: false,
      projectsError: '',
      projectsRequestId: 0,
      rules: [],
      ruleListLoading: false,
      ruleListError: '',
      ruleListRequestId: 0,
      ruleScope: 'ALL',
      contextProjectId: null,
      selectedProjectId: null,
      selectedRuleId: null,
      selectedRule: null,
      params: [],
      inputLoadState: 'IDLE',
      inputLoadError: '',
      inputLoadSource: '',
      inputLoadWarnings: [],
      inputLoadRequestId: 0,
      ruleContextRequestId: 0,
      executionRequestId: 0,
      contextWarnings: [],
      executing: false,
      result: null,
      lastRawResponse: null,
      traceTab: 'tree',
      traceStatusFilter: 'ALL',
      traceKeyword: '',
      testCases: [],
      selectedTestCaseIds: [],
      testCasesLoading: false,
      testCasesError: '',
      testCasesRequestId: 0,
      batchExecuting: false,
      batchResults: [],
      batchExecutionRequestId: 0,
      varMap: {},
      functionNameMap: {},
      modelData: null,
      definitionModel: null,
      requestTimeoutMs: 180000,
    }
  },
  created() {
    this.loadProjects()
    const projectId = routeProjectId(
      this.$route,
      this.$store && this.$store.state.currentProject
    )
    this.contextProjectId = projectId
    if (projectId) this.applyProjectContext(projectId)
    else this.loadRulesByScope()
    this.loadVarMap()
    this.loadFunctionNameMap()
  },
  computed: {
    traceInfoJson: function () {
      if (
        !this.result ||
        !this.result.traces ||
        this.result.traces.length === 0
      )
        return ''
      var filtered = filterTraceTree(this.result.traces[0], {
        status: this.traceStatusFilter,
        keyword: this.traceKeyword,
      })
      return filtered ? JSON.stringify(filtered) : ''
    },
    inputParamsJson: function () {
      // inputParamsJson 供 TraceTree 组件渲染入参，始终基于当前 params 构建
      return JSON.stringify(this.buildParamMap())
    },
    outputResultJson: function () {
      if (!this.result || !this.result.hasOutput) return ''
      return JSON.stringify(this.result.output)
    },
    inputLoadStateTitle: function () {
      return {
        LOADING: '正在准备测试输入',
        READY: '测试输入已就绪',
        EMPTY: '未识别到结构化输入',
        ERROR: '测试输入加载失败',
      }[this.inputLoadState] || '尚未加载测试输入'
    },
    inputLoadStateDescription: function () {
      if (this.inputLoadState === 'LOADING') {
        return '正在读取规则引用和测试样例，请稍候。'
      }
      if (this.inputLoadState === 'READY') {
        const source = this.inputLoadSource === 'FALLBACK'
          ? '规则引用字段'
          : '统一测试结构'
        return `已从${source}加载 ${this.params.length} 个参数；重新加载会保留已填写值。`
      }
      if (this.inputLoadState === 'EMPTY') {
        return '该规则可能无需入参，也可以手动添加脚本所需参数。'
      }
      if (this.inputLoadState === 'ERROR') {
        return this.inputLoadError || '无法确认规则需要哪些输入，可重试或手动添加参数。'
      }
      return ''
    },
  },
  methods: {
    async applyProjectContext(projectId) {
      this.contextProjectId = Number(projectId)
      this.ruleScope = 'PROJECT'
      this.selectedProjectId = Number(projectId)
      await this.onProjectChange()
    },
    async loadRulesByScope() {
      if (this.ruleScope === 'ALL') {
        await this.loadRuleOptions(() =>
          listDefinitions({ pageNum: 1, pageSize: 1000 })
        )
      } else if (this.ruleScope === 'GLOBAL') {
        await this.loadGlobalRules()
      } else if (this.selectedProjectId) {
        await this.loadProjectRules(this.selectedProjectId)
      } else {
        ++this.ruleListRequestId
        this.rules = []
        this.ruleListError = ''
        this.ruleListLoading = false
      }
    },
    async loadProjects() {
      const requestId = ++this.projectsRequestId
      this.projectsLoading = true
      this.projectsError = ''
      try {
        const res = await listProjects({ pageNum: 1, pageSize: 1000 })
        if (requestId !== this.projectsRequestId) return
        this.projects = res.data.records || []
      } catch (e) {
        if (requestId !== this.projectsRequestId) return
        this.projects = []
        this.projectsError = this.requestErrorMessage(
          e,
          '请检查网络或权限后重试'
        )
      } finally {
        if (requestId === this.projectsRequestId) {
          this.projectsLoading = false
        }
      }
    },
    async loadVarMap(contextRequestId) {
      this.varMap = {}
      if (!this.selectedRuleId) return
      const ruleId = this.selectedRuleId
      try {
        var response = await listInputFields(ruleId)
        if (!this.isActiveRuleContext(contextRequestId, ruleId)) return
        var fields = this.unwrapResponse(response)
        fields = Array.isArray(fields) ? fields : []
        var map = {}
        fields.forEach(function (field) {
          if (!field || field.varId == null || !field.refType) return
          var path = field.scriptName || field.fieldName
          if (path) map[path] = field.fieldLabel || field.fieldName || path
        })
        this.varMap = map
      } catch (e) {
        if (this.isActiveRuleContext(contextRequestId, ruleId)) {
          this.addContextWarning(
            '字段中文名称加载失败，追踪树将暂时显示字段编码'
          )
        }
      }
    },
    async loadFunctionNameMap(contextRequestId) {
      this.functionNameMap = {}
      if (!this.selectedRule) return
      const ruleId = this.selectedRuleId
      var pid = this.selectedRule.projectId
      if (!pid) return
      try {
        var r = await listAllFunctionsByProject(pid)
        if (!this.isActiveRuleContext(contextRequestId, ruleId)) return
        var funcData = r && r.data ? r.data : r
        var list = Array.isArray(funcData)
          ? funcData
          : funcData && Array.isArray(funcData.records)
          ? funcData.records
          : []
        var map = {}
        for (var j = 0; j < list.length; j++) {
          var f = list[j]
          if (f && f.funcCode && f.funcName) map[f.funcCode] = f.funcName
        }
        this.functionNameMap = map
      } catch (e) {
        if (this.isActiveRuleContext(contextRequestId, ruleId)) {
          this.addContextWarning(
            '函数中文名称加载失败，追踪树仍可使用函数编码查看'
          )
        }
      }
    },
    async loadModelJson(contextRequestId) {
      this.modelData = null
      this.definitionModel = null
      if (!this.selectedRuleId || !this.selectedRule) return
      const ruleId = this.selectedRuleId
      try {
        var r = await getContent(ruleId)
        if (!this.isActiveRuleContext(contextRequestId, ruleId)) return
        var content = r && r.data ? r.data : r
        if (content && content.modelJson) {
          var model = JSON.parse(content.modelJson)
          this.definitionModel = model
          if (model.nodes && model.edges) {
            this.modelData = { nodes: model.nodes, edges: model.edges }
          }
        }
      } catch (e) {
        if (this.isActiveRuleContext(contextRequestId, ruleId)) {
          this.addContextWarning(
            '规则设计内容加载失败，图形化追踪可能不可用'
          )
        }
      }
    },
    async onScopeChange() {
      ++this.ruleContextRequestId
      this.selectedRuleId = null
      this.selectedRule = null
      this.rules = []
      this.params = []
      this.resetExecutionState()
      this.contextWarnings = []
      this.resetInputLoadState()
      this.resetTestCases()
      this.selectedProjectId = this.contextProjectId

      await this.loadRulesByScope()
    },
    async loadGlobalRules() {
      const params = {
        pageNum: 1,
        pageSize: 1000,
        scope: 'GLOBAL',
      }
      return this.loadRuleOptions(() =>
        this.contextProjectId
          ? listProjectDefinitions(this.contextProjectId, params)
          : listDefinitions(params)
      )
    },
    async onProjectChange() {
      ++this.ruleContextRequestId
      this.selectedRuleId = null
      this.selectedRule = null
      this.rules = []
      this.params = []
      this.resetExecutionState()
      this.contextWarnings = []
      this.resetInputLoadState()
      this.resetTestCases()
      if (!this.selectedProjectId) {
        ++this.ruleListRequestId
        this.ruleListLoading = false
        this.ruleListError = ''
        return
      }
      await this.loadProjectRules(this.selectedProjectId)
    },
    async onRuleChange() {
      const contextRequestId = ++this.ruleContextRequestId
      this.selectedRule =
        this.rules.find((r) => r.id === this.selectedRuleId) || null
      this.params = []
      this.resetExecutionState()
      this.contextWarnings = []
      this.resetInputLoadState()
      await Promise.all([
        this.loadModelJson(contextRequestId),
        this.loadFunctionNameMap(contextRequestId),
        this.loadVarMap(contextRequestId),
        this.loadVariables(),
        this.loadTestCases(),
      ])
    },
    async loadProjectRules(projectId) {
      const targetProjectId = projectId
      return this.loadRuleOptions(() =>
        listProjectDefinitions(targetProjectId, {
          pageNum: 1,
          pageSize: 1000,
        })
      )
    },
    async loadRuleOptions(loader) {
      const requestId = ++this.ruleListRequestId
      this.ruleListLoading = true
      this.ruleListError = ''
      this.rules = []
      try {
        const res = await loader()
        if (requestId !== this.ruleListRequestId) return
        this.rules = (res.data && res.data.records) || []
      } catch (e) {
        if (requestId !== this.ruleListRequestId) return
        this.ruleListError = this.requestErrorMessage(
          e,
          '请检查网络或权限后重试'
        )
      } finally {
        if (requestId === this.ruleListRequestId) {
          this.ruleListLoading = false
        }
      }
    },
    retryRuleOptions() {
      return this.loadRulesByScope()
    },
    resetTestCases() {
      ++this.testCasesRequestId
      ++this.batchExecutionRequestId
      this.testCases = []
      this.selectedTestCaseIds = []
      this.batchResults = []
      this.testCasesError = ''
      this.testCasesLoading = false
      this.batchExecuting = false
    },
    async loadTestCases() {
      this.resetTestCases()
      if (!this.selectedRuleId) return
      const ruleId = this.selectedRuleId
      const requestId = ++this.testCasesRequestId
      this.testCasesLoading = true
      try {
        const res = await listApiScenarios(ruleId)
        if (!this.isActiveTestCaseRequest(requestId, ruleId)) return
        const data = this.unwrapResponse(res)
        this.testCases = Array.isArray(data) ? data : []
      } catch (e) {
        if (!this.isActiveTestCaseRequest(requestId, ruleId)) return
        this.testCasesError = this.requestErrorMessage(
          e,
          '固定测试用例加载失败，请重试'
        )
      } finally {
        if (this.isActiveTestCaseRequest(requestId, ruleId)) {
          this.testCasesLoading = false
        }
      }
    },
    async saveCurrentTestCase() {
      if (!this.selectedRuleId) return
      let promptResult
      try {
        promptResult = await this.$prompt(
          '请输入固定用例名称',
          '收藏测试用例',
          {
            inputValidator: (value) => Boolean(value && value.trim()),
            inputErrorMessage: '用例名称不能为空',
          }
        )
      } catch (e) {
        return
      }
      const params = this.buildParamMap()
      const response = this.lastRawResponse || {
        code: 200,
        data: { success: false, result: null, errorMessage: '尚未执行' },
      }
      await createApiScenario(this.selectedRuleId, {
        scenarioName: promptResult.value.trim(),
        description: '规则测试页收藏的固定测试用例',
        requestJson: JSON.stringify({
          clientAppName: 'rule-test',
          params: params,
        }),
        responseJson: JSON.stringify(response),
        responseSource: this.lastRawResponse ? 'EXECUTED' : 'MANUAL',
        businessCodePath: '',
        includeInDoc: 0,
        status: 1,
      })
      await this.loadTestCases()
      this.notifySuccess('固定测试用例已收藏')
    },
    async applyTestCase(testCase) {
      const ruleId = this.selectedRuleId
      const values = scenarioParams(testCase && testCase.requestJson)
      await this.loadVariables()
      if (String(ruleId) !== String(this.selectedRuleId)) return
      const existing = {}
      this.params.forEach((param) => {
        existing[param.key] = param
      })
      const flattened = this.flattenCaseParams(values)
      Object.keys(flattened).forEach((key) => {
        if (existing[key]) {
          existing[key].value = flattened[key]
        } else {
          this.params.push({
            key: key,
            label: '',
            value: flattened[key],
            type: this.valueType(flattened[key]),
            example: '',
            fromVar: false,
            options: [],
          })
        }
      })
      this.resetExecutionState()
    },
    async executeSelectedTestCases() {
      const selected = this.testCases.filter(
        (testCase) => this.selectedTestCaseIds.indexOf(testCase.id) >= 0
      )
      if (selected.length === 0) return
      const ruleId = this.selectedRuleId
      const requestId = ++this.batchExecutionRequestId
      this.batchExecuting = true
      this.batchResults = []
      try {
        for (const testCase of selected) {
          try {
            const request = JSON.parse(testCase.requestJson || '{}')
            if (this.selectedProjectId) {
              request.projectId = this.selectedProjectId
            }
            const actual = await executeApiScenario(
              ruleId,
              request,
              this.requestTimeoutMs
            )
            if (!this.isActiveBatchExecution(requestId, ruleId)) return
            const expected = JSON.parse(testCase.responseJson || '{}')
            this.batchResults.push({
              scenarioName: testCase.scenarioName,
              success: true,
              diffs: diffTestResults(expected, actual),
            })
          } catch (e) {
            if (!this.isActiveBatchExecution(requestId, ruleId)) return
            this.batchResults.push({
              scenarioName: testCase.scenarioName,
              success: false,
              diffs: [
                {
                  path: '$',
                  expected: '成功执行',
                  actual: e.message || '执行失败',
                },
              ],
            })
          }
        }
      } finally {
        if (this.isActiveBatchExecution(requestId, ruleId)) {
          this.batchExecuting = false
        }
      }
    },
    flattenCaseParams(value, prefix, target) {
      const output = target || {}
      const currentPrefix = prefix || ''
      Object.keys(value || {}).forEach((key) => {
        const path = currentPrefix ? currentPrefix + '.' + key : key
        const item = value[key]
        if (item && typeof item === 'object' && !Array.isArray(item))
          this.flattenCaseParams(item, path, output)
        else output[path] = item
      })
      return output
    },
    valueType(value) {
      if (typeof value === 'boolean') return 'BOOLEAN'
      if (typeof value === 'number') return 'NUMBER'
      return 'STRING'
    },
    async loadVariables() {
      if (!this.selectedRule || !this.selectedRuleId) {
        this.resetInputLoadState()
        return
      }
      const ruleId = this.selectedRuleId
      const requestId = ++this.inputLoadRequestId
      const previousParams = this.params.map((param) => ({ ...param }))
      const previousValues = new Map(
        previousParams
          .filter((param) => param.key)
          .map((param) => [param.key, param.value])
      )
      const manualParams = previousParams.filter((param) => !param.fromVar)
      const warnings = []
      let schemaSucceeded = false
      this.inputLoadState = 'LOADING'
      this.inputLoadError = ''
      this.inputLoadSource = ''
      this.inputLoadWarnings = []
      try {
        var schema = normalizeTestSchema(
          await getRuleTestSchema({
            targetType: 'RULE',
            targetId: ruleId,
          })
        )
        if (!this.isActiveInputRequest(requestId, ruleId)) return
        schemaSucceeded = true
        this.appendWarnings(warnings, schema.diagnostics)
        var schemaFields = schemaFieldsToTestFields(schema.inputs)
        if (schemaFields.length > 0) {
          var schemaValues = flattenSchemaSample(
            schemaFields,
            schema.sampleParams
          )
          var schemaParams = schemaFields.map(function (field) {
            return {
              key: field.fieldName,
              label: field.fieldLabel,
              value: previousValues.has(field.fieldName)
                ? previousValues.get(field.fieldName)
                : schemaValues[field.fieldName],
              type: field.fieldType,
              refType: field.refType || '',
              example: field.exampleValue || '',
              fromVar: true,
              options: field.validValues || [],
            }
          })
          this.commitInputLoad(
            requestId,
            ruleId,
            this.mergeManualParams(schemaParams, manualParams),
            'SCHEMA',
            warnings
          )
          return
        }
        if (Object.keys(schema.sampleParams).length > 0) {
          var flattenedSample = this.flattenCaseParams(schema.sampleParams)
          var sampleParams = Object.keys(flattenedSample).map((key) => ({
            key: key,
            label: '',
            value: previousValues.has(key)
              ? previousValues.get(key)
              : flattenedSample[key],
            type: this.valueType(flattenedSample[key]),
            refType: '',
            example: '',
            fromVar: false,
            options: [],
          }))
          this.commitInputLoad(
            requestId,
            ruleId,
            this.mergeManualParams(sampleParams, manualParams),
            'SCHEMA',
            warnings
          )
          return
        }
      } catch (e) {
        if (!this.isActiveInputRequest(requestId, ruleId)) return
        warnings.push(
          '统一测试结构加载失败：' +
            this.requestErrorMessage(e, '服务暂时不可用')
        )
      }

      try {
        var fields = await this.loadInputFieldsFromServer(ruleId)
        if (!this.isActiveInputRequest(requestId, ruleId)) return
        if (fields.length > 0) {
          var fallbackParams = await this.buildInputFieldParams(
            fields,
            previousValues,
            warnings
          )
          if (!this.isActiveInputRequest(requestId, ruleId)) return
          this.commitInputLoad(
            requestId,
            ruleId,
            this.mergeManualParams(fallbackParams, manualParams),
            'FALLBACK',
            warnings
          )
          return
        }
        this.params = manualParams
        this.inputLoadState = 'EMPTY'
        this.inputLoadSource = 'FALLBACK'
        this.inputLoadWarnings = this.uniqueMessages(warnings)
      } catch (e) {
        if (!this.isActiveInputRequest(requestId, ruleId)) return
        const fallbackMessage = this.requestErrorMessage(
          e,
          '规则输入字段服务暂时不可用'
        )
        if (schemaSucceeded) {
          warnings.push('规则输入字段回退加载失败：' + fallbackMessage)
          this.params = manualParams
          this.inputLoadState = 'EMPTY'
          this.inputLoadSource = 'SCHEMA'
          this.inputLoadWarnings = this.uniqueMessages(warnings)
          return
        }
        this.params = manualParams
        this.inputLoadState = 'ERROR'
        this.inputLoadError = '规则输入字段加载失败：' + fallbackMessage
        this.inputLoadWarnings = this.uniqueMessages(warnings)
      }
    },
    async loadInputFieldsFromServer(ruleId = this.selectedRuleId) {
      var res = await listInputFields(ruleId)
      var fields = this.unwrapResponse(res)
      return Array.isArray(fields)
        ? fields.filter(function (field) {
            return field && (field.scriptName || field.fieldName)
          })
        : []
    },
    async buildInputFieldParams(fields, previousValues, warnings) {
      var params = []
      var seenKeys = new Set()
      for (var i = 0; i < fields.length; i++) {
        var field = fields[i]
        var key = field.scriptName || field.fieldName
        if (!key || seenKeys.has(key)) continue
        var fieldType = await this.resolveInputFieldType(field, warnings)
        var param = {
          key: key,
          label: field.fieldLabel || field.fieldName || key,
          value:
            previousValues && previousValues.has(key)
              ? previousValues.get(key)
              : this.sampleValueForInputField(field, fieldType),
          type: fieldType,
          refType: field.refType || '',
          example: '',
          fromVar: true,
          options: [],
        }
        if (param.type === 'ENUM') {
          try {
            var optionResponse =
              field.refType === 'DATA_OBJECT'
                ? await getDataObjectFieldOptions(field.varId)
                : await getVariableOptions(field.varId)
            param.options = this.unwrapResponse(optionResponse) || []
          } catch (e) {
            warnings.push(
              `${param.label}的枚举选项加载失败，可手工输入后继续测试`
            )
          }
        }
        params.push(param)
        seenKeys.add(key)
      }
      return params
    },
    async applyInputFieldsToParams(fields) {
      var previousParams = this.params.map((param) => ({ ...param }))
      var previousValues = new Map(
        previousParams
          .filter((param) => param.key)
          .map((param) => [param.key, param.value])
      )
      var warnings = []
      var structured = await this.buildInputFieldParams(
        fields,
        previousValues,
        warnings
      )
      this.params = this.mergeManualParams(
        structured,
        previousParams.filter((param) => !param.fromVar)
      )
      this.inputLoadWarnings = this.uniqueMessages([
        ...this.inputLoadWarnings,
        ...warnings,
      ])
      return structured.length
    },
    async resolveInputFieldType(field, warnings = []) {
      var fieldType = field && field.fieldType ? field.fieldType : 'STRING'
      if (!field || field.refType !== 'MODEL' || !field.varId) return fieldType
      try {
        var res = await getModel(field.varId)
        var model = this.unwrapResponse(res)
        var outputs =
          model && Array.isArray(model.outputFields) ? model.outputFields : []
        if (outputs.length === 1 && outputs[0].fieldType) {
          return outputs[0].fieldType
        }
      } catch (e) {
        warnings.push(
          `${field.fieldLabel || field.fieldName || '模型字段'}的输出类型加载失败，暂按${fieldType}测试`
        )
      }
      return fieldType
    },
    sampleValueForInputField(field, fieldType) {
      if (
        field &&
        field.exampleValue !== undefined &&
        field.exampleValue !== null &&
        field.exampleValue !== ''
      ) {
        return field.exampleValue
      }
      if (
        field &&
        field.defaultValue !== undefined &&
        field.defaultValue !== null &&
        field.defaultValue !== ''
      ) {
        return field.defaultValue
      }
      return sampleValueForVarType(
        fieldType || (field && (field.fieldType || field.varType))
      )
    },
    addParam() {
      this.params.push({
        key: '',
        label: '',
        value: '',
        type: 'STRING',
        example: '',
        fromVar: false,
        options: [],
      })
    },
    applyRiskDemoParams() {
      const demoParams = [
        {
          key: 'requestId',
          label: '请求流水号',
          value: 'REQ_DEMO_001',
          type: 'STRING',
        },
        {
          key: 'taxpayerType',
          label: '客商类型',
          value: '一般纳税人',
          type: 'STRING',
        },
        {
          key: 'goodsCategory',
          label: '产品总线',
          value: '货物',
          type: 'STRING',
        },
        {
          key: 'totalAmount',
          label: '交易金额',
          value: 113000,
          type: 'NUMBER',
        },
        { key: 'isExempt', label: '是否减免', value: false, type: 'BOOLEAN' },
        { key: 'annualRevenue', label: '年营收', value: 5000, type: 'NUMBER' },
        {
          key: 'taxComplianceScore',
          label: '合规评分',
          value: 85,
          type: 'NUMBER',
        },
        {
          key: 'yearsInBusiness',
          label: '经营年限',
          value: 10,
          type: 'NUMBER',
        },
        {
          key: 'hasViolation',
          label: '严重违规',
          value: false,
          type: 'BOOLEAN',
        },
        { key: 'creditLevel', label: '信用等级', value: 'A', type: 'STRING' },
        {
          key: 'taxBurdenDeviation',
          label: '指标偏离度',
          value: 0.08,
          type: 'NUMBER',
        },
        {
          key: 'violationCount',
          label: '历史风险事件次数',
          value: 0,
          type: 'NUMBER',
        },
        {
          key: 'serviceType',
          label: '业务类型',
          value: 'ICT服务',
          type: 'ENUM',
        },
        {
          key: 'paymentMode',
          label: '结算方式',
          value: '后付费',
          type: 'ENUM',
        },
        {
          key: 'customerType',
          label: '客户类型',
          value: '企业客户',
          type: 'ENUM',
        },
        {
          key: 'taxpayerQualification',
          label: '纳税人资格',
          value: '一般纳税人',
          type: 'ENUM',
        },
        { key: 'customerLevel', label: '客户等级', value: '金', type: 'ENUM' },
        {
          key: 'monthlyConsumption',
          label: '月消费金额',
          value: 5000,
          type: 'NUMBER',
        },
        {
          key: 'invoiceDeviationRate',
          label: '开票偏差率',
          value: 0.05,
          type: 'NUMBER',
        },
        {
          key: 'redInvoiceRatio',
          label: '红冲发票比例',
          value: 0.02,
          type: 'NUMBER',
        },
        {
          key: 'zeroRateInvoiceRatio',
          label: '零税率发票占比',
          value: 0.01,
          type: 'NUMBER',
        },
        {
          key: 'crossRegionInvoiceRatio',
          label: '跨地区开票比例',
          value: 0.08,
          type: 'NUMBER',
        },
        {
          key: 'billingAmount',
          label: '含税账单金额',
          value: 100000,
          type: 'NUMBER',
        },
        {
          key: 'basicServiceRatio',
          label: '基础通信占比',
          value: 0.6,
          type: 'NUMBER',
        },
        {
          key: 'vasServiceRatio',
          label: '增值业务占比',
          value: 0.4,
          type: 'NUMBER',
        },
      ]
      this.params = demoParams.map((item) => ({
        key: item.key,
        label: item.label,
        value: item.value,
        type: item.type,
        example: String(item.value),
        fromVar: false,
        options: [],
      }))
      this.resetExecutionState()
    },
    async handleExecute() {
      if (!this.selectedRuleId) return
      const ruleId = this.selectedRuleId
      const projectId = this.selectedProjectId
      const requestId = ++this.executionRequestId
      const paramMap = this.buildParamMap()
      this.executing = true
      this.result = null
      this.lastRawResponse = null
      try {
        const request = { definitionId: ruleId, params: paramMap }
        if (projectId) {
          request.projectId = projectId
        }
        const res = await executeRule(
          request,
          this.requestTimeoutMs
        )
        if (!this.isActiveExecution(requestId, ruleId, projectId)) return
        this.lastRawResponse = res
        this.result = normalizeTestResult(res)
        // 执行完成后切换到追踪树标签页
        this.traceTab = 'tree'
      } catch (e) {
        if (!this.isActiveExecution(requestId, ruleId, projectId)) return
        this.lastRawResponse = null
        this.result = {
          success: false,
          errorMessage: e.message || '执行异常',
          executeTimeMs: 0,
        }
      } finally {
        if (this.isActiveExecution(requestId, ruleId, projectId)) {
          this.executing = false
        }
      }
    },
    handleClear() {
      this.params = []
      this.resetExecutionState()
    },
    handleClearParams() {
      this.params = []
    },
    unwrapResponse(res) {
      if (res && Object.prototype.hasOwnProperty.call(res, 'data'))
        return res.data
      return res
    },
    resetInputLoadState() {
      ++this.inputLoadRequestId
      this.inputLoadState = 'IDLE'
      this.inputLoadError = ''
      this.inputLoadSource = ''
      this.inputLoadWarnings = []
    },
    resetExecutionState() {
      ++this.executionRequestId
      this.executing = false
      this.result = null
      this.lastRawResponse = null
    },
    isActiveInputRequest(requestId, ruleId) {
      return (
        requestId === this.inputLoadRequestId &&
        String(ruleId) === String(this.selectedRuleId)
      )
    },
    isActiveRuleContext(contextRequestId, ruleId) {
      return (
        String(ruleId) === String(this.selectedRuleId) &&
        (contextRequestId == null ||
          contextRequestId === this.ruleContextRequestId)
      )
    },
    isActiveTestCaseRequest(requestId, ruleId) {
      return (
        requestId === this.testCasesRequestId &&
        String(ruleId) === String(this.selectedRuleId)
      )
    },
    isActiveBatchExecution(requestId, ruleId) {
      return (
        requestId === this.batchExecutionRequestId &&
        String(ruleId) === String(this.selectedRuleId)
      )
    },
    isActiveExecution(requestId, ruleId, projectId) {
      return (
        requestId === this.executionRequestId &&
        String(ruleId) === String(this.selectedRuleId) &&
        String(projectId || '') === String(this.selectedProjectId || '')
      )
    },
    commitInputLoad(requestId, ruleId, params, source, warnings) {
      if (!this.isActiveInputRequest(requestId, ruleId)) return false
      this.params = params
      this.inputLoadState = params.length ? 'READY' : 'EMPTY'
      this.inputLoadError = ''
      this.inputLoadSource = source
      this.inputLoadWarnings = this.uniqueMessages(warnings)
      return true
    },
    mergeManualParams(structured, manual) {
      const result = structured.map((param) => ({ ...param }))
      const keys = new Set(result.map((param) => param.key).filter(Boolean))
      manual.forEach((param) => {
        if (!param.key || keys.has(param.key)) return
        result.push({ ...param })
        keys.add(param.key)
      })
      return result
    },
    appendWarnings(target, warnings) {
      ;(warnings || []).forEach((warning) => {
        const message =
          typeof warning === 'string'
            ? warning
            : warning && (warning.message || warning.code)
        if (message) target.push(message)
      })
    },
    uniqueMessages(messages) {
      return Array.from(
        new Set((messages || []).filter((message) => String(message).trim()))
      )
    },
    addContextWarning(message) {
      if (message && !this.contextWarnings.includes(message)) {
        this.contextWarnings.push(message)
      }
    },
    requestErrorMessage(error, fallback) {
      const responseMessage =
        error &&
        error.response &&
        error.response.data &&
        error.response.data.message
      const message = responseMessage || (error && error.message)
      return message && String(message).trim() ? String(message) : fallback
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
    notifySuccess(message) {
      if (this.$message && this.$message.success) this.$message.success(message)
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
      return (
        [
          'NUMBER',
          'INTEGER',
          'DOUBLE',
          'FLOAT',
          'DECIMAL',
          'LONG',
          'PROBABILITY',
        ].indexOf(type) >= 0
      )
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
        if (
          !cur[parts[i]] ||
          typeof cur[parts[i]] !== 'object' ||
          Array.isArray(cur[parts[i]])
        ) {
          cur[parts[i]] = {}
        }
        cur = cur[parts[i]]
      }
      cur[parts[parts.length - 1]] = value
    },
    ruleOptionLabel(rule) {
      if (!rule) return ''
      const name = rule.ruleName || ''
      const code = rule.ruleCode ? ' (' + rule.ruleCode + ')' : ''
      if (rule.scope === 'GLOBAL') return '[全局] ' + name + code
      const project =
        this.projects.find(
          (item) => String(item.id) === String(rule.projectId)
        ) || {}
      const identity = [
        rule.projectName || project.projectName,
        rule.projectCode || project.projectCode,
      ].filter((part) => part != null && String(part).trim())
      const prefix = identity.length ? '[' + identity.join(' / ') + '] ' : ''
      return prefix + name + code
    },
    mtl(t) {
      return (
        {
          TABLE: '决策表',
          TREE: '决策树',
          FLOW: '决策流',
          RULE_SET: '规则集',
          CROSS: '交叉表',
          SCORE: '评分卡',
          CROSS_ADV: '复杂交叉表',
          SCORE_ADV: '复杂评分卡',
          SCRIPT: 'QL脚本',
        }[t] || t
      )
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
    },
  },
}
</script>

<style lang="scss" scoped>
.rule-test-page {
  height: 100%;
  min-height: 0;
}
.test-layout {
  display: flex;
  gap: 16px;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}
.test-left {
  flex: 0 0 480px;
  min-width: 380px;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
}
.fixed-cases-card {
  margin-top: 12px;
}
.fixed-cases-card.is-empty {
  padding-bottom: 8px;
}
.fixed-case-empty {
  color: var(--tianshu-text-tertiary);
  font-size: 12px;
  line-height: 20px;
}
.fixed-cases-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.case-state {
  padding: 12px 0;
  color: var(--tianshu-text-tertiary);
  text-align: center;
}
.case-state-error {
  color: var(--tianshu-danger-text);
}
.fixed-case-list {
  max-height: 168px;
  overflow: auto;
}
.fixed-case-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;
  border-bottom: 1px solid var(--tianshu-border-subtle);
}
.batch-result-table {
  margin-top: 12px;
}
.diff-pass {
  color: var(--tianshu-success-text);
}
.diff-lines {
  max-height: 96px;
  overflow: auto;
  color: var(--tianshu-danger-text);
  font-size: 12px;
  line-height: 1.6;
}
.trace-filter-bar {
  display: flex;
  align-items: center;
  flex: 0 0 auto;
  gap: 8px;
  color: var(--tianshu-text-secondary);
  font-size: 12px;
}
.test-right {
  flex: 1;
  min-width: 0;
  min-height: 0;
}
.test-result-card,
.test-result-content,
.trace-section,
.trace-tabs {
  min-height: 0;
}
.test-result-card,
.test-result-content,
.trace-section,
.trace-tabs {
  display: flex;
  flex-direction: column;
}
.test-result-card {
  height: 100%;
  margin-bottom: 0;
  overflow: hidden;
}
.test-result-content,
.trace-section,
.trace-tabs {
  flex: 1;
}
.test-result-content,
.trace-section {
  overflow: hidden;
}
.trace-tabs {
  margin-top: 8px;
}
.trace-tabs :deep(.el-tabs__header) {
  flex: 0 0 auto;
}
.trace-tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
.trace-tabs :deep(.el-tab-pane) {
  height: 100%;
  min-height: 0;
}
.trace-tabs :deep(#pane-json),
.trace-tabs :deep(#pane-tree) {
  overflow: auto;
}
.rule-info {
  margin-top: 12px;
}
.test-setup-state,
.input-load-state {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
  padding: 10px 12px;
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 6px;
  background: var(--tianshu-bg-soft);
}
.test-setup-state strong,
.test-setup-state span,
.input-load-state strong,
.input-load-state span {
  display: block;
}
.test-setup-state strong,
.input-load-state strong {
  color: var(--tianshu-text-primary);
  font-size: 12px;
}
.test-setup-state span,
.input-load-state span {
  margin-top: 3px;
  color: var(--tianshu-text-tertiary);
  font-size: 11px;
  line-height: 1.45;
}
.test-setup-state.is-error,
.input-load-state.is-error {
  border-color: var(--tianshu-danger-border);
  background: var(--tianshu-danger-bg);
}
.test-setup-state.is-error strong,
.input-load-state.is-error strong {
  color: var(--tianshu-danger-text);
}
.input-load-state.is-ready {
  border-color: var(--tianshu-success-border);
  background: var(--tianshu-success-bg);
}
.input-load-state.is-ready strong {
  color: var(--tianshu-success-text);
}
.input-load-state.is-loading {
  border-color: var(--tianshu-info-border);
  background: var(--tianshu-info-bg);
}
.input-load-warnings,
.context-load-warnings {
  margin: 8px 0 0;
  padding: 8px 10px 8px 28px;
  border-radius: 5px;
  background: var(--tianshu-warning-bg);
  color: var(--tianshu-warning-text);
  font-size: 11px;
  line-height: 1.55;
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
  color: var(--tianshu-text-tertiary);
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
  color: var(--tianshu-text-primary);
}
.result-section-title.is-error {
  color: var(--tianshu-danger-text);
}
.result-pre {
  background: var(--tianshu-bg-muted);
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 4px;
  color: var(--tianshu-text-primary);
  padding: 12px;
  font-size: 13px;
  line-height: 1.5;
  overflow: auto;
  max-height: 300px;
  white-space: pre-wrap;
  word-break: break-all;
}
.result-pre.is-error {
  border-color: var(--tianshu-danger-border);
  background: var(--tianshu-danger-bg);
  color: var(--tianshu-danger-text);
}
.trace-tree-wrap {
  min-height: 100%;
  padding: 8px 0;
}
@media screen and (max-width: 1000px) {
  .rule-test-page,
  .test-layout,
  .test-left,
  .test-right,
  .test-result-card,
  .test-result-content,
  .trace-section,
  .trace-tabs {
    height: auto;
  }
  .test-layout {
    flex-direction: column;
    overflow: visible;
  }
  .test-left,
  .test-result-card,
  .test-result-content,
  .trace-section {
    overflow: visible;
  }
  .test-left {
    flex: none;
    min-width: 0;
  }
  .trace-tabs {
    display: block;
    flex: none;
  }
  .trace-tabs :deep(.el-tabs__content) {
    overflow: visible;
  }
  .trace-tabs :deep(#pane-json),
  .trace-tabs :deep(#pane-tree) {
    height: auto;
    overflow: visible;
  }
  .trace-tree-wrap {
    min-height: 0;
  }
}
</style>
