<template>
  <div class="se-designer uiue-compact-workbench uiue-compact-designer">
    <!-- 顶部工具栏 -->
    <div class="se-header">
      <div class="se-title-area">
        <el-button
          size="small"
          :icon="ElIconBack"
          class="uiue-detail-back"
          aria-label="返回"
          data-designer-back
          title="返回"
          @click="$router.back()"
        />
        <el-icon class="se-title-icon"><el-icon-edit-outline /></el-icon>
        <span class="se-title">QL脚本编辑器</span>
        <el-tag size="small" type="info" style="margin-left: 8px"
          >{{ lineCount }} 行</el-tag
        >
      </div>
      <div class="se-toolbar designer-version-toolbar">
        <rule-draft-read-only
          :visible="!canEditDraft"
          :loading="!draftGuardLoaded"
          :load-error="Boolean(draftGuardError) || varsLoadError"
          :revision-label="viewRevisionLabel"
          :revision-state="viewRevision ? viewRevision.state : ''"
          :can-fork="canForkViewRevision"
          :has-editable-draft="hasPendingDraft"
          :source-options="designerSourceOptions"
          :selected-source="selectedDesignerSource"
          :source-loading="designerSourcesLoading"
          :disabled="designerBusy"
          :read-only-reason="designerReadOnlyReason"
          :can-test-frozen="designerCanTest && referenceCatalogMode === 'FROZEN'"
          :migration-required="draftGuardLoaded && frozenReferenceCatalog?.localEditingAllowed === false"
          @test-frozen="handleTest"
          @open-migration="$router.push('/transfer')"
          @retry="startViewedRevisionRefresh(true)"
          @go-lifecycle="goRuleLifecycle"
          @fork="forkViewRevision"
          @change-source="switchDesignerSource"
          @delete-source="deleteDesignerSource"
        />
        <rule-designer-action-bar
          @check="handleCheck"
          @locate="locateDesignerIssue"
          :issue-context="incomingValidationIssue"
          :can-edit="canEditDraft"
          :can-test="designerCanTest"
          :state="designerActionState"
          :busy="designerBusy"
          :recovery="designerRecoveryCandidate"
          :report="designerValidationReport"
          @save="handleSave"
          @compile="handleCompile"
          @test="handleTest"
          @publish="handlePublish"
          @restore="restoreDesignerRecovery"
          @discard-recovery="discardDesignerRecovery"
        />
      </div>
    </div>

    <rule-designer-status :state="designerActionState" :field-count="varPickerOptions.length" :loading="loadingVars" :source-label="viewRevisionLabel" :reference-mode="referenceCatalogMode" :can-use-current-resources="canEnterDesignerLivePreview" @use-current="runDesignerAction(enterDesignerLivePreview)" />
    <rule-designer-dialogs :choice="designerChoice" @resolve="resolveDesignerChoice" />



    <div
      ref="designerBody"
      class="se-body"
      :class="{ 'is-resizing': resizingVarPanel }"
    >
      <aside class="se-var-panel" :class="{ collapsed: varPanelCollapsed }" :style="varPanelStyle">
        <button v-if="varPanelCollapsed" class="se-var-header" type="button" aria-label="展开内容选择器" @click="toggleVarPanel">›</button>
        <div v-if="varsLoadError && !varPanelCollapsed" class="se-picker-error">
          内容加载失败 <el-button link size="small" @click="loadProjectVars($route.params.id)">重试</el-button>
        </div>
        <field-picker-panel
          embedded :visible="!varPanelCollapsed" :width="varPanelWidth" :items="scriptPickerItems"
          :loading="loadingVars" :query="varSearchKey" :category="scriptPickerCategory"
          :selected-key="scriptPickerSelection" :selected-keys="scriptPickerSelectedKeys" :recent-keys="scriptPickerRecentKeys"
          :initial-page-size="scriptVarPageSize"
          @search="varSearchKey = $event" @category="scriptPickerCategory = $event"
          @select="insertPickerContent" @close="toggleVarPanel" @page-size="changePickerPageSize"
        >
          <template v-if="referenceCatalogMode === 'FROZEN' && !scriptPickerItems.length" #empty>
            <div class="se-frozen-empty">
              <strong>此历史版本未保存可选字段</strong>
              <span v-if="canEnterDesignerLivePreview">可加载当前可用字段继续编辑，已发布版本不会被修改。</span>
              <el-button v-if="canEnterDesignerLivePreview" type="primary" size="small" @click="runDesignerAction(enterDesignerLivePreview)">加载当前可用字段</el-button>
              <span v-else>{{ designerReadOnlyReason || '当前只能查看此版本保存的资源。' }}</span>
            </div>
          </template>
        </field-picker-panel>
      </aside>
      <div
        v-show="!varPanelCollapsed"
        class="se-resizer"
        role="separator"
        aria-orientation="vertical"
        aria-label="调整字段列表宽度"
        :aria-valuemin="varPanelMinWidth"
        :aria-valuemax="varPanelMaxWidth"
        :aria-valuenow="varPanelWidth"
        @mousedown="startVarPanelResize"
        @touchstart.prevent="startVarPanelTouchResize"
        @dblclick="resetVarPanelWidth"
      >
        <span class="se-resizer-handle" />
      </div>

      <!-- 主编辑区 -->
      <div class="se-editor-area">
        <!-- 状态栏 -->
        <div class="se-statusbar">
          <el-switch
            v-model="showEngineReferences"
            size="small"
            active-text="显示引用名称与版本"
            aria-label="显示引用名称与版本"
          />
          <span v-if="showEngineReferences" class="se-line-info">{{ scriptReferences.length }} 处引用</span>
          <span class="se-statusbar-spacer" />
          <span v-if="compileStatus === 1" class="se-status-item status-ok">
            <el-icon><el-icon-success /></el-icon> 脚本有效
          </span>
          <span
            v-else-if="compileStatus === 2"
            class="se-status-item status-err"
          >
            <el-icon><el-icon-error /></el-icon>
            {{ compileMessage || '脚本错误' }}
          </span>
          <span v-else class="se-status-item">
            <el-icon><el-icon-info /></el-icon> 未验证
          </span>
          <span class="se-line-info"
            >{{ lineCount }} 行 / {{ script.length }} 字符</span
          >
        </div>

        <!-- 编辑器 -->
        <div class="se-editor-container">
          <MonacoEditor
            ref="monacoEditorComponent"
            v-model:value="script"
            language="ql"
            height="100%"
            :references="qlReferenceItems"
            :reference-matches="scriptReferences"
            :functions="projectFunctions"
            :show-reference-decorations="showEngineReferences"
            :reference-versions="referenceVersions"
            :version-references="scriptVersionReferences"
            :reference-catalog-mode="referenceCatalogMode"
            @editor-ready="onEditorReady"
            @reference-select="recordScriptReference"
            :options="editorOptions"
          />
        </div>

        <!-- 底部提示 -->
        <div class="se-footer">
          <span class="se-footer-tip">
            <el-icon><el-icon-edit-outline /></el-icon> 双击左侧内容插入引用，也可直接编辑代码；名称和版本仅用于展示，复制与保存仍为原始脚本
          </span>
        </div>
      </div>
    </div>

    <!-- 测试弹窗 -->
    <designer-test-dialog
      @locate="locateDesignerIssue"
      v-model:visible="testVisible"
      :execution-source="designerTestExecutionSource"
      :execution-source-name="viewRevisionLabel"
      :definition-id="definitionId"
      :project-id="projectIdForRefs"
      model-type="SCRIPT"
      :model-json-provider="serializeDesignerDraft"
      :params-template="testParamsTemplate"
    />
  </div>
