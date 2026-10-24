<template>
  <div class="var-picker-wrap" :style="{ width: width || '100%' }">
    <!-- 手动输入模式 -->
    <template v-if="!operandMode && customMode">
      <div class="custom-input-row">
        <el-input
          v-model="localCustomValue"
          :size="size"
          :disabled="disabled"
          :placeholder="placeholder || '输入变量编码'"
          clearable
          @update:model-value="onCustomInput"
          @clear="onCustomClear"
        />
        <el-tooltip
          v-if="allowCustom && hasVarOptions"
          content="切换为从变量管理选择"
          placement="top"
        >
          <span class="mode-switch" @click="customMode = false"
            ><el-icon><el-icon-collection /></el-icon
          ></span>
        </el-tooltip>
      </div>
    </template>

    <!-- 选择模式 -->
    <template v-else>
      <div :class="{ 'select-input-row': !virtualRef }">
        <el-popover
          v-if="groupedByCategory && (hasVarOptions || operandMode || !allowCustom)"
          ref="popover"
          :visible="popoverVisible"
          :virtual-ref="virtualRef"
          :virtual-triggering="!!virtualRef"
          placement="bottom-start"
          :width="popoverWidth"
          :popper-class="'var-picker-popover' + (popoverVisible ? '' : ' var-picker-popover--closed')"
          :show-arrow="false"
          :popper-style="panelPositionStyle"
          :popper-options="{ strategy: 'fixed', modifiers: [{ name: 'computeStyles', options: { gpuAcceleration: false } }, { name: 'preventOverflow', options: { altAxis: true, tether: false, padding: 4 } }] }"
        >
          <field-picker-panel
            v-if="catalogMounted"
            ref="fieldPanel" :items="panelItems" :selected-key="panelSelectedKey" :visible="popoverVisible"
            :selected-keys="panelSelectedKeys" :recent-keys="panelRecentKeys"
            :query="searchText" :category="panelCategory" :loading="loading"
            :expected-type="typeFilter || (operandMode ? expectedType : '')"
            :visible-tags="visibleTags" :selectable-tags="selectableTags"
            :width="panelWidth" :height="panelHeight" :initial-page-size="resolvedPageSize"
            :editor-mode="editorMode"
            :editor-kind="editorKind"
            :manual-kinds="operandMode ? allowedKinds.filter(kind => ['LITERAL', 'PATH'].includes(kind) && canUseAction(kind)) : []"
            :expression="allowExpression && operandMode && !writableOnly && canUseAction('EXPRESSION') && allowedKinds.some(kind => ['FUNCTION', 'OPERATION', 'ACCESS', 'CAST', 'ARRAY', 'LIST_QUERY'].includes(kind))"
            @category="panelCategory = $event" @search="searchText = $event" @select="onItemClick"
            @manual="requestManualEdit" @expression="requestExpression" @close="closePopover"
            @back="$emit('editor-back')"
            @move="panelPosition = $event" @resize="resizePanel" @page-size="onPageSizeChange"
          ><template #editor><slot name="editor" :visible="popoverVisible && editorMode" /></template>
          <template #editor-actions><slot name="editor-actions" /></template>
          <template v-if="$slots.version || operandMode && !writableOnly" #version="scope">
            <slot v-if="$slots.version" name="version" v-bind="scope" />
            <field-version-select v-else :item="scope.item" :selection="fieldVersionSelection(scope.item)" @change="changeFieldVersion(scope.item, $event)" @loading="scope.item && (pendingVersions[pendingVersionKey(scope.item)] = $event)" />
          </template></field-picker-panel>
          <template v-if="!virtualRef" v-slot:reference>
            <el-input
              ref="reference"
              class="vp-reference"
              :class="{ 'vp-reference--readonly': operandMode, 'vp-reference--content': showContentReference }"
              :size="size"
              :disabled="disabled"
              :placeholder="placeholder || '选择变量/常量/对象字段'"
              :model-value="referenceInputValue"
              :title="showContentReference ? referenceContentTitle : undefined"
              :readonly="operandMode"
              aria-label="打开内容选择器"
              aria-haspopup="dialog"
              :aria-expanded="popoverVisible ? 'true' : 'false'"
              @mousedown="onReferenceMouseDown"
              @focus="onInputFocus"
              @update:model-value="onReferenceInput"
              @click.stop="onInputClick"
              @keydown="onReferenceKeydown"
            >
              <template v-slot:prefix><content-reference v-if="showContentReference" :value="referenceOperand" :options="vars" :sensitive="sensitive" /></template>
              <template v-slot:suffix>
                <el-icon
                  v-if="!value || !clearable"
                  class="el-input__icon vp-arrow-btn"
                  @mousedown.prevent
                  @click.stop="openPopover"
                >
                  <el-icon-arrow-down />
                </el-icon>
                <el-icon
                  v-if="value && clearable"
                  class="el-input__icon vp-clear-btn"
                  @mousedown.stop
                  @click.stop="onClearValue"
                >
                  <el-icon-close />
                </el-icon>
              </template>
            </el-input>
          </template>
        </el-popover>

        <!-- 无变量时回退到输入框 -->
        <el-input
          v-else
          v-model="localCustomValue"
          :size="size"
          :disabled="disabled"
          :placeholder="placeholder || '输入变量编码'"
          clearable
          @focus="onInputFocus"
          @update:model-value="onCustomInput"
          @clear="onCustomClear"
        />

        <el-tooltip
          v-if="!operandMode && allowCustom && hasVarOptions"
          content="切换为手动输入变量"
          placement="top"
        >
          <span class="mode-switch" @click="customMode = true"
            ><el-icon><el-icon-edit /></el-icon
          ></span>
        </el-tooltip>
      </div>
    </template>
  </div>
</template>

<script>
import { markRaw } from 'vue'
import FieldPickerPanel from './FieldPickerPanel.vue'
import ContentReference from './ContentReference.vue'
import { contentReferenceParts } from '@/utils/contentReference'
import FieldVersionSelect from './FieldVersionSelect.vue'
import { versionFields, resourceVersionIdentity } from '@/utils/resourceVersion'
import { getFieldVersionFields } from '@/api/fieldPickerVersion'
import { fieldIdentity, pickerTypeMatches, pickerFunctionTypeMatches } from '@/utils/fieldPicker'
import { arrayLiteralDraft } from '@/utils/literalType'
import { pickerReferenceDisplay } from '@/utils/pickerReferenceDisplay'
import { operationOptions } from '@/utils/contentOperation'
import { recentFieldKeys, rememberField, selectedFieldKeys } from '@/utils/fieldPickerHistory'
import { preferredFieldPageSize, normalizeFieldPageSize, setPreferredFieldPageSize } from '@/utils/fieldPickerPreferences'
import { canUsePickerAction, fieldPickerTags, matchesFieldTags } from '@/utils/fieldPickerPolicy'
import {
  Collection as ElIconCollection,
  ArrowDown as ElIconArrowDown,
  Close as ElIconClose,
  Edit as ElIconEdit,
  Search as ElIconSearch,
} from '@element-plus/icons-vue'
import { $emit } from '../../utils/gogocodeTransfer'
import { varTypeLabel, varTypeTagColor } from '@/constants/varTypes'
import { formatVarDisplay } from '@/utils/varDisplay'
import { formatExpressionFormula } from '@/utils/expressionDisplay'
import { REFERENCE_PICKER_CATEGORIES, API_PICKER_CATEGORIES } from '@/utils/pickerCategories'
import {
  groupReferenceOptions,
  referenceChildRelativePath,
} from '@/utils/referenceGroups'
import {
  createFunctionOperand,
  createReferenceOperand,
  createOperationOperand,
} from '@/utils/operand'

const openPickers = []

