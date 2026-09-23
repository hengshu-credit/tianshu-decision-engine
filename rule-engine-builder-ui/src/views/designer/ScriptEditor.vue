<template>
  <div class="se-designer uiue-compact-workbench uiue-compact-designer">
    <rule-draft-read-only
      :visible="!canEditDraft"
      :loading="!draftGuardLoaded"
      :load-error="Boolean(draftGuardError)"
      :revision-label="viewRevisionLabel"
      :revision-state="viewRevision ? viewRevision.state : ''"
      :can-fork="canForkViewRevision"
      :has-editable-draft="hasPendingDraft"
      :source-options="designerSourceOptions"
      :selected-source="selectedDesignerSource"
      :source-loading="designerSourcesLoading"
      @go-back="$router.back()"
      @retry="startViewedRevisionRefresh(true)"
      @go-lifecycle="goRuleLifecycle"
      @fork="forkViewRevision"
      @change-source="switchDesignerSource"
    />
    <!-- 顶部工具栏 -->
    <div class="se-header">
      <div class="se-title-area">
        <el-button
          link
          :icon="ElIconBack"
          aria-label="返回"
          title="返回"
          @click="$router.back()"
          style="color: var(--tianshu-text-secondary)"
        />
        <el-icon class="se-title-icon"><el-icon-edit-outline /></el-icon>
        <span class="se-title">QL脚本编辑器</span>
        <el-tag size="small" type="info" style="margin-left: 8px"
          >{{ lineCount }} 行</el-tag
        >
      </div>
      <div class="se-toolbar">
        <rule-designer-version-select
          v-if="canEditDraft && designerSourceOptions.length"
          :options="designerSourceOptions"
          :model-value="selectedDesignerSource"
          :loading="designerSourcesLoading"
          @change="switchDesignerSource"
          @delete="deleteDesignerSource"
          :disabled="designerBusy"
        />
        <rule-designer-action-bar
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

    <rule-designer-status :state="designerActionState" :field-count="varPickerOptions.length" :loading="loadingVars" :source-label="viewRevisionLabel" />
    <rule-designer-dialogs :choice="designerChoice" @resolve="resolveDesignerChoice" />



    <div
      ref="designerBody"
      class="se-body"
      :class="{ 'is-resizing': resizingVarPanel }"
    >
      <!-- 左侧变量面板 -->
      <div
        class="se-var-panel"
        :class="{ collapsed: varPanelCollapsed }"
        :style="varPanelStyle"
      >
        <div
          class="se-var-header"
          @click="toggleVarPanel"
        >
          <span v-if="!varPanelCollapsed"
            ><el-icon><el-icon-s-data /></el-icon> 项目变量</span
          >
          <app-icon :name="varPanelCollapsed ? 'ArrowRight' : 'ArrowLeft'" />
        </div>
        <div v-show="!varPanelCollapsed" class="se-var-list">
          <div
            v-if="loadingVars"
            style="text-align: center; padding: 20px; color: var(--tianshu-text-tertiary)"
          >
            <el-icon><el-icon-loading /></el-icon> 加载中...
          </div>
          <div
            v-else-if="varsLoadError"
            class="text-danger"
            style="text-align: center; padding: 20px"
          >
            <el-icon><el-icon-warning /></el-icon> 加载失败
            <el-button
              link
              size="small"
              @click="loadProjectVars($route.params.id)"
              style="display: block; margin: 6px auto 0"
              >重试</el-button
            >
          </div>
          <div
            v-else-if="varTree.length === 0"
            style="text-align: center; padding: 20px; color: #bbb"
          >
            <el-icon><el-icon-folder-opened /></el-icon> 暂无项目变量
          </div>
          <template v-else>
            <!-- 搜索过滤 -->
            <div class="se-var-search">
              <el-input
                v-model="varSearchKey"
                size="small"
                placeholder="搜索变量..."
                :prefix-icon="ElIconSearch"
                clearable
              />
            </div>
            <!-- 树形分组 -->
            <div v-for="cat in filteredVarTree" :key="cat.key" class="se-cat">
              <div class="se-cat-header" @click="toggleCat(cat.key)">
                <app-icon
                  :name="expandedCats[cat.key] ? 'CaretBottom' : 'CaretRight'"
                  class="se-toggle-icon"
                />
                <app-icon :name="cat.icon" class="se-cat-icon" />
                <span class="se-cat-label">{{ cat.label }}</span>
                <span class="se-cat-count">{{ countLeaves(cat) }}</span>
              </div>
              <div v-show="expandedCats[cat.key]" class="se-cat-body">
                <!-- 两级结构：一级分类 -> 直接叶子 -->
                <template v-if="!cat.hasSubGroups">
                  <div
                    v-for="v in pagedScriptList(cat.key, cat.children)"
                    :key="v.varCode"
                    class="se-var-item"
                    :title="'双击插入: ' + v.varCode"
                    @dblclick="insertVar(v)"
                  >
                    <el-tag
                      :type="varTypeColor(v.varType)"
                      size="small"
                      class="var-type-tag"
                      >{{ varTypeLabel(v.varType) }}</el-tag
                    >
                    <span class="var-code">{{ v.varCode }}</span>
                    <span class="var-label">{{ v.varLabel }}</span>
                  </div>
                  <el-pagination
                    v-if="scriptListNeedsPaging(cat.children)"
                    class="se-var-pager"
                    size="small"
                    layout="prev,pager,next"
                    :current-page="scriptListPage(cat.key)"
                    :page-size="scriptVarPageSize"
                    :total="cat.children.length"
                    @current-change="(p) => onScriptListPageChange(cat.key, p)"
                  />
                </template>
                <!-- 三级结构：一级分类 -> 二级组 -> 叶子 -->
                <template v-else>
                  <div
                    v-for="group in cat.children"
                    :key="cat.key + '.' + group.key"
                    class="se-group"
                  >
                    <div
                      class="se-group-header"
                      @click="toggleGroup(cat.key + '.' + group.key)"
                    >
                      <app-icon
                        :name="
                          expandedGroups[cat.key + '.' + group.key]
                            ? 'CaretBottom'
                            : 'CaretRight'
                        "
                        class="se-toggle-icon"
                      />
                      <span class="se-group-label">{{ group.label }}</span>
                      <span class="se-cat-count">{{
                        group.children.length
                      }}</span>
                    </div>
                    <div v-show="expandedGroups[cat.key + '.' + group.key]">
                      <div
                        v-for="v in pagedScriptList(
                          cat.key + '.' + group.key,
                          group.children
                        )"
                        :key="v.varCode"
                        class="se-var-item se-var-indent"
                        :title="'双击插入: ' + v.varCode"
                        @dblclick="insertVar(v)"
                      >
                        <el-tag
                          :type="varTypeColor(v.varType)"
                          size="small"
                          class="var-type-tag"
                          >{{ varTypeLabel(v.varType) }}</el-tag
                        >
                        <span class="var-code">{{ v.varCode }}</span>
                        <span class="var-label">{{ v.varLabel }}</span>
                      </div>
                      <el-pagination
                        v-if="scriptListNeedsPaging(group.children)"
                        class="se-var-pager se-var-pager--group"
                        size="small"
                        layout="prev,pager,next"
                        :current-page="
                          scriptListPage(cat.key + '.' + group.key)
                        "
                        :page-size="scriptVarPageSize"
                        :total="group.children.length"
                        @current-change="
                          (p) =>
                            onScriptListPageChange(cat.key + '.' + group.key, p)
                        "
                      />
                    </div>
                  </div>
                </template>
              </div>
            </div>
          </template>
        </div>
      </div>

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
          <span class="se-statusbar-spacer" />
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
            @editor-ready="onEditorReady"
            :options="editorOptions"
          />
        </div>

        <!-- 底部提示 -->
        <div class="se-footer">
          <span class="se-footer-tip">
            <el-icon><el-icon-edit-outline /></el-icon> 直接编写 QLExpress
            脚本，保存后即可用于规则执行
          </span>
        </div>
      </div>
    </div>

    <!-- 测试弹窗 -->
    <designer-test-dialog
      v-model:visible="testVisible"
      :definition-id="definitionId"
      :project-id="projectIdForRefs"
      model-type="SCRIPT"
      :model-json-provider="serializeDesignerDraft"
      :params-template="testParamsTemplate"
    />
  </div>
