<template>
  <div class="uiue-list-page management-list-page sftp-log-page">
    <div class="module-hint"><div class="hint-title">SFTP 查询日志</div><div class="hint-text">记录变量或规则触发的 SFTP 文件读取、远端地址和 trace_id。预览会重新从连接池读取文件，页面不会保存文件副本。</div></div>
    <el-tabs v-model="activeTab" class="management-table-region sftp-tabs" @tab-click="onTabChange"><el-tab-pane label="数据源配置" name="datasource" /><el-tab-pane label="查询日志" name="logs" /></el-tabs>
    <div class="uiue-search-container uiue-filter-toolbar"><el-form :inline="true" size="small" @keyup.enter="handleQuery"><el-form-item label="项目编码"><project-filter-select v-model:value="query.projectCode" field="projectCode" placeholder="项目编码" style="width: 150px" /></el-form-item><el-form-item label="Trace ID"><el-input v-model="query.traceId" clearable placeholder="规则或模块 trace_id" style="width: 230px" /></el-form-item><el-form-item label="结果"><el-select v-model="query.success" clearable placeholder="全部" style="width: 100px"><el-option label="成功" :value="1" /><el-option label="失败" :value="0" /></el-select></el-form-item><el-form-item><el-button type="primary" @click="handleQuery">查询</el-button><el-button @click="reset">重置</el-button></el-form-item></el-form><div class="uiue-btn-bar"><div class="btn-right"><el-button size="small" @click="load">刷新</el-button></div></div></div>
    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError"><el-button link type="primary" :disabled="loading" @click="load">重新加载</el-button></el-alert>
    <el-table class="management-table" :data="rows" border size="small" v-loading="loading" show-overflow-tooltip><el-table-column prop="actionType" label="触发动作" width="170"><template #default="{ row }">{{ actionLabel(row.actionType) }}</template></el-table-column><el-table-column prop="targetName" label="触发变量/数据源" min-width="150"><template #default="{ row }">{{ row.targetName || row.targetCode || '—' }}</template></el-table-column><el-table-column label="文件路径" min-width="260" show-overflow-tooltip><template #default="{ row }"><code>{{ remotePath(row) }}</code></template></el-table-column><el-table-column prop="requestUrl" label="文件链接地址" min-width="260" show-overflow-tooltip /><el-table-column label="缓存/来源" width="150"><template #default="{ row }">{{ cacheSummary(row) }}</template></el-table-column><el-table-column label="重试" width="70" align="center"><template #default="{ row }">{{ responseValue(row, 'retryCount') ?? '—' }}</template></el-table-column><el-table-column prop="traceId" label="SFTP trace_id" min-width="190" show-overflow-tooltip /><el-table-column prop="ruleTraceId" label="规则 trace_id" min-width="190" show-overflow-tooltip /><el-table-column prop="success" label="结果" width="80" align="center"><template #default="{ row }"><el-tag size="small" :type="row.success === 1 ? 'success' : 'danger'">{{ row.success === 1 ? '成功' : '失败' }}</el-tag></template></el-table-column><el-table-column label="异常" min-width="220" show-overflow-tooltip><template #default="{ row }">{{ row.errorMessage || '—' }}</template></el-table-column><el-table-column prop="createTime" label="触发时间" width="170" /><el-table-column label="操作" width="90" fixed="right" align="center"><template #default="{ row }"><el-button link type="primary" size="small" :disabled="row.success !== 1" @click="preview(row)">预览</el-button></template></el-table-column></el-table>
    <el-pagination style="margin-top: 12px; text-align: right" :current-page="query.pageNum" :page-size="query.pageSize" :total="total" layout="total,sizes,prev,pager,next" :page-sizes="[10, 20, 30, 50]" @current-change="p => { query.pageNum = p; load() }" @size-change="s => { query.pageSize = s; query.pageNum = 1; load() }" />
    <el-dialog v-model="previewVisible" title="SFTP 文件预览" width="900px" destroy-on-close><div v-if="previewLoading" class="preview-state">正在从 SFTP 读取文件…</div><el-alert v-else-if="previewError" type="error" :title="previewError" :closable="false" show-icon /><template v-else-if="previewUrl"><img v-if="previewKind === 'image'" :src="previewUrl" class="preview-image" alt="SFTP 文件预览" /><iframe v-else-if="previewKind === 'pdf'" :src="previewUrl" class="preview-frame" title="PDF 文件预览" /><pre v-else-if="previewText" class="preview-text">{{ previewText }}</pre><div v-else class="preview-state">当前文件类型不支持内嵌预览。</div><a :href="previewUrl" download="sftp-file">下载文件</a></template><div v-else class="preview-state">暂无可预览内容。</div></el-dialog>
  </div>
</template>

<script>
import { listRuntimeLogs } from '@/api/runtimeLog'
import { previewSftpLog } from '@/api/sftp'
import ProjectFilterSelect from '@/components/ProjectFilterSelect.vue'
import { routeProjectId } from '@/utils/projectContext'

