<template>
  <div class="uiue-list-page database-page management-list-page">
    <div class="module-hint">
      <div class="hint-title">数据库管理</div>
      <div class="hint-text">
        统一维护可复用的数据库连接，在变量管理中选择“数据库查询”来获取业务数据。
      </div>
    </div>
    <div class="usage-guide">
      <div
        v-for="item in databaseGuideCards"
        :key="item.title"
        class="guide-item"
      >
        <div class="guide-title">{{ item.title }}</div>
        <div class="guide-text">{{ item.text }}</div>
      </div>
    </div>

    <el-tabs class="management-table-region" v-model="activeTab">
      <el-tab-pane class="management-table-region" label="数据源配置" name="datasource">
        <div class="uiue-search-container uiue-filter-toolbar">
          <el-form :inline="true" size="small" @keyup.enter="handleQuery">
            <el-form-item label="项目编码">
              <project-filter-select
                v-model:value="qp.projectCode"
                field="projectCode"
                placeholder="输入项目编码"
                style="width: 150px"
              />
            </el-form-item>
            <el-form-item label="项目名称">
              <project-filter-select
                v-model:value="qp.projectName"
                field="projectName"
                placeholder="输入项目名称"
                style="width: 150px"
              />
            </el-form-item>
            <el-form-item label="作用范围">
              <el-select
                v-model="qp.scope"
                clearable
                placeholder="全部"
                style="width: 110px"
              >
                <el-option label="全局" value="GLOBAL" />
                <el-option label="项目" value="PROJECT" />
              </el-select>
            </el-form-item>
            <el-form-item label="数据源编码">
              <remote-filter-select
                v-model:value="qp.datasourceCode"
                :fetch-options="fetchDatasourceCodeOptions"
                option-label-key="datasourceCode"
                option-value-key="datasourceCode"
                allow-free-input
                placeholder="前缀筛选"
                style="width: 150px"
              />
            </el-form-item>
            <el-form-item label="数据源名称">
              <remote-filter-select
                v-model:value="qp.datasourceName"
                :fetch-options="fetchDatasourceNameOptions"
                option-label-key="datasourceName"
                option-value-key="datasourceName"
                allow-free-input
                placeholder="名称筛选"
                style="width: 150px"
              />
            </el-form-item>
            <el-form-item label="数据库类型">
              <el-select
                v-model="qp.dbType"
                clearable
                placeholder="全部"
                style="width: 120px"
              >
                <el-option
                  v-for="item in dbTypeOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select
                v-model="qp.status"
                clearable
                placeholder="全部"
                style="width: 100px"
              >
                <el-option label="启用" :value="1" />
                <el-option label="停用" :value="0" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleQuery">查询</el-button>
              <el-button @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-form>
          <div class="uiue-btn-bar">
            <div class="btn-right">
          <table-column-settings :columns="databaseColumns" storage-key="tianshu:table-columns:database-list" />
              <governance-workspace-toolbar resource-type="DATABASE" />
              <el-button
                v-permission="'database:edit'"
                type="primary"
                size="small"
                :icon="ElIconPlus"
                @click="handleCreate"
                >新建数据库</el-button
              >
            </div>
          </div>
        </div>

        <el-table class="management-table" show-overflow-tooltip
          :data="tableData"
          border
          size="small"
          v-loading="loading"
          style="width: 100%"
        >
          <el-table-column label="作用范围" width="90" align="center">
            <template v-slot="{ row }">
              <el-tag
                :type="row.scope === 'GLOBAL' ? 'warning' : 'success'"
                size="small"
                >{{ row.scope === 'GLOBAL' ? '全局' : '项目' }}</el-tag
              >
            </template>
          </el-table-column>
          <el-table-column
            prop="projectName"
            label="项目名称"
            min-width="120"
            show-overflow-tooltip
          >
            <template v-slot="{ row }">{{ row.projectName || '—' }}</template>
          </el-table-column>
          <el-table-column
            prop="datasourceCode"
            label="数据源编码"
            min-width="140"
            show-overflow-tooltip
          />
          <el-table-column
            prop="datasourceName"
            label="数据源名称"
            min-width="150"
            show-overflow-tooltip
          />
          <el-table-column
            prop="dbType"
            label="类型"
            width="100"
            align="center"
          >
            <template v-slot="{ row }"
              ><el-tag size="small">{{ row.dbType }}</el-tag></template
            >
          </el-table-column>
          <el-table-column label="连接方式" width="100" align="center">
            <template v-slot="{ row }">
              <el-tag
                :type="
                  row.connectionMode === 'SSH_TUNNEL' ? 'warning' : 'success'
                "
                size="small"
                >{{ connectionModeLabel(row.connectionMode) }}</el-tag
              >
            </template>
          </el-table-column>
          <el-table-column
            label="数据库地址"
            min-width="180"
            show-overflow-tooltip
          >
            <template v-slot="{ row }">{{ formatDbAddress(row) }}</template>
          </el-table-column>
          <el-table-column
            prop="jdbcUrl"
            label="JDBC URL"
            min-width="260"
            show-overflow-tooltip
          />
          <el-table-column
            label="SSH隧道"
            min-width="150"
            show-overflow-tooltip
          >
            <template v-slot="{ row }">{{ formatSshAddress(row) }}</template>
          </el-table-column>
          <el-table-column label="连接池" width="120" align="center">
            <template v-slot="{ row }"
              >{{ row.minIdle || 1 }} / {{ row.maxPoolSize || 5 }}</template
            >
          </el-table-column>
          <el-table-column prop="status" label="状态" width="160" align="center">
            <template v-slot="{ row }">
              <governance-resource-status resource-type="DATABASE" :resource-id="row.id" :status="row.status"><el-tag
                :type="row.status === 1 ? 'success' : 'info'"
                size="small"
                >{{ row.status === 1 ? '启用' : '停用' }}</el-tag
              ></governance-resource-status>
            </template>
          </el-table-column>
          <el-table-column class-name="table-operation-column" :show-overflow-tooltip="false" label="操作" :width="operationColumnWidth(['查询', '编辑', '测试', '版本', '发布', '删除'])" align="center" fixed="right">
            <template v-slot="{ row }">
              <el-button v-permission="{ code: 'database:edit', projectId: row.projectId, scope: row.scope }"
                link
                data-action="inspect"
                size="small"
                type="primary"
                @click="openQuery(row)"
                >查询</el-button
              >
              <el-button
                v-permission="{ code: 'database:edit', projectId: row.projectId, scope: row.scope }"
                link
                data-action="edit"
                size="small"
                type="warning"
                @click="handleEdit(row)"
                >编辑</el-button
              >
              <el-button v-permission="{ code: 'database:edit', projectId: row.projectId, scope: row.scope }"
                link
                data-action="execute"
                size="small"
                type="success"
                @click="handleTest(row)"
                >测试</el-button
              >
              <governance-publish-action resource-type="DATABASE" :resource-id="row.id" />
              <el-button
                v-permission="{ code: 'database:edit', projectId: row.projectId, scope: row.scope }"
                link
                data-action="delete"
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
          :current-page="qp.pageNum"
          :page-size="qp.pageSize"
          :total="total"
          layout="total,sizes,prev,pager,next"
          :page-sizes="[10, 20, 30, 50, 100, 200, 500]"
          @current-change="
            (p) => {
              qp.pageNum = p
              loadData()
            }
          "
          @size-change="
            (s) => {
              qp.pageSize = s
              qp.pageNum = 1
              loadData()
            }
          "
        />
      </el-tab-pane>
      <el-tab-pane class="management-table-region" label="调用日志" name="logs">
        <module-call-log class="management-table-region" module-type="DATABASE" title="数据库调用日志" />
      </el-tab-pane>
    </el-tabs>



    <el-dialog
      title="只读查询"
      v-model="queryDialogVisible"
      width="900px"
      append-to-body
      @closed="closeQuery"
    >
      <div class="query-target">
        当前数据源：{{ queryTarget.datasourceName }} /
        {{ queryTarget.datasourceCode }}
      </div>
      <el-form label-width="90px" size="small">
        <el-form-item label="SQL">
          <monaco-editor
            v-model:value="queryForm.sql"
            language="sql"
            height="170px"
            :read-only="queryLoading"
            @change="syncQueryParamsToSql"
          />
          <div class="query-sql-help">
            仅支持单条 SELECT，不允许分号或锁定查询。使用 ? 表示按顺序绑定的参数。
          </div>
        </el-form-item>
        <el-row :gutter="12" align="middle">
          <el-col :span="16">
            <div class="query-placeholder-summary">
              <strong>查询参数</strong>
              <span>
                已识别 {{ queryPlaceholderCount }} 个有效 ? 占位符
              </span>
            </div>
          </el-col>
          <el-col :span="8">
            <el-form-item label="最大行数">
              <el-input-number
                v-model="queryForm.maxRows"
                :min="0"
                :max="2147483647"
                :precision="0"
                :disabled="queryLoading"
                style="width: 100%"
                @change="resetQueryResult"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="内容上限（字节）">
          <el-input-number
            v-model="queryForm.maxContentBytes"
            :min="0"
            :max="67108864"
            :precision="0"
            :disabled="queryLoading"
            style="width: 100%"
            @change="resetQueryResult"
          />
          <span class="field-help">0 表示不限制；超过上限时查询失败并释放结果。</span>
        </el-form-item>
        <el-form-item label="查询超时（秒）">
          <el-input-number v-model="queryForm.queryTimeoutSeconds" :min="0" :max="2147483647" :precision="0" :disabled="queryLoading" />
          <span class="field-help">0 表示不设置单独超时；正式请求仍受项目总超时约束。</span>
        </el-form-item>
        <div v-if="queryParamRows.length" class="query-params">
          <div
            v-for="(param, index) in queryParamRows"
            :key="index"
            class="query-param-row"
          >
            <div class="query-param-order">
              <strong>参数 {{ index + 1 }}</strong>
              <span>对应第 {{ index + 1 }} 个 ?</span>
            </div>
            <el-select
              v-model="param.type"
              :aria-label="`参数 ${index + 1} 类型`"
              :disabled="queryLoading"
              class="query-param-type"
              @change="onQueryParamTypeChange(param)"
            >
              <el-option
                v-for="option in queryParamTypeOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
            <el-input-number
              v-if="param.type === 'NUMBER'"
              v-model="param.value"
              :aria-label="`参数 ${index + 1} 值`"
              :disabled="queryLoading"
              controls-position="right"
              class="query-param-value"
              @change="resetQueryResult"
            />
            <el-select
              v-else-if="param.type === 'BOOLEAN'"
              v-model="param.value"
              :aria-label="`参数 ${index + 1} 值`"
              :disabled="queryLoading"
              class="query-param-value"
              @change="resetQueryResult"
            >
              <el-option label="是（true）" :value="true" />
              <el-option label="否（false）" :value="false" />
            </el-select>
            <div v-else-if="param.type === 'NULL'" class="query-null-value">
              空值（NULL）
            </div>
            <el-input
              v-else
              v-model="param.value"
              :aria-label="`参数 ${index + 1} 值`"
              :disabled="queryLoading"
              class="query-param-value"
              placeholder="输入文本、日期或时间"
              @change="resetQueryResult"
            />
          </div>
        </div>
        <div v-else class="query-no-params">
          当前 SQL 不含参数占位符，可直接执行查询。
        </div>
      </el-form>
      <div
        class="query-result-state"
        :class="'is-' + queryStatus.toLowerCase()"
      >
        <strong>{{ queryStatusTitle }}</strong>
        <span>{{ queryStatusDescription }}</span>
      </div>
      <el-table show-overflow-tooltip
        v-if="queryStatus === 'SUCCESS' && queryRows.length"
        :data="queryRows"
        border
        size="small"
        max-height="300"
        style="width: 100%"
      >
        <el-table-column
          v-for="col in queryColumns"
          :key="col"
          :prop="col"
          :label="col"
          min-width="120"
          show-overflow-tooltip
        />
      </el-table>
      <template v-slot:footer>
        <div>
          <el-button size="small" @click="closeQuery"
            >关闭</el-button
          >
          <el-button v-permission="'database:edit'"
            size="small"
            type="primary"
            :loading="queryLoading"
            @click="runQuery"
            >执行查询</el-button
          >
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import GovernancePublishAction from '@/components/GovernancePublishAction.vue'
import GovernanceWorkspaceToolbar from '@/components/GovernanceWorkspaceToolbar.vue'
import GovernanceResourceStatus from '@/components/GovernanceResourceStatus.vue'
import { completeResourceChange } from '@/utils/governanceWorkspace'
import { analyzeSqlQuery, validateReadOnlyQuery } from '@/utils/sqlQuery'
import { markRaw } from 'vue'
import { Plus as ElIconPlus } from '@element-plus/icons-vue'
import { deleteDbDatasource, listDbDatasources, queryDbDatasource, testDbDatasource } from '@/api/database'
import ModuleCallLog from '@/components/common/ModuleCallLog.vue'
import MonacoEditor from '@/components/MonacoEditor'
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'
import ProjectFilterSelect from '@/components/ProjectFilterSelect.vue'

