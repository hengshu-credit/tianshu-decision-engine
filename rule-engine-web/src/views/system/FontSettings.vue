<template>
  <section class="font-settings" aria-label="引擎字体">
    <h3>引擎字体</h3>
    <p>选择后实时预览当前页面，保存配置后应用到全部页面。</p>
    <el-form label-position="top" :disabled="disabled || busy">
      <el-form-item label="字体来源">
        <el-select :model-value="font.source" aria-label="字体来源" @update:model-value="changeSource">
          <el-option label="默认 · 阿里妈妈方圆体 VF SemiBold" value="DEFAULT" />
          <el-option label="本机字体" value="LOCAL" />
          <el-option label="上传字体" value="UPLOAD" />
        </el-select>
      </el-form-item>
      <template v-if="font.source === 'LOCAL'">
        <el-form-item label="本机字体名称">
          <div class="font-controls">
            <el-select :model-value="font.family" aria-label="本机字体名称" filterable allow-create default-first-option placeholder="选择或输入本机字体名称" @update:model-value="family => update({ family })">
              <el-option v-for="item in localFonts" :key="item.postscriptName" :label="item.fullName" :value="item.postscriptName" />
            </el-select>
            <el-button :disabled="disabled || busy" @click="readLocalFonts">读取本机字体</el-button>
          </div>
          <small>读取需浏览器授权。其他设备未安装此字体时使用默认字体；需要所有设备显示一致时请上传字体文件。</small>
        </el-form-item>
      </template>
      <template v-if="font.source === 'UPLOAD'">
        <el-form-item label="字体文件">
          <el-upload :auto-upload="false" :show-file-list="false" accept=".ttf,.otf,.ttc,.otc" :disabled="disabled || busy" :on-change="upload"><el-button :loading="busy" :disabled="disabled || busy">上传字体文件</el-button></el-upload>
          <small>支持 TTF、OTF、TTC、OTC，最大 32 MB；请使用有权嵌入网页的字体。</small>
        </el-form-item>
        <div v-if="font.assetId && faces.length" class="font-pair">
          <el-form-item label="子字体"><el-select :model-value="font.face" aria-label="子字体" @update:model-value="face => update({ face, instance: 0 })"><el-option v-for="(item, index) in faces" :key="index" :label="item.name" :value="index" /></el-select></el-form-item>
          <el-form-item label="字体样式"><el-select :model-value="font.instance" aria-label="字体样式" @update:model-value="instance => update({ instance })"><el-option v-for="(item, index) in instances" :key="index" :label="item.name" :value="index" /></el-select></el-form-item>
        </div>
      </template>
    </el-form>
    <small v-if="previewLoading" role="status">正在加载字体预览…</small>
    <el-alert v-if="error" :title="error" type="warning" :closable="false" show-icon />
  </section>
</template>

<script>
import { getSystemFontMetadata, getSystemFontPreview, uploadSystemFont } from '@/api/systemSettings'
import { DEFAULT_ENGINE_FONT, normalizeEngineFont, previewEngineFont } from '@/system/engineFont'

