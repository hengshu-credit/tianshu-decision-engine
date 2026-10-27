<template>
  <div class="uiue-list-page management-list-page sftp-page">
    <div class="module-hint">
      <div class="hint-title">SFTP 数据源配置</div>
      <div class="hint-text">维护可复用的 SFTP 连接池。规则执行时的 SFTP 文件读取会沿用当前规则 trace_id，并在查询日志中保留远端文件地址。</div>
    </div>

    <el-tabs model-value="datasource" class="sftp-tabs" :before-leave="navigateTab">
      <el-tab-pane label="数据源配置" name="datasource" />
      <el-tab-pane label="查询日志" name="logs" />
    </el-tabs>

    <div class="uiue-search-container uiue-filter-toolbar">
      <el-form :inline="true" size="small" @keyup.enter="handleQuery">
        <el-form-item label="项目编码"><project-filter-select v-model:value="query.projectCode" field="projectCode" placeholder="项目编码" style="width: 150px" /></el-form-item>
        <el-form-item label="项目名称"><project-filter-select v-model:value="query.projectName" field="projectName" placeholder="项目名称" style="width: 150px" /></el-form-item>
        <el-form-item label="数据源编码"><el-input v-model="query.datasourceCode" clearable placeholder="编码" style="width: 150px" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="query.status" clearable placeholder="全部" style="width: 100px"><el-option label="启用" :value="1" /><el-option label="停用" :value="0" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="handleQuery">查询</el-button><el-button @click="resetQuery">重置</el-button></el-form-item>
      </el-form>
      <div class="uiue-btn-bar"><div class="btn-right"><table-column-settings v-model="datasourceColumnOrder" :columns="datasourceColumns" storage-key="tianshu:table-columns:sftp-list" /><governance-workspace-toolbar resource-type="SFTP" /><el-button size="small" @click="load">刷新</el-button><el-button v-permission="'database:edit'" type="primary" size="small" @click="openCreate">新建 SFTP 数据源</el-button></div></div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError"><el-button link type="primary" :disabled="loading" @click="load">重新加载</el-button></el-alert>
    <el-table class="management-table" :data="rows" border size="small" :aria-busy="loading" show-overflow-tooltip>
      <el-table-column v-for="column in visibleDatasourceColumns" :key="column.key" :prop="column.key" :label="column.label" :min-width="column.minWidth" :align="column.align">
        <template #default="{ row }">
          <el-tag v-if="column.key === 'scope'" size="small" :type="row.scope === 'GLOBAL' ? 'warning' : 'success'">{{ row.scope === 'GLOBAL' ? '全局' : '项目' }}</el-tag>
          <template v-else-if="column.key === 'address'">{{ row.host }}:{{ row.port || 22 }}{{ row.basePath || '/' }}</template>
          <template v-else-if="column.key === 'pool'">{{ row.minIdle ?? 1 }} / {{ row.maxPoolSize ?? 8 }}</template>
          <governance-resource-status v-else-if="column.key === 'status'" resource-type="SFTP" :resource-id="row.id" :status="row.status"><el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag></governance-resource-status>
          <template v-else>{{ row[column.key] || '—' }}</template>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="250" fixed="right" align="center"><template #default="{ row }"><el-button v-permission="{ code: 'database:edit', projectId: (row.projectId ?? row.ownerProjectId), scope: (row.scope ?? row.ownerScope) }" link type="primary" size="small" @click="openEdit(row)">编辑</el-button><el-button v-permission="{ code: 'database:edit', projectId: (row.projectId ?? row.ownerProjectId), scope: (row.scope ?? row.ownerScope) }" link type="success" size="small" @click="test(row)">测试</el-button><governance-publish-action resource-type="SFTP" :resource-id="row.id" /><el-button v-permission="{ code: 'database:edit', projectId: (row.projectId ?? row.ownerProjectId), scope: (row.scope ?? row.ownerScope) }" link type="danger" size="small" @click="remove(row)">删除</el-button></template></el-table-column>
    </el-table>
    <el-pagination style="margin-top: 12px; text-align: right" :current-page="query.pageNum" :page-size="query.pageSize" :total="total" layout="total,sizes,prev,pager,next" :page-sizes="[10, 20, 30, 50]" @current-change="page => { query.pageNum = page; load() }" @size-change="size => { query.pageSize = size; query.pageNum = 1; load() }" />

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑 SFTP 数据源' : '新建 SFTP 数据源'" width="820px" class="resizable-config-dialog" :close-on-click-modal="false" :before-close="closeDialog" :show-close="!saving" :close-on-press-escape="!saving">
      <el-alert v-if="connectionTestStatus !== 'idle'" :type="connectionTestOutdated ? 'warning' : connectionTestStatus" :title="connectionTestOutdated ? '连接配置已变更，请重新测试' : connectionTestMessage" :closable="false" show-icon />
      <p class="field-help">先选择范围并填写连接信息，再测试连接和保存草稿。保存后仍需提交审批，通过后才生效。</p>
      <el-form ref="formRef" :model="form" :rules="rules" :disabled="saving" label-width="130px" size="small">
        <scope-project-fields v-model:scope="form.scope" v-model:project-id="form.projectId" :projects="projects" project-label="项目名称" project-prop="projectId" />
        <el-form-item label="数据源编码" prop="datasourceCode"><el-input v-model="form.datasourceCode" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="数据源名称" prop="datasourceName"><el-input v-model="form.datasourceName" /></el-form-item>
        <div class="form-section"><div class="section-title">连接方式</div><el-row :gutter="12"><el-col :xs="24" :sm="16"><el-form-item label="主机" prop="host"><el-input v-model="form.host" placeholder="sftp.example.com" /></el-form-item></el-col><el-col :xs="24" :sm="8"><el-form-item label="端口"><el-input-number v-model="form.port" :min="1" :max="65535" controls-position="right" style="width: 100%" /></el-form-item></el-col></el-row><el-form-item label="远端根目录"><el-input v-model="form.basePath" placeholder="/upload/contracts" /></el-form-item><el-form-item label="用户名" prop="username"><el-input v-model="form.username" autocomplete="off" /></el-form-item><el-form-item label="密码"><el-input v-model="form.password" :disabled="isCredentialCleared('password')" type="password" show-password autocomplete="new-password" :placeholder="credentialPlaceholder('password', '使用私钥时可留空')" /><el-checkbox v-if="hasStoredCredential('password') || isCredentialCleared('password')" :model-value="isCredentialCleared('password')" @change="setCredentialCleared('password', $event)">清除已存密码</el-checkbox></el-form-item><el-form-item label="私钥"><el-input v-model="form.privateKey" :disabled="isCredentialCleared('privateKey')" type="textarea" :rows="3" :placeholder="credentialPlaceholder('privateKey', 'PEM 私钥内容；使用密码时可留空')" /><el-checkbox v-if="hasStoredCredential('privateKey') || isCredentialCleared('privateKey')" :model-value="isCredentialCleared('privateKey')" @change="setCredentialCleared('privateKey', $event)">清除已存私钥</el-checkbox></el-form-item><el-form-item label="私钥口令"><el-input v-model="form.passphrase" :disabled="isCredentialCleared('passphrase')" :placeholder="credentialPlaceholder('passphrase', '私钥的解密口令')" type="password" show-password autocomplete="new-password" /><el-checkbox v-if="hasStoredCredential('passphrase') || isCredentialCleared('passphrase')" :model-value="isCredentialCleared('passphrase')" @change="setCredentialCleared('passphrase', $event)">清除已存私钥口令</el-checkbox></el-form-item><el-form-item label="主机指纹校验"><el-switch v-model="form.strictHostKeyChecking" /><span class="field-help">开启后需填写 known_hosts；关闭时使用 JSch 默认的非交互连接模式。</span></el-form-item><el-form-item v-if="form.strictHostKeyChecking" label="known_hosts"><el-input v-model="form.knownHosts" :disabled="isCredentialCleared('knownHosts')" type="textarea" :rows="2" :placeholder="credentialPlaceholder('knownHosts', 'known_hosts 文件内容')" /><el-checkbox v-if="hasStoredCredential('knownHosts') || isCredentialCleared('knownHosts')" :model-value="isCredentialCleared('knownHosts')" @change="setCredentialCleared('knownHosts', $event)">清除已存主机指纹</el-checkbox></el-form-item></div>
        <div class="form-section"><div class="section-title">连接池与文件限制</div><el-row :gutter="12"><el-col :xs="24" :sm="8"><el-form-item label="最大连接数"><el-input-number v-model="form.maxPoolSize" :min="1" :max="100" controls-position="right" style="width: 100%" /></el-form-item></el-col><el-col :xs="24" :sm="8"><el-form-item label="最小空闲" prop="minIdle"><el-input-number v-model="form.minIdle" :min="0" :max="100" controls-position="right" style="width: 100%" /></el-form-item></el-col><el-col :xs="24" :sm="8"><el-form-item label="借用超时(ms)"><el-input-number v-model="form.borrowTimeoutMs" :min="100" :max="600000" controls-position="right" style="width: 100%" /></el-form-item></el-col></el-row><el-row :gutter="12"><el-col :xs="24" :sm="8"><el-form-item label="连接超时(ms)"><el-input-number v-model="form.connectTimeoutMs" :min="100" :max="600000" controls-position="right" style="width: 100%" /></el-form-item></el-col><el-col :xs="24" :sm="8"><el-form-item label="会话超时(ms)"><el-input-number v-model="form.sessionTimeoutMs" :min="100" :max="600000" controls-position="right" style="width: 100%" /></el-form-item></el-col><el-col :xs="24" :sm="8"><el-form-item label="Socket超时(ms)"><el-input-number v-model="form.socketTimeoutMs" :min="100" :max="600000" controls-position="right" style="width: 100%" /></el-form-item></el-col></el-row><el-row :gutter="12"><el-col :xs="24" :sm="8"><el-form-item label="空闲超时(ms)"><el-input-number v-model="form.idleTimeoutMs" :min="1000" :max="86400000" controls-position="right" style="width: 100%" /></el-form-item></el-col><el-col :xs="24" :sm="8"><el-form-item label="单文件上限(byte)"><el-input-number v-model="form.maxFileSizeBytes" :min="1024" :max="268435456" controls-position="right" style="width: 100%" /></el-form-item></el-col><el-col :xs="24" :sm="8"><el-form-item label="文件名编码"><el-input v-model="form.fileNameEncoding" placeholder="UTF-8" /></el-form-item></el-col></el-row></div>
        <el-form-item label="扩展配置"><el-input v-model="form.extraConfig" type="textarea" :rows="2" placeholder='JSON，例如 {"compression.s2c":"zlib,none"}' /></el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer><el-button :disabled="saving" @click="closeDialog">取消</el-button><el-button :loading="testing" :disabled="saving" @click="testDraft">测试连接</el-button><el-button v-permission="'database:edit'" type="primary" :loading="saving" :disabled="saving" @click="save">保存草稿</el-button></template>
    </el-dialog>
  </div>