import TableColumnSettings from '@/components/common/TableColumnSettings.vue'
import { routeProjectId } from '@/utils/projectContext'
import { operationColumnWidth as getOperationColumnWidth } from '@/utils/operationColumnWidth'

export default {
  data() {
    return {
      databaseColumns: [
        { key: 'scope', label: '作用范围' }, { key: 'projectName', label: '项目名称' },
        { key: 'datasourceCode', label: '数据源编码' }, { key: 'datasourceName', label: '数据源名称' },
        { key: 'dbType', label: '类型' }, { key: 'connectionMode', label: '连接方式' },
        { key: 'address', label: '数据库地址' }, { key: 'jdbcUrl', label: 'JDBC URL' },
        { key: 'ssh', label: 'SSH隧道' }, { key: 'pool', label: '连接池' }, { key: 'status', label: '状态' },
      ],
      databaseGuideCards: [
        {
          title: '只读连接',
          text: '使用只读账号，先测试数据库地址、账号和校验 SQL；通过 SSH 访问时，同时检查隧道设置。',
        },
        {
          title: '查询模板',
          text: '先在“查询”中验证 SELECT SQL 和参数，设置返回行数与超时，再用于变量取值。',
        },
        {
          title: '数据库变量',
          text: '在变量管理中选择“数据库查询”，依次选择连接、填写 SQL 与参数，再选择需要返回的字段；字段名保持数据库原样。',
        },
      ],
      contextProjectId: null,
      activeTab: 'datasource',
      tableData: [],
      total: 0,
      loading: false,
      qp: {
        pageNum: 1,
        pageSize: 20,
        projectCode: '',
        projectName: '',
        scope: '',
        datasourceCode: '',
        datasourceName: '',
        dbType: '',
        status: '',
      },
      queryDialogVisible: false,
      queryTarget: {},
      queryLoading: false,
      queryForm: { sql: '', maxRows: 1000, maxContentBytes: 0, queryTimeoutSeconds: 0 },
      queryParamRows: [],
      queryStatus: 'IDLE',
      queryError: '',
      queryRequestId: 0,
      queryRows: [],
      queryColumns: [],
      queryParamTypeOptions: [
        { label: '文本', value: 'STRING' },
        { label: '数值', value: 'NUMBER' },
        { label: '布尔', value: 'BOOLEAN' },
        { label: '空值', value: 'NULL' },
      ],
      dbTypeOptions: [
        {
          label: 'MySQL',
          value: 'MYSQL',
          driver: 'com.mysql.cj.jdbc.Driver',
          validation: 'SELECT 1',
          port: 3306,
          params:
            'useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false',
        },
        {
          label: 'PostgreSQL',
          value: 'POSTGRESQL',
          driver: 'org.postgresql.Driver',
          validation: 'SELECT 1',
          port: 5432,
          params: 'sslmode=disable',
        },
        {
          label: 'Oracle',
          value: 'ORACLE',
          driver: 'oracle.jdbc.OracleDriver',
          validation: 'SELECT 1 FROM DUAL',
          port: 1521,
          params: '',
        },
        {
          label: 'SQL Server',
          value: 'SQLSERVER',
          driver: 'com.microsoft.sqlserver.jdbc.SQLServerDriver',
          validation: 'SELECT 1',
          port: 1433,
          params: 'encrypt=false;trustServerCertificate=true',
        },
        {
          label: '其他',
          value: 'OTHER',
          driver: '',
          validation: 'SELECT 1',
          port: 0,
          params: '',
        },
      ],
      ElIconPlus: markRaw(ElIconPlus),
    }
  },
  name: 'DatabaseList',
  components: { GovernancePublishAction, GovernanceWorkspaceToolbar,
    GovernanceResourceStatus,
    TableColumnSettings,
    ModuleCallLog,
    MonacoEditor,
    RemoteFilterSelect,
    ProjectFilterSelect,
  },
  created() {
    this.contextProjectId = routeProjectId(
      this.$route,
      this.$store && this.$store.state.currentProject
    )
    if (this.contextProjectId) {
      this.qp.projectId = this.contextProjectId
    }
    this.loadData()
  },
  computed: {
    queryPlaceholderCount() {
      return this.countSqlPlaceholders(this.queryForm.sql)
    },
    queryStatusTitle() {
      return {
        IDLE: '等待执行查询',
        RUNNING: '正在执行查询',
        SUCCESS: '查询成功',
        EMPTY: '查询成功，未返回数据',
        ERROR: '查询失败',
      }[this.queryStatus]
    },
    queryStatusDescription() {
      if (this.queryStatus === 'RUNNING') return '正在连接数据库并读取结果。'
      if (this.queryStatus === 'SUCCESS')
        return `已返回 ${this.queryRows.length} 行，${this.queryForm.maxRows === 0 ? '未限制返回行数' : '最多返回 ' + this.queryForm.maxRows + ' 行'}。`
      if (this.queryStatus === 'EMPTY')
        return 'SQL 已正常执行，但当前条件没有匹配记录。'
      if (this.queryStatus === 'ERROR')
        return this.queryError || '请检查 SQL、参数和值后重试。'
      return '填写只读 SQL 并核对参数后执行。'
    },
  },
  methods: {
    operationColumnWidth(labels) { return getOperationColumnWidth(labels) },
    async loadData() {
      this.loading = true
      try {
        const res = await listDbDatasources(this.cleanParams({ ...this.qp }))
        this.tableData = (res.data && res.data.records) || []
        this.total = (res.data && res.data.total) || 0
      } finally {
        this.loading = false
      }
    },
    fetchDatasourceCodeOptions({ query, pageNum, pageSize }) {
      return listDbDatasources({
        ...this.qp,
        pageNum,
        pageSize,
        datasourceCode: query || '',
      })
    },
    fetchDatasourceNameOptions({ query, pageNum, pageSize }) {
      return listDbDatasources({
        ...this.qp,
        pageNum,
        pageSize,
        datasourceName: query || '',
      })
    },
    handleQuery() {
      this.qp.pageNum = 1
      this.loadData()
    },
    resetQuery() {
      this.qp = {
        pageNum: 1,
        pageSize: this.qp.pageSize,
        projectCode: '',
        projectName: '',
        scope: '',
        datasourceCode: '',
        datasourceName: '',
          dbType: '',
          status: '',
          ...(this.contextProjectId
            ? { projectId: this.contextProjectId }
            : {}),
        }
      this.loadData()
    },
    handleCreate() {
      if (!this.contextProjectId) {
        this.$router.push('/database/new')
        return
      }
      this.$router.push({
        path: '/database/new',
        query: { projectId: this.contextProjectId },
      })
    },
    handleEdit(row) {
      this.$router.push('/database/' + row.id)
    },
    handleDelete(row) {
      this.$confirm(
        '确定删除数据库数据源「' + row.datasourceName + '」?',
        '确认',
        { type: 'warning' }
      )
        .then(async () => {
          const response = await deleteDbDatasource(row.id)
          await completeResourceChange(this, response, () => this.loadData())
        })
        .catch(() => {})
    },
    async handleTest(row) {
      await testDbDatasource(row.id)
      this.$message.success('连接成功')
    },
    openQuery(row) {
      ++this.queryRequestId
      this.queryTarget = row
      this.queryForm = { sql: '', maxRows: 1000, maxContentBytes: 0, queryTimeoutSeconds: 0 }
      this.queryParamRows = []
      this.queryRows = []
      this.queryColumns = []
      this.queryStatus = 'IDLE'
      this.queryError = ''
      this.queryLoading = false
      this.queryDialogVisible = true
    },
    async runQuery() {
      const validationError = this.validateReadOnlyQuery(this.queryForm.sql)
      if (validationError) {
        this.queryStatus = 'ERROR'
        this.queryError = validationError
        return
      }
      this.syncQueryParamsToSql(this.queryForm.sql, false)
      const paramError = this.validateQueryParams()
      if (paramError) {
        this.queryStatus = 'ERROR'
        this.queryError = paramError
        return
      }
      const params = this.queryParamRows.map((param) =>
        this.queryParamValue(param)
      )
      const datasourceId = this.queryTarget.id
      const requestId = ++this.queryRequestId
      this.queryLoading = true
      this.queryStatus = 'RUNNING'
      this.queryError = ''
      this.queryRows = []
      this.queryColumns = []
      try {
        const res = await queryDbDatasource(datasourceId, {
          sql: this.queryForm.sql,
          params,
          maxRows: this.queryForm.maxRows,
          maxContentBytes: this.queryForm.maxContentBytes ?? 0,
          queryTimeoutSeconds: this.queryForm.queryTimeoutSeconds ?? 0,
        })
        if (!this.isActiveQueryRequest(requestId, datasourceId)) return
        this.queryRows = res.data || []
        this.queryColumns = this.queryRows.length
          ? Object.keys(this.queryRows[0])
          : []
        this.queryStatus = this.queryRows.length ? 'SUCCESS' : 'EMPTY'
      } catch (e) {
        if (!this.isActiveQueryRequest(requestId, datasourceId)) return
        this.queryStatus = 'ERROR'
        this.queryError = this.requestErrorMessage(
          e,
          '请检查数据库连接、SQL 和参数后重试'
        )
      } finally {
        if (this.isActiveQueryRequest(requestId, datasourceId)) {
          this.queryLoading = false
        }
      }
    },
    closeQuery() {
      ++this.queryRequestId
      this.queryLoading = false
      this.queryDialogVisible = false
    },
    resetQueryResult() {
      ++this.queryRequestId
      this.queryLoading = false
      this.queryStatus = 'IDLE'
      this.queryError = ''
      this.queryRows = []
      this.queryColumns = []
    },
    syncQueryParamsToSql(sql, resetResult = true) {
      const placeholderCount = this.countSqlPlaceholders(sql)
      const rows = this.queryParamRows
        .slice(0, placeholderCount)
        .map((param) => ({ ...param }))
      while (rows.length < placeholderCount) {
        rows.push({ type: 'STRING', value: '' })
      }
      this.queryParamRows = rows
      if (resetResult) this.resetQueryResult()
    },
    countSqlPlaceholders(sql) {
      return analyzeSqlQuery(sql).placeholderCount
    },
    onQueryParamTypeChange(param) {
      if (param.type === 'NUMBER' || param.type === 'NULL') param.value = null
      else if (param.type === 'BOOLEAN') param.value = true
      else param.value = param.value == null ? '' : String(param.value)
      this.resetQueryResult()
    },
    queryParamValue(param) {
      if (param.type === 'NULL') return null
      if (param.type === 'BOOLEAN') return Boolean(param.value)
      if (param.type === 'NUMBER') return Number(param.value)
      return param.value == null ? '' : String(param.value)
    },
    validateQueryParams() {
      for (let index = 0; index < this.queryParamRows.length; index++) {
        const param = this.queryParamRows[index]
        if (
          param.type === 'NUMBER' &&
          (param.value === null ||
            param.value === '' ||
            !Number.isFinite(Number(param.value)))
        ) {
          return `参数 ${index + 1} 请选择数值类型并填写有效数字`
        }
      }
      return ''
    },
    validateReadOnlyQuery,
    isActiveQueryRequest(requestId, datasourceId) {
      return (
        requestId === this.queryRequestId &&
        String(datasourceId) === String(this.queryTarget.id)
      )
    },
    requestErrorMessage(error, fallback) {
      const responseMessage =
        error &&
        error.response &&
        error.response.data &&
        error.response.data.message
      const message = responseMessage || (error && error.message)
      const text = message && String(message).trim()
      if (!text) return fallback
      const normalized = text.toLowerCase()
      if (normalized.includes('access denied for user') || normalized.includes('failed to initialize pool')) {
        return `${text}；请编辑该数据源核对只读账号、密码和数据库权限后，先点击“测试连接”。`
      }
      if (normalized.includes('communications link failure') || normalized.includes('connection refused')) {
        return `${text}；请核对数据库主机、端口、网络白名单和 SSH 隧道配置。`
      }
      return text
    },
    formatDbAddress(row) {
      const host = row.host || ''
      const port = row.port ? ':' + row.port : ''
      const databaseName = row.databaseName ? '/' + row.databaseName : ''
      return host ? host + port + databaseName : '—'
    },
    formatSshAddress(row) {
      if (row.connectionMode !== 'SSH_TUNNEL') return '—'
      const host = row.sshHost || ''
      const port = row.sshPort ? ':' + row.sshPort : ''
      const user = row.sshUsername ? row.sshUsername + '@' : ''
      return host ? user + host + port : '未配置'
    },
    connectionModeLabel(mode) {
      return mode === 'SSH_TUNNEL' ? 'SSH隧道' : '直连'
    },
    cleanParams(params) {
      Object.keys(params).forEach((key) => {
        if (
          params[key] === '' ||
          params[key] === null ||
          params[key] === undefined
        )
          delete params[key]
      })
      return params
    },
  },
}
</script>

