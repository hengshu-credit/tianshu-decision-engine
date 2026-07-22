<template>
  <section class="expression-editor-page">
    <el-alert
      v-if="!activeSession"
      title="表达式配置会话已失效，请返回规则页面重新打开表达式"
      type="warning"
      :closable="false"
      show-icon
    />
    <template v-else>
      <header class="expression-editor-page__toolbar">
        <div>
          <strong>{{ activeSession.title || '配置表达式' }}</strong>
          <span v-if="activeSession.savedAt">草稿已临时保存</span>
        </div>
        <div class="expression-editor-page__actions">
          <el-button size="small" :icon="ElIconBack" @click="goBack"
            >返回</el-button
          >
          <el-button size="small" @click="undo">撤销</el-button>
          <el-button size="small" @click="redo">重做</el-button>
          <el-button size="small" :icon="ElIconVideoPlay" @click="openTest"
            >测试</el-button
          >
          <el-button size="small" :icon="ElIconDocument" @click="saveDraft"
            >临时保存</el-button
          >
          <el-button
            type="primary"
            size="small"
            :icon="ElIconCpu"
            :loading="compiling"
            @click="saveAndCompile"
            >保存并编译</el-button
          >
        </div>
      </header>

      <div class="expression-editor-page__editor">
        <expression-editor-dialog
          ref="editor"
          :visible="true"
          :embedded="true"
          :value="activeSession.draft"
          :vars="activeSession.vars || []"
          :functions="activeSession.functions || []"
          :list-options="activeSession.listOptions || []"
          :allowed-kinds="activeSession.allowedKinds || []"
          :context="activeSession.context || 'READ_EXPRESSION'"
          :expected-type="activeSession.expectedType || ''"
          :title="activeSession.title || '配置表达式'"
          @cancel="goBack"
        />
      </div>

      <el-dialog
        title="测试当前表达式"
        v-model="testVisible"
        width="680px"
        append-to-body
        :close-on-click-modal="false"
      >
        <div class="expression-test-mode">
          <span>输入字段范围</span>
          <el-radio-group
            :model-value="resolutionMode"
            size="small"
            @update:model-value="changeResolutionMode"
          >
            <el-radio-button value="CURRENT">只测试当前逻辑</el-radio-button>
            <el-radio-button value="DEEP">朔源到最底层</el-radio-button>
          </el-radio-group>
        </div>
        <el-alert
          v-if="resolutionMode === 'DEEP'"
          class="expression-test-warning"
          type="warning"
          :closable="false"
          show-icon
          title="朔源测试可能产生费用，并会真实调用外部 API、查询数据库和匹配名单。"
        />
        <div v-if="runtimeNodes.length" class="expression-test-runtime">
          <span>将真实执行</span>
          <el-tag
            v-for="node in runtimeNodes"
            :key="runtimeNodeKey(node)"
            size="small"
            type="warning"
          >
            {{ node.label || node.scriptName }} · {{ node.sourceType }}
          </el-tag>
        </div>
        <el-alert
          v-for="message in diagnostics"
          :key="message"
          class="expression-test-diagnostic"
          type="warning"
          :closable="false"
          :title="message"
        />
        <el-form
          v-loading="schemaLoading"
          label-position="top"
          size="small"
          class="expression-test-form"
        >
          <el-form-item v-for="field in testFields" :key="fieldKey(field)">
            <template #label>
              <span>{{ field.label || field.code || field.scriptName }}</span>
              <code>{{ field.scriptName || field.code }}</code>
            </template>
            <el-switch
              v-if="fieldInputKind(field) === 'BOOLEAN'"
              v-model="testValues[fieldPath(field)]"
              active-text="是"
              inactive-text="否"
            />
            <el-input-number
              v-else-if="fieldInputKind(field) === 'NUMBER'"
              v-model="testValues[fieldPath(field)]"
              :controls="false"
              class="expression-test-number"
            />
            <el-input
              v-else
              v-model="testValues[fieldPath(field)]"
              :type="fieldInputKind(field) === 'JSON' ? 'textarea' : 'text'"
              :rows="fieldInputKind(field) === 'JSON' ? 3 : 1"
              :placeholder="field.valueType || 'STRING'"
            />
          </el-form-item>
          <el-empty
            v-if="!schemaLoading && !testFields.length"
            description="当前表达式不需要输入字段"
            :image-size="72"
          />
        </el-form>
        <section
          v-if="testResult"
          class="expression-test-result"
          :class="{ 'is-error': !testResult.success }"
        >
          <div>
            <strong>{{ testResult.success ? '测试通过' : '测试失败' }}</strong>
            <span>{{ testResult.executeTimeMs || 0 }} ms</span>
          </div>
          <pre v-if="testResult.success">{{
            formatResult(testResult.result)
          }}</pre>
          <p v-else>{{ testResult.errorMessage || '表达式执行失败' }}</p>
        </section>
        <template #footer>
          <el-button @click="testVisible = false">关闭</el-button>
          <el-button type="primary" :loading="testing" @click="runTest"
            >开始测试</el-button
          >
        </template>
      </el-dialog>
    </template>
  </section>
