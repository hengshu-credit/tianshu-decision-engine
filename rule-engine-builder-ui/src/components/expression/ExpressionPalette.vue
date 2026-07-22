<template>
  <aside class="expression-palette" :style="paletteStyle">
    <nav class="palette-categories" aria-label="表达式资源分类">
      <button
        v-for="category in categories"
        :key="category.key"
        type="button"
        class="palette-category"
        :class="{ 'palette-category--active': activeCategory === category.key }"
        @click="selectCategory(category.key)"
      >
        <span :title="category.label">{{ category.label }}</span>
        <span class="palette-category__count">{{ category.count }}</span>
      </button>
    </nav>
    <div
      class="palette-resize-handle"
      title="拖拽调整分类宽度"
      @mousedown.prevent.stop="startResize('category', $event)"
      @touchstart.prevent.stop="startTouchResize('category', $event)"
    />

    <section class="palette-content">
      <div class="palette-search">
        <el-input
          v-model="keyword"
          size="small"
          clearable
          :prefix-icon="ElIconSearch"
          :placeholder="searchPlaceholder"
          @update:model-value="page = 1"
        />
      </div>

      <div class="palette-results">
        <template v-if="activeCategory === 'manual'">
          <div class="palette-manual-kinds">
            <button
              v-for="item in pagedItems"
              :key="item.key"
              type="button"
              class="palette-manual-kind"
              @click="insertManual(item.key)"
            >
              <app-icon :name="item.icon" />
              <span
                ><strong>{{ item.label }}</strong
                ><small>{{ item.description }}</small></span
              >
            </button>
          </div>
        </template>

        <template v-else-if="isGroupedReferenceCategory">
          <table class="palette-reference-table">
            <thead>
              <tr>
                <th>类型</th>
                <th>对象编码</th>
                <th>对象名称</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="group in pagedItems" :key="group.groupKey">
                <tr class="palette-reference-group" @click="toggleGroup(group)">
                  <td>
                    <span class="palette-group-toggle"
                      ><app-icon
                        :name="
                          expandedGroupKey === group.groupKey
                            ? 'ArrowDown'
                            : 'ArrowRight'
                        "
                      />{{ group.children.length }}</span
                    >
                  </td>
                  <td class="palette-reference-code">{{ group.groupCode }}</td>
                  <td class="palette-reference-name">{{ group.groupLabel }}</td>
                </tr>
                <tr
                  v-if="expandedGroupKey === group.groupKey"
                  class="palette-reference-children-row"
                >
                  <td colspan="3">
                    <button
                      v-for="child in pagedGroupChildren(group)"
                      :key="referenceKey(child)"
                      type="button"
                      class="palette-reference-child"
                      @click.stop="emitInsert(referenceTemplate(child))"
                    >
                      <code>{{ childRelativePath(child) }}</code>
                      <span
                        class="palette-type-badge"
                        :class="
                          'palette-type-badge--' + typeChar(child.varType)
                        "
                        >{{ typeChar(child.varType) }}</span
                      >
                      <span>{{ childDisplayName(child) }}</span>
                    </button>
                    <el-pagination
                      v-if="group.children.length > pageSize"
                      small
                      layout="prev,pager,next"
                      :current-page="groupChildPage(group)"
                      :page-size="pageSize"
                      :total="group.children.length"
                      @current-change="setGroupChildPage(group, $event)"
                    />
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </template>

        <template v-else-if="isReferenceCategory">
          <table class="palette-reference-table">
            <thead>
              <tr>
                <th>类型</th>
                <th>字段编码</th>
                <th>字段名称</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="item in pagedItems"
                :key="referenceKey(item)"
                @click="emitInsert(referenceTemplate(item))"
              >
                <td>
                  <span
                    class="palette-type-badge"
                    :class="'palette-type-badge--' + typeChar(item.varType)"
                    :title="typeLabel(item.varType)"
                    >{{ typeChar(item.varType) }}</span
                  >
                </td>
                <td class="palette-reference-code" :title="referenceCode(item)">
                  {{ referenceCode(item) }}
                </td>
                <td
                  class="palette-reference-name"
                  :title="referenceLabel(item)"
                >
                  {{ referenceLabel(item) }}
                </td>
              </tr>
            </tbody>
          </table>
        </template>

        <template v-else-if="activeCategory === 'function'">
          <button
            v-for="fn in pagedItems"
            :key="functionKey(fn)"
            class="palette-result"
            type="button"
            @click="emitInsert(functionTemplate(fn))"
          >
            <span>{{
              fn.funcName || fn.functionLabel || functionCode(fn)
            }}</span>
            <code>{{ functionCode(fn) }}()</code>
          </button>
        </template>

        <template v-else-if="activeCategory === 'list'">
          <button
            v-if="pagedItems.length"
            class="palette-action palette-list-query"
            type="button"
            @click="insertListQuery"
          >
            <el-icon><el-icon-collection /></el-icon>
            <span
              ><strong>配置名单查询</strong
              ><small>选择名单、字段及组合命中模式</small></span
            >
          </button>
        </template>

        <template v-else-if="activeCategory === 'operation'">
          <div class="palette-grid">
            <button
              v-for="item in pagedItems"
              :key="item.key"
              type="button"
              @click="emitInsert(operationTemplate(item.value))"
            >
              {{ item.label }}
            </button>
          </div>
        </template>

        <template v-else-if="activeCategory === 'transform'">
          <button
            v-for="item in pagedItems"
            :key="item.key"
            class="palette-action"
            type="button"
            @click="insertTransform(item.key)"
          >
            {{ item.label }}
          </button>
        </template>

        <div v-if="showEmpty" class="palette-empty">
          <el-icon><el-icon-search /></el-icon>
          <span>当前分类暂无匹配内容</span>
        </div>
      </div>

      <el-pagination
        v-if="needsPaging"
        class="palette-pagination"
        size="small"
        layout="total,prev,pager,next"
        :current-page="page"
        :page-size="pageSize"
        :total="filteredItems.length"
        @current-change="page = $event"
      />
      <p class="palette-tip">
        先选中中间位置，再点击资源；路径会优先反解为已有字段。
      </p>
    </section>
    <div
      class="palette-resize-handle palette-resize-handle--outer"
      title="拖拽调整资源内容宽度"
      @mousedown.prevent.stop="startResize('content', $event)"
      @touchstart.prevent.stop="startTouchResize('content', $event)"
    />
  </aside>
