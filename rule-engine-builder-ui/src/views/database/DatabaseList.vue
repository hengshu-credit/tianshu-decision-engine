<template>
  <div class="uiue-list-page database-page">
    <div class="module-hint">
      <div class="hint-title">数据库管理</div>
      <div class="hint-text">统一维护外部数据库连接池，供数据查询变量（var_source=DB）通过后端访问外部库。</div>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="数据源配置" name="datasource">
        <div class="uiue-search-container">
          <el-form :inline="true" size="small">
            <el-form-item label="作用范围">
              <el-select v-model="qp.scope" clearable placeholder="全部" style="width:110px;">
                <el-option label="全局" value="GLOBAL" />
                <el-option label="项目" value="PROJECT" />
              </el-select>
            </el-form-item>
            <el-form-item label="数据源编码">
              <remote-filter-select v-model="qp.datasourceCode" :fetch-options="fetchDatasourceCodeOptions" option-label-key="datasourceCode" option-value-key="datasourceCode" allow-free-input placeholder="前缀筛选" style="width:150px;" />
            </el-form-item>
            <el-form-item label="数据源名称">
              <remote-filter-select v-model="qp.datasourceName" :fetch-options="fetchDatasourceNameOptions" option-label-key="datasourceName" option-value-key="datasourceName" allow-free-input placeholder="名称筛选" style="width:150px;" />
            </el-form-item>
            <el-form-item label="数据库类型">
              <el-select v-model="qp.dbType" clearable placeholder="全部" style="width:120px;">
                <el-option v-for="item in dbTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="qp.status" clearable placeholder="全部" style="width:100px;">
                <el-option label="启用" :value="1" />
                <el-option label="停用" :value="0" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleQuery">查询</el-button>
              <el-button @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-form>
        </div>

        <div class="uiue-btn-bar">
          <div class="btn-right">
            <el-button type="primary" size="small" icon="el-icon-plus" @click="handleCreate">新建数据库</el-button>
          </div>
        </div>

        <el-table :data="tableData" border size="small" v-loading="loading" style="width:100%;">
          <el-table-column label="作用范围" width="90" align="center">
            <template slot-scope="{ row }">
              <el-tag :type="row.scope === 'GLOBAL' ? 'warning' : 'success'" size="mini">{{ row.scope === 'GLOBAL' ? '全局' : '项目' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="projectName" label="项目名称" min-width="120" show-overflow-tooltip>
            <template slot-scope="{ row }">{{ row.projectName || '—' }}</template>
          </el-table-column>
          <el-table-column prop="datasourceCode" label="数据源编码" min-width="140" show-overflow-tooltip />
          <el-table-column prop="datasourceName" label="数据源名称" min-width="150" show-overflow-tooltip />
          <el-table-column prop="dbType" label="类型" width="100" align="center">
            <template slot-scope="{ row }"><el-tag size="mini">{{ row.dbType }}</el-tag></template>
          </el-table-column>
          <el-table-column label="连接方式" width="100" align="center">
            <template slot-scope="{ row }">
              <el-tag :type="row.connectionMode === 'SSH_TUNNEL' ? 'warning' : 'success'" size="mini">{{ connectionModeLabel(row.connectionMode) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="数据库地址" min-width="180" show-overflow-tooltip>
            <template slot-scope="{ row }">{{ formatDbAddress(row) }}</template>
          </el-table-column>
          <el-table-column prop="jdbcUrl" label="JDBC URL" min-width="260" show-overflow-tooltip />
          <el-table-column label="SSH隧道" min-width="150" show-overflow-tooltip>
            <template slot-scope="{ row }">{{ formatSshAddress(row) }}</template>
          </el-table-column>
          <el-table-column label="连接池" width="120" align="center">
            <template slot-scope="{ row }">{{ row.minIdle || 1 }} / {{ row.maxPoolSize || 5 }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="70" align="center">
            <template slot-scope="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="mini">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" align="center">
            <template slot-scope="{ row }">
              <el-button type="text" size="small" @click="handleEdit(row)">编辑</el-button>
              <el-button type="text" size="small" @click="handleTest(row)">测试</el-button>
              <el-button type="text" size="small" @click="openQuery(row)">查询</el-button>
              <el-button type="text" size="small" class="btn-delete" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          :current-page="qp.pageNum"
          :page-size="qp.pageSize"
          :total="total"
          layout="total,sizes,prev,pager,next"
          :page-sizes="[10,30,50,100,200,500]"
          @current-change="p => { qp.pageNum = p; loadData() }"
          @size-change="s => { qp.pageSize = s; qp.pageNum = 1; loadData() }"
        />
      </el-tab-pane>
      <el-tab-pane label="调用日志" name="logs">
        <module-call-log module-type="DATABASE" title="数据库调用日志" />
      </el-tab-pane>
    </el-tabs>

    <el-dialog :title="form.id ? '编辑数据库数据源' : '新建数据库数据源'" :visible.sync="dialogVisible" width="900px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="120px" size="small">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="作用范围">
              <el-select v-model="form.scope" style="width:100%" @change="onScopeChange">
                <el-option label="全局" value="GLOBAL" />
                <el-option label="项目级" value="PROJECT" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item v-if="form.scope === 'PROJECT'" label="所属项目" prop="projectId">
              <el-select v-model="form.projectId" filterable placeholder="请选择项目" style="width:100%">
                <el-option v-for="project in projects" :key="project.id" :label="project.projectName" :value="project.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="数据源编码" prop="datasourceCode">
              <el-input v-model="form.datasourceCode" placeholder="如 customer_core_db" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="数据源名称" prop="datasourceName">
              <el-input v-model="form.datasourceName" placeholder="如 客户核心库" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="数据库类型">
              <el-select v-model="form.dbType" style="width:100%" @change="onDbTypeChange">
                <el-option v-for="item in dbTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="驱动类">
              <el-input v-model="form.driverClassName" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="连接方式">
          <el-radio-group v-model="form.connectionMode">
            <el-radio-button label="DIRECT">直连</el-radio-button>
            <el-radio-button label="SSH_TUNNEL">SSH隧道</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="14">
            <el-form-item label="数据库主机">
              <el-input v-model="form.host" placeholder="如 mysql.internal" @input="onJdbcPartChange" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="端口">
              <el-input-number v-model="form.port" :min="1" :max="65535" controls-position="right" style="width:100%" :controls="false" @change="onJdbcPartChange" />
            </el-form-item>
          </el-col>
          <el-col :span="14">
            <el-form-item label="库名/服务名">
              <el-input v-model="form.databaseName" placeholder="如 rule_engine" @input="onJdbcPartChange" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="扩展参数">
          <el-input v-model="form.jdbcParams" placeholder="useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai，也可直接追加在JDBC URL后" @input="onJdbcPartChange" />
        </el-form-item>
        <el-form-item label="JDBC URL" prop="jdbcUrl">
          <el-input v-model="form.jdbcUrl" placeholder="jdbc:mysql://host:3306/db?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai" @input="form.jdbcAutoBuild = false">
            <el-button slot="append" @click="generateJdbcUrl(true)">生成</el-button>
          </el-input>
          <el-checkbox v-model="form.jdbcAutoBuild" style="margin-top:6px;" @change="onJdbcAutoBuildChange">按上方表单自动生成 JDBC URL</el-checkbox>
        </el-form-item>
        <div v-if="form.connectionMode === 'SSH_TUNNEL'" class="form-section">
          <div class="section-title">SSH 隧道</div>
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="SSH主机">
                <el-input v-model="form.sshHost" placeholder="堡垒机地址" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="SSH端口">
                <el-input-number v-model="form.sshPort" :min="1" :max="65535" controls-position="right" style="width:100%" :controls="false"/>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="SSH用户">
                <el-input v-model="form.sshUsername" autocomplete="off" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="SSH密码">
                <el-input v-model="form.sshPassword" type="password" autocomplete="new-password" show-password />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="私钥口令">
                <el-input v-model="form.sshPassphrase" type="password" autocomplete="new-password" show-password />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="12">
            <el-col :span="16">
              <el-form-item label="SSH私钥">
                <el-input v-model="form.sshPrivateKey" class="code-input" type="textarea" :rows="4" placeholder="可粘贴 PEM 私钥内容；密码和私钥二选一" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="SSH超时">
                <el-input-number v-model="form.sshTimeoutMs" :min="1000" :step="1000" style="width:100%" />
              </el-form-item>
            </el-col>
          </el-row>
        </div>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="用户名">
              <el-input v-model="form.username" autocomplete="off" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="密码">
              <el-input v-model="form.password" type="password" autocomplete="new-password" show-password />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="最大连接数">
              <el-input-number v-model="form.maxPoolSize" :min="1" :max="100" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="最小空闲">
              <el-input-number v-model="form.minIdle" :min="0" :max="100" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="连接超时">
              <el-input-number v-model="form.connectionTimeoutMs" :min="100" :step="500" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="空闲超时">
              <el-input-number v-model="form.idleTimeoutMs" :min="10000" :step="60000" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="校验 SQL">
          <monaco-editor v-model="form.validationQuery" language="sql" theme="rule-sql-light" height="90px" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="dialogVisible = false">取消</el-button>
        <el-button size="small" @click="handleTestDraft">测试连接</el-button>
        <el-button size="small" type="primary" @click="handleSubmit">保存</el-button>
      </div>
    </el-dialog>

    <el-dialog title="只读查询" :visible.sync="queryDialogVisible" width="840px" append-to-body>
      <div class="query-target">当前数据源：{{ queryTarget.datasourceName }} / {{ queryTarget.datasourceCode }}</div>
      <el-form label-width="90px" size="small">
        <el-form-item label="SQL">
          <monaco-editor v-model="queryForm.sql" language="sql" theme="rule-sql-light" height="170px" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="16">
            <el-form-item label="参数数组">
              <monaco-editor v-model="queryForm.paramsText" language="json" height="90px" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="最大行数">
              <el-input-number v-model="queryForm.maxRows" :min="1" :max="500" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <el-table v-if="queryRows.length" :data="queryRows" border size="mini" max-height="300" style="width:100%;">
        <el-table-column v-for="col in queryColumns" :key="col" :prop="col" :label="col" min-width="120" show-overflow-tooltip />
      </el-table>
      <div v-else class="empty-query-result">暂无查询结果</div>
      <div slot="footer">
        <el-button size="small" @click="queryDialogVisible = false">关闭</el-button>
        <el-button size="small" type="primary" :loading="queryLoading" @click="runQuery">执行查询</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import {
  createDbDatasource,
  deleteDbDatasource,
  listDbDatasources,
  queryDbDatasource,
  testDbDatasource,
  testDbDatasourceDraft,
  updateDbDatasource
} from '@/api/database'
import { listProjects } from '@/api/project'
import ModuleCallLog from '@/components/common/ModuleCallLog.vue'
import MonacoEditor from '@/components/MonacoEditor'
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'

export default {
  name: 'DatabaseList',
  components: { ModuleCallLog, MonacoEditor, RemoteFilterSelect },
  data() {
    return {
      projects: [],
      activeTab: 'datasource',
      tableData: [],
      total: 0,
      loading: false,
      qp: { pageNum: 1, pageSize: 10, scope: '', datasourceCode: '', datasourceName: '', dbType: '', status: '' },
      dialogVisible: false,
      form: this.emptyForm(),
      rules: {
        datasourceCode: [{ required: true, message: '请输入数据源编码', trigger: 'blur' }],
        datasourceName: [{ required: true, message: '请输入数据源名称', trigger: 'blur' }],
        jdbcUrl: [{ required: true, message: '请输入 JDBC URL', trigger: 'blur' }],
        projectId: [{ required: true, message: '请选择所属项目', trigger: 'change' }]
      },
      queryDialogVisible: false,
      queryTarget: {},
      queryLoading: false,
      queryForm: { sql: '', paramsText: '[]', maxRows: 100 },
      queryRows: [],
      queryColumns: [],
      dbTypeOptions: [
        {
          label: 'MySQL',
          value: 'MYSQL',
          driver: 'com.mysql.cj.jdbc.Driver',
          validation: 'SELECT 1',
          port: 3306,
          params: 'useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false'
        },
        { label: 'PostgreSQL', value: 'POSTGRESQL', driver: 'org.postgresql.Driver', validation: 'SELECT 1', port: 5432, params: 'sslmode=disable' },
        { label: 'Oracle', value: 'ORACLE', driver: 'oracle.jdbc.OracleDriver', validation: 'SELECT 1 FROM DUAL', port: 1521, params: '' },
        { label: 'SQL Server', value: 'SQLSERVER', driver: 'com.microsoft.sqlserver.jdbc.SQLServerDriver', validation: 'SELECT 1', port: 1433, params: 'encrypt=false;trustServerCertificate=true' },
        { label: '其他', value: 'OTHER', driver: '', validation: 'SELECT 1', port: 0, params: '' }
      ]
    }
  },
  created() {
    this.loadProjects()
    this.loadData()
  },
  methods: {
    emptyForm() {
      return {
        id: null, scope: 'PROJECT', projectId: null, datasourceCode: '', datasourceName: '',
        dbType: 'MYSQL', connectionMode: 'DIRECT', host: '', port: 3306, databaseName: '',
        jdbcParams: 'useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false',
        jdbcAutoBuild: true, driverClassName: 'com.mysql.cj.jdbc.Driver', jdbcUrl: '', username: '',
        password: '', sshHost: '', sshPort: 22, sshUsername: '', sshPassword: '', sshPrivateKey: '',
        sshPassphrase: '', sshTimeoutMs: 10000, maxPoolSize: 5, minIdle: 1, connectionTimeoutMs: 3000, idleTimeoutMs: 600000,
        validationQuery: 'SELECT 1', description: '', status: 1
      }
    },
    async loadProjects() {
      try {
        const res = await listProjects({ pageNum: 1, pageSize: 500, status: 1 })
        this.projects = (res.data && res.data.records) || []
      } catch (e) {
        this.projects = []
      }
    },
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
      return listDbDatasources({ ...this.qp, pageNum, pageSize, datasourceCode: query || '' })
    },
    fetchDatasourceNameOptions({ query, pageNum, pageSize }) {
      return listDbDatasources({ ...this.qp, pageNum, pageSize, datasourceName: query || '' })
    },
    handleQuery() {
      this.qp.pageNum = 1
      this.loadData()
    },
    resetQuery() {
      this.qp = { pageNum: 1, pageSize: this.qp.pageSize, scope: '', datasourceCode: '', datasourceName: '', dbType: '', status: '' }
      this.loadData()
    },
    handleCreate() {
      this.$router.push('/database/new')
    },
    handleEdit(row) {
      this.$router.push('/database/' + row.id)
    },
    handleSubmit() {
      this.$refs.form.validate(async valid => {
        if (!valid) return
        const data = this.normalizeForm(this.form)
        if (data.id) {
          await updateDbDatasource(data)
          this.$message.success('更新成功')
        } else {
          await createDbDatasource(data)
          this.$message.success('创建成功')
        }
        this.dialogVisible = false
        this.loadData()
      })
    },
    handleDelete(row) {
      this.$confirm('确定删除数据库数据源「' + row.datasourceName + '」?', '确认', { type: 'warning' }).then(async () => {
        await deleteDbDatasource(row.id)
        this.$message.success('删除成功')
        this.loadData()
      }).catch(() => {})
    },
    async handleTest(row) {
      await testDbDatasource(row.id)
      this.$message.success('连接成功')
    },
    handleTestDraft() {
      this.$refs.form.validate(async valid => {
        if (!valid) return
        await testDbDatasourceDraft(this.normalizeForm(this.form))
        this.$message.success('连接成功')
      })
    },
    openQuery(row) {
      this.queryTarget = row
      this.queryForm = { sql: '', paramsText: '[]', maxRows: 100 }
      this.queryRows = []
      this.queryColumns = []
      this.queryDialogVisible = true
    },
    async runQuery() {
      let params
      try {
        params = this.queryForm.paramsText ? JSON.parse(this.queryForm.paramsText) : []
        if (!Array.isArray(params)) throw new Error('参数必须是数组')
      } catch (e) {
        this.$message.error('参数数组不是合法 JSON')
        return
      }
      this.queryLoading = true
      try {
        const res = await queryDbDatasource(this.queryTarget.id, {
          sql: this.queryForm.sql,
          params,
          maxRows: this.queryForm.maxRows
        })
        this.queryRows = res.data || []
        this.queryColumns = this.queryRows.length ? Object.keys(this.queryRows[0]) : []
      } finally {
        this.queryLoading = false
      }
    },
    onScopeChange(scope) {
      if (scope === 'GLOBAL') this.form.projectId = 0
    },
    onDbTypeChange(value) {
      const option = this.dbTypeOptions.find(item => item.value === value)
      if (!option) return
      if (option.driver) this.form.driverClassName = option.driver
      if (option.validation) this.form.validationQuery = option.validation
      if (option.port) this.form.port = option.port
      this.form.jdbcParams = option.params || ''
      this.onJdbcPartChange()
    },
    onJdbcPartChange() {
      if (this.form.jdbcAutoBuild) {
        this.generateJdbcUrl(false)
      }
    },
    onJdbcAutoBuildChange(enabled) {
      if (enabled) this.generateJdbcUrl(true)
    },
    generateJdbcUrl(showWarning) {
      const jdbcUrl = this.buildJdbcUrl(this.form)
      if (jdbcUrl) {
        this.form.jdbcUrl = jdbcUrl
        return true
      }
      if (showWarning) this.$message.warning('请先填写数据库主机、端口和库名/服务名')
      return false
    },
    buildJdbcUrl(form) {
      const host = (form.host || '').trim()
      const port = form.port
      const databaseName = (form.databaseName || '').trim()
      const params = (form.jdbcParams || '').trim()
      if (!host || !port) return ''
      if (form.dbType === 'MYSQL') {
        if (!databaseName) return ''
        return this.appendQuestionParams(`jdbc:mysql://${host}:${port}/${databaseName}`, params)
      }
      if (form.dbType === 'POSTGRESQL') {
        if (!databaseName) return ''
        return this.appendQuestionParams(`jdbc:postgresql://${host}:${port}/${databaseName}`, params)
      }
      if (form.dbType === 'ORACLE') {
        if (!databaseName) return ''
        return this.appendQuestionParams(`jdbc:oracle:thin:@//${host}:${port}/${databaseName}`, params)
      }
      if (form.dbType === 'SQLSERVER') {
        let url = `jdbc:sqlserver://${host}:${port}`
        if (databaseName) url += `;databaseName=${databaseName}`
        if (params) url += `;${params.replace(/^[?;]/, '')}`
        return url
      }
      return form.jdbcUrl || ''
    },
    appendQuestionParams(url, params) {
      if (!params) return url
      const normalized = params.replace(/^[?&]/, '')
      return `${url}${url.indexOf('?') >= 0 ? '&' : '?'}${normalized}`
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
    normalizeForm(form) {
      const data = { ...form }
      if (data.scope === 'GLOBAL') data.projectId = 0
      if (!data.jdbcUrl) {
        data.jdbcUrl = this.buildJdbcUrl(data)
      }
      delete data.jdbcAutoBuild
      return data
    },
    cleanParams(params) {
      Object.keys(params).forEach(key => {
        if (params[key] === '' || params[key] === null || params[key] === undefined) delete params[key]
      })
      return params
    }
  }
}
</script>

<style lang="scss" scoped>
.database-page {
  .module-hint {
    background: #ECFDF5;
    border: 1px solid #A7F3D0;
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
    color: #475569;
    line-height: 1.5;
  }

  .query-target {
    margin-bottom: 12px;
  }

  .sql-input ::v-deep textarea {
    font-family: Menlo, Monaco, Consolas, monospace;
    font-size: 12px;
    line-height: 1.5;
  }

  .code-input ::v-deep textarea {
    font-family: Menlo, Monaco, Consolas, monospace;
    font-size: 12px;
    line-height: 1.5;
  }

  .form-section {
    border: 1px solid #E5E7EB;
    border-radius: 4px;
    padding: 12px 12px 0;
    margin-bottom: 12px;
  }

  .section-title {
    color: #334155;
    font-weight: 700;
    margin-bottom: 10px;
  }

  .empty-query-result {
    border: 1px dashed #CBD5E1;
    color: #94A3B8;
    text-align: center;
    padding: 28px;
    border-radius: 4px;
  }
}
</style>