</template>

<script>
import { markRaw } from 'vue'
import {
  Back as ElIconBack,
  VideoPlay as ElIconVideoPlay,
  Document as ElIconDocument,
  Cpu as ElIconCpu,
} from '@element-plus/icons-vue'
import ExpressionEditorDialog from '@/components/expression/ExpressionEditorDialog.vue'
import { cloneOperand } from '@/utils/operand'
import {
  compileExpression,
  executeExpression,
  getExpressionTestSchema,
} from '@/api/expression'

export default {
  data() {
    return {
      expressionSessionId: this.$route.params.sessionId,
      compiling: false,
      testVisible: false,
      schemaLoading: false,
      testing: false,
      resolutionMode: 'CURRENT',
      testFields: [],
      runtimeNodes: [],
      diagnostics: [],
      testValues: {},
      testResult: null,
      ElIconBack: markRaw(ElIconBack),
      ElIconVideoPlay: markRaw(ElIconVideoPlay),
      ElIconDocument: markRaw(ElIconDocument),
      ElIconCpu: markRaw(ElIconCpu),
    }
  },
  name: 'ExpressionEditorPage',
  components: { ExpressionEditorDialog },
  deactivated() {
    this.persistDraft()
  },
  beforeUnmount() {
    this.persistDraft()
  },
  computed: {
    session() {
      const getter = this.$store.getters['expressionSessions/sessionById']
      return typeof getter === 'function'
        ? getter(this.expressionSessionId)
        : null
    },
    activeSession() {
      return this.session && this.session.status === 'ACTIVE'
        ? this.session
        : null
    },
  },
  methods: {
    currentDraft() {
      if (
        this.$refs.editor &&
        typeof this.$refs.editor.getDraft === 'function'
      ) {
        return this.$refs.editor.getDraft()
      }
      return cloneOperand(this.activeSession && this.activeSession.draft)
    },
    undo() {
      if (this.$refs.editor && typeof this.$refs.editor.undo === 'function')
        this.$refs.editor.undo()
    },
    redo() {
      if (this.$refs.editor && typeof this.$refs.editor.redo === 'function')
        this.$refs.editor.redo()
    },
    validateDraft() {
      if (
        !this.$refs.editor ||
        typeof this.$refs.editor.validateDraft !== 'function'
      )
        return []
      return this.$refs.editor.validateDraft()
    },
    requestPayload(params = {}) {
      return {
        ruleId: this.normalizedRuleId(),
        resolutionMode: this.resolutionMode,
        operand: this.currentDraft(),
        params,
      }
    },
    normalizedRuleId() {
      const value = this.activeSession
        ? this.activeSession.ruleId
        : this.$route.params.ruleId
      const numeric = Number(value)
      return Number.isNaN(numeric) ? value : numeric
    },
    persistDraft() {
      if (!this.activeSession) return
      return this.$store.dispatch('expressionSessions/saveDraft', {
        sessionId: this.activeSession.sessionId,
        draft: this.currentDraft(),
      })
    },
    async saveDraft() {
      if (!this.activeSession) return
      await this.persistDraft()
      this.$message.success('表达式草稿已临时保存')
    },
    async saveAndCompile() {
      if (!this.activeSession || this.compiling) return
      const errors = this.validateDraft()
      if (errors.length) return
      this.compiling = true
      try {
        const operand = this.currentDraft()
        const response = await compileExpression({
          ruleId: this.normalizedRuleId(),
          resolutionMode: 'CURRENT',
          operand,
          params: {},
        })
        const result = this.responseData(response)
        if (!result || !result.success) {
          this.$message.error(
            '表达式编译失败: ' +
              (result && result.errorMessage ? result.errorMessage : '未知错误')
          )
          return
        }
        await this.$store.dispatch('expressionSessions/saveCompiled', {
          sessionId: this.activeSession.sessionId,
          operand,
          compiledScript: result.compiledScript || '',
        })
        this.$message.success('表达式保存并编译成功')
        this.goBack()
      } finally {
        this.compiling = false
      }
    },
    async openTest() {
      if (!this.activeSession) return
      const errors = this.validateDraft()
      if (errors.length) return
      this.testVisible = true
      this.testResult = null
      await this.loadTestSchema()
    },
    async changeResolutionMode(mode) {
      if (mode === this.resolutionMode) return
      if (mode === 'DEEP') {
        try {
          await this.$confirm(
            '朔源到最底层会真实调用外部 API、查询数据库和匹配名单，可能产生费用。确认继续？',
            '朔源测试确认',
            {
              type: 'warning',
              confirmButtonText: '确认并继续',
              cancelButtonText: '取消',
            }
          )
        } catch (e) {
          return
        }
      }
      this.resolutionMode = mode
      this.testResult = null
      await this.loadTestSchema()
    },
    async loadTestSchema() {
      this.schemaLoading = true
      try {
        const response = await getExpressionTestSchema(this.requestPayload({}))
        const schema = this.responseData(response) || {}
        this.testFields = schema.inputs || []
        this.runtimeNodes = schema.runtimeNodes || []
        this.diagnostics = schema.diagnostics || []
        const values = {}
        this.testFields.forEach((field) => {
          const path = this.fieldPath(field)
          const sample = this.readPath(schema.sampleParams || {}, path)
          values[path] = this.editableValue(field, sample)
        })
        this.testValues = values
      } finally {
        this.schemaLoading = false
      }
    },
    async runTest() {
      if (this.testing) return
      this.testing = true
      this.testResult = null
      try {
        const response = await executeExpression(
          this.requestPayload(this.buildTestParams())
        )
        this.testResult = this.responseData(response)
      } catch (error) {
        if (error && error.message) this.$message.error(error.message)
      } finally {
        this.testing = false
      }
    },
    buildTestParams() {
      const params = {}
      this.testFields.forEach((field) => {
        const path = this.fieldPath(field)
        this.setPath(
          params,
          path,
          this.runtimeValue(field, this.testValues[path])
        )
      })
      return params
    },
    editableValue(field, value) {
      if (this.fieldInputKind(field) === 'JSON') {
        return value == null ? '' : JSON.stringify(value, null, 2)
      }
      if (value != null) return value
      if (this.fieldInputKind(field) === 'NUMBER') return 0
      if (this.fieldInputKind(field) === 'BOOLEAN') return false
      return ''
    },
    runtimeValue(field, value) {
      if (
        this.fieldInputKind(field) === 'JSON' &&
        typeof value === 'string' &&
        value.trim()
      ) {
        try {
          return JSON.parse(value)
        } catch (e) {
          throw new Error(
            (field.label || this.fieldPath(field)) + ' 不是有效 JSON'
          )
        }
      }
      return value
    },
    fieldInputKind(field) {
      const type = String((field && field.valueType) || '').toUpperCase()
      if (
        [
          'INTEGER',
          'INT',
          'LONG',
          'NUMBER',
          'DOUBLE',
          'FLOAT',
          'DECIMAL',
          'PROBABILITY',
        ].includes(type)
      )
        return 'NUMBER'
      if (['BOOLEAN', 'BOOL'].includes(type)) return 'BOOLEAN'
      if (['ARRAY', 'LIST', 'VECTOR', 'OBJECT', 'MAP'].includes(type))
        return 'JSON'
      return 'TEXT'
    },
    fieldPath(field) {
      return field.scriptName || field.code || ''
    },
    fieldKey(field) {
      return (
        (field.refType || '') +
        ':' +
        (field.refId == null ? this.fieldPath(field) : field.refId)
      )
    },
    runtimeNodeKey(node) {
      return (
        (node.refType || node.sourceType || '') +
        ':' +
        (node.refId == null ? node.scriptName : node.refId)
      )
    },
    readPath(source, path) {
      return String(path || '')
        .split('.')
        .filter(Boolean)
        .reduce(
          (value, key) => (value == null ? undefined : value[key]),
          source
        )
    },
    setPath(target, path, value) {
      const parts = String(path || '')
        .split('.')
        .filter(Boolean)
      if (!parts.length) return
      let current = target
      parts.forEach((part, index) => {
        if (index === parts.length - 1) current[part] = value
        else {
          if (!current[part] || typeof current[part] !== 'object')
            current[part] = {}
          current = current[part]
        }
      })
    },
    responseData(response) {
      return response && response.data !== undefined ? response.data : response
    },
    formatResult(value) {
      if (typeof value === 'string') return value
      try {
        return JSON.stringify(value, null, 2)
      } catch (e) {
        return String(value)
      }
    },
    goBack() {
      this.$router.back()
    },
  },
}
</script>

