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
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
}
</style>