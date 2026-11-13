<template>
  <section class="ledger-management">
    <section aria-label="事件管理">
      <h3>事件管理</h3>
      <div class="uiue-search-container uiue-filter-toolbar">
        <el-form inline size="small" @submit.prevent="search">
          <el-form-item label="事件名称"><el-input v-model="keyword" clearable @keyup.enter="search" /></el-form-item>
          <el-form-item label="事件类型"><el-select v-model="typeId" clearable style="width:170px" @change="search"><el-option v-for="type in ledgerTypes" :key="type.id" :value="type.id" :label="type.name" /></el-select></el-form-item>
          <el-form-item><el-button type="primary" @click="search">查询</el-button></el-form-item>
        </el-form>
        <div class="uiue-btn-bar"><div class="btn-right"><el-button size="small" @click="loadEvents">刷新</el-button><el-button v-if="canEdit" type="primary" size="small" :disabled="eventClosing" @click="openEvent()">新建事件</el-button></div></div>
      </div>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-table :data="events" :aria-busy="loading" border size="small" row-key="id">
        <el-table-column prop="code" label="事件编码" min-width="170" />
        <el-table-column prop="title" label="事件名称" min-width="200" />
        <el-table-column label="事件类型" min-width="130"><template #default="{row}">{{ row.typeName || '未分类' }}</template></el-table-column>
        <el-table-column label="归属" min-width="130"><template #default="{row}">{{ row.scope === 'GLOBAL' ? '全局' : `项目 #${row.projectId}` }}</template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="{row}">{{ row.statusLabel || statusName(row.status) }}</template></el-table-column>
        <el-table-column label="操作" width="150" fixed="right"><template #default="{row}"><el-button v-if="canEdit && row.canEdit" link type="primary" :disabled="deleting || eventClosing" @click="openEvent(row)">编辑</el-button><el-button v-if="canEdit && row.canEdit" link type="danger" :disabled="deleting" @click="removeEvent(row)">删除</el-button></template></el-table-column>
      </el-table>
      <el-pagination :current-page="page" :page-size="20" :total="total" layout="total, prev, pager, next" @current-change="changePage" />
    </section>
    <section aria-label="事件类型管理">
      <h3>事件类型管理</h3>
      <dismissible-guidance tag="p" guide-id="ledger.types" label="台账类型说明" class="ledger-help">类型名称可调整，文章与事件仍按稳定 ID 关联。已被事件、草稿或历史文章引用的类型不能删除。</dismissible-guidance>
      <div class="uiue-search-container uiue-filter-toolbar"><el-form inline size="small"><el-form-item label="类型名称"><el-input v-model="typeKeyword" clearable placeholder="筛选编码或名称" /></el-form-item></el-form><div class="uiue-btn-bar"><div class="btn-right"><el-button size="small" @click="loadLedgerTypes">刷新</el-button><el-button v-if="canManageTypes" type="primary" size="small" :disabled="typeSaving || typeClosing" @click="openType()">新增事件类型</el-button></div></div></div>
      <el-alert v-if="ledgerTypesError" :title="ledgerTypesError" type="error" :closable="false" />
      <el-table :data="visibleTypes" border size="small" row-key="id"><el-table-column prop="code" label="类型编码" min-width="170" /><el-table-column prop="name" label="类型名称" min-width="180" /><el-table-column prop="sortOrder" label="排序" width="100" /><el-table-column label="操作" width="150" fixed="right"><template #default="{row}"><el-button v-if="canManageTypes && row.canManage" link type="primary" :disabled="deleting || typeSaving || typeClosing" @click="openType(row)">编辑</el-button><el-button v-if="canManageTypes && row.canManage" link type="danger" :disabled="deleting" @click="removeType(row)">删除</el-button></template></el-table-column></el-table>
    </section>
    <ledger-event-dialog ref="eventEditor" v-model="eventVisible" :event="editingEvent" :types="ledgerTypes" :scope="projectId ? 'PROJECT' : ''" :project-id="projectId" @saved="loadEvents" @closed="eventClosing = false" />
    <el-dialog v-model="typeVisible" :title="typeForm.id ? '编辑事件类型' : '新增事件类型'" width="min(500px, 95vw)" :close-on-click-modal="false" :close-on-press-escape="!typeSaving" :show-close="!typeSaving" :before-close="closeType" @closed="typeClosing = false">
      <el-form label-width="90px" :disabled="typeSaving"><el-form-item label="类型编码" required><el-input v-model="typeForm.code" maxlength="128" placeholder="请输入唯一编码" /></el-form-item><el-form-item label="类型名称" required><el-input v-model="typeForm.name" maxlength="100" /></el-form-item><el-form-item label="排序"><el-input-number v-model="typeForm.sortOrder" :precision="0" :min="-2147483648" :max="2147483647" /></el-form-item></el-form>
      <template #footer><el-button :disabled="typeSaving" @click="closeType()">取消</el-button><el-button type="primary" :loading="typeSaving" @click="saveType">保存</el-button></template>
    </el-dialog>
  </section>