</template>

<script>
import { createSftpDatasource, deleteSftpDatasource, getSftpDatasource, listSftpDatasources, testSftpDatasource, testSftpDatasourceDraft, updateSftpDatasource } from '@/api/sftp'
import { listProjects } from '@/api/project'
import { editableResource } from '@/api/governance'
import { completeResourceChange } from '@/utils/governanceWorkspace'
import ProjectFilterSelect from '@/components/ProjectFilterSelect.vue'
import GovernanceWorkspaceToolbar from '@/components/GovernanceWorkspaceToolbar.vue'
import GovernanceResourceStatus from '@/components/GovernanceResourceStatus.vue'
import GovernancePublishAction from '@/components/GovernancePublishAction.vue'
import ScopeProjectFields from '@/components/common/ScopeProjectFields.vue'
import TableColumnSettings from '@/components/common/TableColumnSettings.vue'
import { routeProjectId, withProjectContext } from '@/utils/projectContext'
import credentialEditMixin from '@/mixins/credentialEditMixin'
import configurationDraftMixin from '@/mixins/configurationDraftMixin'

const draftProtection = configurationDraftMixin(vm => ({ form: vm.dialogVisible ? vm.form : null }))

function emptyForm() {
  return { projectId: null, scope: 'PROJECT', datasourceCode: '', datasourceName: '', connectionMode: 'DIRECT', host: '', port: 22, basePath: '', username: '', clearCredentials: [], password: '', privateKey: '', passphrase: '', knownHosts: '', strictHostKeyChecking: false, connectTimeoutMs: 10000, sessionTimeoutMs: 30000, socketTimeoutMs: 30000, maxPoolSize: 8, minIdle: 1, borrowTimeoutMs: 5000, idleTimeoutMs: 600000, maxFileSizeBytes: 10485760, fileNameEncoding: 'UTF-8', extraConfig: '', description: '', status: 1 }
}