export default {
  name: 'FontSettings',
  props: { modelValue: Object, disabled: Boolean, active: Boolean },
  emits: ['update:modelValue', 'busy', 'valid'],
  data: () => ({ localFonts: [], faces: [], busy: false, error: '', previewLoading: false, previewFailed: false, requestId: 0, previewUrl: '', previewKey: '', metadataId: '' }),
  computed: {
    font() { return normalizeEngineFont(this.modelValue) },
    instances() { return this.faces[this.font.face]?.instances || [] },
    valid() { return this.font.source === 'DEFAULT' || this.font.source === 'LOCAL' && Boolean(this.font.family.trim()) || this.font.source === 'UPLOAD' && !this.previewLoading && !this.previewFailed && Boolean(this.font.assetId === this.metadataId && this.instances[this.font.instance]) },
  },
  watch: {
    font: { deep: true, immediate: true, handler() { this.refreshPreview() } },
    active() { this.refreshPreview() },
    disabled() { this.refreshPreview() },
    valid: { immediate: true, handler(value) { this.$emit('valid', Boolean(value)) } },
  },
  beforeUnmount() { this.requestId++; previewEngineFont(null); if (this.previewUrl) URL.revokeObjectURL(this.previewUrl) },
  methods: {
    update(value) { this.$emit('update:modelValue', { ...this.font, ...value }) },
    changeSource(source) { this.$emit('update:modelValue', { ...DEFAULT_ENGINE_FONT, source }) },
    async readLocalFonts() {
      this.error = ''
      if (!window.queryLocalFonts) { this.error = '当前浏览器无法读取本机字体，请使用 HTTPS 或 localhost 下的 Chrome / Edge，也可直接输入字体名称或上传文件。'; return }
      try { this.localFonts = (await window.queryLocalFonts()).map(({ fullName, postscriptName }) => ({ fullName, postscriptName })).sort((a, b) => a.fullName.localeCompare(b.fullName, 'zh-CN')) }
      catch { this.error = '未获得本机字体读取权限。可重新授权、直接输入字体名称或上传文件。' }
    },
    async upload(file) {
      if (this.disabled || this.busy) return
      if (!/\.(ttf|otf|ttc|otc)$/i.test(file.name) || !file.raw?.size || file.raw.size > 32 * 1024 * 1024) { this.error = '请选择 32 MB 以内的 TTF、OTF、TTC 或 OTC 字体'; return }
      this.busy = true; this.$emit('busy', true); this.error = ''
      try {
        const { data } = await uploadSystemFont(file.raw)
        this.faces = data.faces; this.metadataId = data.id
        this.$emit('update:modelValue', { ...DEFAULT_ENGINE_FONT, source: 'UPLOAD', assetId: data.id })
      } catch (error) { this.error = error.message || '字体上传失败' }
      finally { this.busy = false; this.$emit('busy', false) }
    },
    async refreshPreview() {
      const requestId = ++this.requestId
      const font = this.font
      this.error = ''; this.previewFailed = false
      this.previewLoading = font.source === 'UPLOAD' && this.active && !this.disabled
      try {
        if (font.source === 'UPLOAD' && font.assetId) {
          if (this.metadataId !== font.assetId) {
            const { data } = await getSystemFontMetadata(font.assetId)
            if (requestId !== this.requestId) return
            this.faces = data.faces; this.metadataId = font.assetId
          }
          if (!this.active || this.disabled) { previewEngineFont(null); return }
          const key = `${font.assetId}:${font.face}`
          if (key !== this.previewKey) {
            const response = await getSystemFontPreview(font.assetId, font.face)
            if (requestId !== this.requestId) return
            if (this.previewUrl) URL.revokeObjectURL(this.previewUrl)
            this.previewUrl = URL.createObjectURL(response.data); this.previewKey = key
          }
          const loaded = await previewEngineFont({ ...font, previewUrl: this.previewUrl, coordinates: this.instances[font.instance]?.coordinates })
          if (loaded === false && requestId === this.requestId) { this.previewFailed = true; this.error = '浏览器无法加载此字体，已回退到默认字体，请换用兼容的字体文件。' }
        } else {
          const loaded = await previewEngineFont(!this.active || this.disabled || font.source === 'UPLOAD' ? null : font)
          if (loaded === false && requestId === this.requestId) this.error = '本机未找到可用的字体，已回退到默认字体，请检查名称或上传字体文件。'
        }
      } catch (error) { if (requestId === this.requestId) { this.previewFailed = true; this.error = error.message || '字体预览失败，请重新上传'; previewEngineFont(null) } }
      finally { if (requestId === this.requestId) this.previewLoading = false }
    },
  },
}
</script>

<style scoped>
.font-settings { border-top: 1px solid var(--tianshu-border-subtle); padding-top: 16px; margin-top: 16px; }
.font-settings h3 { margin: 0; font-size: 16px; }
.font-settings p, .font-settings small { color: var(--tianshu-text-tertiary); font-size: 12px; line-height: 1.6; }
.font-settings small { display: block; width: 100%; margin-top: 6px; }
.font-controls { display: flex; gap: 12px; width: 100%; flex-wrap: wrap; }
.font-controls .el-select { flex: 1; min-width: 180px; }
.font-pair { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
@media (max-width: 600px) { .font-pair { grid-template-columns: 1fr; gap: 0; } }
</style>
