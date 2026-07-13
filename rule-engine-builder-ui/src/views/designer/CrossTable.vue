<template>
  <div class="ct-designer">
    <!-- 顶部工具栏 -->
    <div class="ct-header">
      <div class="ct-title-area">
        <el-button type="text" icon="el-icon-back" @click="$router.back()" style="color:#606266;" />
        <i class="el-icon-data-analysis ct-title-icon" />
        <span class="ct-title">交叉表设计器</span>
        <el-tag size="mini" type="info" style="margin-left:8px;">
          {{ model.rowHeaders.length }} 行 × {{ model.colHeaders.length }} 列
        </el-tag>
      </div>
      <div class="ct-toolbar">
        <el-button-group>
          <el-button size="small" icon="el-icon-plus" @click="addRow">添加行</el-button>
          <el-button size="small" icon="el-icon-plus" @click="addColumn">添加列</el-button>
        </el-button-group>
        <el-divider direction="vertical" />
        <el-button size="small" icon="el-icon-document" @click="handleSave">临时保存配置</el-button>
        <el-button size="small" type="warning" icon="el-icon-cpu" @click="handleCompile">保存并编译</el-button>
        <el-button size="small" type="primary" icon="el-icon-video-play" @click="handleTest">编译后测试</el-button>
      </div>
    </div>

    <!-- 维度变量定义 -->
    <div class="ct-dim-panel">
      <div class="ct-dim-card">
        <div class="dim-label"><i class="el-icon-s-unfold dim-icon row-icon" /> 行维度</div>
        <operand-picker
          :vars="varPickerOptions"
          :selected-vars="selectedVarPickerOptions"
          :value="model.rowVar.operand"
          :allowed-kinds="readOperandKinds"
          placeholder="选择行维度字段或路径"
          style="margin-bottom:6px;"
          @input="operand => setDimOperand('rowVar', operand)"
        />
        <el-input v-model="model.rowVar.varLabel" size="small" placeholder="中文名称（如 纳税人类型）" style="margin-bottom:6px;" />
      </div>
      <div class="ct-dim-cross">
        <div class="cross-label">×</div>
        <div class="cross-desc">{{ model.resultVar.varLabel || '结果值' }}</div>
      </div>
      <div class="ct-dim-card">
        <div class="dim-label"><i class="el-icon-s-fold dim-icon col-icon" /> 列维度</div>
        <operand-picker
          :vars="varPickerOptions"
          :selected-vars="selectedVarPickerOptions"
          :value="model.colVar.operand"
          :allowed-kinds="readOperandKinds"
          placeholder="选择列维度字段或路径"
          style="margin-bottom:6px;"
          @input="operand => setDimOperand('colVar', operand)"
        />
        <el-input v-model="model.colVar.varLabel" size="small" placeholder="中文名称（如 货物类别）" style="margin-bottom:6px;" />
      </div>
      <div class="ct-dim-card">
        <div class="dim-label"><i class="el-icon-finished dim-icon result-icon" /> 结果变量</div>
        <operand-picker
          :vars="varPickerOptions"
          :selected-vars="selectedVarPickerOptions"
          :value="model.resultVar.operand"
          :allowed-kinds="writeOperandKinds"
          writable-only
          placeholder="选择结果字段或路径"
          style="margin-bottom:6px;"
          @input="operand => setDimOperand('resultVar', operand)"
        />
        <el-input v-model="model.resultVar.varLabel" size="small" placeholder="中文名称（如 适用税率）" style="margin-bottom:6px;" />
        <el-select v-model="model.resultVar.varType" size="small" style="width:100%;margin-top:6px;" popper-append-to-body>
          <el-option v-for="opt in varTypeFormOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </div>
    </div>

    <!-- 交叉矩阵 -->
    <div class="ct-matrix-wrap">
      <table class="ct-matrix">
        <colgroup>
          <col class="col-row-header" />
          <col v-for="(col, ci) in model.colHeaders" :key="'col-' + ci" class="col-data" />
          <col class="col-action" />
        </colgroup>
        <thead>
          <tr>
            <!-- 左上角交叉单元格 -->
            <th class="corner-cell">
              <div class="corner-row">{{ model.rowVar.varLabel || '行' }}</div>
              <div class="corner-divider" />
              <div class="corner-col">{{ model.colVar.varLabel || '列' }}</div>
            </th>
            <!-- 列头单元格 -->
            <th v-for="(col, ci) in model.colHeaders" :key="'ch-' + ci" class="col-header-cell">
              <div class="header-cell-inner">
                <operand-picker
                  :value="model.colHeaderOperands[ci]"
                  :vars="varPickerOptions"
                  :functions="projectFunctions"
                  :allowed-kinds="valueOperandKinds"
                  :expected-type="model.colVar.varType"
                  size="mini"
                  placeholder="选择列值"
                  class="header-input"
                  @input="operand => setHeaderOperand('col', ci, operand)"
                />
                <el-tooltip content="删除此列" placement="top">
                  <el-button
                    type="text"
                    size="mini"
                    icon="el-icon-close"
                    class="delete-col-btn"
                    @click="removeColumn(ci)"
                  />
                </el-tooltip>
              </div>
            </th>
            <!-- 添加列按钮 -->
            <th class="add-col-cell">
              <el-button type="text" size="mini" icon="el-icon-plus" @click="addColumn" style="color:#1890ff;" />
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, ri) in model.rowHeaders" :key="'row-' + ri">
            <!-- 行头单元格 -->
            <td class="row-header-cell">
              <div class="row-header-inner">
                <operand-picker
                  :value="model.rowHeaderOperands[ri]"
                  :vars="varPickerOptions"
                  :functions="projectFunctions"
                  :allowed-kinds="valueOperandKinds"
                  :expected-type="model.rowVar.varType"
                  size="mini"
                  placeholder="选择行值"
                  class="header-input"
                  @input="operand => setHeaderOperand('row', ri, operand)"
                />
                <el-tooltip content="删除此行" placement="right">
                  <el-button
                    type="text"
                    size="mini"
                    icon="el-icon-close"
                    class="delete-row-btn"
                    @click="removeRow(ri)"
                  />
                </el-tooltip>
              </div>
            </td>
            <!-- 数据单元格 -->
            <td
              v-for="(col, ci) in model.colHeaders"
              :key="'cell-' + ri + '-' + ci"
              :class="['data-cell', { 'cell-filled': isCellFilled(ri, ci), 'cell-focused': focusedCell === ri + '_' + ci }]"
            >
              <operand-picker
                :value="model.cellOperands[ri][ci]"
                :vars="varPickerOptions"
                :functions="projectFunctions"
                :allowed-kinds="valueOperandKinds"
                :expected-type="model.resultVar.varType"
                size="mini"
                placeholder="选择结果值"
                class="cell-input"
                @input="operand => setCellOperand(ri, ci, operand)"
              />
            </td>
            <!-- 行操作 -->
            <td class="add-row-cell" />
          </tr>
          <!-- 添加行按钮行 -->
          <tr>
            <td class="add-row-trigger" @click="addRow">
              <el-button type="text" size="mini" icon="el-icon-plus" style="color:#1890ff;">添加行</el-button>
            </td>
            <td v-for="(col, ci) in model.colHeaders" :key="'add-' + ci" class="add-row-trigger" @click="addRow" />
            <td class="add-row-trigger" />
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 说明预览 -->
    <div class="ct-preview">
      <i class="el-icon-info preview-icon" />
      <span>查询逻辑：当 <strong>{{ model.rowVar.varCode || '行变量' }}</strong> = [行值] 且 <strong>{{ model.colVar.varCode || '列变量' }}</strong> = [列值] 时，输出 <strong>{{ model.resultVar.varCode || '结果变量' }}</strong> = [对应单元格值]</span>
    </div>

    <!-- 脚本预览/编辑面板 -->
    <script-panel
      v-if="definitionId"
      ref="scriptPanel"
      :definitionId="definitionId"
      :onBeforeCompile="handleSave"
      @mode-change="mode => scriptMode = mode"
    />
    <div v-if="scriptMode === 'script'" class="script-override-banner">
      <i class="el-icon-warning" /> 脚本覆盖模式已激活，可视化编辑暂停。
    </div>

    <designer-test-dialog
      :visible.sync="testVisible"
      :definition-id="definitionId"
      :project-id="projectIdForRefs"
      model-type="CROSS"
      :model-json="model"
      :params-template="testParamsTemplate"
    />
  </div>
