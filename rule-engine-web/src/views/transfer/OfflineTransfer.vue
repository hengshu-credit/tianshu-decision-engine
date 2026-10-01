<template>
  <div class="uiue-list-page transfer-page">
    <header class="page-header">
      <div>
        <span class="page-eyebrow">CROSS ENVIRONMENT</span>
        <h1>离线配置迁移</h1>
        <p>按血缘导出配置，上传到其他环境后预检冲突并确认导入。名单记录、日志和账单不会进入配置包。</p>
      </div>
      <el-tag type="info" effect="plain">配置包即时生成</el-tag>
    </header>

    <el-tabs v-model="activeTab" class="transfer-tabs" @tab-change="onTabChange">
      <el-tab-pane label="配置迁移" name="transfer">
    <section class="transfer-grid">
      <article class="transfer-card">
        <div class="card-heading">
          <div>
            <span class="card-kicker">EXPORT</span>
            <h2>导出配置</h2>
          </div>
          <el-tag size="small" type="info">按血缘带出上游</el-tag>
        </div>
        <p class="card-help">按名称或编码搜索要迁移的资源，可添加多个。系统会按血缘一并导出所依赖的配置。</p>
        <div v-for="(root, index) in roots" :key="root.key" class="root-row">
          <el-select v-model="root.resourceType" size="small" class="root-type" :aria-label="`第 ${index + 1} 项资源类型`" :disabled="exporting" @change="onRootTypeChange(root)">
            <el-option v-for="type in resourceTypes" :key="type.value" :label="type.label" :value="type.value" />
          </el-select>
          <div class="root-resource">
            <remote-filter-select
              :key="`${root.key}:${root.resourceType}`"
              v-model:value="root.resourceId"
              :fetch-options="root.fetchOptions"
              option-label-key="label"
              option-value-key="id"
              :aria-label="`第 ${index + 1} 项导出资源`"
              placeholder="搜索资源名称或编码"
              size="small"
              :disabled="exporting"
              @change="onRootSelectionChange(root)"
            />
            <p v-if="root.loadError" class="root-error" role="alert">{{ root.loadError }}</p>
          </div>
          <el-button v-if="roots.length > 1" link type="danger" :disabled="exporting" :aria-label="`移除第 ${index + 1} 项资源`" @click="removeRoot(index)">移除</el-button>
        </div>
        <el-alert v-if="exportError" :title="exportError" type="error" :closable="false" />
        <el-checkbox v-model="exportOnlySelected" :disabled="exporting">
          只迁移所选内容，不带出上游依赖
        </el-checkbox>
        <div class="card-actions">
          <el-button size="small" :disabled="exporting" @click="addRoot">添加根资源</el-button>
          <el-button type="primary" :loading="exporting" @click="exportPackage">生成并下载配置包</el-button>
        </div>
        <section v-if="exportLineageRoots.length" class="inline-lineage-panel">
          <div class="inline-lineage-toolbar">
            <strong>导出内容血缘</strong>
            <el-select v-model="selectedExportRootKey" size="small" aria-label="选择导出内容血缘" style="width: 250px">
              <el-option v-for="item in exportLineageRoots" :key="item.key" :label="item.label" :value="item.key" />
            </el-select>
          </div>
          <lineage-graph v-if="exportLineageGraph" embedded :initial-graph="exportLineageGraph" />
          <el-empty v-else-if="!exportLineageLoading" description="该内容暂无可用血缘" />
        </section>
      </article>

      <article class="transfer-card">
        <div class="card-heading">
          <div>
            <span class="card-kicker">IMPORT</span>
            <h2>导入配置</h2>
          </div>
          <el-tag size="small" type="warning">先预检再写入</el-tag>
        </div>
        <el-upload drag :auto-upload="false" :show-file-list="false" accept=".zip" :disabled="applying" :on-change="selectFile">
          <div class="upload-copy">
            <strong>{{ importFile ? importFile.name : '拖入配置包，或点击选择 ZIP' }}</strong>
            <span>只接受 TIANSHU_RESOURCE_TRANSFER 配置包</span>
          </div>
        </el-upload>
        <el-form :disabled="applying" class="import-options">
          <div class="import-options__target">
            <div class="import-field">
              <span class="import-field__label">目标范围</span>
              <el-select v-model="options.targetScope" aria-label="目标范围" placeholder="目标范围" @change="onTargetScopeChange">
                <el-option label="项目级" value="PROJECT" />
                <el-option label="全局" value="GLOBAL" />
              </el-select>
            </div>
            <div class="import-field import-field--project">
              <span class="import-field__label">目标项目</span>
              <div class="project-target-slot">
                <remote-filter-select
                  v-show="options.targetScope === 'PROJECT' && !options.createProject"
                  v-model:value="options.targetProjectId"
                  :fetch-options="fetchTargetProjects"
                  option-label-key="label"
                  option-value-key="id"
                  aria-label="目标项目"
                  placeholder="搜索项目名称或编码"
                />
                <span v-show="options.targetScope === 'PROJECT' && options.createProject" class="project-target-state">导入时创建新项目</span>
                <span v-show="options.targetScope === 'GLOBAL'" class="project-target-state">全局范围，不绑定项目</span>
              </div>
            </div>
            <el-checkbox v-show="options.targetScope === 'PROJECT'" v-model="options.createProject" class="create-project-toggle">导入时新建项目</el-checkbox>
          </div>
          <div class="project-create-fields" :class="{ 'is-enabled': options.targetScope === 'PROJECT' && options.createProject }">
            <div class="project-create-fields__heading">新建项目配置</div>
            <div v-show="options.targetScope === 'PROJECT' && options.createProject" class="project-create-fields__inputs">
              <el-input v-model="options.projectCode" aria-label="新项目编码" placeholder="新项目编码（可覆盖源编码）" />
              <el-input v-model="options.projectName" aria-label="新项目名称" placeholder="新项目名称（可覆盖源名称）" />
            </div>
            <span v-show="!(options.targetScope === 'PROJECT' && options.createProject)" class="project-create-fields__placeholder">开启“导入时新建项目”后，在此填写目标项目编码和名称。</span>
          </div>
          <div class="import-options__policies">
            <el-checkbox v-model="options.publishRules">导入并发布规则（重建固定版本）</el-checkbox>
            <el-select v-model="options.variablePolicy" aria-label="变量同码策略" placeholder="变量同码策略">
              <el-option label="同编码同类型复用" value="REUSE" />
              <el-option label="新建并追加后缀" value="SUFFIX" />
            </el-select>
            <el-select v-model="options.resourcePolicy" aria-label="资源冲突策略" placeholder="资源冲突策略">
              <el-option label="新建并追加后缀" value="SUFFIX" />
              <el-option label="复用相同配置" value="REUSE" />
              <el-option label="覆盖已有配置" value="OVERWRITE" />
            </el-select>
            <el-input v-model="options.suffix" aria-label="新建后缀" placeholder="新建后缀，如 _dev" />
          </div>
        </el-form>
        <p class="import-hint">项目级导入请选择目标项目，或勾选“导入时新建项目”；全局导入会把项目级资源转换为全局范围。修改文件或选项后需重新预览冲突。</p>
        <el-alert v-if="previewError" :title="previewError" type="error" :closable="false" />
        <div class="card-actions">
          <el-button :disabled="!importFile || applying" :loading="previewing" @click="previewPackage">预览冲突</el-button>
          <el-button type="primary" :disabled="!hasCurrentPreview || applying" :loading="applying" @click="applyPackage">确认导入</el-button>
        </div>
      </article>
    </section>

    <section v-if="preview" class="transfer-card preview-card">
      <div class="card-heading">
        <div>
          <span class="card-kicker">PREVIEW</span>
          <h2>导入预检</h2>
        </div>
        <el-tag :type="preview.conflictCount ? 'warning' : 'success'">
          {{ preview.conflictCount ? preview.conflictCount + ' 个冲突' : '未发现治理快照冲突' }}
        </el-tag>
      </div>
      <div class="preview-summary">
        <span>资源 {{ preview.resources?.length || 0 }} 个</span>
        <span>根节点 {{ preview.roots?.length || 0 }} 个</span>
        <span>名单数据：不包含</span>
        <span>包摘要：{{ preview.packageDigest }}</span>
      </div>
      <div v-if="preview.resources?.length" class="selected-resource-list">
        <div class="selected-resource-list__heading">
          <strong>选择要导入的内容</strong>
          <span>取消勾选的资源会转为外部关联，导入时需要在下方选择目标内容。</span>
        </div>
        <div class="selected-resource-list__items">
          <div v-for="item in preview.resources" :key="item.key" class="resource-review-row">
            <el-checkbox v-model="selectedResourceKeysDraft" :value="item.key">
              {{ item.resourceCode || item.key }} · {{ item.resourceType }}
            </el-checkbox>
            <el-tag v-if="item.recommendedAction === 'REUSE'" size="small" type="success">系统建议复用</el-tag>
            <span v-if="item.existingResourceKey" class="resource-reuse-target">目标候选：{{ item.existingResourceKey }}</span>
            <el-select
              v-if="item.reviewable"
              v-model="resourceActionsDraft[item.key]"
              size="small"
              class="resource-action-select"
              aria-label="迁移处理方式"
            >
              <el-option v-for="action in resourceActionOptions(item)" :key="action.value" :label="action.label" :value="action.value" />
            </el-select>
          </div>
        </div>
      </div>
      <div v-if="previewLineageRoots.length" class="offline-lineage-panel">
        <div class="offline-lineage-toolbar">
          <div>
            <strong>配置包血缘</strong>
            <span>血缘来自离线包清单，不依赖目标环境现有配置。</span>
          </div>
          <el-select v-model="selectedLineageRoot" size="small" aria-label="选择血缘起点" style="width: 280px">
            <el-option v-for="root in previewLineageRoots" :key="root.key" :label="root.label" :value="root.key" />
          </el-select>
        </div>
        <lineage-graph v-if="offlineLineageGraph" embedded :initial-graph="offlineLineageGraph" />
      </div>
      <transfer-association-panel
        :associations="preview.associations || []"
        :resource-bindings="resourceBindingsDraft"
        :field-bindings="fieldBindingsDraft"
        @update:resource-bindings="resourceBindingsDraft = $event"
        @update:field-bindings="fieldBindingsDraft = $event"
        @search="searchAssociationCandidates"
      />
      <el-table :data="preview.conflicts || []" size="small" class="transfer-table">
        <el-table-column prop="resourceType" label="资源类型" width="150" />
        <el-table-column prop="resourceCode" label="编码" min-width="180" />
        <el-table-column prop="conflictType" label="冲突" width="180" />
        <el-table-column prop="recommendedAction" label="建议" width="150" />
        <el-table-column prop="message" label="处理说明" min-width="260" />
      </el-table>
      <el-alert v-if="preview.warnings?.length" type="warning" :closable="false" title="导入包存在提示" class="preview-warning">
        <template #default><ul><li v-for="warning in preview.warnings" :key="warning">{{ warning }}</li></ul></template>
      </el-alert>
      <el-alert v-if="preview.resources?.some(item => item.requiredEnvironmentFields?.length)" type="info" :closable="false" title="目标环境需要补充配置" class="preview-warning">
        外数、数据库凭据和 Java/Bean 函数运行依赖不会从源环境直接复制，请在目标环境完成复核。
      </el-alert>
    </section>

      </el-tab-pane>
      <el-tab-pane label="迁移日志" name="logs">
        <section class="transfer-card transfer-log-card">
          <div class="card-heading">
            <div>
              <span class="card-kicker">AUDIT LOG</span>
              <h2>迁移日志</h2>
            </div>
            <el-button size="small" @click="loadLogs">刷新</el-button>
          </div>
          <el-table v-loading="logsLoading" :data="logs" size="small" class="transfer-table">
            <el-table-column prop="operationType" label="操作" width="100">
              <template #default="{ row }">{{ row.operationType === 'EXPORT' ? '导出' : '导入' }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }"><el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">{{ row.status === 'SUCCESS' ? '成功' : '失败' }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="operator" label="操作人" width="140" show-overflow-tooltip />
            <el-table-column prop="resourceCount" label="资源数量" width="100" />
            <el-table-column prop="packageDigest" label="包摘要" min-width="220" show-overflow-tooltip />
            <el-table-column prop="createTime" label="时间" width="180" />
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{ row }"><el-button link type="primary" size="small" @click="openLogDetail(row)">查看</el-button></template>
            </el-table-column>
          </el-table>
          <el-pagination v-model:current-page="logPageNum" v-model:page-size="logPageSize" :total="logTotal" size="small" layout="total, prev, pager, next" @current-change="loadLogs" />
        </section>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="resultVisible" title="导入结果" width="620px">
      <el-alert type="success" :closable="false" title="配置已导入">
        {{ appliedOptions?.publishRules ? '规则已按所选策略发布。' : '规则保存为目标环境草稿。' }}其他资源已通过治理流程导入生效；复用、覆盖和新建明细见下方结果。
      </el-alert>
      <pre class="result-json">{{ JSON.stringify(importResult, null, 2) }}</pre>
    </el-dialog>
    <el-dialog v-model="logDetailVisible" title="迁移日志详情" width="900px">
      <el-alert v-if="logDetail" :type="logDetail.status === 'SUCCESS' ? 'success' : 'warning'" :closable="false" :title="`${logDetail.operationType === 'EXPORT' ? '导出' : '导入'} · ${logDetail.status}`" />
      <pre v-if="logDetail" class="result-json">{{ logDetail.contentJson || '{}' }}</pre>
      <section class="log-lineage-section">
        <div class="inline-lineage-toolbar"><strong>本次迁移血缘</strong><span v-if="!logLineageLoading && !logLineage?.nodes?.length">日志中没有可用血缘，缺失内容已跳过。</span></div>
        <lineage-graph v-if="logLineage?.nodes?.length" embedded :initial-graph="logLineage" />
        <div v-else-if="logLineageLoading" class="lineage-loading">正在生成血缘图…</div>
      </section>
    </el-dialog>
  </div>
