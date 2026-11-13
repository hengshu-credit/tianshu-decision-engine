<template>
  <section class="ledger-assets">
    <div class="uiue-search-container uiue-filter-toolbar">
      <el-form inline size="small" @keyup.enter="search">
        <el-form-item label="范围"><el-select v-model="filters.scope" clearable placeholder="全部" style="width:115px" @change="scopeChanged"><el-option label="仅自己" value="PRIVATE" /><el-option label="全局" value="GLOBAL" /><el-option label="项目级" value="PROJECT" /></el-select></el-form-item>
        <el-form-item v-if="filters.scope === 'PROJECT'" label="项目"><project-resource-select v-model="filters.projectId" style="width:170px" @change="search" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="filters.status" clearable placeholder="全部" style="width:110px" @change="search"><el-option v-for="item in statuses" :key="item.value" :value="item.value" :label="item.label" /></el-select></el-form-item>
        <el-form-item label="名称"><el-input v-model="filters.keyword" clearable placeholder="搜索素材名称" style="width:160px" /></el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
      <div class="uiue-btn-bar"><div class="btn-right"><table-column-settings v-model="columns" :columns="columnOptions" storage-key="tianshu:table-columns:ledger-assets" /><el-button size="small" @click="load">刷新</el-button><el-button v-if="canWrite && !picker" size="small" :disabled="!selection.length" type="danger" @click="remove(selection)">删除所选</el-button><el-button v-if="canWrite" size="small" type="primary" :loading="uploading" @click="$refs.upload.click()">上传素材</el-button></div></div>
    </div>
    <dismissible-guidance tag="p" guide-id="ledger.assets" label="台账素材说明" class="ledger-help">图片先私有暂存，安全检测通过后可预览和引用。支持 5 MB 以内 PNG、JPEG、WebP；已引用素材不能删除；私有素材可由上传者明确共享范围，已共享素材被引用后不能改范围。</dismissible-guidance>
    <input ref="upload" type="file" accept="image/png,image/jpeg,image/webp" multiple hidden @change="upload">
    <el-table :data="records" row-key="id" class="management-table" size="small" border :aria-busy="loading" @selection-change="selection = $event">
      <el-table-column v-if="!picker" type="selection" width="42" :selectable="row => row.canManage && !row.referenced" />
      <template v-for="column in columns" :key="column">
        <el-table-column v-if="column === 'name'" label="素材" min-width="230"><template #default="{ row }"><div class="ledger-asset-name"><el-image v-if="row.contentUrl" :src="row.contentUrl" :preview-src-list="[row.contentUrl]" preview-teleported fit="contain" /><span v-else class="ledger-asset-placeholder"><app-icon name="Picture" /></span><div>{{ row.name }}<small>{{ sizeLabel(row.sizeBytes) }} · {{ row.contentType }}</small></div></div></template></el-table-column>
        <el-table-column v-if="column === 'scope'" label="归属" min-width="125"><template #default="{ row }">{{ scopeLabel(row) }}</template></el-table-column>
        <el-table-column v-if="column === 'status'" label="安全状态" min-width="180"><template #default="{ row }"><el-tag :type="row.scanStatus === 'CLEAN' ? 'success' : row.scanStatus === 'PENDING' ? 'warning' : 'danger'">{{ statusLabel(row.scanStatus) }}</el-tag><div class="ledger-table-summary">{{ row.scanMessage }}</div></template></el-table-column>
        <el-table-column v-if="column === 'owner'" prop="ownerName" label="上传者" width="110" />
        <el-table-column v-if="column === 'time'" label="更新时间" width="170"><template #default="{ row }">{{ time(row.updateTime) }}</template></el-table-column>
      </template>
      <el-table-column label="操作" :width="picker ? 110 : operationColumnWidth(['下载', '编辑', '引用', '检测', '删除'])" fixed="right" class-name="table-operation-column"><template #default="{ row }"><div class="table-operation-group"><el-button v-if="picker" link type="primary" :disabled="row.scanStatus !== 'CLEAN'" @click="$emit('select', row)">插入正文</el-button><template v-else><el-button v-if="row.scanStatus === 'CLEAN'" link @click="download(row)">下载</el-button><el-button v-if="row.canManage" link type="primary" @click="edit(row)">编辑</el-button><el-button link @click="references(row)">引用</el-button><el-button v-if="row.canManage && row.scanStatus !== 'CLEAN'" link :loading="scanning === row.id" @click="scan(row)">重试检测</el-button><el-button v-if="row.canManage" link type="danger" :disabled="row.referenced" @click="remove([row])">删除</el-button></template></div></template></el-table-column>
      <template #empty><el-empty description="暂无可查看的素材" /></template>
    </el-table>
    <el-pagination :current-page="page" :page-size="size" :total="total" layout="total, prev, pager, next" @current-change="changePage" />
    <el-dialog v-model="editVisible" title="编辑素材" width="530px" append-to-body class="ledger-dialog" :close-on-click-modal="false"><el-form label-width="90px" size="small"><el-form-item label="素材名称" required><el-input v-model="form.name" maxlength="255" /></el-form-item><el-form-item label="作用范围"><el-select v-model="form.scope" :disabled="!form.canChangeScope"><el-option label="仅自己" value="PRIVATE" :disabled="String(form.ownerUserId) !== String(userId)" /><el-option label="全局" value="GLOBAL" :disabled="!globalEditable" /><el-option label="项目级" value="PROJECT" /></el-select></el-form-item><el-form-item v-if="form.scope === 'PROJECT'" label="所属项目"><project-resource-select v-model="form.projectId" :disabled="!form.canChangeScope" /></el-form-item></el-form><template #footer><el-button @click="editVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template></el-dialog>
    <el-dialog v-model="referencesVisible" title="素材引用" width="760px" append-to-body class="ledger-dialog"><el-table :data="referenceRows" size="small"><el-table-column prop="title" label="文章" min-width="250" /><el-table-column label="版本" width="110"><template #default="{ row }">{{ row.type === 'DRAFT' ? '我的草稿' : `v${row.versionNo}` }}</template></el-table-column><el-table-column label="操作" width="90"><template #default="{ row }"><el-button link type="primary" @click="openReference(row)">查看</el-button></template></el-table-column></el-table><p class="ledger-help">仅列出有权查看的引用。存在其他受限引用时，删除仍会被拒绝。</p></el-dialog>
  </section>
