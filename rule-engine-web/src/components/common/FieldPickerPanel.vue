<template>
  <section v-show="visible" ref="panel" class="fp-panel" :class="{ 'fp-panel--compact': width < 680, 'fp-panel--narrow': width < 520, 'fp-panel--editor': editorMode }" role="dialog" aria-label="内容选择器" :style="{ width: width + 'px', height: height + 'px' }">
    <header class="fp-header" @pointerdown="startMotion($event, 'move')">
      <strong>{{ editorMode ? ({ LITERAL: '调整阈值', PATH: '写入路径', EXPRESSION: '组合表达式' })[editorKind] : '内容选择器' }}</strong>
      <div v-if="editorMode || manualKinds.length || expression" class="fp-actions" role="toolbar" aria-label="配置面板" @pointerdown.stop>
        <button v-if="editorMode" type="button" @click="$emit('back')">返回内容</button>
        <button v-if="manualKinds.includes('LITERAL')" type="button" :aria-pressed="editorMode && editorKind === 'LITERAL'" @click="$emit('manual', 'LITERAL')">输入阈值</button>
        <button v-if="manualKinds.includes('PATH')" type="button" :aria-pressed="editorMode && editorKind === 'PATH'" @click="$emit('manual', 'PATH')">写入路径</button>
        <button v-if="expression" type="button" :aria-pressed="editorMode && editorKind === 'EXPRESSION'" title="在当前面板中配置组合表达式" @click="$emit('expression')">ƒx 组合表达式</button>
      </div>
      <button type="button" aria-label="关闭内容选择器" @pointerdown.stop @click="$emit('close')">×</button>
    </header>
    <div v-if="editorMode && editorKind === 'EXPRESSION'" class="fp-editor-tools"><slot name="editor-actions" /></div>
    <div v-show="editorMode" class="fp-editor"><slot name="editor" /></div>
    <div v-show="!editorMode" class="fp-body">
      <nav class="fp-categories" aria-label="内容分类">
        <div class="fp-category-list">
          <button v-for="entry in categories" :key="entry.key" type="button" :class="{ active: category === entry.key }" @click="changeCategory(entry.key)">
            <span class="fp-category-icon" aria-hidden="true">{{ entry.icon }}</span><span>{{ entry.label }}</span><small>{{ entry.count }}</small>
          </button>
        </div>
      <div class="fp-sidebar-hint">单击查看或展开 · 双击 / Enter 选择</div>
      </nav>
      <main class="fp-main">
        <div class="fp-search"><el-input :model-value="query" size="small" clearable aria-label="搜索内容名称或编码" placeholder="搜索内容名称或编码" @update:model-value="search" /></div>
        <div v-if="selectedOutsideSearch" class="fp-located"><span>当前已选（不在搜索结果中）</span><button type="button" @click="preview = selectedNode">{{ selectedNode.name }}</button></div>
        <div class="fp-columns" role="row"><span>来源 / 类型</span><span>内容名称</span><span>内容编码</span><span>版本选择</span></div>
        <div ref="viewport" class="fp-viewport" role="treegrid" aria-label="可选内容" :aria-rowcount="rows.length" @scroll="scrollTop = $event.target.scrollTop">
          <div class="fp-sticky">
            <field-picker-row v-for="node in stickyParents" :key="node.key" :node="node" :depth="ancestors(node).length" :selected="node.key === selectedKey" :expanded="true" @preview="inspect(node, $event)" @confirm="confirm(node)">
              <template #version><slot name="version" :item="node.item" :active="preview?.key === node.key"><span class="fp-no-version">{{ versionLabel(node) }}</span></slot></template>
            </field-picker-row>
          </div>
          <div :style="{ height: virtualStart * rowHeight + 'px' }" />
          <field-picker-row v-for="row in windowRows" :key="row.node.key" :node="row.node" :depth="row.depth" :selected="row.node.key === selectedKey" :expanded="expanded.has(row.node.key) || !!query" @preview="inspect(row.node, $event)" @confirm="confirm(row.node)">
            <template #version><slot name="version" :item="row.node.item" :active="preview?.key === row.node.key"><span class="fp-no-version" :title="versionLabel(row.node) === '—' ? '该字段没有独立的版本绑定' : '跟随当前目录版本'">{{ versionLabel(row.node) }}</span></slot></template>
          </field-picker-row>
          <div :style="{ height: Math.max(0, pageRows.length - virtualEnd) * rowHeight + 'px' }" />
          <div v-if="!rows.length" class="fp-empty">{{ loading ? '正在加载字段…' : '没有符合条件的字段' }}</div>
        </div>
        <el-tooltip
          v-if="previewText"
          :content="previewText"
          placement="top"
          :show-after="120"
          popper-class="field-picker-preview-tooltip"
        >
          <div class="fp-preview">{{ previewText }}</div>
        </el-tooltip>
        <div v-else class="fp-preview">选择内容可查看完整名称、编码和数据类型</div>
        <footer class="fp-pagination">
          <span>共 {{ rows.length }} 项</span>
          <label>每页 <select :value="pageSize" aria-label="每页字段数" @change="changePageSize(Number($event.target.value))"><option v-for="size in pageSizes" :key="size" :value="size">{{ size }} 条</option></select></label>
          <button type="button" aria-label="上一页" :disabled="page === 1" @click="setPage(page - 1)">‹</button>
          <label><input :value="page" type="number" min="1" :max="pageCount" aria-label="页码" @change="setPage(Number($event.target.value))" /> / {{ pageCount }}</label>
          <button type="button" aria-label="下一页" :disabled="page === pageCount" @click="setPage(page + 1)">›</button>
        </footer>
      </main>
    </div>
    <span class="fp-resize" role="separator" aria-label="调整内容选择器大小" @pointerdown.prevent.stop="startMotion($event, 'resize')" />
  </section>
