<template>
  <section>
    <div class="uiue-search-container uiue-filter-toolbar">
      <el-form inline size="small" @keyup.enter="search">
        <el-form-item label="范围"><el-select v-model="filters.scope" clearable placeholder="全部" style="width:110px" @change="scopeChanged"><el-option label="全局" value="GLOBAL" /><el-option label="项目级" value="PROJECT" /><el-option label="我的私有草稿" value="PRIVATE" /></el-select></el-form-item>
        <el-form-item v-if="filters.scope === 'PROJECT'" label="项目"><project-resource-select v-model="filters.projectId" style="width:170px" @change="search" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="filters.status" clearable placeholder="全部" style="width:135px" @change="search"><el-option label="我的草稿" value="DRAFT" /><el-option label="已发布" value="PUBLISHED" /></el-select></el-form-item>
        <el-form-item label="关键词"><el-input v-model="filters.keyword" clearable placeholder="标题或正文" style="width:180px" /></el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
      <div class="uiue-btn-bar"><div class="btn-right"><table-column-settings v-model="columns" :columns="columnOptions" storage-key="tianshu:table-columns:ledger-articles" /><el-button size="small" @click="load">刷新</el-button><el-button v-if="canWrite" type="primary" size="small" @click="create">新建文章</el-button></div></div>
    </div>
    <el-table :data="records" row-key="id" class="management-table" size="small" border :aria-busy="loading">
      <template v-for="column in columns" :key="column">
        <el-table-column v-if="column === 'title'" label="文章标题" min-width="250"><template #default="{ row }"><el-button link type="primary" @click="open(row)">{{ row.title || '未命名草稿' }}</el-button><div class="ledger-table-summary">{{ row.summary }}</div></template></el-table-column>
        <el-table-column v-if="column === 'status'" label="状态" width="145"><template #default="{ row }"><el-tag :type="row.publishedVersion ? 'success' : 'info'">{{ row.publishedVersion ? `已发布 v${row.publishedVersion}` : '未发布' }}</el-tag><el-tag v-if="row.hasDraft" type="warning">有草稿</el-tag></template></el-table-column>
        <el-table-column v-if="column === 'scope'" label="归属" min-width="135"><template #default="{ row }">{{ row.scope === 'PRIVATE' ? '仅自己' : row.scope === 'GLOBAL' ? '全局' : row.projectName || `项目 #${row.projectId}` }}</template></el-table-column>
        <el-table-column v-if="column === 'owner'" prop="ownerName" label="作者" width="110" />
        <el-table-column v-if="column === 'time'" label="更新时间" width="170"><template #default="{ row }">{{ time(row.updateTime) }}</template></el-table-column>
      </template>
      <el-table-column label="操作" :width="operationColumnWidth(['编辑', '发布', '版本', '删除草稿'])" fixed="right" class-name="table-operation-column"><template #default="{ row }"><div class="table-operation-group"><el-button v-if="row.canEdit" link type="primary" @click="edit(row)">{{ row.hasDraft ? '继续编辑' : '编辑' }}</el-button><el-button v-if="row.canEdit && row.hasDraft" link type="success" @click="edit(row, true)">发布</el-button><ledger-versions :article-id="row.id" @changed="load" /><el-button v-if="row.canEdit && row.hasDraft" link type="danger" @click="discard(row)">删除草稿</el-button></div></template></el-table-column>
      <template #empty><el-empty description="暂无文章，可新建文章并保存草稿" /></template>
    </el-table>
    <el-pagination :current-page="page" :page-size="size" :total="total" layout="total, prev, pager, next" @current-change="changePage" />
  </section>
</template>
<script>
import ProjectResourceSelect from '@/components/ProjectResourceSelect.vue'
import TableColumnSettings from '@/components/common/TableColumnSettings.vue'
import LedgerVersions from './LedgerVersions.vue'
import { listLedgerArticles, discardLedgerDraft } from '@/api/ledger'
import { hasPermission } from '@/security/permissionState'
import { ledgerTime } from '@/utils/ledger'
import { operationColumnWidth } from '@/utils/operationColumnWidth'
export default {
  name: 'LedgerArticles', components: { ProjectResourceSelect, TableColumnSettings, LedgerVersions },
  props: { projectId: { type: [Number, String], default: null } },
  data() { return { filters: { scope: this.projectId ? 'PROJECT' : '', projectId: this.projectId, status: '', keyword: '' }, records: [], loading: false, generation: 0, page: 1, size: 20, total: 0, columns: ['title', 'status', 'scope', 'owner', 'time'], columnOptions: [{ key: 'title', label: '文章标题' }, { key: 'status', label: '状态' }, { key: 'scope', label: '归属' }, { key: 'owner', label: '作者' }, { key: 'time', label: '更新时间' }] } },
  computed: { canWrite() { return hasPermission('ledger:edit') } },
  mounted() { this.load() }, beforeUnmount() { this.generation++ },
  activated() { if (this.generation > 0 && !this.loading) this.load() },
  watch: { projectId(value) { this.filters.scope = value ? 'PROJECT' : ''; this.filters.projectId = value; this.search() } },
  methods: {
    time: ledgerTime, operationColumnWidth,
    async load() { const generation = ++this.generation; this.loading = true; try { const response = await listLedgerArticles({ ...this.filters, projectId: this.filters.scope === 'PROJECT' ? this.filters.projectId : undefined, pageNum: this.page, pageSize: this.size }); if (generation === this.generation) { this.records = response.data?.records || []; this.total = response.data?.total || 0 } } catch { /* request 已提示。 */ } finally { if (generation === this.generation) this.loading = false } },
    search() { this.page = 1; this.load() }, changePage(page) { this.page = page; this.load() },
    scopeChanged() { if (this.filters.scope !== 'PROJECT') this.filters.projectId = null; this.search() },
    reset() { this.filters = { scope: this.projectId ? 'PROJECT' : '', projectId: this.projectId, status: '', keyword: '' }; this.search() },
    create() { this.$router.push({ path: '/ledger/articles/new', query: { projectId: this.filters.projectId || undefined } }) },
    edit(row, publish = false) { this.$router.push({ path: `/ledger/articles/${row.id}/edit`, query: { publish: publish ? '1' : undefined } }) },
    open(row) { if (row.hasDraft && row.canEdit) this.edit(row); else if (row.publishedRevisionId) this.$router.push({ path: `/ledger/articles/${row.id}`, query: { revisionId: row.publishedRevisionId } }) },
    async discard(row) { try { await this.$confirm('删除此私有草稿？已发布文章及历史版本保持不变。', '删除草稿'); await discardLedgerDraft(row.id, row.version); this.$message.success('草稿已删除'); this.load() } catch { /* 用户取消或 request 已提示。 */ } },
  },
}
</script>
