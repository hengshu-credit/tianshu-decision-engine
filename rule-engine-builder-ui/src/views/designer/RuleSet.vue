<template>
  <div class="rs-designer">
    <div class="rs-header">
      <div class="rs-title-area">
        <el-button type="text" icon="el-icon-back" class="rs-back" @click="$router.back()" />
        <i class="el-icon-s-claim rs-title-icon" />
        <span class="rs-title">规则集配置</span>
        <el-tag size="mini" type="info">共 {{ model.rules.length }} 条规则</el-tag>
        <el-tag size="mini" type="success">启用 {{ enabledRuleCount }} 条</el-tag>
      </div>
      <div class="rs-toolbar">
        <span class="toolbar-label">执行模式</span>
        <el-select v-model="model.executionMode" size="small" class="mode-select">
          <el-option label="串行命中退出" value="SERIAL" />
          <el-option label="并行全部运行" value="PARALLEL" />
        </el-select>
        <el-tooltip :content="executionModeDesc" placement="bottom" effect="light">
          <i class="el-icon-question tip-icon" />
        </el-tooltip>
        <el-divider direction="vertical" />
        <el-button size="small" icon="el-icon-plus" @click="addRule">添加规则</el-button>
        <el-button size="small" icon="el-icon-time" @click="openVersionDialog">版本历史</el-button>
        <el-button size="small" icon="el-icon-document" @click="handleSave">临时保存配置</el-button>
        <el-button size="small" type="warning" icon="el-icon-cpu" @click="handleCompile">保存并编译</el-button>
        <el-button size="small" type="primary" icon="el-icon-video-play" @click="handleTest">编译后测试</el-button>
      </div>
    </div>

    <div v-if="loadingVars || varPickerOptions.length" class="rs-var-status">
      <span v-if="loadingVars"><i class="el-icon-loading" /> 加载变量库...</span>
      <span v-else><i class="el-icon-s-custom" /> 已加载 {{ varPickerOptions.length }} 个变量/常量/对象字段</span>
    </div>

    <div class="rs-summary">
      <div class="summary-item">
        <span class="summary-label">排序规则</span>
        <span class="summary-value">优先级越大越先执行；同优先级按页面顺序</span>
      </div>
      <div class="summary-item">
        <span class="summary-label">返回结果</span>
        <span class="summary-value">list[rule]，仅包含命中的规则</span>
      </div>
    </div>

    <div class="rs-rule-list">
      <template v-if="contentLoaded && model.rules.length > 0">
        <div
          v-for="(rule, index) in model.rules"
          :key="rule.uid || index"
          class="rs-rule-card"
          :class="{ 'is-disabled': !rule.enabled }"
          draggable="true"
          @dragstart="onDragStart(index)"
          @dragover.prevent
          @drop="onDrop(index)"
        >
          <div class="rs-rule-head">
            <span class="drag-handle" title="拖拽排序"><i class="el-icon-rank" /></span>
            <span class="rule-index">#{{ index + 1 }}</span>
            <el-switch v-model="rule.enabled" active-text="启用" inactive-text="停用" />
            <el-input v-model="rule.ruleCode" size="small" class="rule-code" placeholder="规则编码" />
            <el-input v-model="rule.ruleName" size="small" class="rule-name" placeholder="规则名称" />
            <el-input-number v-model="rule.priority" :min="1" :max="9999" :precision="0" size="small" class="rule-priority" />
            <div class="rule-actions">
              <el-button type="text" size="mini" icon="el-icon-top" :disabled="index === 0" @click="moveRule(index, -1)" />
              <el-button type="text" size="mini" icon="el-icon-bottom" :disabled="index === model.rules.length - 1" @click="moveRule(index, 1)" />
              <el-button type="text" size="mini" @click="copyRule(index)">复制</el-button>
              <el-button type="text" size="mini" class="btn-delete" @click="removeRule(index)">删除</el-button>
            </div>
          </div>

          <div class="rs-rule-body">
            <div class="condition-panel">
              <div class="panel-title">命中条件</div>
              <condition-group-editor
                v-if="rule.conditionRoot"
                :group="rule.conditionRoot"
                :vars="varPickerOptions"
                :selected-vars="selectedVarPickerOptions"
                :get-var-options-fn="getVarOptions"
                :allow-custom-var="true"
              />
            </div>
            <div class="action-panel">
              <div class="panel-title">命中动作</div>
              <action-block-editor
                :action-data="rule.actionData"
                :vars="varPickerOptions"
                :selected-vars="selectedVarPickerOptions"
                :functions="projectFunctions"
                @update="data => updateRuleActionData(index, data)"
              />
            </div>
          </div>
        </div>
      </template>

      <div v-if="!contentLoaded" class="rs-loading">
        <i class="el-icon-loading" /> 加载规则集数据...
      </div>
      <div v-else-if="contentLoaded && model.rules.length === 0" class="rs-empty">
        <i class="el-icon-s-claim rs-empty-icon" />
        <p>暂无规则，请点击「添加规则」开始配置。</p>
      </div>
    </div>

    <script-panel
      v-if="definitionId"
      ref="scriptPanel"
      :definitionId="definitionId"
      :onBeforeCompile="handleSave"
      @mode-change="onScriptModeChange"
    />

    <div v-if="scriptMode === 'script'" class="script-override-banner">
      <i class="el-icon-warning" />
      <span>脚本覆盖模式已激活，可视化编辑暂停。如需恢复请在脚本面板切换回「可视化模式」。</span>
    </div>

        <designer-test-dialog
      :visible.sync="testVisible"
      :definition-id="definitionId"
      :project-id="projectIdForRefs"
      model-type="RULE_SET"
      :model-json-provider="serializeModel"
      :params-template="testParamsTemplate"
    />

    <el-dialog title="规则集版本历史" :visible.sync="versionVisible" width="920px" append-to-body>
      <el-table :data="versions" border size="small" v-loading="versionLoading" max-height="360">
        <el-table-column prop="version" label="版本" width="80">
          <template slot-scope="{ row }">v{{ row.version }}</template>
        </el-table-column>
        <el-table-column prop="changeLog" label="变更说明" min-width="180" show-overflow-tooltip />
        <el-table-column prop="publishBy" label="发布人" width="120" />
        <el-table-column prop="publishTime" label="发布时间" width="170">
          <template slot-scope="{ row }">{{ formatVersionTime(row.publishTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template slot-scope="{ row, $index }">
            <el-button type="text" size="mini" @click="viewVersion(row)">查看内容</el-button>
            <el-button type="text" size="mini" :disabled="$index === versions.length - 1" @click="compareVersion(row, versions[$index + 1])">对比上一版</el-button>
            <el-button type="text" size="mini" @click="rollbackDraft(row)">恢复草稿</el-button>
            <el-button type="text" size="mini" @click="publishVersion(row)">发布此版</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="versionCompare" class="version-compare">
        <el-alert
          :title="'v' + versionCompare.left.version + ' 与 v' + versionCompare.right.version + ' 对比'"
          :type="versionCompare.modelJsonChanged || versionCompare.compiledScriptChanged ? 'warning' : 'success'"
          :closable="false"
        />
        <div class="version-diff-grid">
          <pre>{{ formatVersionJson(versionCompare.left.modelJson) }}</pre>
          <pre>{{ formatVersionJson(versionCompare.right.modelJson) }}</pre>
        </div>
      </div>
    </el-dialog>

    <el-dialog :title="versionViewTitle" :visible.sync="versionViewVisible" width="760px" append-to-body>
      <pre class="version-json">{{ versionViewContent }}</pre>
    </el-dialog>
  </div>
</template>

<script>
import {
  saveContent,
  compileRule,
  executeRule,
  getContent,
  refreshFields,
  listVersions,
  getVersion,
  compareVersions,
  rollbackVersion,
  publishRule
} from '@/api/definition'
import varPickerMixin from '@/mixins/varPickerMixin'
import ScriptPanel from '@/components/common/ScriptPanel.vue'
import DesignerTestDialog from '@/components/common/DesignerTestDialog.vue'
import ConditionGroupEditor from '@/components/decision/ConditionGroupEditor.vue'
import ActionBlockEditor from '@/components/flow/ActionBlockEditor.vue'
import { newBlock } from '@/utils/actionDataCodegen'
import {
  createEmptyGroup,
  createEmptyLeaf,
  migrateRuleConditionsToTree,
  collectVarCodesFromConditionTree,
  walkConditionLeaves
} from '@/utils/decisionConditionTree'
import { buildSampleParamsFromCodes, collectActionDataInputCodes } from '@/utils/testSampleParams'

export default {
  name: 'RuleSet',
  components: { DesignerTestDialog, ScriptPanel, ConditionGroupEditor, ActionBlockEditor },
  mixins: [varPickerMixin],
  data() {
    return {
      definitionId: null,
      model: {
        executionMode: 'SERIAL',
        rules: []
      },
      scriptMode: 'visual',
      contentLoaded: false,
      draggedIndex: -1,
      testVisible: false,
      testParamsTemplate: {},
      testParams: {},
      testResult: null,
      versionVisible: false,
      versionLoading: false,
      versions: [],
      versionCompare: null,
      versionViewVisible: false,
      versionViewTitle: '',
      versionViewContent: ''
    }
  },
  computed: {
    enabledRuleCount() {
      return (this.model.rules || []).filter(rule => rule.enabled !== false).length
    },
    executionModeDesc() {
      if (this.model.executionMode === 'PARALLEL') {
        return '并行全部运行：按优先级和页面顺序检查全部规则，命中的规则都会执行动作并进入返回列表'
      }
      return '串行命中退出：按优先级和页面顺序检查规则，命中第一条后执行动作并停止'
    },
    testVarCodeList() {
      const s = new Set()
      ;(this.model.rules || []).forEach(rule => {
        if (rule.enabled === false) return
        collectVarCodesFromConditionTree(rule.conditionRoot, s)
        collectActionDataInputCodes(rule.actionData, this.projectRefs, s)
      })
      return Array.from(s)
    }
  },
  created() {
    this.definitionId = this.$route.params.id
    this.loadProjectVars(this.definitionId)
    this.loadContent()
  },
  methods: {
    async loadContent() {
      try {
        const res = await getContent(this.definitionId)
        const content = res && res.data ? res.data : res
        if (content && content.modelJson && content.modelJson !== '{}') {
          this.model = JSON.parse(content.modelJson)
        }
        this.normalizeModel()
      } catch (e) {
        this.$message.error('加载内容失败: ' + (e.message || '未知错误'))
      } finally {
        this.contentLoaded = true
      }
    },
    normalizeModel() {
      if (!this.model || typeof this.model !== 'object') {
        this.model = { executionMode: 'SERIAL', rules: [] }
      }
      if (!this.model.executionMode) this.$set(this.model, 'executionMode', 'SERIAL')
      if (!Array.isArray(this.model.rules)) this.$set(this.model, 'rules', [])
      const legacyCols = Array.isArray(this.model.conditions) ? this.model.conditions : []
      this.model.rules.forEach((rule, index) => {
        if (!rule.uid) this.$set(rule, 'uid', this.createRuleUid())
        if (!rule.ruleCode) this.$set(rule, 'ruleCode', this.nextRuleCode(index))
        if (!rule.ruleName) this.$set(rule, 'ruleName', '规则' + (index + 1))
        if (rule.priority === undefined || rule.priority === null) this.$set(rule, 'priority', 1)
        if (rule.enabled === undefined) this.$set(rule, 'enabled', rule.status !== 0)
        if (!rule.conditionRoot) {
          const migrated = Array.isArray(rule.conditions) && rule.conditions.length
            ? migrateRuleConditionsToTree(rule.conditions, legacyCols)
            : createEmptyGroup('AND')
          if (!migrated.children || !migrated.children.length) migrated.children = [createEmptyLeaf()]
          this.$set(rule, 'conditionRoot', migrated)
        }
        if (!Array.isArray(rule.actionData)) this.$set(rule, 'actionData', [newBlock('assign')])
        if (rule.status !== undefined) this.$delete(rule, 'status')
        if (rule.conditions !== undefined) this.$delete(rule, 'conditions')
      })
      if (this.model.conditions !== undefined) this.$delete(this.model, 'conditions')
      if (this.model.actions !== undefined) this.$delete(this.model, 'actions')
    },
    _syncModelVarRefs() {
      let changed = false
      ;(this.model.rules || []).forEach(rule => {
        walkConditionLeaves(rule.conditionRoot, leaf => {
          if (leaf.varCode && this.syncVarItem(leaf)) changed = true
          if (leaf.valueKind === 'VAR' && leaf.value) {
            const item = {
              varCode: leaf.value,
              varLabel: leaf.rightVarLabel,
              _varId: leaf._rightVarId,
              _refType: leaf._rightRefType || leaf._refType,
              varType: leaf.rightVarType
            }
            if (this.syncVarItem(item)) {
              leaf.value = item.varCode
              leaf.rightVarLabel = item.varLabel
              leaf._rightVarId = item._varId
              leaf._rightRefType = item._refType
              leaf.rightVarType = item.varType
              changed = true
            }
          }
        })
        if (this.syncActionDataVarRefs(rule.actionData || [])) changed = true
      })
      if (changed) this.$forceUpdate()
    },
    collectSelectedVarItems() {
      const items = []
      ;(this.model.rules || []).forEach(rule => {
        walkConditionLeaves(rule.conditionRoot, leaf => {
          items.push(leaf)
          if (leaf.valueKind === 'VAR' && leaf.value) {
            items.push({
              varCode: leaf.value,
              varLabel: leaf.rightVarLabel,
              _varId: leaf._rightVarId,
              _refType: leaf._rightRefType || leaf._refType,
              varType: leaf.rightVarType
            })
          }
        })
        items.push(...this.collectActionDataVarItems(rule.actionData || []))
      })
      return items
    },
    addRule() {
      this.model.rules.push({
        uid: this.createRuleUid(),
        ruleCode: this.nextRuleCode(this.model.rules.length),
        ruleName: '规则' + (this.model.rules.length + 1),
        priority: 1,
        enabled: true,
        conditionRoot: { type: 'group', op: 'AND', children: [createEmptyLeaf()] },
        actionData: [newBlock('assign')]
      })
    },
    copyRule(index) {
      const orig = this.model.rules[index]
      if (!orig) return
      const copy = JSON.parse(JSON.stringify(orig))
      copy.uid = this.createRuleUid()
      copy.ruleCode = this.nextRuleCode(this.model.rules.length)
      copy.ruleName = copy.ruleName + ' 副本'
      this.model.rules.splice(index + 1, 0, copy)
    },
    removeRule(index) {
      this.$confirm('确认删除该规则？', '提示', { type: 'warning' }).then(() => {
        this.model.rules.splice(index, 1)
      }).catch(() => {})
    },
    moveRule(index, step) {
      const target = index + step
      if (target < 0 || target >= this.model.rules.length) return
      const rows = this.model.rules
      const item = rows.splice(index, 1)[0]
      rows.splice(target, 0, item)
    },
    onDragStart(index) {
      this.draggedIndex = index
    },
    onDrop(index) {
      if (this.draggedIndex < 0 || this.draggedIndex === index) return
      const item = this.model.rules.splice(this.draggedIndex, 1)[0]
      this.model.rules.splice(index, 0, item)
      this.draggedIndex = -1
    },
    updateRuleActionData(index, data) {
      const rule = this.model.rules[index]
      if (!rule) return
      this.$set(rule, 'actionData', Array.isArray(data) ? data : [])
    },
    nextRuleCode(index) {
      let text = String(index + 1)
      while (text.length < 4) text = '0' + text
      return 'R' + text
    },
    createRuleUid() {
      return 'rs-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8)
    },
    async handleSave() {
      try {
        this.normalizeModel()
        await saveContent({ definitionId: this.definitionId, modelJson: JSON.stringify(this.serializeModel()) })
        await refreshFields(this.definitionId, JSON.stringify(this.serializeModel()))
        this.refreshProjectRefs()
        this.$message.success('保存成功')
      } catch (e) {
        this.$message.error('保存失败: ' + (e && e.message ? e.message : '未知错误'))
        throw e
      }
    },
    serializeModel() {
      const copy = JSON.parse(JSON.stringify(this.model))
      ;(copy.rules || []).forEach(rule => {
        delete rule.uid
        rule.status = rule.enabled === false ? 0 : 1
      })
      return copy
    },
    async handleCompile() {
      await this.handleSave()
      const res = await compileRule(this.definitionId)
      const body = res && res.data ? res.data : res
      if (body && body.success) {
        this.$message.success('编译成功')
        await this.loadProjectVars(this.definitionId)
        if (this.$refs.scriptPanel) this.$refs.scriptPanel.refresh()
      } else {
        this.$message.error('编译失败: ' + (body ? body.errorMessage : '未知错误'))
      }
    },
    buildTestParamsTemplate() {
      return buildSampleParamsFromCodes(this.testVarCodeList, this.projectRefs)
    },
    handleTest() {
      const template = this.buildTestParamsTemplate()
      this.testParamsTemplate = template
      this.testParams = template
      this.testResult = null
      this.testVisible = true
    },
    async doTest() {
      const res = await executeRule({ definitionId: this.definitionId, params: this.testParams })
      this.testResult = res && res.data ? res.data : res
    },
    testVarLabel(code) {
      const ref = this.projectRefs.find(r => r.refCode === code)
      if (ref && ref.varObj && ref.varObj.varLabel) return ref.varObj.varLabel
      if (ref && ref.refLabel && ref.refLabel.label) return ref.refLabel.label
      return code
    },
    testVarMeta(code) {
      const ref = this.projectRefs.find(r => r.refCode === code)
      const vt = (ref && ref.varType) || 'STRING'
      let enumOptions = ''
      if (vt === 'ENUM' && ref && ref.varObj) {
        const opts = this.getVarOptions(code) || []
        enumOptions = opts.map(o => o.value || o.optionValue).filter(Boolean).join(',')
      }
      return { varType: vt, enumOptions }
    },
    testEnumOpts(code) {
      const m = this.testVarMeta(code)
      return m.enumOptions ? m.enumOptions.split(',').map(s => s.trim()).filter(Boolean) : []
    },
    formatResult(val) {
      if (val === null || val === undefined) return '(空)'
      try {
        return JSON.stringify(typeof val === 'string' ? JSON.parse(val) : val, null, 2)
      } catch (e) {
        return String(val)
      }
    },
    onScriptModeChange(mode) {
      this.scriptMode = mode
    },
    async openVersionDialog() {
      this.versionVisible = true
      this.versionCompare = null
      await this.loadVersions()
    },
    async loadVersions() {
      if (!this.definitionId) return
      this.versionLoading = true
      try {
        const res = await listVersions(this.definitionId)
        this.versions = Array.isArray(res.data) ? res.data : (Array.isArray(res) ? res : [])
      } catch (e) {
        this.$message.error(e.message || '加载版本历史失败')
      } finally {
        this.versionLoading = false
      }
    },
    async viewVersion(row) {
      if (!row || !row.version) return
      try {
        const res = await getVersion(this.definitionId, row.version)
        const version = res && res.data ? res.data : res
        this.versionViewTitle = '规则集版本 v' + row.version
        this.versionViewContent = this.formatVersionJson(version.modelJson)
        this.versionViewVisible = true
      } catch (e) {
        this.$message.error(e.message || '查看版本失败')
      }
    },
    async compareVersion(left, right) {
      if (!left || !right) return
      try {
        const res = await compareVersions(this.definitionId, left.version, right.version)
        this.versionCompare = res && res.data ? res.data : res
      } catch (e) {
        this.$message.error(e.message || '版本对比失败')
      }
    },
    async rollbackDraft(row) {
      if (!row || !row.version) return
      try {
        await this.$confirm('恢复会覆盖当前草稿内容，但不会自动发布，确认恢复到 v' + row.version + '？', '确认恢复', { type: 'warning' })
        await rollbackVersion(this.definitionId, row.version)
        this.$message.success('恢复成功')
        await this.loadContent()
        await this.loadVersions()
      } catch (e) {
        if (e !== 'cancel') this.$message.error(e.message || '恢复失败')
      }
    },
    async publishVersion(row) {
      if (!row || !row.version) return
      try {
        await this.$confirm('将 v' + row.version + ' 恢复为当前草稿、重新编译并发布，确认继续？', '发布指定版本', { type: 'warning' })
        await rollbackVersion(this.definitionId, row.version)
        const compiled = await compileRule(this.definitionId)
        const body = compiled && compiled.data ? compiled.data : compiled
        if (!body || !body.success) {
          this.$message.error('编译失败: ' + (body ? body.errorMessage : '未知错误'))
          return
        }
        await publishRule(this.definitionId, { changeLog: '发布规则集 v' + row.version })
        this.$message.success('发布成功')
        await this.loadContent()
        await this.loadVersions()
        if (this.$refs.scriptPanel) this.$refs.scriptPanel.refresh()
      } catch (e) {
        if (e !== 'cancel') this.$message.error(e.message || '发布失败')
      }
    },
    formatVersionTime(value) {
      return value ? String(value).replace('T', ' ') : '-'
    },
    formatVersionJson(value) {
      if (!value) return ''
      try {
        return JSON.stringify(JSON.parse(value), null, 2)
      } catch (e) {
        return String(value)
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.rs-designer {
  background: #fff;
  border-radius: 4px;
  padding: 16px 20px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.08);
  min-height: 100%;
  box-sizing: border-box;
}
.rs-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}
.rs-title-area,
.rs-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.rs-back {
  color: #606266;
}
.rs-title-icon {
  font-size: 18px;
  color: #1677ff;
}
.rs-title {
  font-size: 16px;
  font-weight: 700;
  color: #202733;
}
.toolbar-label {
  font-size: 13px;
  color: #606266;
}
.mode-select {
  width: 148px;
}
.tip-icon {
  color: #909399;
  cursor: pointer;
}
.rs-var-status {
  margin: 8px 0;
  font-size: 12px;
  color: #52c41a;
}
.rs-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 10px;
  margin: 12px 0;
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #f8fafc;
}
.summary-item {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.summary-label {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}
.summary-value {
  color: #1f2937;
  font-size: 13px;
  overflow-wrap: anywhere;
}
.rs-rule-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.rs-rule-card {
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
  padding: 12px;
}
.rs-rule-card.is-disabled {
  background: #f8fafc;
  opacity: .78;
}
.rs-rule-head {
  display: grid;
  grid-template-columns: 26px 42px 108px minmax(110px, 150px) minmax(160px, 1fr) 120px auto;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.drag-handle {
  cursor: move;
  color: #94a3b8;
  font-size: 16px;
  text-align: center;
}
.rule-index {
  font-weight: 700;
  color: #475569;
}
.rule-code,
.rule-name,
.rule-priority {
  width: 100%;
}
.rule-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
}
.btn-delete {
  color: #f56c6c;
}
.rs-rule-body {
  display: grid;
  grid-template-columns: minmax(360px, 1.1fr) minmax(340px, .9fr);
  gap: 14px;
}
.condition-panel,
.action-panel {
  min-width: 0;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px;
  background: #fbfdff;
}
.panel-title {
  font-size: 13px;
  font-weight: 700;
  color: #334155;
  margin-bottom: 8px;
}
.rs-loading,
.rs-empty {
  text-align: center;
  color: #909399;
  padding: 36px 0;
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
}
.rs-empty-icon {
  font-size: 30px;
  color: #bfbfbf;
}
.script-override-banner {
  margin-top: 10px;
  padding: 8px 10px;
  border: 1px solid #ffd591;
  background: #fff7e6;
  color: #ad6800;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.test-alert {
  margin-bottom: 10px;
}
.result-pre,
.version-json,
.version-diff-grid pre {
  margin: 0;
  padding: 10px;
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 4px;
  max-height: 360px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
}
.text-danger {
  color: #f56c6c;
}
.version-compare {
  margin-top: 12px;
}
.version-diff-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 10px;
}
@media (max-width: 980px) {
  .rs-rule-head,
  .rs-rule-body,
  .version-diff-grid {
    grid-template-columns: 1fr;
  }
  .rule-actions {
    justify-content: flex-start;
  }
}
</style>
