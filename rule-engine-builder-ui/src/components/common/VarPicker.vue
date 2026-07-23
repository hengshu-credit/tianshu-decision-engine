<template>
  <div class="var-picker-wrap" :style="{ width: width || '100%' }">
    <!-- 手动输入模式 -->
    <template v-if="!operandMode && customMode">
      <div class="custom-input-row">
        <el-input
          v-model="localCustomValue"
          :size="size"
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
      <div class="select-input-row">
        <el-popover
          v-if="groupedByCategory && (hasVarOptions || operandMode)"
          ref="popover"
          v-model:visible="popoverVisible"
          placement="bottom-start"
          :width="popoverWidth"
          trigger="manual"
          popper-class="var-picker-popover"
        >
          <div class="vp-panel" :style="panelStyle" @mousedown.stop @click.stop>
            <!-- 左侧：变量类型分类列表 -->
            <div class="vp-left">
              <div
                v-for="cat in categoryList"
                :key="cat.key"
                class="vp-cat-item"
                :class="{ 'vp-cat-item--active': activeCategory === cat.key }"
                @click="onCategoryClick(cat.key)"
              >
                <span class="vp-cat-label">{{ cat.label }}</span>
                <span class="vp-cat-count">{{ cat.count }}</span>
              </div>
            </div>
            <div v-if="activeCategory === 'manual'" class="vp-right vp-manual">
              <div class="vp-manual-title">请选择手动输入类型</div>
              <div class="vp-manual-types">
                <button
                  v-if="allowsOperandKind('LITERAL')"
                  type="button"
                  class="vp-manual-type"
                  @click="requestManualEdit('LITERAL')"
                >
                  <el-icon><el-icon-edit-outline /></el-icon>
                  <span>手输阈值</span>
                  <small>直接输入数值、文本、布尔值或日期</small>
                </button>
                <button
                  v-if="allowsOperandKind('PATH')"
                  type="button"
                  class="vp-manual-type"
                  @click="requestManualEdit('PATH')"
                >
                  <el-icon><el-icon-link /></el-icon>
                  <span>手输路径</span>
                  <small>输入运行时字段路径并自动识别引用</small>
                </button>
              </div>
            </div>
            <!-- 右侧：字段表格 + 搜索 -->
            <div v-else class="vp-right">
              <div class="vp-search" v-if="showSearch">
                <el-input
                  v-model="searchText"
                  size="small"
                  placeholder="搜索变量编码或名称..."
                  clearable
                  :prefix-icon="ElIconSearch"
                />
              </div>
              <div class="vp-table-wrap">
                <table class="vp-table">
                  <thead>
                    <tr>
                      <th class="vp-th vp-th--type">类型</th>
                      <th class="vp-th vp-th--code">{{ codeColumnLabel() }}</th>
                      <th class="vp-th vp-th--name">{{ nameColumnLabel() }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    <template
                      v-for="v in pagedRightItems"
                      :key="fieldGroupKey(v) || optionIdentityKey(v)"
                    >
                      <tr
                        class="vp-row"
                        :class="{
                          'vp-row--selected':
                            !isFieldGroup(v) && v.varCode === currentValue,
                        }"
                        @click="onItemClick(v)"
                      >
                        <td class="vp-td vp-td--type">
                          <span
                            class="vp-type-badge"
                            :class="'vp-type-badge--' + typeChar(v.varType)"
                            :title="typeLabel(v.varType)"
                            >{{ typeChar(v.varType) }}</span
                          >
                        </td>
                        <td class="vp-td vp-td--code">
                          {{ isFieldGroup(v) ? fieldGroupCode(v) : v.varCode }}
                        </td>
                        <td class="vp-td vp-td--name">
                          {{
                            isFieldGroup(v)
                              ? fieldGroupLabel(v)
                              : v.varLabel || v.varCode
                          }}
                        </td>
                      </tr>
                      <!-- 数据对象/模型嵌套行：点击分组展开，点击子字段才选择 -->
                      <tr
                        v-if="v.children && expandedObject === fieldGroupKey(v)"
                        class="vp-children-row"
                      >
                        <td colspan="3" class="vp-children-td">
                          <div class="vp-children-wrap">
                            <div class="vp-children-title">
                              <el-icon><el-icon-document /></el-icon>
                              {{ fieldGroupLabel(v) }} 字段列表
                            </div>
                            <div class="vp-children-list">
                              <div
                                v-for="child in pagedObjectChildren(v)"
                                :key="
                                  (fieldGroupKey(v) || optionIdentityKey(v)) +
                                  '.' +
                                  optionIdentityKey(child)
                                "
                                class="vp-child-item"
                                :class="{
                                  'vp-child-item--selected':
                                    child.varCode === currentValue,
                                }"
                                @click.stop="onItemClick(child)"
                              >
                                <span class="vp-child-path">{{
                                  fieldChildRelativePath(child)
                                }}</span>
                                <span
                                  class="vp-type-badge vp-type-badge--sm"
                                  :class="
                                    'vp-type-badge--' + typeChar(child.varType)
                                  "
                                  :title="typeLabel(child.varType)"
                                  >{{ typeChar(child.varType) }}</span
                                >
                                <span class="vp-child-name">{{
                                  fieldChildDisplayName(child)
                                }}</span>
                              </div>
                            </div>
                            <el-pagination
                              v-if="objectChildNeedsPaging(v)"
                              class="vp-mini-pager"
                              size="small"
                              layout="prev,pager,next"
                              :current-page="objectChildPage(v)"
                              :page-size="fieldPageSize"
                              :total="v.children.length"
                              @current-change="
                                (p) => onObjectChildPageChange(v, p)
                              "
                            />
                          </div>
                        </td>
                      </tr>
                    </template>
                    <tr v-if="filteredRightItems.length === 0">
                      <td colspan="3" class="vp-empty">
                        <el-icon><el-icon-warning-outline /></el-icon>
                        {{ loading ? '加载中...' : '暂无数据' }}
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <el-pagination
                v-if="rightNeedsPaging"
                class="vp-pager"
                size="small"
                layout="total,prev,pager,next"
                :current-page="rightPage"
                :page-size="fieldPageSize"
                :total="filteredRightItems.length"
                @current-change="onRightPageChange"
              />
            </div>
            <div
              class="vp-resize-handle"
              title="拖拽调整面板大小"
              @mousedown.prevent.stop="startPanelResize"
              @touchstart.prevent.stop="startPanelTouchResize"
            />
          </div>
          <template v-slot:reference>
            <div
              ref="reference"
              class="vp-reference"
              :style="referenceStyle"
              tabindex="0"
              role="button"
              aria-label="打开字段与表达式选择器"
              @click.stop="openPopover"
              @keydown.enter.prevent.stop="openPopover"
              @keydown.space.prevent.stop="openPopover"
            >
              <el-input
                :size="size"
                :placeholder="placeholder || '选择变量/常量/对象字段'"
                :model-value="referenceInputValue"
                :readonly="operandMode"
                style="width: 100%"
                @focus="onInputFocus"
                @update:model-value="onReferenceInput"
                @click="onInputClick"
              >
                <template v-slot:prefix>
                  <span
                    v-if="operandMode && operandKindMetaValue.label"
                    class="vp-operand-kind"
                    :class="'vp-operand-kind--' + operandKindMetaValue.tone"
                    :title="operandKindMetaValue.label"
                    >{{ operandKindMetaValue.label }}</span
                  >
                </template>
                <template v-slot:suffix>
                  <el-icon class="el-input__icon">
                    <el-icon-arrow-down />
                  </el-icon>
                  <el-icon
                    v-if="value && allowCustom"
                    class="el-input__icon vp-clear-btn"
                    @mousedown.stop
                    @click.stop="onClearValue"
                  >
                    <el-icon-close />
                  </el-icon>
                </template>
              </el-input>
            </div>
          </template>
        </el-popover>

        <!-- 无变量时回退到输入框 -->
        <el-input
          v-else
          v-model="localCustomValue"
          :size="size"
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
import {
  Collection as ElIconCollection,
  EditPen as ElIconEditOutline,
  Link as ElIconLink,
  Document as ElIconDocument,
  Warning as ElIconWarningOutline,
  ArrowDown as ElIconArrowDown,
  Close as ElIconClose,
  Edit as ElIconEdit,
  Search as ElIconSearch,
} from '@element-plus/icons-vue'
import { $emit } from '../../utils/gogocodeTransfer'
import { varTypeLabel, varTypeTagColor } from '@/constants/varTypes'
import { formatVarDisplay } from '@/utils/varDisplay'
import { REFERENCE_PICKER_CATEGORIES } from '@/utils/pickerCategories'
import {
  groupReferenceOptions,
  referenceChildRelativePath,
} from '@/utils/referenceGroups'
import {
  createFunctionOperand,
  createReferenceOperand,
  operandDisplay,
  operandKindMeta,
} from '@/utils/operand'

