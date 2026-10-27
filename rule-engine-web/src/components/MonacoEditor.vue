<template>
  <div
    ref="container"
    class="monaco-editor-container"
    :class="{ 'has-reference-decorations': referenceDecorationIds.length > 0 }"
    :aria-busy="loading"
    :style="{ height: height }"
    @mousedown.capture="focusReferenceCode"
    @dblclick.capture="focusReferenceCode"
    @mouseup="normalizeReferenceCaret"
  />
</template>

<script>
import { markRaw } from 'vue'
import { $emit } from '../utils/gogocodeTransfer'
import { getMonacoScrollbarOptions, syncMonacoTheme } from '@/theme/monacoTheme'
import { contentSourceIcon } from '@/utils/contentDisplay'
import { waitForMonaco } from '@/utils/monacoLoader'
import {
  buildQlCompletionItems,
  buildQlHover,
  buildQlSignatureHelp,
} from '@/utils/qlEditorIntelligence'
import {
  buildQlReferenceDecorations,
  collectQlScriptReferences,
  createQlReferenceCatalog,
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
    versionReferences: { type: Array, default: () => [] },
    // 设计器已解析时共用结果，避免父子组件各扫描一次脚本。
    referenceMatches: { type: Array, default: null },
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
      loading: true,
      loadController: null,
      intelligenceDisposables: [],
      referenceCommandDisposable: null,
      referenceDecorationIds: [],
      movingReferenceCaret: false,
      // 防止 watch 在用户输入时重置内容 — 仅在父组件主动变更值时更新编辑器
      isInternalChange: false,
    }
  },
  computed: {
    referenceCatalog() { return markRaw(createQlReferenceCatalog(this.normalizedReferenceItems(), this.effectiveFunctions())) },
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
      this.queueReferenceDecorations()
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
    references: { deep: true, handler() { this.registerIntelligenceProviders(window.monaco); this.queueReferenceDecorations() } },
    referenceItems: { deep: true, handler() { this.registerIntelligenceProviders(window.monaco); this.queueReferenceDecorations() } },
    functions: { deep: true, handler() { this.registerIntelligenceProviders(window.monaco); this.queueReferenceDecorations() } },
    functionItems: { deep: true, handler() { this.registerIntelligenceProviders(window.monaco); this.queueReferenceDecorations() } },
    completionItems: { deep: true, handler() { this.registerIntelligenceProviders(window.monaco) } },
    referenceVersions: { deep: true, handler() { this.queueReferenceDecorations() } },
    referenceCatalogMode() { this.queueReferenceDecorations() },
    versionReferences: { deep: true, handler() { this.queueReferenceDecorations() } },
    referenceMatches() { this.queueReferenceDecorations() },
    showReferenceDecorations() { this.queueReferenceDecorations() },
  },
  async mounted() {
    window.addEventListener(
      'tianshu-theme-change',
      this.handleGlobalThemeChange
    )
    this.loadController = markRaw(new AbortController())
    const monaco = window.monaco || await waitForMonaco(this.loadController.signal)
    this.loading = false
    if (!this.loadController.signal.aborted && !monaco) $emit(this, 'load-error')
    if (this.loadController.signal.aborted || !monaco || typeof window === 'undefined' || !this.$refs.container) return
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
      fixedOverflowWidgets: true,
      // 允许自定义快捷键不被拦截
      automaticallyFixSuggestions: false,
      ...this.options,
    }

    this.editor = markRaw(
      window.monaco.editor.create(this.$refs.container, options)
    )
    this.registerIntelligenceProviders(window.monaco)
    this.refreshReferenceDecorations()

    // 内容变化时同步到父组件（使用 _internalChangeFlag 标记区分用户输入）
    this.editor.onDidChangeModelContent(() => {
      this._internalChangeFlag = true
      const newVal = this.editor.getValue()
      $emit(this, 'input', newVal)
      $emit(this, 'update:value', newVal)
      $emit(this, 'change', newVal)
      this.queueReferenceDecorations()
    })

    // 格式化快捷键
    this.editor.addCommand(
      window.monaco.KeyMod.CtrlCmd | window.monaco.KeyCode.KeyS,
      () => {
        this.format()
      }
    )
    // 父组件会在 ready 中插入加载期间排队的字段，必须先安装内容监听。
    $emit(this, 'editor-ready', this.editor)
  },
  beforeUnmount() {
    this.loadController?.abort()
    this.cancelReferenceDecorations()
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
    focusReferenceCode(event) {
      if (!this.editor || this.movingReferenceCaret || event.button !== 0) return
      let metadata = event.target?.closest?.('.content-ref__metadata')
      let end
      if (!metadata) {
        for (const edge of this.$refs.container.querySelectorAll('.view-lines .content-ref__first, .view-lines .content-ref__last')) {
          const rect = edge.getBoundingClientRect()
          if (!rect.width || event.clientY < rect.top || event.clientY > rect.bottom) continue
          const before = edge.classList.contains('content-ref__first') && event.clientX < rect.left && event.clientX >= rect.left - 3
          const after = edge.classList.contains('content-ref__last') && event.clientX >= rect.right && event.clientX <= rect.right + 3
          if (before || after) { metadata = edge; end = after; break }
        }
      }
      if (!metadata) return
      const range = [...metadata.classList].map(name => /^content-ref__range-(\d+)-(\d+)$/.exec(name)).find(Boolean)
      if (!range) return
      end ??= metadata.classList.contains('content-ref__edge-end')
      event.preventDefault()
      event.stopPropagation()
      this.moveToReferenceCode(range[0], Number(range[end ? 2 : 1]), end)
    },
    async normalizeReferenceCaret(event) {
      if (this.movingReferenceCaret || event.button !== 0
        || event.target?.closest?.('.content-ref__segment, .monaco-hover, .suggest-widget, .scrollbar')) return
      await this.$nextTick()
      if (!this.editor?.hasTextFocus() || this.editor.getSelections().length !== 1 || !this.editor.getSelection().isEmpty()) return
      const offset = this.editor.getModel().getOffsetAt(this.editor.getPosition())
      for (const code of this.$refs.container.querySelectorAll('.view-lines .content-ref__code:not(.content-ref__metadata)')) {
        const range = [...code.classList].map(name => /^content-ref__range-(\d+)-(\d+)$/.exec(name)).find(Boolean)
        if (range && (offset === Number(range[1]) || offset === Number(range[2]))) {
          this.moveToReferenceCode(range[0], offset, offset === Number(range[2]))
          return
        }
      }
    },
    moveToReferenceCode(rangeClass, offset, end) {
      if (!this.editor || this.movingReferenceCaret) return
      const fragments = rangeClass ? [...this.$refs.container.querySelectorAll(`.view-lines .${rangeClass}.content-ref__code:not(.content-ref__metadata)`)]
        .filter(element => element.textContent && element.getBoundingClientRect().width > 0) : []
      const code = end ? fragments.at(-1) : fragments[0]
      this.movingReferenceCaret = true
      try {
        this.editor.focus()
        if (!code) {
          this.editor.setPosition(this.editor.getModel().getPositionAt(offset))
          return
        }
        // 源码列号在注入文本两侧相同，转发到可见编码边缘，让 Monaco 自己确定视图光标位置。
        const rect = code.getBoundingClientRect()
        for (const type of ['mousedown', 'mouseup']) code.dispatchEvent(new MouseEvent(type, {
          bubbles: true, cancelable: true, view: window, button: 0, buttons: type === 'mousedown' ? 1 : 0,
          detail: 1, clientX: end ? rect.right - 0.5 : rect.left + 0.5, clientY: rect.top + rect.height / 2,
        }))
      } finally {
        this.movingReferenceCaret = false
      }
    },
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
    cancelReferenceDecorations() {
      if (this._referenceDecorationFrame != null) window.cancelAnimationFrame(this._referenceDecorationFrame)
      this._referenceDecorationFrame = null
    },
    queueReferenceDecorations() {
      if (!this.editor || this.language !== 'ql' || !this.showReferenceDecorations) {
        this.cancelReferenceDecorations()
        this.clearReferenceDecorations()
        return
      }
      if (this._referenceDecorationFrame != null) return
      this._referenceDecorationFrame = window.requestAnimationFrame(() => {
        this._referenceDecorationFrame = null
        this.refreshReferenceDecorations()
      })
    },
    refreshReferenceDecorations() {
      this.cancelReferenceDecorations()
      if (!this.editor || this.language !== 'ql' || !this.showReferenceDecorations) {
        this.clearReferenceDecorations()
        return
      }
      const model = this.editor.getModel?.()
      if (!model || typeof model.getPositionAt !== 'function' || typeof this.editor.deltaDecorations !== 'function') return
      const matches = this.referenceMatches || collectQlScriptReferences(
        this.editor.getValue?.() || this.value,
        [], [], [], this.versionReferences, this.referenceCatalog
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
      const referenceDetails = item => {
        const version = this.referenceVersions[item.referenceVersionKey]?.version
        return [item.referenceContentType || '引用', item.referenceDataType || item.referenceType || '?', item.referenceName || item.label,
          item.refCode || item.label, version != null ? `v${version}` : item.referenceVersion || '—'].join(' · ')
      }
      const toSuggestion = (item, range) => ({
        ...item,
        range,
        detail: item.referenceType
          ? referenceDetails(item)
          : item.detail,
        documentation: item.documentation && typeof item.documentation === 'string'
          ? { value: item.referenceType
              ? `${referenceDetails(item)}\n${item.documentation}`
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
            // 已渲染的引用由编码装饰统一提供完整提示，避免与通用悬浮信息重复。
            if (!this.hoverProvider && this.showReferenceDecorations && model.getDecorationsInRange?.({
              startLineNumber: position.lineNumber, endLineNumber: position.lineNumber,
              startColumn: position.column, endColumn: position.column,
            }).some(decoration => decoration.options.description === 'engine-content-code')) return null
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
            const versionLabel = version != null ? `v${version}` : ''
            const typeLabel = referenceTypeDisplay({
              option: matchedReference || { _refType: custom.refType },
            })
            return {
              contents: [
                { value: `**${[contentSourceIcon({ kind: 'REFERENCE', refType: custom.refType || 'VARIABLE' }), typeLabel,
                  custom.label || custom.refCode, custom.refCode, versionLabel || '—'].join(' · ')}**` },
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
  emits: ['input', 'editor-ready', 'load-error', 'update:value', 'change', 'reference-select'],
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

















</style>