export default {
  data() {
    return {
      /** 当前是否处于手动输入模式 */
      customMode: false,
      /** 手动输入模式下的本地值，避免依赖 prop 异步更新导致失焦清空 */
      localCustomValue: this.value || '',
      /** popover 显示状态 */
      popoverVisible: false,
      fieldPanelInitialized: false,
      /** 当前选中的分类 */
      activeCategory: this.operandMode ? 'manual' : 'standalone',
      /** 搜索文本 */
      searchText: '',
      /** 输入框内临时检索文本，不直接改写外部绑定值 */
      referenceKeyword: '',
      /** 展开的数据对象 varCode */
      expandedObject: null,
      /** 大字段集合分页 */
      fieldPageSize: 100,
      rightPage: 1,
      objectChildPages: {},
      /** 弹窗尺寸，可通过右下角拖拽调整 */
      panelWidth: 780,
      panelHeight: 500,
      panelCategory: 'all',
      panelPosition: null,
      maxPanelWidth: 1440,
      maxPanelHeight: 960,
      resizingPanel: false,
      panelBodyCursor: '',
      panelBodyUserSelect: '',
      suppressFocusOpen: false,
      pointerFocusPending: false,
      focusOpenTimer: null,
      pendingValuePosition: false,
      stagedVersions: {},
      groupVersions: {},
      pendingVersions: {},
      groupRestoreRequests: {},
      ElIconSearch: markRaw(ElIconSearch),
    }
  },
  components: {
    FieldPickerPanel,
    ContentReference,
    FieldVersionSelect,
    ElIconCollection,
    ElIconArrowDown,
    ElIconClose,
    ElIconEdit,
  },
  name: 'VarPicker',
  props: {
    /** 绑定值：varCode（默认）或 id（数字） */
    value: { type: [String, Number, Object], default: '' },
    vars: { type: Array, default: () => [] },
    /** 当前设计器页面已选择过的字段，用于快速复用 */
    selectedVars: { type: Array, default: () => [] },
    typeFilter: { type: String, default: '' },
    showAllWhenFilterEmpty: { type: Boolean, default: false },
    placeholder: { type: String, default: '' },
    size: { type: String, default: 'small' },
    width: { type: String, default: '' },
    grouped: { type: Boolean, default: false },
    groupedByCategory: { type: Boolean, default: true },
    loading: { type: Boolean, default: false },
    // 首次打开前不构建目录和字段树；打开后保留面板，以免丢失表达式和阈值草稿。
    lazyCatalog: { type: Boolean, default: true },
    disabled: { type: Boolean, default: false },
    sensitive: Boolean,
    virtualRef: { type: Object, default: null },
    /** 是否允许手动输入自定义变量（不在变量管理中的） */
    allowCustom: { type: Boolean, default: true },
    editOnInput: { type: Boolean, default: false },
    clearable: { type: Boolean, default: true },
    /**
     * 指定 value 字段类型：
     * - 'code'（默认）：使用 varCode 作为 option value
     * - 'id'：使用 var.id 作为 option value（用于模型出入参关联变量）
     * - 'reference'：使用 refType:id 作为 option value（用于混合引用选择）
     */
    valueKey: { type: String, default: 'code' },
    /**
     * 表头标签配置，支持不同选择场景：
     * - 'variable': 变量编码 | 变量名称（默认）
     * - 'dataObject': 属性字段路径 | 属性名称
     * - 'model': 模型编码 | 模型名称
     * 或直接传入对象 { codeLabel, nameLabel }
     */
    columnLabels: { type: [String, Object], default: 'variable' },
    /** 鍊间笉鍦ㄥ€欓€夐」涓椂鏄惁鑷姩鍒囨崲鍒版墜杈撴ā寮?*/
    autoSwitchCustom: { type: Boolean, default: true },
    /** 值表达式使用操作数模式；资源绑定使用 ID / reference 模式。 */
    operandMode: { type: Boolean, default: false },
    allowedKinds: {
      type: Array,
      default: () => ['LITERAL', 'PATH', 'REFERENCE'],
    },
    expectedType: { type: String, default: '' },
    writableOnly: { type: Boolean, default: false },
    visibleTags: { type: [Array, Object], default: null },
    selectableTags: { type: [Array, Object], default: null },
    functions: { type: Array, default: () => [] },
    pageSize: { type: Number, default: null },
    versionSelection: { type: Object, default: null },
    editorMode: Boolean,
    editorKind: { type: String, default: 'EXPRESSION' },
    manualInPanel: Boolean,
    allowExpression: { type: Boolean, default: true },
    dismissOnOutside: { type: Boolean, default: true },
    restoreManualOnOpen: { type: Boolean, default: true },
  },
  watch: {
    vars() {
      if (!this.hasVarOptions) {
        this.customMode = false
      } else {
        this._autoSwitchIfUnmatched()
      }
      this.restorePendingPosition()
      if (this.popoverVisible) this.restoreVersionGroup()
    },
    functions() { this.restorePendingPosition() },
    categoryList: {
      deep: true,
      immediate: true,

      handler(list) {
        if (!list || list.length === 0) return
        var exists = list.some(
          function (cat) {
            return cat.key === this.activeCategory
          }.bind(this)
        )
        if (!exists) {
          this.activeCategory = list[0].key
          this.rightPage = 1
          this.expandedObject = null
        }
      },
    },
    value(newVal) {
      this.localCustomValue = newVal || ''
      this._autoSwitchIfUnmatched()
    },
    customMode(val) {
      if (val) this.localCustomValue = this.value || ''
    },
    searchText: {
      flush: 'sync',
      handler(value) {
        this.rightPage = 1
        this.objectChildPages = {}
        if (value) this.pendingValuePosition = false
      },
    },
    popoverVisible(val) {
      if (val) { this.fieldPanelInitialized = true; this.stagedVersions = {}; this.restoreVersionGroup() }
      this.$emit('visible-change', val)
      this.updateDocumentListener(val)
      this.setPickerInert(!val)
      if (!val) {
        this.referenceKeyword = ''
        this.stopPanelResize()
      }
    },
  },
  mounted() {
    this._autoSwitchIfUnmatched()
  },
  beforeUnmount() {
    this.updateDocumentListener(false)
    this.stopPanelResize()
    if (this.focusOpenTimer) clearTimeout(this.focusOpenTimer)
  },
  computed: {
    catalogMounted() { return !this.lazyCatalog || this.popoverVisible || this.fieldPanelInitialized },
    resolvedPageSize() { return this.pageSize == null ? preferredFieldPageSize.value : normalizeFieldPageSize(this.pageSize) },
    panelRecentKeys() { return recentFieldKeys.value },
    panelSelectedKeys() { return [...new Set([this.panelSelectedKey, ...selectedFieldKeys(this.value, this.panelItems, this.selectedItems)].filter(Boolean))] },
    basePanelItems() {
      if (!this.catalogMounted) return []
      const expected = this.typeFilter || (this.operandMode ? this.expectedType : '')
      const fields = (!this.operandMode || this.allowsOperandKind('REFERENCE'))
        ? this.vars.filter(item => this.isWritableOption(item)) : []
      const functions = this.operandMode && !this.writableOnly && this.allowsOperandKind('FUNCTION')
        ? this.functions.filter(fn => pickerFunctionTypeMatches(fn.returnType, expected) || this.value?.kind === 'FUNCTION' && this.value.versionMode === 'FIXED' && String(fn.id ?? fn.functionId) === String(this.value.functionId)).map(fn => ({
          _function: true, function: fn, varCode: fn.functionCode || fn.funcCode || fn.functionName || fn.name || '',
          varLabel: fn.functionLabel || fn.funcName || fn.functionName || fn.label || '', varType: fn.returnType || 'FUNCTION',
        })) : []
      if (this.operandMode && !this.writableOnly && this.allowsOperandKind('FUNCTION') && this.value?.kind === 'FUNCTION' && this.value.versionMode === 'FIXED'
        && !functions.some(item => String(item.function.id ?? item.function.functionId) === String(this.value.functionId))) functions.push({
        _function: true, function: { ...this.value, id: this.value.functionId, returnType: this.value.valueType }, varCode: this.value.functionCode,
        varLabel: this.value.label, varType: this.value.valueType,
      })
      const operators = this.operandMode && !this.writableOnly && !expected && this.allowsOperandKind('OPERATION')
        ? operationOptions(this.value?.valueType).map(({ value: operator, label }) => ({ _operation: operator, _varId: operator, _refType: 'OPERATOR', _ref: { category: 'operator' }, varCode: operator, varType: 'OPERATOR', varLabel: label })) : []
      return [...fields, ...functions, ...operators]
    },
    categorizedVars() {
      const result = new Map()
      for (const item of this.vars) {
        const category = (item._ref && item._ref.category) || 'standalone'
        if (this.typeFilter && item.varType !== this.typeFilter) continue
        if (!this.isWritableOption(item)) continue
        if (!result.has(category)) result.set(category, [])
        result.get(category).push(item)
      }
      return result
    },
    panelItems() {
      let items = this.basePanelItems
      for (const [key, group] of Object.entries(this.groupVersions)) {
        if (group.versionMode !== 'FIXED') continue
        const sample = items.find(item => this.versionGroupKey(item) === key)
        const category = key.startsWith('model:') ? 'model' : 'object'
        const prefix = category === 'model' ? 'model' : 'object'
        const fields = [...(group.fields || []), { rootReference: true, refId: group.versionOwnerId,
          refType: category === 'model' ? 'MODEL' : 'DATA_OBJECT_ROOT', code: group.ownerCode, label: group.ownerLabel, valueType: 'OBJECT' }]
        items = items.filter(item => this.versionGroupKey(item) !== key).concat(fields.map(field => ({
          ...field, ...versionFields(group), _groupVersion: versionFields(group), id: field.refId, _varId: field.refId, _refType: field.refType,
          varCode: field.code, varLabel: field.label, varType: field.valueType,
          _ref: { ...sample?._ref, category, [`${prefix}Id`]: group.versionOwnerId, [`${prefix}Code`]: group.ownerCode, [`${prefix}Label`]: group.ownerLabel },
        })))
      }
      return items.map(item => this.versionedItem(item, this.fieldVersionSelection(item)))
    },
    panelSelectedKey() {
      if (this.operandMode && this.value?.kind === 'PATH' && this.value.protocolPath) {
        const item = this.vars.find(item => item._refType === 'API_CONTEXT' && item.varCode === this.value.value)
        return item ? fieldIdentity(item) : ''
      }
      const selected = this.panelItems.find(item => this.isCurrentOption(item))
      return selected ? fieldIdentity(selected) : ''
    },
    panelPositionStyle() {
      return this.panelPosition ? { left: this.panelPosition.left + 'px !important', top: this.panelPosition.top + 'px !important', position: 'fixed', transform: 'none' } : {}
    },
    /** 是否有可选的变量选项 */
    hasVarOptions() {
      return this.vars.length > 0
    },
    /** 弹窗宽度：和可拖拽面板保持一致，避免右侧出现空白区域 */
    popoverWidth() {
      return this.panelWidth
    },
    panelStyle() {
      return {
        width: this.panelWidth + 'px',
        height: this.panelHeight + 'px',
      }
    },
    /** 是否显示搜索框（项数超过 10 时） */
    showSearch() {
      return true
    },
    referenceInputValue() {
      if (this.sensitive && this.value?.kind) return contentReferenceParts(this.value, this.vars, true).map(part => part.text).join(' ')
      if (this.operandMode) {
        return ['LITERAL', 'PATH', 'REFERENCE'].includes(this.value && this.value.kind)
          ? pickerReferenceDisplay(this.value, this.vars)
          : formatExpressionFormula(this.value, { humanReadable: true })
      }
      return this.referenceKeyword || (this.editOnInput ? this.value : this.displayValue)
    },
    showContentReference() { const value = this.referenceOperand; return !this.referenceKeyword && !!value && (!!value.kind && !['REFERENCE', 'PATH'].includes(value.kind) || !!(value.code || value.value || value.varCode || value.refCode)) },
    referenceContentTitle() { return contentReferenceParts(this.referenceOperand, this.vars, this.sensitive).map(part => part.title).join(' · ') },
    referenceOperand() { return this.operandMode ? this.value : this.selectedReferenceOption ? { ...this.selectedReferenceOption, ...this.versionSelection } : null },
    selectedReferenceOption() {
      return this.vars.find(item => this.valueKey === 'reference' ? this.referenceValue(item) === this.value : this.valueKey === 'id' ? String(item.id) === String(this.value) : item.varCode === this.value)
    },
    /** 当前选中的 varCode（用于高亮） */
    currentValue() {
      if (!this.value) return null
      if (this.operandMode && typeof this.value === 'object') {
        var operandOption = this.findOptionByIdentity(
          this.value.refId,
          this.value.refType
        )
        return operandOption
          ? operandOption.varCode
          : this.value.code || this.value.value || null
      }
      if (this.valueKey === 'reference') {
        var referenceParts = String(this.value).split(':')
        var referenceOption = this.findOptionByIdentity(
          referenceParts.slice(1).join(':'),
          referenceParts[0]
        )
        return referenceOption ? referenceOption.varCode : null
      }
      if (this.valueKey === 'id') {
        var found = this.vars.find(
          function (v) {
            return String(v.id) === String(this.value)
          }.bind(this)
        )
        return found ? found.varCode : null
      }
      return this.value
    },
    /** 显示文本 */
    displayValue() {
      if (!this.value) return ''
      var v =
        this.valueKey === 'reference'
          ? this.findOptionByReferenceValue(this.value)
          : this.valueKey === 'id'
          ? this.vars.find(
              function (item) {
                return String(item.id) === String(this.value)
              }.bind(this)
            )
          : this.vars.find(item => item.varCode === this.value)
      if (!v) return this.value
      var ref = v._ref || {}
      var label = this.optionLabel(v)
      var code = v.varCode || ''
      if (!label || label === code) return code
      if (ref.category === 'object') {
        return code ? label + ' ' + this.objectFieldPath(v) : label
      }
      return code ? label + ' ' + code : label
    },
    /** 分类列表（仅显示有数据的分类） */
    categoryList() {
      if (!this.catalogMounted) return []
      var list = [
        { key: 'manual', label: '手动输入', count: 0 },
        { key: 'selected', label: '已选字段', count: 0 },
        ...[...REFERENCE_PICKER_CATEGORIES, ...API_PICKER_CATEGORIES].map(function (item) {
          return { key: item.key, label: item.label, count: 0 }
        }),
        { key: 'function', label: '函数/方法', count: 0 },
      ]
      list.forEach(
        function (item) {
          if (item.key === 'manual') {
            item.count =
              (this.allowsOperandKind('LITERAL') ? 1 : 0) +
              (this.allowsOperandKind('PATH') ? 1 : 0)
          } else if (item.key === 'function') {
            item.count = this.allowsOperandKind('FUNCTION')
              ? this.functions.length
              : 0
          } else {
            item.count = this.categoryItems(item.key).length
          }
        }.bind(this)
      )
      return list.filter(
        function (item) {
          if (item.key === 'manual') return this.operandMode && item.count > 0
          if (item.key === 'function') return this.operandMode && item.count > 0
          if (this.operandMode && !this.allowsOperandKind('REFERENCE'))
            return false
          return item.count > 0
        }.bind(this)
      )
    },
    selectedItems() {
      var result = []
      var seen = {}
      ;(this.selectedVars || []).forEach(
        function (item) {
          var option = this.resolveSelectedOption(item)
          if (!option || !option.varCode) return
          var key = this.optionIdentityKey(option)
          if (seen[key]) return
          seen[key] = true
          result.push(Object.assign({}, option, { _selected: true }))
        }.bind(this)
      )
      return result
    },
    /** 右侧项列表（按当前分类过滤 + 排序）。对象/模型字段自动附加同分组下的所有字段 children） */
    rightItems() {
      if (this.activeCategory === 'function') {
        return this.functions.map(function (fn) {
          return {
            _function: true,
            varCode:
              fn.functionCode ||
              fn.funcCode ||
              fn.functionName ||
              fn.funcName ||
              fn.name ||
              '',
            varLabel:
              fn.functionLabel ||
              fn.funcName ||
              fn.functionName ||
              fn.label ||
              '',
            varType: fn.returnType || 'FUNCTION',
            function: fn,
          }
        })
      }
      if (this.activeCategory === 'selected') {
        return this.selectedItems.filter(this.isWritableOption)
      }
      var list = (this.categorizedVars.get(this.activeCategory) || []).slice()

      if (this.isGroupedFieldCategory(this.activeCategory)) {
        return this.groupFieldItems(list, this.activeCategory)
      }

      return list.sort(function (a, b) {
        return (a.varCode || '').localeCompare(b.varCode || '')
      })
    },
    /** 过滤后的右侧项（支持搜索） */
    filteredRightItems() {
      return this.filterItemsByKeyword(this.rightItems)
    },
    rightNeedsPaging() {
      return this.filteredRightItems.length > this.fieldPageSize
    },
    pagedRightItems() {
      if (!this.rightNeedsPaging) return this.filteredRightItems
      var start = (this.rightPage - 1) * this.fieldPageSize
      return this.filteredRightItems.slice(start, start + this.fieldPageSize)
    },
  },
  methods: {
    pendingVersionKey(item) { return item._versionGroup ? 'group:' + this.versionGroupKey(item) : fieldIdentity(item) },
    versionGroupKey(item) { const id = resourceVersionIdentity(item); return id && ['model', 'dataobject'].includes(id.module) ? `${id.module}:${id.ownerId}` : '' },
    async restoreVersionGroup() {
      const selected = this.value
      if (!this.vars.length || this.vars.every(item => item.catalogMode === 'FROZEN' || item.catalogMode === 'UNAVAILABLE')) return
      if (!this.operandMode || selected?.versionMode !== 'FIXED' || !selected.versionOwnerId || !['MODEL', 'MODEL_OUTPUT', 'DATA_OBJECT', 'DATA_OBJECT_ROOT'].includes(selected.refType)) return
      const module = selected.refType.startsWith('MODEL') ? 'model' : 'dataobject'
      const key = `${module}:${selected.versionOwnerId}`
      const cached = this.groupVersions[key]
      if (selected.versionSource !== 'BUSINESS_VERSION' && String(cached?.versionId) === String(selected.versionId) && cached?.versionSource === selected.versionSource) return
      const request = (this.groupRestoreRequests[key] || 0) + 1
      this.groupRestoreRequests[key] = request
      this.pendingVersions['group:' + key] = true
      try {
        const response = await getFieldVersionFields({ module, ownerId: selected.versionOwnerId }, selected.versionId, selected.versionSource)
        if (this.groupRestoreRequests[key] !== request || this.value !== selected || !this.popoverVisible) return
        this.groupVersions[key] = { ...response.data, versionMode: 'FIXED' }
        this.$nextTick(() => this.$refs.fieldPanel?.locateSelected?.())
      } catch (error) { if (this.groupRestoreRequests[key] === request && this.value === selected && this.popoverVisible) this.$message?.error(error.message || '历史字段加载失败') }
      finally { if (this.groupRestoreRequests[key] === request) this.pendingVersions['group:' + key] = false }
    },
    fieldVersionSelection(item) {
      if (!item) return {}
      if (item._versionGroup) return this.groupVersions[this.versionGroupKey(item)] || versionFields(item)
      return this.stagedVersions[fieldIdentity(item)] || (this.operandMode && this.isCurrentOption(item) ? this.value : versionFields(item)) || {}
    },
    versionedItem(item, selection) {
      if (!selection?.versionMode) return item
      const result = { ...item, ...versionFields(selection) }
      if (selection.versionMode !== 'FIXED') return result
      if (item._function) {
        const code = selection.funcCode || selection.functionCode
        const label = selection.funcName || selection.label
        result.function = { ...item.function, ...selection, id: item.function.id ?? item.function.functionId,
          funcCode: code, functionCode: code, funcName: label, functionLabel: label,
          returnType: selection.returnType || selection.valueType || item.function.returnType }
        result.varCode = result.function.funcCode; result.varLabel = result.function.funcName; result.varType = result.function.returnType
      }
      else {
        if (selection.code) result.varCode = selection.code
        if (selection.label) { result.varLabel = selection.label; result.varLabelText = selection.label }
        if (selection.valueType) result.varType = selection.valueType
        if (Object.hasOwn(selection, 'constantValue')) result.constantValue = selection.constantValue
      }
      return result
    },
    changeFieldVersion(item, selection) {
      if (!item) return
      if (item._versionGroup) {
        this.groupVersions[this.versionGroupKey(item)] = selection
        this.stagedVersions = {}
        return
      }
      const current = this.basePanelItems.find(candidate => fieldIdentity(candidate) === fieldIdentity(item))
      if (selection.versionMode === 'LATEST' && !current) { this.$message?.warning('最新版中没有此字段，请在父级切换最新版后重新选择'); return }
      const base = current || item
      this.stagedVersions[fieldIdentity(item)] = selection
      if (!this.isCurrentOption(item)) return
      const updated = this.versionedItem(base, selection)
      if (!pickerTypeMatches(updated.varType, this.typeFilter || this.expectedType)) {
        this.$message?.warning('此版本的字段类型不符合当前位置要求，请重新选择字段')
        return
      }
      const operand = updated._function ? createFunctionOperand(updated.function, this.value?.args || []) : createReferenceOperand(updated)
      if (this.value?.kind === 'PATH') Object.assign(operand, { kind: 'PATH', relativePath: this.value.relativePath,
        code: operand.code + (this.value.relativePath || ''), value: operand.value + (this.value.relativePath || '') })
      $emit(this, 'update:value', operand)
      $emit(this, 'select', operand)
    },
    canUseAction(kind) { return canUsePickerAction(kind, this.visibleTags, this.selectableTags) },
    onPageSizeChange(value) {
      const size = setPreferredFieldPageSize(value)
      this.$emit('update:pageSize', size)
      this.$emit('page-size', size)
    },
    requestExpression() { this.$emit('expression') },
    resizePanel(size) { this.panelWidth = size.width; this.panelHeight = size.height },
    referenceValue(item) {
      if (!item) return ''
      const id = item._varId != null ? item._varId : item.id
      const refType = item._refType || item.refType || (item._ref && item._ref.refType) || 'REF'
      return id == null || id === '' ? '' : `${refType}:${id}`
    },
    findOptionByReferenceValue(value) {
      if (!value) return null
      const parts = String(value).split(':')
      return this.findOptionByIdentity(parts.slice(1).join(':'), parts[0])
    },
    categoryItems(category) {
      if (category === 'manual') return []
      if (category === 'function') return this.functions
      if (category === 'selected') {
        return this.selectedItems.filter(this.isWritableOption)
      }
      var list = (this.categorizedVars.get(category) || []).slice()

      if (this.isGroupedFieldCategory(category)) {
        return this.groupFieldItems(list, category)
      }

      return list.sort(function (a, b) {
        return (a.varCode || '').localeCompare(b.varCode || '')
      })
    },
    isWritableOption(item) {
      if (!this.writableOnly) return true
      const category = (item && item._ref && item._ref.category) || 'standalone'
      return category === 'standalone' || category === 'object'
    },
    isGroupedFieldCategory(category) {
      return category === 'object' || category === 'model' || category === 'external'
    },
    groupFieldItems(list, category) {
      return groupReferenceOptions(list, category).map(
        function (group) {
          var first = Object.assign({}, group, {
            _fieldGroup: true,
            _fieldGroupKey: this.fieldGroupKeyByCategory(
              group.groupCode,
              category
            ),
            _fieldGroupCategory: category,
          })
          if (category === 'object') {
            first._objectGroup = true
            first._objectGroupKey = group.groupCode
            first.varType = 'OBJECT'
          } else if (category === 'model') {
            first._modelGroup = true
            first._modelGroupKey = group.groupCode
            first.varType = 'MODEL'
          } else if (category === 'external') {
            first._externalGroup = true
            first._externalGroupKey = group.groupCode
            first.varType = 'OBJECT'
          }
          return first
        }.bind(this)
      )
    },
    isFieldGroup(item) {
      return !!(
        item &&
        (item._fieldGroup || item._objectGroup || item._modelGroup)
      )
    },
    fieldGroupKey(item) {
      if (!item) return ''
      if (item._fieldGroupKey) return item._fieldGroupKey
      if (item._objectGroupKey) return item._objectGroupKey
      if (item._modelGroupKey) return 'model:' + item._modelGroupKey
      return ''
    },
    fieldGroupKeyByCategory(code, category) {
      return category === 'model' ? 'model:' + code : code
    },
    fieldGroupCode(item) {
      var category = this.fieldGroupCategory(item)
      return this.fieldGroupCodeByCategory(item, category)
    },
    fieldGroupCodeByCategory(item, category) {
      if (category === 'model') return this.modelGroupCode(item)
      if (category === 'external') return this.externalGroupCode(item)
      return this.objectGroupCode(item)
    },
    fieldGroupLabel(item) {
      var category = this.fieldGroupCategory(item)
      if (category === 'model') return this.modelGroupLabel(item)
      if (category === 'external') return this.externalGroupLabel(item)
      return this.objectGroupLabel(item)
    },
    fieldGroupCategory(item) {
      if (item && item._fieldGroupCategory) return item._fieldGroupCategory
      var ref = (item && item._ref) || {}
      return ref.category || this.activeCategory
    },
    filterItemsByKeyword(items) {
      if (!this.searchText) return items
      var s = this.normalizeSearchText(this.searchText)
      var starts = []
      var contains = []
      items.forEach(
        function (v) {
          var rank = this.searchRank(v, s)
          if (rank === 1) starts.push(v)
          else if (rank === 2) contains.push(v)
        }.bind(this)
      )
      return starts.concat(contains)
    },
    objectGroupCode(item) {
      var ref = (item && item._ref) || {}
      if (ref.objectCode) return ref.objectCode
      var code = item && item.varCode ? item.varCode : ''
      return code.indexOf('.') !== -1
        ? code.split('.')[0]
        : (item && item.objectCode) || 'unknown'
    },
    objectGroupLabel(item) {
      var ref = (item && item._ref) || {}
      return (
        ref.objectLabel ||
        ref.objectCode ||
        (item && item.objectLabel) ||
        this.objectGroupCode(item)
      )
    },
    modelGroupCode(item) {
      var ref = (item && item._ref) || {}
      if (ref.modelCode) return ref.modelCode
      if (item && item.modelCode) return item.modelCode
      var code = item && item.varCode ? item.varCode : ''
      return code.indexOf('.') !== -1 ? code.split('.')[0] : 'unknown'
    },
    modelGroupLabel(item) {
      var ref = (item && item._ref) || {}
      return (
        ref.modelLabel ||
        ref.modelName ||
        (item && item.modelLabel) ||
        this.modelGroupCode(item)
      )
    },
    externalGroupCode(item) {
      var ref = (item && item._ref) || {}
      return ref.apiCode || (item && item.apiCode) || String((item && (item.varCode || item.refCode)) || '').split('.')[0]
    },
    externalGroupLabel(item) {
      var ref = (item && item._ref) || {}
      return ref.apiLabel || ref.apiName || (item && item.apiLabel) || this.externalGroupCode(item)
    },
    objectFieldPath(item) {
      if (!item) return ''
      var code = item.varCode || ''
      if (code.indexOf('.') !== -1) return code
      var ref = item._ref || {}
      var objectCode =
        ref.objectScriptName || ref.objectCode || item.objectCode || ''
      return objectCode ? objectCode + '.' + code : code
    },
    objectFieldRelativePath(item) {
      return referenceChildRelativePath(item, 'object')
    },
    modelFieldRelativePath(item) {
      return referenceChildRelativePath(item, 'model')
    },
    fieldChildRelativePath(item) {
      var ref = (item && item._ref) || {}
      if (ref.category === 'model') return this.modelFieldRelativePath(item)
      return this.objectFieldRelativePath(item)
    },
    objectFieldDisplayName(item) {
      var label = this.optionLabel(item)
      var relativeCode = this.objectFieldRelativePath(item)
      var ref = (item && item._ref) || {}
      var objectLabel = ref.objectLabel || ''
      if (objectLabel && label.indexOf(objectLabel + '/') === 0) {
        label = label.substring(objectLabel.length + 1)
      } else if (label.indexOf('/') !== -1) {
        label = label.substring(label.lastIndexOf('/') + 1)
      }
      if (relativeCode && label.indexOf(relativeCode + ' ') === 0) {
        label = label.substring(relativeCode.length + 1)
      }
      if (
        relativeCode &&
        label.lastIndexOf(' ' + relativeCode) ===
          label.length - relativeCode.length - 1
      ) {
        label = label.substring(0, label.length - relativeCode.length - 1)
      }
      return label || relativeCode
    },
    modelFieldDisplayName(item) {
      var label = this.optionLabel(item)
      var relativeCode = this.modelFieldRelativePath(item)
      var modelLabel = this.modelGroupLabel(item)
      if (modelLabel && label.indexOf(modelLabel + '/') === 0) {
        label = label.substring(modelLabel.length + 1)
      } else if (label.indexOf('/') !== -1) {
        label = label.substring(label.lastIndexOf('/') + 1)
      }
      if (relativeCode && label.indexOf(relativeCode + ' ') === 0) {
        label = label.substring(relativeCode.length + 1)
      }
      if (
        relativeCode &&
        label.lastIndexOf(' ' + relativeCode) ===
          label.length - relativeCode.length - 1
      ) {
        label = label.substring(0, label.length - relativeCode.length - 1)
      }
      return label || relativeCode
    },
    fieldChildDisplayName(item) {
      var ref = (item && item._ref) || {}
      if (ref.category === 'model') return this.modelFieldDisplayName(item)
      return this.objectFieldDisplayName(item)
    },
    fieldCodeWithoutObject(item) {
      var code = item && item.varCode ? item.varCode : ''
      if (code.indexOf('.') === -1) return code
      return code.substring(code.lastIndexOf('.') + 1)
    },
    findOptionByCode(code) {
      if (!code) return null
      var exact = this.vars.find(function (v) {
        return v.varCode === code
      })
      if (exact) return exact
      var matches = this.vars.filter(
        function (v) {
          return (
            v &&
            v._ref &&
            v._ref.category === 'object' &&
            this.fieldCodeWithoutObject(v) === code
          )
        }.bind(this)
      )
      return matches.length === 1 ? matches[0] : null
    },
    findOptionByIdentity(id, refType, relativePath = '') {
      if (id == null || id === '' || !refType) return null
      return (
        this.vars.find(v => this.optionIdentityKey(v) === `${refType}:${id}:${relativePath || ''}`
          || (this.optionIdentityKey(v) === `${refType}:${id}` && !relativePath)) || null
      )
    },
    isCurrentOption(item) {
      if (!item || this.isFieldGroup(item) || this.value == null || this.value === '') return false
      if (this.operandMode && typeof this.value === 'object') {
        if (this.value.kind === 'FUNCTION') {
          const fn = item.function || {}
          return item._function && this.value.functionId != null &&
            String(fn.functionId != null ? fn.functionId : fn.id) === String(this.value.functionId)
        }
        const expectedKey = this.value.refType === 'EXTERNAL_API'
          ? `${this.value.refType}:${this.value.refId}:${this.value.relativePath || ''}`
          : `${this.value.refType}:${this.value.refId}`
        return this.value.refId != null && !!this.value.refType &&
          this.optionIdentityKey(item) === expectedKey
      }
      return this.valueKey === 'reference' ? this.referenceValue(item) === String(this.value) : this.valueKey === 'id' ? String(item.id) === String(this.value) : item.varCode === this.value
    },
    resolveSelectedOption(item) {
      if (item == null || item === '') return null
      if (typeof item === 'string' || typeof item === 'number') return null
      var id =
        item._varId != null
          ? item._varId
          : item.id != null
          ? item.id
          : item.varObj && item.varObj.id
      var refType =
        item._refType ||
        item.refType ||
        (item.varObj && item.varObj.refType) ||
        (item._ref && item._ref.refType)
      return this.findOptionByIdentity(id, refType)
    },
    optionIdentityKey(item) {
      if (item && item._function) {
        const fn = item.function || {}
        return 'FUNCTION:' + (fn.functionId != null ? fn.functionId : fn.id != null ? fn.id : item.varCode)
      }
      var id =
        item &&
        (item._varId != null
          ? item._varId
          : item.id != null
          ? item.id
          : item.varObj && item.varObj.id)
      var refType =
        item &&
        (item._refType ||
          item.refType ||
          (item.varObj && item.varObj.refType) ||
          (item._ref && item._ref.refType))
      if (id != null && id !== '') {
        const relativePath = refType === 'EXTERNAL_API' ? (item.relativePath || '') : ''
        return refType === 'EXTERNAL_API'
          ? `${refType}:${id}:${relativePath}`
          : `${refType || 'REF'}:${id}`
      }
      var cat = (item && item._ref && item._ref.category) || ''
      return cat + ':' + (item && item.varCode ? item.varCode : '')
    },
    optionCategory(item) {
      return (item && item._ref && item._ref.category) || 'standalone'
    },
    optionLabel(item) {
      if (!item) return ''
      return (
        item.varLabelText ||
        item.labelText ||
        item.varName ||
        item.varLabel ||
        ''
      )
    },
    normalizeSearchText(text) {
      return String(text || '')
        .trim()
        .toLowerCase()
    },
    searchTexts(item) {
      if (!item) return []
      var ref = item._ref || {}
      return [
        item.varCode,
        item.varLabel,
        item.varLabelText,
        item.varCodeText,
        ref.objectCode,
        ref.objectScriptName,
        ref.objectLabel,
        ref.modelCode,
        ref.modelLabel,
        ref.modelName,
        this.fieldChildRelativePath(item),
        this.fieldChildDisplayName(item),
      ]
        .filter(Boolean)
        .map(function (text) {
          return String(text).toLowerCase()
        })
    },
    searchRank(item, keyword) {
      var texts = this.searchTexts(item)
      var childRanks = []
      ;(item.children || []).forEach(
        function (child) {
          childRanks.push(this.searchRank(child, keyword))
        }.bind(this)
      )
      if (
        texts.some(function (text) {
          return text.indexOf(keyword) === 0
        }) ||
        childRanks.indexOf(1) !== -1
      )
        return 1
      if (
        texts.some(function (text) {
          return text.indexOf(keyword) !== -1
        }) ||
        childRanks.indexOf(2) !== -1
      )
        return 2
      return 0
    },
    /** 类型短标签（一字符） */
    typeShortLabel(t) {
      var map = {
        STRING: '字',
        NUMBER: '数',
        BOOLEAN: '布',
        DATE: '日',
        ENUM: '枚',
        OBJECT: '对',
        LIST: '列',
        MAP: '映',
      }
      return map[t] || t
    },
    /** 类型标签颜色 */
    typeColor(t) {
      return varTypeTagColor(t)
    },
    /** 分类点击 */
    onCategoryClick(cat) {
      this.pendingValuePosition = false
      this.activeCategory = cat
      this.expandedObject = null
      this.rightPage = 1
    },
    onRightPageChange(page) {
      this.pendingValuePosition = false
      this.rightPage = page
    },
    objectChildPageKey(item) {
      return (
        this.fieldGroupKey(item) || (item && item.varCode ? item.varCode : '')
      )
    },
    objectChildPage(item) {
      var key = this.objectChildPageKey(item)
      return (key && this.objectChildPages[key]) || 1
    },
    objectChildNeedsPaging(item) {
      return this.filteredObjectChildren(item).length > this.fieldPageSize
    },
    pagedObjectChildren(item) {
      var children = this.filteredObjectChildren(item)
      if (!children.length) return []
      if (!this.objectChildNeedsPaging(item)) return children
      var page = this.objectChildPage(item)
      var start = (page - 1) * this.fieldPageSize
      return children.slice(start, start + this.fieldPageSize)
    },
    filteredObjectChildren(item) {
      if (!item || !item.children) return []
      if (!this.searchText) return item.children
      var keyword = this.normalizeSearchText(this.searchText)
      var starts = []
      var contains = []
      item.children.forEach(
        function (child) {
          var rank = this.searchRank(child, keyword)
          if (rank === 1) starts.push(child)
          else if (rank === 2) contains.push(child)
        }.bind(this)
      )
      return starts.concat(contains)
    },
    onObjectChildPageChange(item, page) {
      this.pendingValuePosition = false
      var key = this.objectChildPageKey(item)
      if (key) this.objectChildPages[key] = page
    },
    /** 行点击：字段分组展开嵌套，其他直接选中 */
    onItemClick(item) {
      if (this.pendingVersions[this.pendingVersionKey(item)] || this.pendingVersions['group:' + this.versionGroupKey(item)]) return
      if (item.disabled) return
      const tags = fieldPickerTags(item)
      if (!matchesFieldTags(tags, this.visibleTags) || !matchesFieldTags(tags, this.selectableTags)) return
      if (!this.isFieldGroup(item)) rememberField(item)
      if (item._operation) {
        if (!this.allowExpression) this.closePopover()
        this.$emit('expression', createOperationOperand([{ operand: null }, { operator: item._operation, operand: null }]))
        return
      }
      this.pendingValuePosition = false
      if (this.isFieldGroup(item)) {
        var groupKey = this.fieldGroupKey(item) || item.varCode
        if (this.expandedObject === groupKey) {
          this.expandedObject = null
        } else {
          this.expandedObject = groupKey
          if (!this.objectChildPages[groupKey])
            this.objectChildPages[groupKey] = 1
        }
        return
      }
      if (this.operandMode) {
        var operand = item._function
          ? createFunctionOperand(item.function)
          : createReferenceOperand(item)
        $emit(this, 'update:value', operand)
        $emit(this, 'select', operand)
        this.closePopover()
        return
      }
      var val = this.valueKey === 'reference'
        ? this.referenceValue(item)
        : this.valueKey === 'id'
          ? item.id
          : item.varCode
      $emit(this, 'update:value', val)
      $emit(this, 'select', item)
      this.closePopover()
    },
    closePopover({ restoreFocus = true } = {}) {
      this.suppressFocusOpen = true
      if (restoreFocus) this.moveFocusBeforeClose()
      else if (this.getPopoverElement()?.contains(document.activeElement)) document.activeElement.blur()
      this.popoverVisible = false
      var popover = this.$refs.popover
      if (popover && typeof popover.hide === 'function') popover.hide()
      else if (popover && typeof popover.doClose === 'function')
        popover.doClose()
      this.setPickerInert(true)
      if (this.focusOpenTimer) clearTimeout(this.focusOpenTimer)
      this.focusOpenTimer = setTimeout(
        function () {
          this.suppressFocusOpen = false
          this.focusOpenTimer = null
        }.bind(this),
        0
      )
    },
    getPopoverElement() {
      if (typeof document === 'undefined') return null
      var popover = this.$refs.popover
      var popper = popover && popover.popperRef
      if (popper && popper.contentRef) return popper.contentRef
      if (popover && popover.popperElm) return popover.popperElm

      var reference = this.getReferenceElement()
      var trigger =
        reference && reference.getAttribute('aria-describedby')
          ? reference
          : reference && reference.querySelector
            ? reference.querySelector('[aria-describedby]')
            : null
      var describedBy = trigger && trigger.getAttribute('aria-describedby')
      if (!describedBy) return null
      var ids = describedBy.split(/\s+/)
      for (var i = 0; i < ids.length; i += 1) {
        var element = document.getElementById(ids[i])
        if (element) return element
      }
      return null
    },
    getReferenceElement() {
      if (this.virtualRef) return this.virtualRef
      var reference = this.$refs.reference
      return reference && reference.$el ? reference.$el : reference || null
    },
    moveFocusBeforeClose() {
      if (typeof document === 'undefined') return
      var popper = this.getPopoverElement()
      var active = document.activeElement
      var reference = this.$refs.reference
      var referenceElement = this.getReferenceElement()
      var input = referenceElement && referenceElement.matches('input')
        ? referenceElement
        : referenceElement && referenceElement.querySelector
          ? referenceElement.querySelector('input')
          : null
      if (reference && typeof reference.focus === 'function') return reference.focus()
      if (input && typeof input.focus === 'function') return input.focus()
      if (popper && active && popper.contains(active) && typeof active.blur === 'function') active.blur()
    },
    setPickerInert(inert) {
      var popper = this.getPopoverElement()
      if (!popper || !popper.setAttribute) return
      if (inert) popper.setAttribute('inert', '')
      else popper.removeAttribute('inert')
    },
    /** 获取选项的实际值（varCode 或 id） */
    getOptionValue(v) {
      if (this.valueKey === 'reference') return this.referenceValue(v)
      return this.valueKey === 'id' ? v.id : v.varCode || v.varLabel
    },
    /** 统一变量展示文本 */
    getVarLabel(v) {
      var ref = v._ref || {}
      var objLabel = ref.category === 'object' ? ref.objectLabel || '' : ''
      return formatVarDisplay({
        varLabel: v.varLabel,
        varCode: v.varCode,
        varType: v.varType,
        sourceType:
          ref.category === 'object'
            ? 'dataObject'
            : ref.category === 'constant'
            ? 'constant'
            : 'variable',
        objectLabel: objLabel,
      })
    },
    /** 输入框获得焦点时自动弹出选择器面板 */
    onReferenceMouseDown() {
      this.pointerFocusPending = true
      this.$nextTick(function () {
        this.pointerFocusPending = false
      })
    },
    onInputFocus() {
      if (this.pointerFocusPending) return
      if (this.suppressFocusOpen) return
      this.openPopover()
    },
    onReferenceInput(value) {
      if (this.operandMode) return
      if (this.editOnInput) $emit(this, 'update:value', value)
      this.referenceKeyword = value || ''
      this.searchText = value || ''
      this.openPopover()
      this.$nextTick(function () {
        this.switchToSearchMatchCategory()
        this.$nextTick(this.expandSingleSearchGroup)
      })
    },
    /** 点击输入框时弹出选择器面板 */
    onInputClick(options) {
      this.pointerFocusPending = false
      this.openPopover(options)
    },
    onReferenceKeydown(event) {
      if (!event) return
      var shouldOpen =
        event.key === 'Enter' ||
        event.key === 'ArrowDown' ||
        (this.operandMode && event.key === ' ')
      if (!shouldOpen) return
      event.preventDefault()
      event.stopPropagation()
      this.openPopover()
    },
    openPopover(options = {}) {
      if (this.disabled) return
      if (this.groupedByCategory && (this.hasVarOptions || this.operandMode || !this.allowCustom)) {
        var wasVisible = this.popoverVisible
        if (!wasVisible) {
          // 当前引用框位于另一个选择器面板内时，它是该面板的子选择器。
          // 切换单元格则关闭旧分支，草稿仍由各 OperandPicker 自己保留。
          for (const picker of [...openPickers]) {
            if (picker !== this && !picker.getPopoverElement()?.contains(this.$el)) picker.closePopover({ restoreFocus: false })
          }
        }
        this.setPickerInert(false)
        this.popoverVisible = true
        if (!this.editorMode && !wasVisible && !this.referenceKeyword && this.value != null && this.value !== '') {
          this.pendingValuePosition = !this.focusCurrentValueInPicker(options)
        }
      }
    },
    restorePendingPosition() {
      if (this.popoverVisible && this.pendingValuePosition) {
        this.pendingValuePosition = !this.focusCurrentValueInPicker()
      }
    },
    focusCurrentValueInPicker(options = {}) {
      var option
      if (this.operandMode && this.value && typeof this.value === 'object') {
        if (this.value.kind === 'ARRAY' || arrayLiteralDraft(this.value)) {
          if (options.restoreManual !== false) {
            if (arrayLiteralDraft(this.value) && this.allowsOperandKind('LITERAL')) this.requestManualEdit('LITERAL')
            else this.$emit('expression', this.value)
          }
          return true
        }
        if (this.value.kind === 'LITERAL' || (this.value.kind === 'PATH' && !this.value.protocolPath)) {
          this.positionPickerCategory('manual')
          if (options.restoreManual !== false && this.restoreManualOnOpen && this.allowsOperandKind(this.value.kind)) this.requestManualEdit(this.value.kind)
          return true
        }
        if (this.value.kind === 'FUNCTION') {
          if (this.positionPickerCategory('function') !== 'function') return false
          option = this.rightItems.find(this.isCurrentOption)
          if (!option) return false
          this.focusFlatOption(option)
          this.scrollCurrentValueIntoView()
          return true
        }
        option = this.findOptionByIdentity(this.value.refId, this.value.refType)
      } else {
        option =
          this.valueKey === 'reference'
            ? this.findOptionByReferenceValue(this.value)
            : this.valueKey === 'id'
            ? this.vars.find(
                function (v) {
                  return String(v.id) === String(this.value)
                }.bind(this)
              )
            : this.vars.find(v => v.varCode === this.value)
      }
      if (!option) {
        this.positionPickerCategory(this.activeCategory)
        return false
      }

      var category = this.optionCategory(option)
      if (this.positionPickerCategory(category) !== category) return false

      if (this.isGroupedFieldCategory(category)) {
        this.focusGroupedOption(option)
      } else {
        this.focusFlatOption(option)
      }
      this.scrollCurrentValueIntoView()
      return true
    },
    positionPickerCategory(category) {
      var categories = this.categoryList || []
      var requestedAvailable = categories.some(function (item) {
        return item.key === category
      })
      var currentAvailable = categories.some(
        function (item) {
          return item.key === this.activeCategory
        }.bind(this)
      )
      var target = requestedAvailable
        ? category
        : currentAvailable
          ? this.activeCategory
          : categories.length
            ? categories[0].key
            : ''
      if (target) this.activeCategory = target
      this.rightPage = 1
      this.expandedObject = null
      return target
    },
    focusFlatOption(option) {
      var list = this.filteredRightItems
      var index = list.findIndex(
        function (item) {
          return (
            this.optionIdentityKey(item) === this.optionIdentityKey(option)
          )
        }.bind(this)
      )
      if (index >= 0) {
        this.rightPage = Math.floor(index / this.fieldPageSize) + 1
      }
    },
    focusObjectOption(option) {
      this.focusGroupedOption(option)
    },
    focusGroupedOption(option) {
      var category = this.optionCategory(option)
      var groupCode = this.fieldGroupCodeByCategory(option, category)
      var groupKey = this.fieldGroupKeyByCategory(groupCode, category)
      var groups = this.filteredRightItems
      var groupIndex = groups.findIndex(
        function (item) {
          return (
            this.fieldGroupKey(item) === groupKey ||
            this.fieldGroupCode(item) === groupCode
          )
        }.bind(this)
      )
      if (groupIndex >= 0) {
        this.rightPage = Math.floor(groupIndex / this.fieldPageSize) + 1
      }
      this.expandedObject = groupKey
      var group = groups[groupIndex]
      if (group && group.children && group.children.length) {
        var childIndex = group.children.findIndex(
          function (child) {
            return (
              this.optionIdentityKey(child) ===
                this.optionIdentityKey(option)
            )
          }.bind(this)
        )
        if (childIndex >= 0) {
          this.objectChildPages[groupKey] =
            Math.floor(childIndex / this.fieldPageSize) + 1
        }
      }
    },
    scrollCurrentValueIntoView() {
      this.$nextTick(function () {
        if (!this.popoverVisible) return
        var popper = this.getPopoverElement()
        if (!popper) return
        var target = popper.querySelector(
          '.vp-row--selected, .vp-child-item--selected'
        )
        if (target && target.scrollIntoView) {
          target.scrollIntoView({ block: 'nearest' })
          if (target.focus) target.focus({ preventScroll: true })
        }
      })
    },
    switchToSearchMatchCategory() {
      if (!this.searchText || this.filteredRightItems.length) return
      var category = this.categoryList.find(
        function (item) {
          return (
            item.key !== 'manual' &&
            this.filterItemsByKeyword(this.categoryItems(item.key)).length > 0
          )
        }.bind(this)
      )
      if (category) {
        this.activeCategory = category.key
        this.rightPage = 1
        this.expandedObject = null
      }
    },
    expandSingleSearchGroup() {
      if (
        this.isGroupedFieldCategory(this.activeCategory) &&
        this.searchText &&
        this.filteredRightItems.length === 1
      ) {
        this.expandedObject =
          this.fieldGroupKey(this.filteredRightItems[0]) ||
          this.filteredRightItems[0].varCode
      }
    },
    updateDocumentListener(visible) {
      if (typeof document === 'undefined') return
      const index = openPickers.indexOf(this)
      if (index >= 0) openPickers.splice(index, 1)
      if (visible) openPickers.push(this)
      var method = visible ? 'addEventListener' : 'removeEventListener'
      document[method]('pointerdown', this.onDocumentPointerDown, true)
      document[method]('keydown', this.onDocumentKeyDown, true)
    },
    onDocumentKeyDown(event) {
      if (!this.popoverVisible || !event || event.key !== 'Escape') return
      if (openPickers.at(-1) !== this) return
      const active = event.target
      if (this.editorMode && this.editorKind === 'EXPRESSION' && active?.closest?.('input, textarea, [contenteditable="true"], [role="combobox"]')) return
      if (active?.closest?.('.monaco-editor, [role="combobox"][aria-expanded="true"]')) return
      event.preventDefault()
      event.stopPropagation()
      event.stopImmediatePropagation?.()
      this.closePopover()
    },
    onDocumentPointerDown(event) {
      if (!this.popoverVisible || !this.dismissOnOutside) return
      var target = event.target
      var root = this.$el
      var popper = this.getPopoverElement()
      if (
        (root && root.contains(target)) ||
        (popper && popper.contains(target)) ||
        (!popper &&
          target &&
          target.closest &&
          target.closest('.var-picker-popover')) ||
        (target && target.closest && target.closest('.fp-row'))
      ) {
        return
      }
      // 子选择器和下拉菜单会传送到 body，沿 ARIA 关联识别整条浮层分支。
      const popups = new Set(popper ? [popper] : [])
      for (const popup of popups) {
        for (const control of popup.querySelectorAll('[aria-controls], [aria-owns], [aria-describedby]')) {
          const ids = ['aria-controls', 'aria-owns', 'aria-describedby'].map(name => control.getAttribute(name) || '').join(' ').trim().split(/\s+/)
          for (const id of ids) {
            const owned = id && document.getElementById(id)
            if (owned?.contains(target)) return
            if (owned) popups.add(owned)
          }
        }
      }
      this.closePopover({ restoreFocus: false })
    },
    startPanelResize(event) {
      this.beginPanelResize(
        event && event.clientX,
        event && event.clientY,
        event
      )
    },
    startPanelTouchResize(event) {
      var touch = event && event.touches && event.touches[0]
      if (!touch) return
      this.beginPanelResize(touch.clientX, touch.clientY, event)
    },
    beginPanelResize(clientX, clientY, event) {
      if (clientX == null || clientY == null) return
      if (event && event.preventDefault) event.preventDefault()
      this.resizingPanel = true
      this.panelBodyCursor = document.body.style.cursor
      this.panelBodyUserSelect = document.body.style.userSelect
      document.body.style.cursor = 'nwse-resize'
      document.body.style.userSelect = 'none'
      this.updatePanelSize(clientX, clientY)
      window.addEventListener('mousemove', this.onPanelResize)
      window.addEventListener('mouseup', this.stopPanelResize)
      window.addEventListener('touchmove', this.onPanelTouchResize, {
        passive: false,
      })
      window.addEventListener('touchend', this.stopPanelResize)
      window.addEventListener('touchcancel', this.stopPanelResize)
    },
    onPanelResize(event) {
      this.updatePanelSize(event.clientX, event.clientY)
    },
    onPanelTouchResize(event) {
      var touch = event && event.touches && event.touches[0]
      if (!touch) return
      if (event && event.preventDefault) event.preventDefault()
      this.updatePanelSize(touch.clientX, touch.clientY)
    },
    updatePanelSize(clientX, clientY) {
      var popper = this.getPopoverElement()
      if (!popper) return
      var rect = popper.getBoundingClientRect()
      var width = Math.round(clientX - rect.left)
      var height = Math.round(clientY - rect.top)
      this.panelWidth = Math.min(Math.max(width, 520), this.maxPanelWidth)
      this.panelHeight = Math.min(Math.max(height, 300), this.maxPanelHeight)
    },
    stopPanelResize() {
      window.removeEventListener('mousemove', this.onPanelResize)
      window.removeEventListener('mouseup', this.stopPanelResize)
      window.removeEventListener('touchmove', this.onPanelTouchResize)
      window.removeEventListener('touchend', this.stopPanelResize)
      window.removeEventListener('touchcancel', this.stopPanelResize)
      if (!this.resizingPanel) return
      this.resizingPanel = false
      document.body.style.cursor = this.panelBodyCursor || ''
      document.body.style.userSelect = this.panelBodyUserSelect || ''
    },
    /** 清除选中的值 */
    onClearValue() {
      $emit(this, 'update:value', this.operandMode ? null : '')
      $emit(this, 'select', null)
      $emit(this, 'clear')
      this.closePopover()
    },
    onChange(val) {
      $emit(this, 'update:value', val)
      if (!val) {
        $emit(this, 'select', null)
        return
      }
      var varObj =
        this.valueKey === 'reference'
          ? this.findOptionByReferenceValue(val)
          : this.valueKey === 'id'
          ? this.vars.find(function (v) {
              return String(v.id) === String(val)
            }) || null
          : null
      $emit(this, 'select', varObj)
    },
    /** 手动输入模式下的输入事件 */
    onCustomInput(val) {
      $emit(this, 'update:value', val)
      $emit(this, 'select', { varCode: val, varLabel: val, _custom: true })
    },
    /** 手动输入模式下的清空事件 */
    onCustomClear() {
      this.localCustomValue = ''
      $emit(this, 'update:value', '')
      $emit(this, 'select', null)
    },
    /** 回显时自动识别：当前值非空但在变量列表中找不到时，自动切换到手动输入模式 */
    _autoSwitchIfUnmatched() {
      if (this.operandMode) return
      if (
        !this.autoSwitchCustom ||
        !this.allowCustom ||
        !this.value ||
        this.customMode
      )
        return
      if (!this.hasVarOptions) return
      var found
      if (this.valueKey === 'reference') {
        found = !!this.findOptionByReferenceValue(this.value)
      } else if (this.valueKey === 'id') {
        found = this.vars.some(
          function (v) {
            return String(v.id) === String(this.value)
          }.bind(this)
        )
      } else {
        found = !!this.findOptionByCode(this.value)
      }
      if (!found) {
        this.customMode = true
        this.localCustomValue = this.value
      }
    },
    allowsOperandKind(kind) {
      return (this.allowedKinds || []).includes(kind)
    },
    requestManualEdit(kind) {
      if (!this.allowsOperandKind(kind) || !this.canUseAction(kind)) return
      $emit(this, 'manual-edit', kind)
      if (!this.manualInPanel) this.closePopover()
    },
    emitOperand(operand) {
      $emit(this, 'update:value', operand)
      $emit(this, 'select', operand)
      this.closePopover()
    },
    typeLabel(t) {
      return varTypeLabel(t)
    },
    /** 根据配置获取编码列标签 */
    codeColumnLabel() {
      if (typeof this.columnLabels === 'object')
        return this.columnLabels.codeLabel || '字段编码'
      if (
        this.activeCategory === 'object' ||
        this.activeCategory === 'selected'
      )
        return '字段编码'
      if (this.activeCategory === 'model') return '模型输出字段'
      const labels = {
        variable: '变量编码',
        dataObject: '属性字段路径',
        model: '模型输出字段',
      }
      return labels[this.columnLabels] || '字段编码'
    },
    /** 根据配置获取名称列标签 */
    nameColumnLabel() {
      if (typeof this.columnLabels === 'object')
        return this.columnLabels.nameLabel || '字段名称'
      if (
        this.activeCategory === 'object' ||
        this.activeCategory === 'selected'
      )
        return '字段名称'
      if (this.activeCategory === 'model') return '输出字段名称'
      const labels = {
        variable: '变量名称',
        dataObject: '属性名称',
        model: '输出字段名称',
      }
      return labels[this.columnLabels] || '字段名称'
    },
    /** 获取类型单字符标识（带颜色） */
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
  },
  emits: ['input', 'update:value', 'select', 'manual-edit', 'expression', 'editor-back', 'page-size', 'update:pageSize', 'clear', 'visible-change'],
}
</script>

