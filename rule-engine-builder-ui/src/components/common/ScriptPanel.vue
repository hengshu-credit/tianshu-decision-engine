<template>
  <div class="script-panel" :class="{ 'is-collapsed': !expanded }">
    <div class="sp-header" @click="toggleExpand">
      <div class="sp-header-left">
        <el-icon class="sp-icon"><el-icon-s-promotion /></el-icon>
        <span class="sp-title">检查脚本预览</span>
        <el-tag :type="statusTag.type" size="small" class="sp-status-tag">
          {{ statusTag.text }}
        </el-tag>
        <span class="sp-check-guidance">{{ guidance }}</span>
      </div>
      <div class="sp-header-right" @click.stop>
        <el-tooltip
          :content="expanded ? '收起脚本面板' : '展开脚本面板'"
          placement="top"
        >
          <el-button
            size="small"
            circle
            :icon="expandIcon"
            class="sp-toggle-btn"
            :aria-label="expanded ? '收起脚本面板' : '展开脚本面板'"
            :title="expanded ? '收起脚本面板' : '展开脚本面板'"
            @click.stop="toggleExpand"
          />
        </el-tooltip>
      </div>
    </div>

    <transition name="sp-slide">
      <div v-show="expanded" class="sp-body">
        <div class="sp-statusbar">
          <span
            v-if="content.compileMessage"
            class="sp-statusbar-item"
            :class="{ 'sp-error': content.compileStatus === 2 }"
          >
            <el-icon v-if="content.compileStatus === 2">
              <el-icon-close-circle />
            </el-icon>
            {{ content.compileMessage }}
          </span>
          <div class="sp-statusbar-spacer" />
          <el-button
            size="small"
            :icon="ElIconDocumentCopy"
            @click="copyScript"
          >
            复制
          </el-button>
        </div>

        <div class="sp-editor-container">
          <div class="sp-line-numbers" aria-hidden="true">
            <div v-for="n in lineCount" :key="n" class="sp-line-num">
              {{ n }}
            </div>
          </div>
          <textarea
            ref="editorRef"
            v-model="editScript"
            class="sp-editor readonly"
            readonly
            :placeholder="placeholder"
            spellcheck="false"
            autocomplete="off"
            autocorrect="off"
            autocapitalize="off"
            @scroll="syncScroll"
          />
        </div>

        <div class="sp-footer">
          <span class="sp-footer-tip">
            <el-icon><el-icon-info /></el-icon>
            此处仅预览由可视化配置编译生成的 QLExpress 脚本。
          </span>
          <span class="sp-line-info">
            {{ lineCount }} 行 / {{ editScript.length }} 字符
          </span>
        </div>
      </div>
    </transition>
  </div>
</template>

<script>
import { markRaw } from 'vue'
import {
  Promotion as ElIconSPromotion,
  CircleClose as ElIconCloseCircle,
  InfoFilled as ElIconInfo,
  DocumentCopy as ElIconDocumentCopy,
  ArrowDown as ElIconArrowDown,
  ArrowUp as ElIconArrowUp,
} from '@element-plus/icons-vue'

export default {
  name: 'ScriptPanel',
  components: {
    ElIconSPromotion,
    ElIconCloseCircle,
    ElIconInfo,
  },
  props: {
    compileResult: { type: Object, default: null },
    guidance: { type: String, default: '由顶部“编译”生成' },
    placeholder: { type: String, default: '请先点击顶部“编译”生成脚本' },
    emptyMessage: { type: String, default: '暂无脚本，请先编译' },
  },
  data() {
    return {
      expanded: false,
      content: {},
      editScript: '',
      ElIconDocumentCopy: markRaw(ElIconDocumentCopy),
    }
  },
  computed: {
    lineCount() {
      return (this.editScript.match(/\n/g) || []).length + 1
    },
    statusTag() {
      const status = this.content.compileStatus
      if (status === 1) return { type: 'success', text: '脚本已生成' }
      if (status === 2) return { type: 'danger', text: '脚本生成失败' }
      return { type: 'info', text: '未生成' }
    },
    expandIcon() {
      return this.expanded ? ElIconArrowDown : ElIconArrowUp
    },
  },
  watch: {
    compileResult: {
      immediate: true,
      handler(result) {
        this.applyCompileResult(result)
      },
    },
  },
  methods: {
    toggleExpand() {
      this.expanded = !this.expanded
    },
    applyCompileResult(result) {
      this.content = result
        ? {
            compileStatus: result.compileSuccess ? 1 : 2,
            compileMessage: result.compileMessage || '',
          }
        : {}
      this.editScript = result?.compiledScript || result?.revision?.compiledScript || ''
    },
    copyScript() {
      if (!this.editScript) {
        this.$message.warning(this.emptyMessage)
        return
      }
      if (navigator.clipboard) {
        navigator.clipboard.writeText(this.editScript).then(() => {
          this.$message.success('脚本已复制到剪贴板')
        })
      } else {
        const editor = this.$refs.editorRef
        editor.select()
        document.execCommand('copy')
        this.$message.success('脚本已复制')
      }
    },
    syncScroll(e) {
      const lineNumbers = this.$el.querySelector('.sp-line-numbers')
      if (lineNumbers) lineNumbers.scrollTop = e.target.scrollTop
    },
  },
}
</script>

