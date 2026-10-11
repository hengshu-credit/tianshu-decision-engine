<template>
  <el-dialog
    class="resizable-config-dialog"
    title="测试执行"
    v-model="innerVisible"
    width="760px"
    append-to-body
    :close-on-click-modal="false"
  >
    <div class="designer-test-dialog">
      <el-alert v-if="executionSource" :title="executionSourceLabel(executionSource, executionSourceName || resolvedIdentity)" type="info" :closable="false" show-icon style="margin-bottom: 10px" />
      <div
        style="
          margin-bottom: 10px;
          display: flex;
          align-items: center;
          gap: 8px;
        "
      >
        <span>页面请求超时</span>
        <el-input-number
          v-model="requestTimeoutMs"
          :min="1000"
          :max="1800000"
          :step="1000"
          size="small"
        />
        <span style="color: var(--tianshu-text-tertiary)">毫秒</span>
      </div>
      <dialog-resize-handle :visible="visible" :min-width="640" :min-height="420" />
      <div class="editor-label">输入参数 JSON</div>
      <div v-if="schemaLoading" class="schema-loading" role="status">正在加载字段样例，可以先编辑参数；已输入内容会保留。</div>
      <el-alert
        v-if="schemaDiagnostics.length"
        :title="schemaDiagnostics.join('；')"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 8px"
      />
      <monaco-editor
        v-model:value="paramsJson"
        language="json"
        height="max(160px, calc(260px + var(--dialog-resize-height-delta, 0px)))"
        :key="editorKey"
        @change="validateJson"
      />
      <div v-if="jsonError" class="json-error">{{ jsonError }}</div>

      <div v-if="result" class="test-result">
        <el-alert
          :title="resultThrottled ? executionStatusLabel(result.executionStatus) : result.success ? '执行成功' : '执行失败'"
          :type="resultThrottled ? 'warning' : result.success ? 'success' : 'error'"
          :description="executionStatusHint(result.executionStatus)"
          :closable="false"
          show-icon
        />
        <el-tabs v-model="activeTab" class="result-tabs">
          <el-tab-pane label="本次输入" name="input">
            <pre class="result-pre">{{ formatJson(lastInput) }}</pre>
          </el-tab-pane>
          <el-tab-pane label="执行输出" name="output">
            <pre class="result-pre">{{ formatOutput(result.output) }}</pre>
          </el-tab-pane>
          <el-tab-pane label="表达式追踪树" name="tree" lazy>
            <div class="result-trace">
              <trace-tree
                :trace-info="traceInfoJson"
                :model-type="modelType"
                :input-params="JSON.stringify(lastInput)"
                :output-result="outputResultJson"
                :execute-time-ms="result.executeTimeMs"
              />
            </div>
          </el-tab-pane>
          <el-tab-pane v-if="result.errorMessage" :label="resultThrottled ? '容量提示' : '错误信息'" name="error">
            <pre class="result-pre" :class="resultThrottled ? 'capacity-pre' : 'error-pre'">{{ result.errorMessage }}</pre>
          </el-tab-pane>
        </el-tabs>
        <div class="result-meta">耗时 {{ result.executeTimeMs || 0 }} ms</div>
      </div>
    </div>
    <template v-slot:footer>
      <el-button size="small" @click="close">关闭</el-button>
      <el-button size="small" :disabled="schemaLoading || executing" @click="resetParams">重置样例</el-button>
      <el-button
        size="small"
        type="primary"
        :icon="ElIconVideoPlay"
        :loading="executing"
        :disabled="schemaLoading || executing"
        @click="execute"
      >
        执行测试
      </el-button>
    </template>
  </el-dialog>
</template>

<script>
import { markRaw } from 'vue'
import { VideoPlay as ElIconVideoPlay } from '@element-plus/icons-vue'
import { $emit } from '../../utils/gogocodeTransfer'
import { executeRule, getRuleTestSchema } from '@/api/definition'
import MonacoEditor from '@/components/MonacoEditor'
import TraceTree from '@/components/common/TraceTree.vue'
import DialogResizeHandle from '@/components/common/DialogResizeHandle.vue'
import { normalizeTestResult, formatTestOutput } from '@/utils/testResult'
import { executionStatusLabel, executionStatusHint } from '@/utils/executionStatus'
import { executionSourceKey, executionSourceLabel, requireExecutionIdentity } from '@/utils/ruleTestExecutionSource'

