<template>
  <section class="expression-formula-preview" aria-label="表达式预览">
    <div
      class="expression-formula-preview__read"
      title="双击编辑执行脚本"
      @dblclick="!editing && startEditing()"
    >
      <div class="expression-formula-preview__business">
        <span>业务公式</span
        ><code>{{ formula || '请选择中间位置并添加内容' }}</code>
        <el-button v-if="!editing" size="small" @click.stop="startEditing">编辑脚本</el-button>
      </div>
      <small>公式随配置实时更新；双击可编辑脚本，确认后同步到公式。</small>
    </div>
    <div v-if="editing" class="expression-formula-preview__editor">
      <monaco-editor
        v-model:value="editScript"
        language="ql"
        height="132px"
        :options="editorOptions"
      />
      <p v-if="parseError" class="expression-formula-preview__error">
        {{ parseError }}
      </p>
      <div class="expression-formula-preview__actions">
        <el-button size="small" @click="cancelEditing">取消修改</el-button>
        <el-button type="primary" size="small" @click="confirmEditing"
          >确认脚本</el-button
        >
      </div>
    </div>
    <script-panel
      v-show="!editing"
      class="expression-formula-preview__script"
      :compile-result="previewResult"
      guidance="根据当前表达式实时生成"
      placeholder="配置表达式后生成脚本"
      empty-message="暂无脚本，请先配置表达式"
    />
  </section>
</template>

<script>
import { $emit } from '../../utils/gogocodeTransfer'
import MonacoEditor from '@/components/MonacoEditor.vue'
import ScriptPanel from '@/components/common/ScriptPanel.vue'
import { compileOperand } from '@/utils/operand'
import { formatExpressionFormula } from '@/utils/expressionDisplay'
import {
  ExpressionParseError,
  parseExpressionScript,
} from '@/utils/expressionParser'

export default {
  name: 'ExpressionFormulaPreview',
  components: { MonacoEditor, ScriptPanel },
  props: {
    operand: { type: Object, default: null },
    vars: { type: Array, default: () => [] },
    functions: { type: Array, default: () => [] },
  },
  data() {
    return {
      editing: false,
      editScript: '',
      originalScript: '',
      parseError: '',
      editorOptions: {
        lineNumbers: 'off',
        folding: false,
        minimap: { enabled: false },
      },
    }
  },
  computed: {
    formula() {
      return formatExpressionFormula(this.operand)
    },
    script() {
      try {
        return compileOperand(this.operand)
      } catch (e) {
        return ''
      }
    },
    previewResult() {
      return this.script ? { compileSuccess: true, compiledScript: this.script } : null
    },
  },
  methods: {
    startEditing() {
      this.originalScript = this.script
      this.editScript = this.script
      this.parseError = ''
      this.editing = true
    },
    cancelEditing() {
      this.editScript = this.originalScript
      this.parseError = ''
      this.editing = false
      $emit(this, 'cancel')
    },
    confirmEditing() {
      try {
        const operand = parseExpressionScript(this.editScript, {
          vars: this.vars,
          functions: this.functions,
        })
        this.parseError = ''
        this.editing = false
        $emit(this, 'confirm', operand)
      } catch (error) {
        if (error instanceof ExpressionParseError) {
          this.parseError = `第 ${error.line} 行，第 ${error.column} 列：${error.message}`
        } else {
          this.parseError = error.message || '脚本解析失败'
        }
      }
    },
  },
  emits: ['confirm', 'cancel'],
}
</script>

<style scoped>
.expression-formula-preview {
  border-top: 1px solid var(--tianshu-border-subtle);
  background: var(--tianshu-bg-surface);
}
.expression-formula-preview__read {
  display: grid;
  gap: 8px;
  padding: 10px 13px;
  cursor: text;
}
.expression-formula-preview__business {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
}
.expression-formula-preview__business > span {
  color: var(--tianshu-text-tertiary);
  font-size: 12px;
}
.expression-formula-preview__business code {
  color: var(--el-color-primary);
  font-family: Consolas, monospace;
  overflow-wrap: anywhere;
  white-space: normal;
  max-height: 80px;
  overflow: auto;
}
.expression-formula-preview__script {
  margin: 0 12px 12px;
}
@media (max-height: 800px) {
  .expression-formula-preview__script :deep(.sp-editor-container),
  .expression-formula-preview__script :deep(.sp-editor) {
    min-height: 160px;
  }
}
.expression-formula-preview small {
  color: var(--tianshu-text-tertiary);
  font-size: 11px;
}
.expression-formula-preview__editor {
  padding: 10px;
}
.expression-formula-preview__actions {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  margin-top: 8px;
}
.expression-formula-preview__error {
  margin: 7px 0 0;
  color: var(--tianshu-danger-text);
  font-size: 12px;
}
</style>