<style scoped>
.expression-editor-page {
  display: grid;
  height: calc(100vh - 82px);
  min-height: 520px;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 10px;
  overflow: hidden;
}
.expression-editor-page__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 14px;
  border: 1px solid #e1e7ef;
  border-radius: 8px;
  background: #fff;
}
.expression-editor-page__toolbar > div:first-child {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}
.expression-editor-page__toolbar strong {
  color: #26364d;
  font-size: 16px;
}
.expression-editor-page__toolbar span {
  color: #8a98aa;
  font-size: 12px;
}
.expression-editor-page__actions {
  display: flex;
  flex: none;
  gap: 7px;
}
.expression-editor-page__editor {
  min-width: 0;
  min-height: 0;
}
.expression-test-mode {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
  color: #53637a;
}
.expression-test-warning,
.expression-test-diagnostic {
  margin-bottom: 12px;
}
.expression-test-runtime {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 7px;
  margin-bottom: 12px;
  color: #7b8798;
  font-size: 12px;
}
.expression-test-form {
  max-height: 390px;
  overflow: auto;
  padding-right: 5px;
}
.expression-test-form code {
  margin-left: 8px;
  color: #789;
  font-size: 11px;
}
.expression-test-number {
  width: 100%;
}
.expression-test-result {
  margin-top: 14px;
  padding: 12px;
  border: 1px solid #b7e2ce;
  border-radius: 6px;
  background: #f1fbf6;
}
.expression-test-result.is-error {
  border-color: #f1c4c8;
  background: #fff5f5;
}
.expression-test-result > div {
  display: flex;
  justify-content: space-between;
  color: #39785d;
}
.expression-test-result.is-error > div {
  color: #b24a55;
}
.expression-test-result pre,
.expression-test-result p {
  max-height: 160px;
  margin: 9px 0 0;
  overflow: auto;
  color: #33445b;
  font-family: Consolas, monospace;
  white-space: pre-wrap;
}
</style>