</template>

<script>
import { saveContent, compileRule, getContent, refreshFields } from '@/api/definition'
import { VAR_TYPE_FORM_OPTIONS } from '@/constants/varTypes'
import varPickerMixin from '@/mixins/varPickerMixin'
import DesignerTestDialog from '@/components/common/DesignerTestDialog.vue'
import OperandPicker from '@/components/common/OperandPicker.vue'
import ScriptPanel from '@/components/common/ScriptPanel.vue'
import { isSuccessResult, resultErrorMessage } from '@/utils/apiResponse'
import { collectOperandReferences, compileOperand, createLiteralOperand, operandFromReferenceFields, syncOperandReference } from '@/utils/operand'

export default {
  name: 'CrossTable',
  components: { DesignerTestDialog, OperandPicker, ScriptPanel },
  mixins: [varPickerMixin],
  data() {
    return {
      definitionId: null,
      contentLoaded: false,
      model: {
        rowVar: { varCode: '', varLabel: '', varType: 'STRING' },
        colVar: { varCode: '', varLabel: '', varType: 'STRING' },
        resultVar: { varCode: '', varLabel: '', varType: 'NUMBER' },
        rowHeaders: [''],
        colHeaders: [''],
        cells: [['']],
        rowHeaderOperands: [null],
        colHeaderOperands: [null],
        cellOperands: [[null]]
      },
      readOperandKinds: ['PATH', 'REFERENCE', 'FUNCTION'],
      writeOperandKinds: ['PATH', 'REFERENCE'],
      valueOperandKinds: ['LITERAL', 'PATH', 'REFERENCE', 'FUNCTION'],
      focusedCell: null,
      scriptMode: 'visual',
      testVisible: false,
      testParamsTemplate: {},
      varTypeFormOptions: VAR_TYPE_FORM_OPTIONS
    }
  },
  created() {
    this.definitionId = this.$route.params.id
    this.loadContent()
  },
  methods: {
    collectSelectedVarItems() {
      const items = []
      const add = operand => collectOperandReferences(operand).forEach(reference => items.push({ varCode: reference.code, _varId: reference.refId, _refType: reference.refType, varType: reference.valueType }))
      ;['rowVar', 'colVar', 'resultVar'].forEach(key => add(this.model[key] && this.model[key].operand))
      ;(this.model.rowHeaderOperands || []).forEach(add)
      ;(this.model.colHeaderOperands || []).forEach(add)
      ;(this.model.cellOperands || []).forEach(row => row.forEach(add))
      return items
    },
    setDimOperand(dimKey, operand) {
      const dim = this.model[dimKey]
      this.$set(dim, 'operand', operand || null)
      this.$set(dim, 'varCode', operand ? (operand.code || operand.value || '') : '')
      this.$set(dim, 'varLabel', operand ? (operand.label || operand.code || operand.value || '') : '')
      this.$set(dim, 'varType', (operand && operand.valueType) || dim.varType || 'STRING')
      this.$set(dim, '_varId', operand && operand.refId != null ? operand.refId : null)
      this.$set(dim, '_refType', (operand && operand.refType) || '')
    },
    setHeaderOperand(axis, index, operand) {
      const operands = axis === 'row' ? this.model.rowHeaderOperands : this.model.colHeaderOperands
      const values = axis === 'row' ? this.model.rowHeaders : this.model.colHeaders
      this.$set(operands, index, operand || null)
      this.$set(values, index, operand && operand.kind === 'LITERAL' ? operand.value : compileOperand(operand))
    },
    setCellOperand(row, column, operand) {
      this.$set(this.model.cellOperands[row], column, operand || null)
      this.$set(this.model.cells[row], column, operand && operand.kind === 'LITERAL' ? operand.value : compileOperand(operand))
    },
    applyVarToDim(variable, dimKey) {
      if (!variable) return
      const varLabel = variable.varLabel || variable.varCode
      const _varId = variable._varId || (variable.varObj && variable.varObj.id) || null
      const _refType = variable._refType || variable.refType || (variable.varObj && variable.varObj.refType) || null
      this.model[dimKey] = {
        ...this.model[dimKey],
        varCode: variable.varCode,
        varLabel,
        _varId,
        _refType,
        varType: variable.varType
      }
      // 如果是枚举行/列变量，自动填充表头
      if (variable.varType === 'ENUM') {
        const options = this.getVarOptions(variable.varCode)
        if (options.length > 0) {
          const vals = options.map(o => o.optionValue)
          if (dimKey === 'rowVar') {
            const oldLen = this.model.rowHeaders.length
            this.model.rowHeaders = vals
            this.model.rowHeaderOperands = vals.map(value => createLiteralOperand(value, variable.varType))
            const colCount = this.model.colHeaders.length
            this.model.cells = vals.map((_, ri) =>
              ri < oldLen ? [...(this.model.cells[ri] || []), ...Array(Math.max(0, colCount - (this.model.cells[ri] || []).length)).fill('')]
                          : Array(colCount).fill('')
            )
          } else if (dimKey === 'colVar') {
            this.model.colHeaders = vals
            this.model.colHeaderOperands = vals.map(value => createLiteralOperand(value, variable.varType))
            this.model.cells = this.model.cells.map(row => {
              const newRow = [...row]
              while (newRow.length < vals.length) newRow.push('')
              return newRow.slice(0, vals.length)
            })
          }
        }
      }
    },
    async loadContent() {
      try {
        const res = await getContent(this.definitionId)
        const content = res && res.data ? res.data : res
        if (content && content.modelJson && content.modelJson !== '{}') {
          this.model = JSON.parse(content.modelJson)
        }
      } catch (e) {
        this.$message.error('加载内容失败: ' + (e.message || '未知错误'))
      } finally {
        this.normalizeModel()
        this.contentLoaded = true
        this._trySyncModelVarRefs()
      }
    },
    /** 加载最新变量后，同步 model 中行/列/结果变量的 varCode 和 varLabel */
    _syncModelVarRefs() {
      let changed = false
      ;['rowVar', 'colVar', 'resultVar'].forEach(key => {
        if (!this.model[key]) return
        const result = syncOperandReference(this.model[key].operand, this.varPickerOptions)
        if (result.changed) { this.setDimOperand(key, result.operand); changed = true }
      })
      if (changed) this.$forceUpdate()
    },
    normalizeModel() {
      if (!this.model.rowVar) this.$set(this.model, 'rowVar', { varCode: '', varLabel: '', varType: 'STRING' })
      if (!this.model.colVar) this.$set(this.model, 'colVar', { varCode: '', varLabel: '', varType: 'STRING' })
      if (!this.model.resultVar) this.$set(this.model, 'resultVar', { varCode: '', varLabel: '', varType: 'NUMBER' })
      if (!this.model.rowHeaders) this.$set(this.model, 'rowHeaders', [''])
      if (!this.model.colHeaders) this.$set(this.model, 'colHeaders', [''])
      if (!this.model.cells) this.$set(this.model, 'cells', [['']])
      const rows = this.model.rowHeaders.length
      const cols = this.model.colHeaders.length
      while (this.model.cells.length < rows) {
        this.model.cells.push(Array(cols).fill(''))
      }
      this.model.cells.forEach(row => {
        while (row.length < cols) row.push('')
      })
      ;['rowVar', 'colVar', 'resultVar'].forEach(key => {
        const dim = this.model[key]
        if (!dim.operand) this.$set(dim, 'operand', operandFromReferenceFields(dim))
      })
      if (!Array.isArray(this.model.rowHeaderOperands)) this.$set(this.model, 'rowHeaderOperands', this.model.rowHeaders.map(value => createLiteralOperand(value, this.model.rowVar.varType)))
      if (!Array.isArray(this.model.colHeaderOperands)) this.$set(this.model, 'colHeaderOperands', this.model.colHeaders.map(value => createLiteralOperand(value, this.model.colVar.varType)))
      if (!Array.isArray(this.model.cellOperands)) this.$set(this.model, 'cellOperands', this.model.cells.map(row => row.map(value => createLiteralOperand(value, this.model.resultVar.varType))))
      while (this.model.rowHeaderOperands.length < rows) this.model.rowHeaderOperands.push(null)
      while (this.model.colHeaderOperands.length < cols) this.model.colHeaderOperands.push(null)
      while (this.model.cellOperands.length < rows) this.model.cellOperands.push(Array(cols).fill(null))
      this.model.cellOperands.forEach(row => { while (row.length < cols) row.push(null) })
    },
    isCellFilled(ri, ci) {
      return !!(this.model.cells[ri] && this.model.cells[ri][ci] !== '' && this.model.cells[ri][ci] !== null && this.model.cells[ri][ci] !== undefined)
    },
    addRow() {
      const colCount = Math.max(1, (this.model.colHeaders || []).length)
      this.model.rowHeaders.push('')
      this.model.cells.push(Array(colCount).fill(''))
      this.model.rowHeaderOperands.push(null)
      this.model.cellOperands.push(Array(colCount).fill(null))
    },
    removeRow(ri) {
      if (this.model.rowHeaders.length <= 1) {
        this.$message.warning('至少保留一行')
        return
      }
      this.model.rowHeaders.splice(ri, 1)
      this.model.cells.splice(ri, 1)
      this.model.rowHeaderOperands.splice(ri, 1)
      this.model.cellOperands.splice(ri, 1)
    },
    addColumn() {
      this.model.colHeaders.push('')
      this.model.cells.forEach(row => row.push(''))
      this.model.colHeaderOperands.push(null)
      this.model.cellOperands.forEach(row => row.push(null))
    },
    removeColumn(ci) {
      if (this.model.colHeaders.length <= 1) {
        this.$message.warning('至少保留一列')
        return
      }
      this.model.colHeaders.splice(ci, 1)
      this.model.cells.forEach(row => row.splice(ci, 1))
      this.model.colHeaderOperands.splice(ci, 1)
      this.model.cellOperands.forEach(row => row.splice(ci, 1))
    },
    async handleSave() {
      await saveContent({ definitionId: this.definitionId, modelJson: JSON.stringify(this.model) })
      await refreshFields(this.definitionId, JSON.stringify(this.model))
      this.refreshProjectRefs()

      this.$message.success('保存成功')
    },
    async handleCompile() {
      await this.handleSave()
      const res = await compileRule(this.definitionId)
      if (isSuccessResult(res)) {
        this.$message.success('编译成功')
        // 异步刷新变量映射和脚本面板
        await this.loadProjectVars(this.definitionId)
        if (this.$refs.scriptPanel) {
          this.$refs.scriptPanel.refresh()
        }
      } else {
        this.$message.error('编译失败: ' + resultErrorMessage(res))
      }
    },
    handleTest() {
      this.testParamsTemplate = this.buildTestParamsTemplate()
      this.testVisible = true
    },
    buildTestParamsTemplate() {
      const params = {}
      if (this.model.rowVar && this.model.rowVar.varCode) {
        params[this.model.rowVar.varCode] = (this.model.rowHeaders || []).filter(Boolean)[0] || ''
      }
      if (this.model.colVar && this.model.colVar.varCode) {
        params[this.model.colVar.varCode] = (this.model.colHeaders || []).filter(Boolean)[0] || ''
      }
      return params
    }
  }
}
</script>