</template>
<script>
import DismissibleGuidance from '@/components/common/DismissibleGuidance.vue'
import ProjectResourceSelect from '@/components/ProjectResourceSelect.vue'
import TableColumnSettings from '@/components/common/TableColumnSettings.vue'
import { listLedgerAssets, uploadLedgerAsset, updateLedgerAsset, scanLedgerAsset, deleteLedgerAssets, getLedgerAssetReferences, downloadLedgerAsset } from '@/api/ledger'
import { hasPermission, getCurrentUser } from '@/security/permissionState'
import { LEDGER_ASSET_STATUSES, ledgerSize, ledgerTime } from '@/utils/ledger'
import { operationColumnWidth } from '@/utils/operationColumnWidth'
export default {
  name: 'LedgerAssets', components: { DismissibleGuidance, ProjectResourceSelect, TableColumnSettings },
  props: { projectId: { type: [String, Number], default: null }, picker: Boolean }, emits: ['select', 'uploaded'],
  data() { return { filters: { scope: this.projectId ? 'PROJECT' : '', projectId: this.projectId, status: '', keyword: '' }, records: [], selection: [], loading: false, generation: 0, page: 1, size: 20, total: 0, uploading: false, scanning: null, saving: false, editVisible: false, form: {}, referencesVisible: false, referenceRows: [], statuses: LEDGER_ASSET_STATUSES, columns: ['name', 'scope', 'status', 'owner', 'time'], columnOptions: [{ key: 'name', label: '素材' }, { key: 'scope', label: '归属' }, { key: 'status', label: '安全状态' }, { key: 'owner', label: '上传者' }, { key: 'time', label: '更新时间' }] } },
  computed: { canWrite() { return hasPermission('ledger:edit') }, userId() { return getCurrentUser()?.userId }, globalEditable() { return hasPermission('ledger:edit', { scope: 'GLOBAL' }) } },
  mounted() { this.load() }, beforeUnmount() { this.generation++ },
  activated() { if (this.generation > 0 && !this.loading) this.load() },
  watch: { projectId(value) { this.filters.scope = value ? 'PROJECT' : ''; this.filters.projectId = value; this.search() } },
  methods: {
    operationColumnWidth, time: ledgerTime, sizeLabel: ledgerSize,
    statusLabel(value) { return LEDGER_ASSET_STATUSES.find(item => item.value === value)?.label || value },
    scopeLabel(row) { return row.scope === 'PRIVATE' ? '仅自己' : row.scope === 'GLOBAL' ? '全局' : row.projectName || `项目 #${row.projectId}` },
    async load() { const generation = ++this.generation; this.loading = true; try { const response = await listLedgerAssets({ ...this.filters, projectId: this.filters.scope === 'PROJECT' ? this.filters.projectId : undefined, pageNum: this.page, pageSize: this.size }); if (generation === this.generation) { this.records = response.data?.records || []; this.total = response.data?.total || 0; this.selection = [] } } catch { /* request 已提示。 */ } finally { if (generation === this.generation) this.loading = false } },
    search() { this.page = 1; this.load() }, changePage(page) { this.page = page; this.load() },
    scopeChanged() { if (this.filters.scope !== 'PROJECT') this.filters.projectId = null; this.search() },
    reset() { this.filters = { scope: this.projectId ? 'PROJECT' : '', projectId: this.projectId, status: '', keyword: '' }; this.search() },
    async upload(event) { const files = Array.from(event.target.files || []); event.target.value = ''; this.uploading = true; try { for (const file of files) { if (file.size > 5 * 1024 * 1024 || !/\.(png|jpe?g|webp)$/i.test(file.name)) { this.$message.warning(`${file.name}：仅支持 5 MB 以内的 PNG、JPEG、WebP 图片`); continue } const response = await uploadLedgerAsset(file); this.$emit('uploaded', response.data); if (response.data.scanStatus !== 'CLEAN') this.$message.warning(response.data.scanMessage) } this.filters.scope = 'PRIVATE'; this.filters.projectId = null; this.search() } catch { /* request 已提示。 */ } finally { this.uploading = false } },
    edit(row) { this.form = { ...row }; this.editVisible = true },
    async save() { this.saving = true; try { await updateLedgerAsset(this.form.id, { name: this.form.name, scope: this.form.scope, projectId: this.form.scope === 'PROJECT' ? this.form.projectId : null, version: this.form.version }); this.editVisible = false; this.load() } catch { /* request 已提示。 */ } finally { this.saving = false } },
    async scan(row) { this.scanning = row.id; try { const response = await scanLedgerAsset(row.id, row.version); this.$message[response.data.scanStatus === 'CLEAN' ? 'success' : 'warning'](response.data.scanMessage); this.load() } catch { /* request 已提示。 */ } finally { this.scanning = null } },
    async remove(rows) { try { await this.$confirm(`删除所选 ${rows.length} 个素材？已被引用的素材无法删除。`, '删除素材'); await deleteLedgerAssets(rows.map(row => row.id)); this.$message.success('素材已删除'); this.load() } catch { /* 用户取消或 request 已提示。 */ } },
    async references(row) { try { const response = await getLedgerAssetReferences(row.id); this.referenceRows = response.data || []; this.referencesVisible = true } catch { /* request 已提示。 */ } },
    openReference(row) { this.referencesVisible = false; this.$router.push(row.type === 'DRAFT' ? `/ledger/articles/${row.articleId}/edit` : { path: `/ledger/articles/${row.articleId}`, query: { revisionId: row.revisionId } }) },
    async download(row) { try { const response = await downloadLedgerAsset(row.id); const url = URL.createObjectURL(response.data); const anchor = document.createElement('a'); anchor.href = url; anchor.download = row.name.replace(/\.[^.]+$/, '') + '.png'; anchor.click(); setTimeout(() => URL.revokeObjectURL(url), 1000) } catch { /* request 已提示。 */ } },
  },
}
</script>