<style lang="scss" scoped>
.script-panel {
  margin-top: 16px;
  overflow: hidden;
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 6px;
}

.sp-header,
.sp-header-left,
.sp-header-right,
.sp-statusbar,
.sp-footer {
  display: flex;
  align-items: center;
}

.sp-header {
  justify-content: space-between;
  padding: 8px 14px;
  cursor: pointer;
  background: var(--tianshu-bg-muted);
}

.sp-header-left,
.sp-header-right,
.sp-statusbar {
  gap: 8px;
}

.sp-header-left {
  min-width: 0;
  flex-wrap: wrap;
}

.sp-icon {
  color: var(--el-color-primary);
}

.sp-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--tianshu-text-primary);
}

.sp-check-guidance {
  color: var(--tianshu-text-tertiary);
  font-size: 12px;
}

.sp-toggle-btn {
  border: none;
}

.sp-body {
  background: var(--tianshu-bg-soft);
}

.sp-statusbar {
  padding: 6px 12px;
  background: var(--tianshu-bg-muted);
  border-bottom: 1px solid var(--tianshu-border-subtle);
}

.sp-statusbar-item {
  font-size: 11px;
  color: var(--tianshu-text-tertiary);
}

.sp-error {
  color: var(--tianshu-danger-text);
}

.sp-statusbar-spacer {
  flex: 1;
}

.sp-statusbar :deep(.el-button) {
  min-height: 26px;
  padding: 6px 12px;
  color: var(--tianshu-info-text);
  font-weight: 600;
  background: var(--tianshu-info-bg);
  border-color: var(--tianshu-info-border);
}

.sp-statusbar :deep(.el-button:hover) {
  color: var(--tianshu-info-text);
  background: var(--tianshu-bg-active);
  border-color: var(--el-color-primary);
}

.sp-editor-container {
  display: flex;
  min-height: 200px;
  max-height: 420px;
  overflow: hidden;
}

.sp-line-numbers {
  min-width: 42px;
  padding: 12px 8px 12px 12px;
  overflow: hidden;
  color: var(--tianshu-text-tertiary);
  text-align: right;
  background: var(--tianshu-bg-muted);
  border-right: 1px solid var(--tianshu-border-subtle);
}

.sp-line-num,
.sp-editor,
.sp-line-info {
  font-family: Consolas, Monaco, 'Courier New', monospace;
}

.sp-line-num,
.sp-editor {
  font-size: 13px;
  line-height: 1.6;
}

.sp-editor {
  flex: 1;
  width: 100%;
  min-height: 200px;
  max-height: 420px;
  padding: 12px 16px;
  overflow: auto;
  color: var(--tianshu-text-primary);
  resize: none;
  background: var(--tianshu-bg-soft);
  border: none;
  outline: none;
}

.sp-footer {
  justify-content: space-between;
  padding: 5px 12px;
  color: var(--tianshu-text-tertiary);
  background: var(--tianshu-bg-muted);
  border-top: 1px solid var(--tianshu-border-subtle);
}

.sp-footer-tip,
.sp-line-info {
  font-size: 11px;
}

.sp-slide-enter-active,
.sp-slide-leave-active {
  overflow: hidden;
  transition: max-height 0.25s ease;
}

.sp-slide-enter-from,
.sp-slide-leave-to {
  max-height: 0;
}

.sp-slide-enter-to,
.sp-slide-leave-from {
  max-height: 600px;
}
</style>