<style lang="scss" scoped>
.ct-designer {
  background: #fff;
  border-radius: 4px;
  padding: 20px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
  min-height: 100%;
}

/* 顶部标题栏 */
.ct-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 8px;
}
.ct-title-area {
  display: flex;
  align-items: center;
}
.ct-title-icon {
  font-size: 18px;
  color: #722ed1;
  margin-right: 8px;
}
.ct-title {
  font-size: 16px;
  font-weight: bold;
  color: #282828;
}
.ct-toolbar {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* 维度定义面板 */
.ct-dim-panel {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 20px;
  padding: 16px;
  background: #fafafa;
  border-radius: 6px;
  border: 1px solid #eeeeee;
}
.ct-dim-card {
  flex: 1;
  min-width: 160px;
}
.dim-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #555;
  margin-bottom: 8px;
}
.dim-icon {
  font-size: 15px;
}
.row-icon { color: #1890ff; }
.col-icon { color: #52c41a; }
.result-icon { color: #fa8c16; }
.ct-dim-cross {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding-top: 32px;
}
.cross-label {
  font-size: 28px;
  color: #bbb;
  line-height: 1;
}
.cross-desc {
  font-size: 11px;
  color: #999;
  margin-top: 4px;
  white-space: nowrap;
}

/* 矩阵表格 */
.ct-matrix-wrap {
  overflow-x: auto;
  border-radius: 6px;
  border: 1px solid #e8e8e8;
  margin-bottom: 16px;
}
.ct-matrix {
  border-collapse: collapse;
  width: 100%;
  table-layout: auto;

  th, td {
    border: 1px solid #e8e8e8;
    padding: 0;
    vertical-align: middle;
  }

  .col-row-header { width: 140px; min-width: 120px; }
  .col-data { width: 130px; min-width: 110px; }
  .col-action { width: 50px; }
}

/* 左上角交叉单元格 */
.corner-cell {
  background: #f5f5f5;
  position: relative;
  overflow: hidden;
  padding: 0;
  min-height: 56px;
}
.corner-row {
  position: absolute;
  bottom: 6px;
  left: 8px;
  font-size: 11px;
  color: #888;
}
.corner-col {
  position: absolute;
  top: 6px;
  right: 8px;
  font-size: 11px;
  color: #888;
}
.corner-divider {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(to bottom right, transparent calc(50% - 0.5px), #d0d0d0, transparent calc(50% + 0.5px));
  pointer-events: none;
}

/* 列头单元格 */
.col-header-cell {
  background: #e8f3ff;
  padding: 6px;
  text-align: center;
}
.header-cell-inner {
  display: flex;
  align-items: center;
  gap: 4px;
}
.header-input {
  flex: 1;
}
.delete-col-btn {
  flex-shrink: 0;
  color: #ccc !important;
  padding: 0 !important;
  font-size: 13px;
  &:hover { color: #F76E6C !important; }
}
.add-col-cell {
  background: #f9f9f9;
  text-align: center;
  padding: 6px;
}

/* 行头单元格 */
.row-header-cell {
  background: #f0fff4;
  padding: 6px;
}
.row-header-inner {
  display: flex;
  align-items: center;
  gap: 4px;
}
.delete-row-btn {
  flex-shrink: 0;
  color: #ccc !important;
  padding: 0 !important;
  font-size: 13px;
  &:hover { color: #F76E6C !important; }
}

/* 数据单元格 */
.data-cell {
  background: #fff;
  padding: 5px;
  transition: background 0.15s;
  &.cell-filled {
    background: #fafffe;
  }
  &.cell-focused {
    background: #e6f0ff !important;
    outline: none;
  }
  &:hover { background: #f5f5f5; }
}
.cell-input ::v-deep input {
  text-align: center;
  font-weight: 500;
}

/* 添加行触发行 */
.add-row-trigger {
  background: #fafafa;
  text-align: center;
  cursor: pointer;
  height: 32px;
  padding: 4px;
  &:hover { background: #e6f7ff; }
}
.add-row-cell {
  background: #fafafa;
}

/* 查询逻辑预览 */
.ct-preview {
  padding: 10px 14px;
  background: #f0f7ff;
  border-radius: 4px;
  font-size: 13px;
  color: #555;
  display: flex;
  align-items: center;
  gap: 8px;
}
.preview-icon {
  color: #1890ff;
  font-size: 14px;
  flex-shrink: 0;
}

/* 测试结果 */
.test-result {
  margin-top: 16px;
}
.script-override-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  background: #fff1f0;
  border: 1px solid #ffccc7;
  border-radius: 4px;
  margin-top: 8px;
  font-size: 12px;
  color: #cf1322;
  i { color: #f5222d; font-size: 14px; }
}
</style>
