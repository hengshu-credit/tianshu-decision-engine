<template>
  <div class="uiue-list-page management-list-page sftp-page">
    <div class="module-hint">
      <div class="hint-title">SFTP 数据源配置</div>
      <div class="hint-text">维护可复用的 SFTP 连接池。规则执行时的 SFTP 文件读取会沿用当前规则 trace_id，并在查询日志中保留远端文件地址。</div>
    </div>

    <div class="sftp-subnav">
      <el-button type="primary" plain size="small" @click="$router.push('/sftp')">数据源配置</el-button>
      <el-button size="small" @click="$router.push('/sftp/logs')">查询日志</el-button>
    </div>

    <div class="uiue-search-container uiue-filter-toolbar">
      <el-form :inline="true" size="small" @keyup.enter="load">
        <el-form-item label="项目编码"><project-filter-select v-model:value="query.projectCode" field="projectCode" placeholder="项目编码" style="width: 150px" /></el-form-item>
        <el-form-item label="项目名称"><project-filter-select v-model:value="query.projectName" field="projectName" placeholder="项目名称" style="width: 150px" /></el-form-item>
        <el-form-item label="数据源编码"><el-input v-model="query.datasourceCode" clearable placeholder="编码" style="width: 150px" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="query.status" clearable placeholder="全部" style="width: 100px"><el-option label="启用" :value="1" /><el-option label="停用" :value="0" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="load">查询</el-button><el-button @click="resetQuery">重置</el-button></el-form-item>
      </el-form>
      <div class="uiue-btn-bar"><div class="btn-right"><governance-workspace-toolbar resource-type="SFTP" /><el-button size="small" @click="load">刷新</el-button><el-button v-permission="'database:edit'" type="primary" size="small" @click="openCreate">新建 SFTP 数据源</el-button></div></div>
    </div>

    <el-table class="management-table" :data="rows" border size="small" v-loading="loading" show-overflow-tooltip>
      <el-table-column label="作用范围" width="90" align="center"><template #default="{ row }"><el-tag size="small" :type="row.scope === 'GLOBAL' ? 'warning' : 'success'">{{ row.scope === 'GLOBAL' ? '全局' : '项目' }}</el-tag></template></el-table-column>
      <el-table-column prop="projectName" label="项目名称" min-width="130"><template #default="{ row }">{{ row.projectName || '—' }}</template></el-table-column>
      <el-table-column prop="datasourceCode" label="数据源编码" min-width="150" />
      <el-table-column prop="datasourceName" label="数据源名称" min-width="150" />
      <el-table-column label="SFTP 地址" min-width="220"><template #default="{ row }">{{ row.host }}:{{ row.port || 22 }}{{ row.basePath || '/' }}</template></el-table-column>
      <el-table-column label="认证" width="110" align="center"><template #default="{ row }">{{ row.privateKey ? '私钥' : '密码' }}</template></el-table-column>
      <el-table-column label="连接池" width="120" align="center"><template #default="{ row }">{{ row.minIdle || 1 }} / {{ row.maxPoolSize || 8 }}</template></el-table-column>
      <el-table-column prop="status" label="状态" width="150" align="center"><template #default="{ row }"><governance-resource-status resource-type="SFTP" :resource-id="row.id" :status="row.status"><el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag></governance-resource-status></template></el-table-column>
      <el-table-column label="操作" width="250" fixed="right" align="center"><template #default="{ row }"><el-button v-permission="'database:edit'" link type="primary" size="small" @click="openEdit(row)">编辑</el-button><el-button v-permission="'database:edit'" link type="success" size="small" @click="test(row)">测试</el-button><governance-publish-action resource-type="SFTP" :resource-id="row.id" /><el-button v-permission="'database:edit'" link type="danger" size="small" @click="remove(row)">删除</el-button></template></el-table-column>
    </el-table>
    <el-pagination style="margin-top: 12px; text-align: right" :current-page="query.pageNum" :page-size="query.pageSize" :total="total" layout="total,sizes,prev,pager,next" :page-sizes="[10, 30, 50]" @current-change="page => { query.pageNum = page; load() }" @size-change="size => { query.pageSize = size; query.pageNum = 1; load() }" />

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑 SFTP 数据源' : '新建 SFTP 数据源'" width="820px" class="resizable-config-dialog" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="130px" size="small">
        <scope-project-fields v-model:scope="form.scope" v-model:project-id="form.projectId" :projects="projects" project-label="项目名称" />
        <el-form-item label="数据源编码" prop="datasourceCode"><el-input v-model="form.datasourceCode" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="数据源名称" prop="datasourceName"><el-input v-model="form.datasourceName" /></el-form-item>
        <div class="form-section"><div class="section-title">连接方式</div><el-row :gutter="12"><el-col :span="16"><el-form-item label="主机" prop="host"><el-input v-model="form.host" placeholder="sftp.example.com" /></el-form-item></el-col><el-col :span="8"><el-form-item label="端口"><el-input-number v-model="form.port" :min="1" :max="65535" controls-position="right" style="width: 100%" /></el-form-item></el-col></el-row><el-form-item label="远端根目录"><el-input v-model="form.basePath" placeholder="/upload/contracts" /></el-form-item><el-form-item label="用户名" prop="username"><el-input v-model="form.username" autocomplete="off" /></el-form-item><el-form-item label="密码"><el-input v-model="form.password" type="password" show-password autocomplete="new-password" placeholder="使用私钥时可留空" /></el-form-item><el-form-item label="私钥"><el-input v-model="form.privateKey" type="textarea" :rows="3" placeholder="PEM 私钥内容；使用密码时可留空" /></el-form-item><el-form-item label="私钥口令"><el-input v-model="form.passphrase" type="password" show-password autocomplete="new-password" /></el-form-item><el-form-item label="主机指纹校验"><el-switch v-model="form.strictHostKeyChecking" /><span class="field-help">开启后需填写 known_hosts；关闭时使用 JSch 默认的非交互连接模式。</span></el-form-item><el-form-item v-if="form.strictHostKeyChecking" label="known_hosts"><el-input v-model="form.knownHosts" type="textarea" :rows="2" placeholder="known_hosts 文件内容" /></el-form-item></div>
        <div class="form-section"><div class="section-title">连接池与文件限制</div><el-row :gutter="12"><el-col :span="8"><el-form-item label="最大连接数"><el-input-number v-model="form.maxPoolSize" :min="1" :max="100" controls-position="right" style="width: 100%" /></el-form-item></el-col><el-col :span="8"><el-form-item label="最小空闲"><el-input-number v-model="form.minIdle" :min="0" :max="100" controls-position="right" style="width: 100%" /></el-form-item></el-col><el-col :span="8"><el-form-item label="借用超时(ms)"><el-input-number v-model="form.borrowTimeoutMs" :min="100" :max="600000" controls-position="right" style="width: 100%" /></el-form-item></el-col></el-row><el-row :gutter="12"><el-col :span="8"><el-form-item label="连接超时(ms)"><el-input-number v-model="form.connectTimeoutMs" :min="100" :max="600000" controls-position="right" style="width: 100%" /></el-form-item></el-col><el-col :span="8"><el-form-item label="会话超时(ms)"><el-input-number v-model="form.sessionTimeoutMs" :min="100" :max="600000" controls-position="right" style="width: 100%" /></el-form-item></el-col><el-col :span="8"><el-form-item label="Socket超时(ms)"><el-input-number v-model="form.socketTimeoutMs" :min="100" :max="600000" controls-position="right" style="width: 100%" /></el-form-item></el-col></el-row><el-row :gutter="12"><el-col :span="8"><el-form-item label="空闲超时(ms)"><el-input-number v-model="form.idleTimeoutMs" :min="1000" :max="86400000" controls-position="right" style="width: 100%" /></el-form-item></el-col><el-col :span="8"><el-form-item label="单文件上限(byte)"><el-input-number v-model="form.maxFileSizeBytes" :min="1024" :max="268435456" controls-position="right" style="width: 100%" /></el-form-item></el-col><el-col :span="8"><el-form-item label="文件名编码"><el-input v-model="form.fileNameEncoding" placeholder="UTF-8" /></el-form-item></el-col></el-row></div>
        <el-form-item label="扩展配置"><el-input v-model="form.extraConfig" type="textarea" :rows="2" placeholder='JSON，例如 {"compression.s2c":"zlib,none"}' /></el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button :loading="testing" @click="testDraft">测试连接</el-button><el-button v-permission="'database:edit'" type="primary" :loading="saving" @click="save">保存草稿</el-button></template>
    </el-dialog>
  </div>
