<template>
  <div class="operand-picker">
    <condition-range-input v-if="rangeMode" :value="value" :value-type="expectedType || value?.valueType || 'NUMBER'" @input="onEditorApply" />
    <condition-collection-input v-else-if="collectionMode" :value="value" :value-type="expectedType || 'STRING'" @input="onEditorApply" />
    <var-picker
      v-bind="$attrs"
      v-else
      ref="varPicker"
      :value="value"
      :virtual-ref="virtualRef"
      :vars="vars"
      :functions="functions"
      :allowed-kinds="allowedKinds"
      :expected-type="expectedType"
      :writable-only="writableOnly"
      :visible-tags="pickerVisibleTags"
      :selectable-tags="pickerSelectableTags"
      :operand-mode="true"
      :editor-mode="editorVisible || !!manualKind"
      :editor-kind="manualKind || 'EXPRESSION'"
      manual-in-panel
      :dismiss-on-outside="dismissOnOutside"
      @visible-change="onPickerVisibility"
      @editor-back="returnToPicker"
      @input="onQuickInput"
      @select="onQuickSelect"
      @manual-edit="openManualInput"
      @expression="operand => operand?.kind ? openExpressionEditor(operand) : openEditor()"
    >
      <template #editor="{ visible }">
        <section v-if="manualKind" class="operand-manual-panel" :aria-label="manualKind === 'PATH' ? '路径配置' : '阈值配置'" @compositionstart="manualComposing = true" @compositionend="manualComposing = false">
          <div class="operand-manual-content" @keydown.ctrl.enter.prevent="finishManualInput" @keydown.meta.enter.prevent="finishManualInput">
            <div v-if="manualKind === 'LITERAL'" class="operand-type-row">
              <label>数据类型</label>
              <el-select :model-value="manualTypeMode" aria-label="阈值数据类型" @update:model-value="selectManualType"><el-option label="自动推荐" value="AUTO" /><el-option v-for="type in valueTypes" :key="type.value" :label="type.label" :value="type.value" :disabled="!typeCompatible(type.value, expectedType)" /></el-select>
              <span class="operand-type-recommendation">{{ manualTypeMode === 'AUTO' ? '自动推荐：' : '按你的选择：' }}{{ manualTypeLabel }}</span>
            </div>
            <template v-if="manualKind === 'PATH'">
              <label class="operand-content-label">取值来源</label>
              <var-picker class="operand-path-source" :value="manualPathSource" :vars="manualReferences" :allowed-kinds="['REFERENCE']" operand-mode
                :writable-only="writableOnly" :visible-tags="pickerVisibleTags" :selectable-tags="pickerSelectableTags"
                placeholder="选择变量、对象、模型或 API" @input="selectPathSource" />
            </template>
            <label class="operand-content-label">{{ manualKind === 'PATH' ? '子路径（可选）' : '阈值内容' }}</label>
            <monaco-editor v-if="manualKind === 'LITERAL'" ref="manualCode" :value="manualInputText" :language="manualJson ? 'json' : 'plaintext'" height="210px" :options="{ ariaLabel: '阈值内容', minimap: { enabled: false }, wordWrap: 'on', autoSurround: 'never', formatOnType: false, formatOnPaste: false }" @input="updateManualValue" @editor-ready="onManualEditorReady" />
            <el-input v-else ref="manualInput" :model-value="manualSubpathInput" :disabled="!manualPathSource" placeholder="例如 customer.name、items[0].name" aria-label="子路径" clearable @update:model-value="updateSubpath" @keydown.enter="onManualEnter" />
            <p class="operand-manual-help">{{ manualKind === 'PATH' ? '留空取整个内容；只填写来源内部的路径，无需重复来源编码，也无需预先声明子字段。' : '手选类型优先；自动模式推荐可保留输入内容的类型，数值支持整数和小数。' }}</p>
            <div v-if="manualKind === 'PATH' && manualSubpathInput" class="operand-type-row">
              <label title="按实际返回值选择类型，用于比较和参数配置">结果类型</label>
              <el-select :model-value="manualOperand?.pathValueType || ''" placeholder="请选择取值结果类型" aria-label="取值结果类型" @update:model-value="selectPathResultType">
                <el-option v-for="type in valueTypes" :key="type.value" :label="type.label" :value="type.value" :disabled="!typeCompatible(type.value, expectedType)" />
              </el-select>
            </div>
            <div v-if="manualKind === 'PATH' && manualOperand?.resolved && manualPathMatch" class="operand-path-match">
              <span>匹配内容</span><strong>{{ manualPathMatch.label }}</strong>
              <span>编码</span><code>{{ manualPathMatch.code }}</code>
              <template v-if="manualPathMatch.version"><span>版本</span><strong>{{ manualPathMatch.version }}</strong></template>
              <span>剩余子路径</span><code>{{ manualPathMatch.suffix || '已完整匹配' }}</code>
            </div>
            <p v-if="manualKind === 'PATH' && pathDirectoryError" class="operand-manual-error" role="alert">{{ pathDirectoryError }}</p>
            <div v-if="manualPathCandidates.length" class="operand-path-candidates">
              <p>存在多个同名字段，请选择对应内容：</p>
              <button v-for="candidate in manualPathCandidates" :key="candidateKey(candidate)" type="button" @click="selectManualPathCandidate(candidate)"><span>{{ candidateLabel(candidate) }}</span><code>{{ candidateCode(candidate) }}</code></button>
            </div>
            <p v-if="manualError" class="operand-manual-error" role="alert">{{ manualError }}</p>
          </div>
          <footer class="operand-manual-footer"><span>关闭或切换面板保留草稿，应用后回填。</span><el-button @click="$refs.varPicker?.closePopover()">暂存并关闭</el-button><el-button type="primary" @click="finishManualInput">{{ manualKind === 'PATH' ? '使用路径' : '应用阈值' }}</el-button></footer>
        </section>
        <div v-if="editorInitialized" v-show="editorVisible" class="operand-expression-panel"><expression-editor-dialog ref="inlineEditor" :visible="visible && editorVisible" :value="editorValue" :vars="vars" :functions="functions" :list-options="listOptions" :allowed-kinds="allowedKinds" :context="editorContext" :expected-type="expectedType" :title="editorTitle" :visible-tags="pickerVisibleTags" :selectable-tags="pickerSelectableTags" embedded compact preserve-draft @apply="onEditorApply" @cancel="$refs.varPicker?.closePopover()" /></div>
      </template>
      <template #editor-actions><button v-if="allowedKinds.includes('LIST_QUERY')" type="button" class="operand-list-query" @click="insertListQuery">名单查询</button><button type="button" @click="$refs.inlineEditor?.undo()">撤销</button><button type="button" @click="$refs.inlineEditor?.redo()">重做</button><button type="button" @click="openFullPageEditor">展开页面</button></template>
    </var-picker>
    <div v-if="value?.kind === 'FUNCTION' && !manualKind && !virtualRef" class="operand-function-args">
      <button v-for="(arg, index) in value.args || []" :key="index" type="button" class="operand-argument" :aria-label="`配置参数 ${argumentName(index)}`" @click="openArgument(index)">{{ argumentName(index) }}: {{ displayOperand(arg) || '选择字段或输入值' }}</button>
      <operand-picker v-if="activeArgument !== null" ref="argumentPicker" :value="value.args[activeArgument]" :vars="vars" :functions="functions" :allowed-kinds="allowedKinds" :visible-tags="visibleTags" :selectable-tags="selectableTags" :expected-type="argumentType(activeArgument)" :editor-title="'配置参数 ' + argumentName(activeArgument)" dismiss-on-outside @input="updateArgument" />
    </div>
  </div>