</template>

<script>
import FieldPickerPanel from '@/components/common/FieldPickerPanel.vue'
import { fieldIdentity } from '@/utils/fieldPicker'
import { recentFieldKeys, rememberField } from '@/utils/fieldPickerHistory'
import { preferredFieldPageSize, setPreferredFieldPageSize } from '@/utils/fieldPickerPreferences'
import { parseDesignerModelJson } from '@/utils/designerModelJson'
import { markRaw } from 'vue'
import {
  EditPen as ElIconEditOutline,
  CircleCheckFilled as ElIconSuccess,
  CircleCloseFilled as ElIconError,
  InfoFilled as ElIconInfo,
  Back as ElIconBack,
  Document as ElIconDocument,
  Cpu as ElIconCpu,
  VideoPlay as ElIconVideoPlay,
} from '@element-plus/icons-vue'
import varPickerMixin from '@/mixins/varPickerMixin'
import ruleDraftMixin from '@/mixins/ruleDraftMixin'
import MonacoEditor from '@/components/MonacoEditor'
import { listFieldVersions } from '@/api/fieldPickerVersion'
import {
  collectQlScriptReferences,
  createQlReferenceCatalog,
  buildQlReferenceDecorations,
  qlReferenceVersionIdentity,
  qlReferenceVersionKey,
} from '@/utils/qlScriptReferences'
import DesignerTestDialog from '@/components/common/DesignerTestDialog.vue'
import RuleDraftReadOnly from '@/components/rule/RuleDraftReadOnly.vue'
import RuleDesignerActionBar from '@/components/rule/RuleDesignerActionBar.vue'
import {
  buildSampleParamsFromCodes,
  collectScriptInputCodes,
} from '@/utils/testSampleParams'
import {
  buildQlCompletionItems,
  buildQlHover,
  buildQlSignatureHelp,
} from '@/utils/qlEditorIntelligence'

