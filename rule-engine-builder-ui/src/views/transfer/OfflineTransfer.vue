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
        <div class="card-actions">
          <el-button size="small" :disabled="exporting" @click="addRoot">添加根资源</el-button>
          <el-button type="primary" :loading="exporting" @click="exportPackage">生成并下载配置包</el-button>
        </div>
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
          <el-select v-model="options.targetScope" aria-label="目标范围" placeholder="目标范围" @change="onTargetScopeChange">
            <el-option label="项目级" value="PROJECT" />
            <el-option label="全局" value="GLOBAL" />
          </el-select>
          <remote-filter-select
            v-if="options.targetScope === 'PROJECT' && !options.createProject"
            v-model:value="options.targetProjectId"
            :fetch-options="fetchTargetProjects"
            option-label-key="label"
            option-value-key="id"
            aria-label="目标项目"
            placeholder="搜索项目名称或编码"
          />
          <el-checkbox v-if="options.targetScope === 'PROJECT'" v-model="options.createProject">导入时新建项目</el-checkbox>
          <el-input v-if="options.createProject" v-model="options.projectCode" placeholder="新项目编码（可覆盖源编码）" />
          <el-input v-if="options.createProject" v-model="options.projectName" placeholder="新项目名称（可覆盖源名称）" />
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
          <el-input v-model="options.suffix" placeholder="新建后缀，如 _dev" />
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

    <el-dialog v-model="resultVisible" title="导入结果" width="620px">
      <el-alert type="success" :closable="false" title="配置已导入">
        {{ appliedOptions?.publishRules ? '规则已按所选策略发布。' : '规则保存为目标环境草稿。' }}其他资源已通过治理流程导入生效；复用、覆盖和新建明细见下方结果。
      </el-alert>
      <pre class="result-json">{{ JSON.stringify(importResult, null, 2) }}</pre>
    </el-dialog>
  </div>
</template>

<script>
import { ElMessage, ElMessageBox } from 'element-plus'
import { exportResourceTransfer, importResourceTransfer, previewResourceTransfer, listTransferResources } from '@/api/transfer'
import { listProjects } from '@/api/project'
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'

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
  components: { RemoteFilterSelect },
  data() {
    return {
      resourceTypes: RESOURCE_TYPES,
      roots: [newRoot(1, this.fetchRootOptionsByKey)],
      nextRootKey: 2,
      exporting: false,
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
      options: {
        targetScope: 'PROJECT', targetProjectId: '', createProject: false,
        projectCode: '', projectName: '', publishRules: false,
        variablePolicy: 'REUSE', resourcePolicy: 'SUFFIX', suffix: '_imported'
      }
    }
  },
  computed: {
    optionsSignature() { return JSON.stringify(this.normalizedOptions()) },
    hasCurrentPreview() {
      return Boolean(this.preview && !this.previewing && this.importFile === this.previewFile
        && this.optionsSignature === this.previewOptionsSignature)
    }
  },
  watch: {
    options: { deep: true, flush: 'sync', handler() { this.invalidatePreview() } }
  },
  beforeUnmount() { this.invalidatePreview() },
  methods: {
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
    selectFile(upload) { this.importFile = upload?.raw || null; this.invalidatePreview() },
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
        const response = await exportResourceTransfer(roots)
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
        publishRules: Boolean(this.options.publishRules)
      }
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
.import-options { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-top: 16px; }
.preview-card { margin-top: 18px; }
.preview-summary { display: flex; flex-wrap: wrap; gap: 16px; padding: 16px 0; color: var(--el-text-color-secondary); font-size: 13px; }
.preview-warning { margin-top: 16px; }
.import-hint { margin: 10px 0 0; color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.5; }
.preview-warning ul { margin: 0; padding-left: 18px; }
.result-json { max-height: 360px; overflow: auto; background: var(--el-fill-color-light); padding: 12px; border-radius: 8px; font-size: 12px; }
@media (max-width: 900px) { .transfer-grid { grid-template-columns: 1fr; } }
@media (max-width: 560px) { .root-row { grid-template-columns: 1fr auto; } .root-type { grid-column: 1 / -1; } }
</style>
