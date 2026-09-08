<template>
  <div class="uiue-list-page list-detail-page">
    <div class="detail-header">
      <div>
        <div class="detail-title">{{ library.listName || '名单详情' }}</div>
        <div class="detail-meta">
          {{ library.listCode || '-' }} · {{ listTypeLabel(library.listType) }}
        </div>
      </div>
      <div class="detail-actions">
        <el-button size="small" @click="$router.push('/list')">返回</el-button>
        <el-button size="small" :icon="ElIconDownload" @click="downloadTemplate"
          >模板</el-button
        >
        <el-button
          v-permission="'field:edit'"
          size="small"
          :icon="ElIconUpload2"
          @click="$refs.fileInput.click()"
          >导入并审批</el-button
        >
        <el-button
          size="small"
          :icon="ElIconDownload"
          type="primary"
          @click="exportData"
          >导出</el-button
        >
        <input
          ref="fileInput"
          type="file"
          accept=".xlsx"
          class="hidden-file"
          @change="handleFileChange"
        />
      </div>
    </div>

    <div class="workflow-guide" role="note">
      <div class="workflow-guide-title">名单内容变更流程</div>
      <div class="workflow-steps" aria-label="名单内容变更流程">
        <span class="workflow-step is-current">1 填写或导入</span>
        <span class="workflow-arrow">→</span>
        <span class="workflow-step">2 提交审批</span>
        <span class="workflow-arrow">→</span>
        <span class="workflow-step">3 审批通过后生效</span>
      </div>
      <div class="workflow-guide-text">
        审批通过前，当前有效记录和变更日志都不会改变；重复数据会自动跳过，错误行会明确提示。
      </div>
    </div>

    <div
      v-if="batchFeedback && !dialogVisible"
      class="batch-feedback"
      :class="{ 'is-error': !batchFeedback.submittable }"
      role="alert"
    >
      <div class="batch-feedback-title">{{ batchFeedback.title }}</div>
      <div class="batch-feedback-summary">{{ batchFeedback.summary }}</div>
      <ul v-if="batchFeedback.errors.length" class="batch-feedback-errors">
        <li v-for="(error, index) in batchFeedback.errors" :key="index">
          {{ error }}
        </li>
      </ul>
      <el-button link type="primary" @click="batchFeedback = null">关闭提示</el-button>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="名单内容" name="records">
        <div class="uiue-search-container">
          <el-form :inline="true" size="small" @keyup.enter="handleQuery">
            <el-form-item label="内容类型">
              <el-select
                v-model="query.itemType"
                clearable
                placeholder="全部"
                style="width: 120px"
              >
                <el-option
                  v-for="opt in itemTypeOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select
                v-model="query.status"
                clearable
                placeholder="全部"
                style="width: 100px"
              >
                <el-option label="启用" :value="1" />
                <el-option label="停用" :value="0" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-checkbox v-model="query.effectiveOnly"
                >仅有效期内</el-checkbox
              >
            </el-form-item>
            <el-form-item label="关键字">
              <el-input
                v-model="query.keyword"
                clearable
                placeholder="内容/原因/备注"
                style="width: 180px"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleQuery">查询</el-button>
              <el-button @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-form>
        </div>
        <div class="uiue-btn-bar">
          <div class="btn-right">
            <el-button
              v-permission="'field:edit'"
              type="primary"
              size="small"
              :icon="ElIconPlus"
              @click="handleCreate"
              >新增变更</el-button
            >
          </div>
        </div>
        <el-table show-overflow-tooltip
          :data="records"
          border
          size="small"
          v-loading="loading"
          style="width: 100%"
        >
          <el-table-column
            prop="itemContent"
            label="名单内容"
            min-width="180"
            show-overflow-tooltip
          />
          <el-table-column label="内容类型" width="100" align="center">
            <template v-slot="{ row }"
              ><el-tag size="small">{{
                itemTypeLabel(row.itemType)
              }}</el-tag></template
            >
          </el-table-column>
          <el-table-column label="有效期" min-width="230" show-overflow-tooltip>
            <template v-slot="{ row }"
              >{{ formatTime(row.effectiveTime) || '立即' }} 至
              {{ formatTime(row.expireTime) || '长期' }}</template
            >
          </el-table-column>
          <el-table-column
            prop="reason"
            label="插入原因"
            min-width="150"
            show-overflow-tooltip
          />
          <el-table-column
            prop="remark"
            label="备注"
            min-width="150"
            show-overflow-tooltip
          />
          <el-table-column
            prop="lastOperation"
            label="操作"
            width="80"
            align="center"
          />
          <el-table-column prop="createTime" label="插入时间" min-width="160" />
          <el-table-column label="状态" width="70" align="center">
            <template v-slot="{ row }"
              ><el-tag
                :type="row.status === 1 ? 'success' : 'info'"
                size="small"
                >{{ row.status === 1 ? '启用' : '停用' }}</el-tag
              ></template
            >
          </el-table-column>
          <el-table-column fixed="right" class-name="table-operation-column" :show-overflow-tooltip="false" label="执行操作" width="160" align="center">
            <template v-slot="{ row }">
              <el-button
                v-permission="'field:edit'"
                link
                size="small"
                type="warning"
                @click="handleEdit(row)"
                >修改</el-button
              >
              <el-button
                link
                size="small"
                type="primary"
                @click="handleTrace(row)"
                >追踪</el-button
              >
              <el-button
                v-permission="'field:edit'"
                link
                size="small"
                type="danger"
                class="btn-delete"
                @click="handleDelete(row)"
                >删除</el-button
              >
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          :current-page="query.pageNum"
          :page-size="query.pageSize"
          :total="total"
          layout="total,sizes,prev,pager,next"
          :page-sizes="[10, 30, 50, 100, 200, 500]"
          @current-change="
            (p) => {
              query.pageNum = p
              loadRecords()
            }
          "
          @size-change="
            (s) => {
              query.pageSize = s
              query.pageNum = 1
              loadRecords()
            }
          "
        />
      </el-tab-pane>
      <el-tab-pane label="变更日志" name="logs">
        <div class="uiue-btn-bar log-toolbar">
          <span v-if="traceRecord" class="trace-title"
            >正在追踪：{{ traceRecord.itemContent }}</span
          >
          <el-button v-if="traceRecord" size="small" @click="clearTrace"
            >查看全部日志</el-button
          >
        </div>
        <el-table show-overflow-tooltip
          :data="logs"
          border
          size="small"
          v-loading="logLoading"
          style="width: 100%"
        >
          <el-table-column
            prop="itemContent"
            label="名单内容"
            min-width="180"
            show-overflow-tooltip
          />
          <el-table-column label="内容类型" width="100" align="center">
            <template v-slot="{ row }">{{
              itemTypeLabel(row.itemType)
            }}</template>
          </el-table-column>
          <el-table-column prop="operation" label="执行操作" width="90" />
          <el-table-column label="有效期" min-width="230" show-overflow-tooltip>
            <template v-slot="{ row }">{{ formatPeriod(row) }}</template>
          </el-table-column>
          <el-table-column
            prop="changeContent"
            label="变更内容"
            min-width="260"
            show-overflow-tooltip
          />
          <el-table-column
            prop="reason"
            label="原因"
            min-width="150"
            show-overflow-tooltip
          />
          <el-table-column
            prop="remark"
            label="备注"
            min-width="150"
            show-overflow-tooltip
          />
          <el-table-column prop="createTime" label="操作时间" min-width="160" />
        </el-table>
        <el-pagination
          :current-page="logQuery.pageNum"
          :page-size="logQuery.pageSize"
          :total="logTotal"
          layout="total,sizes,prev,pager,next"
          :page-sizes="[10, 30, 50, 100, 200, 500]"
          @current-change="onLogPageChange"
          @size-change="onLogSizeChange"
        />
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      :title="form.id ? '修改名单记录并审批' : '新增名单记录并审批'"
      v-model="dialogVisible"
      width="640px"
      append-to-body
    >
      <div
        v-if="batchFeedback"
        class="batch-feedback is-error"
        role="alert"
      >
        <div class="batch-feedback-title">{{ batchFeedback.title }}</div>
        <div class="batch-feedback-summary">{{ batchFeedback.summary }}</div>
        <ul v-if="batchFeedback.errors.length" class="batch-feedback-errors">
          <li v-for="(error, index) in batchFeedback.errors" :key="index">
            {{ error }}
          </li>
        </ul>
      </div>
      <el-form
        ref="form"
        :model="form"
        :rules="rules"
        label-width="100px"
        size="small"
      >
        <el-form-item label="名单内容" prop="itemContent">
          <el-input
            v-model="form.itemContent"
            placeholder="手机号、身份证、IP、设备号等"
          />
        </el-form-item>
        <el-form-item label="内容类型" prop="itemType">
          <el-select v-model="form.itemType" style="width: 180px">
            <el-option
              v-for="opt in itemTypeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
            style="margin-left: 16px"
          />
        </el-form-item>
        <el-form-item label="有效期">
          <el-date-picker
            v-model="validRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="生效时间"
            end-placeholder="失效时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="插入原因">
          <el-input v-model="form.reason" />
        </el-form-item>
        <el-form-item label="插入备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template v-slot:footer>
        <div>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button v-permission="'field:edit'" type="primary" @click="submit"
            >生成审批草稿</el-button
          >
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { markRaw } from 'vue'
import {
  Download as ElIconDownload,
  Upload as ElIconUpload2,
  Plus as ElIconPlus,
} from '@element-plus/icons-vue'
import {
  getLibrary,
  listRecords,
  stageRecordChange,
  listRecordLogs,
  importRecords,
  listTemplateUrl,
  listExportUrl,
} from '@/api/ruleList'