</template>

<script>
import { markRaw } from 'vue'
import {
  Collection as ElIconCollection,
  Search as ElIconSearch,
} from '@element-plus/icons-vue'
import { $emit } from '../../utils/gogocodeTransfer'
import {
  createAccessOperand,
  createArrayOperand,
  createCastOperand,
  createListQueryOperand,
  createLiteralOperand,
  createOperationOperand,
  createPathOperand,
  createReferenceOperand,
} from '@/utils/operand'
import { varTypeLabel } from '@/constants/varTypes'
import {
  REFERENCE_PICKER_CATEGORIES,
  pickerReferenceCategory,
} from '@/utils/pickerCategories'
import {
  filterReferenceGroups,
  groupReferenceOptions,
  referenceChildDisplayName,
  referenceChildRelativePath,
} from '@/utils/referenceGroups'
import { createFunctionTemplate } from './expressionTree'

const OPERATORS = [
  '+',
  '-',
  '*',
  '/',
  '%',
  '==',
  '!=',
  '>',
  '>=',
  '<',
  '<=',
  '&&',
  '||',
]

export default {
  data() {
    return {
      activeCategory: '',
      keyword: '',
      expandedGroupKey: '',
      groupChildPages: {},
      page: 1,
      pageSize: 50,
      operators: OPERATORS,
      categoryWidth: 156,
      contentWidth: 360,
      resizeTarget: '',
      resizeStartX: 0,
      resizeStartWidth: 0,
      bodyCursor: '',
      bodyUserSelect: '',
      ElIconSearch: markRaw(ElIconSearch),
    }
  },
  components: {
    ElIconCollection,
    ElIconSearch,
  },
  name: 'ExpressionPalette',
  props: {
    vars: { type: Array, default: () => [] },
    functions: { type: Array, default: () => [] },
    listOptions: { type: Array, default: () => [] },
    allowedKinds: { type: Array, default: () => [] },
    expectedType: { type: String, default: '' },
  },
  computed: {
    categories() {
      const result = []
      const manualCount =
        (this.allows('LITERAL') ? 1 : 0) + (this.allows('PATH') ? 1 : 0)
      if (manualCount)
        result.push({ key: 'manual', label: '手动输入', count: manualCount })
      if (this.allows('REFERENCE')) {
        REFERENCE_PICKER_CATEGORIES.forEach((category) => {
          const count = this.vars.filter(
            (item) => pickerReferenceCategory(item) === category.key
          ).length
          result.push({ key: category.key, label: category.label, count })
        })
      }
      if (this.allows('FUNCTION') && this.functions.length)
        result.push({
          key: 'function',
          label: '函数/方法',
          count: this.functions.length,
        })
      if (this.allows('LIST_QUERY'))
        result.push({ key: 'list', label: '名单查询', count: 1 })
      if (this.allows('OPERATION'))
        result.push({
          key: 'operation',
          label: '运算符',
          count: this.operators.length,
        })
      const transformCount =
        (this.allows('ACCESS') ? 2 : 0) +
        (this.allows('CAST') ? 1 : 0) +
        (this.allows('ARRAY') ? 1 : 0)
      if (transformCount)
        result.push({
          key: 'transform',
          label: '取值与转换',
          count: transformCount,
        })
      return result
    },
    isReferenceCategory() {
      return REFERENCE_PICKER_CATEGORIES.some(
        (item) => item.key === this.activeCategory
      )
    },
    isGroupedReferenceCategory() {
      return this.activeCategory === 'object' || this.activeCategory === 'model'
    },
    manualItems() {
      const items = []
      if (this.allows('LITERAL'))
        items.push({
          key: 'LITERAL',
          label: '输入阈值',
          description: '数值、文本、布尔值或日期',
          icon: 'EditPen',
        })
      if (this.allows('PATH'))
        items.push({
          key: 'PATH',
          label: '输入字段路径',
          description: '优先反解为已有字段并保留稳定 ID',
          icon: 'Link',
        })
      return items
    },
    operationItems() {
      return this.operators.map((value) => ({
        key: value,
        value,
        label: value,
        searchTexts: [value, '运算符'],
      }))
    },
    transformItems() {
      const items = []
      if (this.allows('ACCESS'))
        items.push(
          {
            key: 'KEY',
            label: '取字典 Key',
            searchTexts: ['取字典 key', '对象取值'],
          },
          {
            key: 'INDEX',
            label: '取数组 Index',
            searchTexts: ['取数组 index', '数组下标'],
          }
        )
      if (this.allows('CAST'))
        items.push({
          key: 'CAST',
          label: '类型转换',
          searchTexts: ['类型转换', 'cast'],
        })
      if (this.allows('ARRAY'))
        items.push({
          key: 'ARRAY',
          label: '数组',
          searchTexts: ['数组', 'array'],
        })
      return items
    },
    activeItems() {
      if (this.isReferenceCategory) {
        const items = this.vars.filter(
          (item) => pickerReferenceCategory(item) === this.activeCategory
        )
        return this.isGroupedReferenceCategory
          ? groupReferenceOptions(items, this.activeCategory)
          : items
      }
      if (this.activeCategory === 'function') return this.functions
      if (this.activeCategory === 'manual') return this.manualItems
      if (this.activeCategory === 'list')
        return [
          {
            key: 'LIST_QUERY',
            label: '配置名单查询',
            searchTexts: ['名单查询', '名单', 'list'],
          },
        ]
      if (this.activeCategory === 'operation') return this.operationItems
      if (this.activeCategory === 'transform') return this.transformItems
      return []
    },
    filteredItems() {
      const key = this.keyword.trim().toLowerCase()
      if (this.isGroupedReferenceCategory)
        return filterReferenceGroups(this.activeItems, key, this.searchTexts)
      if (!key) return this.activeItems
      return this.activeItems.filter((item) =>
        this.searchTexts(item).some((value) => value.includes(key))
      )
    },
    pagedItems() {
      const start = (this.page - 1) * this.pageSize
      return this.filteredItems.slice(start, start + this.pageSize)
    },
    needsPaging() {
      return this.filteredItems.length > this.pageSize
    },
    searchPlaceholder() {
      return '搜索当前分类的名称或编码'
    },
    showEmpty() {
      return this.filteredItems.length === 0
    },
    paletteStyle() {
      return {
        gridTemplateColumns:
          this.categoryWidth + 'px 6px ' + this.contentWidth + 'px 6px',
      }
    },
  },
  watch: {
    categories: {
      deep: true,
      immediate: true,
      handler() {
        this.ensureActiveCategory()
      },
    },
    filteredItems(items) {
      if (
        this.isGroupedReferenceCategory &&
        this.keyword &&
        items.length === 1
      ) {
        this.expandedGroupKey = items[0].groupKey
      } else if (
        this.expandedGroupKey &&
        !items.some((item) => item.groupKey === this.expandedGroupKey)
      ) {
        this.expandedGroupKey = ''
      }
    },
  },
  beforeUnmount() {
    this.stopResize()
  },
  methods: {
    allows(kind) {
      return !this.allowedKinds.length || this.allowedKinds.includes(kind)
    },
    ensureActiveCategory() {
      if (this.categories.some((item) => item.key === this.activeCategory))
        return
      const preferred =
        this.categories.find((item) => item.key === 'standalone') ||
        this.categories[0]
      this.activeCategory = preferred ? preferred.key : ''
    },
    selectCategory(key) {
      this.activeCategory = key
      this.expandedGroupKey = ''
      this.page = 1
    },
    insertManual(kind) {
      if (!this.allows(kind)) return
      const operand =
        kind === 'PATH'
          ? createPathOperand('')
          : createLiteralOperand('', this.expectedType || 'STRING')
      this.emitInsert(operand)
    },
    emitInsert(operand) {
      $emit(this, 'insert', operand)
    },
    insertListQuery() {
      this.emitInsert(
        createListQueryOperand({
          combinationMode: 'ANY_FIELD_ANY_LIST',
          matchMode: 'IN_LIST',
        })
      )
    },
    searchTexts(item) {
      if (Array.isArray(item.searchTexts))
        return item.searchTexts.map((value) => String(value).toLowerCase())
      if (this.activeCategory === 'function') {
        return [
          this.functionCode(item),
          item.funcName,
          item.functionLabel,
          item.functionName,
        ]
          .filter(Boolean)
          .map((value) => String(value).toLowerCase())
      }
      if (item.description)
        return [item.label, item.description, item.key]
          .filter(Boolean)
          .map((value) => String(value).toLowerCase())
      const ref = item._ref || {}
      return [
        item.varCode,
        item.varLabel,
        item.refCode,
        item.label,
        ref.objectLabel,
        ref.modelLabel,
      ]
        .filter(Boolean)
        .map((value) => String(value).toLowerCase())
    },
    referenceTemplate(item) {
      return createReferenceOperand(item)
    },
    toggleGroup(group) {
      this.expandedGroupKey =
        this.expandedGroupKey === group.groupKey ? '' : group.groupKey
    },
    groupChildPage(group) {
      return this.groupChildPages[group.groupKey] || 1
    },
    setGroupChildPage(group, page) {
      this.groupChildPages[group.groupKey] = page
    },
    pagedGroupChildren(group) {
      const start = (this.groupChildPage(group) - 1) * this.pageSize
      return group.children.slice(start, start + this.pageSize)
    },
    childRelativePath(item) {
      return referenceChildRelativePath(item, this.activeCategory)
    },
    childDisplayName(item) {
      return referenceChildDisplayName(item, this.activeCategory)
    },
    functionTemplate(fn) {
      return createFunctionTemplate(fn)
    },
    operationTemplate(operator) {
      return createOperationOperand([
        { operand: null },
        { operator, operand: null },
      ])
    },
    accessTemplate(type) {
      return createAccessOperand(
        null,
        type,
        createLiteralOperand('', type === 'INDEX' ? 'NUMBER' : 'STRING')
      )
    },
    castTemplate(type) {
      return createCastOperand(type, null)
    },
    arrayTemplate() {
      return createArrayOperand([null])
    },
    insertTransform(key) {
      if (key === 'KEY' || key === 'INDEX')
        this.emitInsert(this.accessTemplate(key))
      else if (key === 'CAST') this.emitInsert(this.castTemplate('NUMBER'))
      else if (key === 'ARRAY') this.emitInsert(this.arrayTemplate())
    },
    functionCode(fn) {
      return (
        fn.functionCode ||
        fn.funcCode ||
        fn.functionName ||
        fn.funcName ||
        fn.name ||
        ''
      )
    },
    functionKey(fn) {
      return String(
        fn.functionId != null
          ? fn.functionId
          : fn.id != null
          ? fn.id
          : this.functionCode(fn)
      )
    },
    referenceKey(item) {
      const type =
        item._refType ||
        item.refType ||
        (item._ref && item._ref.refType) ||
        pickerReferenceCategory(item)
      const id =
        item._varId != null
          ? item._varId
          : item.id != null
          ? item.id
          : item.refId != null
          ? item.refId
          : this.referenceCode(item)
      return type + ':' + id
    },
    referenceCode(item) {
      return item.varCode || item.refCode || item.code || ''
    },
    referenceLabel(item) {
      return (
        item.varLabelText ||
        item.varLabel ||
        item.label ||
        this.referenceCode(item)
      )
    },
    typeLabel(type) {
      return varTypeLabel(type)
    },
    typeChar(varType) {
      const map = {
        STRING: 's',
        NUMBER: 'i',
        INTEGER: 'i',
        DOUBLE: 'i',
        PROBABILITY: 'i',
        BOOLEAN: 'b',
        DATE: 'd',
        ENUM: 'e',
        OBJECT: 'o',
        LIST: 'l',
        MAP: 'm',
        MODEL: 'M',
      }
      return map[varType] || '?'
    },
    startResize(target, event) {
      this.beginResize(target, event.clientX)
    },
    startTouchResize(target, event) {
      const touch = event.touches && event.touches[0]
      if (touch) this.beginResize(target, touch.clientX)
    },
    beginResize(target, clientX) {
      this.resizeTarget = target
      this.resizeStartX = clientX
      this.resizeStartWidth =
        target === 'category' ? this.categoryWidth : this.contentWidth
      this.bodyCursor = document.body.style.cursor
      this.bodyUserSelect = document.body.style.userSelect
      document.body.style.cursor = 'col-resize'
      document.body.style.userSelect = 'none'
      window.addEventListener('mousemove', this.onResize)
      window.addEventListener('mouseup', this.stopResize)
      window.addEventListener('touchmove', this.onTouchResize, {
        passive: false,
      })
      window.addEventListener('touchend', this.stopResize)
      window.addEventListener('touchcancel', this.stopResize)
    },
    onResize(event) {
      this.resizeColumn(this.resizeTarget, event.clientX - this.resizeStartX)
    },
    onTouchResize(event) {
      const touch = event.touches && event.touches[0]
      if (!touch) return
      event.preventDefault()
      this.resizeColumn(this.resizeTarget, touch.clientX - this.resizeStartX)
    },
    resizeColumn(target, delta) {
      if (target === 'category') {
        this.categoryWidth = Math.min(
          240,
          Math.max(128, this.resizeStartWidth + delta)
        )
        return
      }
      const available = window.innerWidth - this.categoryWidth - 12 - 420 - 320
      const maximum = Math.max(280, Math.min(640, available))
      this.contentWidth = Math.min(
        maximum,
        Math.max(280, this.resizeStartWidth + delta)
      )
    },
    stopResize() {
      window.removeEventListener('mousemove', this.onResize)
      window.removeEventListener('mouseup', this.stopResize)
      window.removeEventListener('touchmove', this.onTouchResize)
      window.removeEventListener('touchend', this.stopResize)
      window.removeEventListener('touchcancel', this.stopResize)
      if (!this.resizeTarget) return
      this.resizeTarget = ''
      document.body.style.cursor = this.bodyCursor || ''
      document.body.style.userSelect = this.bodyUserSelect || ''
    },
  },
  emits: ['insert'],
}
</script>