</template>

<script>
import { ElMessage, ElMessageBox } from 'element-plus'
import { exportResourceTransfer, importResourceTransfer, previewResourceTransfer, listTransferResources, listTransferResourceFields, listTransferLogs, getTransferLog, getTransferLogLineage } from '@/api/transfer'
import { getLineageGraph } from '@/api/lineage'
import { listProjects } from '@/api/project'
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'
import LineageGraph from '@/views/lineage/LineageGraph.vue'
import TransferAssociationPanel from '@/components/transfer/TransferAssociationPanel.vue'

const RESOURCE_TYPES = [
  { value: 'PROJECT', label: '项目' },
  { value: 'RULE', label: '规则' },
  { value: 'VARIABLE', label: '变量/常量' },
  { value: 'DATA_OBJECT', label: '数据对象' },
  { value: 'FUNCTION', label: '函数' },
  { value: 'DATABASE', label: '数据库' },
  { value: 'EXTERNAL_DATASOURCE', label: '外数数据源' },
  { value: 'EXTERNAL_API', label: '外数 API' },
  { value: 'LIST_LIBRARY', label: '名单配置' },
  { value: 'MODEL', label: '模型' },
  { value: 'EXPERIMENT', label: '分流实验' }
]

const LINEAGE_TYPES = { DATABASE: 'DB', EXTERNAL_DATASOURCE: 'DATASOURCE', EXTERNAL_API: 'API', LIST_LIBRARY: 'LIST' }

