<template>
  <el-drawer
    :model-value="modelValue"
    class="theme-settings-drawer"
    title="主题设置"
    direction="rtl"
    :size="drawerSize"
    :before-close="handleBeforeClose"
    :lock-scroll="false"
    :modal="false"
    append-to-body
  >
    <div class="theme-settings">
      <section class="theme-section" aria-labelledby="theme-style-title">
        <h3 id="theme-style-title">整体风格</h3>
        <div class="option-grid option-grid--two">
          <button
            v-for="option in schemeOptions"
            :key="option.value"
            type="button"
            class="preview-card"
            :class="{ 'is-active': draft.colorScheme === option.value }"
            :data-theme-scheme="option.value"
            :aria-label="option.label"
            :aria-pressed="draft.colorScheme === option.value"
            @click="patchDraft({ colorScheme: option.value })"
          >
            <span class="style-preview" :class="option.previewClass">
              <span class="style-preview__sidebar" />
              <span class="style-preview__body">
                <span />
                <span />
              </span>
            </span>
            <span>{{ option.label }}</span>
            <span
              v-if="draft.colorScheme === option.value"
              class="selection-check"
              aria-hidden="true"
            >
              <el-icon><el-icon-check /></el-icon>
            </span>
          </button>
        </div>
      </section>

      <section class="theme-section" aria-labelledby="theme-accent-title">
        <h3 id="theme-accent-title">主题色</h3>
        <p class="section-hint">选择预设，或配置自己的纯色与渐变</p>
        <div class="accent-mode-control" aria-label="主题色来源">
          <button
            v-for="option in accentModeOptions"
            :key="option.value"
            type="button"
            :class="{ 'is-active': draft.accentMode === option.value }"
            :data-accent-mode="option.value"
            :aria-pressed="draft.accentMode === option.value"
            @click="patchDraft({ accentMode: option.value })"
          >
            {{ option.label }}
          </button>
        </div>

        <div v-if="draft.accentMode === 'PRESET'" class="accent-editor">
          <div class="swatch-group">
            <span class="swatch-group__label">纯色预设</span>
            <div class="swatch-grid">
              <button
                v-for="preset in solidPresets"
                :key="preset.id"
                type="button"
                class="theme-swatch"
                :class="{ 'is-active': draft.accentPreset === preset.id }"
                :data-accent="preset.id"
                :data-accent-kind="preset.kind"
                :aria-label="preset.name"
                :aria-pressed="draft.accentPreset === preset.id"
                :style="swatchStyle(preset)"
                @click="patchDraft({ accentMode: 'PRESET', accentPreset: preset.id })"
              >
                <el-icon v-if="draft.accentPreset === preset.id">
                  <el-icon-check />
                </el-icon>
              </button>
            </div>
          </div>
          <div class="swatch-group">
            <span class="swatch-group__label">渐变预设</span>
            <div class="gradient-grid">
              <button
                v-for="preset in gradientPresets"
                :key="preset.id"
                type="button"
                class="gradient-swatch"
                :class="{ 'is-active': draft.accentPreset === preset.id }"
                :data-accent="preset.id"
                :data-accent-kind="preset.kind"
                :aria-label="preset.name"
                :aria-pressed="draft.accentPreset === preset.id"
                :style="swatchStyle(preset)"
                @click="patchDraft({ accentMode: 'PRESET', accentPreset: preset.id })"
              >
                <span>{{ preset.name }}</span>
                <el-icon v-if="draft.accentPreset === preset.id">
                  <el-icon-check />
                </el-icon>
              </button>
            </div>
          </div>
        </div>

        <div
          v-else-if="draft.accentMode === 'CUSTOM_SOLID'"
          class="accent-editor custom-accent-panel"
        >
          <div class="custom-color-row">
            <span>自定义主题色</span>
            <div class="custom-color-control">
              <el-color-picker
                :model-value="draft.customSolidColor"
                :show-alpha="false"
                aria-label="自定义主题色"
                @update:model-value="updateCustomSolidColor"
              />
              <code>{{ draft.customSolidColor }}</code>
            </div>
          </div>
          <div
            class="custom-accent-preview"
            :style="{ background: customAccent.background }"
          >
            <span :style="{ color: customAccent.foreground }">实时主题预览</span>
          </div>
          <p class="custom-accent-note">辅助色会自动从主题色生成，并同步适配提示与操作状态。</p>
        </div>

        <div v-else class="accent-editor custom-accent-panel">
          <div class="custom-color-row">
            <span>渐变色数量</span>
            <div class="segmented-control" aria-label="渐变色数量">
              <button
                v-for="count in [2, 3]"
                :key="count"
                type="button"
                :class="{ 'is-active': draft.customGradientColors.length === count }"
                :data-gradient-count="count"
                :aria-pressed="draft.customGradientColors.length === count"
                @click="setGradientColorCount(count)"
              >
                {{ count }} 色
              </button>
            </div>
          </div>
          <div class="gradient-color-list">
            <div
              v-for="(color, index) in draft.customGradientColors"
              :key="index"
              class="gradient-color-item"
              :data-custom-gradient-color="index"
            >
              <span>颜色 {{ index + 1 }}</span>
              <div class="custom-color-control">
                <el-color-picker
                  :model-value="color"
                  :show-alpha="false"
                  :aria-label="'渐变颜色 ' + (index + 1)"
                  @update:model-value="updateGradientColor(index, $event)"
                />
                <code>{{ color }}</code>
              </div>
            </div>
          </div>
          <div class="custom-color-row">
            <span>渐变方式</span>
            <div class="segmented-control" aria-label="渐变方式">
              <button
                v-for="option in gradientTypeOptions"
                :key="option.value"
                type="button"
                :class="{ 'is-active': draft.customGradientType === option.value }"
                :data-gradient-type="option.value"
                :aria-pressed="draft.customGradientType === option.value"
                @click="patchDraft({ customGradientType: option.value })"
              >
                {{ option.label }}
              </button>
            </div>
          </div>
          <div
            v-if="draft.customGradientType === 'LINEAR'"
            class="custom-color-row"
          >
            <span>渐变角度</span>
            <el-input-number
              :model-value="draft.customGradientAngle"
              :min="0"
              :max="360"
              :step="15"
              controls-position="right"
              aria-label="渐变角度"
              @change="updateGradientAngle"
            />
          </div>
          <div
            class="custom-accent-preview"
            :style="{ background: customAccent.background }"
          >
            <span :style="{ color: customAccent.foreground }">实时渐变预览</span>
          </div>
        </div>
      </section>

      <section class="theme-section" aria-labelledby="navigation-layout-title">
        <h3 id="navigation-layout-title">菜单位置</h3>
        <p class="section-hint">一级菜单始终平铺，顶部空间不足时可横向滚动</p>
        <div class="option-grid option-grid--two">
          <button
            v-for="option in navigationOptions"
            :key="option.value"
            type="button"
            class="preview-card preview-card--compact"
            :class="{ 'is-active': draft.navigationLayout === option.value }"
            :data-navigation-layout="option.value"
            :aria-label="option.label"
            :aria-pressed="draft.navigationLayout === option.value"
            @click="patchDraft({ navigationLayout: option.value })"
          >
            <span class="navigation-preview" :class="option.previewClass">
              <i />
              <span />
            </span>
            <span>{{ option.label }}</span>
            <span
              v-if="draft.navigationLayout === option.value"
              class="selection-check"
              aria-hidden="true"
            >
              <el-icon><el-icon-check /></el-icon>
            </span>
          </button>
        </div>
      </section>

      <section class="theme-section" aria-labelledby="sidebar-style-title">
        <h3 id="sidebar-style-title">导航栏风格</h3>
        <div class="option-grid option-grid--two">
          <button
            v-for="option in sidebarOptions"
            :key="option.value"
            type="button"
            class="preview-card preview-card--compact"
            :class="{ 'is-active': draft.sidebarTheme === option.value }"
            :data-sidebar-theme="option.value"
            :aria-label="option.label"
            :aria-pressed="draft.sidebarTheme === option.value"
            @click="patchDraft({ sidebarTheme: option.value })"
          >
            <span class="sidebar-preview" :class="option.previewClass">
              <span />
              <i />
              <i />
              <i />
            </span>
            <span>{{ option.label }}</span>
            <span
              v-if="draft.sidebarTheme === option.value"
              class="selection-check"
              aria-hidden="true"
            >
              <el-icon><el-icon-check /></el-icon>
            </span>
          </button>
        </div>
      </section>

      <section class="theme-section" aria-labelledby="content-layout-title">
        <h3 id="content-layout-title">内容区域</h3>
        <div class="setting-row">
          <span>内容区宽度</span>
          <div class="segmented-control" aria-label="内容区宽度">
            <button
              v-for="option in contentWidthOptions"
              :key="option.value"
              type="button"
              :class="{ 'is-active': draft.contentWidth === option.value }"
              :data-content-width="option.value"
              :aria-pressed="draft.contentWidth === option.value"
              @click="patchDraft({ contentWidth: option.value })"
            >
              {{ option.label }}
            </button>
          </div>
        </div>
      </section>

      <section class="theme-section" aria-labelledby="table-scroll-title">
        <h3 id="table-scroll-title">列表表格</h3>
        <div class="setting-row">
          <span>滚动方式</span>
          <div class="segmented-control" aria-label="列表表格滚动方式">
            <button
              v-for="option in tableScrollOptions"
              :key="option.value"
              type="button"
              :class="{ 'is-active': draft.tableScrollMode === option.value }"
              :data-table-scroll-mode="option.value"
              :aria-pressed="draft.tableScrollMode === option.value"
              @click="patchDraft({ tableScrollMode: option.value })"
            >
              {{ option.label }}
            </button>
          </div>
        </div>
        <p class="section-hint">表格内滚动时，管理列表占满可用高度，表头和底部分页保持可见；随内容增高时，滚动整个内容区。</p>
      </section>

      <section class="theme-section" aria-labelledby="other-settings-title">
        <h3 id="other-settings-title">其他设置</h3>
        <label class="setting-row">
          <span>色弱模式</span>
          <el-switch
            v-model="draft.colorWeak"
            aria-label="色弱模式"
            @change="emitPreview"
          />
        </label>
      </section>

      <button
        type="button"
        class="restore-button"
        data-action="restore-default"
        :disabled="saving"
        @click="restoreDefault"
      >
        <el-icon><el-icon-refresh-left /></el-icon>
        <span>恢复默认</span>
      </button>
    </div>

    <template #footer>
      <div class="drawer-actions">
        <button
          type="button"
          class="drawer-button drawer-button--secondary"
          data-action="cancel"
          :disabled="saving"
          @click="requestClose"
        >
          取消
        </button>
        <button
          type="button"
          class="drawer-button drawer-button--primary"
          data-action="save"
          :disabled="saving"
          @click="submit"
        >
          {{ saving ? '保存中…' : '保存设置' }}
        </button>
      </div>
    </template>
  </el-drawer>
