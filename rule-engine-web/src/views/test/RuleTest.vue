<template>
  <div class="uiue-list-page rule-test-page">
    <config-layer-guide
      title="规则测试步骤"
      test-id="rule-test-layers"
      :items="[
        { label: '选择范围和规则', detail: '确定项目与规则版本' },
        { label: '准备输入样例', detail: '加载字段或手工填写参数' },
        { label: '执行并查看追踪', detail: '核对结果、耗时和命中路径' },
      ]"
    />
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
              <project-resource-select
                v-model="selectedProjectId"
                placeholder="请选择项目"
                clearable
                :loading="projectsLoading"
                :disabled="!!contextProjectId"
                style="width: 100%"
                @change="onProjectChange"
               :projects="projects" />
            </el-form-item>
            <el-form-item label="规则">
              <remote-filter-select
                :key="`${ruleScope}:${selectedProjectId}:${contextProjectId}`"
                v-model:value="selectedRuleId"
                :fetch-options="fetchRuleChoicePage"
                :selected-option="selectedRule ? { ...selectedRule, displayName: ruleOptionLabel(selectedRule) } : null"
                option-value-key="id"
                option-label-key="displayName"
                :placeholder="
                  ruleScope === 'PROJECT' && !selectedProjectId
                    ? '请先选择项目'
                    : '请选择规则'
                "
                :disabled="ruleScope === 'PROJECT' && !selectedProjectId"
                :loading="ruleListLoading"
                style="width: 100%"
                @change="onRuleChange"
              />
            </el-form-item>
            <el-form-item v-if="selectedRuleId" label="测试版本">
              <el-select v-model="selectedTestSourceValue" :loading="testSourcesLoading" :disabled="testSourcesLoading" style="width: 100%" data-testid="rule-test-source" @change="onTestSourceChange">
                <el-option v-for="item in testSourceOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-form>
          <el-alert v-if="testSourcesError" :title="testSourcesError" type="error" :closable="false" show-icon>
            <el-button link @click="reloadTestSources">重新加载测试版本</el-button>
          </el-alert>
          <el-alert v-else-if="selectedExecutionSource" :title="executionSourceLabel(selectedExecutionSource, selectedTestSourceLabel || inputExecutionIdentity)" type="info" :closable="false" show-icon />
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
              <el-descriptions-item label="测试版本"
                >{{ selectedTestSourceLabel || '尚未选择' }}</el-descriptions-item
              >
              <el-descriptions-item label="规则上线状态">
                <el-tag
                  :type="
                    { 0: 'info', 1: 'success', 2: 'warning' }[
                      selectedRule.status
                    ]
                  "
                  size="small"
                >
                  {{ ['未发布', '已发布', '已下线'][selectedRule.status] }}
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
          :inert="testCaseLoading"
        >
          <div class="uiue-card-title input-parameters-title">
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
              v-if="inputMode === 'form'"
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
          <div class="input-mode-toolbar">
            <el-radio-group :model-value="inputMode" size="small" aria-label="输入方式" @update:model-value="switchInputMode">
              <el-radio-button value="form">表单填写</el-radio-button>
              <el-radio-button value="json">JSON 编辑</el-radio-button>
            </el-radio-group>
            <el-button link size="small" :disabled="inputSampleJson === null || inputLoadState === 'LOADING' || inputLoadState === 'ERROR'" @click="generateInputSample">生成样例</el-button>
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
          <div v-if="inputMode === 'json'" class="input-json-editor" data-testid="rule-test-input-json">
            <monaco-editor :key="inputEditorKey" v-model:value="paramsJson" language="json" height="300px" />
            <div v-if="inputJsonError" class="input-json-error" role="alert">{{ inputJsonError }}</div>
          </div>
          <div
            v-else-if="params.length === 0 && (selectedRule ? inputLoadState === 'EMPTY' : inputLoadState === 'IDLE')"
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

        <div v-if="selectedRule" class="uiue-card">
          <expected-output-editor
            ref="expectationEditor"
            v-model="expectationJson"
            v-model:snapshot-json="expectedSnapshotJson"
            :outputs="testOutputFields"
            :actual-response-json="lastRawResponseJson"
          />
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
              <el-button link size="small" @click="reportsVisible = true">回归报告</el-button>
              <el-button v-permission="'rule:edit'" link size="small" :disabled="testCaseLoading" @click="saveCurrentTestCase"
                ><el-icon><el-icon-star-off /></el-icon>
                保存为新用例</el-button
              >
              <el-button v-if="loadedTestCase" v-permission="'rule:edit'" link size="small" :disabled="testCaseLoading" :loading="testCaseSaving" @click="saveCurrentTestCase('UPDATE')">保存当前用例</el-button>
              <el-button
                link
                size="small"
                :loading="batchExecuting"
                :disabled="selectedTestCaseIds.length === 0"
                @click="executeSelectedTestCases"
                v-permission="'rule:edit'"
                ><el-icon><el-icon-video-play /></el-icon> 批量执行</el-button
              >
              <el-button v-if="batchExecuting" link size="small" :disabled="batchStopRequested" @click="stopBatchExecution">停止后续</el-button>
            </span>
          </div>
          <p v-if="batchTotal" role="status">
            已完成 {{ batchResults.length }} / {{ batchTotal }} 个用例。
            {{ batchStopRequested ? '已停止后续用例；已提交的请求会继续完成并保留结果。' : batchExecuting ? '正在执行当前用例。' : '批量执行结束。' }}
          </p>
          <p class="fixed-case-empty">已有启用固定用例时，发布前须全部通过。请选择已保存的草稿执行回归；临时修改的用例输入或预期不计入发布证据。</p>
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
                  :type="row.executionStatus === 'THROTTLED' ? 'warning' : row.success ? 'success' : 'danger'"
                  size="small"
                  >{{ row.executionStatus === 'THROTTLED' ? executionStatusLabel(row.executionStatus) : row.success ? '成功' : '失败' }}</el-tag
                ></template
              >
            </el-table-column>
            <el-table-column label="预期校验" min-width="110">
              <template #default="{ row }"><el-tag :type="validationTagType(row.validationStatus)">{{ validationLabel(row.validationStatus) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="校验详情" min-width="220">
              <template v-slot="{ row }">
                <span>{{ row.validationMessage }}</span>
                <div v-if="row.diffs.length" class="diff-lines">
                  <div v-for="(diff, index) in row.diffs" :key="index">
                    <code>{{ diff.path }}</code
                    >：{{ diff.expectedMissing ? '字段不存在' : diff.expectedJson }} →
                    {{ diff.actualMissing ? '字段不存在' : diff.actualJson }}
                  </div>
                </div>
                <div v-for="(check, index) in row.checks" :key="index">{{ check.label }}：{{ check.message }}<span v-if="check.status === 'FAILED'">；预期 {{ check.expectedJson }}，实际 {{ check.actualMissing ? '字段不存在' : check.actualJson }}</span></div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="test-actions">
          <span class="test-timeout-label">页面请求超时</span>
          <el-input-number
            v-model="requestTimeoutMs"
            :min="1000"
            :max="1800000"
            :step="1000"
            size="small"
            class="test-timeout-input"
          />
          <span class="test-timeout-unit">毫秒</span>
          <el-button
            type="primary"
            :loading="executing"
            :disabled="!selectedRuleId || testCaseLoading || testSourcesLoading || !!testSourcesError || (!!selectedExecutionSource && inputLoadState === 'ERROR')"
            @click="handleExecute"
            v-permission="'rule:edit'"
          >
            <el-icon><el-icon-video-play /></el-icon> 执行测试
          </el-button>
          <el-button :disabled="testCaseLoading" @click="handleClear">清空</el-button>
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
              :title="resultThrottled ? executionStatusLabel(result.executionStatus) : result.success ? '执行成功' : '执行失败'"
              :type="resultThrottled ? 'warning' : result.success ? 'success' : 'error'"
              :closable="false"
              show-icon
              style="margin-bottom: 16px"
            >
              <span v-if="resultThrottled">{{ executionStatusHint(result.executionStatus) }} </span>
              <span>耗时 {{ result.executeTimeMs }} ms</span>
            </el-alert>

            <div class="test-validation-result" data-testid="test-validation-result">
              <el-tag :type="validationStale ? 'info' : validationTagType(testValidation.status)">{{ validationStale ? '待重新校验' : validationLabel(testValidation.status) }}</el-tag>
              <span>{{ validationStale ? '输入或预期已修改，请重新执行后查看校验结果' : testValidation.message }}</span>
              <template v-if="!validationStale">
                <div v-for="(check, index) in testValidation.checks || []" :key="index">{{ check.label }}：{{ check.message }}<span v-if="check.status === 'FAILED'">；预期 {{ check.expectedJson }}，实际 {{ check.actualMissing ? '字段不存在' : check.actualJson }}</span></div>
                <div v-for="(diff, index) in testValidation.diffs || []" :key="index"><code>{{ diff.path }}</code>：{{ diff.expectedMissing ? '字段不存在' : diff.expectedJson }} → {{ diff.actualMissing ? '字段不存在' : diff.actualJson }}</div>
              </template>
            </div>

            <div v-if="result.errorMessage" style="margin-bottom: 16px">
              <div class="result-section-title" :class="resultThrottled ? 'is-throttled' : 'is-error'">{{ resultThrottled ? '容量提示' : '错误信息' }}</div>
              <pre class="result-pre" :class="resultThrottled ? 'is-throttled' : 'is-error'">{{ result.errorMessage }}</pre>
            </div>

            <div style="margin-bottom: 16px">
              <div class="result-section-title">返回结果</div>
              <pre class="result-pre">{{ exactOutputDisplay }}</pre>
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
                  <el-option :label="executionStatusLabel('THROTTLED')" value="THROTTLED" />
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
    <regression-report-drawer v-if="reportsVisible && selectedRuleId" :definition-id="selectedRuleId" :revision-id="inputExecutionIdentity?.revisionId || null" @close="reportsVisible = false" />
  </div>
