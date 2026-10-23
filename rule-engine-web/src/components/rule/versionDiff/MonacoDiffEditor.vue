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
import { markRaw } from 'vue'
import { Loading as ElIconLoading } from '@element-plus/icons-vue'
import { $emit } from '../../../utils/gogocodeTransfer'
import { getMonacoScrollbarOptions, syncMonacoTheme } from '@/theme/monacoTheme'
import { waitForMonaco } from '@/utils/monacoLoader'
import { buildQlReferenceDecorations, collectQlScriptReferences } from '@/utils/qlScriptReferences'
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
    references: { type: Array, default: () => [] },
    functions: { type: Array, default: () => [] },
    referenceVersions: { type: Object, default: () => ({}) },
    referenceCatalogMode: { type: String, default: 'LIVE' },
  },
  data() {
    return {
      diffEditor: null,
      originalModel: null,
      modifiedModel: null,
      originalDecorationIds: [],
      modifiedDecorationIds: [],
      loading: true,
      loadFailed: false,
      destroyed: false,
      loadController: null,
    }
  },
  watch: {
    original(value) {
      this.updateModel(this.originalModel, value)
      this.$nextTick(() => this.refreshReferenceDecorations())
    },
    modified(value) {
      this.updateModel(this.modifiedModel, value)
      this.$nextTick(() => this.refreshReferenceDecorations())
    },
    references: { deep: true, handler() { this.refreshReferenceDecorations() } },
    functions: { deep: true, handler() { this.refreshReferenceDecorations() } },
    referenceVersions: { deep: true, handler() { this.refreshReferenceDecorations() } },
    referenceCatalogMode() { this.refreshReferenceDecorations() },
    language(value) {
      if (!window.monaco) return
      if (this.originalModel)
        window.monaco.editor.setModelLanguage(this.originalModel, value)
      if (this.modifiedModel)
        window.monaco.editor.setModelLanguage(this.modifiedModel, value)
    },
  },
  async mounted() {
    window.addEventListener(
      'tianshu-theme-change',
      this.handleGlobalThemeChange
    )
    this.loadController = markRaw(new AbortController())
    const monaco = window.monaco || await waitForMonaco(this.loadController.signal)
    if (this.destroyed || this.loadController.signal.aborted || typeof window === 'undefined') return
    if (!monaco || !this.$refs.container) {
      this.loading = false
      this.loadFailed = true
      return
    }
    this.ensureQlLanguage(window.monaco)
    this.syncGlobalTheme()
    this.originalModel = markRaw(
      window.monaco.editor.createModel(this.original || '', this.language)
    )
    this.modifiedModel = markRaw(
      window.monaco.editor.createModel(this.modified || '', this.language)
    )
    this.diffEditor = markRaw(
      window.monaco.editor.createDiffEditor(this.$refs.container, {
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
        scrollbar: getMonacoScrollbarOptions(),
        folding: true,
        padding: { top: 8, bottom: 8 },
      })
    )
    this.diffEditor.setModel({
      original: this.originalModel,
      modified: this.modifiedModel,
    })
    this.refreshReferenceDecorations()
    this.loading = false
    $emit(this, 'ready', this.diffEditor)
    this.$nextTick(() => {
      if (this.diffEditor) this.diffEditor.layout()
    })
  },
  beforeUnmount() {
    this.loadController?.abort()
    window.removeEventListener(
      'tianshu-theme-change',
      this.handleGlobalThemeChange
    )
    this.destroyed = true
    this.clearReferenceDecorations()
    if (this.diffEditor) this.diffEditor.dispose()
    if (this.originalModel) this.originalModel.dispose()
    if (this.modifiedModel) this.modifiedModel.dispose()
    this.diffEditor = null
    this.originalModel = null
    this.modifiedModel = null
  },
  methods: {
    handleGlobalThemeChange(event) {
      const scheme = event && event.detail && event.detail.colorScheme
      this.syncGlobalTheme(scheme)
    },
    syncGlobalTheme(colorScheme) {
      return syncMonacoTheme(window.monaco, { colorScheme })
    },
    updateModel(model, value) {
      if (!model) return
      const nextValue = value || ''
      if (!model.getValue || model.getValue() !== nextValue)
        model.setValue(nextValue)
    },
    normalizedReferences() {
      return (this.references || []).map(reference => ({
        ...reference,
        varCode: reference.varCode || reference.refCode || reference.scriptName,
        varLabelText: reference.varLabelText || reference.varLabel || reference.refLabel?.label || reference.label,
        _varId: reference._varId != null ? reference._varId : reference.id,
        _refType: reference._refType || reference.refType,
      })).filter(reference => reference.varCode && reference._varId != null && reference._refType)
    },
    clearReferenceDecorations() {
      if (this.diffEditor?.getOriginalEditor && this.originalDecorationIds.length) {
        this.diffEditor.getOriginalEditor().deltaDecorations(this.originalDecorationIds, [])
      }
      if (this.diffEditor?.getModifiedEditor && this.modifiedDecorationIds.length) {
        this.diffEditor.getModifiedEditor().deltaDecorations(this.modifiedDecorationIds, [])
      }
      this.originalDecorationIds = []
      this.modifiedDecorationIds = []
    },
    refreshReferenceDecorations() {
      this.clearReferenceDecorations()
      if (this.language !== 'ql' || !this.diffEditor?.getOriginalEditor || (!this.references.length && !this.functions.length)) return
      const references = this.normalizedReferences()
      const apply = (editor) => {
        const model = editor?.getModel?.()
        if (!model || typeof model.getPositionAt !== 'function') return []
        const matches = collectQlScriptReferences(model.getValue(), references, this.functions)
        return editor.deltaDecorations([], buildQlReferenceDecorations(matches, model, this.referenceVersions, this.referenceCatalogMode, { includeType: true }))
      }
      this.originalDecorationIds = apply(this.diffEditor.getOriginalEditor())
      this.modifiedDecorationIds = apply(this.diffEditor.getModifiedEditor())
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
  border: 1px solid var(--tianshu-border);
  border-radius: 4px;
  overflow: hidden;
  background: var(--tianshu-bg-surface);
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
  color: var(--tianshu-text-tertiary);
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
  color: var(--tianshu-text-secondary);
  font-size: 13px;
  font-weight: 600;
}
.monaco-diff-fallback pre {
  min-height: 280px;
  margin: 0;
  padding: 12px;
  border: 1px solid var(--tianshu-border-subtle);
  background: var(--tianshu-bg-muted);
  color: var(--tianshu-text-primary);
  font: 13px/1.6 Consolas, Monaco, 'Courier New', monospace;
  white-space: pre-wrap;
}
</style>