</template>

<script>
import { markRaw } from 'vue'
import FieldPickerRow from './FieldPickerRow.vue'
import { buildFieldTree, filterFieldTree, fieldAncestors, FIELD_PAGE_SIZES, FIELD_ROW_HEIGHT, visibleFieldRows } from '@/utils/fieldPicker'
import { normalizeFieldPageSize } from '@/utils/fieldPickerPreferences'
import { filterFieldTreeByTags } from '@/utils/fieldPickerPolicy'
import { fieldPickerDetails } from '@/utils/fieldPickerDetails'
import { canViewPickerItem, recordPickerActivity } from '@/security/pickerAccess'
import { API_PICKER_CATEGORIES } from '@/utils/pickerCategories'

const categoryMeta = {
  ...Object.fromEntries(API_PICKER_CATEGORIES.map(item => [item.key, [item.label, item.icon]])),
  selected: ['已选内容', '✓'], recent: ['最近使用', '◷'], all: ['全部内容', '⊞'], standalone: ['变量', '{}'], constant: ['常量', 'C'], object: ['数据对象', '◇'], model: ['模型', 'M'], function: ['函数 / 方法', 'ƒ'], operator: ['操作符', '∑'], external: ['外数 API', '↗'], rule: ['规则', 'R'], list: ['名单', '[]'], database: ['数据库', '▤'], sftp: ['SFTP', '↗'],
}
export default {
  name: 'FieldPickerPanel',
  components: { FieldPickerRow },
  props: {
    items: { type: Array, default: () => [] }, selectedKey: { type: String, default: '' },
    selectedKeys: { type: Array, default: () => [] }, recentKeys: { type: Array, default: () => [] },
    query: { type: String, default: '' }, category: { type: String, default: 'all' },
    width: { type: Number, default: 780 }, height: { type: Number, default: 500 },
    initialPageSize: { type: Number, default: 50 }, loading: Boolean, visible: Boolean,
    manualKinds: { type: Array, default: () => [] }, expression: Boolean, editorMode: Boolean, editorKind: { type: String, default: 'EXPRESSION' },
    expectedType: { type: String, default: '' },
    visibleTags: { type: [Array, Object], default: null },
    selectableTags: { type: [Array, Object], default: null },
  },
  emits: ['category', 'search', 'select', 'manual', 'expression', 'close', 'back', 'move', 'resize', 'page-size'],
  data() { return { expanded: new Set(), page: 1, pageSize: normalizeFieldPageSize(this.initialPageSize), scrollTop: 0, viewportHeight: 300, preview: null, motion: null, locatePending: true, rowHeight: FIELD_ROW_HEIGHT, lastConfirmKey: '', lastConfirmAt: 0 } },
  computed: {
    pageSizes() { return [...new Set([...FIELD_PAGE_SIZES, this.pageSize])].sort((a, b) => a - b) },
    tree() { return markRaw(filterFieldTreeByTags(filterFieldTree(buildFieldTree(this.items.filter(canViewPickerItem)), this.expectedType), this.visibleTags, this.selectableTags)) },
    categories() {
      const counts = new Map([['all', 0], ['selected', this.availableSelected.length], ['recent', this.availableRecent.length]])
      this.tree.nodes.forEach(node => {
        // 对象/模型分组行只是目录结构，不应被计为可选内容数量。
        if (node.item && (!node.item._versionGroup || node.item.rootReference)) counts.set(node.category, (counts.get(node.category) || 0) + 1)
      })
      counts.set('all', [...this.tree.nodes.values()].filter(node => node.item && node.selectable).length)
      const persistentCategories = new Set(['recent', 'selected'])
      return Object.entries(categoryMeta)
        .filter(([key]) => persistentCategories.has(key) || (counts.get(key) || 0) > 0)
        .map(([key, [label, icon]]) => ({ key, label, icon, count: counts.get(key) || 0 }))
    },
    availableSelected() { return [...new Set([this.selectedKey, ...this.selectedKeys])].filter(key => this.tree.nodes.get(key)?.selectable) },
    availableRecent() { return this.recentKeys.filter(key => this.tree.nodes.get(key)?.selectable) },
    rows() {
      if (['recent', 'selected'].includes(this.category)) {
        const keys = this.category === 'recent' ? this.availableRecent : this.availableSelected
        const query = this.query.trim().toLowerCase()
        return keys.map(key => ({ node: this.tree.nodes.get(key), depth: 0 })).filter(({ node }) => !query || [node.name, node.code].some(text => text.toLowerCase().includes(query)))
      }
      return visibleFieldRows(this.tree, this.expanded, this.category, this.query)
    },
    pageCount() { return Math.max(1, Math.ceil(this.rows.length / this.pageSize)) },
    pageRows() { return this.rows.slice((this.page - 1) * this.pageSize, this.page * this.pageSize) },
    virtualStart() { return Math.max(0, Math.floor(this.scrollTop / this.rowHeight) - 3) },
    virtualEnd() { return Math.min(this.pageRows.length, this.virtualStart + Math.ceil(this.viewportHeight / this.rowHeight) + 7) },
    windowRows() { return this.pageRows.slice(this.virtualStart, this.virtualEnd) },
    stickyParents() {
      if (['recent', 'selected'].includes(this.category)) return []
      const current = this.pageRows[Math.min(this.pageRows.length - 1, Math.floor(this.scrollTop / this.rowHeight))]
      const pageKeys = new Set(this.pageRows.map(row => row.node.key))
      return fieldAncestors(current?.node).filter(parent => !pageKeys.has(parent.key))
    },
    selectedNode() { return this.tree.nodes.get(this.selectedKey) },
    selectedOutsideSearch() { return this.selectedNode && this.query && !this.rows.some(row => row.node.key === this.selectedKey) },
    previewText() { return fieldPickerDetails(this.preview, this.preview ? this.versionLabel(this.preview) : '') },
  },
  watch: {
    initialPageSize(value) { this.changePageSize(value, false) },
    visible(value) { if (value) { this.locatePending = true; this.$nextTick(this.locate) } else this.stopMotion() },
    selectedKey() { if (this.visible && this.locatePending) this.$nextTick(this.locate) },
    tree() { if (this.preview && !this.tree.nodes.has(this.preview.key)) this.preview = null; if (this.visible && this.locatePending) this.$nextTick(this.locate) },
    categories(value) { if (!value.some(item => item.key === this.category)) this.changeCategory('all') },
    pageCount(value) { if (this.page > value) this.setPage(value) },
    height() { this.$nextTick(this.measure) },
    width() { this.$nextTick(this.measure) },
  },
  mounted() { this.measure(); this.locate(); window.addEventListener('resize', this.clamp) },
  beforeUnmount() { this.stopMotion(); window.removeEventListener('resize', this.clamp) },
  methods: {
    versionLabel(node) {
      if (node.item?.versionMode === 'FIXED') return `${node.item.versionSource === 'FUNCTION_HISTORY' ? '旧' : ''}v${node.item.version}`
      if (node.item?.versionMode === 'LATEST' && node.item.version != null) return `最新·v${node.item.version}`
      const version = node.version ?? node.item?._ref?.modelVersion ?? node.item?.function?.version ?? node.item?.varObj?.version
      const frozen = (node.catalogMode || node.item?.catalogMode || node.item?.function?.catalogMode) === 'FROZEN'
      return version != null ? `${frozen ? '' : '最新 · '}v${version}` : '—'
    },
    ancestors: fieldAncestors,
    measure() { this.viewportHeight = this.$refs.viewport?.clientHeight || 300 },
    search(value) { this.locatePending = false; this.$emit('search', value); this.$emit('category', 'all'); this.setPage(1) },
    changeCategory(key) { this.locatePending = false; this.$emit('category', key); this.setPage(1) },
    setPage(value) { this.page = Math.max(1, Math.min(this.pageCount, Math.trunc(value) || 1)); this.scrollTop = 0; if (this.$refs.viewport) this.$refs.viewport.scrollTop = 0 },
    changePageSize(value, notify = true) {
      const size = normalizeFieldPageSize(value)
      if (size === this.pageSize) return
      const offset = (this.page - 1) * this.pageSize
      this.pageSize = size
      this.setPage(Math.floor(offset / size) + 1)
      if (notify) this.$emit('page-size', size)
    },
    inspect(node, event) {
      if (node.item && !canViewPickerItem(node.item)) return
      this.locatePending = false
      this.preview = node
      if (node.item && !(event?.detail > 1)) recordPickerActivity('FIELD_VIEW', node.item, this.$route)
      // The second click of a double click must not collapse the tree before confirmation.
      if (event?.detail > 1 || !node.children.length) return
      const expanded = new Set(this.expanded)
      if (expanded.has(node.key)) expanded.delete(node.key); else expanded.add(node.key)
      this.expanded = expanded
    },
    confirm(node) {
      if (node.item && node.selectable && canViewPickerItem(node.item)) {
        const now = Date.now()
        if (this.lastConfirmKey === node.key && now - this.lastConfirmAt < 300) return
        this.lastConfirmKey = node.key
        this.lastConfirmAt = now
        recordPickerActivity('FIELD_SELECT', node.item, this.$route)
        this.$emit('select', node.item)
      }
    },
    async locate() {
      if (!this.visible || !this.$refs.panel) return
      this.clamp()
      const node = this.selectedNode
      if (!node) return
      this.locatePending = false
      this.preview = node
      this.$emit('category', node.category)
      this.expanded = new Set([...this.expanded, ...fieldAncestors(node).map(parent => parent.key)])
      await this.$nextTick()
      if (!this.visible || !this.$refs.panel) return
      const index = this.rows.findIndex(row => row.node.key === node.key)
      if (index < 0) return
      this.setPage(Math.floor(index / this.pageSize) + 1)
      await this.$nextTick()
      this.scrollTop = Math.max(0, ((index % this.pageSize) - fieldAncestors(node).length - 1) * this.rowHeight)
      if (this.$refs.viewport) this.$refs.viewport.scrollTop = this.scrollTop
    },
    startMotion(event, kind) {
      if (event.button !== 0) return
      const rect = this.$refs.panel.getBoundingClientRect()
      this.motion = { kind, x: event.clientX, y: event.clientY, left: rect.left, top: rect.top, width: rect.width, height: rect.height }
      event.preventDefault()
      window.addEventListener('pointermove', this.onMotion)
      window.addEventListener('pointerup', this.stopMotion)
      window.addEventListener('pointercancel', this.stopMotion)
    },
    onMotion(event) {
      if (!this.motion) return
      const m = this.motion, dx = event.clientX - m.x, dy = event.clientY - m.y
      if (m.kind === 'move') this.$emit('move', { left: Math.max(0, Math.min(window.innerWidth - this.width, m.left + dx)), top: Math.max(0, Math.min(window.innerHeight - this.height, m.top + dy)) })
      else this.$emit('resize', { width: Math.min(window.innerWidth - m.left, Math.max(Math.min(520, window.innerWidth), m.width + dx)), height: Math.min(window.innerHeight - m.top, Math.max(Math.min(300, window.innerHeight), m.height + dy)) })
    },
    stopMotion() { this.motion = null; window.removeEventListener('pointermove', this.onMotion); window.removeEventListener('pointerup', this.stopMotion); window.removeEventListener('pointercancel', this.stopMotion) },
    clamp() {
      if (!this.visible || !this.$refs.panel) return
      const width = Math.min(this.width, window.innerWidth - 8), height = Math.min(this.height, window.innerHeight - 8)
      this.$emit('resize', { width, height })
      this.measure()
    },
  },
}
</script>

