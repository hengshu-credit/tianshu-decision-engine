<template>
  <div class="uiue-list-page approval-detail-page">
    <header class="detail-header">
      <button class="back-button" type="button" @click="goBack">
        ← 返回上一页
      </button>
      <div v-if="request" class="header-main">
        <div>
          <div class="request-number">{{ request.requestNo }}</div>
          <h1>{{ resourceTitle }}</h1>
          <div class="header-meta">
            <el-tag :type="statusType(request.status)">
              {{ statusLabel(request.status) }}
            </el-tag>
            <span>{{ resourceTypeLabel(request.resourceType) }}</span>
            <span>{{ actionLabel(request.action) }}</span>
            <span>申请人 {{ request.applicant || '—' }}</span>
          </div>
        </div>
        <div class="header-actions">
          <el-button
            v-if="request.resourceType === 'RULE'"
            @click="openRuleDesign"
          >
            打开规则设计
          </el-button>
          <el-button
            v-if="request.status === 'EDITING' && canManageDraft"
            v-permission="'approval:submit'"
            @click="runPreflight"
          >
            依赖预检
          </el-button>
          <el-button
            v-if="request.status === 'EDITING' && canManageDraft"
            v-permission="'approval:submit'"
            type="primary"
            @click="openAction('submit')"
          >
            提交审批
          </el-button>
          <el-button
            v-if="request.status === 'PENDING'"
            v-permission="'approval:approve'"
            type="success"
            @click="openAction('approve')"
          >
            通过并生效
          </el-button>
          <el-button
            v-if="request.status === 'PENDING'"
            v-permission="'approval:approve'"
            type="danger"
            plain
            @click="openAction('reject')"
          >
            驳回
          </el-button>
          <el-button
            v-if="['EDITING', 'PENDING'].includes(request.status) && canManageDraft"
            v-permission="'approval:submit'"
            @click="openAction('cancel')"
          >
            取消申请
          </el-button>
        </div>
      </div>
    </header>

    <main v-loading="loading" class="detail-content">
      <template v-if="request">
        <section class="overview-grid">
          <article>
            <span>基准版本</span>
            <strong>{{ request.baseVersionNo ? `V${request.baseVersionNo}` : '新资源' }}</strong>
          </article>
          <article>
            <span>生命周期动作</span>
            <strong>{{ actionLabel(request.action) }}</strong>
          </article>
          <article>
            <span>依赖状态</span>
            <strong :class="{ danger: preflightErrors.length }">
              {{ preflightErrors.length ? `${preflightErrors.length} 个冲突` : '检查通过' }}
            </strong>
          </article>
          <article>
            <span>凭据变更</span>
            <strong>{{ detail.credentialChanged ? '已变更（内容受保护）' : '无变更' }}</strong>
          </article>
        </section>

        <section
          v-if="request.status === 'REJECTED'"
          class="terminal-notice is-rejected"
        >
          <strong>本次草稿已终止，资源继续使用当前生效版本</strong>
          <span>驳回内容不可再编辑；申请和审批意见已完整保留在下方时间线中。</span>
        </section>
        <section
          v-else-if="request.status === 'CONFLICT'"
          class="terminal-notice is-conflict"
        >
          <strong>依赖或基准版本已经变化</strong>
          <span>该申请不会覆盖当前生效版本，请修正依赖后重新发起审批。</span>
        </section>

        <section class="content-card diff-section">
          <div class="section-heading">
            <div>
              <span class="section-kicker">VERSION COMPARISON</span>
              <h2>配置差异</h2>
            </div>
            <span class="diff-summary">{{ diff.summary || '无配置差异' }}</span>
          </div>
          <div class="version-selector-row">
            <el-select v-model="compareLeftKey" size="small">
              <el-option
                v-for="item in compareOptions"
                :key="`left-${item.value}`"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
            <span>对比</span>
            <el-select v-model="compareRightKey" size="small">
              <el-option
                v-for="item in compareOptions"
                :key="`right-${item.value}`"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </div>
          <div class="diff-columns-head">
            <div>
              <span>左侧版本</span>
              <strong>{{ compareLeftLabel }}</strong>
            </div>
            <div>
              <span>提交版本</span>
              <strong>{{ compareRightLabel }}</strong>
            </div>
          </div>
          <json-version-diff
            :original="compareOriginal"
            :modified="compareModified"
            :original-label="compareLeftLabel"
            :modified-label="compareRightLabel"
            height="400px"
          />
          <div v-if="isRequestComparison && changedFields.length" class="diff-list">
            <article
              v-for="field in changedFields"
              :key="field.key"
              class="diff-row"
              :class="`is-${String(field.changeType || '').toLowerCase()}`"
            >
              <header>
                <code>{{ field.key }}</code>
                <span v-if="field.sensitive" class="sensitive-badge">
                  敏感内容受保护
                </span>
                <span v-else class="change-badge">
                  {{ changeLabel(field.changeType) }}
                </span>
              </header>
              <div class="diff-values">
                <pre>{{ displayValue(field.leftValue, field.sensitive) }}</pre>
                <pre>{{ displayValue(field.rightValue, field.sensitive) }}</pre>
              </div>
            </article>
          </div>
          <div v-else-if="isRequestComparison && !changedFields.length" class="no-diff">
            基准版本与提交内容一致
          </div>
        </section>

        <section class="two-column-grid">
          <article class="content-card">
            <div class="section-heading compact">
              <div>
                <span class="section-kicker">DEPENDENCY CHECK</span>
                <h2>血缘与依赖</h2>
              </div>
              <el-button
                v-if="request.status === 'EDITING' && canManageDraft"
                v-permission="'approval:submit'"
                size="small"
                @click="runPreflight"
              >
                重新检查
              </el-button>
            </div>
            <div v-if="preflightErrors.length" class="issue-list">
              <div
                v-for="issue in preflightErrors"
                :key="`${issue.code}-${issue.referencePath}`"
                class="issue-item"
              >
                <strong>{{ issue.message }}</strong>
                <code>{{ issue.referencePath || issue.code }}</code>
              </div>
            </div>
            <lineage-graph
              v-if="lineageNodeId && lineageNodeType"
              :key="`${lineageNodeType}-${lineageNodeId}`"
              embedded
              :initial-node-type="lineageNodeType"
              :initial-node-id="lineageNodeId"
              initial-direction="ALL"
            />
            <el-table show-overflow-tooltip v-else :data="detail.dependencies || []" size="small">
              <el-table-column prop="targetResourceType" label="依赖类型" width="130" />
              <el-table-column prop="targetResourceId" label="资源 ID" width="100" />
              <el-table-column label="生效版本" width="100">
                <template #default="{ row }">
                  {{ row.targetVersionNo ? `V${row.targetVersionNo}` : '—' }}
                </template>
              </el-table-column>
              <el-table-column prop="referencePath" label="引用位置" min-width="180" />
              <template #empty>
                <div class="table-empty">当前配置没有外部依赖</div>
              </template>
            </el-table>
          </article>

          <article class="content-card">
            <div class="section-heading compact">
              <div>
                <span class="section-kicker">APPROVAL HISTORY</span>
                <h2>审批时间线</h2>
              </div>
            </div>
            <ol class="timeline">
              <li v-for="event in detail.events || []" :key="event.id">
                <span class="timeline-dot" />
                <div>
                  <strong>{{ eventLabel(event.action) }}</strong>
                  <p>{{ event.comment || '未填写说明' }}</p>
                  <small>
                    {{ event.actor || 'SYSTEM' }} · {{ formatTime(event.createTime) }}
                  </small>
                </div>
              </li>
            </ol>
          </article>
        </section>

        <section class="content-card versions-card">
          <div class="section-heading compact">
            <div>
              <span class="section-kicker">IMMUTABLE VERSIONS</span>
              <h2>历史生效版本</h2>
            </div>
            <span>恢复历史内容时会新增版本，已有历史不会被覆盖。</span>
          </div>
          <el-table show-overflow-tooltip :data="detail.versions || []" row-key="id">
            <el-table-column label="版本" width="90">
              <template #default="{ row }"><strong>V{{ row.versionNo }}</strong></template>
            </el-table-column>
            <el-table-column prop="changeSummary" label="变更说明" min-width="240" />
            <el-table-column prop="effectiveStatus" label="版本状态" width="110" />
            <el-table-column prop="createBy" label="生效人" width="130" />
            <el-table-column label="生效时间" width="175">
              <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column fixed="right" class-name="table-operation-column" :show-overflow-tooltip="false" label="操作" width="250">
              <template #default="{ row }">
                <el-button link type="info" @click="viewGovernanceVersion(row)">
                  查看
                </el-button>
                <el-button link type="primary" @click="compareGovernanceVersion(row)">
                  与当前审批对比
                </el-button>
                <el-button
                  v-permission="'approval:submit'"
                  link
                  type="primary"
                  :disabled="['EDITING', 'PENDING'].includes(request.status)"
                  @click="restoreVersion(row)"
                >
                  恢复此版本
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </template>
    </main>

    <el-dialog v-model="actionDialogVisible" :title="actionDialogTitle" width="520px">
      <el-form label-position="top">
        <el-form-item :label="activeAction === 'reject' ? '驳回原因' : '操作说明'">
          <el-input
            v-model="actionComment"
            type="textarea"
            :rows="4"
            :placeholder="actionPlaceholder"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="actionDialogVisible = false">取消</el-button>
        <el-button
          :type="activeAction === 'reject' ? 'danger' : 'primary'"
          :loading="actionLoading"
          @click="confirmAction"
        >
          确认
        </el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="versionPreviewVisible" :title="versionPreviewTitle" width="760px">
      <pre class="version-preview">{{ prettySnapshot(versionPreviewSnapshot) }}</pre>
    </el-dialog>
  </div>
