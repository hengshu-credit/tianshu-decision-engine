<template>
  <div class="act-designer">
    <!-- 顶部工具栏 -->
    <div class="act-header">
      <div class="act-title-area">
        <el-button type="text" icon="el-icon-back" @click="$router.back()" style="color:#606266;" />
        <i class="el-icon-data-analysis act-title-icon" />
        <span class="act-title">复杂交叉表设计器</span>
        <el-tag size="mini" type="info" style="margin-left:8px;">
          {{ totalRowCount }} 行 × {{ totalColCount }} 列
        </el-tag>
      </div>
      <div class="act-toolbar">
        <el-button size="small" icon="el-icon-document" @click="handleSave">临时保存配置</el-button>
        <el-button size="small" type="warning" icon="el-icon-cpu" @click="handleCompile">保存并编译</el-button>
        <el-button size="small" type="primary" icon="el-icon-video-play" @click="handleTest">编译后测试</el-button>
      </div>
    </div>

    <!-- 维度配置区：行维度 + 列维度 并排 -->
    <div class="act-dim-row">
      <!-- 行维度 -->
      <div class="act-dim-panel">
        <div class="dim-panel-header">
          <i class="el-icon-s-unfold" style="color:#1890ff;" /> 行维度
          <el-button size="mini" icon="el-icon-plus" @click="addDimension('row')">添加行维度</el-button>
        </div>
        <div v-for="(dim, di) in model.rowDimensions" :key="'rd-' + di" class="dim-config-card">
          <div class="dim-config-header">
            <operand-picker
              :vars="varPickerOptions"
              :functions="projectFunctions"
              :selected-vars="selectedVarPickerOptions"
              :value="dim.operand"
              :allowed-kinds="readOperandKinds"
              placeholder="选择维度字段或路径"
              width="100%"
              class="dim-field-var"
              @input="value => setDimensionOperand(dim, value)"
            />
            <el-input v-model="dim.varLabel" size="mini" placeholder="维度名称" class="dim-field-label" />
            <el-select v-model="dim.varType" size="mini" class="dim-field-type" popper-append-to-body>
              <el-option v-for="opt in varTypeFormOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
            <el-button type="text" size="mini" icon="el-icon-delete" style="color:#F76E6C;" @click="removeDimension('row', di)" />
          </div>
          <div class="segments-area">
            <div v-for="(seg, si) in dim.segments" :key="si" class="segment-row">
              <el-select v-model="seg.operator" size="mini" class="seg-op" @change="onSegmentOperatorChange(seg)">
                <el-option label="等于" value="==" /><el-option label="不等于" value="!=" />
                <el-option label="大于" value=">" /><el-option label="大于等于" value=">=" />
                <el-option label="小于" value="<" /><el-option label="小于等于" value="<=" />
                <el-option label="区间" value="range" />
              </el-select>
              <template v-if="seg.operator === 'range'">
                <el-select v-model="seg.rangeBoundary" size="mini" class="seg-boundary" aria-label="区间边界">
                  <el-option v-for="boundary in rangeBoundaryOptions" :key="boundary" :label="boundary" :value="boundary" />
                </el-select>
                <operand-picker :value="seg.minOperand" :vars="varPickerOptions" :functions="projectFunctions" :selected-vars="selectedVarPickerOptions" :allowed-kinds="valueOperandKinds" placeholder="最小值" width="100%" class="seg-val" @input="value => setSegmentOperand(seg, 'minOperand', value)" />
                <span class="seg-sep">~</span>
                <operand-picker :value="seg.maxOperand" :vars="varPickerOptions" :functions="projectFunctions" :selected-vars="selectedVarPickerOptions" :allowed-kinds="valueOperandKinds" placeholder="最大值" width="100%" class="seg-val" @input="value => setSegmentOperand(seg, 'maxOperand', value)" />
              </template>
              <operand-picker v-else :value="seg.valueOperand" :vars="varPickerOptions" :functions="projectFunctions" :selected-vars="selectedVarPickerOptions" :allowed-kinds="valueOperandKinds" placeholder="选择值或字段" width="100%" class="seg-val" @input="value => setSegmentOperand(seg, 'valueOperand', value)" />
              <el-input v-model="seg.label" size="mini" placeholder="标签" class="seg-label" />
              <el-button type="text" size="mini" icon="el-icon-close" style="color:#ccc;" @click="dim.segments.splice(si, 1)" />
            </div>
            <el-button type="text" size="mini" icon="el-icon-plus" @click="addSegment(dim)">添加分段</el-button>
          </div>
        </div>
      </div>

      <!-- 列维度 -->
      <div class="act-dim-panel">
        <div class="dim-panel-header">
          <i class="el-icon-s-fold" style="color:#52c41a;" /> 列维度
          <el-button size="mini" icon="el-icon-plus" @click="addDimension('col')">添加列维度</el-button>
        </div>
        <div v-for="(dim, di) in model.colDimensions" :key="'cd-' + di" class="dim-config-card">
          <div class="dim-config-header">
            <operand-picker
              :vars="varPickerOptions"
              :functions="projectFunctions"
              :selected-vars="selectedVarPickerOptions"
              :value="dim.operand"
              :allowed-kinds="readOperandKinds"
              placeholder="选择维度字段或路径"
              width="100%"
              class="dim-field-var"
              @input="value => setDimensionOperand(dim, value)"
            />
            <el-input v-model="dim.varLabel" size="mini" placeholder="维度名称" class="dim-field-label" />
            <el-select v-model="dim.varType" size="mini" class="dim-field-type" popper-append-to-body>
              <el-option v-for="opt in varTypeFormOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
            <el-button type="text" size="mini" icon="el-icon-delete" style="color:#F76E6C;" @click="removeDimension('col', di)" />
          </div>
          <div class="segments-area">
            <div v-for="(seg, si) in dim.segments" :key="si" class="segment-row">
              <el-select v-model="seg.operator" size="mini" class="seg-op" @change="onSegmentOperatorChange(seg)">
                <el-option label="等于" value="==" /><el-option label="不等于" value="!=" />
                <el-option label="大于" value=">" /><el-option label="大于等于" value=">=" />
                <el-option label="小于" value="<" /><el-option label="小于等于" value="<=" />
                <el-option label="区间" value="range" />
              </el-select>
              <template v-if="seg.operator === 'range'">
                <el-select v-model="seg.rangeBoundary" size="mini" class="seg-boundary" aria-label="区间边界">
                  <el-option v-for="boundary in rangeBoundaryOptions" :key="boundary" :label="boundary" :value="boundary" />
                </el-select>
                <operand-picker :value="seg.minOperand" :vars="varPickerOptions" :functions="projectFunctions" :selected-vars="selectedVarPickerOptions" :allowed-kinds="valueOperandKinds" placeholder="最小值" width="100%" class="seg-val" @input="value => setSegmentOperand(seg, 'minOperand', value)" />
                <span class="seg-sep">~</span>
                <operand-picker :value="seg.maxOperand" :vars="varPickerOptions" :functions="projectFunctions" :selected-vars="selectedVarPickerOptions" :allowed-kinds="valueOperandKinds" placeholder="最大值" width="100%" class="seg-val" @input="value => setSegmentOperand(seg, 'maxOperand', value)" />
              </template>
              <operand-picker v-else :value="seg.valueOperand" :vars="varPickerOptions" :functions="projectFunctions" :selected-vars="selectedVarPickerOptions" :allowed-kinds="valueOperandKinds" placeholder="选择值或字段" width="100%" class="seg-val" @input="value => setSegmentOperand(seg, 'valueOperand', value)" />
              <el-input v-model="seg.label" size="mini" placeholder="标签" class="seg-label" />
              <el-button type="text" size="mini" icon="el-icon-close" style="color:#ccc;" @click="dim.segments.splice(si, 1)" />
            </div>
            <el-button type="text" size="mini" icon="el-icon-plus" @click="addSegment(dim)">添加分段</el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 结果变量：独立一行 -->
    <div class="act-result-row">
      <div class="dim-panel-header">
        <i class="el-icon-finished" style="color:#fa8c16;" /> 结果变量
      </div>
      <div class="result-config">
        <operand-picker
          :vars="varPickerOptions"
          :functions="projectFunctions"
          :selected-vars="selectedVarPickerOptions"
          :value="model.resultVar.operand"
          :allowed-kinds="writeOperandKinds"
          writable-only
          placeholder="选择结果字段或手输路径"
          width="100%"
          class="result-field-var"
          @input="value => setDimensionOperand(model.resultVar, value)"
        />
        <el-input v-model="model.resultVar.varLabel" size="mini" placeholder="结果名称" class="result-field-label" />
        <el-select v-model="model.resultVar.varType" size="mini" class="result-field-type" popper-append-to-body>
          <el-option v-for="opt in varTypeFormOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </div>
    </div>

    <!-- 交叉矩阵 -->
    <div class="act-card" v-if="totalRowCount > 0 && totalColCount > 0">
      <div class="act-card-title"><i class="el-icon-s-grid" /> 交叉矩阵（{{ totalRowCount }} × {{ totalColCount }}）</div>
      <div class="act-matrix-wrap">
        <table class="act-matrix">
          <thead>
            <!-- 多级列表头：每个列维度一行 -->
            <tr v-for="(headerRow, level) in colHeaderRows" :key="'ch-level-' + level">
              <!-- 左上角单元格仅在第一行显示 -->
              <th
                v-if="level === 0"
                class="corner-cell"
                :rowspan="colDimLevels"
                :colspan="rowDimLevels"
              >
                <div class="corner-inner">
                  <div class="corner-row-label">{{ rowDimLabel }}</div>
                  <div class="corner-divider" />
                  <div class="corner-col-label">{{ colDimLabel }}</div>
                </div>
              </th>
              <th
                v-for="(cell, ci) in headerRow"
                :key="'ch-' + level + '-' + ci"
                :colspan="cell.colspan"
                class="col-header-cell"
                :class="{ 'col-header-top': level === 0, 'col-header-bottom': level === colDimLevels - 1 }"
              >
                {{ cell.label }}
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(rowCombo, ri) in rowCombinations" :key="'row-' + ri">
              <!-- 多级行表头：每个行维度一列，使用 rowspan 合并 -->
              <td
                v-for="cell in rowHeaderCells[ri]"
                :key="'rh-' + ri + '-' + cell.level"
                :rowspan="cell.rowspan"
                class="row-header-cell"
                :class="{ 'row-header-first': cell.level === 0 }"
              >
                {{ cell.label }}
              </td>
              <td
                v-for="(colCombo, ci) in colCombinations"
                :key="'cell-' + ri + '-' + ci"
                class="data-cell"
              >
                <operand-picker
                  :value="cellData[ri][ci]"
                  :vars="varPickerOptions"
                  :functions="projectFunctions"
                  :selected-vars="selectedVarPickerOptions"
                  :allowed-kinds="valueOperandKinds"
                  placeholder="选择结果或手输阈值"
                  width="100%"
                  class="cell-input"
                  @input="value => setCellOperand(ri, ci, value)"
                />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 脚本预览 -->
    <script-panel
      v-if="definitionId"
      ref="scriptPanel"
      :definitionId="definitionId"
      :onBeforeCompile="handleSave"
      @mode-change="mode => scriptMode = mode"
    />

    <!-- 测试弹窗 -->
        <designer-test-dialog
      :visible.sync="testVisible"
      :definition-id="definitionId"
      :project-id="projectIdForRefs"
      model-type="CROSS_ADV"
      :model-json-provider="buildSaveModel"
      :params-template="testParamsTemplate"
    />
  </div>
