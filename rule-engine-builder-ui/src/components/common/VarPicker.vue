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
        <el-cascader
          v-if="groupedByCategory && cascaderOptions.length"
          :value="cascaderValue"
          :options="cascaderOptions"
          :placeholder="placeholder || '选择变量/常量/对象字段'"
          :size="size"
          style="width:100%"
          clearable
          filterable
          :props="cascaderProps"
          @change="onCascaderChange"
          @clear="$emit('input', ''); $emit('select', null)"
        />

        <el-select
          v-else-if="vars.length"
          :value="value"
          :placeholder="placeholder || '选择变量'"
          filterable
          clearable
          :size="size"
          style="width:100%"
          popper-append-to-body
          @change="onChange"
          @clear="$emit('input', ''); $emit('select', null)"
        >
          <template v-if="grouped">
            <el-option-group v-for="group in groupedOptions" :key="group.label" :label="group.label">
              <el-option v-for="v in group.vars" :key="v.id || v.varCode" :value="getOptionValue(v)" :label="getVarLabel(v)" />
            </el-option-group>
          </template>
          <template v-if="!grouped">
            <el-option v-for="v in filteredVars" :key="v.id || v.varCode" :value="getOptionValue(v)" :label="getVarLabel(v)" />
            <div v-if="filteredVars.length === 0" class="var-empty">
              <i class="el-icon-warning-outline" /> {{ loading ? '加载中...' : '暂无可用变量，请先在变量管理中创建' }}
            </div>
          </template>
        </el-select>

        <!-- 无变量时自动回退到输入框 -->
        <el-input
          v-else
          v-model="localCustomValue"
          :size="size"
          :placeholder="placeholder || '输入变量编码'"
          clearable
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
    valueKey: { type: String, default: 'code' }
  },
  data() {
    return {
      /** 当前是否处于手动输入模式 */
      customMode: false,
      /** 手动输入模式下的本地值，避免依赖 prop 异步更新导致失焦清空 */
      localCustomValue: this.value || ''
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
    value(newVal) {
      this.localCustomValue = newVal || ''
      this._autoSwitchIfUnmatched()
    },
    customMode(val) {
      if (val) this.localCustomValue = this.value || ''
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
    filteredVars() {
      let list = this.vars
      if (this.typeFilter) list = list.filter(v => v.varType === this.typeFilter)
      return list
    },
    cascaderProps() {
      return { emitPath: true, checkStrictly: false, expandTrigger: 'hover' }
    },
    cascaderValue() {
      if (!this.value) return null
      // valueKey='id' 时，this.value 是 numeric id（字符串），查找 id→path 映射
      if (this.valueKey === 'id') {
        return this.idToPath[this.value] || null
      }
      // valueKey='code' 时，this.value 是 varCode，查找 refCode→path 映射
      return this.refCodeToPath[this.value] || null
    },
    /** id（字符串）→ cascader path 的映射 */
    idToPath() {
      const map = {}
      const walk = (nodes, path) => {
        (nodes || []).forEach(n => {
          const p = [...path, n.value]
          if (n.children && n.children.length) {
            walk(n.children, p)
          } else {
            // 叶节点的 value 即为 id
            map[String(n.value)] = p
          }
        })
      }
      walk(this.cascaderOptions, [])
      return map
    },
    refCodeToPath() {
      const map = {}
      const walk = (nodes, path) => {
        (nodes || []).forEach(n => {
          const p = [...path, n.value]
          if (n.children && n.children.length) {
            walk(n.children, p)
          } else {
            map[n.value] = p
          }
        })
      }
      walk(this.cascaderOptions, [])
      return map
    },
    /** 三级联动：一级分类 -> 二级组/对象 -> 三级具体项 */
    cascaderOptions() {
      const refs = this.vars.filter(v => v._ref)
      if (!refs.length) return []
      let standalone = refs.filter(v => v._ref.category === 'standalone')
      const constantRefs = refs.filter(v => v._ref.category === 'constant')
      const objectRefs = refs.filter(v => v._ref.category === 'object')
      let useTypeFilter = !!this.typeFilter
      if (useTypeFilter && this.showAllWhenFilterEmpty) {
        const filteredStandalone = standalone.filter(v => v.varType === this.typeFilter)
        const hasAnyFiltered = filteredStandalone.length > 0 || constantRefs.some(v => v.varType === this.typeFilter) || objectRefs.some(v => v.varType === this.typeFilter)
        if (!hasAnyFiltered) useTypeFilter = false
      }
      if (useTypeFilter) {
        standalone = standalone.filter(v => v.varType === this.typeFilter)
      }
      // valueKey='id' 时叶子节点使用 id（数值），否则使用 varCode（字符串）
      const useIdValue = this.valueKey === 'id'
      const options = []
      if (standalone.length) {
        options.push({
          value: '__standalone__',
          label: '普通变量',
          children: standalone.map(v => ({ value: useIdValue ? v.id : v.varCode, label: this.getVarLabel(v) }))
        })
      }
      if (constantRefs.length) {
        // 常量直接作为叶子（refCode = scriptName），不再按 group 分组
        const filtered = useTypeFilter ? constantRefs.filter(v => v.varType === this.typeFilter) : constantRefs
        if (filtered.length) {
          options.push({
            value: '__constant__',
            label: '常量',
            children: filtered.map(v => ({ value: useIdValue ? v.id : v.varCode, label: this.getVarLabel(v) }))
          })
        }
      }
      const byObject = {}
      objectRefs.forEach(v => {
        const oc = v._ref.objectCode || ''
        const ol = v._ref.objectLabel || oc
        const key = oc || 'unknown'
        if (!byObject[key]) byObject[key] = { objectCode: oc, objectLabel: ol, vars: [] }
        byObject[key].vars.push(v)
      })
      const objChildren = Object.keys(byObject).filter(k => k !== 'unknown').map(key => {
        const g = byObject[key]
        const items = useTypeFilter ? g.vars.filter(v => v.varType === this.typeFilter) : g.vars
        if (!items.length) return null
        return {
          value: g.objectCode,
          label: g.objectLabel || g.objectCode,
          children: items.map(v => ({ value: useIdValue ? v.id : v.varCode, label: this.getVarLabel(v) }))
        }
      }).filter(Boolean)
      if (objChildren.length) {
        options.push({ value: '__object__', label: '对象', children: objChildren })
      }
      return options
    },
    groupedOptions() {
      const groups = [
        { label: '输入参数 (INPUT)', source: 'INPUT', vars: [] },
        { label: '计算变量 (COMPUTED)', source: 'COMPUTED', vars: [] },
        { label: '常量 (CONSTANT)', source: 'CONSTANT', vars: [] },
        { label: '其他', source: '', vars: [] }
      ]
      const known = new Set(['INPUT', 'COMPUTED', 'CONSTANT'])
      this.vars.forEach(v => {
        const g = groups.find(g => g.source === (v.varSource || '')) || groups[3]
        if (!known.has(v.varSource || '')) groups[3].vars.push(v)
        else g.vars.push(v)
      })
      return groups.filter(g => g.vars.length > 0)
    }
  },
  methods: {
    /** 获取选项的实际值（varCode 或 id） */
    getOptionValue(v) {
      return this.valueKey === 'id' ? v.id : (v.varCode || v.varLabel)
    },
    /** 统一变量展示文本（变量类型 / 标签(编码) 或 对象时：类型 / 对象标签 / 字段名(编码)） */
    getVarLabel(v) {
      const ref = v._ref || {}
      const objLabel = ref.category === 'object' ? (ref.objectLabel || '') : ''
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
      const refCode = path[path.length - 1]
      this.$emit('input', refCode)
      const varObj = this.vars.find(v => v.varCode === refCode) || null
      this.$emit('select', varObj)
    },
    onChange(val) {
      // val 是 getOptionValue(v) 的结果（varCode 或 id）
      this.$emit('input', val)
      if (!val) {
        this.$emit('select', null)
        return
      }
      // 根据 valueKey 查找对应的完整变量对象
      const varObj = this.valueKey === 'id'
        ? (this.vars.find(v => String(v.id) === String(val)) || null)
        : (this.vars.find(v => v.varCode === val) || null)
      this.$emit('select', varObj)
    },
    /** 手动输入模式下的输入事件（v-model 已同步 localCustomValue） */
    onCustomInput(val) {
      this.$emit('input', val)
      const varObj = this.vars.find(v => v.varCode === val) || null
      this.$emit('select', varObj || { varCode: val, varLabel: val, _custom: true })
    },
    /** 手动输入模式下的清空事件 */
    onCustomClear() {
      this.localCustomValue = ''
      this.$emit('input', '')
      this.$emit('select', null)
    },
    /**
     * 回显时自动识别：当前值非空但在变量列表中找不到时，自动切换到手动输入模式。
     * 在 vars 加载完成或 value 变化时调用。
     */
    _autoSwitchIfUnmatched() {
      if (!this.allowCustom || !this.value || this.customMode) return
      if (!this.hasVarOptions) return
      let found
      if (this.valueKey === 'id') {
        found = this.vars.some(v => String(v.id) === String(this.value))
      } else {
        found = this.vars.some(v => v.varCode === this.value)
      }
      if (!found) {
        this.customMode = true
        this.localCustomValue = this.value
      }
    },
    typeLabel(t) {
      return varTypeLabel(t)
    },
    typeColor(t) {
      return varTypeTagColor(t)
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
.select-input-row .el-cascader,
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
.var-option {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.var-label {
  font-weight: 500;
  color: #333;
  flex-shrink: 0;
}
.var-code {
  flex: 1;
  font-size: 11px;
  color: #999;
  font-family: 'Consolas', monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.var-type-tag {
  flex-shrink: 0;
  margin-left: auto;
}
.var-empty {
  padding: 8px 12px;
  font-size: 12px;
  color: #bbb;
  text-align: center;
}
</style>