</template>

<script>
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import {
  approveGovernanceRequest,
  cancelGovernanceRequest,
  getGovernanceRequest,
  preflightGovernanceRequest,
  rejectGovernanceRequest,
  restoreGovernanceVersion,
  submitGovernanceRequest
} from '@/api/governance'
import { listRuleRevisions } from '@/api/definition'
import { getCurrentUser } from '@/security/permissionState'
import { resolveDesignerRouteName } from '@/utils/ruleDesignerNavigation'
import JsonVersionDiff from '@/components/common/JsonVersionDiff.vue'
import LineageGraph from '@/views/lineage/LineageGraph.vue'

export default {
  name: 'ApprovalDetail',
  components: { JsonVersionDiff, LineageGraph },
  setup() {
    return {
      route: useRoute(),
      navigation: useRouter()
    }
  },
  data() {
    return {
      loading: false,
      actionLoading: false,
      detail: {},
      actionDialogVisible: false,
      activeAction: '',
      actionComment: '',
      compareLeftKey: '',
      compareRightKey: 'CURRENT',
      versionPreviewVisible: false,
      versionPreviewTitle: '',
      versionPreviewSnapshot: ''
    }
  },
  computed: {
    request() {
      return this.detail.request || null
    },
    diff() {
      return this.detail.diff || { fields: [] }
    },
    changedFields() {
      return (this.diff.fields || []).filter(field => field.changed)
    },
    compareOptions() {
      const currentLabel = this.request && this.request.status === 'EDITING'
        ? '当前审批（编辑中草稿）'
        : '当前审批（冻结快照）'
      return [
        ...(this.detail.versions || []).map(version => ({
          label: `历史生效 V${version.versionNo}`,
          value: `VERSION:${version.id}`
        })),
        { label: currentLabel, value: 'CURRENT' }
      ]
    },
    compareOriginal() {
      return this.comparisonSnapshot(this.compareLeftKey)
    },
    compareModified() {
      return this.comparisonSnapshot(this.compareRightKey)
    },
    compareLeftLabel() {
      return this.comparisonLabel(this.compareLeftKey)
    },
    compareRightLabel() {
      return this.comparisonLabel(this.compareRightKey)
    },
    isRequestComparison() {
      const base = (this.detail.versions || []).find(version =>
        version.versionNo === this.request?.baseVersionNo
      )
      return this.compareRightKey === 'CURRENT' &&
        (!base || this.compareLeftKey === `VERSION:${base.id}`)
    },
    lineageNodeType() {
      const type = this.request && this.request.resourceType
      return {
        PROJECT: 'PROJECT',
        VARIABLE: 'VARIABLE',
        RULE: 'RULE',
        RULE_PROJECT_BINDING: 'RULE',
        MODEL: 'MODEL',
        EXTERNAL_API: 'API',
        DATABASE: 'DB',
        LIST_LIBRARY: 'LIST',
        LIST_RECORD_BATCH: 'LIST',
        EXTERNAL_DATASOURCE: 'DATASOURCE'
      }[type] || ''
    },
    lineageNodeId() {
      if (!this.request) return ''
      const snapshot = this.requestSnapshot()
      if (this.request.resourceType === 'RULE_PROJECT_BINDING') {
        return snapshot.definitionId || ''
      }
      if (this.request.resourceType === 'LIST_RECORD_BATCH') {
        return snapshot.listId || this.request.resourceId
      }
      return this.request.resourceId
    },
    resourceTitle() {
      if (!this.request) return '审批详情'
      const value = this.requestSnapshot()
      if (
        this.request.resourceType === 'RULE_PROJECT_BINDING' &&
        value.definitionId &&
        value.projectId
      ) {
        const action = this.request.action === 'DELETE' ? '移出' : '加入'
        const rule = value.ruleName
          ? `“${value.ruleName}”`
          : `#${value.definitionId}`
        const project = value.projectName
          ? `“${value.projectName}”`
          : `#${value.projectId}`
        return `将规则${rule}${action}项目${project}`
      }
      if (
        this.request.resourceType === 'LIST_RECORD_BATCH' &&
        value.batchId
      ) {
        const count = (value.addCount || 0) +
          (value.updateCount || 0) + (value.deleteCount || 0)
        return `${value.listName || '名单'} · ${count} 条内容变更`
      }
      return value.ruleName || value.modelName || value.funcName ||
        value.datasourceName || value.projectName || value.varLabel ||
        value.objectLabel || value.experimentName || value.listName ||
        value.validationName || value.billingName ||
        `${this.resourceTypeLabel(this.request.resourceType)} #${this.request.resourceId}`
    },
    preflightErrors() {
      const report = this.parseReport(this.request && this.request.validationReportJson)
      return report.errors || []
    },
    canManageDraft() {
      if (!this.request) return false
      const user = getCurrentUser()
      return !user || user.username === this.request.applicant
    },
    actionDialogTitle() {
      return {
        submit: '提交审批',
        approve: '通过并立即生效',
        reject: '驳回申请',
        cancel: '取消申请'
      }[this.activeAction] || '确认操作'
    },
    actionPlaceholder() {
      return this.activeAction === 'reject'
        ? '请说明需要修改的具体内容'
        : '填写本次操作说明（可选）'
    }
  },
  created() {
    this.loadDetail()
  },
  methods: {
    goBack() {
      this.navigation.back()
    },
    async openRuleDesign() {
      const response = await listRuleRevisions(this.request.resourceId)
      const revisions = response.data || []
      const terminal = ['CONFLICT', 'REJECTED', 'CANCELLED']
        .includes(this.request.status)
      const bound = revisions.find(revision =>
        revision.governanceRequestId === this.request.id
      )
      const revision = terminal
        ? revisions.find(item => item.state === 'DRAFT') || bound
        : bound || revisions.find(item => item.state === 'REVIEW')
      const routeName = resolveDesignerRouteName(
        this.requestSnapshot().modelType
      )
      if (!revision || !routeName) {
        ElMessage.error('未找到可打开的规则修订')
        return
      }
      await this.navigation.push({
        name: routeName,
        params: { id: String(this.request.resourceId) },
        query: {
          sourceType: 'REVISION',
          sourceId: String(revision.id)
        }
      })
    },
    async loadDetail() {
      this.loading = true
      try {
        const response = await getGovernanceRequest(this.route.params.id)
        this.detail = response.data || {}
        const base = (this.detail.versions || []).find(version =>
          version.versionNo === this.detail.request?.baseVersionNo
        ) || (this.detail.versions || [])[0]
        this.compareLeftKey = base ? `VERSION:${base.id}` : 'CURRENT'
        this.compareRightKey = 'CURRENT'
      } finally {
        this.loading = false
      }
    },
    async runPreflight() {
      const response = await preflightGovernanceRequest(this.request.id)
      const report = response.data || {}
      if (report.valid) {
        ElMessage.success('依赖预检通过')
      } else {
        ElMessage.warning(`发现 ${(report.errors || []).length} 个阻断问题`)
      }
      await this.loadDetail()
    },
    openAction(action) {
      this.activeAction = action
      this.actionComment = ''
      this.actionDialogVisible = true
    },
    async confirmAction() {
      if (this.activeAction === 'reject' && !this.actionComment.trim()) {
        ElMessage.warning('请填写驳回原因')
        return
      }
      const actions = {
        submit: submitGovernanceRequest,
        approve: approveGovernanceRequest,
        reject: rejectGovernanceRequest,
        cancel: cancelGovernanceRequest
      }
      this.actionLoading = true
      try {
        const response = await actions[this.activeAction](
          this.request.id, this.actionComment.trim()
        )
        this.actionDialogVisible = false
        if (response.data && response.data.status === 'CONFLICT') {
          ElMessage.warning(
            '审批内容或依赖已变化，已生成可编辑草稿，请重新提交审批'
          )
        } else if (this.activeAction === 'reject') {
          ElMessage.success('已驳回，资源继续使用当前生效版本')
        } else {
          ElMessage.success('操作成功')
        }
        await this.loadDetail()
      } finally {
        this.actionLoading = false
      }
    },
    async restoreVersion(version) {
      await ElMessageBox.confirm(
        `将以 V${version.versionNo} 的内容新增审批草稿，已有版本不会被覆盖。`,
        '恢复历史版本',
        { type: 'warning' }
      )
      const response = await restoreGovernanceVersion(
        this.request.resourceType,
        this.request.resourceId,
        version.id
      )
      ElMessage.success('已生成历史恢复草稿')
      const projectId = Number(this.route.query && this.route.query.projectId)
      this.navigation.push({
        path: `/approval/${response.data.id}`,
        query: Number.isInteger(projectId) && projectId > 0
          ? { projectId }
          : {},
      })
    },
    compareGovernanceVersion(version) {
      this.compareLeftKey = `VERSION:${version.id}`
      this.compareRightKey = 'CURRENT'
    },
    viewGovernanceVersion(version) {
      this.versionPreviewTitle = `历史生效 V${version.versionNo}`
      this.versionPreviewSnapshot = version.snapshotJson || ''
      this.versionPreviewVisible = true
    },
    comparisonSnapshot(key) {
      if (key === 'CURRENT') {
        return this.request
          ? this.request.submittedSnapshotJson ||
              this.request.draftSnapshotJson || '{}'
          : '{}'
      }
      const id = Number(String(key || '').replace('VERSION:', ''))
      const version = (this.detail.versions || []).find(item => item.id === id)
      return version ? version.snapshotJson || '{}' : '{}'
    },
    comparisonLabel(key) {
      const option = this.compareOptions.find(item => item.value === key)
      return option ? option.label : '空配置'
    },
    prettySnapshot(snapshot) {
      if (!snapshot) return '{}'
      try {
        return JSON.stringify(JSON.parse(snapshot), null, 2)
      } catch (error) {
        return String(snapshot)
      }
    },
    requestSnapshot() {
      const text = this.request.submittedSnapshotJson || this.request.draftSnapshotJson
      try {
        return text ? JSON.parse(text) : {}
      } catch (error) {
        return {}
      }
    },
    parseReport(text) {
      try {
        return text ? JSON.parse(text) : {}
      } catch (error) {
        return {}
      }
    },
    displayValue(value, sensitive) {
      if (sensitive) {
        return value || '未配置'
      }
      if (value === null || value === undefined || value === '') return '—'
      return typeof value === 'object'
        ? JSON.stringify(value, null, 2)
        : String(value)
    },
    resourceTypeLabel(type) {
      return {
        VARIABLE: '字段',
        DATA_OBJECT: '数据对象',
        MODEL: '模型',
        EXTERNAL_DATASOURCE: '外数源',
        EXTERNAL_API: '外数 API',
        DATABASE: '数据库',
        FUNCTION: '函数',
        RULE: '规则',
        RULE_PROJECT_BINDING: '项目规则关联',
        LIST_LIBRARY: '名单库',
        LIST_RECORD_BATCH: '名单内容批次',
        FIELD_VALIDATION: '字段校验',
        BILLING_CONFIG: '计费配置',
        EXPERIMENT: '分流',
        PROJECT: '项目'
      }[type] || type
    },
    actionLabel(action) {
      return {
        CREATE: '新增',
        UPDATE: '修改',
        ENABLE: '启用',
        DISABLE: '停用',
        DELETE: '删除',
        RESTORE: '恢复版本'
      }[action] || action
    },
    statusLabel(status) {
      return {
        EDITING: '编辑中',
        PENDING: '待审批',
        APPROVED: '已通过',
        REJECTED: '已驳回',
        CONFLICT: '存在冲突',
        CANCELLED: '已取消'
      }[status] || status
    },
    statusType(status) {
      return {
        EDITING: 'info',
        PENDING: 'warning',
        APPROVED: 'success',
        REJECTED: 'danger',
        CONFLICT: 'danger',
        CANCELLED: 'info'
      }[status] || 'info'
    },
    changeLabel(type) {
      return {
        ADDED: '新增',
        REMOVED: '删除',
        MODIFIED: '修改'
      }[type] || '变化'
    },
    eventLabel(action) {
      return {
        CREATE_DRAFT: '创建草稿',
        SAVE_DRAFT: '保存草稿',
        SUBMIT: '提交审批',
        APPROVE: '审批通过并生效',
        REJECT: '审批驳回',
        CANCEL: '取消申请',
        CONFLICT: '依赖冲突'
      }[action] || action
    },
    formatTime(value) {
      return value ? String(value).replace('T', ' ').slice(0, 19) : '—'
    }
  }
}
</script>