</template>
<script>
import DismissibleGuidance from '@/components/common/DismissibleGuidance.vue'
import LedgerEventDialog from './LedgerEventDialog.vue'
import ledgerTypesMixin from '@/mixins/ledgerTypesMixin'
import configurationDraftMixin from '@/mixins/configurationDraftMixin'
import { manageLedgerEvents, deleteLedgerEvent, saveLedgerType, deleteLedgerType } from '@/api/ledger'
import { LEDGER_EVENT_STATUSES } from '@/utils/ledger'
import { hasPermission } from '@/security/permissionState'

export default {
  components: { DismissibleGuidance, LedgerEventDialog },
  props: { projectId: { type: [String, Number], default: null } },
  mixins: [ledgerTypesMixin, configurationDraftMixin(vm => ({ event: vm.eventVisible ? vm.$refs.eventEditor?.form : null, type: vm.typeVisible ? vm.typeForm : null }), vm => vm.typeSaving || vm.deleting || Boolean(vm.eventVisible && vm.$refs.eventEditor?.busy))],
  data: () => ({ keyword: '', typeKeyword: '', typeId: null, page: 1, total: 0, events: [], loading: false, error: '', generation: 0, mutationGeneration: 0, deleting: false, editingEvent: null, eventVisible: false, eventClosing: false, typeVisible: false, typeClosing: false, typeForm: {}, typeSaving: false }),
  computed: {
    canEdit() { return hasPermission('ledger:edit') },
    canManageTypes() { return hasPermission('ledger:manage', { scope: 'GLOBAL' }) },
    visibleTypes() { return this.ledgerTypes.filter(type => type.name.includes(this.typeKeyword) || (type.code || '').includes(this.typeKeyword)) },
  },
  watch: { eventVisible(open) { if (!open) { this.eventClosing = true; this.markConfigurationSaved() } }, typeVisible(open) { if (!open) this.typeClosing = true } },
  mounted() { this.loadEvents() },
  beforeUnmount() { this.generation++; this.mutationGeneration++ },
  methods: {
    statusName(value) { return LEDGER_EVENT_STATUSES.find(item => item.value === value)?.label || value },
    async loadEvents() { const generation = ++this.generation; this.loading = true; this.error = ''; try { const response = await manageLedgerEvents({ keyword: this.keyword, typeId: this.typeId || undefined, pageNum: this.page, pageSize: 20 }); if (generation === this.generation) { this.events = response.data.records || []; this.total = response.data.total || 0 } } catch (error) { if (generation === this.generation) this.error = error.message || '事件读取失败' } finally { if (generation === this.generation) this.loading = false } },
    search() { this.page = 1; this.loadEvents() }, changePage(page) { this.page = page; this.loadEvents() },
    async openEvent(event = null) { if (this.eventClosing || this.eventVisible || this.deleting) return; this.editingEvent = event; this.eventVisible = true; await this.$nextTick(); this.markConfigurationSaved() },
    async openType(type = null) { if (this.typeClosing || this.typeSaving || this.typeVisible || this.deleting) return; this.typeForm = type ? { ...type } : { code: '', name: '', sortOrder: 0 }; this.typeVisible = true; await this.$nextTick(); this.markConfigurationSaved() },
    async closeType(done) { if (!(await this.confirmConfigurationLeave())) return; this.typeVisible = false; this.markConfigurationSaved(); if (typeof done === 'function') done() },
    async saveType() {
      if (this.typeSaving) return
      if (!this.typeForm.code?.trim()) { this.$message.warning('请填写唯一的类型编码'); return }
      if (!this.typeForm.name?.trim()) { this.$message.warning('请填写类型名称'); return }
      const generation = this.mutationGeneration; this.typeSaving = true
      try { await saveLedgerType(this.typeForm.id, this.typeForm); if (generation !== this.mutationGeneration) return; this.typeVisible = false; this.markConfigurationSaved(); await this.loadLedgerTypes(); await this.loadEvents(); this.$message.success('事件类型已保存') }
      catch { /* 请求层已提示，保留表单供修改或重试。 */ } finally { this.typeSaving = false }
    },
    async removeEvent(event) { await this.remove(() => deleteLedgerEvent(event.id, event.version), `删除事件“${event.title}”？已被文章引用的事件不能删除。`) },
    async removeType(type) { await this.remove(() => deleteLedgerType(type.id, type.version), `删除类型“${type.name}”？已有引用会阻止删除。`) },
    async remove(action, message) {
      if (this.deleting) return
      this.deleting = true; const generation = this.mutationGeneration
      try { await this.$confirm(message, '确认删除', { type: 'warning' }); if (generation !== this.mutationGeneration) return; await action(); if (generation !== this.mutationGeneration) return; await this.loadLedgerTypes(); await this.loadEvents(); this.$message.success('已删除') }
      catch { /* 取消或请求层已提示。 */ } finally { this.deleting = false }
    },
  },
}
</script>
<style scoped>
.ledger-management { display: grid; gap: 24px; }
.ledger-management h3 { margin: 0 0 12px; font-size: 16px; font-weight: 500; }
</style>