export default {
  data() {
    return {
      listId: this.$route.params.id,
      library: {},
      activeTab: 'records',
      loading: false,
      logLoading: false,
      records: [],
      logs: [],
      batchFeedback: null,
      traceRecord: null,
      total: 0,
      logTotal: 0,
      query: {
        pageNum: 1,
        pageSize: 10,
        itemType: '',
        status: '',
        keyword: '',
        effectiveOnly: false,
      },
      logQuery: { pageNum: 1, pageSize: 10 },
      dialogVisible: false,
      validRange: [],
      form: this.emptyForm(),
      itemTypeOptions: [
        { label: '手机号', value: 'MOBILE' },
        { label: '身份证', value: 'ID_CARD' },
        { label: '地址', value: 'ADDRESS' },
        { label: 'IP', value: 'IP' },
        { label: '设备号', value: 'DEVICE' },
        { label: '姓名', value: 'NAME' },
        { label: 'GPS', value: 'GPS' },
        { label: '邮箱', value: 'EMAIL' },
        { label: '银行卡', value: 'BANK_CARD' },
        { label: '其他', value: 'OTHER' },
      ],
      rules: {
        itemContent: [
          { required: true, message: '请输入名单内容', trigger: 'blur' },
        ],
        itemType: [
          { required: true, message: '请选择内容类型', trigger: 'change' },
        ],
      },
      ElIconDownload: markRaw(ElIconDownload),
      ElIconUpload2: markRaw(ElIconUpload2),
      ElIconPlus: markRaw(ElIconPlus),
    }
  },
  name: 'ListDetail',
  created() {
    this.loadLibrary()
    this.loadRecords()
    this.loadLogs()
  },
  watch: {
    '$route.params.id'(id) {
      if (String(id || '') === String(this.listId || '')) return
      this.listId = id
      this.library = {}
      this.records = []
      this.logs = []
      this.traceRecord = null
      this.batchFeedback = null
      this.logTotal = 0
      this.logQuery = { pageNum: 1, pageSize: this.logQuery.pageSize }
      this.activeTab = 'records'
      this.dialogVisible = false
      this.form = this.emptyForm()
      this.validRange = []
      this.loadLibrary()
      this.loadRecords()
      this.loadLogs()
    },
  },
  methods: {
    emptyForm() {
      return {
        id: null,
        itemContent: '',
        itemType: 'MOBILE',
        effectiveTime: '',
        expireTime: '',
        reason: '',
        remark: '',
        lastOperation: 'ADD',
        status: 1,
      }
    },
    async loadLibrary() {
      const res = await getLibrary(this.listId)
      this.library = res.data || {}
    },
    async loadRecords() {
      this.loading = true
      try {
        const params = { ...this.query }
        if (params.status === '') delete params.status
        const res = await listRecords(this.listId, params)
        this.records = (res.data && res.data.records) || []
        this.total = (res.data && res.data.total) || 0
      } finally {
        this.loading = false
      }
    },
    async loadLogs() {
      this.logLoading = true
      try {
        const params = { ...this.logQuery }
        if (
          this.traceRecord &&
          this.traceRecord.itemType &&
          this.traceRecord.itemContent
        ) {
          params.itemType = this.traceRecord.itemType
          params.itemContent = this.traceRecord.itemContent
        }
        const res = await listRecordLogs(this.listId, params)
        this.logs = (res.data && res.data.records) || []
        this.logTotal = (res.data && res.data.total) || 0
      } finally {
        this.logLoading = false
      }
    },
    handleQuery() {
      this.query.pageNum = 1
      this.loadRecords()
    },
    resetQuery() {
      this.query = {
        pageNum: 1,
        pageSize: this.query.pageSize,
        itemType: '',
        status: '',
        keyword: '',
        effectiveOnly: false,
      }
      this.loadRecords()
    },
    handleCreate() {
      this.batchFeedback = null
      this.form = this.emptyForm()
      this.validRange = []
      this.dialogVisible = true
    },
    handleEdit(row) {
      this.batchFeedback = null
      this.form = { ...this.emptyForm(), ...row, lastOperation: 'UPDATE' }
      this.validRange =
        row.effectiveTime || row.expireTime
          ? [
              this.normalizeDateTime(row.effectiveTime),
              this.normalizeDateTime(row.expireTime),
            ]
          : []
      this.dialogVisible = true
    },
    handleTrace(row) {
      if (!row || !row.itemType || !String(row.itemContent || '').trim()) return
      this.traceRecord = row
      this.logQuery.pageNum = 1
      this.activeTab = 'logs'
      this.loadLogs()
    },
    clearTrace() {
      this.traceRecord = null
      this.logQuery.pageNum = 1
      this.loadLogs()
    },
    onLogPageChange(page) {
      this.logQuery.pageNum = page
      this.loadLogs()
    },
    onLogSizeChange(size) {
      this.logQuery.pageSize = size
      this.logQuery.pageNum = 1
      this.loadLogs()
    },
    submit() {
      this.$refs.form.validate(async (valid) => {
        if (!valid) return
        const payload = { ...this.form }
        payload.effectiveTime =
          this.validRange && this.validRange[0]
            ? this.normalizeDateTime(this.validRange[0])
            : null
        payload.expireTime =
          this.validRange && this.validRange[1]
            ? this.normalizeDateTime(this.validRange[1])
            : null
        const operation = payload.id ? 'UPDATE' : 'ADD'
        const response = await stageRecordChange(
          this.listId,
          operation,
          payload
        )
        if (this.handleBatchResult(response.data || {}, '名单记录变更')) {
          this.dialogVisible = false
        }
      })
    },
    handleDelete(row) {
      this.$confirm(
        `将为名单内容「${row.itemContent}」生成删除审批。审批通过前，该记录仍保持当前状态。`,
        '发起删除审批',
        { type: 'warning' }
      )
        .then(async () => {
          const response = await stageRecordChange(
            this.listId,
            'DELETE',
            row
          )
          this.handleBatchResult(response.data || {}, '删除变更')
        })
        .catch(() => {})
    },
    async handleFileChange(event) {
      const file = event.target.files && event.target.files[0]
      event.target.value = ''
      if (!file) return
      const res = await importRecords(this.listId, file)
      const data = res.data || {}
      this.handleBatchResult(data, 'Excel 导入')
    },
    handleBatchResult(data, sourceLabel) {
      const validCount = (data.addCount || 0) +
        (data.updateCount || 0) + (data.deleteCount || 0)
      const summary = `共 ${data.totalCount || 0} 条，待变更 ${validCount} 条，` +
        `重复 ${data.duplicateCount || 0} 条，错误 ${data.invalidCount || 0} 条`
      if (data.submittable && data.approvalRequestId) {
        this.batchFeedback = null
        this.$message.success(
          `${sourceLabel}已生成审批草稿；${summary}`
        )
        this.$router.push('/approval/' + data.approvalRequestId)
        return true
      }
      this.batchFeedback = {
        submittable: false,
        title: `${sourceLabel}未提交审批`,
        summary,
        errors: (data.errors || []).slice(0, 20),
      }
      this.$message.warning('请按页面提示修正错误后重新提交')
      return false
    },
    downloadTemplate() {
      window.open(listTemplateUrl, '_blank')
    },
    exportData() {
      window.open(listExportUrl(this.listId), '_blank')
    },
    formatTime(value) {
      return value ? String(value).replace('T', ' ') : ''
    },
    formatPeriod(row) {
      if (!row) return '立即 至 长期'
      const start = this.formatTime(row.effectiveTime) || '立即'
      const end = this.formatTime(row.expireTime) || '长期'
      return `${start} 至 ${end}`
    },
    normalizeDateTime(value) {
      if (!value) return ''
      return String(value).trim().replace(' ', 'T')
    },
    itemTypeLabel(type) {
      const opt = this.itemTypeOptions.find((item) => item.value === type)
      return opt ? opt.label : type
    },
    listTypeLabel(type) {
      return (
        { BLACK: '黑名单', GREY: '灰名单', WHITE: '白名单', OTHER: '其他' }[
          type
        ] ||
        type ||
        '-'
      )
    },
  },
}
</script>

