<template>
  <el-dialog
    title="测试执行"
    :visible.sync="innerVisible"
    width="760px"
    append-to-body
    :close-on-click-modal="false"
  >
    <div class="designer-test-dialog">
      <div class="editor-label">输入参数 JSON</div>
      <el-alert
        v-if="schemaDiagnostics.length"
        :title="schemaDiagnostics.join('；')"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom:8px;"
      />
      <monaco-editor
        v-model="paramsJson"
        language="json"
        height="260px"
        :key="editorKey"
        @input="validateJson"
      />
      <div v-if="jsonError" class="json-error">{{ jsonError }}</div>

      <div v-if="result" class="test-result">
        <el-alert
          :title="result.success ? '执行成功' : '执行失败'"
          :type="result.success ? 'success' : 'error'"
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
          <el-tab-pane v-if="result.errorMessage" label="错误信息" name="error">
            <pre class="result-pre error-pre">{{ result.errorMessage }}</pre>
          </el-tab-pane>
        </el-tabs>
        <div class="result-meta">耗时 {{ result.executeTimeMs || 0 }} ms</div>
      </div>
    </div>
    <template slot="footer">
      <el-button size="small" @click="close">关闭</el-button>
      <el-button size="small" @click="resetParams">重置样例</el-button>
      <el-button
        size="small"
        type="primary"
        icon="el-icon-video-play"
        :loading="executing"
        @click="execute"
      >
        执行测试
      </el-button>
    </template>
  </el-dialog>
</template>

<script>
import { executeRule, getRuleTestSchema } from '@/api/definition'
import MonacoEditor from '@/components/MonacoEditor'
import { normalizeTestResult, formatTestOutput } from '@/utils/testResult'

export default {
  name: 'DesignerTestDialog',
  components: { MonacoEditor },
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    definitionId: {
      type: [String, Number],
      default: null
    },
    targetType: {
      type: String,
      default: 'RULE'
    },
    projectId: {
      type: [String, Number],
      default: null
    },
    modelType: {
      type: String,
      default: ''
    },
    modelJson: {
      type: [Object, String],
      default: null
    },
    modelJsonProvider: {
      type: Function,
      default: null
    },
    paramsTemplate: {
      type: [Object, String],
      default: () => ({})
    }
  },
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
      schemaDiagnostics: []
    }
  },
  computed: {
    innerVisible: {
      get() {
        return this.visible
      },
      set(value) {
        this.$emit('update:visible', value)
      }
    }
  },
  watch: {
    visible(value) {
      if (value) this.open()
    },
    paramsTemplate: {
      deep: true,
      handler() {
        if (this.visible) this.resetParams()
      }
    }
  },
  methods: {
    async open() {
      this.result = null
      this.lastInput = null
      this.activeTab = 'output'
      this.resolvedTemplate = null
      await this.loadTestSchema()
      this.resetParams()
    },
    async loadTestSchema() {
      const currentModel = this.modelJsonProvider ? this.modelJsonProvider() : this.modelJson
      if (!this.definitionId && !currentModel) return
      const modelJson = typeof currentModel === 'string'
        ? currentModel
        : (currentModel ? JSON.stringify(currentModel) : null)
      try {
        const response = await getRuleTestSchema({
          targetType: this.targetType || 'RULE',
          targetId: this.definitionId,
          projectId: this.projectId,
          modelType: this.modelType || undefined,
          modelJson
        })
        const schema = response && response.data !== undefined ? response.data : response
        if (schema && schema.sampleParams) this.resolvedTemplate = schema.sampleParams
        this.schemaDiagnostics = schema && Array.isArray(schema.diagnostics) ? schema.diagnostics : []
      } catch (e) {
        this.schemaDiagnostics = [e.message || '测试字段解析失败']
      }
    },
    resetParams() {
      this.paramsJson = this.formatJson(this.normalizeTemplate())
      this.jsonError = ''
      this.editorKey += 1
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
      this.result = null
      this.lastInput = params
      try {
        const res = await executeRule({ definitionId: this.definitionId, params })
        this.result = normalizeTestResult(res)
        this.activeTab = this.result && this.result.errorMessage ? 'error' : 'output'
      } catch (e) {
        this.result = normalizeTestResult({ success: false, errorMessage: e.message || '执行异常', executeTimeMs: 0, result: null })
        this.activeTab = 'error'
      } finally {
        this.executing = false
      }
    },
    close() {
      this.innerVisible = false
    },
    formatJson(value) {
      if (value === null || value === undefined) return '{}'
      try {
        return JSON.stringify(typeof value === 'string' ? JSON.parse(value) : value, null, 2)
      } catch (e) {
        return String(value)
      }
    },
    formatOutput(value) {
      return formatTestOutput(value)
    }
  }
}
</script>

<style scoped>
.designer-test-dialog {
  min-height: 340px;
}
.editor-label {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
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
.result-pre {
  background: #f5f7fa;
  border: 1px solid #ebeef5;
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
.result-meta {
  color: #909399;
  font-size: 12px;
  text-align: right;
}
</style>