<style>
.var-picker-popover--closed { display: none !important; }
.var-picker-popover {
  box-sizing: content-box !important;
  padding: 0 !important;
  border-radius: 0 !important;
  max-width: calc(100vw - 8px);
}
</style>

<style scoped>
.vp-input-version { color:var(--el-color-primary); background:var(--tianshu-info-bg); padding:1px 4px; margin-right:3px; font-size:11px; white-space:nowrap; }
.var-picker-wrap {
  display: flex;
  flex: 1 1 auto;
  min-width: 0;
  width: 100%;
  vertical-align: middle;
  max-width: 100%;
}
.var-picker-wrap :deep(.el-popover__reference-wrapper) {
  display: block;
  flex: 1;
  min-width: 0;
  width: 100%;
}
.custom-input-row,
.select-input-row {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
}
.select-input-row > span:not(.mode-switch) {
  display: block;
  flex: 1;
  min-width: 0;
  width: 100%;
}
.custom-input-row .el-input,
.select-input-row .el-popover,
.select-input-row :deep(.el-popover__reference-wrapper),
.select-input-row .el-select,
.select-input-row .el-input {
  flex: 1;
  min-width: 0;
  width: 100%;
}
.vp-reference {
  flex: 1 1 auto;
  min-width: 0;
  width: 100%;
}
.vp-reference :deep(.el-input__wrapper) {
  min-width: 0;
  padding-left: 4px;
  padding-right: 4px;
}
.vp-reference :deep(.el-input__inner) {
  min-width: 0;
  padding-right: 0;
  padding-left: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}