<style scoped>
.approval-detail-page {
  color: var(--tianshu-text-primary);
}

.detail-header {
  padding: 0 0 16px;
  border-bottom: 1px solid #e2e6ec;
}

.back-button {
  padding: 0;
  border: 0;
  background: transparent;
  color: #657184;
  cursor: pointer;
}

.header-main {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-top: 22px;
}

.request-number,
.section-kicker {
  color: var(--el-color-primary);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.1em;
}

.header-main h1 {
  margin: 5px 0 10px;
  font-size: 28px;
  letter-spacing: -0.035em;
}

.header-meta,
.header-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.header-meta span {
  color: #6c7686;
  font-size: 13px;
}

.detail-content {
  padding: 16px 0 32px;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.overview-grid article {
  display: flex;
  min-height: 88px;
  flex-direction: column;
  justify-content: center;
  padding: 17px 20px;
  border: 1px solid #e1e6ed;
  border-radius: 4px;
  background: var(--tianshu-bg-surface);
}

.overview-grid span {
  margin-bottom: 8px;
  color: #7b8595;
  font-size: 12px;
}

.overview-grid strong {
  font-size: 16px;
}

.overview-grid .danger {
  color: #c93636;
}

.terminal-notice {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin-bottom: 16px;
  padding: 15px 18px;
  border: 1px solid #efc7c7;
  border-left: 4px solid #c93636;
  border-radius: 9px;
  background: #fff8f8;
}

.terminal-notice span {
  color: #6d7582;
  font-size: 13px;
}

.content-card {
  border: 1px solid #e1e6ed;
  border-radius: 4px;
  background: var(--tianshu-bg-surface);
  box-shadow: 0 1px 3px rgb(23 36 58 / 5%);
}

.diff-section {
  margin-bottom: 16px;
  padding: 22px;
}

.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 18px;
}