</template>

<script>
import { $emit } from '../../utils/gogocodeTransfer'
import VarPicker from './VarPicker.vue'
import MonacoEditor from '@/components/MonacoEditor.vue'
import ConditionRangeInput from './ConditionRangeInput.vue'
import ConditionCollectionInput from './ConditionCollectionInput.vue'
import { isCollectionOperator } from '@/utils/conditionCollection'
import { isRangeOperator } from '@/utils/conditionRange'
import ExpressionEditorDialog from '@/components/expression/ExpressionEditorDialog.vue'
import {
  cloneOperand,
  createLiteralOperand,
  createListQueryOperand,
  createPathOperand,
  createRelativePathOperand,
  pathOperandSource,
  resolvePathOperand,
  VALUE_OPERAND_KINDS,
  operandDisplay,
  functionArgumentExpectedType,
} from '@/utils/operand'
import { createFunctionTemplate, functionParameters } from '@/components/expression/expressionTree'
import { fieldPickerTags, matchesFieldTags } from '@/utils/fieldPickerPolicy'
import { canViewPickerItem } from '@/security/pickerAccess'
import { matchPathContent } from '@/utils/pathContentMatch'
import { resourceVersionIdentity } from '@/utils/resourceVersion'
import { getFieldVersionFields } from '@/api/fieldPickerVersion'
import { LITERAL_TYPE_OPTIONS, literalTypeCompatible, literalTypeFamily, literalValidationError, recommendLiteralType, arrayLiteralDraft } from '@/utils/literalType'
import {
  createExpressionSessionId,
  createExpressionSessionTitle,
  expressionSourceIdentity,
} from '@/utils/expressionSession'

