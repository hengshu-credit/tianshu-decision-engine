<template>
  <div class="var-picker-wrap" :style="{ width: width || '100%' }">
    <!-- 手动输入模式 -->
    <template v-if="customMode">
      <div class="custom-input-row">
        <el-input
          v-model="localCustomValue"
          :size="size"
          :placeholder="placeholder || '输入变量编码'"
          clearable
          @input="onCustomInput"
          @clear="onCustomClear"
        />
        <el-tooltip v-if="allowCustom && hasVarOptions" content="切换为从变量管理选择" placement="top">
          <span class="mode-switch" @click="customMode = false"><i class="el-icon-collection" /></span>
        </el-tooltip>
      </div>
    </template>

    <!-- 选择模式 -->
    <template v-else>
      <div class="select-input-row">
        <el-popover
          v-if="groupedByCategory && hasVarOptions"
          ref="popover"
          v-model="popoverVisible"
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
            <!-- 右侧：字段表格 + 搜索 -->
            <div class="vp-right">
              <div class="vp-search" v-if="showSearch">
                <el-input
                  v-model="searchText"
                  size="mini"
                  placeholder="搜索变量编码或名称..."
                  clearable
                  prefix-icon="el-icon-search"
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
                    <template v-for="v in pagedRightItems">
                      <tr
                        :key="v._objectGroupKey || v.varCode"
                        class="vp-row"
                        :class="{ 'vp-row--selected': v.varCode === currentValue }"
                        @click="onItemClick(v)"
                      >
                        <td class="vp-td vp-td--type">
                          <span class="vp-type-badge" :class="'vp-type-badge--' + typeChar(v.varType)" :title="typeLabel(v.varType)">{{ typeChar(v.varType) }}</span>
                          <span class="vp-type-label">{{ typeLabel(v.varType) }}</span>
                        </td>
                        <td class="vp-td vp-td--code">{{ v._objectGroup ? objectGroupCode(v) : v.varCode }}</td>
                        <td class="vp-td vp-td--name">{{ v._objectGroup ? objectGroupLabel(v) : (v.varLabel || v.varCode) }}</td>
                      </tr>
                      <!-- 数据对象嵌套行：点击展开显示字段路径 -->
                      <tr
                        v-if="v.children && expandedObject === v._objectGroupKey"
                        :key="(v._objectGroupKey || v.varCode) + '-children'"
                        class="vp-children-row"
                      >
                        <td colspan="3" class="vp-children-td">
                          <div class="vp-children-wrap">
                            <div class="vp-children-title">
                              <i class="el-icon-document" /> {{ v._ref && v._ref.objectLabel ? v._ref.objectLabel : v.objectCode }} 字段列表
                            </div>
                            <div class="vp-children-list">
                              <div
                                v-for="child in pagedObjectChildren(v)"
                                :key="(v._objectGroupKey || '') + '.' + child.varCode"
                                class="vp-child-item"
                                :class="{ 'vp-child-item--selected': child.varCode === currentValue }"
                                @click.stop="onItemClick(child)"
                              >
                                <span class="vp-child-path">{{ objectFieldRelativePath(child) }}</span>
                                <span class="vp-type-badge vp-type-badge--sm" :class="'vp-type-badge--' + typeChar(child.varType)" :title="typeLabel(child.varType)">{{ typeChar(child.varType) }}</span>
                                <span class="vp-child-type">{{ typeLabel(child.varType) }}</span>
                                <span class="vp-child-name">{{ objectFieldDisplayName(child) }}</span>
                              </div>
                            </div>
                            <el-pagination
                              v-if="objectChildNeedsPaging(v)"
                              class="vp-mini-pager"
                              small
                              layout="prev,pager,next"
                              :current-page="objectChildPage(v)"
                              :page-size="fieldPageSize"
                              :total="v.children.length"
                              @current-change="p => onObjectChildPageChange(v, p)"
                            />
                          </div>
                        </td>
                      </tr>
                    </template>
                    <tr v-if="filteredRightItems.length === 0">
                      <td colspan="3" class="vp-empty">
                        <i class="el-icon-warning-outline" /> {{ loading ? '加载中...' : '暂无数据' }}
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <el-pagination
                v-if="rightNeedsPaging"
                class="vp-pager"
                small
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
          <div
            slot="reference"
            class="vp-reference"
            @click.stop="openPopover"
          >
            <el-input
              :value="displayValue"
              :size="size"
              :placeholder="placeholder || '选择变量/常量/对象字段'"
              readonly
              style="width:100%"
              @focus="onInputFocus"
              @click.native="onInputClick"
            >
              <i slot="suffix" class="el-input__icon el-icon-arrow-down" />
              <i
                v-if="value && allowCustom"
                slot="suffix"
                class="el-input__icon el-icon-close vp-clear-btn"
                @mousedown.stop
                @click.stop="onClearValue"
              />
            </el-input>
          </div>
        </el-popover>

        <!-- 无变量时回退到输入框 -->
        <el-input
          v-else
          v-model="localCustomValue"
          :size="size"
          :placeholder="placeholder || '输入变量编码'"
          clearable
          @focus="onInputFocus"
          @input="onCustomInput"
          @clear="onCustomClear"
        />

        <el-tooltip v-if="allowCustom && hasVarOptions" content="切换为手动输入变量" placement="top">
          <span class="mode-switch" @click="customMode = true"><i class="el-icon-edit" /></span>
        </el-tooltip>
      </div>
    </template>
  </div>