export default {
  data() {
    return {
      paramsJson: '{}',
      jsonError: '',
      result: null,
      lastInput: null,
      executing: false,
      editorKey: 1,
      activeTab: 'output',
      resolvedTemplate: null,
      resolvedIdentity: null,
      schemaDiagnostics: [],
      schemaLoading: false,
      schemaSourceError: '',
      schemaRequestId: 0,
      executionRequestId: 0,
      sampleOwnerKey: '',
      lastGeneratedParams: '{}',
      requestTimeoutMs: 180000,
      ElIconVideoPlay: markRaw(ElIconVideoPlay),
    }
  },
  name: 'DesignerTestDialog',
  components: { MonacoEditor, TraceTree, DialogResizeHandle },
  props: {
    visible: {
      type: Boolean,
      default: false,
    },
    definitionId: {
      type: [String, Number],
      default: null,
    },
    executionSource: { type: Object, default: null },
    executionSourceName: { type: String, default: '' },
    targetType: {
      type: String,
      default: 'RULE',
    },
    projectId: {
      type: [String, Number],
      default: null,
    },
    modelType: {
      type: String,
      default: '',
    },
    modelJson: {
      type: [Object, String],
      default: null,
    },
    modelJsonProvider: {
      type: Function,
      default: null,
    },
    paramsTemplate: {
      type: [Object, String],
      default: () => ({}),
    },
  },
  computed: {
    resultThrottled() {
      return Boolean(this.result && this.result.executionStatus === 'THROTTLED')
    },
    traceInfoJson() {
      return this.result && this.result.traces
        ? JSON.stringify(this.result.traces)
        : ''
    },
    outputResultJson() {
      return this.result && this.result.hasOutput
        ? JSON.stringify(this.result.output)
        : ''
    },
    innerVisible: {
      get() {
        return this.visible
      },
      set(value) {
        $emit(this, 'update:visible', value)
      },
    },
  },
  watch: {
    executionSource: { deep: true, handler() { this.executionContextChanged() } },
    definitionId() { this.executionContextChanged() },
    projectId() { this.executionContextChanged() },
    visible(value) {
      if (value) this.open()
      else this.invalidateRequests()
    },
    paramsTemplate: {
      deep: true,
      handler() {
        if (this.visible && !this.schemaLoading && this.paramsJson === this.lastGeneratedParams) this.resetParams()
      },
    },
  },
  beforeUnmount() { this.invalidateRequests() },
  methods: {
    executionStatusLabel,
    executionStatusHint,
    executionSourceLabel,
    executionContextChanged() {
      this.invalidateRequests()
      this.result = null
      this.lastInput = null
      if (this.visible) this.open()
    },
    executionContextKey() {
      return JSON.stringify([this.definitionId, this.projectId, executionSourceKey(this.executionSource),
        this.executionSource && this.executionSource.sourceType !== 'PREVIEW' ? null : this.currentModelJson()])
    },
    executionSourcePayload() {
      if (this.executionSource && this.executionSource.sourceType !== 'PREVIEW') return { executionSource: { ...this.executionSource } }
      return {
        ...(this.executionSource ? { executionSource: { ...this.executionSource } } : {}),
        modelType: this.modelType || undefined,
        modelJson: this.currentModelJson(),
      }
    },
    async open() {
      this.executionRequestId++
      this.executing = false
      this.result = null
      this.lastInput = null
      this.activeTab = 'output'
      this.resolvedTemplate = null
      this.resolvedIdentity = null
      this.schemaDiagnostics = []
      this.schemaSourceError = ''
      const owner = `${this.targetType}:${this.definitionId}:${this.projectId}:${executionSourceKey(this.executionSource)}`
      if (owner !== this.sampleOwnerKey) {
        this.sampleOwnerKey = owner
        this.resetParams()
      }
      const inputAtOpen = this.paramsJson
      const wasEdited = inputAtOpen !== this.lastGeneratedParams
      const requestId = ++this.schemaRequestId
      await this.loadTestSchema(requestId)
      if (requestId === this.schemaRequestId && !wasEdited && this.paramsJson === inputAtOpen) this.resetParams()
    },
    invalidateRequests() {
      this.schemaRequestId++
      this.executionRequestId++
      this.schemaLoading = false
      this.executing = false
    },
    async loadTestSchema(requestId = ++this.schemaRequestId) {
      const modelJson = this.currentModelJson()
      if (!this.definitionId && !modelJson) return
      this.schemaLoading = true
      const context = this.executionContextKey()
      const definitionId = this.definitionId
      const source = this.executionSource ? { ...this.executionSource } : null
      try {
        const response = await getRuleTestSchema({
          targetType: this.targetType || 'RULE',
          targetId: this.definitionId,
          projectId: Number(this.projectId) > 0 ? this.projectId : undefined,
          ...this.executionSourcePayload(),
        })
        const schema =
          response && response.data !== undefined ? response.data : response
        if (requestId !== this.schemaRequestId || context !== this.executionContextKey()) return
        requireExecutionIdentity(schema?.executionIdentity, definitionId, source)
        this.resolvedIdentity = schema?.executionIdentity || null
        if (schema && schema.sampleParams)
          this.resolvedTemplate = schema.sampleParams
        this.schemaDiagnostics =
          schema && Array.isArray(schema.diagnostics) ? schema.diagnostics : []
      } catch (e) {
        if (requestId === this.schemaRequestId && context === this.executionContextKey()) {
          this.schemaSourceError = e.message || '测试字段解析失败'
          this.schemaDiagnostics = [this.schemaSourceError]
        }
      } finally {
        if (requestId === this.schemaRequestId) this.schemaLoading = false
      }
    },
    resetParams() {
      this.paramsJson = this.formatJson(this.normalizeTemplate())
      this.lastGeneratedParams = this.paramsJson
      this.jsonError = ''
    },
    normalizeTemplate() {
      if (this.resolvedTemplate !== null) return this.resolvedTemplate
      if (typeof this.paramsTemplate === 'string') {
        try {
          return JSON.parse(this.paramsTemplate || '{}')
        } catch (e) {
          return {}
        }
      }
      return this.paramsTemplate || {}
    },
    validateJson(value) {
      this.jsonError = ''
      if (!value || !value.trim()) return
      try {
        JSON.parse(value)
      } catch (e) {
        this.jsonError = 'JSON 格式错误：' + e.message
      }
    },
    async execute() {
      if (this.schemaLoading || this.executing) return
      if (this.executionSource && this.schemaSourceError) {
        this.$message.error(this.schemaSourceError)
        return
      }
      if (this.jsonError) {
        this.$message.error('请先修正 JSON 格式错误')
        return
      }
      let params
      try {
        params = JSON.parse(this.paramsJson || '{}')
      } catch (e) {
        this.jsonError = 'JSON 格式错误：' + e.message
        return
      }
      this.executing = true
      const requestId = ++this.executionRequestId
      const context = this.executionContextKey()
      const definitionId = this.definitionId
      const source = this.executionSource ? { ...this.executionSource } : null
      this.result = null
      this.lastInput = params
      try {
        const res = await executeRule(
          {
            definitionId: this.definitionId,
            projectId: Number(this.projectId) > 0 ? this.projectId : undefined,
            ...this.executionSourcePayload(),
            params,
          },
          this.requestTimeoutMs
        )
        if (requestId !== this.executionRequestId || context !== this.executionContextKey()) return
        const result = normalizeTestResult(res)
        requireExecutionIdentity(result.executionIdentity, definitionId, source)
        this.result = result
        this.activeTab =
          this.result && this.result.errorMessage ? 'error' : 'output'
      } catch (e) {
        if (requestId !== this.executionRequestId) return
        this.result = normalizeTestResult({
          success: false,
          errorMessage: e.message || '执行异常',
          executeTimeMs: 0,
          result: null,
        })
        this.activeTab = 'error'
      } finally {
        if (requestId === this.executionRequestId) this.executing = false
      }
    },
    close() {
      this.invalidateRequests()
      this.innerVisible = false
    },
    currentModelJson() {
      const currentModel = this.modelJsonProvider
        ? this.modelJsonProvider()
        : this.modelJson
      return typeof currentModel === 'string'
        ? currentModel
        : currentModel
        ? JSON.stringify(currentModel)
        : null
    },
    formatJson(value) {
      if (value === null || value === undefined) return '{}'
      try {
        return JSON.stringify(
          typeof value === 'string' ? JSON.parse(value) : value,
          null,
          2
        )
      } catch (e) {
        return String(value)
      }
    },
    formatOutput(value) {
      return formatTestOutput(value)
    },
  },
  emits: ['update:visible'],
}
</script>

<style scoped>
.designer-test-dialog {
  min-height: 340px;
}
.editor-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--tianshu-text-primary);
  margin-bottom: 8px;
}
.schema-loading {
  margin-bottom: 8px;
  color: var(--tianshu-text-secondary);
  font-size: 12px;
}
.json-error {
  color: #f56c6c;
  font-size: 12px;
  margin-top: 6px;
}
.test-result {
  margin-top: 14px;
}
.result-tabs {
  margin-top: 10px;
}
.result-trace {
  max-height: 420px;
  overflow: auto;
}
.result-pre {
  background: var(--tianshu-bg-muted);
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 4px;
  padding: 10px 12px;
  margin: 0;
  max-height: 220px;
  overflow: auto;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}
.error-pre {
  background: #fff2f0;
  border-color: #ffccc7;
  color: #cf1322;
}
.capacity-pre {
  background: var(--tianshu-warning-bg);
  border-color: var(--tianshu-warning-border);
  color: var(--tianshu-warning-text);
}
.result-meta {
  color: var(--tianshu-text-tertiary);
  font-size: 12px;
  text-align: right;
}
</style>
