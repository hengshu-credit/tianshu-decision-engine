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
          trigger="click"
          popper-class="var-picker-popover"
        >
          <div class="vp-panel">
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
                    <template v-for="v in filteredRightItems">
                      <tr
                        :key="v._objectGroupKey || v.varCode"
                        class="vp-row"
                        :class="{ 'vp-row--selected': v.varCode === currentValue }"
                        @click="onItemClick(v)"
                      >
                        <td class="vp-td vp-td--type">
                          <span class="vp-type-badge" :class="'vp-type-badge--' + typeChar(v.varType)">{{ typeChar(v.varType) }}</span>
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
                              <i class="el-icon-document" /> {{ v._ref && v._ref.objectLabel ? v._ref.objectLabel : v.objectCode }} 字段路径
                            </div>
                            <div class="vp-children-list">
                              <div
                                v-for="child in v.children"
                                :key="(v._objectGroupKey || '') + '.' + child.varCode"
                                class="vp-child-item"
                                @click.stop="onItemClick(child)"
                              >
                                <span class="vp-child-path">{{ objectFieldPath(child) }}</span>
                                <span class="vp-type-badge vp-type-badge--sm" :class="'vp-type-badge--' + typeChar(child.varType)">{{ typeChar(child.varType) }}</span>
                                <span class="vp-child-name">{{ optionLabel(child) || child.varCode }}</span>
                              </div>
                            </div>
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
            </div>
          </div>
          <div slot="reference" class="vp-reference">
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
              <i v-if="value && allowCustom" slot="suffix" class="el-input__icon el-icon-close vp-clear-btn" @click.stop="onClearValue" />
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
      expandedObject: null
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
    popoverVisible(val) {
      if (!val) {
        this.searchText = ''
        this.expandedObject = null
      }
    }
  },
  mounted() {
    this._autoSwitchIfUnmatched()
  },
  computed: {
    /** 是否有可选的变量选项 */
    hasVarOptions() {
      return this.vars.length > 0
    },
    /** 弹窗宽度：跟随容器，最小 520px */
    popoverWidth() {
      var el = this.$el
      if (el && el.offsetWidth > 520) return el.offsetWidth
      return 520
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
        { key: 'standalone', label: '普通变量', count: 0 },
        { key: 'constant', label: '常量', count: 0 },
        { key: 'object', label: '数据对象', count: 0 },
        { key: 'model', label: '模型', count: 0 }
      ]
      var counts = { standalone: 0, constant: 0, object: 0, model: 0 }
      this.vars.forEach(function (v) {
        var cat = (v._ref && v._ref.category) || 'standalone'
        if (counts[cat] !== undefined) counts[cat]++
      })
      list.forEach(function (item) {
        item.count = counts[item.key]
      })
      return list.filter(function (item) { return item.count > 0 })
    },
    /** 右侧项列表（按当前分类过滤 + 排序）。对象字段自动附加同对象下的所有字段 children） */
    rightItems() {
      var self = this
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
    },
    /** 行点击：数据对象展开嵌套，其他直接选中 */
    onItemClick(item) {
      if (item._objectGroup) {
        var groupKey = item._objectGroupKey || item.varCode
        if (this.expandedObject === groupKey) {
          this.expandedObject = null
        } else {
          this.expandedObject = groupKey
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
      if (this.groupedByCategory && this.hasVarOptions) {
        this.popoverVisible = true
      }
    },
    /** 点击输入框时弹出选择器面板 */
    onInputClick() {
      if (this.groupedByCategory && this.hasVarOptions) {
        this.popoverVisible = true
      }
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
        MAP: 'm'
      }
      return map[varType] || '?'
    }
  }
}
</script>

<style scoped>
.var-picker-wrap {
  display: inline-block;
  vertical-align: middle;
}
.custom-input-row,
.select-input-row {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
}
.custom-input-row .el-input,
.select-input-row .el-popover,
.select-input-row .el-select,
.select-input-row .el-input {
  flex: 1;
  min-width: 0;
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
  height: 360px;
  user-select: none;
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
.vp-th--type { width: 36px; text-align: center; padding: 8px 4px; }
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
.vp-td--type { text-align: center; padding: 7px 4px; }
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
.vp-child-path {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 11px;
  color: #909399;
  flex-shrink: 0;
}
.vp-child-name {
  color: #303133;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
/* 默认 ? - 浅灰 */
.vp-type-badge--? { background: #C0C4CC; }
</style>