</template>

<script>
import { varTypeLabel, varTypeTagColor } from '@/constants/varTypes'
import { formatVarDisplay } from '@/utils/varDisplay'

export default {
  name: 'VarPicker',
  props: {
    /** 绑定值：varCode（默认）或 id（数字） */
    value: { type: [String, Number], default: '' },
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
    columnLabels: { type: [String, Object], default: 'variable' }
  },
  data() {
    return {
      /** 当前是否处于手动输入模式 */
      customMode: false,
      /** 手动输入模式下的本地值，避免依赖 prop 异步更新导致失焦清空 */
      localCustomValue: this.value || '',
      /** popover 显示状态 */
      popoverVisible: false,
      /** 当前选中的分类 */
      activeCategory: 'standalone',
      /** 搜索文本 */
      searchText: '',
      /** 展开的数据对象 varCode */
      expandedObject: null,
      /** 大字段集合分页 */
      fieldPageSize: 100,
      rightPage: 1,
      objectChildPages: {},
      /** 弹窗尺寸，可通过右下角拖拽调整 */
      panelWidth: 560,
      panelHeight: 360,
      resizingPanel: false,
      panelBodyCursor: '',
      panelBodyUserSelect: ''
    }
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
      immediate: true,
      handler(list) {
        if (!list || list.length === 0) return
        var exists = list.some(function (cat) { return cat.key === this.activeCategory }.bind(this))
        if (!exists) {
          this.activeCategory = list[0].key
        }
      }
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
      if (!val) {
        this.searchText = ''
        this.expandedObject = null
        this.stopPanelResize()
      }
    }
  },
  mounted() {
    this._autoSwitchIfUnmatched()
  },
  beforeDestroy() {
    this.updateDocumentListener(false)
    this.stopPanelResize()
  },
  computed: {
    /** 是否有可选的变量选项 */
    hasVarOptions() {
      return this.vars.length > 0
    },
    /** 弹窗宽度：跟随容器，最小 520px */
    popoverWidth() {
      var el = this.$el
      var base = el && el.offsetWidth > 520 ? el.offsetWidth : 520
      return Math.max(base, this.panelWidth)
    },
    panelStyle() {
      return {
        width: this.popoverWidth + 'px',
        height: this.panelHeight + 'px'
      }
    },
    /** 是否显示搜索框（项数超过 10 时） */
    showSearch() {
      return this.rightItems.length > 10
    },
    /** 当前选中的 varCode（用于高亮） */
    currentValue() {
      if (!this.value) return null
      if (this.valueKey === 'id') {
        var found = this.vars.find(function (v) { return String(v.id) === String(this.value) }.bind(this))
        return found ? found.varCode : null
      }
      var matched = this.findOptionByCode(this.value)
      if (matched) return matched.varCode
      return this.value
    },
    /** 显示文本 */
    displayValue() {
      if (!this.value) return ''
      var v = this.valueKey === 'id'
        ? this.vars.find(function (item) { return String(item.id) === String(this.value) }.bind(this))
        : this.findOptionByCode(this.value)
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
        { key: 'selected', label: '已选字段', count: 0 },
        { key: 'standalone', label: '普通变量', count: 0 },
        { key: 'constant', label: '常量', count: 0 },
        { key: 'object', label: '数据对象', count: 0 },
        { key: 'model', label: '模型', count: 0 }
      ]
      var counts = { selected: this.selectedItems.length, standalone: 0, constant: 0, object: 0, model: 0 }
      this.vars.forEach(function (v) {
        var cat = (v._ref && v._ref.category) || 'standalone'
        if (counts[cat] !== undefined) counts[cat]++
      })
      list.forEach(function (item) {
        item.count = counts[item.key]
      })
      return list.filter(function (item) { return item.count > 0 })
    },
    selectedItems() {
      var result = []
      var seen = {}
      ;(this.selectedVars || []).forEach(function (item) {
        var option = this.resolveSelectedOption(item)
        if (!option || !option.varCode) return
        var key = this.optionIdentityKey(option)
        if (seen[key]) return
        seen[key] = true
        result.push(Object.assign({}, option, { _selected: true }))
      }.bind(this))
      return result
    },
    /** 右侧项列表（按当前分类过滤 + 排序）。对象字段自动附加同对象下的所有字段 children） */
    rightItems() {
      var self = this
      if (this.activeCategory === 'selected') {
        return this.selectedItems
      }
      var list = this.vars.filter(function (v) {
        var cat = (v._ref && v._ref.category) || 'standalone'
        if (cat !== self.activeCategory) return false
        if (self.typeFilter && v.varType !== self.typeFilter) return false
        return true
      })

      // 对象分类：按 objectCode 分组，每组的第一个 item 附加 children 数组
      if (this.activeCategory === 'object') {
        var byObj = {}
        list.forEach(function (v) {
          var key = self.objectGroupCode(v)
          if (!byObj[key]) byObj[key] = []
          byObj[key].push(v)
        })
        var result = []
        var keys = Object.keys(byObj)
        for (var i = 0; i < keys.length; i++) {
          var group = byObj[keys[i]]
          group.sort(function (a, b) { return (a.varCode || '').localeCompare(b.varCode || '') })
          var first = Object.assign({}, group[0], { children: group, _objectGroup: true, _objectGroupKey: keys[i] })
          result.push(first)
        }
        return result
      }

      return list.sort(function (a, b) { return (a.varCode || '').localeCompare(b.varCode || '') })
    },
    /** 过滤后的右侧项（支持搜索） */
    filteredRightItems() {
      if (!this.searchText) return this.rightItems
      var s = this.searchText.toLowerCase()
      return this.rightItems.filter(function (v) {
        return (v.varCode && v.varCode.toLowerCase().indexOf(s) !== -1) ||
          (v.varLabel && v.varLabel.toLowerCase().indexOf(s) !== -1) ||
          (v.children && v.children.some(function (child) {
            return (child.varCode && child.varCode.toLowerCase().indexOf(s) !== -1) ||
              (child.varLabel && child.varLabel.toLowerCase().indexOf(s) !== -1)
          }))
      })
    },
    rightNeedsPaging() {
      return this.filteredRightItems.length > this.fieldPageSize
    },
    pagedRightItems() {
      if (!this.rightNeedsPaging) return this.filteredRightItems
      var start = (this.rightPage - 1) * this.fieldPageSize
      return this.filteredRightItems.slice(start, start + this.fieldPageSize)
    }
  },
  methods: {
    objectGroupCode(item) {
      var ref = (item && item._ref) || {}
      if (ref.objectCode) return ref.objectCode
      var code = item && item.varCode ? item.varCode : ''
      return code.indexOf('.') !== -1 ? code.split('.')[0] : (item && item.objectCode) || 'unknown'
    },
    objectGroupLabel(item) {
      var ref = (item && item._ref) || {}
      return ref.objectLabel || ref.objectCode || (item && item.objectLabel) || this.objectGroupCode(item)
    },
    objectFieldPath(item) {
      if (!item) return ''
      var code = item.varCode || ''
      if (code.indexOf('.') !== -1) return code
      var ref = item._ref || {}
      var objectCode = ref.objectScriptName || ref.objectCode || item.objectCode || ''
      return objectCode ? objectCode + '.' + code : code
    },
    objectFieldRelativePath(item) {
      if (!item) return ''
      var code = item.varCode || ''
      var ref = item._ref || {}
      var objectCode = ref.objectScriptName || ref.objectCode || item.objectCode || ''
      if (objectCode && code.indexOf(objectCode + '.') === 0) {
        return code.substring(objectCode.length + 1)
      }
      return code
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
      if (relativeCode && label.lastIndexOf(' ' + relativeCode) === label.length - relativeCode.length - 1) {
        label = label.substring(0, label.length - relativeCode.length - 1)
      }
      return label || relativeCode
    },
    fieldCodeWithoutObject(item) {
      var code = item && item.varCode ? item.varCode : ''
      if (code.indexOf('.') === -1) return code
      return code.substring(code.lastIndexOf('.') + 1)
    },
    findOptionByCode(code) {
      if (!code) return null
      var exact = this.vars.find(function (v) { return v.varCode === code })
      if (exact) return exact
      var matches = this.vars.filter(function (v) {
        return v && v._ref && v._ref.category === 'object' && this.fieldCodeWithoutObject(v) === code
      }.bind(this))
      return matches.length === 1 ? matches[0] : null
    },
    findOptionByIdentity(id, refType) {
      if (id == null || id === '') return null
      return this.vars.find(function (v) {
        var optionId = v.id != null ? v.id : (v._varId != null ? v._varId : (v.varObj && v.varObj.id))
        var optionType = v._refType || v.refType || (v.varObj && v.varObj.refType) || (v._ref && v._ref.refType)
        return String(optionId) === String(id) && (!refType || !optionType || optionType === refType)
      }) || null
    },
    resolveSelectedOption(item) {
      if (item == null || item === '') return null
      if (typeof item === 'string' || typeof item === 'number') {
        return this.findOptionByCode(item) || this.findOptionByIdentity(item, null)
      }
      var id = item._varId != null ? item._varId : (item.id != null ? item.id : (item.varObj && item.varObj.id))
      var refType = item._refType || item.refType || (item.varObj && item.varObj.refType) || (item._ref && item._ref.refType)
      return this.findOptionByIdentity(id, refType) ||
        this.findOptionByCode(item.varCode || item.refCode)
    },
    optionIdentityKey(item) {
      var id = item && (item._varId != null ? item._varId : (item.id != null ? item.id : (item.varObj && item.varObj.id)))
      var refType = item && (item._refType || item.refType || (item.varObj && item.varObj.refType) || (item._ref && item._ref.refType))
      if (id != null && id !== '') return (refType || 'REF') + ':' + id
      var cat = (item && item._ref && item._ref.category) || ''
      return cat + ':' + (item && item.varCode ? item.varCode : '')
    },
    optionCategory(item) {
      return (item && item._ref && item._ref.category) || 'standalone'
    },
    optionLabel(item) {
      if (!item) return ''
      return item.varLabelText || item.labelText || item.varName || item.varLabel || ''
    },
    /** 类型短标签（一字符） */
    typeShortLabel(t) {
      var map = {
        STRING: '字', NUMBER: '数', BOOLEAN: '布',
        DATE: '日', ENUM: '枚', OBJECT: '对', LIST: '列', MAP: '映'
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
      return item && (item._objectGroupKey || item.varCode) ? (item._objectGroupKey || item.varCode) : ''
    },
    objectChildPage(item) {
      var key = this.objectChildPageKey(item)
      return (key && this.objectChildPages[key]) || 1
    },
    objectChildNeedsPaging(item) {
      return item && item.children && item.children.length > this.fieldPageSize
    },
    pagedObjectChildren(item) {
      if (!item || !item.children) return []
      if (!this.objectChildNeedsPaging(item)) return item.children
      var page = this.objectChildPage(item)
      var start = (page - 1) * this.fieldPageSize
      return item.children.slice(start, start + this.fieldPageSize)
    },
    onObjectChildPageChange(item, page) {
      var key = this.objectChildPageKey(item)
      if (key) this.$set(this.objectChildPages, key, page)
    },
    /** 行点击：数据对象展开嵌套，其他直接选中 */
    onItemClick(item) {
      if (item._objectGroup) {
        var groupKey = item._objectGroupKey || item.varCode
        if (this.expandedObject === groupKey) {
          this.expandedObject = null
        } else {
          this.expandedObject = groupKey
          if (!this.objectChildPages[groupKey]) this.$set(this.objectChildPages, groupKey, 1)
        }
        return
      }
      var val = this.valueKey === 'id' ? item.id : item.varCode
      this.$emit('input', val)
      this.$emit('select', item)
      this.closePopover()
    },
    closePopover() {
      var doClose = function () {
        this.popoverVisible = false
        var popover = this.$refs.popover
        if (popover && typeof popover.doClose === 'function') {
          popover.doClose()
        }
        this.hidePickerPoppers()
      }.bind(this)
      doClose()
      this.$nextTick(doClose)
      setTimeout(doClose, 0)
    },
    hidePickerPoppers() {
      if (typeof document === 'undefined') return
      var poppers = document.querySelectorAll('.var-picker-popover')
      for (var i = 0; i < poppers.length; i++) {
        poppers[i].style.display = 'none'
      }
    },
    /** 获取选项的实际值（varCode 或 id） */
    getOptionValue(v) {
      return this.valueKey === 'id' ? v.id : (v.varCode || v.varLabel)
    },
    /** 统一变量展示文本 */
    getVarLabel(v) {
      var ref = v._ref || {}
      var objLabel = ref.category === 'object' ? (ref.objectLabel || '') : ''
      return formatVarDisplay({
        varLabel: v.varLabel,
        varCode: v.varCode,
        varType: v.varType,
        sourceType: ref.category === 'object' ? 'dataObject' : (ref.category === 'constant' ? 'constant' : 'variable'),
        objectLabel: objLabel
      })
    },
    onCascaderChange(path) {
      if (!path || !path.length) {
        this.$emit('input', '')
        this.$emit('select', null)
        return
      }
      var refCode = path[path.length - 1]
      this.$emit('input', refCode)
      var varObj = this.vars.find(function (v) { return v.varCode === refCode }) || null
      this.$emit('select', varObj)
    },
    /** 输入框获得焦点时自动弹出选择器面板 */
    onInputFocus() {
      this.openPopover()
    },
    /** 点击输入框时弹出选择器面板 */
    onInputClick() {
      this.openPopover()
    },
    openPopover() {
      if (this.groupedByCategory && this.hasVarOptions) {
        var wasVisible = this.popoverVisible
        this.popoverVisible = true
        if (!wasVisible) this.$nextTick(this.focusCurrentValueInPicker)
      }
    },
    focusCurrentValueInPicker() {
      var option = this.valueKey === 'id'
        ? this.findOptionByIdentity(this.value, null)
        : this.findOptionByCode(this.value)
      if (!option) return

      var category = this.optionCategory(option)
      this.activeCategory = category
      this.expandedObject = null
      this.rightPage = 1

      this.$nextTick(function () {
        if (category === 'object') {
          this.focusObjectOption(option)
        } else {
          this.focusFlatOption(option)
        }
        this.scrollCurrentValueIntoView()
      }.bind(this))
    },
    focusFlatOption(option) {
      var list = this.filteredRightItems
      var index = list.findIndex(function (item) {
        return this.optionIdentityKey(item) === this.optionIdentityKey(option) || item.varCode === option.varCode
      }.bind(this))
      if (index >= 0) {
        this.rightPage = Math.floor(index / this.fieldPageSize) + 1
      }
    },
    focusObjectOption(option) {
      var groupKey = this.objectGroupCode(option)
      var groups = this.filteredRightItems
      var groupIndex = groups.findIndex(function (item) {
        return item._objectGroupKey === groupKey || this.objectGroupCode(item) === groupKey
      }.bind(this))
      if (groupIndex >= 0) {
        this.rightPage = Math.floor(groupIndex / this.fieldPageSize) + 1
      }
      this.expandedObject = groupKey
      var group = groups[groupIndex]
      if (group && group.children && group.children.length) {
        var childIndex = group.children.findIndex(function (child) {
          return this.optionIdentityKey(child) === this.optionIdentityKey(option) || child.varCode === option.varCode
        }.bind(this))
        if (childIndex >= 0) {
          this.$set(this.objectChildPages, groupKey, Math.floor(childIndex / this.fieldPageSize) + 1)
        }
      }
    },
    scrollCurrentValueIntoView() {
      this.$nextTick(function () {
        var popover = this.$refs.popover
        var popper = popover && popover.popperElm
        if (!popper) return
        var target = popper.querySelector('.vp-row--selected, .vp-child-item--selected')
        if (target && target.scrollIntoView) {
          target.scrollIntoView({ block: 'nearest' })
        }
      })
    },
    updateDocumentListener(visible) {
      if (typeof document === 'undefined') return
      var method = visible ? 'addEventListener' : 'removeEventListener'
      document[method]('mousedown', this.onDocumentMouseDown, true)
    },
    onDocumentMouseDown(event) {
      if (!this.popoverVisible) return
      var target = event.target
      var root = this.$el
      var popover = this.$refs.popover
      var popper = popover && popover.popperElm
      if ((root && root.contains(target)) || (popper && popper.contains(target))) {
        return
      }
      this.closePopover()
    },
    startPanelResize(event) {
      this.beginPanelResize(event && event.clientX, event && event.clientY, event)
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
      window.addEventListener('touchmove', this.onPanelTouchResize, { passive: false })
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
      var popover = this.$refs.popover
      var popper = popover && popover.popperElm
      if (!popper) return
      var rect = popper.getBoundingClientRect()
      var width = Math.round(clientX - rect.left)
      var height = Math.round(clientY - rect.top)
      this.panelWidth = Math.min(Math.max(width, 520), 960)
      this.panelHeight = Math.min(Math.max(height, 300), 720)
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
      this.$emit('input', '')
      this.$emit('select', null)
      this.$emit('clear')
      this.closePopover()
    },
    onChange(val) {
      this.$emit('input', val)
      if (!val) {
        this.$emit('select', null)
        return
      }
      var varObj = this.valueKey === 'id'
        ? (this.vars.find(function (v) { return String(v.id) === String(val) }) || null)
        : (this.vars.find(function (v) { return v.varCode === val }) || null)
      this.$emit('select', varObj)
    },
    /** 手动输入模式下的输入事件 */
    onCustomInput(val) {
      this.$emit('input', val)
      var varObj = this.vars.find(function (v) { return v.varCode === val }) || null
      this.$emit('select', varObj || { varCode: val, varLabel: val, _custom: true })
    },
    /** 手动输入模式下的清空事件 */
    onCustomClear() {
      this.localCustomValue = ''
      this.$emit('input', '')
      this.$emit('select', null)
    },
    /** 回显时自动识别：当前值非空但在变量列表中找不到时，自动切换到手动输入模式 */
    _autoSwitchIfUnmatched() {
      if (!this.allowCustom || !this.value || this.customMode) return
      if (!this.hasVarOptions) return
      var found
      if (this.valueKey === 'id') {
        found = this.vars.some(function (v) { return String(v.id) === String(this.value) }.bind(this))
      } else {
        found = !!this.findOptionByCode(this.value)
      }
      if (!found) {
        this.customMode = true
        this.localCustomValue = this.value
      }
    },
    typeLabel(t) {
      return varTypeLabel(t)
    },
    /** 根据配置获取编码列标签 */
    codeColumnLabel() {
      if (typeof this.columnLabels === 'object') return this.columnLabels.codeLabel || '字段编码'
      if (this.activeCategory === 'object' || this.activeCategory === 'selected') return '字段编码'
      if (this.activeCategory === 'model') return '模型编码'
      const labels = {
        variable: '变量编码',
        dataObject: '属性字段路径',
        model: '模型编码'
      }
      return labels[this.columnLabels] || '字段编码'
    },
    /** 根据配置获取名称列标签 */
    nameColumnLabel() {
      if (typeof this.columnLabels === 'object') return this.columnLabels.nameLabel || '字段名称'
      if (this.activeCategory === 'object' || this.activeCategory === 'selected') return '字段名称'
      if (this.activeCategory === 'model') return '模型名称'
      const labels = {
        variable: '变量名称',
        dataObject: '属性名称',
        model: '模型名称'
      }
      return labels[this.columnLabels] || '字段名称'
    },
    /** 获取类型单字符标识（带颜色） */
    typeChar(varType) {
      const map = {
        STRING: 's',
        NUMBER: 'i',
        BOOLEAN: 'b',
        DATE: 'd',
        ENUM: 'e',
        OBJECT: 'o',
        LIST: 'l',
        MAP: 'm',
        MODEL: 'M'
      }
      return map[varType] || '?'
    }
  }
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
  display: inline-block;
  vertical-align: middle;
  max-width: 100%;
}
.var-picker-wrap ::v-deep .el-popover__reference-wrapper {
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
.select-input-row ::v-deep .el-popover__reference-wrapper,
.select-input-row .el-select,
.select-input-row .el-input {
  flex: 1;
  min-width: 0;
  width: 100%;
}
.vp-reference {
  width: 100%;
}
.mode-switch {
  flex-shrink: 0;
  cursor: pointer;
  color: #1890ff;
  font-size: 14px;
  padding: 2px;
  border-radius: 3px;
  transition: all 0.2s;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.mode-switch:hover {
  background: #e6f7ff;
  color: #096dd9;
}
.vp-clear-btn {
  cursor: pointer;
  color: #909399;
  margin-right: 4px;
}
.vp-clear-btn:hover {
  color: #409EFF;
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
  max-width: 960px;
  max-height: 720px;
}
.vp-left {
  width: 100px;
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
}
.vp-cat-item:hover {
  background: #f5f7fa;
}
.vp-cat-item--active {
  background: #e6f7ff;
  color: #1890ff;
  font-weight: 600;
  border-right: 2px solid #1890ff;
}
.vp-cat-count {
  font-size: 10px;
  color: #c0c4cc;
  background: #f0f2f5;
  padding: 1px 5px;
  border-radius: 8px;
}
.vp-cat-item--active .vp-cat-count {
  background: #bae7ff;
  color: #1890ff;
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
.vp-th--type { width: 72px; text-align: center; padding: 8px 4px; }
.vp-th--code { width: 130px; }
.vp-th--name { min-width: 80px; }
.vp-row {
  cursor: pointer;
  transition: background 0.1s;
}
.vp-row:hover { background: #f5f7fa; }
.vp-row--selected { background: #e6f7ff; }
.vp-row--selected:hover { background: #d0e8ff; }
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
.vp-children-row { background: #f9fafc; }
.vp-children-td { padding: 6px 12px; }
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
.vp-children-list { max-height: 160px; overflow-y: auto; }
.vp-child-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  font-size: 12px;
  cursor: pointer;
  transition: background 0.1s;
}
.vp-child-item:hover { background: #e6f7ff; }
.vp-child-item--selected { background: #d0e8ff; }
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
.vp-type-label,
.vp-child-type {
  font-size: 11px;
  color: #606266;
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
  border-color: #409EFF;
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
.vp-type-badge--s { background: #409EFF; }
/* 整数 i - 橙色 */
.vp-type-badge--i { background: #E6A23C; }
/* 布尔 b - 绿色 */
.vp-type-badge--b { background: #67C23A; }
/* 日期 d - 紫色 */
.vp-type-badge--d { background: #9C27B0; }
/* 枚举 e - 红色 */
.vp-type-badge--e { background: #F56C6C; }
/* 对象 o - 青色 */
.vp-type-badge--o { background: #00BCD4; }
/* 列表 l - 黄色 */
.vp-type-badge--l { background: #FF9800; }
/* 映射 m - 灰色 */
.vp-type-badge--m { background: #909399; }
/* 模型 M - 绿色 */
.vp-type-badge--M { background: #13C2C2; }
/* 默认 ? - 浅灰 */
.vp-type-badge--? { background: #C0C4CC; }
</style>