.section-heading.compact {
  align-items: center;
  padding: 20px 20px 0;
}

.section-heading h2 {
  margin: 4px 0 0;
  font-size: 19px;
}

.section-heading > span,
.diff-summary {
  color: #727c8b;
  font-size: 13px;
}

.diff-columns-head {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin: 0 0 9px 0;
}

.version-selector-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.version-selector-row .el-select {
  width: 220px;
}

.diff-columns-head > div {
  display: flex;
  justify-content: space-between;
  padding: 10px 14px;
  border: 1px solid #e5e9ef;
  border-radius: 8px;
  background: #f8f9fb;
  color: #687384;
  font-size: 12px;
}

.diff-columns-head strong {
  color: #2b3544;
}

.diff-list {
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.diff-row {
  overflow: hidden;
  border: 1px solid #e3e7ed;
  border-radius: 9px;
}

.diff-row > header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid #e8ebf0;
  background: #fafbfc;
}

.diff-row code {
  flex: 1;
  color: #4e596a;
  font-size: 12px;
}

.change-badge,
.sensitive-badge {
  padding: 3px 7px;
  border-radius: 5px;
  background: #fff0db;
  color: #9a5800;
  font-size: 11px;
  font-weight: 700;
}

.sensitive-badge {
  background: #edf1f7;
  color: #596579;
}