<style lang="scss" scoped>
.database-page {
  .module-hint {
    background: #ecfdf5;
    border: 1px solid #a7f3d0;
    border-radius: 4px;
    padding: 12px 14px;
    margin-bottom: 14px;
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .hint-title {
    color: #047857;
    font-weight: 700;
    white-space: nowrap;
  }

  .hint-text,
  .query-target {
    color: var(--tianshu-text-secondary);
    line-height: 1.5;
  }

  .query-target {
    margin-bottom: 12px;
  }

  .usage-guide {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 12px;
    margin-bottom: 14px;
  }

  .guide-item {
    border: 1px solid var(--tianshu-border-subtle);
    border-radius: 4px;
    padding: 10px 12px;
    background: var(--tianshu-bg-surface);
  }

  .guide-title {
    color: var(--tianshu-text-primary);
    font-weight: 700;
    margin-bottom: 6px;
  }

  .guide-text {
    color: var(--tianshu-text-tertiary);
    font-size: 12px;
    line-height: 1.6;
  }

  .sql-input :deep(textarea) {
    font-family: Menlo, Monaco, Consolas, monospace;
    font-size: 12px;
    line-height: 1.5;
  }

  .code-input :deep(textarea) {
    font-family: Menlo, Monaco, Consolas, monospace;
    font-size: 12px;
    line-height: 1.5;
  }

  .form-section {
    border: 1px solid #e5e7eb;
    border-radius: 4px;
    padding: 12px 12px 0;
    margin-bottom: 12px;
  }

  .section-title {
    color: #334155;
    font-weight: 700;
    margin-bottom: 10px;
  }

  .query-sql-help {
    color: var(--tianshu-text-tertiary);
    font-size: 12px;
    line-height: 1.5;
    margin-top: 6px;
  }

  .query-placeholder-summary {
    display: flex;
    align-items: center;
    gap: 10px;
    color: var(--tianshu-text-primary);
    padding: 0 0 14px 90px;

    span {
      color: var(--tianshu-text-tertiary);
      font-size: 12px;
    }
  }

  .query-params {
    border: 1px solid #dbeafe;
    background: #f8fbff;
    border-radius: 4px;
    padding: 8px 12px;
    margin: 0 0 14px 90px;
  }

  .query-param-row {
    display: grid;
    grid-template-columns: 132px 126px minmax(0, 1fr);
    align-items: center;
    gap: 10px;
    padding: 8px 0;

    & + & {
      border-top: 1px solid var(--tianshu-border-subtle);
    }
  }

  .query-param-order {
    display: flex;
    flex-direction: column;
    gap: 2px;
    color: #334155;

    span {
      color: var(--tianshu-text-disabled);
      font-size: 11px;
    }
  }

  .query-param-value {
    width: 100%;
  }

  .query-null-value {
    height: 32px;
    display: flex;
    align-items: center;
    padding: 0 10px;
    color: var(--tianshu-text-tertiary);
    background: #f1f5f9;
    border: 1px dashed var(--tianshu-border);
    border-radius: 4px;
  }

  .query-no-params {
    color: var(--tianshu-text-tertiary);
    background: var(--tianshu-bg-soft);
    border: 1px dashed var(--tianshu-border);
    border-radius: 4px;
    padding: 12px 14px;
    margin: 0 0 14px 90px;
  }

  .query-result-state {
    display: flex;
    flex-direction: column;
    gap: 4px;
    border: 1px solid var(--tianshu-border);
    background: var(--tianshu-bg-soft);
    color: var(--tianshu-text-secondary);
    border-radius: 4px;
    padding: 12px 14px;
    margin-bottom: 12px;

    span {
      font-size: 12px;
    }

    &.is-running {
      border-color: #93c5fd;
      background: #eff6ff;
      color: #1d4ed8;
    }

    &.is-success,
    &.is-empty {
      border-color: #86efac;
      background: #f0fdf4;
      color: #15803d;
    }

    &.is-error {
      border-color: #fecaca;
      background: #fef2f2;
      color: #b91c1c;
    }
  }

  @media (max-width: 1200px) {
    .usage-guide {
      grid-template-columns: repeat(1, minmax(0, 1fr));
    }

    .query-placeholder-summary,
    .query-params,
    .query-no-params {
      margin-left: 0;
      padding-left: 12px;
    }
  }
}
</style>