</template>

<script>
import {
  Check as ElIconCheck,
  RefreshLeft as ElIconRefreshLeft,
} from '@element-plus/icons-vue'
import {
  ACCENT_PRESETS,
  DEFAULT_THEME_CONFIG,
  normalizeThemeConfig,
  resolveThemeAccent,
} from '@/theme/themeConfig'

function cloneTheme(value) {
  return normalizeThemeConfig(value)
}

export default {
  name: 'ThemeSettingsDrawer',
  components: { ElIconCheck, ElIconRefreshLeft },
  props: {
    modelValue: { type: Boolean, default: false },
    config: { type: Object, default: () => ({ ...DEFAULT_THEME_CONFIG }) },
    saving: { type: Boolean, default: false },
  },
  emits: ['update:modelValue', 'preview', 'save', 'cancel'],
  data() {
    const initial = cloneTheme(this.config)
    return {
      draft: initial,
      openedSnapshot: cloneTheme(initial),
      accentPresets: ACCENT_PRESETS,
      accentModeOptions: [
        { value: 'PRESET', label: '预设' },
        { value: 'CUSTOM_SOLID', label: '自定义纯色' },
        { value: 'CUSTOM_GRADIENT', label: '自定义渐变' },
      ],
      gradientTypeOptions: [
        { value: 'LINEAR', label: '线性' },
        { value: 'RADIAL', label: '径向' },
      ],
      schemeOptions: [
        { value: 'LIGHT', label: '白天模式', previewClass: 'is-light' },
        { value: 'DARK', label: '夜间模式', previewClass: 'is-dark' },
      ],
      sidebarOptions: [
        { value: 'DARK', label: '深色导航', previewClass: 'is-dark' },
        { value: 'LIGHT', label: '浅色导航', previewClass: 'is-light' },
      ],
      navigationOptions: [
        { value: 'LEFT', label: '左侧菜单', previewClass: 'is-left' },
        { value: 'TOP', label: '顶部菜单', previewClass: 'is-top' },
      ],
      contentWidthOptions: [
        { value: 'FLUID', label: '流式' },
        { value: 'FIXED', label: '定宽' },
      ],
      tableScrollOptions: [
        { value: 'AUTO', label: '随内容增高' },
        { value: 'FIXED', label: '表格内滚动' },
      ],
    }
  },
  computed: {
    drawerSize() {
      return 'min(424px, 100vw)'
    },
    solidPresets() {
      return this.accentPresets.filter(item => item.kind === 'solid')
    },
    gradientPresets() {
      return this.accentPresets.filter(item => item.kind === 'gradient')
    },
    customAccent() {
      return resolveThemeAccent(this.draft)
    },
  },
  watch: {
    modelValue(open) {
      if (!open) return
      this.openedSnapshot = cloneTheme(this.config)
      this.draft = cloneTheme(this.config)
    },
    config: {
      deep: true,
      handler(value) {
        if (this.modelValue) return
        this.draft = cloneTheme(value)
      },
    },
  },
  methods: {
    swatchStyle(preset) {
      return {
        background: preset.background,
        color: preset.foreground,
      }
    },
    patchDraft(patch) {
      if (this.saving) return
      this.draft = cloneTheme({ ...this.draft, ...patch })
      this.emitPreview()
    },
    updateCustomSolidColor(color) {
      if (!color) return
      this.patchDraft({ customSolidColor: color })
    },
    updateGradientColor(index, color) {
      if (!color) return
      const colors = [...this.draft.customGradientColors]
      colors[index] = color
      this.patchDraft({ customGradientColors: colors })
    },
    setGradientColorCount(count) {
      const colors = [...this.draft.customGradientColors]
      if (count === 3 && colors.length === 2) {
        colors.splice(1, 0, '#6C3BFF')
      } else if (count === 2 && colors.length === 3) {
        colors.splice(1, 1)
      }
      this.patchDraft({ customGradientColors: colors })
    },
    updateGradientAngle(angle) {
      if (!Number.isInteger(angle)) return
      this.patchDraft({ customGradientAngle: angle })
    },
    emitPreview() {
      if (this.saving) return
      this.$emit('preview', cloneTheme(this.draft))
    },
    restoreDefault() {
      if (this.saving) return
      this.draft = cloneTheme(DEFAULT_THEME_CONFIG)
      this.emitPreview()
    },
    submit() {
      if (this.saving) return
      this.$emit('save', cloneTheme(this.draft))
    },
    requestClose() {
      if (this.saving) return
      this.$emit('cancel', cloneTheme(this.openedSnapshot))
      this.$emit('update:modelValue', false)
    },
    handleBeforeClose(done) {
      if (this.saving) return
      this.$emit('cancel', cloneTheme(this.openedSnapshot))
      this.$emit('update:modelValue', false)
      if (typeof done === 'function') done()
    },
  },
}
</script>