const VAR_PANEL_WIDTH_KEY = 'qlexpress.scriptEditor.contentPickerWidth'
const DEFAULT_VAR_PANEL_WIDTH = 560

export default {
  data() {
    return {
      definitionId: null,
      contentLoaded: false,
      script: '',
      compileStatus: 0,
      compileMessage: '',
      varPanelCollapsed: false,
      varSearchKey: '',
      scriptPickerCategory: 'all',
      scriptPickerSelection: '',
      /** 脚本中引用的变量映射：{refCode, varId}，与 modelJson.scriptVarRefs 保持同步 */
      scriptVarRefs: [],
      testVisible: false,
      testParamsTemplate: {},
      testParamsJson: '{}',
      testResult: null,
      monacoEditor: null,
      qlProviderDisposables: [],
      showEngineReferences: true,
      referenceDecorationIds: [],
      referenceVersions: {},
      referenceVersionRequest: 0,
      pendingVarInsertions: [],
      varPanelWidth: DEFAULT_VAR_PANEL_WIDTH,
      varPanelMinWidth: 420,
      varPanelMaxWidth: 760,
      editorMinWidth: 320,
      resizingVarPanel: false,
      resizeBodyCursor: '',
      resizeBodyUserSelect: '',
      scriptVarPageSize: preferredFieldPageSize.value,
      ElIconBack: markRaw(ElIconBack),
      ElIconDocument: markRaw(ElIconDocument),
      ElIconCpu: markRaw(ElIconCpu),
      ElIconVideoPlay: markRaw(ElIconVideoPlay),
    }
  },
  components: { FieldPickerPanel,
    RuleDesignerActionBar,
    RuleDraftReadOnly,
    DesignerTestDialog,
    MonacoEditor,
    ElIconEditOutline,
    ElIconSuccess,
    ElIconError,
    ElIconInfo,
  },
  name: 'ScriptEditor',
  mixins: [varPickerMixin, ruleDraftMixin],
  computed: {
    qlReferenceItems() { return markRaw(this.qlIntelligenceRefs()) },
    scriptReferenceCatalog() { return markRaw(createQlReferenceCatalog(this.varPickerOptions, this.projectFunctions, this.scriptVarRefs)) },
    scriptReferences() {
      return markRaw(collectQlScriptReferences(this.script, this.varPickerOptions, this.projectFunctions, this.scriptVarRefs, this.scriptVersionReferences, this.scriptReferenceCatalog))
    },
    varPanelStyle() {
      if (this.varPanelCollapsed) return {}
      return {
        width: this.varPanelWidth + 'px',
        minWidth: this.varPanelWidth + 'px',
        maxWidth: this.varPanelWidth + 'px',
      }
    },
    editorOptions() {
      return {
        fontSize: 13,
        lineHeight: 22,
        fontFamily: "'Courier New', Consolas, monospace",
        lineNumbers: 'on',
        minimap: { enabled: false },
        scrollBeyondLastLine: false,
        wordWrap: 'on',
        tabSize: 2,
        insertSpaces: true,
        formatOnPaste: true,
        automaticLayout: true,
      }
    },
    lineCount() {
      return (this.script.match(/\n/g) || []).length + 1
    },
    scriptPickerItems() {
      return [...this.varPickerOptions, ...(this.projectFunctions || []).map(fn => ({
        varCode: fn.funcCode + '()', refCode: fn.funcCode, _varId: fn.id, _refType: 'FUNCTION',
        _function: true, function: fn, catalogMode: fn.catalogMode,
        varLabel: fn.funcName, varType: fn.returnType || 'FUNCTION', version: fn.currentVersion ?? fn.version,
      }))].map(item => {
        const version = this.referenceVersions?.[qlReferenceVersionKey({ option: item }, this.referenceCatalogMode)]?.version
        return version == null ? item : { ...item, version }
      })
    },
    scriptPickerSelectedKeys() { return this.scriptVarRefs.map(reference => `${reference.refType}:${reference.varId}`) },
    scriptPickerRecentKeys() { return recentFieldKeys.value },
  },
  watch: {
    scriptReferences: {
      flush: 'post',
      handler() { this.loadReferenceVersions() },
    },
    showEngineReferences() {
      this.referenceVersionRequest++
      this.referenceVersions = {}
      this.loadReferenceVersions()
    },
    referenceCatalogKey() { this.resetReferenceVersions() },
    referenceCatalogMode(mode) {
      this.resetReferenceVersions()
      if (mode === 'LIVE') {
        this.scriptPickerCategory = 'all'
        this.varSearchKey = ''
        this.scriptPickerSelection = ''
      }
    },
    projectRefs() { this.resetReferenceVersions() },
    projectFunctions() { this.resetReferenceVersions() },

  },
  created() {
    this.definitionId = this.$route.params.id
    this.loadContent()
  },
  mounted() {
    this.restoreVarPanelWidth()
  },
  beforeUnmount() {
    this.referenceVersionRequest++
    this.monacoEditor?.deltaDecorations(this.referenceDecorationIds, [])
    this.stopVarPanelResize()
    this.disposeQlProviders()
  },
  methods: {
    refreshReferenceDecorations() {
      const editor = this.monacoEditor
      const model = editor?.getModel()
      if (!model) return
      const decorations = this.showEngineReferences
        ? buildQlReferenceDecorations(this.scriptReferences, model, this.referenceVersions, this.referenceCatalogMode) : []
      this.referenceDecorationIds = editor.deltaDecorations(this.referenceDecorationIds, decorations)
    },
    resetReferenceVersions() {
      this.referenceVersionRequest++
      this.referenceVersions = {}
      this.$nextTick(() => { this.loadReferenceVersions() })
    },
    async loadReferenceVersions() {
      if (!this.showEngineReferences || this.referenceCatalogMode !== 'LIVE') return
      const request = this.referenceVersionRequest
      const pending = []
      for (const reference of this.scriptReferences) {
        const key = qlReferenceVersionKey(reference, this.referenceCatalogMode)
        if (!key || this.referenceVersions[key]) continue
        this.referenceVersions[key] = { loading: true }
        pending.push((async () => {
          try {
            const response = await listFieldVersions(qlReferenceVersionIdentity(reference, this.referenceCatalogMode))
            if (request !== this.referenceVersionRequest) return
            this.referenceVersions[key] = { version: response.data?.[0]?.displayVersion ?? response.data?.[0]?.version }
          } catch {
            if (request === this.referenceVersionRequest) this.referenceVersions[key] = { error: true }
          }
        })())
      }
      await Promise.all(pending)
    },
    restoreVarPanelWidth() {
      try {
        const saved = Number(
          window.localStorage &&
            window.localStorage.getItem(VAR_PANEL_WIDTH_KEY)
        )
        if (Number.isFinite(saved) && saved > 0) {
          this.varPanelWidth = this.clampVarPanelWidth(saved)
        }
      } catch (e) {
        // localStorage 不可用时保持默认宽度
      }
    },
    persistVarPanelWidth() {
      try {
        if (window.localStorage) {
          window.localStorage.setItem(
            VAR_PANEL_WIDTH_KEY,
            String(this.varPanelWidth)
          )
        }
      } catch (e) {
        // 忽略持久化失败，拖拽本身仍然生效
      }
    },
    startVarPanelResize(event) {
      this.beginVarPanelResize(event && event.clientX, event)
    },
    startVarPanelTouchResize(event) {
      const touch = event && event.touches && event.touches[0]
      if (!touch) return
      this.beginVarPanelResize(touch.clientX, event)
    },
    beginVarPanelResize(clientX, event) {
      if (this.varPanelCollapsed || clientX == null) return
      if (event && event.preventDefault) event.preventDefault()
      this.resizingVarPanel = true
      this.resizeBodyCursor = document.body.style.cursor
      this.resizeBodyUserSelect = document.body.style.userSelect
      document.body.style.cursor = 'col-resize'
      document.body.style.userSelect = 'none'
      this.updateVarPanelWidth(clientX)
      window.addEventListener('mousemove', this.onVarPanelResize)
      window.addEventListener('mouseup', this.stopVarPanelResize)
      window.addEventListener('touchmove', this.onVarPanelTouchResize, {
        passive: false,
      })
      window.addEventListener('touchend', this.stopVarPanelResize)
      window.addEventListener('touchcancel', this.stopVarPanelResize)
    },
    onVarPanelResize(event) {
      this.updateVarPanelWidth(event.clientX)
    },
    onVarPanelTouchResize(event) {
      const touch = event && event.touches && event.touches[0]
      if (!touch) return
      if (event && event.preventDefault) event.preventDefault()
      this.updateVarPanelWidth(touch.clientX)
    },
    stopVarPanelResize() {
      window.removeEventListener('mousemove', this.onVarPanelResize)
      window.removeEventListener('mouseup', this.stopVarPanelResize)
      window.removeEventListener('touchmove', this.onVarPanelTouchResize)
      window.removeEventListener('touchend', this.stopVarPanelResize)
      window.removeEventListener('touchcancel', this.stopVarPanelResize)
      if (!this.resizingVarPanel) return
      this.resizingVarPanel = false
      document.body.style.cursor = this.resizeBodyCursor || ''
      document.body.style.userSelect = this.resizeBodyUserSelect || ''
      this.persistVarPanelWidth()
    },
    updateVarPanelWidth(clientX) {
      const body = this.$refs.designerBody
      if (!body || clientX == null) return
      const rect = body.getBoundingClientRect()
      const maxWidth = this.panelMaxWidthForBody(rect.width)
      this.varPanelWidth = this.clampVarPanelWidth(
        clientX - rect.left,
        maxWidth
      )
    },
    panelMaxWidthForBody(bodyWidth) {
      if (!bodyWidth) return this.varPanelMaxWidth
      return Math.max(
        this.varPanelMinWidth,
        Math.min(this.varPanelMaxWidth, bodyWidth - this.editorMinWidth - 12)
      )
    },
    clampVarPanelWidth(width, maxWidth = this.varPanelMaxWidth) {
      return Math.min(
        Math.max(Math.round(width), this.varPanelMinWidth),
        maxWidth
      )
    },
    resetVarPanelWidth() {
      this.varPanelWidth = this.clampVarPanelWidth(DEFAULT_VAR_PANEL_WIDTH)
      this.persistVarPanelWidth()
    },
    toggleVarPanel() {
      this.varPanelCollapsed = !this.varPanelCollapsed
    },
    async loadContent() {
      try {
        if (this.draftGuardPromise) await this.draftGuardPromise
        const content = this.viewRevision
        if (content) {
          this.script = ''
          this.scriptVarRefs = []
          if (content.modelJson && content.modelJson !== '{}') {
            try {
              const model = parseDesignerModelJson(content.modelJson)
              if (model.script) this.script = model.script
              // 加载脚本变量引用映射（用于通过 varId 同步变量编码变更）
              if (Array.isArray(model.scriptVarRefs)) {
                this.scriptVarRefs = model.scriptVarRefs
              }
            } catch (e) {
              this.script = content.modelJson
            }
          } else if (content.compiledScript) {
            this.script = content.compiledScript
          }
          this.compileStatus = content.compiledScript ? 1 : 0
          this.compileMessage = ''
        }
      } catch (e) {
        this.$message.error('加载内容失败: ' + (e.message || '未知错误'))
      } finally {
        this.contentLoaded = true
        this.$nextTick(() =>
          this.initializeDesignerDraftTracking(this.serializeDesignerDraft())
        )
      }
    },
    buildModelJson() {
      this.syncScriptVarRefsFromScript()
      return {
        script: this.script,
        scriptVarRefs: this.scriptVarRefs,
      }
    },
    serializeDesignerDraft() {
      const refs = (this.scriptVarRefs || []).filter((ref) => {
        if (ref.varId == null || !ref.refType || !ref.refCode) return false
        const regex = new RegExp(
          '(?<![\\p{L}\\p{N}_$])' + this.escapeRegex(ref.refCode) + '(?![\\p{L}\\p{N}_$])',
          'u'
        )
        return regex.test(this.script || '')
      })
      return JSON.stringify({ script: this.script, scriptVarRefs: refs })
    },
    async performDesignerSave() {
      // 保存前：从脚本中提取实际引用的变量，更新 scriptVarRefs
      const modelJson = JSON.stringify(this.buildModelJson())
      const result = await this.saveDraftModel(modelJson)
      if (!result) return false
      this.compileStatus = result.compileSuccess ? 1 : 2
      this.compileMessage = result.compileMessage || ''
      this.$message.success('草稿已保存')
      this.refreshProjectRefs()
      return result
    },
    handleSave() {
      return this.runDesignerAction(async () => {
        if (this.designerBusy) return false
        this.designerBusy = true
        try { return await this.performDesignerSave() } finally { this.designerBusy = false }
      })
    },
    handleCompile() {
      return this.runDesignerAction(async () => {
        if (this.designerBusy) return false
        this.designerBusy = true
        try { return await this.compileDesignerDraft() } finally { this.designerBusy = false }
      })
    },
    async handleTest() {
      if (!this.ensureDesignerReadyForTest()) return
      this.testParamsTemplate = this.buildTestParamsTemplate()
      const codes = collectScriptInputCodes(this.script, this.projectRefs)
      this.testParamsJson = JSON.stringify(
        buildSampleParamsFromCodes(Array.from(codes), this.projectRefs),
        null,
        2
      )
      this.testResult = null
      this.testVisible = true
    },
    buildTestParamsTemplate() {
      const codes = collectScriptInputCodes(this.script, this.projectRefs)
      return buildSampleParamsFromCodes(Array.from(codes), this.projectRefs)
    },
    insertPickerContent(item) {
      this.scriptPickerSelection = fieldIdentity(item)
      rememberField(item)
      this.insertVar(item)
    },
    changePickerPageSize(size) { this.scriptVarPageSize = size; setPreferredFieldPageSize(size) },
    insertVar(v) {
      if (!v) return
      if (!this.monacoEditor) {
        this.pendingVarInsertions.push(v)
        return
      }
      const code = v.varCode
      const editor = this.monacoEditor
      const selection = editor.getSelection()
      editor.executeEdits('insert-var', [
        {
          range: selection,
          text: code,
          forceMoveMarkers: true,
        },
      ])
      // 同步记录引用的 varId（以最后一次插入同名变量时的 _varId 为准）
      const selectedRefType = v._refType || v.refType || null
      const refCode = selectedRefType === 'FUNCTION' ? v.refCode : code
      if (v._varId != null && selectedRefType) {
        const existing = this.scriptVarRefs.find((r) => r.refCode === refCode)
        if (existing) {
          existing.varId = v._varId
          existing.refType = selectedRefType
        } else {
          this.scriptVarRefs.push({
            refCode,
            varId: v._varId,
            refType: selectedRefType,
          })
        }
      }
      this.$nextTick(() => editor.focus())
    },
    onEditorReady(editor) {
      this.monacoEditor = editor
      const pending = this.pendingVarInsertions.splice(0)
      pending.forEach((variable) => this.insertVar(variable))
    },
    qlIntelligenceRefs() {
      return (this.projectRefs || []).map((ref) => ({
        ...ref,
        varLabel:
          ref.varLabel ||
          (ref.refLabel && ref.refLabel.label) ||
          ref.displayName ||
          ref.refCode,
      }))
    },
    recordScriptReference(reference) {
      if (!reference || reference.varId == null || !reference.refType) return
      const existing = this.scriptVarRefs.find(
        (item) => item.refCode === reference.refCode
      )
      if (existing) {
        existing.varId = reference.varId
        existing.refType = reference.refType
        return
      }
      this.scriptVarRefs.push({ ...reference })
    },
    registerQlProviders() {
      this.disposeQlProviders()
      const monaco = window.monaco
      if (!monaco || !monaco.languages) return
      const commandId = `tianshu.record-script-reference.${this.$.uid}`
      if (monaco.editor && monaco.editor.registerCommand) {
        this.qlProviderDisposables.push(
          monaco.editor.registerCommand(commandId, (_accessor, reference) => {
            this.recordScriptReference(reference)
          })
        )
      }
      if (monaco.languages.registerCompletionItemProvider) {
        this.qlProviderDisposables.push(
          monaco.languages.registerCompletionItemProvider('ql', {
            triggerCharacters: ['.'],
            provideCompletionItems: (model, position) => {
              const line = model.getLineContent(position.lineNumber)
              const linePrefix = line.slice(0, position.column - 1)
              const word = model.getWordUntilPosition(position)
              const range = {
                startLineNumber: position.lineNumber,
                endLineNumber: position.lineNumber,
                startColumn: word.startColumn,
                endColumn: word.endColumn,
              }
              const suggestions = buildQlCompletionItems(
                this.qlIntelligenceRefs(),
                this.projectFunctions,
                linePrefix
              ).map((item) => ({
                label: item.label,
                insertText: item.insertText,
                detail: item.detail,
                documentation: { value: item.documentation || '' },
                range,
                kind:
                  item.kind === 'FUNCTION'
                    ? monaco.languages.CompletionItemKind.Function
                    : monaco.languages.CompletionItemKind.Field,
                insertTextRules: item.snippet
                  ? monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet
                  : undefined,
                command:
                  item.stableReference && monaco.editor.registerCommand
                    ? {
                        id: commandId,
                        title: '记录稳定字段引用',
                        arguments: [item.stableReference],
                      }
                    : undefined,
              }))
              return { suggestions }
            },
          })
        )
      }
      if (monaco.languages.registerHoverProvider) {
        this.qlProviderDisposables.push(
          monaco.languages.registerHoverProvider('ql', {
            provideHover: (model, position) => {
              const hover = buildQlHover(
                model.getLineContent(position.lineNumber),
                position.column,
                this.qlIntelligenceRefs()
              )
              if (!hover) return null
              return {
                contents: [
                  { value: `**${hover.label}**` },
                  { value: `\`${hover.refCode}\`` },
                  {
                    value: `类型：\`${hover.valueType}\`  ·  来源：\`${
                      hover.refType || 'REFERENCE'
                    }\``,
                  },
                ],
              }
            },
          })
        )
      }
      if (monaco.languages.registerSignatureHelpProvider) {
        this.qlProviderDisposables.push(
          monaco.languages.registerSignatureHelpProvider('ql', {
            signatureHelpTriggerCharacters: ['(', ','],
            provideSignatureHelp: (model, position) => {
              const line = model.getLineContent(position.lineNumber)
              const signature = buildQlSignatureHelp(
                line.slice(0, position.column - 1),
                this.projectFunctions
              )
              if (!signature) return null
              return {
                value: {
                  signatures: [
                    {
                      label: signature.label,
                      parameters: signature.parameters,
                    },
                  ],
                  activeSignature: 0,
                  activeParameter: signature.activeParameter,
                },
                dispose() {},
              }
            },
          })
        )
      }
    },
    disposeQlProviders() {
      this.qlProviderDisposables.forEach((disposable) => {
        if (disposable && disposable.dispose) disposable.dispose()
      })
      this.qlProviderDisposables = []
    },
    /**
     * 仅清理通过选择器显式记录、但已不再出现在脚本中的引用。
     * 禁止扫描资源目录后通过 refCode 反向推断 ID。
     */
    syncScriptVarRefsFromScript() {
      if (!this.script) return
      const script = this.script
      const matched = (this.scriptVarRefs || []).filter((ref) => {
        if (ref.varId == null || !ref.refType || !ref.refCode) return false
        // 标识符边界同时支持中文与 $，并防止匹配到更长的字段名。
        const regex = new RegExp(
          '(?<![\\p{L}\\p{N}_$])' + this.escapeRegex(ref.refCode) + '(?![\\p{L}\\p{N}_$])',
          'u'
        )
        return regex.test(script)
      })
      this.scriptVarRefs = matched
    },
    escapeRegex(str) {
      return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    },
    /**
     * 同步 modelJson.scriptVarRefs 中的变量引用。
     * 当 projectRefs 加载完成后，用 varId 在 projectRefs 中查找最新的 refCode，
     * 若 refCode 与脚本中实际使用的不同，说明变量编码被修改了，同步更新 scriptVarRefs。
     */
    _syncModelVarRefs() {
      if (!this.scriptVarRefs || !this.scriptVarRefs.length) return
      let changed = false
      this.scriptVarRefs.forEach((ref) => {
        if (!ref.varId) return
        const newRef = this.findRefByVarId(ref.varId, ref.refType)
        if (newRef && newRef.refCode !== ref.refCode) {
          ref.refCode = newRef.refCode
          ref.refType = newRef.refType
          changed = true
        } else if (newRef && ref.refType !== newRef.refType) {
          ref.refType = newRef.refType
          changed = true
        }
      })
      if (changed) this.$forceUpdate()
    },
  },
}
</script>