</template>

<script>
import ElIconLoading from '@/components/common/AppLoadingIcon.vue'
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'
import ProjectResourceSelect from '@/components/ProjectResourceSelect.vue'
import RegressionReportDrawer from '@/components/rule/RegressionReportDrawer.vue'
import { validationLabel, validationTagType } from '@/utils/testValidationStatus'
import {
  Star as ElIconStarOff,
  VideoPlay as ElIconVideoPlay,
  Refresh as ElIconRefresh,
  Plus as ElIconPlus,
  MagicStick as ElIconMagicStick,
  Delete as ElIconDelete,
} from '@element-plus/icons-vue'
import { listProjects } from '@/api/project'
import {
  listDefinitions,
  listProjectDefinitions,
  getContent,
  listRuleRevisions,
  listPublishedVersions,
  getRuleRevision,
  getVersionById,
  listInputFields,
  getRuleTestSchema,
  listApiScenarios,
  createApiScenario,
  updateApiScenario,
  executeApiScenarioValidated,
} from '@/api/definition'
import { getVariableOptions } from '@/api/variable'
import { getDataObjectFieldOptions } from '@/api/dataObject'
import { listAllFunctionsByProject } from '@/api/function'
import { getModel } from '@/api/model'
import TraceTree from '@/components/common/TraceTree.vue'
import AsyncState from '@/components/common/AsyncState.vue'
import ConfigLayerGuide from '@/components/common/ConfigLayerGuide.vue'
import ExpectedOutputEditor from '@/components/test/ExpectedOutputEditor.vue'
import MonacoEditor from '@/components/MonacoEditor'
import { cloneExactJson, createExactNumber, exactNumberText, isExactNumber, parseExactJson, parseExecutionSnapshotJson, stringifyExactJson } from '@/utils/exactJson'
import { sampleValueForVarType } from '@/utils/testParamTemplate'
import { normalizeTestResult } from '@/utils/testResult'
import { executionStatusLabel, executionStatusHint } from '@/utils/executionStatus'
import { revisionTestSource, executionSourceLabel, requireExecutionIdentity } from '@/utils/ruleTestExecutionSource'
import { routeProjectId } from '@/utils/projectContext'
import {
  normalizeTestSchema,
  schemaFieldsToTestFields,
  flattenSchemaSample,
  buildNestedSchemaParams,
} from '@/utils/testSchema'
import {
  filterTraceTree,
  scenarioParams,
} from '@/utils/testCaseTools'