export default {
  name: 'SftpQueryLog',
  components: { ProjectFilterSelect },
  data() { return { activeTab: 'logs', query: { pageNum: 1, pageSize: 20, moduleType: 'SFTP', projectCode: '', traceId: '', success: null }, rows: [], total: 0, loading: false, loadError: '', listRequestId: 0, previewVisible: false, previewLoading: false, previewError: '', previewRequestId: 0, previewController: null, previewUrl: '', previewKind: '', previewText: '' } },
  mounted() { this.query.projectId = routeProjectId(this.$route, this.$store?.state?.currentProject); this.load() },
  watch: { previewVisible: { flush: 'sync', handler(visible) { if (!visible) this.releasePreview() } } },
  beforeUnmount() { this.listRequestId++; this.releasePreview() },
  methods: {
    async load() {
      const requestId = ++this.listRequestId
      this.loading = true
      this.loadError = ''
      try {
        const res = await listRuntimeLogs({ ...this.query })
        if (requestId !== this.listRequestId) return
        this.rows = res.data?.records || []
        this.total = res.data?.total || 0
      } catch (error) {
        if (requestId !== this.listRequestId) return
        this.rows = []
        this.total = 0
        this.loadError = error.message || 'SFTP 查询日志读取失败，请重新加载'
      } finally {
        if (requestId === this.listRequestId) this.loading = false
      }
    },
    handleQuery() { this.query.pageNum = 1; return this.load() },
    onTabChange(tab) { const paneName = tab && (tab.paneName ?? tab.name ?? (tab.props && tab.props.name)); if ((paneName && paneName.value !== undefined ? paneName.value : paneName) === 'datasource') this.$router.push('/sftp') },
    reset() { this.query = { ...this.query, pageNum: 1, projectCode: '', traceId: '', success: null }; this.load() },
    actionLabel(value) { return { SFTP_VARIABLE_READ: '变量读取', SFTP_QUERY: '文件查询', TEST_CONNECTION: '连接测试', TEST_CONNECTION_DRAFT: '草稿连接测试' }[value] || value || 'SFTP 调用' },
    remotePath(row) { try { return JSON.parse(row.requestBody || '{}').remotePath || '—' } catch (e) { return '—' } },
    responseValue(row, key) { try { return JSON.parse(row.responseBody || '{}')[key] } catch (e) { return null } },
    cacheSummary(row) { const status = this.responseValue(row, 'cacheStatus'); const origin = this.responseValue(row, 'dataOrigin'); return [status, origin].filter(Boolean).join(' / ') || '—' },
    async preview(row) {
      this.releasePreview()
      const requestId = this.previewRequestId
      this.previewController = new AbortController()
      this.previewVisible = true
      this.previewLoading = true
      try {
        const response = await previewSftpLog(row.id, { signal: this.previewController.signal })
        if (requestId !== this.previewRequestId) return
        const blob = response.data
        const type = String(blob.type || '').toLowerCase()
        const kind = type.startsWith('image/') ? 'image' : type.includes('pdf') ? 'pdf' : ''
        const text = !kind && (type.startsWith('text/') || type.includes('json') || type.includes('xml')) ? await blob.text() : ''
        if (requestId !== this.previewRequestId) return
        this.previewUrl = URL.createObjectURL(blob)
        this.previewKind = kind
        this.previewText = text
      } catch (error) {
        if (requestId === this.previewRequestId) this.previewError = error.message || '文件读取失败，请重新预览'
      } finally {
        if (requestId === this.previewRequestId) this.previewLoading = false
      }
    },
    releasePreview() {
      this.previewRequestId++
      this.previewController?.abort()
      this.previewController = null
      if (this.previewUrl) URL.revokeObjectURL(this.previewUrl)
      this.previewUrl = ''; this.previewText = ''; this.previewKind = ''; this.previewError = ''; this.previewLoading = false
    },
  },
}
</script>

<style scoped>
.sftp-log-page .module-hint { display: flex; align-items: center; gap: 12px; padding: 12px 16px; margin-bottom: 12px; border: 1px solid var(--tianshu-info-border); border-radius: 6px; background: var(--tianshu-info-bg); color: var(--tianshu-info-text); }
.sftp-log-page .hint-title { color: inherit; font-weight: 700; white-space: nowrap; }
.sftp-log-page .hint-text { color: var(--tianshu-text-secondary); line-height: 1.5; }
.sftp-tabs { margin-bottom: 12px; }
.preview-state { min-height: 180px; display: flex; align-items: center; justify-content: center; color: var(--tianshu-text-tertiary); }
.preview-image { display: block; max-width: 100%; max-height: 65vh; margin: 0 auto; object-fit: contain; }
.preview-frame { width: 100%; height: 68vh; border: 0; }
.preview-text { max-height: 65vh; overflow: auto; padding: 12px; white-space: pre-wrap; background: var(--tianshu-bg-page); }
@media (max-width: 720px) { .sftp-log-page .module-hint { align-items: flex-start; flex-direction: column; gap: 4px; } }
</style>
