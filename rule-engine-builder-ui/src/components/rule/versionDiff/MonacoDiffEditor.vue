<template>
  <div class="monaco-diff-editor" :style="{ height }">
    <div
      v-show="!loadFailed"
      ref="container"
      class="monaco-diff-editor-container"
    />
    <div v-if="loading" class="monaco-diff-state">
      <el-icon><el-icon-loading /></el-icon> 正在加载代码差异编辑器…
    </div>
    <div v-else-if="loadFailed" class="monaco-diff-fallback">
      <div>
        <div class="monaco-diff-fallback-title">基准版本</div>
        <pre>{{ original }}</pre>
      </div>
      <div>
        <div class="monaco-diff-fallback-title">对比版本</div>
        <pre>{{ modified }}</pre>
      </div>
    </div>
  </div>
</template>

<script>
import { Loading as ElIconLoading } from '@element-plus/icons-vue'
import { $emit } from '../../../utils/gogocodeTransfer'
export default {
  components: {
    ElIconLoading,
  },
  name: 'MonacoDiffEditor',
  props: {
    original: {
      type: String,
      default: '',
    },
    modified: {
      type: String,
      default: '',
    },
    language: {
      type: String,
      default: 'ql',
    },
    height: {
      type: String,
      default: '480px',
    },
  },
  data() {
    return {
      diffEditor: null,
      originalModel: null,
      modifiedModel: null,
      loading: true,
      loadFailed: false,
      destroyed: false,
    }
  },
  watch: {
    original(value) {
      this.updateModel(this.originalModel, value)
    },
    modified(value) {
      this.updateModel(this.modifiedModel, value)
    },
    language(value) {
      if (!window.monaco) return
      if (this.originalModel)
        window.monaco.editor.setModelLanguage(this.originalModel, value)
      if (this.modifiedModel)
        window.monaco.editor.setModelLanguage(this.modifiedModel, value)
    },
  },
  async mounted() {
    let attempts = 0
    while (!window.monaco && attempts < 100 && !this.destroyed) {
      await new Promise((resolve) => setTimeout(resolve, 100))
      attempts++
    }
    if (this.destroyed) return
    if (!window.monaco || !this.$refs.container) {
      this.loading = false
      this.loadFailed = true
      return
    }
    this.ensureQlLanguage(window.monaco)
    this.originalModel = window.monaco.editor.createModel(
      this.original || '',
      this.language
    )
    this.modifiedModel = window.monaco.editor.createModel(
      this.modified || '',
      this.language
    )
    this.diffEditor = window.monaco.editor.createDiffEditor(
      this.$refs.container,
      {
        readOnly: true,
        originalEditable: false,
        automaticLayout: true,
        renderSideBySide: true,
        enableSplitViewResizing: true,
        renderIndicators: true,
        renderOverviewRuler: true,
        diffWordWrap: 'on',
        fontSize: 13,
        fontFamily: "Consolas, Monaco, 'Courier New', monospace",
        lineNumbers: 'on',
        minimap: { enabled: false },
        scrollBeyondLastLine: false,
        folding: true,
        padding: { top: 8, bottom: 8 },
      }
    )
    this.diffEditor.setModel({
      original: this.originalModel,
      modified: this.modifiedModel,
    })
    this.loading = false
    $emit(this, 'ready', this.diffEditor)
    this.$nextTick(() => {
      if (this.diffEditor) this.diffEditor.layout()
    })
  },
  beforeUnmount() {
    this.destroyed = true
    if (this.diffEditor) this.diffEditor.dispose()
    if (this.originalModel) this.originalModel.dispose()
    if (this.modifiedModel) this.modifiedModel.dispose()
    this.diffEditor = null
    this.originalModel = null
    this.modifiedModel = null
  },
  methods: {
    updateModel(model, value) {
      if (!model) return
      const nextValue = value || ''
      if (!model.getValue || model.getValue() !== nextValue)
        model.setValue(nextValue)
    },
    ensureQlLanguage(monaco) {
      if (this.language !== 'ql' || !monaco.languages) return
      const languages = monaco.languages.getLanguages
        ? monaco.languages.getLanguages()
        : []
      if (
        !languages.some((item) => item.id === 'ql') &&
        monaco.languages.register
      ) {
        monaco.languages.register({ id: 'ql', aliases: ['QLExpress', 'QL'] })
      }
      if (
        !monaco.__ruleVersionDiffQlTokens &&
        monaco.languages.setMonarchTokensProvider
      ) {
        monaco.languages.setMonarchTokensProvider('ql', {
          keywords: [
            'if',
            'then',
            'else',
            'return',
            'for',
            'while',
            'true',
            'false',
            'null',
            'new',
            'in',
          ],
          operators: [
            '=',
            '>',
            '<',
            '!',
            '==',
            '<=',
            '>=',
            '!=',
            '&&',
            '||',
            '+',
            '-',
            '*',
            '/',
            '%',
          ],
          tokenizer: {
            root: [
              [
                /[a-zA-Z_$][\w$]*/,
                { cases: { '@keywords': 'keyword', '@default': 'identifier' } },
              ],
              [/\d+(\.\d+)?/, 'number'],
              [/"([^"\\]|\\.)*"/, 'string'],
              [/'([^'\\]|\\.)*'/, 'string'],
              [/\/\/.*$/, 'comment'],
            ],
          },
        })
        monaco.__ruleVersionDiffQlTokens = true
      }
    },
  },
  emits: ['ready'],
}
</script>

<style scoped>
.monaco-diff-editor {
  position: relative;
  min-height: 320px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
  background: #fff;
}
.monaco-diff-editor-container {
  width: 100%;
  height: 100%;
}
.monaco-diff-state {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #909399;
  font-size: 13px;
}
.monaco-diff-fallback {
  display: grid;
  grid-template-columns: minmax(420px, 1fr) minmax(420px, 1fr);
  gap: 12px;
  height: 100%;
  overflow: auto;
  padding: 12px;
}
.monaco-diff-fallback-title {
  margin-bottom: 8px;
  color: #606266;
  font-size: 13px;
  font-weight: 600;
}
.monaco-diff-fallback pre {
  min-height: 280px;
  margin: 0;
  padding: 12px;
  border: 1px solid #ebeef5;
  background: #f5f7fa;
  color: #303133;
  font: 13px/1.6 Consolas, Monaco, 'Courier New', monospace;
  white-space: pre-wrap;
}
</style>