function inheriltClassAndStyle() {
  const attrs = this.$attrs
  attrs.class && this.$el.classList.add(attrs.class)
  attrs.style &&
    Object.entries(attrs.style).forEach(([k, v]) => {
      this.$el.style[k] = v
    })
}

let operandPickerSequence = 0

export default {
  components: {
    ExpressionEditorDialog,
    VarPicker,
    MonacoEditor,
    ConditionRangeInput,
    ConditionCollectionInput,
  },
  mounted() {
    inheriltClassAndStyle.call(this)
  },
  name: 'OperandPicker',
  inheritAttrs: false,
  props: {
    value: { type: Object, default: null },
    virtualRef: { type: Object, default: null },
    vars: { type: Array, default: () => [] },
    functions: { type: Array, default: () => [] },
    listOptions: { type: Array, default: () => [] },
    allowedKinds: { type: Array, default: () => VALUE_OPERAND_KINDS.slice() },
    expectedType: { type: String, default: '' },
    rangeOperator: { type: String, default: '' },
    conditionOperator: { type: String, default: '' },
    context: { type: String, default: '' },
    writableOnly: { type: Boolean, default: false },
    visibleTags: { type: [Array, Object], default: null },
    selectableTags: { type: [Array, Object], default: null },
    editorTitle: { type: String, default: '配置表达式' },
    dismissOnOutside: { type: Boolean, default: true },
  },
  data() {
    return {
      editorVisible: false,
      editorValue: null,
      editorInitialized: false,
      editorSourceValue: '',
      pickerVisible: false,
      manualComposing: false,
      manualTypeMode: 'AUTO',
      manualInputText: '',
      manualDrafts: {},
      manualSourceValue: '',
      suppressQuickSelect: false,
      manualKind: '',
      manualOperand: null,
      manualPathSource: null,
      manualSubpathInput: '',
      manualVersionReferences: [],
      pathDirectoryError: '',
      pathDirectoryRequest: 0,
      manualPathCandidates: [],
      manualError: '',
      activeArgument: null,
      expressionSourceKey: `operand-picker-${++operandPickerSequence}`,
      expressionSourceSessions: {},
      expressionSessionId: '',
      lastAppliedExpressionRevision: 0,
    }
  },
  computed: {
    pickerVisibleTags() { return this.visibleTags ?? (this.editorContext === 'WRITE_TARGET' ? ['write'] : null) },
    pickerSelectableTags() { return this.selectableTags ?? (this.editorContext === 'WRITE_TARGET' ? ['write'] : null) },
    manualReferences() { return this.vars.filter(item => !item.disabled && canViewPickerItem(item) && matchesFieldTags(fieldPickerTags(item), this.pickerVisibleTags) && matchesFieldTags(fieldPickerTags(item), this.pickerSelectableTags) && (!this.writableOnly || fieldPickerTags(item).includes('write'))) },
    manualPathMatch() { return matchPathContent(this.manualOperand, [...this.manualReferences, ...this.manualVersionReferences]) },
    manualJson() { return this.manualKind === 'LITERAL' && ['LIST', 'OBJECT'].includes(literalTypeFamily(this.manualOperand?.valueType)) },
    valueTypes() { const type = this.manualOperand?.valueType; return type && !LITERAL_TYPE_OPTIONS.some(item => item.value === type) ? [...LITERAL_TYPE_OPTIONS, { value: type, label: type }] : LITERAL_TYPE_OPTIONS },
    manualTypeLabel() { return this.valueTypes.find(type => type.value === this.manualOperand?.valueType)?.label || this.manualOperand?.valueType || '' },
    rangeMode() { return isRangeOperator(this.conditionOperator || this.rangeOperator) },
    collectionMode() { return isCollectionOperator(this.conditionOperator || this.rangeOperator) },
    editorContext() {
      if (this.context) return this.context
      return this.writableOnly ? 'WRITE_TARGET' : 'READ_EXPRESSION'
    },
  },
  watch: {
    vars() { if (this.manualKind === 'PATH' && this.manualInputText && !this.manualOperand?.resolved) { this.resolveManualPath(); this.saveManualDraft() } },
    value(value) {
      if (JSON.stringify(value) === this.manualSourceValue) return
      this.manualSourceValue = JSON.stringify(value)
      this.manualDrafts = {}
      if (value?.kind !== 'FUNCTION') this.activeArgument = null
      if (value == null) {
        this.manualKind = ''
        this.manualOperand = null
        this.manualPathCandidates = []
        return
      }
      if (this.manualKind && value.kind !== this.manualKind) {
        this.manualKind = ''
        this.manualOperand = null
        this.manualPathCandidates = []
        return
      }
      // 组件实例会在切换节点/动作块时复用；手输编辑器必须同步新的绑定值，
      // 否则用户会看到上一个节点留下的阈值或路径。
      if (this.manualKind && value.kind === this.manualKind) {
        this.openManualInput(this.manualKind, { reset: true })
      }
    },
  },
  activated() {
    if (this.sourceOverlay) { this.sourceOverlay.style.display = this.sourceOverlayDisplay; this.sourceOverlay = null }
    this.consumePendingExpression()
  },
  methods: {
    onPickerVisibility(visible) { this.pickerVisible = visible; if (visible && this.manualKind) this.$nextTick(this.focusManualInput) },
    displayOperand: operandDisplay,
    argumentName(index) { const params = functionParameters(this.findFunction(this.value)); return params[index]?.name || params[index]?.label || `参数 ${index + 1}` },
    argumentType(index) { return functionArgumentExpectedType(this.value, index) },
    openArgument(index) {
      this.activeArgument = index
      this.$nextTick(() => this.$refs.argumentPicker?.$refs.varPicker?.onInputClick({ restoreManual: false }))
    },
    updateArgument(operand) {
      const value = cloneOperand(this.value)
      value.args[this.activeArgument] = operand
      $emit(this, 'update:value', value)
      $emit(this, 'select', value)
    },
    onQuickInput(operand) {
      if (operand && operand.kind === 'FUNCTION') {
        if (this.value?.kind === 'FUNCTION' && operand.functionId != null && String(operand.functionId) === String(this.value.functionId)
          && JSON.stringify(operand.parameterTypes) === JSON.stringify(this.value.parameterTypes)) {
          this.pendingFunctionOperand = { ...cloneOperand(operand), args: cloneOperand(this.value.args) }
          $emit(this, 'update:value', this.pendingFunctionOperand)
          return
        }
        const template = createFunctionTemplate(
          this.findFunction(operand) || operand
        )
        if (template.args.length) {
          this.pendingFunctionOperand = template
          $emit(this, 'update:value', template)
          return
        }
      }
      $emit(this, 'update:value', operand)
    },
    onQuickSelect(operand) {
      if (this.pendingFunctionOperand) { operand = this.pendingFunctionOperand; this.pendingFunctionOperand = null }
      if (this.suppressQuickSelect) {
        this.suppressQuickSelect = false
        return
      }
      $emit(this, 'select', operand)
    },
    openEditor() {
      return this.openExpressionEditor(this.manualOperand || this.value)
    },
    insertListQuery() { this.$refs.inlineEditor?.insertTemplate(createListQueryOperand()) },
    openFullPageEditor() {
      const draft = this.$refs.inlineEditor ? this.$refs.inlineEditor.getDraft() : this.editorInitialized ? this.editorValue : this.value
      return this.openExpressionEditor(draft, { fullPage: true })
    },
    async openExpressionEditor(operand, { fullPage = false } = {}) {
      this.saveManualDraft()
      if (!fullPage) {
        const source = JSON.stringify(this.value)
        if (!this.editorInitialized || this.editorSourceValue !== source || operand?.kind === 'OPERATION' && operand !== this.value) {
          this.editorValue = cloneOperand(operand)
          this.editorSourceValue = source
          this.editorInitialized = true
          this.$refs.inlineEditor?.reset(this.editorValue)
        }
        this.manualKind = ''
        this.manualOperand = null
        this.editorVisible = true
        await this.$nextTick()
        this.$refs.varPicker?.openPopover?.({ restoreManual: false })
        return
      }
      const editorValue = cloneOperand(operand)
      const ruleId = this.designerRuleId()
      if (!this.$store || !this.$router) {
        this.editorValue = editorValue
        this.editorVisible = true
        return
      }

      const sourceIdentity = expressionSourceIdentity(this.designerReturnRoute())
      const sourceKey = this.expressionSourceSessions[sourceIdentity] || (this.expressionSourceSessions[sourceIdentity] =
        Object.keys(this.expressionSourceSessions).length ? `operand-picker-${++operandPickerSequence}` : this.expressionSourceKey)
      const sourceValue = JSON.stringify(this.value)
      const sessionId = createExpressionSessionId(ruleId ?? this.$route?.name, sourceKey)
      const previous = this.$store.getters['expressionSessions/sessionById']?.(sessionId)
      if (this.expressionSessionId !== sessionId || previous?.sourceValue !== sourceValue) this.lastAppliedExpressionRevision = 0
      const routeTitle =
        this.$route && this.$route.meta && this.$route.meta.title
      await this.$store.dispatch('expressionSessions/openSession', {
        sessionId,
        ruleId,
        sourceKey,
        sourceIdentity,
        sourceValue,
        draft: editorValue,
        vars: this.vars,
        functions: this.functions,
        listOptions: this.listOptions,
        allowedKinds: this.allowedKinds,
        context: this.editorContext,
        expectedType: this.expectedType,
        returnRoute: this.designerReturnRoute(),
        title: createExpressionSessionTitle(
          routeTitle,
          this.editorTitle,
          this.$attrs.placeholder
        ),
      })
      this.expressionSessionId = sessionId
      this.editorVisible = false
      this.$refs.varPicker?.closePopover?.()
      const overlay = this.$el?.closest('.el-overlay')
      if (overlay) { this.sourceOverlay = overlay; this.sourceOverlayDisplay = overlay.style.display; overlay.style.display = 'none' }
      await this.$router.push({
        name: ruleId == null ? 'ConfigurationExpressionEditor' : 'ExpressionEditor',
        params: ruleId == null ? { sessionId } : { ruleId: String(ruleId), sessionId },
      })
    },
    designerReturnRoute() {
      const route = this.$route || {}
      return {
        name: route.name,
        params: { ...(route.params || {}) },
        query: { ...(route.query || {}) },
      }
    },
    designerRuleId() {
      const route = this.$route
      if (
        !route ||
        !route.path ||
        !route.path.startsWith('/designer/') ||
        route.path.startsWith('/designer/expression/')
      )
        return null
      const value = route.params && route.params.id
      if (value == null || value === '') return null
      return value
    },
    async consumePendingExpression() {
      if (!this.expressionSessionId || !this.$store) return
      const getter =
        this.$store.getters['expressionSessions/pendingCompiledResult']
      if (typeof getter !== 'function') return
      const result = getter(this.expressionSessionId)
      if (!result || result.revision <= this.lastAppliedExpressionRevision)
        return
      if (result.sourceIdentity && (result.sourceIdentity !== expressionSourceIdentity(this.designerReturnRoute())
        || result.sourceValue !== JSON.stringify(this.value))) return

      this.lastAppliedExpressionRevision = result.revision
      const value = cloneOperand(result.operand)
      this.editorInitialized = false
      $emit(this, 'update:value', value)
      $emit(this, 'select', value)
      this.manualKind = ''
      this.manualOperand = null
      this.manualPathCandidates = []
      await this.$store.dispatch('expressionSessions/markApplied', {
        sessionId: this.expressionSessionId,
        revision: result.revision,
      })
    },
    typeCompatible: literalTypeCompatible,
    saveManualDraft() {
      if (this.manualKind && this.manualOperand) this.manualDrafts[this.manualKind] = { operand: cloneOperand(this.manualOperand), text: this.manualInputText, typeMode: this.manualTypeMode,
        pathSource: cloneOperand(this.manualPathSource), subpath: this.manualSubpathInput }
    },
    openManualInput(kind, { reset = false } = {}) {
      if (!reset) this.saveManualDraft()
      const source = JSON.stringify(this.value)
      if (this.manualSourceValue !== source) { this.manualSourceValue = source; this.manualDrafts = {} }
      const saved = reset ? null : this.manualDrafts[kind]
      this.manualKind = kind
      this.editorVisible = false
      this.manualError = ''
      this.manualComposing = false
      const arrayDraft = kind === 'LITERAL' ? arrayLiteralDraft(this.value) : null
      const existing = this.value?.kind === kind || !!arrayDraft
      const reference = kind === 'PATH' && this.value?.kind === 'REFERENCE' ? createRelativePathOperand(this.value) : null
      this.manualOperand = saved?.operand || (existing ? arrayDraft || cloneOperand(this.value) : reference || (kind === 'PATH' ? createPathOperand('') : createLiteralOperand('', recommendLiteralType('', this.expectedType))))
      const value = this.manualOperand.value
      this.manualInputText = saved?.text ?? (typeof value === 'object' && value !== null ? JSON.stringify(value, null, 2) : String(value ?? ''))
      this.manualTypeMode = saved?.typeMode || (existing && kind === 'LITERAL' ? this.manualOperand.valueType : 'AUTO')
      this.manualPathCandidates = []
      if (kind === 'PATH' && this.manualInputText && !this.manualOperand?.resolved) this.resolveManualPath()
      this.manualPathSource = saved?.pathSource || pathOperandSource(this.manualOperand)
      this.manualSubpathInput = saved?.subpath ?? (this.manualPathSource ? this.manualInputText.slice(this.manualPathSource.code.length).replace(/^\./, '') : '')
      if (kind === 'PATH') this.loadPathDirectory()
      this.$nextTick(() => {
        this.$refs.varPicker?.openPopover?.({ restoreManual: false })
        this.$nextTick(this.focusManualInput)
      })
    },
    focusManualInput() {
      if (this.manualKind === 'LITERAL') { this.$refs.manualCode?.getEditor?.()?.layout(); this.$refs.manualCode?.focus?.() }
      else this.$refs.manualInput?.focus?.()
    },
    onManualEditorReady(editor) {
      // Monaco 会接管 Ctrl/Cmd+Enter，需在编辑器内绑定应用，Enter 仍用于换行。
      editor.addCommand(window.monaco.KeyMod.CtrlCmd | window.monaco.KeyCode.Enter, () => this.finishManualInput())
      if (!this.pickerVisible || this.manualKind !== 'LITERAL') return
      editor.layout()
      editor.focus()
      const model = editor.getModel()
      editor.setPosition(model.getPositionAt(model.getValueLength()))
    },
    selectManualType(type) {
      this.manualTypeMode = type
      this.manualOperand = { ...this.manualOperand, valueType: type === 'AUTO' ? recommendLiteralType(this.manualInputText, this.expectedType) : type }
      if (this.manualJson && !this.manualInputText) {
        this.manualInputText = literalTypeFamily(this.manualOperand.valueType) === 'LIST' ? '[]' : '{}'
        this.manualOperand.value = this.manualInputText
      }
      this.manualError = ''
      this.saveManualDraft()
      this.$nextTick(this.focusManualInput)
    },
    patchManualOperand(fields) {
      if (fields.valueType) this.selectManualType(fields.valueType)
      if (Object.hasOwn(fields, 'value')) this.updateManualValue(fields.value)
    },
    updateManualValue(value) {
      this.manualInputText = String(value ?? '')
      this.manualError = ''
      if (this.manualKind === 'PATH') this.resolveManualPath()
      else {
        this.manualOperand = { ...this.manualOperand, value, valueType: this.manualTypeMode === 'AUTO' ? recommendLiteralType(value, this.expectedType) : this.manualTypeMode }
      }
      this.saveManualDraft()
    },
    resolveManualPath() {
      if (this.manualKind !== 'PATH') return
      const source = this.manualOperand?.refId ? { ...this.manualOperand, value: this.manualInputText } : createPathOperand(this.manualInputText)
      const result = resolvePathOperand(source, this.manualReferences)
      this.manualOperand = result.operand
      this.manualPathCandidates = result.candidates
    },
    selectPathSource(reference) {
      const sameSource = reference && this.manualPathSource && String(reference.refId) === String(this.manualPathSource.refId)
        && reference.refType === this.manualPathSource.refType && reference.relativePath === this.manualPathSource.relativePath
      const path = sameSource ? this.manualSubpathInput : ''
      const type = sameSource ? this.manualOperand?.pathValueType : ''
      this.manualPathSource = reference ? cloneOperand(reference) : null
      this.manualSubpathInput = ''
      this.manualOperand = reference ? createRelativePathOperand(reference) : createPathOperand('')
      this.manualInputText = this.manualOperand.value
      this.manualError = ''
      this.manualPathCandidates = []
      if (path) { this.manualOperand.pathValueType = type; this.updateSubpath(path) }
      this.loadPathDirectory()
      this.saveManualDraft()
    },
    async loadPathDirectory() {
      const request = ++this.pathDirectoryRequest
      this.manualVersionReferences = []
      this.pathDirectoryError = ''
      const source = this.manualPathSource
      const identity = resourceVersionIdentity(source)
      if (source?.versionMode !== 'FIXED' || !identity || !['model', 'dataobject'].includes(identity.module)) return
      try {
        const response = await getFieldVersionFields(identity, source.versionId, source.versionSource)
        if (request !== this.pathDirectoryRequest) return
        const group = response.data
        const kind = identity.module === 'model' ? 'model' : 'object'
        this.manualVersionReferences = (group.fields || []).map(field => ({
          ...source, ...field, _varId: field.refId, _refType: field.refType, varCode: field.code,
          varLabelText: field.label, varType: field.valueType, version: group.version, displayVersion: group.displayVersion,
          _ref: { [`${kind}Id`]: identity.ownerId },
        }))
      } catch (error) { if (request === this.pathDirectoryRequest) this.pathDirectoryError = '该版本字段目录加载失败，保留已有路径；重新选择来源可重试。' }
    },
    updateSubpath(path) {
      this.manualSubpathInput = path
      this.manualError = ''
      try {
        this.manualOperand = createRelativePathOperand(this.manualPathSource, path, this.manualOperand?.pathValueType || this.expectedType)
        this.manualInputText = this.manualOperand.value
      } catch (error) { this.manualError = error.message; this.manualOperand = { ...this.manualOperand, resolved: false } }
      this.saveManualDraft()
    },
    selectPathResultType(type) {
      this.manualOperand = { ...this.manualOperand, pathValueType: type }
      this.updateSubpath(this.manualSubpathInput)
    },
    selectManualPathCandidate(candidate) {
      const result = resolvePathOperand(createPathOperand(this.manualInputText), [candidate])
      this.manualOperand = result.operand
      this.manualPathCandidates = []
      this.manualError = ''
      this.saveManualDraft()
    },
    emitPathResolve(result) {
      $emit(this, 'path-resolve', { operand: cloneOperand(result.operand), candidates: result.candidates.slice() })
    },
    emitManualOperand() {
      if (this.manualKind === 'PATH' && this.manualPathSource) {
        this.updateSubpath(this.manualSubpathInput)
        if (this.manualError) return false
        if (this.manualSubpathInput && !this.manualOperand.valueType) { this.manualError = '请选择取值结果类型'; return false }
        if (!literalTypeCompatible(this.manualOperand.valueType, this.expectedType)) { this.manualError = '取值结果类型不符合当前位置要求'; return false }
      }
      this.manualError = this.manualKind === 'LITERAL' ? literalValidationError(this.manualOperand, this.expectedType)
        : !this.manualInputText.trim() ? '请输入字段路径'
          : this.manualPathCandidates.length ? '请选择对应字段'
            : !this.manualOperand?.resolved ? '未找到对应字段，请检查路径或先选择可用字段' : ''
      if (this.manualError) return false
      const value = cloneOperand(this.manualOperand)
      if (value.kind === 'PATH') {
        delete value.pathDisplay
        if (value.versionMode === 'FIXED' && this.manualPathMatch?.sourceKey) {
          const { label, code, matchedPath, version, sourceKey } = this.manualPathMatch
          value.pathDisplay = { label, code, matchedPath, version, sourceKey }
        }
      }
      if (value.kind === 'LITERAL' && literalTypeFamily(value.valueType) === 'BOOLEAN' && typeof value.value === 'string') value.value = value.value.trim().toLowerCase()
      this.manualSourceValue = JSON.stringify(value)
      this.saveManualDraft()
      $emit(this, 'update:value', value)
      $emit(this, 'select', value)
      if (this.manualKind === 'PATH') this.emitPathResolve({ operand: value, candidates: [] })
      return true
    },
    onManualEnter(event) {
      if (event.isComposing || this.manualComposing) return
      event.preventDefault()
      this.finishManualInput()
    },
    finishManualInput() {
      if (!this.manualKind || this.manualComposing || !this.emitManualOperand()) return
      this.manualKind = ''
      this.manualOperand = null
      this.manualPathCandidates = []
      this.$refs.varPicker?.closePopover?.()
    },
    returnToPicker() {
      this.saveManualDraft()
      this.manualKind = ''
      this.editorVisible = false
      this.$nextTick(() => this.$refs.varPicker?.openPopover?.({ restoreManual: false }))
    },
    onEditorApply(operand) {
      const value = cloneOperand(operand)
      $emit(this, 'update:value', value)
      $emit(this, 'select', value)
      this.manualKind = ''
      this.manualOperand = null
      this.manualPathCandidates = []
      this.editorVisible = false
      this.$refs.varPicker?.closePopover?.()
    },
    findFunction(operand) {
      if (!operand) return null
      if (operand.versionMode === 'FIXED') return operand
      const byId = operand.functionId == null
        ? null
        : this.functions.find((fn) => {
            const id = fn.functionId != null ? fn.functionId : fn.id
            return String(id) === String(operand.functionId)
          })
      if (byId) return byId
      // 有稳定 ID 但目录中不存在时不能按可变编码猜测另一个同名方法。
      if (operand.functionId != null) return null
      return this.functions.find((fn) => {
        const code = fn.functionCode || fn.funcCode || fn.functionName || fn.funcName || fn.name || ''
        return code === operand.functionCode
      }) || null
    },
    candidateKey(item) {
      const type =
        item._refType || item.refType || (item._ref && item._ref.refType) || ''
      const id =
        item._varId != null
          ? item._varId
          : item.refId != null
          ? item.refId
          : item.id
      return type + ':' + (id != null ? id : this.candidateCode(item))
    },
    candidateCode(item) {
      return item.varCode || item.refCode || item.code || ''
    },
    candidateLabel(item) {
      return (
        item.varLabelText ||
        item.varLabel ||
        item.label ||
        this.candidateCode(item)
      )
    },
  },
  emits: ['input', 'update:value', 'select', 'path-resolve'],
}
</script>