const newRoot = (key, fetch) => ({ key, resourceType: 'RULE', resourceId: '', loadError: '', requestId: 0, fetchOptions: params => fetch(key, params) })

export default {
  name: 'OfflineTransfer',
  components: { RemoteFilterSelect, LineageGraph, TransferAssociationPanel },
  data() {
    return {
      resourceTypes: RESOURCE_TYPES,
      roots: [newRoot(1, this.fetchRootOptionsByKey)],
      nextRootKey: 2,
      exporting: false,
      exportOnlySelected: false,
      exportError: '',
      previewing: false,
      applying: false,
      importFile: null,
      preview: null,
      previewFile: null,
      previewOptionsSignature: '',
      previewRequestId: 0,
      previewError: '',
      appliedOptions: null,
      importResult: null,
      resultVisible: false,
      activeTab: 'transfer',
      logs: [], logsLoading: false, logTotal: 0, logPageNum: 1, logPageSize: 20,
      logDetail: null,
      logDetailVisible: false,
      logLineage: null,
      logLineageLoading: false,
      selectedLineageRoot: '',
      selectedExportRootKey: '',
      exportLineageGraphs: {},
      exportLineageLoading: false,
      options: {
        targetScope: 'PROJECT', targetProjectId: '', createProject: false,
        projectCode: '', projectName: '', publishRules: false,
        variablePolicy: 'REUSE', resourcePolicy: 'SUFFIX', suffix: '_imported',
        selectedResourceKeys: [], resourceBindings: {}, fieldBindings: {}
      },
      selectedResourceKeysDraft: [],
      resourceActionsDraft: {},
      resourceBindingsDraft: {},
      fieldBindingsDraft: {},
    }
  },
  computed: {
    optionsSignature() { return JSON.stringify(this.normalizedOptions()) },
    hasCurrentPreview() {
      return Boolean(this.preview && !this.previewing && this.importFile === this.previewFile
        && this.optionsSignature === this.previewOptionsSignature)
    },
    previewLineageRoots() {
      const resources = this.preview?.resources || []
      return resources.map(item => ({ key: item.key, label: `${item.resourceCode || item.key} · ${item.resourceType}` }))
    },
    exportLineageRoots() {
      return this.roots.filter(item => item.resourceId).map(item => ({
        key: item.key,
        label: `${item.resourceType} · ${item.resourceId}`,
      }))
    },
    exportLineageGraph() {
      return this.exportLineageGraphs[this.selectedExportRootKey] || null
    },
    offlineLineageGraph() {
      if (!this.preview || !this.selectedLineageRoot) return null
      const resources = this.preview.resources || []
      const selected = resources.find(item => item.key === this.selectedLineageRoot)
      if (!selected) return null
      const typeMap = { PROJECT: 'PROJECT', RULE: 'RULE', VARIABLE: 'VARIABLE', DATA_OBJECT: 'DATA_OBJECT', FUNCTION: 'FUNCTION', DATABASE: 'DB', EXTERNAL_DATASOURCE: 'DATASOURCE', EXTERNAL_API: 'API', LIST_LIBRARY: 'LIST', MODEL: 'MODEL', EXPERIMENT: 'EXPERIMENT', RULE_VERSION: 'RULE' }
      const nodeMap = new Map(resources.map(item => [item.key, {
        id: item.key, type: typeMap[item.resourceType] || item.resourceType,
        label: item.resourceCode || item.key, code: item.resourceCode || item.key,
        refId: item.sourceResourceId,
      }]))
      const edges = resources.flatMap(item => (item.references || []).map(reference => ({
        from: item.key, to: reference.targetKey, label: reference.path || '依赖',
      })))
      const related = new Set([selected.key])
      const queue = [selected.key]
      while (queue.length) {
        const current = queue.shift()
        edges.forEach(edge => {
          if (edge.from === current && !related.has(edge.to)) { related.add(edge.to); queue.push(edge.to) }
          if (edge.to === current && !related.has(edge.from)) { related.add(edge.from); queue.push(edge.from) }
        })
      }
      return {
        startNode: nodeMap.get(selected.key),
        nodes: [...related].map(key => nodeMap.get(key)).filter(Boolean),
        edges: edges.filter(edge => related.has(edge.from) && related.has(edge.to)
          && nodeMap.has(edge.from) && nodeMap.has(edge.to)),
      }
    }
  },
  watch: {
    options: { deep: true, flush: 'sync', handler() { this.invalidatePreview() } }
  },
  created() { this.loadLogs() },
  beforeUnmount() { this.invalidatePreview() },
  methods: {
    onTabChange(tab) { if (tab === 'logs') this.loadLogs() },
    async loadLogs() {
      this.logsLoading = true
      try {
        const response = await listTransferLogs({ pageNum: this.logPageNum, pageSize: this.logPageSize })
        const data = response.data || {}
        this.logs = data.records || []
        this.logTotal = data.total || 0
      } catch (error) {
        this.logs = []
        this.logTotal = 0
      } finally { this.logsLoading = false }
    },
    async openLogDetail(row) {
      this.logDetailVisible = true
      this.logLineageLoading = true
      try {
        const [detail, lineage] = await Promise.all([getTransferLog(row.id), getTransferLogLineage(row.id)])
        this.logDetail = detail.data || null
        this.logLineage = lineage.data || null
      } catch (error) {
        this.logDetail = null
        this.logLineage = null
      } finally {
        this.logLineageLoading = false
      }
    },
    addRoot() { this.roots.push(newRoot(this.nextRootKey++, this.fetchRootOptionsByKey)) },
    removeRoot(index) { this.roots.splice(index, 1) },
    onRootTypeChange(root) {
      root.resourceId = ''
      root.loadError = ''
      root.requestId += 1
      this.exportError = ''
    },
    onRootSelectionChange(root) {
      this.exportError = ''
      if (!root.resourceId) return
      if (this.roots.some(other => other !== root && other.resourceType === root.resourceType
        && String(other.resourceId) === String(root.resourceId))) {
        root.resourceId = ''
        ElMessage.warning('该资源已添加，请选择其他资源')
      }
      this.selectedExportRootKey = root.key
      this.loadExportLineage(root)
    },
    async loadExportLineage(root) {
      if (!root || !root.resourceId) return
      this.exportLineageLoading = true
      try {
        const response = await getLineageGraph({
          nodeType: LINEAGE_TYPES[root.resourceType] || root.resourceType,
          nodeId: Number(root.resourceId),
          direction: 'ALL',
          maxDepth: 2,
        })
        this.exportLineageGraphs = { ...this.exportLineageGraphs, [root.key]: response.data || null }
      } catch (error) {
        this.exportLineageGraphs = { ...this.exportLineageGraphs, [root.key]: null }
      } finally {
        this.exportLineageLoading = false
      }
    },
    fetchRootOptionsByKey(key, params) {
      const root = this.roots.find(item => item.key === key)
      return root ? this.fetchRootOptions(root, params) : Promise.resolve({ records: [], total: 0 })
    },
    async fetchRootOptions(root, { query, pageNum, pageSize }) {
      const resourceType = root.resourceType
      const requestId = ++root.requestId
      root.loadError = ''
      try {
        const response = await listTransferResources({
          nodeType: LINEAGE_TYPES[resourceType] || resourceType, keyword: query, pageNum, pageSize
        })
        const data = response.data || {}
        const records = (data.records || []).map(item => {
          const scopeLabel = item.scope === 'GLOBAL' ? '全局'
            : item.projectName ? `${item.projectName}${item.projectCode ? ` / ${item.projectCode}` : ''}` : item.projectId ? `项目 ${item.projectId}` : '未绑定项目'
          return { id: item.id, label: resourceType === 'PROJECT' ? item.displayName : `${item.displayName} · ${scopeLabel}` }
        })
        return { ...data, records }
      } catch (error) {
        if (root.resourceType === resourceType && root.requestId === requestId) {
          root.loadError = '候选资源加载失败，请重新展开选择器或搜索重试'
        }
        throw error
      }
    },
    invalidatePreview() {
      this.previewRequestId += 1
      this.preview = null
      this.previewFile = null
      this.previewOptionsSignature = ''
      this.previewError = ''
      this.previewing = false
    },
    selectFile(upload) {
      this.importFile = upload?.raw || null
      this.selectedLineageRoot = ''
      this.selectedResourceKeysDraft = []
      this.resourceActionsDraft = {}
      this.resourceBindingsDraft = {}
      this.fieldBindingsDraft = {}
      this.invalidatePreview()
    },
    async fetchTargetProjects({ query, pageNum, pageSize }) {
      const response = await listProjects({ keyword: query, pageNum, pageSize })
      const data = response.data || {}
      return { ...data, records: (data.records || []).map(project => ({
        id: project.id, label: `${project.projectName} / ${project.projectCode}`
      })) }
    },
    onTargetScopeChange(scope) {
      if (scope !== 'GLOBAL') return
      this.options.createProject = false
      this.options.targetProjectId = ''
      this.options.projectCode = ''
      this.options.projectName = ''
    },
    async exportPackage() {
      if (this.exporting) return
      this.exportError = ''
      const roots = this.roots.map(item => ({ resourceType: item.resourceType, resourceId: Number(item.resourceId) }))
      if (!roots.length || roots.some(item => !Number.isSafeInteger(item.resourceId) || item.resourceId <= 0)) {
        ElMessage.warning('请为每一行选择要导出的资源')
        return
      }
      if (new Set(roots.map(item => `${item.resourceType}:${item.resourceId}`)).size !== roots.length) {
        ElMessage.warning('导出资源重复，请移除重复项')
        return
      }
      this.exporting = true
      try {
        const response = await exportResourceTransfer(roots, { includeDependencies: !this.exportOnlySelected })
        const url = URL.createObjectURL(response.data)
        const anchor = document.createElement('a')
        anchor.href = url
        anchor.download = 'tianshu-resource-transfer.zip'
        anchor.click()
        URL.revokeObjectURL(url)
      } catch (error) {
        this.exportError = error.message || '配置包生成失败，请重试'
      } finally { this.exporting = false }
    },
    async previewPackage() {
      if (this.applying || !this.importFile) return
      if (this.preview?.resources?.length && !this.selectedResourceKeysDraft.length) {
        ElMessage.warning('请至少选择一项要导入的内容')
        return
      }
      this.invalidatePreview()
      if (!this.validateTargetOptions()) return
      const requestId = this.previewRequestId
      const file = this.importFile
      const options = this.normalizedOptions()
      const signature = JSON.stringify(options)
      this.previewing = true
      try {
        const response = await previewResourceTransfer(file, options)
        if (requestId !== this.previewRequestId || file !== this.importFile || signature !== this.optionsSignature) return
        if (!response.data?.packageDigest) throw new Error('预检未返回有效的配置包摘要，请重新预览冲突')
        this.preview = response.data
        this.selectedResourceKeysDraft = (response.data.selectedResourceKeys
          || (response.data.resources || []).map((item) => item.key)).slice()
        this.resourceActionsDraft = { ...this.resourceActionsDraft }
        const previewResources = response.data.resources || []
        previewResources.forEach((item) => {
          if (item.selectedAction) this.resourceActionsDraft[item.key] = item.selectedAction
        })
        this.resourceBindingsDraft = { ...(this.options.resourceBindings || {}), ...this.resourceBindingsDraft }
        this.fieldBindingsDraft = { ...(this.options.fieldBindings || {}), ...this.fieldBindingsDraft }
        this.selectedLineageRoot = response.data.roots?.[0]
          || response.data.resources?.[0]?.key || ''
        this.previewFile = file
        this.previewOptionsSignature = signature
      } catch (error) {
        if (requestId === this.previewRequestId) this.previewError = error.message || '预检失败，请重新预览冲突'
      } finally {
        if (requestId === this.previewRequestId) this.previewing = false
      }
    },
    async applyPackage() {
      if (this.applying) return
      if (this.preview?.resources?.length && !this.selectedResourceKeysDraft.length) {
        ElMessage.warning('请至少选择一项要导入的内容')
        return
      }
      if (!this.hasCurrentPreview) {
        ElMessage.warning('请先对当前文件和导入选项重新预览冲突')
        return
      }
      if (!this.validateTargetOptions()) return
      const file = this.importFile
      const options = this.normalizedOptions()
      const requestId = this.previewRequestId
      const projectHint = options.targetScope === 'GLOBAL' ? '全局范围'
        : options.createProject ? `新项目「${options.projectName || options.projectCode}」` : '所选项目'
      const ruleHint = options.publishRules ? '规则将按选项发布并重建固定版本' : '规则只创建目标环境草稿'
      this.applying = true
      try {
        await ElMessageBox.confirm(`确认导入到${projectHint}？普通资源将通过治理流程导入生效，${ruleHint}。`, '确认导入', { type: 'warning' })
        if (!this.hasCurrentPreview || requestId !== this.previewRequestId) {
          ElMessage.warning('导入配置已变化，请重新预览冲突')
          return
        }
        this.resultVisible = false
        this.importResult = null
        this.importResult = (await importResourceTransfer(file, options)).data
        this.appliedOptions = options
        this.resultVisible = true
        this.invalidatePreview()
      } catch (error) {
        if (error === 'cancel' || error === 'close') return
        this.invalidatePreview()
        if (!error.requestErrorNotified) ElMessage.error(error.message || '导入失败，请核对目标环境后重新预览冲突')
      } finally { this.applying = false }
    },
    normalizedOptions() {
      const createProject = this.options.targetScope === 'PROJECT' && Boolean(this.options.createProject)
      return {
        ...this.options,
        createProject,
        targetProjectId: this.options.targetScope === 'PROJECT' && !createProject && this.options.targetProjectId
          ? Number(this.options.targetProjectId) : null,
        projectCode: createProject ? this.options.projectCode.trim() : null,
        projectName: createProject ? this.options.projectName.trim() : null,
        projectBindings: {},
        publishRules: Boolean(this.options.publishRules),
        selectedResourceKeys: this.selectedResourceKeysDraft.slice(),
        resourceActions: { ...this.resourceActionsDraft },
        resourceBindings: { ...this.resourceBindingsDraft },
        fieldBindings: { ...this.fieldBindingsDraft },
      }
    },
    async searchAssociationCandidates(association) {
      if (!association) return
      try {
        const response = await listTransferResources({
          nodeType: LINEAGE_TYPES[association.targetResourceType] || association.targetResourceType,
          keyword: association.targetCode || '',
          pageNum: 1,
          pageSize: 50,
        })
        const data = response.data || {}
        const candidates = (data.records || []).map((item) => ({
          id: item.id,
          code: item.code || item.resourceCode,
          name: item.displayName || item.name,
          projectId: item.projectId,
          fields: item.fields || [],
        }))
        const target = (this.preview.associations || []).find((item) => item.referenceKey === association.referenceKey)
        if (target) {
          target.candidates = candidates
          const targetResourceId = association.targetResourceId || this.resourceBindingsDraft[association.targetKey]
          if (target.childPath && targetResourceId) {
            const fieldsResponse = await listTransferResourceFields('DATA_OBJECT', targetResourceId)
            target.fieldCandidates = fieldsResponse.data || []
          }
        }
      } catch (error) {
        ElMessage.error(error.message || '加载关联候选失败')
      }
    },
    resourceActionOptions(item) {
      const options = [
        { value: 'REUSE', label: '复用现有内容' },
        { value: 'SUFFIX', label: '新建并追加后缀' },
      ]
      if (item.resourceType !== 'VARIABLE' && item.resourceType !== 'PROJECT') {
        options.splice(1, 0, { value: 'OVERWRITE', label: '覆盖现有内容' })
      }
      return options
    },
    validateTargetOptions() {
      if (this.options.targetScope === 'PROJECT' && !this.options.createProject
        && !/^[1-9]\d*$/.test(String(this.options.targetProjectId || ''))) {
        ElMessage.warning('项目级导入请选择目标项目，或勾选导入时新建项目')
        return false
      }
      if (this.options.targetScope === 'PROJECT' && this.options.createProject && !this.options.projectCode.trim()) {
        ElMessage.warning('新建项目请填写项目编码')
        return false
      }
      return true
    }
  }
}
</script>

