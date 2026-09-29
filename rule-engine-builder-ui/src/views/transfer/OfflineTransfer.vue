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
        <p class="card-help">选择资源作为根节点，系统会递归带出变量、数据对象、函数、模型、外数、数据库、名单配置和规则依赖。</p>
        <div v-for="(root, index) in roots" :key="root.key" class="root-row">
          <el-select v-model="root.resourceType" size="small" class="root-type">
            <el-option v-for="type in resourceTypes" :key="type.value" :label="type.label" :value="type.value" />
          </el-select>
          <el-input v-model="root.resourceId" size="small" placeholder="资源 ID" inputmode="numeric" />
          <el-button v-if="roots.length > 1" link type="danger" @click="removeRoot(index)">移除</el-button>
        </div>
        <div class="card-actions">
          <el-button size="small" @click="addRoot">添加根资源</el-button>
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
        <el-upload drag :auto-upload="false" :show-file-list="false" accept=".zip" :on-change="selectFile">
          <div class="upload-copy">
            <strong>{{ importFile ? importFile.name : '拖入配置包，或点击选择 ZIP' }}</strong>
            <span>只接受 TIANSHU_RESOURCE_TRANSFER 配置包</span>
          </div>
        </el-upload>
        <div class="import-options">
          <el-select v-model="options.targetScope" placeholder="目标范围" @change="onTargetScopeChange">
            <el-option label="项目级" value="PROJECT" />
            <el-option label="全局" value="GLOBAL" />
          </el-select>
          <el-input v-if="options.targetScope === 'PROJECT' && !options.createProject" v-model="options.targetProjectId" placeholder="目标项目 ID" />
          <el-checkbox v-if="options.targetScope === 'PROJECT'" v-model="options.createProject">导入时新建项目</el-checkbox>
          <el-input v-if="options.createProject" v-model="options.projectCode" placeholder="新项目编码（可覆盖源编码）" />
          <el-input v-if="options.createProject" v-model="options.projectName" placeholder="新项目名称（可覆盖源名称）" />
          <el-checkbox v-model="options.publishRules">导入并发布规则（重建固定版本）</el-checkbox>
          <el-select v-model="options.variablePolicy" placeholder="变量同码策略">
            <el-option label="同编码同类型复用" value="REUSE" />
            <el-option label="新建并追加后缀" value="SUFFIX" />
          </el-select>
          <el-select v-model="options.resourcePolicy" placeholder="资源冲突策略">
            <el-option label="新建并追加后缀" value="SUFFIX" />
            <el-option label="复用相同配置" value="REUSE" />
            <el-option label="覆盖已有配置" value="OVERWRITE" />
          </el-select>
          <el-input v-model="options.suffix" placeholder="新建后缀，如 _dev" />
        </div>
        <p class="import-hint">项目级导入请选择目标项目 ID，或勾选“导入时新建项目”；全局导入会把项目级资源转换为全局范围。</p>
        <div class="card-actions">
          <el-button :disabled="!importFile" :loading="previewing" @click="previewPackage">预览冲突</el-button>
          <el-button type="primary" :disabled="!importFile || !preview" :loading="applying" @click="applyPackage">确认导入</el-button>
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
        规则保持为目标环境草稿，其他资源按治理审批流程写入。
      </el-alert>
      <pre class="result-json">{{ JSON.stringify(importResult, null, 2) }}</pre>
    </el-dialog>
  </div>
</template>

<script>
import { ElMessage, ElMessageBox } from 'element-plus'
import { exportResourceTransfer, importResourceTransfer, previewResourceTransfer } from '@/api/transfer'

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

export default {
  name: 'OfflineTransfer',
  data() {
    return {
      resourceTypes: RESOURCE_TYPES,
      roots: [{ resourceType: 'RULE', resourceId: '' }],
      exporting: false,
      previewing: false,
      applying: false,
      importFile: null,
      preview: null,
      importResult: null,
      resultVisible: false,
      options: {
        targetScope: 'PROJECT', targetProjectId: '', createProject: false,
        projectCode: '', projectName: '', publishRules: false,
        variablePolicy: 'REUSE', resourcePolicy: 'SUFFIX', suffix: '_imported'
      }
    }
  },
  methods: {
    addRoot() { this.roots.push({ resourceType: 'RULE', resourceId: '' }) },
    removeRoot(index) { this.roots.splice(index, 1) },
    selectFile(upload) { this.importFile = upload?.raw || null; this.preview = null },
    onTargetScopeChange(scope) {
      if (scope !== 'GLOBAL') return
      this.options.createProject = false
      this.options.targetProjectId = ''
      this.options.projectCode = ''
      this.options.projectName = ''
    },
    async exportPackage() {
      const roots = this.roots.map(item => ({ resourceType: item.resourceType, resourceId: Number(item.resourceId) }))
      if (roots.some(item => !Number.isInteger(item.resourceId) || item.resourceId <= 0)) {
        ElMessage.warning('请填写有效的根资源 ID')
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
      } finally { this.exporting = false }
    },
    async previewPackage() {
      if (!this.validateTargetOptions()) return
      this.previewing = true
      try { this.preview = (await previewResourceTransfer(this.importFile, this.normalizedOptions())).data } finally { this.previewing = false }
    },
    async applyPackage() {
      if (!this.validateTargetOptions()) return
      const projectHint = this.options.createProject ? '并新建目标项目' : '并绑定目标项目'
      const ruleHint = this.options.publishRules ? '规则将按选项发布并重建固定版本' : '规则只创建目标环境草稿'
      await ElMessageBox.confirm(`确认写入目标环境${projectHint}？普通资源会进入治理审批，${ruleHint}。`, '确认导入', { type: 'warning' })
      this.applying = true
      try {
        this.importResult = (await importResourceTransfer(this.importFile, this.normalizedOptions())).data
        this.resultVisible = true
      } finally { this.applying = false }
    },
    normalizedOptions() {
      return {
        ...this.options,
        targetProjectId: this.options.targetScope === 'PROJECT' && !this.options.createProject && this.options.targetProjectId
          ? Number(this.options.targetProjectId) : null,
        projectCode: this.options.createProject ? this.options.projectCode.trim() : null,
        projectName: this.options.createProject ? this.options.projectName.trim() : null,
        projectBindings: {},
        publishRules: Boolean(this.options.publishRules)
      }
    },
    validateTargetOptions() {
      if (this.options.targetScope === 'PROJECT' && !this.options.createProject
        && !/^[1-9]\d*$/.test(String(this.options.targetProjectId || ''))) {
        ElMessage.warning('项目级导入请填写目标项目 ID，或勾选导入时新建项目')
        return false
      }
      if (this.options.createProject && !this.options.projectCode.trim()) {
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
</style>