<style lang="scss" scoped>
.theme-settings {
  display: flex;
  min-height: 100%;
  padding: 0 4px 16px;
  flex-direction: column;
  color: var(--tianshu-text-primary, #1e293b);
}

.theme-section {
  padding: 0 0 24px;
  margin: 0 0 24px;
  border-bottom: 1px solid var(--tianshu-border-subtle, #e2e8f0);

  h3 {
    margin: 0 0 12px;
    color: var(--tianshu-text-primary, #1e293b);
    font-size: 14px;
    font-weight: 600;
    line-height: 20px;
  }
}

.section-hint {
  margin: -8px 0 12px;
  color: var(--tianshu-text-tertiary, #64748b);
  font-size: 12px;
  line-height: 18px;
}

.accent-mode-control {
  display: grid;
  padding: 3px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 3px;
  background: var(--tianshu-bg-muted, #f1f5f9);
  border: 1px solid var(--tianshu-border-subtle, #e2e8f0);
  border-radius: 8px;

  button {
    min-width: 0;
    padding: 6px 4px;
    overflow: hidden;
    color: var(--tianshu-text-tertiary, #64748b);
    font: inherit;
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
    background: transparent;
    border: 0;
    border-radius: 5px;
    cursor: pointer;

    &:hover,
    &:focus-visible {
      color: var(--el-color-primary, #2639e9);
      outline: none;
    }

    &.is-active {
      color: var(--tianshu-text-primary, #1e293b);
      font-weight: 600;
      background: var(--tianshu-bg-elevated, #ffffff);
      box-shadow: 0 2px 7px var(--tianshu-shadow-color, rgba(15, 23, 42, 0.1));
    }
  }
}

.accent-editor {
  margin-top: 14px;
}

.custom-accent-panel {
  padding: 12px;
  background: var(--tianshu-bg-soft, #f8fafc);
  border: 1px solid var(--tianshu-border-subtle, #e2e8f0);
  border-radius: 10px;
}

.custom-color-row,
.gradient-color-item {
  display: flex;
  min-height: 36px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--tianshu-text-secondary, #475569);
  font-size: 12px;
}

.custom-color-row + .custom-color-row,
.gradient-color-list + .custom-color-row {
  margin-top: 10px;
}

.gradient-color-list {
  display: grid;
  margin-top: 10px;
  gap: 8px;
}

.custom-color-control {
  display: flex;
  min-width: 130px;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;

  code {
    min-width: 68px;
    color: var(--tianshu-text-secondary, #475569);
    font-family: 'JetBrains Mono', 'Cascadia Code', monospace;
    font-size: 11px;
  }
}

.custom-accent-preview {
  position: relative;
  display: flex;
  height: 48px;
  margin-top: 12px;
  overflow: hidden;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  box-shadow: 0 8px 20px var(--tianshu-primary-shadow, rgba(38, 57, 233, 0.16));

  &::after {
    position: absolute;
    inset: 0;
    content: '';
    background-image: linear-gradient(
      90deg,
      transparent 0,
      rgba(255, 255, 255, 0.16) 50%,
      transparent 100%
    );
    transform: translateX(-100%);
    animation: accent-preview-scan 3.4s ease-in-out infinite;
  }

  span {
    position: relative;
    z-index: 1;
    font-size: 12px;
    font-weight: 600;
    letter-spacing: 0.08em;
  }
}

.custom-accent-note {
  margin: 9px 0 0;
  color: var(--tianshu-text-tertiary, #64748b);
  font-size: 11px;
  line-height: 17px;
}

@keyframes accent-preview-scan {
  0%,
  55% {
    transform: translateX(-100%);
  }

  100% {
    transform: translateX(100%);
  }
}

.option-grid {
  display: grid;
  gap: 12px;
}

.option-grid--two {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.preview-card {
  position: relative;
  display: flex;
  min-width: 0;
  padding: 8px;
  flex-direction: column;
  gap: 8px;
  color: var(--tianshu-text-secondary, #475569);
  font: inherit;
  font-size: 12px;
  text-align: left;
  background: var(--tianshu-bg-surface, #ffffff);
  border: 1px solid var(--tianshu-border, #cbd5e1);
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 160ms ease, box-shadow 160ms ease;

  &:hover,
  &:focus-visible,
  &.is-active {
    border-color: var(--el-color-primary, #2639e9);
    outline: none;
    box-shadow: 0 0 0 2px var(--tianshu-focus-ring, rgba(38, 57, 233, 0.16));
  }
}

.preview-card--compact {
  align-items: center;
}

.style-preview {
  display: flex;
  width: 100%;
  height: 52px;
  overflow: hidden;
  background: #f4f6fa;
  border-radius: 5px;
  box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.08);

  &.is-dark {
    background: #111827;

    .style-preview__body {
      background: #1e293b;

      span {
        background: #334155;
      }
    }
  }
}

.style-preview__sidebar {
  width: 18px;
  background: #191f3a;
}

.style-preview__body {
  display: flex;
  padding: 10px 8px;
  flex: 1;
  flex-direction: column;
  gap: 6px;

  span {
    height: 7px;
    background: #ffffff;
    border-radius: 2px;
  }
}

.sidebar-preview {
  display: flex;
  width: 48px;
  height: 58px;
  padding: 8px 7px;
  flex-direction: column;
  gap: 6px;
  border: 1px solid rgba(15, 23, 42, 0.1);
  border-radius: 5px;

  span,
  i {
    display: block;
    height: 5px;
    background: currentColor;
    border-radius: 3px;
    opacity: 0.62;
  }

  span {
    height: 8px;
    margin-bottom: 2px;
    opacity: 0.9;
  }

  &.is-dark {
    color: #dbe4f0;
    background: #191f3a;
  }

  &.is-light {
    color: #64748b;
    background: #ffffff;
  }
}

.navigation-preview {
  position: relative;
  display: grid;
  width: 72px;
  height: 50px;
  overflow: hidden;
  grid-template-columns: 16px 1fr;
  background: var(--tianshu-bg-muted, #f1f5f9);
  border: 1px solid var(--tianshu-border-subtle, #e2e8f0);
  border-radius: 5px;

  i {
    background: var(--tianshu-brand-background, #2639e9);
  }

  span {
    position: relative;
    margin: 9px;
    background: repeating-linear-gradient(
      180deg,
      var(--tianshu-border, #cbd5e1) 0 4px,
      transparent 4px 10px
    );
  }

  &.is-top {
    grid-template-rows: 13px 1fr;
    grid-template-columns: 1fr;
  }
}

.selection-check {
  position: absolute;
  right: 8px;
  bottom: 7px;
  color: var(--el-color-primary, #2639e9);
  font-size: 14px;
}

.swatch-group + .swatch-group {
  margin-top: 16px;
}

.swatch-group__label {
  display: block;
  margin-bottom: 8px;
  color: var(--tianshu-text-tertiary, #64748b);
  font-size: 12px;
}

.swatch-grid {
  display: grid;
  grid-template-columns: repeat(8, 24px);
  gap: 8px;
}

.theme-swatch {
  display: inline-flex;
  width: 24px;
  height: 24px;
  padding: 0;
  align-items: center;
  justify-content: center;
  border: 2px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.18);

  &:hover,
  &:focus-visible,
  &.is-active {
    border-color: var(--tianshu-bg-elevated, #ffffff);
    outline: 2px solid var(--el-color-primary, #2639e9);
    outline-offset: 1px;
  }
}

.gradient-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.gradient-swatch {
  display: flex;
  min-height: 40px;
  padding: 8px 10px;
  align-items: center;
  justify-content: space-between;
  color: #ffffff;
  font: inherit;
  font-size: 12px;
  font-weight: 600;
  text-align: left;
  border: 2px solid transparent;
  border-radius: 8px;
  cursor: pointer;

  &:hover,
  &:focus-visible,
  &.is-active {
    border-color: var(--tianshu-bg-elevated, #ffffff);
    outline: 2px solid var(--el-color-primary, #2639e9);
    outline-offset: 1px;
  }
}

.setting-row {
  display: flex;
  min-height: 36px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  color: var(--tianshu-text-secondary, #475569);
}

.setting-row + .setting-row {
  margin-top: 8px;
}

.segmented-control {
  display: inline-flex;
  padding: 3px;
  background: var(--tianshu-bg-muted, #f1f5f9);
  border-radius: 6px;

  button {
    min-width: 48px;
    padding: 4px 8px;
    color: var(--tianshu-text-tertiary, #64748b);
    font: inherit;
    font-size: 12px;
    background: transparent;
    border: 0;
    border-radius: 4px;
    cursor: pointer;

    &.is-active {
      color: var(--tianshu-text-primary, #1e293b);
      background: var(--tianshu-bg-elevated, #ffffff);
      box-shadow: 0 1px 3px var(--tianshu-shadow-color, rgba(15, 23, 42, 0.12));
    }
  }
}

.restore-button {
  display: flex;
  width: 100%;
  min-height: 36px;
  padding: 8px 12px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--tianshu-text-secondary, #475569);
  font: inherit;
  background: var(--tianshu-bg-surface, #ffffff);
  border: 1px solid var(--tianshu-border, #cbd5e1);
  border-radius: 6px;
  cursor: pointer;

  &:hover,
  &:focus-visible {
    color: var(--el-color-primary, #2639e9);
    border-color: var(--el-color-primary, #2639e9);
    outline: none;
  }
}

.drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.drawer-button {
  min-width: 88px;
  min-height: 36px;
  padding: 8px 16px;
  font: inherit;
  border-radius: 6px;
  cursor: pointer;

  &:disabled {
    cursor: not-allowed;
    opacity: 0.58;
  }
}

.drawer-button--secondary {
  color: var(--tianshu-text-secondary, #475569);
  background: var(--tianshu-bg-surface, #ffffff);
  border: 1px solid var(--tianshu-border, #cbd5e1);
}

.drawer-button--primary {
  color: var(--tianshu-brand-foreground, #ffffff);
  background: var(--tianshu-brand-background, #2639e9);
  border: 1px solid var(--el-color-primary, #2639e9);
}

@media (prefers-reduced-motion: reduce) {
  .custom-accent-preview::after {
    animation: none;
  }
}
</style>
