<template>
  <div class="se-designer">
    <!-- 顶部工具栏 -->
    <div class="se-header">
      <div class="se-title-area">
        <el-button type="text" icon="el-icon-back" @click="$router.back()" style="color:#606266;" />
        <i class="el-icon-edit-outline se-title-icon" />
        <span class="se-title">QL脚本编辑器</span>
        <el-tag size="mini" type="info" style="margin-left:8px;">{{ lineCount }} 行</el-tag>
      </div>
      <div class="se-toolbar">
        <el-button size="small" icon="el-icon-document" @click="handleSave">保存</el-button>
        <el-button size="small" type="warning" icon="el-icon-cpu" @click="handleCompile">验证脚本</el-button>
        <el-button size="small" type="primary" icon="el-icon-video-play" @click="handleTest">测试</el-button>
      </div>
    </div>

    <div ref="designerBody" class="se-body" :class="{ 'is-resizing': resizingVarPanel }">
      <!-- 左侧变量面板 -->
      <div class="se-var-panel" :class="{ collapsed: varPanelCollapsed }" :style="varPanelStyle">
        <div class="se-var-header" @click="varPanelCollapsed = !varPanelCollapsed">
          <span v-if="!varPanelCollapsed"><i class="el-icon-s-data" /> 项目变量</span>
          <i :class="varPanelCollapsed ? 'el-icon-arrow-right' : 'el-icon-arrow-left'" />
        </div>
        <div v-show="!varPanelCollapsed" class="se-var-list">
          <div v-if="loadingVars" style="text-align:center;padding:20px;color:#999;">
            <i class="el-icon-loading" /> 加载中...
          </div>
          <div v-else-if="varsLoadError" class="text-danger" style="text-align:center;padding:20px;">
            <i class="el-icon-warning" /> 加载失败
            <el-button type="text" size="mini" @click="loadProjectVars($route.params.id)" style="display:block;margin:6px auto 0;">重试</el-button>
          </div>
          <div v-else-if="varTree.length === 0" style="text-align:center;padding:20px;color:#bbb;">
            <i class="el-icon-folder-opened" /> 暂无项目变量
          </div>
          <template v-else>
            <!-- 搜索过滤 -->
            <div class="se-var-search">
              <el-input
                v-model="varSearchKey"
                size="mini"
                placeholder="搜索变量..."
                prefix-icon="el-icon-search"
                clearable
              />
            </div>
            <!-- 树形分组 -->
            <div v-for="cat in filteredVarTree" :key="cat.key" class="se-cat">
              <div class="se-cat-header" @click="toggleCat(cat.key)">
                <i :class="expandedCats[cat.key] ? 'el-icon-caret-bottom' : 'el-icon-caret-right'" class="se-toggle-icon" />
                <i :class="cat.icon" class="se-cat-icon" />
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
                    <el-tag :type="varTypeColor(v.varType)" size="mini" class="var-type-tag">{{ varTypeLabel(v.varType) }}</el-tag>
                    <span class="var-code">{{ v.varCode }}</span>
                    <span class="var-label">{{ v.varLabel }}</span>
                  </div>
                  <el-pagination
                    v-if="scriptListNeedsPaging(cat.children)"
                    class="se-var-pager"
                    small
                    layout="prev,pager,next"
                    :current-page="scriptListPage(cat.key)"
                    :page-size="scriptVarPageSize"
                    :total="cat.children.length"
                    @current-change="p => onScriptListPageChange(cat.key, p)"
                  />
                </template>
                <!-- 三级结构：一级分类 -> 二级组 -> 叶子 -->
                <template v-else>
                  <div v-for="group in cat.children" :key="cat.key + '.' + group.key" class="se-group">
                    <div class="se-group-header" @click="toggleGroup(cat.key + '.' + group.key)">
                      <i :class="expandedGroups[cat.key + '.' + group.key] ? 'el-icon-caret-bottom' : 'el-icon-caret-right'" class="se-toggle-icon" />
                      <span class="se-group-label">{{ group.label }}</span>
                      <span class="se-cat-count">{{ group.children.length }}</span>
                    </div>
                    <div v-show="expandedGroups[cat.key + '.' + group.key]">
                      <div
                        v-for="v in pagedScriptList(cat.key + '.' + group.key, group.children)"
                        :key="v.varCode"
                        class="se-var-item se-var-indent"
                        :title="'双击插入: ' + v.varCode"
                        @dblclick="insertVar(v)"
                      >
                        <el-tag :type="varTypeColor(v.varType)" size="mini" class="var-type-tag">{{ varTypeLabel(v.varType) }}</el-tag>
                        <span class="var-code">{{ v.varCode }}</span>
                        <span class="var-label">{{ v.varLabel }}</span>
                      </div>
                      <el-pagination
                        v-if="scriptListNeedsPaging(group.children)"
                        class="se-var-pager se-var-pager--group"
                        small
                        layout="prev,pager,next"
                        :current-page="scriptListPage(cat.key + '.' + group.key)"
                        :page-size="scriptVarPageSize"
                        :total="group.children.length"
                        @current-change="p => onScriptListPageChange(cat.key + '.' + group.key, p)"
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
            <i class="el-icon-success" /> 脚本有效
          </span>
          <span v-else-if="compileStatus === 2" class="se-status-item status-err">
            <i class="el-icon-error" /> {{ compileMessage || '脚本错误' }}
          </span>
          <span v-else class="se-status-item">
            <i class="el-icon-info" /> 未验证
          </span>
          <span class="se-statusbar-spacer" />
          <span class="se-line-info">{{ lineCount }} 行 / {{ script.length }} 字符</span>
        </div>

        <!-- 编辑器 -->
        <div class="se-editor-container">
          <MonacoEditor
            v-model="script"
            language="ql"
            theme="qlexpress-dark"
            height="100%"
            @editor-ready="onEditorReady"
            :options="editorOptions"
          />
        </div>

        <!-- 底部提示 -->
        <div class="se-footer">
          <span class="se-footer-tip">
            <i class="el-icon-edit-outline" /> 直接编写 QLExpress 脚本，保存后即可用于规则执行
          </span>
        </div>
      </div>
    </div>

    <!-- 测试弹窗 -->
    <el-dialog title="测试执行" :visible.sync="testVisible" width="600px" append-to-body>
      <p class="test-hint"><i class="el-icon-info" /> 输入测试参数（JSON 格式），包含脚本中使用的变量</p>
      <el-input
        v-model="testParamsJson"
        type="textarea"
        :rows="6"
        placeholder='{"taxpayerQualification": "一般纳税人", "billingAmount": 10000}'
      />
      <template slot="footer">
        <el-button size="small" @click="testVisible = false">取消</el-button>
        <el-button size="small" type="primary" icon="el-icon-video-play" @click="doTest">执行</el-button>
      </template>
      <div v-if="testResult" class="test-result">
        <el-alert
          :title="testResult.success ? '执行成功' : '执行失败'"
          :type="testResult.success ? 'success' : 'error'"
          :closable="false" show-icon style="margin-bottom:10px;"
        />
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="返回值">
            <strong style="font-size:16px;color:#1890ff;">{{ testResult.result }}</strong>
          </el-descriptions-item>
          <el-descriptions-item label="耗时">{{ testResult.executeTimeMs }}ms</el-descriptions-item>
          <el-descriptions-item v-if="testResult.errorMessage" label="错误">
            <span class="err-text">{{ testResult.errorMessage }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { saveContent, compileRule, executeRule, getContent, refreshFields } from '@/api/definition'
import varPickerMixin from '@/mixins/varPickerMixin'
import MonacoEditor from '@/components/MonacoEditor'
import { buildSampleParamsFromCodes, collectScriptInputCodes } from '@/utils/testSampleParams'

const VAR_PANEL_WIDTH_KEY = 'qlexpress.scriptEditor.varPanelWidth'
const DEFAULT_VAR_PANEL_WIDTH = 300

export default {
  name: 'ScriptEditor',
  mixins: [varPickerMixin],
  components: { MonacoEditor },
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
      testParamsJson: '{}',
      testResult: null,
      monacoEditor: null,
      varPanelWidth: DEFAULT_VAR_PANEL_WIDTH,
      varPanelMinWidth: 220,
      varPanelMaxWidth: 560,
      editorMinWidth: 420,
      resizingVarPanel: false,
      resizeBodyCursor: '',
      resizeBodyUserSelect: '',
      scriptVarPageSize: 100,
      scriptVarPages: {}
    }
  },
  computed: {
    varPanelStyle() {
      if (this.varPanelCollapsed) return {}
      return {
        width: this.varPanelWidth + 'px',
        minWidth: this.varPanelWidth + 'px',
        maxWidth: this.varPanelWidth + 'px'
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
        automaticLayout: true
      }
    },
    lineCount() {
      return (this.script.match(/\n/g) || []).length + 1
    },
    /** 将 varPickerOptions + projectFunctions 构建为三级树形结构 */
    varTree() {
      const tree = []
      const refs = this.varPickerOptions

      const standalone = refs.filter(v => v._ref && v._ref.category === 'standalone')
      if (standalone.length) {
        tree.push({ key: '__standalone__', label: '普通变量', icon: 'el-icon-s-data', hasSubGroups: false, children: standalone })
      }

      const constRefs = refs.filter(v => v._ref && v._ref.category === 'constant')
      if (constRefs.length) {
        const byGroup = {}
        constRefs.forEach(v => {
          const gc = v._ref.groupCode || '_ungrouped'
          const gl = v._ref.groupLabel || gc
          if (!byGroup[gc]) byGroup[gc] = { key: gc, label: gl, children: [] }
          byGroup[gc].children.push(v)
        })
        tree.push({ key: '__constant__', label: '常量', icon: 'el-icon-collection', hasSubGroups: true, children: Object.values(byGroup) })
      }

      const objRefs = refs.filter(v => v._ref && v._ref.category === 'object')
      if (objRefs.length) {
        const byObj = {}
        objRefs.forEach(v => {
          const oc = v._ref.objectCode || '_ungrouped'
          const ol = v._ref.objectLabel || oc
          if (!byObj[oc]) byObj[oc] = { key: oc, label: ol, children: [] }
          byObj[oc].children.push(v)
        })
        tree.push({ key: '__object__', label: '对象', icon: 'el-icon-files', hasSubGroups: true, children: Object.values(byObj) })
      }

      if (this.projectFunctions && this.projectFunctions.length) {
        tree.push({
          key: '__function__',
          label: '自定义函数',
          icon: 'el-icon-s-operation',
          hasSubGroups: false,
          children: this.projectFunctions.map(f => ({
            varCode: f.funcCode + '()',
            varLabel: f.funcName,
            varType: 'FUNC'
          }))
        })
      }

      return tree
    },
    /** 搜索过滤后的树 */
    filteredVarTree() {
      const kw = (this.varSearchKey || '').trim().toLowerCase()
      if (!kw) return this.varTree
      return this.varTree.map(cat => {
        if (!cat.hasSubGroups) {
          const filtered = cat.children.filter(v =>
            (v.varCode && v.varCode.toLowerCase().includes(kw)) ||
            (v.varLabel && v.varLabel.toLowerCase().includes(kw))
          )
          return filtered.length ? { ...cat, children: filtered } : null
        }
        const filteredGroups = cat.children.map(group => {
          const filtered = group.children.filter(v =>
            (v.varCode && v.varCode.toLowerCase().includes(kw)) ||
            (v.varLabel && v.varLabel.toLowerCase().includes(kw))
          )
          return filtered.length ? { ...group, children: filtered } : null
        }).filter(Boolean)
        return filteredGroups.length ? { ...cat, children: filteredGroups } : null
      }).filter(Boolean)
    }
  },
  watch: {
    varSearchKey() {
      this.scriptVarPages = {}
    },
    varTree: {
      immediate: true,
      handler(tree) {
        if (!tree || !tree.length) return
        const cats = { ...this.expandedCats }
        const groups = { ...this.expandedGroups }
        tree.forEach(cat => {
          if (cats[cat.key] === undefined) cats[cat.key] = true
          if (cat.hasSubGroups) {
            cat.children.forEach(g => {
              const gk = cat.key + '.' + g.key
              if (groups[gk] === undefined) groups[gk] = true
            })
          }
        })
        this.expandedCats = cats
        this.expandedGroups = groups
      }
    }
  },
  created() {
    this.definitionId = this.$route.params.id
    this.loadContent()
  },
  mounted() {
    this.restoreVarPanelWidth()
  },
  beforeDestroy() {
    this.stopVarPanelResize()
  },
  methods: {
    restoreVarPanelWidth() {
      try {
        const saved = Number(window.localStorage && window.localStorage.getItem(VAR_PANEL_WIDTH_KEY))
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
          window.localStorage.setItem(VAR_PANEL_WIDTH_KEY, String(this.varPanelWidth))
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
      window.addEventListener('touchmove', this.onVarPanelTouchResize, { passive: false })
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
      this.layoutEditor()
    },
    updateVarPanelWidth(clientX) {
      const body = this.$refs.designerBody
      if (!body || clientX == null) return
      const rect = body.getBoundingClientRect()
      const maxWidth = this.panelMaxWidthForBody(rect.width)
      this.varPanelWidth = this.clampVarPanelWidth(clientX - rect.left, maxWidth)
      this.layoutEditor()
    },
    panelMaxWidthForBody(bodyWidth) {
      if (!bodyWidth) return this.varPanelMaxWidth
      return Math.max(
        this.varPanelMinWidth,
        Math.min(this.varPanelMaxWidth, bodyWidth - this.editorMinWidth - 12)
      )
    },
    clampVarPanelWidth(width, maxWidth = this.varPanelMaxWidth) {
      return Math.min(Math.max(Math.round(width), this.varPanelMinWidth), maxWidth)
    },
    resetVarPanelWidth() {
      this.varPanelWidth = this.clampVarPanelWidth(DEFAULT_VAR_PANEL_WIDTH)
      this.persistVarPanelWidth()
      this.layoutEditor()
    },
    layoutEditor() {
      this.$nextTick(() => {
        if (this.monacoEditor && this.monacoEditor.layout) {
          this.monacoEditor.layout()
        }
      })
    },
    toggleCat(key) {
      this.$set(this.expandedCats, key, !this.expandedCats[key])
    },
    toggleGroup(key) {
      this.$set(this.expandedGroups, key, !this.expandedGroups[key])
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
      this.$set(this.scriptVarPages, key, page)
    },
    async loadContent() {
      try {
        const res = await getContent(this.definitionId)
        const content = res && res.data ? res.data : res
        if (content) {
          if (content.compiledScript) {
            this.script = content.compiledScript
          } else if (content.modelJson && content.modelJson !== '{}') {
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
          }
          this.compileStatus = content.compileStatus || 0
          this.compileMessage = content.compileMessage || ''
        }
      } catch (e) {
        this.$message.error('加载内容失败: ' + (e.message || '未知错误'))
      } finally {
        this.contentLoaded = true
      }
    },
    async handleSave() {
      // 保存前：从脚本中提取实际引用的变量，更新 scriptVarRefs
      this.syncScriptVarRefsFromScript()
      const modelJson = JSON.stringify({
        script: this.script,
        scriptVarRefs: this.scriptVarRefs
      })
      await saveContent({ definitionId: this.definitionId, modelJson })
      await refreshFields(this.definitionId, modelJson)
      this.$message.success('保存成功')
      this.refreshProjectRefs()
    },
    async handleCompile() {
      await this.handleSave()
      const res = await compileRule(this.definitionId)
      const result = res && res.data ? res.data : res
      if (result && result.success) {
        this.compileStatus = 1
        this.compileMessage = ''
        this.$message.success('脚本验证通过')
      } else {
        this.compileStatus = 2
        this.compileMessage = result && result.errorMessage ? result.errorMessage : '未知错误'
        this.$message.error('脚本验证失败: ' + this.compileMessage)
      }
    },
    handleTest() {
      const codes = collectScriptInputCodes(this.script, this.projectRefs)
      this.testParamsJson = JSON.stringify(buildSampleParamsFromCodes(Array.from(codes), this.projectRefs), null, 2)
      this.testResult = null
      this.testVisible = true
    },
    async doTest() {
      let params = {}
      try { params = JSON.parse(this.testParamsJson || '{}') } catch (e) {
        this.$message.error('参数 JSON 格式错误')
        return
      }
      const res = await executeRule({ definitionId: this.definitionId, params })
      this.testResult = res && res.data ? res.data : res
    },
    insertVar(v) {
      if (!this.monacoEditor || !v) return
      const code = v.varCode
      const editor = this.monacoEditor
      const selection = editor.getSelection()
      editor.executeEdits('insert-var', [{
        range: selection,
        text: code,
        forceMoveMarkers: true
      }])
      // 同步记录引用的 varId（以最后一次插入同名变量时的 _varId 为准）
      if (v._varId != null) {
        const existing = this.scriptVarRefs.find(r => r.refCode === code)
        if (existing) {
          existing.varId = v._varId
          existing.refType = v._refType || v.refType || null
        } else {
          this.scriptVarRefs.push({ refCode: code, varId: v._varId, refType: v._refType || v.refType || null })
        }
      }
      this.$nextTick(() => editor.focus())
    },
    onEditorReady(editor) {
      this.monacoEditor = editor
    },
    /**
     * 从脚本内容中提取实际引用的变量，同步更新 scriptVarRefs。
     * 遍历 projectRefs 中所有 refCode，检查是否在脚本中出现，
     * 出现则说明被引用，保留对应的 varId。
     */
    syncScriptVarRefsFromScript() {
      if (!this.script) return
      const script = this.script
      const matched = []
      // 遍历所有已知引用，检查脚本中是否出现
      ;(this.projectRefs || []).forEach(ref => {
        // 使用 word boundary 匹配，防止 "amount" 匹配到 "billingAmount"
        const regex = new RegExp('\\b' + this.escapeRegex(ref.refCode) + '\\b')
        if (regex.test(script)) {
          // 尝试从旧映射中获取 varId
          const old = this.scriptVarRefs.find(r => r.refCode === ref.refCode)
          matched.push({
            refCode: ref.refCode,
            varId: old && old.varId ? old.varId : (ref.varObj && ref.varObj.id ? ref.varObj.id : null),
            refType: old && old.refType ? old.refType : ref.refType
          })
        }
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
      this.scriptVarRefs.forEach(ref => {
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
  }
}
</script>

<style lang="scss" scoped>
$editor-bg: #1e1e2e;
$editor-text: #cdd6f4;
$editor-line-bg: #181825;
$editor-line-text: #585b70;
$editor-border: #313244;

.se-designer {
  background: #f3f3f3;
  height: calc(100vh - 82px);
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: 4px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}

.se-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  padding: 12px 20px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
  flex-wrap: wrap;
  gap: 8px;
  flex-shrink: 0;
}
.se-title-area { display: flex; align-items: center; }
.se-title-icon { font-size: 18px; color: #722ed1; margin-right: 8px; }
.se-title { font-size: 16px; font-weight: bold; color: #282828; }
.se-toolbar { display: flex; align-items: center; gap: 6px; }

.se-body {
  flex: 1;
  display: flex;
  margin: 12px;
  gap: 0;
  min-height: 0;
  overflow: hidden;
}

/* 变量面板 */
.se-var-panel {
  width: 300px;
  min-width: 220px;
  background: #fff;
  border-radius: 6px 0 0 6px;
  border: 1px solid #e8e8e8;
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
  background: #fafafa;
  border-bottom: 1px solid #f0f0f0;
  font-size: 13px;
  font-weight: 600;
  color: #555;
  cursor: pointer;
  gap: 6px;
  i { color: #999; }
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
  border-bottom: 1px solid #f0f0f0;
  position: sticky;
  top: 0;
  background: #fff;
  z-index: 1;
}

/* 一级分类 */
.se-cat-header {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 7px 10px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
  color: #333;
  background: #fafafa;
  border-bottom: 1px solid #f0f0f0;
  user-select: none;
  &:hover { background: #f0f0f0; }
}
.se-toggle-icon { font-size: 12px; color: #999; width: 14px; text-align: center; flex-shrink: 0; }
.se-cat-icon { font-size: 13px; color: #8c8c8c; flex-shrink: 0; }
.se-cat-label { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.se-cat-count {
  flex-shrink: 0;
  font-size: 10px;
  color: #fff;
  background: #bfbfbf;
  border-radius: 8px;
  padding: 0 5px;
  line-height: 16px;
  font-weight: normal;
}
.se-cat-body { }

/* 二级分组 */
.se-group-header {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 5px 10px 5px 22px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 500;
  color: #555;
  user-select: none;
  border-bottom: 1px solid #fafafa;
  &:hover { background: #f5f5f5; }
}
.se-group-label { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* 叶子节点 */
.se-var-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px 4px 24px;
  cursor: pointer;
  font-size: 12px;
  transition: background 0.15s;
  &:hover { background: #e6f7ff; }
}
.se-var-indent { padding-left: 38px; }
.var-type-tag { flex-shrink: 0; }
.var-code { font-family: 'Consolas', monospace; color: #333; white-space: nowrap; }
.var-label { color: #999; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.se-var-pager {
  padding: 5px 8px;
  text-align: right;
  border-bottom: 1px solid #f0f0f0;
  background: #fff;
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
  background: #fff;
  border-top: 1px solid #e8e8e8;
  border-bottom: 1px solid #e8e8e8;
  user-select: none;
  z-index: 2;
  transition: background 0.15s;
  &:hover,
  .se-body.is-resizing & {
    background: #eef4ff;
  }
  &:hover .se-resizer-handle,
  .se-body.is-resizing & .se-resizer-handle {
    background: #2639e9;
  }
}
.se-resizer-handle {
  width: 2px;
  height: 36px;
  border-radius: 2px;
  background: #d9d9d9;
  transition: background 0.15s;
}

/* 编辑区 */
.se-editor-area {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  border-radius: 0 6px 6px 0;
  border: 1px solid #e8e8e8;
  overflow: hidden;
}

.se-statusbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: #11111b;
  border-bottom: 1px solid $editor-border;
}
.se-status-item {
  font-size: 11px;
  color: #888;
  display: flex;
  align-items: center;
  gap: 3px;
  &.status-ok { color: #52c41a; }
  &.status-err { color: #ff6b6b; }
}
.se-statusbar-spacer { flex: 1; }
.se-line-info { font-size: 11px; color: #585b70; font-family: 'Consolas', monospace; }

.se-editor-container {
  display: flex;
  flex: 1;
  min-height: 400px;
  overflow: hidden;
  background: $editor-bg;
}

.se-footer {
  display: flex;
  align-items: center;
  padding: 5px 12px;
  background: #11111b;
  border-top: 1px solid $editor-border;
}
.se-footer-tip {
  font-size: 11px;
  color: #a6e3a1;
  display: flex;
  align-items: center;
  gap: 4px;
}

.test-hint { font-size: 12px; color: #909399; margin-bottom: 8px; }
.test-result { margin-top: 16px; }
</style>