export default {
  name: 'SftpDatasourceList',
  mixins: [credentialEditMixin, draftProtection],
  components: { ProjectFilterSelect, GovernanceWorkspaceToolbar, GovernanceResourceStatus, GovernancePublishAction, ScopeProjectFields, TableColumnSettings },
  data() {
    return { datasourceColumns: [
      { key: 'scope', label: '作用范围', minWidth: 90, align: 'center' }, { key: 'projectName', label: '项目名称', minWidth: 130 },
      { key: 'datasourceCode', label: '数据源编码', minWidth: 150, required: true }, { key: 'datasourceName', label: '数据源名称', minWidth: 150 },
      { key: 'address', label: 'SFTP 地址', minWidth: 220 }, { key: 'username', label: '认证用户', minWidth: 110 },
      { key: 'pool', label: '连接池', minWidth: 120, align: 'center' }, { key: 'status', label: '状态', minWidth: 150, align: 'center' },
    ], datasourceColumnOrder: ['scope', 'projectName', 'datasourceCode', 'datasourceName', 'address', 'username', 'pool', 'status'], query: { pageNum: 1, pageSize: 20, projectCode: '', projectName: '', datasourceCode: '', status: null }, rows: [], total: 0, loading: false, loadError: '', listRequestId: 0, editRequestId: 0, projects: [], dialogVisible: false, form: emptyForm(), saving: false, testing: false, connectionTestStatus: 'idle', connectionTestSignature: '', connectionTestMessage: '', testRequestId: 0, rules: { projectId: [{ validator: (_rule, value, callback) => callback(this.form.scope === 'PROJECT' && !(Number(value) > 0) ? new Error('请选择所属项目') : undefined), trigger: 'change' }], minIdle: [{ validator: (_rule, value, callback) => callback(Number(value) > Number(this.form.maxPoolSize) ? new Error('最小空闲连接数不能超过最大连接数') : undefined), trigger: 'change' }], datasourceCode: [{ required: true, message: '请输入数据源编码', trigger: 'blur' }], datasourceName: [{ required: true, message: '请输入数据源名称', trigger: 'blur' }], host: [{ required: true, message: '请输入 SFTP 主机', trigger: 'blur' }], username: [{ required: true, message: '请输入用户名', trigger: 'blur' }] } }
  },
  beforeUnmount() { this.listRequestId++; this.testRequestId++; this.editRequestId++ },
  watch: { dialogVisible: { flush: 'sync', handler(visible) { if (!visible) this.resetConnectionTest() } } },
  computed: {
    visibleDatasourceColumns() { return this.datasourceColumnOrder.map(key => this.datasourceColumns.find(column => column.key === key)).filter(Boolean) },
    connectionTestOutdated() { return Boolean(this.connectionTestSignature && this.connectionTestSignature !== JSON.stringify(this.normalize(this.form))) },
  },
  created() { this.query.projectId = routeProjectId(this.$route, this.$store?.state?.currentProject) },
  async mounted() { await Promise.all([this.load(), this.loadProjects()]) },
  methods: {
    async load() {
      const requestId = ++this.listRequestId
      this.loading = true
      this.loadError = ''
      try {
        const res = await listSftpDatasources({ ...this.query })
        if (requestId !== this.listRequestId) return
        this.rows = res.data?.records || []
        this.total = res.data?.total || 0
      } catch (error) {
        if (requestId !== this.listRequestId) return
        this.rows = []
        this.total = 0
        this.loadError = error.message || 'SFTP 数据源读取失败，请重新加载'
      } finally {
        if (requestId === this.listRequestId) this.loading = false
      }
    },
    handleQuery() { this.query.pageNum = 1; return this.load() },
    async loadProjects() { try { const res = await listProjects({ pageNum: 1, pageSize: 500 }); this.projects = res.data?.records || [] } catch (e) { this.projects = [] } },
    async navigateTab(name) {
      if (name === 'logs') await this.$router.push(withProjectContext('/sftp/logs', this.query.projectId))
      // 标签用于路由导航，不切换本页空面板，避免缓存离开时的错误选中项。
      return false
    },
    resetQuery() { this.query = { ...this.query, pageNum: 1, projectCode: '', projectName: '', datasourceCode: '', status: null }; this.load() },
    openCreate() { if (this.saving) return; this.editRequestId++; this.resetConnectionTest(); this.form = { ...emptyForm(), projectId: this.query.projectId }; this.dialogVisible = true; this.markConfigurationSaved(); this.$nextTick(() => this.$refs.formRef?.clearValidate()) },
    async openEdit(row) {
      if (this.saving) return
      const requestId = ++this.editRequestId
      try {
        const res = await getSftpDatasource(row.id)
        if (requestId !== this.editRequestId) return
        const detail = await editableResource('SFTP', row.id, res.data || row)
        if (requestId !== this.editRequestId) return
        this.resetConnectionTest()
        this.form = { ...emptyForm(), ...detail, clearCredentials: [] }
        this.dialogVisible = true
        this.markConfigurationSaved()
        this.$nextTick(() => this.$refs.formRef?.clearValidate())
      } catch (error) {
        if (requestId !== this.editRequestId) return
        this.$message.error('读取 SFTP 配置或草稿失败，请重试：' + error.message)
      }
    },
    normalize(data) { const result = { ...data }; if (result.scope === 'GLOBAL') result.projectId = 0; if (!result.extraConfig) result.extraConfig = null; return result },
    async confirmConfigurationLeave() {
      if (this.saving) return false
      return draftProtection.methods.confirmConfigurationLeave.call(this)
    },
    async closeDialog(done) {
      if (!await this.confirmConfigurationLeave()) return
      this.discardDialog()
      if (typeof done === 'function') done()
    },
    discardDialog() {
      this.editRequestId++
      this.resetConnectionTest()
      this.dialogVisible = false
      this.form = emptyForm()
      this.markConfigurationSaved()
    },
    async save() {
      if (this.saving) return
      this.saving = true
      try {
        const valid = await this.$refs.formRef.validate().catch(() => false)
        if (!valid) return
        const payload = this.normalize(this.form)
        const response = payload.id ? await updateSftpDatasource(payload) : await createSftpDatasource(payload)
        this.discardDialog()
        await completeResourceChange(this, response, () => this.load())
      } finally { this.saving = false }
    },
    resetConnectionTest() { this.testRequestId++; this.testing = false; this.connectionTestStatus = 'idle'; this.connectionTestSignature = ''; this.connectionTestMessage = '' },
    async testDraft() {
      const valid = await this.$refs.formRef.validate().catch(() => false)
      if (!valid || this.testing || this.saving) return
      const requestId = ++this.testRequestId
      const payload = this.normalize(this.form)
      const signature = JSON.stringify(payload)
      this.testing = true
      try {
        await testSftpDatasourceDraft(payload)
        if (requestId !== this.testRequestId) return
        this.connectionTestSignature = signature
        this.connectionTestStatus = 'success'
        this.connectionTestMessage = 'SFTP 连接测试成功，结果对应本次测试配置'
        if (!this.connectionTestOutdated) this.$message.success('SFTP 连接成功')
      } catch (error) {
        if (requestId !== this.testRequestId) return
        this.connectionTestSignature = signature
        this.connectionTestStatus = 'error'
        this.connectionTestMessage = error.message || 'SFTP 连接测试失败，请检查地址、凭据与网络'
      } finally {
        if (requestId === this.testRequestId) this.testing = false
      }
    },
    async test(row) { try { await testSftpDatasource(row.id); this.$message.success('SFTP 连接成功') } catch (e) { /* request interceptor already shows the error */ } },
    remove(row) { this.$confirm(`确定删除「${row.datasourceName}」？`, '确认删除', { type: 'warning' }).then(async () => { const response = await deleteSftpDatasource(row.id); await completeResourceChange(this, response, () => this.load()) }).catch(() => {}) },
  },
}
</script>

<style scoped>
.sftp-page .module-hint {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  margin-bottom: 12px;
  border: 1px solid var(--tianshu-info-border);
  border-radius: 6px;
  background: var(--tianshu-info-bg);
  color: var(--tianshu-info-text);
}
.sftp-page .hint-title { color: inherit; font-weight: 700; white-space: nowrap; }
.sftp-page .hint-text { color: var(--tianshu-text-secondary); line-height: 1.5; }
.sftp-tabs { margin-bottom: 12px; }
.form-section { padding: 12px; margin: 4px 0 14px; border: 1px solid var(--tianshu-border-subtle); border-radius: 8px; }
.section-title { margin-bottom: 10px; color: var(--tianshu-text-primary); font-size: 13px; font-weight: 700; }
.field-help { margin-left: 8px; color: var(--tianshu-text-tertiary); font-size: 12px; }
@media (max-width: 900px) { .sftp-page :deep(.uiue-search-container) { align-items: flex-start; } }
@media (max-width: 720px) { .sftp-page .module-hint { align-items: flex-start; flex-direction: column; gap: 4px; } }
</style>
