<template>
  <el-drawer :model-value="true" title="回归报告" size="90%" @close="$emit('close')">
    <p class="report-hint">固定用例的实际执行会自动归档。历史通过不等于当前可发布，规则内容、依赖或用例改变后需要重新回归。</p>
    <div class="uiue-search-container uiue-filter-toolbar">
      <span>{{ revisionId ? '复核范围：当前选择的已保存草稿或版本' : '请选择已保存的草稿或版本后复核发布条件' }}</span>
      <div class="report-actions">
        <el-button v-permission="'rule:edit'" :disabled="!revisionId" :loading="checking" @click="check">复核当前修订</el-button>
        <el-button :loading="loading" @click="refresh">刷新报告</el-button>
      </div>
    </div>
    <el-alert v-if="checkError" :title="checkError" type="error" :closable="false" show-icon />
    <el-alert v-if="gate" :title="'本次复核：' + gate.message" :type="gate.allowed && gate.requiredCount ? 'success' : 'warning'" :closable="false" show-icon />
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-table :aria-busy="loading" :data="rows" border size="small" show-overflow-tooltip empty-text="暂无回归报告，请选择固定用例执行">
      <el-table-column prop="scenarioName" label="用例" min-width="140" />
      <el-table-column label="执行来源" min-width="135"><template #default="{ row }">{{ sourceLabel(row) }}</template></el-table-column>
      <el-table-column label="预期校验" min-width="110"><template #default="{ row }"><el-tag :type="validationTagType(row.validationStatus)">{{ validationLabel(row.validationStatus) }}</el-tag></template></el-table-column>
      <el-table-column label="报告状态" min-width="150"><template #default="{ row }">{{ evidenceLabel(row) }}</template></el-table-column>
      <el-table-column prop="executedBy" label="执行人" min-width="110" />
      <el-table-column prop="createTime" label="执行时间" min-width="180" />
      <el-table-column label="操作" width="80" fixed="right"><template #default="{ row }"><el-button link @click="openDetail(row)">查看</el-button></template></el-table-column>
    </el-table>
    <el-pagination :current-page="page" :page-size="20" :total="total" layout="total, prev, pager, next" @current-change="changePage" />
    <el-dialog v-model="detailVisible" title="回归报告详情" width="90%" append-to-body>
      <div :aria-busy="detailLoading">
        <el-alert v-if="detailError" :title="detailError" type="error" :closable="false" />
        <template v-if="detail">
          <p>{{ detail.scenarioName }} · {{ sourceLabel(detail) }} · {{ evidenceLabel(detail) }}</p>
          <p>{{ detail.message }}</p>
          <p v-if="detail.traceId">执行追踪：<code>{{ detail.traceId }}</code></p>
          <el-alert v-if="validation.truncated" title="报告详情已截断，校验结论仍保留" type="info" :closable="false" />
          <ul v-if="validation.checks?.length"><li v-for="(item, index) in validation.checks" :key="index">{{ item.label }}：{{ item.message }}</li></ul>
          <div v-for="(item, index) in validation.diffs || []" :key="index" class="report-diff">
            <strong>{{ item.path }}</strong><p>预期：{{ item.expectedMissing ? '字段不存在' : item.expectedJson }}</p><p>实际：{{ item.actualMissing ? '字段不存在' : item.actualJson }}</p>
          </div>
        </template>
      </div>
    </el-dialog>
  </el-drawer>
</template>

<script>
import { listRuleRegressionReports, getRuleRegressionReport, checkRuleRegressionReports } from '@/api/definition'
import { validationLabel, validationTagType } from '@/utils/testValidationStatus'

function parsed(value) { try { return JSON.parse(value || '{}') || {} } catch { return {} } }

export default {
  name: 'RegressionReportDrawer',
  props: { definitionId: { type: [String, Number], required: true }, revisionId: { type: [String, Number], default: null } },
  emits: ['close'],
  data: () => ({ rows: [], total: 0, page: 1, loading: false, error: '', gate: null, checking: false, checkError: '', requestId: 0, checkId: 0,
    detail: null, detailVisible: false, detailLoading: false, detailError: '', detailId: 0 }),
  computed: {
    validation() { return parsed(this.detail?.validationJson) },
    caseStates() { return new Map((this.gate?.cases || []).filter(item => item.reportId).map(item => [String(item.reportId), item.status])) },
  },
  watch: {
    definitionId: { immediate: true, handler() { this.page = 1; this.detailId++; this.detailVisible = false; this.refresh() } },
    revisionId() { this.checkId++; this.gate = null; this.checking = false; this.checkError = '' },
  },
  beforeUnmount() { this.requestId++; this.checkId++; this.detailId++ },
  methods: {
    validationLabel, validationTagType,
    sourceLabel(row) {
      const identity = parsed(row.executionIdentityJson)
      if (identity.versionNo != null) return '发布版本 v' + identity.versionNo
      if (identity.revisionNo != null) return (identity.revisionState === 'DRAFT' ? '草稿 ' : '修订 ') + identity.revisionNo
      return row.revisionId ? '已保存修订 #' + row.revisionId : '工作区调试'
    },
    evidenceLabel(row) {
      if (Number(row.eligible) !== 1) return '仅诊断，不作为发布证据'
      if (row.state === 'STALE') return '已过期'
      if (row.state === 'RUNNING') return '运行中'
      if (row.state === 'ERROR') return '执行未完成'
      const current = this.caseStates.get(String(row.id))
      return current === 'PASSED' ? '当前证据有效' : current === 'STALE' ? '已过期' : current ? '尚未满足发布条件' : '历史报告，待复核'
    },
    async refresh() {
      const requestId = ++this.requestId
      this.checkId++; this.gate = null; this.checking = false; this.checkError = ''
      this.loading = true; this.error = ''; this.rows = []; this.total = 0
      try {
        const response = await listRuleRegressionReports(this.definitionId, { pageNum: this.page, pageSize: 20 })
        if (requestId !== this.requestId) return
        this.rows = response?.data?.records || []; this.total = Number(response?.data?.total || 0)
      } catch (error) { if (requestId === this.requestId) this.error = '报告读取失败：' + error.message }
      finally { if (requestId === this.requestId) this.loading = false }
    },
    changePage(page) { this.page = page; this.refresh() },
    async check() {
      if (!this.revisionId || this.checking) return
      const requestId = ++this.checkId
      this.checking = true; this.gate = null; this.checkError = ''
      try {
        const response = await checkRuleRegressionReports(this.definitionId, this.revisionId)
        if (requestId === this.checkId) this.gate = response.data
      } catch (error) { if (requestId === this.checkId) this.checkError = '发布条件复核失败：' + error.message }
      finally { if (requestId === this.checkId) this.checking = false }
    },
    async openDetail(row) {
      const requestId = ++this.detailId
      this.detail = null; this.detailVisible = true; this.detailLoading = true; this.detailError = ''
      try {
        const response = await getRuleRegressionReport(this.definitionId, row.id)
        if (requestId === this.detailId) this.detail = response.data
      } catch (error) { if (requestId === this.detailId) this.detailError = '报告详情读取失败：' + error.message }
      finally { if (requestId === this.detailId) this.detailLoading = false }
    },
  },
}
</script>

<style scoped>
.report-hint { color: var(--tianshu-text-secondary); line-height: 1.6; }
.report-actions { display: flex; gap: 8px; margin-left: auto; flex-wrap: wrap; }
.report-diff { overflow-wrap: anywhere; padding: 8px 0; border-bottom: 1px solid var(--tianshu-border-subtle); }
.el-alert, .el-table { margin-bottom: 12px; }
</style>