.vp-reference :deep(.el-input__prefix-inner > :last-child) {
  margin-right: 4px;
}
.vp-reference :deep(.el-input__suffix-inner) {
  gap: 4px;
}
.vp-reference :deep(.el-input__suffix-inner > :first-child) {
  margin-left: 4px;
}
.vp-reference :deep(.el-input__icon) {
  margin-left: 0;
}
.vp-reference--readonly :deep(.el-input__inner),
.vp-reference--readonly :deep(.el-input__wrapper) {
  cursor: pointer;
}
.vp-reference--content :deep(.el-input__wrapper) { position: relative; }
.vp-reference--content :deep(.el-input__prefix) { position: absolute; left: 4px; right: 24px; min-width: 0; max-width: calc(100% - 28px); margin: 0; pointer-events: none; }
.vp-reference--content :deep(.el-input__prefix-inner) { min-width: 0; max-width: 100%; }
.vp-reference--content :deep(.el-input__prefix *) { pointer-events: none !important; }
.vp-reference--content :deep(.el-input__inner) { color: transparent; caret-color: transparent; }
.mode-switch {
  flex-shrink: 0;
  cursor: pointer;
  color: var(--tianshu-info-text);
  font-size: 14px;
  padding: 2px;
  border-radius: 3px;
  transition: all 0.2s;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.mode-switch:hover {
  background: var(--tianshu-info-bg);
  color: var(--el-color-primary-dark-2);
}
.vp-arrow-btn,
.vp-clear-btn {
  cursor: pointer;
  color: var(--tianshu-text-tertiary);
}
.vp-arrow-btn:hover,
.vp-clear-btn:hover {
  color: var(--tianshu-info-text);
}
.vp-arrow-btn {
  flex-shrink: 0;
}
.var-empty {
  padding: 8px 12px;
  font-size: 12px;
  color: var(--tianshu-text-disabled);
  text-align: center;
}

</style>
