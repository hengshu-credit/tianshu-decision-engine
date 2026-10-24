<template>
  <section class="watermark-settings" aria-labelledby="watermark-settings-title">
    <header class="watermark-settings-heading">
      <div><h3 id="watermark-settings-title">页面水印</h3><p>调整会直接在当前页面预览，点击“保存配置”后对所有登录用户生效。</p></div>
      <el-switch :model-value="form.watermarkEnabled" :disabled="disabled" aria-label="开启页面水印" @update:model-value="value => update('watermarkEnabled', value)" />
    </header>
      <el-form label-position="top" :disabled="disabled">
        <el-form-item label="水印内容" :error="form.watermarkContent.trim() ? '' : '请输入水印内容'" required>
          <el-input :model-value="form.watermarkContent" type="textarea" :rows="3" maxlength="200" show-word-limit aria-label="水印内容" @update:model-value="value => update('watermarkContent', value)" />
          <div class="watermark-fields"><span>追加动态字段</span><el-button v-for="field in fields" :key="field" size="small" :disabled="disabled || tokenContent(field).length > 200" @click="update('watermarkContent', tokenContent(field))">{{ field }}</el-button></div>
          <small>支持自定义文字和换行。动态字段自动替换为当前系统名称、登录账号及毫秒级登录时间。</small>
        </el-form-item>
        <el-form-item label="透明度">
          <div class="watermark-transparency">
            <el-slider :model-value="form.watermarkTransparency" :min="0" :max="100" :format-tooltip="value => value + '%'" aria-label="水印透明度" @update:model-value="value => update('watermarkTransparency', value)" />
            <el-input-number :model-value="form.watermarkTransparency" :min="0" :max="100" :precision="0" controls-position="right" aria-label="水印透明度百分比" @update:model-value="value => update('watermarkTransparency', value ?? 88)" /><span>%</span>
          </div>
          <small>数值越大，水印越淡；0% 为不透明，100% 为完全透明。</small>
        </el-form-item>
        <div class="watermark-selects">
          <el-form-item label="排列方式"><el-select :model-value="form.watermarkLayout" aria-label="水印排列方式" @update:model-value="value => update('watermarkLayout', value)"><el-option v-for="item in layouts" :key="item.value" :value="item.value" :label="item.label" /></el-select></el-form-item>
          <el-form-item label="密度"><el-select :model-value="form.watermarkDensity" aria-label="水印密度" @update:model-value="value => update('watermarkDensity', value)"><el-option v-for="item in densities" :key="item.value" :value="item.value" :label="item.label" /></el-select></el-form-item>
        </div>
      </el-form>
  </section>
</template>

<script>
import { resolveWatermarkSettings, WATERMARK_LAYOUTS, WATERMARK_DENSITIES } from '@/system/watermark'

export default {
  name: 'WatermarkSettings',
  props: { modelValue: { type: Object, required: true }, disabled: Boolean },
  emits: ['update:modelValue'],
  data: () => ({ fields: ['系统名称', '用户名', '登录时间'], layouts: WATERMARK_LAYOUTS, densities: WATERMARK_DENSITIES }),
  computed: {
    form() { return { ...this.modelValue, ...resolveWatermarkSettings(this.modelValue) } },
  },
  methods: {
    update(field, value) { if (!this.disabled) this.$emit('update:modelValue', { ...this.form, [field]: value }) },
    tokenContent(field) { const content = this.form.watermarkContent; return `${content}${content && !/\s$/.test(content) ? ' ' : ''}{${field}}` },
  },
}
</script>

<style scoped lang="scss">
.watermark-settings { max-width: 800px; border-top: 1px solid var(--tianshu-border-subtle); margin-top: 24px; padding-top: 24px; }
.watermark-settings-heading { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 16px; margin-bottom: 16px; }
.watermark-settings h3 { margin: 0; font-size: 16px; }
.watermark-settings p { margin: 8px 0 0; color: var(--tianshu-text-secondary); font-size: 13px; }
.watermark-fields { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-top: 12px; }
.watermark-fields span { color: var(--tianshu-text-tertiary); font-size: 12px; }
.watermark-fields .el-button + .el-button { margin-left: 0; }
.watermark-transparency { display: flex; align-items: center; gap: 16px; width: 100%; }
.watermark-transparency .el-slider { flex: 1; min-width: 0; }
.watermark-transparency .el-input-number { width: 96px; flex: none; }
.watermark-selects { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 16px; }
.watermark-settings small { display: block; margin-top: 8px; font-size: 12px; color: var(--tianshu-text-tertiary); line-height: 1.6; }
@media (max-width: 480px) {
  .watermark-selects { grid-template-columns: minmax(0, 1fr); gap: 0; }
  .watermark-transparency { flex-wrap: wrap; gap: 8px; }
  .watermark-transparency .el-slider { flex-basis: 100%; margin: 0 6px; }
  .watermark-transparency .el-input-number { width: min(96px, calc(100% - 24px)); }
}
</style>