<style scoped>
.fp-panel { display:flex; flex-direction:column; max-width:calc(100vw - 8px); max-height:calc(100vh - 8px); min-height:240px; color:var(--tianshu-text-primary); background:var(--tianshu-bg-surface); overflow:hidden; font-size:12px; }
.fp-panel button,.fp-panel select,.fp-panel input { color:inherit; font:inherit; border:1px solid var(--tianshu-border); border-radius:0; background:var(--tianshu-bg-surface); }
.fp-panel button { cursor:pointer; }
.fp-panel button:disabled { cursor:default; opacity:.45; }
.fp-header { display:grid; grid-template-columns:minmax(0,1fr) auto minmax(0,1fr); align-items:center; gap:8px; padding:0 12px; height:44px; flex:none; border-bottom:1px solid var(--tianshu-border); cursor:move; touch-action:none; user-select:none; }
.fp-header strong { grid-column:1; white-space:nowrap; }
.fp-header > button { grid-column:3; justify-self:end; width:28px; height:28px; padding:0; border:0; font-size:20px; line-height:1; }
.fp-header > button:hover { background:var(--tianshu-bg-hover); }
.fp-body { display:flex; flex:1; min-height:0; }
.fp-editor { flex:1; min-height:0; min-width:0; overflow:hidden; }
.fp-editor-tools { display: flex; justify-content: flex-end; gap: 8px; padding: 6px 12px; border-bottom: 1px solid var(--tianshu-border-subtle); }
.fp-editor-tools :deep(button) { height: 26px; min-width: 60px; padding: 0 8px; border: 1px solid var(--tianshu-border); border-radius: 0; background: var(--tianshu-bg-surface); color: var(--tianshu-text-primary); font: inherit; cursor: pointer; }
.fp-actions :deep(button[aria-pressed="true"]) { box-shadow: inset 0 -2px var(--el-color-primary); font-weight: 600; }
.fp-actions :deep(button) { color: var(--tianshu-primary-text, var(--el-color-primary)); font: inherit; border: 1px solid var(--tianshu-border); border-radius: 0; background: var(--tianshu-bg-surface); cursor: pointer; }
.fp-categories { display:flex; flex-direction:column; width:148px; flex:none; min-height:0; overflow:hidden; border-right:1px solid var(--tianshu-border); }
.fp-category-list { flex:1; min-height:0; overflow-x:hidden; overflow-y:auto; padding:6px; }
.fp-sidebar-hint { display:flex; align-items:center; justify-content:center; flex:none; min-height:36px; box-sizing:border-box; padding:8px 4px; border-top:1px solid var(--tianshu-border-subtle); color:var(--tianshu-text-secondary); font-size:11px; line-height:18px; text-align:center; white-space:normal; }
.fp-categories button { display:flex; width:100%; align-items:center; gap:6px; height:34px; padding:0 6px; border:0; white-space:nowrap; }
.fp-categories button.active { color:var(--tianshu-primary-text, var(--el-color-primary)); background:var(--tianshu-info-bg); font-weight:600; }
.fp-categories small { margin-left:auto; font-size:10px; color:var(--tianshu-text-tertiary); }
.fp-category-icon { width:20px; flex:none; text-align:center; }
.fp-main { display:flex; flex:1; min-width:0; flex-direction:column; }
.fp-search { padding:8px; }
.fp-search :deep(.el-input__wrapper) { border-radius:0; }
.fp-columns { display:grid; grid-template-columns:64px minmax(70px,1fr) minmax(70px,1.15fr) 130px 28px; align-items:center; min-height:30px; padding:0 8px; color:var(--tianshu-text-tertiary); border-bottom:1px solid var(--tianshu-border); }
.fp-viewport { flex:1; min-height:0; overflow:auto; position:relative; overflow-anchor:none; }
.fp-sticky { position:sticky; top:0; height:0; overflow:visible; z-index:2; }
.fp-sticky :deep(.fp-row) { background:var(--tianshu-bg-muted); box-shadow:0 1px var(--tianshu-border); }
.fp-empty { padding:30px 10px; text-align:center; color:var(--tianshu-text-tertiary); }
.fp-preview { min-height:28px; line-height:28px; padding:0 8px; border-top:1px solid var(--tianshu-border); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; color:var(--tianshu-text-secondary); }
:global(.field-picker-preview-tooltip.el-popper) { max-width:min(720px,calc(100vw - 24px)); white-space:normal; overflow-wrap:anywhere; }
.fp-pagination { display:flex; align-items:center; justify-content:flex-end; flex-wrap:wrap; gap:6px; padding:6px 8px; border-top:1px solid var(--tianshu-border); font-size:11px; }
.fp-pagination > span { margin-right:auto; }
.fp-pagination input { width:36px; padding:2px; }
.fp-pagination select,.fp-pagination button { height:24px; }
.fp-actions { grid-column:2; display:grid; grid-auto-flow:column; grid-auto-columns:112px; justify-self:center; gap:8px; }
.fp-actions :deep(button) { box-sizing:border-box; height:28px; padding:0 8px; font-size:12px; font-weight:500; white-space:nowrap; color:var(--tianshu-primary-text, var(--el-color-primary)); background:var(--tianshu-info-bg); border-color:color-mix(in srgb,var(--el-color-primary) 28%,var(--tianshu-border)); }
.fp-actions :deep(button:hover) { background:color-mix(in srgb,var(--el-color-primary) 14%,var(--tianshu-bg-surface)); border-color:var(--tianshu-primary-text, var(--el-color-primary)); }
.fp-actions :deep(button:focus-visible) { outline:2px solid var(--el-color-primary); outline-offset:2px; }
.fp-panel--compact .fp-actions { grid-auto-columns:104px; gap:6px; }
.fp-panel--compact .fp-actions :deep(button) { padding:0 4px; }
.fp-panel--editor .fp-actions { grid-auto-columns:104px; }
.fp-panel--compact.fp-panel--editor .fp-header { grid-template-columns: minmax(0,1fr) auto; height: auto; padding-top: 8px; padding-bottom: 8px; }
.fp-panel--compact.fp-panel--editor .fp-header > button { grid-column: 2; grid-row: 1; }
.fp-panel--compact.fp-panel--editor .fp-actions { grid-column: 1 / -1; grid-row: 2; grid-auto-columns: minmax(0,1fr); width: 100%; gap: 4px; }
.fp-panel--narrow .fp-header { grid-template-columns:minmax(0,1fr) auto; height:auto; min-height:44px; padding:8px 12px; }
.fp-panel--narrow .fp-header > button { grid-column:2; grid-row:1; }
.fp-panel--narrow .fp-actions { grid-column:1 / -1; grid-row:2; grid-auto-columns:minmax(0,1fr); width:100%; max-width:352px; }
.fp-panel--narrow .fp-action-arrow { display:none; }
.fp-resize { position:absolute; right:0; bottom:0; width:16px; height:16px; cursor:nwse-resize; touch-action:none; background:linear-gradient(135deg,transparent 65%,var(--tianshu-text-tertiary) 66%,transparent 72%,transparent 82%,var(--tianshu-text-tertiary) 83%,transparent 90%); }
.fp-located { padding:4px 8px; background:var(--tianshu-info-bg); display:flex; gap:8px; }
.fp-no-version { color:var(--tianshu-text-tertiary); }
.fp-panel :deep(.rule-call-version) { display:block; margin:0; }
.fp-panel :deep(.rule-call-version > span:first-child),.fp-panel :deep(.rule-call-version > small) { display:none; }
.fp-panel :deep(.rule-call-version .el-select) { width:100%; }
.fp-panel :deep(.rule-call-version .el-select__wrapper) { border-radius:0; min-height:26px; font-size:11px; }
@media(max-width:640px) { .fp-categories { width:112px; } .fp-category-icon { display:none; } .fp-columns { grid-template-columns:64px minmax(45px,1fr) minmax(45px,1fr) 96px 24px; } }
</style>
