<template>
  <div class="uiue-list-page list-detail-page">
    <div class="detail-header">
      <div>
        <div class="detail-title">{{ library.listName || '名单详情' }}</div>
        <div class="detail-meta">{{ library.listCode || '-' }} · {{ listTypeLabel(library.listType) }}</div>
      </div>
      <div class="detail-actions">
        <el-button size="small" @click="$router.push('/list')">返回</el-button>
        <el-button size="small" icon="el-icon-download" @click="downloadTemplate">模板</el-button>
        <el-button size="small" icon="el-icon-upload2" @click="$refs.fileInput.click()">导入</el-button>
        <el-button size="small" icon="el-icon-download" type="primary" @click="exportData">导出</el-button>
        <input ref="fileInput" type="file" accept=".xlsx" class="hidden-file" @change="handleFileChange">
      </div>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="名单内容" name="records">
        <div class="uiue-search-container">
          <el-form :inline="true" size="small" @keyup.enter.native="handleQuery">
            <el-form-item label="内容类型">
              <el-select v-model="query.itemType" clearable placeholder="全部" style="width:120px;">
                <el-option v-for="opt in itemTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="query.status" clearable placeholder="全部" style="width:100px;">
                <el-option label="启用" :value="1" />
                <el-option label="停用" :value="0" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-checkbox v-model="query.effectiveOnly">仅有效期内</el-checkbox>
            </el-form-item>
            <el-form-item label="关键字">
              <el-input v-model="query.keyword" clearable placeholder="内容/原因/备注" style="width:180px;" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleQuery">查询</el-button>
              <el-button @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-form>
        </div>
        <div class="uiue-btn-bar">
          <div class="btn-right">
            <el-button type="primary" size="small" icon="el-icon-plus" @click="handleCreate">新增记录</el-button>
          </div>
        </div>
        <el-table :data="records" border size="small" v-loading="loading" style="width:100%;">
          <el-table-column prop="itemContent" label="名单内容" min-width="180" show-overflow-tooltip />
          <el-table-column label="内容类型" width="100" align="center">
            <template slot-scope="{ row }"><el-tag size="mini">{{ itemTypeLabel(row.itemType) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="有效期" min-width="230" show-overflow-tooltip>
            <template slot-scope="{ row }">{{ formatTime(row.effectiveTime) || '立即' }} 至 {{ formatTime(row.expireTime) || '长期' }}</template>
          </el-table-column>
          <el-table-column prop="reason" label="插入原因" min-width="150" show-overflow-tooltip />
          <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
          <el-table-column prop="lastOperation" label="操作" width="80" align="center" />
          <el-table-column prop="createTime" label="插入时间" min-width="160" />
          <el-table-column label="状态" width="70" align="center">
            <template slot-scope="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'" size="mini">{{ row.status === 1 ? '启用' : '停用' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="执行操作" width="160" align="center">
            <template slot-scope="{ row }">
              <el-button type="text" size="small" @click="handleEdit(row)">修改</el-button>
              <el-button type="text" size="small" @click="handleTrace(row)">追踪</el-button>
              <el-button type="text" size="small" class="btn-delete" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          :current-page="query.pageNum"
          :page-size="query.pageSize"
          :total="total"
          layout="total,sizes,prev,pager,next"
          :page-sizes="[10,30,50,100,200,500]"
          @current-change="p => { query.pageNum = p; loadRecords() }"
          @size-change="s => { query.pageSize = s; query.pageNum = 1; loadRecords() }"
        />
      </el-tab-pane>
      <el-tab-pane label="变更日志" name="logs">
        <div class="uiue-btn-bar log-toolbar">
          <span v-if="traceRecord" class="trace-title">正在追踪：{{ traceRecord.itemContent }}</span>
          <el-button v-if="traceRecord" size="small" @click="clearTrace">查看全部日志</el-button>
        </div>
        <el-table :data="logs" border size="small" v-loading="logLoading" style="width:100%;">
          <el-table-column prop="itemContent" label="名单内容" min-width="180" show-overflow-tooltip />
          <el-table-column label="内容类型" width="100" align="center">
            <template slot-scope="{ row }">{{ itemTypeLabel(row.itemType) }}</template>
          </el-table-column>
          <el-table-column prop="operation" label="执行操作" width="90" />
          <el-table-column label="有效期" min-width="230" show-overflow-tooltip>
            <template slot-scope="{ row }">{{ formatPeriod(row) }}</template>
          </el-table-column>
          <el-table-column prop="changeContent" label="变更内容" min-width="260" show-overflow-tooltip />
          <el-table-column prop="reason" label="原因" min-width="150" show-overflow-tooltip />
          <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
          <el-table-column prop="createTime" label="操作时间" min-width="160" />
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog :title="form.id ? '修改名单记录' : '新增名单记录'" :visible.sync="dialogVisible" width="640px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="100px" size="small">
        <el-form-item label="名单内容" prop="itemContent">
          <el-input v-model="form.itemContent" placeholder="手机号、身份证、IP、设备号等" />
        </el-form-item>
        <el-form-item label="内容类型" prop="itemType">
          <el-select v-model="form.itemType" style="width:180px;">
            <el-option v-for="opt in itemTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" style="margin-left:16px;" />
        </el-form-item>
        <el-form-item label="有效期">
          <el-date-picker
            v-model="validRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="生效时间"
            end-placeholder="失效时间"
            value-format="yyyy-MM-ddTHH:mm:ss"
            style="width:100%;"
          />
        </el-form-item>
        <el-form-item label="插入原因">
          <el-input v-model="form.reason" />
        </el-form-item>
        <el-form-item label="插入备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { getLibrary, listRecords, createRecord, updateRecord, deleteRecord, listRecordLogs, importRecords, listTemplateUrl, listExportUrl } from '@/api/ruleList'

export default {
  name: 'ListDetail',
  data() {
    return {
      listId: this.$route.params.id,
      library: {},
      activeTab: 'records',
      loading: false,
      logLoading: false,
      records: [],
      logs: [],
      traceRecord: null,
      total: 0,
      query: { pageNum: 1, pageSize: 10, itemType: '', status: '', keyword: '', effectiveOnly: false },
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
        { label: '其他', value: 'OTHER' }
      ],
      rules: {
        itemContent: [{ required: true, message: '请输入名单内容', trigger: 'blur' }],
        itemType: [{ required: true, message: '请选择内容类型', trigger: 'change' }]
      }
    }
  },
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
      this.activeTab = 'records'
      this.dialogVisible = false
      this.form = this.emptyForm()
      this.validRange = []
      this.loadLibrary()
      this.loadRecords()
      this.loadLogs()
    }
  },
  methods: {
    emptyForm() {
      return { id: null, itemContent: '', itemType: 'MOBILE', effectiveTime: '', expireTime: '', reason: '', remark: '', lastOperation: 'ADD', status: 1 }
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
        const params = { pageNum: 1, pageSize: 100 }
        if (this.traceRecord && this.traceRecord.id) params.recordId = this.traceRecord.id
        const res = await listRecordLogs(this.listId, params)
        this.logs = (res.data && res.data.records) || []
      } finally {
        this.logLoading = false
      }
    },
    handleQuery() { this.query.pageNum = 1; this.loadRecords() },
    resetQuery() {
      this.query = { pageNum: 1, pageSize: this.query.pageSize, itemType: '', status: '', keyword: '', effectiveOnly: false }
      this.loadRecords()
    },
    handleCreate() {
      this.form = this.emptyForm()
      this.validRange = []
      this.dialogVisible = true
    },
    handleEdit(row) {
      this.form = { ...this.emptyForm(), ...row, lastOperation: 'UPDATE' }
      this.validRange = row.effectiveTime || row.expireTime
        ? [this.normalizeDateTime(row.effectiveTime), this.normalizeDateTime(row.expireTime)]
        : []
      this.dialogVisible = true
    },
    handleTrace(row) {
      this.traceRecord = row
      this.activeTab = 'logs'
      this.loadLogs()
    },
    clearTrace() {
      this.traceRecord = null
      this.loadLogs()
    },
    submit() {
      this.$refs.form.validate(async valid => {
        if (!valid) return
        const payload = { ...this.form }
        payload.effectiveTime = this.validRange && this.validRange[0] ? this.normalizeDateTime(this.validRange[0]) : null
        payload.expireTime = this.validRange && this.validRange[1] ? this.normalizeDateTime(this.validRange[1]) : null
        payload.lastOperation = payload.id ? 'UPDATE' : 'ADD'
        if (payload.id) await updateRecord(this.listId, payload)
        else await createRecord(this.listId, payload)
        this.$message.success('保存成功')
        this.dialogVisible = false
        this.loadRecords()
        this.loadLogs()
      })
    },
    handleDelete(row) {
      this.$confirm(`确定删除名单内容「${row.itemContent}」？`, '确认删除', { type: 'warning' })
        .then(async () => {
          await deleteRecord(this.listId, row.id)
          this.$message.success('删除成功')
          this.loadRecords()
          this.loadLogs()
        }).catch(() => {})
    },
    async handleFileChange(event) {
      const file = event.target.files && event.target.files[0]
      event.target.value = ''
      if (!file) return
      const res = await importRecords(this.listId, file)
      const data = res.data || {}
      if (data.errorCount > 0) {
        this.$message.warning(`导入完成：成功 ${data.successCount || 0} 条，失败 ${data.errorCount} 条`)
      } else {
        this.$message.success(`导入成功 ${data.successCount || 0} 条`)
      }
      this.loadRecords()
      this.loadLogs()
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
      const opt = this.itemTypeOptions.find(item => item.value === type)
      return opt ? opt.label : type
    },
    listTypeLabel(type) {
      return { BLACK: '黑名单', GREY: '灰名单', WHITE: '白名单', OTHER: '其他' }[type] || type || '-'
    }
  }
}
</script>

<style scoped>
.detail-header { display:flex; align-items:center; justify-content:space-between; gap:12px; margin-bottom:12px; padding:12px 14px; background:#fff; border:1px solid #ebeef5; border-radius:6px; }
.detail-title { font-size:16px; font-weight:600; color:#303133; line-height:1.4; }
.detail-meta { color:#909399; font-size:12px; margin-top:2px; }
.detail-actions { display:flex; align-items:center; gap:8px; }
.hidden-file { display:none; }
.log-toolbar { justify-content:flex-start; gap:12px; }
.trace-title { color:#606266; font-size:13px; }
</style>