<style scoped>
.expression-palette {
  display: grid;
  min-width: 0;
  min-height: 0;
  flex: none;
  border-right: 1px solid #e8edf3;
  background: #fff;
}
.palette-categories {
  overflow-y: auto;
  padding: 12px 8px;
  border-right: 1px solid #e8edf3;
  background: #f7f9fc;
}
.palette-category {
  display: flex;
  width: 100%;
  min-height: 38px;
  align-items: center;
  gap: 8px;
  margin: 2px 0;
  padding: 7px 9px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #526278;
  cursor: pointer;
  text-align: left;
  white-space: nowrap;
}
.palette-category > span:first-child {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.palette-category:hover {
  background: #edf3fb;
  color: #26364d;
}
.palette-category--active {
  background: #e7f0ff;
  color: #1f67d2;
  font-weight: 600;
}
.palette-category__count {
  flex: none;
  min-width: 24px;
  margin-left: auto;
  color: #8a98aa;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  text-align: right;
}
.palette-resize-handle {
  position: relative;
  z-index: 3;
  width: 6px;
  cursor: col-resize;
  background: #f3f6fa;
}
.palette-resize-handle::after {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 2px;
  width: 2px;
  background: transparent;
  content: '';
}
.palette-resize-handle:hover::after {
  background: #2878ff;
}
.palette-resize-handle--outer {
  border-right: 1px solid #e8edf3;
}
.palette-content {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  padding: 12px;
  background: #fbfcfe;
}
.palette-search {
  flex: none;
  margin-bottom: 10px;
}
.palette-results {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.palette-result,
.palette-action,
.palette-manual-kind {
  display: flex;
  width: 100%;
  min-height: 38px;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin: 3px 0;
  padding: 7px 9px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: #26364d;
  cursor: pointer;
  text-align: left;
}
.palette-result:hover,
.palette-action:hover,
.palette-manual-kind:hover {
  border-color: #b9d3ff;
  background: #edf5ff;
}
.palette-result span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.palette-result code {
  flex: 0 1 42%;
  overflow: hidden;
  color: #7a8799;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.palette-reference-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  font-size: 12px;
}
.palette-reference-table th {
  position: sticky;
  z-index: 1;
  top: 0;
  padding: 8px;
  border-bottom: 1px solid #dce4ee;
  background: #f7f9fc;
  text-align: left;
}
.palette-reference-table th:first-child {
  width: 48px;
  text-align: center;
}
.palette-reference-table th:nth-child(2) {
  width: 46%;
}
.palette-reference-table td {
  padding: 8px;
  border-bottom: 1px solid #edf1f5;
  overflow-wrap: anywhere;
  vertical-align: top;
}
.palette-reference-table td:first-child {
  text-align: center;
}
.palette-reference-table tbody tr {
  cursor: pointer;
}
.palette-reference-table tbody tr:hover {
  background: #edf5ff;
}
.palette-reference-group {
  cursor: pointer;
}
.palette-group-toggle {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: #607089;
  font-size: 11px;
}
.palette-reference-children-row td {
  padding: 6px 8px;
  background: #f8fafc;
}
.palette-reference-child {
  display: grid;
  width: 100%;
  grid-template-columns: minmax(90px, 1fr) 22px minmax(90px, 1fr);
  align-items: center;
  gap: 8px;
  padding: 7px 9px;
  border: 0;
  border-bottom: 1px solid #edf1f5;
  background: transparent;
  color: #26364d;
  cursor: pointer;
  text-align: left;
}
.palette-reference-child:hover {
  background: #e9f3ff;
}
.palette-reference-child code {
  overflow: hidden;
  color: #6f7f92;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.palette-reference-code {
  color: #6f7f92;
  font-family: Consolas, monospace;
}
.palette-reference-name {
  color: #26364d;
  font-weight: 500;
}
.palette-type-badge {
  display: inline-flex;
  width: 20px;
  height: 20px;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  background: #c0c4cc;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
}
.palette-type-badge--s {
  background: #409eff;
}
.palette-type-badge--i {
  background: #e6a23c;
}
.palette-type-badge--b {
  background: #67c23a;
}
.palette-type-badge--d {
  background: #9c27b0;
}
.palette-type-badge--e {
  background: #f56c6c;
}
.palette-type-badge--o {
  background: #00bcd4;
}
.palette-type-badge--l {
  background: #ff9800;
}
.palette-type-badge--m {
  background: #909399;
}
.palette-type-badge--M {
  background: #13c2c2;
}
.palette-manual-kinds {
  display: grid;
  gap: 8px;
}
.palette-manual-kind {
  justify-content: flex-start;
  min-height: 54px;
  border-color: #dce4ee;
  background: #fff;
}
.palette-manual-kind i {
  flex: none;
  color: #2878ff;
  font-size: 17px;
}
.palette-manual-kind span,
.palette-action span {
  display: grid;
  gap: 3px;
}
.palette-manual-kind strong,
.palette-action strong {
  font-size: 13px;
  font-weight: 600;
}
.palette-manual-kind small,
.palette-action small {
  color: #8290a3;
  line-height: 1.4;
}
.palette-manual-kind--active {
  border-color: #2878ff;
  background: #edf5ff;
}
.palette-manual-editor {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid #dce4ee;
  border-radius: 7px;
  background: #fff;
}
.palette-manual-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 10px;
}
.palette-path-candidates {
  margin-top: 10px;
}
.palette-path-candidates p {
  margin: 0 0 6px;
  color: #c45656;
  font-size: 12px;
}
.palette-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(40px, 1fr));
  gap: 7px;
}
.palette-grid button {
  height: 34px;
  border: 1px solid #dce3ec;
  border-radius: 5px;
  background: #fff;
  color: #26364d;
  cursor: pointer;
}
.palette-grid button:hover {
  border-color: #2878ff;
  color: #2878ff;
}
.palette-action {
  justify-content: flex-start;
  border-color: #dce4ee;
  background: #fff;
}
.palette-empty {
  display: grid;
  min-height: 160px;
  place-content: center;
  gap: 8px;
  color: #9aa7b7;
  text-align: center;
}
.palette-empty i {
  font-size: 22px;
}
.palette-pagination {
  flex: none;
  margin-top: 8px;
  text-align: center;
}
.palette-tip {
  flex: none;
  margin: 10px 0 0;
  color: #8794a7;
  font-size: 11px;
  line-height: 1.55;
}
</style>