<style lang="scss" scoped>
.se-frozen-empty { display: flex; flex-direction: column; align-items: center; gap: 12px; line-height: 1.6; white-space: normal; }
.se-frozen-empty strong { color: var(--tianshu-text-primary); font-weight: 500; }
.se-designer {
  position: relative;
  background: var(--tianshu-bg-workspace);
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: 4px;
  box-shadow: var(--tianshu-shadow-small);
}
.se-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--tianshu-bg-surface);
  padding: 12px 20px;
  box-shadow: var(--tianshu-shadow-small);
  flex-wrap: wrap;
  gap: 8px;
  flex-shrink: 0;
}
.se-title-area {
  display: flex;
  align-items: center;
}
.se-title-icon {
  font-size: 18px;
  color: var(--el-color-primary);
  margin-right: 8px;
}
.se-title {
  font-size: 16px;
  font-weight: bold;
  color: var(--tianshu-text-primary);
}
.se-toolbar {
  display: flex;
  align-items: center;
  gap: 6px;
}
.se-body {
  flex: 1;
  display: flex;
  margin: 12px;
  gap: 0;
  min-height: 0;
  overflow: hidden;
}
.se-var-panel {
  width: 560px;
  min-width: 420px;
  background: var(--tianshu-bg-surface);
  border-radius: 6px 0 0 6px;
  border: 1px solid var(--tianshu-border-subtle);
  border-right: none;
  display: flex;
  flex-direction: column;
  transition: width 0.2s, min-width 0.2s;
  overflow: hidden;
  &.collapsed {
    width: 36px !important;
    min-width: 36px !important;
    max-width: 36px !important;
  }
}
.se-var-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: var(--tianshu-bg-muted);
  border-bottom: 1px solid var(--tianshu-border-subtle);
  font-size: 13px;
  font-weight: 600;
  color: var(--tianshu-text-secondary);
  cursor: pointer;
  gap: 6px;
  i {
    color: var(--tianshu-text-tertiary);
  }
}
.se-picker-error { padding: 12px; color: var(--tianshu-danger-text); }
.se-resizer {
  flex: 0 0 8px;
  cursor: col-resize;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--tianshu-bg-surface);
  border-top: 1px solid var(--tianshu-border-subtle);
  border-bottom: 1px solid var(--tianshu-border-subtle);
  user-select: none;
  z-index: 2;
  transition: background 0.15s;
  &:hover,
  .se-body.is-resizing & {
    background: var(--tianshu-bg-active);
  }
  &:hover .se-resizer-handle,
  .se-body.is-resizing & .se-resizer-handle {
    background: var(--el-color-primary);
  }
}
.se-resizer-handle {
  width: 2px;
  height: 36px;
  border-radius: 2px;
  background: var(--tianshu-border);
  transition: background 0.15s;
}
.se-editor-area {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  border-radius: 0 6px 6px 0;
  border: 1px solid var(--tianshu-border-subtle);
  overflow: hidden;
}
.se-statusbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 6px 12px;
  flex-shrink: 0;
  background: var(--tianshu-bg-muted);
  border-bottom: 1px solid var(--tianshu-border-subtle);
}
.se-status-item {
  font-size: 11px;
  color: var(--tianshu-text-tertiary);
  display: flex;
  align-items: center;
  gap: 3px;
  &.status-ok {
    color: var(--tianshu-success-text);
  }
  &.status-err {
    color: var(--tianshu-danger-text);
  }
}
.se-statusbar-spacer {
  flex: 1;
}
.se-line-info {
  font-size: 11px;
  color: var(--tianshu-text-tertiary);
  font-family: 'Consolas', monospace;
}
.se-editor-container {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: var(--tianshu-bg-soft);
}
.se-editor-container :deep(.monaco-editor-container) {
  position: absolute;
  inset: 0;
  border: none;
  border-radius: 0;
}









.se-footer {
  display: flex;
  align-items: center;
  padding: 5px 12px;
  flex-shrink: 0;
  background: var(--tianshu-bg-muted);
  border-top: 1px solid var(--tianshu-border-subtle);
}
.se-footer-tip {
  font-size: 11px;
  color: var(--tianshu-text-secondary);
  display: flex;
  align-items: center;
  gap: 4px;
}
.test-hint {
  font-size: 12px;
  color: var(--tianshu-text-tertiary);
  margin-bottom: 8px;
}
.test-result {
  margin-top: 16px;
}
@media (max-width: 1100px) {
  .se-body { flex-direction: column; overflow-y: auto; }
  .se-editor-area { order: 1; flex: none; min-height: 320px; border-radius: 6px 6px 0 0; }
  .se-var-panel { order: 2; flex: none; width: 100% !important; min-width: 0 !important; max-width: none !important; height: 360px; border-left: 1px solid var(--tianshu-border-subtle); border-radius: 0 0 6px 6px; }
  .se-var-panel.collapsed { height: 36px; }
  .se-resizer { display: none; }
}
</style>