<style scoped>
.transfer-page { padding-bottom: 36px; }
.transfer-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; }
.transfer-card { background: var(--el-bg-color-overlay); border: 1px solid var(--el-border-color-light); border-radius: 14px; padding: 20px; box-shadow: 0 8px 28px rgba(15, 23, 42, .05); }
.transfer-tabs { margin-top: 4px; }
.transfer-tabs :deep(.el-tabs__content) { overflow: visible; }
.transfer-log-card { margin-top: 12px; }
.transfer-log-card .el-pagination { display: flex; justify-content: flex-end; margin-top: 14px; }
.card-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.card-kicker { color: var(--el-color-primary); font-size: 11px; letter-spacing: .12em; font-weight: 700; }
.card-heading h2 { margin: 5px 0 0; font-size: 20px; }
.card-help { color: var(--el-text-color-secondary); line-height: 1.65; min-height: 52px; }
.root-row { display: grid; grid-template-columns: 160px 1fr auto; gap: 8px; margin: 10px 0; }
.root-resource { min-width: 0; }
.root-error { margin: 6px 0 0; color: var(--el-color-danger); font-size: 12px; line-height: 1.5; }
.card-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 18px; }
.upload-copy { display: flex; flex-direction: column; gap: 8px; color: var(--el-text-color-secondary); }
.upload-copy strong { color: var(--el-text-color-primary); }
.import-options { display: grid; gap: 12px; margin-top: 16px; }
.import-options__target { display: grid; grid-template-columns: minmax(140px, .7fr) minmax(220px, 1.3fr) auto; gap: 10px; align-items: end; }
.import-field { display: grid; min-width: 0; gap: 5px; }
.import-field__label, .project-create-fields__heading { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.4; }
.project-target-slot { position: relative; min-width: 0; min-height: 32px; }
.project-target-slot > * { width: 100%; }
.project-target-state { display: flex; box-sizing: border-box; min-height: 32px; align-items: center; padding: 0 11px; border: 1px solid var(--el-border-color); border-radius: 4px; background: var(--el-fill-color-light); color: var(--el-text-color-secondary); font-size: 12px; }
.create-project-toggle { margin-bottom: 7px; white-space: nowrap; }
.project-create-fields { box-sizing: border-box; min-height: 82px; padding: 11px 12px; border: 1px solid var(--el-border-color-light); border-radius: 8px; background: var(--el-fill-color-extra-light); }
.project-create-fields.is-enabled { border-color: var(--el-color-primary-light-5); background: var(--tianshu-info-bg); }
.project-create-fields__inputs { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-top: 7px; }
.project-create-fields__placeholder { display: block; margin-top: 7px; color: var(--el-text-color-placeholder); font-size: 12px; line-height: 1.5; }
.import-options__policies { display: grid; grid-template-columns: auto repeat(3, minmax(0, 1fr)); gap: 10px; align-items: center; }
.import-options__policies .el-checkbox { white-space: nowrap; }
.preview-card { margin-top: 18px; }
.preview-summary { display: flex; flex-wrap: wrap; gap: 16px; padding: 16px 0; color: var(--el-text-color-secondary); font-size: 13px; }
.selected-resource-list { margin-top: 16px; padding: 12px; border: 1px solid var(--el-border-color-lighter); border-radius: 10px; background: var(--el-bg-color); }
.selected-resource-list__heading { display: flex; flex-wrap: wrap; gap: 8px; align-items: baseline; }
.selected-resource-list__heading span { color: var(--el-text-color-secondary); font-size: 12px; }
.selected-resource-list__items { display: grid; gap: 8px; margin-top: 8px; }
.resource-review-row { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.resource-action-select { width: 150px; }
.resource-reuse-target { color: var(--el-text-color-secondary); font-size: 12px; }
.inline-lineage-panel, .log-lineage-section { margin-top: 16px; padding: 12px; border: 1px solid var(--el-border-color-light); border-radius: 10px; background: var(--el-fill-color-extra-light); }
.inline-lineage-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 10px; color: var(--el-text-color-secondary); font-size: 12px; }
.inline-lineage-toolbar strong { color: var(--el-text-color-primary); font-size: 13px; }
.offline-lineage-panel { margin: 2px 0 18px; padding: 12px; border: 1px solid var(--el-border-color-light); border-radius: 10px; background: var(--el-fill-color-extra-light); }
.offline-lineage-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 14px; margin-bottom: 10px; }
.offline-lineage-toolbar > div { display: grid; gap: 3px; min-width: 0; }
.offline-lineage-toolbar strong { color: var(--el-text-color-primary); font-size: 13px; }
.offline-lineage-toolbar span { color: var(--el-text-color-secondary); font-size: 12px; }
.offline-lineage-panel :deep(.lineage-page) { padding: 0; }
.offline-lineage-panel :deep(.lineage-graph-layout) { margin-top: 0; }
.preview-warning { margin-top: 16px; }
.import-hint { margin: 10px 0 0; color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.5; }
.preview-warning ul { margin: 0; padding-left: 18px; }
.result-json { max-height: 360px; overflow: auto; background: var(--el-fill-color-light); padding: 12px; border-radius: 8px; font-size: 12px; }
@media (max-width: 900px) { .transfer-grid { grid-template-columns: 1fr; } }
@media (max-width: 900px) { .import-options__target, .import-options__policies { grid-template-columns: 1fr 1fr; } .create-project-toggle { grid-column: 1 / -1; margin-bottom: 0; } .import-options__policies .el-checkbox { grid-column: 1 / -1; } }
@media (max-width: 560px) { .root-row { grid-template-columns: 1fr auto; } .root-type { grid-column: 1 / -1; } .import-options__target, .project-create-fields__inputs, .import-options__policies { grid-template-columns: 1fr; } .create-project-toggle, .import-options__policies .el-checkbox { grid-column: auto; } }
</style>