</template>

<script>
import { saveContent, compileRule, executeRule, getContent, refreshFields } from '@/api/definition'
import { VAR_TYPE_FORM_OPTIONS } from '@/constants/varTypes'
import varPickerMixin from '@/mixins/varPickerMixin'
import OperandPicker from '@/components/common/OperandPicker.vue'
import ScriptPanel from '@/components/common/ScriptPanel.vue'
import DesignerTestDialog from '@/components/common/DesignerTestDialog.vue'
import { addCode, buildSampleParamsFromCodes, coerceSampleValue, isLeafRef, setParamPath } from '@/utils/testSampleParams'
import { isSuccessResult, resultErrorMessage } from '@/utils/apiResponse'
import { collectOperandReferences, compileOperand, createLiteralOperand, operandDisplay, operandFromReferenceFields, syncOperandReference } from '@/utils/operand'
import { getExpressionContext } from '@/constants/expressionContexts'

const RANGE_BOUNDARIES = ['[)', '()', '[]', '(]']

export default {
  name: 'AdvancedCrossTable',
  components: { DesignerTestDialog, OperandPicker, ScriptPanel },
  mixins: [varPickerMixin],
  data() {
    return {
      definitionId: null,
      contentLoaded: false,
      model: {
        rowDimensions: [],
        colDimensions: [],
        resultVar: { varCode: '', varLabel: '', varType: 'NUMBER', _varId: null },
        cells: []
      },
      cellData: [],
      scriptMode: 'visual',
      testVisible: false,
      testParamsTemplate: {},
      testParamsJson: '{}',
      testResult: null,
      varTypeFormOptions: VAR_TYPE_FORM_OPTIONS,
      rangeBoundaryOptions: RANGE_BOUNDARIES,
      readOperandKinds: getExpressionContext('READ_EXPRESSION').allowedKinds,
      writeOperandKinds: getExpressionContext('WRITE_TARGET').allowedKinds,
      valueOperandKinds: getExpressionContext('READ_EXPRESSION').allowedKinds
    }
  },
  computed: {
    rowCombinations() { return this.cartesianProduct(this.model.rowDimensions) },
    colCombinations() { return this.cartesianProduct(this.model.colDimensions) },
    totalRowCount() { return this.rowCombinations.length },
    totalColCount() { return this.colCombinations.length },
    /** 列维度层级数 */
    colDimLevels() { return Math.max(1, (this.model.colDimensions || []).length) },
    /** 行维度层级数 */
    rowDimLevels() { return Math.max(1, (this.model.rowDimensions || []).length) },
    /** 行维度名称（拼接所有维度标签） */
    rowDimLabel() {
      return (this.model.rowDimensions || []).map(d => d.varLabel || d.varCode || '').join(' / ') || '行'
    },
    /** 列维度名称 */
    colDimLabel() {
      return (this.model.colDimensions || []).map(d => d.varLabel || d.varCode || '').join(' / ') || '列'
    },
    /**
     * 多级列表头行数组，每一级是一个 [{label, colspan}] 数组。
     * 例如 2 个列维度（客户类型3项 × 纳税人2项），生成2行：
     *   Level 0: [{label:'企业',colspan:2},{label:'个人',colspan:2},{label:'超企',colspan:2}]
     *   Level 1: [{label:'一般',colspan:1},{label:'小规模',colspan:1},...重复3次]
     */
    colHeaderRows() {
      const dims = this.model.colDimensions || []
      if (dims.length === 0) return []
      const rows = []
      for (let level = 0; level < dims.length; level++) {
        const cells = []
        let colspan = 1
        for (let l = level + 1; l < dims.length; l++) {
          colspan *= (dims[l].segments || []).length || 1
        }
        let repeat = 1
        for (let l = 0; l < level; l++) {
          repeat *= (dims[l].segments || []).length || 1
        }
        const segs = dims[level].segments || []
        for (let r = 0; r < repeat; r++) {
          for (const seg of segs) {
            cells.push({ label: seg.label || operandDisplay(seg.valueOperand) || '-', colspan })
          }
        }
        rows.push(cells)
      }
      return rows
    },
    /**
     * 多级行表头。为每个数据行返回需要渲染的 td 列表（含 rowspan）。
     * 仅在某分组首行显示该维度的 td，其余行省略（靠 rowspan 合并）。
     */
    rowHeaderCells() {
      const dims = this.model.rowDimensions || []
      if (dims.length === 0) return []
      const totalRows = this.totalRowCount
      const result = []
      for (let ri = 0; ri < totalRows; ri++) {
        const cells = []
        for (let level = 0; level < dims.length; level++) {
          let rowspan = 1
          for (let l = level + 1; l < dims.length; l++) {
            rowspan *= (dims[l].segments || []).length || 1
          }
          if (ri % rowspan === 0) {
            const combo = this.rowCombinations[ri]
            cells.push({
              label: combo && combo[level] ? (combo[level].label || operandDisplay(combo[level].valueOperand) || '-') : '-',
              rowspan,
              level
            })
          }
        }
        result.push(cells)
      }
      return result
    }
  },
  watch: {
    totalRowCount() { this.syncCellData() },
    totalColCount() { this.syncCellData() }
  },
  created() {
    this.definitionId = this.$route.params.id
    this.loadContent()
  },
  methods: {
    collectSelectedVarItems() {
      const items = []
      const add = operand => collectOperandReferences(operand).forEach(reference => items.push({ varCode: reference.code, varType: reference.valueType, _varId: reference.refId, _refType: reference.refType }))
      add(this.model.resultVar && this.model.resultVar.operand)
      ;[...(this.model.rowDimensions || []), ...(this.model.colDimensions || [])].forEach(dim => {
        add(dim.operand)
        ;(dim.segments || []).forEach(segment => { add(segment.valueOperand); add(segment.minOperand); add(segment.maxOperand) })
      })
      this.cellData.forEach(row => row.forEach(add))
      return items
    },
    cartesianProduct(dimensions) {
      if (!dimensions || dimensions.length === 0) return []
      let result = [[]]
      for (const dim of dimensions) {
        const segs = dim.segments || []
        if (segs.length === 0) return []
        const newResult = []
        for (const existing of result) {
          for (const seg of segs) {
            newResult.push([...existing, seg])
          }
        }
        result = newResult
      }
      return result
    },
    syncCellData() {
      const rows = this.totalRowCount
      const cols = this.totalColCount
      if (rows === 0 || cols === 0) { this.cellData = []; return }
      const newData = []
      for (let r = 0; r < rows; r++) {
        const row = []
        for (let c = 0; c < cols; c++) {
          row.push(this.cellData[r] && this.cellData[r][c] != null ? this.cellData[r][c] : createLiteralOperand('', this.model.resultVar.varType || 'STRING'))
        }
        newData.push(row)
      }
      this.cellData = newData
    },
    async loadContent() {
      try {
        const res = await getContent(this.definitionId)
        const content = res && res.data ? res.data : res
        if (content && content.modelJson && content.modelJson !== '{}') {
          const parsed = JSON.parse(content.modelJson)
          this.model = parsed
          if (parsed.cells) {
            this.cellData = this.flattenCells(parsed.cells)
          }
        }
      } catch (e) {
        this.$message.error('加载内容失败: ' + (e.message || '未知错误'))
      } finally {
        this.normalizeModel()
        this._syncModelVarRefs()
        this.syncCellData()
        this.contentLoaded = true
      }
    },
    flattenCells(cells) {
      if (!Array.isArray(cells)) return []
      return cells.map(row => {
        if (!Array.isArray(row)) return []
        return row.map(cell => {
          if (Array.isArray(cell)) cell = cell[0]
          if (cell && cell.kind) return cell
          return createLiteralOperand(cell != null ? cell : '', this.model.resultVar && this.model.resultVar.varType || 'STRING')
        })
      })
    },
    normalizeModel() {
      if (!this.model.rowDimensions) this.$set(this.model, 'rowDimensions', [])
      if (!this.model.colDimensions) this.$set(this.model, 'colDimensions', [])
      if (!this.model.resultVar) this.$set(this.model, 'resultVar', { varCode: '', varLabel: '', varType: 'NUMBER', _varId: null })
      if (!this.model.resultVar.operand) this.$set(this.model.resultVar, 'operand', operandFromReferenceFields(this.model.resultVar))
      ;[...(this.model.rowDimensions || []), ...(this.model.colDimensions || [])].forEach(dim => {
        if (!dim.operand) this.$set(dim, 'operand', operandFromReferenceFields(dim))
        ;(dim.segments || []).forEach(segment => {
          if (segment.operator === 'range' && !RANGE_BOUNDARIES.includes(segment.rangeBoundary)) this.$set(segment, 'rangeBoundary', '[)')
          if (!segment.valueOperand) this.$set(segment, 'valueOperand', createLiteralOperand(segment.value, dim.varType || 'STRING'))
          if (!segment.minOperand) this.$set(segment, 'minOperand', createLiteralOperand(segment.min, dim.varType || 'STRING'))
          if (!segment.maxOperand) this.$set(segment, 'maxOperand', createLiteralOperand(segment.max, dim.varType || 'STRING'))
        })
      })
    },
    /** 根据 modelJson 中已有的 _varId 同步填充变量元信息 */
    _syncModelVarRefs() {
      const refs = this.projectRefs || []
      const findRef = (varId, refType) => {
        if (varId == null) return null
        return refs.find(r => r.varObj && String(r.varObj.id) === String(varId) && (!refType || r.refType === refType)) || null
      }
      const fillRef = (v) => {
        if (!v) return
        const ref = findRef(v._varId, v._refType)
        if (ref) {
          v.varCode = ref.refCode
          v.varLabel = ref.refLabel.label + ' ' + ref.refLabel.code
          v.varType = ref.varType
          v._refType = ref.refType
        }
      }
      if (this.model.resultVar) fillRef(this.model.resultVar)
      ;(this.model.rowDimensions || []).forEach(d => fillRef(d))
      ;(this.model.colDimensions || []).forEach(d => fillRef(d))
      this.syncAllOperands()
    },
    setDimensionOperand(dim, value) {
      this.$set(dim, 'operand', value)
      dim.varCode = compileOperand(value)
      dim.varLabel = value && (value.label || value.code || value.value) || ''
      dim.varType = value && value.valueType || dim.varType || 'STRING'
      dim._varId = value && value.refId != null ? value.refId : null
      dim._refType = value && value.refType || null
      if (dim.varType === 'ENUM') {
        const options = this.getVarOptions(dim.varCode)
        if (options.length > 0) {
          dim.segments = options.map(o => ({ label: o.label || o.value, operator: '==', rangeBoundary: '[)', value: o.value, valueOperand: createLiteralOperand(o.value, 'STRING') }))
        }
      }
    },
    setSegmentOperand(segment, field, value) {
      this.$set(segment, field, value)
      const scalar = field === 'valueOperand' ? 'value' : field === 'minOperand' ? 'min' : 'max'
      segment[scalar] = compileOperand(value)
    },
    onSegmentOperatorChange(segment) {
      if (segment.operator !== 'range' || RANGE_BOUNDARIES.includes(segment.rangeBoundary)) return
      if (this.$set) this.$set(segment, 'rangeBoundary', '[)')
      else segment.rangeBoundary = '[)'
    },
    setCellOperand(row, col, value) {
      this.$set(this.cellData[row], col, value)
    },
    syncAllOperands() {
      const sync = (holder, field, setter) => {
        if (!holder || !holder[field]) return
        const result = syncOperandReference(holder[field], this.varPickerOptions)
        if (result.changed) setter(result.operand)
      }
      sync(this.model.resultVar, 'operand', value => this.setDimensionOperand(this.model.resultVar, value))
      ;[...(this.model.rowDimensions || []), ...(this.model.colDimensions || [])].forEach(dim => {
        sync(dim, 'operand', value => this.setDimensionOperand(dim, value))
        ;(dim.segments || []).forEach(segment => ['valueOperand', 'minOperand', 'maxOperand'].forEach(field => sync(segment, field, value => this.setSegmentOperand(segment, field, value))))
      })
      this.cellData.forEach((row, ri) => row.forEach((operand, ci) => sync(row, ci, value => this.setCellOperand(ri, ci, value))))
    },
    addDimension(type) {
      const dims = type === 'row' ? this.model.rowDimensions : this.model.colDimensions
      dims.push({ varCode: '', varLabel: '', varType: 'STRING', _varId: null, operand: null, segments: [{ label: '', operator: '==', rangeBoundary: '[)', value: '', valueOperand: createLiteralOperand('', 'STRING') }] })
    },
    removeDimension(type, index) {
      const dims = type === 'row' ? this.model.rowDimensions : this.model.colDimensions
      dims.splice(index, 1)
    },
    addSegment(dim) {
      dim.segments.push({ label: '', operator: '==', rangeBoundary: '[)', value: '', valueOperand: createLiteralOperand('', dim.varType || 'STRING') })
    },
    buildSaveModel() {
      const saveModel = JSON.parse(JSON.stringify(this.model))
      saveModel.cells = JSON.parse(JSON.stringify(this.cellData))
      return saveModel
    },
    async handleSave() {
      const saveModel = this.buildSaveModel()
      await saveContent({ definitionId: this.definitionId, modelJson: JSON.stringify(saveModel) })
      await refreshFields(this.definitionId, JSON.stringify(saveModel))
      this.refreshProjectRefs()

      this.$message.success('保存成功')
    },
    async handleCompile() {
      await this.handleSave()
      const res = await compileRule(this.definitionId)
      if (isSuccessResult(res)) {
        this.$message.success('编译成功')
        if (this.$refs.scriptPanel) this.$refs.scriptPanel.refresh()
      } else {
        this.$message.error('编译失败: ' + resultErrorMessage(res))
      }
    },
    handleTest() {
      this.testParamsTemplate = this.buildTestParamsTemplate()
      this.testParamsJson = JSON.stringify(this.buildTestParamsTemplate(), null, 2)
      this.testResult = null
      this.testVisible = true
    },
    buildTestParamsTemplate() {
      const codes = new Set()
      const rowDimensions = this.model.rowDimensions || []
      const colDimensions = this.model.colDimensions || []
      ;[...rowDimensions, ...colDimensions].forEach(dim => {
        addCode(codes, dim.varCode)
        collectOperandReferences(dim.operand).forEach(reference => addCode(codes, reference.code || reference.path))
        ;(dim.segments || []).forEach(segment => {
          ['valueOperand', 'minOperand', 'maxOperand'].forEach(field => {
            collectOperandReferences(segment[field]).forEach(reference => addCode(codes, reference.code || reference.path))
          })
        })
      })
      const params = buildSampleParamsFromCodes(Array.from(codes), this.projectRefs)
      ;[...rowDimensions, ...colDimensions].forEach(dim => {
        const segment = (dim.segments || []).find(item => item && (
          item.valueOperand && item.valueOperand.kind === 'LITERAL' && item.valueOperand.value !== '' ||
          !item.valueOperand && item.value !== undefined && item.value !== ''
        ))
        if (!dim.varCode || !segment) return
        const ref = this.projectRefs.find(r => r.refCode === dim.varCode)
        if (ref && !isLeafRef(ref)) return
        const sampleValue = segment.valueOperand ? segment.valueOperand.value : segment.value
        setParamPath(params, dim.varCode, coerceSampleValue(sampleValue, ref))
      })
      return params
    },
    async doTest() {
      let params = {}
      try { params = JSON.parse(this.testParamsJson || '{}') } catch (e) {
        this.$message.error('参数 JSON 格式错误')
        return
      }
      const res = await executeRule({ definitionId: this.definitionId, params })
      this.testResult = res && res.data ? res.data : res
    }
  }
}
</script>

