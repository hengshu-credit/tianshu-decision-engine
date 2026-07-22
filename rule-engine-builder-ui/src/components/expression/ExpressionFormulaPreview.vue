<template>
  <section class="expression-formula-preview">
    <div
      v-if="!editing"
      class="expression-formula-preview__read"
      title="双击编辑执行脚本"
      @dblclick="startEditing"
    >
      <div class="expression-formula-preview__business">
        <span>业务公式</span
        ><code>{{ formula || '请选择中间位置并添加内容' }}</code>
      </div>
      <div class="expression-formula-preview__script">
        <span>执行脚本</span><code>{{ script || '-' }}</code>
      </div>
      <small>双击可手工编辑脚本；确认后会重新解析为结构化公式。</small>
    </div>
    <div v-else class="expression-formula-preview__editor">
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
  </section>
</template>

<script>
import { $emit } from '../../utils/gogocodeTransfer'
import MonacoEditor from '@/components/MonacoEditor.vue'
import { compileOperand } from '@/utils/operand'
import { formatExpressionFormula } from '@/utils/expressionDisplay'
import {
  ExpressionParseError,
  parseExpressionScript,
} from '@/utils/expressionParser'

export default {
  name: 'ExpressionFormulaPreview',
  components: { MonacoEditor },
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
  margin-bottom: 12px;
  border: 1px solid #dce5ef;
  border-radius: 7px;
  background: #fff;
}
.expression-formula-preview__read {
  display: grid;
  gap: 8px;
  padding: 10px 13px;
  cursor: text;
}
.expression-formula-preview__business,
.expression-formula-preview__script {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  gap: 10px;
}
.expression-formula-preview span {
  color: #7d8a9d;
  font-size: 12px;
}
.expression-formula-preview code {
  color: #174ea6;
  font-family: Consolas, monospace;
  overflow-wrap: anywhere;
  white-space: normal;
}
.expression-formula-preview__script code {
  color: #526278;
}
.expression-formula-preview small {
  color: #9aa7b7;
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
  color: #e35d6a;
  font-size: 12px;
}
</style>
