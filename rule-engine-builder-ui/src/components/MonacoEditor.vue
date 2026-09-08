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
  },
  data() {
    return {
      editor: null,
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
    },
    language(val) {
      if (this.editor && window.monaco) {
        window.monaco.editor.setModelLanguage(this.editor.getModel(), val)
      }
    },
    readOnly(val) {
      if (this.editor) {
        this.editor.updateOptions({ readOnly: val })
      }
    },
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
    $emit(this, 'editor-ready', this.editor)

    // 内容变化时同步到父组件（使用 _internalChangeFlag 标记区分用户输入）
    this.editor.onDidChangeModelContent(() => {
      this._internalChangeFlag = true
      const newVal = this.editor.getValue()
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
      this.editor.dispose()
      this.editor = null
    }
  },
  methods: {
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
  emits: ['input', 'editor-ready', 'update:value', 'change'],
}
</script>

<style scoped>
.monaco-editor-container {
  width: 100%;
  min-width: 0;
  border: 1px solid var(--tianshu-border);
  border-radius: 4px;
  overflow: hidden;
}
</style>