<style scoped>
.operand-picker {
  position: relative;
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 4px;
  width: 100%;
  flex-wrap: wrap;
}
.operand-function-args { display:flex; flex-wrap:wrap; gap:4px; width:100%; order:1; }
.operand-argument { max-width:100%; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; border:1px solid var(--tianshu-border); border-radius:0; background:var(--tianshu-bg-muted); color:var(--tianshu-primary-text, var(--el-color-primary)); padding:4px 8px; cursor:pointer; }
.operand-picker > .var-picker-wrap { flex: 1; min-width: 0; }
.operand-expression-panel { height: 100%; min-height: 0; }
.operand-list-query { margin-right: auto; }
.operand-manual-panel { display: flex; flex-direction: column; height: 100%; min-height: 0; background: var(--tianshu-bg-surface); }
.operand-manual-content { flex: 1; min-height: 0; overflow: auto; padding: 18px; }
.operand-type-row { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin-bottom: 18px; }
.operand-type-row > .el-select { width: 220px; }
.operand-type-recommendation,.operand-manual-help { font-size: 12px; color: var(--tianshu-text-secondary); }
.operand-content-label { display: block; margin-bottom: 8px; font-weight: 600; }
.operand-path-source { margin-bottom: 12px; }
.operand-manual-panel :deep(.el-input__wrapper),.operand-manual-panel :deep(.el-textarea__inner),.operand-manual-panel :deep(.el-select__wrapper) { border-radius: 0; }
.operand-manual-help { line-height: 1.7; margin: 10px 0; }
.operand-path-match { display: grid; grid-template-columns: 76px minmax(0,1fr); gap: 8px; padding: 12px; border: 1px solid var(--tianshu-border); background: var(--tianshu-bg-muted); }
.operand-path-match > code { grid-column: 2; overflow-wrap: anywhere; color: var(--el-color-primary); }
.operand-path-match > span { color: var(--tianshu-text-secondary); }
.operand-path-match > strong { font-weight: 500; }
.operand-manual-error { color: var(--el-color-danger); font-size: 12px; }
.operand-path-candidates { border: 1px solid var(--tianshu-border); padding: 8px; }
.operand-path-candidates p { margin: 0 0 8px; color: var(--tianshu-text-secondary); }
.operand-path-candidates button { display: flex; justify-content: space-between; width: 100%; gap: 10px; padding: 8px; border: 0; background: transparent; color: var(--tianshu-text-primary); cursor: pointer; text-align: left; }
.operand-path-candidates button:hover { background: var(--tianshu-info-bg); }
.operand-path-candidates code { overflow-wrap: anywhere; }
.operand-manual-footer { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; padding: 10px 12px; border-top: 1px solid var(--tianshu-border); }
.operand-manual-footer > span { flex: 1; font-size: 12px; color: var(--tianshu-text-secondary); }
.operand-manual-footer :deep(.el-button + .el-button) { margin-left: 0; }
</style>