<style lang="scss" scoped>
.act-designer {
  background: #f3f3f3;
  padding: 20px;
  min-height: 100%;
}
.act-header {
  display: flex; align-items: center; justify-content: space-between;
  background: #fff; border-radius: 4px; padding: 14px 20px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1); margin-bottom: 16px;
  flex-wrap: wrap; gap: 8px;
}
.act-title-area { display: flex; align-items: center; }
.act-title-icon { font-size: 18px; color: #722ed1; margin-right: 8px; }
.act-title { font-size: 16px; font-weight: bold; color: #282828; }
.act-toolbar { display: flex; align-items: center; gap: 6px; }

/* 维度配置：行+列并排 */
.act-dim-row {
  display: flex; gap: 16px; margin-bottom: 16px;
}
.act-dim-panel {
  flex: 1; min-width: 0; background: #fff; border-radius: 6px;
  padding: 12px 16px; box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}

/* 结果变量：独立一行 */
.act-result-row {
  background: #fff; border-radius: 6px;
  padding: 12px 16px; box-shadow: 0 1px 3px rgba(0,0,0,0.1);
  margin-bottom: 16px;
}
.result-config {
  display: flex; align-items: center; gap: 8px; padding: 8px 0;
}
.result-field-var { flex: 3; min-width: 0; }
.result-field-label { flex: 2; min-width: 0; }
.result-field-type { flex: 1; min-width: 100px; }

.dim-panel-header {
  display: flex; align-items: center; justify-content: space-between;
  font-size: 13px; font-weight: 600; color: #555; margin-bottom: 10px;
  gap: 8px;
}

/* 维度卡片内：表头自适应 */
.dim-config-card {
  border: 1px solid #e8e8e8; border-radius: 4px; margin-bottom: 10px; padding: 8px;
}
.dim-config-header {
  display: flex; align-items: center; gap: 6px; margin-bottom: 6px;
}
.dim-field-var { flex: 3; min-width: 0; }
.dim-field-label { flex: 2; min-width: 0; }
.dim-field-type { flex: 1; min-width: 100px; }

/* 分段条件行自适应 */
.segments-area { padding-left: 4px; }
.segment-row {
  display: flex; align-items: center; gap: 6px; margin-bottom: 6px;
}
.seg-op { flex: 2; min-width: 80px; }
.seg-boundary { flex: 0 0 64px; }
.seg-val { flex: 2; min-width: 0; }
.seg-label { flex: 3; min-width: 0; }
.seg-sep { color: #999; flex-shrink: 0; }

/* 矩阵 */
.act-card {
  background: #fff; border-radius: 4px; padding: 16px 20px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1); margin-bottom: 16px;
}
.act-card-title {
  font-size: 14px; font-weight: bold; color: #333; margin-bottom: 14px;
  display: flex; align-items: center; gap: 6px;
  i { color: #722ed1; }
}
.act-matrix-wrap { overflow-x: auto; border-radius: 6px; border: 1px solid #e8e8e8; }
.act-matrix {
  border-collapse: collapse; width: 100%;
  th, td { border: 1px solid #e8e8e8; padding: 6px; vertical-align: middle; text-align: center; }
}
.corner-cell {
  background: #f5f5f5; min-width: 100px; position: relative; padding: 0; min-height: 56px;
}
.corner-inner { position: relative; width: 100%; height: 56px; }
.corner-row-label {
  position: absolute; bottom: 6px; left: 8px; font-size: 11px; color: #888;
}
.corner-col-label {
  position: absolute; top: 6px; right: 8px; font-size: 11px; color: #888;
}
.corner-divider {
  position: absolute; top: 0; left: 0; right: 0; bottom: 0;
  background: linear-gradient(to bottom right, transparent calc(50% - 0.5px), #d0d0d0, transparent calc(50% + 0.5px));
  pointer-events: none;
}
.col-header-cell {
  background: #e8f3ff; min-width: 90px; font-size: 12px; font-weight: 600; color: #333;
  white-space: nowrap;
}
.col-header-top { background: #daeeff; }
.row-header-cell {
  background: #f0fff4; min-width: 80px; font-size: 12px; font-weight: 500; color: #333;
  white-space: nowrap;
}
.row-header-first { background: #e0f5e9; font-weight: 600; }
.data-cell { background: #fff; min-width: 90px; }
.cell-input ::v-deep input { text-align: center; font-weight: 500; }

.test-hint { font-size: 12px; color: #909399; margin-bottom: 8px; }
.test-result { margin-top: 16px; }
</style>