export default {
  data() {
    return {
      /** 当前是否处于手动输入模式 */
      customMode: false,
      /** 手动输入模式下的本地值，避免依赖 prop 异步更新导致失焦清空 */
      localCustomValue: this.value || '',
      /** popover 显示状态 */
      popoverVisible: false,
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
      panelWidth: 560,
      panelHeight: 360,
      maxPanelWidth: 1440,
      maxPanelHeight: 960,
      resizingPanel: false,
      panelBodyCursor: '',
      panelBodyUserSelect: '',
      suppressFocusOpen: false,
      focusOpenTimer: null,
      ElIconSearch: markRaw(ElIconSearch),
    }
  },
  components: {
    ElIconCollection,
    ElIconEditOutline,
    ElIconLink,
    ElIconDocument,
    ElIconWarningOutline,
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
    /** 是否允许手动输入自定义变量（不在变量管理中的） */
    allowCustom: { type: Boolean, default: true },
    /**
     * 指定 value 字段类型：
     * - 'code'（默认）：使用 varCode 作为 option value
     * - 'id'：使用 var.id 作为 option value（用于模型出入参关联变量）
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
    /** 统一操作数模式；迁移期间字符串模式仍服务尚未接入的旧页面 */
    operandMode: { type: Boolean, default: false },
    allowedKinds: {
      type: Array,
      default: () => ['LITERAL', 'PATH', 'REFERENCE'],
    },
    expectedType: { type: String, default: 'STRING' },
    writableOnly: { type: Boolean, default: false },
    functions: { type: Array, default: () => [] },
  },
  watch: {
    vars() {
      if (!this.hasVarOptions) {
        this.customMode = false
      } else {
        this._autoSwitchIfUnmatched()
      }
    },
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
    activeCategory() {
      this.rightPage = 1
      this.expandedObject = null
    },
    searchText() {
      this.rightPage = 1
      this.objectChildPages = {}
    },
    popoverVisible(val) {
      this.updateDocumentListener(val)
      this.setPickerInert(!val)
      if (!val) {
        this.searchText = ''
        this.referenceKeyword = ''
        this.expandedObject = null
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
      if (this.operandMode) return operandDisplay(this.value)
      return this.referenceKeyword || this.displayValue
    },
    operandKindMetaValue() {
      return operandKindMeta(this.value)
    },
    referenceStyle() {
      const label = this.operandMode ? this.operandKindMetaValue.label : ''
      return {
        '--vp-prefix-offset': label
          ? Math.min(84, label.length * 12 + 24) + 'px'
          : '12px',
      }
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
        this.valueKey === 'id'
          ? this.vars.find(
              function (item) {
                return String(item.id) === String(this.value)
              }.bind(this)
            )
          : null
      if (!v) return this.value
      var ref = v._ref || {}
      var label = this.optionLabel(v)
      var code = v.varCode || ''
      if (ref.category === 'object') {
        return code ? label + ' ' + this.objectFieldPath(v) : label
      }
      return code ? label + ' ' + code : label
    },
    /** 分类列表（仅显示有数据的分类） */
    categoryList() {
      var list = [
        { key: 'manual', label: '手动输入', count: 0 },
        { key: 'selected', label: '已选字段', count: 0 },
        ...REFERENCE_PICKER_CATEGORIES.map(function (item) {
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
      var self = this
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
      var list = this.vars.filter(function (v) {
        var cat = (v._ref && v._ref.category) || 'standalone'
        if (cat !== self.activeCategory) return false
        if (self.typeFilter && v.varType !== self.typeFilter) return false
        if (!self.isWritableOption(v)) return false
        return true
      })

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
    categoryItems(category) {
      var self = this
      if (category === 'manual') return []
      if (category === 'function') return this.functions
      if (category === 'selected') {
        return this.selectedItems.filter(this.isWritableOption)
      }
      var list = this.vars.filter(function (v) {
        var cat = (v._ref && v._ref.category) || 'standalone'
        if (cat !== category) return false
        if (self.typeFilter && v.varType !== self.typeFilter) return false
        if (!self.isWritableOption(v)) return false
        return true
      })

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
      return category === 'object' || category === 'model'
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
      return this.objectGroupCode(item)
    },
    fieldGroupLabel(item) {
      var category = this.fieldGroupCategory(item)
      if (category === 'model') return this.modelGroupLabel(item)
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
    findOptionByIdentity(id, refType) {
      if (id == null || id === '' || !refType) return null
      return (
        this.vars.find(function (v) {
          var optionId =
            v.id != null
              ? v.id
              : v._varId != null
              ? v._varId
              : v.varObj && v.varObj.id
          var optionType =
            v._refType ||
            v.refType ||
            (v.varObj && v.varObj.refType) ||
            (v._ref && v._ref.refType)
          return String(optionId) === String(id) && optionType === refType
        }) || null
      )
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
      if (id != null && id !== '') return (refType || 'REF') + ':' + id
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
      this.activeCategory = cat
      this.expandedObject = null
      this.rightPage = 1
    },
    onRightPageChange(page) {
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
      var key = this.objectChildPageKey(item)
      if (key) this.objectChildPages[key] = page
    },
    /** 行点击：字段分组展开嵌套，其他直接选中 */
    onItemClick(item) {
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
      var val = this.valueKey === 'id' ? item.id : item.varCode
      $emit(this, 'update:value', val)
      $emit(this, 'select', item)
      this.closePopover()
    },
    closePopover() {
      this.suppressFocusOpen = true
      this.moveFocusBeforeClose()
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

      var reference = this.$refs.reference
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
    moveFocusBeforeClose() {
      if (typeof document === 'undefined') return
      var popper = this.getPopoverElement()
      var active = document.activeElement
      if (!popper || !active || !popper.contains(active)) return
      var reference = this.$refs.reference
      if (reference && typeof reference.focus === 'function') reference.focus()
      else if (typeof active.blur === 'function') active.blur()
    },
    setPickerInert(inert) {
      var popper = this.getPopoverElement()
      if (!popper || !popper.setAttribute) return
      if (inert) popper.setAttribute('inert', '')
      else popper.removeAttribute('inert')
    },
    /** 获取选项的实际值（varCode 或 id） */
    getOptionValue(v) {
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
    onInputFocus() {
      if (this.suppressFocusOpen) return
      this.openPopover()
    },
    onReferenceInput(value) {
      if (this.operandMode) return
      this.referenceKeyword = value || ''
      this.searchText = value || ''
      this.openPopover()
      this.$nextTick(function () {
        this.switchToSearchMatchCategory()
        this.$nextTick(this.expandSingleSearchGroup)
      })
    },
    /** 点击输入框时弹出选择器面板 */
    onInputClick() {
      this.openPopover()
    },
    openPopover() {
      if (this.groupedByCategory && (this.hasVarOptions || this.operandMode)) {
        var wasVisible = this.popoverVisible
        this.setPickerInert(false)
        this.popoverVisible = true
        if (!this.referenceKeyword) this.searchText = ''
        if (!wasVisible) {
          this.$nextTick(function () {
            if (
              this.operandMode &&
              !this.value &&
              this.allowsOperandKind('LITERAL')
            ) {
              this.activeCategory = 'manual'
            } else {
              this.focusCurrentValueInPicker()
            }
          })
        }
      }
    },
    focusCurrentValueInPicker() {
      var option
      if (this.operandMode && this.value && typeof this.value === 'object') {
        option = this.findOptionByIdentity(this.value.refId, this.value.refType)
      } else {
        option =
          this.valueKey === 'id'
            ? this.vars.find(
                function (v) {
                  return String(v.id) === String(this.value)
                }.bind(this)
              )
            : null
      }
      if (!option) return

      var category = this.optionCategory(option)
      this.activeCategory = category
      this.expandedObject = null
      this.rightPage = 1

      this.$nextTick(
        function () {
          if (this.isGroupedFieldCategory(category)) {
            this.focusGroupedOption(option)
          } else {
            this.focusFlatOption(option)
          }
          this.scrollCurrentValueIntoView()
        }.bind(this)
      )
    },
    focusFlatOption(option) {
      var list = this.filteredRightItems
      var index = list.findIndex(
        function (item) {
          return (
            this.optionIdentityKey(item) === this.optionIdentityKey(option) ||
            item.varCode === option.varCode
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
                this.optionIdentityKey(option) ||
              child.varCode === option.varCode
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
        var popper = this.getPopoverElement()
        if (!popper) return
        var target = popper.querySelector(
          '.vp-row--selected, .vp-child-item--selected'
        )
        if (target && target.scrollIntoView) {
          target.scrollIntoView({ block: 'nearest' })
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
      if (category) this.activeCategory = category.key
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
      var method = visible ? 'addEventListener' : 'removeEventListener'
      document[method]('mousedown', this.onDocumentMouseDown, true)
      document[method]('keydown', this.onDocumentKeyDown, true)
    },
    onDocumentKeyDown(event) {
      if (!this.popoverVisible || !event || event.key !== 'Escape') return
      event.preventDefault()
      event.stopPropagation()
      this.closePopover()
    },
    onDocumentMouseDown(event) {
      if (!this.popoverVisible) return
      var target = event.target
      var root = this.$el
      var popper = this.getPopoverElement()
      if (
        (root && root.contains(target)) ||
        (popper && popper.contains(target)) ||
        (!popper &&
          target &&
          target.closest &&
          target.closest('.var-picker-popover'))
      ) {
        return
      }
      this.closePopover()
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
        this.valueKey === 'id'
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
      if (this.valueKey === 'id') {
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
      if (!this.allowsOperandKind(kind)) return
      $emit(this, 'manual-edit', kind)
      this.closePopover()
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
  emits: ['input', 'update:value', 'select', 'manual-edit', 'clear'],
}
</script>

<style>
.var-picker-popover {
  box-sizing: content-box !important;
  padding: 0 !important;
}
</style>

<style scoped>
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
.vp-reference :deep(.el-input__prefix) {
  display: flex;
  align-items: center;
  left: 6px;
}
.vp-reference :deep(.el-input--prefix .el-input__inner) {
  padding-left: var(--vp-prefix-offset, 12px);
}
.vp-operand-kind {
  display: inline-flex;
  align-items: center;
  max-width: 72px;
  height: 20px;
  padding: 0 6px;
  border-radius: 3px;
  font-size: 10px;
  line-height: 20px;
  white-space: nowrap;
  color: #fff;
  background: #909399;
}
.vp-operand-kind--literal {
  background: #e6a23c;
}
.vp-operand-kind--path {
  background: #607d8b;
}
.vp-operand-kind--path-resolved {
  background: #546e7a;
}
.vp-operand-kind--variable {
  background: var(--el-color-primary);
}
.vp-operand-kind--constant {
  background: #9c6ade;
}
.vp-operand-kind--object {
  background: #00a870;
}
.vp-operand-kind--model {
  background: #f56c6c;
}
.vp-operand-kind--function {
  background: #13c2c2;
}
.mode-switch {
  flex-shrink: 0;
  cursor: pointer;
  color: var(--el-color-primary);
  font-size: 14px;
  padding: 2px;
  border-radius: 3px;
  transition: all 0.2s;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.mode-switch:hover {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary-dark-2);
}
.vp-clear-btn {
  cursor: pointer;
  color: #909399;
  margin-right: 4px;
}
.vp-clear-btn:hover {
  color: var(--el-color-primary);
}
.var-empty {
  padding: 8px 12px;
  font-size: 12px;
  color: #bbb;
  text-align: center;
}

/* ── 两列弹窗面板 ── */
.vp-panel {
  display: flex;
  user-select: none;
  position: relative;
  box-sizing: border-box;
  overflow: hidden;
  min-width: 520px;
  min-height: 300px;
  max-width: 1440px;
  max-height: 960px;
}
.vp-left {
  width: 132px;
  flex-shrink: 0;
  border-right: 1px solid #ebeef5;
  overflow-y: auto;
}
.vp-cat-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  font-size: 12px;
  color: #606266;
  cursor: pointer;
  transition: background 0.15s;
  border-bottom: 1px solid #f5f5f5;
  white-space: nowrap;
}
.vp-cat-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.vp-cat-item:hover {
  background: #f5f7fa;
}
.vp-cat-item--active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 600;
  border-right: 2px solid var(--el-color-primary);
}
.vp-cat-count {
  flex: none;
  min-width: 24px;
  box-sizing: border-box;
  text-align: center;
  font-size: 10px;
  color: #c0c4cc;
  background: #f0f2f5;
  padding: 1px 5px;
  border-radius: 8px;
}
.vp-cat-item--active .vp-cat-count {
  background: var(--el-color-primary-light-7);
  color: var(--el-color-primary);
}
.vp-manual {
  padding: 20px;
  box-sizing: border-box;
  overflow-y: auto;
}
.vp-manual-title {
  color: #303133;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 12px;
}
.vp-manual-types {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.vp-manual-type {
  display: grid;
  grid-template-columns: 26px 1fr;
  gap: 3px 8px;
  padding: 14px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: #fff;
  color: #303133;
  cursor: pointer;
  text-align: left;
}
.vp-manual-type i {
  grid-row: 1 / 3;
  color: var(--el-color-primary);
  font-size: 20px;
  align-self: center;
}
.vp-manual-type span {
  font-weight: 600;
}
.vp-manual-type small {
  color: #909399;
  line-height: 1.4;
}
.vp-manual-type:hover {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.vp-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.vp-search {
  padding: 8px 10px 6px;
  border-bottom: 1px solid #ebeef5;
  flex-shrink: 0;
}
.vp-search .el-input {
  width: 100%;
}
.vp-table-wrap {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}
.vp-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.vp-th {
  background: #fafafa;
  padding: 8px 10px;
  border-bottom: 1px solid #ebeef5;
  font-weight: 600;
  color: #606266;
  text-align: left;
  white-space: nowrap;
  position: sticky;
  top: 0;
  z-index: 1;
}
.vp-th--type {
  width: 72px;
  text-align: center;
  padding: 8px 4px;
}
.vp-th--code {
  width: 130px;
}
.vp-th--name {
  min-width: 80px;
}
.vp-row {
  cursor: pointer;
  transition: background 0.1s;
}
.vp-row:hover {
  background: #f5f7fa;
}
.vp-row--selected {
  background: var(--el-color-primary-light-9);
}
.vp-row--selected:hover {
  background: var(--el-color-primary-light-8);
}
.vp-td {
  padding: 7px 10px;
  border-bottom: 1px solid #f5f5f5;
  color: #303133;
}
.vp-td--type {
  text-align: center;
  padding: 7px 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}
.vp-td--code {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 11px;
  color: #909399;
}
.vp-td--name {
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.vp-empty {
  text-align: center;
  padding: 30px 0;
  color: #c0c4cc;
}
.vp-children-row {
  background: #f9fafc;
}
.vp-children-td {
  padding: 6px 12px;
}
.vp-children-wrap {
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  overflow: hidden;
}
.vp-children-title {
  background: #f5f7fa;
  padding: 4px 10px;
  font-size: 11px;
  color: #909399;
  border-bottom: 1px solid #ebeef5;
}
.vp-children-list {
  max-height: 160px;
  overflow-y: auto;
}
.vp-child-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  font-size: 12px;
  cursor: pointer;
  transition: background 0.1s;
}
.vp-child-item:hover {
  background: var(--el-color-primary-light-9);
}
.vp-child-item--selected {
  background: var(--el-color-primary-light-8);
}
.vp-child-path {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 11px;
  color: #909399;
  flex-shrink: 0;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.vp-child-name {
  color: #303133;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.vp-pager {
  flex-shrink: 0;
  padding: 6px 8px;
  border-top: 1px solid #ebeef5;
  text-align: right;
  background: #fff;
}
.vp-mini-pager {
  padding: 4px 8px 6px;
  text-align: right;
  border-top: 1px solid #ebeef5;
  background: #fff;
}
.vp-resize-handle {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 18px;
  height: 18px;
  cursor: nwse-resize;
  touch-action: none;
  z-index: 5;
}
.vp-resize-handle::after {
  content: '';
  position: absolute;
  right: 3px;
  bottom: 3px;
  width: 8px;
  height: 8px;
  border-right: 2px solid #c0c4cc;
  border-bottom: 2px solid #c0c4cc;
}
.vp-resize-handle:hover::after {
  border-color: var(--el-color-primary);
}

/* ── 类型单字符标识 ── */
.vp-type-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  font-family: 'Consolas', 'Monaco', monospace;
  color: #fff;
  flex-shrink: 0;
}
.vp-type-badge--sm {
  width: 16px;
  height: 16px;
  font-size: 10px;
  border-radius: 3px;
}
/* 字符串 s - 蓝色 */
.vp-type-badge--s {
  background: var(--el-color-primary);
}
/* 整数 i - 橙色 */
.vp-type-badge--i {
  background: #e6a23c;
}
/* 布尔 b - 绿色 */
.vp-type-badge--b {
  background: #67c23a;
}
/* 日期 d - 紫色 */
.vp-type-badge--d {
  background: #9c27b0;
}
/* 枚举 e - 红色 */
.vp-type-badge--e {
  background: #f56c6c;
}
/* 对象 o - 青色 */
.vp-type-badge--o {
  background: #00bcd4;
}
/* 列表 l - 黄色 */
.vp-type-badge--l {
  background: #ff9800;
}
/* 映射 m - 灰色 */
.vp-type-badge--m {
  background: #909399;
}
/* 模型 M - 绿色 */
.vp-type-badge--M {
  background: #13c2c2;
}
/* 默认 ? - 浅灰 */
.vp-type-badge--\? {
  background: #c0c4cc;
}
</style>