<style scoped>
.detail-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  padding: 12px 14px;
  background: var(--tianshu-bg-surface);
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 6px;
}
.detail-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--tianshu-text-primary);
  line-height: 1.4;
}
.detail-meta {
  color: var(--tianshu-text-tertiary);
  font-size: 12px;
  margin-top: 2px;
}
.detail-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.hidden-file {
  display: none;
}
.workflow-guide {
  margin-bottom: 12px;
  padding: 12px 14px;
  border: 1px solid var(--tianshu-info-border);
  border-radius: 6px;
  background: var(--tianshu-info-bg);
}
.workflow-guide-title {
  color: var(--tianshu-info-text);
  font-size: 13px;
  font-weight: 600;
}
.workflow-steps {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}
.workflow-step {
  padding: 3px 9px;
  border-radius: 999px;
  background: var(--tianshu-bg-surface);
  color: var(--tianshu-text-secondary);
  font-size: 12px;
}
.workflow-step.is-current {
  background: var(--el-color-primary);
  color: var(--tianshu-brand-foreground);
}
.workflow-arrow {
  color: var(--tianshu-text-tertiary);
}
.workflow-guide-text {
  margin-top: 8px;
  color: var(--tianshu-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}
.batch-feedback {
  margin-bottom: 12px;
  padding: 12px 14px;
  border: 1px solid var(--tianshu-success-border);
  border-radius: 6px;
  background: var(--tianshu-success-bg);
}
.batch-feedback.is-error {
  border-color: var(--tianshu-warning-border);
  background: var(--tianshu-warning-bg);
}
.batch-feedback-title {
  color: var(--tianshu-warning-text);
  font-size: 13px;
  font-weight: 600;
}
.batch-feedback-summary {
  margin-top: 4px;
  color: var(--tianshu-text-secondary);
  font-size: 12px;
}
.batch-feedback-errors {
  margin: 8px 0 4px;
  padding-left: 20px;
  color: var(--tianshu-warning-text);
  font-size: 12px;
  line-height: 1.7;
}
.log-toolbar {
  justify-content: flex-start;
  gap: 12px;
}
.trace-title {
  color: var(--tianshu-text-secondary);
  font-size: 13px;
}
</style>
