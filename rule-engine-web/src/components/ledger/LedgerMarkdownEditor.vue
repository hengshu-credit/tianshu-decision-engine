<template>
  <div class="ledger-markdown-editor" :aria-busy="loading">
    <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button link @click="initialize">重试</el-button></el-alert>
    <div ref="editor" />
  </div>
</template>
<script>
import { markRaw } from 'vue'
import { Vditor, loadLedgerMarkdown, ledgerEditorCdn } from '@/utils/ledgerMarkdown'
import { uploadLedgerAsset } from '@/api/ledger'

export default {
  name: 'LedgerMarkdownEditor',
  props: { modelValue: { type: String, default: '' } },
  emits: ['update:modelValue', 'asset-uploaded', 'dirty'],
  data: () => ({ editor: null, loading: false, error: '', generation: 0, updating: false }),
  watch: { modelValue(value) { if (this.editor && !this.loading && this.editor.getValue() !== value) { this.updating = true; this.editor.setValue(value || ''); this.updating = false } } },
  mounted() { this.initialize(); this.$refs.editor.addEventListener('input', this.handleInput, true); window.addEventListener('tianshu-theme-change', this.syncTheme) },
  beforeUnmount() { this.generation++; this.$refs.editor?.removeEventListener('input', this.handleInput, true); window.removeEventListener('tianshu-theme-change', this.syncTheme); this.editor?.destroy(); this.editor = null },
  methods: {
    async initialize() {
      const generation = ++this.generation
      this.loading = true; this.error = ''
      try {
        await loadLedgerMarkdown()
        if (generation !== this.generation) return
        this.editor?.destroy()
        this.editor = markRaw(new Vditor(this.$refs.editor, {
          cdn: ledgerEditorCdn, lang: 'zh_CN', mode: 'wysiwyg', minHeight: 450, cache: { enable: false },
          value: this.modelValue, placeholder: '记录背景、调整过程和结论，可直接粘贴截图。',
          toolbar: ['headings', 'bold', 'italic', 'strike', '|', 'list', 'ordered-list', 'check', 'quote', '|', 'table', 'code', 'link', 'upload', '|', 'undo', 'redo', 'edit-mode'],
          toolbarConfig: { pin: false }, preview: { hljs: { enable: false }, markdown: { sanitize: true }, actions: [] }, image: { isPreview: false },
          upload: { accept: 'image/png,image/jpeg,image/webp', handler: this.upload },
          input: value => { if (!this.updating) this.$emit('update:modelValue', value) },
          after: () => { if (generation !== this.generation) return; this.loading = false; this.syncTheme(); this.$refs.editor.querySelectorAll('[contenteditable=true]').forEach(element => { element.setAttribute('aria-label', 'Markdown 正文'); element.setAttribute('role', 'textbox') }) },
        }))
      } catch (error) { if (generation === this.generation) { this.error = error.message; this.loading = false } }
    },
    syncTheme() { if (this.editor && !this.loading) { const dark = document.documentElement.dataset.theme === 'dark'; this.editor.setTheme(dark ? 'dark' : 'classic', dark ? 'dark' : 'light') } },
    handleInput(event) { if (!this.loading && !this.updating && event.target.closest?.('[contenteditable=true]')) this.$emit('dirty') },
    getValue() { return this.editor && !this.loading ? this.editor.getValue() : null },
    insert(value) { if (this.editor && !this.loading) { this.editor.insertValue(value, true); this.$emit('dirty') } },
    insertAsset(asset) { if (asset.scanStatus !== 'CLEAN') return; this.insert(`\n![图片](/api/rule/ledger/assets/${asset.id}/content)\n`) },
    async upload(files) {
      const generation = this.generation
      try {
        for (const file of Array.from(files)) {
          if (!/\.(png|jpe?g|webp)$/i.test(file.name) || file.size > 5 * 1024 * 1024) return '仅支持 5 MB 以内的 PNG、JPEG、WebP 图片'
          const response = await uploadLedgerAsset(file)
          if (generation !== this.generation) return null
          this.$emit('asset-uploaded', response.data)
          if (response.data.scanStatus === 'CLEAN') this.insertAsset(response.data)
          else this.$message.warning(response.data.scanMessage || '素材已私有暂存，安全检测通过后才能使用')
        }
        return null
      } catch (error) { return error.message || '图片上传失败' }
    },
  },
}
</script>