.diff-values {
  display: grid;
  grid-template-columns: 1fr 1fr;
}

.diff-values pre {
  min-height: 44px;
  margin: 0;
  padding: 12px 14px;
  overflow: auto;
  color: #344052;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
}

.diff-values pre:first-child {
  border-right: 1px solid #e8ebf0;
  background: #fff8f8;
}

.diff-values pre:last-child {
  background: #f5fbf7;
}

.no-diff,
.table-empty {
  padding: 32px;
  color: #8993a2;
  text-align: center;
}

.two-column-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(360px, 0.75fr);
  gap: 16px;
  margin-bottom: 16px;
}

.issue-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 0 20px 20px;
}

.issue-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 11px 13px;
  border-left: 3px solid #c93636;
  background: #fff7f7;
}

.issue-item strong {
  color: #a52727;
  font-size: 13px;
}

.issue-item code {
  color: #7d6b6b;
  font-size: 11px;
}

.timeline {
  margin: 0;
  padding: 4px 22px 22px 40px;
  list-style: none;
}

.timeline li {
  position: relative;
  padding: 0 0 20px 18px;
  border-left: 1px solid #dfe4eb;
}

.timeline li:last-child {
  padding-bottom: 0;
  border-left-color: transparent;
}

.timeline-dot {
  position: absolute;
  top: 3px;
  left: -5px;
  width: 9px;
  height: 9px;
  border: 2px solid #fff;
  border-radius: 50%;
  background: var(--el-color-primary);
  box-shadow: 0 0 0 1px #8ba9e3;
}

.timeline p {
  margin: 5px 0;
  color: #616d7d;
  font-size: 13px;
}

.timeline small {
  color: #929baa;
}

.versions-card {
  overflow: hidden;
}

.version-preview {
  max-height: 560px;
  margin: 0;
  padding: 14px;
  overflow: auto;
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 4px;
  background: var(--tianshu-bg-soft);
  font: 12px/1.6 ui-monospace, SFMono-Regular, Menlo, monospace;
  white-space: pre-wrap;
}

@media (max-width: 1000px) {
  .header-main {
    align-items: flex-start;
    flex-direction: column;
  }

  .overview-grid {
    grid-template-columns: 1fr 1fr;
  }

  .two-column-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 680px) {
  .detail-content,
  .detail-header {
    padding-right: 14px;
    padding-left: 14px;
  }

  .overview-grid,
  .diff-columns-head,
  .diff-values {
    grid-template-columns: 1fr;
  }

  .diff-values pre:first-child {
    border-right: 0;
    border-bottom: 1px solid #e8ebf0;
  }
}
</style>