</template>

<script>
import { createSftpDatasource, deleteSftpDatasource, getSftpDatasource, listSftpDatasources, testSftpDatasource, testSftpDatasourceDraft, updateSftpDatasource } from '@/api/sftp'
import { listProjects } from '@/api/project'
import { editableResource } from '@/api/governance'

function emptyForm() {
  return { projectId: null, scope: 'PROJECT', datasourceCode: '', datasourceName: '', connectionMode: 'DIRECT', host: '', port: 22, basePath: '', username: '', password: '', privateKey: '', passphrase: '', knownHosts: '', strictHostKeyChecking: false, connectTimeoutMs: 10000, sessionTimeoutMs: 30000, socketTimeoutMs: 30000, maxPoolSize: 8, minIdle: 1, borrowTimeoutMs: 5000, idleTimeoutMs: 600000, maxFileSizeBytes: 10485760, fileNameEncoding: 'UTF-8', extraConfig: '', description: '', status: 1 }
}

export default {
  name: 'SftpDatasourceList',
  data() {
    return { query: { pageNum: 1, pageSize: 10, projectCode: '', projectName: '', datasourceCode: '', status: null }, rows: [], total: 0, loading: false, projects: [], dialogVisible: false, form: emptyForm(), saving: false, testing: false, rules: { datasourceCode: [{ required: true, message: '请输入数据源编码', trigger: 'blur' }], datasourceName: [{ required: true, message: '请输入数据源名称', trigger: 'blur' }], host: [{ required: true, message: '请输入 SFTP 主机', trigger: 'blur' }], username: [{ required: true, message: '请输入用户名', trigger: 'blur' }] } }
  },
  async mounted() { await Promise.all([this.load(), this.loadProjects()]) },
  methods: {
    async load() { this.loading = true; try { const res = await listSftpDatasources(this.query); this.rows = res.data?.records || []; this.total = res.data?.total || 0 } finally { this.loading = false } },
    async loadProjects() { try { const res = await listProjects({ pageNum: 1, pageSize: 500 }); this.projects = res.data?.records || [] } catch (e) { this.projects = [] } },
    resetQuery() { this.query = { ...this.query, pageNum: 1, projectCode: '', projectName: '', datasourceCode: '', status: null }; this.load() },
    openCreate() { this.form = emptyForm(); this.dialogVisible = true; this.$nextTick(() => this.$refs.formRef?.clearValidate()) },
    async openEdit(row) { const res = await getSftpDatasource(row.id); let detail = res.data || row; try { detail = await editableResource('SFTP', row.id, detail) } catch (e) { /* no editable draft yet; use the sanitized effective projection */ } this.form = { ...emptyForm(), ...(detail || row) }; this.dialogVisible = true; this.$nextTick(() => this.$refs.formRef?.clearValidate()) },
    normalize(data) { const result = { ...data }; if (result.scope === 'GLOBAL') result.projectId = 0; if (!result.extraConfig) result.extraConfig = null; return result },
    async save() { const valid = await this.$refs.formRef.validate().catch(() => false); if (!valid) return; this.saving = true; try { const payload = this.normalize(this.form); const response = payload.id ? await updateSftpDatasource(payload) : await createSftpDatasource(payload); this.$message.success(response.data?.approvalRequestId ? '配置草稿已提交审批' : '保存成功'); this.dialogVisible = false; await this.load() } finally { this.saving = false } },
    async testDraft() { const valid = await this.$refs.formRef.validate().catch(() => false); if (!valid) return; this.testing = true; try { await testSftpDatasourceDraft(this.normalize(this.form)); this.$message.success('SFTP 连接成功') } finally { this.testing = false } },
    async test(row) { try { await testSftpDatasource(row.id); this.$message.success('SFTP 连接成功') } catch (e) { /* request interceptor already shows the error */ } },
    remove(row) { this.$confirm(`确定删除「${row.datasourceName}」？`, '确认删除', { type: 'warning' }).then(async () => { await deleteSftpDatasource(row.id); this.$message.success('删除草稿已提交'); await this.load() }).catch(() => {}) },
  },
}
</script>

<style scoped>
.sftp-subnav { display: flex; gap: 8px; margin-bottom: 12px; }
.form-section { padding: 12px; margin: 4px 0 14px; border: 1px solid var(--tianshu-border-subtle); border-radius: 8px; }
.section-title { margin-bottom: 10px; color: var(--tianshu-text-primary); font-size: 13px; font-weight: 700; }
.field-help { margin-left: 8px; color: var(--tianshu-text-tertiary); font-size: 12px; }
@media (max-width: 900px) { .sftp-page :deep(.uiue-search-container) { align-items: flex-start; } }
</style>
