<template>
  <div
    ref="container"
    class="monaco-editor-container"
    :style="{ height: height }"
  />
</template>

<script>
import { markRaw } from 'vue'
import { $emit } from '../utils/gogocodeTransfer'
import { getMonacoScrollbarOptions, syncMonacoTheme } from '@/theme/monacoTheme'
import {
  buildQlCompletionItems,
  buildQlHover,
  buildQlSignatureHelp,
} from '@/utils/qlEditorIntelligence'
import {
  buildQlReferenceDecorations,
  collectQlScriptReferences,
  referenceTypeDisplay,
} from '@/utils/qlScriptReferences'
export default {
  name: 'MonacoEditor',
  props: {
    value: {
      type: String,
      default: '',
    },
    language: {
      type: String,
      default: 'json',
    },
    readOnly: {
      type: Boolean,
      default: false,
    },
    height: {
      type: String,
      default: '300px',
    },
    options: {
      type: Object,
      default: () => ({}),
    },
    // 引擎引用与函数目录。只在传入时启用默认 QL 智能提示，其他页面仍可通过
    // completionProvider / hoverProvider / signatureHelpProvider 定制差异。
    references: {
      type: Array,
      default: () => [],
    },
    referenceItems: {
      type: Array,
      default: () => [],
    },
    functions: {
      type: Array,
      default: () => [],
    },
    functionItems: {
      type: Array,
      default: () => [],
    },
    completionItems: {
      type: Array,
      default: () => [],
    },
    completionProvider: {
      type: Function,
      default: null,
    },
    hoverProvider: {
      type: Function,
      default: null,
    },
    signatureHelpProvider: {
      type: Function,
      default: null,
    },
    enableIntelligence: {
      type: Boolean,
      default: true,
    },
    showReferenceDecorations: {
      type: Boolean,
      default: true,
    },
    referenceVersions: {
      type: Object,
      default: () => ({}),
    },
    referenceCatalogMode: {
      type: String,
      default: 'LIVE',
    },
  },
  data() {
    return {
      editor: null,
      intelligenceDisposables: [],
      referenceCommandDisposable: null,
      referenceDecorationIds: [],
      // 防止 watch 在用户输入时重置内容 — 仅在父组件主动变更值时更新编辑器
      isInternalChange: false,
    }
  },
  watch: {
    value(val) {
      if (!this.editor) return
      // 仅外部值变化（非用户输入）才更新编辑器内容，防止光标跳位
      if (!this._internalChangeFlag) {
        const current = this.editor.getValue()
        if (current !== val) {
          // 使用 setModelContent 替换内容，保持光标位置不变
          const model = this.editor.getModel()
          model.setValue(val)
        }
      }
      this._internalChangeFlag = false
      this.$nextTick(() => this.refreshReferenceDecorations())
    },
    language(val) {
      if (this.editor && window.monaco) {
        window.monaco.editor.setModelLanguage(this.editor.getModel(), val)
        this.registerLanguage(window.monaco)
        this.registerIntelligenceProviders(window.monaco)
        this.refreshReferenceDecorations()
      }
    },
    readOnly(val) {
      if (this.editor) {
        this.editor.updateOptions({ readOnly: val })
      }
    },
    references: { deep: true, handler() { this.registerIntelligenceProviders(window.monaco); this.refreshReferenceDecorations() } },
    referenceItems: { deep: true, handler() { this.registerIntelligenceProviders(window.monaco); this.refreshReferenceDecorations() } },
    functions: { deep: true, handler() { this.registerIntelligenceProviders(window.monaco); this.refreshReferenceDecorations() } },
    functionItems: { deep: true, handler() { this.registerIntelligenceProviders(window.monaco); this.refreshReferenceDecorations() } },
    completionItems: { deep: true, handler() { this.registerIntelligenceProviders(window.monaco) } },
    referenceVersions: { deep: true, handler() { this.refreshReferenceDecorations() } },
    referenceCatalogMode() { this.refreshReferenceDecorations() },
    showReferenceDecorations() { this.refreshReferenceDecorations() },
  },
  async mounted() {
    window.addEventListener(
      'tianshu-theme-change',
      this.handleGlobalThemeChange
    )
    // 等待 monaco 加载（最多等待 10s 防死循环）
    let attempts = 0
    while (!window.monaco && attempts < 100) {
      await new Promise((resolve) => setTimeout(resolve, 100))
      attempts++
    }
    if (!window.monaco || !this.$refs.container) return
    this.registerQlExpressLanguage(window.monaco)
    this.registerSqlLanguage(window.monaco)
    this.registerLanguage(window.monaco)
    const editorTheme = this.syncGlobalTheme()

    const options = {
      value: this.value,
      language: this.language,
      theme: editorTheme,
      readOnly: this.readOnly,
      automaticLayout: true,
      fontSize: 13,
      fontFamily: "'Courier New', Consolas, monospace",
      lineNumbers: 'on',
      minimap: { enabled: false },
      scrollBeyondLastLine: false,
      wordWrap: 'on',
      tabSize: 2,
      insertSpaces: true,
      formatOnPaste: true,
      formatOnType: true,
      folding: true,
      lineDecorationsWidth: 4,
      lineNumbersMinChars: 4,
      renderLineHighlight: 'line',
      scrollbar: getMonacoScrollbarOptions(),
      padding: { top: 8, bottom: 8 },
      // 修复：启用 Tab 缩进，禁用 Ctrl+Space 默认补全冲突
      acceptSuggestionOnEnter: 'on',
      quickSuggestions: { other: true, comments: false, strings: false },
      suggestOnTriggerCharacters: true,
      parameterHints: { enabled: true },
      // 允许自定义快捷键不被拦截
      automaticallyFixSuggestions: false,
      ...this.options,
    }

    this.editor = markRaw(
      window.monaco.editor.create(this.$refs.container, options)
    )
    this.registerIntelligenceProviders(window.monaco)
    this.refreshReferenceDecorations()
    $emit(this, 'editor-ready', this.editor)

    // 内容变化时同步到父组件（使用 _internalChangeFlag 标记区分用户输入）
    this.editor.onDidChangeModelContent(() => {
      this._internalChangeFlag = true
      const newVal = this.editor.getValue()
      $emit(this, 'input', newVal)
      $emit(this, 'update:value', newVal)
      $emit(this, 'change', newVal)
    })

    // 格式化快捷键
    this.editor.addCommand(
      window.monaco.KeyMod.CtrlCmd | window.monaco.KeyCode.KeyS,
      () => {
        this.format()
      }
    )
  },
  beforeUnmount() {
    window.removeEventListener(
      'tianshu-theme-change',
      this.handleGlobalThemeChange
    )
    if (this.editor) {
      this.clearReferenceDecorations()
      this.editor.dispose()
      this.editor = null
    }
    this.disposeIntelligenceProviders()
  },
  methods: {
    normalizedReferenceItems() {
      return this.effectiveReferences().map((reference) => ({
        ...reference,
        varCode: reference.varCode || reference.refCode || reference.scriptName,
        varLabelText: reference.varLabelText || reference.varLabel || reference.refLabel?.label || reference.label,
        _varId: reference._varId != null ? reference._varId : reference.id,
        _refType: reference._refType || reference.refType,
        _ref: reference._ref || reference,
      })).filter(reference => reference.varCode && reference._varId != null && reference._refType)
    },
    clearReferenceDecorations() {
      if (!this.editor || !this.referenceDecorationIds.length) return
      if (typeof this.editor.deltaDecorations === 'function') {
        this.editor.deltaDecorations(this.referenceDecorationIds, [])
      }
      this.referenceDecorationIds = []
    },
    refreshReferenceDecorations() {
      if (!this.editor || this.language !== 'ql' || !this.showReferenceDecorations) {
        this.clearReferenceDecorations()
        return
      }
      const model = this.editor.getModel?.()
      if (!model || typeof model.getPositionAt !== 'function' || typeof this.editor.deltaDecorations !== 'function') return
      const matches = collectQlScriptReferences(
        this.editor.getValue?.() || this.value,
        this.normalizedReferenceItems(),
        this.effectiveFunctions()
      )
      const decorations = buildQlReferenceDecorations(
        matches,
        model,
        this.referenceVersions,
        this.referenceCatalogMode,
        { includeType: true }
      )
      this.referenceDecorationIds = this.editor.deltaDecorations(this.referenceDecorationIds, decorations)
    },
    registerLanguage(monaco) {
      if (this.language === 'ql') this.registerQlExpressLanguage(monaco)
      if (this.language === 'sql') this.registerSqlLanguage(monaco)
    },
    effectiveReferences() {
      return this.referenceItems.length ? this.referenceItems : this.references
    },
    effectiveFunctions() {
      return this.functionItems.length ? this.functionItems : this.functions
    },
    disposeIntelligenceProviders() {
      this.intelligenceDisposables.forEach((disposable) => {
        if (disposable && typeof disposable.dispose === 'function') disposable.dispose()
      })
      this.intelligenceDisposables = []
      if (this.referenceCommandDisposable && typeof this.referenceCommandDisposable.dispose === 'function') {
        this.referenceCommandDisposable.dispose()
      }
      this.referenceCommandDisposable = null
    },
    registerIntelligenceProviders(monaco) {
      this.disposeIntelligenceProviders()
      if (!this.enableIntelligence || !monaco?.languages || !this.editor) return

      const refs = this.effectiveReferences()
      const functions = this.effectiveFunctions()
      const isQl = this.language === 'ql'
      const hasCompletion = Boolean(this.completionProvider || this.completionItems.length || isQl && (refs.length || functions.length))
      const hasHover = Boolean(this.hoverProvider || isQl && refs.length)
      const hasSignatureHelp = Boolean(this.signatureHelpProvider || isQl && functions.length)
      if (!hasCompletion && !hasHover && !hasSignatureHelp) return
      const commandId = `tianshu.monaco-reference.${this.$.uid}`
      if (monaco.editor?.registerCommand) {
        this.referenceCommandDisposable = monaco.editor.registerCommand(
          commandId,
          (_accessor, reference) => $emit(this, 'reference-select', reference)
        )
      }
      const currentModel = (model) => model && this.editor && model === this.editor.getModel()
      const toRange = (model, position) => {
        const word = model.getWordUntilPosition
          ? model.getWordUntilPosition(position)
          : { startColumn: position.column, endColumn: position.column }
        return {
          startLineNumber: position.lineNumber,
          endLineNumber: position.lineNumber,
          startColumn: word.startColumn,
          endColumn: word.endColumn,
        }
      }
      const toSuggestion = (item, range) => ({
        ...item,
        range,
        detail: item.referenceType
          ? `${item.referenceType} ${item.refCode || item.label || ''} ${item.referenceName || ''} ${item.referenceVersion || '版本未提供'}`.trim()
          : item.detail,
        documentation: item.documentation && typeof item.documentation === 'string'
          ? { value: item.referenceType
              ? `${item.referenceType} ${item.refCode || item.label || ''} ${item.referenceName || ''} ${item.referenceVersion || '版本未提供'}\n${item.documentation}`
              : item.documentation }
          : item.documentation,
        kind: item.kind === 'FUNCTION'
          ? monaco.languages.CompletionItemKind?.Function || 1
          : monaco.languages.CompletionItemKind?.Field || 17,
        insertTextRules: item.snippet
          ? monaco.languages.CompletionItemInsertTextRule?.InsertAsSnippet
          : undefined,
        command: item.stableReference && monaco.editor?.registerCommand
          ? { id: commandId, title: '记录引擎引用', arguments: [item.stableReference] }
          : undefined,
      })

      if (hasCompletion && monaco.languages.registerCompletionItemProvider) {
        this.intelligenceDisposables.push(monaco.languages.registerCompletionItemProvider(this.language, {
          triggerCharacters: ['.'],
          provideCompletionItems: async (model, position) => {
            if (!currentModel(model)) return { suggestions: [] }
            const line = model.getLineContent ? model.getLineContent(position.lineNumber) : ''
            const linePrefix = line.slice(0, Math.max(0, position.column - 1))
            const context = { model, position, linePrefix, monaco, editor: this.editor }
            const custom = this.completionProvider
              ? await this.completionProvider(context)
              : null
            const items = custom
              ? (Array.isArray(custom) ? custom : custom.suggestions || [])
              : [
                  ...this.completionItems,
                  ...(isQl ? buildQlCompletionItems(refs, functions, linePrefix) : []),
                ]
            const range = toRange(model, position)
            return {
              ...(custom && !Array.isArray(custom) ? custom : {}),
              suggestions: items.map((item) => toSuggestion(item, item.range || range)),
            }
          },
        }))
      }

      if (hasHover && monaco.languages.registerHoverProvider) {
        this.intelligenceDisposables.push(monaco.languages.registerHoverProvider(this.language, {
          provideHover: async (model, position) => {
            if (!currentModel(model)) return null
            const context = { model, position, monaco, editor: this.editor }
            const custom = this.hoverProvider
              ? await this.hoverProvider(context)
              : buildQlHover(
                  model.getLineContent(position.lineNumber),
                  position.column,
                  refs
                )
            if (!custom) return null
            if (custom.contents) return custom
            const matchedReference = refs.find(reference => reference.refCode === custom.refCode)
            const version = matchedReference && (
              matchedReference.displayVersion
              ?? matchedReference.modelVersion
              ?? matchedReference.objectVersion
              ?? matchedReference.version
              ?? matchedReference._ref?.modelVersion
              ?? matchedReference._ref?.objectVersion
              ?? matchedReference.varObj?.currentVersion
              ?? matchedReference.varObj?.version
            )
            const versionLabel = version != null ? `v${version}` : '版本未提供'
            const typeLabel = referenceTypeDisplay({
              option: matchedReference || { _refType: custom.refType },
            })
            return {
              contents: [
                { value: `**${typeLabel} · ${custom.refCode || ''} · ${custom.label || custom.refCode || ''} · ${versionLabel}**` },
                { value: `数据类型：\`${custom.valueType || matchedReference?.varType || 'OBJECT'}\`` },
              ],
            }
          },
        }))
      }

      if (hasSignatureHelp && monaco.languages.registerSignatureHelpProvider) {
        this.intelligenceDisposables.push(monaco.languages.registerSignatureHelpProvider(this.language, {
          signatureHelpTriggerCharacters: ['(', ','],
          provideSignatureHelp: async (model, position) => {
            if (!currentModel(model)) return null
            const line = model.getLineContent ? model.getLineContent(position.lineNumber) : ''
            const linePrefix = line.slice(0, Math.max(0, position.column - 1))
            const context = { model, position, linePrefix, monaco, editor: this.editor }
            const signature = this.signatureHelpProvider
              ? await this.signatureHelpProvider(context)
              : buildQlSignatureHelp(linePrefix, functions)
            if (!signature) return null
            if (signature.value) return signature
            return {
              value: {
                signatures: [{ label: signature.label, parameters: signature.parameters }],
                activeSignature: 0,
                activeParameter: signature.activeParameter || 0,
              },
              dispose() {},
            }
          },
        }))
      }
    },
    handleGlobalThemeChange(event) {
      const scheme = event && event.detail && event.detail.colorScheme
      this.syncGlobalTheme(scheme)
    },
    syncGlobalTheme(colorScheme) {
      return syncMonacoTheme(window.monaco, { colorScheme })
    },
    registerQlExpressLanguage(monaco) {
      if (!monaco || this.language !== 'ql') return

      if (!monaco.__qlexpressLanguageRegistered) {
        const exists = monaco.languages
          .getLanguages()
          .some((lang) => lang.id === 'ql')
        if (!exists) {
          monaco.languages.register({
            id: 'ql',
            aliases: ['QLExpress', 'QL'],
          })
        }

        monaco.languages.setLanguageConfiguration('ql', {
          comments: {
            lineComment: '//',
            blockComment: ['/*', '*/'],
          },
          brackets: [
            ['{', '}'],
            ['[', ']'],
            ['(', ')'],
          ],
          autoClosingPairs: [
            { open: '{', close: '}' },
            { open: '[', close: ']' },
            { open: '(', close: ')' },
            { open: '"', close: '"' },
            { open: "'", close: "'" },
          ],
          surroundingPairs: [
            { open: '{', close: '}' },
            { open: '[', close: ']' },
            { open: '(', close: ')' },
            { open: '"', close: '"' },
            { open: "'", close: "'" },
          ],
        })

        monaco.languages.setMonarchTokensProvider('ql', {
          defaultToken: '',
          tokenPostfix: '.ql',
          keywords: [
            'if',
            'then',
            'else',
            'return',
            'for',
            'while',
            'break',
            'continue',
            'true',
            'false',
            'null',
            'new',
            'in',
            'and',
            'or',
            'not',
          ],
          builtins: [
            'sum',
            'count',
            'max',
            'min',
            'avg',
            'contains',
            'startsWith',
            'endsWith',
            'size',
            'length',
            'abs',
            'round',
            'floor',
            'ceil',
            'date',
            'now',
            'format',
            'println',
          ],
          operators: [
            '=',
            '>',
            '<',
            '!',
            '~',
            '?',
            ':',
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
            '&',
            '|',
            '^',
            '%',
          ],
          symbols: /[=><!~?:&|+*^%/-]+/,
          tokenizer: {
            root: [
              { include: '@whitespace' },
              { include: '@numbers' },
              { include: '@strings' },
              [/[{}()[\]]/, '@brackets'],
              [/[;,]/, 'delimiter'],
              [/[.]/, 'delimiter'],
              [
                /@symbols/,
                {
                  cases: {
                    '@operators': 'operator',
                    '@default': '',
                  },
                },
              ],
              [
                /[a-zA-Z_$][\w$]*(?=\s*\()/,
                {
                  cases: {
                    '@keywords': 'keyword',
                    '@builtins': 'predefined',
                    '@default': 'function',
                  },
                },
              ],
              [
                /[a-zA-Z_$][\w$]*/,
                {
                  cases: {
                    '@keywords': 'keyword',
                    '@default': 'identifier',
                  },
                },
              ],
            ],
            whitespace: [
              [/[ \t\r\n]+/, 'white'],
              [/\/\/.*$/, 'comment'],
              [/\/\*/, 'comment', '@comment'],
            ],
            comment: [
              [/[^*/]+/, 'comment'],
              [/\*\//, 'comment', '@pop'],
              [/./, 'comment'],
            ],
            numbers: [
              [/0[xX][0-9a-fA-F]+/, 'number'],
              [/\d+(\.\d+)?([eE][+-]?\d+)?/, 'number'],
            ],
            strings: [
              [/"([^"\\]|\\.)*$/, 'string.invalid'],
              [/'([^'\\]|\\.)*$/, 'string.invalid'],
              [/"/, 'string', '@stringDouble'],
              [/'/, 'string', '@stringSingle'],
            ],
            stringDouble: [
              [/[^\\"]+/, 'string'],
              [/\\./, 'string.escape'],
              [/"/, 'string', '@pop'],
            ],
            stringSingle: [
              [/[^\\']+/, 'string'],
              [/\\./, 'string.escape'],
              [/'/, 'string', '@pop'],
            ],
          },
        })

        monaco.__qlexpressLanguageRegistered = true
      }

    },
    registerSqlLanguage(monaco) {
      if (!monaco || this.language !== 'sql') return

      if (!monaco.__ruleEngineSqlLanguageRegistered) {
        const exists = monaco.languages
          .getLanguages()
          .some((lang) => lang.id === 'sql')
        if (!exists) {
          monaco.languages.register({
            id: 'sql',
            aliases: ['SQL'],
          })
        }

        monaco.languages.setLanguageConfiguration('sql', {
          comments: {
            lineComment: '--',
            blockComment: ['/*', '*/'],
          },
          brackets: [['(', ')']],
          autoClosingPairs: [
            { open: '(', close: ')' },
            { open: "'", close: "'" },
            { open: '"', close: '"' },
          ],
        })

        monaco.languages.setMonarchTokensProvider('sql', {
          defaultToken: '',
          tokenPostfix: '.sql',
          keywords: [
            'select',
            'from',
            'where',
            'and',
            'or',
            'not',
            'insert',
            'update',
            'delete',
            'join',
            'left',
            'right',
            'inner',
            'outer',
            'on',
            'group',
            'by',
            'order',
            'having',
            'limit',
            'offset',
            'as',
            'case',
            'when',
            'then',
            'else',
            'end',
            'distinct',
            'union',
            'all',
            'is',
            'null',
            'like',
            'in',
            'between',
            'exists',
          ],
          operators: [
            '=',
            '>',
            '<',
            '!',
            '~',
            '?',
            ':',
            '==',
            '<=',
            '>=',
            '!=',
            '<>',
            '+',
            '-',
            '*',
            '/',
            '%',
          ],
          symbols: /[=><!~?:+*%/-]+/,
          tokenizer: {
            root: [
              { include: '@whitespace' },
              { include: '@numbers' },
              { include: '@strings' },
              [/[(),.;]/, 'delimiter'],
              [
                /@symbols/,
                {
                  cases: {
                    '@operators': 'operator',
                    '@default': '',
                  },
                },
              ],
              [
                /[a-zA-Z_][\w$]*/,
                {
                  cases: {
                    '@keywords': 'keyword',
                    '@default': 'identifier',
                  },
                },
              ],
            ],
            whitespace: [
              [/[ \t\r\n]+/, 'white'],
              [/--.*$/, 'comment'],
              [/\/\*/, 'comment', '@comment'],
            ],
            comment: [
              [/[^*/]+/, 'comment'],
              [/\*\//, 'comment', '@pop'],
              [/./, 'comment'],
            ],
            numbers: [[/\d+(\.\d+)?([eE][+-]?\d+)?/, 'number']],
            strings: [
              [/"([^"\\]|\\.)*$/, 'string.invalid'],
              [/'([^'\\]|\\.)*$/, 'string.invalid'],
              [/"/, 'string', '@stringDouble'],
              [/'/, 'string', '@stringSingle'],
            ],
            stringDouble: [
              [/[^\\"]+/, 'string'],
              [/\\./, 'string.escape'],
              [/"/, 'string', '@pop'],
            ],
            stringSingle: [
              [/[^\\']+/, 'string'],
              [/\\./, 'string.escape'],
              [/'/, 'string', '@pop'],
            ],
          },
        })

        monaco.__ruleEngineSqlLanguageRegistered = true
      }

    },
    format() {
      if (this.editor) {
        this.editor.getAction('editor.action.formatDocument').run()
      }
    },
    focus() {
      if (this.editor) {
        this.editor.focus()
      }
    },
    getEditor() {
      return this.editor
    },
  },
  emits: ['input', 'editor-ready', 'update:value', 'change', 'reference-select'],
}
</script>

<style scoped>
.monaco-editor-container {
  width: 100%;
  min-width: 0;
  background: var(--tianshu-bg-soft);
  border: 1px solid var(--tianshu-border);
  border-radius: 4px;
  overflow: hidden;
}

/* 行内引擎引用使用主题语义色，保证浅色、深色和自定义主色下都有足够饱和度。 */
.monaco-editor-container :deep(.se-reference-type),
.monaco-editor-container :deep(.se-reference-code),
.monaco-editor-container :deep(.se-reference-name),
.monaco-editor-container :deep(.se-reference-version) {
  border: none;
  border-radius: 0;
  padding: 1px 5px;
  line-height: 1.35;
  font-weight: 700;
}

.monaco-editor-container :deep(.se-reference-type) {
  color: var(--tianshu-info-text);
  background: color-mix(in srgb, var(--el-color-primary) 20%, var(--tianshu-bg-surface));
  border-radius: 4px 0 0 4px;
}

.monaco-editor-container :deep(.se-reference-code) {
  color: var(--tianshu-primary-text);
  background: color-mix(in srgb, var(--el-color-primary) 32%, var(--tianshu-bg-surface));
  text-decoration: underline;
  text-decoration-color: var(--el-color-primary);
  text-decoration-thickness: 2px;
  text-underline-offset: 2px;
}

.monaco-editor-container :deep(.se-reference-name) {
  color: var(--tianshu-success-text);
  background: color-mix(in srgb, var(--el-color-success) 24%, var(--tianshu-bg-surface));
}

.monaco-editor-container :deep(.se-reference-version) {
  color: var(--tianshu-warning-text);
  background: color-mix(in srgb, var(--el-color-warning) 24%, var(--tianshu-bg-surface));
  border-radius: 0 4px 4px 0;
}
</style>