export default {
  components: { ProjectResourceSelect, RemoteFilterSelect, RegressionReportDrawer,
    TraceTree,
    AsyncState,
    ConfigLayerGuide,
    ExpectedOutputEditor,
    MonacoEditor,
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
      reportsVisible: false,
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
      testSourceOptions: [],
      selectedTestSourceValue: '',
      testSourcesLoading: false,
      testSourcesError: '',
      testSourcesRequestId: 0,
      params: [],
      inputMode: 'form',
      paramsJson: '{}',
      inputEditorKey: 0,
      inputSampleJson: null,
      inputLoadState: 'IDLE',
      inputLoadError: '',
      inputLoadSource: '',
      inputLoadWarnings: [],
      inputLoadRequestId: 0,
      inputExecutionIdentity: null,
      ruleContextRequestId: 0,
      executionRequestId: 0,
      contextWarnings: [],
      executing: false,
      result: null,
      lastRawResponse: null,
      lastRawResponseJson: '',
      expectationJson: '{"version":1,"mode":"NONE","checks":[]}',
      expectedSnapshotJson: '',
      executedExpectationJson: null,
      executedExpectedSnapshotJson: '',
      testOutputFields: [],
      testValidation: { status: 'NOT_EVALUATED', message: '执行后查看预期校验', checks: [], diffs: [] },
      loadedTestCase: null,
      loadedRequestJson: '',
      loadedInputParamsJson: '',
      testCaseEditSession: 0,
      testCaseSaving: false,
      testCaseLoading: false,
      executedInputParamsJson: '',
      traceTab: 'tree',
      traceStatusFilter: 'ALL',
      traceKeyword: '',
      testCases: [],
      selectedTestCaseIds: [],
      testCasesLoading: false,
      testCasesError: '',
      testCasesRequestId: 0,
      batchExecuting: false,
      batchTotal: 0,
      batchStopRequested: false,
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
  beforeUnmount() {
    this.testSourcesRequestId++
    this.ruleContextRequestId++
    this.inputLoadRequestId++
    this.executionRequestId++
    this.batchExecutionRequestId++
    this.testCasesRequestId++
    this.testCaseEditSession++
    this.projectsRequestId++
    this.ruleListRequestId++
  },
  computed: {
    inputJsonError() {
      if (this.inputMode !== 'json') return ''
      try { this.parseInputJson(); return '' } catch (error) { return error.message }
    },
    selectedExecutionSource() {
      return this.testSourceOptions.find(item => item.value === this.selectedTestSourceValue)?.source || null
    },
    selectedTestSourceLabel() {
      return this.testSourceOptions.find(item => item.value === this.selectedTestSourceValue)?.label || ''
    },
    resultThrottled() {
      return Boolean(this.result && this.result.executionStatus === 'THROTTLED')
    },
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
      // 执行结果必须绑定执行时快照；用户在结果页编辑输入不应改写历史追踪。
      if (this.executedInputParamsJson) return this.executedInputParamsJson
      try { return stringifyExactJson(this.buildParamMap()) } catch { return '' }
    },
    outputResultJson: function () {
      if (!this.result || !this.result.hasOutput) return ''
      return this.rawOutputJson() || JSON.stringify(this.result.output)
    },
    exactOutputDisplay() { return this.rawOutputJson(2) || this.formatJson(this.result && this.result.output) },
    validationStale() {
      if (!this.result) return false
      try {
        return stringifyExactJson(this.buildParamMap()) !== this.executedInputParamsJson ||
          this.expectationJson !== this.executedExpectationJson || this.expectedSnapshotJson !== this.executedExpectedSnapshotJson
      } catch { return true }
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
    executionSourceLabel,
    resetTestSources() {
      this.testSourcesRequestId++
      this.testSourceOptions = []
      this.selectedTestSourceValue = ''
      this.testSourcesLoading = false
      this.testSourcesError = ''
    },
    async loadExecutionSources() {
      const ruleId = this.selectedRuleId, requestId = ++this.testSourcesRequestId
      this.resetExecutionState()
      this.resetInputLoadState()
      this.batchExecutionRequestId++
      this.batchExecuting = false
      this.batchResults = []
      this.testSourcesLoading = true
      this.testSourcesError = ''
      this.testSourceOptions = []
      this.selectedTestSourceValue = ''
      if (!ruleId) { this.testSourcesLoading = false; return false }
      try {
        const [revisionResponse, versionResponse] = await Promise.all([listRuleRevisions(ruleId), listPublishedVersions(ruleId)])
        if (requestId !== this.testSourcesRequestId || String(ruleId) !== String(this.selectedRuleId)) return false
        const revisions = this.unwrapResponse(revisionResponse), versions = this.unwrapResponse(versionResponse)
        if (!Array.isArray(revisions) || !Array.isArray(versions)) throw new Error('测试版本列表响应不完整，请重新加载')
        const ordered = (Array.isArray(revisions) ? [...revisions] : []).filter(item => item.state !== 'DELETED')
          .sort((left, right) => (right.revisionNo || 0) - (left.revisionNo || 0))
        const revisionOptions = ordered.map(item => ({
          value: `REVISION:${item.id}`, source: revisionTestSource(item),
          label: `${item.state === 'DRAFT' ? '草稿' : '修订'} ${item.revisionNo || item.id} · ${{ DRAFT: '未发布', REVIEW: '评审中', APPROVED: '已批准', PUBLISHED: '已发布', OFFLINE: '已下线', REJECTED: '已驳回' }[item.state] || item.state}`,
        })).filter(item => item.source)
        const versionOptions = (Array.isArray(versions) ? versions : []).map(item => ({
          value: `VERSION:${item.id}`, source: { sourceType: 'VERSION', sourceId: String(item.id) },
          label: `发布版本 v${item.version}${item.generation > 1 ? ` · 第 ${item.generation} 次发布` : ''}${item.available === false ? ' · 缺少可验证制品' : ''}`,
        }))
        const businessOptions = versions.filter(item => item.bindingId != null && item.available !== false).map(item => ({
          value: `BUSINESS_VERSION:${item.bindingId}`, source: { sourceType: 'BUSINESS_VERSION', sourceId: String(item.bindingId) }, label: `指定业务版本 v${item.version}（跟随覆盖发布）`,
        }))
        this.testSourceOptions = [...(businessOptions.length ? [{ value: 'LATEST', source: { sourceType: 'LATEST' }, label: '最新版（当前生产）' }] : []),
          ...businessOptions, ...revisionOptions.filter(item => item.source.lockVersion != null), ...versionOptions,
          ...revisionOptions.filter(item => item.source.lockVersion == null)]
        if (!this.testSourceOptions.length) this.testSourceOptions = [{ value: 'WORKSPACE', source: { sourceType: 'WORKSPACE' }, label: '旧版工作区（没有修订记录）' }]
        this.selectedTestSourceValue = this.testSourceOptions[0].value
        return true
      } catch (error) {
        if (requestId === this.testSourcesRequestId) this.testSourcesError = this.requestErrorMessage(error, '测试版本读取失败，请重试')
        return false
      } finally {
        if (requestId === this.testSourcesRequestId) this.testSourcesLoading = false
      }
    },
    async reloadTestSources() {
      if (await this.loadExecutionSources()) await this.onTestSourceChange()
    },
    async onTestSourceChange() {
      const contextRequestId = ++this.ruleContextRequestId
      this.resetExecutionState()
      this.resetInputLoadState()
      this.batchExecutionRequestId++
      this.batchExecuting = false
      this.batchResults = []
      await Promise.all([this.loadModelJson(contextRequestId), this.loadFunctionNameMap(contextRequestId), this.loadVarMap(contextRequestId), this.loadVariables()])
    },
    executionStatusLabel,
    executionStatusHint,
    validationLabel, validationTagType,
    resetExpectationContext() {
      this.testCaseEditSession++
      this.testCaseSaving = false
      this.testCaseLoading = false
      this.expectationJson = '{"version":1,"mode":"NONE","checks":[]}'
      this.expectedSnapshotJson = ''
      this.testOutputFields = []
      this.loadedTestCase = null
      this.loadedRequestJson = ''
      this.loadedInputParamsJson = ''
    },
    unwrapValidatedResponse(response) {
      const payload = this.unwrapResponse(response)
      if (!payload || !payload.execution || typeof payload.responseJson !== 'string') throw new Error('测试服务未返回有效的执行结果')
      parseExecutionSnapshotJson(payload.responseJson)
      const allowed = ['PASSED', 'FAILED', 'NOT_CONFIGURED', 'INVALID', 'NOT_EVALUATED']
      const validation = payload.validation && allowed.includes(payload.validation.status)
        ? { ...payload.validation, checks: payload.validation.checks || [], diffs: payload.validation.diffs || [] }
        : { status: 'NOT_EVALUATED', message: '服务端未返回可识别的预期校验结果', checks: [], diffs: [] }
      return { ...payload, validation }
    },
    rawOutputJson(space = 0) {
      if (!this.lastRawResponseJson) return ''
      try {
        const root = parseExecutionSnapshotJson(this.lastRawResponseJson)
        const value = root && root.data && typeof root.data === 'object' ? root.data : root
        for (const key of ['result', 'outputs', 'value', 'resolvedValue']) if (value && Object.hasOwn(value, key)) return stringifyExactJson(value[key], space)
      } catch { /* 原始快照由执行响应校验；无可用快照时沿用原展示。 */ }
      return ''
    },
    buildRequestJson() {
      const params = this.buildParamMap()
      const paramsJson = stringifyExactJson(params)
      if (this.loadedRequestJson && paramsJson === this.loadedInputParamsJson) return this.loadedRequestJson
      const request = this.loadedRequestJson ? parseExactJson(this.loadedRequestJson) : { clientAppName: 'rule-test' }
      Object.defineProperty(request, 'params', { value: params, writable: true, configurable: true, enumerable: true })
      return stringifyExactJson(request)
    },
    async applyProjectContext(projectId) {
      this.contextProjectId = Number(projectId)
      this.ruleScope = 'PROJECT'
      this.selectedProjectId = Number(projectId)
      await this.onProjectChange()
    },
    async loadRulesByScope() {
      if (this.ruleScope === 'ALL') {
        await this.loadRuleOptions(() =>
          listDefinitions({ pageNum: 1, pageSize: 20 })
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
        const res = await listProjects({ pageNum: 1, pageSize: 20 })
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
      if (this.selectedExecutionSource && this.selectedExecutionSource.sourceType !== 'WORKSPACE') return
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
      if (this.selectedExecutionSource && this.selectedExecutionSource.sourceType !== 'WORKSPACE') return
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
        const source = this.selectedExecutionSource
        var r = source?.sourceType === 'VERSION' ? await getVersionById(ruleId, source.sourceId)
          : source?.sourceType === 'REVISION' ? await getRuleRevision(ruleId, source.sourceId) : await getContent(ruleId)
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
      this.resetTestSources()
      ++this.ruleContextRequestId
      this.selectedRuleId = null
      this.selectedRule = null
      this.rules = []
      this.params = []
      this.updateInputJson('{}')
      this.resetExecutionState()
      this.resetExpectationContext()
      this.contextWarnings = []
      this.resetInputLoadState()
      this.resetTestCases()
      this.selectedProjectId = this.contextProjectId

      await this.loadRulesByScope()
    },
    async loadGlobalRules() {
      const params = {
        pageNum: 1,
        pageSize: 20,
        scope: 'GLOBAL',
      }
      return this.loadRuleOptions(() =>
        this.contextProjectId
          ? listProjectDefinitions(this.contextProjectId, params)
          : listDefinitions(params)
      )
    },
    async onProjectChange() {
      this.resetTestSources()
      ++this.ruleContextRequestId
      this.selectedRuleId = null
      this.selectedRule = null
      this.rules = []
      this.params = []
      this.updateInputJson('{}')
      this.resetExecutionState()
      this.resetExpectationContext()
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
        this.rules.find((r) => String(r.id) === String(this.selectedRuleId)) || null
      this.params = []
      this.updateInputJson('{}')
      this.resetExecutionState()
      this.resetExpectationContext()
      this.contextWarnings = []
      this.resetInputLoadState()
      if (!await this.loadExecutionSources() || !this.isActiveRuleContext(contextRequestId, this.selectedRuleId)) return
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
          pageSize: 20,
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
    async fetchRuleChoicePage({ query, pageNum, pageSize }) {
      const requestId = ++this.ruleListRequestId
      this.ruleListError = ''
      const params = { pageNum, pageSize, keyword: query || '', ...(this.ruleScope === 'GLOBAL' ? { scope: 'GLOBAL' } : {}) }
      const projectId = this.ruleScope === 'PROJECT' ? this.selectedProjectId : this.ruleScope === 'GLOBAL' ? this.contextProjectId : null
      try {
        const response = projectId ? await listProjectDefinitions(projectId, params) : await listDefinitions(params)
        if (requestId !== this.ruleListRequestId) return { data: { records: [], total: 0 } }
        const data = response.data || {}
        const records = data.records || []
        const known = new Map(this.rules.map(item => [String(item.id), item]))
        records.forEach(item => known.set(String(item.id), item))
        this.rules = [...known.values()]
        return { data: { ...data, records: records.map(item => ({ ...item, displayName: this.ruleOptionLabel(item) })) } }
      } catch (error) {
        if (requestId === this.ruleListRequestId) this.ruleListError = this.requestErrorMessage(error, '规则读取失败，请重试')
        throw error
      } finally {
        if (requestId === this.ruleListRequestId) this.ruleListLoading = false
      }
    },
    retryRuleOptions() {
      return this.loadRulesByScope()
    },
    resetTestCases() {
      this.reportsVisible = false
      ++this.testCasesRequestId
      ++this.batchExecutionRequestId
      this.testCases = []
      this.selectedTestCaseIds = []
      this.batchResults = []
      this.testCasesError = ''
      this.batchTotal = 0
      this.batchStopRequested = false
      this.testCasesLoading = false
      this.batchExecuting = false
    },
    async loadTestCases({ preserveSelection = false } = {}) {
      const selected = new Set(preserveSelection ? this.selectedTestCaseIds.map(String) : [])
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
        this.selectedTestCaseIds = this.testCases.filter(item => selected.has(String(item.id))).map(item => item.id)
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
    async saveCurrentTestCase(mode = 'NEW') {
      if (!this.selectedRuleId || this.testCaseSaving || this.testCaseLoading) return
      const ruleId = this.selectedRuleId
      const contextRequestId = this.ruleContextRequestId
      const session = this.testCaseEditSession
      const editorCheck = this.$refs.expectationEditor?.validate?.()
      if (editorCheck && !editorCheck.valid) { this.notifyWarning(editorCheck.errors[0]); return }
      let paramsJson, requestJson, expectationMode
      const config = this.expectationJson
      try {
        paramsJson = stringifyExactJson(this.buildParamMap()); requestJson = this.buildRequestJson()
        expectationMode = config == null ? 'SNAPSHOT' : parseExactJson(config).mode
      }
      catch (error) { this.notifyWarning(error.message); return }
      const executedResponse = this.lastRawResponse
      if (executedResponse && paramsJson !== this.executedInputParamsJson) {
        this.notifyWarning('输入参数已修改，请重新执行后再收藏输入与结果')
        return
      }
      const responseJson = expectationMode === 'SNAPSHOT' ? this.expectedSnapshotJson || '{}'
        : this.lastRawResponseJson || (this.loadedInputParamsJson === paramsJson ? this.loadedTestCase?.responseJson : null) || '{}'
      const existing = mode === 'UPDATE' ? this.loadedTestCase : null
      const saveRequest = {
        scenarioName: existing?.scenarioName || '',
        description: existing?.description || '规则测试页保存的固定测试用例',
        requestJson, responseJson, expectationJson: config,
        responseSource: executedResponse && responseJson === this.lastRawResponseJson ? 'EXECUTED' : 'MANUAL',
        businessCodePath: existing?.businessCodePath || '', includeInDoc: existing?.includeInDoc || 0,
        sortOrder: existing?.sortOrder, status: existing?.status ?? 1,
      }
      this.testCaseSaving = true
      try {
        if (!existing) {
          const promptResult = await this.$prompt('请输入固定用例名称', '收藏测试用例', {
            inputValidator: (value) => Boolean(value && value.trim()), inputErrorMessage: '用例名称不能为空',
          })
          saveRequest.scenarioName = promptResult.value.trim()
        }
        if (!this.isActiveRuleContext(contextRequestId, ruleId) || session !== this.testCaseEditSession) return
        const response = existing ? await updateApiScenario(ruleId, existing.id, saveRequest) : await createApiScenario(ruleId, saveRequest)
        if (!this.isActiveRuleContext(contextRequestId, ruleId) || session !== this.testCaseEditSession) return
        const saved = this.unwrapResponse(response)
        if (saved?.id) this.loadedTestCase = { ...saveRequest, ...saved }
        this.loadedRequestJson = requestJson
        this.loadedInputParamsJson = paramsJson
        await this.loadTestCases({ preserveSelection: true })
        this.notifySuccess(existing ? '测试用例已更新' : '固定测试用例已收藏')
      } catch (error) {
        if (error && typeof error === 'object' && error.message) this.notifyWarning(error.message)
      } finally {
        if (session === this.testCaseEditSession) this.testCaseSaving = false
      }
    },
    async applyTestCase(testCase) {
      const ruleId = this.selectedRuleId
      const session = ++this.testCaseEditSession
      this.testCaseSaving = false
      const values = scenarioParams(testCase && testCase.requestJson)
      this.testCaseLoading = true
      this.expectationJson = testCase.expectationJson ?? null
      this.expectedSnapshotJson = testCase.responseJson || ''
      this.resetExecutionState()
      try {
        const loading = this.loadVariables()
        const inputRequestId = this.inputLoadRequestId
        await loading
        if (!this.isActiveInputRequest(inputRequestId, ruleId) || session !== this.testCaseEditSession) return
        this.replaceParamsFromValues(values)
        this.updateInputJson(stringifyExactJson(values, 2))
        this.loadedTestCase = { ...testCase }
        this.loadedRequestJson = testCase.requestJson
        this.loadedInputParamsJson = stringifyExactJson(values)
      } finally {
        if (session === this.testCaseEditSession) this.testCaseLoading = false
      }
    },
    async executeSelectedTestCases() {
      if (this.batchExecuting || this.testSourcesLoading || this.testSourcesError || (this.selectedExecutionSource && this.inputLoadState === 'ERROR')) return
      const selected = this.testCases.filter(
        (testCase) => this.selectedTestCaseIds.indexOf(testCase.id) >= 0
      ).map(testCase => ({ ...testCase }))
      if (selected.length === 0) return
      const ruleId = this.selectedRuleId
      const projectId = this.selectedProjectId
      const source = this.selectedExecutionSource ? { ...this.selectedExecutionSource } : null
      const requestId = ++this.batchExecutionRequestId
      this.batchExecuting = true
      this.batchTotal = selected.length
      this.batchStopRequested = false
      this.batchResults = []
      try {
        for (const testCase of selected) {
          if (this.batchStopRequested || !this.isActiveBatchExecution(requestId, ruleId)) break
          try {
            const actual = await executeApiScenarioValidated(
              ruleId,
              { ...(source ? { executionSource: source } : {}), scenarioId: testCase.id, projectId: projectId || undefined, requestJson: testCase.requestJson || '{}',
                expectationJson: testCase.expectationJson ?? '{"version":1,"mode":"SNAPSHOT"}', expectedResponseJson: testCase.responseJson || '{}' },
              this.requestTimeoutMs
            )
            if (!this.isActiveBatchExecution(requestId, ruleId)) return
            const payload = this.unwrapValidatedResponse(actual)
            requireExecutionIdentity(payload.execution.executionIdentity, ruleId, source)
            const normalized = normalizeTestResult(payload.execution)
            this.batchResults.push({
              reportId: payload.reportId,
              scenarioName: testCase.scenarioName,
              executionIdentity: payload.execution.executionIdentity,
              success: normalized.success === true,
              executionStatus: normalized.executionStatus || '',
              validationStatus: payload.validation.status, validationMessage: payload.validation.message,
              checks: payload.validation.checks, diffs: payload.validation.diffs,
            })
          } catch (e) {
            if (!this.isActiveBatchExecution(requestId, ruleId)) return
            this.batchResults.push({
              scenarioName: testCase.scenarioName,
              success: false,
              validationStatus: 'NOT_EVALUATED', validationMessage: e.message || '执行请求失败，未进行预期校验', checks: [], diffs: [],
            })
          }
        }
      } finally {
        if (this.isActiveBatchExecution(requestId, ruleId)) {
          this.batchExecuting = false
        }
      }
    },
    stopBatchExecution() { this.batchStopRequested = true },
    flattenCaseParams(value, prefix, target) {
      const output = target || Object.create(null)
      const currentPrefix = prefix || ''
      Object.keys(value || {}).forEach((key) => {
        const path = currentPrefix ? currentPrefix + '.' + key : key
        const item = value[key]
        if (item && typeof item === 'object' && !isExactNumber(item) && !Array.isArray(item) && Object.keys(item).length > 0)
          this.flattenCaseParams(item, path, output)
        else output[path] = item
      })
      return output
    },
    valueType(value) {
      if (typeof value === 'boolean') return 'BOOLEAN'
      if (typeof value === 'number' || isExactNumber(value)) return 'NUMBER'
      if (Array.isArray(value)) return 'LIST'
      if (value && typeof value === 'object') return 'OBJECT'
      return 'STRING'
    },
    caseDisplayValue(value) {
      if (isExactNumber(value)) return exactNumberText(value)
      return value && typeof value === 'object' ? stringifyExactJson(value) : value
    },
    flattenCaseParamEntries(value, path = [], entries = []) {
      for (const key of Object.keys(value || {})) {
        const item = value[key], segments = [...path, key]
        if (item && typeof item === 'object' && !isExactNumber(item) && !Array.isArray(item) && Object.keys(item).length) this.flattenCaseParamEntries(item, segments, entries)
        else entries.push({ key: segments.join('.'), value: item, path: segments })
      }
      return entries
    },
    async loadVariables() {
      const ruleId = this.selectedRuleId
      const jsonAtStart = this.paramsJson
      const validJson = !this.inputJsonError
      if (this.inputMode === 'json' && validJson) this.replaceParamsFromValues(this.parseInputJson())
      const loading = this.loadInputVariables()
      const requestId = this.inputLoadRequestId
      await loading
      if (!this.isActiveInputRequest(requestId, ruleId)) return
      if (this.inputMode === 'json' && validJson && this.paramsJson === jsonAtStart && this.inputLoadState !== 'ERROR') {
        this.updateInputJson(stringifyExactJson(this.buildFormParamMap(), 2))
      }
    },
    async loadInputVariables() {
      if (!this.selectedRule || !this.selectedRuleId) {
        this.resetInputLoadState()
        return
      }
      const ruleId = this.selectedRuleId
      const requestId = ++this.inputLoadRequestId
      const source = this.selectedExecutionSource ? { ...this.selectedExecutionSource } : null
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
            ...(source ? { executionSource: source } : {}),
            ...(source && this.selectedProjectId ? { projectId: this.selectedProjectId } : {}),
          })
        )
        if (!this.isActiveInputRequest(requestId, ruleId)) return
        if (source?.sourceType !== 'WORKSPACE' || schema.executionIdentity) requireExecutionIdentity(schema.executionIdentity, ruleId, source)
        this.inputExecutionIdentity = schema.executionIdentity || null
        schemaSucceeded = true
        if (source && source.sourceType !== 'WORKSPACE') this.varMap = Object.fromEntries(schema.inputs.map(field => [field.scriptName || field.code, field.label || field.code || field.scriptName]))
        this.testOutputFields = schema.outputs || []
        if (schema.sampleParamsJson) schema.sampleParams = parseExactJson(schema.sampleParamsJson)
        this.appendWarnings(warnings, schema.diagnostics)
        var schemaFields = schemaFieldsToTestFields(schema.inputs)
        // 与规则详情测试弹窗共用 Schema、字段转换和嵌套样例生成逻辑。
        this.inputSampleJson = stringifyExactJson(Object.keys(schema.sampleParams).length
          ? schema.sampleParams
          : buildNestedSchemaParams(schemaFields, flattenSchemaSample(schemaFields, schema.sampleParams)), 2)
        if (schemaFields.length > 0) {
          var schemaValues = flattenSchemaSample(
            schemaFields,
            schema.sampleParams
          )
          var schemaParams = schemaFields.map((field) => {
            return {
              key: field.fieldName,
              label: field.fieldLabel,
              value: previousValues.has(field.fieldName)
                ? previousValues.get(field.fieldName)
                : this.caseDisplayValue(schemaValues[field.fieldName]),
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
              : this.caseDisplayValue(flattenedSample[key]),
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
        if (source && source.sourceType !== 'WORKSPACE') {
          this.commitInputLoad(requestId, ruleId, manualParams, 'SCHEMA', warnings)
          return
        }
      } catch (e) {
        if (!this.isActiveInputRequest(requestId, ruleId)) return
        if (source && source.sourceType !== 'WORKSPACE') {
          this.inputLoadState = 'ERROR'
          this.inputLoadError = this.requestErrorMessage(e, '所选版本的测试字段读取失败')
          return
        }
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
          this.inputSampleJson = stringifyExactJson(this.buildFormParamMap(fallbackParams.map((param) => ({
            ...param, value: this.sampleValueForInputField(fields.find(field => (field.scriptName || field.fieldName) === param.key), param.type),
          }))), 2)
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
    parseInputJson() {
      const value = parseExactJson(this.paramsJson)
      if (!value || typeof value !== 'object' || Array.isArray(value) || isExactNumber(value)) throw new Error('输入参数必须是 JSON 对象')
      return value
    },
    updateInputJson(value) {
      this.paramsJson = value
      // 与测试弹窗一样，主动替换内容时重建编辑器，避免沿用上次输入事件的编辑状态。
      this.inputEditorKey++
    },
    replaceParamsFromValues(values) {
      const existing = Object.create(null)
      this.params.forEach((param) => { existing[param.key] = param })
      this.params = this.flattenCaseParamEntries(values).map(({ key, value, path }) => {
        const display = this.caseDisplayValue(value)
        return { key, label: '', type: this.valueType(value), example: '', fromVar: false, options: [],
          ...existing[key], value: display, caseValue: value, caseDisplay: display, caseKey: key, casePath: path, fromCase: true }
      })
    },
    switchInputMode(mode) {
      if (mode === this.inputMode) return
      try {
        if (mode === 'json') this.updateInputJson(stringifyExactJson(this.buildFormParamMap(), 2))
        else this.replaceParamsFromValues(this.parseInputJson())
        this.inputMode = mode
      } catch (error) { this.notifyWarning(error.message) }
    },
    generateInputSample() {
      if (this.inputSampleJson === null || ['LOADING', 'ERROR'].includes(this.inputLoadState)) return
      const values = parseExactJson(this.inputSampleJson)
      this.replaceParamsFromValues(values)
      this.updateInputJson(stringifyExactJson(values, 2))
      this.resetExecutionState()
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
      this.updateInputJson(stringifyExactJson(this.buildFormParamMap(), 2))
      this.resetExecutionState()
    },
    async handleExecute() {
      if (!this.selectedRuleId || this.testCaseLoading) return
      if (this.testSourcesLoading || this.testSourcesError || (this.selectedExecutionSource && this.inputLoadState === 'ERROR')) return
      const editorCheck = this.$refs.expectationEditor?.validate?.()
      if (editorCheck && !editorCheck.valid) { this.notifyWarning(editorCheck.errors[0]); return }
      const ruleId = this.selectedRuleId
      const projectId = this.selectedProjectId
      const source = this.selectedExecutionSource ? { ...this.selectedExecutionSource } : null
      const requestId = ++this.executionRequestId
      let requestJson
      try {
        requestJson = this.buildRequestJson()
        this.executedInputParamsJson = stringifyExactJson(this.buildParamMap())
      } catch (error) { this.notifyWarning(error.message); return }
      const expectationJson = this.expectationJson ?? '{"version":1,"mode":"SNAPSHOT"}'
      const expectedResponseJson = this.expectedSnapshotJson
      const configuredExpectation = this.expectationJson
      this.executing = true
      this.result = null
      this.lastRawResponse = null
      this.lastRawResponseJson = ''
      try {
        const request = { requestJson, expectationJson, expectedResponseJson, ...(source ? { executionSource: source } : {}) }
        if (projectId) {
          request.projectId = projectId
        }
        const res = await executeApiScenarioValidated(
          ruleId,
          request,
          this.requestTimeoutMs
        )
        if (!this.isActiveExecution(requestId, ruleId, projectId)) return
        const payload = this.unwrapValidatedResponse(res)
        requireExecutionIdentity(payload.execution.executionIdentity, ruleId, source)
        this.lastRawResponse = { data: payload.execution }
        this.lastRawResponseJson = payload.responseJson
        this.result = normalizeTestResult(payload.execution)
        this.testValidation = payload.validation
        this.executedExpectationJson = configuredExpectation
        this.executedExpectedSnapshotJson = expectedResponseJson
        // 执行完成后切换到追踪树标签页
        this.traceTab = 'tree'
      } catch (e) {
        if (!this.isActiveExecution(requestId, ruleId, projectId)) return
        this.lastRawResponse = null
        this.lastRawResponseJson = ''
        this.testValidation = { status: 'NOT_EVALUATED', message: '执行请求未完成，未进行预期校验', checks: [], diffs: [] }
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
      this.updateInputJson('{}')
      this.resetExecutionState()
    },
    handleClearParams() {
      this.handleClear()
    },
    unwrapResponse(res) {
      if (res && Object.prototype.hasOwnProperty.call(res, 'data'))
        return res.data
      return res
    },
    resetInputLoadState() {
      this.inputSampleJson = null
      this.inputExecutionIdentity = null
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
      this.lastRawResponseJson = ''
      this.testValidation = { status: 'NOT_EVALUATED', message: '执行后查看预期校验', checks: [], diffs: [] }
      this.executedInputParamsJson = ''
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
      return this.inputMode === 'json' ? this.parseInputJson() : this.buildFormParamMap()
    },
    buildFormParamMap(params = this.params) {
      const paramMap = Object.create(null)
      for (const p of params) {
        if (!p.key && !p.fromCase) continue
        let val = p.value
        if (p.fromCase && p.key === p.caseKey && Object.is(val, p.caseDisplay)) {
          val = val === undefined ? undefined : cloneExactJson(p.caseValue)
        } else if (this.isNumberType(p.type) && val !== '' && val != null) {
          val = isExactNumber(val) ? val : createExactNumber(typeof val === 'number' ? exactNumberText(val) : String(val))
        } else if (p.type === 'BOOLEAN' && val !== '' && val != null) {
          val = val === true || val === 'true'
        } else if (['LIST', 'ARRAY', 'OBJECT', 'MAP'].includes(p.type) && typeof val === 'string' && val !== '') {
          val = parseExactJson(val)
        }
        this.setParamValue(paramMap, p.key, val, p.fromCase && p.key === p.caseKey ? p.casePath : null)
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
          'BIGDECIMAL',
          'BYTE',
          'SHORT',
          'INT',
        ].indexOf(type) >= 0
      )
    },
    setParamValue(target, key, value, sourcePath = null) {
      const set = (object, name, data) => Object.defineProperty(object, name, { value: data, writable: true, enumerable: true, configurable: true })
      if (!sourcePath && (!key || key.indexOf('.') < 0)) {
        set(target, key, value)
        return
      }
      var parts = sourcePath || key.split('.').filter(Boolean)
      if (parts.length === 0) return
      var cur = target
      for (var i = 0; i < parts.length - 1; i++) {
        if (
          !Object.hasOwn(cur, parts[i]) || !cur[parts[i]] ||
          typeof cur[parts[i]] !== 'object' ||
          Array.isArray(cur[parts[i]])
        ) {
          set(cur, parts[i], Object.create(null))
        }
        cur = cur[parts[i]]
      }
      set(cur, parts[parts.length - 1], value)
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
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;

  > :deep(.config-layer-guide) {
    flex: 0 0 auto;
  }
}
.test-layout {
  display: flex;
  flex: 1;
  gap: 16px;
  min-height: 0;
  overflow: hidden;
}
.test-left {
  flex: 0 0 480px;
  min-width: 380px;
  min-height: 0;
  padding-bottom: 8px;
  overflow-x: hidden;
  overflow-y: auto;
}
.input-parameters-title,
.input-mode-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.input-mode-toolbar {
  justify-content: space-between;
  margin-bottom: 12px;
}
.input-json-editor {
  margin-top: 12px;
}
.input-json-error {
  margin-top: 8px;
  color: var(--tianshu-danger-text);
  font-size: 12px;
}
.test-actions {
  position: sticky;
  bottom: 0;
  z-index: 1;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 16px;
  padding: 12px 8px;
  background: var(--tianshu-bg-surface);

  .el-button + .el-button {
    margin-left: 0;
  }
}
.test-timeout-label {
  color: var(--tianshu-text-secondary);
}
.test-timeout-unit {
  color: var(--tianshu-text-tertiary);
}
.test-timeout-input {
  width: 150px;
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

  > span:first-child {
    flex-shrink: 0;
    white-space: nowrap;
  }

  > span:last-child {
    display: flex;
    flex-wrap: wrap;
    justify-content: flex-end;
    gap: 4px 8px;

    .el-button + .el-button {
      margin-left: 0;
    }
  }
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
.test-validation-result {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  padding: 12px;
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 8px;
  color: var(--tianshu-text-secondary);
  background: var(--tianshu-bg-soft);
  overflow-wrap: anywhere;

  > div {
    flex-basis: 100%;
  }
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
.result-section-title.is-throttled {
  color: var(--tianshu-warning-text);
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
.result-pre.is-throttled {
  border-color: var(--tianshu-warning-border);
  background: var(--tianshu-warning-bg);
  color: var(--tianshu-warning-text);
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
    flex: none;
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