</template>

<script>
import { markRaw } from 'vue'
import {
  EditPen as ElIconEditOutline,
  DataAnalysis as ElIconSData,
  Loading as ElIconLoading,
  Warning as ElIconWarning,
  FolderOpened as ElIconFolderOpened,
  CircleCheckFilled as ElIconSuccess,
  CircleCloseFilled as ElIconError,
  InfoFilled as ElIconInfo,
  Back as ElIconBack,
  Document as ElIconDocument,
  Cpu as ElIconCpu,
  VideoPlay as ElIconVideoPlay,
  Search as ElIconSearch,
} from '@element-plus/icons-vue'
import varPickerMixin from '@/mixins/varPickerMixin'
import ruleDraftMixin from '@/mixins/ruleDraftMixin'
import MonacoEditor from '@/components/MonacoEditor'
import DesignerTestDialog from '@/components/common/DesignerTestDialog.vue'
import RuleDraftReadOnly from '@/components/rule/RuleDraftReadOnly.vue'
import RuleDesignerVersionSelect from '@/components/rule/RuleDesignerVersionSelect.vue'
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

const VAR_PANEL_WIDTH_KEY = 'qlexpress.scriptEditor.varPanelWidth'
const DEFAULT_VAR_PANEL_WIDTH = 300

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
      expandedCats: {},
      expandedGroups: {},
      /** 脚本中引用的变量映射：{refCode, varId}，与 modelJson.scriptVarRefs 保持同步 */
      scriptVarRefs: [],
      testVisible: false,
      testParamsTemplate: {},
      testParamsJson: '{}',
      testResult: null,
      monacoEditor: null,
      qlProviderDisposables: [],
      pendingVarInsertions: [],
      varPanelWidth: DEFAULT_VAR_PANEL_WIDTH,
      varPanelMinWidth: 220,
      varPanelMaxWidth: 560,
      editorMinWidth: 420,
      resizingVarPanel: false,
      resizeBodyCursor: '',
      resizeBodyUserSelect: '',
      scriptVarPageSize: 100,
      scriptVarPages: {},
      ElIconBack: markRaw(ElIconBack),
      ElIconDocument: markRaw(ElIconDocument),
      ElIconCpu: markRaw(ElIconCpu),
      ElIconVideoPlay: markRaw(ElIconVideoPlay),
      ElIconSearch: markRaw(ElIconSearch),
    }
  },
  components: {
    RuleDesignerActionBar,
    RuleDraftReadOnly,
    RuleDesignerVersionSelect,
    DesignerTestDialog,
    MonacoEditor,
    ElIconEditOutline,
    ElIconSData,
    ElIconLoading,
    ElIconWarning,
    ElIconFolderOpened,
    ElIconSuccess,
    ElIconError,
    ElIconInfo,
  },
  name: 'ScriptEditor',
  mixins: [varPickerMixin, ruleDraftMixin],
  computed: {
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
    /** 将 varPickerOptions + projectFunctions 构建为三级树形结构 */
    varTree() {
      const tree = []
      const refs = this.varPickerOptions

      const standalone = refs.filter(
        (v) => v._ref && v._ref.category === 'standalone'
      )
      if (standalone.length) {
        tree.push({
          key: '__standalone__',
          label: '普通变量',
          icon: 'DataAnalysis',
          hasSubGroups: false,
          children: standalone,
        })
      }

      const constRefs = refs.filter(
        (v) => v._ref && v._ref.category === 'constant'
      )
      if (constRefs.length) {
        const byGroup = {}
        constRefs.forEach((v) => {
          const gc = v._ref.groupCode || '_ungrouped'
          const gl = v._ref.groupLabel || gc
          if (!byGroup[gc]) byGroup[gc] = { key: gc, label: gl, children: [] }
          byGroup[gc].children.push(v)
        })
        tree.push({
          key: '__constant__',
          label: '常量',
          icon: 'Collection',
          hasSubGroups: true,
          children: Object.values(byGroup),
        })
      }

      const objRefs = refs.filter((v) => v._ref && v._ref.category === 'object')
      if (objRefs.length) {
        const byObj = {}
        objRefs.forEach((v) => {
          const oc = v._ref.objectCode || '_ungrouped'
          const ol = v._ref.objectLabel || oc
          if (!byObj[oc]) byObj[oc] = { key: oc, label: ol, children: [] }
          byObj[oc].children.push(v)
        })
        tree.push({
          key: '__object__',
          label: '对象',
          icon: 'Files',
          hasSubGroups: true,
          children: Object.values(byObj),
        })
      }

      const modelRefs = refs.filter((v) => v._ref && v._ref.category === 'model')
      if (modelRefs.length) {
        tree.push({
          key: '__model__',
          label: '模型输出',
          icon: 'DataAnalysis',
          hasSubGroups: false,
          children: modelRefs,
        })
      }

      if (this.projectFunctions && this.projectFunctions.length) {
        tree.push({
          key: '__function__',
          label: '自定义函数',
          icon: 'ScaleToOriginal',
          hasSubGroups: false,
          children: this.projectFunctions.map((f) => ({
            varCode: f.funcCode + '()',
            refCode: f.funcCode,
            _varId: f.id,
            _refType: 'FUNCTION',
            varLabel: f.funcName,
            varType: 'FUNC',
          })),
        })
      }

      return tree
    },
    /** 搜索过滤后的树 */
    filteredVarTree() {
      const kw = (this.varSearchKey || '').trim().toLowerCase()
      if (!kw) return this.varTree
      return this.varTree
        .map((cat) => {
          if (!cat.hasSubGroups) {
            const filtered = cat.children.filter(
              (v) =>
                (v.varCode && v.varCode.toLowerCase().includes(kw)) ||
                (v.varLabel && v.varLabel.toLowerCase().includes(kw))
            )
            return filtered.length ? { ...cat, children: filtered } : null
          }
          const filteredGroups = cat.children
            .map((group) => {
              const filtered = group.children.filter(
                (v) =>
                  (v.varCode && v.varCode.toLowerCase().includes(kw)) ||
                  (v.varLabel && v.varLabel.toLowerCase().includes(kw))
              )
              return filtered.length ? { ...group, children: filtered } : null
            })
            .filter(Boolean)
          return filteredGroups.length
            ? { ...cat, children: filteredGroups }
            : null
        })
        .filter(Boolean)
    },
  },
  watch: {
    varSearchKey() {
      this.scriptVarPages = {}
    },
    varTree: {
      deep: true,
      immediate: true,

      handler(tree) {
        if (!tree || !tree.length) return
        const cats = { ...this.expandedCats }
        const groups = { ...this.expandedGroups }
        tree.forEach((cat) => {
          if (cats[cat.key] === undefined) cats[cat.key] = true
          if (cat.hasSubGroups) {
            cat.children.forEach((g) => {
              const gk = cat.key + '.' + g.key
              if (groups[gk] === undefined) groups[gk] = true
            })
          }
        })
        this.expandedCats = cats
        this.expandedGroups = groups
      },
    },
  },
  created() {
    this.definitionId = this.$route.params.id
    this.loadContent()
  },
  mounted() {
    this.restoreVarPanelWidth()
  },
  beforeUnmount() {
    this.stopVarPanelResize()
    this.disposeQlProviders()
  },
  methods: {
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
    toggleCat(key) {
      this.expandedCats[key] = !this.expandedCats[key]
    },
    toggleGroup(key) {
      this.expandedGroups[key] = !this.expandedGroups[key]
    },
    /** 统计一级分类下叶子节点总数 */
    countLeaves(cat) {
      if (!cat.hasSubGroups) return cat.children.length
      return cat.children.reduce((sum, g) => sum + g.children.length, 0)
    },
    scriptListNeedsPaging(list) {
      return Array.isArray(list) && list.length > this.scriptVarPageSize
    },
    scriptListPage(key) {
      return this.scriptVarPages[key] || 1
    },
    pagedScriptList(key, list) {
      if (!this.scriptListNeedsPaging(list)) return list || []
      const page = this.scriptListPage(key)
      const start = (page - 1) * this.scriptVarPageSize
      return list.slice(start, start + this.scriptVarPageSize)
    },
    onScriptListPageChange(key, page) {
      this.scriptVarPages[key] = page
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
              const model = JSON.parse(content.modelJson)
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
    async doTest() {
      let params = {}
      try {
        params = JSON.parse(this.testParamsJson || '{}')
      } catch (e) {
        this.$message.error('参数 JSON 格式错误')
        return
      }
      const res = await this.executeDesignerPreview(params, 'SCRIPT')
      this.testResult = res && res.data ? res.data : res
    },
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
      if (typeof this.registerQlProviders === 'function') {
        this.registerQlProviders()
      }
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
.se-designer {
  position: relative;
  background: var(--tianshu-bg-workspace);
  height: calc(100vh - 82px);
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
  width: 300px;
  min-width: 220px;
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
.se-var-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  overscroll-behavior: contain;
  padding: 0;
}
.se-var-search {
  padding: 6px 8px;
  border-bottom: 1px solid var(--tianshu-border-subtle);
  position: sticky;
  top: 0;
  background: var(--tianshu-bg-surface);
  z-index: 1;
}
.se-cat-header {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 7px 10px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
  color: var(--tianshu-text-primary);
  background: var(--tianshu-bg-muted);
  border-bottom: 1px solid var(--tianshu-border-subtle);
  user-select: none;
  &:hover {
    background: var(--tianshu-bg-hover);
  }
}
.se-toggle-icon {
  font-size: 12px;
  color: var(--tianshu-text-tertiary);
  width: 14px;
  text-align: center;
  flex-shrink: 0;
}
.se-cat-icon {
  font-size: 13px;
  color: var(--tianshu-text-tertiary);
  flex-shrink: 0;
}
.se-cat-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.se-cat-count {
  flex-shrink: 0;
  font-size: 10px;
  color: var(--tianshu-info-text);
  background: var(--tianshu-info-bg);
  border-radius: 8px;
  padding: 0 5px;
  line-height: 16px;
  font-weight: normal;
}
.se-cat-body {
}
.se-group-header {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 5px 10px 5px 22px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 500;
  color: var(--tianshu-text-secondary);
  user-select: none;
  border-bottom: 1px solid var(--tianshu-border-subtle);
  &:hover {
    background: var(--tianshu-bg-hover);
  }
}
.se-group-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.se-var-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px 4px 24px;
  cursor: pointer;
  font-size: 12px;
  transition: background 0.15s;
  &:hover {
    background: var(--tianshu-bg-active);
  }
}
.se-var-indent {
  padding-left: 38px;
}
.var-type-tag {
  flex-shrink: 0;
}
.var-code {
  font-family: 'Consolas', monospace;
  color: var(--tianshu-text-primary);
  white-space: nowrap;
}
.var-label {
  color: var(--tianshu-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.se-var-pager {
  padding: 5px 8px;
  text-align: right;
  border-bottom: 1px solid var(--tianshu-border-subtle);
  background: var(--tianshu-bg-surface);
}
.se-var-pager--group {
  padding-left: 28px;
}
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
</style>
