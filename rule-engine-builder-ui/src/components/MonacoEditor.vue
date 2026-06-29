<template>
  <div ref="container" class="monaco-editor-container" :style="{ height: height }" />
</template>

<script>
export default {
  name: 'MonacoEditor',
  props: {
    value: {
      type: String,
      default: ''
    },
    language: {
      type: String,
      default: 'json'
    },
    theme: {
      type: String,
      default: 'vs'
    },
    readOnly: {
      type: Boolean,
      default: false
    },
    height: {
      type: String,
      default: '300px'
    },
    options: {
      type: Object,
      default: () => ({})
    }
  },
  data() {
    return {
      editor: null,
      // 防止 watch 在用户输入时重置内容 — 仅在父组件主动变更值时更新编辑器
      isInternalChange: false
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
    theme(val) {
      if (window.monaco) {
        window.monaco.editor.setTheme(val)
      }
    },
    readOnly(val) {
      if (this.editor) {
        this.editor.updateOptions({ readOnly: val })
      }
    }
  },
  async mounted() {
    // 等待 monaco 加载（最多等待 10s 防死循环）
    let attempts = 0
    while (!window.monaco && attempts < 100) {
      await new Promise(resolve => setTimeout(resolve, 100))
      attempts++
    }
    if (!window.monaco || !this.$refs.container) return
    this.registerQlExpressLanguage(window.monaco)

    const options = {
      value: this.value,
      language: this.language,
      theme: this.theme,
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
      scrollbar: {
        vertical: 'auto',
        horizontal: 'auto',
        verticalScrollbarSize: 10,
        horizontalScrollbarSize: 10
      },
      padding: { top: 8, bottom: 8 },
      // 修复：启用 Tab 缩进，禁用 Ctrl+Space 默认补全冲突
      acceptSuggestionOnEnter: 'on',
      quickSuggestions: { other: true, comments: false, strings: false },
      suggestOnTriggerCharacters: true,
      parameterHints: { enabled: true },
      // 允许自定义快捷键不被拦截
      automaticallyFixSuggestions: false,
      ...this.options
    }

    this.editor = window.monaco.editor.create(this.$refs.container, options)
    this.$emit('editor-ready', this.editor)
    this.$nextTick(() => {
      if (this.editor) {
        this.editor.layout()
      }
    })

    // 内容变化时同步到父组件（使用 _internalChangeFlag 标记区分用户输入）
    this.editor.onDidChangeModelContent(() => {
      this._internalChangeFlag = true
      const newVal = this.editor.getValue()
      this.$emit('input', newVal)
      this.$emit('change', newVal)
    })

    // 格式化快捷键
    this.editor.addCommand(window.monaco.KeyMod.CtrlCmd | window.monaco.KeyCode.KeyS, () => {
      this.format()
    })
  },
  beforeDestroy() {
    if (this.editor) {
      this.editor.dispose()
      this.editor = null
    }
  },
  methods: {
    registerQlExpressLanguage(monaco) {
      if (!monaco || this.language !== 'ql') return

      if (!monaco.__qlexpressLanguageRegistered) {
        const exists = monaco.languages.getLanguages().some(lang => lang.id === 'ql')
        if (!exists) {
          monaco.languages.register({
            id: 'ql',
            aliases: ['QLExpress', 'QL']
          })
        }

        monaco.languages.setLanguageConfiguration('ql', {
          comments: {
            lineComment: '//',
            blockComment: ['/*', '*/']
          },
          brackets: [
            ['{', '}'],
            ['[', ']'],
            ['(', ')']
          ],
          autoClosingPairs: [
            { open: '{', close: '}' },
            { open: '[', close: ']' },
            { open: '(', close: ')' },
            { open: '"', close: '"' },
            { open: "'", close: "'" }
          ],
          surroundingPairs: [
            { open: '{', close: '}' },
            { open: '[', close: ']' },
            { open: '(', close: ')' },
            { open: '"', close: '"' },
            { open: "'", close: "'" }
          ]
        })

        monaco.languages.setMonarchTokensProvider('ql', {
          defaultToken: '',
          tokenPostfix: '.ql',
          keywords: [
            'if', 'then', 'else', 'return', 'for', 'while', 'break', 'continue',
            'true', 'false', 'null', 'new', 'in', 'and', 'or', 'not'
          ],
          builtins: [
            'sum', 'count', 'max', 'min', 'avg', 'contains', 'startsWith',
            'endsWith', 'size', 'length', 'abs', 'round', 'floor', 'ceil',
            'date', 'now', 'format', 'println'
          ],
          operators: [
            '=', '>', '<', '!', '~', '?', ':', '==', '<=', '>=', '!=',
            '&&', '||', '+', '-', '*', '/', '&', '|', '^', '%'
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
              [/@symbols/, {
                cases: {
                  '@operators': 'operator',
                  '@default': ''
                }
              }],
              [/[a-zA-Z_$][\w$]*(?=\s*\()/, {
                cases: {
                  '@keywords': 'keyword',
                  '@builtins': 'predefined',
                  '@default': 'function'
                }
              }],
              [/[a-zA-Z_$][\w$]*/, {
                cases: {
                  '@keywords': 'keyword',
                  '@default': 'identifier'
                }
              }]
            ],
            whitespace: [
              [/[ \t\r\n]+/, 'white'],
              [/\/\/.*$/, 'comment'],
              [/\/\*/, 'comment', '@comment']
            ],
            comment: [
              [/[^*/]+/, 'comment'],
              [/\*\//, 'comment', '@pop'],
              [/./, 'comment']
            ],
            numbers: [
              [/0[xX][0-9a-fA-F]+/, 'number'],
              [/\d+(\.\d+)?([eE][+-]?\d+)?/, 'number']
            ],
            strings: [
              [/"([^"\\]|\\.)*$/, 'string.invalid'],
              [/'([^'\\]|\\.)*$/, 'string.invalid'],
              [/"/, 'string', '@stringDouble'],
              [/'/, 'string', '@stringSingle']
            ],
            stringDouble: [
              [/[^\\"]+/, 'string'],
              [/\\./, 'string.escape'],
              [/"/, 'string', '@pop']
            ],
            stringSingle: [
              [/[^\\']+/, 'string'],
              [/\\./, 'string.escape'],
              [/'/, 'string', '@pop']
            ]
          }
        })

        monaco.__qlexpressLanguageRegistered = true
      }

      if (!monaco.__qlexpressThemeRegistered) {
        monaco.editor.defineTheme('qlexpress-dark', {
          base: 'vs-dark',
          inherit: true,
          rules: [
            { token: 'keyword', foreground: 'C586C0', fontStyle: 'bold' },
            { token: 'function', foreground: 'DCDCAA' },
            { token: 'predefined', foreground: '4EC9B0' },
            { token: 'identifier', foreground: 'D4D4D4' },
            { token: 'number', foreground: 'B5CEA8' },
            { token: 'string', foreground: 'CE9178' },
            { token: 'string.escape', foreground: 'D7BA7D' },
            { token: 'comment', foreground: '6A9955', fontStyle: 'italic' },
            { token: 'operator', foreground: 'D4D4D4' },
            { token: 'delimiter', foreground: '858585' }
          ],
          colors: {
            'editor.background': '#1E1E1E',
            'editor.foreground': '#D4D4D4',
            'editorLineNumber.foreground': '#6E7681',
            'editorLineNumber.activeForeground': '#C9D1D9',
            'editorCursor.foreground': '#FFFFFF',
            'editor.selectionBackground': '#264F78',
            'editor.lineHighlightBackground': '#2A2D2E',
            'editorIndentGuide.background': '#404040',
            'editorIndentGuide.activeBackground': '#707070'
          }
        })
        monaco.__qlexpressThemeRegistered = true
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
    }
  }
}
</script>

<style scoped>
.monaco-editor-container {
  width: 100%;
  min-width: 0;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
}
</style>
